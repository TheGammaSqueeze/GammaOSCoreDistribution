/*
 * Copyright 2021 The Android Open Source Project
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

package com.android.server.nearby.common.bluetooth.fastpair;

import android.bluetooth.BluetoothDevice;
import android.os.Parcel;
import android.os.Parcelable;

import com.android.server.nearby.intdefs.NearbyEventIntDefs.EventCode;

import java.util.Objects;

import javax.annotation.Nullable;

/**
 * Describes events that are happening during fast pairing. EventCode is required, everything else
 * is optional.
 */
public class Event implements Parcelable {

    private final @EventCode int mEventCode;
    private final long mTimestamp;
    private final Short mProfile;
    private final BluetoothDevice mBluetoothDevice;
    private final Exception mException;

    private Event(@EventCode int eventCode, long timestamp, @Nullable Short profile,
            @Nullable BluetoothDevice bluetoothDevice, @Nullable Exception exception) {
        mEventCode = eventCode;
        mTimestamp = timestamp;
        mProfile = profile;
        mBluetoothDevice = bluetoothDevice;
        mException = exception;
    }

    /**
     * Returns event code.
     */
    public @EventCode int getEventCode() {
        return mEventCode;
    }

    /**
     * Returns timestamp.
     */
    public long getTimestamp() {
        return mTimestamp;
    }

    /**
     * Returns profile.
     */
    @Nullable
    public Short getProfile() {
        return mProfile;
    }

    /**
     * Returns Bluetooth device.
     */
    @Nullable
    public BluetoothDevice getBluetoothDevice() {
        return mBluetoothDevice;
    }

    /**
     * Returns exception.
     */
    @Nullable
    public Exception getException() {
        return mException;
    }

    /**
     * Returns whether profile is not null.
     */
    public boolean hasProfile() {
        return getProfile() != null;
    }

    /**
     * Returns whether Bluetooth device is not null.
     */
    public boolean hasBluetoothDevice() {
        return getBluetoothDevice() != null;
    }

    /**
     * Returns a builder.
     */
    public static Builder builder() {
        return new Event.Builder();
    }

    /**
     * Returns whether it fails.
     */
    public boolean isFailure() {
        return getException() != null;
    }

    @Override
    public String toString() {
        return "Event{"
                + "eventCode=" + mEventCode + ", "
                + "timestamp=" + mTimestamp + ", "
                + "profile=" + mProfile + ", "
                + "bluetoothDevice=" + mBluetoothDevice + ", "
                + "exception=" + mException
                + "}";
    }

    @Override
    public boolean equals(@Nullable Object o) {
        if (o == this) {
            return true;
        }
        if (o instanceof Event) {
            Event that = (Event) o;
            return this.mEventCode == that.getEventCode()
                    && this.mTimestamp == that.getTimestamp()
                    && (this.mProfile == null
                        ? that.getProfile() == null : this.mProfile.equals(that.getProfile()))
                    && (this.mBluetoothDevice == null
                        ? that.getBluetoothDevice() == null :
                            this.mBluetoothDevice.equals(that.getBluetoothDevice()))
                    && (this.mException == null
                        ?  that.getException() == null :
                            this.mException.equals(that.getException()));
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(mEventCode, mTimestamp, mProfile, mBluetoothDevice, mException);
    }


    /**
     * Builder
     */
    public static class Builder {
        private @EventCode int mEventCode;
        private long mTimestamp;
        private Short mProfile;
        private BluetoothDevice mBluetoothDevice;
        private Exception mException;

        /**
         * Set event code.
         */
        public Builder setEventCode(@EventCode int eventCode) {
            this.mEventCode = eventCode;
            return this;
        }

        /**
         * Set timestamp.
         */
        public Builder setTimestamp(long timestamp) {
            this.mTimestamp = timestamp;
            return this;
        }

        /**
         * Set profile.
         */
        public Builder setProfile(@Nullable Short profile) {
            this.mProfile = profile;
            return this;
        }

        /**
         * Set Bluetooth device.
         */
        public Builder setBluetoothDevice(@Nullable BluetoothDevice device) {
            this.mBluetoothDevice = device;
            return this;
        }

        /**
         * Set exception.
         */
        public Builder setException(@Nullable Exception exception) {
            this.mException = exception;
            return this;
        }

        /**
         * Builds event.
         */
        public Event build() {
            return new Event(mEventCode, mTimestamp, mProfile, mBluetoothDevice, mException);
        }
    }

    @Override
    public final void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(getEventCode());
        dest.writeLong(getTimestamp());
        dest.writeValue(getProfile());
        dest.writeParcelable(getBluetoothDevice(), 0);
        dest.writeSerializable(getException());
    }

    @Override
    public final int describeContents() {
        return 0;
    }

    /**
     * Event Creator instance.
     */
    public static final Creator<Event> CREATOR =
            new Creator<Event>() {
                @Override
                /** Creates Event from Parcel. */
                public Event createFromParcel(Parcel in) {
                    return Event.builder()
                            .setEventCode(in.readInt())
                            .setTimestamp(in.readLong())
                            .setProfile((Short) in.readValue(Short.class.getClassLoader()))
                            .setBluetoothDevice(
                                    in.readParcelable(BluetoothDevice.class.getClassLoader()))
                            .setException((Exception) in.readSerializable())
                            .build();
                }

                @Override
                /** Returns Event array. */
                public Event[] newArray(int size) {
                    return new Event[size];
                }
            };
}
