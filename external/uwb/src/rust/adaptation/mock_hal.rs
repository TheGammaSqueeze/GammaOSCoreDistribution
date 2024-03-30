/*
 * Copyright (C) 2022 The Android Open Source Project
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

//! MockHal

use android_hardware_uwb::aidl::android::hardware::uwb::{
    IUwbChip::IUwbChipAsync, IUwbClientCallback::IUwbClientCallback,
};
use android_hardware_uwb::binder::{Result as BinderResult, Strong};
use async_trait::async_trait;
use binder::{SpIBinder, StatusCode};
use std::collections::VecDeque;
use std::sync::Mutex as StdMutex;

#[cfg(test)]
enum ExpectedHalCall {
    Open { out: BinderResult<()> },
    Close { out: BinderResult<()> },
    CoreInit { out: BinderResult<()> },
    SessionInit { expected_session_id: i32, out: BinderResult<()> },
    SendUciMessage { expected_data: Vec<u8>, out: BinderResult<i32> },
}

#[cfg(test)]
pub struct MockHal {
    expected_calls: StdMutex<VecDeque<ExpectedHalCall>>,
}

#[cfg(test)]
impl MockHal {
    pub fn new() -> Self {
        Self { expected_calls: StdMutex::new(VecDeque::new()) }
    }
    #[allow(dead_code)]
    pub fn expect_open(&self, out: BinderResult<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedHalCall::Open { out });
    }
    #[allow(dead_code)]
    pub fn expect_close(&self, out: BinderResult<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedHalCall::Close { out });
    }
    #[allow(dead_code)]
    pub fn expect_core_init(&self, out: BinderResult<()>) {
        self.expected_calls.lock().unwrap().push_back(ExpectedHalCall::CoreInit { out });
    }
    #[allow(dead_code)]
    pub fn expect_session_init(&self, expected_session_id: i32, out: BinderResult<()>) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedHalCall::SessionInit { expected_session_id, out });
    }
    pub fn expect_send_uci_message(&self, expected_data: Vec<u8>, out: BinderResult<i32>) {
        self.expected_calls
            .lock()
            .unwrap()
            .push_back(ExpectedHalCall::SendUciMessage { expected_data, out });
    }
}

#[cfg(test)]
impl Drop for MockHal {
    fn drop(&mut self) {
        assert!(self.expected_calls.lock().unwrap().is_empty());
    }
}

#[cfg(test)]
impl Default for MockHal {
    fn default() -> Self {
        Self::new()
    }
}

#[cfg(test)]
impl binder::Interface for MockHal {}

#[cfg(test)]
impl binder::FromIBinder for MockHal {
    fn try_from(_ibinder: SpIBinder) -> std::result::Result<Strong<Self>, binder::StatusCode> {
        Err(binder::StatusCode::OK)
    }
}

#[cfg(test)]
#[async_trait]
impl<P: binder::BinderAsyncPool> IUwbChipAsync<P> for MockHal {
    fn getName(&self) -> binder::BoxFuture<BinderResult<String>> {
        Box::pin(std::future::ready(Ok("default".into())))
    }

    fn open<'a>(
        &'a self,
        _cb: &'a binder::Strong<dyn IUwbClientCallback>,
    ) -> binder::BoxFuture<'a, BinderResult<()>> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedHalCall::Open { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some(out) => Box::pin(std::future::ready(out)),
            None => Box::pin(std::future::ready(Err(StatusCode::UNKNOWN_ERROR.into()))),
        }
    }

    fn close(&self) -> binder::BoxFuture<BinderResult<()>> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedHalCall::Close { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some(out) => Box::pin(std::future::ready(out)),
            None => Box::pin(std::future::ready(Err(StatusCode::UNKNOWN_ERROR.into()))),
        }
    }

    fn coreInit(&self) -> binder::BoxFuture<BinderResult<()>> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedHalCall::CoreInit { out }) => Some(out),
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some(out) => Box::pin(std::future::ready(out)),
            None => Box::pin(std::future::ready(Err(StatusCode::UNKNOWN_ERROR.into()))),
        }
    }

    fn sessionInit(&self, session_id: i32) -> binder::BoxFuture<BinderResult<()>> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedHalCall::SessionInit { expected_session_id, out })
                    if expected_session_id == session_id =>
                {
                    Some(out)
                }
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };

        match expected_out {
            Some(out) => Box::pin(std::future::ready(out)),
            None => Box::pin(std::future::ready(Err(StatusCode::UNKNOWN_ERROR.into()))),
        }
    }

    fn getSupportedAndroidUciVersion(&self) -> binder::BoxFuture<BinderResult<i32>> {
        Box::pin(std::future::ready(Ok(0)))
    }

    fn sendUciMessage(&self, cmd: &[u8]) -> binder::BoxFuture<BinderResult<i32>> {
        let expected_out = {
            let mut expected_calls = self.expected_calls.lock().unwrap();
            match expected_calls.pop_front() {
                Some(ExpectedHalCall::SendUciMessage { expected_data, out })
                    if expected_data == cmd =>
                {
                    Some(out)
                }
                Some(call) => {
                    expected_calls.push_front(call);
                    None
                }
                None => None,
            }
        };
        match expected_out {
            Some(out) => Box::pin(std::future::ready(out)),
            None => Box::pin(std::future::ready(Err(StatusCode::UNKNOWN_ERROR.into()))),
        }
    }
}
