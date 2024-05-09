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

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

public class HandWritingView extends View {
    private static final String TAG = "HandWritingView";
    private static final int MSG_FLAG_SAVE = 0;
    private static final int CHANGE_BACKGROUND = 1;
    private Context mContext;
    private RkHandWritingJNI mNativeJNI;
    private Canvas mCanvas = null;
    private Bitmap mBitmap = null;
    private Path mPath;
    private Paint mPaint;
    private Paint mPenPaint;
    private Paint mEraserPaint;//橡皮擦
    private ArrayList<PointStruct> mPointList;//存储所有点的数据
    private ArrayList<PointStruct> mPathList;//存储单条线段的数据
    private ArrayList<ArrayList<PointStruct>> mNoteList;//存储所有线段的数据
    private ArrayList<ArrayList<PointStruct>> mRevokeNoteList;//存储撤销线段的数据
    private int mViewWidth, mViewHeight;
    private int mScreenWidth, mScreenHeight;
    private int mUIHeight;
    private int mLastX, mLastY;
    private Rect mRect;
    private int mPenColor, mPenWidth;
    private PenThread mPenThread;
    private int mCount = 0;
    private int mSize = 0;
    private boolean mIsStrokesEnable = false;
    private boolean mIsEraserEnable = false;
    public static boolean isUndoEnable = false;
    public static boolean isRedoEnable = false;

    private int[] mDrawableIDs = {/*R.drawable.background1, R.drawable.background2, R.drawable.background3,
            R.drawable.background4, R.drawable.background5, R.drawable.background6,
            R.drawable.background7, R.drawable.background8, R.drawable.background9,
            R.drawable.background10*/};
    private int mDrawable = 0;

    public HandWritingView(Context context) {
        super(context);
        mContext = context;
    }

