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

package com.android.car.carlauncher;

import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.app.Activity;
import android.app.ActivityOptions;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.content.pm.CarPackageManager;
import android.car.media.CarMediaManager;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.LauncherActivityInfo;
import android.content.pm.LauncherApps;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.os.Process;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Util class that contains helper method used by app launcher classes.
 */
public class AppLauncherUtils {
    private static final String TAG = "AppLauncherUtils";

    @Retention(SOURCE)
    @IntDef({APP_TYPE_LAUNCHABLES, APP_TYPE_MEDIA_SERVICES})
    @interface AppTypes {}
    static final int APP_TYPE_LAUNCHABLES = 1;
    static final int APP_TYPE_MEDIA_SERVICES = 2;

    private static final String TAG_AUTOMOTIVE_APP = "automotiveApp";
    private static final String TAG_USES = "uses";
    private static final String ATTRIBUTE_NAME = "name";
    private static final String TYPE_VIDEO = "video";
    static final String PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR = ";";

    // Max no. of uses tags in automotiveApp XML. This is an arbitrary limit to be defensive
    // to bad input.
    private static final int MAX_APP_TYPES = 64;

    private AppLauncherUtils() {
    }

    /**
     * Comparator for {@link AppMetaData} that sorts the list
     * by the "displayName" property in ascending order.
     */
    static final Comparator<AppMetaData> ALPHABETICAL_COMPARATOR = Comparator
            .comparing(AppMetaData::getDisplayName, String::compareToIgnoreCase);

    /**
     * Helper method that launches the app given the app's AppMetaData.
     *
     * @param app the requesting app's AppMetaData
     */
    static void launchApp(Context context, Intent intent) {
        ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(context.getDisplayId());
        context.startActivity(intent, options.toBundle());
    }

    /** Bundles application and services info. */
    static class LauncherAppsInfo {
        /*
         * Map of all car launcher components' (including launcher activities and media services)
         * metadata keyed by ComponentName.
         */
        private final Map<ComponentName, AppMetaData> mLaunchables;

        /** Map of all the media services keyed by ComponentName. */
        private final Map<ComponentName, ResolveInfo> mMediaServices;

        LauncherAppsInfo(@NonNull Map<ComponentName, AppMetaData> launchablesMap,
                @NonNull Map<ComponentName, ResolveInfo> mediaServices) {
            mLaunchables = launchablesMap;
            mMediaServices = mediaServices;
        }

        /** Returns true if all maps are empty. */
        boolean isEmpty() {
            return mLaunchables.isEmpty() && mMediaServices.isEmpty();
        }

        /**
         * Returns whether the given componentName is a media service.
         */
        boolean isMediaService(ComponentName componentName) {
            return mMediaServices.containsKey(componentName);
        }

        /** Returns the {@link AppMetaData} for the given componentName. */
        @Nullable
        AppMetaData getAppMetaData(ComponentName componentName) {
            return mLaunchables.get(componentName);
        }

        /** Returns a new list of all launchable components' {@link AppMetaData}. */
        @NonNull
        List<AppMetaData> getLaunchableComponentsList() {
            return new ArrayList<>(mLaunchables.values());
        }
    }

    private final static LauncherAppsInfo EMPTY_APPS_INFO = new LauncherAppsInfo(
            Collections.emptyMap(), Collections.emptyMap());

    /*
     * Gets the media source in a given package. If there are multiple sources in the package,
     * returns the first one.
     */
    static ComponentName getMediaSource(@NonNull PackageManager packageManager,
            @NonNull String packageName) {
        Intent mediaIntent = new Intent();
        mediaIntent.setPackage(packageName);
        mediaIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);

        List<ResolveInfo> mediaServices = packageManager.queryIntentServices(mediaIntent,
                PackageManager.GET_RESOLVED_FILTER);

