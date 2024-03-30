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

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.BaseEncoding.base16;

import android.net.Uri;

import androidx.annotation.Nullable;

import com.google.protobuf.ByteString;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;

/**
 * Opens the {@code inputUri} and {@code outputUri} as local files and provides reading/writing
 * data operations.
 *
 * To support bluetooth testing on real devices, the named pipes are created as local files and the
 * pipe data are transferred via usb cable, then (1) the peripheral device writes {@code Event} to
 * the output stream and reads {@code Command} from the input stream (2) the central devices write
 * {@code Command} to the output stream and read {@code Event} from the input stream.
 *
 * The {@code Event} and {@code Command} are special protocols which are defined at
 * simulator_stream_protocol.proto.
 */
public class LocalFileStreamIOHandler implements StreamIOHandler {

    private static final int MAX_IO_DATA_LENGTH_BYTE = 65535;

    private final String mInputPath;
    private final String mOutputPath;

    LocalFileStreamIOHandler(Uri inputUri, Uri outputUri) throws IOException {
        if (!isFileExists(inputUri.getPath())) {
            throw new FileNotFoundException("Input path is not exists.");
        }
        if (!isFileExists(outputUri.getPath())) {
            throw new FileNotFoundException("Output path is not exists.");
        }

        this.mInputPath = inputUri.getPath();
        this.mOutputPath = outputUri.getPath();
    }

    /**
     * Reads a {@code ByteString} from the input stream. The input stream must be opened before
     * calling this method.
     */
    @Override
    public ByteString read() throws IOException {
        try (InputStreamReader inputStream = new InputStreamReader(
                new FileInputStream(mInputPath))) {
            int size = inputStream.read();
            if (size == 0) {
                throw new IOException(String.format("Missing data size %d", size));
            }

            if (size > MAX_IO_DATA_LENGTH_BYTE) {
                throw new IOException("Exceed the maximum data length when reading.");
            }

            char[] data = new char[size];
            int count = inputStream.read(data);
            if (count != size) {
                throw new IOException(
                        String.format("Expected size was %s but got %s", size, count));
            }

            return ByteString.copyFrom(base16().decode(new String(data)));
        }
    }

    /**
     * Writes a {@code output} into the output stream. The output stream must be opened before
     * calling this method.
     */
    @Override
    public void write(ByteString output) throws IOException {
        checkArgument(output.size() > 0, "Output data is empty.");

        if (output.size() > MAX_IO_DATA_LENGTH_BYTE) {
            throw new IOException("Exceed the maximum data length when writing.");
        }

        try (OutputStreamWriter outputStream =
                     new OutputStreamWriter(new FileOutputStream(mOutputPath))) {
            String base16Output = base16().encode(output.toByteArray());
            outputStream.write(base16Output.length());
            outputStream.write(base16Output);
        }
    }

    private static boolean isFileExists(@Nullable String path) {
        return path != null && new File(path).exists();
    }
}
