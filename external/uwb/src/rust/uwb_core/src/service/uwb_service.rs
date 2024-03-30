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

//! This module provides UwbService and its related components.

use log::{debug, error};
use tokio::sync::{mpsc, oneshot};

use crate::service::error::{Error, Result};
use crate::session::session_manager::SessionManager;
use crate::uci::uci_hal::UciHal;
use crate::uci::uci_manager::{UciManager, UciManagerImpl};

#[cfg(test)]
use crate::uci::mock_uci_manager::MockUciManager;

/// The entry class (a.k.a top shim) of the core library. The class accepts requests from the
/// client, and delegates the requests to other components. It should provide the
/// backward-compatible interface for the client of the library.
pub struct UwbService {
    cmd_sender: mpsc::UnboundedSender<(UwbCommand, oneshot::Sender<Result<()>>)>,
}

impl UwbService {
    /// Create a new UwbService instance.
    pub fn new<U: UciHal>(uci_hal: U) -> Self {
        let uci_manager = UciManagerImpl::new(uci_hal);
        let (cmd_sender, cmd_receiver) = mpsc::unbounded_channel();
        let mut actor = UwbServiceActor::new(cmd_receiver, uci_manager);
        tokio::spawn(async move { actor.run().await });

        Self { cmd_sender }
    }

    #[cfg(test)]
    fn new_for_testing(uci_manager: MockUciManager) -> Self {
        let (cmd_sender, cmd_receiver) = mpsc::unbounded_channel();
        // TODO(akahuang): Change to use MockSessionManager.
        let mut actor = UwbServiceActor::new(cmd_receiver, uci_manager);
        tokio::spawn(async move { actor.run().await });

        Self { cmd_sender }
    }

    /// Enable the UWB service.
    pub async fn enable(&mut self) -> Result<()> {
        self.send_cmd(UwbCommand::Enable).await
    }

    /// Disable the UWB service.
    pub async fn disable(&mut self) -> Result<()> {
        self.send_cmd(UwbCommand::Disable).await
    }

    // Send the |cmd| to the SessionManagerActor.
    async fn send_cmd(&self, cmd: UwbCommand) -> Result<()> {
        let (result_sender, result_receiver) = oneshot::channel();
        self.cmd_sender.send((cmd, result_sender)).map_err(|cmd| {
            error!("Failed to send cmd: {:?}", cmd.0);
            Error::TokioFailure
        })?;
        result_receiver.await.unwrap_or(Err(Error::TokioFailure))
    }
}

struct UwbServiceActor<U: UciManager> {
    cmd_receiver: mpsc::UnboundedReceiver<(UwbCommand, oneshot::Sender<Result<()>>)>,
    uci_manager: U,
    session_manager: Option<SessionManager>,
}

impl<U: UciManager> UwbServiceActor<U> {
    fn new(
        cmd_receiver: mpsc::UnboundedReceiver<(UwbCommand, oneshot::Sender<Result<()>>)>,
        uci_manager: U,
    ) -> Self {
        Self { cmd_receiver, uci_manager, session_manager: None }
    }

    async fn run(&mut self) {
        loop {
            tokio::select! {
                cmd = self.cmd_receiver.recv() => {
                    match cmd {
                        None => {
                            debug!("UwbService is about to drop.");
                            break;
                        },
                        Some((cmd, result_sender)) => {
                            let result = self.handle_cmd(cmd).await;
                            let _ = result_sender.send(result);
                        }
                    }
                }
            }
        }
    }

    async fn handle_cmd(&mut self, cmd: UwbCommand) -> Result<()> {
        match cmd {
            UwbCommand::Enable => {
                if self.session_manager.is_some() {
                    debug!("The service is already enabled, skip.");
                    return Ok(());
                }

                let (uci_notf_sender, uci_notf_receiver) = mpsc::unbounded_channel();
                self.uci_manager.open_hal(uci_notf_sender).await.map_err(|e| {
                    error!("Failed to open the UCI HAL: ${:?}", e);
                    Error::UciError
                })?;

                self.session_manager =
                    Some(SessionManager::new(self.uci_manager.clone(), uci_notf_receiver));
                Ok(())
            }
            UwbCommand::Disable => {
                if self.session_manager.is_none() {
                    debug!("The service is already disabled, skip.");
                    return Ok(());
                }

                self.session_manager = None;
                self.uci_manager.close_hal().await.map_err(|e| {
                    error!("Failed to open the UCI HAL: ${:?}", e);
                    Error::UciError
                })?;
                Ok(())
            }
        }
    }
}

#[derive(Debug)]
enum UwbCommand {
    Enable,
    Disable,
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn test_open_close_uci() {
        let mut uci_manager = MockUciManager::new();
        uci_manager.expect_open_hal(vec![], Ok(()));
        uci_manager.expect_close_hal(Ok(()));
        let mut service = UwbService::new_for_testing(uci_manager);

        let result = service.enable().await;
        assert!(result.is_ok());
        let result = service.disable().await;
        assert!(result.is_ok());
    }
}
