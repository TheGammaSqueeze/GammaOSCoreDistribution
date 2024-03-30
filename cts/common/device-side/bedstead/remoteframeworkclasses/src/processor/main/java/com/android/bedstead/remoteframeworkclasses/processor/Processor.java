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

package com.android.bedstead.remoteframeworkclasses.processor;

import com.android.bedstead.remoteframeworkclasses.processor.annotations.RemoteFrameworkClasses;

import com.google.android.enterprise.connectedapps.annotations.CrossUser;
import com.google.auto.service.AutoService;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Resources;
import com.squareup.javapoet.AnnotationSpec;
import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.JavaFile;
import com.squareup.javapoet.MethodSpec;
import com.squareup.javapoet.ParameterSpec;
import com.squareup.javapoet.TypeSpec;

import java.io.IOException;
import java.io.PrintWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.tools.JavaFileObject;

/**
 * Processor for generating {@code RemoteSystemService} classes.
 *
 * <p>This is started by including the {@link RemoteFrameworkClasses} annotation.
 *
 * <p>For each entry in {@code FRAMEWORK_CLASSES} this will generate an interface including all
 * public
 * and test APIs with the {@code CrossUser} annotation. This interface will be named the same as
 * the
 * framework class except with a prefix of "Remote", and will be in the same package.
 *
 * <p>This will also generate an implementation of the interface which takes an instance of the
 * framework class in the constructor, and each method proxying calls to the framework class.
 */
@SupportedAnnotationTypes({
        "com.android.bedstead.remoteframeworkclasses.processor.annotations.RemoteFrameworkClasses",
})
@AutoService(javax.annotation.processing.Processor.class)
public final class Processor extends AbstractProcessor {

    private static final String[] FRAMEWORK_CLASSES = {
            "android.app.admin.DevicePolicyManager",
            "android.net.wifi.WifiManager",
            "android.os.HardwarePropertiesManager",
            "android.os.UserManager",
            "android.content.pm.PackageManager",
            "android.content.pm.CrossProfileApps",
            "android.content.pm.LauncherApps",
            "android.accounts.AccountManager",
            "android.app.Activity",
            "android.content.Context",
            "android.content.ContentResolver",
            "android.security.KeyChain",
            "android.bluetooth.BluetoothManager",
            "android.bluetooth.BluetoothAdapter"
    };

    private static final String PARENT_PROFILE_INSTANCE =
            "public android.app.admin.DevicePolicyManager getParentProfileInstance(android"
                    + ".content.ComponentName)";
    private static final String GET_CONTENT_RESOLVER =
            "public android.content.ContentResolver getContentResolver()";
    private static final String GET_ADAPTER =
            "public android.bluetooth.BluetoothAdapter getAdapter()";
    private static final String GET_DEFAULT_ADAPTER =
            "public static android.bluetooth.BluetoothAdapter getDefaultAdapter()";

