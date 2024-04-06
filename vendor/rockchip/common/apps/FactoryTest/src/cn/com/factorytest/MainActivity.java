package cn.com.factorytest;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.Uri;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiConfiguration;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.Bundle;
import android.os.StatFs;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.text.Editable;
import android.text.format.Formatter;
import android.Manifest;
import android.content.pm.PackageManager;

import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.math.*;

public class MainActivity extends Activity {

	private static final String TAG = Tools.TAG;
    private static final boolean DISABLED_WRITE_MAC = false;
	private static final boolean DISABLED_POWER_LED = true;
	private static final boolean DISABLED_KEY = true;
	private static final boolean DISABLED_RTC = true;
	private static final boolean DISABLED_USB2 = false;
	private static final boolean DISABLED_DEVICE_ID = true;
	private static final boolean DISABLED_SN  = true;
    private static boolean ageing_test_ok_flag = false;
    TextView m_firmware_version;
    TextView m_ddr_size;
    TextView m_nand_size;
    TextView m_device_type;
    TextView m_macvalue;
    TextView m_snvalue;
    TextView m_ip;
    TextView m_wifimac;
    TextView m_wifiip;
    TextView m_device_id;
    
    TextView m_mactitle;
    EditText m_maccheck;
    TextView m_TextView_Time;
    TextView m_TextView_TF;
    TextView m_TextView_USB1;
    TextView m_TextView_USB2;

    TextView m_TextView_Pcie;
    TextView m_TextView_Gesture;
    TextView m_TextView_Gsensor;
    TextView m_TextView_Gyro;
    TextView m_TextView_MCU;
    TextView m_TextView_SPIFLASH;
    TextView m_TextView_AGEING;
	TextView m_TextView_Gigabit_network;
    TextView m_TextView_Lan;
    TextView m_TextView_Wifi;
	TextView m_TextView_BT;
	TextView m_TextView_Rtc;
    TextView m_TextView_HDMI;
	TextView m_TextView_PD12;
	TextView m_TextView_Charge;
    Button m_Button_write_mac_usid;
    Button m_Button_NetLed;
    Button m_Button_PowerLed;
	Button m_Button_Key;

    Button m_Button_EnableWol;
	Button m_Button_Restore_MCU_settings;
	Button m_Button_speaker_MIC;
    Button m_Button_DisableWol;
 

    Handler mHandler = new FactoryHandler();

    private final int MSG_WIFI_TEST_ERROR =  77;
    private final int MSG_WIFI_TEST_OK =  78;
    private final int MSG_LAN_TEST_ERROR =  79;
    private final int MSG_LAN_TEST_OK =  80;
    private final int MSG_TF_TEST_ERROR =  81;
    private final int MSG_TF_TEST_OK =  82;
    private final int MSG_USB1_TEST_ERROR = 83;
    private final int MSG_USB1_TEST_OK =  84;
    private final int MSG_USB2_TEST_ERROR =  85;
    private final int MSG_USB2_TEST_OK =  86;
    private final int MSG_NETLED_TEST_Start =  87;
    private final int MSG_NETLED_TEST_End =  88;
    private final int MSG_POWERLED_TEST_Start =  89;
    private final int MSG_POWERLED_TEST_End =  90;
    private final int MSG_WIFI_TOAST =  91;
    private final int MSG_PLAY_VIDEO =  92;
	private final int MSG_TF_TEST_XL_OK = 93;
	private final int MSG_TF_TEST_XL_ERROR = 94;
	private final int MSG_USB1_TEST_XL_OK = 95;
	private final int MSG_USB1_TEST_XL_ERROR = 96;
	private final int MSG_android_6_0_TEXT_LAYOUT = 97;
	private final int MSG_USB2_TEST_XL_OK = 98;
	private final int MSG_USB2_TEST_XL_ERROR = 99;
	private final int MSG_RTC_TEST_OK = 100;
	private final int MSG_RTC_TEST_ERROR = 101;
	private final int MSG_BT_TEST_ERROR =  102;
	private final int MSG_BT_TEST_OK =  103;
	private final int MSG_GSENSOR_TEST_OK =  104;
	private final int MSG_GSENSOR_TEST_ERROR =  105;
	private final int MSG_GYRO_TEST_OK =  106;
	private final int MSG_GYRO_TEST_ERROR =  107;
	private final int MSG_GESTURE_TEST_OK =  108;
	private final int MSG_GESTURE_TEST_ERROR =  109;
	private final int MSG_PCIE_TEST_OK =  110;
	private final int MSG_PCIE_TEST_ERROR =  111;
	private final int MSG_MCU_TEST_ERROR =  112;
	private final int MSG_MCU_TEST_OK =  113;
	private final int MSG_SPIFLASH_TEST_ERROR =  114;
	private final int MSG_SPIFLASH_TEST_OK =  115;
	private final int MSG_Gigabit_network_TEST_ERROR =  116;
	private final int MSG_Gigabit_network_TEST_OK =  117;	
	private final int MSG_HDMI_TEST_ERROR =  118;
	private final int MSG_HDMI_TEST_OK =  119;	
	private final int MSG_PD12_TEST_ERROR =  120;
	private final int MSG_PD12_TEST_OK =  121;	
	private final int MSG_PD2_TEST_ERROR =  122;
	private final int MSG_PD1_TEST_ERROR =  123;	
	private final int MSG_Charge_TEST_ERROR =  124;
	private final int MSG_Charge_TEST_OK =  125;	
	private final int MSG_AGEING_TEST_ERROR =	126;
	private final int MSG_AGEING_TEST_OK =  127;				
    private final int MSG_TIME = 777;
    private static final String nullip = "0.0.0.0";
    private static final String USB_PATH = (Tools.isAndroid5_1_1()?"/storage/udisk":"/storage/external_storage/sd");
    private static final String USB1_PATH = (Tools.isAndroid5_1_1()?"/storage/udisk0":"/storage/external_storage/sda");
    private static final String USB2_PATH = (Tools.isAndroid5_1_1()?"/storage/udisk1":"/storage/external_storage/sdb");
    private static final String TFCARD_PATH = (Tools.isAndroid5_1_1()?"/storage/sdcard":"/storage/external_storage/sdcard");
    private List<ScanResult> wifiList;
	
    String configSSID =  "";
    int configLevel = 60;
    
    int wifiLevel = 0;
    String usb_path = "";
    LinearLayout mLeftLayout, mBottomLayout ,mBottomLayout2,mBottomLayout3
    ,mBottomLayout4,mBottomLayout5;
    String configFile = "";
    int tag_net = 0;
    int tag_power = 0;
    AudioManager mAudioManager = null;
    int maxVolume;
    int currentVolume;
    String lssue_value = "";
    String client_value = "";
    String readMac = "";
    String readSn = "";
    String readDeviceid = "";
    
    private boolean bIsKeyDown = false;
    //系统灯和网络灯测试时间 单位s
    int ledtime = 60;
	//videoview 全屏播放时间
    private final long  MSG_PLAY_VIDEO_TIME= 30 * 60 * 1000;

