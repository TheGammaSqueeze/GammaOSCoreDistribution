/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car.apitest;

import android.car.Car;
import android.car.CarVersion;
import android.car.content.pm.CarPackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.test.suitebuilder.annotation.MediumTest;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertThrows;

import org.junit.Before;
import org.junit.Test;

@MediumTest
public class CarPackageManagerTest extends CarApiTestBase {

    private CarPackageManager mCarPackageManager;

    @Before
    public void setFixtures() {
        mCarPackageManager = (CarPackageManager) getCar().getCarManager(Car.PACKAGE_SERVICE);
    }

    @Test
    public void testCreate() throws Exception {
        assertThat(mCarPackageManager).isNotNull();
    }

    @Test
    public void testGetTargetCarVersion_self() throws Exception {
        CarVersion apiVersion = mCarPackageManager.getTargetCarVersion();

        assertWithMessage("getTargetCarVersion()").that(apiVersion).isNotNull();
        assertWithMessage("major version")
                .that(apiVersion.getMajorVersion())
                .isEqualTo(108);
        assertWithMessage("minor version for")
                .that(apiVersion.getMinorVersion())
                .isEqualTo(42);
    }

    @Test
    public void testgetTargetCarVersion_noPackage() throws Exception {
        String pkg = "I can't believe a package with this name exist. If so, well, too bad!";

        NameNotFoundException e = assertThrows(NameNotFoundException.class,
                () -> mCarPackageManager.getTargetCarVersion(pkg));

        assertWithMessage("exception msg").that(e.getMessage()).contains(pkg);
    }

    @Test
    public void testGetTargetCarMajorAndMinorVersion_notSet() throws Exception {
        String pkg = "com.android.car";
        // TODO(b/228506662): it would be better add another app that explicitly sets sdkTarget
        // version instead, so it doesn't depend on com.android.car's version (which this test
        // doesn't control)
        int targetSdk = Car.getPlatformVersion().getMajorVersion();

        CarVersion apiVersion = mCarPackageManager.getTargetCarVersion(pkg);

        assertWithMessage("getTargetCarVersion(%s)", pkg).that(apiVersion).isNotNull();
        assertWithMessage("major version for %s", pkg)
                .that(apiVersion.getMajorVersion())
                .isEqualTo(targetSdk);
        assertWithMessage("minor version for %s", pkg)
                .that(apiVersion.getMinorVersion())
                .isEqualTo(0);
    }

    @Test
    public void testGetTargetCarMajorAndMinorVersion_set() throws Exception {
        String pkg = sContext.getPackageName();

        CarVersion apiVersion = mCarPackageManager.getTargetCarVersion(pkg);

        assertWithMessage("getTargetCarVersion(%s)", pkg).that(apiVersion).isNotNull();
        assertWithMessage("major version for %s", pkg)
                .that(apiVersion.getMajorVersion())
                .isEqualTo(108);
        assertWithMessage("minor version for %s", pkg)
                .that(apiVersion.getMinorVersion())
                .isEqualTo(42);
    }
}
