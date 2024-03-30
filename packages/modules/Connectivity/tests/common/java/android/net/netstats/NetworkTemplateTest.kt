/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.net.netstats

import android.net.NetworkStats.DEFAULT_NETWORK_ALL
import android.net.NetworkStats.METERED_ALL
import android.net.NetworkStats.METERED_YES
import android.net.NetworkStats.ROAMING_YES
import android.net.NetworkStats.ROAMING_ALL
import android.net.NetworkTemplate
import android.net.NetworkTemplate.MATCH_BLUETOOTH
import android.net.NetworkTemplate.MATCH_CARRIER
import android.net.NetworkTemplate.MATCH_ETHERNET
import android.net.NetworkTemplate.MATCH_MOBILE
import android.net.NetworkTemplate.MATCH_MOBILE_WILDCARD
import android.net.NetworkTemplate.MATCH_PROXY
import android.net.NetworkTemplate.MATCH_WIFI
import android.net.NetworkTemplate.MATCH_WIFI_WILDCARD
import android.net.NetworkTemplate.NETWORK_TYPE_ALL
import android.net.NetworkTemplate.OEM_MANAGED_ALL
import android.telephony.TelephonyManager
import com.android.net.module.util.NetworkStatsUtils.SUBSCRIBER_ID_MATCH_RULE_ALL
import com.android.net.module.util.NetworkStatsUtils.SUBSCRIBER_ID_MATCH_RULE_EXACT
import com.android.testutils.ConnectivityModuleTest
import com.android.testutils.DevSdkIgnoreRule
import com.android.testutils.SC_V2
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

private const val TEST_IMSI1 = "imsi"
private const val TEST_WIFI_KEY1 = "wifiKey1"
private const val TEST_WIFI_KEY2 = "wifiKey2"

@RunWith(JUnit4::class)
@ConnectivityModuleTest
class NetworkTemplateTest {
    @Rule
    @JvmField
    val ignoreRule = DevSdkIgnoreRule(ignoreClassUpTo = SC_V2)

