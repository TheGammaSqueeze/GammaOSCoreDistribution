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

import android.content.Context;
import android.media.tv.AdRequest;
import android.media.tv.AdResponse;
import android.media.tv.AitInfo;
import android.media.tv.BroadcastInfoRequest;
import android.media.tv.BroadcastInfoResponse;
import android.media.tv.TvInputService;
import android.net.Uri;
import android.view.Surface;

public class StubTvInputService2 extends TvInputService {
    static String sTvInputSessionId;
    public static StubSessionImpl2 sStubSessionImpl2;

    public static String getSessionId() {
        return sTvInputSessionId;
    }

    @Override
    public Session onCreateSession(String inputId, String tvInputSessionId) {
        sTvInputSessionId = tvInputSessionId;
        sStubSessionImpl2 = new StubSessionImpl2(this);
        return sStubSessionImpl2;
    }

    @Override
    public Session onCreateSession(String inputId) {
        return new StubSessionImpl2(this);
    }

    public static class StubSessionImpl2 extends TvInputService.Session {
        public int mAdRequestCount;
        public int mBroadcastInfoRequestCount;

        public AdRequest mAdRequest;
        public BroadcastInfoRequest mBroadcastInfoRequest;

        StubSessionImpl2(Context context) {
            super(context);
        }

        public void resetValues() {
            mAdRequestCount = 0;
            mBroadcastInfoRequestCount = 0;

            mAdRequest = null;
            mBroadcastInfoRequest = null;
        }

        @Override
        public void onRelease() {
            sTvInputSessionId = null;
        }

        @Override
        public boolean onSetSurface(Surface surface) {
            return false;
        }

        @Override
        public void onSetStreamVolume(float volume) {
        }

        @Override
        public boolean onTune(Uri channelUri) {
            return false;
        }

        @Override
        public void onSetCaptionEnabled(boolean enabled) {
        }

        @Override
        public void onRequestAd(AdRequest request) {
            super.onRequestAd(request);
            mAdRequestCount++;
            mAdRequest = request;
        }

        @Override
        public void onRequestBroadcastInfo(BroadcastInfoRequest request) {
            super.onRequestBroadcastInfo(request);
            mBroadcastInfoRequestCount++;
            mBroadcastInfoRequest = request;
        }

        @Override
        public void onRemoveBroadcastInfo(int info) {
            super.onRemoveBroadcastInfo(info);
        }

        @Override
        public void onSetInteractiveAppNotificationEnabled(boolean enable) {
            super.onSetInteractiveAppNotificationEnabled(enable);
        }

        @Override
        public void notifyAdResponse(AdResponse response) {
            super.notifyAdResponse(response);
        }

        @Override
        public void notifyAitInfoUpdated(AitInfo info) {
            super.notifyAitInfoUpdated(info);
        }

        @Override
        public void notifyBroadcastInfoResponse(BroadcastInfoResponse response) {
            super.notifyBroadcastInfoResponse(response);
        }

        @Override
        public void notifySignalStrength(int strength) {
            super.notifySignalStrength(strength);
        }

        @Override
        public void notifyTuned(Uri uri) {
            super.notifyTuned(uri);
        }
    }
}
