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

import android.annotation.NonNull;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.Signature; // This actually is certificate!
import android.os.ParcelFileDescriptor;
import android.os.PersistableBundle;
import android.sysprop.HypervisorProperties;
import android.system.virtualizationservice.VirtualMachineAppConfig;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Represents a configuration of a virtual machine. A configuration consists of hardware
 * configurations like the number of CPUs and the size of RAM, and software configurations like the
 * OS and application to run on the virtual machine.
 *
 * @hide
 */
public final class VirtualMachineConfig {
    // These defines the schema of the config file persisted on disk.
    private static final int VERSION = 1;
    private static final String KEY_VERSION = "version";
    private static final String KEY_CERTS = "certs";
    private static final String KEY_APKPATH = "apkPath";
    private static final String KEY_PAYLOADCONFIGPATH = "payloadConfigPath";
    private static final String KEY_DEBUGLEVEL = "debugLevel";
    private static final String KEY_PROTECTED_VM = "protectedVm";
    private static final String KEY_MEMORY_MIB = "memoryMib";
    private static final String KEY_NUM_CPUS = "numCpus";
    private static final String KEY_CPU_AFFINITY = "cpuAffinity";

    // Paths to the APK file of this application.
    private final @NonNull String mApkPath;
    private final @NonNull Signature[] mCerts;

    /** A debug level defines the set of debug features that the VM can be configured to. */
    public enum DebugLevel {
        /**
         * Not debuggable at all. No log is exported from the VM. Debugger can't be attached to the
         * app process running in the VM. This is the default level.
         */
        NONE,

        /**
         * Only the app is debuggable. Log from the app is exported from the VM. Debugger can be
         * attached to the app process. Rest of the VM is not debuggable.
         */
        APP_ONLY,

        /**
         * Fully debuggable. All logs (both logcat and kernel message) are exported. All processes
         * running in the VM can be attached to the debugger. Rooting is possible.
         */
        FULL,
    }

    private final DebugLevel mDebugLevel;

    /**
     * Whether to run the VM in protected mode, so the host can't access its memory.
     */
    private final boolean mProtectedVm;

    /**
     * The amount of RAM to give the VM, in MiB. If this is 0 or negative the default will be used.
     */
    private final int mMemoryMib;

    /**
     * Number of vCPUs in the VM. Defaults to 1 when not specified.
     */
    private final int mNumCpus;

    /**
     * Comma-separated list of CPUs or CPU ranges to run vCPUs on (e.g. 0,1-3,5), or
     * colon-separated list of assignments of vCPU to host CPU assignments (e.g. 0=0:1=1:2=2).
     * Default is no mask which means a vCPU can run on any host CPU.
     */
    private final String mCpuAffinity;

    /**
     * Path within the APK to the payload config file that defines software aspects of this config.
     */
    private final @NonNull String mPayloadConfigPath;

    // TODO(jiyong): add more items like # of cpu, size of ram, debuggability, etc.

    private VirtualMachineConfig(
            @NonNull String apkPath,
            @NonNull Signature[] certs,
            @NonNull String payloadConfigPath,
            DebugLevel debugLevel,
            boolean protectedVm,
            int memoryMib,
            int numCpus,
            String cpuAffinity) {
        mApkPath = apkPath;
        mCerts = certs;
        mPayloadConfigPath = payloadConfigPath;
        mDebugLevel = debugLevel;
        mProtectedVm = protectedVm;
        mMemoryMib = memoryMib;
        mNumCpus = numCpus;
        mCpuAffinity = cpuAffinity;
    }