    private static final Set<String> BLOCKLISTED_METHODS = ImmutableSet.of(
            // DevicePolicyManager

            // Uses ServiceConnection
            "public boolean bindDeviceAdminServiceAsUser(android.content.ComponentName, android"
                    + ".content.Intent, android.content.ServiceConnection, int, android.os"
                    + ".UserHandle)",
            // Uses AttestedKeyPair
            "public android.security.AttestedKeyPair generateKeyPair(android.content"
                    + ".ComponentName, String, android.security.keystore.KeyGenParameterSpec, int)",
            // Uses Executor
            "public void installSystemUpdate(@NonNull android.content.ComponentName, android.net"
                    + ".Uri, java.util.concurrent.Executor, android.app.admin.DevicePolicyManager"
                    + ".InstallSystemUpdateCallback)",

            // Uses Drawable
            "public android.graphics.drawable.Drawable getDrawable(@NonNull String, @NonNull "
                    + "String, @NonNull java.util.concurrent.Callable<"
                    + "android.graphics.drawable.Drawable>)",
            "public android.graphics.drawable.Drawable getDrawable(@NonNull String, @NonNull "
                    + "String, @NonNull java.util.concurrent.Callable<"
                    + "android.graphics.drawable.Drawable>)",
            "public android.graphics.drawable.Drawable getDrawable(@NonNull String, @NonNull "
                    + "String, @NonNull String, @NonNull java.util.concurrent.Callable<"
                    + "android.graphics.drawable.Drawable>)",
            "public android.graphics.drawable.Drawable getDrawableForDensity(@NonNull String, "
                    + "@NonNull String, int, @NonNull java.util.concurrent.Callable<"
                    + "android.graphics.drawable.Drawable>)",
            "public android.graphics.drawable.Drawable getDrawableForDensity(@NonNull String, "
                    + "@NonNull String, @NonNull String, int, @NonNull "
                    + "java.util.concurrent.Callable<android.graphics.drawable.Drawable>)",
            "public android.graphics.drawable.Drawable getDrawable(@NonNull String, "
                    + "@NonNull String, "
                    + "@NonNull java.util.function.Supplier<android.graphics.drawable.Drawable>)",
            "public android.graphics.drawable.Drawable getDrawable(@NonNull String, "
                    + "@NonNull String, @NonNull String, @NonNull "
                    + "java.util.function.Supplier<android.graphics.drawable.Drawable>)",
            "public android.graphics.drawable.Drawable getDrawableForDensity(@NonNull String, "
                    + "@NonNull String, int, @NonNull "
                    + "java.util.function.Supplier<android.graphics.drawable.Drawable>)",
            "public android.graphics.drawable.Drawable getDrawableForDensity(@NonNull String, "
                    + "@NonNull String, @NonNull String, int, @NonNull "
                    + "java.util.function.Supplier<android.graphics.drawable.Drawable>)",



    // WifiManager

            // Uses Executor
            "public void addSuggestionConnectionStatusListener(java.util.concurrent.Executor, "
                    + "android.net.wifi.WifiManager.SuggestionConnectionStatusListener)",
            "public void addSuggestionUserApprovalStatusListener(java.util.concurrent.Executor, "
                    + "android.net.wifi.WifiManager.SuggestionUserApprovalStatusListener)",
            "public void clearApplicationUserData(android.content.ComponentName, @NonNull String,"
                    + " @NonNull java.util.concurrent.Executor, android.app.admin"
                    + ".DevicePolicyManager.OnClearApplicationUserDataListener)",
            "public void registerScanResultsCallback(java.util.concurrent.Executor, android.net"
                    + ".wifi.WifiManager.ScanResultsCallback)",
            "public void registerSubsystemRestartTrackingCallback(java.util.concurrent.Executor, "
                    + "android.net.wifi.WifiManager.SubsystemRestartTrackingCallback)",
            "public void reportCreateInterfaceImpact(int, boolean, "
                    + "@NonNull java.util.concurrent.Executor, @NonNull "
                    + "java.util.function.BiConsumer<java.lang.Boolean,java.util.Set<"
                    + "android.net.wifi.WifiManager.InterfaceCreationImpact>>)",
            "public void queryAutojoinGlobal(@NonNull java.util.concurrent.Executor, "
                    + "@NonNull java.util.function.Consumer<java.lang.Boolean>)",


            // Uses WpsCallback
            "public void cancelWps(android.net.wifi.WifiManager.WpsCallback)",
            // Uses MulticastLock
            "public android.net.wifi.WifiManager.MulticastLock createMulticastLock(String)",
            // Uses WifiLock
            "public android.net.wifi.WifiManager.WifiLock createWifiLock(int, String)",
            "public android.net.wifi.WifiManager.WifiLock createWifiLock(String)",
            // Uses SuggestionConnectionStatusListener
            "public void removeSuggestionConnectionStatusListener(android.net.wifi.WifiManager"
                    + ".SuggestionConnectionStatusListener)",
            // Uses SuggestionUserApprovalStatusListener
            "public void removeSuggestionUserApprovalStatusListener(android.net.wifi.WifiManager"
                    + ".SuggestionUserApprovalStatusListener)",
            // Uses LocalOnlyHotspotCallback
            "public void startLocalOnlyHotspot(android.net.wifi.WifiManager"
                    + ".LocalOnlyHotspotCallback, android.os.Handler)",
            // Uses WpsCallback
            "public void startWps(android.net.wifi.WpsInfo, android.net.wifi.WifiManager"
                    + ".WpsCallback)",
            // Uses ScanResultsCallback
            "public void unregisterScanResultsCallback(@NonNull android.net.wifi.WifiManager"
                    + ".ScanResultsCallback)",
            // Uses SubsystemRestartTrackingCallback
            "public void unregisterSubsystemRestartTrackingCallback(android.net.wifi.WifiManager"
                    + ".SubsystemRestartTrackingCallback)",

            // PackageManager

            // Uses IBinder
            "public android.os.IBinder getHoldLockToken()",
            "public void holdLock(android.os.IBinder, int)",
            // Uses Drawable
            "public abstract android.graphics.drawable.Drawable getActivityBanner(@NonNull "
                    + "android.content.ComponentName) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityBanner(@NonNull "
                    + "android.content.Intent) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityIcon(@NonNull android"
                    + ".content.ComponentName) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityIcon(@NonNull android"
                    + ".content.Intent) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityLogo(@NonNull android"
                    + ".content.ComponentName) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getActivityLogo(@NonNull android"
                    + ".content.Intent) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getApplicationBanner(@NonNull "
                    + "android.content.pm.ApplicationInfo)",
            "public abstract android.graphics.drawable.Drawable getApplicationBanner(@NonNull "
                    + "String) throws android.content.pm.PackageManager.NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getApplicationIcon(@NonNull "
                    + "android.content.pm.ApplicationInfo)",
            "public abstract android.graphics.drawable.Drawable getApplicationIcon(@NonNull "
                    + "String) throws android.content.pm.PackageManager.NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getApplicationLogo(@NonNull "
                    + "android.content.pm.ApplicationInfo)",
            "public abstract android.graphics.drawable.Drawable getApplicationLogo(@NonNull "
                    + "String) throws android.content.pm.PackageManager.NameNotFoundException",
            "public abstract android.graphics.drawable.Drawable getDefaultActivityIcon()",
            "public abstract android.graphics.drawable.Drawable getDrawable(@NonNull String, "
                    + "@DrawableRes int, @Nullable android.content.pm.ApplicationInfo)",
            "public abstract android.graphics.drawable.Drawable getUserBadgedDrawableForDensity"
                    + "(@NonNull android.graphics.drawable.Drawable, @NonNull android.os"
                    + ".UserHandle, @Nullable android.graphics.Rect, int)",
            "public abstract android.graphics.drawable.Drawable getUserBadgedIcon(@NonNull "
                    + "android.graphics.drawable.Drawable, @NonNull android.os.UserHandle)",
            "public boolean isDefaultApplicationIcon(@NonNull android.graphics.drawable.Drawable)",
            // Uses Executor
            "public void getGroupOfPlatformPermission(@NonNull String, @NonNull java.util"
                    + ".concurrent.Executor, @NonNull java.util.function.Consumer<java.lang"
                    + ".String>)",
            "public void getPlatformPermissionsForGroup(@NonNull String, @NonNull java.util"
                    + ".concurrent.Executor, @NonNull java.util.function.Consumer<java.util"
                    + ".List<java.lang.String>>)",
            // Uses Resources
            "public abstract android.content.res.Resources getResourcesForActivity(@NonNull "
                    + "android.content.ComponentName) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public abstract android.content.res.Resources getResourcesForApplication(@NonNull "
                    + "android.content.pm.ApplicationInfo) throws android.content.pm"
                    + ".PackageManager.NameNotFoundException",
            "public android.content.res.Resources getResourcesForApplication(@NonNull android"
                    + ".content.pm.ApplicationInfo, @Nullable android.content.res.Configuration) "
                    + "throws android.content.pm.PackageManager.NameNotFoundException",
            "public abstract android.content.res.Resources getResourcesForApplication(@NonNull "
                    + "String) throws android.content.pm.PackageManager.NameNotFoundException",
            // Uses PackageInstaller
            "public abstract android.content.pm.PackageInstaller getPackageInstaller()",
            // Uses XmlResourceParser
            "public abstract android.content.res.XmlResourceParser getXml(@NonNull String, "
                    + "@XmlRes int, @Nullable android.content.pm.ApplicationInfo)",
            // Uses OnChecksumsReadyListener
            "public void requestChecksums(@NonNull String, boolean, int, @NonNull java.util"
                    + ".List<java.security.cert.Certificate>, @NonNull android.content.pm"
                    + ".PackageManager.OnChecksumsReadyListener) throws java.security.cert"
                    + ".CertificateEncodingException, android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            // Uses ComponentInfoFlags
            "public android.content.pm.ActivityInfo getActivityInfo(@NonNull android.content"
                    + ".ComponentName, @NonNull android.content.pm.PackageManager"
                    + ".ComponentInfoFlags) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public android.content.pm.ProviderInfo getProviderInfo(@NonNull android.content"
                    + ".ComponentName, @NonNull android.content.pm.PackageManager"
                    + ".ComponentInfoFlags) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public android.content.pm.ActivityInfo getReceiverInfo(@NonNull android.content"
                    + ".ComponentName, @NonNull android.content.pm.PackageManager"
                    + ".ComponentInfoFlags) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public android.content.pm.ServiceInfo getServiceInfo(@NonNull android.content"
                    + ".ComponentName, @NonNull android.content.pm.PackageManager"
                    + ".ComponentInfoFlags) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public java.util.List<android.content.pm.ProviderInfo> queryContentProviders"
                    + "(@Nullable String, int, @NonNull android.content.pm.PackageManager"
                    + ".ComponentInfoFlags)",
            "public android.content.pm.ProviderInfo resolveContentProvider(@NonNull String, "
                    + "@NonNull android.content.pm.PackageManager.ComponentInfoFlags)",

            // Uses ApplicationInfoFlags
            "public android.content.pm.ApplicationInfo getApplicationInfo(@NonNull String, "
                    + "@NonNull android.content.pm.PackageManager.ApplicationInfoFlags) throws "
                    + "android.content.pm.PackageManager.NameNotFoundException",
            "public java.util.List<android.content.pm.ApplicationInfo> getInstalledApplications"
                    + "(@NonNull android.content.pm.PackageManager.ApplicationInfoFlags)",
            "public java.util.List<android.content.pm.ApplicationInfo> "
                    + "getInstalledApplicationsAsUser(@NonNull android.content.pm.PackageManager"
                    + ".ApplicationInfoFlags, int)",
            // Uses PackageInfoFlags
            "public java.util.List<android.content.pm.PackageInfo> getInstalledPackages(@NonNull "
                    + "android.content.pm.PackageManager.PackageInfoFlags)",
            "public android.content.pm.PackageInfo getPackageArchiveInfo(@NonNull String, "
                    + "@NonNull android.content.pm.PackageManager.PackageInfoFlags)",
            "public int[] getPackageGids(@NonNull String, @NonNull android.content.pm"
                    + ".PackageManager.PackageInfoFlags) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public android.content.pm.PackageInfo getPackageInfo(@NonNull String, @NonNull "
                    + "android.content.pm.PackageManager.PackageInfoFlags) throws android.content"
                    + ".pm.PackageManager.NameNotFoundException",
            "public android.content.pm.PackageInfo getPackageInfo(@NonNull android.content.pm"
                    + ".VersionedPackage, @NonNull android.content.pm.PackageManager"
                    + ".PackageInfoFlags) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public int getPackageUid(@NonNull String, @NonNull android.content.pm.PackageManager"
                    + ".PackageInfoFlags) throws android.content.pm.PackageManager"
                    + ".NameNotFoundException",
            "public java.util.List<android.content.pm.PackageInfo> getPackagesHoldingPermissions"
                    + "(@NonNull String[], @NonNull android.content.pm.PackageManager"
                    + ".PackageInfoFlags)",
            "public java.util.List<android.content.pm.SharedLibraryInfo> getSharedLibraries"
                    + "(@NonNull android.content.pm.PackageManager.PackageInfoFlags)",

            // Uses ResolveInfoFlags
            "public java.util.List<android.content.pm.ResolveInfo> queryBroadcastReceivers"
                    + "(@NonNull android.content.Intent, @NonNull android.content.pm"
                    + ".PackageManager.ResolveInfoFlags)",
            "public java.util.List<android.content.pm.ResolveInfo> queryIntentActivities(@NonNull"
                    + " android.content.Intent, @NonNull android.content.pm.PackageManager"
                    + ".ResolveInfoFlags)",

            "public java.util.List<android.content.pm.ResolveInfo> queryIntentActivityOptions"
                    + "(@Nullable android.content.ComponentName, @Nullable java.util.List<android"
                    + ".content.Intent>, @NonNull android.content.Intent, @NonNull android"
                    + ".content.pm.PackageManager.ResolveInfoFlags)",
            "public java.util.List<android.content.pm.ResolveInfo> queryIntentContentProviders"
                    + "(@NonNull android.content.Intent, @NonNull android.content.pm"
                    + ".PackageManager.ResolveInfoFlags)",
            "public java.util.List<android.content.pm.ResolveInfo> queryIntentServices(@NonNull "
                    + "android.content.Intent, @NonNull android.content.pm.PackageManager"
                    + ".ResolveInfoFlags)",
            "public android.content.pm.ResolveInfo resolveActivity(@NonNull android.content"
                    + ".Intent, @NonNull android.content.pm.PackageManager.ResolveInfoFlags)",
            "public android.content.pm.ResolveInfo resolveService(@NonNull android.content"
                    + ".Intent, @NonNull android.content.pm.PackageManager.ResolveInfoFlags)",


            // CrossProfileApps

            // Uses Drawable
            "public android.graphics.drawable.Drawable getProfileSwitchingIconDrawable("
                    + "android.os.UserHandle)",
            // Uses Activity
            "public void startActivity("
                    + "android.content.Intent, android.os.UserHandle, android.app.Activity)",
            "public void startActivity(android.content.Intent, android.os.UserHandle,"
                    + "android.app.Activity, android.os.Bundle)",

            // LauncherApps

            //Uses LauncherApps.Callback
            "public void registerCallback("
                    + "android.content.pm.LauncherApps.Callback, android.os.Handler)",
            "public void registerCallback("
                    + "android.content.pm.LauncherApps.Callback)",

            //Uses Drawable
            "public android.graphics.drawable.Drawable getShortcutBadgedIconDrawable("
                    + "android.content.pm.ShortcutInfo, int)",
            "public android.graphics.drawable.Drawable getShortcutIconDrawable("
                    + "android.content.pm.ShortcutInfo, int)",

            //Uses Executor
            "public void registerPackageInstallerSessionCallback("
                    + "@NonNull @CallbackExecutor java.util.concurrent.Executor,"
                    + "@NonNull android.content.pm.PackageInstaller.SessionCallback)",

            // Uses LauncherActivityInfo
            "public java.util.List<android.content.pm.LauncherActivityInfo> getActivityList("
                    + "String, android.os.UserHandle)",
            "public java.util.List<android.content.pm.LauncherActivityInfo> "
                    + "getShortcutConfigActivityList(String, android.os.UserHandle)",
            "public android.content.IntentSender getShortcutConfigActivityIntent("
                    + "@NonNull android.content.pm.LauncherActivityInfo)",
            "public android.content.pm.LauncherActivityInfo resolveActivity("
                    + "android.content.Intent, android.os.UserHandle)",

            //Uses LauncherApps.ShortcutQuery
            "public java.util.List<android.content.pm.ShortcutInfo> getShortcuts("
                    + "android.content.pm.LauncherApps.ShortcutQuery, android.os.UserHandle)",
            "public void unregisterCallback(android.content.pm.LauncherApps.Callback)",

            //Uses PackageInfo.SessionCallback
            "public void unregisterPackageInstallerSessionCallback("
                    + "android.content.pm.PackageInstaller.SessionCallback)",

            // AccountManager

            // Uses Activity
            "public android.accounts.AccountManagerFuture<android.os.Bundle> finishSession"
                    + "(android.os.Bundle, android.app.Activity, android.accounts"
                    + ".AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> editProperties"
                    + "(String, android.app.Activity, android.accounts"
                    + ".AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android"
                    + ".accounts.Account, String, android.os.Bundle, android.app.Activity, "
                    + "android.accounts.AccountManagerCallback<android.os.Bundle>, android.os"
                    + ".Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> "
                    + "getAuthTokenByFeatures(String, String, String[], android.app.Activity, "
                    + "android.os.Bundle, android.os.Bundle, android.accounts"
                    + ".AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> "
                    + "startAddAccountSession(String, String, String[], android.os.Bundle, "
                    + "android.app.Activity, android.accounts.AccountManagerCallback<android.os"
                    + ".Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> "
                    + "startUpdateCredentialsSession(android.accounts.Account, String, android.os"
                    + ".Bundle, android.app.Activity, android.accounts"
                    + ".AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> updateCredentials"
                    + "(android.accounts.Account, String, android.os.Bundle, android.app"
                    + ".Activity, android.accounts.AccountManagerCallback<android.os.Bundle>, "
                    + "android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> confirmCredentials"
                    + "(android.accounts.Account, android.os.Bundle, android.app.Activity, "
                    + "android.accounts.AccountManagerCallback<android.os.Bundle>, android.os"
                    + ".Handler)",

            // Uses OnAccountsUpdateListener
            "public void addOnAccountsUpdatedListener(android.accounts.OnAccountsUpdateListener, "
                    + "android.os.Handler, boolean)",
            "public void addOnAccountsUpdatedListener(android.accounts.OnAccountsUpdateListener, "
                    + "android.os.Handler, boolean, String[])",
            "public void removeOnAccountsUpdatedListener(android.accounts"
                    + ".OnAccountsUpdateListener)",

            // Uses AccountManagerCallback
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android"
                    + ".accounts.Account, String, boolean, android.accounts"
                    + ".AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android"
                    + ".accounts.Account, String, android.os.Bundle, boolean, android.accounts"
                    + ".AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android"
                    + ".accounts.Account, String, boolean, android.accounts"
                    + ".AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.os.Bundle> getAuthToken(android"
                    + ".accounts.Account, String, android.os.Bundle, boolean, android.accounts"
                    + ".AccountManagerCallback<android.os.Bundle>, android.os.Handler)",
            "public android.os.Bundle hasFeatures(android.accounts.Account, String[], android"
                    + ".accounts.AccountManagerCallback<java.lang.Boolean>, android.os.Handler)",
            "public android.os.Bundle isCredentialsUpdateSuggested(android.accounts.Account, "
                    + "String, android.accounts.AccountManagerCallback<java.lang.Boolean>, "
                    + "android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.accounts.Account[]> "
                    + "getAccountsByTypeAndFeatures(String, String[], android.accounts"
                    + ".AccountManagerCallback<android.accounts.Account[]>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<java.lang.Boolean> hasFeatures(android"
                    + ".accounts.Account, String[], android.accounts.AccountManagerCallback<java"
                    + ".lang.Boolean>, android.os.Handler)",
            "public android.os.Bundle isCredentialsUpdateSuggested(android.accounts"
                    + ".AccountAuthenticatorResponse, android.accounts.Account, String) throws "
                    + "android.accounts.NetworkErrorException",
            "public android.accounts.AccountManagerFuture<java.lang.Boolean> "
                    + "isCredentialsUpdateSuggested(android.accounts.Account, String, android"
                    + ".accounts.AccountManagerCallback<java.lang.Boolean>, android.os.Handler)",
            "public android.accounts.AccountManagerFuture<android.accounts.Account> renameAccount"
                    + "(android.accounts.Account, @Size(min=1) String, android.accounts"
                    + ".AccountManagerCallback<android.accounts.Account>, android.os.Handler)",

            // Uses android.accounts.AccountManager
            "public static android.accounts.AccountManager get(android.content.Context)",

            // Activity

            // Uses android.view.View
            "public void addContentView(android.view.View, android.view.ViewGroup.LayoutParams)",
            "@Nullable public android.view.View getCurrentFocus()",
            "@Nullable public android.view.View onCreatePanelView(int)",
            "@Nullable public android.view.View onCreateView(@NonNull String, @NonNull android"
                    + ".content.Context, @NonNull android.util.AttributeSet)",
            "@Nullable public android.view.View onCreateView(@Nullable android.view.View, "
                    + "@NonNull String, @NonNull android.content.Context, @NonNull android.util"
                    + ".AttributeSet)",
            "public boolean onPreparePanel(int, @Nullable android.view.View, @NonNull android"
                    + ".view.Menu)",
            "public void openContextMenu(android.view.View)",
            "public void registerForContextMenu(android.view.View)",
            "public void setContentView(android.view.View)",
            "public void setContentView(android.view.View, android.view.ViewGroup.LayoutParams)",
            "public void unregisterForContextMenu(android.view.View)",

            // Uses java.io.FileDescriptor
            "public void dump(@NonNull String, @Nullable java.io.FileDescriptor, @NonNull java.io"
                    + ".PrintWriter, @Nullable String[])",
            "public void dumpInternal(@NonNull String, @Nullable java.io.FileDescriptor, @NonNull "
                    + "java.io.PrintWriter, @Nullable String[])",

            // Uses android.window.OnBackInvokedDispatcher
            "public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher()",

            // Uses android.app.Activity
            "@Deprecated public void finishActivityFromChild(@NonNull android.app.Activity, int)",
            "@Deprecated public void finishFromChild(android.app.Activity)",
            "public final android.app.Activity getParent()",
            "@Deprecated public boolean navigateUpToFromChild(android.app.Activity, android"
                    + ".content.Intent)",
            "protected void onChildTitleChanged(android.app.Activity, CharSequence)",
            "@Deprecated public boolean onNavigateUpFromChild(android.app.Activity)",
            "@Deprecated public void startActivityFromChild(@NonNull android.app.Activity, "
                    + "@RequiresPermission android.content.Intent, int)",
            "@Deprecated public void startActivityFromChild(@NonNull android.app.Activity, "
                    + "@RequiresPermission android.content.Intent, int, @Nullable android.os"
                    + ".Bundle)",
            "@Deprecated public void startIntentSenderFromChild(android.app.Activity, android"
                    + ".content.IntentSender, int, android.content.Intent, int, int, int) throws "
                    + "android.content.IntentSender.SendIntentException",
            "@Deprecated public void startIntentSenderFromChild(android.app.Activity, android"
                    + ".content.IntentSender, int, android.content.Intent, int, int, int, "
                    + "@Nullable android.os.Bundle) throws android.content.IntentSender"
                    + ".SendIntentException",

            // Uses android.app.ActionBar
            "@Nullable public android.app.ActionBar getActionBar()",

            // Uses android.app.Application
            "public final android.app.Application getApplication()",

            // Uses android.app.Fragment
            "@Deprecated public void onAttachFragment(android.app.Fragment)",
            "@Deprecated public void startActivityFromFragment(@NonNull android.app.Fragment, "
                    + "@RequiresPermission android.content.Intent, int)",
            "@Deprecated public void startActivityFromFragment(@NonNull android.app.Fragment, "
                    + "@RequiresPermission android.content.Intent, int, @Nullable android.os"
                    + ".Bundle)",

            // Uses android.app.FragmentManager
            "@Deprecated public android.app.FragmentManager getFragmentManager()",

            // Uses android.transition.Scene
            "public android.transition.Scene getContentScene()",

            // Uses android.transition.TransitionManager
            "public android.transition.TransitionManager getContentTransitionManager()",
            "public void setContentTransitionManager(android.transition.TransitionManager)",

            // Uses Object
            "@Nullable public Object getLastNonConfigurationInstance()",
            "public Object onRetainNonConfigurationInstance()",

            // Uses android.view.LayoutInflater
            "@NonNull public android.view.LayoutInflater getLayoutInflater()",

            // Uses android.view.MenuInflater
            "@NonNull public android.view.MenuInflater getMenuInflater()",

            // Uses android.app.LoaderManager
            "@Deprecated public android.app.LoaderManager getLoaderManager()",

            // Uses android.media.session.MediaController
            "public final android.media.session.MediaController getMediaController()",

            // Uses android.content.SharedPreferences
            "public android.content.SharedPreferences getPreferences(int)",

            // Uses android.view.SearchEvent
            "public final android.view.SearchEvent getSearchEvent()",

            // Uses android.window.SplashScreen
            "@NonNull public final android.window.SplashScreen getSplashScreen()",

            // Uses android.app.VoiceInteractor
            "public android.app.VoiceInteractor getVoiceInteractor()",

            // Uses android.view.Window
            "public android.view.Window getWindow()",

            // Uses android.view.WindowManager
            "public android.view.WindowManager getWindowManager()",

            // Uses android.database.Cursor
            "@Deprecated public final android.database.Cursor managedQuery(android.net.Uri, "
                    + "String[], String, String[], String)",
            "@Deprecated public void startManagingCursor(android.database.Cursor)",
            "@Deprecated public void stopManagingCursor(android.database.Cursor)",

            // Uses android.view.ActionMode
            "@CallSuper public void onActionModeFinished(android.view.ActionMode)",
            "@CallSuper public void onActionModeStarted(android.view.ActionMode)",
            "@Nullable public android.view.ActionMode onWindowStartingActionMode(android.view"
                    + ".ActionMode.Callback)",
            "@Nullable public android.view.ActionMode onWindowStartingActionMode(android.view"
                    + ".ActionMode.Callback, int)",
            "@Nullable public android.view.ActionMode startActionMode(android.view.ActionMode"
                    + ".Callback)",
            "@Nullable public android.view.ActionMode startActionMode(android.view.ActionMode"
                    + ".Callback, int)",

            // Uses android.view.MenuItem
            "public boolean onContextItemSelected(@NonNull android.view.MenuItem)",
            "public void onContextMenuClosed(@NonNull android.view.Menu)",
            "public boolean onMenuItemSelected(int, @NonNull android.view.MenuItem)",
            "public boolean onOptionsItemSelected(@NonNull android.view.MenuItem)",

            // Uses android.view.ContextMenu
            "public void onCreateContextMenu(android.view.ContextMenu, android.view.View, android"
                    + ".view.ContextMenu.ContextMenuInfo)",

            // Uses android.app.Dialog
            "@Deprecated protected android.app.Dialog onCreateDialog(int)",
            "@Deprecated @Nullable protected android.app.Dialog onCreateDialog(int, android.os"
                    + ".Bundle)",
            "@Deprecated protected void onPrepareDialog(int, android.app.Dialog)",
            "@Deprecated protected void onPrepareDialog(int, android.app.Dialog, android.os"
                    + ".Bundle)",

            // Uses android.app.TaskStackBuilder
            "public void onCreateNavigateUpTaskStack(android.app.TaskStackBuilder)",
            "public void onPrepareNavigateUpTaskStack(android.app.TaskStackBuilder)",

            // Uses android.view.Menu
            "public boolean onCreateOptionsMenu(android.view.Menu)",
            "public boolean onCreatePanelMenu(int, @NonNull android.view.Menu)",
            "public boolean onMenuOpened(int, @NonNull android.view.Menu)",
            "public void onOptionsMenuClosed(android.view.Menu)",
            "public void onPanelClosed(int, @NonNull android.view.Menu)",
            "public boolean onPrepareOptionsMenu(android.view.Menu)",

            // Uses android.util.Dumpable
            "public boolean addDumpable(@NonNull android.util.Dumpable)",
            "public boolean removeDumpable(@NonNull android.util.Dumpable)",

            // Uses android.graphics.Canvas
            "@Deprecated public boolean onCreateThumbnail(android.graphics.Bitmap, android"
                    + ".graphics.Canvas)",

            // Uses android.os.CancellationSignal
            "public void onGetDirectActions(@NonNull android.os.CancellationSignal, @NonNull java"
                    + ".util.function.Consumer<java.util.List<android.app.DirectAction>>)",
            "public void onPerformDirectAction(@NonNull String, @NonNull android.os.Bundle, "
                    + "@NonNull android.os.CancellationSignal, @NonNull java.util.function"
                    + ".Consumer<android.os.Bundle>)",

            // Uses android.view.SearchEvent
            "public boolean onSearchRequested(@Nullable android.view.SearchEvent)",

            // Uses android.app.Application.ActivityLifecycleCallbacks
            "public void registerActivityLifecycleCallbacks(@NonNull android.app.Application"
                    + ".ActivityLifecycleCallbacks)",
            "public void unregisterActivityLifecycleCallbacks(@NonNull android.app.Application"
                    + ".ActivityLifecycleCallbacks)",

            // Uses Runnable
            "public final void runOnUiThread(Runnable)",

            // Uses android.widget.Toolbar
            "public void setActionBar(@Nullable android.widget.Toolbar)",

            // Uses android.app.SharedElementCallback
            "public void setEnterSharedElementCallback(android.app.SharedElementCallback)",
            "public void setExitSharedElementCallback(android.app.SharedElementCallback)",

            // Uses Drawable
            "public final void setFeatureDrawable(int, android.graphics.drawable.Drawable)",

            // Uses android.media.session.MediaController
            "public final void setMediaController(android.media.session.MediaController)",

            // Context

            // Uses Object
            "public abstract Object getSystemService(@NonNull String)",

            // ContextThemeWrapper
            "protected void onApplyThemeResource(android.content.res.Resources.Theme, int, "
                    + "boolean)",

            // Context

            // Uses java.util.concurrent.Executor
            "public boolean bindIsolatedService(@NonNull @RequiresPermission android.content"
                    + ".Intent, int, @NonNull String, @NonNull java.util.concurrent.Executor, "
                    + "@NonNull android.content.ServiceConnection)",
            "public boolean bindService(@NonNull @RequiresPermission android.content.Intent, int,"
                    + " @NonNull java.util.concurrent.Executor, @NonNull android.content"
                    + ".ServiceConnection)",

            // Uses android.content.ServiceConnection
            "public abstract boolean bindService(@RequiresPermission android.content.Intent, "
                    + "@NonNull android.content.ServiceConnection, int)",
            "public boolean bindServiceAsUser(@NonNull @RequiresPermission android.content"
                    + ".Intent, @NonNull android.content.ServiceConnection, int, @NonNull android"
                    + ".os.UserHandle)",
            "public abstract void unbindService(@NonNull android.content.ServiceConnection)",
            "public void updateServiceGroup(@NonNull android.content.ServiceConnection, int, int)",

            // Uses android.content.Context
            "@NonNull public android.content.Context createAttributionContext(@Nullable String)",
            "public abstract android.content.Context createConfigurationContext(@NonNull android"
                    + ".content.res.Configuration)",
            "@NonNull public android.content.Context createContext(@NonNull android.content"
                    + ".ContextParams)",
            "public abstract android.content.Context createContextForSplit(String) throws android"
                    + ".content.pm.PackageManager.NameNotFoundException",
            "public abstract android.content.Context createDeviceProtectedStorageContext()",
            "@DisplayContext public abstract android.content.Context createDisplayContext"
                    + "(@NonNull android.view.Display)",
            "public abstract android.content.Context createPackageContext(String, int) throws "
                    + "android.content.pm.PackageManager.NameNotFoundException",
            "@NonNull @UiContext public android.content.Context createWindowContext(int, "
                    + "@Nullable android.os.Bundle)",
            "@NonNull @UiContext public android.content.Context createWindowContext(@NonNull "
                    + "android.view.Display, int, @Nullable android.os.Bundle)",
            "public abstract android.content.Context getApplicationContext()",

            // Uses android.content.res.AssetManager
            "public abstract android.content.res.AssetManager getAssets()",

            // Uses java.lang.ClassLoader
            "public abstract ClassLoader getClassLoader()",

            // Uses android.view.Display
            "@Nullable public android.view.Display getDisplay()",

            // Uses android.graphics.drawable.Drawable
            "@Nullable public final android.graphics.drawable.Drawable getDrawable(@DrawableRes "
                    + "int)",
            "@Deprecated public abstract android.graphics.drawable.Drawable getWallpaper()",
            "@Deprecated public abstract android.graphics.drawable.Drawable peekWallpaper()",

            // Uses java.util.concurrent.Executor
            "public java.util.concurrent.Executor getMainExecutor()",

            // Uses android.os.Looper
            "public abstract android.os.Looper getMainLooper()",

            // Uses android.content.pm.PackageManager
            "public abstract android.content.pm.PackageManager getPackageManager()",

            // Uses android.content.ContextParams
            "@Nullable public android.content.ContextParams getParams()",

            // Uses android.content.res.Resources
            "public abstract android.content.res.Resources getResources()",

            // Uses android.content.SharedPreferences
            "public abstract android.content.SharedPreferences getSharedPreferences(String, int)",

            // Uses android.content.res.Resources.Theme
            "public abstract android.content.res.Resources.Theme getTheme()",

            // Uses android.content.res.TypedArray
            "@NonNull public final android.content.res.TypedArray obtainStyledAttributes(@NonNull"
                    + " @StyleableRes int[])",
            "@NonNull public final android.content.res.TypedArray obtainStyledAttributes"
                    + "(@StyleRes int, @NonNull @StyleableRes int[]) throws android.content.res"
                    + ".Resources.NotFoundException",
            "@NonNull public final android.content.res.TypedArray obtainStyledAttributes"
                    + "(@Nullable android.util.AttributeSet, @NonNull @StyleableRes int[])",
            "@NonNull public final android.content.res.TypedArray obtainStyledAttributes"
                    + "(@Nullable android.util.AttributeSet, @NonNull @StyleableRes int[], "
                    + "@AttrRes int, @StyleRes int)",

            // Uses java.io.FileInputStream
            "public abstract java.io.FileInputStream openFileInput(String) throws java.io"
                    + ".FileNotFoundException",

            // Uses java.io.FileOutputStream
            "public abstract java.io.FileOutputStream openFileOutput(String, int) throws java.io"
                    + ".FileNotFoundException",

            // Uses android.database.sqlite.SQLiteDatabase
            "public abstract android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String, "
                    + "int, android.database.sqlite.SQLiteDatabase.CursorFactory)",
            "public abstract android.database.sqlite.SQLiteDatabase openOrCreateDatabase(String, "
                    + "int, android.database.sqlite.SQLiteDatabase.CursorFactory, @Nullable "
                    + "android.database.DatabaseErrorHandler)",

            // Uses android.content.ComponentCallbacks
            "public void registerComponentCallbacks(android.content.ComponentCallbacks)",
            "public void unregisterComponentCallbacks(android.content.ComponentCallbacks)",

            // Uses android.content.BroadcastReceiver
            "@Nullable public abstract android.content.Intent registerReceiver(@Nullable android"
                    + ".content.BroadcastReceiver, android.content.IntentFilter)",
            "@Nullable public abstract android.content.Intent registerReceiver(@Nullable android"
                    + ".content.BroadcastReceiver, android.content.IntentFilter, int)",
            "@Nullable public abstract android.content.Intent registerReceiver(android.content"
                    + ".BroadcastReceiver, android.content.IntentFilter, @Nullable String, "
                    + "@Nullable android.os.Handler)",
            "@Nullable public abstract android.content.Intent registerReceiver(android.content"
                    + ".BroadcastReceiver, android.content.IntentFilter, @Nullable String, "
                    + "@Nullable android.os.Handler, int)",
            "public abstract void sendOrderedBroadcast(@NonNull @RequiresPermission android"
                    + ".content.Intent, @Nullable String, @Nullable android.content"
                    + ".BroadcastReceiver, @Nullable android.os.Handler, int, @Nullable String, "
                    + "@Nullable android.os.Bundle)",
            "public void sendOrderedBroadcast(@NonNull android.content.Intent, @Nullable String, "
                    + "@Nullable String, @Nullable android.content.BroadcastReceiver, @Nullable "
                    + "android.os.Handler, int, @Nullable String, @Nullable android.os.Bundle)",
            "public abstract void sendOrderedBroadcastAsUser(@RequiresPermission android.content"
                    + ".Intent, android.os.UserHandle, @Nullable String, android.content"
                    + ".BroadcastReceiver, @Nullable android.os.Handler, int, @Nullable String, "
                    + "@Nullable android.os.Bundle)",
            "public abstract void sendStickyOrderedBroadcast(@RequiresPermission android.content"
                    + ".Intent, android.content.BroadcastReceiver, @Nullable android.os.Handler, "
                    + "int, @Nullable String, @Nullable android.os.Bundle)",
            "public abstract void sendStickyOrderedBroadcastAsUser(@RequiresPermission android"
                    + ".content.Intent, android.os.UserHandle, android.content.BroadcastReceiver,"
                    + " @Nullable android.os.Handler, int, @Nullable String, @Nullable android.os"
                    + ".Bundle)",
            "public abstract void unregisterReceiver(android.content.BroadcastReceiver)",

            // Uses java.io.InputStream
            "@Deprecated public abstract void setWallpaper(java.io.InputStream) throws java.io"
                    + ".IOException",


            // Doesn't make sense as it requires an actual Context
            "public abstract boolean moveDatabaseFrom(android.content.Context, String)",
            "public abstract boolean moveSharedPreferencesFrom(android.content.Context, String)",

            // ContentResolver

            // Uses Object
            "public static Object addStatusChangeListener(int, android.content.SyncStatusObserver)",
            "public static void removeStatusChangeListener(Object)",

            // Uses android.content.ContentProviderClient
            "@Nullable public final android.content.ContentProviderClient "
                    + "acquireContentProviderClient(@NonNull android.net.Uri)",
            "@Nullable public final android.content.ContentProviderClient "
                    + "acquireContentProviderClient(@NonNull String)",
            "@Nullable public final android.content.ContentProviderClient "
                    + "acquireUnstableContentProviderClient(@NonNull android.net.Uri)",
            "@Nullable public final android.content.ContentProviderClient "
                    + "acquireUnstableContentProviderClient(@NonNull String)",

            // Uses android.content.ContentResolver.MimeTypeInfo
            "@NonNull public final android.content.ContentResolver.MimeTypeInfo getTypeInfo"
                    + "(@NonNull String)",

            // Uses android.util.Size
            "@NonNull public android.graphics.Bitmap loadThumbnail(@NonNull android.net.Uri, "
                    + "@NonNull android.util.Size, @Nullable android.os.CancellationSignal) "
                    + "throws java.io.IOException",

            // Uses android.database.ContentObserver
            "public void notifyChange(@NonNull android.net.Uri, @Nullable android.database"
                    + ".ContentObserver)",
            "@Deprecated public void notifyChange(@NonNull android.net.Uri, @Nullable android"
                    + ".database.ContentObserver, boolean)",
            "public void notifyChange(@NonNull android.net.Uri, @Nullable android.database"
                    + ".ContentObserver, int)",
            "public void notifyChange(@NonNull java.util.Collection<android.net.Uri>, @Nullable "
                    + "android.database.ContentObserver, int)",
            "public final void registerContentObserver(@NonNull android.net.Uri, boolean, "
                    + "@NonNull android.database.ContentObserver)",
            "public final void unregisterContentObserver(@NonNull android.database"
                    + ".ContentObserver)",

            // Uses android.os.CancellationSignal
            "@Nullable public final android.content.res.AssetFileDescriptor openAssetFile"
                    + "(@NonNull android.net.Uri, @NonNull String, @Nullable android.os"
                    + ".CancellationSignal) throws java.io.FileNotFoundException",
            "@Nullable public final android.content.res.AssetFileDescriptor "
                    + "openAssetFileDescriptor(@NonNull android.net.Uri, @NonNull String, "
                    + "@Nullable android.os.CancellationSignal) throws java.io"
                    + ".FileNotFoundException",
            "@Nullable public final android.os.ParcelFileDescriptor openFile(@NonNull android.net"
                    + ".Uri, @NonNull String, @Nullable android.os.CancellationSignal) throws "
                    + "java.io.FileNotFoundException",
            "@Nullable public final android.os.ParcelFileDescriptor openFileDescriptor(@NonNull "
                    + "android.net.Uri, @NonNull String, @Nullable android.os.CancellationSignal)"
                    + " throws java.io.FileNotFoundException",
            "@Nullable public final android.content.res.AssetFileDescriptor openTypedAssetFile"
                    + "(@NonNull android.net.Uri, @NonNull String, @Nullable android.os.Bundle, "
                    + "@Nullable android.os.CancellationSignal) throws java.io"
                    + ".FileNotFoundException",
            "@Nullable public final android.content.res.AssetFileDescriptor "
                    + "openTypedAssetFileDescriptor(@NonNull android.net.Uri, @NonNull String, "
                    + "@Nullable android.os.Bundle, @Nullable android.os.CancellationSignal) "
                    + "throws java.io.FileNotFoundException",
            "public final boolean refresh(@NonNull android.net.Uri, @Nullable android.os.Bundle, "
                    + "@Nullable android.os.CancellationSignal)",

            // Uses java.io.InputStream
            "@Nullable public final java.io.InputStream openInputStream(@NonNull android.net.Uri)"
                    + " throws java.io.FileNotFoundException",

            // Uses java.io.OutputStream
            "@Nullable public final java.io.OutputStream openOutputStream(@NonNull android.net"
                    + ".Uri) throws java.io.FileNotFoundException",
            "@Nullable public final java.io.OutputStream openOutputStream(@NonNull android.net"
                    + ".Uri, @NonNull String) throws java.io.FileNotFoundException",

            // Uses android.database.Cursor
            "@Nullable public final android.database.Cursor query(@NonNull @RequiresPermission"
                    + ".Read android.net.Uri, @Nullable String[], @Nullable String, @Nullable "
                    + "String[], @Nullable String)",
            "@Nullable public final android.database.Cursor query(@NonNull @RequiresPermission"
                    + ".Read android.net.Uri, @Nullable String[], @Nullable String, @Nullable "
                    + "String[], @Nullable String, @Nullable android.os.CancellationSignal)",
            "@Nullable public final android.database.Cursor query(@NonNull @RequiresPermission"
                    + ".Read android.net.Uri, @Nullable String[], @Nullable android.os.Bundle, "
                    + "@Nullable android.os.CancellationSignal)",

            // Uses android.content.ContentResolver
            "@NonNull public static android.content.ContentResolver wrap(@NonNull android.content"
                    + ".ContentProvider)",
            "@NonNull public static android.content.ContentResolver wrap(@NonNull android.content"
                    + ".ContentProviderClient)",


            // KeyChain

            // Uses android.app.Activity
            "public static void choosePrivateKeyAlias(@NonNull android.app.Activity, @NonNull "
                    + "android.security.KeyChainAliasCallback, @Nullable String[], @Nullable java"
                    + ".security.Principal[], @Nullable String, int, @Nullable String)",
            "public static void choosePrivateKeyAlias(@NonNull android.app.Activity, @NonNull "
                    + "android.security.KeyChainAliasCallback, @Nullable String[], @Nullable java"
                    + ".security.Principal[], @Nullable android.net.Uri, @Nullable String)",

            // BluetoothAdapter

            // Uses android.bluetooth.BluetoothProfile
            "public void closeProfileProxy(int, android.bluetooth.BluetoothProfile)",
            "public boolean getProfileProxy(android.content.Context, android.bluetooth"
                    + ".BluetoothProfile.ServiceListener, int)",

            // Uses android.bluetooth.le.BluetoothLeAdvertiser
            "public android.bluetooth.le.BluetoothLeAdvertiser getBluetoothLeAdvertiser()",

            // Uses android.bluetooth.le.BluetoothLeScanner
            "public android.bluetooth.le.BluetoothLeScanner getBluetoothLeScanner()",

            // Uses android.bluetooth.BluetoothAdapter.LeScanCallback
            "public boolean startLeScan(android.bluetooth.BluetoothAdapter.LeScanCallback)",
            "public boolean startLeScan(java.util.UUID[], android.bluetooth.BluetoothAdapter.LeScanCallback)",
            "public void stopLeScan(android.bluetooth.BluetoothAdapter.LeScanCallback)",

            // Uses android.bluetooth.BluetoothServerSocket
            "public android.bluetooth.BluetoothServerSocket listenUsingInsecureL2capChannel() throws java.io.IOException",
            "public android.bluetooth.BluetoothServerSocket listenUsingInsecureRfcommWithServiceRecord(String, java.util.UUID) throws java.io.IOException",
            "public android.bluetooth.BluetoothServerSocket listenUsingL2capChannel() throws java.io.IOException",
            "public android.bluetooth.BluetoothServerSocket listenUsingRfcommWithServiceRecord(String, java.util.UUID) throws java.io.IOException",

            // BluetoothManager

            // Uses android.bluetooth.BluetoothGattServer
            "public android.bluetooth.BluetoothGattServer openGattServer(android.content.Context, android.bluetooth.BluetoothGattServerCallback)"
    );

