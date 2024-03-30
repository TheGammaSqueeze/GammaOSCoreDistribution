//! jni for uwb native stack
use jni::objects::{JObject, JValue};
use jni::sys::{
    jarray, jboolean, jbyte, jbyteArray, jint, jintArray, jlong, jobject, jshort, jshortArray,
    jsize,
};
use jni::JNIEnv;
use log::{error, info};
use num_traits::ToPrimitive;
use uwb_uci_packets::{
    GetCapsInfoRspPacket, Packet, SessionGetAppConfigRspPacket, SessionSetAppConfigRspPacket,
    StatusCode, UciResponseChild, UciResponsePacket, UciVendor_9_ResponseChild,
    UciVendor_A_ResponseChild, UciVendor_B_ResponseChild, UciVendor_E_ResponseChild,
    UciVendor_F_ResponseChild,
};
use uwb_uci_rust::error::UwbErr;
use uwb_uci_rust::event_manager::EventManagerImpl as EventManager;
use uwb_uci_rust::uci::{uci_hrcv::UciResponse, Dispatcher, DispatcherImpl, JNICommand};

trait Context<'a> {
    fn convert_byte_array(&self, array: jbyteArray) -> Result<Vec<u8>, jni::errors::Error>;
    fn get_array_length(&self, array: jarray) -> Result<jsize, jni::errors::Error>;
    fn get_short_array_region(
        &self,
        array: jshortArray,
        start: jsize,
        buf: &mut [jshort],
    ) -> Result<(), jni::errors::Error>;
    fn get_int_array_region(
        &self,
        array: jintArray,
        start: jsize,
        buf: &mut [jint],
    ) -> Result<(), jni::errors::Error>;
    fn get_dispatcher(&self) -> Result<&'a mut dyn Dispatcher, UwbErr>;
}

struct JniContext<'a> {
    env: JNIEnv<'a>,
    obj: JObject<'a>,
}

impl<'a> JniContext<'a> {
    fn new(env: JNIEnv<'a>, obj: JObject<'a>) -> Self {
        Self { env, obj }
    }
}

impl<'a> Context<'a> for JniContext<'a> {
    fn convert_byte_array(&self, array: jbyteArray) -> Result<Vec<u8>, jni::errors::Error> {
        self.env.convert_byte_array(array)
    }
    fn get_array_length(&self, array: jarray) -> Result<jsize, jni::errors::Error> {
        self.env.get_array_length(array)
    }
    fn get_short_array_region(
        &self,
        array: jshortArray,
        start: jsize,
        buf: &mut [jshort],
    ) -> Result<(), jni::errors::Error> {
        self.env.get_short_array_region(array, start, buf)
    }
    fn get_int_array_region(
        &self,
        array: jintArray,
        start: jsize,
        buf: &mut [jint],
    ) -> Result<(), jni::errors::Error> {
        self.env.get_int_array_region(array, start, buf)
    }
    fn get_dispatcher(&self) -> Result<&'a mut dyn Dispatcher, UwbErr> {
        let dispatcher_ptr_value = self.env.get_field(self.obj, "mDispatcherPointer", "J")?;
        let dispatcher_ptr = dispatcher_ptr_value.j()?;
        if dispatcher_ptr == 0i64 {
            error!("The dispatcher is not initialized.");
            return Err(UwbErr::NoneDispatcher);
        }
        // Safety: dispatcher pointer must not be a null pointer and it must point to a valid dispatcher object.
        // This can be ensured because the dispatcher is created in an earlier stage and
        // won't be deleted before calling doDeinitialize.
        unsafe { Ok(&mut *(dispatcher_ptr as *mut DispatcherImpl)) }
    }
}

/// Initialize UWB
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeInit(
    _env: JNIEnv,
    _obj: JObject,
) -> jboolean {
    logger::init(
        logger::Config::default()
            .with_tag_on_device("uwb")
            .with_min_level(log::Level::Trace)
            .with_filter("trace,jni=info"),
    );
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeInit: enter");
    true as jboolean
}

/// Get max session number
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetMaxSessionNumber(
    _env: JNIEnv,
    _obj: JObject,
) -> jint {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetMaxSessionNumber: enter");
    5
}

/// Turn on UWB. initialize the GKI module and HAL module for UWB device.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoInitialize(
    env: JNIEnv,
    obj: JObject,
) -> jboolean {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoInitialize: enter");
    boolean_result_helper(do_initialize(&JniContext::new(env, obj)), "DoInitialize")
}

/// Turn off UWB. Deinitilize the GKI and HAL module, power of the UWB device.
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoDeinitialize(
    env: JNIEnv,
    obj: JObject,
) -> jboolean {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeDoDeinitialize: enter");
    boolean_result_helper(do_deinitialize(&JniContext::new(env, obj)), "DoDeinitialize")
}

/// get nanos
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos(
    _env: JNIEnv,
    _obj: JObject,
) -> jlong {
    info!(
        "Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetTimestampResolutionNanos: enter"
    );
    0
}

