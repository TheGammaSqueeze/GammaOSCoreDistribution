use std::cell::RefCell;
use std::collections::VecDeque;

use uwb_uci_packets::GetDeviceInfoRspPacket;
use uwb_uci_rust::error::UwbErr;
use uwb_uci_rust::uci::{uci_hrcv::UciResponse, Dispatcher, JNICommand, Result};

#[cfg(test)]
#[derive(Default)]
pub struct MockDispatcher {
    expected_calls: RefCell<VecDeque<ExpectedCall>>,
    device_info: Option<GetDeviceInfoRspPacket>,
}

#[cfg(test)]
impl MockDispatcher {
    pub fn new() -> Self {
        Default::default()
    }

    pub fn expect_send_jni_command(&mut self, expected_cmd: JNICommand, out: Result<()>) {
        self.expected_calls
            .borrow_mut()
            .push_back(ExpectedCall::SendJniCommand { expected_cmd, out })
    }

    pub fn expect_block_on_jni_command(
        &mut self,
        expected_cmd: JNICommand,
        out: Result<UciResponse>,
    ) {
        self.expected_calls
            .borrow_mut()
            .push_back(ExpectedCall::BlockOnJniCommand { expected_cmd, out })
    }

    pub fn expect_wait_for_exit(&mut self, out: Result<()>) {
        self.expected_calls.borrow_mut().push_back(ExpectedCall::WaitForExit { out })
    }
}

#[cfg(test)]
impl Drop for MockDispatcher {
    fn drop(&mut self) {
        assert!(self.expected_calls.borrow().is_empty());
    }
}

#[cfg(test)]
impl Dispatcher for MockDispatcher {
    fn send_jni_command(&self, cmd: JNICommand) -> Result<()> {
        let mut expected_calls = self.expected_calls.borrow_mut();
        match expected_calls.pop_front() {
            Some(ExpectedCall::SendJniCommand { expected_cmd, out }) if cmd == expected_cmd => out,
            Some(call) => {
                expected_calls.push_front(call);
                Err(UwbErr::Undefined)
            }
            None => Err(UwbErr::Undefined),
        }
    }
    fn block_on_jni_command(&self, cmd: JNICommand) -> Result<UciResponse> {
        let mut expected_calls = self.expected_calls.borrow_mut();
        match expected_calls.pop_front() {
            Some(ExpectedCall::BlockOnJniCommand { expected_cmd, out }) if cmd == expected_cmd => {
                out
            }
            Some(call) => {
                expected_calls.push_front(call);
                Err(UwbErr::Undefined)
            }
            None => Err(UwbErr::Undefined),
        }
    }
    fn wait_for_exit(&mut self) -> Result<()> {
        let mut expected_calls = self.expected_calls.borrow_mut();
        match expected_calls.pop_front() {
            Some(ExpectedCall::WaitForExit { out }) => out,
            Some(call) => {
                expected_calls.push_front(call);
                Err(UwbErr::Undefined)
            }
            None => Err(UwbErr::Undefined),
        }
    }

    fn set_device_info(&mut self, device_info: Option<GetDeviceInfoRspPacket>) {
        self.device_info = device_info;
    }

    fn get_device_info(&self) -> &Option<GetDeviceInfoRspPacket> {
        &self.device_info
    }
}

#[cfg(test)]
enum ExpectedCall {
    SendJniCommand { expected_cmd: JNICommand, out: Result<()> },
    BlockOnJniCommand { expected_cmd: JNICommand, out: Result<UciResponse> },
    WaitForExit { out: Result<()> },
}