    private static final ClassName NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteDevicePolicyManager");
    private static final ClassName NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteContentResolver");
    private static final ClassName NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableRemoteBluetoothAdapter");

    // TODO(b/205562849): These only support passing null, which is fine for existing tests but
    //  will be misleading
    private static final ClassName NULL_PARCELABLE_ACTIVITY_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableActivity");
    private static final ClassName NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableAccountManagerCallback");
    private static final ClassName NULL_HANDLER_CALLBACK_CLASSNAME =
            ClassName.get("com.android.bedstead.remoteframeworkclasses",
                    "NullParcelableHandler");

    private static final ClassName COMPONENT_NAME_CLASSNAME =
            ClassName.get("android.content", "ComponentName");

    private static final ClassName ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME =
            ClassName.get(
                    "com.android.bedstead.remoteframeworkclasses", "AccountManagerFutureWrapper");

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations,
            RoundEnvironment roundEnv) {
        if (!roundEnv.getElementsAnnotatedWith(RemoteFrameworkClasses.class).isEmpty()) {
            Set<MethodSignature> blocklistedMethodSignatures = BLOCKLISTED_METHODS.stream()
                    .map(m -> {
                        try {
                            return MethodSignature.forApiString(
                                    m, processingEnv.getTypeUtils(),
                                    processingEnv.getElementUtils());
                        } catch (Exception e) {
                            System.out.println("Error parsing blocklistedMethod " + m + ": " + e);
                            return null;
                        }
                    })
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());

            for (String systemService : FRAMEWORK_CLASSES) {
                TypeElement typeElement =
                        processingEnv.getElementUtils().getTypeElement(systemService);
                generateRemoteSystemService(
                        typeElement, blocklistedMethodSignatures, processingEnv.getElementUtils());
            }

            generateWrappers();
        }

        return true;
    }

    private void generateWrappers() {
        generateWrapper(NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_ACTIVITY_CLASSNAME);
        generateWrapper(NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME);
        generateWrapper(NULL_HANDLER_CALLBACK_CLASSNAME);
    }

    private void generateWrapper(ClassName className) {
        String contents = null;
        try {
            URL url = Processor.class.getResource(
                    "/parcelablewrappers/" + className.simpleName() + ".java.txt");
            contents = Resources.toString(url, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("Could not parse wrapper " + className, e);
        }

        JavaFileObject builderFile;
        try {
            builderFile = processingEnv.getFiler()
                    .createSourceFile(className.packageName() + "." + className.simpleName());
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write parcelablewrapper for " + className, e);
        }

        try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
            out.write(contents);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Could not write parcelablewrapper for " + className, e);
        }
    }

    private void generateRemoteSystemService(
            TypeElement frameworkClass,
            Set<MethodSignature> blocklistedMethodSignatures,
            Elements elements) {
        Set<ExecutableElement> methods = filterMethods(getMethods(frameworkClass,
                        processingEnv.getElementUtils()),
                Apis.forClass(frameworkClass.getQualifiedName().toString(),
                        processingEnv.getTypeUtils(), processingEnv.getElementUtils()), elements)
                .stream()
                .filter(t -> !blocklistedMethodSignatures.contains(
                        MethodSignature.forMethod(t, elements)))
                .collect(Collectors.toSet());

        generateFrameworkInterface(frameworkClass, methods);
        generateFrameworkImpl(frameworkClass, methods);

        if (frameworkClass.getSimpleName().contentEquals("DevicePolicyManager")) {
            // Special case, we need to support the .getParentProfileInstance method
            generateDpmParent(frameworkClass, methods);
        }
    }

    private void generateFrameworkInterface(
            TypeElement frameworkClass, Set<ExecutableElement> methods) {
        MethodSignature parentProfileInstanceSignature =
                MethodSignature.forApiString(PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getContentResolverSignature =
                MethodSignature.forApiString(GET_CONTENT_RESOLVER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getAdapterSignature =
                MethodSignature.forApiString(GET_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getDefaultAdapterSignature =
                MethodSignature.forApiString(GET_DEFAULT_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());

        Map<MethodSignature, ClassName> signatureReturnOverrides = new HashMap<>();
        signatureReturnOverrides.put(parentProfileInstanceSignature,
                ClassName.get("android.app.admin", "RemoteDevicePolicyManager"));
        signatureReturnOverrides.put(getContentResolverSignature,
                ClassName.get("android.content", "RemoteContentResolver"));
        signatureReturnOverrides.put(getAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));
        signatureReturnOverrides.put(getDefaultAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));

        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName className = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString());
        ClassName implClassName = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString() + "Impl");
        TypeSpec.Builder classBuilder =
                TypeSpec.interfaceBuilder(className)
                        .addModifiers(Modifier.PUBLIC);

        classBuilder.addJavadoc("Public, test, and system interface for {@link $T}.\n\n",
                frameworkClass);
        classBuilder.addJavadoc("<p>All methods are annotated {@link $T} for compatibility with the"
                + " Connected Apps SDK.\n\n", CrossUser.class);
        classBuilder.addJavadoc("<p>For implementation see {@link $T}.\n", implClassName);


        classBuilder.addAnnotation(AnnotationSpec.builder(CrossUser.class)
                .addMember("parcelableWrappers",
                        "{$T.class, $T.class, $T.class, $T.class, $T.class, $T.class}",
                        NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME,
                        NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME,
                        NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME,
                        NULL_PARCELABLE_ACTIVITY_CLASSNAME,
                        NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME,
                        NULL_HANDLER_CALLBACK_CLASSNAME)
                .addMember("futureWrappers", "$T.class",
                        ACCOUNT_MANAGE_FUTURE_WRAPPER_CLASSNAME)
                .build());

        for (ExecutableElement method : methods) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                            .addAnnotation(CrossUser.class);


            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());

            if (signatureReturnOverrides.containsKey(signature)) {
                methodBuilder.returns(signatureReturnOverrides.get(signature));
            }

            methodBuilder.addJavadoc("See {@link $T#$L}.",
                    ClassName.get(frameworkClass.asType()), method.getSimpleName());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            for (VariableElement param : method.getParameters()) {
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()),
                                param.getSimpleName().toString()).build();

                methodBuilder.addParameter(parameterSpec);
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private void generateDpmParent(TypeElement frameworkClass, Set<ExecutableElement> methods) {
        MethodSignature parentProfileInstanceSignature = MethodSignature.forApiString(
                PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                processingEnv.getElementUtils());
        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName className =
                ClassName.get(packageName, "Remote" + frameworkClass.getSimpleName() + "Parent");
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(className).addModifiers(Modifier.FINAL, Modifier.PUBLIC);

        classBuilder.addAnnotation(AnnotationSpec.builder(CrossUser.class)
                .addMember("parcelableWrappers",
                        "{$T.class, $T.class, $T.class, $T.class, $T.class, $T.class}",
                        NULL_PARCELABLE_REMOTE_DEVICE_POLICY_MANAGER_CLASSNAME,
                        NULL_PARCELABLE_REMOTE_CONTENT_RESOLVER_CLASSNAME,
                        NULL_PARCELABLE_REMOTE_BLUETOOTH_ADAPTER_CLASSNAME,
                        NULL_PARCELABLE_ACTIVITY_CLASSNAME,
                        NULL_PARCELABLE_ACCOUNT_MANAGER_CALLBACK_CLASSNAME,
                        NULL_HANDLER_CALLBACK_CLASSNAME)
                .build());

        classBuilder.addField(ClassName.get(frameworkClass),
                "mFrameworkClass", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(frameworkClass), "frameworkClass")
                        .addCode("mFrameworkClass = frameworkClass;")
                        .build()
        );

        for (ExecutableElement method : methods) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(CrossUser.class);

            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            methodBuilder.addParameter(COMPONENT_NAME_CLASSNAME, "profileOwnerComponentName");

            List<String> paramNames = new ArrayList<>();

            for (VariableElement param : method.getParameters()) {
                String paramName = param.getSimpleName().toString();
                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()), paramName).build();

                paramNames.add(paramName);

                methodBuilder.addParameter(parameterSpec);
            }

            if (signature.equals(parentProfileInstanceSignature)) {
                // Special case, we want to return a RemoteDevicePolicyManager instead
                methodBuilder.returns(ClassName.get(
                        "android.app.admin", "RemoteDevicePolicyManager"));
                methodBuilder.addStatement(
                        "mFrameworkClass.getParentProfileInstance(profileOwnerComponentName).$L"
                                + "($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
                methodBuilder.addStatement("throw new $T($S)", UnsupportedOperationException.class,
                        "TestApp does not support calling .getParentProfileInstance() on a parent"
                                + ".");
            } else if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "mFrameworkClass.getParentProfileInstance(profileOwnerComponentName).$L"
                                + "($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            } else {
                methodBuilder.addStatement(
                        "return mFrameworkClass.getParentProfileInstance"
                                + "(profileOwnerComponentName).$L($L)",
                        method.getSimpleName(), String.join(", ", paramNames));
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private void generateFrameworkImpl(TypeElement frameworkClass, Set<ExecutableElement> methods) {
        MethodSignature parentProfileInstanceSignature =
                MethodSignature.forApiString(PARENT_PROFILE_INSTANCE, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getContentResolverSignature =
                MethodSignature.forApiString(GET_CONTENT_RESOLVER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getAdapterSignature =
                MethodSignature.forApiString(GET_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());
        MethodSignature getDefaultAdapterSignature =
                MethodSignature.forApiString(GET_DEFAULT_ADAPTER, processingEnv.getTypeUtils(),
                        processingEnv.getElementUtils());

        Map<MethodSignature, ClassName> signatureReturnOverrides = new HashMap<>();
        signatureReturnOverrides.put(parentProfileInstanceSignature,
                ClassName.get("android.app.admin", "RemoteDevicePolicyManager"));
        signatureReturnOverrides.put(getContentResolverSignature,
                ClassName.get("android.content", "RemoteContentResolver"));
        signatureReturnOverrides.put(getAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));
        signatureReturnOverrides.put(getDefaultAdapterSignature,
                ClassName.get("android.bluetooth", "RemoteBluetoothAdapter"));

        String packageName = frameworkClass.getEnclosingElement().toString();
        ClassName interfaceClassName = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString());
        ClassName className = ClassName.get(packageName,
                "Remote" + frameworkClass.getSimpleName().toString() + "Impl");
        TypeSpec.Builder classBuilder =
                TypeSpec.classBuilder(
                                className)
                        .addSuperinterface(interfaceClassName)
                        .addModifiers(Modifier.PUBLIC);

        classBuilder.addField(ClassName.get(frameworkClass),
                "mFrameworkClass", Modifier.PRIVATE, Modifier.FINAL);

        classBuilder.addMethod(
                MethodSpec.constructorBuilder()
                        .addModifiers(Modifier.PUBLIC)
                        .addParameter(ClassName.get(frameworkClass), "frameworkClass")
                        .addCode("mFrameworkClass = frameworkClass;")
                        .build()
        );

        for (ExecutableElement method : methods) {
            MethodSpec.Builder methodBuilder =
                    MethodSpec.methodBuilder(method.getSimpleName().toString())
                            .returns(ClassName.get(method.getReturnType()))
                            .addModifiers(Modifier.PUBLIC)
                            .addAnnotation(Override.class);

            MethodSignature signature = MethodSignature.forMethod(method,
                    processingEnv.getElementUtils());

            for (TypeMirror thrownType : method.getThrownTypes()) {
                methodBuilder.addException(ClassName.get(thrownType));
            }

            List<String> paramNames = new ArrayList<>();

            for (VariableElement param : method.getParameters()) {
                String paramName = param.getSimpleName().toString();

                ParameterSpec parameterSpec =
                        ParameterSpec.builder(ClassName.get(param.asType()), paramName).build();

                paramNames.add(paramName);

                methodBuilder.addParameter(parameterSpec);
            }

            String frameworkClassName = "mFrameworkClass";

            if (method.getModifiers().contains(Modifier.STATIC)) {
                frameworkClassName = frameworkClass.getQualifiedName().toString();
            }

            if (signatureReturnOverrides.containsKey(signature)) {
                methodBuilder.returns(signatureReturnOverrides.get(signature));
                methodBuilder.addStatement(
                        "$TImpl ret = new $TImpl($L.$L($L))",
                        signatureReturnOverrides.get(signature),
                        signatureReturnOverrides.get(signature), frameworkClassName,
                        method.getSimpleName(), String.join(", ", paramNames));
                // We assume all replacements are null-only
                methodBuilder.addStatement("return null");
            } else if (method.getReturnType().getKind().equals(TypeKind.VOID)) {
                methodBuilder.addStatement(
                        "$L.$L($L)",
                        frameworkClassName, method.getSimpleName(), String.join(", ", paramNames));
            } else {
                methodBuilder.addStatement(
                        "return $L.$L($L)",
                        frameworkClassName, method.getSimpleName(), String.join(", ", paramNames));
            }

            classBuilder.addMethod(methodBuilder.build());
        }

        writeClassToFile(packageName, classBuilder.build());
    }

    private Set<ExecutableElement> filterMethods(
            Set<ExecutableElement> allMethods, Apis validApis, Elements elements) {
        Set<ExecutableElement> filteredMethods = new HashSet<>();

        for (ExecutableElement method : allMethods) {
            MethodSignature methodSignature = MethodSignature.forMethod(method, elements);
            if (validApis.methods().contains(methodSignature)) {
                if (method.getModifiers().contains(Modifier.PROTECTED)) {
                    System.out.println(methodSignature + " is protected. Dropping");
                } else {
                    filteredMethods.add(method);
                }
            } else {
                System.out.println("No matching public API for " + methodSignature);
            }
        }

        return filteredMethods;
    }

    private void writeClassToFile(String packageName, TypeSpec clazz) {
        String qualifiedClassName =
                packageName.isEmpty() ? clazz.name : packageName + "." + clazz.name;

        JavaFile javaFile = JavaFile.builder(packageName, clazz).build();
        try {
            JavaFileObject builderFile =
                    processingEnv.getFiler().createSourceFile(qualifiedClassName);
            try (PrintWriter out = new PrintWriter(builderFile.openWriter())) {
                javaFile.writeTo(out);
            }
        } catch (IOException e) {
            throw new IllegalStateException("Error writing " + qualifiedClassName + " to file", e);
        }
    }

    private Set<ExecutableElement> getMethods(TypeElement interfaceClass, Elements elements) {
        Map<String, ExecutableElement> methods = new HashMap<>();
        getMethods(methods, interfaceClass, elements);
        return new HashSet<>(methods.values());
    }

    private void getMethods(Map<String, ExecutableElement> methods, TypeElement interfaceClass,
            Elements elements) {
        interfaceClass.getEnclosedElements().stream()
                .filter(e -> e instanceof ExecutableElement)
                .map(e -> (ExecutableElement) e)
                .filter(e -> !methods.containsKey(e.getSimpleName().toString()))
                .filter(e -> e.getModifiers().contains(Modifier.PUBLIC))
                .forEach(e -> {
                    methods.put(methodHash(e), e);
                });

        interfaceClass.getInterfaces().stream()
                .map(m -> elements.getTypeElement(m.toString()))
                .forEach(m -> getMethods(methods, m, elements));
    }

    private String methodHash(ExecutableElement method) {
        return method.getSimpleName() + "(" + method.getParameters().stream()
                .map(p -> p.asType().toString()).collect(
                        Collectors.joining(",")) + ")";
    }
}