    @Test
    fun testBuilderMatchRules() {
        // Verify unknown match rules cannot construct templates.
        listOf(Integer.MIN_VALUE, -1, Integer.MAX_VALUE).forEach {
            assertFailsWith<IllegalArgumentException> {
                NetworkTemplate.Builder(it).build()
            }
        }

        // Verify hidden match rules cannot construct templates.
        listOf(MATCH_WIFI_WILDCARD, MATCH_MOBILE_WILDCARD, MATCH_PROXY).forEach {
            assertFailsWith<IllegalArgumentException> {
                NetworkTemplate.Builder(it).build()
            }
        }

        // Verify template which matches metered cellular and carrier networks with
        // the given IMSI. See buildTemplateMobileAll and buildTemplateCarrierMetered.
        listOf(MATCH_MOBILE, MATCH_CARRIER).forEach { matchRule ->
            NetworkTemplate.Builder(matchRule).setSubscriberIds(setOf(TEST_IMSI1))
                    .setMeteredness(METERED_YES).build().let {
                        val expectedTemplate = NetworkTemplate(matchRule, TEST_IMSI1,
                                arrayOf(TEST_IMSI1), arrayOf<String>(), METERED_YES,
                                ROAMING_ALL, DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL,
                                OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_EXACT)
                        assertEquals(expectedTemplate, it)
                    }
        }

        // Verify template which matches roaming cellular and carrier networks with
        // the given IMSI.
        listOf(MATCH_MOBILE, MATCH_CARRIER).forEach { matchRule ->
            NetworkTemplate.Builder(matchRule).setSubscriberIds(setOf(TEST_IMSI1))
                    .setRoaming(ROAMING_YES).setMeteredness(METERED_YES).build().let {
                        val expectedTemplate = NetworkTemplate(matchRule, TEST_IMSI1,
                                arrayOf(TEST_IMSI1), arrayOf<String>(), METERED_YES,
                                ROAMING_YES, DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL,
                                OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_EXACT)
                        assertEquals(expectedTemplate, it)
                    }
        }

        // Verify carrier template cannot be created without IMSI.
        assertFailsWith<IllegalArgumentException> {
            NetworkTemplate.Builder(MATCH_CARRIER).build()
        }

        // Verify template which matches metered cellular networks,
        // regardless of IMSI. See buildTemplateMobileWildcard.
        NetworkTemplate.Builder(MATCH_MOBILE).setMeteredness(METERED_YES).build().let {
            val expectedTemplate = NetworkTemplate(MATCH_MOBILE_WILDCARD, null /*subscriberId*/,
                    null /*subscriberIds*/, arrayOf<String>(),
                    METERED_YES, ROAMING_ALL, DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL,
                    OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_ALL)
            assertEquals(expectedTemplate, it)
        }

        // Verify template which matches metered cellular networks and ratType.
        // See NetworkTemplate#buildTemplateMobileWithRatType.
        NetworkTemplate.Builder(MATCH_MOBILE).setSubscriberIds(setOf(TEST_IMSI1))
                .setMeteredness(METERED_YES).setRatType(TelephonyManager.NETWORK_TYPE_UMTS)
                .build().let {
                    val expectedTemplate = NetworkTemplate(MATCH_MOBILE, TEST_IMSI1,
                            arrayOf(TEST_IMSI1), arrayOf<String>(), METERED_YES,
                            ROAMING_ALL, DEFAULT_NETWORK_ALL, TelephonyManager.NETWORK_TYPE_UMTS,
                            OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_EXACT)
                    assertEquals(expectedTemplate, it)
                }

        // Verify template which matches all wifi networks,
        // regardless of Wifi Network Key. See buildTemplateWifiWildcard and buildTemplateWifi.
        NetworkTemplate.Builder(MATCH_WIFI).build().let {
            val expectedTemplate = NetworkTemplate(MATCH_WIFI_WILDCARD, null /*subscriberId*/,
                    null /*subscriberIds*/, arrayOf<String>(),
                    METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL,
                    OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_ALL)
            assertEquals(expectedTemplate, it)
        }

        // Verify template which matches wifi networks with the given Wifi Network Key.
        // See buildTemplateWifi(wifiNetworkKey).
        NetworkTemplate.Builder(MATCH_WIFI).setWifiNetworkKeys(setOf(TEST_WIFI_KEY1)).build().let {
            val expectedTemplate = NetworkTemplate(MATCH_WIFI, null /*subscriberId*/,
                    null /*subscriberIds*/, arrayOf(TEST_WIFI_KEY1),
                    METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL,
                    OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_ALL)
            assertEquals(expectedTemplate, it)
        }

        // Verify template which matches all wifi networks with the
        // given Wifi Network Key, and IMSI. See buildTemplateWifi(wifiNetworkKey, subscriberId).
        NetworkTemplate.Builder(MATCH_WIFI).setSubscriberIds(setOf(TEST_IMSI1))
                .setWifiNetworkKeys(setOf(TEST_WIFI_KEY1)).build().let {
                    val expectedTemplate = NetworkTemplate(MATCH_WIFI, TEST_IMSI1,
                            arrayOf(TEST_IMSI1), arrayOf(TEST_WIFI_KEY1),
                            METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL,
                            OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_EXACT)
                    assertEquals(expectedTemplate, it)
                }

        // Verify template which matches ethernet and bluetooth networks.
        // See buildTemplateEthernet and buildTemplateBluetooth.
        listOf(MATCH_ETHERNET, MATCH_BLUETOOTH).forEach { matchRule ->
            NetworkTemplate.Builder(matchRule).build().let {
                val expectedTemplate = NetworkTemplate(matchRule, null /*subscriberId*/,
                        null /*subscriberIds*/, arrayOf<String>(),
                        METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL,
                        OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_ALL)
                assertEquals(expectedTemplate, it)
            }
        }
    }

    @Test
    fun testBuilderWifiNetworkKeys() {
        // Verify template builder which generates same template with the given different
        // sequence keys.
        NetworkTemplate.Builder(MATCH_WIFI).setWifiNetworkKeys(
                setOf(TEST_WIFI_KEY1, TEST_WIFI_KEY2)).build().let {
            val expectedTemplate = NetworkTemplate.Builder(MATCH_WIFI).setWifiNetworkKeys(
                    setOf(TEST_WIFI_KEY2, TEST_WIFI_KEY1)).build()
            assertEquals(expectedTemplate, it)
        }

        // Verify template which matches non-wifi networks with the given key is invalid.
        listOf(MATCH_MOBILE, MATCH_CARRIER, MATCH_ETHERNET, MATCH_BLUETOOTH, -1,
                Integer.MAX_VALUE).forEach { matchRule ->
            assertFailsWith<IllegalArgumentException> {
                NetworkTemplate.Builder(matchRule).setWifiNetworkKeys(setOf(TEST_WIFI_KEY1)).build()
            }
        }

        // Verify template which matches wifi networks with the given null key is invalid.
        assertFailsWith<IllegalArgumentException> {
            NetworkTemplate.Builder(MATCH_WIFI).setWifiNetworkKeys(setOf(null)).build()
        }

        // Verify template which matches wifi wildcard with the given empty key set.
        NetworkTemplate.Builder(MATCH_WIFI).setWifiNetworkKeys(setOf<String>()).build().let {
            val expectedTemplate = NetworkTemplate(MATCH_WIFI_WILDCARD, null /*subscriberId*/,
                    arrayOf<String>() /*subscriberIds*/, arrayOf<String>(),
                    METERED_ALL, ROAMING_ALL, DEFAULT_NETWORK_ALL, NETWORK_TYPE_ALL,
                    OEM_MANAGED_ALL, SUBSCRIBER_ID_MATCH_RULE_ALL)
            assertEquals(expectedTemplate, it)
        }
    }
}
