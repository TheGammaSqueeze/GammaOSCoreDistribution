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

//! This is a test helper program that opens files and/or directories, then passes the file
//! descriptors to the specified command. When passing the file descriptors, they are mapped to the
//! specified numbers in the child process.

use anyhow::{bail, Context, Result};
use clap::{App, Arg, Values};
use command_fds::{CommandFdExt, FdMapping};
use log::{debug, error};
use nix::{dir::Dir, fcntl::OFlag, sys::stat::Mode};
use std::fs::{File, OpenOptions};
use std::os::unix::io::{AsRawFd, RawFd};
use std::process::Command;

// `PseudoRawFd` is just an integer and not necessarily backed by a real FD. It is used to denote
// the expecting FD number, when trying to set up FD mapping in the child process. The intention
// with this alias is to improve readability by distinguishing from actual RawFd.
type PseudoRawFd = RawFd;

struct FileMapping<T: AsRawFd> {
    file: T,
    target_fd: PseudoRawFd,
}

impl<T: AsRawFd> FileMapping<T> {
    fn as_fd_mapping(&self) -> FdMapping {
        FdMapping { parent_fd: self.file.as_raw_fd(), child_fd: self.target_fd }
    }
}

struct Args {
    ro_files: Vec<FileMapping<File>>,
    rw_files: Vec<FileMapping<File>>,
    dir_files: Vec<FileMapping<Dir>>,
    cmdline_args: Vec<String>,
}

fn parse_and_create_file_mapping<F, T>(
    values: Option<Values<'_>>,
    opener: F,
) -> Result<Vec<FileMapping<T>>>
where
    F: Fn(&str) -> Result<T>,
    T: AsRawFd,
{
    if let Some(options) = values {
        options
            .map(|option| {
                // Example option: 10:/some/path
                let strs: Vec<&str> = option.split(':').collect();
                if strs.len() != 2 {
                    bail!("Invalid option: {}", option);
                }
                let fd = strs[0].parse::<PseudoRawFd>().context("Invalid FD format")?;
                let path = strs[1];
                Ok(FileMapping { target_fd: fd, file: opener(path)? })
            })
            .collect::<Result<_>>()
    } else {
        Ok(Vec::new())
    }
}

fn parse_args() -> Result<Args> {
    #[rustfmt::skip]
    let matches = App::new("open_then_run")
        .arg(Arg::with_name("open-ro")
             .long("open-ro")
             .value_name("FD:PATH")
             .help("Open <PATH> read-only to pass as fd <FD>")
             .multiple(true)
             .number_of_values(1))
        .arg(Arg::with_name("open-rw")
             .long("open-rw")
             .value_name("FD:PATH")
             .help("Open/create <PATH> read-write to pass as fd <FD>")
             .multiple(true)
             .number_of_values(1))
        .arg(Arg::with_name("open-dir")
             .long("open-dir")
             .value_name("FD:DIR")
             .help("Open <DIR> to pass as fd <FD>")
             .multiple(true)
             .number_of_values(1))
        .arg(Arg::with_name("args")
             .help("Command line to execute with pre-opened FD inherited")
             .last(true)
             .required(true)
             .multiple(true))
        .get_matches();

    let ro_files = parse_and_create_file_mapping(matches.values_of("open-ro"), |path| {
        OpenOptions::new().read(true).open(path).with_context(|| format!("Open {} read-only", path))
    })?;

    let rw_files = parse_and_create_file_mapping(matches.values_of("open-rw"), |path| {
        OpenOptions::new()
            .read(true)
            .write(true)
            .create(true)
            .open(path)
            .with_context(|| format!("Open {} read-write", path))
    })?;

    let dir_files = parse_and_create_file_mapping(matches.values_of("open-dir"), |path| {
        Dir::open(path, OFlag::O_DIRECTORY | OFlag::O_RDONLY, Mode::S_IRWXU)
            .with_context(|| format!("Open {} directory", path))
    })?;

    let cmdline_args: Vec<_> = matches.values_of("args").unwrap().map(|s| s.to_string()).collect();

    Ok(Args { ro_files, rw_files, dir_files, cmdline_args })
}

fn try_main() -> Result<()> {
    let args = parse_args()?;

    let mut command = Command::new(&args.cmdline_args[0]);
    command.args(&args.cmdline_args[1..]);

    // Set up FD mappings in the child process.
    let mut fd_mappings = Vec::new();
    fd_mappings.extend(args.ro_files.iter().map(FileMapping::as_fd_mapping));
    fd_mappings.extend(args.rw_files.iter().map(FileMapping::as_fd_mapping));
    fd_mappings.extend(args.dir_files.iter().map(FileMapping::as_fd_mapping));
    command.fd_mappings(fd_mappings)?;

    debug!("Spawning {:?}", command);
    command.spawn()?;
    Ok(())
}

fn main() {
    android_logger::init_once(
        android_logger::Config::default()
            .with_tag("open_then_run")
            .with_min_level(log::Level::Debug),
    );

    if let Err(e) = try_main() {
        error!("Failed with {:?}", e);
        std::process::exit(1);
    }
}
