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

package android.app.stubs;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.service.notification.Adjustment;
import android.service.notification.NotificationAssistantService;
import android.service.notification.StatusBarNotification;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TestNotificationAssistant extends NotificationAssistantService {
    public static final String TAG = "TestNotificationAssistant";
    public static final String PKG = "android.app.stubs";

    private static TestNotificationAssistant sNotificationAssistantInstance = null;
    boolean mIsConnected;
    boolean mIsPanelOpen = false;
    public List<String> mCurrentCapabilities;
    boolean mNotificationVisible = false;
    int mNotificationId = 1357;
    int mNotificationSeenCount = 0;
    int mNotificationClickCount = 0;
    int mNotificationRank = -1;
    int mNotificationFeedback = 0;
    String mSnoozedKey;
    String mSnoozedUntilContext;
    private NotificationManager mNotificationManager;

    public Map<String, Integer> mRemoved = new HashMap<>();

    public static String getId() {
        return String.format("%s/%s", TestNotificationAssistant.class.getPackage().getName(),
                TestNotificationAssistant.class.getName());
    }

    public static ComponentName getComponentName() {
        return new ComponentName(TestNotificationAssistant.class.getPackage().getName(),
                TestNotificationAssistant.class.getName());
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mNotificationManager = getSystemService(NotificationManager.class);
    }

    @Override
    public void onListenerConnected() {
        super.onListenerConnected();
        sNotificationAssistantInstance = this;
        mIsConnected = true;
    }

    @Override
    public void onListenerDisconnected() {
        mIsConnected = false;
    }

    public static TestNotificationAssistant getInstance() {
        return sNotificationAssistantInstance;
    }

    @Override
    public void onNotificationSnoozedUntilContext(StatusBarNotification statusBarNotification,
            String s) {
        mSnoozedKey = statusBarNotification.getKey();
        mSnoozedUntilContext = s;
    }

    @Override
    public Adjustment onNotificationEnqueued(StatusBarNotification sbn) {
        return null;
    }

    @Override
    public Adjustment onNotificationEnqueued(StatusBarNotification sbn, NotificationChannel channel,
            RankingMap rankingMap) {
        Bundle signals = new Bundle();
        Ranking ranking = new Ranking();
        rankingMap.getRanking(sbn.getKey(), ranking);
        mNotificationRank = ranking.getRank();
        signals.putInt(Adjustment.KEY_USER_SENTIMENT, Ranking.USER_SENTIMENT_POSITIVE);
        return new Adjustment(sbn.getPackageName(), sbn.getKey(), signals, "",
                sbn.getUser());
    }

    @Override
    public void onAllowedAdjustmentsChanged() {
        mCurrentCapabilities = mNotificationManager.getAllowedAssistantAdjustments();
    }

    void resetNotificationVisibilityCounts() {
        mNotificationSeenCount = 0;
    }

    @Override
    public void onNotificationVisibilityChanged(String key, boolean isVisible) {
        if (key.contains(TestNotificationAssistant.class.getPackage().getName()
                + "|" + mNotificationId)) {
            mNotificationVisible = isVisible;
        }
    }

    @Override
    public void onNotificationsSeen(List<String> keys) {
        mNotificationSeenCount += keys.size();
    }

    @Override
    public void onPanelHidden() {
        mIsPanelOpen = false;
    }

    @Override
    public void onPanelRevealed(int items) {
        mIsPanelOpen = true;
    }

    void resetNotificationClickCount() {
        mNotificationClickCount = 0;
    }

    @Override
    public void onNotificationClicked(String key) {
        mNotificationClickCount++;
    }

    @Override
    public void onNotificationFeedbackReceived(String key, RankingMap rankingMap, Bundle feedback) {
        mNotificationFeedback = feedback.getInt(FEEDBACK_RATING, 0);
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn, RankingMap rankingMap,
            int reason) {
        if (sbn == null) {
            return;
        }
        mRemoved.put(sbn.getKey(), reason);
    }
}
