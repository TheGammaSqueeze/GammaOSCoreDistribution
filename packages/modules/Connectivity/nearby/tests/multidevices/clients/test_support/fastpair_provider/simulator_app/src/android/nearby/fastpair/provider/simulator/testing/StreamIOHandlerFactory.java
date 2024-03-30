/*
 * Copyright (C) 2022 The Android Open Source Project
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

package android.nearby.fastpair.provider.simulator.testing;

import android.net.Uri;

import java.io.IOException;

/** A simple factory creating {@link StreamIOHandler} according to {@link Type}. */
public class StreamIOHandlerFactory {

    /** Types for creating {@link StreamIOHandler}. */
    public enum Type {

        /**
         * A {@link StreamIOHandler} accepts local file uris and provides reading/writing file
         * operations.
         */
        LOCAL_FILE
    }

    /** Creates an instance of {@link StreamIOHandler}. */
    public static StreamIOHandler createStreamIOHandler(Type type, Uri input, Uri output)
            throws IOException {
        if (type.equals(Type.LOCAL_FILE)) {
            return new LocalFileStreamIOHandler(input, output);
        }
        throw new IllegalArgumentException(String.format("Can't support %s", type));
    }
}
