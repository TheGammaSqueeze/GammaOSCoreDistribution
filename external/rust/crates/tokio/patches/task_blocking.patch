diff --git a/tests/task_blocking.rs b/tests/task_blocking.rs
index 82bef8a..d9514d2 100644
--- a/tests/task_blocking.rs
+++ b/tests/task_blocking.rs
@@ -114,6 +114,7 @@ fn can_enter_basic_rt_from_within_block_in_place() {
 }
 
 #[test]
+#[cfg(not(target_os = "android"))]
 fn useful_panic_message_when_dropping_rt_in_rt() {
     use std::panic::{catch_unwind, AssertUnwindSafe};
 
