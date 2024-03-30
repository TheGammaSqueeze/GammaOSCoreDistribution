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

package android.quicksettings.cts;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.AlertDialog;
import android.app.Dialog;
import android.os.Looper;
import android.service.quicksettings.Tile;
import android.service.quicksettings.TileService;

import com.android.systemui.qs.nano.QsTileState;

import org.junit.Test;

public class TileServiceTest extends BaseTileServiceTest {
    private final static String TAG = "TileServiceTest";

    @Test
    public void testCreateTileService() {
        final TileService tileService = new TileService();
    }

    @Test
    public void testListening() throws Exception {
        initializeAndListen();
    }

    @Test
    public void testListening_stopped() throws Exception {
        initializeAndListen();
        expandSettings(false);
        waitForListening(false);
    }

    @Test
    public void testLocked_deviceNotLocked() throws Exception {
        initializeAndListen();
        assertFalse(mTileService.isLocked());
    }

    @Test
    public void testSecure_deviceNotSecure() throws Exception {
        initializeAndListen();
        assertFalse(mTileService.isSecure());
    }

    @Test
    public void testTile_hasCorrectIcon() throws Exception {
        initializeAndListen();
        Tile tile = mTileService.getQsTile();
        assertEquals(TestTileService.ICON_ID, tile.getIcon().getResId());
    }

    @Test
    public void testTile_hasCorrectSubtitle() throws Exception {
        initializeAndListen();

        Tile tile = mTileService.getQsTile();
        tile.setSubtitle("test_subtitle");
        tile.updateTile();
        assertEquals("test_subtitle", tile.getSubtitle());
    }

    @Test
    public void testTile_hasCorrectStateDescription() throws Exception {
        initializeAndListen();

        Tile tile = mTileService.getQsTile();
        tile.setStateDescription("test_stateDescription");
        tile.updateTile();
        assertEquals("test_stateDescription", tile.getStateDescription());
    }

    @Test
    public void testShowDialog() throws Exception {
        Looper.prepare();
        Dialog dialog = new AlertDialog.Builder(mContext).create();
        initializeAndListen();
        clickTile(TestTileService.getComponentName().flattenToString());
        waitForClick();

        mTileService.showDialog(dialog);

        assertTrue(dialog.isShowing());
        dialog.dismiss();
    }

    @Test
    public void testUnlockAndRun_phoneIsUnlockedActivityIsRun() throws Exception {
        initializeAndListen();
        assertFalse(mTileService.isLocked());

        TestRunnable testRunnable = new TestRunnable();

        mTileService.unlockAndRun(testRunnable);
        Thread.sleep(100); // wait for activity to run
        waitForRun(testRunnable);
    }

    @Test
    public void testTileInDumpAndHasNonBooleanState() throws Exception {
        initializeAndListen();
        final QsTileState tileState = findTileState();
        assertNotNull(tileState);
        assertFalse(tileState.hasBooleanState());
    }

    @Test
    public void testTileInDumpAndHasCorrectState() throws Exception {
        initializeAndListen();
        CharSequence label = "test_label";
        CharSequence subtitle = "test_subtitle";

        Tile tile = mTileService.getQsTile();
        tile.setState(Tile.STATE_ACTIVE);
        tile.setLabel(label);
        tile.setSubtitle(subtitle);
        tile.updateTile();

        Thread.sleep(200);

        final QsTileState tileState = findTileState();
        assertNotNull(tileState);
        assertEquals(Tile.STATE_ACTIVE,  tileState.state);
        assertEquals(label,  tileState.getLabel());
        assertEquals(subtitle,  tileState.getSecondaryLabel());
    }

    private void clickTile(String componentName) throws Exception {
        executeShellCommand(" cmd statusbar click-tile " + componentName);
    }

    /**
     * Waits for the TileService to receive the clicked event. If it times out it fails the test.
     * @throws InterruptedException
     */
    private void waitForClick() throws InterruptedException {
        int ct = 0;
        while (!TestTileService.hasBeenClicked() && (ct++ < CHECK_RETRIES)) {
            Thread.sleep(CHECK_DELAY);
        }
        assertTrue(TestTileService.hasBeenClicked());
    }

    /**
     * Waits for the runnable to be run. If it times out it fails the test.
     * @throws InterruptedException
     */
    private void waitForRun(TestRunnable t) throws InterruptedException {
        int ct = 0;
        while (!t.hasRan && (ct++ < CHECK_RETRIES)) {
            Thread.sleep(CHECK_DELAY);
        }
        assertTrue(t.hasRan);
    }

    @Override
    protected void waitForListening(boolean state) throws InterruptedException {
        int ct = 0;
        while (TestTileService.isListening() != state && (ct++ < CHECK_RETRIES)) {
            Thread.sleep(CHECK_DELAY);
        }
        assertEquals(state, TestTileService.isListening());
    }

    /**
     * Waits for the TileService to be in the expected connected state. If it times out, it fails
     * the test
     * @param state desired connected state
     * @throws InterruptedException
     */
    @Override
    protected void waitForConnected(boolean state) throws InterruptedException {
        int ct = 0;
        while (TestTileService.isConnected() != state && (ct++ < CHECK_RETRIES)) {
            Thread.sleep(CHECK_DELAY);
        }
        assertEquals(state, TestTileService.isConnected());
    }

    @Override
    protected String getTag() {
        return TAG;
    }

    @Override
    protected String getComponentName() {
        return TestTileService.getComponentName().flattenToString();
    }

    @Override
    protected TileService getTileServiceInstance() {
        return TestTileService.getInstance();
    }

    class TestRunnable implements Runnable {
        boolean hasRan = false;

        @Override
        public void run() {
            hasRan = true;
        }
    }
}
