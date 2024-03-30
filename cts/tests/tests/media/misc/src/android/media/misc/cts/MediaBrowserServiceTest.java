/*
 * Copyright (C) 2015 The Android Open Source Project
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
package android.media.misc.cts;

import static android.media.browse.MediaBrowser.MediaItem.FLAG_PLAYABLE;
import static android.media.misc.cts.MediaBrowserServiceTestService.KEY_PARENT_MEDIA_ID;
import static android.media.misc.cts.MediaBrowserServiceTestService.KEY_SERVICE_COMPONENT_NAME;
import static android.media.misc.cts.MediaBrowserServiceTestService.TEST_SERIES_OF_NOTIFY_CHILDREN_CHANGED;
import static android.media.misc.cts.MediaSessionTestService.KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS;
import static android.media.misc.cts.MediaSessionTestService.STEP_CHECK;
import static android.media.misc.cts.MediaSessionTestService.STEP_CLEAN_UP;
import static android.media.misc.cts.MediaSessionTestService.STEP_SET_UP;
import static android.media.cts.Utils.compareRemoteUserInfo;

import android.content.ComponentName;
import android.media.MediaDescription;
import android.media.browse.MediaBrowser;
import android.media.browse.MediaBrowser.MediaItem;
import android.media.cts.NonMediaMainlineTest;
import android.media.session.MediaSessionManager.RemoteUserInfo;
import android.os.Bundle;
import android.os.Process;
import android.service.media.MediaBrowserService;
import android.service.media.MediaBrowserService.BrowserRoot;
import android.test.InstrumentationTestCase;

import androidx.test.core.app.ApplicationProvider;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test {@link android.service.media.MediaBrowserService}.
 */
@NonMediaMainlineTest
public class MediaBrowserServiceTest extends InstrumentationTestCase {
    // The maximum time to wait for an operation.
    private static final long TIME_OUT_MS = 3000L;
    private static final long WAIT_TIME_FOR_NO_RESPONSE_MS = 500L;
    private static final ComponentName TEST_BROWSER_SERVICE = new ComponentName(
            "android.media.misc.cts", "android.media.misc.cts.StubMediaBrowserService");

    private final TestCountDownLatch mOnChildrenLoadedLatch = new TestCountDownLatch();
    private final TestCountDownLatch mOnChildrenLoadedWithOptionsLatch = new TestCountDownLatch();
    private final TestCountDownLatch mOnItemLoadedLatch = new TestCountDownLatch();

    private final MediaBrowser.SubscriptionCallback mSubscriptionCallback =
            new MediaBrowser.SubscriptionCallback() {
            @Override
            public void onChildrenLoaded(String parentId, List<MediaItem> children) {
                if (children != null) {
                    for (MediaItem item : children) {
                        assertRootHints(item);
                    }
                }
                mOnChildrenLoadedLatch.countDown();
            }

            @Override
            public void onChildrenLoaded(String parentId, List<MediaItem> children,
                    Bundle options) {
                if (children != null) {
                    for (MediaItem item : children) {
                        assertRootHints(item);
                    }
                }
                mOnChildrenLoadedWithOptionsLatch.countDown();
            }
        };

    private final MediaBrowser.ItemCallback mItemCallback = new MediaBrowser.ItemCallback() {
        @Override
        public void onItemLoaded(MediaItem item) {
            assertRootHints(item);
            mOnItemLoadedLatch.countDown();
        }
    };

    private MediaBrowser mMediaBrowser;
    private RemoteUserInfo mBrowserInfo;
    private StubMediaBrowserService mMediaBrowserService;
    private Bundle mRootHints;

