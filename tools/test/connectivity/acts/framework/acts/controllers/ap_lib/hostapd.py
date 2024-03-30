#   Copyright 2016 - The Android Open Source Project
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

import collections
import itertools
import logging
import os
import re
import time

from acts.controllers.ap_lib import hostapd_config
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.utils_lib.commands import shell


class Error(Exception):
    """An error caused by hostapd."""


class Hostapd(object):
    """Manages the hostapd program.

    Attributes:
        config: The hostapd configuration that is being used.
    """

    PROGRAM_FILE = '/usr/sbin/hostapd'
    CLI_PROGRAM_FILE = '/usr/bin/hostapd_cli'

    def __init__(self, runner, interface, working_dir='/tmp'):
        """
        Args:
            runner: Object that has run_async and run methods for executing
                    shell commands (e.g. connection.SshConnection)
            interface: string, The name of the interface to use (eg. wlan0).
            working_dir: The directory to work out of.
        """
        self._runner = runner
        self._interface = interface
        self._working_dir = working_dir
        self.config = None
        self._shell = shell.ShellCommand(runner, working_dir)
        self._log_file = 'hostapd-%s.log' % self._interface
        self._ctrl_file = 'hostapd-%s.ctrl' % self._interface
        self._config_file = 'hostapd-%s.conf' % self._interface
        self._identifier = '%s.*%s' % (self.PROGRAM_FILE, self._config_file)

    def start(self, config, timeout=60, additional_parameters=None):
        """Starts hostapd

        Starts the hostapd daemon and runs it in the background.

        Args:
            config: Configs to start the hostapd with.
            timeout: Time to wait for DHCP server to come up.
            additional_parameters: A dictionary of parameters that can sent
                                   directly into the hostapd config file.  This
                                   can be used for debugging and or adding one
                                   off parameters into the config.

        Returns:
            True if the daemon could be started. Note that the daemon can still
            start and not work. Invalid configurations can take a long amount
            of time to be produced, and because the daemon runs indefinitely
            it's impossible to wait on. If you need to check if configs are ok
            then periodic checks to is_running and logs should be used.
        """
        if self.is_alive():
            self.stop()

        self.config = config

        self._shell.delete_file(self._ctrl_file)
        self._shell.delete_file(self._log_file)
        self._shell.delete_file(self._config_file)
        self._write_configs(additional_parameters=additional_parameters)

        hostapd_command = '%s -dd -t "%s"' % (self.PROGRAM_FILE,
                                              self._config_file)
        base_command = 'cd "%s"; %s' % (self._working_dir, hostapd_command)
        job_str = 'rfkill unblock all; %s > "%s" 2>&1' %\
                  (base_command, self._log_file)
        self._runner.run_async(job_str)

        try:
            self._wait_for_process(timeout=timeout)
            self._wait_for_interface(timeout=timeout)
        except:
            self.stop()
            raise

    def stop(self):
        """Kills the daemon if it is running."""
        if self.is_alive():
            self._shell.kill(self._identifier)

    def channel_switch(self, channel_num):
        """Switches to the given channel.

        Returns:
            acts.libs.proc.job.Result containing the results of the command.
        Raises: See _run_hostapd_cli_cmd
        """
        try:
            channel_freq = hostapd_constants.FREQUENCY_MAP[channel_num]
        except KeyError:
            raise ValueError('Invalid channel number {}'.format(channel_num))
        csa_beacon_count = 10
        channel_switch_cmd = 'chan_switch {} {}'.format(
            csa_beacon_count, channel_freq)
        result = self._run_hostapd_cli_cmd(channel_switch_cmd)

    def get_current_channel(self):
        """Returns the current channel number.

        Raises: See _run_hostapd_cli_cmd
        """
        status_cmd = 'status'
        result = self._run_hostapd_cli_cmd(status_cmd)
        match = re.search(r'^channel=(\d+)$', result.stdout, re.MULTILINE)
        if not match:
            raise Error('Current channel could not be determined')
        try:
            channel = int(match.group(1))
        except ValueError:
            raise Error('Internal error: current channel could not be parsed')
        return channel

    def is_alive(self):
        """
        Returns:
            True if the daemon is running.
        """
        return self._shell.is_alive(self._identifier)

    def pull_logs(self):
        """Pulls the log files from where hostapd is running.

        Returns:
            A string of the hostapd logs.
        """
        # TODO: Auto pulling of logs when stop is called.
        return self._shell.read_file(self._log_file)

    def _run_hostapd_cli_cmd(self, cmd):
        """Run the given hostapd_cli command.

        Runs the command, waits for the output (up to default timeout), and
            returns the result.

        Returns:
            acts.libs.proc.job.Result containing the results of the ssh command.

        Raises:
            acts.lib.proc.job.TimeoutError: When the remote command took too
                long to execute.
            acts.controllers.utils_lib.ssh.connection.Error: When the ssh
                connection failed to be created.
            acts.controllers.utils_lib.ssh.connection.CommandError: Ssh worked,
                but the command had an error executing.
        """
        hostapd_cli_job = 'cd {}; {} -p {} {}'.format(self._working_dir,
                                                      self.CLI_PROGRAM_FILE,
                                                      self._ctrl_file, cmd)
        return self._runner.run(hostapd_cli_job)

    def _wait_for_process(self, timeout=60):
        """Waits for the process to come up.

        Waits until the hostapd process is found running, or there is
        a timeout. If the program never comes up then the log file
        will be scanned for errors.

        Raises: See _scan_for_errors
        """
        start_time = time.time()
        while time.time() - start_time < timeout and not self.is_alive():
            self._scan_for_errors(False)
            time.sleep(0.1)

    def _wait_for_interface(self, timeout=60):
        """Waits for hostapd to report that the interface is up.

        Waits until hostapd says the interface has been brought up or an
        error occurs.

        Raises: see _scan_for_errors
        """
        start_time = time.time()
        while time.time() - start_time < timeout:
            time.sleep(0.1)
            success = self._shell.search_file('Setup of interface done',
                                              self._log_file)
            if success:
                return
            self._scan_for_errors(False)

        self._scan_for_errors(True)

    def _scan_for_errors(self, should_be_up):
        """Scans the hostapd log for any errors.

        Args:
            should_be_up: If true then hostapd program is expected to be alive.
                          If it is found not alive while this is true an error
                          is thrown.

        Raises:
            Error: Raised when a hostapd error is found.
        """
        # Store this so that all other errors have priority.
        is_dead = not self.is_alive()

        bad_config = self._shell.search_file('Interface initialization failed',
                                             self._log_file)
        if bad_config:
            raise Error('Interface failed to start', self)

        bad_config = self._shell.search_file(
            "Interface %s wasn't started" % self._interface, self._log_file)
        if bad_config:
            raise Error('Interface failed to start', self)

        if should_be_up and is_dead:
            raise Error('Hostapd failed to start', self)

    def _write_configs(self, additional_parameters=None):
        """Writes the configs to the hostapd config file."""
        self._shell.delete_file(self._config_file)

        interface_configs = collections.OrderedDict()
        interface_configs['interface'] = self._interface
        interface_configs['ctrl_interface'] = self._ctrl_file
        pairs = ('%s=%s' % (k, v) for k, v in interface_configs.items())

        packaged_configs = self.config.package_configs()
        if additional_parameters:
            packaged_configs.append(additional_parameters)
        for packaged_config in packaged_configs:
            config_pairs = ('%s=%s' % (k, v)
                            for k, v in packaged_config.items()
                            if v is not None)
            pairs = itertools.chain(pairs, config_pairs)

        hostapd_conf = '\n'.join(pairs)

        logging.info('Writing %s' % self._config_file)
        logging.debug('******************Start*******************')
        logging.debug('\n%s' % hostapd_conf)
        logging.debug('*******************End********************')

        self._shell.write_file(self._config_file, hostapd_conf)
