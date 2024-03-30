use std::cell::{Cell, RefCell};
use std::collections::VecDeque;

use jni::sys::{jarray, jbyteArray, jint, jintArray, jshort, jshortArray, jsize};
use uwb_uci_rust::error::UwbErr;
use uwb_uci_rust::uci::Dispatcher;

use crate::mock_dispatcher::MockDispatcher;
use crate::Context;

#[cfg(test)]
pub struct MockContext {
    dispatcher: Cell<MockDispatcher>,
    expected_calls: RefCell<VecDeque<ExpectedCall>>,
}

#[cfg(test)]
impl MockContext {
    pub fn new(dispatcher: MockDispatcher) -> Self {
        Self { dispatcher: Cell::new(dispatcher), expected_calls: Default::default() }
    }

    pub fn get_mock_dispatcher(&mut self) -> &mut MockDispatcher {
        self.dispatcher.get_mut()
    }

    pub fn expect_convert_byte_array(
        &mut self,
        expected_array: jbyteArray,
        out: Result<Vec<u8>, jni::errors::Error>,
    ) {
        self.expected_calls
            .borrow_mut()
            .push_back(ExpectedCall::ConvertByteArray { expected_array, out });
    }

    pub fn expect_get_array_length(
        &mut self,
        expected_array: jarray,
        out: Result<jsize, jni::errors::Error>,
    ) {
        self.expected_calls
            .borrow_mut()
            .push_back(ExpectedCall::GetArrayLength { expected_array, out });
    }

    pub fn expect_get_short_array_region(
        &mut self,
        expected_array: jshortArray,
        expected_start: jsize,
        out: Result<Box<[jshort]>, jni::errors::Error>,
    ) {
        self.expected_calls.borrow_mut().push_back(ExpectedCall::GetShortArrayRegion {
            expected_array,
            expected_start,
            out,
        });
    }

    pub fn expect_get_int_array_region(
        &mut self,
        expected_array: jintArray,
        expected_start: jsize,
        out: Result<Box<[jint]>, jni::errors::Error>,
    ) {
        self.expected_calls.borrow_mut().push_back(ExpectedCall::GetIntArrayRegion {
            expected_array,
            expected_start,
            out,
        });
    }
}

#[cfg(test)]
impl<'a> Context<'a> for MockContext {
    fn convert_byte_array(&self, array: jbyteArray) -> Result<Vec<u8>, jni::errors::Error> {
        let mut expected_calls = self.expected_calls.borrow_mut();
        match expected_calls.pop_front() {
            Some(ExpectedCall::ConvertByteArray { expected_array, out })
                if array == expected_array =>
            {
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown))
            }
            None => Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown)),
        }
    }

    fn get_array_length(&self, array: jarray) -> Result<jsize, jni::errors::Error> {
        let mut expected_calls = self.expected_calls.borrow_mut();
        match expected_calls.pop_front() {
            Some(ExpectedCall::GetArrayLength { expected_array, out })
                if array == expected_array =>
            {
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown))
            }
            None => Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown)),
        }
    }

    fn get_short_array_region(
        &self,
        array: jshortArray,
        start: jsize,
        buf: &mut [jshort],
    ) -> Result<(), jni::errors::Error> {
        let mut expected_calls = self.expected_calls.borrow_mut();
        match expected_calls.pop_front() {
            Some(ExpectedCall::GetShortArrayRegion { expected_array, expected_start, out })
                if array == expected_array && start == expected_start =>
            {
                match out {
                    Ok(expected_buf) => {
                        buf.clone_from_slice(&expected_buf);
                        Ok(())
                    }
                    Err(err) => Err(err),
                }
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown))
            }
            None => Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown)),
        }
    }

    fn get_int_array_region(
        &self,
        array: jintArray,
        start: jsize,
        buf: &mut [jint],
    ) -> Result<(), jni::errors::Error> {
        let mut expected_calls = self.expected_calls.borrow_mut();
        match expected_calls.pop_front() {
            Some(ExpectedCall::GetIntArrayRegion { expected_array, expected_start, out })
                if array == expected_array && start == expected_start =>
            {
                match out {
                    Ok(expected_buf) => {
                        buf.clone_from_slice(&expected_buf);
                        Ok(())
                    }
                    Err(err) => Err(err),
                }
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown))
            }
            None => Err(jni::errors::Error::JniCall(jni::errors::JniError::Unknown)),
        }
    }

    fn get_dispatcher(&self) -> Result<&'a mut dyn Dispatcher, UwbErr> {
        unsafe { Ok(&mut *(self.dispatcher.as_ptr())) }
    }
}

#[cfg(test)]
enum ExpectedCall {
    ConvertByteArray {
        expected_array: jbyteArray,
        out: Result<Vec<u8>, jni::errors::Error>,
    },
    GetArrayLength {
        expected_array: jarray,
        out: Result<jsize, jni::errors::Error>,
    },
    GetShortArrayRegion {
        expected_array: jshortArray,
        expected_start: jsize,
        out: Result<Box<[jshort]>, jni::errors::Error>,
    },
    GetIntArrayRegion {
        expected_array: jintArray,
        expected_start: jsize,
        out: Result<Box<[jint]>, jni::errors::Error>,
    },
}
