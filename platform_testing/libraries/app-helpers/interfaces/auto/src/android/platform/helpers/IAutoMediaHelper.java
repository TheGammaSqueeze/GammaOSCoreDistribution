/*
 * Copyright (C) 2016 The Android Open Source Project
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

import java.util.List;

public interface IAutoMediaHelper extends IAppHelper, Scrollable {
    /**
     * Setup expectations: media app is open
     *
     * This method is used to play media.
     */
    void playMedia();

    /**
     * Setup expectations: on home screen.
     *
     * This method is used to play media from home screen.
     */
    void playPauseMediaFromHomeScreen();

    /**
     * Setup expectations: media app is open.
     *
     * This method is used to pause media.
     */
    void pauseMedia();

    /**
     * Setup expectations: media app is open.
     *
     * This method is used to select next track.
     */
    void clickNextTrack();

    /**
     * Setup expectations: on home screen.
     *
     * This method is used to select next track from home screen.
     */
    void clickNextTrackFromHomeScreen();

    /**
     * Setup expectations: media app is open.
     *
     * This method is used to select previous track.
     */
    void clickPreviousTrack();

    /**
     * Setup expectations: on home screen.
     *
     * This method is used to select previous track from home screen.
     */
    void clickPreviousTrackFromHomeScreen();

    /**
     * Setup expectations: media app is open.
     *
     * This method is used to shuffle tracks.
     */
    void clickShuffleAll();

    /**
     * Setup expectations: media app is open.
     *
     * This method is used to click on nth instance among the visible menu items
     *
     * @param - instance is the index of the menu item (starts from 0)
     */
    void clickMenuItem(int instance);

    /**
     * Setup expectations: media app is open.
     *
     * This method is used to open Folder Menu with menuOptions.
     * Example - openMenu->Folder->Mediafilename->trackName
     *           openMenuWith(Folder,mediafilename,trackName);
     *
     * @param - menuOptions used to pass multiple level of menu options in one go.
     */
    void openMenuWith(String... menuOptions);

    /**
     * Setup expectations: media app is open.
     *
     * This method is used to used to open mediafilename from now playing list.
     *
     *  @param - trackName - media to be played.
     */
    void openNowPlayingWith(String trackName);

    /**
     * Setup expectations: Media app is open.
     *
     * @return to get current playing track name.
     */
    String getMediaTrackName();

    /**
     * Setup expectations: on home screen.
     *
     * @return to get current playing track name from home screen.
     */
    String getMediaTrackNameFromHomeScreen();

    /**
     * Setup expectations: Media app is open. User navigates to sub-page of the Media Player
     *
     * This method is to go back to the Media Player main page from any sub-page.
     */
    void goBackToMediaHomePage();

    /**
     * This method is used to check if media is currently playing Returns true if media is playing
     * else returns false
     */
    boolean isPlaying();

    /**
     * Setup expectations: Media app is open.
     *
     * @return Media App Title
     */
    String getMediaAppTitle();

    /**
     * Setup expectations: Media app is open.
     * Opens the drop down menu in the Media Apps
     */
    void openMediaAppMenuItems();

    /**
     * Setup expectations: "Media apps" Grid is open.
     *
     * @param mediaAppsNames : List of media apps names
     * @return true if all app names in mediaAppsNames shows up in Media Apps Grid
     */
    boolean areMediaAppsPresent(List<String> mediaAppsNames);

    /**
     * Setup expectations: "Media apps" Grid is open.
     *
     * @param appName App name to open
     */
    void openApp(String appName);

    /**
     * Setup expectations: Media app is open.
     */
    void openMediaAppSettingsPage();

    /**
     * Setup expectations: Media app is open. Account not logged in.
     *
     * @return Error message for no user login
     */
    String getMediaAppUserNotLoggedInErrorMessage();

    /**
     * Setup expectations: In Media.
     *
     * <p>Scroll up on page.
     */
    boolean scrollUpOnePage();

    /**
     * Setup expectations: In Media.
     *
     * <p>Scroll down on page.
     */
    boolean scrollDownOnePage();
}