    /** Loads a config from a stream, for example a file. */
    /* package */ static @NonNull VirtualMachineConfig from(@NonNull InputStream input)
            throws IOException, VirtualMachineException {
        PersistableBundle b = PersistableBundle.readFromStream(input);
        final int version = b.getInt(KEY_VERSION);
        if (version > VERSION) {
            throw new VirtualMachineException("Version too high");
        }
        final String apkPath = b.getString(KEY_APKPATH);
        if (apkPath == null) {
            throw new VirtualMachineException("No apkPath");
        }
        final String[] certStrings = b.getStringArray(KEY_CERTS);
        if (certStrings == null || certStrings.length == 0) {
            throw new VirtualMachineException("No certs");
        }
        List<Signature> certList = new ArrayList<>();
        for (String s : certStrings) {
            certList.add(new Signature(s));
        }
        Signature[] certs = certList.toArray(new Signature[0]);
        final String payloadConfigPath = b.getString(KEY_PAYLOADCONFIGPATH);
        if (payloadConfigPath == null) {
            throw new VirtualMachineException("No payloadConfigPath");
        }
        final DebugLevel debugLevel = DebugLevel.values()[b.getInt(KEY_DEBUGLEVEL)];
        final boolean protectedVm = b.getBoolean(KEY_PROTECTED_VM);
        final int memoryMib = b.getInt(KEY_MEMORY_MIB);
        final int numCpus = b.getInt(KEY_NUM_CPUS);
        final String cpuAffinity = b.getString(KEY_CPU_AFFINITY);
        return new VirtualMachineConfig(apkPath, certs, payloadConfigPath, debugLevel, protectedVm,
                memoryMib, numCpus, cpuAffinity);
    }

    /** Persists this config to a stream, for example a file. */
    /* package */ void serialize(@NonNull OutputStream output) throws IOException {
        PersistableBundle b = new PersistableBundle();
        b.putInt(KEY_VERSION, VERSION);
        b.putString(KEY_APKPATH, mApkPath);
        List<String> certList = new ArrayList<>();
        for (Signature cert : mCerts) {
            certList.add(cert.toCharsString());
        }
        String[] certs = certList.toArray(new String[0]);
        b.putStringArray(KEY_CERTS, certs);
        b.putString(KEY_PAYLOADCONFIGPATH, mPayloadConfigPath);
        b.putInt(KEY_DEBUGLEVEL, mDebugLevel.ordinal());
        b.putBoolean(KEY_PROTECTED_VM, mProtectedVm);
        b.putInt(KEY_NUM_CPUS, mNumCpus);
        if (mMemoryMib > 0) {
            b.putInt(KEY_MEMORY_MIB, mMemoryMib);
        }
        b.writeToStream(output);
    }

    /** Returns the path to the payload config within the owning application. */
    public @NonNull String getPayloadConfigPath() {
        return mPayloadConfigPath;
    }

    /**
     * Tests if this config is compatible with other config. Being compatible means that the configs
     * can be interchangeably used for the same virtual machine. Compatible changes includes the
     * number of CPUs and the size of the RAM, and change of the payload as long as the payload is
     * signed by the same signer. All other changes (e.g. using a payload from a different signer,
     * change of the debug mode, etc.) are considered as incompatible.
     */
    public boolean isCompatibleWith(@NonNull VirtualMachineConfig other) {
        if (!Arrays.equals(this.mCerts, other.mCerts)) {
            return false;
        }
        if (this.mDebugLevel != other.mDebugLevel) {
            // TODO(jiyong): should we treat APP_ONLY and FULL the same?
            return false;
        }
        if (this.mProtectedVm != other.mProtectedVm) {
            return false;
        }
        return true;
    }

    /**
     * Converts this config object into a parcel. Used when creating a VM via the virtualization
     * service. Notice that the files are not passed as paths, but as file descriptors because the
     * service doesn't accept paths as it might not have permission to open app-owned files and that
     * could be abused to run a VM with software that the calling application doesn't own.
     */
    /* package */ VirtualMachineAppConfig toParcel() throws FileNotFoundException {
        VirtualMachineAppConfig parcel = new VirtualMachineAppConfig();
        parcel.apk = ParcelFileDescriptor.open(new File(mApkPath), MODE_READ_ONLY);
        parcel.configPath = mPayloadConfigPath;
        switch (mDebugLevel) {
            case NONE:
                parcel.debugLevel = VirtualMachineAppConfig.DebugLevel.NONE;
                break;
            case APP_ONLY:
                parcel.debugLevel = VirtualMachineAppConfig.DebugLevel.APP_ONLY;
                break;
            case FULL:
                parcel.debugLevel = VirtualMachineAppConfig.DebugLevel.FULL;
                break;
        }
        parcel.protectedVm = mProtectedVm;
        parcel.memoryMib = mMemoryMib;
        parcel.numCpus = mNumCpus;
        parcel.cpuAffinity = mCpuAffinity;
        // Don't allow apps to set task profiles ... at last for now. Also, don't forget to
        // validate the string because these are appended to the cmdline argument.
        parcel.taskProfiles = new String[0];
        return parcel;
    }

