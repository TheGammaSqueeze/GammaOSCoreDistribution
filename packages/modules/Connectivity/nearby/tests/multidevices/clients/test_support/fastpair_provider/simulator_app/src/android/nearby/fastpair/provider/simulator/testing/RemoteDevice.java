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

/** Represents a remote device and provides a {@link StreamIOHandler} to communicate with it. */
public class RemoteDevice {
    private final String mId;
    private final StreamIOHandler mStreamIOHandler;
    private final InputStreamListener mInputStreamListener;

    public RemoteDevice(
            String id, StreamIOHandler streamIOHandler, InputStreamListener inputStreamListener) {
        this.mId = id;
        this.mStreamIOHandler = streamIOHandler;
        this.mInputStreamListener = inputStreamListener;
    }

    /** The id used by this device. */
    public String getId() {
        return mId;
    }

    /** The handler processes input and output data channels. */
    public StreamIOHandler getStreamIOHandler() {
        return mStreamIOHandler;
    }

    /** Listener for the input stream. */
    public InputStreamListener getInputStreamListener() {
        return mInputStreamListener;
    }
}
