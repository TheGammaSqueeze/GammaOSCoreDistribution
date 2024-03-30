/*
 * Copyright (C) 2018 The Android Open Source Project
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
package com.android.car.vehiclehal.test;

import static org.junit.Assert.assertEquals;

import android.hardware.automotive.vehicle.V2_0.IVehicle;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

class JsonVhalEventGenerator implements VhalEventGenerator {

    // Exactly one iteration is required for JSON-based end-to-end test
    private static final String NUM_OF_ITERATION = "1";

    private IVehicle mVehicle;
    private File mFile;

    JsonVhalEventGenerator(IVehicle vehicle) {
        mVehicle = vehicle;
    }

    public JsonVhalEventGenerator setJsonFile(File file) throws Exception {
        if (!file.exists()) {
            throw new Exception("JSON test data file does not exist: " + file.getAbsolutePath());
        }
        mFile = file;
        return this;
    }

    @Override
    public void start() throws Exception {
        ArrayList<String> options = new ArrayList<String>(Arrays.asList(
                "--debughal", "--genfakedata", "--startjson", mFile.getAbsolutePath(),
                NUM_OF_ITERATION));

        NativePipeHelper pipe = new NativePipeHelper();
        pipe.create();
        mVehicle.debug(pipe.getNativeHandle(), options);
        assertEquals("", pipe.getOutput());
        pipe.close();
    }

    @Override
    public void stop() throws Exception {
        ArrayList<String> options = new ArrayList<String>(Arrays.asList(
                "--debughal", "--genfakedata", "--stopjson",
                mFile.getAbsolutePath()));
        NativePipeHelper pipe = new NativePipeHelper();
        pipe.create();
        mVehicle.debug(pipe.getNativeHandle(), options);
        assertEquals("", pipe.getOutput());
        pipe.close();
    }
}
