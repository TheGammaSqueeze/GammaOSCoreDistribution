/*
 * Copyright (C) 2020 The Android Open Source Project
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

package android.net

import android.app.usage.NetworkStatsManager.NETWORK_TYPE_5G_NSA
import android.content.Context
import android.net.ConnectivityManager.TYPE_MOBILE
import android.net.ConnectivityManager.TYPE_WIFI
import android.net.NetworkIdentity.OEM_NONE
import android.net.NetworkIdentity.OEM_PAID
import android.net.NetworkIdentity.OEM_PRIVATE
import android.net.NetworkIdentity.buildNetworkIdentity
import android.net.NetworkStats.DEFAULT_NETWORK_ALL
import android.net.NetworkStats.METERED_ALL
import android.net.NetworkStats.METERED_NO
import android.net.NetworkStats.METERED_YES
import android.net.NetworkStats.ROAMING_ALL
import android.net.NetworkTemplate.MATCH_MOBILE
import android.net.NetworkTemplate.MATCH_MOBILE_WILDCARD
import android.net.NetworkTemplate.MATCH_WIFI
import android.net.NetworkTemplate.MATCH_WIFI_WILDCARD
import android.net.NetworkTemplate.NETWORK_TYPE_ALL
import android.net.NetworkTemplate.OEM_MANAGED_ALL
import android.net.NetworkTemplate.OEM_MANAGED_NO
import android.net.NetworkTemplate.OEM_MANAGED_YES
import android.net.NetworkTemplate.WIFI_NETWORK_KEY_ALL
import android.net.NetworkTemplate.buildTemplateCarrierMetered
import android.net.NetworkTemplate.buildTemplateMobileAll
import android.net.NetworkTemplate.buildTemplateMobileWildcard
import android.net.NetworkTemplate.buildTemplateMobileWithRatType
import android.net.NetworkTemplate.buildTemplateWifi
import android.net.NetworkTemplate.buildTemplateWifiWildcard
import android.net.NetworkTemplate.normalize
import android.net.wifi.WifiInfo
import android.os.Build
import android.telephony.TelephonyManager
import com.android.net.module.util.NetworkStatsUtils.SUBSCRIBER_ID_MATCH_RULE_EXACT
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.DevSdkIgnoreRunner
import com.android.testutils.assertParcelSane
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.MockitoAnnotations
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

private const val TEST_IMSI1 = "imsi1"
private const val TEST_IMSI2 = "imsi2"
private const val TEST_IMSI3 = "imsi3"
private const val TEST_WIFI_KEY1 = "wifiKey1"
private const val TEST_WIFI_KEY2 = "wifiKey2"

@RunWith(DevSdkIgnoreRunner::class)
@DevSdkIgnoreRule.IgnoreUpTo(Build.VERSION_CODES.R)
class NetworkTemplateTest {
    private val mockContext = mock(Context::class.java)
    private val mockWifiInfo = mock(WifiInfo::class.java)

    private fun buildMobileNetworkState(subscriberId: String): NetworkStateSnapshot =
            buildNetworkState(TYPE_MOBILE, subscriberId = subscriberId)
    private fun buildWifiNetworkState(subscriberId: String?, wifiKey: String?):
            NetworkStateSnapshot = buildNetworkState(TYPE_WIFI,
            subscriberId = subscriberId, wifiKey = wifiKey)

    private fun buildNetworkState(
        type: Int,
        subscriberId: String? = null,
        wifiKey: String? = null,
        oemManaged: Int = OEM_NONE,
        metered: Boolean = true
    ): NetworkStateSnapshot {
        `when`(mockWifiInfo.getNetworkKey()).thenReturn(wifiKey)
        val lp = LinkProperties()
        val caps = NetworkCapabilities().apply {
            setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED, !metered)
            setCapability(NetworkCapabilities.NET_CAPABILITY_NOT_ROAMING, true)
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PAID,
                    (oemManaged and OEM_PAID) == OEM_PAID)
            setCapability(NetworkCapabilities.NET_CAPABILITY_OEM_PRIVATE,
                    (oemManaged and OEM_PRIVATE) == OEM_PRIVATE)
            setTransportInfo(mockWifiInfo)
        }
        return NetworkStateSnapshot(mock(Network::class.java), caps, lp, subscriberId, type)
    }

    private fun NetworkTemplate.assertMatches(ident: NetworkIdentity) =
            assertTrue(matches(ident), "$this does not match $ident")

    private fun NetworkTemplate.assertDoesNotMatch(ident: NetworkIdentity) =
            assertFalse(matches(ident), "$this should match $ident")

    @Before
    fun setup() {
        MockitoAnnotations.initMocks(this)
    }

    @Test
    fun testWifiWildcardMatches() {
        val templateWifiWildcard = buildTemplateWifiWildcard()

        val identMobileImsi1 = buildNetworkIdentity(mockContext,
                buildMobileNetworkState(TEST_IMSI1),
                false, TelephonyManager.NETWORK_TYPE_UMTS)
        val identWifiImsiNullKey1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(null, TEST_WIFI_KEY1), true, 0)
        val identWifiImsi1Key1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI1, TEST_WIFI_KEY1), true, 0)

        templateWifiWildcard.assertDoesNotMatch(identMobileImsi1)
        templateWifiWildcard.assertMatches(identWifiImsiNullKey1)
        templateWifiWildcard.assertMatches(identWifiImsi1Key1)
    }

    @Test
    fun testWifiMatches() {
        val templateWifiKey1 = buildTemplateWifi(TEST_WIFI_KEY1)
        val templateWifiKey1ImsiNull = buildTemplateWifi(TEST_WIFI_KEY1, null)
        val templateWifiKey1Imsi1 = buildTemplateWifi(TEST_WIFI_KEY1, TEST_IMSI1)
        val templateWifiKeyAllImsi1 = buildTemplateWifi(WIFI_NETWORK_KEY_ALL, TEST_IMSI1)

        val identMobile1 = buildNetworkIdentity(mockContext, buildMobileNetworkState(TEST_IMSI1),
                false, TelephonyManager.NETWORK_TYPE_UMTS)
        val identWifiImsiNullKey1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(null, TEST_WIFI_KEY1), true, 0)
        val identWifiImsi1Key1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI1, TEST_WIFI_KEY1), true, 0)
        val identWifiImsi2Key1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI2, TEST_WIFI_KEY1), true, 0)
        val identWifiImsi1Key2 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI1, TEST_WIFI_KEY2), true, 0)

        // Verify that template with WiFi Network Key only matches any subscriberId and
        // specific WiFi Network Key.
        templateWifiKey1.assertDoesNotMatch(identMobile1)
        templateWifiKey1.assertMatches(identWifiImsiNullKey1)
        templateWifiKey1.assertMatches(identWifiImsi1Key1)
        templateWifiKey1.assertMatches(identWifiImsi2Key1)
        templateWifiKey1.assertDoesNotMatch(identWifiImsi1Key2)

        // Verify that template with WiFi Network Key1 and null imsi matches any network with
        // WiFi Network Key1 and null imsi.
        templateWifiKey1ImsiNull.assertDoesNotMatch(identMobile1)
        templateWifiKey1ImsiNull.assertMatches(identWifiImsiNullKey1)
        templateWifiKey1ImsiNull.assertDoesNotMatch(identWifiImsi1Key1)
        templateWifiKey1ImsiNull.assertDoesNotMatch(identWifiImsi2Key1)
        templateWifiKey1ImsiNull.assertDoesNotMatch(identWifiImsi1Key2)

        // Verify that template with WiFi Network Key1 and imsi1 matches any network with
        // WiFi Network Key1 and imsi1.
        templateWifiKey1Imsi1.assertDoesNotMatch(identMobile1)
        templateWifiKey1Imsi1.assertDoesNotMatch(identWifiImsiNullKey1)
        templateWifiKey1Imsi1.assertMatches(identWifiImsi1Key1)
        templateWifiKey1Imsi1.assertDoesNotMatch(identWifiImsi2Key1)
        templateWifiKey1Imsi1.assertDoesNotMatch(identWifiImsi1Key2)

        // Verify that template with WiFi Network Key all and imsi1 matches any network with
        // any WiFi Network Key and imsi1.
        templateWifiKeyAllImsi1.assertDoesNotMatch(identMobile1)
        templateWifiKeyAllImsi1.assertDoesNotMatch(identWifiImsiNullKey1)
        templateWifiKeyAllImsi1.assertMatches(identWifiImsi1Key1)
        templateWifiKeyAllImsi1.assertDoesNotMatch(identWifiImsi2Key1)
        templateWifiKeyAllImsi1.assertMatches(identWifiImsi1Key2)
    }

    @Test
    fun testMobileMatches() {
        val templateMobileImsi1 = buildTemplateMobileAll(TEST_IMSI1)
        val templateMobileImsi2WithRatType = buildTemplateMobileWithRatType(TEST_IMSI2,
                TelephonyManager.NETWORK_TYPE_UMTS, METERED_YES)

        val mobileImsi1 = buildNetworkState(TYPE_MOBILE, TEST_IMSI1, null /* wifiKey */,
                OEM_NONE, true /* metered */)
        val identMobile1 = buildNetworkIdentity(mockContext, mobileImsi1,
                false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        val mobileImsi2 = buildMobileNetworkState(TEST_IMSI2)
        val identMobile2Umts = buildNetworkIdentity(mockContext, mobileImsi2,
                false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)

        val identWifiImsi1Key1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI1, TEST_WIFI_KEY1), true, 0)

        // Verify that the template matches type and the subscriberId.
        templateMobileImsi1.assertMatches(identMobile1)
        templateMobileImsi2WithRatType.assertMatches(identMobile2Umts)

        // Verify that the template does not match the different subscriberId.
        templateMobileImsi1.assertDoesNotMatch(identMobile2Umts)
        templateMobileImsi2WithRatType.assertDoesNotMatch(identMobile1)

        // Verify that the different type does not match.
        templateMobileImsi1.assertDoesNotMatch(identWifiImsi1Key1)
    }

    @Test
    fun testMobileWildcardMatches() {
        val templateMobileWildcard = buildTemplateMobileWildcard()
        val templateMobileNullImsiWithRatType = buildTemplateMobileWithRatType(null,
                TelephonyManager.NETWORK_TYPE_UMTS, METERED_ALL)

        val mobileImsi1 = buildMobileNetworkState(TEST_IMSI1)
        val identMobile1 = buildNetworkIdentity(mockContext, mobileImsi1,
                false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)

        // Verify that the template matches any subscriberId.
        templateMobileWildcard.assertMatches(identMobile1)
        templateMobileNullImsiWithRatType.assertMatches(identMobile1)

        val identWifiImsi1Key1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI1, TEST_WIFI_KEY1), true, 0)

        // Verify that the different type does not match.
        templateMobileWildcard.assertDoesNotMatch(identWifiImsi1Key1)
        templateMobileNullImsiWithRatType.assertDoesNotMatch(identWifiImsi1Key1)
    }

    @Test
    fun testCarrierMeteredMatches() {
        val templateCarrierImsi1Metered = buildTemplateCarrierMetered(TEST_IMSI1)

        val mobileImsi1 = buildMobileNetworkState(TEST_IMSI1)
        val mobileImsi1Unmetered = buildNetworkState(TYPE_MOBILE, TEST_IMSI1,
                null /* wifiKey */, OEM_NONE, false /* metered */)
        val mobileImsi2 = buildMobileNetworkState(TEST_IMSI2)
        val wifiKey1 = buildWifiNetworkState(null /* subscriberId */,
                TEST_WIFI_KEY1)
        val wifiImsi1Key1 = buildWifiNetworkState(TEST_IMSI1, TEST_WIFI_KEY1)
        val wifiImsi1Key1Unmetered = buildNetworkState(TYPE_WIFI, TEST_IMSI1,
                TEST_WIFI_KEY1, OEM_NONE, false /* metered */)

        val identMobileImsi1Metered = buildNetworkIdentity(mockContext,
                mobileImsi1, false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        val identMobileImsi1Unmetered = buildNetworkIdentity(mockContext,
                mobileImsi1Unmetered, false /* defaultNetwork */,
                TelephonyManager.NETWORK_TYPE_UMTS)
        val identMobileImsi2Metered = buildNetworkIdentity(mockContext,
                mobileImsi2, false /* defaultNetwork */, TelephonyManager.NETWORK_TYPE_UMTS)
        val identWifiKey1Metered = buildNetworkIdentity(
                mockContext, wifiKey1, true /* defaultNetwork */, 0 /* subType */)
        val identCarrierWifiImsi1Metered = buildNetworkIdentity(
                mockContext, wifiImsi1Key1, true /* defaultNetwork */, 0 /* subType */)
        val identCarrierWifiImsi1NonMetered = buildNetworkIdentity(mockContext,
                wifiImsi1Key1Unmetered, true /* defaultNetwork */, 0 /* subType */)

        templateCarrierImsi1Metered.assertMatches(identMobileImsi1Metered)
        templateCarrierImsi1Metered.assertDoesNotMatch(identMobileImsi1Unmetered)
        templateCarrierImsi1Metered.assertDoesNotMatch(identMobileImsi2Metered)
        templateCarrierImsi1Metered.assertDoesNotMatch(identWifiKey1Metered)
        templateCarrierImsi1Metered.assertMatches(identCarrierWifiImsi1Metered)
        templateCarrierImsi1Metered.assertDoesNotMatch(identCarrierWifiImsi1NonMetered)
    }

    // TODO: Refactor this test to reduce the line of codes.
    @Test
    fun testRatTypeGroupMatches() {
        val stateMobileImsi1Metered = buildMobileNetworkState(TEST_IMSI1)
        val stateMobileImsi1NonMetered = buildNetworkState(TYPE_MOBILE, TEST_IMSI1,
                null /* wifiKey */, OEM_NONE, false /* metered */)
        val stateMobileImsi2NonMetered = buildNetworkState(TYPE_MOBILE, TEST_IMSI2,
                null /* wifiKey */, OEM_NONE, false /* metered */)

        // Build UMTS template that matches mobile identities with RAT in the same
        // group with any IMSI. See {@link NetworkTemplate#getCollapsedRatType}.
        val templateUmtsMetered = buildTemplateMobileWithRatType(null,
                TelephonyManager.NETWORK_TYPE_UMTS, METERED_YES)
        // Build normal template that matches mobile identities with any RAT and IMSI.
        val templateAllMetered = buildTemplateMobileWithRatType(null, NETWORK_TYPE_ALL,
                METERED_YES)
        // Build template with UNKNOWN RAT that matches mobile identities with RAT that
        // cannot be determined.
        val templateUnknownMetered =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_UNKNOWN,
                METERED_YES)

        val templateUmtsNonMetered = buildTemplateMobileWithRatType(null,
                TelephonyManager.NETWORK_TYPE_UMTS, METERED_NO)
        val templateAllNonMetered = buildTemplateMobileWithRatType(null, NETWORK_TYPE_ALL,
                METERED_NO)
        val templateUnknownNonMetered =
                buildTemplateMobileWithRatType(null, TelephonyManager.NETWORK_TYPE_UNKNOWN,
                METERED_NO)

        val identUmtsMetered = buildNetworkIdentity(
                mockContext, stateMobileImsi1Metered, false, TelephonyManager.NETWORK_TYPE_UMTS)
        val identHsdpaMetered = buildNetworkIdentity(
                mockContext, stateMobileImsi1Metered, false, TelephonyManager.NETWORK_TYPE_HSDPA)
        val identLteMetered = buildNetworkIdentity(
                mockContext, stateMobileImsi1Metered, false, TelephonyManager.NETWORK_TYPE_LTE)
        val identCombinedMetered = buildNetworkIdentity(
                mockContext, stateMobileImsi1Metered, false, NetworkTemplate.NETWORK_TYPE_ALL)
        val identImsi2UmtsMetered = buildNetworkIdentity(mockContext,
                buildMobileNetworkState(TEST_IMSI2), false, TelephonyManager.NETWORK_TYPE_UMTS)
        val identWifi = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(null, TEST_WIFI_KEY1), true, 0)

        val identUmtsNonMetered = buildNetworkIdentity(
                mockContext, stateMobileImsi1NonMetered, false, TelephonyManager.NETWORK_TYPE_UMTS)
        val identHsdpaNonMetered = buildNetworkIdentity(
                mockContext, stateMobileImsi1NonMetered, false,
                TelephonyManager.NETWORK_TYPE_HSDPA)
        val identLteNonMetered = buildNetworkIdentity(
                mockContext, stateMobileImsi1NonMetered, false, TelephonyManager.NETWORK_TYPE_LTE)
        val identCombinedNonMetered = buildNetworkIdentity(
                mockContext, stateMobileImsi1NonMetered, false, NetworkTemplate.NETWORK_TYPE_ALL)
        val identImsi2UmtsNonMetered = buildNetworkIdentity(mockContext,
                stateMobileImsi2NonMetered, false, TelephonyManager.NETWORK_TYPE_UMTS)

        // Assert that identity with the same RAT and meteredness matches.
        // Verify metered template.
        templateUmtsMetered.assertMatches(identUmtsMetered)
        templateAllMetered.assertMatches(identUmtsMetered)
        templateUnknownMetered.assertDoesNotMatch(identUmtsMetered)
        // Verify non-metered template.
        templateUmtsNonMetered.assertMatches(identUmtsNonMetered)
        templateAllNonMetered.assertMatches(identUmtsNonMetered)
        templateUnknownNonMetered.assertDoesNotMatch(identUmtsNonMetered)

        // Assert that identity with the same RAT but meteredness is different.
        // Thus, it does not match.
        templateUmtsNonMetered.assertDoesNotMatch(identUmtsMetered)
        templateAllNonMetered.assertDoesNotMatch(identUmtsMetered)

        // Assert that identity with the RAT within the same group matches.
        // Verify metered template.
        templateUmtsMetered.assertMatches(identHsdpaMetered)
        templateAllMetered.assertMatches(identHsdpaMetered)
        templateUnknownMetered.assertDoesNotMatch(identHsdpaMetered)
        // Verify non-metered template.
        templateUmtsNonMetered.assertMatches(identHsdpaNonMetered)
        templateAllNonMetered.assertMatches(identHsdpaNonMetered)
        templateUnknownNonMetered.assertDoesNotMatch(identHsdpaNonMetered)

        // Assert that identity with the RAT out of the same group only matches template with
        // NETWORK_TYPE_ALL.
        // Verify metered template.
        templateUmtsMetered.assertDoesNotMatch(identLteMetered)
        templateAllMetered.assertMatches(identLteMetered)
        templateUnknownMetered.assertDoesNotMatch(identLteMetered)
        // Verify non-metered template.
        templateUmtsNonMetered.assertDoesNotMatch(identLteNonMetered)
        templateAllNonMetered.assertMatches(identLteNonMetered)
        templateUnknownNonMetered.assertDoesNotMatch(identLteNonMetered)
        // Verify non-metered template does not match identity with metered.
        templateAllNonMetered.assertDoesNotMatch(identLteMetered)

        // Assert that identity with combined RAT only matches with template with NETWORK_TYPE_ALL
        // and NETWORK_TYPE_UNKNOWN.
        // Verify metered template.
        templateUmtsMetered.assertDoesNotMatch(identCombinedMetered)
        templateAllMetered.assertMatches(identCombinedMetered)
        templateUnknownMetered.assertMatches(identCombinedMetered)
        // Verify non-metered template.
        templateUmtsNonMetered.assertDoesNotMatch(identCombinedNonMetered)
        templateAllNonMetered.assertMatches(identCombinedNonMetered)
        templateUnknownNonMetered.assertMatches(identCombinedNonMetered)
        // Verify that identity with metered does not match non-metered template.
        templateAllNonMetered.assertDoesNotMatch(identCombinedMetered)
        templateUnknownNonMetered.assertDoesNotMatch(identCombinedMetered)

        // Assert that identity with different IMSI matches.
        // Verify metered template.
        templateUmtsMetered.assertMatches(identImsi2UmtsMetered)
        templateAllMetered.assertMatches(identImsi2UmtsMetered)
        templateUnknownMetered.assertDoesNotMatch(identImsi2UmtsMetered)
        // Verify non-metered template.
        templateUmtsNonMetered.assertMatches(identImsi2UmtsNonMetered)
        templateAllNonMetered.assertMatches(identImsi2UmtsNonMetered)
        templateUnknownNonMetered.assertDoesNotMatch(identImsi2UmtsNonMetered)
        // Verify that the same RAT but different meteredness should not match.
        templateUmtsNonMetered.assertDoesNotMatch(identImsi2UmtsMetered)
        templateAllNonMetered.assertDoesNotMatch(identImsi2UmtsMetered)

        // Assert that wifi identity does not match.
        templateUmtsMetered.assertDoesNotMatch(identWifi)
        templateUnknownMetered.assertDoesNotMatch(identWifi)
        templateUmtsNonMetered.assertDoesNotMatch(identWifi)
        templateUnknownNonMetered.assertDoesNotMatch(identWifi)
    }

    @Test
    fun testParcelUnparcel() {
        val templateMobile = NetworkTemplate(MATCH_MOBILE, TEST_IMSI1, null,
                arrayOf<String>(), METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL,
                TelephonyManager.NETWORK_TYPE_LTE, OEM_MANAGED_ALL,
                SUBSCRIBER_ID_MATCH_RULE_EXACT)
        val templateWifi = NetworkTemplate(MATCH_WIFI, null, null,
                arrayOf(TEST_WIFI_KEY1), METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL, 0,
                OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_EXACT)
        val templateOem = NetworkTemplate(MATCH_MOBILE, null, null,
                arrayOf<String>(), METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL, 0,
                OEM_MANAGED_YES, SUBSCRIBER_ID_MATCH_RULE_EXACT)
        assertParcelSane(templateMobile, 10)
        assertParcelSane(templateWifi, 10)
        assertParcelSane(templateOem, 10)
    }

    // Verify NETWORK_TYPE_* constants in NetworkTemplate do not conflict with
    // TelephonyManager#NETWORK_TYPE_* constants.
    @Test
    fun testNetworkTypeConstants() {
        for (ratType in TelephonyManager.getAllNetworkTypes()) {
            assertNotEquals(NETWORK_TYPE_ALL, ratType)
            assertNotEquals(NETWORK_TYPE_5G_NSA, ratType)
        }
    }

    @Test
    fun testOemNetworkConstants() {
        val constantValues = arrayOf(OEM_MANAGED_YES, OEM_MANAGED_ALL, OEM_MANAGED_NO,
                OEM_PAID, OEM_PRIVATE, OEM_PAID or OEM_PRIVATE)

        // Verify that "not OEM managed network" constants are equal.
        assertEquals(OEM_MANAGED_NO, OEM_NONE)

        // Verify the constants don't conflict.
        assertEquals(constantValues.size, constantValues.distinct().count())
    }

    /**
     * Helper to enumerate and assert OEM managed wifi and mobile {@code NetworkTemplate}s match
     * their the appropriate OEM managed {@code NetworkIdentity}s.
     *
     * @param networkType {@code TYPE_MOBILE} or {@code TYPE_WIFI}
     * @param matchType A match rule from {@code NetworkTemplate.MATCH_*} corresponding to the
     *         networkType.
     * @param subscriberId To be populated with {@code TEST_IMSI*} only if networkType is
     *         {@code TYPE_MOBILE}. May be left as null when matchType is
     *         {@link NetworkTemplate.MATCH_MOBILE_WILDCARD}.
     * @param templateWifiKey Top be populated with {@code TEST_WIFI_KEY*} only if networkType is
     *         {@code TYPE_WIFI}. May be left as null when matchType is
     *         {@link NetworkTemplate.MATCH_WIFI_WILDCARD}.
     * @param identWifiKey If networkType is {@code TYPE_WIFI}, this value must *NOT* be null. Provide
     *         one of {@code TEST_WIFI_KEY*}.
     */
    private fun matchOemManagedIdent(
        networkType: Int,
        matchType: Int,
        subscriberId: String? = null,
        templateWifiKey: String? = null,
        identWifiKey: String? = null
    ) {
        val oemManagedStates = arrayOf(OEM_NONE, OEM_PAID, OEM_PRIVATE, OEM_PAID or OEM_PRIVATE)
        val matchSubscriberIds = arrayOf(subscriberId)
        val matchWifiNetworkKeys = arrayOf(templateWifiKey)

        val templateOemYes = NetworkTemplate(matchType, subscriberId, matchSubscriberIds,
                matchWifiNetworkKeys, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL, OEM_MANAGED_YES,
                SUBSCRIBER_ID_MATCH_RULE_EXACT)
        val templateOemAll = NetworkTemplate(matchType, subscriberId, matchSubscriberIds,
                matchWifiNetworkKeys, METERED_ALL, ROAMING_ALL,
                DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL, OEM_MANAGED_ALL,
                SUBSCRIBER_ID_MATCH_RULE_EXACT)

        for (identityOemManagedState in oemManagedStates) {
            val ident = buildNetworkIdentity(mockContext, buildNetworkState(networkType,
                    subscriberId, identWifiKey, identityOemManagedState),
                    /*defaultNetwork=*/false, /*subType=*/0)

            // Create a template with each OEM managed type and match it against the NetworkIdentity
            for (templateOemManagedState in oemManagedStates) {
                val template = NetworkTemplate(matchType, subscriberId, matchSubscriberIds,
                        matchWifiNetworkKeys, METERED_ALL, ROAMING_ALL,
                        DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL, templateOemManagedState,
                        SUBSCRIBER_ID_MATCH_RULE_EXACT)
                if (identityOemManagedState == templateOemManagedState) {
                    template.assertMatches(ident)
                } else {
                    template.assertDoesNotMatch(ident)
                }
            }
            // OEM_MANAGED_ALL ignores OEM state.
            templateOemAll.assertMatches(ident)
            if (identityOemManagedState == OEM_NONE) {
                // OEM_MANAGED_YES matches everything except OEM_NONE.
                templateOemYes.assertDoesNotMatch(ident)
            } else {
                templateOemYes.assertMatches(ident)
            }
        }
    }

    @Test
    fun testOemManagedMatchesIdent() {
        matchOemManagedIdent(TYPE_MOBILE, MATCH_MOBILE, subscriberId = TEST_IMSI1)
        matchOemManagedIdent(TYPE_MOBILE, MATCH_MOBILE_WILDCARD)
        matchOemManagedIdent(TYPE_WIFI, MATCH_WIFI, templateWifiKey = TEST_WIFI_KEY1,
                identWifiKey = TEST_WIFI_KEY1)
        matchOemManagedIdent(TYPE_WIFI, MATCH_WIFI_WILDCARD,
                identWifiKey = TEST_WIFI_KEY1)
    }

    @Test
    fun testNormalize() {
        var mergedImsiList = listOf(arrayOf(TEST_IMSI1, TEST_IMSI2))
        val identMobileImsi1 = buildNetworkIdentity(mockContext,
                buildMobileNetworkState(TEST_IMSI1), false /* defaultNetwork */,
                TelephonyManager.NETWORK_TYPE_UMTS)
        val identMobileImsi2 = buildNetworkIdentity(mockContext,
                buildMobileNetworkState(TEST_IMSI2), false /* defaultNetwork */,
                TelephonyManager.NETWORK_TYPE_UMTS)
        val identMobileImsi3 = buildNetworkIdentity(mockContext,
                buildMobileNetworkState(TEST_IMSI3), false /* defaultNetwork */,
                TelephonyManager.NETWORK_TYPE_UMTS)
        val identWifiImsi1Key1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI1, TEST_WIFI_KEY1), true, 0)
        val identWifiImsi2Key1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI2, TEST_WIFI_KEY1), true, 0)
        val identWifiImsi3WifiKey1 = buildNetworkIdentity(
                mockContext, buildWifiNetworkState(TEST_IMSI3, TEST_WIFI_KEY1), true, 0)

        normalize(buildTemplateMobileAll(TEST_IMSI1), mergedImsiList).also {
            it.assertMatches(identMobileImsi1)
            it.assertMatches(identMobileImsi2)
            it.assertDoesNotMatch(identMobileImsi3)
        }
        normalize(buildTemplateCarrierMetered(TEST_IMSI1), mergedImsiList).also {
            it.assertMatches(identMobileImsi1)
            it.assertMatches(identMobileImsi2)
            it.assertDoesNotMatch(identMobileImsi3)
        }
        normalize(buildTemplateWifi(TEST_WIFI_KEY1, TEST_IMSI1), mergedImsiList).also {
            it.assertMatches(identWifiImsi1Key1)
            it.assertMatches(identWifiImsi2Key1)
            it.assertDoesNotMatch(identWifiImsi3WifiKey1)
        }
        normalize(buildTemplateMobileWildcard(), mergedImsiList).also {
            it.assertMatches(identMobileImsi1)
            it.assertMatches(identMobileImsi2)
            it.assertMatches(identMobileImsi3)
        }
    }
}
