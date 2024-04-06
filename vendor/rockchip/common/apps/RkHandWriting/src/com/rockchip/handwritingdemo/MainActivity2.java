/*
 * Copyright 2023 Rockchip Electronics S.LSI Co. LTD
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

package com.rockchip.handwritingdemo;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.Spinner;
import android.widget.Toast;

import static java.lang.Thread.sleep;

public class MainActivity2 extends Activity {
    private static final String TAG = "MainActivity";
    //evb配置 具体参考dumpsys input中的配置
    public static final float mXScale = 1.498f;
    public static final float mYScale = 1.499f;
    //平板配置 具体参考dumpsys input中的配置
    //public static final float mXScale = 1.000f;
    //public static final float mYScale = 0.999f;
    //大屏配置 具体参考dumpsys input中的配置
    //public static final float mXScale = 0.117f;
    //public static final float mYScale = 0.066f;
    //wacom配置 具体参考dumpsys input中的配置
    //public static final float mXScale = 0.104f;
    //public static final float mYScale = 0.104f;
    //others
    //public static final float mXScale = 0.234f;
    //public static final float mYScale = 0.225f;
    private Context mContext;
    private RkHandWritingJNI mNativeJNI;
    private SettingManager mSettingManager;
    private Handler mHandler;
    private Button mExitButton;
    private Button mUndoButton, mRedoButton;
    private Button mClearButton;
    private Button mBackgroundBtn;
    private Button mSaveButton;
    private Spinner mPenColorSp;
    private CheckBox mSmoothPenCheck, mEraserCheck;
    private HandWritingView mView;
    public static int mScreenHWithoutBar;
    public static int mScreenH;
    public static int mScreenW;
    public static int mUIHeight;
    private int mLeft;
    private int mTop;
    private int mRight;
    private int mBottom;
    public static final String RESERVED_PLANE = "vendor.hwc.reserved_plane_name";
    public static final String PLANE_NAME = "Esmart0-win0";
    private boolean buttonLock = false;
    public static int mInitFlag = 0;
    public static boolean mIsInMultiWindowMode = false;
    private int mLastX, mLastY;
    private int mPenColor, mPenWidth;
    private boolean mIsStrokesEnable = false;
    private boolean mIsEraserEnable = false;
    private Paint mPaint;
    private Canvas mCanvas = null;
    private Bitmap mBitmap = null;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,};

    private Runnable mRunnable = new Runnable() {
        public void run() {
            int count = 0;
            while (mView.getHeight() <= 0) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
                if (count++ > 40) {
                    Log.d(TAG, "Flash test : ++++++++ removeCallbacks");
                    mHandler.removeCallbacks(mRunnable);
                    System.exit(0);
                }
                Log.d(TAG, "Flash test : ++++++++ mView.getHeight() = " + mView.getHeight() + ", count = " + count);
            }
            mUIHeight = mScreenHWithoutBar - mView.getHeight();
            mLeft = 0;
            mTop = mUIHeight;
            mRight = mScreenW;
            mBottom = mScreenHWithoutBar;
            Log.d(TAG, "mViewWidth:" + mView.getWidth() + "mViewHeight:" + mView.getHeight());
            mInitFlag = mView.init(new Rect(mLeft, mTop, mRight, mBottom), mScreenW, mScreenH, mUIHeight);
        }
    };


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //判断权限是否已获取
        if (PermissionUtils.isGrantPermission(getApplicationContext(), PERMISSIONS_STORAGE)) {
            //加载资源
            Log.d(TAG, "PERMISSIONS_STORAGE: ");
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            requestPermissions(PERMISSIONS_STORAGE, 1);
        }
        mContext = getBaseContext();
        mView = (HandWritingView) findViewById(R.id.handwriting_view);
        mNativeJNI = new RkHandWritingJNI();
        mSettingManager = new SettingManager();
        mSaveButton = (Button) findViewById(R.id.save);
        mSaveButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread() {
                    @Override
                    public void run() {
                        if (!buttonLock) {
                            buttonLock = true;
                            if (mInitFlag == 1) {
                                mNativeJNI.native_clear();
                            }
                            buttonLock = false;
                        }
                    }
                }.start();
            }
        });
        mBackgroundBtn = (Button) findViewById(R.id.background);
        mBackgroundBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                new Thread() {
                    @Override
                    public void run() {
                        if (!buttonLock) {
                            buttonLock = true;
                            mView.changeBackground();
                            buttonLock = false;
                        }
                    }
                }.start();
            }
        });
        mExitButton = (Button) findViewById(R.id.exit);
        mExitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        mUndoButton = (Button) findViewById(R.id.undo);
        mUndoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread() {
                    @Override
                    public void run() {
                        if (!buttonLock) {
                            buttonLock = true;
                            if (mInitFlag == 1) {
                                mNativeJNI.native_clear();
                                mView.isUndoEnable = true;
                                mView.undo();
                                mView.isUndoEnable = false;
                            }
                            buttonLock = false;
                        }
                    }
                }.start();
            }
        });
        mRedoButton = (Button) findViewById(R.id.redo);
        mRedoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread() {
                    @Override
                    public void run() {
                        if (!buttonLock) {
                            buttonLock = true;
                            if (mInitFlag == 1) {
                                mNativeJNI.native_clear();
                                mView.isRedoEnable = true;
                                mView.reDo();
                                mView.isRedoEnable = false;
                            }
                            buttonLock = false;
                        }
                    }
                }.start();
            }
        });
        mClearButton = (Button) findViewById(R.id.clear);
        mClearButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                new Thread() {
                    @Override
                    public void run() {
                        if (!buttonLock) {
                            buttonLock = true;
                            if (mInitFlag == 1) {
                                mView.clear();
                                mNativeJNI.native_clear();
                            }
                            buttonLock = false;
                        }
                    }
                }.start();
            }
        });
        mPenColorSp = (Spinner) findViewById(R.id.pen_color_spinner);
        mPenColorSp.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int pos, long id) {
                if (mInitFlag == 1) {
                    switch (pos) {
                        case 0:
                            mView.setPenColor(PointStruct.PEN_BLACK_COLOR);
                            break;
                        case 1:
                            mView.setPenColor(PointStruct.PEN_BLUE_COLOR);
                            break;
                        case 2:
                            mView.setPenColor(PointStruct.PEN_GREEN_COLOR);
                            break;
                        case 3:
                            mView.setPenColor(PointStruct.PEN_RED_COLOR);
                            break;
                        case 4:
                            mView.setPenColor(PointStruct.PEN_WHITE_COLOR);
                            break;
                    }
                }

            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });
        /*mSmoothPenCheck = (CheckBox) findViewById(R.idt.smooth);
        mSmoothPenCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(mInitFlag == 1) {
                    if(isChecked) {
                        mNativeJNI.native_set_smooth_pen_enable(true);
                    } else {
                        mNativeJNI.native_set_smooth_pen_enable(false);
                    }
                }
            }
        });*/
        mEraserCheck = (CheckBox) findViewById(R.id.eraser);
        mEraserCheck.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (mInitFlag == 1) {
                    if (isChecked) {
                        mView.setIsEraser(true);
                    } else {
                        mView.setIsEraser(false);
                    }
                }
            }
        });
        mExitButton = (Button) findViewById(R.id.exit);
        mExitButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                finish();
            }
        });
        DisplayMetrics metrics = new DisplayMetrics();
        metrics = getApplicationContext().getResources().getDisplayMetrics();
        mScreenW = metrics.widthPixels;
        mScreenHWithoutBar = metrics.heightPixels;
        Log.d(TAG, "---zc--- mScreenHWithoutBar:" + mScreenHWithoutBar);
        mScreenH = getRealHeight(mContext);
        mSettingManager.setProperty(RESERVED_PLANE, PLANE_NAME);
        mHandler = new Handler();
        mHandler.postDelayed(mRunnable, 100);
        /*if(!isInMultiWindowMode()) {
            Log.d(TAG, "---zc--- isInMultiWindowMode:" + isInMultiWindowMode());
            mHandler = new Handler();
            mHandler.postDelayed(mRunnable, 50);
        }*/
    }

    @Override
    public void onResume() {
        super.onResume();
        Log.d(TAG, "---zc--- onResume()");
        /*if (mInitFlag == 0) {
            mHandler = new Handler();
            mHandler.postDelayed(mRunnable, 100);
        }
        mIsInMultiWindowMode = isInMultiWindowMode();*/
        mSettingManager.setProperty(RESERVED_PLANE, PLANE_NAME);
        if (mInitFlag == 1) {
            Log.d(TAG, "native_set_handwriting_enable: ");
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        Log.d(TAG, "---zc--- onPause(),mInitFlag:" + mInitFlag);
        mSettingManager.setProperty(RESERVED_PLANE, null);
        if (mInitFlag == 1) {
            mNativeJNI.native_clear();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Log.d(TAG, "---zc--- onDestroy()");
        mView.clear();
        if (mInitFlag == 1) {
            mNativeJNI.native_clear();
            mNativeJNI.native_exit();
            if (!isInMultiWindowMode()) {
                Log.d(TAG, "native_exit: ");
                mNativeJNI.native_exit();
            }
            //重置initFlag
            mInitFlag = 0;
        }
        mSettingManager.setProperty(RESERVED_PLANE, null);
    }

    @Override
    public void setRequestedOrientation(int requestedOrientation) {
        Log.d(TAG, "setRequestedOrientation: " + requestedOrientation);
        super.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE);
    }

    @Override
    public void onMultiWindowModeChanged(boolean isInMultiWindowMode) {
        super.onMultiWindowModeChanged(isInMultiWindowMode);
        Log.d(TAG, "---zc--- isInMultiWindowMode:" + isInMultiWindowMode);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        Log.d(TAG, "---zc--- newConfig:" + newConfig.getLayoutDirection());
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        Log.d(TAG, "keyCode: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (mInitFlag == 1) {
                //mNativeJNI.native_set_handwriting_enable(false);
                mNativeJNI.native_clear();
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        switch (requestCode) {
            case 1:
                if (null == grantResults || grantResults.length < 1) {
                    Toast.makeText(this, "请手动打开storage权限", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    for (int grantResult : grantResults) {
                        if (grantResult != PackageManager.PERMISSION_GRANTED) {
                            Toast.makeText(this, "请手动打开storage权限", Toast.LENGTH_SHORT).show();
                            finish();
                            return;
                        }
                    }
                }

                break;
            default:
                break;
        }
    }

    public static int getRealHeight(Context context) {
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        int screenHeight = 0;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            DisplayMetrics dm = new DisplayMetrics();
            display.getRealMetrics(dm);
            screenHeight = dm.heightPixels;

            //或者也可以使用getRealSize方法
//            Point size = new Point();
//            display.getRealSize(size);
//            screenHeight = size.y;
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
            try {
                screenHeight = (Integer) Display.class.getMethod("getRawHeight").invoke(display);
            } catch (Exception e) {
                DisplayMetrics dm = new DisplayMetrics();
                display.getMetrics(dm);
                screenHeight = dm.heightPixels;
            }
        }
        return screenHeight;
    }
}
