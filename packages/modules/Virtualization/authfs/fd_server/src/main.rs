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

//! This program is a constrained file/FD server to serve file requests through a remote binder
//! service. The file server is not designed to serve arbitrary file paths in the filesystem. On
//! the contrary, the server should be configured to start with already opened FDs, and serve the
//! client's request against the FDs
//!
//! For example, `exec 9</path/to/file fd_server --ro-fds 9` starts the binder service. A client
//! client can then request the content of file 9 by offset and size.

mod aidl;
mod common;
mod fsverity;

use anyhow::{bail, Result};
use binder_common::rpc_server::run_rpc_server;
use log::debug;
use nix::sys::stat::{umask, Mode};
use std::collections::BTreeMap;
use std::fs::File;
use std::os::unix::io::FromRawFd;

use aidl::{FdConfig, FdService};
use authfs_fsverity_metadata::parse_fsverity_metadata;

const RPC_SERVICE_PORT: u32 = 3264; // TODO: support dynamic port for multiple fd_server instances

fn is_fd_valid(fd: i32) -> bool {
    // SAFETY: a query-only syscall
    let retval = unsafe { libc::fcntl(fd, libc::F_GETFD) };
    retval >= 0
}

fn fd_to_owned<T: FromRawFd>(fd: i32) -> Result<T> {
    if !is_fd_valid(fd) {
        bail!("Bad FD: {}", fd);
    }
    // SAFETY: The caller is supposed to provide valid FDs to this process.
    Ok(unsafe { T::from_raw_fd(fd) })
}

fn parse_arg_ro_fds(arg: &str) -> Result<(i32, FdConfig)> {
    let result: Result<Vec<i32>, _> = arg.split(':').map(|x| x.parse::<i32>()).collect();
    let fds = result?;
    if fds.len() > 2 {
        bail!("Too many options: {}", arg);
    }
    Ok((
        fds[0],
        FdConfig::Readonly {
            file: fd_to_owned(fds[0])?,
            // Alternative metadata source, if provided
            alt_metadata: fds
                .get(1)
                .map(|fd| fd_to_owned(*fd))
                .transpose()?
                .and_then(|f| parse_fsverity_metadata(f).ok()),
        },
    ))
}

fn parse_arg_rw_fds(arg: &str) -> Result<(i32, FdConfig)> {
    let fd = arg.parse::<i32>()?;
    let file = fd_to_owned::<File>(fd)?;
    if file.metadata()?.len() > 0 {
        bail!("File is expected to be empty");
    }
    Ok((fd, FdConfig::ReadWrite(file)))
}

fn parse_arg_ro_dirs(arg: &str) -> Result<(i32, FdConfig)> {
    let fd = arg.parse::<i32>()?;
    Ok((fd, FdConfig::InputDir(fd_to_owned(fd)?)))
}

fn parse_arg_rw_dirs(arg: &str) -> Result<(i32, FdConfig)> {
    let fd = arg.parse::<i32>()?;
    Ok((fd, FdConfig::OutputDir(fd_to_owned(fd)?)))
}

struct Args {
    fd_pool: BTreeMap<i32, FdConfig>,
    ready_fd: Option<File>,
}

fn parse_args() -> Result<Args> {
    #[rustfmt::skip]
    let matches = clap::App::new("fd_server")
        .arg(clap::Arg::with_name("ro-fds")
             .long("ro-fds")
             .multiple(true)
             .number_of_values(1))
        .arg(clap::Arg::with_name("rw-fds")
             .long("rw-fds")
             .multiple(true)
             .number_of_values(1))
        .arg(clap::Arg::with_name("ro-dirs")
             .long("ro-dirs")
             .multiple(true)
             .number_of_values(1))
        .arg(clap::Arg::with_name("rw-dirs")
             .long("rw-dirs")
             .multiple(true)
             .number_of_values(1))
        .arg(clap::Arg::with_name("ready-fd")
            .long("ready-fd")
            .takes_value(true))
        .get_matches();

    let mut fd_pool = BTreeMap::new();
    if let Some(args) = matches.values_of("ro-fds") {
        for arg in args {
            let (fd, config) = parse_arg_ro_fds(arg)?;
            fd_pool.insert(fd, config);
        }
    }
    if let Some(args) = matches.values_of("rw-fds") {
        for arg in args {
            let (fd, config) = parse_arg_rw_fds(arg)?;
            fd_pool.insert(fd, config);
        }
    }
    if let Some(args) = matches.values_of("ro-dirs") {
        for arg in args {
            let (fd, config) = parse_arg_ro_dirs(arg)?;
            fd_pool.insert(fd, config);
        }
    }
    if let Some(args) = matches.values_of("rw-dirs") {
        for arg in args {
            let (fd, config) = parse_arg_rw_dirs(arg)?;
            fd_pool.insert(fd, config);
        }
    }
    let ready_fd = if let Some(arg) = matches.value_of("ready-fd") {
        let fd = arg.parse::<i32>()?;
        Some(fd_to_owned(fd)?)
    } else {
        None
    };
    Ok(Args { fd_pool, ready_fd })
}

fn main() -> Result<()> {
    android_logger::init_once(
        android_logger::Config::default().with_tag("fd_server").with_min_level(log::Level::Debug),
    );

    let args = parse_args()?;

    // Allow open/create/mkdir from authfs to create with expecting mode. It's possible to still
    // use a custom mask on creation, then report the actual file mode back to authfs. But there
    // is no demand now.
    let old_umask = umask(Mode::empty());
    debug!("Setting umask to 0 (old: {:03o})", old_umask.bits());

    let service = FdService::new_binder(args.fd_pool).as_binder();
    debug!("fd_server is starting as a rpc service.");
    let mut ready_fd = args.ready_fd;
    let retval = run_rpc_server(service, RPC_SERVICE_PORT, || {
        debug!("fd_server is ready");
        // Close the ready-fd if we were given one to signal our readiness.
        drop(ready_fd.take());
    });

    if retval {
        debug!("RPC server has shut down gracefully");
        Ok(())
    } else {
        bail!("Premature termination of RPC server");
    }
}
