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

package android.media.tv.interactive.cts;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertNotNull;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.media.tv.AdRequest;
import android.media.tv.AdResponse;
import android.media.tv.AitInfo;
import android.media.tv.BroadcastInfoRequest;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.CommandRequest;
import android.media.tv.CommandResponse;
import android.media.tv.DsmccRequest;
import android.media.tv.DsmccResponse;
import android.media.tv.PesRequest;
import android.media.tv.PesResponse;
import android.media.tv.SectionRequest;
import android.media.tv.SectionResponse;
import android.media.tv.StreamEventRequest;
import android.media.tv.StreamEventResponse;
import android.media.tv.TableRequest;
import android.media.tv.TableResponse;
import android.media.tv.TimelineRequest;
import android.media.tv.TimelineResponse;
import android.media.tv.TsRequest;
import android.media.tv.TsResponse;
import android.media.tv.TvContract;
import android.media.tv.TvInputInfo;
import android.media.tv.TvInputManager;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.media.tv.interactive.TvInteractiveAppManager;
import android.media.tv.interactive.TvInteractiveAppServiceInfo;
import android.media.tv.interactive.TvInteractiveAppView;
import android.net.Uri;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.ParcelFileDescriptor;
import android.tv.cts.R;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.SurfaceView;
import android.view.View;

import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.RequiredFeatureRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Test {@link android.media.tv.interactive.TvInteractiveAppService}.
 */
@RunWith(AndroidJUnit4.class)
public class TvInteractiveAppServiceTest {
    private static final long TIME_OUT_MS = 20000L;
    private static final Uri CHANNEL_0 = TvContract.buildChannelUri(0);

    private Instrumentation mInstrumentation;
    private ActivityScenario<TvInteractiveAppViewStubActivity> mActivityScenario;
    private TvInteractiveAppViewStubActivity mActivity;
    private TvInteractiveAppView mTvIAppView;

    private TvView mTvView;
    private TvInteractiveAppManager mManager;
    private TvInteractiveAppServiceInfo mStubInfo;
    private StubTvInteractiveAppService.StubSessionImpl mSession;
    private TvInputManager mTvInputManager;
    private TvInputInfo mTvInputInfo;
    private StubTvInputService2.StubSessionImpl2 mInputSession;

    @Rule
    public RequiredFeatureRule featureRule = new RequiredFeatureRule(
            PackageManager.FEATURE_LIVE_TV);

    private final MockCallback mCallback = new MockCallback();
    private final MockTvInputCallback mTvInputCallback = new MockTvInputCallback();

    public static class MockCallback extends TvInteractiveAppView.TvInteractiveAppCallback {
        private int mRequestCurrentChannelUriCount = 0;
        private int mStateChangedCount = 0;
        private int mBiIAppCreatedCount = 0;
        private int mRequestSigningCount = 0;

        private String mIAppServiceId = null;
        private Integer mState = null;
        private Integer mErr = null;
        private Uri mBiIAppUri = null;
        private String mBiIAppId = null;

        private void resetValues() {
            mRequestCurrentChannelUriCount = 0;
            mStateChangedCount = 0;
            mBiIAppCreatedCount = 0;
            mRequestSigningCount = 0;

            mIAppServiceId = null;
            mState = null;
            mErr = null;
            mBiIAppUri = null;
            mBiIAppId = null;
        }

        @Override
        public void onRequestCurrentChannelUri(String iAppServiceId) {
            super.onRequestCurrentChannelUri(iAppServiceId);
            mRequestCurrentChannelUriCount++;
        }

        @Override
        public void onRequestSigning(String iAppServiceId, String signingId,
                String algorithm, String alias, byte[] data) {
            super.onRequestSigning(iAppServiceId, signingId, algorithm, alias, data);
            mRequestSigningCount++;
        }

        @Override
        public void onStateChanged(String iAppServiceId, int state, int err) {
            super.onStateChanged(iAppServiceId, state, err);
            mStateChangedCount++;
            mIAppServiceId = iAppServiceId;
            mState = state;
            mErr = err;
        }

        @Override
        public void onBiInteractiveAppCreated(String iAppServiceId, Uri biIAppUri,
                String biIAppId) {
            super.onBiInteractiveAppCreated(iAppServiceId, biIAppUri, biIAppId);
            mBiIAppCreatedCount++;
            mIAppServiceId = iAppServiceId;
            mBiIAppUri = biIAppUri;
            mBiIAppId = biIAppId;
        }

