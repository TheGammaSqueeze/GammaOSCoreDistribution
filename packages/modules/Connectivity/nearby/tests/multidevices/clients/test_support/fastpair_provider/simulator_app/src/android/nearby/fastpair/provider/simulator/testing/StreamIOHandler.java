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

import com.google.protobuf.ByteString;

import java.io.IOException;

/**
 * Opens input and output data channels, then provides read and write operations to the data
 * channels.
 */
public interface StreamIOHandler {
    /**
     * Reads stream data from the input channel.
     *
     * @return a protocol buffer contains the input message
     * @throws IOException errors occur when reading the input stream
     */
    ByteString read() throws IOException;

    /**
     * Writes stream data to the output channel.
     *
     * @param output a protocol buffer contains the output message
     * @throws IOException errors occur when writing the output message to output stream
     */
    void write(ByteString output) throws IOException;
}
