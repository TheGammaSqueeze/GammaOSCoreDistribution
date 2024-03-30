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

use std::future::Future;
use std::pin::Pin;
use std::task::{Context, Poll};
use std::time::Duration;

use tokio::time::{sleep, Sleep};

/// Pinned Sleep instance. It can be used in tokio::select! macro.
pub(super) struct PinSleep(Pin<Box<Sleep>>);

impl PinSleep {
    pub fn new(duration: Duration) -> Self {
        Self(Box::pin(sleep(duration)))
    }
}

impl Future for PinSleep {
    type Output = ();

    fn poll(mut self: Pin<&mut Self>, cx: &mut Context<'_>) -> Poll<()> {
        self.0.as_mut().poll(cx)
    }
}

/// Generate the setter method for the field of the struct for the builder pattern.
macro_rules! builder_field {
    ($field:ident, $ty:ty, $wrap:expr) => {
        pub fn $field(&mut self, value: $ty) -> &mut Self {
            self.$field = $wrap(value);
            self
        }
    };
    ($field:ident, $ty:ty) => {
        builder_field!($field, $ty, ::std::convert::identity);
    };
}
pub(crate) use builder_field;

#[cfg(test)]
pub fn init_test_logging() {
    let _ = env_logger::builder().is_test(true).try_init();
}