/// reset the device
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDeviceReset(
    env: JNIEnv,
    obj: JObject,
    reset_config: jbyte,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeDeviceReset: enter");
    byte_result_helper(reset_device(&JniContext::new(env, obj), reset_config as u8), "ResetDevice")
}

/// init the session
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionInit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    session_type: jbyte,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionInit: enter");
    byte_result_helper(
        session_init(&JniContext::new(env, obj), session_id as u32, session_type as u8),
        "SessionInit",
    )
}

/// deinit the session
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionDeInit(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeSessionDeInit: enter");
    byte_result_helper(
        session_deinit(&JniContext::new(env, obj), session_id as u32),
        "SessionDeInit",
    )
}

/// get session count
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionCount(
    env: JNIEnv,
    obj: JObject,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionCount: enter");
    match get_session_count(&JniContext::new(env, obj)) {
        Ok(count) => count,
        Err(e) => {
            error!("GetSessionCount failed with {:?}", e);
            -1
        }
    }
}

///  start the ranging
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStart(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStart: enter");
    byte_result_helper(ranging_start(&JniContext::new(env, obj), session_id as u32), "RangingStart")
}

/// stop the ranging
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStop(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeRangingStop: enter");
    byte_result_helper(ranging_stop(&JniContext::new(env, obj), session_id as u32), "RangingStop")
}

/// get the session state
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionState(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetSessionState: enter");
    match get_session_state(&JniContext::new(env, obj), session_id as u32) {
        Ok(state) => state,
        Err(e) => {
            error!("GetSessionState failed with {:?}", e);
            -1
        }
    }
}

/// set app configurations
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    app_config_param_len: jint,
    app_config_params: jbyteArray,
) -> jbyteArray {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetAppConfigurations: enter");
    match set_app_configurations(
        &JniContext::new(env, obj),
        session_id as u32,
        no_of_params as u32,
        app_config_param_len as u32,
        app_config_params,
    ) {
        Ok(data) => {
            let uwb_config_status_class =
                env.find_class("com/android/server/uwb/data/UwbConfigStatusData").unwrap();
            let mut buf: Vec<u8> = Vec::new();
            for iter in data.get_cfg_status() {
                buf.push(iter.cfg_id as u8);
                buf.push(iter.status as u8);
            }
            let cfg_jbytearray = env.byte_array_from_slice(&buf).unwrap();
            let uwb_config_status_object = env.new_object(
                uwb_config_status_class,
                "(II[B)V",
                &[
                    JValue::Int(data.get_status().to_i32().unwrap()),
                    JValue::Int(data.get_cfg_status().len().to_i32().unwrap()),
                    JValue::Object(JObject::from(cfg_jbytearray)),
                ],
            );
            *uwb_config_status_object.unwrap()
        }
        Err(e) => {
            error!("SetAppConfig failed with: {:?}", e);
            *JObject::null()
        }
    }
}

/// get app configurations
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetAppConfigurations(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    no_of_params: jint,
    app_config_param_len: jint,
    app_config_params: jbyteArray,
) -> jbyteArray {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetAppConfigurations: enter");
    match get_app_configurations(
        &JniContext::new(env, obj),
        session_id as u32,
        no_of_params as u32,
        app_config_param_len as u32,
        app_config_params,
    ) {
        Ok(data) => {
            let uwb_tlv_info_class =
                env.find_class("com/android/server/uwb/data/UwbTlvData").unwrap();
            let mut buf: Vec<u8> = Vec::new();
            for tlv in data.get_tlvs() {
                buf.push(tlv.cfg_id as u8);
                buf.push(tlv.v.len() as u8);
                buf.extend(&tlv.v);
            }
            let tlv_jbytearray = env.byte_array_from_slice(&buf).unwrap();
            let uwb_tlv_info_object = env.new_object(
                uwb_tlv_info_class,
                "(II[B)V",
                &[
                    JValue::Int(data.get_status().to_i32().unwrap()),
                    JValue::Int(data.get_tlvs().len().to_i32().unwrap()),
                    JValue::Object(JObject::from(tlv_jbytearray)),
                ],
            );
            *uwb_tlv_info_object.unwrap()
        }
        Err(e) => {
            error!("GetAppConfig failed with: {:?}", e);
            *JObject::null()
        }
    }
}

/// get capability info
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetCapsInfo(
    env: JNIEnv,
    obj: JObject,
) -> jbyteArray {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetCapsInfo: enter");
    match get_caps_info(&JniContext::new(env, obj)) {
        Ok(data) => {
            let uwb_tlv_info_class =
                env.find_class("com/android/server/uwb/data/UwbTlvData").unwrap();
            let mut buf: Vec<u8> = Vec::new();
            for tlv in data.get_tlvs() {
                buf.push(tlv.t as u8);
                buf.push(tlv.v.len() as u8);
                buf.extend(&tlv.v);
            }
            let tlv_jbytearray = env.byte_array_from_slice(&buf).unwrap();
            let uwb_tlv_info_object = env.new_object(
                uwb_tlv_info_class,
                "(II[B)V",
                &[
                    JValue::Int(data.get_status().to_i32().unwrap()),
                    JValue::Int(data.get_tlvs().len().to_i32().unwrap()),
                    JValue::Object(JObject::from(tlv_jbytearray)),
                ],
            );
            *uwb_tlv_info_object.unwrap()
        }
        Err(e) => {
            error!("GetCapsInfo failed with: {:?}", e);
            *JObject::null()
        }
    }
}

