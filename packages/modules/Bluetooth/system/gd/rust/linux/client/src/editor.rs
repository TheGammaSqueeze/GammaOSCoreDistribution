//! Tools to work with rustyline readline() library.

use futures::Future;

use rustyline::completion::Completer;
use rustyline::error::ReadlineError;
use rustyline::highlight::Highlighter;
use rustyline::hint::Hinter;
use rustyline::validate::Validator;
use rustyline::{CompletionType, Config, Editor};
use rustyline_derive::Helper;

use std::pin::Pin;
use std::sync::{Arc, Mutex};
use std::task::{Context, Poll};

use crate::console_blue;

#[derive(Helper)]
struct BtHelper {
    commands: Vec<String>,
}

impl Completer for BtHelper {
    type Candidate = String;

    // Returns completion based on supported commands.
    // TODO: Add support to autocomplete BT address, command parameters, etc.
    fn complete(
        &self,
        line: &str,
        pos: usize,
        _ctx: &rustyline::Context<'_>,
    ) -> Result<(usize, Vec<String>), ReadlineError> {
        let slice = &line[..pos];
        let mut completions = vec![];

        for cmd in self.commands.iter() {
            if cmd.starts_with(slice) {
                completions.push(cmd.clone());
            }
        }

        Ok((0, completions))
    }
}

impl Hinter for BtHelper {
    type Hint = String;
}

impl Highlighter for BtHelper {}

impl Validator for BtHelper {}

/// A future that does async readline().
///
/// async readline() is implemented by spawning a thread for the blocking readline(). While this
/// readline() thread is blocked, it yields back to executor and will wake the executor up when the
/// blocked thread has proceeded and got input from readline().
pub struct AsyncReadline {
    rl: Arc<Mutex<Editor<BtHelper>>>,
    result: Arc<Mutex<Option<rustyline::Result<String>>>>,
}

impl Future for AsyncReadline {
    type Output = rustyline::Result<String>;

    fn poll(self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<rustyline::Result<String>> {
        let option = self.result.lock().unwrap().take();
        if let Some(res) = option {
            return Poll::Ready(res);
        }

        let waker = cx.waker().clone();
        let result_clone = self.result.clone();
        let rl = self.rl.clone();
        std::thread::spawn(move || {
            let readline = rl.lock().unwrap().readline(console_blue!("bluetooth> "));
            *result_clone.lock().unwrap() = Some(readline);
            waker.wake();
        });

        Poll::Pending
    }
}

/// Wrapper of rustyline editor that supports async readline().
pub struct AsyncEditor {
    rl: Arc<Mutex<Editor<BtHelper>>>,
}

impl AsyncEditor {
    /// Creates new async rustyline editor.
    ///
    /// * `commands` - List of commands for autocomplete.
    pub fn new(commands: Vec<String>) -> AsyncEditor {
        let builder = Config::builder()
            .auto_add_history(true)
            .history_ignore_dups(true)
            .completion_type(CompletionType::List);
        let config = builder.build();
        let mut rl = rustyline::Editor::with_config(config);
        let helper = BtHelper { commands };
        rl.set_helper(Some(helper));
        AsyncEditor { rl: Arc::new(Mutex::new(rl)) }
    }

    /// Does async readline().
    ///
    /// Returns a future that will do the readline() when await-ed. This does not block the thread
    /// but rather yields to the executor while waiting for a command to be entered.
    pub fn readline(&self) -> AsyncReadline {
        AsyncReadline { rl: self.rl.clone(), result: Arc::new(Mutex::new(None)) }
    }
}
