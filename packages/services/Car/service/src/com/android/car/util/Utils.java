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
package com.android.car.util;

import static android.car.user.CarUserManager.lifecycleEventTypeToString;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.BOILERPLATE_CODE;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SuppressLint;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.builtin.util.Slogf;
import android.car.user.CarUserManager.UserLifecycleEvent;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.UserHandle;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.common.CommonConstants.UserLifecycleEventType;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

/**
 * Some potentially useful static methods.
 */
public final class Utils {
    static final Boolean DBG = false;
    // https://developer.android.com/reference/java/util/UUID
    private static final int UUID_LENGTH = 16;

    /**
     * Returns a byte buffer corresponding to the passed long argument.
     *
     * @param primitive data to convert format.
     */
    public static byte[] longToBytes(long primitive) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.BYTES);
        buffer.putLong(primitive);
        return buffer.array();
    }

    /**
     * Returns a byte buffer corresponding to the passed long argument.
     *
     * @param array data to convert format.
     */
    public static long bytesToLong(byte[] array) {
        ByteBuffer buffer = ByteBuffer.allocate(Long.SIZE / Byte.SIZE);
        buffer.put(array);
        buffer.flip();
        long value = buffer.getLong();
        return value;
    }

    /**
     * Returns a String in Hex format that is formed from the bytes in the byte array
     * Useful for debugging
     *
     * @param array the byte array
     * @return the Hex string version of the input byte array
     */
    public static String byteArrayToHexString(byte[] array) {
        StringBuilder sb = new StringBuilder(array.length * 2);
        for (byte b : array) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    /**
     * Convert UUID to Big Endian byte array
     *
     * @param uuid UUID to convert
     * @return the byte array representing the UUID
     */
    @NonNull
    public static byte[] uuidToBytes(@NonNull UUID uuid) {

        return ByteBuffer.allocate(UUID_LENGTH)
                .order(ByteOrder.BIG_ENDIAN)
                .putLong(uuid.getMostSignificantBits())
                .putLong(uuid.getLeastSignificantBits())
                .array();
    }

    /**
     * Convert Big Endian byte array to UUID
     *
     * @param bytes byte array to convert
     * @return the UUID representing the byte array, or null if not a valid UUID
     */
    @Nullable
    public static UUID bytesToUUID(@NonNull byte[] bytes) {
        if (bytes.length != UUID_LENGTH) {
            return null;
        }

        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        return new UUID(buffer.getLong(), buffer.getLong());
    }

    /**
     * Generate a random zero-filled string of given length
     *
     * @param length of string
     * @return generated string
     */
    @SuppressLint("DefaultLocale")  // Should always have the same format regardless of locale
    public static String generateRandomNumberString(int length) {
        return String.format("%0" + length + "d",
                ThreadLocalRandom.current().nextInt((int) Math.pow(10, length)));
    }


    /**
     * Concatentate the given 2 byte arrays
     *
     * @param a input array 1
     * @param b input array 2
     * @return concatenated array of arrays 1 and 2
     */
    @Nullable
    public static byte[] concatByteArrays(@Nullable byte[] a, @Nullable byte[] b) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try {
            if (a != null) {
                outputStream.write(a);
            }
            if (b != null) {
                outputStream.write(b);
            }
        } catch (IOException e) {
            return null;
        }
        return outputStream.toByteArray();
    }

    @ExcludeFromCodeCoverageGeneratedReport(reason = BOILERPLATE_CODE,
            details = "private constructor")
    private Utils() {
        throw new UnsupportedOperationException("contains only static methods");
    }

    /**
     * Returns the content resolver for the given user. This can be used to put/get the
     * user's settings.
     *
     * @param context The context of the package.
     * @param userId The id of the user which the content resolver is being requested for. It also
     * accepts {@link UserHandle#USER_CURRENT}.
     */
    public static ContentResolver getContentResolverForUser(Context context,
            @UserIdInt int userId) {
        if (userId == UserHandle.CURRENT.getIdentifier()) {
            userId = ActivityManager.getCurrentUser();
        }
        return context
                .createContextAsUser(
                        UserHandle.of(userId), /* flags= */ 0)
                .getContentResolver();
    }

    /**
     * Checks if the type of the {@code event} matches {@code expectedType}.
     *
     * @param tag The tag for logging.
     * @param event The event to check the type against {@code expectedType}.
     * @param expectedType The expected event type.
     * @return true if {@code event}'s type matches {@code expectedType}.
     *         Otherwise, log a wtf and return false.
     */
    public static boolean isEventOfType(String tag, UserLifecycleEvent event,
            @UserLifecycleEventType int expectedType) {
        if (event.getEventType() == expectedType) {
            return true;
        }
        Slogf.wtf(tag, "Received an unexpected event: %s. Expected type: %s.", event,
                lifecycleEventTypeToString(expectedType));
        return false;
    }

    /**
     * Checks if the type of the {@code event} is one of the types in {@code expectedTypes}.
     *
     * @param tag The tag for logging.
     * @param event The event to check the type against {@code expectedTypes}.
     * @param expectedTypes The expected event types. Must not be empty.
     * @return true if {@code event}'s type can be found in {@code expectedTypes}.
     *         Otherwise, log a wtf and return false.
     */
    public static boolean isEventAnyOfTypes(String tag, UserLifecycleEvent event,
            @UserLifecycleEventType int... expectedTypes) {
        for (int i = 0; i < expectedTypes.length; i++) {
            if (event.getEventType() == expectedTypes[i]) {
                return true;
            }
        }
        Slogf.wtf(tag, "Received an unexpected event: %s. Expected types: [%s]", event,
                Arrays.stream(expectedTypes).mapToObj(t -> lifecycleEventTypeToString(t)).collect(
                        Collectors.joining(",")));
        return false;
    }

    /**
     * Checks if the calling UID owns the give package.
     *
     * @throws SecurityException if the calling UID doesn't own the given package.
     */
    public static void checkCalledByPackage(Context context, String packageName) {
        int callingUid = Binder.getCallingUid();
        PackageManager pm = context.getPackageManager();
        String[] packages = pm.getPackagesForUid(callingUid);
        if (packages != null) {
            for (String candidate: packages) {
                if (candidate.equals(packageName)) {
                    return;
                }
            }
        }
        throw new SecurityException(
                "Package " + packageName + " is not associated to UID " + callingUid);
    }

}
