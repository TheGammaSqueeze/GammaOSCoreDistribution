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

pub mod fira_app_config_params;

use crate::uci::params::{AppConfigTlv, SessionType};

/// The parameters of the UWB session.
// TODO(akahuang): Add CCC support.
#[derive(Debug, Clone)]
pub enum AppConfigParams {
    Fira(fira_app_config_params::FiraAppConfigParams),
}

impl AppConfigParams {
    pub fn generate_tlvs(&self) -> Vec<AppConfigTlv> {
        match self {
            Self::Fira(params) => params.generate_tlvs(),
        }
    }

    pub fn generate_updated_tlvs(&self, prev_params: &Self) -> Vec<AppConfigTlv> {
        match self {
            Self::Fira(params) => match prev_params {
                Self::Fira(prev_params) => params.generate_updated_tlvs(prev_params),
            },
        }
    }

    pub fn is_type_matched(&self, session_type: SessionType) -> bool {
        match self {
            Self::Fira(_) => {
                session_type == SessionType::FiraDataTransfer
                    || session_type == SessionType::FiraRangingSession
            }
        }
    }
}
