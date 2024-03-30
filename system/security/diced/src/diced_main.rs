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

//! Main entry point for diced, the friendly neighborhood DICE service.

use binder::get_interface;
use diced::{DiceMaintenance, DiceNode, DiceNodeImpl, ProxyNodeHal, ResidentNode};
use std::convert::TryInto;
use std::panic;
use std::sync::Arc;

static DICE_NODE_SERVICE_NAME: &str = "android.security.dice.IDiceNode";
static DICE_MAINTENANCE_SERVICE_NAME: &str = "android.security.dice.IDiceMaintenance";
static DICE_HAL_SERVICE_NAME: &str = "android.hardware.security.dice.IDiceDevice/default";

fn main() {
    android_logger::init_once(
        android_logger::Config::default().with_tag("diced").with_min_level(log::Level::Debug),
    );
    // Redirect panic messages to logcat.
    panic::set_hook(Box::new(|panic_info| {
        log::error!("{}", panic_info);
    }));

    // Saying hi.
    log::info!("Diced, your friendly neighborhood DICE service, is starting.");

    let node_impl: Arc<dyn DiceNodeImpl + Send + Sync> = match get_interface(DICE_HAL_SERVICE_NAME)
    {
        Ok(dice_device) => {
            Arc::new(ProxyNodeHal::new(dice_device).expect("Failed to construct a proxy node."))
        }
        Err(e) => {
            log::warn!("Failed to connect to DICE HAL: {:?}", e);
            log::warn!("Using sample dice artifacts.");
            let (cdi_attest, cdi_seal, bcc) = diced_sample_inputs::make_sample_bcc_and_cdis()
                .expect("Failed to create sample dice artifacts.");
            Arc::new(
                ResidentNode::new(
                    cdi_attest[..]
                        .try_into()
                        .expect("Failed to convert cdi_attest into array ref."),
                    cdi_seal[..].try_into().expect("Failed to convert cdi_seal into array ref."),
                    bcc,
                )
                .expect("Failed to construct a resident node."),
            )
        }
    };

    let node = DiceNode::new_as_binder(node_impl.clone())
        .expect("Failed to create IDiceNode service instance.");

    let maintenance = DiceMaintenance::new_as_binder(node_impl)
        .expect("Failed to create IDiceMaintenance service instance.");

    binder::add_service(DICE_NODE_SERVICE_NAME, node.as_binder())
        .expect("Failed to register IDiceNode Service");

    binder::add_service(DICE_MAINTENANCE_SERVICE_NAME, maintenance.as_binder())
        .expect("Failed to register IDiceMaintenance Service");

    log::info!("Joining thread pool now.");
    binder::ProcessState::join_thread_pool();
}
