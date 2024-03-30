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

package com.android.systemui.go;

import com.android.keyguard.KeyguardBiometricLockoutLogger;
import com.android.systemui.CoreStartable;
import com.android.systemui.LatencyTester;
import com.android.systemui.ScreenDecorations;
import com.android.systemui.SliceBroadcastRelayHandler;
import com.android.systemui.accessibility.SystemActions;
import com.android.systemui.accessibility.WindowMagnification;
import com.android.systemui.biometrics.AuthController;
import com.android.systemui.clipboardoverlay.ClipboardListener;
import com.android.systemui.dagger.qualifiers.PerUser;
import com.android.systemui.globalactions.GlobalActionsComponent;
import com.android.systemui.keyboard.KeyboardUI;
import com.android.systemui.keyguard.KeyguardViewMediator;
import com.android.systemui.log.SessionTracker;
import com.android.systemui.media.RingtonePlayer;
import com.android.systemui.power.PowerUI;
import com.android.systemui.recents.Recents;
import com.android.systemui.shortcut.ShortcutKeyDispatcher;
import com.android.systemui.statusbar.notification.InstantAppNotifier;
import com.android.systemui.theme.ThemeOverlayController;
import com.android.systemui.toast.ToastUI;
import com.android.systemui.usb.StorageNotification;
import com.android.systemui.util.NotificationChannels;
import com.android.systemui.volume.VolumeUI;
import com.android.systemui.wmshell.WMShell;

import dagger.Binds;
import dagger.Module;
import dagger.multibindings.ClassKey;
import dagger.multibindings.IntoMap;

/**
 * Collection of {@link CoreStartable}s that should be run on Android Go. This is forked from
 * {@link com.android.systemui.dagger.SystemUICoreStartableModule}.
 */
@Module
abstract class SystemUIGoCoreStartableModule {
    /** Inject into AuthController. */
    @Binds
    @IntoMap
    @ClassKey(AuthController.class)
    abstract CoreStartable bindAuthController(AuthController authController);

    /** Inject into ClipboardListener. */
    @Binds
    @IntoMap
    @ClassKey(ClipboardListener.class)
    abstract CoreStartable bindClipboardListener(ClipboardListener clipboardListener);

    /** Inject into GlobalActionsComponent. */
    @Binds
    @IntoMap
    @ClassKey(GlobalActionsComponent.class)
    abstract CoreStartable bindGlobalActionsComponent(
            GlobalActionsComponent globalActionsComponent);

    /** Inject into InstantAppNotifier. */
    @Binds
    @IntoMap
    @ClassKey(InstantAppNotifier.class)
    abstract CoreStartable bindInstantAppNotifier(InstantAppNotifier instantAppNotifier);

    /** Inject into KeyboardUI. */
    @Binds
    @IntoMap
    @ClassKey(KeyboardUI.class)
    abstract CoreStartable bindKeyboardUI(KeyboardUI keyboardUI);

    /** Inject into KeyguardBiometricLockoutLogger */
    @Binds
    @IntoMap
    @ClassKey(KeyguardBiometricLockoutLogger.class)
    abstract CoreStartable bindKeyguardBiometricLockoutLogger(
            KeyguardBiometricLockoutLogger keyguardBiometricLockoutLogger);

    /** Inject into KeyguardViewMediator. */
    @Binds
    @IntoMap
    @ClassKey(KeyguardViewMediator.class)
    abstract CoreStartable bindKeyguardViewMediator(KeyguardViewMediator keyguardViewMediator);

    /** Inject into LatencyTests. */
    @Binds
    @IntoMap
    @ClassKey(LatencyTester.class)
    abstract CoreStartable bindLatencyTester(LatencyTester latencyTester);

    /** Inject into NotificationChannels. */
    @Binds
    @IntoMap
    @ClassKey(NotificationChannels.class)
    @PerUser
    abstract CoreStartable bindNotificationChannels(NotificationChannels notificationChannels);

    /** Inject into PowerUI. */
    @Binds
    @IntoMap
    @ClassKey(PowerUI.class)
    abstract CoreStartable bindPowerUI(PowerUI powerUI);

    /** Inject into Recents. */
    @Binds
    @IntoMap
    @ClassKey(Recents.class)
    abstract CoreStartable bindRecents(Recents recents);

    /** Inject into RingtonePlayer. */
    @Binds
    @IntoMap
    @ClassKey(RingtonePlayer.class)
    abstract CoreStartable bind(RingtonePlayer ringtonePlayer);

    /** Inject into ScreenDecorations. */
    @Binds
    @IntoMap
    @ClassKey(ScreenDecorations.class)
    abstract CoreStartable bindScreenDecorations(ScreenDecorations screenDecorations);

    /** Inject into SessionTracker. */
    @Binds
    @IntoMap
    @ClassKey(SessionTracker.class)
    abstract CoreStartable bindSessionTracker(SessionTracker service);

    /** Inject into ShortcutKeyDispatcher. */
    @Binds
    @IntoMap
    @ClassKey(ShortcutKeyDispatcher.class)
    abstract CoreStartable bindShortcutKeyDispatcher(ShortcutKeyDispatcher shortcutKeyDispatcher);

    /** Inject into SliceBroadcastRelayHandler. */
    @Binds
    @IntoMap
    @ClassKey(SliceBroadcastRelayHandler.class)
    abstract CoreStartable bindSliceBroadcastRelayHandler(
            SliceBroadcastRelayHandler sliceBroadcastRelayHandler);

    /** Inject into StorageNotification. */
    @Binds
    @IntoMap
    @ClassKey(StorageNotification.class)
    abstract CoreStartable bindStorageNotification(StorageNotification storageNotification);

    /** Inject into SystemActions. */
    @Binds
    @IntoMap
    @ClassKey(SystemActions.class)
    abstract CoreStartable bindSystemActions(SystemActions systemActions);

    /** Inject into ThemeOverlayController. */
    @Binds
    @IntoMap
    @ClassKey(ThemeOverlayController.class)
    abstract CoreStartable bindThemeOverlayController(
            ThemeOverlayController themeOverlayController);

    /** Inject into ToastUI. */
    @Binds
    @IntoMap
    @ClassKey(ToastUI.class)
    abstract CoreStartable bindToastUI(ToastUI toastUi);

    /** Inject into VolumeUI. */
    @Binds
    @IntoMap
    @ClassKey(VolumeUI.class)
    abstract CoreStartable bindVolumeUI(VolumeUI volumeUi);

    /** Inject into WindowMagnification. */
    @Binds
    @IntoMap
    @ClassKey(WindowMagnification.class)
    abstract CoreStartable bindWindowMagnification(WindowMagnification windowMagnification);

    /** Inject into WMShell. */
    @Binds
    @IntoMap
    @ClassKey(WMShell.class)
    abstract CoreStartable bindWMShell(WMShell wmShell);
}
