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

package android.photopicker.cts;

import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertMimeType;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertPersistedGrant;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertPickerUriFormat;
import static android.photopicker.cts.util.PhotoPickerAssertionsUtils.assertRedactedReadOnlyAccess;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createDNGVideos;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImages;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createVideos;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findPreviewAddOrSelectButton;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.runner.AndroidJUnit4;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Photo Picker Device only tests for common flows.
 */
@RunWith(AndroidJUnit4.class)
public class PhotoPickerTest extends PhotoPickerBaseTest {
    private List<Uri> mUriList = new ArrayList<>();

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();

        if (mActivity != null) {
            mActivity.finish();
        }
    }

    @Test
    public void testSingleSelect() throws Exception {
        final int itemCount = 1;
        createImages(itemCount, mContext.getUserId(), mUriList);

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final UiObject item = findItemList(itemCount).get(0);
        clickAndWait(item);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertPersistedGrant(uri, mContext.getContentResolver());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testSingleSelectForFavoritesAlbum() throws Exception {
        final int itemCount = 1;
        createImages(itemCount, mContext.getUserId(), mUriList, true);

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        UiObject albumsTab = mDevice.findObject(new UiSelector().text(
                "Albums"));
        clickAndWait(albumsTab);
        final UiObject album = findItemList(1).get(0);
        clickAndWait(album);

        final UiObject item = findItemList(itemCount).get(0);
        clickAndWait(item);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testLaunchPreviewMultipleForVideoAlbum() throws Exception {
        final int videoCount = 2;
        createVideos(videoCount, mContext.getUserId(), mUriList);

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.setType("video/*");
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        UiObject albumsTab = mDevice.findObject(new UiSelector().text(
                "Albums"));
        clickAndWait(albumsTab);
        final UiObject album = findItemList(1).get(0);
        clickAndWait(album);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();

        assertThat(itemCount).isEqualTo(videoCount);

        for (int i = 0; i < itemCount; i++) {
            clickAndWait(itemList.get(i));
        }

        clickAndWait(findViewSelectedButton());

        // Wait for playback to start. This is needed in some devices where playback
        // buffering -> ready state takes around 10s.
        final long playbackStartTimeout = 10000;
        (findPreviewVideoImageView()).waitUntilGone(playbackStartTimeout);
    }

    @Test
    public void testSingleSelectWithPreview() throws Exception {
        final int itemCount = 1;
        createImages(itemCount, mContext.getUserId(), mUriList);

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final UiObject item = findItemList(itemCount).get(0);
        item.longClick();
        mDevice.waitForIdle();

        final UiObject addButton = findPreviewAddOrSelectButton();
        assertThat(addButton.waitForExists(1000)).isTrue();
        clickAndWait(addButton);

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect_invalidParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit() + 1);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        final GetResultActivity.Result res = mActivity.getResult();
        assertThat(res.resultCode).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void testMultiSelect_invalidNegativeParam() throws Exception {
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, -1);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        final GetResultActivity.Result res = mActivity.getResult();
        assertThat(res.resultCode).isEqualTo(Activity.RESULT_CANCELED);
    }

    @Test
    public void testMultiSelect_returnsNotMoreThanMax() throws Exception {
        final int maxCount = 2;
        final int imageCount = maxCount + 1;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, maxCount);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        // Select maxCount + 1 item
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(itemList.get(i));
        }

        UiObject snackbarTextView = mDevice.findObject(new UiSelector().text(
                "Select up to 2 items"));
        assertWithMessage("Timed out while waiting for snackbar to appear").that(
                snackbarTextView.waitForExists(SHORT_TIMEOUT)).isTrue();

        assertWithMessage("Timed out waiting for snackbar to disappear").that(
                snackbarTextView.waitUntilGone(SHORT_TIMEOUT)).isTrue();

        clickAndWait(findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(maxCount);
    }

    @Test
    public void testDoesNotRespectExtraAllowMultiple() throws Exception {
        final int imageCount = 2;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        // Select 1 item
        clickAndWait(itemList.get(0));

        final Uri uri = mActivity.getResult().data.getData();
        assertPickerUriFormat(uri, mContext.getUserId());
        assertPersistedGrant(uri, mContext.getContentResolver());
        assertRedactedReadOnlyAccess(uri);
    }

    @Test
    public void testMultiSelect() throws Exception {
        final int imageCount = 4;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(itemList.get(i));
        }

        clickAndWait(findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_longPress() throws Exception {
        final int videoCount = 3;
        createDNGVideos(videoCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        intent.setType("video/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(videoCount);

        // Select one item from Photo grid
        clickAndWait(itemList.get(0));

        // Preview the item
        UiObject item = itemList.get(1);
        item.longClick();
        mDevice.waitForIdle();

        final UiObject addOrSelectButton = findPreviewAddOrSelectButton();
        assertWithMessage("Timed out waiting for AddOrSelectButton to appear")
                .that(addOrSelectButton.waitForExists(1000)).isTrue();

        // Select the item from Preview
        clickAndWait(addOrSelectButton);

        mDevice.pressBack();

        // Select one more item from Photo grid
        clickAndWait(itemList.get(2));

        clickAndWait(findAddButton());

        // Verify that all 3 items are returned
        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(3);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    public void testMultiSelect_preview() throws Exception {
        final int imageCount = 4;
        createImages(imageCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(itemList.get(i));
        }

        clickAndWait(findViewSelectedButton());

        // Swipe left three times
        swipeLeftAndWait();
        swipeLeftAndWait();
        swipeLeftAndWait();

        // Deselect one item
        clickAndWait(findPreviewSelectedCheckButton());

        // Return selected items
        clickAndWait(findPreviewAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount - 1);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
        }
    }

    @Test
    @Ignore("Re-enable once we find work around for b/226318844")
    public void testMultiSelect_previewVideoPlayPause() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        // Check Play/Pause in first video
        testVideoPreviewPlayPause();

        // Move to third video
        swipeLeftAndWait();
        swipeLeftAndWait();
        // Check Play/Pause in third video
        testVideoPreviewPlayPause();

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMultiSelect_previewVideoMuteButtonInitial() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 1);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Initial state of the mute Button
        // Check that initial state of mute button is mute, i.e., volume off
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Click Mute Button
        // Click to unmute the audio
        clickAndWait(muteButton);
        // Check that mute button state is unmute, i.e., it shows `volume up` icon
        assertMuteButtonState(muteButton, /* isMuted */ false);
        // Click on the muteButton and check that mute button status is now 'mute'
        clickAndWait(muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Click on the muteButton and check that mute button status is now unmute
        clickAndWait(muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // Test 3: Next preview resumes mute state
        // Go back and launch preview again
        mDevice.pressBack();
        clickAndWait(findViewSelectedButton());

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMultiSelect_previewVideoMuteButtonOnSwipe() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();
        final UiObject playerView = findPlayerView();

        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Test 1: Swipe resumes mute state, with state of the button is 'volume off' / 'mute'
        assertMuteButtonState(muteButton, /* isMuted */ true);
        // Swipe to next page and check that muteButton is in mute state.
        swipeLeftAndWait();
        // set-up and wait for player controls to be sticky
        setUpAndAssertStickyPlayerControls(playerView, playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ true);

        // Test 2: Swipe resumes mute state, with state of mute button 'volume up' / 'unmute'
        // Click muteButton again to check the next video resumes the previous video's mute state
        clickAndWait(muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);
        // check that next video resumed previous video's mute state
        swipeLeftAndWait();
        // Wait for 1s before checking Play/Pause button's visibility
        playPauseButton.waitForExists(1000);
        // check that player controls are visible
        assertPlayerControlsVisible(playPauseButton, muteButton);
        assertMuteButtonState(muteButton, /* isMuted */ false);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    @Ignore("Re-enable once we find work around for b/226318844")
    public void testMultiSelect_previewVideoControlsVisibility() throws Exception {
        launchPreviewMultipleWithVideos(/* videoCount */ 3);

        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();
        // Check that buttons auto hide.
        assertPlayerControlsAutoHide(playPauseButton, muteButton);

        final UiObject playerView = findPlayerView();
        // Click on StyledPlayerView to make the video controls visible
        clickAndWait(playerView);
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Wait for 1s and check that controls are still visible
        assertPlayerControlsDontAutoHide(playPauseButton, muteButton);

        // Click on StyledPlayerView and check that controls are no longer visible. Don't click in
        // the center, clicking in the center may pause the video.
        playerView.clickBottomRight();
        mDevice.waitForIdle();
        assertPlayerControlsHidden(playPauseButton, muteButton);

        // Swipe left and check that controls are not visible
        swipeLeftAndWait();
        assertPlayerControlsHidden(playPauseButton, muteButton);

        // Click on the StyledPlayerView and check that controls appear
        clickAndWait(playerView);
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Swipe left to check that controls are now visible on swipe
        swipeLeftAndWait();
        assertPlayerControlsVisible(playPauseButton, muteButton);

        // Check that the player controls are auto hidden in 1s
        assertPlayerControlsAutoHide(playPauseButton, muteButton);

        // We don't test the result of the picker here because the intention of the test is only to
        // test the video controls
    }

    @Test
    public void testMimeTypeFilter() throws Exception {
        final int videoCount = 2;
        createDNGVideos(videoCount, mContext.getUserId(), mUriList);
        final int imageCount = 1;
        createImages(imageCount, mContext.getUserId(), mUriList);

        final String mimeType = "video/dng";

        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        intent.setType(mimeType);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // find all items
        final List<UiObject> itemList = findItemList(-1);
        final int itemCount = itemList.size();
        assertThat(itemCount).isAtLeast(videoCount);
        for (int i = 0; i < itemCount; i++) {
            clickAndWait(itemList.get(i));
        }

        clickAndWait(findAddButton());

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            final Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(uri, mContext.getUserId());
            assertPersistedGrant(uri, mContext.getContentResolver());
            assertRedactedReadOnlyAccess(uri);
            assertMimeType(uri, mimeType);
        }
    }

    private void assertMuteButtonState(UiObject muteButton, boolean isMuted)
            throws UiObjectNotFoundException {
        // We use content description to assert the state of the mute button, there is no other way
        // to test this.
        final String expectedContentDescription = isMuted ? "Unmute video" : "Mute video";
        final String assertMessage =
                "Expected mute button content description to be " + expectedContentDescription;
        assertWithMessage(assertMessage).that(muteButton.getContentDescription())
                .isEqualTo(expectedContentDescription);
    }

    private void testVideoPreviewPlayPause() throws Exception {
        final UiObject playPauseButton = findPlayPauseButton();
        final UiObject muteButton = findMuteButton();

        // Wait for buttons to auto hide.
        assertPlayerControlsAutoHide(playPauseButton, muteButton);

        // Click on StyledPlayerView to make the video controls visible
        clickAndWait(findPlayerView());

        // PlayPause button is now pause button, click the button to pause the video.
        clickAndWait(playPauseButton);

        // Wait for 1s and check that play button is not auto hidden
        assertPlayerControlsDontAutoHide(playPauseButton, muteButton);

        // PlayPause button is now play button, click the button to play the video.
        clickAndWait(playPauseButton);
        // Check that pause button auto-hides in 1s.
        assertPlayerControlsAutoHide(playPauseButton, muteButton);
    }

    private void launchPreviewMultipleWithVideos(int videoCount) throws  Exception {
        createVideos(videoCount, mContext.getUserId(), mUriList);
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        intent.setType("video/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        final List<UiObject> itemList = findItemList(videoCount);
        final int itemCount = itemList.size();

        assertThat(itemCount).isEqualTo(videoCount);

        for (int i = 0; i < itemCount; i++) {
            clickAndWait(itemList.get(i));
        }

        clickAndWait(findViewSelectedButton());

        // Wait for playback to start. This is needed in some devices where playback
        // buffering -> ready state takes around 10s.
        final long playbackStartTimeout = 10000;
        (findPreviewVideoImageView()).waitUntilGone(playbackStartTimeout);

        // Wait for Binder calls to complete and device to be idle
        MediaStore.waitForIdle(mContext.getContentResolver());
        mDevice.waitForIdle();
    }

    private void setUpAndAssertStickyPlayerControls(UiObject playerView, UiObject playPauseButton,
            UiObject muteButton) throws Exception {
        // Wait for 1s for player view to exist
        playerView.waitForExists(1000);
        // Wait for 1s or Play/Pause button to hide
        playPauseButton.waitUntilGone(1000);
        // Click on StyledPlayerView to make the video controls visible
        clickAndWait(playerView);
        assertPlayerControlsVisible(playPauseButton, muteButton);
    }

    private void assertPlayerControlsVisible(UiObject playPauseButton, UiObject muteButton) {
        assertVisible(playPauseButton, "Expected play/pause button to be visible");
        assertVisible(muteButton, "Expected mute button to be visible");
    }

    private void assertPlayerControlsHidden(UiObject playPauseButton, UiObject muteButton) {
        assertHidden(playPauseButton, "Expected play/pause button to be hidden");
        assertHidden(muteButton, "Expected mute button to be hidden");
    }

    private void assertPlayerControlsAutoHide(UiObject playPauseButton, UiObject muteButton) {
        // These buttons should auto hide in 1 second after the video playback start. Since we can't
        // identify the video playback start time, we wait for 2 seconds instead.
        assertWithMessage("Expected play/pause button to auto hide in 2s")
                .that(playPauseButton.waitUntilGone(2000)).isTrue();
        assertHidden(muteButton, "Expected mute button to hide after 2s");
    }

    private void assertPlayerControlsDontAutoHide(UiObject playPauseButton, UiObject muteButton) {
        assertWithMessage("Expected play/pause button to not auto hide in 1s")
                .that(playPauseButton.waitUntilGone(1100)).isFalse();
        assertVisible(muteButton, "Expected mute button to be still visible after 1s");
    }

    private void assertVisible(UiObject button, String message) {
        assertWithMessage(message).that(button.exists()).isTrue();
    }

    private void assertHidden(UiObject button, String message) {
        assertWithMessage(message).that(button.exists()).isFalse();
    }

    private static UiObject findViewSelectedButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/button_view_selected"));
    }

    private static UiObject findPreviewSelectedCheckButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_selected_check_button"));
    }


    private static UiObject findPlayerView() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_player_view"));
    }

    private static UiObject findMuteButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_mute"));
    }

    private static UiObject findPlayPauseButton() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/exo_play_pause"));
    }

    private static UiObject findPreviewVideoImageView() {
        return new UiObject(new UiSelector().resourceIdMatches(
                REGEX_PACKAGE_NAME + ":id/preview_video_image"));
    }

    private void clickAndWait(UiObject uiObject) throws Exception {
        uiObject.click();
        mDevice.waitForIdle();
    }

    private void swipeLeftAndWait() {
        final int width = mDevice.getDisplayWidth();
        final int height = mDevice.getDisplayHeight();
        mDevice.swipe(15 * width / 20, height / 2, width / 20, height / 2, 10);
        mDevice.waitForIdle();
    }
}
