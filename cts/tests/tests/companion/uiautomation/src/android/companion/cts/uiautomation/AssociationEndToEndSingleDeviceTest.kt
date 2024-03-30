package android.companion.cts.uiautomation

import android.companion.AssociationRequest.DEVICE_PROFILE_APP_STREAMING
import android.companion.AssociationRequest.DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
import android.companion.AssociationRequest.DEVICE_PROFILE_COMPUTER

import android.platform.test.annotations.AppModeFull
import com.android.compatibility.common.util.FeatureUtil
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

import org.junit.Assume.assumeFalse

/**
 * Tests the Association Flow end-to-end.
 *
 * Build/Install/Run:
 * atest CtsCompanionDeviceManagerUiAutomationTestCases:AssociationEndToEndSingleDeviceTest
 */
@AppModeFull(reason = "CompanionDeviceManager APIs are not available to the instant apps.")
@RunWith(Parameterized::class)
class AssociationEndToEndSingleDeviceTest(
    profile: String?,
    profilePermission: String?,
    profileName: String // Used only by the Parameterized test runner for tagging.
) : UiAutomationTestBase(profile, profilePermission) {

    override fun setUp() {
        super.setUp()

        assumeFalse(FeatureUtil.isWatch())
        // TODO(b/211722613): Add support for DEVICE_PROFILE_APP_STREAMING
        // DEVICE_PROFILE_COMPUTER and DEVICE_PROFILE_AUTOMOTIVE_PROJECTION
        // profiles in the confirmation UI (the "single_device" flow variant).
        assumeFalse(profile == DEVICE_PROFILE_COMPUTER)
        assumeFalse(profile == DEVICE_PROFILE_APP_STREAMING)
        assumeFalse(profile == DEVICE_PROFILE_AUTOMOTIVE_PROJECTION)
    }

    @Test
    fun test_userRejected() =
            super.test_userRejected(singleDevice = true, selfManaged = false, displayName = null)

    @Test
    fun test_userDismissed() =
            super.test_userDismissed(singleDevice = true, selfManaged = false, displayName = null)

    @Test
    fun test_timeout() = super.test_timeout(singleDevice = true)

    @Test
    fun test_userConfirmed() = super.test_userConfirmed_foundDevice(singleDevice = true) {
        // Wait until a device is found, which should activate the "positive" button, and click on
        // the button.
        confirmationUi.waitUntilPositiveButtonIsEnabledAndClick()
    }

    companion object {
        /**
         * List of (profile, permission, name) tuples that represent all supported profiles and
         * null.
         * Each test will be suffixed with "[profile=<NAME>]", e.g.: "[profile=WATCH]".
         */
        @Parameterized.Parameters(name = "profile={2}")
        @JvmStatic
        fun parameters() = supportedProfilesAndNull()
    }
}