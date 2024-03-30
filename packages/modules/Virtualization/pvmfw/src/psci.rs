// Copyright 2022, The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

//! PSCI calls.

const PSCI_SYSTEM_OFF: u32 = 0x84000008;
const PSCI_SYSTEM_RESET: u32 = 0x84000009;
const PSCI_SYSTEM_RESET2: u32 = 0x84000012;

pub fn system_off() -> u32 {
    hvc32(PSCI_SYSTEM_OFF, 0, 0, 0, 0, 0, 0, 0)[0]
}

pub fn system_reset() -> u32 {
    hvc32(PSCI_SYSTEM_RESET, 0, 0, 0, 0, 0, 0, 0)[0]
}

#[allow(unused)]
pub fn system_reset2(reset_type: u32, cookie: u32) -> u32 {
    hvc32(PSCI_SYSTEM_RESET2, reset_type, cookie, 0, 0, 0, 0, 0)[0]
}

/// Make an HVC32 call to the hypervisor, following the SMC Calling Convention version 1.3.
#[inline(always)]
#[allow(clippy::too_many_arguments)]
fn hvc32(
    function: u32,
    arg1: u32,
    arg2: u32,
    arg3: u32,
    arg4: u32,
    arg5: u32,
    arg6: u32,
    arg7: u32,
) -> [u32; 8] {
    let mut ret = [0; 8];

    #[cfg(target_arch = "aarch64")]
    unsafe {
        core::arch::asm!(
            "hvc #0",
            inout("w0") function => ret[0],
            inout("w1") arg1 => ret[1],
            inout("w2") arg2 => ret[2],
            inout("w3") arg3 => ret[3],
            inout("w4") arg4 => ret[4],
            inout("w5") arg5 => ret[5],
            inout("w6") arg6 => ret[6],
            inout("w7") arg7 => ret[7],
            options(nomem, nostack)
        )
    }

    #[cfg(not(target_arch = "aarch64"))]
    unimplemented!();

    ret
}
