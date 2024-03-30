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

package android.system.virtualmachine;

import static android.os.ParcelFileDescriptor.MODE_READ_ONLY;
import static android.os.ParcelFileDescriptor.MODE_READ_WRITE;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Binder;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.system.virtualizationservice.IVirtualMachine;
import android.system.virtualizationservice.IVirtualMachineCallback;
import android.system.virtualizationservice.IVirtualizationService;
import android.system.virtualizationservice.PartitionType;
import android.system.virtualizationservice.VirtualMachineAppConfig;
import android.system.virtualizationservice.VirtualMachineState;
import android.util.JsonReader;

import com.android.internal.annotations.GuardedBy;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.zip.ZipFile;

/**
 * A handle to the virtual machine. The virtual machine is local to the app which created the
 * virtual machine.
 *
 * @hide
 */
public class VirtualMachine {
    /** Name of the directory under the files directory where all VMs created for the app exist. */
    private static final String VM_DIR = "vm";

    /** Name of the persisted config file for a VM. */
    private static final String CONFIG_FILE = "config.xml";

    /** Name of the instance image file for a VM. (Not implemented) */
    private static final String INSTANCE_IMAGE_FILE = "instance.img";

    /** Name of the idsig file for a VM */
    private static final String IDSIG_FILE = "idsig";

    /** Name of the idsig files for extra APKs. */
    private static final String EXTRA_IDSIG_FILE_PREFIX = "extra_idsig_";

    /** Name of the virtualization service. */
    private static final String SERVICE_NAME = "android.system.virtualizationservice";

    /** Status of a virtual machine */
    public enum Status {
        /** The virtual machine has just been created, or {@link #stop()} was called on it. */
        STOPPED,
        /** The virtual machine is running. */
        RUNNING,
        /**
         * The virtual machine is deleted. This is a irreversable state. Once a virtual machine is
         * deleted, it can never be undone, which means all its secrets are permanently lost.
         */
        DELETED,
    }

    /** Lock for internal synchronization. */
    private final Object mLock = new Object();

    /** The package which owns this VM. */
    private final @NonNull String mPackageName;

    /** Name of this VM within the package. The name should be unique in the package. */
    private final @NonNull String mName;

    /**
     * Path to the config file for this VM. The config file is where the configuration is persisted.
     */
    private final @NonNull File mConfigFilePath;

    /** Path to the instance image file for this VM. */
    private final @NonNull File mInstanceFilePath;

    /** Path to the idsig file for this VM. */
    private final @NonNull File mIdsigFilePath;

    private static class ExtraApkSpec {
        public final File apk;
        public final File idsig;

        ExtraApkSpec(File apk, File idsig) {
            this.apk = apk;
            this.idsig = idsig;
        }
    }

    /**
     * List of extra apks. Apks are specified by the vm config, and corresponding idsigs are to be
     * generated.
     */
    private final @NonNull List<ExtraApkSpec> mExtraApks;

    /** Size of the instance image. 10 MB. */
    private static final long INSTANCE_FILE_SIZE = 10 * 1024 * 1024;

    /** The configuration that is currently associated with this VM. */
    private @NonNull VirtualMachineConfig mConfig;

    /** Handle to the "running" VM. */
    private @Nullable IVirtualMachine mVirtualMachine;

    /** The registered callback */
    @GuardedBy("mLock")
    private @Nullable VirtualMachineCallback mCallback;

    /** The executor on which the callback will be executed */
    @GuardedBy("mLock")
    private @Nullable Executor mCallbackExecutor;

    private @Nullable ParcelFileDescriptor mConsoleReader;
    private @Nullable ParcelFileDescriptor mConsoleWriter;

    private @Nullable ParcelFileDescriptor mLogReader;
    private @Nullable ParcelFileDescriptor mLogWriter;

    private final ExecutorService mExecutorService = Executors.newCachedThreadPool();

    static {
        System.loadLibrary("virtualmachine_jni");
    }

    private VirtualMachine(
            @NonNull Context context, @NonNull String name, @NonNull VirtualMachineConfig config)
            throws VirtualMachineException {
        mPackageName = context.getPackageName();
        mName = name;
        mConfig = config;
        mConfigFilePath = getConfigFilePath(context, name);

        final File vmRoot = new File(context.getFilesDir(), VM_DIR);
        final File thisVmDir = new File(vmRoot, mName);
        mInstanceFilePath = new File(thisVmDir, INSTANCE_IMAGE_FILE);
        mIdsigFilePath = new File(thisVmDir, IDSIG_FILE);
        mExtraApks = setupExtraApks(context, config, thisVmDir);
    }