    private Context mContext;
	private BTDeviceReceiver mBTDeviceReceiver;
	private int CONFIG_BT_RSSI = -100;
	private boolean BT_ERR =true;
	private int BT_try_count = 1;
	private int btLevel = 0;
	private final String BTSSID="Khadas";
    String[] usbStatus = new String[4];
	private BTAdmin localBTAdmin;

    
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
		mContext = this;
        mAudioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);    
        //最大音量    
        maxVolume = mAudioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC);    
        //当前音量    
        currentVolume = mAudioManager.getStreamVolume(AudioManager.STREAM_MUSIC); 
        //进入产测apk设置最大音量
        mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, maxVolume, 0); 
        
        m_firmware_version = (TextView)findViewById(R.id.firmware_version_value);
        m_device_type = (TextView)findViewById(R.id.device_type_value);
        m_macvalue = (TextView)findViewById(R.id.mac_value);
        m_snvalue = (TextView)findViewById(R.id.sn_value);
        m_device_id = (TextView)findViewById(R.id.device_id_value);
        
        m_ip = (TextView)findViewById(R.id.ip_value);
        m_wifiip = (TextView)findViewById(R.id.wifi_ip_value);
        m_wifimac = (TextView)findViewById(R.id.wifi_mac_value);
        m_nand_size = (TextView)findViewById(R.id.nand_size_value);
        m_ddr_size = (TextView)findViewById(R.id.ddr_size_value);
        
        m_TextView_Time = (TextView)findViewById(R.id.TextView_Time);
        m_TextView_TF = (TextView)findViewById(R.id.TextView_TF);
        m_TextView_USB1 = (TextView)findViewById(R.id.TextView_USB1);
        m_TextView_USB2 = (TextView)findViewById(R.id.TextView_USB2);

        m_TextView_Gsensor = (TextView)findViewById(R.id.TextView_Gsensor);
        m_TextView_Gyro = (TextView)findViewById(R.id.TextView_Gyro);
        m_TextView_Gesture = (TextView)findViewById(R.id.TextView_Gesture);
        m_TextView_Pcie = (TextView)findViewById(R.id.TextView_Pcie);
        m_Button_EnableWol = (Button)findViewById(R.id.EnableWol);
		m_Button_Restore_MCU_settings = (Button)findViewById(R.id.Restore_MCU_settings);
		m_Button_speaker_MIC = (Button)findViewById(R.id.speaker_MIC);
        m_Button_DisableWol = (Button)findViewById(R.id.DisableWol);
        m_TextView_Lan = (TextView)findViewById(R.id.TextView_Lan);
        m_TextView_MCU = (TextView)findViewById(R.id.TextView_MCU);
        m_TextView_SPIFLASH = (TextView)findViewById(R.id.TextView_SPIFLASH);
        m_TextView_Gigabit_network = (TextView)findViewById(R.id.TextView_Gigabit_network);	
		m_TextView_HDMI = (TextView)findViewById(R.id.TextView_HDMI);	
        m_TextView_PD12 = (TextView)findViewById(R.id.TextView_PD12);
		m_TextView_Charge = (TextView)findViewById(R.id.TextView_Charge);			
        m_TextView_AGEING = (TextView)findViewById(R.id.TextView_AGEING);
        m_TextView_Wifi = (TextView)findViewById(R.id.TextView_Wifi);
		m_TextView_BT = (TextView)findViewById(R.id.TextView_BT);
		m_TextView_Rtc = (TextView)findViewById(R.id.TextView_Rtc);
		Log.d(TAG, "hlm Tools.getBoardType(): " + Tools.getBoardType());
        if (Tools.getBoardType() == Tools.KHADAS_EDGE) {
            m_TextView_Gsensor.setVisibility(View.GONE);
            m_TextView_Gyro.setVisibility(View.GONE);
            m_TextView_Gesture.setVisibility(View.GONE);
            m_TextView_Pcie.setVisibility(View.GONE);
            m_Button_EnableWol.setVisibility(View.GONE);
			m_Button_Restore_MCU_settings.setVisibility(View.GONE);
			m_Button_speaker_MIC.setVisibility(View.GONE);
            m_Button_DisableWol.setVisibility(View.GONE);
			m_TextView_TF.setVisibility(View.GONE);
			m_TextView_Lan.setVisibility(View.GONE);
			m_TextView_Gigabit_network.setVisibility(View.GONE);	
			m_TextView_Charge.setVisibility(View.GONE);			
        }	
		if (Tools.getBoardType() == Tools.KHADAS_EDGEV) {	
			m_Button_speaker_MIC.setVisibility(View.GONE);
		}
		if(DISABLED_RTC) {
	    m_TextView_Rtc.setVisibility(View.GONE);
		}

		if(DISABLED_USB2) {
			m_TextView_USB2.setVisibility(View.GONE);
		}

		if(DISABLED_DEVICE_ID) {
			m_device_id.setVisibility(View.GONE);
		}

		if(DISABLED_SN) {
			m_snvalue.setVisibility(View.GONE);
		}
        
        m_maccheck = (EditText)findViewById(R.id.EditTextMac); 
        m_maccheck.setInputType(InputType.TYPE_NULL);
        m_maccheck.addTextChangedListener(mTextWatcher);
        m_mactitle = (TextView)findViewById(R.id.MacTitle);
        
        m_Button_write_mac_usid = (Button)findViewById(R.id.Button_Writemac);

		if(DISABLED_WRITE_MAC) {
			m_Button_write_mac_usid.setVisibility(View.GONE);
		}
        m_Button_PowerLed = (Button)findViewById(R.id.Button_PowerLed);
        m_Button_NetLed = (Button)findViewById(R.id.Button_NetLed);
		if(DISABLED_POWER_LED) {
        m_Button_PowerLed.setVisibility(View.GONE);
		}

		m_Button_Key = (Button)findViewById(R.id.KeyTest);
		if(DISABLED_KEY && Tools.getBoardType() != Tools.KHADAS_CAPTAIN) {
        m_Button_Key.setVisibility(View.GONE);
		}
        

        mLeftLayout = (LinearLayout) findViewById(R.id.Layout_Left);
        mBottomLayout = (LinearLayout) findViewById(R.id.Layout_Bottom);
        mBottomLayout2 = (LinearLayout) findViewById(R.id.Layout_Bottom2);
        mBottomLayout3 = (LinearLayout) findViewById(R.id.Layout_Bottom3);
        mBottomLayout4 = (LinearLayout) findViewById(R.id.Layout_Bottom4);
        mBottomLayout5 = (LinearLayout) findViewById(R.id.Layout_Bottom5);
		
		mWifiManager = (WifiManager) getSystemService(Context.WIFI_SERVICE);
		mWifiManager.setWifiEnabled(true);
		
        updateTime();
        new Thread() {
            public void run() {
                test_Thread();
            }
        }.start();
        new Thread() {
            public void run() {
		while(true) {
			try {
				
				if(0 == FactoryReceiver.ageing_flag){
					Tools.writeFile(Tools.White_Led,"default-on");
					Tools.writeFile(Tools.Red_Led,"default-on");
					Thread.sleep(1000);
					Tools.writeFile(Tools.White_Led, "off");
					Tools.writeFile(Tools.Red_Led, "off");
					Thread.sleep(1000);
					test_Gigabit_network();
					test_HDMI();
					test_volumes();
					test_ETH();
				}
				else
					test_cpu_ageing();
				if(2 == VideoFragment.ageing_test_step && !ageing_test_ok_flag){
					mHandler.sendEmptyMessage(MSG_AGEING_TEST_OK);
					ageing_test_ok_flag = true;
				}
				else if(1 == VideoFragment.ageing_test_step && ageing_test_ok_flag){
					mHandler.sendEmptyMessage(MSG_AGEING_TEST_ERROR);
					ageing_test_ok_flag = false;
				}
				
			}  catch(Exception localException1){

			}
		}
            }
        }.start();
    }
 
 	private String execSuCmd(String cmd) {
		try {
			Process mProcess = Runtime.getRuntime().exec("cmdclient "+cmd);
			BufferedReader mInputReader = new BufferedReader(new InputStreamReader(mProcess.getInputStream()));
			BufferedReader mErrorReader = new BufferedReader(new InputStreamReader(mProcess.getErrorStream()));
			String msg = "";
			String line;
			int i = 0;
			while ((line = mInputReader.readLine()) != null) {
				if(0!=i)
					msg += '\n';
				msg += line;
				i = 1;
			}
			mInputReader.close();

			i = 0;
			while ((line = mErrorReader.readLine()) != null) {
				if(0!=i)
					msg += '\n';
				msg += line;
				i = 1;
			}
			mErrorReader.close();
			mProcess.destroy();
			//Log.d(TAG, msg);
			return msg;
		} catch (IOException e) {
			e.printStackTrace();
			return "execSuCmd Error";
		}
	}
	
    public void test_Thread() {
		test_AGEING();
        mHandler.sendEmptyMessage(MSG_USB1_TEST_XL_ERROR);
        mHandler.sendEmptyMessage(MSG_USB2_TEST_XL_ERROR);	
		//mHandler.sendEmptyMessage(MSG_SPIFLASH_TEST_ERROR);	
		//mHandler.sendEmptyMessage(MSG_GSENSOR_TEST_ERROR);	
		//mHandler.sendEmptyMessage(MSG_GYRO_TEST_ERROR);		
        test_Gsensor();
        test_Gyro();
        test_Gesture();
        test_Pcie();
        test_MCU();
        test_SPIFLASH();
		test_Gigabit_network();
		test_HDMI();
        test_PD();
        test_Charge();	
        test_USB();
        test_volumes();
        test_ETH();
	test_rtc();
        test_BT(true);
        boolean bWifiOk = false;

            for (int i = 0; i < 10; i++) {
                if (test_Wifi()) {
                    bWifiOk = true;
                    break;
                }
            }

		if(bWifiOk){	
			mHandler.sendEmptyMessage(MSG_WIFI_TEST_OK);
        }else{
            mHandler.sendEmptyMessage(MSG_WIFI_TEST_ERROR);
        }

    }
	
