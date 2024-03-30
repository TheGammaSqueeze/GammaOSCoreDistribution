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

//! Manages running instances of the CompOS VM. At most one instance should be running at
//! a time, started on demand.

use crate::instance_starter::{CompOsInstance, InstanceStarter};
use android_system_virtualizationservice::aidl::android::system::virtualizationservice;
use anyhow::{bail, Result};
use compos_aidl_interface::binder::Strong;
use compos_common::compos_client::VmParameters;
use compos_common::{
    CURRENT_INSTANCE_DIR, DEX2OAT_CPU_SET_PROP_NAME, DEX2OAT_THREADS_PROP_NAME,
    PREFER_STAGED_VM_CONFIG_PATH, TEST_INSTANCE_DIR,
};
use rustutils::system_properties;
use std::num::NonZeroU32;
use std::str::FromStr;
use std::sync::{Arc, Mutex, Weak};
use virtualizationservice::IVirtualizationService::IVirtualizationService;

// Enough memory to complete odrefresh in the VM.
const VM_MEMORY_MIB: i32 = 1024;

pub struct InstanceManager {
    service: Strong<dyn IVirtualizationService>,
    state: Mutex<State>,
}

impl InstanceManager {
    pub fn new(service: Strong<dyn IVirtualizationService>) -> Self {
        Self { service, state: Default::default() }
    }

    pub fn start_current_instance(&self) -> Result<Arc<CompOsInstance>> {
        let mut vm_parameters = new_vm_parameters()?;
        vm_parameters.config_path = Some(PREFER_STAGED_VM_CONFIG_PATH.to_owned());
        self.start_instance(CURRENT_INSTANCE_DIR, vm_parameters)
    }

    pub fn start_test_instance(&self, prefer_staged: bool) -> Result<Arc<CompOsInstance>> {
        let mut vm_parameters = new_vm_parameters()?;
        vm_parameters.debug_mode = true;
        if prefer_staged {
            vm_parameters.config_path = Some(PREFER_STAGED_VM_CONFIG_PATH.to_owned());
        }
        self.start_instance(TEST_INSTANCE_DIR, vm_parameters)
    }

    fn start_instance(
        &self,
        instance_name: &str,
        vm_parameters: VmParameters,
    ) -> Result<Arc<CompOsInstance>> {
        let mut state = self.state.lock().unwrap();
        state.mark_starting()?;
        // Don't hold the lock while we start the instance to avoid blocking other callers.
        drop(state);

        let instance_starter = InstanceStarter::new(instance_name, vm_parameters);
        let instance = self.try_start_instance(instance_starter);

        let mut state = self.state.lock().unwrap();
        if let Ok(ref instance) = instance {
            state.mark_started(instance)?;
        } else {
            state.mark_stopped();
        }
        instance
    }

    fn try_start_instance(&self, instance_starter: InstanceStarter) -> Result<Arc<CompOsInstance>> {
        let compos_instance = instance_starter.start_new_instance(&*self.service)?;
        Ok(Arc::new(compos_instance))
    }
}

fn new_vm_parameters() -> Result<VmParameters> {
    let cpus = match system_properties::read(DEX2OAT_THREADS_PROP_NAME)? {
        Some(s) => Some(NonZeroU32::from_str(&s)?),
        None => {
            // dex2oat uses all CPUs by default. To match the behavior, give the VM all CPUs by
            // default.
            NonZeroU32::new(num_cpus::get() as u32)
        }
    };
    let cpu_set = system_properties::read(DEX2OAT_CPU_SET_PROP_NAME)?;
    let task_profiles = vec!["VMCompilationPerformance".to_string()];
    Ok(VmParameters {
        cpus,
        cpu_set,
        task_profiles,
        memory_mib: Some(VM_MEMORY_MIB),
        ..Default::default()
    })
}

// Ensures we only run one instance at a time.
// Valid states:
// Starting: is_starting is true, running_instance is None.
// Started: is_starting is false, running_instance is Some(x) and there is a strong ref to x.
// Stopped: is_starting is false and running_instance is None or a weak ref to a dropped instance.
// The panic calls here should never happen, unless the code above in InstanceManager is buggy.
// In particular nothing the client does should be able to trigger them.
#[derive(Default)]
struct State {
    running_instance: Option<Weak<CompOsInstance>>,
    is_starting: bool,
}

impl State {
    // Move to Starting iff we are Stopped.
    fn mark_starting(&mut self) -> Result<()> {
        if self.is_starting {
            bail!("An instance is already starting");
        }
        if let Some(weak) = &self.running_instance {
            if weak.strong_count() != 0 {
                bail!("An instance is already running");
            }
        }
        self.running_instance = None;
        self.is_starting = true;
        Ok(())
    }

    // Move from Starting to Stopped.
    fn mark_stopped(&mut self) {
        if !self.is_starting || self.running_instance.is_some() {
            panic!("Tried to mark stopped when not starting");
        }
        self.is_starting = false;
    }

    // Move from Starting to Started.
    fn mark_started(&mut self, instance: &Arc<CompOsInstance>) -> Result<()> {
        if !self.is_starting {
            panic!("Tried to mark started when not starting")
        }
        if self.running_instance.is_some() {
            panic!("Attempted to mark started when already started");
        }
        self.is_starting = false;
        self.running_instance = Some(Arc::downgrade(instance));
        Ok(())
    }
}
