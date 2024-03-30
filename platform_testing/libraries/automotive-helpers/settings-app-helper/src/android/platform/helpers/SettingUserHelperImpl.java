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

package android.platform.helpers;

import android.app.Instrumentation;
import android.os.SystemClock;
import android.support.test.uiautomator.By;
import android.support.test.uiautomator.UiObject2;
import android.util.Log;


/**
 * Implementation of {@link IAutoUserHelper} to support tests of account settings
 */
public class SettingUserHelperImpl extends AbstractAutoStandardAppHelper
    implements IAutoUserHelper {

    // Packages
    private static final String APP_NAME = AutoConfigConstants.SETTINGS;
    private static final String APP_CONFIG = AutoConfigConstants.PROFILE_ACCOUNT_SETTINGS;

    // Wait Time
    private static final int UI_RESPONSE_WAIT_MS = 10000;

    //constants
    private static final String TAG = "SettingUserHelperImpl";

    public SettingUserHelperImpl(Instrumentation instr) {
        super(instr);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getPackage() {
        return getApplicationConfig(AutoConfigConstants.SETTINGS_PACKAGE);
    }

    /**
     * {@inheritDoc}
     */
    // Add a new user
    @Override
    public void addUser() {
        clickbutton(AutoConfigConstants.ADD_PROFILE);
        clickbutton(AutoConfigConstants.OK);
        SystemClock.sleep(UI_RESPONSE_WAIT_MS);
    }

    // delete an existing user
    @Override
    public void deleteUser(String user) {
        if (isUserPresent(user)) {
            clickbutton(user);
            clickbutton(AutoConfigConstants.DELETE);
            clickbutton(AutoConfigConstants.DELETE);
            SystemClock.sleep(UI_RESPONSE_WAIT_MS);
        }
    }

    // delete self User
    @Override
    public void deleteCurrentUser() {
        clickbutton(AutoConfigConstants.DELETE_SELF);
        clickbutton(AutoConfigConstants.DELETE);
        SystemClock.sleep(UI_RESPONSE_WAIT_MS);
    }

    /**
     * {@inheritDoc}
     */
    // check if a user is present in the list of existing users
    @Override
    public boolean isUserPresent(String user) {
        UiObject2 addUserButton =
            scrollAndFindUiObject(
                getResourceFromConfig(APP_NAME, APP_CONFIG, AutoConfigConstants.ADD_PROFILE),
                getScrollScreenIndex());
        Log.v(
            TAG,
            String.format(
                "AddProfileButton = %s ; UI_Obj = %s",
                AutoConfigConstants.ADD_PROFILE, addUserButton));
        if (addUserButton == null) {
            clickbutton(AutoConfigConstants.MANAGE_OTHER_PROFILES);
            UiObject2 UserObject = scrollAndFindUiObject(By.text(user), getScrollScreenIndex());
            return UserObject != null;
        }
        return false;
    }

    // switch User from current user to given user
    @Override
    public void switchUser(String userFrom, String userTo) {
        goToQuickSettings();
        clickbutton(userFrom);
        clickbutton(userTo);
        SystemClock.sleep(UI_RESPONSE_WAIT_MS);
    }

    // add User via quick settings
    @Override
    public void addUserQuickSettings(String userFrom) {
        goToQuickSettings();
        clickbutton(userFrom);
        addUser();
    }

    // make an existing user admin
    @Override
    public void makeUserAdmin(String user) {
        if (isUserPresent(user)) {
            clickbutton(user);
            clickbutton(AutoConfigConstants.MAKE_ADMIN);
            clickbutton(AutoConfigConstants.MAKE_ADMIN_CONFIRM);
        }
    }


    // click an on-screen element if expected text for that element is present
    private void clickbutton(String button_text) {
        UiObject2 buttonObject =
            scrollAndFindUiObject(getResourceFromConfig(APP_NAME, APP_CONFIG, button_text),
                getScrollScreenIndex());
        if (buttonObject == null) {
            buttonObject = scrollAndFindUiObject(By.text(button_text), getScrollScreenIndex());
        }
        Log.v(
            TAG,
            String.format("button =  %s ; UI_Obj = %s", button_text, buttonObject));

        if (buttonObject == null) {
            throw new RuntimeException(
                String.format("Unable to find Object with text: %s", button_text));
        }
        clickAndWaitForIdleScreen(buttonObject);
        SystemClock.sleep(UI_RESPONSE_WAIT_MS);
    }

    // go to quick Settings for switching User
    private void goToQuickSettings() {
        clickbutton(AutoConfigConstants.TIME_PATTERN);
    }

    //set scroll index to 1
    private int getScrollScreenIndex() {
        int scrollScreenIndex = 0;
        if (hasSplitScreenSettingsUI()) {
            scrollScreenIndex = 1;
        }
        return scrollScreenIndex;
    }
}