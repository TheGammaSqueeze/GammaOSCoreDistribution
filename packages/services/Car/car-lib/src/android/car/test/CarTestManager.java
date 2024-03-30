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
package android.car.test;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.TestApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.annotation.AddedInOrBefore;
import android.car.annotation.ApiRequirements;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * API for testing only. Allows mocking vehicle hal.
 *
 * @hide
 */
@TestApi
public final class CarTestManager extends CarManagerBase {

    private final ICarTest mService;

    /**
     * Constructs a new {@link CarTestManager}
     *
     * @hide
     */
    @TestApi
    public CarTestManager(@NonNull Car car, @NonNull IBinder carServiceBinder) {
        super(car);
        mService = ICarTest.Stub.asInterface(carServiceBinder);
    }

    /**
     * @hide
     */
    @Override
    @AddedInOrBefore(majorVersion = 33)
    public void onCarDisconnected() {
        // test will fail. nothing to do.
    }

    /**
     * Releases all car services. This make sense for test purpose when it is necessary to reduce
     * interference between testing and real instances of Car Service. For example changing audio
     * focus in CarAudioService may affect framework's AudioManager listeners. AudioManager has a
     * lot of complex logic which is hard to mock.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    @AddedInOrBefore(majorVersion = 33)
    public void stopCarService(@NonNull IBinder token) {
        try {
            mService.stopCarService(token);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Re-initializes previously released car service.
     *
     * @see {@link #stopCarService(IBinder)}
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    @AddedInOrBefore(majorVersion = 33)
    public void startCarService(@NonNull IBinder token) {
        try {
            mService.startCarService(token);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Returns OEM service name.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    @ApiRequirements(minCarVersion = ApiRequirements.CarVersion.TIRAMISU_2,
             minPlatformVersion = ApiRequirements.PlatformVersion.TIRAMISU_0)
    public String getOemServiceName() throws RemoteException {
        return mService.getOemServiceName();
    }
}
