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

package com.android.car;

import static android.car.CarOccupantZoneManager.DISPLAY_TYPE_MAIN;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_IDLING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_MOVING;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_PARKED;
import static android.car.drivingstate.CarDrivingStateEvent.DRIVING_STATE_UNKNOWN;
import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import android.annotation.FloatRange;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.car.Car;
import android.car.builtin.os.BinderHelper;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.view.DisplayHelper;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateEvent.CarDrivingState;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.car.drivingstate.ICarDrivingStateChangeListener;
import android.car.drivingstate.ICarUxRestrictionsChangeListener;
import android.car.drivingstate.ICarUxRestrictionsManager;
import android.car.hardware.CarPropertyValue;
import android.car.hardware.property.CarPropertyEvent;
import android.car.hardware.property.ICarPropertyEventListener;
import android.car.VehicleAreaType;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.automotive.vehicle.VehicleProperty;
import android.hardware.display.DisplayManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.util.ArraySet;
import android.util.AtomicFile;
import android.util.JsonReader;
import android.util.JsonToken;
import android.util.JsonWriter;
import android.util.SparseIntArray;
import android.view.Display;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.internal.util.IntArray;
import com.android.car.systeminterface.SystemInterface;
import com.android.car.util.TransitionLog;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * A service that listens to current driving state of the vehicle and maps it to the
 * appropriate UX restrictions for that driving state.
 * <p>
 * <h1>UX Restrictions Configuration</h1>
 * When this service starts, it will first try reading the configuration set through
 * {@link #saveUxRestrictionsConfigurationForNextBoot(List)}.
 * If one is not available, it will try reading the configuration saved in
 * {@code R.xml.car_ux_restrictions_map}. If XML is somehow unavailable, it will
 * fall back to a hard-coded configuration.
 * <p>
 * <h1>Multi-Display</h1>
 * Only physical displays that are available at service initialization are recognized.
 * This service does not support pluggable displays.
 */
public class CarUxRestrictionsManagerService extends ICarUxRestrictionsManager.Stub implements
        CarServiceBase {
    private static final String TAG = CarLog.tagFor(CarUxRestrictionsManagerService.class);
    private static final boolean DBG = false;
    private static final int MAX_TRANSITION_LOG_SIZE = 20;
    private static final int PROPERTY_UPDATE_RATE = 5; // Update rate in Hz

    private static final int UNKNOWN_JSON_SCHEMA_VERSION = -1;
    private static final int JSON_SCHEMA_VERSION_V1 = 1;
    private static final int JSON_SCHEMA_VERSION_V2 = 2;

    @IntDef({UNKNOWN_JSON_SCHEMA_VERSION, JSON_SCHEMA_VERSION_V1, JSON_SCHEMA_VERSION_V2})
    @Retention(RetentionPolicy.SOURCE)
    private @interface JsonSchemaVersion {}

    private static final String JSON_NAME_SCHEMA_VERSION = "schema_version";
    private static final String JSON_NAME_RESTRICTIONS = "restrictions";
    private static final int DEFAULT_PORT = 0;

    @VisibleForTesting
    static final String CONFIG_FILENAME_PRODUCTION = "ux_restrictions_prod_config.json";
    @VisibleForTesting
    static final String CONFIG_FILENAME_STAGED = "ux_restrictions_staged_config.json";

    private final Context mContext;
    private final DisplayManager mDisplayManager;
    private final CarDrivingStateService mDrivingStateService;
    private final CarPropertyService mCarPropertyService;
    private final CarOccupantZoneService mCarOccupantZoneService;
    private final HandlerThread mClientDispatchThread  = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    private final Handler mClientDispatchHandler  = new Handler(mClientDispatchThread.getLooper());
    private final RemoteCallbackList<ICarUxRestrictionsChangeListener> mUxRClients =
            new RemoteCallbackList<>();

    /**
     * Metadata associated with a binder callback.
     */
    private static class RemoteCallbackListCookie {
        final Integer mPhysicalPort;

        RemoteCallbackListCookie(Integer physicalPort) {
            mPhysicalPort = physicalPort;
        }
    }

    private final Object mLock = new Object();

    /**
     * This lookup caches the mapping from an int display id to an int that represents a physical
     * port.
     */
    @GuardedBy("mLock")
    private final SparseIntArray mPortLookup = new SparseIntArray();

    @GuardedBy("mLock")
    private Map<Integer, CarUxRestrictionsConfiguration> mCarUxRestrictionsConfigurations;

    @GuardedBy("mLock")
    private Map<Integer, CarUxRestrictions> mCurrentUxRestrictions;

    @GuardedBy("mLock")
    private String mRestrictionMode = UX_RESTRICTION_MODE_BASELINE;

    @GuardedBy("mLock")
    private float mCurrentMovingSpeed;

    // Represents a physical port for display.
    @GuardedBy("mLock")
    private int mDefaultDisplayPhysicalPort;

    @GuardedBy("mLock")
    private final List<Integer> mPhysicalPorts = new ArrayList<>();

    // Flag to disable broadcasting UXR changes - for development purposes
    @GuardedBy("mLock")
    private boolean mUxRChangeBroadcastEnabled = true;

    // For dumpsys logging
    @GuardedBy("mLock")
    private final LinkedList<TransitionLog> mTransitionLogs = new LinkedList<>();

    public CarUxRestrictionsManagerService(Context context, CarDrivingStateService drvService,
            CarPropertyService propertyService, CarOccupantZoneService carOccupantZoneService) {
        mContext = context;
        mDisplayManager = mContext.getSystemService(DisplayManager.class);
        mDrivingStateService = drvService;
        mCarPropertyService = propertyService;
        mCarOccupantZoneService = carOccupantZoneService;
    }

    @Override
    public void init() {
        synchronized (mLock) {
            initPhysicalPortLocked();

            // Unrestricted until driving state information is received. During boot up, we don't
            // want
            // everything to be blocked until data is available from CarPropertyManager.  If we
            // start
            // driving and we don't get speed or gear information, we have bigger problems.
            mCurrentUxRestrictions = new HashMap<>();
            for (int port : mPhysicalPorts) {
                mCurrentUxRestrictions.put(port, createUnrestrictedRestrictions());
            }

            // Load the prod config, or if there is a staged one, promote that first only if the
            // current driving state, as provided by the driving state service, is parked.
            mCarUxRestrictionsConfigurations = convertToMap(loadConfig());
        }

        // subscribe to driving state changes
        mDrivingStateService.registerDrivingStateChangeListener(
                mICarDrivingStateChangeEventListener);
        // subscribe to property service for speed
        mCarPropertyService.registerListener(VehicleProperty.PERF_VEHICLE_SPEED,
                PROPERTY_UPDATE_RATE, mICarPropertyEventListener);

        initializeUxRestrictions();
    }

    @Override
    public List<CarUxRestrictionsConfiguration> getConfigs() {
        CarServiceUtils.assertPermission(mContext,
                Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION);
        synchronized (mLock) {
            return new ArrayList<>(mCarUxRestrictionsConfigurations.values());
        }
    }

    /**
     * Loads UX restrictions configurations and returns them.
     *
     * <p>Reads config from the following sources in order:
     * <ol>
     * <li>saved config set by {@link #saveUxRestrictionsConfigurationForNextBoot(List)};
     * <li>XML resource config from {@code R.xml.car_ux_restrictions_map};
     * <li>hardcoded default config.
     * </ol>
     *
     * This method attempts to promote staged config file, which requires getting the current
     * driving state.
     */
    @VisibleForTesting
    List<CarUxRestrictionsConfiguration> loadConfig() {
        promoteStagedConfig();
        List<CarUxRestrictionsConfiguration> configs;

        // Production config, if available, is the first choice.
        File prodConfig = getFile(CONFIG_FILENAME_PRODUCTION);
        if (prodConfig.exists()) {
            logd("Attempting to read production config");
            configs = readPersistedConfig(prodConfig);
            if (configs != null) {
                return configs;
            }
        }

        // XML config is the second choice.
        logd("Attempting to read config from XML resource");
        configs = readXmlConfig();
        if (configs != null) {
            return configs;
        }

        // This should rarely happen.
        Slogf.w(TAG, "Creating default config");

        configs = new ArrayList<>();
        synchronized (mLock) {
            for (int port : mPhysicalPorts) {
                configs.add(createDefaultConfig(port));
            }
        }
        return configs;
    }

    private File getFile(String filename) {
        SystemInterface systemInterface = CarLocalServices.getService(SystemInterface.class);
        return new File(systemInterface.getSystemCarDir(), filename);
    }

    @Nullable
    private List<CarUxRestrictionsConfiguration> readXmlConfig() {
        try {
            return CarUxRestrictionsConfigurationXmlParser.parse(
                    mContext, R.xml.car_ux_restrictions_map);
        } catch (IOException | XmlPullParserException e) {
            Slogf.e(TAG, "Could not read config from XML resource", e);
        }
        return null;
    }

    /**
     * Promotes the staged config to prod, by replacing the prod file. Only do this if the car is
     * parked to avoid changing the restrictions during a drive.
     */
    private void promoteStagedConfig() {
        Path stagedConfig = getFile(CONFIG_FILENAME_STAGED).toPath();

        CarDrivingStateEvent currentDrivingStateEvent =
                mDrivingStateService.getCurrentDrivingState();
        // Only promote staged config when car is parked.
        if (currentDrivingStateEvent != null
                && currentDrivingStateEvent.eventValue == DRIVING_STATE_PARKED
                && Files.exists(stagedConfig)) {

            Path prod = getFile(CONFIG_FILENAME_PRODUCTION).toPath();
            try {
                logd("Attempting to promote stage config");
                Files.move(stagedConfig, prod, REPLACE_EXISTING);
            } catch (IOException e) {
                Slogf.e(TAG, "Could not promote state config", e);
            }
        }
    }

    // Update current restrictions by getting the current driving state and speed.
    private void initializeUxRestrictions() {
        CarDrivingStateEvent currentDrivingStateEvent =
                mDrivingStateService.getCurrentDrivingState();
        // if we don't have enough information from the CarPropertyService to compute the UX
        // restrictions, then leave the UX restrictions unchanged from what it was initialized to
        // in the constructor.
        if (currentDrivingStateEvent == null
                || currentDrivingStateEvent.eventValue == DRIVING_STATE_UNKNOWN) {
            return;
        }

        // At this point the underlying CarPropertyService has provided us enough information to
        // compute the UX restrictions that could be potentially different from the initial UX
        // restrictions.
        synchronized (mLock) {
            handleDrivingStateEventLocked(currentDrivingStateEvent);
        }
    }

    private @FloatRange(from = 0f) Optional<Float> getCurrentSpeed() {
        CarPropertyValue value = mCarPropertyService.getPropertySafe(
                VehicleProperty.PERF_VEHICLE_SPEED, VehicleAreaType.VEHICLE_AREA_TYPE_GLOBAL);
        if (value != null && value.getStatus() == CarPropertyValue.STATUS_AVAILABLE) {
            return Optional.of(Math.abs((Float) value.getValue()));
        }
        return Optional.empty();
    }

    @Override
    public void release() {
        while (mUxRClients.getRegisteredCallbackCount() > 0) {
            for (int i = mUxRClients.getRegisteredCallbackCount() - 1; i >= 0; i--) {
                ICarUxRestrictionsChangeListener client = mUxRClients.getRegisteredCallbackItem(i);
                if (client == null) {
                    continue;
                }
                mUxRClients.unregister(client);
            }
        }
        mDrivingStateService.unregisterDrivingStateChangeListener(
                mICarDrivingStateChangeEventListener);
    }

    // Binder methods

    /**
     * Registers a {@link ICarUxRestrictionsChangeListener} to be notified for changes to the UX
     * restrictions.
     *
     * @param listener  Listener to register
     * @param displayId UX restrictions on this display will be notified.
     */
    @Override
    public void registerUxRestrictionsChangeListener(
            ICarUxRestrictionsChangeListener listener, int displayId) {
        if (listener == null) {
            Slogf.e(TAG, "registerUxRestrictionsChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }
        int physicalPort;
        synchronized (mLock) {
            physicalPort = getPhysicalPortLocked(displayId);
            if (physicalPort == DisplayHelper.INVALID_PORT) {
                Slogf.e(TAG, "Invalid displayId=" + displayId);
                return;
            }
        }
        mUxRClients.register(listener, new RemoteCallbackListCookie(physicalPort));
    }

    /**
     * Unregister the given UX Restrictions listener
     *
     * @param listener client to unregister
     */
    @Override
    public void unregisterUxRestrictionsChangeListener(ICarUxRestrictionsChangeListener listener) {
        if (listener == null) {
            Slogf.e(TAG, "unregisterUxRestrictionsChangeListener(): listener null");
            throw new IllegalArgumentException("Listener is null");
        }

        mUxRClients.unregister(listener);
    }

    /**
     * Gets the current UX restrictions for a display.
     *
     * @param displayId UX restrictions on this display will be returned.
     */
    @Override
    public CarUxRestrictions getCurrentUxRestrictions(int displayId) {
        CarUxRestrictions restrictions;
        synchronized (mLock) {
            if (mCurrentUxRestrictions == null) {
                Slogf.wtf(TAG, "getCurrentUxRestrictions() called before init()");
                return null;
            }
            restrictions = mCurrentUxRestrictions.get(getPhysicalPortLocked(displayId));
        }
        if (restrictions == null) {
            Slogf.e(TAG, "Restrictions are null for displayId:" + displayId
                    + ". Returning full restrictions.");
            restrictions = createFullyRestrictedRestrictions();
        }
        return restrictions;
    }

    /**
     * Convenience method to retrieve restrictions for default display.
     */
    @Nullable
    public CarUxRestrictions getCurrentUxRestrictions() {
        return getCurrentUxRestrictions(Display.DEFAULT_DISPLAY);
    }

    @Override
    public boolean saveUxRestrictionsConfigurationForNextBoot(
            List<CarUxRestrictionsConfiguration> configs) {
        CarServiceUtils.assertPermission(mContext,
                Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION);

        validateConfigs(configs);

        return persistConfig(configs, CONFIG_FILENAME_STAGED);
    }

    @Override
    @Nullable
    public List<CarUxRestrictionsConfiguration> getStagedConfigs() {
        CarServiceUtils.assertPermission(mContext,
                Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION);

        File stagedConfig = getFile(CONFIG_FILENAME_STAGED);
        if (stagedConfig.exists()) {
            logd("Attempting to read staged config");
            return readPersistedConfig(stagedConfig);
        } else {
            return null;
        }
    }

    /**
     * Sets the restriction mode to use. Restriction mode allows a different set of restrictions to
     * be applied in the same driving state. Restrictions for each mode can be configured through
     * {@link CarUxRestrictionsConfiguration}.
     *
     * <p>Defaults to {@link CarUxRestrictionsManager#UX_RESTRICTION_MODE_BASELINE}.
     *
     * @param mode the restriction mode
     * @return {@code true} if mode was successfully changed; {@code false} otherwise.
     * @see CarUxRestrictionsConfiguration.DrivingStateRestrictions
     * @see CarUxRestrictionsConfiguration.Builder
     */
    @Override
    public boolean setRestrictionMode(@NonNull String mode) {
        CarServiceUtils.assertPermission(mContext,
                Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION);
        Objects.requireNonNull(mode, "mode must not be null");

        synchronized (mLock) {
            if (mRestrictionMode.equals(mode)) {
                return true;
            }

            addTransitionLogLocked(TAG, mRestrictionMode, mode, System.currentTimeMillis(),
                    "Restriction mode");
            mRestrictionMode = mode;
            logd("Set restriction mode to: " + mode);

            handleDrivingStateEventLocked(mDrivingStateService.getCurrentDrivingState());
        }
        return true;
    }

    @Override
    @NonNull
    public String getRestrictionMode() {
        CarServiceUtils.assertPermission(mContext,
                Car.PERMISSION_CAR_UX_RESTRICTIONS_CONFIGURATION);

        synchronized (mLock) {
            return mRestrictionMode;
        }
    }

    /**
     * Writes configuration into the specified file.
     *
     * IO access on file is not thread safe. Caller should ensure threading protection.
     */
    private boolean persistConfig(List<CarUxRestrictionsConfiguration> configs, String filename) {
        File file = getFile(filename);
        AtomicFile stagedFile = new AtomicFile(file);
        FileOutputStream fos;
        try {
            fos = stagedFile.startWrite();
        } catch (IOException e) {
            Slogf.e(TAG, "Could not open file to persist config", e);
            return false;
        }
        try (JsonWriter jsonWriter = new JsonWriter(
                new OutputStreamWriter(fos, StandardCharsets.UTF_8))) {
            writeJson(jsonWriter, configs);
        } catch (IOException e) {
            Slogf.e(TAG, "Could not persist config", e);
            stagedFile.failWrite(fos);
            return false;
        }
        stagedFile.finishWrite(fos);
        return true;
    }

    @VisibleForTesting
    void writeJson(JsonWriter jsonWriter, List<CarUxRestrictionsConfiguration> configs)
            throws IOException {
        jsonWriter.beginObject();
        jsonWriter.name(JSON_NAME_SCHEMA_VERSION).value(JSON_SCHEMA_VERSION_V2);
        jsonWriter.name(JSON_NAME_RESTRICTIONS);
        jsonWriter.beginArray();
        for (CarUxRestrictionsConfiguration config : configs) {
            config.writeJson(jsonWriter);
        }
        jsonWriter.endArray();
        jsonWriter.endObject();
    }

    @Nullable
    private List<CarUxRestrictionsConfiguration> readPersistedConfig(File file) {
        if (!file.exists()) {
            Slogf.e(TAG, "Could not find config file: " + file.getName());
            return null;
        }

        // Take one pass at the file to check the version and then a second pass to read the
        // contents. We could assess the version and read in one pass, but we're preferring
        // clarity over complexity here.
        int schemaVersion = readFileSchemaVersion(file);

        AtomicFile configFile = new AtomicFile(file);
        try (JsonReader reader = new JsonReader(
                new InputStreamReader(configFile.openRead(), StandardCharsets.UTF_8))) {
            List<CarUxRestrictionsConfiguration> configs = new ArrayList<>();
            switch (schemaVersion) {
                case JSON_SCHEMA_VERSION_V1:
                    readV1Json(reader, configs);
                    break;
                case JSON_SCHEMA_VERSION_V2:
                    readV2Json(reader, configs);
                    break;
                default:
                    Slogf.e(TAG, "Unable to parse schema for version " + schemaVersion);
            }

            return configs;
        } catch (IOException e) {
            Slogf.e(TAG, "Could not read persisted config file " + file.getName(), e);
        }
        return null;
    }

    private void readV1Json(JsonReader reader,
            List<CarUxRestrictionsConfiguration> configs) throws IOException {
        readRestrictionsArray(reader, configs, JSON_SCHEMA_VERSION_V1);
    }

    private void readV2Json(JsonReader reader,
            List<CarUxRestrictionsConfiguration> configs) throws IOException {
        reader.beginObject();
        while (reader.hasNext()) {
            String name = reader.nextName();
            switch (name) {
                case JSON_NAME_RESTRICTIONS:
                    readRestrictionsArray(reader, configs, JSON_SCHEMA_VERSION_V2);
                    break;
                default:
                    reader.skipValue();
            }
        }
        reader.endObject();
    }

    private int readFileSchemaVersion(File file) {
        AtomicFile configFile = new AtomicFile(file);
        try (JsonReader reader = new JsonReader(
                new InputStreamReader(configFile.openRead(), StandardCharsets.UTF_8))) {
            List<CarUxRestrictionsConfiguration> configs = new ArrayList<>();
            if (reader.peek() == JsonToken.BEGIN_ARRAY) {
                // only schema V1 beings with an array - no need to keep reading
                reader.close();
                return JSON_SCHEMA_VERSION_V1;
            } else {
                reader.beginObject();
                while (reader.hasNext()) {
                    String name = reader.nextName();
                    switch (name) {
                        case JSON_NAME_SCHEMA_VERSION:
                            int schemaVersion = reader.nextInt();
                            // got the version, no need to continue reading
                            reader.close();
                            return schemaVersion;
                        default:
                            reader.skipValue();
                    }
                }
                reader.endObject();
            }
        } catch (IOException e) {
            Slogf.e(TAG, "Could not read persisted config file " + file.getName(), e);
        }
        return UNKNOWN_JSON_SCHEMA_VERSION;
    }

    private void readRestrictionsArray(JsonReader reader,
            List<CarUxRestrictionsConfiguration> configs, @JsonSchemaVersion int schemaVersion)
            throws IOException {
        reader.beginArray();
        while (reader.hasNext()) {
            configs.add(CarUxRestrictionsConfiguration.readJson(reader, schemaVersion));
        }
        reader.endArray();
    }

    /**
     * Enable/disable UX restrictions change broadcast blocking.
     * Setting this to true will stop broadcasts of UX restriction change to listeners.
     * This method works only on debug builds and the caller of this method needs to have the same
     * signature of the car service.
     */
    public void setUxRChangeBroadcastEnabled(boolean enable) {
        if (!isDebugBuild()) {
            Slogf.e(TAG, "Cannot set UX restriction change broadcast.");
            return;
        }
        // Check if the caller has the same signature as that of the car service.
        if (mContext.getPackageManager().checkSignatures(Process.myUid(), Binder.getCallingUid())
                != PackageManager.SIGNATURE_MATCH) {
            throw new SecurityException(
                    "Caller " + mContext.getPackageManager().getNameForUid(Binder.getCallingUid())
                            + " does not have the right signature");
        }

        synchronized (mLock) {
            if (enable) {
                // if enabling it back, send the current restrictions
                mUxRChangeBroadcastEnabled = true;
                handleDrivingStateEventLocked(
                        mDrivingStateService.getCurrentDrivingState());
            } else {
                // fake parked state, so if the system is currently restricted, the restrictions are
                // relaxed.
                handleDispatchUxRestrictionsLocked(DRIVING_STATE_PARKED, /* speed= */ 0f);
                mUxRChangeBroadcastEnabled = false;
            }
        }
    }

    private boolean isDebugBuild() {
        return BuildHelper.isUserDebugBuild() || BuildHelper.isEngBuild();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        synchronized (mLock) {
            writer.println("*CarUxRestrictionsManagerService*");

            writer.println("UX Restrictions Clients:");
            writer.increaseIndent();
            BinderHelper.dumpRemoteCallbackList(mUxRClients, writer);
            writer.decreaseIndent();

            for (int port : mCurrentUxRestrictions.keySet()) {
                CarUxRestrictions restrictions = mCurrentUxRestrictions.get(port);
                writer.printf("Port: 0x%02X UXR: %s\n", port, restrictions.toString());
            }
            if (isDebugBuild()) {
                writer.println("mUxRChangeBroadcastEnabled? " + mUxRChangeBroadcastEnabled);
            }
            writer.println("UX Restriction configurations:");
            for (CarUxRestrictionsConfiguration config :
                    mCarUxRestrictionsConfigurations.values()) {
                config.dump(writer);
            }
            writer.println("UX Restriction change log:");
            for (TransitionLog tlog : mTransitionLogs) {
                writer.println(tlog);
            }
        }
    }

    /**
     * {@link CarDrivingStateEvent} listener registered with the {@link CarDrivingStateService}
     * for getting driving state change notifications.
     */
    private final ICarDrivingStateChangeListener mICarDrivingStateChangeEventListener =
            new ICarDrivingStateChangeListener.Stub() {
                @Override
                public void onDrivingStateChanged(CarDrivingStateEvent event) {
                    logd("Driving State Changed:" + event.eventValue);
                    synchronized (mLock) {
                        handleDrivingStateEventLocked(event);
                    }
                }
            };

    /**
     * Handle the driving state change events coming from the {@link CarDrivingStateService}.
     * Map the driving state to the corresponding UX Restrictions and dispatch the
     * UX Restriction change to the registered clients.
     */
    @VisibleForTesting
    @GuardedBy("mLock")
    void handleDrivingStateEventLocked(CarDrivingStateEvent event) {
        if (event == null) {
            return;
        }
        int drivingState = event.eventValue;
        Optional<Float> currentSpeed = getCurrentSpeed();

        if (currentSpeed.isPresent()) {
            mCurrentMovingSpeed = currentSpeed.get();
            handleDispatchUxRestrictionsLocked(drivingState, mCurrentMovingSpeed);
        } else if (drivingState != DRIVING_STATE_MOVING) {
            // If speed is unavailable, but the driving state is parked or unknown, it can still be
            // handled.
            logd("Speed null when driving state is: " + drivingState);
            handleDispatchUxRestrictionsLocked(drivingState, /* speed= */ 0f);
        } else {
            // If we get here with driving state != parked or unknown && speed == null,
            // something is wrong.  CarDrivingStateService could not have inferred idling or moving
            // when speed is not available
            Slogf.e(TAG, "Unexpected:  Speed null when driving state is: " + drivingState);
            return;
        }
    }

    /**
     * {@link CarPropertyEvent} listener registered with the {@link CarPropertyService} for getting
     * speed change notifications.
     */
    private final ICarPropertyEventListener mICarPropertyEventListener =
            new ICarPropertyEventListener.Stub() {
                @Override
                public void onEvent(List<CarPropertyEvent> events) throws RemoteException {
                    synchronized (mLock) {
                        for (CarPropertyEvent event : events) {
                            if ((event.getEventType()
                                    == CarPropertyEvent.PROPERTY_EVENT_PROPERTY_CHANGE)
                                    && (event.getCarPropertyValue().getPropertyId()
                                    == VehicleProperty.PERF_VEHICLE_SPEED)) {
                                handleSpeedChangeLocked(
                                        Math.abs((Float) event.getCarPropertyValue().getValue()));
                            }
                        }
                    }
                }
            };

    @GuardedBy("mLock")
    private void handleSpeedChangeLocked(@FloatRange(from = 0f) float newSpeed) {
        if (newSpeed == mCurrentMovingSpeed) {
            // Ignore if speed hasn't changed
            return;
        }
        mCurrentMovingSpeed = newSpeed;
        int currentDrivingState = mDrivingStateService.getCurrentDrivingState().eventValue;
        if (currentDrivingState != DRIVING_STATE_MOVING) {
            // Ignore speed changes if the vehicle is not moving
            return;
        }
        handleDispatchUxRestrictionsLocked(currentDrivingState, mCurrentMovingSpeed);
    }

    /**
     * Handle dispatching UX restrictions change.
     *
     * @param currentDrivingState driving state of the vehicle
     * @param speed               speed of the vehicle
     */
    @GuardedBy("mLock")
    private void handleDispatchUxRestrictionsLocked(@CarDrivingState int currentDrivingState,
            @FloatRange(from = 0f) float speed) {
        Objects.requireNonNull(mCarUxRestrictionsConfigurations,
                "mCarUxRestrictionsConfigurations must be initialized");
        Objects.requireNonNull(mCurrentUxRestrictions,
                "mCurrentUxRestrictions must be initialized");

        if (isDebugBuild() && !mUxRChangeBroadcastEnabled) {
            Slogf.d(TAG, "Not dispatching UX Restriction due to setting");
            return;
        }

        Map<Integer, CarUxRestrictions> newUxRestrictions = new HashMap<>();
        for (int port : mPhysicalPorts) {
            CarUxRestrictionsConfiguration config = mCarUxRestrictionsConfigurations.get(port);
            if (config == null) {
                continue;
            }

            CarUxRestrictions uxRestrictions = config.getUxRestrictions(
                    currentDrivingState, speed, mRestrictionMode);
            logd(String.format("Display port 0x%02x\tDO old->new: %b -> %b",
                    port,
                    mCurrentUxRestrictions.get(port).isRequiresDistractionOptimization(),
                    uxRestrictions.isRequiresDistractionOptimization()));
            logd(String.format("Display port 0x%02x\tUxR old->new: 0x%x -> 0x%x",
                    port,
                    mCurrentUxRestrictions.get(port).getActiveRestrictions(),
                    uxRestrictions.getActiveRestrictions()));
            newUxRestrictions.put(port, uxRestrictions);
        }

        // Ignore dispatching if the restrictions has not changed.
        Set<Integer> displayToDispatch = new ArraySet<>();
        for (int port : newUxRestrictions.keySet()) {
            if (!mCurrentUxRestrictions.containsKey(port)) {
                // This should never happen.
                Slogf.wtf(TAG, "Unrecognized port:" + port);
                continue;
            }
            CarUxRestrictions uxRestrictions = newUxRestrictions.get(port);
            if (!mCurrentUxRestrictions.get(port).isSameRestrictions(uxRestrictions)) {
                displayToDispatch.add(port);
            }
        }
        if (displayToDispatch.isEmpty()) {
            return;
        }

        for (int port : displayToDispatch) {
            addTransitionLogLocked(
                    mCurrentUxRestrictions.get(port), newUxRestrictions.get(port));
        }

        dispatchRestrictionsToClients(newUxRestrictions, displayToDispatch);

        mCurrentUxRestrictions = newUxRestrictions;
    }

    private void dispatchRestrictionsToClients(Map<Integer, CarUxRestrictions> displayRestrictions,
            Set<Integer> displayToDispatch) {
        logd("dispatching to clients");
        boolean success = mClientDispatchHandler.post(() -> {
            int numClients = mUxRClients.beginBroadcast();
            for (int i = 0; i < numClients; i++) {
                ICarUxRestrictionsChangeListener callback = mUxRClients.getBroadcastItem(i);
                RemoteCallbackListCookie cookie =
                        (RemoteCallbackListCookie) mUxRClients.getBroadcastCookie(i);
                if (!displayToDispatch.contains(cookie.mPhysicalPort)) {
                    continue;
                }
                CarUxRestrictions restrictions = displayRestrictions.get(cookie.mPhysicalPort);
                if (restrictions == null) {
                    // don't dispatch to displays without configurations
                    continue;
                }
                try {
                    callback.onUxRestrictionsChanged(restrictions);
                } catch (RemoteException e) {
                    Slogf.e(TAG, "Dispatch to listener %s failed for restrictions (%s)", callback,
                            restrictions);
                }
            }
            mUxRClients.finishBroadcast();
        });

        if (!success) {
            Slogf.e(TAG, "Unable to post (" + displayRestrictions + ") event to dispatch handler");
        }
    }

    @GuardedBy("mLock")
    private void initPhysicalPortLocked() {
        IntArray displayIds = mCarOccupantZoneService.getAllDisplayIdsForDriver(DISPLAY_TYPE_MAIN);
        Slogf.d(TAG, "displayIds: " + Arrays.toString(displayIds.toArray()));

        for (int i = 0; i < displayIds.size(); ++i) {
            int port = getPhysicalPortLocked(displayIds.get(i));
            if (i == 0) {
                // The first port will be the default port.
                mDefaultDisplayPhysicalPort = port;
            }
            mPhysicalPorts.add(port);
        }
    }

    private Map<Integer, CarUxRestrictionsConfiguration> convertToMap(
            List<CarUxRestrictionsConfiguration> configs) {
        validateConfigs(configs);

        Map<Integer, CarUxRestrictionsConfiguration> result = new HashMap<>();
        if (configs.size() == 1) {
            CarUxRestrictionsConfiguration config = configs.get(0);
            synchronized (mLock) {
                int port = config.getPhysicalPort() == null
                        ? mDefaultDisplayPhysicalPort
                        : config.getPhysicalPort();
                result.put(port, config);
            }
        } else {
            for (CarUxRestrictionsConfiguration config : configs) {
                result.put(config.getPhysicalPort(), config);
            }
        }
        return result;
    }

    /**
     * Validates configs for multi-display:
     * - share the same restrictions parameters;
     * - each sets display port;
     * - each has unique display port.
     */
    @VisibleForTesting
    void validateConfigs(List<CarUxRestrictionsConfiguration> configs) {
        if (configs.size() == 0) {
            throw new IllegalArgumentException("Empty configuration.");
        }

        if (configs.size() == 1) {
            return;
        }

        CarUxRestrictionsConfiguration first = configs.get(0);
        Set<Integer> existingPorts = new ArraySet<>();
        for (CarUxRestrictionsConfiguration config : configs) {
            if (!config.hasSameParameters(first)) {
                // Input should have the same restriction parameters because:
                // - it doesn't make sense otherwise; and
                // - in format it matches how xml can only specify one set of parameters.
                throw new IllegalArgumentException(
                        "Configurations should have the same restrictions parameters.");
            }

            Integer port = config.getPhysicalPort();
            if (port == null) {
                // Size was checked above; safe to assume there are multiple configs.
                throw new IllegalArgumentException(
                        "Input contains multiple configurations; each must set physical port.");
            }
            if (existingPorts.contains(port)) {
                throw new IllegalArgumentException("Multiple configurations for port " + port);
            }

            existingPorts.add(port);
        }
    }

    /**
     * Returns the physical port id for the display or {@code DisplayHelper.INVALID_PORT} if {@link
     * DisplayManager#getDisplay(int)} is not aware of the provided id.
     */
    @Nullable
    @GuardedBy("mLock")
    private int getPhysicalPortLocked(int displayId) {
        int index = mPortLookup.indexOfKey(displayId);
        if (index < 0) {
            Display display = mDisplayManager.getDisplay(displayId);
            if (display == null) {
                mPortLookup.delete(displayId);
                Slogf.w(TAG, "Could not retrieve display for id: " + displayId);
                return DisplayHelper.INVALID_PORT;
            }
            int port = DisplayHelper.getPhysicalPort(display);
            if (port != DisplayHelper.INVALID_PORT) {
                mPortLookup.put(displayId, port);
                return port;
            }
        }
        return mPortLookup.valueAt(index);
    }

    private CarUxRestrictions createUnrestrictedRestrictions() {
        return new CarUxRestrictions.Builder(/* reqOpt= */ false,
                CarUxRestrictions.UX_RESTRICTIONS_BASELINE, SystemClock.elapsedRealtimeNanos())
                .build();
    }

    private CarUxRestrictions createFullyRestrictedRestrictions() {
        return new CarUxRestrictions.Builder(
                /*reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED,
                SystemClock.elapsedRealtimeNanos()).build();
    }

    CarUxRestrictionsConfiguration createDefaultConfig(int port) {
        return new CarUxRestrictionsConfiguration.Builder()
                .setPhysicalPort(port)
                .setUxRestrictions(DRIVING_STATE_PARKED,
                        false, CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
                .setUxRestrictions(DRIVING_STATE_IDLING,
                        false, CarUxRestrictions.UX_RESTRICTIONS_BASELINE)
                .setUxRestrictions(DRIVING_STATE_MOVING,
                        true, CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED)
                .setUxRestrictions(DRIVING_STATE_UNKNOWN,
                        true, CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED)
                .build();
    }

    @GuardedBy("mLock")
    private void addTransitionLogLocked(String name, String from, String to, long timestamp,
            String extra) {
        if (mTransitionLogs.size() >= MAX_TRANSITION_LOG_SIZE) {
            mTransitionLogs.remove();
        }

        TransitionLog tLog = new TransitionLog(name, from, to, timestamp, extra);
        mTransitionLogs.add(tLog);
    }

    @GuardedBy("mLock")
    private void addTransitionLogLocked(
            CarUxRestrictions oldRestrictions, CarUxRestrictions newRestrictions) {
        if (mTransitionLogs.size() >= MAX_TRANSITION_LOG_SIZE) {
            mTransitionLogs.remove();
        }
        StringBuilder extra = new StringBuilder();
        extra.append(oldRestrictions.isRequiresDistractionOptimization() ? "DO -> " : "No DO -> ");
        extra.append(newRestrictions.isRequiresDistractionOptimization() ? "DO" : "No DO");

        TransitionLog tLog = new TransitionLog(TAG,
                oldRestrictions.getActiveRestrictions(), newRestrictions.getActiveRestrictions(),
                System.currentTimeMillis(), extra.toString());
        mTransitionLogs.add(tLog);
    }

    private static void logd(String msg) {
        if (DBG) {
            Slogf.d(TAG, msg);
        }
    }
}
