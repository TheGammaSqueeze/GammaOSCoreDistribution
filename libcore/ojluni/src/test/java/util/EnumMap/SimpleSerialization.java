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

/*
 * @test
 * @bug 6312706
 * @summary A serialized EnumMap can be successfully de-serialized.
 * @author Neil Richards <neil.richards@ngmr.net>, <neil_richards@uk.ibm.com>
 */
package test.java.util.EnumMap;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.EnumMap;

import org.testng.Assert;
import org.testng.annotations.Test;

public class SimpleSerialization {
    private enum TestEnum { e00, e01, e02, e03, e04, e05, e06, e07 }

    @Test
    public void testSimpleSerialization() throws Exception {
        final EnumMap<TestEnum, String> enumMap = new EnumMap<>(TestEnum.class);

        enumMap.put(TestEnum.e01, TestEnum.e01.name());
        enumMap.put(TestEnum.e04, TestEnum.e04.name());
        enumMap.put(TestEnum.e05, TestEnum.e05.name());

        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        final ObjectOutputStream oos = new ObjectOutputStream(baos);

        oos.writeObject(enumMap);
        oos.close();

        final byte[] data = baos.toByteArray();
        final ByteArrayInputStream bais = new ByteArrayInputStream(data);
        final ObjectInputStream ois = new ObjectInputStream(bais);

        final Object deserializedObject = ois.readObject();
        ois.close();

        Assert.assertTrue(enumMap.equals(deserializedObject));
    }
}