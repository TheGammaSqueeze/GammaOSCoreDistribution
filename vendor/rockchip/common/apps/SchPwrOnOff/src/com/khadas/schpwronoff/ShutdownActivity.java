package com.khadas.schpwronoff;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.PowerManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Window;
import android.view.WindowManager;

import android.util.Log;

public class ShutdownActivity extends Activity {
    private static final String TAG = "ShutdownActivity";
    public static CountDownTimer sCountDownTimer = null;
    private String mMessage;
    private int mSecondsCountdown;
    private TelephonyManager mTelephonyManager;
    private static final int DIALOG = 1;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SchPwrWakeLock.acquireCpuWakeLock(this);
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        Log.d(TAG, "screen is on ? ----- " + pm.isScreenOn());

        mTelephonyManager = (TelephonyManager) getSystemService(Context.TELEPHONY_SERVICE);
        final Window win = getWindow();
        win.addFlags(WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);

        final int countSeconds = 11;
        final int millisSeconds = 1000;
        if (savedInstanceState == null) {
            mSecondsCountdown = countSeconds;
        } else {
            mSecondsCountdown = savedInstanceState.getInt("lefttime");
            mMessage = savedInstanceState.getString("message");
        }
        sCountDownTimer = new CountDownTimer(mSecondsCountdown * millisSeconds, millisSeconds) {
            @Override
            public void onTick(long millisUntilFinished) {
                mSecondsCountdown = (int) (millisUntilFinished / millisSeconds);
                if (mSecondsCountdown > 1) {
                    mMessage = getString(R.string.schpwr_shutdown_message, mSecondsCountdown);
                } else {
                    mMessage = getString(R.string.schpwr_shutdown_message_second, mSecondsCountdown);
                }
                Log.d(TAG, "showDialog time = " + millisUntilFinished / millisSeconds);
                Log.d(TAG, "isFinishing() = " + isFinishing());
                if (!isFinishing()) {
                    showDialog(DIALOG);
                }
            }

            @Override
            public void onFinish() {
                if (mTelephonyManager.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                    Log.d(TAG, "phone is incall, countdown end");
                    SchPwrWakeLock.releaseCpuWakeLock();
                    finish();
                } else {
                    Log.d(TAG, "count down timer arrived, shutdown phone");
                    fireShutDown();
                    sCountDownTimer = null;
                }
            }
        };

        Log.d(TAG, "ShutdonwActivity onCreate");
        if (sCountDownTimer == null) {
            SchPwrWakeLock.releaseCpuWakeLock();
            finish();
        } else {
            sCountDownTimer.start();
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt("lefttime", mSecondsCountdown);
        outState.putString("message", mMessage);
    }

    private void cancelCountDownTimer() {
        if (sCountDownTimer != null) {
            Log.d(TAG, "cancel sCountDownTimer");
            sCountDownTimer.cancel();
            sCountDownTimer = null;
        }
    }

    @Override
    protected Dialog onCreateDialog(int id) {
         Log.d(TAG, "onCreateDialog");
        AlertDialog dialog = new AlertDialog.Builder(this)
                .setCancelable(false).setIcon(android.R.drawable.ic_dialog_alert)
                .setTitle(com.android.internal.R.string.power_off).setMessage(mMessage)
                .setPositiveButton(com.android.internal.R.string.yes, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelCountDownTimer();
                        fireShutDown();
                    }
                }).setNegativeButton(com.android.internal.R.string.no, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        cancelCountDownTimer();
                        SchPwrWakeLock.releaseCpuWakeLock();
                        finish();
                    }
                }).create();
        if (!getResources().getBoolean(com.android.internal.R.bool.config_sf_slowBlur)) {
            dialog.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        }
        

        return dialog;
    }

    @Override
    protected void onPrepareDialog(int id, Dialog dialog) {
        ((AlertDialog) dialog).setMessage(mMessage);
    }

    private void fireShutDown() {
        Intent intent = new Intent(Intent.ACTION_REQUEST_SHUTDOWN);
        intent.putExtra(Intent.EXTRA_KEY_CONFIRM, false);
        intent.setFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
    }
}
