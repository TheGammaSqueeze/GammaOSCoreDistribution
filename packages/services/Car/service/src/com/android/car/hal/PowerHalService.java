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
package com.android.car.hal;

import static android.hardware.automotive.vehicle.VehicleProperty.AP_POWER_STATE_REPORT;
import static android.hardware.automotive.vehicle.VehicleProperty.AP_POWER_STATE_REQ;
import static android.hardware.automotive.vehicle.VehicleProperty.DISPLAY_BRIGHTNESS;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.hardware.automotive.vehicle.VehicleApPowerStateConfigFlag;
import android.hardware.automotive.vehicle.VehicleApPowerStateReport;
import android.hardware.automotive.vehicle.VehicleApPowerStateReq;
import android.hardware.automotive.vehicle.VehicleApPowerStateReqIndex;
import android.hardware.automotive.vehicle.VehicleApPowerStateShutdownParam;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.os.ServiceSpecificException;

import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Translates HAL power events to higher-level semantic information.
 */
public class PowerHalService extends HalServiceBase {
    // Set display brightness from 0-100%
    public static final int MAX_BRIGHTNESS = 100;

    private static final int[] SUPPORTED_PROPERTIES = new int[]{
            AP_POWER_STATE_REQ,
            AP_POWER_STATE_REPORT,
            DISPLAY_BRIGHTNESS
    };

    @VisibleForTesting
    public static final int SET_WAIT_FOR_VHAL = VehicleApPowerStateReport.WAIT_FOR_VHAL;
    @VisibleForTesting
    public static final int SET_DEEP_SLEEP_ENTRY = VehicleApPowerStateReport.DEEP_SLEEP_ENTRY;
    @VisibleForTesting
    public static final int SET_DEEP_SLEEP_EXIT = VehicleApPowerStateReport.DEEP_SLEEP_EXIT;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_POSTPONE = VehicleApPowerStateReport.SHUTDOWN_POSTPONE;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_START = VehicleApPowerStateReport.SHUTDOWN_START;
    @VisibleForTesting
    public static final int SET_ON = VehicleApPowerStateReport.ON;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_PREPARE = VehicleApPowerStateReport.SHUTDOWN_PREPARE;
    @VisibleForTesting
    public static final int SET_SHUTDOWN_CANCELLED = VehicleApPowerStateReport.SHUTDOWN_CANCELLED;

    @VisibleForTesting
    public static final int SHUTDOWN_CAN_SLEEP = VehicleApPowerStateShutdownParam.CAN_SLEEP;
    @VisibleForTesting
    public static final int SHUTDOWN_IMMEDIATELY =
            VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY;
    @VisibleForTesting
    public static final int SHUTDOWN_ONLY = VehicleApPowerStateShutdownParam.SHUTDOWN_ONLY;
    @VisibleForTesting
    public static final int SET_HIBERNATION_ENTRY = VehicleApPowerStateReport.HIBERNATION_ENTRY;
    @VisibleForTesting
    public static final int SET_HIBERNATION_EXIT = VehicleApPowerStateReport.HIBERNATION_EXIT;

    private final Object mLock = new Object();

    private static String powerStateReportName(int state) {
        String baseName;
        switch(state) {
            case SET_WAIT_FOR_VHAL:      baseName = "WAIT_FOR_VHAL";      break;
            case SET_DEEP_SLEEP_ENTRY:   baseName = "DEEP_SLEEP_ENTRY";   break;
            case SET_DEEP_SLEEP_EXIT:    baseName = "DEEP_SLEEP_EXIT";    break;
            case SET_SHUTDOWN_POSTPONE:  baseName = "SHUTDOWN_POSTPONE";  break;
            case SET_SHUTDOWN_START:     baseName = "SHUTDOWN_START";     break;
            case SET_ON:                 baseName = "ON";                 break;
            case SET_SHUTDOWN_PREPARE:   baseName = "SHUTDOWN_PREPARE";   break;
            case SET_SHUTDOWN_CANCELLED: baseName = "SHUTDOWN_CANCELLED"; break;
            case SET_HIBERNATION_ENTRY:  baseName = "HIBERNATION_ENTRY";  break;
            case SET_HIBERNATION_EXIT:   baseName = "HIBERNATION_EXIT";   break;
            default:                     baseName = "<unknown>";          break;
        }
        return baseName + "(" + state + ")";
    }

