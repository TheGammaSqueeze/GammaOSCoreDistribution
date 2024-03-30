/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.server.bluetooth;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.server.bluetooth.BluetoothShellCommand.BluetoothCommand;

import com.google.common.truth.Expect;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.FileDescriptor;
import java.io.PrintWriter;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothShellCommandTest {
    @Rule public final Expect expect = Expect.create();

    @Mock
    BluetoothManagerService mManagerService;

    @Mock
    Context mContext;

    BluetoothShellCommand mShellCommand;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mShellCommand = new BluetoothShellCommand(mManagerService, mContext);
        mShellCommand.init(
                mock(Binder.class), mock(FileDescriptor.class), mock(FileDescriptor.class),
                mock(FileDescriptor.class), new String[0], -1);
    }

    @Test
    public void enableCommand() throws Exception {
        BluetoothCommand enableCmd = mShellCommand.new Enable();
        String cmdName = "enable";

        assertThat(enableCmd.getName()).isEqualTo(cmdName);
        assertThat(enableCmd.isMatch(cmdName)).isTrue();
        assertThat(enableCmd.isPrivileged()).isFalse();
        when(mManagerService.enable(any())).thenReturn(true);
        assertThat(enableCmd.exec(cmdName)).isEqualTo(0);
    }

    @Test
    public void disableCommand() throws Exception {
        BluetoothCommand disableCmd = mShellCommand.new Disable();
        String cmdName = "disable";

        assertThat(disableCmd.getName()).isEqualTo(cmdName);
        assertThat(disableCmd.isMatch(cmdName)).isTrue();
        assertThat(disableCmd.isPrivileged()).isFalse();
        when(mManagerService.disable(any(), anyBoolean())).thenReturn(true);
        assertThat(disableCmd.exec(cmdName)).isEqualTo(0);
    }

    @Test
    public void waitForStateCommand() throws Exception {
        BluetoothCommand waitCmd = mShellCommand.new WaitForAdapterState();

        expect.that(waitCmd.getName()).isEqualTo("wait-for-state");
        String[] validCmd = {
            "wait-for-state:STATE_OFF",
            "wait-for-state:STATE_ON",
        };
        for (String m : validCmd) {
            expect.that(waitCmd.isMatch(m)).isTrue();
        }
        String[] falseCmd = {
            "wait-for-stateSTATE_ON",
            "wait-for-foo:STATE_ON",
        };
        for (String m : falseCmd) {
            expect.that(waitCmd.isMatch(m)).isFalse();
        }
        String[] throwCmd = {
            "wait-for-state:STATE_BLE_TURNING_ON",
            "wait-for-state:STATE_ON:STATE_OFF",
            "wait-for-state::STATE_ON",
            "wait-for-state:STATE_ON:",
        };
        for (String m : throwCmd) {
            assertThrows(m, IllegalArgumentException.class, () -> waitCmd.isMatch(m));
        }

        expect.that(waitCmd.isPrivileged()).isFalse();
        when(mManagerService.waitForManagerState(eq(BluetoothAdapter.STATE_OFF))).thenReturn(true);

        expect.that(waitCmd.exec(validCmd[0])).isEqualTo(0);
    }

    @Test
    public void onCommand_withNullString_callsOnHelp() {
        BluetoothShellCommand command = spy(mShellCommand);

        command.onCommand(null);

        verify(command).onHelp();
    }

    @Test
    public void onCommand_withEnableString_callsEnableCommand() throws Exception {
        BluetoothCommand enableCmd = spy(mShellCommand.new Enable());
        mShellCommand.mBluetoothCommands[0] = enableCmd;

        mShellCommand.onCommand("enable");

        verify(enableCmd).exec(eq("enable"));
    }

    @Test
    public void onCommand_withPrivilegedCommandName_throwsSecurityException() {
        final String privilegedCommandName = "test_privileged_cmd_name";
        BluetoothCommand privilegedCommand =
                mShellCommand.new BluetoothCommand(true, privilegedCommandName) {
                    @Override
                    int exec(String cmd) throws RemoteException {
                        return 0;
                    }
                    @Override
                    public void onHelp(PrintWriter pw) { }
        };
        mShellCommand.mBluetoothCommands[0] = privilegedCommand;

        assertThrows(SecurityException.class,
                () -> mShellCommand.onCommand(privilegedCommandName));
    }
}