    /**
     * Creates a virtual machine with the given name and config. Once a virtual machine is created
     * it is persisted until it is deleted by calling {@link #delete()}. The created virtual machine
     * is in {@link #STOPPED} state. To run the VM, call {@link #run()}.
     */
    /* package */ static @NonNull VirtualMachine create(
            @NonNull Context context, @NonNull String name, @NonNull VirtualMachineConfig config)
            throws VirtualMachineException {
        if (config == null) {
            throw new VirtualMachineException("null config");
        }
        VirtualMachine vm = new VirtualMachine(context, name, config);

        try {
            final File thisVmDir = vm.mConfigFilePath.getParentFile();
            Files.createDirectories(thisVmDir.getParentFile().toPath());

            // The checking of the existence of this directory and the creation of it is done
            // atomically. If the directory already exists (i.e. the VM with the same name was
            // already created), FileAlreadyExistsException is thrown
            Files.createDirectory(thisVmDir.toPath());

            try (FileOutputStream output = new FileOutputStream(vm.mConfigFilePath)) {
                vm.mConfig.serialize(output);
            }
        } catch (FileAlreadyExistsException e) {
            throw new VirtualMachineException("virtual machine already exists", e);
        } catch (IOException e) {
            throw new VirtualMachineException(e);
        }

        try {
            vm.mInstanceFilePath.createNewFile();
        } catch (IOException e) {
            throw new VirtualMachineException("failed to create instance image", e);
        }

        IVirtualizationService service =
                IVirtualizationService.Stub.asInterface(
                        ServiceManager.waitForService(SERVICE_NAME));

        try {
            service.initializeWritablePartition(
                    ParcelFileDescriptor.open(vm.mInstanceFilePath, MODE_READ_WRITE),
                    INSTANCE_FILE_SIZE,
                    PartitionType.ANDROID_VM_INSTANCE);
        } catch (FileNotFoundException e) {
            throw new VirtualMachineException("instance image missing", e);
        } catch (RemoteException e) {
            throw new VirtualMachineException("failed to create instance partition", e);
        }

        return vm;
    }

    /** Loads a virtual machine that is already created before. */
    /* package */ static @Nullable VirtualMachine load(
            @NonNull Context context, @NonNull String name) throws VirtualMachineException {
        File configFilePath = getConfigFilePath(context, name);
        VirtualMachineConfig config;
        try (FileInputStream input = new FileInputStream(configFilePath)) {
            config = VirtualMachineConfig.from(input);
        } catch (FileNotFoundException e) {
            // The VM doesn't exist.
            return null;
        } catch (IOException e) {
            throw new VirtualMachineException(e);
        }

        VirtualMachine vm = new VirtualMachine(context, name, config);

        // If config file exists, but the instance image file doesn't, it means that the VM is
        // corrupted. That's different from the case that the VM doesn't exist. Throw an exception
        // instead of returning null.
        if (!vm.mInstanceFilePath.exists()) {
            throw new VirtualMachineException("instance image missing");
        }

        return vm;
    }

    /**
     * Returns the name of this virtual machine. The name is unique in the package and can't be
     * changed.
     */
    public @NonNull String getName() {
        return mName;
    }

    /**
     * Returns the currently selected config of this virtual machine. There can be multiple virtual
     * machines sharing the same config. Even in that case, the virtual machines are completely
     * isolated from each other; one cannot share its secret to another virtual machine even if they
     * share the same config. It is also possible that a virtual machine can switch its config,
     * which can be done by calling {@link #setConfig(VirtualMachineCOnfig)}.
     */
    public @NonNull VirtualMachineConfig getConfig() {
        return mConfig;
    }

    /** Returns the current status of this virtual machine. */
    public @NonNull Status getStatus() throws VirtualMachineException {
        try {
            if (mVirtualMachine != null) {
                switch (mVirtualMachine.getState()) {
                    case VirtualMachineState.NOT_STARTED:
                        return Status.STOPPED;
                    case VirtualMachineState.STARTING:
                    case VirtualMachineState.STARTED:
                    case VirtualMachineState.READY:
                    case VirtualMachineState.FINISHED:
                        return Status.RUNNING;
                    case VirtualMachineState.DEAD:
                        return Status.STOPPED;
                }
            }
        } catch (RemoteException e) {
            throw new VirtualMachineException(e);
        }
        if (!mConfigFilePath.exists()) {
            return Status.DELETED;
        }
        return Status.STOPPED;
    }