        if (mediaServices == null || mediaServices.isEmpty()) {
            return null;
        }
        String defaultService = mediaServices.get(0).serviceInfo.name;
        if (!TextUtils.isEmpty(defaultService)) {
            return new ComponentName(packageName, defaultService);
        }
        return null;
    }

    /**
     * Gets all the components that we want to see in the launcher in unsorted order, including
     * launcher activities and media services.
     *
     * @param appsToHide            A (possibly empty) list of apps (package names) to hide
     * @param customMediaComponents A (possibly empty) list of media components (component names)
     *                              that shouldn't be shown in Launcher because their applications'
     *                              launcher activities will be shown
     * @param appTypes              Types of apps to show (e.g.: all, or media sources only)
     * @param openMediaCenter       Whether launcher should navigate to media center when the
     *                              user selects a media source.
     * @param launcherApps          The {@link LauncherApps} system service
     * @param carPackageManager     The {@link CarPackageManager} system service
     * @param packageManager        The {@link PackageManager} system service
     * @param videoAppPredicate     Predicate that checks if a given {@link ResolveInfo} resolves
     *                              to a video app. See {@link #VideoAppPredicate}. Media-services
     *                              of such apps are always excluded.
     * @param carMediaManager       The {@link CarMediaManager} system service
     * @return a new {@link LauncherAppsInfo}
     */
    @NonNull
    static LauncherAppsInfo getLauncherApps(
            Context context,
            @NonNull Set<String> appsToHide,
            @NonNull Set<String> customMediaComponents,
            @AppTypes int appTypes,
            boolean openMediaCenter,
            LauncherApps launcherApps,
            CarPackageManager carPackageManager,
            PackageManager packageManager,
            @NonNull Predicate<ResolveInfo> videoAppPredicate,
            CarMediaManager carMediaManager) {

        if (launcherApps == null || carPackageManager == null || packageManager == null
                || carMediaManager == null) {
            return EMPTY_APPS_INFO;
        }

        // Using new list since we require a mutable list to do removeIf.
        List<ResolveInfo> mediaServices = new ArrayList<>();
        mediaServices.addAll(
                packageManager.queryIntentServices(
                        new Intent(MediaBrowserService.SERVICE_INTERFACE),
                        PackageManager.GET_RESOLVED_FILTER));
        // Exclude Media Services from Video apps from being considered. These apps should offer a
        // normal Launcher Activity as an entry point.
        mediaServices.removeIf(videoAppPredicate);

        List<LauncherActivityInfo> availableActivities =
                launcherApps.getActivityList(null, Process.myUserHandle());

        int launchablesSize = mediaServices.size() + availableActivities.size();
        Map<ComponentName, AppMetaData> launchablesMap = new HashMap<>(launchablesSize);
        Map<ComponentName, ResolveInfo> mediaServicesMap = new HashMap<>(mediaServices.size());
        Set<String> mEnabledPackages = new ArraySet<>(launchablesSize);

        // Process media services
        if ((appTypes & APP_TYPE_MEDIA_SERVICES) != 0) {
            for (ResolveInfo info : mediaServices) {
                String packageName = info.serviceInfo.packageName;
                String className = info.serviceInfo.name;
                ComponentName componentName = new ComponentName(packageName, className);
                mediaServicesMap.put(componentName, info);
                mEnabledPackages.add(packageName);
                if (shouldAddToLaunchables(componentName, appsToHide, customMediaComponents,
                        appTypes, APP_TYPE_MEDIA_SERVICES)) {
                    final boolean isDistractionOptimized = true;

                    Intent intent = new Intent(Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE);
                    intent.putExtra(Car.CAR_EXTRA_MEDIA_COMPONENT, componentName.flattenToString());

                    AppMetaData appMetaData = new AppMetaData(
                        info.serviceInfo.loadLabel(packageManager),
                        componentName,
                        info.serviceInfo.loadIcon(packageManager),
                        isDistractionOptimized,
                        contextArg -> {
                            if (openMediaCenter) {
                                AppLauncherUtils.launchApp(contextArg, intent);
                            } else {
                                selectMediaSourceAndFinish(contextArg, componentName,
                                        carMediaManager);
                            }
                        },
                        /* alternateLaunchCallback */ null);
                    launchablesMap.put(componentName, appMetaData);
                }
            }
        }

        // Process activities
        if ((appTypes & APP_TYPE_LAUNCHABLES) != 0) {
            for (LauncherActivityInfo info : availableActivities) {
                ComponentName componentName = info.getComponentName();
                String packageName = componentName.getPackageName();
                mEnabledPackages.add(packageName);
                if (shouldAddToLaunchables(componentName, appsToHide, customMediaComponents,
                        appTypes, APP_TYPE_LAUNCHABLES)) {
                    boolean isDistractionOptimized =
                        isActivityDistractionOptimized(carPackageManager, packageName,
                            info.getName());

                    Intent intent = new Intent(Intent.ACTION_MAIN)
                        .setComponent(componentName)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                    AppMetaData appMetaData = new AppMetaData(
                        info.getLabel(),
                        componentName,
                        info.getBadgedIcon(0),
                        isDistractionOptimized,
                        contextArg -> AppLauncherUtils.launchApp(contextArg, intent),
                        /* alternateLaunchCallback */ null);
                    launchablesMap.put(componentName, appMetaData);
                }
            }

            List<ResolveInfo> disabledActivities = getDisabledActivities(context, packageManager,
                    mEnabledPackages);
            for (ResolveInfo info : disabledActivities) {
                String packageName = info.activityInfo.packageName;
                String className = info.activityInfo.name;
                ComponentName componentName = new ComponentName(packageName, className);
                if (!shouldAddToLaunchables(componentName, appsToHide, customMediaComponents,
                        appTypes, APP_TYPE_LAUNCHABLES)) {
                    continue;
                }
                boolean isDistractionOptimized =
                        isActivityDistractionOptimized(carPackageManager, packageName, className);

                Intent intent = new Intent(Intent.ACTION_MAIN)
                        .setComponent(componentName)
                        .addCategory(Intent.CATEGORY_LAUNCHER)
                        .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                AppMetaData appMetaData = new AppMetaData(
                        info.activityInfo.loadLabel(packageManager),
                        componentName,
                        info.activityInfo.loadIcon(packageManager),
                        isDistractionOptimized,
                        contextArg -> {
                            packageManager.setApplicationEnabledSetting(packageName,
                                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0);
                            /* Fetch the current enabled setting to make sure the setting is synced
                             * before launching the activity. Otherwise, the activity may not
                             * launch.
                             */
                            if (packageManager.getApplicationEnabledSetting(packageName)
                                    != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                                throw new IllegalStateException(
                                        "Failed to enable the disabled package [" + packageName
                                                + "]");
                            }
                            Log.i(TAG, "Successfully enabled package [" + packageName + "]");
                            AppLauncherUtils.launchApp(contextArg, intent);
                        },
                        /* alternateLaunchCallback */ null);
                launchablesMap.put(componentName, appMetaData);
            }
        }

        return new LauncherAppsInfo(launchablesMap, mediaServicesMap);
    }

    /**
     * Predicate that can be used to check if a given {@link ResolveInfo} resolves to a Video app.
     */
    static class VideoAppPredicate implements Predicate<ResolveInfo> {
        private final PackageManager mPackageManager;

        VideoAppPredicate(PackageManager packageManager) {
            mPackageManager = packageManager;
        }

        @Override
        public boolean test(ResolveInfo resolveInfo) {
            String packageName = resolveInfo != null ? getPackageName(resolveInfo) : null;
            if (packageName == null) {
                Log.w(TAG, "Unable to determine packageName from resolveInfo");
                return false;
            }
            List<String> automotiveAppTypes =
                    getAutomotiveAppTypes(mPackageManager, getPackageName(resolveInfo));
            return automotiveAppTypes.contains(TYPE_VIDEO);
        }

        protected String getPackageName(ResolveInfo resolveInfo) {
            // A valid ResolveInfo should have exactly one of these set.
            if (resolveInfo.activityInfo != null) {
                return resolveInfo.activityInfo.packageName;
            }
            if (resolveInfo.serviceInfo != null) {
                return resolveInfo.serviceInfo.packageName;
            }
            if (resolveInfo.providerInfo != null) {
                return resolveInfo.providerInfo.packageName;
            }
            // Unexpected case.
            return null;
        }
    }


    /**
     * Returns whether app identified by {@code packageName} declares itself as a video app.
     */
    public static boolean isVideoApp(PackageManager packageManager, String packageName) {
        return getAutomotiveAppTypes(packageManager, packageName).contains(TYPE_VIDEO);
    }

    /**
     * Queries an app manifest and resources to determine the types of AAOS app it declares itself
     * as.
     *
     * @param packageManager {@link PackageManager} to query.
     * @param packageName App package.
     * @return List of AAOS app-types from XML resources.
     */
    public static List<String> getAutomotiveAppTypes(PackageManager packageManager,
            String packageName) {
        ApplicationInfo appInfo;
        Resources appResources;
        try {
            appInfo = packageManager.getApplicationInfo(packageName, PackageManager.GET_META_DATA);
            appResources = packageManager.getResourcesForApplication(appInfo);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Unexpected package not found for: " + packageName, e);
            return new ArrayList<>();
        }

        int resourceId =
                appInfo.metaData != null
                        ? appInfo.metaData.getInt("com.android.automotive", -1) : -1;
        if (resourceId == -1) {
            return new ArrayList<>();
        }
        try (XmlResourceParser parser = appResources.getXml(resourceId)) {
            return parseAutomotiveAppTypes(parser);
        }
    }

    @VisibleForTesting
    static List<String> parseAutomotiveAppTypes(XmlPullParser parser) {
        try {
            // This pattern for parsing can be seen in Javadocs for XmlPullParser.
            List<String> appTypes = new ArrayList<>();
            ArrayDeque<String> tagStack = new ArrayDeque<>();
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    String tag = parser.getName();
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "Start tag " + tag);
                    }
                    tagStack.addFirst(tag);
                    if (!validTagStack(tagStack)) {
                        Log.w(TAG, "Invalid XML; tagStack: " + tagStack);
                        return new ArrayList<>();
                    }
                    if (TAG_USES.equals(tag)) {
                        String nameValue =
                                parser.getAttributeValue(/* namespace= */ null , ATTRIBUTE_NAME);
                        if (TextUtils.isEmpty(nameValue)) {
                            Log.w(TAG, "Invalid XML; uses tag with missing/empty name attribute");
                            return new ArrayList<>();
                        }
                        appTypes.add(nameValue);
                        if (appTypes.size() > MAX_APP_TYPES) {
                            Log.w(TAG, "Too many uses tags in automotiveApp tag");
                            return new ArrayList<>();
                        }
                        if (Log.isLoggable(TAG, Log.VERBOSE)) {
                            Log.v(TAG, "Found appType: " + nameValue);
                        }
                    }
                } else if (eventType == XmlPullParser.END_TAG) {
                    if (Log.isLoggable(TAG, Log.VERBOSE)) {
                        Log.v(TAG, "End tag " + parser.getName());
                    }
                    tagStack.removeFirst();
                }
                eventType = parser.next();
            }
            return appTypes;
        } catch (XmlPullParserException | IOException e) {
            Log.w(TAG, "Unexpected exception whiling parsing XML resource", e);
            return new ArrayList<>();
        }
    }

    private static boolean validTagStack(ArrayDeque<String> tagStack) {
        // Expected to be called after a new tag is pushed on this stack.
        // Ensures that XML is of form:
        // <automotiveApp>
        //     <uses/>
        //     <uses/>
        //     ....
        // </automotiveApp>
        switch (tagStack.size()) {
            case 1:
                return TAG_AUTOMOTIVE_APP.equals(tagStack.peekFirst());
            case 2:
                return TAG_USES.equals(tagStack.peekFirst());
            default:
                return false;
        }
    }

    private static List<ResolveInfo> getDisabledActivities(Context context,
            PackageManager packageManager, Set<String> enabledPackages) {
        ContentResolver contentResolverForUser = context.createContextAsUser(
                UserHandle.getUserHandleForUid(Process.myUid()), /* flags= */ 0)
                .getContentResolver();
        String settingsValue = Settings.Secure.getString(contentResolverForUser,
                KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE);
        Set<String> disabledPackages = TextUtils.isEmpty(settingsValue) ? new ArraySet<>()
                : new ArraySet<>(Arrays.asList(settingsValue.split(
                        PACKAGES_DISABLED_ON_RESOURCE_OVERUSE_SEPARATOR)));
        if (disabledPackages.isEmpty()) {
            return Collections.emptyList();
        }

        List<ResolveInfo> allActivities = packageManager.queryIntentActivities(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER),
                PackageManager.ResolveInfoFlags.of(PackageManager.GET_RESOLVED_FILTER
                        | PackageManager.MATCH_DISABLED_UNTIL_USED_COMPONENTS));

        List<ResolveInfo> disabledActivities = new ArrayList<>();
        for (int i = 0; i < allActivities.size(); ++i) {
            ResolveInfo info = allActivities.get(i);
            if (!enabledPackages.contains(info.activityInfo.packageName)
                    && disabledPackages.contains(info.activityInfo.packageName)) {
                disabledActivities.add(info);
            }
        }
        return disabledActivities;
    }

    private static boolean shouldAddToLaunchables(@NonNull ComponentName componentName,
            @NonNull Set<String> appsToHide,
            @NonNull Set<String> customMediaComponents,
            @AppTypes int appTypesToShow,
            @AppTypes int componentAppType) {
        if (appsToHide.contains(componentName.getPackageName())) {
            return false;
        }
        switch (componentAppType) {
            // Process media services
            case APP_TYPE_MEDIA_SERVICES:
                // For a media service in customMediaComponents, if its application's launcher
                // activity will be shown in the Launcher, don't show the service's icon in the
                // Launcher.
                if (customMediaComponents.contains(componentName.flattenToString())
                        && (appTypesToShow & APP_TYPE_LAUNCHABLES) != 0) {
                    return false;
                }
                return true;
            // Process activities
            case APP_TYPE_LAUNCHABLES:
                return true;
            default:
                Log.e(TAG, "Invalid componentAppType : " + componentAppType);
                return false;
        }
    }

    private static void selectMediaSourceAndFinish(Context context, ComponentName componentName,
            CarMediaManager carMediaManager) {
        try {
            carMediaManager.setMediaSource(componentName, CarMediaManager.MEDIA_SOURCE_MODE_BROWSE);
            if (context instanceof Activity) {
                ((Activity) context).finish();
            }
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected", e);
        }
    }

    /**
     * Gets if an activity is distraction optimized.
     *
     * @param carPackageManager The {@link CarPackageManager} system service
     * @param packageName       The package name of the app
     * @param activityName      The requested activity name
     * @return true if the supplied activity is distraction optimized
     */
    static boolean isActivityDistractionOptimized(
            CarPackageManager carPackageManager, String packageName, String activityName) {
        boolean isDistractionOptimized = false;
        // try getting distraction optimization info
        try {
            if (carPackageManager != null) {
                isDistractionOptimized =
                        carPackageManager.isActivityDistractionOptimized(packageName, activityName);
            }
        } catch (CarNotConnectedException e) {
            Log.e(TAG, "Car not connected when getting DO info", e);
        }
        return isDistractionOptimized;
    }
}
