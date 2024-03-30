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

package com.android.car.watchdog;

import static com.android.car.watchdog.WatchdogPerfHandler.INTERNAL_APPLICATION_CATEGORY_TYPE_MAPS;
import static com.android.car.watchdog.WatchdogPerfHandler.INTERNAL_APPLICATION_CATEGORY_TYPE_MEDIA;

import android.automotive.watchdog.PerStateBytes;
import android.automotive.watchdog.internal.ApplicationCategoryType;
import android.automotive.watchdog.internal.ComponentType;
import android.automotive.watchdog.internal.IoOveruseConfiguration;
import android.automotive.watchdog.internal.PackageMetadata;
import android.automotive.watchdog.internal.PerStateIoOveruseThreshold;
import android.automotive.watchdog.internal.ResourceOveruseConfiguration;
import android.automotive.watchdog.internal.ResourceSpecificConfiguration;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.SparseArray;

import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;

/**
 * Cache to store overuse configurations in memory.
 *
 * <p>It assumes that the error checking and loading/merging initial configs are done prior to
 * setting the cache.
 */
public final class OveruseConfigurationCache {
    static final PerStateBytes DEFAULT_THRESHOLD =
            constructPerStateBytes(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);

    private final Object mLock = new Object();
    @GuardedBy("mLock")
    private final ArraySet<String> mSafeToKillSystemPackages = new ArraySet<>();
    @GuardedBy("mLock")
    private final ArraySet<String> mSafeToKillVendorPackages = new ArraySet<>();
    @GuardedBy("mLock")
    private final List<String> mVendorPackagePrefixes = new ArrayList<>();
    @GuardedBy("mLock")
    private final SparseArray<ArraySet<String>> mPackagesByAppCategoryType = new SparseArray<>();
    @GuardedBy("mLock")
    private final SparseArray<PerStateBytes> mGenericIoThresholdsByComponent = new SparseArray<>();
    @GuardedBy("mLock")
    private final ArrayMap<String, PerStateBytes> mIoThresholdsBySystemPackages = new ArrayMap<>();
    @GuardedBy("mLock")
    private final ArrayMap<String, PerStateBytes> mIoThresholdsByVendorPackages = new ArrayMap<>();
    @GuardedBy("mLock")
    private final SparseArray<PerStateBytes> mIoThresholdsByAppCategoryType = new SparseArray<>();

    /** Dumps the contents of the cache. */
    public void dump(IndentingPrintWriter writer) {
        writer.println("*" + getClass().getSimpleName() + "*");
        writer.increaseIndent();
        synchronized (mLock) {
            writer.println("mSafeToKillSystemPackages: " + mSafeToKillSystemPackages);
            writer.println("mSafeToKillVendorPackages: " + mSafeToKillVendorPackages);
            writer.println("mVendorPackagePrefixes: " + mVendorPackagePrefixes);
            writer.println("mPackagesByAppCategoryType: ");
            writer.increaseIndent();
            for (int i = 0; i < mPackagesByAppCategoryType.size(); ++i) {
                writer.print("App category: "
                        + toApplicationCategoryTypeString(mPackagesByAppCategoryType.keyAt(i)));
                writer.println(", Packages: " + mPackagesByAppCategoryType.valueAt(i));
            }
            writer.decreaseIndent();
            writer.println("mGenericIoThresholdsByComponent: ");
            writer.increaseIndent();
            for (int i = 0; i < mGenericIoThresholdsByComponent.size(); ++i) {
                writer.print("Component type: "
                        + toComponentTypeString(mGenericIoThresholdsByComponent.keyAt(i)));
                writer.print(", Threshold: ");
                dumpPerStateBytes(mGenericIoThresholdsByComponent.valueAt(i), writer);
            }
            writer.decreaseIndent();
            writer.println("mIoThresholdsBySystemPackages: ");
            writer.increaseIndent();
            for (int i = 0; i < mIoThresholdsBySystemPackages.size(); ++i) {
                writer.print("Package name: " + mIoThresholdsBySystemPackages.keyAt(i));
                writer.print(", Threshold: ");
                dumpPerStateBytes(mIoThresholdsBySystemPackages.valueAt(i), writer);
            }
            writer.decreaseIndent();
            writer.println("mIoThresholdsByVendorPackages: ");
            writer.increaseIndent();
            for (int i = 0; i < mIoThresholdsByVendorPackages.size(); ++i) {
                writer.print("Package name: " + mIoThresholdsByVendorPackages.keyAt(i));
                writer.print(", Threshold: ");
                dumpPerStateBytes(mIoThresholdsByVendorPackages.valueAt(i), writer);
            }
            writer.decreaseIndent();
            writer.println("mIoThresholdsByAppCategoryType: ");
            writer.increaseIndent();
            for (int i = 0; i < mIoThresholdsByAppCategoryType.size(); ++i) {
                writer.print("App category: "
                        + toApplicationCategoryTypeString(mIoThresholdsByAppCategoryType.keyAt(i)));
                writer.print(", Threshold: ");
                dumpPerStateBytes(mIoThresholdsByAppCategoryType.valueAt(i), writer);
            }
            writer.decreaseIndent();
        }
        writer.decreaseIndent();
    }