/// update multicast list
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate(
    env: JNIEnv,
    obj: JObject,
    session_id: jint,
    action: jbyte,
    no_of_controlee: jbyte,
    addresses: jshortArray,
    sub_session_ids: jintArray,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeControllerMulticastListUpdate: enter");
    byte_result_helper(
        multicast_list_update(
            &JniContext::new(env, obj),
            session_id as u32,
            action as u8,
            no_of_controlee as u8,
            addresses,
            sub_session_ids,
        ),
        "ControllerMulticastListUpdate",
    )
}

/// set country code
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetCountryCode(
    env: JNIEnv,
    obj: JObject,
    country_code: jbyteArray,
) -> jbyte {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeSetCountryCode: enter");
    byte_result_helper(set_country_code(&JniContext::new(env, obj), country_code), "SetCountryCode")
}

/// set country code
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeSendRawVendorCmd(
    env: JNIEnv,
    obj: JObject,
    gid: jint,
    oid: jint,
    payload: jbyteArray,
) -> jobject {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeRawVendor: enter");
    let uwb_vendor_uci_response_class =
        env.find_class("com/android/server/uwb/data/UwbVendorUciResponse").unwrap();
    match send_raw_vendor_cmd(
        &JniContext::new(env, obj),
        gid.try_into().expect("invalid gid"),
        oid.try_into().expect("invalid oid"),
        payload,
    ) {
        Ok((gid, oid, payload)) => *env
            .new_object(
                uwb_vendor_uci_response_class,
                "(BII[B)V",
                &[
                    JValue::Byte(StatusCode::UciStatusOk.to_i8().unwrap()),
                    JValue::Int(gid.to_i32().unwrap()),
                    JValue::Int(oid.to_i32().unwrap()),
                    JValue::Object(JObject::from(
                        env.byte_array_from_slice(payload.as_ref()).unwrap(),
                    )),
                ],
            )
            .unwrap(),
        Err(e) => {
            error!("send raw uci cmd failed with: {:?}", e);
            *env.new_object(
                uwb_vendor_uci_response_class,
                "(BII[B)V",
                &[
                    JValue::Byte(StatusCode::UciStatusFailed.to_i8().unwrap()),
                    JValue::Int(-1),
                    JValue::Int(-1),
                    JValue::Object(JObject::null()),
                ],
            )
            .unwrap()
        }
    }
}

/// retrieve the UWB power stats
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetPowerStats(
    env: JNIEnv,
    obj: JObject,
) -> jobject {
    info!("Java_com_android_server_uwb_jni_NativeUwbManager_nativeGetPowerStats: enter");
    let uwb_power_stats_class =
        env.find_class("com/android/server/uwb/info/UwbPowerStats").unwrap();
    match get_power_stats(&JniContext::new(env, obj)) {
        Ok(para) => {
            let power_stats = env.new_object(uwb_power_stats_class, "(IIII)V", &para).unwrap();
            *power_stats
        }
        Err(e) => {
            error!("Get power stats failed with: {:?}", e);
            *JObject::null()
        }
    }
}

fn boolean_result_helper(result: Result<(), UwbErr>, function_name: &str) -> jboolean {
    match result {
        Ok(()) => true as jboolean,
        Err(err) => {
            error!("{} failed with: {:?}", function_name, err);
            false as jboolean
        }
    }
}

fn byte_result_helper(result: Result<(), UwbErr>, function_name: &str) -> jbyte {
    match result {
        Ok(()) => StatusCode::UciStatusOk.to_i8().unwrap(),
        Err(err) => {
            error!("{} failed with: {:?}", function_name, err);
            match err {
                UwbErr::StatusCode(status_code) => status_code
                    .to_i8()
                    .unwrap_or_else(|| StatusCode::UciStatusFailed.to_i8().unwrap()),
                _ => StatusCode::UciStatusFailed.to_i8().unwrap(),
            }
        }
    }
}

fn do_initialize<'a, T: Context<'a>>(context: &T) -> Result<(), UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    dispatcher.send_jni_command(JNICommand::Enable)?;
    match uwa_get_device_info(dispatcher) {
        Ok(res) => {
            if let UciResponse::GetDeviceInfoRsp(device_info) = res {
                dispatcher.set_device_info(Some(device_info));
            }
        }
        Err(e) => {
            error!("GetDeviceInfo failed with: {:?}", e);
            return Err(UwbErr::failed());
        }
    }
    Ok(())
}

fn do_deinitialize<'a, T: Context<'a>>(context: &T) -> Result<(), UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    dispatcher.send_jni_command(JNICommand::Disable(true))?;
    dispatcher.wait_for_exit()?;
    Ok(())
}

