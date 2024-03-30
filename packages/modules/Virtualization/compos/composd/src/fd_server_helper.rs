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

//! A helper library to start a fd_server.

use anyhow::{Context, Result};
use log::{debug, warn};
use minijail::Minijail;
use nix::fcntl::OFlag;
use nix::unistd::pipe2;
use std::fs::File;
use std::io::Read;
use std::os::unix::io::{AsRawFd, FromRawFd, RawFd};
use std::path::Path;

const FD_SERVER_BIN: &str = "/apex/com.android.virt/bin/fd_server";

/// Config for starting a `FdServer`
#[derive(Default)]
pub struct FdServerConfig {
    /// List of file FDs exposed for read-only operations.
    pub ro_file_fds: Vec<RawFd>,
    /// List of file FDs exposed for read-write operations.
    pub rw_file_fds: Vec<RawFd>,
    /// List of directory FDs exposed for read-only operations.
    pub ro_dir_fds: Vec<RawFd>,
    /// List of directory FDs exposed for read-write operations.
    pub rw_dir_fds: Vec<RawFd>,
}

impl FdServerConfig {
    /// Creates a `FdServer` based on the current config.
    pub fn into_fd_server(self) -> Result<FdServer> {
        let (ready_read_fd, ready_write_fd) = create_pipe()?;
        let fd_server_jail = self.do_spawn_fd_server(ready_write_fd)?;
        wait_for_fd_server_ready(ready_read_fd)?;
        Ok(FdServer { jailed_process: fd_server_jail })
    }

    fn do_spawn_fd_server(self, ready_file: File) -> Result<Minijail> {
        let mut inheritable_fds = Vec::new();
        let mut args = vec![FD_SERVER_BIN.to_string()];
        for fd in self.ro_file_fds {
            args.push("--ro-fds".to_string());
            args.push(fd.to_string());
            inheritable_fds.push(fd);
        }
        for fd in self.rw_file_fds {
            args.push("--rw-fds".to_string());
            args.push(fd.to_string());
            inheritable_fds.push(fd);
        }
        for fd in self.ro_dir_fds {
            args.push("--ro-dirs".to_string());
            args.push(fd.to_string());
            inheritable_fds.push(fd);
        }
        for fd in self.rw_dir_fds {
            args.push("--rw-dirs".to_string());
            args.push(fd.to_string());
            inheritable_fds.push(fd);
        }
        let ready_fd = ready_file.as_raw_fd();
        args.push("--ready-fd".to_string());
        args.push(ready_fd.to_string());
        inheritable_fds.push(ready_fd);

        debug!("Spawn fd_server {:?} (inheriting FDs: {:?})", args, inheritable_fds);
        let jail = Minijail::new()?;
        let _pid = jail.run(Path::new(FD_SERVER_BIN), &inheritable_fds, &args)?;
        Ok(jail)
    }
}

/// `FdServer` represents a running `fd_server` process. The process lifetime is associated with
/// the instance lifetime.
pub struct FdServer {
    jailed_process: Minijail,
}

impl Drop for FdServer {
    fn drop(&mut self) {
        if let Err(e) = self.jailed_process.kill() {
            if !matches!(e, minijail::Error::Killed(_)) {
                warn!("Failed to kill fd_server: {}", e);
            }
        }
    }
}

fn create_pipe() -> Result<(File, File)> {
    let (raw_read, raw_write) = pipe2(OFlag::O_CLOEXEC)?;
    // SAFETY: We are the sole owners of these fds as they were just created.
    let read_fd = unsafe { File::from_raw_fd(raw_read) };
    let write_fd = unsafe { File::from_raw_fd(raw_write) };
    Ok((read_fd, write_fd))
}

fn wait_for_fd_server_ready(mut ready_fd: File) -> Result<()> {
    let mut buffer = [0];
    // When fd_server is ready it closes its end of the pipe. And if it exits, the pipe is also
    // closed. Either way this read will return 0 bytes at that point, and there's no point waiting
    // any longer.
    let _ = ready_fd.read(&mut buffer).context("Waiting for fd_server to be ready")?;
    debug!("fd_server is ready");
    Ok(())
}
