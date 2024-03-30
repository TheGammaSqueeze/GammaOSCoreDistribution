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
package android.app.dream.cts.app;

import android.annotation.NonNull;
import android.content.Intent;
import android.graphics.Color;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;

/**
 * {@link DreamOverlayService} provides a test implementation of
 * {@link android.service.dreams.DreamOverlayService}. When informed of the dream state, the service
 * populates a child window with a simple view.Once that view's visibility changes, the dream
 * broadcasts an action that tests wait upon as a signal the overlay has been displayed.
 */
public class DreamOverlayService extends android.service.dreams.DreamOverlayService {
    public static final String ACTION_DREAM_OVERLAY_SHOWN =
            "android.app.dream.cts.app.action.overlay_shown";
    public static final String TEST_PACKAGE = "android.dreams.cts";

    @Override
    public void onStartDream(@NonNull WindowManager.LayoutParams layoutParams) {
        addWindowOverlay(layoutParams);
    }

    private void addWindowOverlay(WindowManager.LayoutParams layoutParams) {
        FrameLayout layout = new FrameLayout(this);
        layout.setBackgroundColor(Color.YELLOW);
        layout.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        // Add a listener for when the root layout becomes visible. We use this event to signal the
        // dream overlay has been shown.
        layout.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
            if (layout.getVisibility() == View.VISIBLE) {
                final Intent intent = new Intent();
                intent.setPackage(TEST_PACKAGE);
                intent.setAction(ACTION_DREAM_OVERLAY_SHOWN);
                sendBroadcast(intent);
            }
        });

        final WindowManager wm = getSystemService(WindowManager.class);
        wm.addView(layout, layoutParams);
    }
}
