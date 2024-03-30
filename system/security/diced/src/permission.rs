// Copyright 2021, The Android Open Source Project
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

//! This crate provides convenience wrappers for the SELinux permission
//! defined in the diced SELinux access class.

use keystore2_selinux as selinux;
use selinux::{implement_class, ClassPermission};

implement_class!(
    /// Permission provides a convenient abstraction from the SELinux class `diced`.
    #[selinux(class_name = diced)]
    #[derive(Clone, Copy, Debug, PartialEq)]
    pub enum Permission {
        /// Checked when a client attempts to call seal or unseal.
        #[selinux(name = use_seal)]
        UseSeal,
        /// Checked when a client attempts to call IDiceNode::sign.
        #[selinux(name = use_sign)]
        UseSign,
        /// Checked when a client attempts to call IDiceNode::getAttestationChain.
        #[selinux(name = get_attestation_chain)]
        GetAttestationChain,
        /// Checked when a client attempts to call IDiceNode::derive.
        #[selinux(name = derive)]
        Derive,
        /// Checked when a client wants to demote itself by calling IDiceNode::demote.
        #[selinux(name = demote)]
        Demote,
        /// Checked when a client calls IDiceMaintenance::demote in an attempt to
        /// demote this dice node.
        #[selinux(name = demote_self)]
        DemoteSelf,
    }
);
