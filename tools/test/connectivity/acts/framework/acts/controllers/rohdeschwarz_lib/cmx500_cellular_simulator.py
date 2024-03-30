#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import logging
from acts.controllers.rohdeschwarz_lib import cmx500
from acts.controllers.rohdeschwarz_lib.cmx500 import LteBandwidth
from acts.controllers.rohdeschwarz_lib.cmx500 import LteState
from acts.controllers import cellular_simulator as cc
from acts.controllers.cellular_lib import LteSimulation


CMX_TM_MAPPING = {
    LteSimulation.TransmissionMode.TM1: cmx500.TransmissionModes.TM1,
    LteSimulation.TransmissionMode.TM2: cmx500.TransmissionModes.TM2,
    LteSimulation.TransmissionMode.TM3: cmx500.TransmissionModes.TM3,
    LteSimulation.TransmissionMode.TM4: cmx500.TransmissionModes.TM4,
    LteSimulation.TransmissionMode.TM7: cmx500.TransmissionModes.TM7,
    LteSimulation.TransmissionMode.TM8: cmx500.TransmissionModes.TM8,
    LteSimulation.TransmissionMode.TM9: cmx500.TransmissionModes.TM9,
}

CMX_SCH_MAPPING = {
    LteSimulation.SchedulingMode.STATIC: cmx500.SchedulingMode.USERDEFINEDCH
}

CMX_MIMO_MAPPING = {
    LteSimulation.MimoMode.MIMO_1x1: cmx500.MimoModes.MIMO1x1,
    LteSimulation.MimoMode.MIMO_2x2: cmx500.MimoModes.MIMO2x2,
    LteSimulation.MimoMode.MIMO_4x4: cmx500.MimoModes.MIMO4x4,
}


