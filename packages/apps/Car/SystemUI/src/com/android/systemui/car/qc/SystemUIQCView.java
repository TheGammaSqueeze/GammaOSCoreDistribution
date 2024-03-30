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

package com.android.systemui.car.qc;

import android.app.ActivityManager;
import android.content.Context;
import android.content.res.TypedArray;
import android.net.Uri;
import android.util.AttributeSet;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.qc.controller.BaseQCController;
import com.android.car.qc.controller.LocalQCController;
import com.android.car.qc.controller.RemoteQCController;
import com.android.car.qc.provider.BaseLocalQCProvider;
import com.android.car.qc.view.QCView;
import com.android.systemui.R;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;

/**
 * Quick Control View Element for CarSystemUI.
 *
 * This extended class allows for specifying a local or remote quick controls provider via xml
 * attributes. When a remote provider is specified, it will bind to the provider as the current
 * foreground user instead of user 0.
 *
 * @attr ref R.styleable#SystemUIQCView_remoteQCProvider
 * @attr ref R.styleable#SystemUIQCView_localQCProvider
 */
public class SystemUIQCView extends QCView {
    private BaseQCController mController;
    private BaseLocalQCProvider mLocalQCProvider;

    public SystemUIQCView(Context context) {
        super(context);
        init(context, /* attrs= */ null);
    }

    public SystemUIQCView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs);
    }

    public SystemUIQCView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context, attrs);
    }

    public SystemUIQCView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        init(context, attrs);
    }

    /**
     * Toggle whether or not this view should listen to live updates.
     */
    public void listen(boolean shouldListen) {
        if (mController != null) {
            mController.listen(shouldListen);
        }
    }

    /**
     * Destroy the current QCView and associated controller.
     */
    public void destroy() {
        if (mController != null) {
            mController.destroy();
            mController = null;
        }
        removeAllViews();
    }

    /**
     * @return {@link BaseLocalQCProvider} for this System UI Quick Controls View.
     */
    @Nullable
    public BaseLocalQCProvider getLocalQCProvider() {
        return mLocalQCProvider;
    }

    private void init(@NonNull Context context, @Nullable AttributeSet attrs) {
        setFocusable(false);
        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.SystemUIQCView);
        String remoteUri = a.getString(R.styleable.SystemUIQCView_remoteQCProvider);
        if (remoteUri != null) {
            Uri uri = Uri.parse(remoteUri);
            if (uri.getUserInfo() == null) {
                // To bind to the content provider as the current user rather than user 0 (which
                // SystemUI is running on), add the current user id followed by the '@' symbol
                // before the Uri's authority.
                uri = uri.buildUpon().authority(
                        String.format("%s@%s", ActivityManager.getCurrentUser(),
                                uri.getAuthority())).build();
            }
            bindRemoteQCView(uri);
        } else {
            String localClass = a.getString(R.styleable.SystemUIQCView_localQCProvider);
            if (localClass != null) {
                bindLocalQCView(localClass);
            }
        }
        a.recycle();
    }

    private void bindRemoteQCView(Uri uri) {
        mController = new RemoteQCController(mContext, uri);
        mController.addObserver(this);
        mController.bind();
    }

    private void bindLocalQCView(String localClass) {
        mLocalQCProvider = createLocalQCProviderInstance(localClass, mContext);
        mController = new LocalQCController(mContext, mLocalQCProvider);
        mController.addObserver(this);
        mController.bind();
    }

    private BaseLocalQCProvider createLocalQCProviderInstance(String controllerName,
            Context context) {
        try {
            Class<?> clazz = Class.forName(controllerName);
            Constructor<?> providerConstructor = clazz.getConstructor(Context.class);
            return (BaseLocalQCProvider) providerConstructor.newInstance(context);
        } catch (ClassNotFoundException | NoSuchMethodException | InstantiationException
                | InvocationTargetException | IllegalAccessException e) {
            throw new IllegalArgumentException("Invalid controller: " + controllerName, e);
        }
    }
}