        @Override
        public void onPlaybackCommandRequest(String id, String type, Bundle bundle) {
            super.onPlaybackCommandRequest(id, type, bundle);
        }

        @Override
        public void onRequestCurrentChannelLcn(String id) {
            super.onRequestCurrentChannelLcn(id);
        }

        @Override
        public void onRequestCurrentTvInputId(String id) {
            super.onRequestCurrentTvInputId(id);
        }

        @Override
        public void onRequestStreamVolume(String id) {
            super.onRequestStreamVolume(id);
        }

        @Override
        public void onRequestTrackInfoList(String id) {
            super.onRequestTrackInfoList(id);
        }

        @Override
        public void onSetVideoBounds(String id, Rect rect) {
            super.onSetVideoBounds(id, rect);
        }

        @Override
        public void onTeletextAppStateChanged(String id, int state) {
            super.onTeletextAppStateChanged(id, state);
        }

    }

    public static class MockTvInputCallback extends TvView.TvInputCallback {
        private int mAitInfoUpdatedCount = 0;

        private AitInfo mAitInfo = null;

        private void resetValues() {
            mAitInfoUpdatedCount = 0;

            mAitInfo = null;
        }

        public void onAitInfoUpdated(String inputId, AitInfo aitInfo) {
            super.onAitInfoUpdated(inputId, aitInfo);
            mAitInfoUpdatedCount++;
            mAitInfo = aitInfo;
        }
        public void onSignalStrengthUpdated(String inputId, int strength) {
            super.onSignalStrengthUpdated(inputId, strength);
        }
        public void onTuned(String inputId, Uri uri) {
            super.onTuned(inputId, uri);
        }
    }

    private TvInteractiveAppView findTvInteractiveAppViewById(int id) {
        return (TvInteractiveAppView) mActivity.findViewById(id);
    }

    private TvView findTvViewById(int id) {
        return (TvView) mActivity.findViewById(id);
    }

    private void runTestOnUiThread(final Runnable r) throws Throwable {
        final Throwable[] exceptions = new Throwable[1];
        mInstrumentation.runOnMainSync(new Runnable() {
            public void run() {
                try {
                    r.run();
                } catch (Throwable throwable) {
                    exceptions[0] = throwable;
                }
            }
        });
        if (exceptions[0] != null) {
            throw exceptions[0];
        }
    }

    private void linkTvView() {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvView.setCallback(mTvInputCallback);
        mTvView.tune(mTvInputInfo.getId(), CHANNEL_0);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvView.getInputSession() != null);
        mInputSession = StubTvInputService2.sStubSessionImpl2;
        assertNotNull(mInputSession);
        mInputSession.resetValues();