private void registerBTReceiver(){

	mBTDeviceReceiver = new BTDeviceReceiver();
	IntentFilter filter=new IntentFilter();
	filter.addAction(BluetoothDevice.ACTION_FOUND);
	filter.addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
	mContext.registerReceiver(mBTDeviceReceiver,filter);
	Log.d(TAG, "registerBTReceiver");
}
private class BTDeviceReceiver extends BroadcastReceiver{

	@Override
	public void onReceive(Context context, Intent intent) {
		String action =intent.getAction();
		if(BluetoothDevice.ACTION_FOUND.equals(action)){
			BluetoothDevice btd=intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
			short rssi = intent.getExtras().getShort(BluetoothDevice.EXTRA_RSSI);
			if(btd!=null){
				String name = btd.getName();
				if(name!=null){
					if(name.equals(BTSSID)){
						if(rssi>CONFIG_BT_RSSI){
							btLevel = -rssi;
							BT_ERR = false;
							//BT_ERR = true;
							//BT_try_count = 1;
							mHandler.sendEmptyMessage(MSG_BT_TEST_OK);
						}else {
                   			BT_ERR = true;
						}
					}
				   Log.d(TAG,"BT Found device name= "+btd.getName()+"rssi = "+rssi);
				}
			}
		}else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {

			  if(BT_ERR){

				  BT_try_count--;
				  if(BT_try_count>0) {
				  test_BT(false);
				  }
				  if(BT_try_count<=0){
                  mHandler.sendEmptyMessage(MSG_BT_TEST_ERROR);
				  }
			  }

		    Log.d(TAG,"BT Found End");
		}
	}
}