    private static String powerStateReqName(int state) {
        String baseName;
        switch(state) {
            case VehicleApPowerStateReq.ON:               baseName = "ON";               break;
            case VehicleApPowerStateReq.SHUTDOWN_PREPARE: baseName = "SHUTDOWN_PREPARE"; break;
            case VehicleApPowerStateReq.CANCEL_SHUTDOWN:  baseName = "CANCEL_SHUTDOWN";  break;
            case VehicleApPowerStateReq.FINISHED:         baseName = "FINISHED";         break;
            default:                                      baseName = "<unknown>";        break;
        }
        return baseName + "(" + state + ")";
    }

    /**
     * Interface to be implemented by any object that wants to be notified by any Vehicle's power
     * change.
     */
    public interface PowerEventListener {
        /**
         * Received power state change event.
         * @param state One of STATE_*
         */
        void onApPowerStateChange(PowerState state);

        /**
         * Received display brightness change event.
         * @param brightness in percentile. 100% full.
         */
        void onDisplayBrightnessChange(int brightness);
    }

    /**
     * Contains information about the Vehicle's power state.
     */
    public static final class PowerState {

        @IntDef({SHUTDOWN_TYPE_UNDEFINED, SHUTDOWN_TYPE_POWER_OFF, SHUTDOWN_TYPE_DEEP_SLEEP,
                SHUTDOWN_TYPE_HIBERNATION})
        @Retention(RetentionPolicy.SOURCE)
        public @interface ShutdownType {}

        public static final int SHUTDOWN_TYPE_UNDEFINED = 0;
        public static final int SHUTDOWN_TYPE_POWER_OFF = 1;
        public static final int SHUTDOWN_TYPE_DEEP_SLEEP = 2;
        public static final int SHUTDOWN_TYPE_HIBERNATION = 3;
        /**
         * One of STATE_*
         */
        public final int mState;
        public final int mParam;

        public PowerState(int state, int param) {
            this.mState = state;
            this.mParam = param;
        }

        /**
         * Whether the current PowerState allows postponing or not. Calling this for
         * power state other than STATE_SHUTDOWN_PREPARE will trigger exception.
         * @return
         * @throws IllegalStateException
         */
        public boolean canPostponeShutdown() {
            if (mState != VehicleApPowerStateReq.SHUTDOWN_PREPARE) {
                throw new IllegalStateException("wrong state");
            }
            return (mParam != VehicleApPowerStateShutdownParam.SHUTDOWN_IMMEDIATELY
                    && mParam != VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY
                    && mParam != VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY);
        }

        /**
         * Gets whether the current PowerState allows suspend or not.
         *
         * @throws IllegalStateException if called in state other than {@code
         * STATE_SHUTDOWN_PREPARE}
         */
        public boolean canSuspend() {
            Preconditions.checkArgument(mState == VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                    "canSuspend was called in the wrong state! State = %d", mState);

            return (mParam == VehicleApPowerStateShutdownParam.CAN_HIBERNATE
                    || mParam == VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY
                    || mParam == VehicleApPowerStateShutdownParam.CAN_SLEEP
                    || mParam == VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY);
        }

