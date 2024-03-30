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

//! Simple command-line tool to drive composd for testing and debugging.

use android_system_composd::{
    aidl::android::system::composd::{
        ICompilationTask::ICompilationTask,
        ICompilationTaskCallback::{
            BnCompilationTaskCallback, FailureReason::FailureReason, ICompilationTaskCallback,
        },
        IIsolatedCompilationService::ApexSource::ApexSource,
        IIsolatedCompilationService::IIsolatedCompilationService,
    },
    binder::{
        wait_for_interface, BinderFeatures, DeathRecipient, IBinder, Interface, ProcessState,
        Result as BinderResult, Strong,
    },
};
use anyhow::{bail, Context, Result};
use compos_common::timeouts::timeouts;
use std::sync::{Arc, Condvar, Mutex};
use std::time::Duration;

fn main() -> Result<()> {
    #[rustfmt::skip]
    let app = clap::App::new("composd_cmd")
        .subcommand(
            clap::SubCommand::with_name("staged-apex-compile"))
        .subcommand(
            clap::SubCommand::with_name("test-compile")
                .arg(clap::Arg::with_name("prefer-staged").long("prefer-staged")),
        );
    let args = app.get_matches();

    ProcessState::start_thread_pool();

    match args.subcommand() {
        ("staged-apex-compile", _) => run_staged_apex_compile()?,
        ("test-compile", Some(sub_matches)) => {
            let prefer_staged = sub_matches.is_present("prefer-staged");
            run_test_compile(prefer_staged)?;
        }
        _ => panic!("Unrecognized subcommand"),
    }

    println!("All Ok!");

    Ok(())
}

struct Callback(Arc<State>);

#[derive(Default)]
struct State {
    mutex: Mutex<Option<Outcome>>,
    completed: Condvar,
}

enum Outcome {
    Succeeded,
    Failed(FailureReason, String),
    TaskDied,
}

impl Interface for Callback {}

impl ICompilationTaskCallback for Callback {
    fn onSuccess(&self) -> BinderResult<()> {
        self.0.set_outcome(Outcome::Succeeded);
        Ok(())
    }

    fn onFailure(&self, reason: FailureReason, message: &str) -> BinderResult<()> {
        self.0.set_outcome(Outcome::Failed(reason, message.to_owned()));
        Ok(())
    }
}

impl State {
    fn set_outcome(&self, outcome: Outcome) {
        let mut guard = self.mutex.lock().unwrap();
        *guard = Some(outcome);
        drop(guard);
        self.completed.notify_all();
    }

    fn wait(&self, duration: Duration) -> Result<Outcome> {
        let (mut outcome, result) = self
            .completed
            .wait_timeout_while(self.mutex.lock().unwrap(), duration, |outcome| outcome.is_none())
            .unwrap();
        if result.timed_out() {
            bail!("Timed out waiting for compilation")
        }
        Ok(outcome.take().unwrap())
    }
}

fn run_staged_apex_compile() -> Result<()> {
    run_async_compilation(|service, callback| service.startStagedApexCompile(callback))
}

fn run_test_compile(prefer_staged: bool) -> Result<()> {
    let apex_source = if prefer_staged { ApexSource::PreferStaged } else { ApexSource::NoStaged };
    run_async_compilation(|service, callback| service.startTestCompile(apex_source, callback))
}

fn run_async_compilation<F>(start_compile_fn: F) -> Result<()>
where
    F: FnOnce(
        &dyn IIsolatedCompilationService,
        &Strong<dyn ICompilationTaskCallback>,
    ) -> BinderResult<Strong<dyn ICompilationTask>>,
{
    let service = wait_for_interface::<dyn IIsolatedCompilationService>("android.system.composd")
        .context("Failed to connect to composd service")?;

    let state = Arc::new(State::default());
    let callback = Callback(state.clone());
    let callback = BnCompilationTaskCallback::new_binder(callback, BinderFeatures::default());
    let task = start_compile_fn(&*service, &callback).context("Compilation failed")?;

    // Make sure composd keeps going even if we don't hold a reference to its service.
    drop(service);

    let state_clone = state.clone();
    let mut death_recipient = DeathRecipient::new(move || {
        eprintln!("CompilationTask died");
        state_clone.set_outcome(Outcome::TaskDied);
    });
    // Note that dropping death_recipient cancels this, so we can't use a temporary here.
    task.as_binder().link_to_death(&mut death_recipient)?;

    println!("Waiting");

    match state.wait(timeouts()?.odrefresh_max_execution_time) {
        Ok(Outcome::Succeeded) => Ok(()),
        Ok(Outcome::TaskDied) => bail!("Compilation task died"),
        Ok(Outcome::Failed(reason, message)) => {
            bail!("Compilation failed: {:?}: {}", reason, message)
        }
        Err(e) => {
            if let Err(e) = task.cancel() {
                eprintln!("Failed to cancel compilation: {:?}", e);
            }
            Err(e)
        }
    }
}