        mTvIAppView.setTvView(mTvView);
        mTvView.setInteractiveAppNotificationEnabled(true);
    }

    private Executor getExecutor() {
        return Runnable::run;
    }

    private static Bundle createTestBundle() {
        Bundle b = new Bundle();
        b.putString("stringKey", new String("Test String"));
        return b;
    }

    private static Uri createTestUri() {
        return Uri.parse("content://com.example/");
    }

    @Before
    public void setUp() throws Throwable {
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
        Intent intent = new Intent(Intent.ACTION_MAIN);
        intent.setClass(
                mInstrumentation.getTargetContext(), TvInteractiveAppViewStubActivity.class);

        // DO NOT use ActivityScenario.launch(Class), which can cause ActivityNotFoundException
        // related to BootstrapActivity.
        mActivityScenario = ActivityScenario.launch(intent);
        ConditionVariable activityReferenceObtained = new ConditionVariable();
        mActivityScenario.onActivity(activity -> {
            mActivity = activity;
            activityReferenceObtained.open();
        });
        activityReferenceObtained.block(TIME_OUT_MS);

        assertNotNull("Failed to acquire activity reference.", mActivity);
        mTvIAppView = findTvInteractiveAppViewById(R.id.tviappview);
        assertNotNull("Failed to find TvInteractiveAppView.", mTvIAppView);
        mTvView = findTvViewById(R.id.tviapp_tvview);
        assertNotNull("Failed to find TvView.", mTvView);

        mManager = (TvInteractiveAppManager) mActivity.getSystemService(
                Context.TV_INTERACTIVE_APP_SERVICE);
        assertNotNull("Failed to get TvInteractiveAppManager.", mManager);

        for (TvInteractiveAppServiceInfo info : mManager.getTvInteractiveAppServiceList()) {
            if (info.getServiceInfo().name.equals(StubTvInteractiveAppService.class.getName())) {
                mStubInfo = info;
            }
        }
        assertNotNull(mStubInfo);
        mTvIAppView.setCallback(getExecutor(), mCallback);
        mTvIAppView.setOnUnhandledInputEventListener(getExecutor(),
                new TvInteractiveAppView.OnUnhandledInputEventListener() {
                    @Override
                    public boolean onUnhandledInputEvent(InputEvent event) {
                        return true;
                    }
                });
        mTvIAppView.prepareInteractiveApp(mStubInfo.getId(), 1);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvIAppView.getInteractiveAppSession() != null);
        mSession = StubTvInteractiveAppService.sSession;

        mTvInputManager = (TvInputManager) mActivity.getSystemService(Context.TV_INPUT_SERVICE);
        assertNotNull("Failed to get TvInputManager.", mTvInputManager);

        for (TvInputInfo info : mTvInputManager.getTvInputList()) {
            if (info.getServiceInfo().name.equals(StubTvInputService2.class.getName())) {
                mTvInputInfo = info;
            }
        }
        assertNotNull(mTvInputInfo);
    }

    @After
    public void tearDown() throws Throwable {
        runTestOnUiThread(new Runnable() {
            public void run() {
                mTvIAppView.reset();
                mTvView.reset();
            }
        });
        mInstrumentation.waitForIdleSync();
        mActivity = null;
        mActivityScenario.close();
    }

    @Test
    public void testRequestCurrentChannelUri() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        mSession.requestCurrentChannelUri();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestCurrentChannelUriCount > 0);

        assertThat(mCallback.mRequestCurrentChannelUriCount).isEqualTo(1);
    }

    @Test
    public void testRequestSigning() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        mSession.requestSigning("id", "algo", "alias", new byte[1]);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mRequestSigningCount > 0);

        assertThat(mCallback.mRequestSigningCount).isEqualTo(1);
        // TODO: check values
    }

    @Test
    public void testSendSigningResult() {
        assertNotNull(mSession);
        mSession.resetValues();

        mTvIAppView.sendSigningResult("id", new byte[1]);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mSigningResultCount > 0);

        assertThat(mSession.mSigningResultCount).isEqualTo(1);
        // TODO: check values
    }

    @Test
    public void testNotifyError() {
        assertNotNull(mSession);
        mSession.resetValues();

        mTvIAppView.notifyError("msg", new Bundle());
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mErrorCount > 0);

        assertThat(mSession.mErrorCount).isEqualTo(1);
        // TODO: check values
    }

    @Test
    public void testSetSurface() throws Throwable {
        assertNotNull(mSession);

        assertThat(mSession.mSetSurfaceCount).isEqualTo(1);
    }

    @Test
    public void testLayoutSurface() throws Throwable {
        assertNotNull(mSession);

        final int left = 10;
        final int top = 20;
        final int right = 30;
        final int bottom = 40;

        mSession.layoutSurface(left, top, right, bottom);

        new PollingCheck(TIME_OUT_MS) {
            @Override
            protected boolean check() {
                int childCount = mTvIAppView.getChildCount();
                for (int i = 0; i < childCount; ++i) {
                    View v = mTvIAppView.getChildAt(i);
                    if (v instanceof SurfaceView) {
                        return v.getLeft() == left
                            && v.getTop() == top
                            && v.getRight() == right
                            && v.getBottom() == bottom;
                    }
                }
                return false;
            }
        }.run();
        assertThat(mSession.mSurfaceChangedCount > 0).isTrue();
    }

    @Test
    public void testSessionStateChanged() throws Throwable {
        assertNotNull(mSession);
        mCallback.resetValues();
        mSession.notifySessionStateChanged(
                TvInteractiveAppManager.INTERACTIVE_APP_STATE_ERROR,
                TvInteractiveAppManager.ERROR_UNKNOWN);
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mStateChangedCount > 0);

        assertThat(mCallback.mStateChangedCount).isEqualTo(1);
        assertThat(mCallback.mIAppServiceId).isEqualTo(mStubInfo.getId());
        assertThat(mCallback.mState)
                .isEqualTo(TvInteractiveAppManager.INTERACTIVE_APP_STATE_ERROR);
        assertThat(mCallback.mErr).isEqualTo(TvInteractiveAppManager.ERROR_UNKNOWN);
    }

    @Test
    public void testStartStopInteractiveApp() throws Throwable {
        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.startInteractiveApp();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mStartInteractiveAppCount > 0);
        assertThat(mSession.mStartInteractiveAppCount).isEqualTo(1);

        assertNotNull(mSession);
        mSession.resetValues();
        mTvIAppView.stopInteractiveApp();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mStopInteractiveAppCount > 0);
        assertThat(mSession.mStopInteractiveAppCount).isEqualTo(1);
    }

    @Test
    public void testDispatchKeyDown() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyDownCount > 0);

        assertThat(mSession.mKeyDownCount).isEqualTo(1);
        assertThat(mSession.mKeyDownCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyDownEvent, event);
    }

    @Test
    public void testDispatchKeyUp() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_I;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyUpCount > 0);

        assertThat(mSession.mKeyUpCount).isEqualTo(1);
        assertThat(mSession.mKeyUpCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyUpEvent, event);
    }

    @Test
    public void testDispatchKeyMultiple() {
        assertNotNull(mSession);
        mSession.resetValues();
        final int keyCode = KeyEvent.KEYCODE_L;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_MULTIPLE, keyCode);

        mTvIAppView.dispatchKeyEvent(event);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mKeyMultipleCount > 0);

        assertThat(mSession.mKeyMultipleCount).isEqualTo(1);
        assertThat(mSession.mKeyMultipleCode).isEqualTo(keyCode);
        assertKeyEventEquals(mSession.mKeyMultipleEvent, event);
    }

    @Test
    public void testDispatchUnhandledInputEvent() {
        final int keyCode = KeyEvent.KEYCODE_I;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_UP, keyCode);

        assertThat(mTvIAppView.dispatchUnhandledInputEvent(event)).isTrue();
    }

    @Test
    public void testCreateBiInteractiveApp() {
        assertNotNull(mSession);
        mSession.resetValues();
        mCallback.resetValues();
        final Bundle bundle = createTestBundle();
        final Uri uri = createTestUri();
        final String biIAppId = "biIAppId";

        mTvIAppView.createBiInteractiveApp(uri, bundle);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mCallback.mBiIAppCreatedCount > 0);

        assertThat(mSession.mCreateBiIAppCount).isEqualTo(1);
        assertThat(mSession.mCreateBiIAppUri).isEqualTo(uri);
        assertBundlesAreEqual(mSession.mCreateBiIAppParams, bundle);

        assertThat(mCallback.mIAppServiceId).isEqualTo(mStubInfo.getId());
        assertThat(mCallback.mBiIAppUri).isEqualTo(uri);
        assertThat(mCallback.mBiIAppId).isEqualTo(biIAppId);

        mTvIAppView.destroyBiInteractiveApp(biIAppId);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mDestroyBiIAppCount > 0);

        assertThat(mSession.mDestroyBiIAppCount).isEqualTo(1);
        assertThat(mSession.mDestroyBiIAppId).isEqualTo(biIAppId);
    }

    @Test
    public void testTuned() {
        linkTvView();

        mInputSession.notifyTuned(CHANNEL_0);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mTunedCount > 0);

        assertThat(mSession.mTunedCount).isEqualTo(1);
        assertThat(mSession.mTunedUri).isEqualTo(CHANNEL_0);
    }

    @Test
    public void testVideoAvailable() {
        linkTvView();

        mInputSession.notifyVideoAvailable();
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mVideoAvailableCount > 0);

        assertThat(mSession.mVideoAvailableCount).isEqualTo(1);
    }

    @Test
    public void testAdRequest() throws Throwable {
        linkTvView();

        File tmpFile = File.createTempFile("cts_tv_interactive_app", "tias_test");
        ParcelFileDescriptor fd =
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE);
        AdRequest adRequest = new AdRequest(
                567, AdRequest.REQUEST_TYPE_START, fd, 787L, 989L, 100L, "MMM", new Bundle());
        mSession.requestAd(adRequest);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mAdRequestCount > 0);

        assertThat(mInputSession.mAdRequestCount).isEqualTo(1);
        assertThat(mInputSession.mAdRequest.getId()).isEqualTo(567);
        assertThat(mInputSession.mAdRequest.getRequestType())
                .isEqualTo(AdRequest.REQUEST_TYPE_START);
        assertNotNull(mInputSession.mAdRequest.getFileDescriptor());
        assertThat(mInputSession.mAdRequest.getStartTimeMillis()).isEqualTo(787L);
        assertThat(mInputSession.mAdRequest.getStopTimeMillis()).isEqualTo(989L);
        assertThat(mInputSession.mAdRequest.getEchoIntervalMillis()).isEqualTo(100L);
        assertThat(mInputSession.mAdRequest.getMediaFileType()).isEqualTo("MMM");
        assertNotNull(mInputSession.mAdRequest.getMetadata());

        fd.close();
        tmpFile.delete();
    }

    @Test
    public void testAdResponse() throws Throwable {
        linkTvView();

        AdResponse adResponse = new AdResponse(767, AdResponse.RESPONSE_TYPE_PLAYING, 909L);
        mInputSession.notifyAdResponse(adResponse);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mAdResponseCount > 0);

        assertThat(mSession.mAdResponseCount).isEqualTo(1);
        assertThat(mSession.mAdResponse.getId()).isEqualTo(767);
        assertThat(mSession.mAdResponse.getResponseType())
                .isEqualTo(AdResponse.RESPONSE_TYPE_PLAYING);
        assertThat(mSession.mAdResponse.getElapsedTimeMillis()).isEqualTo(909L);
    }

    @Test
    public void testAitInfo() throws Throwable {
        linkTvView();
        mTvInputCallback.resetValues();

        mInputSession.notifyAitInfoUpdated(
                new AitInfo(TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_HBBTV, 2));
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mTvInputCallback.mAitInfoUpdatedCount > 0);

        assertThat(mTvInputCallback.mAitInfoUpdatedCount).isEqualTo(1);
        assertThat(mTvInputCallback.mAitInfo.getType())
                .isEqualTo(TvInteractiveAppServiceInfo.INTERACTIVE_APP_TYPE_HBBTV);
        assertThat(mTvInputCallback.mAitInfo.getVersion()).isEqualTo(2);
    }

    @Test
    public void testSignalStrength() throws Throwable {
        linkTvView();

        mInputSession.notifySignalStrength(TvInputManager.SIGNAL_STRENGTH_STRONG);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRemoveBroadcastInfo() throws Throwable {
        linkTvView();

        mSession.removeBroadcastInfo(23);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testNotifyBiInteractiveAppCreated() throws Throwable {
        mSession.notifyBiInteractiveAppCreated(createTestUri(), "testAppId");
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testTeletextAppState() throws Throwable {
        mSession.notifyTeletextAppStateChanged(TvInteractiveAppManager.TELETEXT_APP_STATE_HIDE);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestCurrentChannelLcn() throws Throwable {
        mSession.requestCurrentChannelLcn();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestCurrentTvInputId() throws Throwable {
        mSession.requestCurrentTvInputId();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestStreamVolume() throws Throwable {
        mSession.requestStreamVolume();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testRequestTrackInfoList() throws Throwable {
        mSession.requestTrackInfoList();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendPlaybackCommandRequest() throws Throwable {
        mSession.sendPlaybackCommandRequest(mStubInfo.getId(), createTestBundle());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetMediaViewEnabled() throws Throwable {
        mSession.setMediaViewEnabled(false);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetVideoBounds() throws Throwable {
        mSession.setVideoBounds(new Rect());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testResetInteractiveApp() throws Throwable {
        mTvIAppView.resetInteractiveApp();
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentChannelLcn() throws Throwable {
        mTvIAppView.sendCurrentChannelLcn(1);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentChannelUri() throws Throwable {
        mTvIAppView.sendCurrentChannelUri(createTestUri());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendCurrentTvInputId() throws Throwable {
        mTvIAppView.sendCurrentTvInputId("input_id");
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendStreamVolume() throws Throwable {
        mTvIAppView.sendStreamVolume(0.1f);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSendTrackInfoList() throws Throwable {
        mTvIAppView.sendTrackInfoList(new ArrayList<TvTrackInfo>());
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testSetTeletextAppEnabled() throws Throwable {
        mTvIAppView.setTeletextAppEnabled(false);
        mInstrumentation.waitForIdleSync();
    }

    @Test
    public void testTsRequest() throws Throwable {
        linkTvView();

        TsRequest request = new TsRequest(1, BroadcastInfoRequest.REQUEST_OPTION_REPEAT, 11);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (TsRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TS);
        assertThat(request.getRequestId()).isEqualTo(1);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTsPid()).isEqualTo(11);
    }

    @Test
    public void testCommandRequest() throws Throwable {
        linkTvView();

        CommandRequest request = new CommandRequest(2, BroadcastInfoRequest.REQUEST_OPTION_REPEAT,
                "nameSpace1", "name2", "requestArgs", CommandRequest.ARGUMENT_TYPE_XML);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (CommandRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_COMMAND);
        assertThat(request.getRequestId()).isEqualTo(2);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getNamespace()).isEqualTo("nameSpace1");
        assertThat(request.getName()).isEqualTo("name2");
        assertThat(request.getArguments()).isEqualTo("requestArgs");
        assertThat(request.getArgumentType()).isEqualTo(CommandRequest.ARGUMENT_TYPE_XML);
    }

    @Test
    public void testDsmccRequest() throws Throwable {
        linkTvView();

        final Uri uri = createTestUri();
        DsmccRequest request = new DsmccRequest(3, BroadcastInfoRequest.REQUEST_OPTION_REPEAT,
                uri);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (DsmccRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_DSMCC);
        assertThat(request.getRequestId()).isEqualTo(3);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getUri()).isEqualTo(uri);
    }

    @Test
    public void testPesRequest() throws Throwable {
        linkTvView();

        PesRequest request = new PesRequest(4, BroadcastInfoRequest.REQUEST_OPTION_REPEAT, 44, 444);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (PesRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_PES);
        assertThat(request.getRequestId()).isEqualTo(4);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTsPid()).isEqualTo(44);
        assertThat(request.getStreamId()).isEqualTo(444);
    }

    @Test
    public void testSectionRequest() throws Throwable {
        linkTvView();

        SectionRequest request = new SectionRequest(5, BroadcastInfoRequest.REQUEST_OPTION_REPEAT,
                55, 555, 5555);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (SectionRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_SECTION);
        assertThat(request.getRequestId()).isEqualTo(5);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTsPid()).isEqualTo(55);
        assertThat(request.getTableId()).isEqualTo(555);
        assertThat(request.getVersion()).isEqualTo(5555);
    }

    @Test
    public void testStreamEventRequest() throws Throwable {
        linkTvView();

        final Uri uri = createTestUri();
        StreamEventRequest request = new StreamEventRequest(6,
                BroadcastInfoRequest.REQUEST_OPTION_REPEAT, uri, "testName");
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (StreamEventRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_STREAM_EVENT);
        assertThat(request.getRequestId()).isEqualTo(6);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTargetUri()).isEqualTo(uri);
        assertThat(request.getEventName()).isEqualTo("testName");
    }

    @Test
    public void testTableRequest() throws Throwable {
        linkTvView();

        TableRequest request = new TableRequest(7, BroadcastInfoRequest.REQUEST_OPTION_REPEAT, 77,
                TableRequest.TABLE_NAME_PMT, 777);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (TableRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TABLE);
        assertThat(request.getRequestId()).isEqualTo(7);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getTableId()).isEqualTo(77);
        assertThat(request.getTableName()).isEqualTo(TableRequest.TABLE_NAME_PMT);
        assertThat(request.getVersion()).isEqualTo(777);
    }

    @Test
    public void testTimelineRequest() throws Throwable {
        linkTvView();

        TimelineRequest request = new TimelineRequest(8, BroadcastInfoRequest.REQUEST_OPTION_REPEAT,
                8000);
        mSession.requestBroadcastInfo(request);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mInputSession.mBroadcastInfoRequestCount > 0);

        request = (TimelineRequest) mInputSession.mBroadcastInfoRequest;
        assertThat(mInputSession.mBroadcastInfoRequestCount).isEqualTo(1);
        assertThat(request.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE);
        assertThat(request.getRequestId()).isEqualTo(8);
        assertThat(request.getOption()).isEqualTo(BroadcastInfoRequest.REQUEST_OPTION_REPEAT);
        assertThat(request.getIntervalMillis()).isEqualTo(8000);
    }

    @Test
    public void testTsResponse() throws Throwable {
        linkTvView();

        TsResponse response = new TsResponse(1, 11, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                "TestToken");
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (TsResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TS);
        assertThat(response.getRequestId()).isEqualTo(1);
        assertThat(response.getSequence()).isEqualTo(11);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSharedFilterToken()).isEqualTo("TestToken");
    }

    @Test
    public void testCommandResponse() throws Throwable {
        linkTvView();

        CommandResponse response = new CommandResponse(2, 22,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, "commandResponse",
                CommandResponse.RESPONSE_TYPE_JSON);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (CommandResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_COMMAND);
        assertThat(response.getRequestId()).isEqualTo(2);
        assertThat(response.getSequence()).isEqualTo(22);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getResponse()).isEqualTo("commandResponse");
        assertThat(response.getResponseType()).isEqualTo(CommandResponse.RESPONSE_TYPE_JSON);
    }

    @Test
    public void testDsmccResponse() throws Throwable {
        linkTvView();

        File tmpFile = File.createTempFile("cts_tv_interactive_app", "tias_test");
        ParcelFileDescriptor fd =
                ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE);
        final List<String> childList = new ArrayList(Arrays.asList("c1", "c2", "c3"));
        final int[] eventIds = new int[] {1, 2, 3};
        final String[] eventNames = new String[] {"event1", "event2", "event3"};
        DsmccResponse response = new DsmccResponse(3, 3, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                fd);

        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (DsmccResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_DSMCC);
        assertThat(response.getRequestId()).isEqualTo(3);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getBiopMessageType()).isEqualTo(DsmccResponse.BIOP_MESSAGE_TYPE_FILE);
        assertNotNull(response.getFile());

        response = new DsmccResponse(3, 3, BroadcastInfoResponse.RESPONSE_RESULT_OK, true,
                childList);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 1);

        response = (DsmccResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(2);
        assertThat(response.getBiopMessageType()).isEqualTo(
                DsmccResponse.BIOP_MESSAGE_TYPE_SERVICE_GATEWAY);
        assertNotNull(response.getChildList());

        response = new DsmccResponse(3, 3, BroadcastInfoResponse.RESPONSE_RESULT_OK, eventIds,
                eventNames);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 2);

        response = (DsmccResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(3);
        assertThat(response.getBiopMessageType()).isEqualTo(DsmccResponse.BIOP_MESSAGE_TYPE_STREAM);
        assertNotNull(response.getStreamEventIds());
        assertNotNull(response.getStreamEventNames());

        fd.close();
        tmpFile.delete();
    }

    @Test
    public void testPesResponse() throws Throwable {
        linkTvView();

        PesResponse response = new PesResponse(4, 44, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                "testShardFilterToken");
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (PesResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_PES);
        assertThat(response.getRequestId()).isEqualTo(4);
        assertThat(response.getSequence()).isEqualTo(44);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSharedFilterToken()).isEqualTo("testShardFilterToken");
    }

    @Test
    public void testSectionResponse() throws Throwable {
        linkTvView();

        final Bundle bundle = createTestBundle();
        SectionResponse response = new SectionResponse(5, 55,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, 555, 5555, bundle);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (SectionResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_SECTION);
        assertThat(response.getRequestId()).isEqualTo(5);
        assertThat(response.getSequence()).isEqualTo(55);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSessionId()).isEqualTo(555);
        assertThat(response.getVersion()).isEqualTo(5555);
        assertBundlesAreEqual(response.getSessionData(), bundle);
    }

    @Test
    public void testStreamEventResponse() throws Throwable {
        linkTvView();

        final byte[] data = new byte[] {1, 2, 3};
        StreamEventResponse response = new StreamEventResponse(6, 66,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, 666, 6666, data);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (StreamEventResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_STREAM_EVENT);
        assertThat(response.getRequestId()).isEqualTo(6);
        assertThat(response.getSequence()).isEqualTo(66);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getEventId()).isEqualTo(666);
        assertThat(response.getNptMillis()).isEqualTo(6666);
        assertNotNull(response.getData());
    }

    @Test
    public void testTableResponse() throws Throwable {
        linkTvView();

        final Uri uri = createTestUri();
        TableResponse response = new TableResponse(7, 77, BroadcastInfoResponse.RESPONSE_RESULT_OK,
                uri, 777, 7777);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (TableResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TABLE);
        assertThat(response.getRequestId()).isEqualTo(7);
        assertThat(response.getSequence()).isEqualTo(77);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getTableUri()).isEqualTo(uri);
        assertThat(response.getVersion()).isEqualTo(777);
        assertThat(response.getSize()).isEqualTo(7777);
    }

    @Test
    public void testTimelineResponse() throws Throwable {
        linkTvView();

        TimelineResponse response = new TimelineResponse(8, 88,
                BroadcastInfoResponse.RESPONSE_RESULT_OK, "test_selector", 1, 10, 100, 1000);
        mInputSession.notifyBroadcastInfoResponse(response);
        mInstrumentation.waitForIdleSync();
        PollingCheck.waitFor(TIME_OUT_MS, () -> mSession.mBroadcastInfoResponseCount > 0);

        response = (TimelineResponse) mSession.mBroadcastInfoResponse;
        assertThat(mSession.mBroadcastInfoResponseCount).isEqualTo(1);
        assertThat(response.getType()).isEqualTo(TvInputManager.BROADCAST_INFO_TYPE_TIMELINE);
        assertThat(response.getRequestId()).isEqualTo(8);
        assertThat(response.getSequence()).isEqualTo(88);
        assertThat(response.getResponseResult()).isEqualTo(
                BroadcastInfoResponse.RESPONSE_RESULT_OK);
        assertThat(response.getSelector().toString()).isEqualTo("test_selector");
        assertThat(response.getUnitsPerTick()).isEqualTo(1);
        assertThat(response.getUnitsPerSecond()).isEqualTo(10);
        assertThat(response.getWallClock()).isEqualTo(100);
        assertThat(response.getTicks()).isEqualTo(1000);
    }

    @Test
    public void testViewOnAttachedToWindow() {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTvIAppView.onAttachedToWindow();
            }
        });

    }

    @Test
    public void testViewOnDetachedFromWindow() {
        mTvIAppView.onDetachedFromWindow();
    }

    @Test
    public void testViewOnLayout() {
        int left = 1, top = 10, right = 5, bottom = 20;
        mTvIAppView.onLayout(true, left, top, right, bottom);
    }

    @Test
    public void testViewOnMeasure() {
        int widthMeasureSpec = 5, heightMeasureSpec = 10;
        mTvIAppView.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Test
    public void testViewOnVisibilityChanged() {
        mTvIAppView.onVisibilityChanged(mTvIAppView, View.VISIBLE);
    }

    @Test
    public void testOnUnhandledInputEvent() {
        final int keyCode = KeyEvent.KEYCODE_Q;
        final KeyEvent event = new KeyEvent(KeyEvent.ACTION_DOWN, keyCode);
        mTvIAppView.onUnhandledInputEvent(event);
    }

    public static void assertKeyEventEquals(KeyEvent actual, KeyEvent expected) {
        if (expected != null && actual != null) {
            assertThat(actual.getDownTime()).isEqualTo(expected.getDownTime());
            assertThat(actual.getEventTime()).isEqualTo(expected.getEventTime());
            assertThat(actual.getAction()).isEqualTo(expected.getAction());
            assertThat(actual.getKeyCode()).isEqualTo(expected.getKeyCode());
            assertThat(actual.getRepeatCount()).isEqualTo(expected.getRepeatCount());
            assertThat(actual.getMetaState()).isEqualTo(expected.getMetaState());
            assertThat(actual.getDeviceId()).isEqualTo(expected.getDeviceId());
            assertThat(actual.getScanCode()).isEqualTo(expected.getScanCode());
            assertThat(actual.getFlags()).isEqualTo(expected.getFlags());
            assertThat(actual.getSource()).isEqualTo(expected.getSource());
            assertThat(actual.getCharacters()).isEqualTo(expected.getCharacters());
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    private static void assertBundlesAreEqual(Bundle actual, Bundle expected) {
        if (expected != null && actual != null) {
            assertThat(actual.keySet()).isEqualTo(expected.keySet());
            for (String key : expected.keySet()) {
                assertThat(actual.get(key)).isEqualTo(expected.get(key));
            }
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }
}