        /**
         * Gets shutdown type
         *
         * @return {@code ShutdownType} - type of shutdown
         * @throws IllegalStateException if called in state other than {@code
         * STATE_SHUTDOWN_PREPARE}
         */
        @ShutdownType
        public int getShutdownType() {
            Preconditions.checkArgument(mState == VehicleApPowerStateReq.SHUTDOWN_PREPARE,
                    "getShutdownType was called in the wrong state! State = %d", mState);

            int result = SHUTDOWN_TYPE_POWER_OFF;
            if (mParam == VehicleApPowerStateShutdownParam.CAN_SLEEP
                    || mParam == VehicleApPowerStateShutdownParam.SLEEP_IMMEDIATELY) {
                result = SHUTDOWN_TYPE_DEEP_SLEEP;
            } else if (mParam == VehicleApPowerStateShutdownParam.CAN_HIBERNATE
                    || mParam == VehicleApPowerStateShutdownParam.HIBERNATE_IMMEDIATELY) {
                result = SHUTDOWN_TYPE_HIBERNATION;
            }

            return result;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof PowerState)) {
                return false;
            }
            PowerState that = (PowerState) o;
            return this.mState == that.mState && this.mParam == that.mParam;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mState, mParam);
        }

        @Override
        public String toString() {
            return "PowerState state:" + mState + ", param:" + mParam;
        }
    }

    @GuardedBy("mLock")
    private final HashMap<Integer, HalPropConfig> mProperties = new HashMap<>();
    private final VehicleHal mHal;
    @GuardedBy("mLock")
    private LinkedList<HalPropValue> mQueuedEvents;
    @GuardedBy("mLock")
    private PowerEventListener mListener;
    @GuardedBy("mLock")
    private int mMaxDisplayBrightness;

    public PowerHalService(VehicleHal hal) {
        mHal = hal;
    }

    /**
     * Sets the event listener to receive Vehicle's power events.
     */
    public void setListener(PowerEventListener listener) {
        LinkedList<HalPropValue> eventsToDispatch = null;
        synchronized (mLock) {
            mListener = listener;
            if (mQueuedEvents != null && mQueuedEvents.size() > 0) {
                eventsToDispatch = mQueuedEvents;
            }
            mQueuedEvents = null;
        }
        // do this outside lock
        if (eventsToDispatch != null) {
            dispatchEvents(eventsToDispatch, listener);
        }
    }

    /**
     * Send WaitForVhal message to VHAL
     */
    public void sendWaitForVhal() {
        Slogf.i(CarLog.TAG_POWER, "send wait for vhal");
        setPowerState(VehicleApPowerStateReport.WAIT_FOR_VHAL, 0);
    }

    /**
     * Send SleepEntry message to VHAL
     * @param wakeupTimeSec Notify VHAL when system wants to be woken from sleep.
     */
    public void sendSleepEntry(int wakeupTimeSec) {
        Slogf.i(CarLog.TAG_POWER, "send sleep entry");
        setPowerState(VehicleApPowerStateReport.DEEP_SLEEP_ENTRY, wakeupTimeSec);
    }

    /**
     * Send SleepExit message to VHAL
     * Notifies VHAL when SOC has woken.
     */
    public void sendSleepExit() {
        Slogf.i(CarLog.TAG_POWER, "send sleep exit");
        setPowerState(VehicleApPowerStateReport.DEEP_SLEEP_EXIT, 0);
    }

    /**
     * Sends HibernationEntry message to VHAL
     *
     * @param wakeupTimeSec Number of seconds from now to be woken from sleep.
     */
    public void sendHibernationEntry(int wakeupTimeSec) {
        Slogf.i(CarLog.TAG_POWER, "send hibernation entry - wakeupTimeSec = %d",
                wakeupTimeSec);
        setPowerState(VehicleApPowerStateReport.HIBERNATION_ENTRY, wakeupTimeSec);
    }

    /**
     * Sends HibernationExit message to VHAL
     *
     * Notifies VHAL after SOC woke up from hibernation.
     */
    public void sendHibernationExit() {
        Slogf.i(CarLog.TAG_POWER, "send hibernation exit");
        setPowerState(VehicleApPowerStateReport.HIBERNATION_EXIT, 0);
    }

    /**
     * Send Shutdown Postpone message to VHAL
     */
    public void sendShutdownPostpone(int postponeTimeMs) {
        Slogf.i(CarLog.TAG_POWER, "send shutdown postpone, time:" + postponeTimeMs);
        setPowerState(VehicleApPowerStateReport.SHUTDOWN_POSTPONE, postponeTimeMs);
    }

    /**
     * Send Shutdown Start message to VHAL
     */
    public void sendShutdownStart(int wakeupTimeSec) {
        Slogf.i(CarLog.TAG_POWER, "send shutdown start");
        setPowerState(VehicleApPowerStateReport.SHUTDOWN_START, wakeupTimeSec);
    }

    /**
     * Send On message to VHAL
     */
    public void sendOn() {
        Slogf.i(CarLog.TAG_POWER, "send on");
        setPowerState(VehicleApPowerStateReport.ON, 0);
    }

    /**
     * Send Shutdown Prepare message to VHAL
     */
    public void sendShutdownPrepare() {
        Slogf.i(CarLog.TAG_POWER, "send shutdown prepare");
        setPowerState(VehicleApPowerStateReport.SHUTDOWN_PREPARE, 0);
    }

    /**
     * Send Shutdown Cancel message to VHAL
     */
    public void sendShutdownCancel() {
        Slogf.i(CarLog.TAG_POWER, "send shutdown cancel");
        setPowerState(VehicleApPowerStateReport.SHUTDOWN_CANCELLED, 0);
    }

    /**
     * Sets the display brightness for the vehicle.
     * @param brightness value from 0 to 100.
     */
    public void sendDisplayBrightness(int brightness) {
        if (brightness < 0) {
            brightness = 0;
        } else if (brightness > 100) {
            brightness = 100;
        }
        synchronized (mLock) {
            if (mProperties.get(DISPLAY_BRIGHTNESS) == null) {
                return;
            }
        }
        try {
            mHal.set(VehicleProperty.DISPLAY_BRIGHTNESS, 0).to(brightness);
            Slogf.i(CarLog.TAG_POWER, "send display brightness = " + brightness);
        } catch (ServiceSpecificException | IllegalArgumentException e) {
            Slogf.e(CarLog.TAG_POWER, "cannot set DISPLAY_BRIGHTNESS", e);
        }
    }

    private void setPowerState(int state, int additionalParam) {
        if (isPowerStateSupported()) {
            int[] values = { state, additionalParam };
            try {
                mHal.set(VehicleProperty.AP_POWER_STATE_REPORT, 0).to(values);
                Slogf.i(CarLog.TAG_POWER, "setPowerState=" + powerStateReportName(state)
                        + " param=" + additionalParam);
            } catch (ServiceSpecificException e) {
                Slogf.e(CarLog.TAG_POWER, "cannot set to AP_POWER_STATE_REPORT", e);
            }
        }
    }

    /**
     * Returns a {@link PowerState} representing the current power state for the vehicle.
     */
    @Nullable
    public PowerState getCurrentPowerState() {
        HalPropValue value;
        try {
            value = mHal.get(VehicleProperty.AP_POWER_STATE_REQ);
        } catch (ServiceSpecificException e) {
            Slogf.e(CarLog.TAG_POWER, "Cannot get AP_POWER_STATE_REQ", e);
            return null;
        }
        return new PowerState(value.getInt32Value(VehicleApPowerStateReqIndex.STATE),
                value.getInt32Value(VehicleApPowerStateReqIndex.ADDITIONAL));
    }

    /**
     * Determines if the current properties describe a valid power state
     * @return true if both the power state request and power state report are valid
     */
    public boolean isPowerStateSupported() {
        synchronized (mLock) {
            return (mProperties.get(VehicleProperty.AP_POWER_STATE_REQ) != null)
                    && (mProperties.get(VehicleProperty.AP_POWER_STATE_REPORT) != null);
        }
    }

    private boolean isConfigFlagSet(int flag) {
        HalPropConfig config;
        synchronized (mLock) {
            config = mProperties.get(VehicleProperty.AP_POWER_STATE_REQ);
        }
        if (config == null) {
            return false;
        }
        int[] configArray = config.getConfigArray();
        if (configArray.length < 1) {
            return false;
        }
        return (configArray[0] & flag) != 0;
    }

    public boolean isDeepSleepAllowed() {
        return isConfigFlagSet(VehicleApPowerStateConfigFlag.ENABLE_DEEP_SLEEP_FLAG);
    }

    public boolean isHibernationAllowed() {
        return isConfigFlagSet(VehicleApPowerStateConfigFlag.ENABLE_HIBERNATION_FLAG);
    }

    public boolean isTimedWakeupAllowed() {
        return isConfigFlagSet(VehicleApPowerStateConfigFlag.CONFIG_SUPPORT_TIMER_POWER_ON_FLAG);
    }

    @Override
    public void init() {
        synchronized (mLock) {
            for (HalPropConfig config : mProperties.values()) {
                if (VehicleHal.isPropertySubscribable(config)) {
                    mHal.subscribeProperty(this, config.getPropId());
                }
            }
            HalPropConfig brightnessProperty = mProperties.get(DISPLAY_BRIGHTNESS);
            if (brightnessProperty != null) {
                HalAreaConfig[] areaConfigs = brightnessProperty.getAreaConfigs();
                mMaxDisplayBrightness = areaConfigs.length > 0
                        ? areaConfigs[0].getMaxInt32Value() : 0;
                if (mMaxDisplayBrightness <= 0) {
                    Slogf.w(CarLog.TAG_POWER, "Max display brightness from vehicle HAL is invalid:"
                            + mMaxDisplayBrightness);
                    mMaxDisplayBrightness = 1;
                }
            }
        }
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mProperties.clear();
        }
    }

    @Override
    public int[] getAllSupportedProperties() {
        return SUPPORTED_PROPERTIES;
    }

    @Override
    public void takeProperties(Collection<HalPropConfig> properties) {
        if (properties.isEmpty()) {
            return;
        }
        synchronized (mLock) {
            for (HalPropConfig config : properties) {
                mProperties.put(config.getPropId(), config);
            }
        }
    }

    @Override
    public void onHalEvents(List<HalPropValue> values) {
        PowerEventListener listener;
        synchronized (mLock) {
            if (mListener == null) {
                if (mQueuedEvents == null) {
                    mQueuedEvents = new LinkedList<>();
                }
                mQueuedEvents.addAll(values);
                return;
            }
            listener = mListener;
        }
        dispatchEvents(values, listener);
    }

    private void dispatchEvents(List<HalPropValue> values, PowerEventListener listener) {
        for (HalPropValue v : values) {
            switch (v.getPropId()) {
                case AP_POWER_STATE_REPORT:
                    // Ignore this property event. It was generated inside of CarService.
                    break;
                case AP_POWER_STATE_REQ:
                    int state;
                    int param;
                    try {
                        state = v.getInt32Value(VehicleApPowerStateReqIndex.STATE);
                        param = v.getInt32Value(VehicleApPowerStateReqIndex.ADDITIONAL);
                    } catch (IndexOutOfBoundsException e) {
                        Slogf.e(CarLog.TAG_POWER, "Received invalid event, ignore, int32Values: "
                                + v.dumpInt32Values(), e);
                        break;
                    }
                    Slogf.i(CarLog.TAG_POWER, "Received AP_POWER_STATE_REQ="
                            + powerStateReqName(state) + " param=" + param);
                    listener.onApPowerStateChange(new PowerState(state, param));
                    break;
                case DISPLAY_BRIGHTNESS:
                {
                    int maxBrightness;
                    synchronized (mLock) {
                        maxBrightness = mMaxDisplayBrightness;
                    }
                    int brightness;
                    try {
                        brightness = v.getInt32Value(0) * MAX_BRIGHTNESS / maxBrightness;
                    } catch (IndexOutOfBoundsException e) {
                        Slogf.e(CarLog.TAG_POWER, "Received invalid event, ignore, int32Values: "
                                + v.dumpInt32Values(), e);
                        break;
                    }
                    if (brightness < 0) {
                        Slogf.e(CarLog.TAG_POWER, "invalid brightness: " + brightness
                                + ", set to 0");
                        brightness = 0;
                    } else if (brightness > MAX_BRIGHTNESS) {
                        Slogf.e(CarLog.TAG_POWER, "invalid brightness: " + brightness + ", set to "
                                + MAX_BRIGHTNESS);
                        brightness = MAX_BRIGHTNESS;
                    }
                    Slogf.i(CarLog.TAG_POWER, "Received DISPLAY_BRIGHTNESS=" + brightness);
                    listener.onDisplayBrightnessChange(brightness);
                    break;
                }
            }
        }
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(PrintWriter writer) {
        writer.println("*Power HAL*");
        writer.printf("isPowerStateSupported:%b, isDeepSleepAllowed:%b, isHibernationAllowed:%b\n",
                isPowerStateSupported(), isDeepSleepAllowed(), isHibernationAllowed());

    }
}