    public HandWritingView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_FLAG_SAVE:
                    PointStruct pointStruct = (PointStruct) msg.obj;
                    if (pointStruct.action == PointStruct.ACTION_DOWN) {
                        mPathList = new ArrayList<PointStruct>();
                        mPathList.add(pointStruct);
                    } else if (pointStruct.action == PointStruct.ACTION_MOVE) {
                        mPathList.add(pointStruct);
                    } else if (pointStruct.action == PointStruct.ACTION_UP || pointStruct.action == PointStruct.ACTION_OUT) {
                        mPathList.add(pointStruct);
                        mNoteList.add(mPathList);
                    }
                    break;
                case CHANGE_BACKGROUND:
                    Drawable drawable = (Drawable) msg.obj;
                    setBackground(drawable);
                    break;
            }
        }
    };

    @Override
    protected void onDraw(android.graphics.Canvas canvas) {
        super.onDraw(canvas);
        Log.d(TAG, "onDraw: ");
        /*if(MainActivity.mInitFlag == 1) {
            mNativeJNI.native_clear();
        }*/
        if (mBitmap != null) {
            Log.d(TAG, "onDraw: drawBitmap");
            canvas.drawBitmap(mBitmap, 0, 0, null);
        }
    }

    /*@RequiresApi(api = Build.VERSION_CODES.N)
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        Log.d(TAG, "hasFocus: " + hasFocus);
        if(MainActivity.mInitFlag == 1) {
            if(!hasFocus) {
                mNativeJNI.native_clear();
                mNativeJNI.native_set_handwriting_enable(false);
            } else {
                mNativeJNI.native_set_handwriting_enable(true);
            }
        }
    }*/

    public int init(Rect rect, int screenWidth, int screenHeight, int UIHeight) {
        return init(rect, screenWidth, screenHeight, UIHeight, -1);
    }

    public int init(Rect rect, int screenWidth, int screenHeight, int UIHeight, int layerStack) {
        int initFlag = 0;
        mRect = rect;
        mScreenWidth = screenWidth;
        mScreenHeight = screenHeight;
        mViewWidth = Utils.ALIGN(getWidth(), 16);
        mViewHeight = getHeight();
        mUIHeight = UIHeight;
        mPenColor = PointStruct.PEN_BLACK_COLOR;
        mPenWidth = PointStruct.PEN_WIDTH_DEFAULT;
        //画笔
        mPenPaint = new Paint();
        mPenPaint.setStyle(Paint.Style.STROKE);
        mPenPaint.setAntiAlias(true);
        mPenPaint.setDither(true);
        mPenPaint.setStrokeWidth(PointStruct.PEN_WIDTH_DEFAULT);
        mPenPaint.setStrokeJoin(Paint.Join.ROUND);
        mPenPaint.setStrokeCap(Paint.Cap.ROUND);
        mPenPaint.setColor(Color.BLACK);
        mPaint = mPenPaint;
        //橡皮擦
        mEraserPaint = new Paint();
        //采用PorterDuff.Mode.CLEAR，图像覆盖处所有像素点的alpha和color都为0
        mEraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
        mEraserPaint.setStyle(Paint.Style.STROKE);
        //mEraserPaint.setAntiAlias(true);
        //mEraserPaint.setDither(true);
        mEraserPaint.setStrokeWidth(PointStruct.ERASER_WIDTH_DEFAULT);
        mEraserPaint.setStrokeJoin(Paint.Join.ROUND);
        //重点 解决擦除残留问题
        mEraserPaint.setStrokeCap(Paint.Cap.ROUND);
        mPointList = new ArrayList<PointStruct>();
        mPathList = new ArrayList<PointStruct>();
        mNoteList = new ArrayList<ArrayList<PointStruct>>();
        mRevokeNoteList = new ArrayList<ArrayList<PointStruct>>();
        mNativeJNI = new RkHandWritingJNI();
        mPenThread = new PenThread();
        initBitmap();
        mNativeJNI.setPointHandler(mPointHandler);
        mPenThread.start();
        initFlag = mNativeJNI.init(rect, mViewWidth, mViewHeight, mScreenWidth, mScreenHeight, layerStack);
        return initFlag;
    }

    private void initBitmap() {
        if (mBitmap == null) {
            //mBitmap = Bitmap.createBitmap(mScreenWidth, mScreenHeight, Bitmap.Config.ARGB_8888);
            mBitmap = Bitmap.createBitmap(mViewWidth, mViewHeight, Bitmap.Config.ARGB_8888);
        }
        if (mCanvas == null) {
            mCanvas = new Canvas();
            mCanvas.setBitmap(mBitmap);
        }
    }

    public void clear() {
        if (mBitmap != null) {
            mBitmap.eraseColor(Color.TRANSPARENT);
        }
        if (mPath != null) {
            mPath.reset();
        }
        if (mPathList != null) {
            mPathList.clear();
        }
        if (mNoteList != null) {
            mNoteList.clear();
        }
        if (mRevokeNoteList != null) {
            mRevokeNoteList.clear();
        }
        postInvalidate();
    }

    public void undo() {
        if (mNoteList.size() > 0) {
            mRevokeNoteList.add(mNoteList.get(mNoteList.size() - 1));
            Log.d(TAG, "mNoteList.size1: " + mNoteList.size());
            mNoteList.remove(mNoteList.size() - 1);
            Log.d(TAG, "mNoteList.size2: " + mNoteList.size());
            mBitmap.eraseColor(Color.TRANSPARENT);
            for (ArrayList<PointStruct> list : mNoteList) {
                for (PointStruct pointStruct : list) {
                    drawPoint(pointStruct);
                }
            }
            postInvalidate();
        } else {
            Log.d(TAG, "mNoteList.size <= 0 ");
        }
    }

    public void reDo() {
        if (mRevokeNoteList.size() > 0) {
            Log.d(TAG, "mNoteList.size1: " + mNoteList.size());
            mNoteList.add(mRevokeNoteList.get(mRevokeNoteList.size() - 1));
            Log.d(TAG, "mNoteList.size2: " + mNoteList.size());
            mRevokeNoteList.remove(mRevokeNoteList.size() - 1);
            mBitmap.eraseColor(Color.TRANSPARENT);
            for (ArrayList<PointStruct> list : mNoteList) {
                for (PointStruct pointStruct : list) {
                    drawPoint(pointStruct);
                }
            }
            postInvalidate();
        } else {
            Log.d(TAG, "mRevokeNoteList.size <= 0 ");
        }
    }

    public void saveWritingDataEvent(PointStruct pointStruct) {
        Message msg = mHandler.obtainMessage();
        msg.what = MSG_FLAG_SAVE;
        msg.obj = (Object) pointStruct;
        msg.sendToTarget();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int x = (int) event.getX();
        int y = (int) event.getY();
        int pressedValue = (int) event.getPressure();
        int action = event.getAction();
        PointStruct pointEvent = new PointStruct(x, y, pressedValue, action);
        draw(pointEvent);
        return true;
    }

    public void draw(PointStruct pointEvent) {
        if (mBitmap == null || mCanvas == null) {
            Log.d(TAG, "mBitmap == null || mCanvas == null ");
            return;
        }
        Log.d(TAG, "mIsEraserEnable: " + mIsEraserEnable + ",mPenColor:" + mPenColor);
        setPaint(mIsEraserEnable, mPenColor, getPenWidth(pointEvent.pressedValue));
        PointStruct pointStruct = new PointStruct(mLastX, mLastY, pointEvent.x, pointEvent.y,
                pointEvent.pressedValue, mPenColor, mPenWidth, pointEvent.action,
                mIsEraserEnable, mIsStrokesEnable);
        switch (pointEvent.action) {
            case MotionEvent.ACTION_DOWN:
                Log.d(TAG, "ACTION_DOWN: ");
                mCanvas.drawPoint(pointEvent.x, pointEvent.y, mPaint);
                mNativeJNI.drawBitmap(mBitmap);
                mLastX = pointEvent.x;
                mLastY = pointEvent.y;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_OUTSIDE:
                Log.d(TAG, "ACTION_UP: ");
                mLastX = 0;
                mLastY = 0;
                break;
            case MotionEvent.ACTION_MOVE:
                Log.d(TAG, "ACTION_MOVE mLastX:" + mLastX + ",mLastY:" + mLastY +
                        ",x:" + pointEvent.x + ",y:" + pointEvent.y);
                mCanvas.drawLine(mLastX, mLastY, pointEvent.x, pointEvent.y, mPaint);
                Log.d(TAG, "drawLine done.");
                mNativeJNI.drawBitmap(mBitmap);
                mLastX = pointEvent.x;
                mLastY = pointEvent.y;
                postInvalidate();
                break;
        }
        saveWritingDataEvent(pointStruct);
    }

    public void drawPoint(PointStruct pointStruct) {
        if (mBitmap == null || mCanvas == null) {
            Log.d(TAG, "mBitmap == null || mCanvas == null ");
        }
        Log.d(TAG, "mIsEraserEnable: " + mIsEraserEnable);
        setPaint(pointStruct.eraserEnable, pointStruct.penColor, getPenWidth(pointStruct.pressedValue));
        switch (pointStruct.action) {
            case MotionEvent.ACTION_DOWN:
                //Log.d(TAG, "ACTION_DOWN: ");
                mCanvas.drawPoint(pointStruct.x, pointStruct.y, mPaint);
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_OUTSIDE:
                //Log.d(TAG, "ACTION_UP: ");
                postInvalidate();
                break;
            case MotionEvent.ACTION_MOVE:
                //Log.d(TAG, "ACTION_MOVE mLastX:" + pointStruct.lastX + ",mLastY:" + pointStruct.lastY +
                //",x:" + pointStruct.x + ",y:" + pointStruct.y);
                mCanvas.drawLine(pointStruct.lastX, pointStruct.lastY, pointStruct.x, pointStruct.y, mPaint);
                break;
        }
    }

    public void setPaint(boolean isEraserEnable, int penColor, int penWidth) {
        if (isEraserEnable) {
            mPaint = mEraserPaint;
        } else {
            setPenColor(penColor);
            mPenPaint.setStrokeWidth(penWidth);
            mPaint = mPenPaint;
        }
    }

    public void setIsStrokes(boolean isStrokes) {
        mIsStrokesEnable = isStrokes;
    }

    public int getPenWidth(int pressedValue) {
        int penWidth = PointStruct.PEN_WIDTH_DEFAULT;
        if (mIsStrokesEnable) {
            if (pressedValue <= 2000) {
                penWidth = 1;
            } else if (pressedValue > 2000 && pressedValue <= 3200) {
                penWidth = (int) (Math.ceil((pressedValue - 2000) / 300) + 1);
            } else if (pressedValue > 3200) {
                penWidth = (int) (Math.ceil((pressedValue - 3200) / 200) + 5);
            }
        }
        return penWidth;
    }

    public void setIsEraser(boolean isEraser) {
        mIsEraserEnable = isEraser;
    }

    public void changeBackground() {
        Drawable drawable = getResources().getDrawable(mDrawableIDs[mDrawable]);
        Message changeBGMessage = new Message();
        changeBGMessage.what = CHANGE_BACKGROUND;
        changeBGMessage.obj = drawable;
        mHandler.sendMessage(changeBGMessage);
        mDrawable++;
        if (mDrawable >= 10)
            mDrawable = 0;
    }

    public void setPenColor(int penColor) {
        mPenColor = penColor;
        Log.d(TAG, "penColor:" + penColor + " + mPenColor" + mPenColor);
        if (penColor == PointStruct.PEN_BLACK_COLOR) {
            mPenPaint.setColor(Color.BLACK);
        } else if (penColor == PointStruct.PEN_BLUE_COLOR) {
            mPenPaint.setColor(Color.BLUE);
        } else if (penColor == PointStruct.PEN_GREEN_COLOR) {
            mPenPaint.setColor(Color.GREEN);
        } else if (penColor == PointStruct.PEN_RED_COLOR) {
            mPenPaint.setColor(Color.RED);
        } else if (penColor == PointStruct.PEN_WHITE_COLOR) {
            mPenPaint.setColor(Color.WHITE);
        }
    }

    public void savePointInfo(String path, String pictureName) {
        FileOutputStream fileOutputStream = null;
        ObjectOutputStream objectOutputStream = null;
        try {
            File appDir = new File(Environment.getExternalStorageDirectory(), path);
            if (!appDir.exists()) {
                Log.d(TAG, "appDir: " + appDir.mkdirs());
            }
            String fileName = pictureName + ".txt";
            File file = new File(appDir, fileName);
            fileOutputStream = new FileOutputStream(file.toString());  //新建一个内容为空的文件
            objectOutputStream = new ObjectOutputStream(fileOutputStream);
            Log.d(TAG, "mNoteList.size: " + mNoteList.size());
            objectOutputStream.writeObject(mNoteList);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (objectOutputStream != null) {
            try {
                objectOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (fileOutputStream != null) {
            try {
                fileOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private Handler mPointHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            int what = msg.what;
            if (what == RkHandWritingJNI.MSG_FLAG_DRAW) {
                PointStruct pointStruct = (PointStruct) msg.obj;
                if (mPointList.add(pointStruct)) {
                    //Log.d(TAG, "---message---x:" + pointStruct.x + ",y:" + pointStruct.y + ",action:"
                    //+ pointStruct.action);
                    mSize++;
                }
                if (pointStruct.action == PointStruct.ACTION_DOWN) {
                    mPathList = new ArrayList<PointStruct>();
                    mPathList.add(pointStruct);
                } else if (pointStruct.action == PointStruct.ACTION_MOVE) {
                    if (mPathList != null && mPathList.size() > 0) {
                        mPathList.add(pointStruct);
                    }
                } else if (pointStruct.action == PointStruct.ACTION_UP || pointStruct.action == PointStruct.ACTION_OUT) {
                    if (mPathList != null && mPathList.size() > 0) {
                        mPathList.add(pointStruct);
                        mNoteList.add(mPathList);
                        //Log.d(TAG, "mNoteList: " + mNoteList.size());
                        mPathList = null;
                    }
                }
            }
        }
    };

    private class PenThread extends Thread {
        public boolean canRun = true;

        public PenThread() {
        }

        public void setEnable(boolean enable) {
            canRun = enable;
        }

        @Override
        public void run() {
            while (canRun) {
                int size = mPointList.size();
                if (size > mCount) {
                    try {
                        Thread.sleep(5);
                    } catch (InterruptedException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    while ((mPointList.size() - mCount) > 0) {
                        //Log.d(TAG, "mPointList.size(): " + mPointList.size());
                        //Log.d(TAG, "mCount: " + mCount);
                        PointStruct pointStruct = mPointList.get(mCount);
                        //Log.d(TAG, "---penThread---x,y,action: " + pointStruct.x + "," +pointStruct.y + "," + pointStruct.action);
                        if (pointStruct != null) {
                            //Android层同步绘制
                            drawPoint(pointStruct);
                        }
                        mCount++;
                    }
                }
                try {
                    Thread.sleep(5);
                } catch (InterruptedException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }
    }

}
