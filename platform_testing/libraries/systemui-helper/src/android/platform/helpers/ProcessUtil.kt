package android.platform.helpers

import android.platform.uiautomator_helpers.DeviceHelpers.shell
import android.platform.uiautomator_helpers.DeviceHelpers.uiDevice
import android.platform.uiautomator_helpers.WaitUtils.ensureThat
import android.util.Log

/** Allows to execute operations such as restart on a process identififed by [packageName]. */
class ProcessUtil(private val packageName: String) {

    /** Restart [packageName] running `am crash <package-name>`. */
    fun restart() {
        val initialPids = pids
        // make sure the lock screen is enable.
        Log.d(TAG, "Old $packageName PIDs=$initialPids)")
        initialPids
            .map { pid -> "kill $pid" }
            .forEach { killCmd ->
                val result = uiDevice.shell(killCmd)
                Log.d(TAG, "Result of \"$killCmd\": \"$result\"")
            }
        ensureThat("All sysui process restarted") { allProcessesRestarted(initialPids) }
    }

    private val pids: List<String>
        get() {
            val pidofResult = uiDevice.shell("pidof $packageName")
            return if (pidofResult.isEmpty()) {
                emptyList()
            } else pidofResult.split("\\s".toRegex())
        }

    private fun allProcessesRestarted(initialPidsList: List<String>): Boolean =
        (pids intersect initialPidsList).isEmpty()

    private companion object {
        const val TAG = "ProcessUtils"
    }
}