    @Override
    public void setUp() throws Exception {
        mRootHints = new Bundle();
        mRootHints.putBoolean(BrowserRoot.EXTRA_RECENT, true);
        mRootHints.putBoolean(BrowserRoot.EXTRA_OFFLINE, true);
        mRootHints.putBoolean(BrowserRoot.EXTRA_SUGGESTED, true);
        mBrowserInfo = new RemoteUserInfo(
                getInstrumentation().getTargetContext().getPackageName(),
                Process.myPid(),
                Process.myUid());
        mOnChildrenLoadedLatch.reset();
        mOnChildrenLoadedWithOptionsLatch.reset();
        mOnItemLoadedLatch.reset();

        final CountDownLatch onConnectedLatch = new CountDownLatch(1);
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser = new MediaBrowser(getInstrumentation().getTargetContext(),
                    TEST_BROWSER_SERVICE, new MediaBrowser.ConnectionCallback() {
                @Override
                public void onConnected() {
                    mMediaBrowserService = StubMediaBrowserService.sInstance;
                    onConnectedLatch.countDown();
                }
            }, mRootHints);
            mMediaBrowser.connect();
        });
        assertTrue(onConnectedLatch.await(TIME_OUT_MS, TimeUnit.MILLISECONDS));
        assertNotNull(mMediaBrowserService);
    }

    @Override
    public void tearDown() {
        getInstrumentation().runOnMainSync(()-> {
            if (mMediaBrowser != null) {
                mMediaBrowser.disconnect();
                mMediaBrowser = null;
            }
        });
    }

    public void testGetSessionToken() {
        assertEquals(StubMediaBrowserService.sSession.getSessionToken(),
                mMediaBrowserService.getSessionToken());
    }

    public void testNotifyChildrenChanged() throws Exception {
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
        });
        assertTrue(mOnChildrenLoadedLatch.await(TIME_OUT_MS));

        mOnChildrenLoadedLatch.reset();
        mMediaBrowserService.notifyChildrenChanged(StubMediaBrowserService.MEDIA_ID_ROOT);
        assertTrue(mOnChildrenLoadedLatch.await(TIME_OUT_MS));
    }

    public void testNotifyChildrenChangedWithNullOptionsThrowsIAE() {
        try {
            mMediaBrowserService.notifyChildrenChanged(
                    StubMediaBrowserService.MEDIA_ID_ROOT, /*options=*/ null);
            fail();
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }

    public void testNotifyChildrenChangedWithPagination() {
        final int pageSize = 5;
        final int page = 2;
        Bundle options = new Bundle();
        options.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
        options.putInt(MediaBrowser.EXTRA_PAGE, page);

        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, options,
                    mSubscriptionCallback);
        });
        assertTrue(mOnChildrenLoadedWithOptionsLatch.await(TIME_OUT_MS));

        mOnChildrenLoadedWithOptionsLatch.reset();
        mMediaBrowserService.notifyChildrenChanged(StubMediaBrowserService.MEDIA_ID_ROOT);
        assertTrue(mOnChildrenLoadedWithOptionsLatch.await(TIME_OUT_MS));

        // Notify that the items overlapping with the given options are changed.
        mOnChildrenLoadedWithOptionsLatch.reset();
        final int newPageSize = 3;
        final int overlappingNewPage = pageSize * page / newPageSize;
        Bundle overlappingOptions = new Bundle();
        overlappingOptions.putInt(MediaBrowser.EXTRA_PAGE_SIZE, newPageSize);
        overlappingOptions.putInt(MediaBrowser.EXTRA_PAGE, overlappingNewPage);
        mMediaBrowserService.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT, overlappingOptions);
        assertTrue(mOnChildrenLoadedWithOptionsLatch.await(TIME_OUT_MS));

        // Notify that the items non-overlapping with the given options are changed.
        mOnChildrenLoadedWithOptionsLatch.reset();
        Bundle nonOverlappingOptions = new Bundle();
        nonOverlappingOptions.putInt(MediaBrowser.EXTRA_PAGE_SIZE, pageSize);
        nonOverlappingOptions.putInt(MediaBrowser.EXTRA_PAGE, page + 1);
        mMediaBrowserService.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_ROOT, nonOverlappingOptions);
        assertFalse(mOnChildrenLoadedWithOptionsLatch.await(WAIT_TIME_FOR_NO_RESPONSE_MS));
    }

    public void testDelayedNotifyChildrenChanged() throws Exception {
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED,
                    mSubscriptionCallback);
        });
        assertFalse(mOnChildrenLoadedLatch.await(WAIT_TIME_FOR_NO_RESPONSE_MS));

        mMediaBrowserService.sendDelayedNotifyChildrenChanged();
        assertTrue(mOnChildrenLoadedLatch.await(TIME_OUT_MS));

        mOnChildrenLoadedLatch.reset();
        mMediaBrowserService.notifyChildrenChanged(
                StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED);
        assertFalse(mOnChildrenLoadedLatch.await(WAIT_TIME_FOR_NO_RESPONSE_MS));

        mMediaBrowserService.sendDelayedNotifyChildrenChanged();
        assertTrue(mOnChildrenLoadedLatch.await(TIME_OUT_MS));
    }

    public void testDelayedItem() throws Exception {
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN_DELAYED,
                    mItemCallback);
        });
        assertFalse(mOnItemLoadedLatch.await(WAIT_TIME_FOR_NO_RESPONSE_MS));

        mMediaBrowserService.sendDelayedItemLoaded();
        assertTrue(mOnItemLoadedLatch.await(TIME_OUT_MS));
    }

    public void testGetBrowserInfo() throws Exception {
        // StubMediaBrowserService stores the browser info in its onGetRoot().
        assertTrue(compareRemoteUserInfo(mBrowserInfo, StubMediaBrowserService.sBrowserInfo));

        StubMediaBrowserService.clearBrowserInfo();
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.subscribe(StubMediaBrowserService.MEDIA_ID_ROOT, mSubscriptionCallback);
        });
        assertTrue(mOnChildrenLoadedLatch.await(TIME_OUT_MS));
        assertTrue(compareRemoteUserInfo(mBrowserInfo, StubMediaBrowserService.sBrowserInfo));

        StubMediaBrowserService.clearBrowserInfo();
        getInstrumentation().runOnMainSync(()-> {
            mMediaBrowser.getItem(StubMediaBrowserService.MEDIA_ID_CHILDREN[0], mItemCallback);
        });
        assertTrue(mOnItemLoadedLatch.await(TIME_OUT_MS));
        assertTrue(compareRemoteUserInfo(mBrowserInfo, StubMediaBrowserService.sBrowserInfo));
    }

    public void testBrowserRoot() {
        final String id = "test-id";
        final String key = "test-key";
        final String val = "test-val";
        final Bundle extras = new Bundle();
        extras.putString(key, val);

        MediaBrowserService.BrowserRoot browserRoot = new BrowserRoot(id, extras);
        assertEquals(id, browserRoot.getRootId());
        assertEquals(val, browserRoot.getExtras().getString(key));
    }

    /**
     * Check that a series of {@link MediaBrowserService#notifyChildrenChanged} does not break
     * {@link MediaBrowser} on the remote process due to binder buffer overflow.
     */
    public void testSeriesOfNotifyChildrenChanged() throws Exception {
        String parentMediaId = "testSeriesOfNotifyChildrenChanged";
        int numberOfCalls = 100;
        int childrenSize = 1_000;
        List<MediaItem> children = new ArrayList<>();
        for (int id = 0; id < childrenSize; id++) {
            MediaDescription description = new MediaDescription.Builder()
                    .setMediaId(Integer.toString(id)).build();
            children.add(new MediaItem(description, FLAG_PLAYABLE));
        }
        mMediaBrowserService.putChildrenToMap(parentMediaId, children);

        try (RemoteService.Invoker invoker = new RemoteService.Invoker(
                ApplicationProvider.getApplicationContext(),
                MediaBrowserServiceTestService.class,
                TEST_SERIES_OF_NOTIFY_CHILDREN_CHANGED)) {
            Bundle args = new Bundle();
            args.putParcelable(KEY_SERVICE_COMPONENT_NAME, TEST_BROWSER_SERVICE);
            args.putString(KEY_PARENT_MEDIA_ID, parentMediaId);
            args.putInt(KEY_EXPECTED_TOTAL_NUMBER_OF_ITEMS, numberOfCalls * childrenSize);
            invoker.run(STEP_SET_UP, args);
            for (int i = 0; i < numberOfCalls; i++) {
                mMediaBrowserService.notifyChildrenChanged(parentMediaId);
            }
            invoker.run(STEP_CHECK);
            invoker.run(STEP_CLEAN_UP);
        }

        mMediaBrowserService.removeChildrenFromMap(parentMediaId);
    }

    private void assertRootHints(MediaItem item) {
        Bundle rootHints = item.getDescription().getExtras();
        assertNotNull(rootHints);
        assertEquals(mRootHints.getBoolean(BrowserRoot.EXTRA_RECENT),
                rootHints.getBoolean(BrowserRoot.EXTRA_RECENT));
        assertEquals(mRootHints.getBoolean(BrowserRoot.EXTRA_OFFLINE),
                rootHints.getBoolean(BrowserRoot.EXTRA_OFFLINE));
        assertEquals(mRootHints.getBoolean(BrowserRoot.EXTRA_SUGGESTED),
                rootHints.getBoolean(BrowserRoot.EXTRA_SUGGESTED));
    }

    private static class TestCountDownLatch {
        private CountDownLatch mLatch;

        TestCountDownLatch() {
            mLatch = new CountDownLatch(1);
        }

        void reset() {
            mLatch = new CountDownLatch(1);
        }

        void countDown() {
            mLatch.countDown();
        }

        boolean await(long timeoutMs) {
            try {
                return mLatch.await(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                return false;
            }
        }
    }
}