    /** Overwrites the configurations in the cache. */
    public void set(List<ResourceOveruseConfiguration> configs) {
        synchronized (mLock) {
            clearLocked();
            for (int i = 0; i < configs.size(); i++) {
                ResourceOveruseConfiguration config = configs.get(i);
                switch (config.componentType) {
                    case ComponentType.SYSTEM:
                        mSafeToKillSystemPackages.addAll(config.safeToKillPackages);
                        break;
                    case ComponentType.VENDOR:
                        mSafeToKillVendorPackages.addAll(config.safeToKillPackages);
                        mVendorPackagePrefixes.addAll(config.vendorPackagePrefixes);
                        for (int j = 0; j < config.packageMetadata.size(); ++j) {
                            PackageMetadata meta = config.packageMetadata.get(j);
                            ArraySet<String> packages =
                                    mPackagesByAppCategoryType.get(meta.appCategoryType);
                            if (packages == null) {
                                packages = new ArraySet<>();
                            }
                            packages.add(meta.packageName);
                            mPackagesByAppCategoryType.append(meta.appCategoryType, packages);
                        }
                        break;
                    default:
                        // All third-party apps are killable.
                        break;
                }
                for (int j = 0; j < config.resourceSpecificConfigurations.size(); ++j) {
                    if (config.resourceSpecificConfigurations.get(j).getTag()
                            == ResourceSpecificConfiguration.ioOveruseConfiguration) {
                        setIoThresholdsLocked(config.componentType,
                                config.resourceSpecificConfigurations.get(j)
                                        .getIoOveruseConfiguration());
                    }
                }
            }
        }
    }

    /** Returns the threshold for the given package and component type. */
    public PerStateBytes fetchThreshold(String genericPackageName,
            @ComponentType int componentType) {
        synchronized (mLock) {
            PerStateBytes threshold = null;
            switch (componentType) {
                case ComponentType.SYSTEM:
                    threshold = mIoThresholdsBySystemPackages.get(genericPackageName);
                    if (threshold != null) {
                        return copyPerStateBytes(threshold);
                    }
                    break;
                case ComponentType.VENDOR:
                    threshold = mIoThresholdsByVendorPackages.get(genericPackageName);
                    if (threshold != null) {
                        return copyPerStateBytes(threshold);
                    }
                    break;
            }
            threshold = fetchAppCategorySpecificThresholdLocked(genericPackageName);
            if (threshold != null) {
                return copyPerStateBytes(threshold);
            }
            threshold = mGenericIoThresholdsByComponent.get(componentType);
            return threshold != null ? copyPerStateBytes(threshold)
                    : copyPerStateBytes(DEFAULT_THRESHOLD);
        }
    }

    /** Returns whether or not the given package is safe-to-kill on resource overuse. */
    public boolean isSafeToKill(String genericPackageName, @ComponentType int componentType,
            List<String> sharedPackages) {
        synchronized (mLock) {
            BiFunction<List<String>, Set<String>, Boolean> isSafeToKillAnyPackage =
                    (packages, safeToKillPackages) -> {
                        if (packages == null) {
                            return false;
                        }
                        for (int i = 0; i < packages.size(); i++) {
                            if (safeToKillPackages.contains(packages.get(i))) {
                                return true;
                            }
                        }
                        return false;
                    };

            switch (componentType) {
                case ComponentType.SYSTEM:
                    if (mSafeToKillSystemPackages.contains(genericPackageName)) {
                        return true;
                    }
                    return isSafeToKillAnyPackage.apply(sharedPackages, mSafeToKillSystemPackages);
                case ComponentType.VENDOR:
                    if (mSafeToKillVendorPackages.contains(genericPackageName)) {
                        return true;
                    }
                    /*
                     * Packages under the vendor shared UID may contain system packages because when
                     * CarWatchdogService derives the shared component type it attributes system
                     * packages as vendor packages when there is at least one vendor package.
                     */
                    return isSafeToKillAnyPackage.apply(sharedPackages, mSafeToKillSystemPackages)
                            || isSafeToKillAnyPackage.apply(sharedPackages,
                            mSafeToKillVendorPackages);
                default:
                    // Third-party apps are always killable
                    return true;
            }
        }
    }

    /** Returns the list of vendor package prefixes. */
    public List<String> getVendorPackagePrefixes() {
        synchronized (mLock) {
            return new ArrayList<>(mVendorPackagePrefixes);
        }
    }