// unused, but leaving this behind if we want to use it later.
#[allow(dead_code)]
fn get_specification_info<'a, T: Context<'a>>(context: &T) -> Result<[JValue<'a>; 16], UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    match dispatcher.get_device_info() {
        Some(data) => {
            Ok([
                JValue::Int((data.get_uci_version() & 0xFF).into()),
                JValue::Int(((data.get_uci_version() >> 8) & 0xF).into()),
                JValue::Int(((data.get_uci_version() >> 12) & 0xF).into()),
                JValue::Int((data.get_mac_version() & 0xFF).into()),
                JValue::Int(((data.get_mac_version() >> 8) & 0xF).into()),
                JValue::Int(((data.get_mac_version() >> 12) & 0xF).into()),
                JValue::Int((data.get_phy_version() & 0xFF).into()),
                JValue::Int(((data.get_phy_version() >> 8) & 0xF).into()),
                JValue::Int(((data.get_phy_version() >> 12) & 0xF).into()),
                JValue::Int((data.get_uci_test_version() & 0xFF).into()),
                JValue::Int(((data.get_uci_test_version() >> 8) & 0xF).into()),
                JValue::Int(((data.get_uci_test_version() >> 12) & 0xF).into()),
                JValue::Int(1), // fira_major_version
                JValue::Int(0), // fira_minor_version
                JValue::Int(1), // ccc_major_version
                JValue::Int(0), // ccc_minor_version
            ])
        }
        None => {
            error!("Fail to get specification info.");
            Err(UwbErr::failed())
        }
    }
}

fn session_init<'a, T: Context<'a>>(
    context: &T,
    session_id: u32,
    session_type: u8,
) -> Result<(), UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    let res = match dispatcher
        .block_on_jni_command(JNICommand::UciSessionInit(session_id, session_type))?
    {
        UciResponse::SessionInitRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn session_deinit<'a, T: Context<'a>>(context: &T, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciSessionDeinit(session_id))? {
        UciResponse::SessionDeinitRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn get_session_count<'a, T: Context<'a>>(context: &T) -> Result<jbyte, UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    match dispatcher.block_on_jni_command(JNICommand::UciSessionGetCount)? {
        UciResponse::SessionGetCountRsp(rsp) => match status_code_to_res(rsp.get_status()) {
            Ok(()) => Ok(rsp.get_session_count() as jbyte),
            Err(err) => Err(err),
        },
        _ => Err(UwbErr::failed()),
    }
}

fn ranging_start<'a, T: Context<'a>>(context: &T, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciStartRange(session_id))? {
        UciResponse::RangeStartRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn ranging_stop<'a, T: Context<'a>>(context: &T, session_id: u32) -> Result<(), UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciStopRange(session_id))? {
        UciResponse::RangeStopRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn get_session_state<'a, T: Context<'a>>(context: &T, session_id: u32) -> Result<jbyte, UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    match dispatcher.block_on_jni_command(JNICommand::UciGetSessionState(session_id))? {
        UciResponse::SessionGetStateRsp(data) => Ok(data.get_session_state() as jbyte),
        _ => Err(UwbErr::failed()),
    }
}

fn set_app_configurations<'a, T: Context<'a>>(
    context: &T,
    session_id: u32,
    no_of_params: u32,
    app_config_param_len: u32,
    app_config_params: jintArray,
) -> Result<SessionSetAppConfigRspPacket, UwbErr> {
    let app_configs = context.convert_byte_array(app_config_params)?;
    let dispatcher = context.get_dispatcher()?;
    match dispatcher.block_on_jni_command(JNICommand::UciSetAppConfig {
        session_id,
        no_of_params,
        app_config_param_len,
        app_configs,
    })? {
        UciResponse::SessionSetAppConfigRsp(data) => Ok(data),
        _ => Err(UwbErr::failed()),
    }
}

fn get_app_configurations<'a, T: Context<'a>>(
    context: &T,
    session_id: u32,
    no_of_params: u32,
    app_config_param_len: u32,
    app_config_params: jintArray,
) -> Result<SessionGetAppConfigRspPacket, UwbErr> {
    let app_configs = context.convert_byte_array(app_config_params)?;
    let dispatcher = context.get_dispatcher()?;
    match dispatcher.block_on_jni_command(JNICommand::UciGetAppConfig {
        session_id,
        no_of_params,
        app_config_param_len,
        app_configs,
    })? {
        UciResponse::SessionGetAppConfigRsp(data) => Ok(data),
        _ => Err(UwbErr::failed()),
    }
}

fn get_caps_info<'a, T: Context<'a>>(context: &T) -> Result<GetCapsInfoRspPacket, UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    match dispatcher.block_on_jni_command(JNICommand::UciGetCapsInfo)? {
        UciResponse::GetCapsInfoRsp(data) => Ok(data),
        _ => Err(UwbErr::failed()),
    }
}

