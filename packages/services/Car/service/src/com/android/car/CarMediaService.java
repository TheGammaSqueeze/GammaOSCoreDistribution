/*
 * Copyright (C) 2019 The Android Open Source Project
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

import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_BROWSE;
import static android.car.media.CarMediaManager.MEDIA_SOURCE_MODE_PLAYBACK;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_SWITCHING;
import static android.car.user.CarUserManager.USER_LIFECYCLE_EVENT_TYPE_UNLOCKED;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;
import static com.android.car.util.Utils.isEventAnyOfTypes;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.TestApi;
import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimeUtils;
import android.car.hardware.power.CarPowerPolicy;
import android.car.hardware.power.CarPowerPolicyFilter;
import android.car.hardware.power.ICarPowerPolicyListener;
import android.car.hardware.power.PowerComponent;
import android.car.media.CarMediaManager;
import android.car.media.CarMediaManager.MediaSourceMode;
import android.car.media.ICarMedia;
import android.car.media.ICarMediaSourceListener;
import android.car.user.CarUserManager.UserLifecycleListener;
import android.car.user.UserLifecycleEventFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.session.MediaController;
import android.media.session.MediaController.TransportControls;
import android.media.session.MediaSession;
import android.media.session.MediaSession.Token;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.os.HandlerExecutor;
import com.android.car.internal.util.DebugUtils;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.user.CarUserService;
import com.android.car.user.UserHandleHelper;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

/**
 * CarMediaService manages the currently active media source for car apps. This is different from
 * the MediaSessionManager's active sessions, as there can only be one active source in the car,
 * through both browse and playback.
 *
 * In the car, the active media source does not necessarily have an active MediaSession, e.g. if
 * it were being browsed only. However, that source is still considered the active source, and
 * should be the source displayed in any Media related UIs (Media Center, home screen, etc).
 */
public final class CarMediaService extends ICarMedia.Stub implements CarServiceBase {

    private static final boolean DEBUG = false;

    private static final String SOURCE_KEY = "media_source_component";
    private static final String SOURCE_KEY_SEPARATOR = "_";
    private static final String PLAYBACK_STATE_KEY = "playback_state";
    private static final String SHARED_PREF = "com.android.car.media.car_media_service";
    private static final String COMPONENT_NAME_SEPARATOR = ",";
    private static final String MEDIA_CONNECTION_ACTION = "com.android.car.media.MEDIA_CONNECTION";
    private static final String EXTRA_AUTOPLAY = "com.android.car.media.autoplay";
    private static final String LAST_UPDATE_KEY = "last_update";

    private static final int MEDIA_SOURCE_MODES = 2;

    // XML configuration options for autoplay on media source change.
    private static final int AUTOPLAY_CONFIG_NEVER = 0;
    private static final int AUTOPLAY_CONFIG_ALWAYS = 1;
    // This mode uses the current source's last stored playback state to resume playback
    private static final int AUTOPLAY_CONFIG_RETAIN_PER_SOURCE = 2;
    // This mode uses the previous source's playback state to resume playback
    private static final int AUTOPLAY_CONFIG_RETAIN_PREVIOUS = 3;

    private final Context mContext;
    private final CarUserService mUserService;
    private final UserManager mUserManager;
    private final MediaSessionManager mMediaSessionManager;
    private final MediaSessionUpdater mMediaSessionUpdater = new MediaSessionUpdater();
    @GuardedBy("mLock")
    private ComponentName[] mPrimaryMediaComponents = new ComponentName[MEDIA_SOURCE_MODES];
    // MediaController for the current active user's active media session. This controller can be
    // null if playback has not been started yet.
    @GuardedBy("mLock")
    private MediaController mActiveUserMediaController;
    @GuardedBy("mLock")
    private int mCurrentPlaybackState;
    @GuardedBy("mLock")
    private boolean mIsDisabledByPowerPolicy;
    @GuardedBy("mLock")
    private boolean mWasPreviouslyDisabledByPowerPolicy;
    @GuardedBy("mLock")
    private boolean mWasPlayingBeforeDisabled;

    // NOTE: must use getSharedPrefsForWriting() to write to it
    private SharedPreferences mSharedPrefs;
    private SessionChangedListener mSessionsListener;
    private int mPlayOnMediaSourceChangedConfig;
    private int mPlayOnBootConfig;
    private boolean mIndependentPlaybackConfig;

    private boolean mPendingInit;

    private final RemoteCallbackList<ICarMediaSourceListener>[] mMediaSourceListeners =
            new RemoteCallbackList[MEDIA_SOURCE_MODES];

    private final Handler mCommonThreadHandler = new Handler(
            CarServiceUtils.getCommonHandlerThread().getLooper());

    private final HandlerThread mHandlerThread  = CarServiceUtils.getHandlerThread(
            getClass().getSimpleName());
    // Handler to receive PlaybackState callbacks from the active media controller.
    private final Handler mHandler = new Handler(mHandlerThread.getLooper());
    private final Object mLock = new Object();

    /** The component name of the last media source that was removed while being primary. */
    private ComponentName[] mRemovedMediaSourceComponents = new ComponentName[MEDIA_SOURCE_MODES];

    private final IntentFilter mPackageUpdateFilter;
    @GuardedBy("mLock")
    @Nullable
    private Context mUserContext;