    @GuardedBy("mLock")
    private void clearLocked() {
        mSafeToKillSystemPackages.clear();
        mSafeToKillVendorPackages.clear();
        mVendorPackagePrefixes.clear();
        mPackagesByAppCategoryType.clear();
        mGenericIoThresholdsByComponent.clear();
        mIoThresholdsBySystemPackages.clear();
        mIoThresholdsByVendorPackages.clear();
        mIoThresholdsByAppCategoryType.clear();
    }

    @GuardedBy("mLock")
    private void setIoThresholdsLocked(int componentType, IoOveruseConfiguration ioConfig) {
        mGenericIoThresholdsByComponent.append(componentType,
                ioConfig.componentLevelThresholds.perStateWriteBytes);
        switch (componentType) {
            case ComponentType.SYSTEM:
                populateThresholdsByPackagesLocked(
                        ioConfig.packageSpecificThresholds, mIoThresholdsBySystemPackages);
                break;
            case ComponentType.VENDOR:
                populateThresholdsByPackagesLocked(
                        ioConfig.packageSpecificThresholds, mIoThresholdsByVendorPackages);
                setIoThresholdsByAppCategoryTypeLocked(ioConfig.categorySpecificThresholds);
                break;
        }
    }

    @GuardedBy("mLock")
    private void setIoThresholdsByAppCategoryTypeLocked(
            List<PerStateIoOveruseThreshold> thresholds) {
        for (int i = 0; i < thresholds.size(); ++i) {
            PerStateIoOveruseThreshold threshold = thresholds.get(i);
            switch(threshold.name) {
                case INTERNAL_APPLICATION_CATEGORY_TYPE_MAPS:
                    mIoThresholdsByAppCategoryType.append(
                            ApplicationCategoryType.MAPS, threshold.perStateWriteBytes);
                    break;
                case INTERNAL_APPLICATION_CATEGORY_TYPE_MEDIA:
                    mIoThresholdsByAppCategoryType.append(ApplicationCategoryType.MEDIA,
                            threshold.perStateWriteBytes);
                    break;
            }
        }
    }

    @GuardedBy("mLock")
    private void populateThresholdsByPackagesLocked(List<PerStateIoOveruseThreshold> thresholds,
            ArrayMap<String, PerStateBytes> thresholdsByPackages) {
        for (int i = 0; i < thresholds.size(); ++i) {
            thresholdsByPackages.put(
                    thresholds.get(i).name, thresholds.get(i).perStateWriteBytes);
        }
    }

    @GuardedBy("mLock")
    private PerStateBytes fetchAppCategorySpecificThresholdLocked(String genericPackageName) {
        for (int i = 0; i < mPackagesByAppCategoryType.size(); ++i) {
            if (mPackagesByAppCategoryType.valueAt(i).contains(genericPackageName)) {
                return mIoThresholdsByAppCategoryType.get(mPackagesByAppCategoryType.keyAt(i));
            }
        }
        return null;
    }

    private static String toApplicationCategoryTypeString(@ApplicationCategoryType int type) {
        switch (type) {
            case ApplicationCategoryType.MAPS:
                return "ApplicationCategoryType.MAPS";
            case ApplicationCategoryType.MEDIA:
                return "ApplicationCategoryType.MEDIA";
            case ApplicationCategoryType.OTHERS:
                return "ApplicationCategoryType.OTHERS";
            default:
                return "Invalid ApplicationCategoryType";
        }
    }

    private static String toComponentTypeString(@ComponentType int type) {
        switch (type) {
            case ComponentType.SYSTEM:
                return "ComponentType.SYSTEM";
            case ComponentType.VENDOR:
                return "ComponentType.VENDOR";
            case ComponentType.THIRD_PARTY:
                return "ComponentType.THIRD_PARTY";
            default:
                return "ComponentType.UNKNOWN";
        }
    }

    private static void dumpPerStateBytes(PerStateBytes perStateBytes,
            IndentingPrintWriter writer) {
        if (perStateBytes == null) {
            writer.println("{NULL}");
            return;
        }
        writer.println("{Foreground bytes: " + perStateBytes.foregroundBytes
                + ", Background bytes: " + perStateBytes.backgroundBytes + ", Garage mode bytes: "
                + perStateBytes.garageModeBytes + '}');
    }

    private static PerStateBytes constructPerStateBytes(long fgBytes, long bgBytes, long gmBytes) {
        return new PerStateBytes() {{
                foregroundBytes = fgBytes;
                backgroundBytes = bgBytes;
                garageModeBytes = gmBytes;
            }};
    }

    private static PerStateBytes copyPerStateBytes(PerStateBytes perStateBytes) {
        return new PerStateBytes() {{
                foregroundBytes = perStateBytes.foregroundBytes;
                backgroundBytes = perStateBytes.backgroundBytes;
                garageModeBytes = perStateBytes.garageModeBytes;
            }};
    }
}
