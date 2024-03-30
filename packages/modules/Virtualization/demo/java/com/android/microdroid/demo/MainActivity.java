/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.microdroid.demo;

import android.app.Application;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.system.virtualmachine.VirtualMachine;
import android.system.virtualmachine.VirtualMachineCallback;
import android.system.virtualmachine.VirtualMachineConfig;
import android.system.virtualmachine.VirtualMachineConfig.DebugLevel;
import android.system.virtualmachine.VirtualMachineException;
import android.system.virtualmachine.VirtualMachineManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.android.microdroid.testservice.ITestService;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * This app is to demonstrate the use of APIs in the android.system.virtualmachine library.
 * Currently, this app starts a virtual machine running Microdroid and shows the console output from
 * the virtual machine to the UI.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "MicrodroidDemo";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button runStopButton = (Button) findViewById(R.id.runStopButton);
        TextView consoleView = (TextView) findViewById(R.id.consoleOutput);
        TextView logView = (TextView) findViewById(R.id.logOutput);
        TextView payloadView = (TextView) findViewById(R.id.payloadOutput);
        ScrollView scrollConsoleView = (ScrollView) findViewById(R.id.scrollConsoleOutput);
        ScrollView scrollLogView = (ScrollView) findViewById(R.id.scrollLogOutput);

        VirtualMachineModel model = new ViewModelProvider(this).get(VirtualMachineModel.class);

        // When the button is clicked, run or stop the VM
        runStopButton.setOnClickListener(
                new View.OnClickListener() {
                    public void onClick(View v) {
                        if (model.getStatus().getValue() == VirtualMachine.Status.RUNNING) {
                            model.stop();
                        } else {
                            CheckBox debugModeCheckBox = (CheckBox) findViewById(R.id.debugMode);
                            final boolean debug = debugModeCheckBox.isChecked();
                            model.run(debug);
                        }
                    }
                });

        // When the VM status is updated, change the label of the button
        model.getStatus()
                .observeForever(
                        new Observer<VirtualMachine.Status>() {
                            @Override
                            public void onChanged(VirtualMachine.Status status) {
                                if (status == VirtualMachine.Status.RUNNING) {
                                    runStopButton.setText("Stop");
                                    // Clear the outputs from the previous run
                                    consoleView.setText("");
                                    logView.setText("");
                                    payloadView.setText("");
                                } else {
                                    runStopButton.setText("Run");
                                }
                            }
                        });

        // When the console, log, or payload output is updated, append the new line to the
        // corresponding text view.
        model.getConsoleOutput()
                .observeForever(
                        new Observer<String>() {
                            @Override
                            public void onChanged(String line) {
                                consoleView.append(line + "\n");
                                scrollConsoleView.fullScroll(View.FOCUS_DOWN);
                            }
                        });
        model.getLogOutput()
                .observeForever(
                        new Observer<String>() {
                            @Override
                            public void onChanged(String line) {
                                logView.append(line + "\n");
                                scrollLogView.fullScroll(View.FOCUS_DOWN);
                            }
                        });
        model.getPayloadOutput()
                .observeForever(
                        new Observer<String>() {
                            @Override
                            public void onChanged(String line) {
                                payloadView.append(line + "\n");
                            }
                        });
    }

    /** Reads data from an input stream and posts it to the output data */
    static class Reader implements Runnable {
        private final String mName;
        private final MutableLiveData<String> mOutput;
        private final InputStream mStream;

        Reader(String name, MutableLiveData<String> output, InputStream stream) {
            mName = name;
            mOutput = output;
            mStream = stream;
        }

        @Override
        public void run() {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(mStream));
                String line;
                while ((line = reader.readLine()) != null && !Thread.interrupted()) {
                    mOutput.postValue(line);
                }
            } catch (IOException e) {
                Log.e(TAG, "Exception while posting " + mName + " output: " + e.getMessage());
            }
        }
    }

    /** Models a virtual machine and outputs from it. */
    public static class VirtualMachineModel extends AndroidViewModel {
        private VirtualMachine mVirtualMachine;
        private final MutableLiveData<String> mConsoleOutput = new MutableLiveData<>();
        private final MutableLiveData<String> mLogOutput = new MutableLiveData<>();
        private final MutableLiveData<String> mPayloadOutput = new MutableLiveData<>();
        private final MutableLiveData<VirtualMachine.Status> mStatus = new MutableLiveData<>();
        private ExecutorService mExecutorService;

        public VirtualMachineModel(Application app) {
            super(app);
            mStatus.setValue(VirtualMachine.Status.DELETED);
        }

        /** Runs a VM */
        public void run(boolean debug) {
            // Create a VM and run it.
            // TODO(jiyong): remove the call to idsigPath
            mExecutorService = Executors.newFixedThreadPool(4);

            VirtualMachineCallback callback =
                    new VirtualMachineCallback() {
                        // store reference to ExecutorService to avoid race condition
                        private final ExecutorService mService = mExecutorService;

                        @Override
                        public void onPayloadStarted(
                                VirtualMachine vm, ParcelFileDescriptor stream) {
                            if (stream == null) {
                                mPayloadOutput.postValue("(no output available)");
                                return;
                            }

                            InputStream input = new FileInputStream(stream.getFileDescriptor());
                            mService.execute(new Reader("payload", mPayloadOutput, input));
                        }

                        @Override
                        public void onPayloadReady(VirtualMachine vm) {
                            // This check doesn't 100% prevent race condition or UI hang.
                            // However, it's fine for demo.
                            if (mService.isShutdown()) {
                                return;
                            }
                            mPayloadOutput.postValue("(Payload is ready. Testing VM service...)");

                            Future<IBinder> service;
                            try {
                                service = vm.connectToVsockServer(ITestService.SERVICE_PORT);
                            } catch (VirtualMachineException e) {
                                mPayloadOutput.postValue(
                                        String.format(
                                                "(Exception while connecting VM's binder"
                                                        + " service: %s)",
                                                e.getMessage()));
                                return;
                            }

                            mService.execute(() -> testVMService(service));
                        }

                        private void testVMService(Future<IBinder> service) {
                            IBinder binder;
                            try {
                                binder = service.get();
                            } catch (Exception e) {
                                if (!Thread.interrupted()) {
                                    mPayloadOutput.postValue(
                                            String.format(
                                                    "(VM service connection failed: %s)",
                                                    e.getMessage()));
                                }
                                return;
                            }

                            try {
                                ITestService testService = ITestService.Stub.asInterface(binder);
                                int ret = testService.addInteger(123, 456);
                                mPayloadOutput.postValue(
                                        String.format(
                                                "(VM payload service: %d + %d = %d)",
                                                123, 456, ret));
                            } catch (RemoteException e) {
                                mPayloadOutput.postValue(
                                        String.format(
                                                "(Exception while testing VM's binder service:"
                                                        + " %s)",
                                                e.getMessage()));
                            }
                        }

                        @Override
                        public void onPayloadFinished(VirtualMachine vm, int exitCode) {
                            // This check doesn't 100% prevent race condition, but is fine for demo.
                            if (!mService.isShutdown()) {
                                mPayloadOutput.postValue(
                                        String.format(
                                                "(Payload finished. exit code: %d)", exitCode));
                            }
                        }

                        @Override
                        public void onError(VirtualMachine vm, int errorCode, String message) {
                            // This check doesn't 100% prevent race condition, but is fine for demo.
                            if (!mService.isShutdown()) {
                                mPayloadOutput.postValue(
                                        String.format(
                                                "(Error occurred. code: %d, message: %s)",
                                                errorCode, message));
                            }
                        }

                        @Override
                        public void onDied(VirtualMachine vm, @DeathReason int reason) {
                            mService.shutdownNow();
                            mStatus.postValue(VirtualMachine.Status.STOPPED);
                        }
                    };

            try {
                VirtualMachineConfig.Builder builder =
                        new VirtualMachineConfig.Builder(getApplication(), "assets/vm_config.json");
                if (debug) {
                    builder.debugLevel(DebugLevel.FULL);
                }
                VirtualMachineConfig config = builder.build();
                VirtualMachineManager vmm = VirtualMachineManager.getInstance(getApplication());
                mVirtualMachine = vmm.getOrCreate("demo_vm", config);
                try {
                    mVirtualMachine.setConfig(config);
                } catch (VirtualMachineException e) {
                    mVirtualMachine.delete();
                    mVirtualMachine = vmm.create("demo_vm", config);
                }
                mVirtualMachine.run();
                mVirtualMachine.setCallback(Executors.newSingleThreadExecutor(), callback);
                mStatus.postValue(mVirtualMachine.getStatus());

                InputStream console = mVirtualMachine.getConsoleOutputStream();
                InputStream log = mVirtualMachine.getLogOutputStream();
                mExecutorService.execute(new Reader("console", mConsoleOutput, console));
                mExecutorService.execute(new Reader("log", mLogOutput, log));
            } catch (VirtualMachineException e) {
                throw new RuntimeException(e);
            }
        }

        /** Stops the running VM */
        public void stop() {
            try {
                mVirtualMachine.stop();
            } catch (VirtualMachineException e) {
                // Consume
            }
            mVirtualMachine = null;
            mExecutorService.shutdownNow();
            mStatus.postValue(VirtualMachine.Status.STOPPED);
        }

        /** Returns the console output from the VM */
        public LiveData<String> getConsoleOutput() {
            return mConsoleOutput;
        }

        /** Returns the log output from the VM */
        public LiveData<String> getLogOutput() {
            return mLogOutput;
        }

        /** Returns the payload output from the VM */
        public LiveData<String> getPayloadOutput() {
            return mPayloadOutput;
        }

        /** Returns the status of the VM */
        public LiveData<VirtualMachine.Status> getStatus() {
            return mStatus;
        }
    }
}
