/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

use crate::uci::uci_hrcv::UciResponse;
use crate::uci::{HalCallback, JNICommand};
use android_hardware_uwb::aidl::android::hardware::uwb::UwbStatus::UwbStatus;
use std::array::TryFromSliceError;
use std::option::Option;
use tokio::sync::{mpsc, oneshot};
use uwb_uci_packets::StatusCode;

#[derive(Debug, thiserror::Error)]
pub enum UwbErr {
    #[error("StatusCoode error: {0:?}")]
    StatusCode(StatusCode),
    #[error("UWBStatus error: {0:?}")]
    UwbStatus(UwbStatus),
    #[error("Binder error: {0}")]
    Binder(#[from] binder::Status),
    #[error("JNI error: {0}")]
    Jni(#[from] jni::errors::Error),
    #[error("IO error: {0}")]
    Io(#[from] std::io::Error),
    #[error("SendError for JNICommand: {0}")]
    SendJNICommand(
        #[from] mpsc::error::SendError<(JNICommand, Option<oneshot::Sender<UciResponse>>)>,
    ),
    #[error("SendError for HalCallback: {0}")]
    SendHalCallback(#[from] mpsc::error::SendError<HalCallback>),
    #[error("RecvError: {0}")]
    RecvError(#[from] oneshot::error::RecvError),
    #[error("Could not parse: {0}")]
    Parse(#[from] uwb_uci_packets::Error),
    #[error("Could not specialize: {0:?}")]
    Specialize(Vec<u8>),
    #[error("Could not convert: {0:?}")]
    ConvertToArray(#[from] TryFromSliceError),
    #[error("The dispatcher does not exist")]
    NoneDispatcher,
    #[error("Invalid args")]
    InvalidArgs,
    #[error("Exit")]
    Exit,
    #[error("Could not connect to HAL")]
    HalUnavailable(#[from] binder::StatusCode),
    #[error("Could not convert")]
    ConvertToEnum(#[from] std::num::TryFromIntError),
    #[error("Unknown error")]
    Undefined,
}

impl UwbErr {
    pub fn failed() -> Self {
        UwbErr::UwbStatus(UwbStatus::FAILED)
    }

    pub fn refused() -> Self {
        UwbErr::UwbStatus(UwbStatus::REFUSED)
    }
}