    /**
     * Listens to {@link Intent#ACTION_PACKAGE_REMOVED}, so we can fall back to a previously used
     * media source when the active source is uninstalled.
     */
    private final BroadcastReceiver mPackageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getData() == null) {
                return;
            }
            String intentPackage = intent.getData().getSchemeSpecificPart();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                synchronized (mLock) {
                    for (int i = 0; i < MEDIA_SOURCE_MODES; i++) {
                        if (mPrimaryMediaComponents[i] != null
                                && mPrimaryMediaComponents[i].getPackageName().equals(
                                intentPackage)) {
                            if (intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                                // If package is being replaced, it may not be removed from
                                // PackageManager queries  when we check for available
                                // MediaBrowseServices, so we iterate to find the next available
                                // source.
                                for (ComponentName component : getLastMediaSources(i)) {
                                    if (!mPrimaryMediaComponents[i].getPackageName()
                                            .equals(component.getPackageName())) {
                                        mRemovedMediaSourceComponents[i] =
                                                mPrimaryMediaComponents[i];
                                        if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                                            Slogf.d(CarLog.TAG_MEDIA,
                                                    "temporarily replacing updated media source "
                                                            + mPrimaryMediaComponents[i]
                                                            + "with backup source: "
                                                            + component);
                                        }
                                        setPrimaryMediaSource(component, i);
                                        return;
                                    }
                                }
                                Slogf.e(CarLog.TAG_MEDIA, "No available backup media source");
                            } else {
                                if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                                    Slogf.d(CarLog.TAG_MEDIA, "replacing removed media source "
                                            + mPrimaryMediaComponents[i] + "with backup source: "
                                            + getLastMediaSource(i));
                                }
                                mRemovedMediaSourceComponents[i] = null;
                                setPrimaryMediaSource(getLastMediaSource(i), i);
                            }
                        }
                    }
                }
            } else if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())
                    || Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                for (int i = 0; i < MEDIA_SOURCE_MODES; i++) {
                    if (mRemovedMediaSourceComponents[i] != null && mRemovedMediaSourceComponents[i]
                            .getPackageName().equals(intentPackage)) {
                        if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                            Slogf.d(CarLog.TAG_MEDIA, "restoring removed source: "
                                    + mRemovedMediaSourceComponents[i]);
                        }
                        setPrimaryMediaSource(mRemovedMediaSourceComponents[i], i);
                    }
                }
            }
        }
    };

    private final UserLifecycleListener mUserLifecycleListener = event -> {
        if (!isEventAnyOfTypes(CarLog.TAG_MEDIA, event,
                USER_LIFECYCLE_EVENT_TYPE_SWITCHING, USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)) {
            return;
        }
        if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
            Slogf.d(CarLog.TAG_MEDIA, "CarMediaService.onEvent(" + event + ")");
        }

        switch (event.getEventType()) {
            case USER_LIFECYCLE_EVENT_TYPE_SWITCHING:
                maybeInitUser(event.getUserId());
                break;
            case USER_LIFECYCLE_EVENT_TYPE_UNLOCKED:
                onUserUnlocked(event.getUserId());
                break;
        }
    };

    private final ICarPowerPolicyListener mPowerPolicyListener =
            new ICarPowerPolicyListener.Stub() {
                @Override
                public void onPolicyChanged(CarPowerPolicy appliedPolicy,
                        CarPowerPolicy accumulatedPolicy) {
                    boolean shouldBePlaying;
                    MediaController mediaController;
                    boolean isOff = !accumulatedPolicy.isComponentEnabled(PowerComponent.MEDIA);
                    synchronized (mLock) {
                        boolean weArePlaying = mCurrentPlaybackState == PlaybackState.STATE_PLAYING;
                        mIsDisabledByPowerPolicy = isOff;
                        if (isOff) {
                            if (!mWasPreviouslyDisabledByPowerPolicy) {
                                // We're disabling media component.
                                // Remember if we are playing at this transition.
                                mWasPlayingBeforeDisabled = weArePlaying;
                                mWasPreviouslyDisabledByPowerPolicy = true;
                            }
                            shouldBePlaying = false;
                        } else {
                            mWasPreviouslyDisabledByPowerPolicy = false;
                            shouldBePlaying = mWasPlayingBeforeDisabled;
                        }
                        if (shouldBePlaying == weArePlaying) {
                            return;
                        }
                        // Make a change
                        mediaController = mActiveUserMediaController;
                        if (mediaController == null) {
                            return;
                        }
                    }
                    PlaybackState oldState = mediaController.getPlaybackState();
                    if (oldState == null) {
                        return;
                    }
                    savePlaybackState(
                            // The new state is the same as the old state, except for play/pause
                            new PlaybackState.Builder(oldState)
                                    .setState(shouldBePlaying ? PlaybackState.STATE_PLAYING
                                                    : PlaybackState.STATE_PAUSED,
                                            oldState.getPosition(),
                                            oldState.getPlaybackSpeed())
                                    .build());
                    if (shouldBePlaying) {
                        mediaController.getTransportControls().play();
                    } else {
                        mediaController.getTransportControls().pause();
                    }
                }
    };

    private final UserHandleHelper mUserHandleHelper;

    public CarMediaService(Context context, CarUserService userService) {
        this(context, userService,
                new UserHandleHelper(context, context.getSystemService(UserManager.class)));
    }

    @VisibleForTesting
    public CarMediaService(Context context, CarUserService userService,
            @NonNull UserHandleHelper userHandleHelper) {
        mContext = context;
        mUserManager = mContext.getSystemService(UserManager.class);
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);
        mMediaSourceListeners[MEDIA_SOURCE_MODE_PLAYBACK] = new RemoteCallbackList();
        mMediaSourceListeners[MEDIA_SOURCE_MODE_BROWSE] = new RemoteCallbackList();
        mIndependentPlaybackConfig = mContext.getResources().getBoolean(
                R.bool.config_mediaSourceIndependentPlayback);

        mPackageUpdateFilter = new IntentFilter();
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        mPackageUpdateFilter.addDataScheme("package");
        mUserService = userService;
        UserLifecycleEventFilter userSwitchingOrUnlockedEventFilter =
                new UserLifecycleEventFilter.Builder()
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_SWITCHING)
                        .addEventType(USER_LIFECYCLE_EVENT_TYPE_UNLOCKED)
                                .build();
        mUserService.addUserLifecycleListener(userSwitchingOrUnlockedEventFilter,
                mUserLifecycleListener);

        mPlayOnMediaSourceChangedConfig =
                mContext.getResources().getInteger(R.integer.config_mediaSourceChangedAutoplay);
        mPlayOnBootConfig = mContext.getResources().getInteger(R.integer.config_mediaBootAutoplay);
        mUserHandleHelper = userHandleHelper;
    }

    @Override
    // This method is called from ICarImpl after CarMediaService is created.
    public void init() {
        int currentUserId = ActivityManager.getCurrentUser();
        Slogf.d(CarLog.TAG_MEDIA, "init(): currentUser=" + currentUserId);
        maybeInitUser(currentUserId);
        setPowerPolicyListener();
    }

    private void maybeInitUser(int userId) {
        if (userId == UserHandle.SYSTEM.getIdentifier()) {
            return;
        }
        if (mUserManager.isUserUnlocked(UserHandle.of(userId))) {
            initUser(userId);
        } else {
            mPendingInit = true;
        }
    }

    private void initUser(@UserIdInt int userId) {
        Slogf.d(CarLog.TAG_MEDIA,
                "initUser(): userId=" + userId + ", mSharedPrefs=" + mSharedPrefs);
        UserHandle currentUser = UserHandle.of(userId);

        maybeInitSharedPrefs(userId);

        synchronized (mLock) {
            if (mUserContext != null) {
                mUserContext.unregisterReceiver(mPackageUpdateReceiver);
            }
            mUserContext = mContext.createContextAsUser(currentUser, /* flags= */ 0);
            mUserContext.registerReceiver(mPackageUpdateReceiver, mPackageUpdateFilter,
                    Context.RECEIVER_NOT_EXPORTED);

            mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK] = isCurrentUserEphemeral()
                    ? getDefaultMediaSource() : getLastMediaSource(MEDIA_SOURCE_MODE_PLAYBACK);
            mPrimaryMediaComponents[MEDIA_SOURCE_MODE_BROWSE] = isCurrentUserEphemeral()
                    ? getDefaultMediaSource() : getLastMediaSource(MEDIA_SOURCE_MODE_BROWSE);
            mActiveUserMediaController = null;

            updateMediaSessionCallbackForCurrentUser();
            notifyListeners(MEDIA_SOURCE_MODE_PLAYBACK);
            notifyListeners(MEDIA_SOURCE_MODE_BROWSE);
        }

        startMediaConnectorService(shouldStartPlayback(mPlayOnBootConfig), currentUser);
    }

    private void maybeInitSharedPrefs(@UserIdInt int userId) {
        // SharedPreferences are shared among different users thus only need initialized once. And
        // they should be initialized after user 0 is unlocked because SharedPreferences in
        // credential encrypted storage are not available until after user 0 is unlocked.
        // initUser() is called when the current foreground user is unlocked, and by that time user
        // 0 has been unlocked already, so initializing SharedPreferences in initUser() is fine.
        if (mSharedPrefs != null) {
            Slogf.i(CarLog.TAG_MEDIA, "Shared preferences already set (on directory "
                    + mContext.getDataDir() + ") when initializing user " + userId);
            return;
        }
        Slogf.i(CarLog.TAG_MEDIA, "Getting shared preferences when initializing user "
                + userId);
        mSharedPrefs = mContext.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);

        // Try to access the properties to make sure they were properly open
        if (DEBUG) {
            Slogf.i(CarLog.TAG_MEDIA, "Number of prefs: %d", mSharedPrefs.getAll().size());
        } else if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
            Slogf.d(CarLog.TAG_MEDIA, "Number of prefs: %d", mSharedPrefs.getAll().size());
        }
    }

    /**
     * Starts a service on the current user that binds to the media browser of the current media
     * source. We start a new service because this one runs on user 0, and MediaBrowser doesn't
     * provide an API to connect on a specific user. Additionally, this service will attempt to
     * resume playback using the MediaSession obtained via the media browser connection, which
     * is more reliable than using active MediaSessions from MediaSessionManager.
     */
    private void startMediaConnectorService(boolean startPlayback, UserHandle currentUser) {
        synchronized (mLock) {
            if (mUserContext == null) {
                Slogf.wtf(CarLog.TAG_MEDIA,
                        "Cannot start MediaConnection service. User has not been initialized");
                return;
            }
            Intent serviceStart = new Intent(MEDIA_CONNECTION_ACTION);
            serviceStart.setPackage(
                    mContext.getResources().getString(R.string.serviceMediaConnection));
            serviceStart.putExtra(EXTRA_AUTOPLAY, startPlayback);
            mUserContext.startForegroundService(serviceStart);
        }
    }

    private boolean sharedPrefsInitialized() {
        if (mSharedPrefs != null) return true;

        // It shouldn't reach this but let's be cautious.
        Slogf.e(CarLog.TAG_MEDIA, "SharedPreferences are not initialized!");
        String className = getClass().getName();
        for (StackTraceElement ste : Thread.currentThread().getStackTrace()) {
            // Let's print the useful logs only.
            String log = ste.toString();
            if (log.contains(className)) {
                Slogf.e(CarLog.TAG_MEDIA, log);
            }
        }
        return false;
    }

    private boolean isCurrentUserEphemeral() {
        return mUserHandleHelper.isEphemeralUser(UserHandle.of(ActivityManager.getCurrentUser()));
    }

    // Sets a listener to be notified when the current power policy changes.
    // Basically, the listener pauses the audio when a media component is disabled and resumes
    // the audio when a media component is enabled.
    // This is called only from init().
    private void setPowerPolicyListener() {
        CarPowerPolicyFilter filter = new CarPowerPolicyFilter.Builder()
                .setComponents(PowerComponent.MEDIA).build();
        CarLocalServices.getService(CarPowerManagementService.class)
                .addPowerPolicyListener(filter, mPowerPolicyListener);
    }

    @Override
    public void release() {
        mMediaSessionUpdater.unregisterCallbacks();
        mUserService.removeUserLifecycleListener(mUserLifecycleListener);
        CarLocalServices.getService(CarPowerManagementService.class)
                .removePowerPolicyListener(mPowerPolicyListener);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.println("*CarMediaService*");
        writer.increaseIndent();

        writer.printf("Pending init: %b\n", mPendingInit);
        boolean hasSharedPrefs;
        synchronized (mLock) {
            hasSharedPrefs = mSharedPrefs != null;
            dumpCurrentMediaComponentLocked(writer, "playback", MEDIA_SOURCE_MODE_PLAYBACK);
            dumpCurrentMediaComponentLocked(writer, "browse", MEDIA_SOURCE_MODE_BROWSE);
            if (mActiveUserMediaController != null) {
                writer.printf("Current media controller: %s\n",
                        mActiveUserMediaController.getPackageName());
                writer.printf("Current browse service extra: %s\n",
                        getClassName(mActiveUserMediaController));
            } else {
                writer.println("no active user media controller");
            }
            int userId = ActivityManager.getCurrentUser();
            writer.printf("Number of active media sessions (for current user %d): %d\n", userId,
                    mMediaSessionManager.getActiveSessionsForUser(/* notificationListener= */ null,
                            UserHandle.of(userId)).size());

            writer.printf("Disabled by power policy: %s\n", mIsDisabledByPowerPolicy);
            if (mIsDisabledByPowerPolicy) {
                writer.printf("Before being disabled by power policy, audio was %s\n",
                        mWasPlayingBeforeDisabled ? "active" : "inactive");
            }
        }

        if (hasSharedPrefs) {
            dumpLastMediaSources(writer, "Playback", MEDIA_SOURCE_MODE_PLAYBACK);
            dumpLastMediaSources(writer, "Browse", MEDIA_SOURCE_MODE_BROWSE);
            dumpSharedPrefs(writer);
        } else {
            writer.println("No shared preferences");
        }

        writer.decreaseIndent();
    }

    @GuardedBy("mLock")
    private void dumpCurrentMediaComponentLocked(IndentingPrintWriter writer, String name,
            @CarMediaManager.MediaSourceMode int mode) {
        ComponentName componentName = mPrimaryMediaComponents[mode];
        writer.printf("Current %s media component: %s\n", name, componentName == null
                ? "-"
                : componentName.flattenToString());
    }

    private void dumpLastMediaSources(IndentingPrintWriter writer, String name,
            @CarMediaManager.MediaSourceMode int mode) {
        writer.printf("%s media source history:\n", name);
        writer.increaseIndent();
        List<ComponentName> lastMediaSources = getLastMediaSources(mode);
        for (int i = 0; i < lastMediaSources.size(); i++) {
            ComponentName componentName = lastMediaSources.get(i);
            if (componentName == null) {
                Slogf.e(CarLog.TAG_MEDIA, "dump(): empty last media source of %s at index %d: %s",
                        mediaModeToString(mode), i, lastMediaSources);
                continue;
            }
            writer.println(componentName.flattenToString());
        }
        writer.decreaseIndent();
    }

    private void dumpSharedPrefs(IndentingPrintWriter writer) {
        Map<String, ?> allPrefs = mSharedPrefs.getAll();
        long lastUpdate = mSharedPrefs.getLong(LAST_UPDATE_KEY, -1);
        writer.printf("%d shared preferences (saved on directory %s; last update on %d / ",
                allPrefs.size(), mContext.getDataDir(), lastUpdate);
        TimeUtils.dumpTime(writer, lastUpdate);
        writer.print(')');
        if (!Slogf.isLoggable(CarLog.TAG_MEDIA, Log.VERBOSE) || allPrefs.isEmpty()) {
            writer.println();
            return;
        }
        writer.println(':');
        writer.increaseIndent();
        for (Entry<String, ?> pref : allPrefs.entrySet()) {
            writer.printf("%s = %s\n", pref.getKey(), pref.getValue());
        }
        writer.decreaseIndent();
    }

    /**
     * @see {@link CarMediaManager#setMediaSource(ComponentName)}
     */
    @Override
    public void setMediaSource(@NonNull ComponentName componentName,
            @MediaSourceMode int mode) {
        CarServiceUtils.assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
            Slogf.d(CarLog.TAG_MEDIA, "Changing media source to: "
                    + componentName.getPackageName());
        }
        setPrimaryMediaSource(componentName, mode);
    }

    /**
     * @see {@link CarMediaManager#getMediaSource()}
     */
    @Override
    public ComponentName getMediaSource(@CarMediaManager.MediaSourceMode int mode) {
        CarServiceUtils.assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        synchronized (mLock) {
            return mPrimaryMediaComponents[mode];
        }
    }

    /**
     * @see {@link CarMediaManager#registerMediaSourceListener(MediaSourceChangedListener)}
     */
    @Override
    public void registerMediaSourceListener(ICarMediaSourceListener callback,
            @MediaSourceMode int mode) {
        CarServiceUtils.assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        mMediaSourceListeners[mode].register(callback);
    }

    /**
     * @see {@link CarMediaManager#unregisterMediaSourceListener(ICarMediaSourceListener)}
     */
    @Override
    public void unregisterMediaSourceListener(ICarMediaSourceListener callback,
            @MediaSourceMode int mode) {
        CarServiceUtils.assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        mMediaSourceListeners[mode].unregister(callback);
    }

    @Override
    public List<ComponentName> getLastMediaSources(@CarMediaManager.MediaSourceMode int mode) {
        CarServiceUtils.assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        String key = getMediaSourceKey(mode);
        String serialized = mSharedPrefs.getString(key, "");
        return getComponentNameList(serialized).stream()
                .map(name -> ComponentName.unflattenFromString(name)).collect(Collectors.toList());
    }

    /** See {@link CarMediaManager#isIndependentPlaybackConfig}. */
    @Override
    @TestApi
    public boolean isIndependentPlaybackConfig() {
        CarServiceUtils.assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        synchronized (mLock) {
            return mIndependentPlaybackConfig;
        }
    }

    /** See {@link CarMediaManager#setIndependentPlaybackConfig}. */
    @Override
    @TestApi
    public void setIndependentPlaybackConfig(boolean independent) {
        CarServiceUtils.assertPermission(mContext,
                android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        synchronized (mLock) {
            mIndependentPlaybackConfig = independent;
        }
    }

    // TODO(b/153115826): this method was used to be called from the ICar binder thread, but it's
    // now called by UserCarService. Currently UserCarService is calling every listener in one
    // non-main thread, but it's not clear how the final behavior will be. So, for now it's ok
    // to post it to mMainHandler, but once b/145689885 is fixed, we might not need it.
    private void onUserUnlocked(@UserIdInt int userId) {
        Slogf.d(CarLog.TAG_MEDIA, "onUserUnlocked(): userId=" + userId
                + ", mPendingInit=" + mPendingInit);
        mCommonThreadHandler.post(() -> {
            // No need to handle system user, non current foreground user.
            if (userId == UserHandle.SYSTEM.getIdentifier()
                    || userId != ActivityManager.getCurrentUser()) {
                return;
            }
            if (mPendingInit) {
                initUser(userId);
                mPendingInit = false;
                if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                    Slogf.d(CarLog.TAG_MEDIA,
                            "User " + userId + " is now unlocked");
                }
            }
        });
    }

    private void updateMediaSessionCallbackForCurrentUser() {
        if (mSessionsListener != null) {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mSessionsListener);
        }
        mSessionsListener = new SessionChangedListener(ActivityManager.getCurrentUser());
        UserHandle currentUserHandle = UserHandle.of(ActivityManager.getCurrentUser());
        mMediaSessionManager.addOnActiveSessionsChangedListener(null, currentUserHandle,
                new HandlerExecutor(mHandler), mSessionsListener);
        mMediaSessionUpdater.registerCallbacks(mMediaSessionManager.getActiveSessionsForUser(null,
                currentUserHandle));
    }

    /**
     * Attempts to stop the current source using MediaController.TransportControls.stop()
     * This method also unregisters callbacks to the active media controller before calling stop(),
     * to preserve the PlaybackState before stopping.
     */
    private void stopAndUnregisterCallback() {
        synchronized (mLock) {
            if (mActiveUserMediaController != null) {
                mActiveUserMediaController.unregisterCallback(mMediaControllerCallback);
                if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                    Slogf.d(CarLog.TAG_MEDIA,
                            "stopping " + mActiveUserMediaController.getPackageName());
                }
                TransportControls controls = mActiveUserMediaController.getTransportControls();
                if (controls != null) {
                    controls.stop();
                } else {
                    Slogf.e(CarLog.TAG_MEDIA, "Can't stop playback, transport controls unavailable "
                            + mActiveUserMediaController.getPackageName());
                }
            }
        }
    }

    private class SessionChangedListener implements OnActiveSessionsChangedListener {
        private final int mCurrentUser;

        SessionChangedListener(int currentUser) {
            mCurrentUser = currentUser;
        }

        @Override
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            if (ActivityManager.getCurrentUser() != mCurrentUser) {
                Slogf.e(CarLog.TAG_MEDIA, "Active session callback for old user: " + mCurrentUser);
                return;
            }
            mMediaSessionUpdater.registerCallbacks(controllers);
        }
    }

    private class MediaControllerCallback extends MediaController.Callback {

        private final MediaController mMediaController;
        private PlaybackState mPreviousPlaybackState;

        private MediaControllerCallback(MediaController mediaController) {
            mMediaController = mediaController;
        }

        private void register() {
            mMediaController.registerCallback(this);
        }

        private void unregister() {
            mMediaController.unregisterCallback(this);
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if (state != null && state.isActive()
                    && (mPreviousPlaybackState == null || !mPreviousPlaybackState.isActive())) {
                ComponentName mediaSource = getMediaSource(mMediaController.getPackageName(),
                        getClassName(mMediaController));
                if (mediaSource != null && Slogf.isLoggable(CarLog.TAG_MEDIA, Log.INFO)) {
                    synchronized (mLock) {
                        if (!mediaSource.equals(
                                mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK])) {
                            Slogf.i(CarLog.TAG_MEDIA,
                                    "Changing media source due to playback state change: "
                                    + mediaSource.flattenToString());
                        }
                    }
                }
                setPrimaryMediaSource(mediaSource, MEDIA_SOURCE_MODE_PLAYBACK);
            }
            mPreviousPlaybackState = state;
        }
    }

    private class MediaSessionUpdater {
        private Map<Token, MediaControllerCallback> mCallbacks = new HashMap<>();

        /**
         * Register a {@link MediaControllerCallback} for each given controller. Note that if a
         * controller was already watched, we don't register a callback again. This prevents an
         * undesired revert of the primary media source. Callbacks for previously watched
         * controllers that are not present in the given list are unregistered.
         */
        private void registerCallbacks(List<MediaController> newControllers) {

            List<MediaController> additions = new ArrayList<>(newControllers.size());
            Map<MediaSession.Token, MediaControllerCallback> updatedCallbacks =
                    new HashMap<>(newControllers.size());

            for (MediaController controller : newControllers) {
                MediaSession.Token token = controller.getSessionToken();
                MediaControllerCallback callback = mCallbacks.get(token);
                if (callback == null) {
                    callback = new MediaControllerCallback(controller);
                    callback.register();
                    additions.add(controller);
                }
                updatedCallbacks.put(token, callback);
            }

            for (MediaSession.Token token : mCallbacks.keySet()) {
                if (!updatedCallbacks.containsKey(token)) {
                    mCallbacks.get(token).unregister();
                }
            }

            mCallbacks = updatedCallbacks;
            updatePrimaryMediaSourceWithCurrentlyPlaying(additions);
            // If there are no playing media sources, and we don't currently have the controller
            // for the active source, check the active sessions for a matching controller. If this
            // is called after a user switch, its possible for a matching controller to already be
            // active before the user is unlocked, so we check all of the current controllers
            synchronized (mLock) {
                if (mActiveUserMediaController == null) {
                    updateActiveMediaControllerLocked(newControllers);
                }
            }
        }

        /**
         * Unregister all MediaController callbacks
         */
        private void unregisterCallbacks() {
            for (Map.Entry<Token, MediaControllerCallback> entry : mCallbacks.entrySet()) {
                entry.getValue().unregister();
            }
        }
    }

    /**
     * Updates the primary media source, then notifies content observers of the change
     * Will update both the playback and browse sources if independent playback is not supported
     */
    private void setPrimaryMediaSource(@NonNull ComponentName componentName,
            @CarMediaManager.MediaSourceMode int mode) {
        synchronized (mLock) {
            if (mPrimaryMediaComponents[mode] != null
                    && mPrimaryMediaComponents[mode].equals((componentName))) {
                return;
            }
        }

        if (!mIndependentPlaybackConfig) {
            setPlaybackMediaSource(componentName);
            setBrowseMediaSource(componentName);
        } else if (mode == MEDIA_SOURCE_MODE_PLAYBACK) {
            setPlaybackMediaSource(componentName);
        } else if (mode == MEDIA_SOURCE_MODE_BROWSE) {
            setBrowseMediaSource(componentName);
        }
    }

    private void setPlaybackMediaSource(ComponentName playbackMediaSource) {
        stopAndUnregisterCallback();

        synchronized (mLock) {
            mActiveUserMediaController = null;
            mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK] = playbackMediaSource;
        }

        if (playbackMediaSource != null
                && !TextUtils.isEmpty(playbackMediaSource.flattenToString())) {
            if (!isCurrentUserEphemeral()) {
                saveLastMediaSource(playbackMediaSource, MEDIA_SOURCE_MODE_PLAYBACK);
            }
            if (playbackMediaSource
                    .equals(mRemovedMediaSourceComponents[MEDIA_SOURCE_MODE_PLAYBACK])) {
                mRemovedMediaSourceComponents[MEDIA_SOURCE_MODE_PLAYBACK] = null;
            }
            startMediaConnectorService(
                    shouldStartPlayback(mPlayOnMediaSourceChangedConfig),
                    UserHandle.of(ActivityManager.getCurrentUser()));
        } else {
            Slogf.i(
                    CarLog.TAG_MEDIA,
                    "Media source is null, skip starting media connector service");
        }

        notifyListeners(MEDIA_SOURCE_MODE_PLAYBACK);
        // Reset current playback state for the new source, in the case that the app is in an error
        // state (e.g. not signed in). This state will be updated from the app callback registered
        // below, to make sure mCurrentPlaybackState reflects the current source only.
        synchronized (mLock) {
            mCurrentPlaybackState = PlaybackState.STATE_NONE;
            updateActiveMediaControllerLocked(mMediaSessionManager
                    .getActiveSessionsForUser(null,
                            UserHandle.of(ActivityManager.getCurrentUser())));
        }
    }

    private void setBrowseMediaSource(ComponentName browseMediaSource) {
        synchronized (mLock) {
            mPrimaryMediaComponents[MEDIA_SOURCE_MODE_BROWSE] = browseMediaSource;
        }

        if (browseMediaSource != null && !TextUtils.isEmpty(browseMediaSource.flattenToString())) {
            if (!isCurrentUserEphemeral()) {
                saveLastMediaSource(browseMediaSource, MEDIA_SOURCE_MODE_BROWSE);
            }
            if (browseMediaSource
                    .equals(mRemovedMediaSourceComponents[MEDIA_SOURCE_MODE_BROWSE])) {
                mRemovedMediaSourceComponents[MEDIA_SOURCE_MODE_BROWSE] = null;
            }
        }

        notifyListeners(MEDIA_SOURCE_MODE_BROWSE);
    }

    private void notifyListeners(@CarMediaManager.MediaSourceMode int mode) {
        synchronized (mLock) {
            int i = mMediaSourceListeners[mode].beginBroadcast();
            while (i-- > 0) {
                try {
                    ICarMediaSourceListener callback =
                            mMediaSourceListeners[mode].getBroadcastItem(i);
                    callback.onMediaSourceChanged(mPrimaryMediaComponents[mode]);
                } catch (RemoteException e) {
                    Slogf.e(CarLog.TAG_MEDIA, "calling onMediaSourceChanged failed " + e);
                }
            }
            mMediaSourceListeners[mode].finishBroadcast();
        }
    }

    private MediaController.Callback mMediaControllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            savePlaybackState(state);
        }
    };

    /**
     * Finds the currently playing media source, then updates the active source if the component
     * name is different.
     */
    private void updatePrimaryMediaSourceWithCurrentlyPlaying(
            List<MediaController> controllers) {
        for (MediaController controller : controllers) {
            PlaybackState playbackState = controller.getPlaybackState();
            if (playbackState != null && playbackState.isActive()) {
                String newPackageName = controller.getPackageName();
                String newClassName = getClassName(controller);
                if (!matchPrimaryMediaSource(newPackageName, newClassName,
                        MEDIA_SOURCE_MODE_PLAYBACK)) {
                    ComponentName mediaSource = getMediaSource(newPackageName, newClassName);
                    if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.INFO)) {
                        if (mediaSource != null) {
                            Slogf.i(CarLog.TAG_MEDIA,
                                    "MediaController changed, updating media source to: "
                                            + mediaSource.flattenToString());
                        } else {
                            // Some apps, like Chrome, have a MediaSession but no
                            // MediaBrowseService. Media Center doesn't consider such apps as
                            // valid media sources.
                            Slogf.i(CarLog.TAG_MEDIA,
                                    "MediaController changed, but no media browse service found "
                                            + "in package: " + newPackageName);
                        }
                    }
                    setPrimaryMediaSource(mediaSource, MEDIA_SOURCE_MODE_PLAYBACK);
                }
                return;
            }
        }
    }

    private boolean matchPrimaryMediaSource(@NonNull String newPackageName,
            @NonNull String newClassName, @CarMediaManager.MediaSourceMode int mode) {
        synchronized (mLock) {
            if (mPrimaryMediaComponents[mode] != null
                    && mPrimaryMediaComponents[mode].getPackageName().equals(newPackageName)) {
                // If the class name of currently active source is not specified, only checks
                // package name; otherwise checks both package name and class name.
                if (TextUtils.isEmpty(newClassName)) {
                    return true;
                } else {
                    return newClassName.equals(mPrimaryMediaComponents[mode].getClassName());
                }
            }
        }
        return false;
    }

    /**
     * Returns {@code true} if the provided component has a valid {@link MediaBrowseService}.
     */
    @VisibleForTesting
    public boolean isMediaService(@NonNull ComponentName componentName) {
        return getMediaService(componentName) != null;
    }

    /*
     * Gets the media service that matches the componentName for the current foreground user.
     */
    private ComponentName getMediaService(@NonNull ComponentName componentName) {
        String packageName = componentName.getPackageName();
        String className = componentName.getClassName();

        PackageManager packageManager = mContext.getPackageManager();
        Intent mediaIntent = new Intent();
        mediaIntent.setPackage(packageName);
        mediaIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);
        List<ResolveInfo> mediaServices = packageManager.queryIntentServicesAsUser(mediaIntent,
                PackageManager.GET_RESOLVED_FILTER,
                UserHandle.of(ActivityManager.getCurrentUser()));

        for (ResolveInfo service : mediaServices) {
            String serviceName = service.serviceInfo.name;
            if (!TextUtils.isEmpty(serviceName)
                    // If className is not specified, returns the first service in the package;
                    // otherwise returns the matched service.
                    // TODO(b/136274456): find a proper way to handle the case where there are
                    //  multiple services and the className is not specified.

                    && (TextUtils.isEmpty(className) || serviceName.equals(className))) {
                return new ComponentName(packageName, serviceName);
            }
        }

        if (Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
            Slogf.d(CarLog.TAG_MEDIA, "No MediaBrowseService with ComponentName: "
                    + componentName.flattenToString());
        }
        return null;
    }

    /*
     * Gets the component name of the media service.
     */
    @Nullable
    private ComponentName getMediaSource(@NonNull String packageName, @NonNull String className) {
        return getMediaService(new ComponentName(packageName, className));
    }

    private void saveLastMediaSource(@NonNull ComponentName component, int mode) {
        if (!sharedPrefsInitialized()) {
            return;
        }
        String componentName = component.flattenToString();
        String key = getMediaSourceKey(mode);
        String serialized = mSharedPrefs.getString(key, null);
        String modeName = null;
        boolean debug = DEBUG || Slogf.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG);
        if (debug) {
            modeName = mediaModeToString(mode);
        }

        if (serialized == null) {
            if (debug) {
                Slogf.d(CarLog.TAG_MEDIA, "saveLastMediaSource(%s, %s): no value for key %s",
                        componentName, modeName, key);
            }
            getSharedPrefsForWriting().putString(key, componentName).apply();
        } else {
            Deque<String> componentNames = new ArrayDeque<>(getComponentNameList(serialized));
            componentNames.remove(componentName);
            componentNames.addFirst(componentName);
            String newSerialized = serializeComponentNameList(componentNames);
            if (debug) {
                Slogf.d(CarLog.TAG_MEDIA, "saveLastMediaSource(%s, %s): updating %s from %s to %s",
                        componentName, modeName,  key, serialized, newSerialized);
            }
            getSharedPrefsForWriting().putString(key, newSerialized).apply();
        }
    }

    private @NonNull ComponentName getLastMediaSource(int mode) {
        if (sharedPrefsInitialized()) {
            String key = getMediaSourceKey(mode);
            String serialized = mSharedPrefs.getString(key, "");
            if (!TextUtils.isEmpty(serialized)) {
                for (String name : getComponentNameList(serialized)) {
                    ComponentName componentName = ComponentName.unflattenFromString(name);
                    if (isMediaService(componentName)) {
                        return componentName;
                    }
                }
            }
        }
        return getDefaultMediaSource();
    }

    private ComponentName getDefaultMediaSource() {
        String defaultMediaSource = mContext.getString(R.string.config_defaultMediaSource);
        ComponentName defaultComponent = ComponentName.unflattenFromString(defaultMediaSource);
        if (isMediaService(defaultComponent)) {
            return defaultComponent;
        }
        Slogf.e(CarLog.TAG_MEDIA, "No media service in the default component: " + defaultComponent);
        return null;
    }

    private String serializeComponentNameList(Deque<String> componentNames) {
        return componentNames.stream().collect(Collectors.joining(COMPONENT_NAME_SEPARATOR));
    }

    private List<String> getComponentNameList(@NonNull String serialized) {
        String[] componentNames = serialized.split(COMPONENT_NAME_SEPARATOR);
        return (Arrays.asList(componentNames));
    }

    private void savePlaybackState(PlaybackState playbackState) {
        if (!sharedPrefsInitialized()) {
            return;
        }
        if (isCurrentUserEphemeral()) {
            return;
        }
        int state = playbackState != null ? playbackState.getState() : PlaybackState.STATE_NONE;
        synchronized (mLock) {
            mCurrentPlaybackState = state;
        }
        String key = getPlaybackStateKey();
        Slogf.d(CarLog.TAG_MEDIA, "savePlaybackState(): %s = %d)", key, state);
        getSharedPrefsForWriting().putInt(key, state).apply();
    }

    /**
     * Builds a string key for saving the playback state for a specific media source (and user)
     */
    private String getPlaybackStateKey() {
        synchronized (mLock) {
            return PLAYBACK_STATE_KEY + ActivityManager.getCurrentUser()
                    + (mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK] == null ? ""
                    : mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK].flattenToString());
        }
    }

    private String getMediaSourceKey(int mode) {
        return SOURCE_KEY + mode + SOURCE_KEY_SEPARATOR + ActivityManager.getCurrentUser();
    }

    /**
     * Updates active media controller from the list that has the same component name as the primary
     * media component. Clears callback and resets media controller to null if not found.
     */
    @GuardedBy("mLock")
    private void updateActiveMediaControllerLocked(List<MediaController> mediaControllers) {
        if (mPrimaryMediaComponents[MEDIA_SOURCE_MODE_PLAYBACK] == null) {
            return;
        }
        if (mActiveUserMediaController != null) {
            mActiveUserMediaController.unregisterCallback(mMediaControllerCallback);
            mActiveUserMediaController = null;
        }
        for (MediaController controller : mediaControllers) {
            if (matchPrimaryMediaSource(controller.getPackageName(), getClassName(controller),
                    MEDIA_SOURCE_MODE_PLAYBACK)) {
                mActiveUserMediaController = controller;
                PlaybackState state = mActiveUserMediaController.getPlaybackState();
                savePlaybackState(state);
                // Specify Handler to receive callbacks on, to avoid defaulting to the calling
                // thread; this method can be called from the MediaSessionManager callback.
                // Using the version of this method without passing a handler causes a
                // RuntimeException for failing to create a Handler.
                mActiveUserMediaController.registerCallback(mMediaControllerCallback, mHandler);
                return;
            }
        }
    }

    /**
     * Returns whether we should autoplay the current media source
     */
    private boolean shouldStartPlayback(int config) {
        switch (config) {
            case AUTOPLAY_CONFIG_NEVER:
                return false;
            case AUTOPLAY_CONFIG_ALWAYS:
                return true;
            case AUTOPLAY_CONFIG_RETAIN_PER_SOURCE:
                if (!sharedPrefsInitialized()) {
                    return false;
                }
                return mSharedPrefs.getInt(getPlaybackStateKey(), PlaybackState.STATE_NONE)
                        == PlaybackState.STATE_PLAYING;
            case AUTOPLAY_CONFIG_RETAIN_PREVIOUS:
                synchronized (mLock) {
                    return mCurrentPlaybackState == PlaybackState.STATE_PLAYING;
                }
            default:
                Slogf.e(CarLog.TAG_MEDIA, "Unsupported playback configuration: " + config);
                return false;
        }
    }

    /**
     * Gets the editor used to update shared preferences.
     */
    private SharedPreferences.Editor getSharedPrefsForWriting() {
        long now = System.currentTimeMillis();
        Slogf.i(CarLog.TAG_MEDIA, "Updating %s to %d", LAST_UPDATE_KEY, now);
        return mSharedPrefs.edit().putLong(LAST_UPDATE_KEY, now);
    }

    @NonNull
    private static String getClassName(@NonNull MediaController controller) {
        Bundle sessionExtras = controller.getExtras();
        String value =
                sessionExtras == null ? "" : sessionExtras.getString(
                        Car.CAR_EXTRA_BROWSE_SERVICE_FOR_SESSION);
        return value != null ? value : "";
    }

    private static String mediaModeToString(@CarMediaManager.MediaSourceMode int mode) {
        return DebugUtils.constantToString(CarMediaManager.class, "MEDIA_SOURCE_", mode);
    }
}
