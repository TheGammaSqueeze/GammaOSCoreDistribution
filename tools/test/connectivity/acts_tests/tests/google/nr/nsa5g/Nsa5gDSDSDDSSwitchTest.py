#!/usr/bin/env python3
#
#   Copyright 2020 - Google
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.tel_dsds_utils import dds_switch_during_data_transfer_test
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_dds_swap_call_streaming_test
from acts_contrib.test_utils.tel.tel_dsds_utils import dsds_dds_swap_message_streaming_test
from acts_contrib.test_utils.tel.tel_defines import YOUTUBE_PACKAGE_NAME
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest

class Nsa5gDSDSDDSSwitchTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.message_lengths = (50, 160, 180)
        self.tel_logger = TelephonyMetricLogger.for_test_case()

    def teardown_test(self):
        self.android_devices[0].force_stop_apk(YOUTUBE_PACKAGE_NAME)
        ensure_phones_idle(self.log, self.android_devices)

    @test_tracker_info(uuid="0514be56-48b1-4ae9-967f-2326939ef386")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_sms_dds_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap SMS test(Initial DDS is on SIM1).

        1. Make MO/MT SMS via SIM1 when DDS is on SIM1 and idle.
        2. Switch DDS to SIM2.
        3. Make MO/MT SMS via SIM2 when DDS is on SIM2 and idle.
        4. Switch DDS to SIM1, make sure data works fine.

        After Make/Receive SMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[0, 1],
            init_dds=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="d04fca02-881c-4089-bfdf-b1d84c301ff1")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_sms_non_dds_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap SMS test(Initial DDS is on SIM1).

        1. Make MO/MT SMS via SIM2 when DDS is on SIM1 and idle.
        2. Switch DDS to SIM2.
        3. Make MO/MT SMS via SIM1 when DDS is on SIM2 and idle.
        4. Switch DDS to SIM1, make sure data works fine.

        After Make/Receive SMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[1, 0],
            init_dds=0,
            msg_type="SMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="e5562a55-788a-4c33-9b97-8eeb8e412052")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_sms_dds_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap SMS test(Initial DDS is on SIM1).

        1. Start Youtube streaming.
        2. Make MO/MT SMS via SIM1 when DDS is on SIM1 and idle.
        3. Stop Youtube streaming.
        4. Switch DDS to SIM2.
        5. Start Youtube streaming.
        6. Make MO/MT SMS via SIM2 when DDS is on SIM2 and idle.
        7. Stop Youtube streaming.
        8. Switch DDS to SIM1, make sure data works fine.

        After Make/Receive SMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[0, 1],
            init_dds=0,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="71fb524f-4777-4aa5-aa94-28d6d46dc253")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_sms_non_dds_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap SMS test(Initial DDS is on SIM1).

        1. Start Youtube streaming.
        2. Make MO/MT SMS via SIM2 when DDS is on SIM1 and idle.
        3. Stop Youtube streaming.
        4. Switch DDS to SIM2.
        5. Start Youtube streaming.
        6. Make MO/MT SMS via SIM1 when DDS is on SIM2 and idle.
        7. Stop Youtube streaming.
        8. Switch DDS to SIM1, make sure data works fine.

        After Make/Receive SMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[1, 0],
            init_dds=0,
            msg_type="SMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="3f7cf6ff-a3ec-471b-8a13-e3035dd791c6")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_mms_dds_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap MMS test(Initial DDS is on SIM1).

        1. Make MO/MT MMS via SIM1 when DDS is on SIM1 and idle.
        2. Switch DDS to SIM2.
        3. Make MO/MT MMS via SIM2 when DDS is on SIM2 and idle.
        4. Switch DDS to SIM1, make sure data works fine.

        After Make/Receive MMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[0, 1],
            init_dds=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="311205dd-f484-407c-bd4a-93c25a78b02a")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_mms_non_dds_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap MMS test(Initial DDS is on SIM1).

        1. Make MO/MT MMS via SIM2 when DDS is on SIM1 and idle.
        2. Switch DDS to SIM2.
        3. Make MO/MT MMS via SIM1 when DDS is on SIM2 and idle.
        4. Switch DDS to SIM1, make sure data works fine.

        After Make/Receive MMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[1, 0],
            init_dds=0,
            msg_type="MMS",
            direction="mt",
            streaming=False)

    @test_tracker_info(uuid="d817ee1d-8825-4614-abb1-f813c5f4c7de")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_mms_dds_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap MMS test(Initial DDS is on SIM1).

        1. Start Youtube streaming.
        2. Make MO/MT MMS via SIM1 when DDS is on SIM1 and idle.
        3. Stop Youtube streaming.
        4. Switch DDS to SIM2.
        5. Start Youtube streaming.
        6. Make MO/MT MMS via SIM2 when DDS is on SIM2 and idle.
        7. Stop Youtube streaming.
        8. Switch DDS to SIM1, make sure data works fine.

        After Make/Receive MMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[0, 1],
            init_dds=0,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="131f68c6-e0b6-41cb-85c5-a2df125e01b3")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_mms_non_dds_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap MMS test(Initial DDS is on SIM1).

        1. Start Youtube streaming.
        2. Make MO/MT MMS via SIM2 when DDS is on SIM1 and idle.
        3. Stop Youtube streaming.
        4. Switch DDS to SIM2.
        5. Start Youtube streaming.
        6. Make MO/MT MMS via SIM1 when DDS is on SIM2 and idle.
        7. Stop Youtube streaming.
        8. Switch DDS to SIM1, make sure data works fine.

        After Make/Receive MMS will check the dds slot if is attach to the
        network with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_message_streaming_test(
            self.log,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[1, 0],
            init_dds=0,
            msg_type="MMS",
            direction="mt",
            streaming=True)

    @test_tracker_info(uuid="1c3ba14c-d7f6-4737-8ac2-f55fa3b6cc46")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_psim_mo_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap call test(Initial DDS is on SIM1).

        1. Make MO call via SIM1 when DDS is on SIM1 and idle.
        2. Switch DDS to SIM2.
        3. Make MO call via SIM1 when DDS is on SIM2 and idle.
        4. Switch DDS to SIM1, make sure data works fine.

        After call end will check the dds slot if is attach to the network
        with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[0, 0],
            init_dds=0,
            direction="mo",
            duration=30,
            streaming=False)

    @test_tracker_info(uuid="55c3fbd0-0b8b-4275-81a0-1e1715b66ec1")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_psim_mt_5g_nsa_volte_esim_5g_nsa_volte(self):
        """ 5G NSA DDS swap call test(Initial DDS is on SIM1).

        1. Receive MT call via SIM1 when DDS is on SIM1 and idle.
        2. Switch DDS to SIM2.
        3. Receive MT call via SIM1 when DDS is on SIM2 and idle.
        4. Switch DDS to SIM1, make sure data works fine.

        After call end will check the dds slot if is attach to the network
        with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[0, 0],
            init_dds=0,
            direction="mt",
            duration=30,
            streaming=False)

    @test_tracker_info(uuid="1359b4a9-7e3e-4b34-b512-4638ab4ab4a7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_esim_mo_5g_nsa_volte_psim_5g_nsa_volte(self):
        """ 5G NSA DDS swap call test(Initial DDS is on SIM1).

        1. Make MO call via SIM2 when DDS is on SIM1 and idle.
        2. Switch DDS to SIM2.
        3. Make MO call via SIM2 when DDS is on SIM2 and idle.
        4. Switch DDS to SIM1, make sure data works fine.

        After call end will check the dds slot if is attach to the network
        with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[1, 1],
            init_dds=0,
            direction="mo",
            duration=30,
            streaming=False)

    @test_tracker_info(uuid="f4a290dc-3a8b-4364-8b6e-35275a6b8f92")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_voice_esim_mt_5g_nsa_volte_psim_5g_nsa_volte(self):
        """ 5G NSA DDS swap call test(Initial DDS is on SIM1).

        1. Receive MT call via SIM2 when DDS is on SIM1 and idle.
        2. Switch DDS to SIM2.
        3. Receive MT call via SIM2 when DDS is on SIM2 and idle.
        4. Switch DDS to SIM1, make sure data works fine.

        After call end will check the dds slot if is attach to the network
        with assigned RAT successfully and data works fine.
        """
        return dsds_dds_swap_call_streaming_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            test_rat=["5g_volte", "5g_volte"],
            test_slot=[1, 1],
            init_dds=0,
            direction="mt",
            duration=30,
            streaming=False)

    @test_tracker_info(uuid="727a75ef-7277-42fe-8a4b-7b2debe666d9")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_volte", "5g_volte"])

    @test_tracker_info(uuid="4ef4626a-11b3-4a09-ac98-2e3d94e54bf7")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_mo_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_volte", "5g_volte"],
            call_slot=0,
            call_direction="mo")

    @test_tracker_info(uuid="ef3bc49f-e94f-432b-bb51-4b6008359313")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_mt_psim_5g_nsa_volte_esim_5g_nsa_volte(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_volte", "5g_volte"],
            call_slot=0,
            call_direction="mt")

    @test_tracker_info(uuid="6d913c58-dde5-453d-b9a9-30e76cdac554")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_mo_esim_5g_nsa_volte_psim_5g_nsa_volte(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_volte", "5g_volte"],
            call_slot=1,
            call_direction="mo")

    @test_tracker_info(uuid="df91d2ce-ef5e-4d38-a642-6470ade625c6")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_mt_esim_5g_nsa_volte_psim_5g_nsa_volte(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_volte", "5g_volte"],
            call_slot=1,
            call_direction="mt")

    @test_tracker_info(uuid="4ba86f3c-1de6-4888-a2e5-a5e6079c3886")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_mo_psim_5g_nsa_csfb_esim_5g_nsa_csfb(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_csfb", "5g_csfb"],
            call_slot=0,
            call_direction="mo")

    @test_tracker_info(uuid="aa426eb2-dc7b-4ffe-aaa2-a3204251c131")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_mt_psim_5g_nsa_csfb_esim_5g_nsa_csfb(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_csfb", "5g_csfb"],
            call_slot=0,
            call_direction="mt")

    @test_tracker_info(uuid="854634e8-7a2a-4d14-8269-8f4f463f8f56")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_mo_esim_5g_nsa_csfb_psim_5g_nsa_csfb(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_csfb", "5g_csfb"],
            call_slot=1,
            call_direction="mo")

    @test_tracker_info(uuid="02478b9e-6bf6-4148-bbc4-0cbdf59f1625")
    @TelephonyBaseTest.tel_test_wrap
    def test_dds_switch_youtube_and_voice_mt_esim_5g_nsa_csfb_psim_5g_nsa_csfb(self):
        return dds_switch_during_data_transfer_test(
            self.log,
            self.tel_logger,
            self.android_devices,
            nw_rat=["5g_csfb", "5g_csfb"],
            call_slot=1,
            call_direction="mt")
