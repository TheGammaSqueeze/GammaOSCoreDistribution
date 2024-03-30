package android.platform.test.rule

import android.platform.uiautomator_helpers.DeviceHelpers.context
import android.provider.Settings
import org.junit.runner.Description

/**
 * Making sure "Stay awake" setting from Developer settings is set so the screen doesn't turn off
 * while tests are running
 *
 * Setting value is bit-based with 4 bits responsible for different types of charging. So the value
 * is device-dependent but non-zero value means the settings is on.
 * See [Settings.Global.STAY_ON_WHILE_PLUGGED_IN] for more information.
 */
class EnsureKeepScreenAwakeSetRule : TestWatcher() {

    override fun starting(description: Description?) {

        val result =
            Settings.Global.getInt(
                context.contentResolver,
                Settings.Global.STAY_ON_WHILE_PLUGGED_IN
            )
        if (result == 0) {
            throw AssertionError("'Stay awake' option in developer settings should be enabled")
        }
    }
}