    /** A builder used to create a {@link VirtualMachineConfig}. */
    public static class Builder {
        private Context mContext;
        private String mPayloadConfigPath;
        private DebugLevel mDebugLevel;
        private boolean mProtectedVm;
        private int mMemoryMib;
        private int mNumCpus;
        private String mCpuAffinity;

        /** Creates a builder for the given context (APK), and the payload config file in APK. */
        public Builder(@NonNull Context context, @NonNull String payloadConfigPath) {
            mContext = context;
            mPayloadConfigPath = payloadConfigPath;
            mDebugLevel = DebugLevel.NONE;
            mProtectedVm = false;
            mNumCpus = 1;
            mCpuAffinity = null;
        }

        /** Sets the debug level */
        public Builder debugLevel(DebugLevel debugLevel) {
            mDebugLevel = debugLevel;
            return this;
        }

        /** Sets whether to protect the VM memory from the host. Defaults to false. */
        public Builder protectedVm(boolean protectedVm) {
            mProtectedVm = protectedVm;
            return this;
        }

        /**
         * Sets the amount of RAM to give the VM. If this is zero or negative then the default will
         * be used.
         */
        public Builder memoryMib(int memoryMib) {
            mMemoryMib = memoryMib;
            return this;
        }

        /**
         * Sets the number of vCPUs in the VM. Defaults to 1.
         */
        public Builder numCpus(int num) {
            mNumCpus = num;
            return this;
        }

        /**
         * Sets on which host CPUs the vCPUs can run. The format is a comma-separated list of CPUs
         * or CPU ranges to run vCPUs on. e.g. "0,1-3,5" to choose host CPUs 0, 1, 2, 3, and 5.
         * Or this can be a colon-separated list of assignments of vCPU to host CPU assignments.
         * e.g. "0=0:1=1:2=2" to map vCPU 0 to host CPU 0, and so on.
         */
        public Builder cpuAffinity(String affinity) {
            mCpuAffinity = affinity;
            return this;
        }

        /** Builds an immutable {@link VirtualMachineConfig} */
        public @NonNull VirtualMachineConfig build() {
            final String apkPath = mContext.getPackageCodePath();
            final String packageName = mContext.getPackageName();
            Signature[] certs;
            try {
                certs =
                        mContext.getPackageManager()
                                .getPackageInfo(
                                        packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                                .signingInfo
                                .getSigningCertificateHistory();
            } catch (PackageManager.NameNotFoundException e) {
                // This cannot happen as `packageName` is from this app.
                throw new RuntimeException(e);
            }

            final int availableCpus = Runtime.getRuntime().availableProcessors();
            if (mNumCpus < 1 || mNumCpus > availableCpus) {
                throw new IllegalArgumentException("Number of vCPUs (" + mNumCpus + ") is out of "
                        + "range [1, " + availableCpus + "]");
            }

            if (mCpuAffinity != null
                    && !Pattern.matches("[\\d]+(-[\\d]+)?(,[\\d]+(-[\\d]+)?)*", mCpuAffinity)
                    && !Pattern.matches("[\\d]+=[\\d]+(:[\\d]+=[\\d]+)*", mCpuAffinity)) {
                throw new IllegalArgumentException("CPU affinity [" + mCpuAffinity + "]"
                        + " is invalid");
            }

            if (mProtectedVm
                    && !HypervisorProperties.hypervisor_protected_vm_supported().orElse(false)) {
                throw new UnsupportedOperationException(
                        "Protected VMs are not supported on this device.");
            }
            if (!mProtectedVm && !HypervisorProperties.hypervisor_vm_supported().orElse(false)) {
                throw new UnsupportedOperationException(
                        "Unprotected VMs are not supported on this device.");
            }

            return new VirtualMachineConfig(
                    apkPath, certs, mPayloadConfigPath, mDebugLevel, mProtectedVm, mMemoryMib,
                    mNumCpus, mCpuAffinity);
        }
    }
}