fn multicast_list_update<'a, T: Context<'a>>(
    context: &T,
    session_id: u32,
    action: u8,
    no_of_controlee: u8,
    addresses: jshortArray,
    sub_session_ids: jintArray,
) -> Result<(), UwbErr> {
    let mut address_list = vec![0i16; context.get_array_length(addresses)?.try_into().unwrap()];
    context.get_short_array_region(addresses, 0, &mut address_list)?;
    let mut sub_session_id_list =
        vec![0i32; context.get_array_length(sub_session_ids)?.try_into().unwrap()];
    context.get_int_array_region(sub_session_ids, 0, &mut sub_session_id_list)?;
    let dispatcher = context.get_dispatcher()?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciSessionUpdateMulticastList {
        session_id,
        action,
        no_of_controlee,
        address_list: address_list.to_vec(),
        sub_session_id_list: sub_session_id_list.to_vec(),
    })? {
        UciResponse::SessionUpdateControllerMulticastListRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn set_country_code<'a, T: Context<'a>>(
    context: &T,
    country_code: jbyteArray,
) -> Result<(), UwbErr> {
    let code = context.convert_byte_array(country_code)?;
    if code.len() != 2 {
        return Err(UwbErr::failed());
    }
    let dispatcher = context.get_dispatcher()?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciSetCountryCode { code })? {
        UciResponse::AndroidSetCountryCodeRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

fn get_vendor_uci_payload(data: UciResponsePacket) -> Result<Vec<u8>, UwbErr> {
    match data.specialize() {
        UciResponseChild::UciVendor_9_Response(evt) => match evt.specialize() {
            UciVendor_9_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_9_ResponseChild::None => Ok(Vec::new()),
        },
        UciResponseChild::UciVendor_A_Response(evt) => match evt.specialize() {
            UciVendor_A_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_A_ResponseChild::None => Ok(Vec::new()),
        },
        UciResponseChild::UciVendor_B_Response(evt) => match evt.specialize() {
            UciVendor_B_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_B_ResponseChild::None => Ok(Vec::new()),
        },
        UciResponseChild::UciVendor_E_Response(evt) => match evt.specialize() {
            UciVendor_E_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_E_ResponseChild::None => Ok(Vec::new()),
        },
        UciResponseChild::UciVendor_F_Response(evt) => match evt.specialize() {
            UciVendor_F_ResponseChild::Payload(payload) => Ok(payload.to_vec()),
            UciVendor_F_ResponseChild::None => Ok(Vec::new()),
        },
        _ => {
            error!("Invalid vendor response with gid {:?}", data.get_group_id());
            Err(UwbErr::Specialize(data.to_vec()))
        }
    }
}

fn send_raw_vendor_cmd<'a, T: Context<'a>>(
    context: &T,
    gid: u32,
    oid: u32,
    payload: jbyteArray,
) -> Result<(i32, i32, Vec<u8>), UwbErr> {
    let payload = context.convert_byte_array(payload)?;
    let dispatcher = context.get_dispatcher()?;
    match dispatcher.block_on_jni_command(JNICommand::UciRawVendorCmd { gid, oid, payload })? {
        UciResponse::RawVendorRsp(response) => Ok((
            response.get_group_id().to_i32().unwrap(),
            response.get_opcode().to_i32().unwrap(),
            get_vendor_uci_payload(response)?,
        )),
        _ => Err(UwbErr::failed()),
    }
}

fn status_code_to_res(status_code: StatusCode) -> Result<(), UwbErr> {
    match status_code {
        StatusCode::UciStatusOk => Ok(()),
        _ => Err(UwbErr::StatusCode(status_code)),
    }
}

/// create a dispatcher instance
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDispatcherNew(
    env: JNIEnv,
    obj: JObject,
) -> jlong {
    let eventmanager = match EventManager::new(env, obj) {
        Ok(evtmgr) => evtmgr,
        Err(err) => {
            error!("Fail to create event manager{:?}", err);
            return *JObject::null() as jlong;
        }
    };
    match DispatcherImpl::new(eventmanager) {
        Ok(dispatcher) => Box::into_raw(Box::new(dispatcher)) as jlong,
        Err(err) => {
            error!("Fail to create dispatcher {:?}", err);
            *JObject::null() as jlong
        }
    }
}

/// destroy the dispatcher instance
#[no_mangle]
pub extern "system" fn Java_com_android_server_uwb_jni_NativeUwbManager_nativeDispatcherDestroy(
    env: JNIEnv,
    obj: JObject,
) {
    let dispatcher_ptr_value = match env.get_field(obj, "mDispatcherPointer", "J") {
        Ok(value) => value,
        Err(err) => {
            error!("Failed to get the pointer with: {:?}", err);
            return;
        }
    };
    let dispatcher_ptr = match dispatcher_ptr_value.j() {
        Ok(value) => value,
        Err(err) => {
            error!("Failed to get the pointer with: {:?}", err);
            return;
        }
    };
    // Safety: dispatcher pointer must not be a null pointer and must point to a valid dispatcher object.
    // This can be ensured because the dispatcher is created in an earlier stage and
    // won't be deleted before calling this destroy function.
    // This function will early return if the instance is already destroyed.
    let _boxed_dispatcher = unsafe { Box::from_raw(dispatcher_ptr as *mut DispatcherImpl) };
    info!("The dispatcher successfully destroyed.");
}

fn get_power_stats<'a, T: Context<'a>>(context: &T) -> Result<[JValue<'a>; 4], UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    match dispatcher.block_on_jni_command(JNICommand::UciGetPowerStats)? {
        UciResponse::AndroidGetPowerStatsRsp(data) => Ok([
            JValue::Int(data.get_stats().idle_time_ms as i32),
            JValue::Int(data.get_stats().tx_time_ms as i32),
            JValue::Int(data.get_stats().rx_time_ms as i32),
            JValue::Int(data.get_stats().total_wake_count as i32),
        ]),
        _ => Err(UwbErr::failed()),
    }
}

fn uwa_get_device_info(dispatcher: &dyn Dispatcher) -> Result<UciResponse, UwbErr> {
    let res = dispatcher.block_on_jni_command(JNICommand::UciGetDeviceInfo)?;
    Ok(res)
}

fn reset_device<'a, T: Context<'a>>(context: &T, reset_config: u8) -> Result<(), UwbErr> {
    let dispatcher = context.get_dispatcher()?;
    let res = match dispatcher.block_on_jni_command(JNICommand::UciDeviceReset { reset_config })? {
        UciResponse::DeviceResetRsp(data) => data,
        _ => return Err(UwbErr::failed()),
    };
    status_code_to_res(res.get_status())
}

#[cfg(test)]
mod mock_context;
#[cfg(test)]
mod mock_dispatcher;

#[cfg(test)]
mod tests {
    use super::*;

    use crate::mock_context::MockContext;
    use crate::mock_dispatcher::MockDispatcher;

    #[test]
    fn test_boolean_result_helper() {
        assert_eq!(true as jboolean, boolean_result_helper(Ok(()), "Foo"));
        assert_eq!(false as jboolean, boolean_result_helper(Err(UwbErr::Undefined), "Foo"));
    }

    #[test]
    fn test_byte_result_helper() {
        assert_eq!(StatusCode::UciStatusOk.to_i8().unwrap(), byte_result_helper(Ok(()), "Foo"));
        assert_eq!(
            StatusCode::UciStatusFailed.to_i8().unwrap(),
            byte_result_helper(Err(UwbErr::Undefined), "Foo")
        );
        assert_eq!(
            StatusCode::UciStatusRejected.to_i8().unwrap(),
            byte_result_helper(Err(UwbErr::StatusCode(StatusCode::UciStatusRejected)), "Foo")
        );
    }

    #[test]
    fn test_do_initialize() {
        let packet = uwb_uci_packets::GetDeviceInfoRspBuilder {
            status: StatusCode::UciStatusOk,
            uci_version: 0,
            mac_version: 0,
            phy_version: 0,
            uci_test_version: 0,
            vendor_spec_info: vec![],
        }
        .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_send_jni_command(JNICommand::Enable, Ok(()));
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciGetDeviceInfo,
            Ok(UciResponse::GetDeviceInfoRsp(packet.clone())),
        );
        let mut context = MockContext::new(dispatcher);

        let result = do_initialize(&context);
        let device_info = context.get_mock_dispatcher().get_device_info().clone();
        assert!(result.is_ok());
        assert_eq!(device_info.unwrap().to_vec(), packet.to_vec());
    }

    #[test]
    fn test_do_deinitialize() {
        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_send_jni_command(JNICommand::Disable(true), Ok(()));
        dispatcher.expect_wait_for_exit(Ok(()));
        let context = MockContext::new(dispatcher);

        let result = do_deinitialize(&context);
        assert!(result.is_ok());
    }

    #[test]
    fn test_get_specification_info() {
        let packet = uwb_uci_packets::GetDeviceInfoRspBuilder {
            status: StatusCode::UciStatusOk,
            uci_version: 0x1234,
            mac_version: 0x5678,
            phy_version: 0x9ABC,
            uci_test_version: 0x1357,
            vendor_spec_info: vec![],
        }
        .build();
        let expected_array = [
            0x34, 0x2, 0x1, // uci_version
            0x78, 0x6, 0x5, // mac_version.
            0xBC, 0xA, 0x9, // phy_version.
            0x57, 0x3, 0x1, // uci_test_version.
            1,   // fira_major_version
            0,   // fira_minor_version
            1,   // ccc_major_version
            0,   // ccc_minor_version
        ];

        let mut dispatcher = MockDispatcher::new();
        dispatcher.set_device_info(Some(packet));
        let context = MockContext::new(dispatcher);

        let results = get_specification_info(&context).unwrap();
        for (idx, result) in results.iter().enumerate() {
            assert_eq!(TryInto::<jint>::try_into(*result).unwrap(), expected_array[idx]);
        }
    }

    #[test]
    fn test_session_init() {
        let session_id = 1234;
        let session_type = 5;
        let packet =
            uwb_uci_packets::SessionInitRspBuilder { status: StatusCode::UciStatusOk }.build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciSessionInit(session_id, session_type),
            Ok(UciResponse::SessionInitRsp(packet)),
        );
        let context = MockContext::new(dispatcher);

        let result = session_init(&context, session_id, session_type);
        assert!(result.is_ok());
    }

    #[test]
    fn test_session_deinit() {
        let session_id = 1234;
        let packet =
            uwb_uci_packets::SessionDeinitRspBuilder { status: StatusCode::UciStatusOk }.build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciSessionDeinit(session_id),
            Ok(UciResponse::SessionDeinitRsp(packet)),
        );
        let context = MockContext::new(dispatcher);

        let result = session_deinit(&context, session_id);
        assert!(result.is_ok());
    }

    #[test]
    fn test_get_session_count() {
        let session_count = 7;
        let packet = uwb_uci_packets::SessionGetCountRspBuilder {
            status: StatusCode::UciStatusOk,
            session_count,
        }
        .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciSessionGetCount,
            Ok(UciResponse::SessionGetCountRsp(packet)),
        );
        let context = MockContext::new(dispatcher);

        let result = get_session_count(&context).unwrap();
        assert_eq!(result, session_count as jbyte);
    }

    #[test]
    fn test_ranging_start() {
        let session_id = 1234;
        let packet =
            uwb_uci_packets::RangeStartRspBuilder { status: StatusCode::UciStatusOk }.build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciStartRange(session_id),
            Ok(UciResponse::RangeStartRsp(packet)),
        );
        let context = MockContext::new(dispatcher);

        let result = ranging_start(&context, session_id);
        assert!(result.is_ok());
    }

    #[test]
    fn test_ranging_stop() {
        let session_id = 1234;
        let packet =
            uwb_uci_packets::RangeStopRspBuilder { status: StatusCode::UciStatusOk }.build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciStopRange(session_id),
            Ok(UciResponse::RangeStopRsp(packet)),
        );
        let context = MockContext::new(dispatcher);

        let result = ranging_stop(&context, session_id);
        assert!(result.is_ok());
    }

    #[test]
    fn test_get_session_state() {
        let session_id = 1234;
        let session_state = uwb_uci_packets::SessionState::SessionStateActive;
        let packet = uwb_uci_packets::SessionGetStateRspBuilder {
            status: StatusCode::UciStatusOk,
            session_state,
        }
        .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciGetSessionState(session_id),
            Ok(UciResponse::SessionGetStateRsp(packet)),
        );
        let context = MockContext::new(dispatcher);

        let result = get_session_state(&context, session_id).unwrap();
        assert_eq!(result, session_state as jbyte);
    }

    #[test]
    fn test_set_app_configurations() {
        let session_id = 1234;
        let no_of_params = 3;
        let app_config_param_len = 5;
        let app_configs = vec![1, 2, 3, 4, 5];
        let fake_app_config_params = std::ptr::null_mut();
        let packet = uwb_uci_packets::SessionSetAppConfigRspBuilder {
            status: StatusCode::UciStatusOk,
            cfg_status: vec![],
        }
        .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciSetAppConfig {
                session_id,
                no_of_params,
                app_config_param_len,
                app_configs: app_configs.clone(),
            },
            Ok(UciResponse::SessionSetAppConfigRsp(packet.clone())),
        );
        let mut context = MockContext::new(dispatcher);
        context.expect_convert_byte_array(fake_app_config_params, Ok(app_configs));

        let result = set_app_configurations(
            &context,
            session_id,
            no_of_params,
            app_config_param_len,
            fake_app_config_params,
        )
        .unwrap();
        assert_eq!(result.to_vec(), packet.to_vec());
    }

    #[test]
    fn test_get_app_configurations() {
        let session_id = 1234;
        let no_of_params = 3;
        let app_config_param_len = 5;
        let app_configs = vec![1, 2, 3, 4, 5];
        let fake_app_config_params = std::ptr::null_mut();
        let packet = uwb_uci_packets::SessionGetAppConfigRspBuilder {
            status: StatusCode::UciStatusOk,
            tlvs: vec![],
        }
        .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciGetAppConfig {
                session_id,
                no_of_params,
                app_config_param_len,
                app_configs: app_configs.clone(),
            },
            Ok(UciResponse::SessionGetAppConfigRsp(packet.clone())),
        );
        let mut context = MockContext::new(dispatcher);
        context.expect_convert_byte_array(fake_app_config_params, Ok(app_configs));

        let result = get_app_configurations(
            &context,
            session_id,
            no_of_params,
            app_config_param_len,
            fake_app_config_params,
        )
        .unwrap();
        assert_eq!(result.to_vec(), packet.to_vec());
    }

    #[test]
    fn test_get_caps_info() {
        let packet = uwb_uci_packets::GetCapsInfoRspBuilder {
            status: StatusCode::UciStatusOk,
            tlvs: vec![],
        }
        .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciGetCapsInfo,
            Ok(UciResponse::GetCapsInfoRsp(packet.clone())),
        );
        let context = MockContext::new(dispatcher);

        let result = get_caps_info(&context).unwrap();
        assert_eq!(result.to_vec(), packet.to_vec());
    }

    #[test]
    fn test_multicast_list_update() {
        let session_id = 1234;
        let action = 3;
        let no_of_controlee = 5;
        let fake_addresses = std::ptr::null_mut();
        let address_list = Box::new([1, 3, 5, 7, 9]);
        let fake_sub_session_ids = std::ptr::null_mut();
        let sub_session_id_list = Box::new([2, 4, 6, 8, 10]);
        let packet = uwb_uci_packets::SessionUpdateControllerMulticastListRspBuilder {
            status: StatusCode::UciStatusOk,
        }
        .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciSessionUpdateMulticastList {
                session_id,
                action,
                no_of_controlee,
                address_list: address_list.to_vec(),
                sub_session_id_list: sub_session_id_list.to_vec(),
            },
            Ok(UciResponse::SessionUpdateControllerMulticastListRsp(packet)),
        );
        let mut context = MockContext::new(dispatcher);
        context.expect_get_array_length(fake_addresses, Ok(address_list.len() as jsize));
        context.expect_get_short_array_region(fake_addresses, 0, Ok(address_list));
        context
            .expect_get_array_length(fake_sub_session_ids, Ok(sub_session_id_list.len() as jsize));
        context.expect_get_int_array_region(fake_sub_session_ids, 0, Ok(sub_session_id_list));

        let result = multicast_list_update(
            &context,
            session_id,
            action,
            no_of_controlee,
            fake_addresses,
            fake_sub_session_ids,
        );
        assert!(result.is_ok());
    }

    #[test]
    fn test_set_country_code() {
        let fake_country_code = std::ptr::null_mut();
        let country_code = "US".as_bytes().to_vec();
        let packet =
            uwb_uci_packets::AndroidSetCountryCodeRspBuilder { status: StatusCode::UciStatusOk }
                .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciSetCountryCode { code: country_code.clone() },
            Ok(UciResponse::AndroidSetCountryCodeRsp(packet)),
        );
        let mut context = MockContext::new(dispatcher);
        context.expect_convert_byte_array(fake_country_code, Ok(country_code));

        let result = set_country_code(&context, fake_country_code);
        assert!(result.is_ok());
    }

    #[test]
    fn test_send_raw_vendor_cmd() {
        let gid = 2;
        let oid = 4;
        let opcode = 6;
        let fake_payload = std::ptr::null_mut();
        let payload = vec![1, 2, 4, 8];
        let response = vec![3, 6, 9];
        let packet = uwb_uci_packets::UciVendor_9_ResponseBuilder {
            opcode,
            payload: Some(response.clone().into()),
        }
        .build()
        .into();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciRawVendorCmd { gid, oid, payload: payload.clone() },
            Ok(UciResponse::RawVendorRsp(packet)),
        );
        let mut context = MockContext::new(dispatcher);
        context.expect_convert_byte_array(fake_payload, Ok(payload));

        let result = send_raw_vendor_cmd(&context, gid, oid, fake_payload).unwrap();
        assert_eq!(result.0, uwb_uci_packets::GroupId::VendorReserved9 as i32);
        assert_eq!(result.1, opcode as i32);
        assert_eq!(result.2, response);
    }

    #[test]
    fn test_get_power_stats() {
        let idle_time_ms = 5;
        let tx_time_ms = 4;
        let rx_time_ms = 3;
        let total_wake_count = 2;
        let packet = uwb_uci_packets::AndroidGetPowerStatsRspBuilder {
            stats: uwb_uci_packets::PowerStats {
                status: StatusCode::UciStatusOk,
                idle_time_ms,
                tx_time_ms,
                rx_time_ms,
                total_wake_count,
            },
        }
        .build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciGetPowerStats,
            Ok(UciResponse::AndroidGetPowerStatsRsp(packet)),
        );
        let context = MockContext::new(dispatcher);

        let result = get_power_stats(&context).unwrap();
        assert_eq!(TryInto::<jint>::try_into(result[0]).unwrap(), idle_time_ms as jint);
        assert_eq!(TryInto::<jint>::try_into(result[1]).unwrap(), tx_time_ms as jint);
        assert_eq!(TryInto::<jint>::try_into(result[2]).unwrap(), rx_time_ms as jint);
        assert_eq!(TryInto::<jint>::try_into(result[3]).unwrap(), total_wake_count as jint);
    }

    #[test]
    fn test_reset_device() {
        let reset_config = uwb_uci_packets::ResetConfig::UwbsReset as u8;
        let packet =
            uwb_uci_packets::DeviceResetRspBuilder { status: StatusCode::UciStatusOk }.build();

        let mut dispatcher = MockDispatcher::new();
        dispatcher.expect_block_on_jni_command(
            JNICommand::UciDeviceReset { reset_config },
            Ok(UciResponse::DeviceResetRsp(packet)),
        );
        let context = MockContext::new(dispatcher);

        let result = reset_device(&context, reset_config);
        assert!(result.is_ok());
    }
}