private void updateEthandWifi(){
    boolean isEthConnected = NetworkUtils.isEthConnected(this);
    
    if (isEthConnected) {
    	//m_ip.setText(NetworkUtils.getLocalIpAddress(this));
    }else{
    	m_ip.setText(nullip);
    }
    
    WifiManager manager = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
    DhcpInfo dhcpInfo = manager.getDhcpInfo();
    WifiInfo wifiinfo = manager.getConnectionInfo();
    if (wifiinfo != null) {
    	m_wifiip.setText(NetworkUtils.int2ip(wifiinfo.getIpAddress()));
    	m_wifimac.setText(wifiinfo.getMacAddress());
    }else{
    	m_wifiip.setText(nullip);
    	m_wifimac.setText(" ");
    }
}
    
    @Override
    protected void onResume()
    {
        super.onResume();
      //  readVersion();
        
        m_ddr_size.setText((Tools.getmem_TOLAL()*100/1024/1024/100.0)+" GB");
        m_nand_size.setText(Tools.getRomSize(this));
        m_firmware_version.setText(Build.DISPLAY);
        m_device_type.setText(Build.MODEL);
        
        updateEthandWifi();
        
        
        mHandler.sendEmptyMessageDelayed(MSG_PLAY_VIDEO, MSG_PLAY_VIDEO_TIME);
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_TIME_TICK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        registerReceiver(mFactoryReceiver, filter);
        
        IntentFilter mountfilter = new IntentFilter();
        mountfilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
        mountfilter.addDataScheme("file");
        registerReceiver(mountReceiver, mountfilter);
        
		String strMac = Tools.readFile(Tools.Key_OTP_Mac);
		int length = strMac.length();
		if (length != 12) {
			m_macvalue.setTextColor(Color.RED);
			m_macvalue.setText("ERR");
		} else {
			String strTmpMac = "";
			for(int i = 0; i < length; i += 2) {
				strTmpMac += strMac.substring(i, (i + 2) < length ? (i + 2) :  length );
				if( (i + 2) < length) strTmpMac += ':';
			}
			m_macvalue.setTextColor(Color.RED);
			m_macvalue.setText(strTmpMac+" ");
		}
        m_maccheck.requestFocus();
       
    }

    TextWatcher mTextWatcher = new TextWatcher()
    {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count)
        {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int count,
                int after)
        {
        }

        @Override
        public void afterTextChanged(Editable s)
        {          
        	//bIsKeyDown = true;
            mHandler.sendEmptyMessageDelayed(MSG_TIME, 1 * 1000); 
         }  
     };
    
    @Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
    	//退出产测apk恢复系统音量大小
    	if(mAudioManager != null)
    	mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC, currentVolume, 0); 
		super.onDestroy();
	}

	@Override
    protected void onPause() {
        super.onPause();
        mHandler.removeMessages(MSG_NETLED_TEST_Start);
        mHandler.removeMessages(MSG_POWERLED_TEST_Start);
        mHandler.removeMessages(MSG_PLAY_VIDEO);
        unregisterReceiver(mFactoryReceiver);
        unregisterReceiver(mountReceiver);
    }

    public void NetLed_Test(View view){
        Log.e(TAG, "NetLed_Test()");
        m_Button_NetLed.setTag(0);
        mHandler.removeMessages(MSG_NETLED_TEST_Start);
        mHandler.sendEmptyMessage(MSG_NETLED_TEST_Start);
    }

    public void PowerLed_Test(View view){
        Log.e(TAG, "PowerLed_Test()");
        m_Button_PowerLed.setTag(0);
        mHandler.removeMessages(MSG_POWERLED_TEST_Start);
        mHandler.sendEmptyMessage(MSG_POWERLED_TEST_Start);
    }

    public void Write_mac_usid(View view){
    	 Log.e(TAG, "Write_mac_usid()");
    	 m_Button_write_mac_usid.setTag(0);
    	 Intent intent = new Intent(this, WriteMacActivity.class);
    	 startActivity(intent);
    }
    
    public void IRKeyTest(View view){
    	 Log.e(TAG, "IRKeyTest()");
   	 Intent intent = new Intent(this, IRKeyTestActivity.class);
	 startActivity(intent);
    }

	public void EnableWol(View view){
		Log.e(TAG, "EnableWol");
		Tools.writeFile("/sys/class/wol/test", "1");
		Tools.writeFile("/sys/class/wol/enable", "1");
	}

	public void Restore_MCU_settings(View view){
		Log.e(TAG, "hlm Restore_MCU_settings");
		Tools.writeFile("/sys/class/wol/rst_mcu", "0");
	}

	public void speaker_MIC(View view){
		Log.e(TAG, "hlm MIC");
		Intent intent = new Intent(this, PhoneMicTestActivity.class);
		startActivity(intent);
		Log.e(TAG, "buzzer");
        Tools.writeFile("/sys/class/w25q128fw/buzzer", "1");
		
	}
		
	public void DisableWol(View view){
		Log.e(TAG, "DisableWol");
                Tools.writeFile("/sys/class/wol/test", "0");
		Tools.writeFile("/sys/class/wol/enable", "0");
	}

   public void KeyTest(View view){
         Log.e(TAG, "hlm KeyTest()");
		 Intent intent = new Intent(this, IRKeyTestActivity.class);
		 startActivity(intent);
  }

   private void test_BT(boolean delay){
         
	  localBTAdmin = new BTAdmin();
	  registerBTReceiver();
	  localBTAdmin.OpenBT();
	  if (delay) {
		try {
		  Thread.sleep(3000);
		}catch(Exception localException1){
		}
	 }
	  if(!localBTAdmin.ScanBT()){
       mHandler.sendEmptyMessage(MSG_BT_TEST_ERROR);
	  }
   }

  private void test_USB() {

        String pathname2 = "/sys/class/w25q128fw/usb2";
		try (FileReader reader2 = new FileReader(pathname2);
			 BufferedReader br2 = new BufferedReader(reader2)) {
			String line2;
			while ((line2 = br2.readLine()) != null) {
				int usb2 = Integer.parseInt(line2);
				Log.d(TAG, "hlm usb2: " + usb2);
				if(1==usb2){
					mHandler.sendEmptyMessage(MSG_USB1_TEST_OK);	
				}	
				else
					mHandler.sendEmptyMessage(MSG_USB1_TEST_ERROR);	
			}		
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		
        String pathname3 = "/sys/class/w25q128fw/usb3";
		try (FileReader reader3 = new FileReader(pathname3);
			 BufferedReader br3 = new BufferedReader(reader3)) {
			String line3;
			while ((line3 = br3.readLine()) != null) {
				int usb3 = Integer.parseInt(line3);
				Log.d(TAG, "hlm usb3: " + usb3);
				if(1==usb3){
					mHandler.sendEmptyMessage(MSG_USB2_TEST_OK);	
				}	
				else
					mHandler.sendEmptyMessage(MSG_USB2_TEST_ERROR);	
			}		
		} catch (IOException e) {
			e.printStackTrace();
		}				
  }

    private void test_PD() {
		int usb0,usb1,usb2;
        String msg = execSuCmd("i2cget -f -y 4 0x22 1");
		if (msg.contains("failed") || msg.contains("Error")) {
			usb1=0;
		}else{
			usb1=2;
		}

        String msg0 = execSuCmd("i2cget -f -y 8 0x22 1");
		if (msg0.contains("failed") || msg0.contains("Error")) {
			usb0=0;
		}else{
			usb0=1;
		}
		
		usb2 = usb1|usb0;
		Log.d(TAG, "hlm fusb302: " + usb2);
		if(3 == usb2){
			mHandler.sendEmptyMessage(MSG_PD12_TEST_OK);	
		}
		else if(2 == usb2){
			mHandler.sendEmptyMessage(MSG_PD1_TEST_ERROR);	
		}
		else if(1 == usb2){
			mHandler.sendEmptyMessage(MSG_PD2_TEST_ERROR);	
		}
		else{
			mHandler.sendEmptyMessage(MSG_PD12_TEST_ERROR);	
		}
  }

  private void test_Charge() {
        String msg = execSuCmd("i2cget -f -y 8 0x6b 0x6");
		if (msg.contains("failed") || msg.contains("Error")) {
			mHandler.sendEmptyMessage(MSG_Charge_TEST_ERROR);
		}else{
			mHandler.sendEmptyMessage(MSG_Charge_TEST_OK);
		}		
  }
  
  private void test_MCU() {
        String msg = execSuCmd("i2cget -f -y 8 0x18 0x6");
		if (msg.contains("failed") || msg.contains("Error")) {
			mHandler.sendEmptyMessage(MSG_MCU_TEST_ERROR);
		}else{
			mHandler.sendEmptyMessage(MSG_MCU_TEST_OK);
		}
  }

  private void test_SPIFLASH() {

        String pathname = "/sys/class/w25q128fw/id";
		try (FileReader reader = new FileReader(pathname);
			 BufferedReader br = new BufferedReader(reader)) {
			String line;
			while ((line = br.readLine()) != null) {
				int id = Integer.parseInt(line);
				Log.d(TAG, "hlm w25q128fw: " + id);
				if(1==id){
					mHandler.sendEmptyMessage(MSG_SPIFLASH_TEST_OK);	
					return;
				}					
			}		
		} catch (IOException e) {
			e.printStackTrace();
		}
		mHandler.sendEmptyMessage(MSG_SPIFLASH_TEST_ERROR);			
  }
 
   private void test_Gigabit_network() {
        String pathname = "/sys/class/net/eth0/speed";
		try (FileReader reader = new FileReader(pathname);
			 BufferedReader br = new BufferedReader(reader)) {
			String line;
			while ((line = br.readLine()) != null) {
				int speed = Integer.parseInt(line);
				Log.d(TAG, "hlm net speed: " + speed);
				if(1000==speed){
					mHandler.sendEmptyMessage(MSG_Gigabit_network_TEST_OK);	
					return;
				}	
				if(100==speed){
					mHandler.sendEmptyMessage(MSG_Gigabit_network_TEST_OK);	
					return;
				}				
			}		
		} catch (IOException e) {
			e.printStackTrace();
		}
		mHandler.sendEmptyMessage(MSG_Gigabit_network_TEST_ERROR);	
  }
 
   private void test_HDMI() {
		
        String pathname = "/sys/devices/platform/display-subsystem/drm/card0/card0-HDMI-A-1/edid";
		try (FileReader reader = new FileReader(pathname);
			 BufferedReader br = new BufferedReader(reader)) {
			String line;
			if ((line = br.readLine()) != null) {
				Log.d(TAG, "hlm edid="+line);
				mHandler.sendEmptyMessage(MSG_HDMI_TEST_OK);	
				return;
			}		
		} catch (IOException e) {
			e.printStackTrace();
		}
		mHandler.sendEmptyMessage(MSG_HDMI_TEST_ERROR);			
		
  }

  private void test_AGEING() {
      String pathname = "/sys/class/wol/ageing_test";
	try (FileReader reader = new FileReader(pathname);
		 BufferedReader br = new BufferedReader(reader)) {
		String line;
		while ((line = br.readLine()) != null) {
			int id = Integer.parseInt(line);
			Log.d(TAG, "hlm AGEING: " + id);
			if(1==id){
				ageing_test_ok_flag = true;
				mHandler.sendEmptyMessage(MSG_AGEING_TEST_OK);	
				return;
			}					
		}		
	} catch (IOException e) {
		e.printStackTrace();
	}
	ageing_test_ok_flag = false;
	if(0 == FactoryReceiver.ageing_flag)
		m_TextView_AGEING.setVisibility(View.GONE);
	else
		mHandler.sendEmptyMessage(MSG_AGEING_TEST_ERROR);
  }  
  
   private List<File> get_input_list(String path) {
        int fileNum = 0;
	File file = new File(path);
        List<File> list = new ArrayList<File>();
        if (file.exists()) {
           File[] files = file.listFiles();
           for (File file2 : files) {
              String name = file2.getName().substring(0,5);;
              Log.d(TAG, "get name="+name);
              if (name.equals("input")) 
              list.add(file2);
           }
        }  
        return list;
   }

   private void test_Gsensor() {
        List<File> list = get_input_list("/sys/class/input");
        if (list != null) {
           int size = list.size();
           for (int i = 0; i< size; i++) {
               String file = list.get(i).getAbsolutePath()+"/name";
               String name = Tools.readFile(file);
               if (name.equals("gsensor")) {
                   mHandler.sendEmptyMessage(MSG_GSENSOR_TEST_OK);
                   return;
               }
           }
        }
        mHandler.sendEmptyMessage(MSG_GSENSOR_TEST_ERROR);
   }

   private void test_Gyro() {
        List<File> list = get_input_list("/sys/class/input");
        if (list != null) {
           int size = list.size();
           for (int i = 0; i< size; i++) {
               String file = list.get(i).getAbsolutePath()+"/name";
               String name = Tools.readFile(file);
               if (name.equals("gyro")) {
                   mHandler.sendEmptyMessage(MSG_GYRO_TEST_OK);
                   return;
               }
           }
        }
        mHandler.sendEmptyMessage(MSG_GYRO_TEST_ERROR);
   }

   private void test_Gesture() {
        String msg = execSuCmd("i2cget -f -y 8 0x39 0");
		if (msg.contains("failed") || msg.contains("Error")) {
			mHandler.sendEmptyMessage(MSG_GESTURE_TEST_ERROR);
		}else{
			mHandler.sendEmptyMessage(MSG_GESTURE_TEST_OK);
		} 
   }
	
   private void test_Pcie() {
        File file = new File("/dev/nvme0");
        if (file.exists())
            mHandler.sendEmptyMessage(MSG_PCIE_TEST_OK);
        else
            mHandler.sendEmptyMessage(MSG_PCIE_TEST_ERROR);
   }

    private void test_cpu_ageing() {

        String shpath = copyAssetGetFilePath("test_cpu_ageing.sh");

        Log.d(TAG, "===shpath====" + shpath);

        File file = new File(shpath);
        if (file.exists()) {
            try {
                Tools.execCommand(new String[]{"sh", "-c", "chmod 777 " + shpath});
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        for (int i = 0; i < 8; i++) {
            try {
                Process ps = Runtime.getRuntime().exec(shpath);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private String copyAssetGetFilePath(String fileName) {
        try {
            File cacheDir = mContext.getCacheDir();
            if (!cacheDir.exists()) {
                cacheDir.mkdirs();
            }
            File outFile = new File(cacheDir, fileName);
            if (!outFile.exists()) {
                boolean res = outFile.createNewFile();
                if (!res) {
                    return null;
                }
            } else {
                if (outFile.length() > 10) {
                    return outFile.getPath();
                }
            }
            InputStream is = mContext.getAssets().open(fileName);
            FileOutputStream fos = new FileOutputStream(outFile);
            byte[] buffer = new byte[1024];
            int byteCount;
            while ((byteCount = is.read(buffer)) != -1) {
                fos.write(buffer, 0, byteCount);
            }
            fos.flush();
            is.close();
            fos.close();
            return outFile.getPath();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }
	
    private boolean test_Wifi()
    {

        boolean bWifiScaned = false;
                configSSID =  getResources().getString(R.string.config_ap_ssid);
                WifiAdmin  localWifiAdmin  =  new WifiAdmin (this);
                localWifiAdmin.openWifi();
                try {
                    Thread.sleep(3000);
                }
                catch(Exception localException1)
                {
                }
                localWifiAdmin.startScan();
                wifiList = new ArrayList<ScanResult>();
                
                wifiList = localWifiAdmin.getWifiList();
               
                Log.d(TAG, "wifi size: " + wifiList.size());
                if (wifiList != null) {
                    for (ScanResult result : wifiList) {
                        if(result.SSID.equals(configSSID)){
                            wifiLevel = WifiManager.calculateSignalLevel(result.level, 100);
                            Log.d(TAG, "wifiLevel: " + wifiLevel);
                            if(wifiLevel >= configLevel)
                            {
                                bWifiScaned = true;
                            }
                        }
                    }

                 }
//        boolean bWifiScaned = false;
//        WifiAdmin  localWifiAdmin  =  new WifiAdmin (this);
//        localWifiAdmin.openWifi();
//        localWifiAdmin.startScan();
//        List<ScanResult> wifiList = localWifiAdmin.getWifiList();
//
//        if (wifiList != null) {
//            for (ScanResult result : wifiList) {
//                if(result.SSID.equals(configSSID)){
//                    wifiLevel = WifiManager.calculateSignalLevel(result.level, 100);
//                    if(wifiLevel >= configLevel)
//                    {
//                        bWifiScaned = true;
//                    }
//                }
//            }
//        }


        return bWifiScaned;
    }

	
	/**
	 * 判断USB与F
	 */
	private void test_volumes() {
		
		if(Tools.isAndroid5_1_1()){
			test_android5_1();
		}else{
			test_android6_0();
		}
	}

   private void test_rtc() {
      
//	   String time = Tools.readFile(Tools.Rtc_time);

   }

	/**
	 * android6.0 
	 */
	private void test_android6_0() {
		Log.d(TAG, "----- android6.0 -----");
		mHandler.sendEmptyMessage(MSG_android_6_0_TEXT_LAYOUT);
		Boolean[] usbOrSd = Tools.isUsbOrSd(MainActivity.this);
		int usb3_0_flag = 0;
		
		
		if(usbOrSd[0]){
			mHandler.sendEmptyMessage(MSG_TF_TEST_XL_OK);
		}else{
			mHandler.sendEmptyMessage(MSG_TF_TEST_XL_ERROR);
		}
		
        for (int i=0; i< usbStatus.length; i++) {
            usbStatus[i] = getResources().getString(R.string.Test_Fail);
        }

		String val = Tools.readFile("/sys/kernel/debug/usb/devices");
		if (val.indexOf("(O)") == -1) {
			Log.e(TAG, "=========USB2.0 and USB3.0 is bad");
			mHandler.sendEmptyMessage(MSG_USB1_TEST_XL_ERROR);
			mHandler.sendEmptyMessage(MSG_USB2_TEST_XL_ERROR);
			return;
		}

		int length;
		String[] list = val.split("T:|B:|D:|P:|S:|C:|I:|E:");
		int num = -1;
		int count = getSubCount(val, "Bus=");
		String[] tmp = new String[count];
		for (int z=0; z< list.length; z++) {

			if (list[z].indexOf("Bus=") != -1) {
				num++;
			}
			if (num == count)
				break;
			if (num == -1)
				continue;
			tmp[num] = tmp[num] + list[z];
		}
		for (int i=0; i< tmp.length; i++) {
			if ((tmp[i].indexOf("(O)") != -1) && ((tmp[i].indexOf("Ver= 3.") != -1))) {
				Log.d(TAG, "USB3.0 is OK");
				usb3_0_flag = 1;
			}else if ((tmp[i].indexOf("(O)") != -1) && (tmp[i].indexOf("Bus=05") != -1) && (tmp[i].indexOf("Lev=01") != -1)) {
				Log.d("TAG", "USB2.0-0 is OK");
                usbStatus[0] = getResources().getString(R.string.Test_Ok);
			}
		}
		if(usbStatus[0].equals(getResources().getString(R.string.Test_Ok))) {
            mHandler.sendEmptyMessage(MSG_USB1_TEST_XL_OK);
        }else{
            mHandler.sendEmptyMessage(MSG_USB1_TEST_XL_ERROR);
        }
		
		if(1 == usb3_0_flag)
			mHandler.sendEmptyMessage(MSG_USB2_TEST_XL_OK);
		else
			mHandler.sendEmptyMessage(MSG_USB2_TEST_XL_ERROR);
    }
	

    private  int getSubCount(String str, String key) {
        int count = 0;
        int index = 0;
        while ((index = str.indexOf(key, index)) != -1) {
             index = index + key.length();
             count++;
        }
        return count;
    }
	
    private void test_android5_1(){
		Log.d(TAG, "----- android5.1 -----");
        List<String> volumes = getVolumes();
        boolean bSdcard = false;
        boolean bSda = false;
        boolean bSdb = false;
        for(String volume : volumes){
            if(volume.contains(TFCARD_PATH)){
                bSdcard = true;
            }else if(volume.contains(USB1_PATH)){
            	Log.d(TAG, USB1_PATH + " usb1 "+volume.toString());
                usb_path = volume;
                bSda = true;
            }else if(volume.contains(USB2_PATH)){
            	Log.d(TAG, USB2_PATH + " usb2 "+ volume.toString());
                bSdb = true;
            }
        }
        if(bSdcard)
        {
            mHandler.sendEmptyMessage(MSG_TF_TEST_OK);
        }
        else
        {
            mHandler.sendEmptyMessage(MSG_TF_TEST_ERROR);
        }
        
    }
    private List<String> getVolumes(){
        List<String> volumes = new ArrayList<String>();
        try{
            BufferedReader bufferReader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("df").getInputStream()));
            String readline;
            while ((readline = bufferReader.readLine()) != null) {
                Log.d(TAG, "df State:" + readline);
                if(readline.contains(USB_PATH) || readline.contains(TFCARD_PATH)){
                    String[] result = readline.split(" ");
                    if(result.length > 0){
                        volumes.add(result[0]);
                    }
                }
            }
        } catch (FileNotFoundException e){
            return volumes;
        } catch (IOException e){
            return volumes;
        }
        return volumes;
    }
    //仅仅在一个U盘接入情况下判断接入那个USB口
    private boolean isUsb1(){
		try {
		   BufferedReader bufferReader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("lsusb").getInputStream()));
		   String readline = bufferReader.readLine();
		   //Bus 001 Device 008: ID 05e3:0723
		   String USBBus = readline.substring(readline.indexOf("00")+2, readline.lastIndexOf("Device")).trim();
			 Log.d(TAG, "lsusb :  " + USBBus);
			 if(USBBus.equals("1")){
				 Log.d(TAG, "lsusb :  is USB1 mount");
				 return true;
			 }
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
        return false;
    }


    private void test_ETH()
    {
		boolean isEthConnected = NetworkUtils.isEthConnected(this);
        if(Tools.isEthUp())
        {
            mHandler.sendEmptyMessage(MSG_LAN_TEST_OK);
        }
        else
        {
			if(isEthConnected) {
              mHandler.sendEmptyMessage(MSG_LAN_TEST_OK);
			}else {
            mHandler.sendEmptyMessage(MSG_LAN_TEST_ERROR);
			}
        }

		Log.d(TAG,"ETH state: "+Tools.isEthUp());
    }

    class FactoryHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case  MSG_TF_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.TF_Test) + "    " + getResources().getString(R.string.Test_Fail);
                    m_TextView_TF.setText(strTxt);
                    m_TextView_TF.setTextColor(0xFFFF5555);
                }
                break;

                case  MSG_TF_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.TF_Test) + "    " + getResources().getString(R.string.Test_Ok);
                    m_TextView_TF.setText(strTxt);
                    m_TextView_TF.setTextColor(0xFF55FF55);
                }
                break;

                case  MSG_USB1_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.USB1_Test) + "    " + getResources().getString(R.string.Test_Fail);
                    m_TextView_USB1.setText(strTxt);
                    m_TextView_USB1.setTextColor(0xFFFF5555);
                }
                break;

                case  MSG_USB1_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.USB1_Test) + "    " + getResources().getString(R.string.Test_Ok);
                    m_TextView_USB1.setText(strTxt);
                    m_TextView_USB1.setTextColor(0xFF55FF55);
                }
                break;

                case  MSG_USB2_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.USB2_Test) + "    " + getResources().getString(R.string.Test_Fail);
                    m_TextView_USB2.setText(strTxt);
                    m_TextView_USB2.setTextColor(0xFFFF5555);
                }
                break;

                case  MSG_USB2_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.USB2_Test) + "    " + getResources().getString(R.string.Test_Ok);
                    m_TextView_USB2.setText(strTxt);
                    m_TextView_USB2.setTextColor(0xFF55FF55);
                }
                break;

                case  MSG_LAN_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.Lan_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_Lan.setText(strTxt);
                    m_TextView_Lan.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_LAN_TEST_OK");
                }
                break;

                case  MSG_LAN_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.Lan_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_Lan.setText(strTxt);
                    m_TextView_Lan.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_LAN_TEST_ERROR");
                }
                break;

                case  MSG_HDMI_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.HDMI_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_HDMI.setText(strTxt);
                    m_TextView_HDMI.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_HDMI_TEST_OK");
                }
                break;

                case  MSG_HDMI_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.HDMI_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_HDMI.setText(strTxt);
                    m_TextView_HDMI.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_HDMI_TEST_ERROR");
                }
                break;
                case  MSG_AGEING_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.AGEING_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_AGEING.setText(strTxt);
                    m_TextView_AGEING.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_AGEING_TEST_OK");
                }
                break;

                case  MSG_AGEING_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.AGEING_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_AGEING.setText(strTxt);
                    m_TextView_AGEING.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_AGEING_TEST_ERROR");
                }
                break;
                case  MSG_MCU_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.MCU_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_MCU.setText(strTxt);
                    m_TextView_MCU.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_MCU_TEST_OK");
                }
                break;

                case  MSG_MCU_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.MCU_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_MCU.setText(strTxt);
                    m_TextView_MCU.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_MCU_TEST_ERROR");
                }
                break;

                case  MSG_SPIFLASH_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.SPIFLASH_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_SPIFLASH.setText(strTxt);
                    m_TextView_SPIFLASH.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_SPIFLASH_TEST_OK");
                }
                break;

                case  MSG_SPIFLASH_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.SPIFLASH_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_SPIFLASH.setText(strTxt);
                    m_TextView_SPIFLASH.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_SPIFLASH_TEST_ERROR");
                }
                break;

                case  MSG_Gigabit_network_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.Gigabit_network_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_Gigabit_network.setText(strTxt);
                    m_TextView_Gigabit_network.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_Gigabit_network_TEST_OK");
                }
                break;

                case  MSG_Gigabit_network_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.Gigabit_network_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_Gigabit_network.setText(strTxt);
                    m_TextView_Gigabit_network.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_Gigabit_network_TEST_ERROR");
                }
                break;

                case  MSG_PD12_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.PD12_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_PD12.setText(strTxt);
                    m_TextView_PD12.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_PD12_TEST_OK");
                }
                break;

                case  MSG_PD12_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.PD12_Test) + "    " + getResources().getString(R.string.PD12_Test_Fail);

                    m_TextView_PD12.setText(strTxt);
                    m_TextView_PD12.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_PD12_TEST_ERROR");
                }
                break;
				
                case  MSG_PD1_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.PD12_Test) + "    " + getResources().getString(R.string.PD1_Test_Fail);

                    m_TextView_PD12.setText(strTxt);
                    m_TextView_PD12.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_PD1_TEST_ERROR");
                }
                break;

                case  MSG_PD2_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.PD12_Test) + "    " + getResources().getString(R.string.PD2_Test_Fail);

                    m_TextView_PD12.setText(strTxt);
                    m_TextView_PD12.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_PD2_TEST_ERROR");
                }
                break;
				
                case  MSG_Charge_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.Charge_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_Charge.setText(strTxt);
                    m_TextView_Charge.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_ChargeTEST_OK");
                }
                break;

                case  MSG_Charge_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.Charge_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_Charge.setText(strTxt);
                    m_TextView_Charge.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_Charge_TEST_ERROR");
                }
                break;												
                case  MSG_GSENSOR_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.Gsensor_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_Gsensor.setText(strTxt);
                    m_TextView_Gsensor.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_GSENSOR_TEST_OK");
                }
                break;

                case  MSG_GSENSOR_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.Gsensor_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_Gsensor.setText(strTxt);
                    m_TextView_Gsensor.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_GSENSOR_TEST_ERROR");
                }
                break;
                case  MSG_GYRO_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.Gyro_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_Gyro.setText(strTxt);
                    m_TextView_Gyro.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_GYRO_TEST_OK");
                }
                break;

                case  MSG_GYRO_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.Gyro_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_Gyro.setText(strTxt);
                    m_TextView_Gyro.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_GYRO_TEST_ERROR");
                }
                break;

                case  MSG_GESTURE_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.Gesture_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_Gesture.setText(strTxt);
                    m_TextView_Gesture.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_GESTURE_TEST_OK");
                }
                break;

                case  MSG_GESTURE_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.Gesture_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_Gesture.setText(strTxt);
                    m_TextView_Gesture.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_GESTURE_TEST_ERROR");
                }
                break;
                case  MSG_PCIE_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.Pcie_Test) + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_Pcie.setText(strTxt);
                    m_TextView_Pcie.setTextColor(0xFF55FF55);
					Log.d(TAG,"MSG_PCIE_TEST_OK");
                }
                break;

                case  MSG_PCIE_TEST_ERROR:
                {
                    String strTxt = getResources().getString(R.string.Pcie_Test) + "    " + getResources().getString(R.string.Test_Fail);

                    m_TextView_Pcie.setText(strTxt);
                    m_TextView_Pcie.setTextColor(0xFFFF5555);
					Log.d(TAG,"MSG_PCIE_TEST_ERROR");
                }
                break;
                case MSG_WIFI_TEST_OK:
                {
                    String strTxt = getResources().getString(R.string.Wifi_Test) + "    " + configSSID + "    " + wifiLevel + "    " + getResources().getString(R.string.Test_Ok);

                    m_TextView_Wifi.setText(strTxt);
                    m_TextView_Wifi.setTextColor(0xFF55FF55);
                }
                break;

                case MSG_WIFI_TEST_ERROR:
                {
                    String  strTxt = getResources().getString(R.string.Wifi_Test) + "    " + getResources().getString(R.string.Test_Fail);
                    m_TextView_Wifi.setText(strTxt);
                    m_TextView_Wifi.setTextColor(0xFFFF5555);
                }
                break;
				case MSG_BT_TEST_OK:
				{	 String strTxt = getResources().getString(R.string.BT_Test) +"    " + BTSSID + "    "+ btLevel+"    " + getResources().getString(R.string.Test_Ok);
					 m_TextView_BT.setText(strTxt);
					 m_TextView_BT.setTextColor(0xFF55FF55);
				}
					 localBTAdmin.CloseBT();
					 break;
                case MSG_BT_TEST_ERROR:
				{
					String strTxt = getResources().getString(R.string.BT_Test) + "    " + getResources().getString(R.string.Test_Fail);
					m_TextView_BT.setText(strTxt);
					m_TextView_BT.setTextColor(0xFFFF5555);
				}
				localBTAdmin.CloseBT();
				break;
				case MSG_RTC_TEST_OK:
				{
                   String  strTxt = getResources().getString(R.string.Rtc_Test) + "    " + getResources().getString(R.string.Test_Ok);
				   m_TextView_Rtc.setText(strTxt);
				   m_TextView_Rtc.setTextColor(0xFF55FF55);

			    }
				break;
			    case MSG_RTC_TEST_ERROR:
				{
					 String  strTxt = getResources().getString(R.string.Rtc_Test) + "    " + getResources().getString(R.string.Test_Fail);
					 m_TextView_Rtc.setText(strTxt);
					  m_TextView_Rtc.setTextColor(0xFFFF5555);

				}
				break;
                case MSG_NETLED_TEST_Start:
                    tag_net ++;
                    if(tag_net > ledtime){
                   	 mHandler.removeMessages(MSG_NETLED_TEST_Start);
                   	 mHandler.sendEmptyMessage(MSG_NETLED_TEST_End);
                   	 return ;
                   } 
                    Log.d(TAG, "MSG_NETLED_TEST_Start: " + tag_net);
                    if(tag_net % 2 == 1 ){
                    	m_Button_NetLed.setText(getResources().getString(R.string.Led_TestIng)+"!");
                        Tools.writeFile(Tools.Ethernet_Led,"off");
                        mHandler.removeMessages(MSG_NETLED_TEST_Start);
                        mHandler.sendEmptyMessageDelayed(MSG_NETLED_TEST_Start, 1000);
                    }else if(tag_net % 2 == 0){
                    	 m_Button_NetLed.setText(getResources().getString(R.string.Led_TestIng)+"!!");
                        Tools.writeFile(Tools.Ethernet_Led,"default-on");
                        mHandler.removeMessages(MSG_NETLED_TEST_Start);
                        mHandler.sendEmptyMessageDelayed(MSG_NETLED_TEST_Start, 1000);
                    }
                    break;
                case MSG_NETLED_TEST_End:
                    tag_net = 0;
                    m_Button_NetLed.setText(getResources().getString(R.string.Led_Test));
                    if(Tools.isNetworkAvailable(MainActivity.this)){
                        Tools.writeFile(Tools.Ethernet_Led,"on");
                    }else{
                        Tools.writeFile(Tools.Ethernet_Led,"default-on");
                    }
                    break;
                case MSG_POWERLED_TEST_Start:
                    tag_power ++;
                    if(tag_power > ledtime){
                    	 mHandler.removeMessages(MSG_POWERLED_TEST_Start);
                    	 mHandler.sendEmptyMessage(MSG_POWERLED_TEST_End);
                    	 return ;
                    } 
                    Log.d(TAG, "MSG_POWERLED_TEST_Start: " + tag_power);
                    if(tag_power % 2 == 1){
                    	m_Button_PowerLed.setText(getResources().getString(R.string.PowerKey_TestIng)+"!");
                        Tools.writeFile(Tools.Power_Led,"off");
                        mHandler.removeMessages(MSG_POWERLED_TEST_Start);
                        mHandler.sendEmptyMessageDelayed(MSG_POWERLED_TEST_Start, 1000);
                    }else if(tag_power % 2 == 0){
                    	m_Button_PowerLed.setText(getResources().getString(R.string.PowerKey_TestIng)+"!!");
                        Tools.writeFile(Tools.Power_Led,"on");
                        mHandler.removeMessages(MSG_POWERLED_TEST_Start);
                        mHandler.sendEmptyMessageDelayed(MSG_POWERLED_TEST_Start, 1000);
                    }
                    break;
                case MSG_POWERLED_TEST_End:
                    tag_power = 0;
                    m_Button_PowerLed.setText(getResources().getString(R.string.PowerKey_Test));
                    Tools.writeFile(Tools.Power_Led,"on");
                    break;
                case MSG_PLAY_VIDEO:
                    mLeftLayout.setVisibility(View.GONE);
                    mBottomLayout.setVisibility(View.GONE);
                    mBottomLayout2.setVisibility(View.GONE);
                    mBottomLayout3.setVisibility(View.GONE);
                    mBottomLayout4.setVisibility(View.GONE);
                    mBottomLayout5.setVisibility(View.GONE);
                    break;
                case MSG_TIME:
                {
                /*    if(bIsKeyDown)
                    {
                        bIsKeyDown = false;
                        mHandler.removeMessages(MSG_TIME);
                        mHandler.sendEmptyMessageDelayed(MSG_TIME, 1 * 1000); 
                    }
                    else*/
                    {
                        mHandler.removeMessages(MSG_TIME);
                        OnScanText();  
                    }
                }
                break;  
				
				case MSG_TF_TEST_XL_ERROR: {
				String strTxt = getResources().getString(R.string.TF_Test)
						+ "    " + getResources().getString(R.string.Test_Fail);
				m_TextView_TF.setText(strTxt);
				m_TextView_TF.setTextColor(0xFFFF5555);
				}
				break;
				
			case MSG_TF_TEST_XL_OK: {
				String strTxt = getResources().getString(R.string.TF_Test)
						+ "    " + getResources().getString(R.string.Test_Ok);
				m_TextView_TF.setText(strTxt);
				m_TextView_TF.setTextColor(0xFF55FF55);
				}
				break;
				
				case MSG_USB1_TEST_XL_ERROR: {
				String strTxt = getResources().getString(R.string.USB1_Test)
						+ "    " + getResources().getString(R.string.Test_Fail);
				m_TextView_USB1.setText(strTxt);
				m_TextView_USB1.setTextColor(0xFFFF5555);
				}
				break;

			case MSG_USB1_TEST_XL_OK: {
				String strTxt = getResources().getString(R.string.USB1_Test)
						+ "    " + getResources().getString(R.string.Test_Ok);
				m_TextView_USB1.setText(strTxt);
				m_TextView_USB1.setTextColor(0xFF55FF55);
				}
				break;
				
			case  MSG_USB2_TEST_XL_ERROR:
            {
                String strTxt = getResources().getString(R.string.USB2_Test) + "    " + getResources().getString(R.string.Test_Fail);
                m_TextView_USB2.setText(strTxt);
                m_TextView_USB2.setTextColor(0xFFFF5555);
            }
            break;

            case  MSG_USB2_TEST_XL_OK:
            {
                String strTxt = getResources().getString(R.string.USB2_Test) + "    " + getResources().getString(R.string.Test_Ok);
                m_TextView_USB2.setText(strTxt);
                m_TextView_USB2.setTextColor(0xFF55FF55);
            }
            break;
				
            }
        }
    }
    
    private void CheckSameMac(String Scanmac){      
        
        if(Scanmac.equalsIgnoreCase(readMac)){
        	
       Toast.makeText(getApplicationContext(),getResources().getString(R.string.testled), Toast.LENGTH_LONG).show();
       m_mactitle.setText(readMac + "   "+ getResources().getString(R.string.the_same_mac));
       m_mactitle.setTextColor(Color.GREEN);
       			Log.e(TAG, "NetLed_Test()");
                m_Button_NetLed.setTag(0);
                mHandler.removeMessages(MSG_NETLED_TEST_Start);
                mHandler.sendEmptyMessage(MSG_NETLED_TEST_Start);

        		Log.e(TAG, "PowerLed_Test()");
                m_Button_PowerLed.setTag(0);
            	mHandler.removeMessages(MSG_POWERLED_TEST_Start);
            	mHandler.sendEmptyMessage(MSG_POWERLED_TEST_Start);
     
      }
      else
      {
    	  m_mactitle.setText(Scanmac+"   "+getResources().getString(R.string.the_diff_mac));
    	  m_mactitle.setTextColor(Color.RED);
    	  m_maccheck.requestFocus();
      }
    }
    
    private void OnScanText(){
    
    	String strMac = m_maccheck.getText().toString();
    	 int nLength = m_maccheck.getText().toString().length();
    	 
    	if(strMac.isEmpty() ) { return; } 
    	m_maccheck.setText("");
    	
        String strTmpMac = "";
        
        if(getResources().getInteger(R.integer.config_mac_length) == nLength)
        {
            for(int i = 0; i < nLength; i += 2)
            {
                strTmpMac += strMac.substring(i, (i + 2) < nLength ? (i + 2) :  nLength );
                                                                                                                                               
                if( (i + 2) < nLength) strTmpMac += ':';
            }
            strMac = strTmpMac;
            CheckSameMac(strMac);
        }
        else if(getResources().getInteger(R.integer.config_mac_length2) == nLength)
        {
             	CheckSameMac(strMac);
        }
        else
        {
        	strTmpMac = "";  
            m_mactitle.setText(strMac+"   "+getResources().getString(R.string.the_diff_mac));
            m_mactitle.setTextColor(Color.RED);
        	m_maccheck.requestFocus();
        }  

    }
	private static final int BAIDU_READ_PHONE_STATE = 100;
	private static WifiManager mWifiManager;
    private BroadcastReceiver mFactoryReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(action.equals(ConnectivityManager.CONNECTIVITY_ACTION)){
                if(Tools.isNetworkAvailable(context)){
				   if(Tools.isEthUp()) {	
                    mHandler.sendEmptyMessage(MSG_LAN_TEST_OK);
                    Tools.writeFile(Tools.Ethernet_Led,"on");
				    }
                }
                updateEthandWifi();
            }else if(action.equals(Intent.ACTION_TIME_TICK) || action.equals(Intent.ACTION_TIME_CHANGED)){
                updateTime();
            }
        }
    };
    
	
    private BroadcastReceiver mountReceiver = new BroadcastReceiver() {

        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Uri uri = intent.getData();
            if(uri.getScheme().equals("file")){
            	if(action.equals(Intent.ACTION_MEDIA_MOUNTED)){
            		String path = uri.getPath();
            		Log.d(TAG,"mFactoryReceiver mount patch is "+path);
                        if(path.contains(USB2_PATH)){
            			List<String> volumes = getVolumes();
            			boolean isUSB1MOUNT = false;
            	        for(String volume : volumes){
            	        	if(volume.contains(USB1_PATH)){
            	        		isUSB1MOUNT = true;
            	            }
            	        }
            		}else if(path.contains(TFCARD_PATH)){
            			mHandler.sendEmptyMessage(MSG_TF_TEST_OK);
        		}            
              }
            }
        }
    };

    private void updateTime() {
        SimpleDateFormat sdf1 = new SimpleDateFormat("yyyy/MM/dd/  E ");
        SimpleDateFormat sdf2 = new SimpleDateFormat("HH:mm");
        m_TextView_Time.setText(sdf1.format(new Date()) + sdf2.format(new Date()));
    }
    private void readVersion() {
    	
    	String lssue = getResources().getString(R.string.lssue_ver);
    	String client = getResources().getString(R.string.client_ver);
    	String verfile = getResources().getString(R.string.versionfile);

        File OutputFile = new File(verfile);
        if(!OutputFile.exists() )
          {
        	Toast.makeText(getApplicationContext(),verfile + getResources().getString(R.string.noexist), Toast.LENGTH_LONG).show();
            	return;
          }
            
            try {
                FileInputStream instream = new FileInputStream(verfile);
                if(instream != null)
                {
                    InputStreamReader inputreader = new InputStreamReader(instream);
                    BufferedReader buffreader = new BufferedReader(inputreader);
                    
                    Log.d(TAG, "buffreader = " + buffreader.toString());
                    
                    String line;
                    while( (line = buffreader.readLine() )  !=  null)
                    {
                           if(line.startsWith(lssue))
                           {
                        	 lssue_value = line.replace(lssue,"").replace("=", "").trim().toString();
                           }
                           if(line.startsWith(client))
                           {
                        	 client_value = line.replace(client, "").replace("=", "").trim().toString();
                           }
                    }
                    
                    instream.close();
                }
            } catch(FileNotFoundException e) 
            {
                Log.e(TAG, "The File doesn\'t not exist.");
            } catch(IOException e) {
                Log.e(TAG, " readFile error!");
                Log.e(TAG, e.getMessage() );
            }
    	
    }
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        mHandler.removeMessages(MSG_PLAY_VIDEO);
        mHandler.sendEmptyMessageDelayed(MSG_PLAY_VIDEO, MSG_PLAY_VIDEO_TIME);
        mLeftLayout.setVisibility(View.VISIBLE);
        mBottomLayout.setVisibility(View.VISIBLE);
        mBottomLayout2.setVisibility(View.VISIBLE);
        mBottomLayout3.setVisibility(View.VISIBLE);
        mBottomLayout4.setVisibility(View.VISIBLE);
        mBottomLayout5.setVisibility(View.VISIBLE);
        return super.onKeyDown(keyCode, event);
    }

}
