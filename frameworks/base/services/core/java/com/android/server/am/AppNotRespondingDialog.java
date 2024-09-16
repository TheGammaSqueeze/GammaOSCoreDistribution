/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.server.am;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Slog;
import android.view.WindowManager;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;

final class AppNotRespondingDialog extends BaseErrorDialog {
    private static final String TAG = "AppNotRespondingDialog";

    // Event 'what' codes
    static final int FORCE_CLOSE = 1;
    static final int WAIT = 2;
    static final int WAIT_AND_REPORT = 3;

    public static final int CANT_SHOW = -1;
    public static final int ALREADY_SHOWING = -2;

    private final ActivityManagerService mService;
    private final ProcessRecord mProc;
    private final Data mData;

    public AppNotRespondingDialog(ActivityManagerService service, Context context, Data data) {
        super(context);

        // Initialize mService, mProc, and mData to avoid the "might not have been initialized" error
        mService = service;
        mProc = data.proc;
        mData = data;

        // Prevent the dialog from being created by commenting out the UI initialization code
        /*
        Resources res = context.getResources();

        setCancelable(false);

        int resid;
        CharSequence name1 = data.aInfo != null
                ? data.aInfo.loadLabel(context.getPackageManager())
                : null;
        CharSequence name2 = null;
        if (mProc.getPkgList().size() == 1
                && (name2 = context.getPackageManager().getApplicationLabel(mProc.info)) != null) {
            if (name1 != null) {
                resid = com.android.internal.R.string.anr_activity_application;
            } else {
                name1 = name2;
                name2 = mProc.processName;
                resid = com.android.internal.R.string.anr_application_process;
            }
        } else {
            if (name1 != null) {
                name2 = mProc.processName;
                resid = com.android.internal.R.string.anr_activity_process;
            } else {
                name1 = mProc.processName;
                resid = com.android.internal.R.string.anr_process;
            }
        }

        setTitle(name2 != null
                ? res.getString(resid, name1.toString(), name2.toString())
                : res.getString(resid, name1.toString()));

        if (data.aboveSystem) {
            getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ERROR);
        }
        WindowManager.LayoutParams attrs = getWindow().getAttributes();
        attrs.setTitle("Application Not Responding: " + mProc.info.processName);
        attrs.privateFlags = WindowManager.LayoutParams.PRIVATE_FLAG_SYSTEM_ERROR |
                WindowManager.LayoutParams.SYSTEM_FLAG_SHOW_FOR_ALL_USERS;
        getWindow().setAttributes(attrs);
        */
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Do nothing since the UI is not being shown
    }

    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            // Comment out all the dialog handling logic to prevent the dialog from appearing
            /*
            Intent appErrorIntent = null;

            MetricsLogger.action(getContext(), MetricsProto.MetricsEvent.ACTION_APP_ANR,
                    msg.what);

            switch (msg.what) {
                case FORCE_CLOSE:
                    // Kill the application.
                    mService.killAppAtUsersRequest(mProc);
                    break;
                case WAIT_AND_REPORT:
                case WAIT:
                    // Continue waiting for the application.
                    synchronized (mService) {
                        ProcessRecord app = mProc;
                        final ProcessErrorStateRecord errState = app.mErrorState;

                        if (msg.what == WAIT_AND_REPORT) {
                            appErrorIntent = mService.mAppErrors.createAppErrorIntentLOSP(app,
                                    System.currentTimeMillis(), null);
                        }

                        synchronized (mService.mProcLock) {
                            errState.setNotResponding(false);
                            // errState.setNotRespondingReport(null);
                            errState.getDialogController().clearAnrDialogs();
                        }
                        mService.mServices.scheduleServiceTimeoutLocked(app);
                        // If the app remains unresponsive, show the dialog again after a delay.
                        mService.mInternal.rescheduleAnrDialog(mData);
                    }
                    break;
            }

            if (appErrorIntent != null) {
                try {
                    getContext().startActivity(appErrorIntent);
                } catch (ActivityNotFoundException e) {
                    Slog.w(TAG, "bug report receiver disappeared", e);
                }
            }

            dismiss();
            */
        }
    };

    @Override
    protected void closeDialog() {
        // Prevent any action by disabling the close dialog function
        // mHandler.obtainMessage(FORCE_CLOSE).sendToTarget();
    }

    static class Data {
        final ProcessRecord proc;
        final ApplicationInfo aInfo;
        final boolean aboveSystem;

        Data(ProcessRecord proc, ApplicationInfo aInfo, boolean aboveSystem) {
            this.proc = proc;
            this.aInfo = aInfo;
            this.aboveSystem = aboveSystem;
        }
    }
}

