/*
 * Copyright (C) 2017 The Android Open Source Project
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
package android.edi.cts;

import static android.compat.testing.Classpaths.ClasspathType.BOOTCLASSPATH;
import static android.compat.testing.Classpaths.ClasspathType.SYSTEMSERVERCLASSPATH;

import static org.junit.Assume.assumeTrue;

import android.compat.testing.Classpaths;
import android.compat.testing.Classpaths.ClasspathType;
import android.compat.testing.SharedLibraryInfo;

import com.android.compatibility.common.util.DeviceInfo;
import com.android.compatibility.common.util.HostInfoStore;
import com.android.modules.utils.build.testing.DeviceSdkLevel;
import com.android.tradefed.device.DeviceNotAvailableException;
import com.android.tradefed.device.ITestDevice;
import com.android.tradefed.util.FileUtil;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;

import org.jf.dexlib2.iface.ClassDef;
import org.junit.Before;

import java.io.File;
import java.io.IOException;
import java.util.stream.IntStream;

/**
 * Collects information about Java classes present in *CLASSPATH variables and Java shared libraries
 * from the device.
 */
public class ClasspathDeviceInfo extends DeviceInfo {

    private final Object mStoreLock = new Object();

    private ITestDevice mDevice;
    private DeviceSdkLevel mDeviceSdkLevel;

    @Before
    public void before() throws DeviceNotAvailableException {
        mDevice = getDevice();
        mDeviceSdkLevel = new DeviceSdkLevel(mDevice);
        assumeTrue(mDeviceSdkLevel.isDeviceAtLeastR());
    }

    @Override
    protected void collectDeviceInfo(HostInfoStore store) throws Exception {
        store.startArray("jars");
        collectClasspathsJars(store);
        collectSharedLibraries(store);
        store.endArray();
    }

    private void collectClasspathsJars(HostInfoStore store) throws Exception {
        collectClasspathJarInfo(store, BOOTCLASSPATH);
        collectClasspathJarInfo(store, SYSTEMSERVERCLASSPATH);
        // No need to collect DEX2OATBOOTCLASSPATH, as it is just a subset of BOOTCLASSPATH
    }

    private void collectClasspathJarInfo(HostInfoStore store, ClasspathType classpath)
            throws Exception {
        ImmutableList<String> paths = Classpaths.getJarsOnClasspath(mDevice, classpath);
        IntStream.range(0, paths.size())
                .parallel()
                .mapToObj(i -> new JarInfo(i, paths.get(i)))
                .peek(JarInfo::pullRemoteJar)
                .peek(JarInfo::parseClassDefs)
                .forEach(jarInfo -> {
                    synchronized (mStoreLock) {
                        try {
                            store.startGroup();
                            store.addResult("classpath", classpath.name());
                            store.addResult("path", jarInfo.mPath);
                            store.addResult("index", jarInfo.mIndex);
                            collectClassInfo(store, jarInfo.mClassDefs);
                            store.endGroup();
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        } finally {
                            FileUtil.deleteFile(jarInfo.mLocalCopy);
                        }
                    }
                });
    }

    private void collectSharedLibraries(HostInfoStore store) throws Exception {
        if (!mDeviceSdkLevel.isDeviceAtLeastS()) {
            return;
        }
        Classpaths.getSharedLibraryInfos(mDevice, getBuild())
                .stream()
                .parallel()
                .flatMap(sharedLibraryInfo -> IntStream.range(0, sharedLibraryInfo.paths.size())
                        .parallel()
                        .mapToObj(i ->
                                new JarInfo(i, sharedLibraryInfo.paths.get(i), sharedLibraryInfo))
                )
                .filter(JarInfo::doesRemoteFileExist)
                .peek(JarInfo::pullRemoteJar)
                .peek(JarInfo::parseClassDefs)
                .forEach(jarInfo -> {
                    synchronized (mStoreLock) {
                        try {
                            store.startGroup();
                            store.startGroup("shared_library");
                            store.addResult("name", jarInfo.mSharedLibraryInfo.name);
                            store.addResult("type", jarInfo.mSharedLibraryInfo.type);
                            store.addResult("version", jarInfo.mSharedLibraryInfo.version);
                            store.endGroup(); // shared_library
                            store.addResult("path", jarInfo.mPath);
                            store.addResult("index", jarInfo.mIndex);
                            collectClassInfo(store, jarInfo.mClassDefs);
                            store.endGroup();
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        } finally {
                            FileUtil.deleteFile(jarInfo.mLocalCopy);
                        }
                    }
                });
    }


    private void collectClassInfo(HostInfoStore store, ImmutableSet<ClassDef> defs)
            throws IOException {
        store.startArray("classes");
        for (ClassDef classDef : defs) {
            store.startGroup();
            store.addResult("name", classDef.getType());
            store.endGroup();
        }
        store.endArray();
    }

    /** Helper class to represent a jar file during stream transformations. */
    private final class JarInfo {
        final int mIndex;
        final String mPath;
        final SharedLibraryInfo mSharedLibraryInfo;
        ImmutableSet<ClassDef> mClassDefs;
        File mLocalCopy;

        private JarInfo(int index, String path) {
            this(index, path, null);
        }

        private JarInfo(int index, String path, SharedLibraryInfo sharedLibraryInfo) {
            this.mIndex = index;
            this.mPath = path;
            this.mSharedLibraryInfo = sharedLibraryInfo;
        }

        private boolean doesRemoteFileExist() {
            try {
                return mDevice.doesFileExist(mPath);
            } catch (DeviceNotAvailableException e) {
                throw new RuntimeException(e);
            }
        }

        private void pullRemoteJar() {
            try {
                mLocalCopy = mDevice.pullFile(mPath);
            } catch (DeviceNotAvailableException e) {
                throw new RuntimeException(e);
            }
        }

        private void parseClassDefs() {
            try {
                mClassDefs = Classpaths.getClassDefsFromJar(mLocalCopy);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
    }

}