    /**
     * Registers the callback object to get events from the virtual machine. If a callback was
     * already registered, it is replaced with the new one.
     */
    public void setCallback(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull VirtualMachineCallback callback) {
        synchronized (mLock) {
            mCallback = callback;
            mCallbackExecutor = executor;
        }
    }

    /** Clears the currently registered callback. */
    public void clearCallback() {
        synchronized (mLock) {
            mCallback = null;
            mCallbackExecutor = null;
        }
    }

    /** Executes a callback on the callback executor. */
    private void executeCallback(Consumer<VirtualMachineCallback> fn) {
        final VirtualMachineCallback callback;
        final Executor executor;
        synchronized (mLock) {
            callback = mCallback;
            executor = mCallbackExecutor;
        }
        if (callback == null || executor == null) {
            return;
        }
        final long restoreToken = Binder.clearCallingIdentity();
        try {
            executor.execute(() -> fn.accept(callback));
        } finally {
            Binder.restoreCallingIdentity(restoreToken);
        }
    }

    /**
     * Runs this virtual machine. The returning of this method however doesn't mean that the VM has
     * actually started running or the OS has booted there. Such events can be notified by
     * registering a callback object (not implemented currently).
     */
    public void run() throws VirtualMachineException {
        if (getStatus() != Status.STOPPED) {
            throw new VirtualMachineException(this + " is not in stopped state");
        }

        try {
            mIdsigFilePath.createNewFile();
            for (ExtraApkSpec extraApk : mExtraApks) {
                extraApk.idsig.createNewFile();
            }
        } catch (IOException e) {
            // If the file already exists, exception is not thrown.
            throw new VirtualMachineException("failed to create idsig file", e);
        }

        IVirtualizationService service =
                IVirtualizationService.Stub.asInterface(
                        ServiceManager.waitForService(SERVICE_NAME));

        try {
            if (mConsoleReader == null && mConsoleWriter == null) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                mConsoleReader = pipe[0];
                mConsoleWriter = pipe[1];
            }

            if (mLogReader == null && mLogWriter == null) {
                ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
                mLogReader = pipe[0];
                mLogWriter = pipe[1];
            }

            VirtualMachineAppConfig appConfig = getConfig().toParcel();

            // Fill the idsig file by hashing the apk
            service.createOrUpdateIdsigFile(
                    appConfig.apk, ParcelFileDescriptor.open(mIdsigFilePath, MODE_READ_WRITE));

            for (ExtraApkSpec extraApk : mExtraApks) {
                service.createOrUpdateIdsigFile(
                        ParcelFileDescriptor.open(extraApk.apk, MODE_READ_ONLY),
                        ParcelFileDescriptor.open(extraApk.idsig, MODE_READ_WRITE));
            }

            // Re-open idsig file in read-only mode
            appConfig.idsig = ParcelFileDescriptor.open(mIdsigFilePath, MODE_READ_ONLY);
            appConfig.instanceImage = ParcelFileDescriptor.open(mInstanceFilePath, MODE_READ_WRITE);
            List<ParcelFileDescriptor> extraIdsigs = new ArrayList<>();
            for (ExtraApkSpec extraApk : mExtraApks) {
                extraIdsigs.add(ParcelFileDescriptor.open(extraApk.idsig, MODE_READ_ONLY));
            }
            appConfig.extraIdsigs = extraIdsigs;

            android.system.virtualizationservice.VirtualMachineConfig vmConfigParcel =
                    android.system.virtualizationservice.VirtualMachineConfig.appConfig(appConfig);

            // The VM should only be observed to die once
            AtomicBoolean onDiedCalled = new AtomicBoolean(false);

            IBinder.DeathRecipient deathRecipient = new IBinder.DeathRecipient() {
                @Override
                public void binderDied() {
                    if (onDiedCalled.compareAndSet(false, true)) {
                        executeCallback((cb) -> cb.onDied(VirtualMachine.this,
                                VirtualMachineCallback.DEATH_REASON_VIRTUALIZATIONSERVICE_DIED));
                    }
                }
            };

            mVirtualMachine = service.createVm(vmConfigParcel, mConsoleWriter, mLogWriter);
            mVirtualMachine.registerCallback(
                    new IVirtualMachineCallback.Stub() {
                        @Override
                        public void onPayloadStarted(int cid, ParcelFileDescriptor stream) {
                            executeCallback(
                                    (cb) -> cb.onPayloadStarted(VirtualMachine.this, stream));
                        }
                        @Override
                        public void onPayloadReady(int cid) {
                            executeCallback((cb) -> cb.onPayloadReady(VirtualMachine.this));
                        }
                        @Override
                        public void onPayloadFinished(int cid, int exitCode) {
                            executeCallback(
                                    (cb) -> cb.onPayloadFinished(VirtualMachine.this, exitCode));
                        }
                        @Override
                        public void onError(int cid, int errorCode, String message) {
                            executeCallback(
                                    (cb) -> cb.onError(VirtualMachine.this, errorCode, message));
                        }
                        @Override
                        public void onDied(int cid, int reason) {
                            service.asBinder().unlinkToDeath(deathRecipient, 0);
                            if (onDiedCalled.compareAndSet(false, true)) {
                                executeCallback((cb) -> cb.onDied(VirtualMachine.this, reason));
                            }
                        }
                    }
            );
            service.asBinder().linkToDeath(deathRecipient, 0);
            mVirtualMachine.start();
        } catch (IOException e) {
            throw new VirtualMachineException(e);
        } catch (RemoteException e) {
            throw new VirtualMachineException(e);
        }
    }

    /** Returns the stream object representing the console output from the virtual machine. */
    public @NonNull InputStream getConsoleOutputStream() throws VirtualMachineException {
        if (mConsoleReader == null) {
            throw new VirtualMachineException("Console output not available");
        }
        return new FileInputStream(mConsoleReader.getFileDescriptor());
    }

    /** Returns the stream object representing the log output from the virtual machine. */
    public @NonNull InputStream getLogOutputStream() throws VirtualMachineException {
        if (mLogReader == null) {
            throw new VirtualMachineException("Log output not available");
        }
        return new FileInputStream(mLogReader.getFileDescriptor());
    }

    /**
     * Stops this virtual machine. Stopping a virtual machine is like pulling the plug on a real
     * computer; the machine halts immediately. Software running on the virtual machine is not
     * notified with the event. A stopped virtual machine can be re-started by calling {@link
     * #run()}.
     */
    public void stop() throws VirtualMachineException {
        // Dropping the IVirtualMachine handle stops the VM
        mVirtualMachine = null;
    }

    /**
     * Deletes this virtual machine. Deleting a virtual machine means deleting any persisted data
     * associated with it including the per-VM secret. This is an irreversable action. A virtual
     * machine once deleted can never be restored. A new virtual machine created with the same name
     * and the same config is different from an already deleted virtual machine.
     */
    public void delete() throws VirtualMachineException {
        if (getStatus() != Status.STOPPED) {
            throw new VirtualMachineException("Virtual machine is not stopped");
        }
        final File vmRootDir = mConfigFilePath.getParentFile();
        for (ExtraApkSpec extraApks : mExtraApks) {
            extraApks.idsig.delete();
        }
        mConfigFilePath.delete();
        mInstanceFilePath.delete();
        mIdsigFilePath.delete();
        vmRootDir.delete();
    }

    /** Returns the CID of this virtual machine, if it is running. */
    public @NonNull Optional<Integer> getCid() throws VirtualMachineException {
        if (getStatus() != Status.RUNNING) {
            return Optional.empty();
        }
        try {
            return Optional.of(mVirtualMachine.getCid());
        } catch (RemoteException e) {
            throw new VirtualMachineException(e);
        }
    }

    /**
     * Changes the config of this virtual machine to a new one. This can be used to adjust things
     * like the number of CPU and size of the RAM, depending on the situation (e.g. the size of the
     * application to run on the virtual machine, etc.) However, changing a config might make the
     * virtual machine un-bootable if the new config is not compatible with the existing one. For
     * example, if the signer of the app payload in the new config is different from that of the old
     * config, the virtual machine won't boot. To prevent such cases, this method returns exception
     * when an incompatible config is attempted.
     *
     * @return the old config
     */
    public @NonNull VirtualMachineConfig setConfig(@NonNull VirtualMachineConfig newConfig)
            throws VirtualMachineException {
        final VirtualMachineConfig oldConfig = getConfig();
        if (!oldConfig.isCompatibleWith(newConfig)) {
            throw new VirtualMachineException("incompatible config");
        }
        if (getStatus() != Status.STOPPED) {
            throw new VirtualMachineException(
                    "can't change config while virtual machine is not stopped");
        }

        try {
            FileOutputStream output = new FileOutputStream(mConfigFilePath);
            newConfig.serialize(output);
            output.close();
        } catch (IOException e) {
            throw new VirtualMachineException(e);
        }
        mConfig = newConfig;

        return oldConfig;
    }

    private static native IBinder nativeConnectToVsockServer(IBinder vmBinder, int port);

    /**
     * Connects to a VM's RPC server via vsock, and returns a root IBinder object. Guest VMs are
     * expected to set up vsock servers in their payload. After the host app receives onPayloadReady
     * callback, the host app can use this method to establish an RPC session to the guest VMs.
     *
     * <p>If the connection succeeds, the root IBinder object will be returned via {@link
     * VirtualMachineCallback.onVsockServerReady()}. If the connection fails, {@link
     * VirtualMachineCallback.onVsockServerConnectionFailed()} will be called.
     */
    public Future<IBinder> connectToVsockServer(int port) throws VirtualMachineException {
        if (getStatus() != Status.RUNNING) {
            throw new VirtualMachineException("VM is not running");
        }
        return mExecutorService.submit(
                () -> nativeConnectToVsockServer(mVirtualMachine.asBinder(), port));
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("VirtualMachine(");
        sb.append("name:" + getName() + ", ");
        sb.append("config:" + getConfig().getPayloadConfigPath() + ", ");
        sb.append("package: " + mPackageName);
        sb.append(")");
        return sb.toString();
    }

    private static List<String> parseExtraApkListFromPayloadConfig(JsonReader reader)
            throws VirtualMachineException {
        /**
         * JSON schema from packages/modules/Virtualization/microdroid/payload/config/src/lib.rs:
         *
         * <p>{ "extra_apks": [ { "path": "/system/app/foo.apk", }, ... ], ... }
         */
        try {
            List<String> apks = new ArrayList<>();

            reader.beginObject();
            while (reader.hasNext()) {
                if (reader.nextName().equals("extra_apks")) {
                    reader.beginArray();
                    while (reader.hasNext()) {
                        reader.beginObject();
                        String name = reader.nextName();
                        if (name.equals("path")) {
                            apks.add(reader.nextString());
                        } else {
                            reader.skipValue();
                        }
                        reader.endObject();
                    }
                    reader.endArray();
                } else {
                    reader.skipValue();
                }
            }
            reader.endObject();
            return apks;
        } catch (IOException e) {
            throw new VirtualMachineException(e);
        }
    }

    /**
     * Reads the payload config inside the application, parses extra APK information, and then
     * creates corresponding idsig file paths.
     */
    private static List<ExtraApkSpec> setupExtraApks(
            @NonNull Context context, @NonNull VirtualMachineConfig config, @NonNull File vmDir)
            throws VirtualMachineException {
        try {
            ZipFile zipFile = new ZipFile(context.getPackageCodePath());
            String payloadPath = config.getPayloadConfigPath();
            InputStream inputStream =
                    zipFile.getInputStream(zipFile.getEntry(config.getPayloadConfigPath()));
            List<String> apkList =
                    parseExtraApkListFromPayloadConfig(
                            new JsonReader(new InputStreamReader(inputStream)));

            List<ExtraApkSpec> extraApks = new ArrayList<>();
            for (int i = 0; i < apkList.size(); ++i) {
                extraApks.add(
                        new ExtraApkSpec(
                                new File(apkList.get(i)),
                                new File(vmDir, EXTRA_IDSIG_FILE_PREFIX + i)));
            }

            return extraApks;
        } catch (IOException e) {
            throw new VirtualMachineException("Couldn't parse extra apks from the vm config", e);
        }
    }

    private static File getConfigFilePath(@NonNull Context context, @NonNull String name) {
        final File vmRoot = new File(context.getFilesDir(), VM_DIR);
        final File thisVmDir = new File(vmRoot, name);
        return new File(thisVmDir, CONFIG_FILE);
    }
}