class CMX500CellularSimulator(cc.AbstractCellularSimulator):
    """ A cellular simulator for telephony simulations based on the CMX 500
    controller. """

    def __init__(self, ip_address, port='5025'):
        """ Initializes the cellular simulator.

        Args:
            ip_address: the ip address of the CMX500
            port: the port number for the CMX500 controller
        """
        super().__init__()
        try:
            self.cmx = cmx500.Cmx500(ip_address, port)
        except:
            raise cc.CellularSimulatorError('Error when Initializes CMX500.')

        self.bts = self.cmx.bts

    def destroy(self):
        """ Sends finalization commands to the cellular equipment and closes
        the connection. """
        self.log.info('destroy the cmx500 simulator')
        self.cmx.disconnect()

    def setup_lte_scenario(self):
        """ Configures the equipment for an LTE simulation. """
        self.log.info('setup lte scenario')
        self.cmx.switch_lte_signalling(cmx500.LteState.LTE_ON)

    def setup_nr_sa_scenario(self):
        """ Configures the equipment for an NR stand alone simulation. """
        raise NotImplementedError()

    def setup_nr_nsa_scenario(self):
        """ Configures the equipment for an NR non stand alone simulation. """
        self.log.info('setup nsa scenario (start lte cell and nr cell')
        self.cmx.switch_on_nsa_signalling()

    def set_band_combination(self, bands):
        """ Prepares the test equipment for the indicated band combination.

        Args:
            bands: a list of bands represented as ints or strings
        """
        self.num_carriers = len(bands)

    def set_lte_rrc_state_change_timer(self, enabled, time=10):
        """ Configures the LTE RRC state change timer.

        Args:
            enabled: a boolean indicating if the timer should be on or off.
            time: time in seconds for the timer to expire
        """
        self.log.info('set timer enabled to {} and the time to {}'.format(
                enabled, time))
        self.cmx.rrc_state_change_time_enable = enabled
        self.cmx.lte_rrc_state_change_timer = time


    def set_band(self, bts_index, band, frequency_range=None):
        """ Sets the band for the indicated base station.

        Args:
            bts_index: the base station number
            band: the new band
        """
        self.log.info('set band to {}'.format(band))
        if frequency_range:
            self.bts[bts_index].set_band(
                    int(band), frequency_range=frequency_range)
        else:
            self.bts[bts_index].set_band(int(band))

    def get_duplex_mode(self, band):
        """ Determines if the band uses FDD or TDD duplex mode

        Args:
            band: a band number

        Returns:
            an variable of class DuplexMode indicating if band is FDD or TDD
        """
        if 33 <= int(band) <= 46:
            return cmx500.DuplexMode.TDD
        else:
            return cmx500.DuplexMode.FDD

    def set_input_power(self, bts_index, input_power):
        """ Sets the input power for the indicated base station.

        Args:
            bts_index: the base station number
            input_power: the new input power
        """
        if input_power > 23:
            self.log.warning('Open loop supports -50dBm to 23 dBm. '
                             'Setting it to max power 23 dBm')
            input_power = 23
        self.log.info('set input power to {}'.format(input_power))
        self.bts[bts_index].set_ul_power(input_power)

    def set_output_power(self, bts_index, output_power):
        """ Sets the output power for the indicated base station.

        Args:
            bts_index: the base station number
            output_power: the new output power
        """
        self.log.info('set output power to {}'.format(output_power))
        self.bts[bts_index].set_dl_power(output_power)

    def set_tdd_config(self, bts_index, tdd_config):
        """ Sets the tdd configuration number for the indicated base station.

        Args:
            bts_index: the base station number
            tdd_config: the new tdd configuration number (from 0 to 6)
        """
        self.log.info('set tdd config to {}'.format(tdd_config))
        self.bts[bts_index].set_tdd_config(tdd_config)

    def set_ssf_config(self, bts_index, ssf_config):
        """ Sets the Special Sub-Frame config number for the indicated
        base station.

        Args:
            bts_index: the base station number
            ssf_config: the new ssf config number (from 0 to 9)
        """
        self.log.info('set ssf config to {}'.format(ssf_config))
        self.bts[bts_index].set_ssf_config(ssf_config)

    def set_bandwidth(self, bts_index, bandwidth):
        """ Sets the bandwidth for the indicated base station.

        Args:
            bts_index: the base station number
            bandwidth: the new bandwidth in MHz
        """
        self.log.info('set bandwidth of bts {} to {}'.format(
                bts_index, bandwidth))
        self.bts[bts_index].set_bandwidth(int(bandwidth))

    def set_downlink_channel_number(self, bts_index, channel_number):
        """ Sets the downlink channel number for the indicated base station.

        Args:
            bts_index: the base station number
            channel_number: the new channel number (earfcn)
        """
        self.log.info('Sets the downlink channel number to {}'.format(
                channel_number))
        self.bts[bts_index].set_dl_channel(channel_number)

    def set_mimo_mode(self, bts_index, mimo_mode):
        """ Sets the mimo mode for the indicated base station.

        Args:
            bts_index: the base station number
            mimo_mode: the new mimo mode
        """
        self.log.info('set mimo mode to {}'.format(mimo_mode))
        mimo_mode = CMX_MIMO_MAPPING[mimo_mode]
        self.bts[bts_index].set_mimo_mode(mimo_mode)

    def set_transmission_mode(self, bts_index, tmode):
        """ Sets the transmission mode for the indicated base station.

        Args:
            bts_index: the base station number
            tmode: the new transmission mode
        """
        self.log.info('set TransmissionMode to {}'.format(tmode))
        tmode = CMX_TM_MAPPING[tmode]
        self.bts[bts_index].set_transmission_mode(tmode)

    def set_scheduling_mode(self, bts_index, scheduling, mcs_dl=None,
                            mcs_ul=None, nrb_dl=None, nrb_ul=None):
        """ Sets the scheduling mode for the indicated base station.

        Args:
            bts_index: the base station number.
            scheduling: the new scheduling mode.
            mcs_dl: Downlink MCS.
            mcs_ul: Uplink MCS.
            nrb_dl: Number of RBs for downlink.
            nrb_ul: Number of RBs for uplink.
        """
        if scheduling not in CMX_SCH_MAPPING:
            raise cc.CellularSimulatorError(
                "This scheduling mode is not supported")
        log_list = []
        if mcs_dl:
            log_list.append('mcs_dl: {}'.format(mcs_dl))
        if mcs_ul:
            log_list.append('mcs_ul: {}'.format(mcs_ul))
        if nrb_dl:
            log_list.append('nrb_dl: {}'.format(nrb_dl))
        if nrb_ul:
            log_list.append('nrb_ul: {}'.format(nrb_ul))

        self.log.info('set scheduling mode to {}'.format(','.join(log_list)))
        self.bts[bts_index].set_scheduling_mode(
                mcs_dl=mcs_dl, mcs_ul=mcs_ul, nrb_dl=nrb_dl, nrb_ul=nrb_ul)

    def set_dl_256_qam_enabled(self, bts_index, enabled):
        """ Determines what MCS table should be used for the downlink.

        Args:
            bts_index: the base station number
            enabled: whether 256 QAM should be used
        """
        self.log.info('Set 256 QAM DL MCS enabled: ' + str(enabled))
        self.bts[bts_index].set_dl_modulation_table(
            cmx500.ModulationType.Q256 if enabled else cmx500.ModulationType.
            Q64)

    def set_ul_64_qam_enabled(self, bts_index, enabled):
        """ Determines what MCS table should be used for the uplink.

        Args:
            bts_index: the base station number
            enabled: whether 64 QAM should be used
        """
        self.log.info('Set 64 QAM UL MCS enabled: ' + str(enabled))
        self.bts[bts_index].set_ul_modulation_table(
            cmx500.ModulationType.Q64 if enabled else cmx500.ModulationType.Q16
        )

    def set_mac_padding(self, bts_index, mac_padding):
        """ Enables or disables MAC padding in the indicated base station.

        Args:
            bts_index: the base station number
            mac_padding: the new MAC padding setting
        """
        self.log.info('set mac pad on {}'.format(mac_padding))
        self.bts[bts_index].set_dl_mac_padding(mac_padding)

    def set_cfi(self, bts_index, cfi):
        """ Sets the Channel Format Indicator for the indicated base station.

        Args:
            bts_index: the base station number
            cfi: the new CFI setting
        """
        if cfi == 'BESTEFFORT':
            self.log.info('The cfi is BESTEFFORT, use default value')
            return
        try:
            index = int(cfi) + 1
        except Exception as e:
            index = 1
        finally:
            self.log.info('set the cfi and the cfi index is {}'.format(index))
            self.bts[bts_index].set_cfi(index)

    def set_paging_cycle(self, bts_index, cycle_duration):
        """ Sets the paging cycle duration for the indicated base station.

        Args:
            bts_index: the base station number
            cycle_duration: the new paging cycle duration in milliseconds
        """
        self.log.warning('The set_paging_cycle method is not implememted, '
                         'use default value')

    def set_phich_resource(self, bts_index, phich):
        """ Sets the PHICH Resource setting for the indicated base station.

        Args:
            bts_index: the base station number
            phich: the new PHICH resource setting
        """
        self.log.warning('The set_phich_resource method is not implememted, '
                         'use default value')

    def lte_attach_secondary_carriers(self, ue_capability_enquiry):
        """ Activates the secondary carriers for CA. Requires the DUT to be
        attached to the primary carrier first.

        Args:
            ue_capability_enquiry: UE capability enquiry message to be sent to
        the UE before starting carrier aggregation.
        """
        self.wait_until_communication_state()
        self.bts[1].attach_as_secondary_cell()

    def wait_until_attached(self, timeout=120):
        """ Waits until the DUT is attached to the primary carrier.

        Args:
            timeout: after this amount of time the method will raise a
                CellularSimulatorError exception. Default is 120 seconds.
        """
        self.log.info('wait until attached')
        self.cmx.wait_until_attached(timeout)

    def wait_until_communication_state(self, timeout=120):
        """ Waits until the DUT is in Communication state.

        Args:
            timeout: after this amount of time the method will raise a
                CellularSimulatorError exception. Default is 120 seconds.
        Return:
            True if cmx reach rrc state within timeout
        Raise:
            CmxError if tiemout
        """
        self.log.info('wait for rrc on state')
        return self.cmx.wait_for_rrc_state(cmx500.RrcState.RRC_ON, timeout)

    def wait_until_idle_state(self, timeout=120):
        """ Waits until the DUT is in Idle state.

        Args:
            timeout: after this amount of time the method will raise a
                CellularSimulatorError exception. Default is 120 seconds.
        Return:
            True if cmx reach rrc state within timeout
        Raise:
            CmxError if tiemout
        """
        self.log.info('wait for rrc off state')
        return self.cmx.wait_for_rrc_state(cmx500.RrcState.RRC_OFF, timeout)

    def detach(self):
        """ Turns off all the base stations so the DUT loose connection."""
        self.log.info('Bypass simulator detach step for now')

    def stop(self):
        """ Stops current simulation. After calling this method, the simulator
        will need to be set up again. """
        self.log.info('Stops current simulation and disconnect cmx500')
        self.cmx.disconnect()

    def start_data_traffic(self):
        """ Starts transmitting data from the instrument to the DUT. """
        self.log.warning('The start_data_traffic is not implemented yet')

    def stop_data_traffic(self):
        """ Stops transmitting data from the instrument to the DUT. """
        self.log.warning('The stop_data_traffic is not implemented yet')
