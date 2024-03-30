/*
 * Copyright (C) 2013 The Android Open Source Project
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

package com.android.cts.verifier.camera.its;

import static android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ServiceInfo;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.HardwareBuffer;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.SensorPrivacyManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.DngCreator;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.cts.CameraTestUtils;
import android.hardware.camera2.cts.PerformanceTest;
import android.hardware.camera2.params.DynamicRangeProfiles;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.MeteringRectangle;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.AudioAttributes;
import android.media.CamcorderProfile;
import android.media.Image;
import android.media.ImageReader;
import android.media.ImageWriter;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaRecorder;
import android.os.Build;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.util.Pair;
import android.util.Rational;
import android.util.Size;
import android.util.SparseArray;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.InstrumentationRegistry;

import com.android.compatibility.common.util.ReportLog.Metric;
import com.android.cts.verifier.R;
import com.android.cts.verifier.camera.performance.CameraTestInstrumentation;
import com.android.cts.verifier.camera.performance.CameraTestInstrumentation.MetricListener;
import com.android.ex.camera2.blocking.BlockingCameraManager;
import com.android.ex.camera2.blocking.BlockingCameraManager.BlockingOpenException;
import com.android.ex.camera2.blocking.BlockingSessionCallback;
import com.android.ex.camera2.blocking.BlockingStateCallback;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.runner.JUnitCore;
import org.junit.runner.Request;
import org.junit.runner.Result;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class ItsService extends Service implements SensorEventListener {
    public static final String TAG = ItsService.class.getSimpleName();

    // Version number to keep host/server communication in sync
    // This string must be in sync with python side device.py
    // Updated when interface between script and ItsService is changed
    private final String ITS_SERVICE_VERSION = "1.0";

    private final int SERVICE_NOTIFICATION_ID = 37; // random int that is unique within app
    private NotificationChannel mChannel;

    // Timeouts, in seconds.
    private static final int TIMEOUT_CALLBACK = 20;
    private static final int TIMEOUT_3A = 10;

    // Time given for background requests to warm up pipeline
    private static final long PIPELINE_WARMUP_TIME_MS = 2000;

    // State transition timeouts, in ms.
    private static final long TIMEOUT_IDLE_MS = 2000;
    private static final long TIMEOUT_STATE_MS = 500;
    private static final long TIMEOUT_SESSION_CLOSE = 3000;

    // Timeout to wait for a capture result after the capture buffer has arrived, in ms.
    private static final long TIMEOUT_CAP_RES = 2000;

    private static final int MAX_CONCURRENT_READER_BUFFERS = 10;

    // Supports at most RAW+YUV+JPEG, one surface each, plus optional background stream
    private static final int MAX_NUM_OUTPUT_SURFACES = 4;

    // Performance class R version number
    private static final int PERFORMANCE_CLASS_R = Build.VERSION_CODES.R;

    public static final int SERVERPORT = 6000;

    public static final String REGION_KEY = "regions";
    public static final String REGION_AE_KEY = "ae";
    public static final String REGION_AWB_KEY = "awb";
    public static final String REGION_AF_KEY = "af";
    public static final String LOCK_AE_KEY = "aeLock";
    public static final String LOCK_AWB_KEY = "awbLock";
    public static final String TRIGGER_KEY = "triggers";
    public static final String PHYSICAL_ID_KEY = "physicalId";
    public static final String TRIGGER_AE_KEY = "ae";
    public static final String TRIGGER_AF_KEY = "af";
    public static final String VIB_PATTERN_KEY = "pattern";
    public static final String EVCOMP_KEY = "evComp";
    public static final String AUTO_FLASH_KEY = "autoFlash";
    public static final String AUDIO_RESTRICTION_MODE_KEY = "mode";
    public static final int AVAILABILITY_TIMEOUT_MS = 10;

    private static final HashMap<Integer, String> CAMCORDER_PROFILE_QUALITIES_MAP;
    static {
        CAMCORDER_PROFILE_QUALITIES_MAP = new HashMap<Integer, String>();
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_480P, "480P");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_1080P, "1080P");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_2160P, "2160P");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_2K, "2k");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_4KDCI, "4KDC");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_720P, "720P");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_8KUHD, "8KUHD");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_CIF, "CIF");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_HIGH, "HIGH");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_LOW, "LOW");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_QCIF, "QCIF");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_QHD, "QHD");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_QVGA, "QVGA");
        CAMCORDER_PROFILE_QUALITIES_MAP.put(CamcorderProfile.QUALITY_VGA, "VGA");
    };

    private CameraManager mCameraManager = null;
    private HandlerThread mCameraThread = null;
    private Handler mCameraHandler = null;
    private BlockingCameraManager mBlockingCameraManager = null;
    private BlockingStateCallback mCameraListener = null;
    private CameraDevice mCamera = null;
    private CameraCaptureSession mSession = null;
    private ImageReader[] mOutputImageReaders = null;
    private SparseArray<String> mPhysicalStreamMap = new SparseArray<String>();
    private SparseArray<Long> mStreamUseCaseMap = new SparseArray<Long>();
    private ImageReader mInputImageReader = null;
    private CameraCharacteristics mCameraCharacteristics = null;
    private HashMap<String, CameraCharacteristics> mPhysicalCameraChars =
            new HashMap<String, CameraCharacteristics>();
    private ItsUtils.ItsCameraIdList mItsCameraIdList = null;

    private Vibrator mVibrator = null;

    private HandlerThread mSaveThreads[] = new HandlerThread[MAX_NUM_OUTPUT_SURFACES];
    private Handler mSaveHandlers[] = new Handler[MAX_NUM_OUTPUT_SURFACES];
    private HandlerThread mResultThread = null;
    private Handler mResultHandler = null;

    private volatile boolean mThreadExitFlag = false;

    private volatile ServerSocket mSocket = null;
    private volatile SocketRunnable mSocketRunnableObj = null;
    private Semaphore mSocketQueueQuota = null;
    private int mMemoryQuota = -1;
    private LinkedList<Integer> mInflightImageSizes = new LinkedList<>();
    private volatile BlockingQueue<ByteBuffer> mSocketWriteQueue =
            new LinkedBlockingDeque<ByteBuffer>();
    private final Object mSocketWriteEnqueueLock = new Object();
    private final Object mSocketWriteDrainLock = new Object();

    private volatile BlockingQueue<Object[]> mSerializerQueue =
            new LinkedBlockingDeque<Object[]>();

    private AtomicInteger mCountCallbacksRemaining = new AtomicInteger();
    private AtomicInteger mCountRawOrDng = new AtomicInteger();
    private AtomicInteger mCountRaw10 = new AtomicInteger();
    private AtomicInteger mCountRaw12 = new AtomicInteger();
    private AtomicInteger mCountJpg = new AtomicInteger();
    private AtomicInteger mCountYuv = new AtomicInteger();
    private AtomicInteger mCountCapRes = new AtomicInteger();
    private boolean mCaptureRawIsDng;
    private boolean mCaptureRawIsStats;
    private int mCaptureStatsGridWidth;
    private int mCaptureStatsGridHeight;
    private CaptureResult mCaptureResults[] = null;
    private MediaRecorder mMediaRecorder;
    private Surface mRecordSurface;
    private CaptureRequest.Builder mCaptureRequestBuilder;

    private volatile ConditionVariable mInterlock3A = new ConditionVariable(true);

    final Object m3AStateLock = new Object();
    private volatile boolean mConvergedAE = false;
    private volatile boolean mPrecaptureTriggered = false;
    private volatile boolean mConvergeAETriggered = false;
    private volatile boolean mConvergedAF = false;
    private volatile boolean mConvergedAWB = false;
    private volatile boolean mLockedAE = false;
    private volatile boolean mLockedAWB = false;
    private volatile boolean mNeedsLockedAE = false;
    private volatile boolean mNeedsLockedAWB = false;
    private volatile boolean mDoAE = true;
    private volatile boolean mDoAF = true;
    private final LinkedBlockingQueue<String> unavailableEventQueue = new LinkedBlockingQueue<>();
    private final LinkedBlockingQueue<Pair<String, String>> unavailablePhysicalCamEventQueue =
                new LinkedBlockingQueue<>();
    private Set<String> mUnavailablePhysicalCameras;


    class MySensorEvent {
        public Sensor sensor;
        public int accuracy;
        public long timestamp;
        public float values[];
    }

    CameraManager.AvailabilityCallback ac = new CameraManager.AvailabilityCallback() {
        @Override
        public void onCameraAvailable(String cameraId) {
            super.onCameraAvailable(cameraId);
        }

        @Override
        public void onCameraUnavailable(String cameraId) {
            super.onCameraUnavailable(cameraId);
            unavailableEventQueue.offer(cameraId);
        }

        @Override
        public void onPhysicalCameraAvailable(String cameraId, String physicalCameraId) {
            super.onPhysicalCameraAvailable(cameraId, physicalCameraId);
            unavailablePhysicalCamEventQueue.remove(new Pair<>(cameraId, physicalCameraId));
        }

        @Override
        public void onPhysicalCameraUnavailable(String cameraId, String physicalCameraId) {
            super.onPhysicalCameraUnavailable(cameraId, physicalCameraId);
            unavailablePhysicalCamEventQueue.offer(new Pair<>(cameraId, physicalCameraId));
        }
    };

    static class VideoRecordingObject {
        private static final int INVALID_FRAME_RATE = -1;

        public String recordedOutputPath;
        public String quality;
        public Size videoSize;
        public int videoFrameRate; // -1 implies video framerate was not set by the test
        public int fileFormat;

        public VideoRecordingObject(String recordedOutputPath,
                String quality, Size videoSize, int videoFrameRate, int fileFormat) {
            this.recordedOutputPath = recordedOutputPath;
            this.quality = quality;
            this.videoSize = videoSize;
            this.videoFrameRate = videoFrameRate;
            this.fileFormat = fileFormat;
        }

        VideoRecordingObject(String recordedOutputPath, String quality, Size videoSize,
                int fileFormat) {
            this(recordedOutputPath, quality, videoSize, INVALID_FRAME_RATE, fileFormat);
        }

        public boolean isFrameRateValid() {
            return videoFrameRate != INVALID_FRAME_RATE;
        }
    }

    // For capturing motion sensor traces.
    private SensorManager mSensorManager = null;
    private Sensor mAccelSensor = null;
    private Sensor mMagSensor = null;
    private Sensor mGyroSensor = null;
    private volatile LinkedList<MySensorEvent> mEvents = null;
    private volatile Object mEventLock = new Object();
    private volatile boolean mEventsEnabled = false;
    private HandlerThread mSensorThread = null;
    private Handler mSensorHandler = null;

    private SensorPrivacyManager mSensorPrivacyManager;

    // Camera test instrumentation
    private CameraTestInstrumentation mCameraInstrumentation;
    // Camera PerformanceTest metric
    private final ArrayList<Metric> mResults = new ArrayList<Metric>();

    private static final int SERIALIZER_SURFACES_ID = 2;
    private static final int SERIALIZER_PHYSICAL_METADATA_ID = 3;

    public interface CaptureCallback {
        void onCaptureAvailable(Image capture, String physicalCameraId);
    }

    public abstract class CaptureResultListener extends CameraCaptureSession.CaptureCallback {}

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        try {
            mThreadExitFlag = false;

            // Get handle to camera manager.
            mCameraManager = (CameraManager) this.getSystemService(Context.CAMERA_SERVICE);
            if (mCameraManager == null) {
                throw new ItsException("Failed to connect to camera manager");
            }
            mBlockingCameraManager = new BlockingCameraManager(mCameraManager);
            mCameraListener = new BlockingStateCallback();

            // Register for motion events.
            mEvents = new LinkedList<MySensorEvent>();
            mSensorManager = (SensorManager)getSystemService(Context.SENSOR_SERVICE);
            mAccelSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
            mMagSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
            mGyroSensor = mSensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
            mSensorThread = new HandlerThread("SensorThread");
            mSensorThread.start();
            mSensorHandler = new Handler(mSensorThread.getLooper());
            mSensorManager.registerListener(this, mAccelSensor,
                    /*100hz*/ 10000, mSensorHandler);
            mSensorManager.registerListener(this, mMagSensor,
                    SensorManager.SENSOR_DELAY_NORMAL, mSensorHandler);
            mSensorManager.registerListener(this, mGyroSensor,
                    /*200hz*/5000, mSensorHandler);

            // Get a handle to the system vibrator.
            mVibrator = (Vibrator)getSystemService(Context.VIBRATOR_SERVICE);

            // Create threads to receive images and save them.
            for (int i = 0; i < MAX_NUM_OUTPUT_SURFACES; i++) {
                mSaveThreads[i] = new HandlerThread("SaveThread" + i);
                mSaveThreads[i].start();
                mSaveHandlers[i] = new Handler(mSaveThreads[i].getLooper());
            }

            // Create a thread to handle object serialization.
            (new Thread(new SerializerRunnable())).start();;

            // Create a thread to receive capture results and process them.
            mResultThread = new HandlerThread("ResultThread");
            mResultThread.start();
            mResultHandler = new Handler(mResultThread.getLooper());

            // Create a thread for the camera device.
            mCameraThread = new HandlerThread("ItsCameraThread");
            mCameraThread.start();
            mCameraHandler = new Handler(mCameraThread.getLooper());

            // Create a thread to process commands, listening on a TCP socket.
            mSocketRunnableObj = new SocketRunnable();
            (new Thread(mSocketRunnableObj)).start();
        } catch (ItsException e) {
            Logt.e(TAG, "Service failed to start: ", e);
        }

        NotificationManager notificationManager =
                (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        mChannel = new NotificationChannel(
                "ItsServiceChannel", "ItsService", NotificationManager.IMPORTANCE_LOW);
        // Configure the notification channel.
        mChannel.setDescription("ItsServiceChannel");
        mChannel.enableVibration(false);
        notificationManager.createNotificationChannel(mChannel);

        mSensorPrivacyManager = getSystemService(SensorPrivacyManager.class);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        try {
            // Just log a message indicating that the service is running and is able to accept
            // socket connections.
            while (!mThreadExitFlag && mSocket==null) {
                Thread.sleep(1);
            }
            if (!mThreadExitFlag){
                Logt.i(TAG, "ItsService ready");
            } else {
                Logt.e(TAG, "Starting ItsService in bad state");
            }

            Notification notification = new Notification.Builder(this, mChannel.getId())
                    .setContentTitle("CameraITS Service")
                    .setContentText("CameraITS Service is running")
                    .setSmallIcon(R.drawable.icon)
                    .setOngoing(true).build();
            startForeground(SERVICE_NOTIFICATION_ID, notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA);
        } catch (java.lang.InterruptedException e) {
            Logt.e(TAG, "Error starting ItsService (interrupted)", e);
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mThreadExitFlag = true;
        for (int i = 0; i < MAX_NUM_OUTPUT_SURFACES; i++) {
            if (mSaveThreads[i] != null) {
                mSaveThreads[i].quit();
                mSaveThreads[i] = null;
            }
        }
        if (mSensorThread != null) {
            mSensorThread.quitSafely();
            mSensorThread = null;
        }
        if (mResultThread != null) {
            mResultThread.quitSafely();
            mResultThread = null;
        }
        if (mCameraThread != null) {
            mCameraThread.quitSafely();
            mCameraThread = null;
        }
    }

    public void openCameraDevice(String cameraId) throws ItsException {
        Logt.i(TAG, String.format("Opening camera %s", cameraId));

        // Get initial physical unavailable callbacks without opening camera
        mCameraManager.registerAvailabilityCallback(ac, mCameraHandler);

        try {
            if (mMemoryQuota == -1) {
                // Initialize memory quota on this device
                if (mItsCameraIdList == null) {
                    mItsCameraIdList = ItsUtils.getItsCompatibleCameraIds(mCameraManager);
                }
                if (mItsCameraIdList.mCameraIds.size() == 0) {
                    throw new ItsException("No camera devices");
                }
                for (String camId : mItsCameraIdList.mCameraIds) {
                    CameraCharacteristics chars =  mCameraManager.getCameraCharacteristics(camId);
                    Size maxYuvSize = ItsUtils.getMaxOutputSize(
                            chars, ImageFormat.YUV_420_888);
                    // 4 bytes per pixel for RGBA8888 Bitmap and at least 3 Bitmaps per CDD
                    int quota = maxYuvSize.getWidth() * maxYuvSize.getHeight() * 4 * 3;
                    if (quota > mMemoryQuota) {
                        mMemoryQuota = quota;
                    }
                }
            }
        } catch (CameraAccessException e) {
            throw new ItsException("Failed to get device ID list", e);
        }

        try {
            mUnavailablePhysicalCameras = getUnavailablePhysicalCameras(
                    unavailablePhysicalCamEventQueue, cameraId);
            Log.i(TAG, "Unavailable cameras:" + Arrays.asList(mUnavailablePhysicalCameras.toString()));
            mCamera = mBlockingCameraManager.openCamera(cameraId, mCameraListener, mCameraHandler);
            mCameraCharacteristics = mCameraManager.getCameraCharacteristics(cameraId);
            // The camera should be in available->unavailable state.
            unavailableEventQueue.clear();
            boolean isLogicalCamera = hasCapability(
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA);
            if (isLogicalCamera) {
                Set<String> physicalCameraIds = mCameraCharacteristics.getPhysicalCameraIds();
                for (String id : physicalCameraIds) {
                    if (mUnavailablePhysicalCameras.contains(id)) {
                        Log.i(TAG, "Physical camera id not available: " + id);
                        continue;
                    }
                    mPhysicalCameraChars.put(id, mCameraManager.getCameraCharacteristics(id));
                }
            }
            mSocketQueueQuota = new Semaphore(mMemoryQuota, true);
        } catch (CameraAccessException e) {
            throw new ItsException("Failed to open camera", e);
        } catch (BlockingOpenException e) {
            throw new ItsException("Failed to open camera (after blocking)", e);
        } catch (Exception e) {
            throw new ItsException("Failed to get unavailable physical cameras", e);
        }
        mSocketRunnableObj.sendResponse("cameraOpened", "");
    }

    public void closeCameraDevice() throws ItsException {
        try {
            if (mCamera != null) {
                Logt.i(TAG, "Closing camera");
                mCamera.close();
                mCamera = null;
                mCameraManager.unregisterAvailabilityCallback(ac);
            }
        } catch (Exception e) {
            throw new ItsException("Failed to close device");
        }
        mSocketRunnableObj.sendResponse("cameraClosed", "");
    }

    class SerializerRunnable implements Runnable {
        // Use a separate thread to perform JSON serialization (since this can be slow due to
        // the reflection).
        @Override
        public void run() {
            Logt.i(TAG, "Serializer thread starting");
            while (! mThreadExitFlag) {
                try {
                    Object objs[] = mSerializerQueue.take();
                    JSONObject jsonObj = new JSONObject();
                    String tag = null;
                    for (int i = 0; i < objs.length; i++) {
                        Object obj = objs[i];
                        if (obj instanceof String) {
                            if (tag != null) {
                                throw new ItsException("Multiple tags for socket response");
                            }
                            tag = (String)obj;
                        } else if (obj instanceof CameraCharacteristics) {
                            jsonObj.put("cameraProperties", ItsSerializer.serialize(
                                    (CameraCharacteristics)obj));
                        } else if (obj instanceof CaptureRequest) {
                            jsonObj.put("captureRequest", ItsSerializer.serialize(
                                    (CaptureRequest)obj));
                        } else if (obj instanceof CaptureResult) {
                            jsonObj.put("captureResult", ItsSerializer.serialize(
                                    (CaptureResult)obj));
                        } else if (obj instanceof JSONArray) {
                            if (tag == "captureResults") {
                                if (i == SERIALIZER_SURFACES_ID) {
                                    jsonObj.put("outputs", (JSONArray)obj);
                                } else if (i == SERIALIZER_PHYSICAL_METADATA_ID) {
                                    jsonObj.put("physicalResults", (JSONArray)obj);
                                } else {
                                    throw new ItsException(
                                            "Unsupported JSONArray for captureResults");
                                }
                            } else {
                                jsonObj.put("outputs", (JSONArray)obj);
                            }
                        } else {
                            throw new ItsException("Invalid object received for serialization");
                        }
                    }
                    if (tag == null) {
                        throw new ItsException("No tag provided for socket response");
                    }
                    mSocketRunnableObj.sendResponse(tag, null, jsonObj, null);
                    Logt.i(TAG, String.format("Serialized %s", tag));
                } catch (org.json.JSONException e) {
                    Logt.e(TAG, "Error serializing object", e);
                    break;
                } catch (ItsException e) {
                    Logt.e(TAG, "Error serializing object", e);
                    break;
                } catch (java.lang.InterruptedException e) {
                    Logt.e(TAG, "Error serializing object (interrupted)", e);
                    break;
                }
            }
            Logt.i(TAG, "Serializer thread terminated");
        }
    }

    class SocketWriteRunnable implements Runnable {

        // Use a separate thread to service a queue of objects to be written to the socket,
        // writing each sequentially in order. This is needed since different handler functions
        // (called on different threads) will need to send data back to the host script.

        public Socket mOpenSocket = null;
        private Thread mThread = null;

        public SocketWriteRunnable(Socket openSocket) {
            mOpenSocket = openSocket;
        }

        public void setOpenSocket(Socket openSocket) {
            mOpenSocket = openSocket;
        }

        @Override
        public void run() {
            Logt.i(TAG, "Socket writer thread starting");
            while (true) {
                try {
                    ByteBuffer b = mSocketWriteQueue.take();
                    synchronized(mSocketWriteDrainLock) {
                        if (mOpenSocket == null) {
                            Logt.e(TAG, "No open socket connection!");
                            continue;
                        }
                        if (b.hasArray()) {
                            mOpenSocket.getOutputStream().write(b.array(), 0, b.capacity());
                        } else {
                            byte[] barray = new byte[b.capacity()];
                            b.get(barray);
                            mOpenSocket.getOutputStream().write(barray);
                        }
                        mOpenSocket.getOutputStream().flush();
                        Logt.i(TAG, String.format("Wrote to socket: %d bytes", b.capacity()));
                        Integer imgBufSize = mInflightImageSizes.peek();
                        if (imgBufSize != null && imgBufSize == b.capacity()) {
                            mInflightImageSizes.removeFirst();
                            if (mSocketQueueQuota != null) {
                                mSocketQueueQuota.release(imgBufSize);
                            }
                        }
                    }
                } catch (IOException e) {
                    Logt.e(TAG, "Error writing to socket", e);
                    mOpenSocket = null;
                    break;
                } catch (java.lang.InterruptedException e) {
                    Logt.e(TAG, "Error writing to socket (interrupted)", e);
                    mOpenSocket = null;
                    break;
                }
            }
            Logt.i(TAG, "Socket writer thread terminated");
        }

        public synchronized void checkAndStartThread() {
            if (mThread == null || mThread.getState() == Thread.State.TERMINATED) {
                mThread = new Thread(this);
            }
            if (mThread.getState() == Thread.State.NEW) {
                mThread.start();
            }
        }

    }

    class SocketRunnable implements Runnable {

        // Format of sent messages (over the socket):
        // * Serialized JSON object on a single line (newline-terminated)
        // * For byte buffers, the binary data then follows
        //
        // Format of received messages (from the socket):
        // * Serialized JSON object on a single line (newline-terminated)

        private Socket mOpenSocket = null;
        private SocketWriteRunnable mSocketWriteRunnable = null;

        @Override
        public void run() {
            Logt.i(TAG, "Socket thread starting");
            try {
                mSocket = new ServerSocket(SERVERPORT);
            } catch (IOException e) {
                Logt.e(TAG, "Failed to create socket", e);
            }

            // Create a new thread to handle writes to this socket.
            mSocketWriteRunnable = new SocketWriteRunnable(null);

            while (!mThreadExitFlag) {
                // Receive the socket-open request from the host.
                try {
                    Logt.i(TAG, "Waiting for client to connect to socket");
                    mOpenSocket = mSocket.accept();
                    if (mOpenSocket == null) {
                        Logt.e(TAG, "Socket connection error");
                        break;
                    }
                    mSocketWriteQueue.clear();
                    mInflightImageSizes.clear();
                    mSocketWriteRunnable.setOpenSocket(mOpenSocket);
                    mSocketWriteRunnable.checkAndStartThread();
                    Logt.i(TAG, "Socket connected");
                } catch (IOException e) {
                    Logt.e(TAG, "Socket open error: ", e);
                    break;
                }

                // Process commands over the open socket.
                while (!mThreadExitFlag) {
                    try {
                        BufferedReader input = new BufferedReader(
                                new InputStreamReader(mOpenSocket.getInputStream()));
                        if (input == null) {
                            Logt.e(TAG, "Failed to get socket input stream");
                            break;
                        }
                        String line = input.readLine();
                        if (line == null) {
                            Logt.i(TAG, "Socket readline returned null (host disconnected)");
                            break;
                        }
                        processSocketCommand(line);
                    } catch (IOException e) {
                        Logt.e(TAG, "Socket read error: ", e);
                        break;
                    } catch (ItsException e) {
                        Logt.e(TAG, "Script error: ", e);
                        break;
                    }
                }

                // Close socket and go back to waiting for a new connection.
                try {
                    synchronized(mSocketWriteDrainLock) {
                        mSocketWriteQueue.clear();
                        mInflightImageSizes.clear();
                        mOpenSocket.close();
                        mOpenSocket = null;
                        mSocketWriteRunnable.setOpenSocket(null);
                        Logt.i(TAG, "Socket disconnected");
                    }
                } catch (java.io.IOException e) {
                    Logt.e(TAG, "Exception closing socket");
                }
            }

            // It's an overall error state if the code gets here; no recevery.
            // Try to do some cleanup, but the service probably needs to be restarted.
            Logt.i(TAG, "Socket server loop exited");
            mThreadExitFlag = true;
            try {
                synchronized(mSocketWriteDrainLock) {
                    if (mOpenSocket != null) {
                        mOpenSocket.close();
                        mOpenSocket = null;
                        mSocketWriteRunnable.setOpenSocket(null);
                    }
                }
            } catch (java.io.IOException e) {
                Logt.w(TAG, "Exception closing socket");
            }
            try {
                if (mSocket != null) {
                    mSocket.close();
                    mSocket = null;
                }
            } catch (java.io.IOException e) {
                Logt.w(TAG, "Exception closing socket");
            }
        }

        public void processSocketCommand(String cmd)
                throws ItsException {
            // Default locale must be set to "en-us"
            Locale locale = Locale.getDefault();
            if (!Locale.US.equals(locale)) {
                Logt.e(TAG, "Default language is not set to " + Locale.US + "!");
                stopSelf();
            }

            // Each command is a serialized JSON object.
            try {
                JSONObject cmdObj = new JSONObject(cmd);
                Logt.i(TAG, "Start processing command" + cmdObj.getString("cmdName"));
                if ("open".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    openCameraDevice(cameraId);
                } else if ("close".equals(cmdObj.getString("cmdName"))) {
                    closeCameraDevice();
                } else if ("getCameraProperties".equals(cmdObj.getString("cmdName"))) {
                    doGetProps();
                } else if ("getCameraPropertiesById".equals(cmdObj.getString("cmdName"))) {
                    doGetPropsById(cmdObj);
                } else if ("startSensorEvents".equals(cmdObj.getString("cmdName"))) {
                    doStartSensorEvents();
                } else if ("checkSensorExistence".equals(cmdObj.getString("cmdName"))) {
                    doCheckSensorExistence();
                } else if ("getSensorEvents".equals(cmdObj.getString("cmdName"))) {
                    doGetSensorEvents();
                } else if ("do3A".equals(cmdObj.getString("cmdName"))) {
                    do3A(cmdObj);
                } else if ("doCapture".equals(cmdObj.getString("cmdName"))) {
                    doCapture(cmdObj);
                } else if ("doVibrate".equals(cmdObj.getString("cmdName"))) {
                    doVibrate(cmdObj);
                } else if ("setAudioRestriction".equals(cmdObj.getString("cmdName"))) {
                    doSetAudioRestriction(cmdObj);
                } else if ("getCameraIds".equals(cmdObj.getString("cmdName"))) {
                    doGetCameraIds();
                } else if ("doReprocessCapture".equals(cmdObj.getString("cmdName"))) {
                    doReprocessCapture(cmdObj);
                } else if ("getItsVersion".equals(cmdObj.getString("cmdName"))) {
                    mSocketRunnableObj.sendResponse("ItsVersion", ITS_SERVICE_VERSION);
                } else if ("isStreamCombinationSupported".equals(cmdObj.getString("cmdName"))) {
                    doCheckStreamCombination(cmdObj);
                } else if ("isCameraPrivacyModeSupported".equals(cmdObj.getString("cmdName"))) {
                    doCheckCameraPrivacyModeSupport();
                } else if ("isPrimaryCamera".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    doCheckPrimaryCamera(cameraId);
                } else if ("isPerformanceClass".equals(cmdObj.getString("cmdName"))) {
                    doCheckPerformanceClass();
                } else if ("measureCameraLaunchMs".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    doMeasureCameraLaunchMs(cameraId);
                } else if ("measureCamera1080pJpegCaptureMs".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    doMeasureCamera1080pJpegCaptureMs(cameraId);
                } else if ("getSupportedVideoQualities".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    doGetSupportedVideoQualities(cameraId);
                } else if ("getSupportedPreviewSizes".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    doGetSupportedPreviewSizes(cameraId);
                } else if ("doBasicRecording".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    int profileId = cmdObj.getInt("profileId");
                    String quality = cmdObj.getString("quality");
                    int recordingDuration = cmdObj.getInt("recordingDuration");
                    int videoStabilizationMode = cmdObj.getInt("videoStabilizationMode");
                    boolean hlg10Enabled = cmdObj.getBoolean("hlg10Enabled");
                    doBasicRecording(cameraId, profileId, quality, recordingDuration,
                            videoStabilizationMode, hlg10Enabled);
                } else if ("doPreviewRecording".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    String videoSize = cmdObj.getString("videoSize");
                    int recordingDuration = cmdObj.getInt("recordingDuration");
                    boolean stabilize = cmdObj.getBoolean("stabilize");
                    doBasicPreviewRecording(cameraId, videoSize, recordingDuration, stabilize);
                } else if ("isHLG10Supported".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    int profileId = cmdObj.getInt("profileId");
                    doCheckHLG10Support(cameraId, profileId);
                } else if ("doCaptureWithFlash".equals(cmdObj.getString("cmdName"))) {
                    doCaptureWithFlash(cmdObj);
                } else if ("doGetUnavailablePhysicalCameras".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    doGetUnavailablePhysicalCameras(cameraId);
                } else if ("getDisplaySize".equals(cmdObj.getString("cmdName"))) {
                    doGetDisplaySize();
                } else if ("getMaxCamcorderProfileSize".equals(cmdObj.getString("cmdName"))) {
                    String cameraId = cmdObj.getString("cameraId");
                    doGetMaxCamcorderProfileSize(cameraId);
                } else if ("getAvailablePhysicalCameraProperties".equals(cmdObj.getString("cmdName"))) {
                    doGetAvailablePhysicalCameraProperties();
                } else {
                    throw new ItsException("Unknown command: " + cmd);
                }
                Logt.i(TAG, "Finish processing command" + cmdObj.getString("cmdName"));
            } catch (org.json.JSONException e) {
                Logt.e(TAG, "Invalid command: ", e);
            }
        }

        public void sendResponse(String tag, String str, JSONObject obj, ByteBuffer bbuf)
                throws ItsException {
            try {
                JSONObject jsonObj = new JSONObject();
                jsonObj.put("tag", tag);
                if (str != null) {
                    jsonObj.put("strValue", str);
                }
                if (obj != null) {
                    jsonObj.put("objValue", obj);
                }
                if (bbuf != null) {
                    jsonObj.put("bufValueSize", bbuf.capacity());
                }
                ByteBuffer bstr = ByteBuffer.wrap(
                        (jsonObj.toString()+"\n").getBytes(Charset.defaultCharset()));
                synchronized(mSocketWriteEnqueueLock) {
                    if (bstr != null) {
                        mSocketWriteQueue.put(bstr);
                    }
                    if (bbuf != null) {
                        mInflightImageSizes.add(bbuf.capacity());
                        mSocketWriteQueue.put(bbuf);
                    }
                }
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            } catch (java.lang.InterruptedException e) {
                throw new ItsException("Socket error: ", e);
            }
        }

        public void sendResponse(String tag, String str)
                throws ItsException {
            sendResponse(tag, str, null, null);
        }

        public void sendResponse(String tag, JSONObject obj)
                throws ItsException {
            sendResponse(tag, null, obj, null);
        }

        public void sendResponseCaptureBuffer(String tag, ByteBuffer bbuf)
                throws ItsException {
            sendResponse(tag, null, null, bbuf);
        }

        public void sendResponse(LinkedList<MySensorEvent> events)
                throws ItsException {
            Logt.i(TAG, "Sending " + events.size() + " sensor events");
            try {
                JSONArray accels = new JSONArray();
                JSONArray mags = new JSONArray();
                JSONArray gyros = new JSONArray();
                for (MySensorEvent event : events) {
                    JSONObject obj = new JSONObject();
                    obj.put("time", event.timestamp);
                    obj.put("x", event.values[0]);
                    obj.put("y", event.values[1]);
                    obj.put("z", event.values[2]);
                    if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
                        accels.put(obj);
                    } else if (event.sensor.getType() == Sensor.TYPE_MAGNETIC_FIELD) {
                        mags.put(obj);
                    } else if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                        gyros.put(obj);
                    }
                }
                JSONObject obj = new JSONObject();
                obj.put("accel", accels);
                obj.put("mag", mags);
                obj.put("gyro", gyros);
                sendResponse("sensorEvents", null, obj, null);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            }
            Logt.i(TAG, "Sent sensor events");
        }

        public void sendResponse(CameraCharacteristics props)
                throws ItsException {
            try {
                Object objs[] = new Object[2];
                objs[0] = "cameraProperties";
                objs[1] = props;
                mSerializerQueue.put(objs);
            } catch (InterruptedException e) {
                throw new ItsException("Interrupted: ", e);
            }
        }

        public void sendResponse(String tag, HashMap<String, CameraCharacteristics> props)
                throws ItsException {
            try {
                JSONArray jsonSurfaces = new JSONArray();
                int n = props.size();
                for (String s : props.keySet()) {
                    JSONObject jsonSurface = new JSONObject();
                    jsonSurface.put(s, ItsSerializer.serialize(props.get(s)));
                    jsonSurfaces.put(jsonSurface);
                }
                Object objs[] = new Object[2];
                objs[0] = "availablePhysicalCameraProperties";
                objs[1] = jsonSurfaces;
                mSerializerQueue.put(objs);
            } catch (Exception e) {
                throw new ItsException("Interrupted: ", e);
            }
        }

        public void sendVideoRecordingObject(VideoRecordingObject obj)
                throws ItsException {
            try {
                JSONObject videoJson = new JSONObject();
                videoJson.put("recordedOutputPath", obj.recordedOutputPath);
                videoJson.put("quality", obj.quality);
                if (obj.isFrameRateValid()) {
                    videoJson.put("videoFrameRate", obj.videoFrameRate);
                }
                videoJson.put("videoSize", obj.videoSize);
                sendResponse("recordingResponse", null, videoJson, null);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            }
        }

        public void sendResponseCaptureResult(CameraCharacteristics props,
                                              CaptureRequest request,
                                              TotalCaptureResult result,
                                              ImageReader[] readers)
                throws ItsException {
            try {
                JSONArray jsonSurfaces = new JSONArray();
                for (int i = 0; i < readers.length; i++) {
                    JSONObject jsonSurface = new JSONObject();
                    jsonSurface.put("width", readers[i].getWidth());
                    jsonSurface.put("height", readers[i].getHeight());
                    int format = readers[i].getImageFormat();
                    if (format == ImageFormat.RAW_SENSOR) {
                        if (mCaptureRawIsStats) {
                            int aaw = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .width();
                            int aah = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .height();
                            jsonSurface.put("format", "rawStats");
                            jsonSurface.put("width", aaw/mCaptureStatsGridWidth);
                            jsonSurface.put("height", aah/mCaptureStatsGridHeight);
                        } else if (mCaptureRawIsDng) {
                            jsonSurface.put("format", "dng");
                        } else {
                            jsonSurface.put("format", "raw");
                        }
                    } else if (format == ImageFormat.RAW10) {
                        jsonSurface.put("format", "raw10");
                    } else if (format == ImageFormat.RAW12) {
                        jsonSurface.put("format", "raw12");
                    } else if (format == ImageFormat.JPEG) {
                        jsonSurface.put("format", "jpeg");
                    } else if (format == ImageFormat.YUV_420_888) {
                        jsonSurface.put("format", "yuv");
                    } else if (format == ImageFormat.Y8) {
                        jsonSurface.put("format", "y8");
                    } else {
                        throw new ItsException("Invalid format");
                    }
                    jsonSurfaces.put(jsonSurface);
                }

                Map<String, CaptureResult> physicalMetadata =
                        result.getPhysicalCameraResults();
                JSONArray jsonPhysicalMetadata = new JSONArray();
                for (Map.Entry<String, CaptureResult> pair : physicalMetadata.entrySet()) {
                    JSONObject jsonOneMetadata = new JSONObject();
                    jsonOneMetadata.put(pair.getKey(), ItsSerializer.serialize(pair.getValue()));
                    jsonPhysicalMetadata.put(jsonOneMetadata);
                }
                Object objs[] = new Object[4];
                objs[0] = "captureResults";
                objs[1] = result;
                objs[SERIALIZER_SURFACES_ID] = jsonSurfaces;
                objs[SERIALIZER_PHYSICAL_METADATA_ID] = jsonPhysicalMetadata;
                mSerializerQueue.put(objs);
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error: ", e);
            } catch (InterruptedException e) {
                throw new ItsException("Interrupted: ", e);
            }
        }
    }

    public ImageReader.OnImageAvailableListener
            createAvailableListener(final CaptureCallback listener) {
        return new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image i = null;
                try {
                    i = reader.acquireNextImage();
                    String physicalCameraId = new String();
                    for (int idx = 0; idx < mOutputImageReaders.length; idx++) {
                        if (mOutputImageReaders[idx] == reader) {
                            physicalCameraId = mPhysicalStreamMap.get(idx);
                        }
                    }
                    listener.onCaptureAvailable(i, physicalCameraId);
                } finally {
                    if (i != null) {
                        i.close();
                    }
                }
            }
        };
    }

    private ImageReader.OnImageAvailableListener
            createAvailableListenerDropper() {
        return new ImageReader.OnImageAvailableListener() {
            @Override
            public void onImageAvailable(ImageReader reader) {
                Image i = reader.acquireNextImage();
                if (i != null) {
                    i.close();
                }
            }
        };
    }

    private void doStartSensorEvents() throws ItsException {
        synchronized(mEventLock) {
            mEvents.clear();
            mEventsEnabled = true;
        }
        mSocketRunnableObj.sendResponse("sensorEventsStarted", "");
    }

    private void doCheckSensorExistence() throws ItsException {
        try {
            JSONObject obj = new JSONObject();
            obj.put("accel", mAccelSensor != null);
            obj.put("mag", mMagSensor != null);
            obj.put("gyro", mGyroSensor != null);
            obj.put("vibrator", mVibrator.hasVibrator());
            mSocketRunnableObj.sendResponse("sensorExistence", null, obj, null);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    private void doGetSensorEvents() throws ItsException {
        synchronized(mEventLock) {
            mSocketRunnableObj.sendResponse(mEvents);
            mEvents.clear();
            mEventsEnabled = false;
        }
    }

    private void doGetProps() throws ItsException {
        mSocketRunnableObj.sendResponse(mCameraCharacteristics);
    }

    private void doGetPropsById(JSONObject params) throws ItsException {
        String[] devices;
        try {
            // Intentionally not using ItsUtils.getItsCompatibleCameraIds here so it's possible to
            // write some simple script to query camera characteristics even for devices exempted
            // from ITS today.
            devices = mCameraManager.getCameraIdList();
            if (devices == null || devices.length == 0) {
                throw new ItsException("No camera devices");
            }
        } catch (CameraAccessException e) {
            throw new ItsException("Failed to get device ID list", e);
        }

        try {
            String cameraId = params.getString("cameraId");
            CameraCharacteristics characteristics =
                    mCameraManager.getCameraCharacteristics(cameraId);
            mSocketRunnableObj.sendResponse(characteristics);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        } catch (IllegalArgumentException e) {
            throw new ItsException("Illegal argument error:", e);
        } catch (CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        }
    }

    private void doGetAvailablePhysicalCameraProperties() throws ItsException {
        mSocketRunnableObj.sendResponse("availablePhysicalCameraProperties", mPhysicalCameraChars);
    }

    private Set<String> getUnavailablePhysicalCameras(
            LinkedBlockingQueue<Pair<String, String>> queue, String cameraId) throws Exception {
        Set<String> unavailablePhysicalCameras = new HashSet<String>();
        while (true) {
            Pair<String, String> unavailableIdCombo = queue.poll(
                    AVAILABILITY_TIMEOUT_MS, java.util.concurrent.TimeUnit.MILLISECONDS);
            if (unavailableIdCombo == null) {
                // No more entries in the queue. Break out of the loop and return.
                break;
            }
            if (cameraId.equals(unavailableIdCombo.first)) {
                unavailablePhysicalCameras.add(unavailableIdCombo.second);
            }
        };
        return unavailablePhysicalCameras;
    }

    private void doGetCameraIds() throws ItsException {
        if (mItsCameraIdList == null) {
            mItsCameraIdList = ItsUtils.getItsCompatibleCameraIds(mCameraManager);
        }
        if (mItsCameraIdList.mCameraIdCombos.size() == 0) {
            throw new ItsException("No camera devices");
        }

        try {
            JSONObject obj = new JSONObject();
            JSONArray array = new JSONArray();
            for (String id : mItsCameraIdList.mCameraIdCombos) {
                array.put(id);
            }
            obj.put("cameraIdArray", array);
            mSocketRunnableObj.sendResponse("cameraIds", obj);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    private static class HandlerExecutor implements Executor {
        private final Handler mHandler;

        public HandlerExecutor(Handler handler) {
            mHandler = handler;
        }

        @Override
        public void execute(Runnable runCmd) {
            mHandler.post(runCmd);
        }
    }

    private void doCheckStreamCombination(JSONObject params) throws ItsException {
        try {
            JSONObject obj = new JSONObject();
            JSONArray jsonOutputSpecs = ItsUtils.getOutputSpecs(params);
            prepareImageReadersWithOutputSpecs(jsonOutputSpecs, /*inputSize*/null,
                    /*inputFormat*/0, /*maxInputBuffers*/0, /*backgroundRequest*/false);
            int numSurfaces = mOutputImageReaders.length;
            List<OutputConfiguration> outputConfigs =
                    new ArrayList<OutputConfiguration>(numSurfaces);
            for (int i = 0; i < numSurfaces; i++) {
                OutputConfiguration config = new OutputConfiguration(
                        mOutputImageReaders[i].getSurface());
                if (mPhysicalStreamMap.get(i) != null) {
                    config.setPhysicalCameraId(mPhysicalStreamMap.get(i));
                }
                if (mStreamUseCaseMap.get(i) != null) {
                    config.setStreamUseCase(mStreamUseCaseMap.get(i));
                }
                outputConfigs.add(config);
            }

            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            SessionConfiguration sessionConfig = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, outputConfigs,
                new HandlerExecutor(mCameraHandler), sessionListener);
            boolean supported = mCamera.isSessionConfigurationSupported(sessionConfig);

            String supportString = supported ? "supportedCombination" : "unsupportedCombination";
            mSocketRunnableObj.sendResponse("streamCombinationSupport", supportString);

        } catch (UnsupportedOperationException e) {
            mSocketRunnableObj.sendResponse("streamCombinationSupport", "unsupportedOperation");
        } catch (IllegalArgumentException e) {
            throw new ItsException("Error checking stream combination", e);
        } catch (CameraAccessException e) {
            throw new ItsException("Error checking stream combination", e);
        }
    }

    private void doCheckCameraPrivacyModeSupport() throws ItsException {
        boolean hasPrivacySupport = mSensorPrivacyManager
                .supportsSensorToggle(SensorPrivacyManager.Sensors.CAMERA);
        mSocketRunnableObj.sendResponse("cameraPrivacyModeSupport",
                hasPrivacySupport ? "true" : "false");
    }

    private void doGetUnavailablePhysicalCameras(String cameraId) throws ItsException {
        try {
            JSONArray cameras = new JSONArray();
            JSONObject jsonObj = new JSONObject();
            for (String p : mUnavailablePhysicalCameras) {
                cameras.put(p);
            }
            jsonObj.put("unavailablePhysicalCamerasArray", cameras);
            Log.i(TAG, "unavailablePhysicalCameras : " +
                    Arrays.asList(mUnavailablePhysicalCameras.toString()));
            mSocketRunnableObj.sendResponse("unavailablePhysicalCameras", null, jsonObj, null);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    private void doGetDisplaySize() throws ItsException {
        WindowManager windowManager = getSystemService(WindowManager.class);
        if (windowManager == null) {
            throw new ItsException("No window manager.");
        }
        WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
        if (metrics == null) {
            throw new ItsException("No current window metrics in window manager.");
        }
        Rect windowBounds = metrics.getBounds();

        int width = windowBounds.width();
        int height = windowBounds.height();
        if (height > width) {
            height = width;
            width = windowBounds.height();
        }

        Size displaySize = new Size(width, height);
        mSocketRunnableObj.sendResponse("displaySize", displaySize.toString());
    }

    private void doGetMaxCamcorderProfileSize(String cameraId) throws ItsException {
        if (mItsCameraIdList == null) {
            mItsCameraIdList = ItsUtils.getItsCompatibleCameraIds(mCameraManager);
        }
        if (mItsCameraIdList.mCameraIds.size() == 0) {
            throw new ItsException("No camera devices");
        }
        if (!mItsCameraIdList.mCameraIds.contains(cameraId)) {
            throw new ItsException("Invalid cameraId " + cameraId);
        }

        int cameraDeviceId = Integer.parseInt(cameraId);
        int maxArea = -1;
        Size maxProfileSize = new Size(0, 0);
        for (int profileId : CAMCORDER_PROFILE_QUALITIES_MAP.keySet()) {
            if (CamcorderProfile.hasProfile(cameraDeviceId, profileId)) {
                CamcorderProfile profile = CamcorderProfile.get(cameraDeviceId, profileId);
                if (profile == null) {
                    throw new ItsException("Invalid camcorder profile for id " + profileId);
                }

                int area = profile.videoFrameWidth * profile.videoFrameHeight;
                if (area > maxArea) {
                    maxProfileSize = new Size(profile.videoFrameWidth, profile.videoFrameHeight);
                    maxArea = area;
                }
            }
        }
        mSocketRunnableObj.sendResponse("maxCamcorderProfileSize", maxProfileSize.toString());
    }

    private Set<Pair<String, String>> getUnavailablePhysicalCameras() throws ItsException {
        final LinkedBlockingQueue<Pair<String, String>> unavailablePhysicalCamEventQueue =
                new LinkedBlockingQueue<>();
        try {
            CameraManager.AvailabilityCallback ac = new CameraManager.AvailabilityCallback() {
                @Override
                public void onPhysicalCameraUnavailable(String cameraId, String physicalCameraId) {
                    unavailablePhysicalCamEventQueue.offer(new Pair<>(cameraId, physicalCameraId));
                }
            };
            mCameraManager.registerAvailabilityCallback(ac, mCameraHandler);
            Set<Pair<String, String>> unavailablePhysicalCameras =
                    new HashSet<Pair<String, String>>();
            Pair<String, String> candidatePhysicalIds =
                    unavailablePhysicalCamEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                    java.util.concurrent.TimeUnit.MILLISECONDS);
            while (candidatePhysicalIds != null) {
                unavailablePhysicalCameras.add(candidatePhysicalIds);
                candidatePhysicalIds =
                        unavailablePhysicalCamEventQueue.poll(AVAILABILITY_TIMEOUT_MS,
                        java.util.concurrent.TimeUnit.MILLISECONDS);
            }
            mCameraManager.unregisterAvailabilityCallback(ac);
            return unavailablePhysicalCameras;
        } catch (Exception e) {
            throw new ItsException("Exception: ", e);
        }
    }

    private void doCheckPrimaryCamera(String cameraId) throws ItsException {
        if (mItsCameraIdList == null) {
            mItsCameraIdList = ItsUtils.getItsCompatibleCameraIds(mCameraManager);
        }
        if (mItsCameraIdList.mCameraIds.size() == 0) {
            throw new ItsException("No camera devices");
        }
        if (!mItsCameraIdList.mCameraIds.contains(cameraId)) {
            throw new ItsException("Invalid cameraId " + cameraId);
        }

        boolean isPrimaryCamera = false;
        try {
            CameraCharacteristics c = mCameraManager.getCameraCharacteristics(cameraId);
            Integer cameraFacing = c.get(CameraCharacteristics.LENS_FACING);
            for (String id : mItsCameraIdList.mCameraIds) {
                c = mCameraManager.getCameraCharacteristics(id);
                Integer facing = c.get(CameraCharacteristics.LENS_FACING);
                if (cameraFacing.equals(facing)) {
                    if (cameraId.equals(id)) {
                        isPrimaryCamera = true;
                    } else {
                        isPrimaryCamera = false;
                    }
                    break;
                }
            }
        } catch (CameraAccessException e) {
            throw new ItsException("Failed to get camera characteristics", e);
        }

        mSocketRunnableObj.sendResponse("primaryCamera",
                isPrimaryCamera ? "true" : "false");
    }

    private static MediaFormat initializeHLG10Format(Size videoSize, int videoBitRate,
            int videoFrameRate) {
        MediaFormat format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_HEVC,
                videoSize.getWidth(), videoSize.getHeight());
        format.setInteger(MediaFormat.KEY_PROFILE, HEVCProfileMain10);
        format.setInteger(MediaFormat.KEY_BIT_RATE, videoBitRate);
        format.setInteger(MediaFormat.KEY_FRAME_RATE, videoFrameRate);
        format.setInteger(MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
        format.setInteger(MediaFormat.KEY_COLOR_STANDARD, MediaFormat.COLOR_STANDARD_BT2020);
        format.setInteger(MediaFormat.KEY_COLOR_RANGE, MediaFormat.COLOR_RANGE_FULL);
        format.setInteger(MediaFormat.KEY_COLOR_TRANSFER, MediaFormat.COLOR_TRANSFER_HLG);
        format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1);
        return format;
    }

    private void doCheckHLG10Support(String cameraId, int profileId) throws ItsException {
        if (mItsCameraIdList == null) {
            mItsCameraIdList = ItsUtils.getItsCompatibleCameraIds(mCameraManager);
        }
        if (mItsCameraIdList.mCameraIds.size() == 0) {
            throw new ItsException("No camera devices");
        }
        if (!mItsCameraIdList.mCameraIds.contains(cameraId)) {
            throw new ItsException("Invalid cameraId " + cameraId);
        }
        boolean cameraHLG10OutputSupported = false;
        try {
            CameraCharacteristics c = mCameraManager.getCameraCharacteristics(cameraId);
            int[] caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
            cameraHLG10OutputSupported = IntStream.of(caps).anyMatch(x -> x ==
                    CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT);
        } catch (CameraAccessException e) {
            throw new ItsException("Failed to get camera characteristics", e);
        }

        int cameraDeviceId = Integer.parseInt(cameraId);
        CamcorderProfile camcorderProfile = getCamcorderProfile(cameraDeviceId, profileId);
        assert (camcorderProfile != null);

        Size videoSize = new Size(camcorderProfile.videoFrameWidth,
                camcorderProfile.videoFrameHeight);
        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaFormat format = initializeHLG10Format(videoSize, camcorderProfile.videoBitRate,
                camcorderProfile.videoFrameRate);
        boolean codecSupported = (list.findEncoderForFormat(format) != null);
        Log.v(TAG, "codecSupported: " + codecSupported + "cameraHLG10OutputSupported: " +
                cameraHLG10OutputSupported);

        mSocketRunnableObj.sendResponse("hlg10Response",
                codecSupported && cameraHLG10OutputSupported ? "true" : "false");
    }

    private void doCheckPerformanceClass() throws ItsException {
        boolean  isPerfClass = (Build.VERSION.MEDIA_PERFORMANCE_CLASS >= PERFORMANCE_CLASS_R);

        mSocketRunnableObj.sendResponse("performanceClass",
                isPerfClass ? "true" : "false");
    }

    private double invokeCameraPerformanceTest(Class testClass, String testName,
            String cameraId, String metricName) throws ItsException {
        mResults.clear();
        mCameraInstrumentation = new CameraTestInstrumentation();
        MetricListener metricListener = new MetricListener() {
            @Override
            public void onResultMetric(Metric metric) {
                mResults.add(metric);
            }
        };
        mCameraInstrumentation.initialize(this, metricListener);

        Bundle bundle = new Bundle();
        bundle.putString("camera-id", cameraId);
        bundle.putString("perf-measure", "on");
        bundle.putString("perf-class-test", "on");
        InstrumentationRegistry.registerInstance(mCameraInstrumentation, bundle);

        JUnitCore testRunner = new JUnitCore();
        Log.v(TAG, String.format("Execute Test: %s#%s", testClass.getSimpleName(), testName));
        Request request = Request.method(testClass, testName);
        Result runResult = testRunner.run(request);
        if (!runResult.wasSuccessful()) {
            throw new ItsException("Camera PerformanceTest " + testClass.getSimpleName() +
                    "#" + testName + " failed");
        }

        for (Metric m : mResults) {
            if (m.getMessage().equals(metricName) && m.getValues().length == 1) {
                return m.getValues()[0];
            }
        }

        throw new ItsException("Failed to look up " + metricName +
                " in Camera PerformanceTest result!");
    }

    private void doMeasureCameraLaunchMs(String cameraId) throws ItsException {
        double launchMs = invokeCameraPerformanceTest(PerformanceTest.class,
                "testCameraLaunch", cameraId, "camera_launch_average_time_for_all_cameras");
        mSocketRunnableObj.sendResponse("cameraLaunchMs", Double.toString(launchMs));
    }

    private void doMeasureCamera1080pJpegCaptureMs(String cameraId) throws ItsException {
        double jpegCaptureMs = invokeCameraPerformanceTest(PerformanceTest.class,
                "testSingleCapture", cameraId,
                "camera_capture_average_latency_for_all_cameras_jpeg");
        mSocketRunnableObj.sendResponse("camera1080pJpegCaptureMs", Double.toString(jpegCaptureMs));
    }

    private void prepareImageReaders(Size[] outputSizes, int[] outputFormats, Size inputSize,
            int inputFormat, int maxInputBuffers) {
        closeImageReaders();
        mOutputImageReaders = new ImageReader[outputSizes.length];
        for (int i = 0; i < outputSizes.length; i++) {
            // Check if the output image reader can be shared with the input image reader.
            if (outputSizes[i].equals(inputSize) && outputFormats[i] == inputFormat) {
                mOutputImageReaders[i] = ImageReader.newInstance(outputSizes[i].getWidth(),
                        outputSizes[i].getHeight(), outputFormats[i],
                        MAX_CONCURRENT_READER_BUFFERS + maxInputBuffers);
                mInputImageReader = mOutputImageReaders[i];
            } else {
                mOutputImageReaders[i] = ImageReader.newInstance(outputSizes[i].getWidth(),
                        outputSizes[i].getHeight(), outputFormats[i],
                        MAX_CONCURRENT_READER_BUFFERS);
            }
        }

        if (inputSize != null && mInputImageReader == null) {
            mInputImageReader = ImageReader.newInstance(inputSize.getWidth(), inputSize.getHeight(),
                    inputFormat, maxInputBuffers);
        }
    }

    private void closeImageReaders() {
        if (mOutputImageReaders != null) {
            for (int i = 0; i < mOutputImageReaders.length; i++) {
                if (mOutputImageReaders[i] != null) {
                    mOutputImageReaders[i].close();
                    mOutputImageReaders[i] = null;
                }
            }
        }
        if (mInputImageReader != null) {
            mInputImageReader.close();
            mInputImageReader = null;
        }
    }

    private void do3A(JSONObject params) throws ItsException {
        ThreeAResultListener threeAListener = new ThreeAResultListener();
        try {
            // Start a 3A action, and wait for it to converge.
            // Get the converged values for each "A", and package into JSON result for caller.

            // Configure streams on physical sub-camera if PHYSICAL_ID_KEY is specified.
            String physicalId = null;
            CameraCharacteristics c = mCameraCharacteristics;
            if (params.has(PHYSICAL_ID_KEY)) {
                physicalId = params.getString(PHYSICAL_ID_KEY);
                c = mPhysicalCameraChars.get(physicalId);
            }

            // 3A happens on full-res frames.
            Size sizes[] = ItsUtils.getYuvOutputSizes(c);
            int outputFormats[] = new int[1];
            outputFormats[0] = ImageFormat.YUV_420_888;
            Size[] outputSizes = new Size[1];
            outputSizes[0] = sizes[0];
            int width = outputSizes[0].getWidth();
            int height = outputSizes[0].getHeight();

            prepareImageReaders(outputSizes, outputFormats, /*inputSize*/null, /*inputFormat*/0,
                    /*maxInputBuffers*/0);

            List<OutputConfiguration> outputConfigs = new ArrayList<OutputConfiguration>(1);
            OutputConfiguration config =
                    new OutputConfiguration(mOutputImageReaders[0].getSurface());
            if (physicalId != null) {
                config.setPhysicalCameraId(physicalId);
            }
            outputConfigs.add(config);
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            mCamera.createCaptureSessionByOutputConfigurations(
                    outputConfigs, sessionListener, mCameraHandler);
            mSession = sessionListener.waitAndGetSession(TIMEOUT_IDLE_MS);

            // Add a listener that just recycles buffers; they aren't saved anywhere.
            ImageReader.OnImageAvailableListener readerListener =
                    createAvailableListenerDropper();
            mOutputImageReaders[0].setOnImageAvailableListener(readerListener, mSaveHandlers[0]);

            // Get the user-specified regions for AE, AWB, AF.
            // Note that the user specifies normalized [x,y,w,h], which is converted below
            // to an [x0,y0,x1,y1] region in sensor coords. The capture request region
            // also has a fifth "weight" element: [x0,y0,x1,y1,w].
            // Use logical camera's active array size for 3A regions.
            Rect activeArray = mCameraCharacteristics.get(
                    CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            int aaWidth = activeArray.right - activeArray.left;
            int aaHeight = activeArray.bottom - activeArray.top;
            MeteringRectangle[] regionAE = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,aaWidth,aaHeight,1)};
            MeteringRectangle[] regionAF = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,aaWidth,aaHeight,1)};
            MeteringRectangle[] regionAWB = new MeteringRectangle[]{
                    new MeteringRectangle(0,0,aaWidth,aaHeight,1)};
            if (params.has(REGION_KEY)) {
                JSONObject regions = params.getJSONObject(REGION_KEY);
                if (regions.has(REGION_AE_KEY)) {
                    regionAE = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AE_KEY), true, aaWidth, aaHeight);
                }
                if (regions.has(REGION_AF_KEY)) {
                    regionAF = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AF_KEY), true, aaWidth, aaHeight);
                }
                if (regions.has(REGION_AWB_KEY)) {
                    regionAWB = ItsUtils.getJsonWeightedRectsFromArray(
                            regions.getJSONArray(REGION_AWB_KEY), true, aaWidth, aaHeight);
                }
            }

            // An EV compensation can be specified as part of AE convergence.
            int evComp = params.optInt(EVCOMP_KEY, 0);
            if (evComp != 0) {
                Logt.i(TAG, String.format("Running 3A with AE exposure compensation value: %d", evComp));
            }

            // Auto flash can be specified as part of AE convergence.
            boolean autoFlash = params.optBoolean(AUTO_FLASH_KEY, false);
            if (autoFlash == true) {
                Logt.i(TAG, String.format("Running with auto flash mode."));
            }

            // By default, AE and AF both get triggered, but the user can optionally override this.
            // Also, AF won't get triggered if the lens is fixed-focus.
            if (params.has(TRIGGER_KEY)) {
                JSONObject triggers = params.getJSONObject(TRIGGER_KEY);
                if (triggers.has(TRIGGER_AE_KEY)) {
                    mDoAE = triggers.getBoolean(TRIGGER_AE_KEY);
                }
                if (triggers.has(TRIGGER_AF_KEY)) {
                    mDoAF = triggers.getBoolean(TRIGGER_AF_KEY);
                }
            }

            boolean isFixedFocusLens = isFixedFocusLens(c);
            if (mDoAF && isFixedFocusLens) {
                // Send a fake result back for the code that is waiting for this message to see
                // that AF has converged.
                Logt.i(TAG, "Ignoring request for AF on fixed-focus camera");
                mSocketRunnableObj.sendResponse("afResult", "0.0");
                mDoAF = false;
            }

            mInterlock3A.open();
            synchronized(m3AStateLock) {
                // If AE or AWB lock is specified, then the 3A will converge first and then lock these
                // values, waiting until the HAL has reported that the lock was successful.
                mNeedsLockedAE = params.optBoolean(LOCK_AE_KEY, false);
                mNeedsLockedAWB = params.optBoolean(LOCK_AWB_KEY, false);
                mConvergedAE = false;
                mConvergedAWB = false;
                mConvergedAF = false;
                mLockedAE = false;
                mLockedAWB = false;
            }
            long tstart = System.currentTimeMillis();
            boolean triggeredAE = false;
            boolean triggeredAF = false;

            Logt.i(TAG, String.format("Initiating 3A: AE:%d, AF:%d, AWB:1, AELOCK:%d, AWBLOCK:%d",
                    mDoAE?1:0, mDoAF?1:0, mNeedsLockedAE?1:0, mNeedsLockedAWB?1:0));

            // Keep issuing capture requests until 3A has converged.
            while (true) {

                // Block until can take the next 3A frame. Only want one outstanding frame
                // at a time, to simplify the logic here.
                if (!mInterlock3A.block(TIMEOUT_3A * 1000) ||
                        System.currentTimeMillis() - tstart > TIMEOUT_3A * 1000) {
                    throw new ItsException(
                            "3A failed to converge after " + TIMEOUT_3A + " seconds.\n" +
                            "AE converge state: " + mConvergedAE + ", \n" +
                            "AF convergence state: " + mConvergedAF + ", \n" +
                            "AWB convergence state: " + mConvergedAWB + ".");
                }
                mInterlock3A.close();

                synchronized(m3AStateLock) {
                    // If not converged yet, issue another capture request.
                    if (       (mDoAE && (!triggeredAE || !mConvergedAE))
                            || !mConvergedAWB
                            || (mDoAF && (!triggeredAF || !mConvergedAF))
                            || (mDoAE && mNeedsLockedAE && !mLockedAE)
                            || (mNeedsLockedAWB && !mLockedAWB)) {

                        // Baseline capture request for 3A.
                        CaptureRequest.Builder req = mCamera.createCaptureRequest(
                                CameraDevice.TEMPLATE_PREVIEW);
                        req.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF);
                        req.set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO);
                        req.set(CaptureRequest.CONTROL_CAPTURE_INTENT,
                                CaptureRequest.CONTROL_CAPTURE_INTENT_PREVIEW);
                        req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, 0);
                        req.set(CaptureRequest.CONTROL_AE_LOCK, false);
                        req.set(CaptureRequest.CONTROL_AE_REGIONS, regionAE);
                        req.set(CaptureRequest.CONTROL_AF_MODE,
                                CaptureRequest.CONTROL_AF_MODE_AUTO);
                        req.set(CaptureRequest.CONTROL_AF_REGIONS, regionAF);
                        req.set(CaptureRequest.CONTROL_AWB_MODE,
                                CaptureRequest.CONTROL_AWB_MODE_AUTO);
                        req.set(CaptureRequest.CONTROL_AWB_LOCK, false);
                        req.set(CaptureRequest.CONTROL_AWB_REGIONS, regionAWB);
                        // ITS only turns OIS on when it's explicitly requested
                        req.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
                                CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

                        if (evComp != 0) {
                            req.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evComp);
                        }

                        if (autoFlash == false) {
                            req.set(CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON);
                        } else {
                            req.set(CaptureRequest.CONTROL_AE_MODE,
                                    CaptureRequest.CONTROL_AE_MODE_ON_AUTO_FLASH);
                        }

                        if (mConvergedAE && mNeedsLockedAE) {
                            req.set(CaptureRequest.CONTROL_AE_LOCK, true);
                        }
                        if (mConvergedAWB && mNeedsLockedAWB) {
                            req.set(CaptureRequest.CONTROL_AWB_LOCK, true);
                        }

                        boolean triggering = false;
                        // Trigger AE first.
                        if (mDoAE && !triggeredAE) {
                            Logt.i(TAG, "Triggering AE");
                            req.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER,
                                    CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER_START);
                            triggeredAE = true;
                            triggering = true;
                        }

                        // After AE has converged, trigger AF.
                        if (mDoAF && !triggeredAF && (!mDoAE || (triggeredAE && mConvergedAE))) {
                            Logt.i(TAG, "Triggering AF");
                            req.set(CaptureRequest.CONTROL_AF_TRIGGER,
                                    CaptureRequest.CONTROL_AF_TRIGGER_START);
                            triggeredAF = true;
                            triggering = true;
                        }

                        req.addTarget(mOutputImageReaders[0].getSurface());

                        if (triggering) {
                            // Send single request for AE/AF trigger
                            mSession.capture(req.build(),
                                    threeAListener, mResultHandler);
                        } else {
                            // Use repeating request for non-trigger requests
                            mSession.setRepeatingRequest(req.build(),
                                    threeAListener, mResultHandler);
                        }
                    } else {
                        mSocketRunnableObj.sendResponse("3aConverged", "");
                        Logt.i(TAG, "3A converged");
                        break;
                    }
                }
            }
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        } finally {
            mSocketRunnableObj.sendResponse("3aDone", "");
            // stop listener from updating 3A states
            threeAListener.stop();
            if (mSession != null) {
                mSession.close();
            }
        }
    }

    private void doVibrate(JSONObject params) throws ItsException {
        try {
            if (mVibrator == null) {
                throw new ItsException("Unable to start vibrator");
            }
            JSONArray patternArray = params.getJSONArray(VIB_PATTERN_KEY);
            int len = patternArray.length();
            long pattern[] = new long[len];
            for (int i = 0; i < len; i++) {
                pattern[i] = patternArray.getLong(i);
            }
            Logt.i(TAG, String.format("Starting vibrator, pattern length %d",len));

            // Mark the vibrator as alarm to test the audio restriction API
            // TODO: consider making this configurable
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM).build();
            mVibrator.vibrate(pattern, -1, audioAttributes);
            mSocketRunnableObj.sendResponse("vibrationStarted", "");
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        }
    }

    private void doSetAudioRestriction(JSONObject params) throws ItsException {
        try {
            if (mCamera == null) {
                throw new ItsException("Camera is closed");
            }
            int mode = params.getInt(AUDIO_RESTRICTION_MODE_KEY);
            mCamera.setCameraAudioRestriction(mode);
            Logt.i(TAG, String.format("Set audio restriction mode to %d", mode));

            mSocketRunnableObj.sendResponse("audioRestrictionSet", "");
        } catch (org.json.JSONException e) {
            throw new ItsException("JSON error: ", e);
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        }
    }

    /**
     * Parse jsonOutputSpecs to get output surface sizes and formats. Create input and output
     * image readers for the parsed output surface sizes, output formats, and the given input
     * size and format.
     */
    private void prepareImageReadersWithOutputSpecs(JSONArray jsonOutputSpecs, Size inputSize,
            int inputFormat, int maxInputBuffers, boolean backgroundRequest) throws ItsException {
        Size outputSizes[];
        int outputFormats[];
        int numSurfaces = 0;
        mPhysicalStreamMap.clear();
        mStreamUseCaseMap.clear();

        if (jsonOutputSpecs != null) {
            try {
                numSurfaces = jsonOutputSpecs.length();
                if (backgroundRequest) {
                    numSurfaces += 1;
                }
                if (numSurfaces > MAX_NUM_OUTPUT_SURFACES) {
                    throw new ItsException("Too many output surfaces");
                }

                outputSizes = new Size[numSurfaces];
                outputFormats = new int[numSurfaces];
                for (int i = 0; i < numSurfaces; i++) {
                    // Append optional background stream at the end
                    if (backgroundRequest && i == numSurfaces - 1) {
                        outputFormats[i] = ImageFormat.YUV_420_888;
                        outputSizes[i] = new Size(640, 480);
                        continue;
                    }
                    // Get the specified surface.
                    JSONObject surfaceObj = jsonOutputSpecs.getJSONObject(i);
                    String physicalCameraId = surfaceObj.optString("physicalCamera");
                    CameraCharacteristics cameraCharacteristics =  mCameraCharacteristics;
                    mPhysicalStreamMap.put(i, physicalCameraId);
                    if (!physicalCameraId.isEmpty()) {
                        cameraCharacteristics = mPhysicalCameraChars.get(physicalCameraId);
                    }

                    String sformat = surfaceObj.optString("format");
                    Size sizes[];
                    if ("yuv".equals(sformat) || "".equals(sformat)) {
                        // Default to YUV if no format is specified.
                        outputFormats[i] = ImageFormat.YUV_420_888;
                        sizes = ItsUtils.getYuvOutputSizes(cameraCharacteristics);
                    } else if ("jpg".equals(sformat) || "jpeg".equals(sformat)) {
                        outputFormats[i] = ImageFormat.JPEG;
                        sizes = ItsUtils.getJpegOutputSizes(cameraCharacteristics);
                    } else if ("raw".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW_SENSOR;
                        sizes = ItsUtils.getRaw16OutputSizes(cameraCharacteristics);
                    } else if ("raw10".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW10;
                        sizes = ItsUtils.getRaw10OutputSizes(cameraCharacteristics);
                    } else if ("raw12".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW12;
                        sizes = ItsUtils.getRaw12OutputSizes(cameraCharacteristics);
                    } else if ("dng".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW_SENSOR;
                        sizes = ItsUtils.getRaw16OutputSizes(cameraCharacteristics);
                        mCaptureRawIsDng = true;
                    } else if ("rawStats".equals(sformat)) {
                        outputFormats[i] = ImageFormat.RAW_SENSOR;
                        sizes = ItsUtils.getRaw16OutputSizes(cameraCharacteristics);
                        mCaptureRawIsStats = true;
                        mCaptureStatsGridWidth = surfaceObj.optInt("gridWidth");
                        mCaptureStatsGridHeight = surfaceObj.optInt("gridHeight");
                    } else if ("y8".equals(sformat)) {
                        outputFormats[i] = ImageFormat.Y8;
                        sizes = ItsUtils.getY8OutputSizes(cameraCharacteristics);
                    } else {
                        throw new ItsException("Unsupported format: " + sformat);
                    }
                    // If the size is omitted, then default to the largest allowed size for the
                    // format.
                    int width = surfaceObj.optInt("width");
                    int height = surfaceObj.optInt("height");
                    if (width <= 0) {
                        if (sizes == null || sizes.length == 0) {
                            throw new ItsException(String.format(
                                    "Zero stream configs available for requested format: %s",
                                    sformat));
                        }
                        width = ItsUtils.getMaxSize(sizes).getWidth();
                    }
                    if (height <= 0) {
                        height = ItsUtils.getMaxSize(sizes).getHeight();
                    }
                    // The stats computation only applies to the active array region.
                    int aaw = ItsUtils.getActiveArrayCropRegion(cameraCharacteristics).width();
                    int aah = ItsUtils.getActiveArrayCropRegion(cameraCharacteristics).height();
                    if (mCaptureStatsGridWidth <= 0 || mCaptureStatsGridWidth > aaw) {
                        mCaptureStatsGridWidth = aaw;
                    }
                    if (mCaptureStatsGridHeight <= 0 || mCaptureStatsGridHeight > aah) {
                        mCaptureStatsGridHeight = aah;
                    }

                    outputSizes[i] = new Size(width, height);
                    if (!surfaceObj.isNull("useCase")) {
                        mStreamUseCaseMap.put(i, surfaceObj.optLong("useCase"));
                    }
                }
            } catch (org.json.JSONException e) {
                throw new ItsException("JSON error", e);
            }
        } else {
            // No surface(s) specified at all.
            // Default: a single output surface which is full-res YUV.
            Size maxYuvSize = ItsUtils.getMaxOutputSize(
                    mCameraCharacteristics, ImageFormat.YUV_420_888);
            numSurfaces = backgroundRequest ? 2 : 1;

            outputSizes = new Size[numSurfaces];
            outputFormats = new int[numSurfaces];
            outputSizes[0] = maxYuvSize;
            outputFormats[0] = ImageFormat.YUV_420_888;
            if (backgroundRequest) {
                outputSizes[1] = new Size(640, 480);
                outputFormats[1] = ImageFormat.YUV_420_888;
            }
        }

        prepareImageReaders(outputSizes, outputFormats, inputSize, inputFormat, maxInputBuffers);
    }

    /**
     * Wait until mCountCallbacksRemaining is 0 or a specified amount of time has elapsed between
     * each callback.
     */
    private void waitForCallbacks(long timeoutMs) throws ItsException {
        synchronized(mCountCallbacksRemaining) {
            int currentCount = mCountCallbacksRemaining.get();
            while (currentCount > 0) {
                try {
                    mCountCallbacksRemaining.wait(timeoutMs);
                } catch (InterruptedException e) {
                    throw new ItsException("Waiting for callbacks was interrupted.", e);
                }

                int newCount = mCountCallbacksRemaining.get();
                if (newCount == currentCount) {
                    throw new ItsException("No callback received within timeout " +
                            timeoutMs + "ms");
                }
                currentCount = newCount;
            }
        }
    }

    private void doGetSupportedVideoQualities(String id) throws ItsException {
        int cameraId = Integer.parseInt(id);
        StringBuilder profiles = new StringBuilder();
        for (Map.Entry<Integer, String> entry : CAMCORDER_PROFILE_QUALITIES_MAP.entrySet()) {
            appendSupportProfile(profiles, entry.getValue(), entry.getKey(), cameraId);
        }
        mSocketRunnableObj.sendResponse("supportedVideoQualities", profiles.toString());
    }

    private void appendSupportProfile(StringBuilder profiles, String name, int profile,
            int cameraId) {
        if (CamcorderProfile.hasProfile(cameraId, profile)) {
            profiles.append(name).append(':').append(profile).append(';');
        }
    }

    private boolean isVideoStabilizationModeSupported(int mode) {
        int[] videoStabilizationModes = mCameraCharacteristics.get(
                CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES);
        List<Integer> arrList = Arrays.asList(CameraTestUtils.toObject(videoStabilizationModes));
        assert(videoStabilizationModes != null);
        assert(arrList.contains(CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_OFF));
        Log.i(TAG, "videoStabilizationModes:" + Arrays.toString(videoStabilizationModes));
        return arrList.contains(mode);
    }

    private void doGetSupportedPreviewSizes(String id) throws ItsException {
        StreamConfigurationMap configMap = mCameraCharacteristics.get(
                CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
        if (!StreamConfigurationMap.isOutputSupportedFor(SurfaceHolder.class)) {
            mSocketRunnableObj.sendResponse("supportedPreviewSizes", "");
            return;
        }

        // s1440p which is the max supported stream size in a combination, when preview
        // stabilization is on.
        Size maxPreviewSize = new Size(1920, 1440);
        // 320 x 240, we test only sizes >= this.
        Size minPreviewSize = new Size(320, 240);
        Size[] outputSizes = configMap.getOutputSizes(ImageFormat.YUV_420_888);
        if (outputSizes == null) {
            mSocketRunnableObj.sendResponse("supportedPreviewSizes", "");
            return;
        }

        String response = Arrays.stream(outputSizes)
                .distinct()
                .filter(s -> s.getWidth() * s.getHeight()
                        <= maxPreviewSize.getWidth() * maxPreviewSize.getHeight())
                .filter(s -> s.getWidth() * s.getHeight()
                        >= minPreviewSize.getWidth() * minPreviewSize.getHeight())
                .sorted(Comparator.comparingInt(s -> s.getWidth() * s.getHeight()))
                .map(Size::toString)
                .collect(Collectors.joining(";"));

        mSocketRunnableObj.sendResponse("supportedPreviewSizes", response);
    }

    private class MediaCodecListener extends MediaCodec.Callback {
        private final MediaMuxer mMediaMuxer;
        private final Object mCondition;
        private int mTrackId = -1;
        private boolean mEndOfStream = false;

        private MediaCodecListener(MediaMuxer mediaMuxer, Object condition) {
            mMediaMuxer = mediaMuxer;
            mCondition = condition;
        }

        @Override
        public void onInputBufferAvailable(MediaCodec codec, int index) {
            Log.e(TAG, "Unexpected input buffer available callback!");
        }

        @Override
        public void onOutputBufferAvailable(MediaCodec codec, int index,
                MediaCodec.BufferInfo info) {
            synchronized (mCondition) {
                if (mTrackId < 0) {
                    return;
                }

                if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                    mEndOfStream = true;
                    mCondition.notifyAll();
                }

                if (!mEndOfStream) {
                    mMediaMuxer.writeSampleData(mTrackId, codec.getOutputBuffer(index), info);
                    codec.releaseOutputBuffer(index, false);
                }
            }
        }

        @Override
        public void onError(MediaCodec codec, MediaCodec.CodecException e) {
            Log.e(TAG, "Codec error: " + e.getDiagnosticInfo());
        }

        @Override
        public void onOutputFormatChanged(MediaCodec codec, MediaFormat format) {
            synchronized (mCondition) {
                mTrackId = mMediaMuxer.addTrack(format);
                mMediaMuxer.start();
            }
        }
    }

    private void doBasicRecording(String cameraId, int profileId, String quality,
            int recordingDuration, int videoStabilizationMode, boolean hlg10Enabled)
            throws ItsException {
        final long SESSION_CLOSE_TIMEOUT_MS  = 3000;

        if (!hlg10Enabled) {
            doBasicRecording(cameraId, profileId, quality, recordingDuration,
                    videoStabilizationMode);
            return;
        }

        int cameraDeviceId = Integer.parseInt(cameraId);
        CamcorderProfile camcorderProfile = getCamcorderProfile(cameraDeviceId, profileId);
        assert (camcorderProfile != null);
        boolean supportsVideoStabilizationMode = isVideoStabilizationModeSupported(
                videoStabilizationMode);
        if (!supportsVideoStabilizationMode) {
            throw new ItsException("Device does not support video stabilization mode: " +
                    videoStabilizationMode);
        }
        Size videoSize = new Size(camcorderProfile.videoFrameWidth,
                camcorderProfile.videoFrameHeight);
        int fileFormat = camcorderProfile.fileFormat;
        String outputFilePath = getOutputMediaFile(cameraDeviceId, videoSize, quality, fileFormat,
                /* hlg10Enabled= */ true,
                /* stabilized= */
                videoStabilizationMode != CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        assert (outputFilePath != null);

        MediaCodecList list = new MediaCodecList(MediaCodecList.REGULAR_CODECS);
        MediaFormat format = initializeHLG10Format(videoSize, camcorderProfile.videoBitRate,
                camcorderProfile.videoFrameRate);

        String codecName = list.findEncoderForFormat(format);
        assert (codecName != null);

        int[] caps = mCameraCharacteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        assert ((caps != null) && IntStream.of(caps).anyMatch(x -> x ==
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_DYNAMIC_RANGE_TEN_BIT));

        DynamicRangeProfiles profiles = mCameraCharacteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_DYNAMIC_RANGE_PROFILES);
        assert ((profiles != null) &&
                profiles.getSupportedProfiles().contains(DynamicRangeProfiles.HLG10));

        MediaCodec mediaCodec = null;
        MediaMuxer muxer = null;
        Log.i(TAG, "Video recording outputFilePath:"+ outputFilePath);
        try {
            muxer = new MediaMuxer(outputFilePath,
                    MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        } catch (IOException e) {
            throw new ItsException("Error preparing the MediaMuxer.");
        }
        try {
            mediaCodec = MediaCodec.createByCodecName(codecName);
        } catch (IOException e) {
            throw new ItsException("Error preparing the MediaCodec.");
        }

        mediaCodec.configure(format, null, null,
                MediaCodec.CONFIGURE_FLAG_ENCODE);
        Object condition = new Object();
        mediaCodec.setCallback(new MediaCodecListener(muxer, condition), mCameraHandler);

        mRecordSurface = mediaCodec.createInputSurface();
        assert(mRecordSurface != null);

        CameraCaptureSession.StateCallback mockCallback = mock(
                CameraCaptureSession.StateCallback.class);
        // Configure and create capture session.
        try {
            configureAndCreateCaptureSession(CameraDevice.TEMPLATE_RECORD, mRecordSurface,
                    videoStabilizationMode, DynamicRangeProfiles.HLG10, mockCallback);
        } catch (CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        }

        Log.i(TAG, "Now recording video for quality: " + quality + " profile id: " +
                profileId + " cameraId: " + cameraDeviceId + " size: " + videoSize + " in HLG10!");
        mediaCodec.start();
        try {
            Thread.sleep(recordingDuration * 1000); // recordingDuration is in seconds
        } catch (InterruptedException e) {
            throw new ItsException("Unexpected InterruptedException: ", e);
        }

        mediaCodec.signalEndOfInputStream();
        mSession.close();
        verify(mockCallback, timeout(SESSION_CLOSE_TIMEOUT_MS).
                times(1)).onClosed(eq(mSession));

        synchronized (condition) {
            try {
                condition.wait(SESSION_CLOSE_TIMEOUT_MS);
            } catch (InterruptedException e) {
                throw new ItsException("Unexpected InterruptedException: ", e);
            }
        }

        muxer.stop();
        mediaCodec.stop();
        mediaCodec.release();
        muxer.release();
        mRecordSurface.release();
        mRecordSurface = null;

        Log.i(TAG, "10-bit Recording Done for quality: " + quality);

        // Send VideoRecordingObject for further processing.
        VideoRecordingObject obj = new VideoRecordingObject(outputFilePath,
                quality, videoSize, camcorderProfile.videoFrameRate, fileFormat);
        mSocketRunnableObj.sendVideoRecordingObject(obj);
    }

    private void doBasicRecording(String cameraId, int profileId, String quality,
            int recordingDuration, int videoStabilizationMode) throws ItsException {
        int cameraDeviceId = Integer.parseInt(cameraId);
        mMediaRecorder = new MediaRecorder();
        CamcorderProfile camcorderProfile = getCamcorderProfile(cameraDeviceId, profileId);
        assert(camcorderProfile != null);
        boolean supportsVideoStabilizationMode = isVideoStabilizationModeSupported(
                videoStabilizationMode);
        if (!supportsVideoStabilizationMode) {
            throw new ItsException("Device does not support video stabilization mode: " +
                    videoStabilizationMode);
        }
        Size videoSize = new Size(camcorderProfile.videoFrameWidth,
                camcorderProfile.videoFrameHeight);
        int fileFormat = camcorderProfile.fileFormat;
        String outputFilePath = getOutputMediaFile(cameraDeviceId, videoSize, quality,
                fileFormat, /* stabilized= */
                videoStabilizationMode != CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
        assert(outputFilePath != null);
        Log.i(TAG, "Video recording outputFilePath:"+ outputFilePath);
        setupMediaRecorderWithProfile(cameraDeviceId, camcorderProfile, outputFilePath);
        // Prepare MediaRecorder
        try {
            mMediaRecorder.prepare();
        } catch (IOException e) {
            throw new ItsException("Error preparing the MediaRecorder.");
        }

        mRecordSurface = mMediaRecorder.getSurface();
        // Configure and create capture session.
        try {
            configureAndCreateCaptureSession(CameraDevice.TEMPLATE_RECORD, mRecordSurface,
                    videoStabilizationMode);
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        }
        // Start Recording
        if (mMediaRecorder != null) {
            Log.i(TAG, "Now recording video for quality: " + quality + " profile id: " +
                profileId + " cameraId: " + cameraDeviceId + " size: " + videoSize);
            mMediaRecorder.start();
            try {
                Thread.sleep(recordingDuration*1000); // recordingDuration is in seconds
            } catch (InterruptedException e) {
                throw new ItsException("Unexpected InterruptedException: ", e);
            }
            // Stop MediaRecorder
            mMediaRecorder.stop();
            mSession.close();
            mMediaRecorder.reset();
            mMediaRecorder.release();
            mMediaRecorder = null;
            if (mRecordSurface != null) {
                mRecordSurface.release();
                mRecordSurface = null;
            }
        }

        Log.i(TAG, "Recording Done for quality: " + quality);

        // Send VideoRecordingObject for further processing.
        VideoRecordingObject obj = new VideoRecordingObject(outputFilePath,
                quality, videoSize, camcorderProfile.videoFrameRate, fileFormat);
        mSocketRunnableObj.sendVideoRecordingObject(obj);
    }

    /**
     * Records a video of a surface set up as a preview.
     *
     * This method sets up 2 surfaces: an {@link ImageReader} surface and a
     * {@link MediaRecorder} surface. The ImageReader surface is set up with
     * {@link HardwareBuffer#USAGE_COMPOSER_OVERLAY} and set as the target of a capture request
     * created with {@link CameraDevice#TEMPLATE_PREVIEW}. This should force the HAL to use the
     * Preview pipeline and output to the ImageReader. An {@link ImageWriter} pipes the images from
     * ImageReader to the MediaRecorder surface which is encoded into a video.
     */
    private void doBasicPreviewRecording(String cameraId, String videoSizeString,
            int recordingDuration, boolean stabilize)
            throws ItsException {

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            throw new ItsException("Cannot record preview before API level 33");
        }

        boolean stabilizationSupported = isVideoStabilizationModeSupported(
                CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION);
        if (stabilize && !stabilizationSupported) {
            throw new ItsException("Preview stabilization requested, but not supported by device.");
        }

        int cameraDeviceId = Integer.parseInt(cameraId);
        Size videoSize = Size.parseSize(videoSizeString);
        int sensorOrientation = mCameraCharacteristics.get(
                CameraCharacteristics.SENSOR_ORIENTATION);

        // Set up MediaRecorder to accept Images from ImageWriter
        int fileFormat = MediaRecorder.OutputFormat.DEFAULT;

        String outputFilePath = getOutputMediaFile(cameraDeviceId, videoSize,
                /* quality= */"preview", fileFormat, stabilize);
        assert outputFilePath != null;

        try (PreviewRecorder pr = new PreviewRecorder(cameraDeviceId, videoSize,
                sensorOrientation, outputFilePath, mCameraHandler, this)) {
            int stabilizationMode = stabilize
                    ? CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION
                    : CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF;
            configureAndCreateCaptureSession(CameraDevice.TEMPLATE_PREVIEW,
                    pr.getCameraSurface(), stabilizationMode);
            pr.recordPreview(recordingDuration * 1000L);
            mSession.close();
        } catch (CameraAccessException e) {
            throw new ItsException("Error configuring and creating capture request", e);
        }

        Log.i(TAG, "Preview recording complete: " + outputFilePath);
        // Send VideoRecordingObject for further processing.
        VideoRecordingObject obj = new VideoRecordingObject(outputFilePath, /* quality= */"preview",
                videoSize, fileFormat);
        mSocketRunnableObj.sendVideoRecordingObject(obj);
    }

    private void configureAndCreateCaptureSession(int requestTemplate, Surface recordSurface,
            int videoStabilizationMode) throws CameraAccessException {
        configureAndCreateCaptureSession(requestTemplate, recordSurface, videoStabilizationMode,
                DynamicRangeProfiles.STANDARD, /* stateCallback= */ null);
    }

    private void configureAndCreateCaptureSession(int requestTemplate, Surface recordSurface,
            int videoStabilizationMode, long dynamicRangeProfile,
            CameraCaptureSession.StateCallback stateCallback) throws CameraAccessException {
        assert (recordSurface != null);
        // Create capture request builder
        mCaptureRequestBuilder = mCamera.createCaptureRequest(requestTemplate);
        mCaptureRequestBuilder.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE,
            CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);

        switch (videoStabilizationMode) {
            case CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON:
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_ON);
                Log.i(TAG, "Turned ON video stabilization.");
                break;
            case CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION:
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_PREVIEW_STABILIZATION);
                Log.i(TAG, "Turned ON preview stabilization.");
                break;
            case CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF:
                mCaptureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE,
                        CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);
                Log.i(TAG, "Turned OFF video stabilization.");
                break;
            default:
                Log.w(TAG, "Invalid video stabilization mode " + videoStabilizationMode
                        + ". Leaving unchanged.");
                break;
        }

        mCaptureRequestBuilder.addTarget(recordSurface);
        OutputConfiguration outConfig = new OutputConfiguration(recordSurface);
        outConfig.setDynamicRangeProfile(dynamicRangeProfile);

        SessionConfiguration sessionConfiguration = new SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR, List.of(outConfig),
                new HandlerExecutor(mCameraHandler),
                new CameraCaptureSession.StateCallback() {
                    @Override
                    public void onConfigured(CameraCaptureSession session) {
                        mSession = session;
                        try {
                            mSession.setRepeatingRequest(mCaptureRequestBuilder.build(), null, null);
                        } catch (CameraAccessException e) {
                            e.printStackTrace();
                        }
                    }

                    @Override
                    public void onConfigureFailed(CameraCaptureSession session) {
                        Log.i(TAG, "CameraCaptureSession configuration failed.");
                    }

                    @Override
                    public void onClosed(CameraCaptureSession session) {
                        if (stateCallback != null) {
                            stateCallback.onClosed(session);
                        }
                    }
                });

        // Create capture session
        mCamera.createCaptureSession(sessionConfiguration);
    }

    // Returns the default camcorder profile for the given camera at the given quality level
    // Each CamcorderProfile has duration, quality, fileFormat, videoCodec, videoBitRate,
    // videoFrameRate,videoWidth, videoHeight, audioCodec, audioBitRate, audioSampleRate
    // and audioChannels.
    private CamcorderProfile getCamcorderProfile(int cameraId, int profileId) {
        CamcorderProfile camcorderProfile = CamcorderProfile.get(cameraId, profileId);
        return camcorderProfile;
    }

    // This method should be called before preparing MediaRecorder.
    // Set video and audio source should be done before setting the CamcorderProfile.
    // Output file path should be set after setting the CamcorderProfile.
    // These events should always be done in this particular order.
    private void setupMediaRecorderWithProfile(int cameraId, CamcorderProfile camcorderProfile,
            String outputFilePath) {
        mMediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mMediaRecorder.setAudioSource(MediaRecorder.AudioSource.DEFAULT);
        mMediaRecorder.setProfile(camcorderProfile);
        mMediaRecorder.setOutputFile(outputFilePath);
    }

    private String getOutputMediaFile(int cameraId, Size videoSize, String quality,
            int fileFormat, boolean stabilized) {
        return getOutputMediaFile(cameraId, videoSize, quality, fileFormat,
                /* hlg10Enabled= */false, stabilized);
    }

    private String getOutputMediaFile(int cameraId, Size videoSize, String quality,
            int fileFormat, boolean hlg10Enabled, boolean stabilized) {
        // If any quality has file format other than 3gp and webm then the
        // recording file will have mp4 as default extension.
        String fileExtension = "";
        if (fileFormat == MediaRecorder.OutputFormat.THREE_GPP) {
            fileExtension = ".3gp";
        } else if (fileFormat == MediaRecorder.OutputFormat.WEBM) {
            fileExtension = ".webm";
        } else {
            fileExtension = ".mp4";
        }
        // All the video recordings will be available in VideoITS directory on device.
        File mediaStorageDir = new File(getExternalFilesDir(null), "VideoITS");
        if (mediaStorageDir == null) {
            Log.e(TAG, "Failed to retrieve external files directory.");
            return null;
        }
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d(TAG, "Failed to create media storage directory.");
                return null;
            }
        }
        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String fileName = mediaStorageDir.getPath() + File.separator +
                "VID_" + timestamp + '_' + cameraId + '_' + quality + '_' + videoSize;
        if (hlg10Enabled) {
            fileName += "_hlg10";
        }
        if (stabilized) {
            fileName += "_stabilized";
        }
        File mediaFile = new File(fileName);
        return mediaFile + fileExtension;
    }

    private void doCaptureWithFlash(JSONObject params) throws ItsException {
        // Parse the json to get the capture requests
        List<CaptureRequest.Builder> previewStartRequests = ItsSerializer.deserializeRequestList(
            mCamera, params, "previewRequestStart");
        List<CaptureRequest.Builder> previewIdleRequests = ItsSerializer.deserializeRequestList(
            mCamera, params, "previewRequestIdle");
        List<CaptureRequest.Builder> stillCaptureRequests = ItsSerializer.deserializeRequestList(
            mCamera, params, "stillCaptureRequest");

        mCaptureResults = new CaptureResult[2];

        ThreeAResultListener threeAListener = new ThreeAResultListener();
        List<OutputConfiguration> outputConfigs = new ArrayList<OutputConfiguration>();
        SurfaceTexture preview = new SurfaceTexture(/*random int*/ 1);
        Surface previewSurface = new Surface(preview);
        try {
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            try {
                mCountCapRes.set(0);
                mCountJpg.set(0);
                JSONArray jsonOutputSpecs = ItsUtils.getOutputSpecs(params);
                prepareImageReadersWithOutputSpecs(jsonOutputSpecs, /*inputSize*/null,
                         /*inputFormat*/0,/*maxInputBuffers*/0,false);

                outputConfigs.add(new OutputConfiguration(mOutputImageReaders[0].getSurface()));
                outputConfigs.add(new OutputConfiguration(previewSurface));
                mCamera.createCaptureSessionByOutputConfigurations(
                        outputConfigs, sessionListener, mCameraHandler);
                mSession = sessionListener.waitAndGetSession(TIMEOUT_IDLE_MS);
                ImageReader.OnImageAvailableListener readerListener =
                        createAvailableListener(mCaptureCallback);
                mOutputImageReaders[0].setOnImageAvailableListener(readerListener,
                        mSaveHandlers[0]);
            } catch (Exception e) {
                throw new ItsException("Error configuring outputs", e);
            }
            CaptureRequest.Builder previewIdleReq = previewIdleRequests.get(0);
            previewIdleReq.addTarget(previewSurface);
            mSession.setRepeatingRequest(previewIdleReq.build(), threeAListener, mResultHandler);
            Logt.i(TAG, "Triggering precapture sequence");
            mPrecaptureTriggered = false;
            CaptureRequest.Builder previewStartReq = previewStartRequests.get(0);
            previewStartReq.addTarget(previewSurface);
            mSession.capture(previewStartReq.build(), threeAListener ,mResultHandler);
            mInterlock3A.open();
            synchronized(m3AStateLock) {
                mPrecaptureTriggered = false;
                mConvergeAETriggered = false;
            }
            long tstart = System.currentTimeMillis();
            boolean triggeredAE = false;
            while (!mPrecaptureTriggered) {
                if (!mInterlock3A.block(TIMEOUT_3A * 1000) ||
                        System.currentTimeMillis() - tstart > TIMEOUT_3A * 1000) {
                    throw new ItsException (
                        "AE state is " + CaptureResult.CONTROL_AE_STATE_PRECAPTURE +
                        "after " + TIMEOUT_3A + " seconds.");
                }
            }
            mConvergeAETriggered = false;

            tstart = System.currentTimeMillis();
            while (!mConvergeAETriggered) {
                if (!mInterlock3A.block(TIMEOUT_3A * 1000) ||
                        System.currentTimeMillis() - tstart > TIMEOUT_3A * 1000) {
                    throw new ItsException (
                        "3A failed to converge after " + TIMEOUT_3A + " seconds.\n" +
                        "AE converge state: " + mConvergedAE + ".");
                }
            }
            mInterlock3A.close();
            Logt.i(TAG, "AE state after precapture sequence: " + mConvergeAETriggered);
            threeAListener.stop();

            // Send a still capture request
            CaptureRequest.Builder stillCaptureRequest = stillCaptureRequests.get(0);
            Logt.i(TAG, "Taking still capture with ON_AUTO_FLASH.");
            stillCaptureRequest.addTarget(mOutputImageReaders[0].getSurface());
            mSession.capture(stillCaptureRequest.build(), mCaptureResultListener, mResultHandler);
            mCountCallbacksRemaining.set(1);
            long timeout = TIMEOUT_CALLBACK * 1000;
            waitForCallbacks(timeout);
            mSession.stopRepeating();
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        } finally {
            if (mSession != null) {
                mSession.close();
            }
            if (previewSurface != null) {
                previewSurface.release();
            }
            if (preview != null) {
                preview.release();
            }
        }
    }

    private void doCapture(JSONObject params) throws ItsException {
        try {
            // Parse the JSON to get the list of capture requests.
            List<CaptureRequest.Builder> requests = ItsSerializer.deserializeRequestList(
                    mCamera, params, "captureRequests");

            // optional background preview requests
            List<CaptureRequest.Builder> backgroundRequests = ItsSerializer.deserializeRequestList(
                    mCamera, params, "repeatRequests");
            boolean backgroundRequest = backgroundRequests.size() > 0;

            int numSurfaces = 0;
            int numCaptureSurfaces = 0;
            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            try {
                mCountRawOrDng.set(0);
                mCountJpg.set(0);
                mCountYuv.set(0);
                mCountRaw10.set(0);
                mCountRaw12.set(0);
                mCountCapRes.set(0);
                mCaptureRawIsDng = false;
                mCaptureRawIsStats = false;
                mCaptureResults = new CaptureResult[requests.size()];

                JSONArray jsonOutputSpecs = ItsUtils.getOutputSpecs(params);

                prepareImageReadersWithOutputSpecs(jsonOutputSpecs, /*inputSize*/null,
                        /*inputFormat*/0, /*maxInputBuffers*/0, backgroundRequest);
                numSurfaces = mOutputImageReaders.length;
                numCaptureSurfaces = numSurfaces - (backgroundRequest ? 1 : 0);

                List<OutputConfiguration> outputConfigs =
                        new ArrayList<OutputConfiguration>(numSurfaces);
                for (int i = 0; i < numSurfaces; i++) {
                    OutputConfiguration config = new OutputConfiguration(
                            mOutputImageReaders[i].getSurface());
                    if (mPhysicalStreamMap.get(i) != null) {
                        config.setPhysicalCameraId(mPhysicalStreamMap.get(i));
                    }
                    if (mStreamUseCaseMap.get(i) != null) {
                        config.setStreamUseCase(mStreamUseCaseMap.get(i));
                    }
                    outputConfigs.add(config);
                }
                mCamera.createCaptureSessionByOutputConfigurations(outputConfigs,
                        sessionListener, mCameraHandler);
                mSession = sessionListener.waitAndGetSession(TIMEOUT_IDLE_MS);

                for (int i = 0; i < numSurfaces; i++) {
                    ImageReader.OnImageAvailableListener readerListener;
                    if (backgroundRequest && i == numSurfaces - 1) {
                        readerListener = createAvailableListenerDropper();
                    } else {
                        readerListener = createAvailableListener(mCaptureCallback);
                    }
                    mOutputImageReaders[i].setOnImageAvailableListener(readerListener,
                            mSaveHandlers[i]);
                }

                // Plan for how many callbacks need to be received throughout the duration of this
                // sequence of capture requests. There is one callback per image surface, and one
                // callback for the CaptureResult, for each capture.
                int numCaptures = requests.size();
                mCountCallbacksRemaining.set(numCaptures * (numCaptureSurfaces + 1));

            } catch (CameraAccessException e) {
                throw new ItsException("Error configuring outputs", e);
            }

            // Start background requests and let it warm up pipeline
            if (backgroundRequest) {
                List<CaptureRequest> bgRequestList =
                        new ArrayList<CaptureRequest>(backgroundRequests.size());
                for (int i = 0; i < backgroundRequests.size(); i++) {
                    CaptureRequest.Builder req = backgroundRequests.get(i);
                    req.addTarget(mOutputImageReaders[numCaptureSurfaces].getSurface());
                    bgRequestList.add(req.build());
                }
                mSession.setRepeatingBurst(bgRequestList, null, null);
                // warm up the pipeline
                Thread.sleep(PIPELINE_WARMUP_TIME_MS);
            }

            // Initiate the captures.
            long maxExpTimeNs = -1;
            List<CaptureRequest> requestList =
                    new ArrayList<>(requests.size());
            for (int i = 0; i < requests.size(); i++) {
                CaptureRequest.Builder req = requests.get(i);
                // For DNG captures, need the LSC map to be available.
                if (mCaptureRawIsDng) {
                    req.set(CaptureRequest.STATISTICS_LENS_SHADING_MAP_MODE, 1);
                }
                Long expTimeNs = req.get(CaptureRequest.SENSOR_EXPOSURE_TIME);
                if (expTimeNs != null && expTimeNs > maxExpTimeNs) {
                    maxExpTimeNs = expTimeNs;
                }

                for (int j = 0; j < numCaptureSurfaces; j++) {
                    req.addTarget(mOutputImageReaders[j].getSurface());
                }
                requestList.add(req.build());
            }
            mSession.captureBurst(requestList, mCaptureResultListener, mResultHandler);

            long timeout = TIMEOUT_CALLBACK * 1000;
            if (maxExpTimeNs > 0) {
                timeout += maxExpTimeNs / 1000000; // ns to ms
            }
            // Make sure all callbacks have been hit (wait until captures are done).
            // If no timeouts are received after a timeout, then fail.
            waitForCallbacks(timeout);

            // Close session and wait until session is fully closed
            mSession.close();
            sessionListener.getStateWaiter().waitForState(
                    BlockingSessionCallback.SESSION_CLOSED, TIMEOUT_SESSION_CLOSE);

        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        } catch (InterruptedException e) {
            throw new ItsException("Unexpected InterruptedException: ", e);
        }
    }

    /**
     * Perform reprocess captures.
     *
     * It takes captureRequests in a JSON object and perform capture requests in two steps:
     * regular capture request to get reprocess input and reprocess capture request to get
     * reprocess outputs.
     *
     * Regular capture requests:
     *   1. For each capture request in the JSON object, create a full-size capture request with
     *      the settings in the JSON object.
     *   2. Remember and clear noise reduction, edge enhancement, and effective exposure factor
     *      from the regular capture requests. (Those settings will be used for reprocess requests.)
     *   3. Submit the regular capture requests.
     *
     * Reprocess capture requests:
     *   4. Wait for the regular capture results and use them to create reprocess capture requests.
     *   5. Wait for the regular capture output images and queue them to the image writer.
     *   6. Set the noise reduction, edge enhancement, and effective exposure factor from #2.
     *   7. Submit the reprocess capture requests.
     *
     * The output images and results for the regular capture requests won't be written to socket.
     * The output images and results for the reprocess capture requests will be written to socket.
     */
    private void doReprocessCapture(JSONObject params) throws ItsException {
        ImageWriter imageWriter = null;
        ArrayList<Integer> noiseReductionModes = new ArrayList<>();
        ArrayList<Integer> edgeModes = new ArrayList<>();
        ArrayList<Float> effectiveExposureFactors = new ArrayList<>();

        mCountRawOrDng.set(0);
        mCountJpg.set(0);
        mCountYuv.set(0);
        mCountRaw10.set(0);
        mCountRaw12.set(0);
        mCountCapRes.set(0);
        mCaptureRawIsDng = false;
        mCaptureRawIsStats = false;

        try {
            // Parse the JSON to get the list of capture requests.
            List<CaptureRequest.Builder> inputRequests =
                    ItsSerializer.deserializeRequestList(mCamera, params, "captureRequests");

            // Prepare the image readers for reprocess input and reprocess outputs.
            int inputFormat = getReprocessInputFormat(params);
            Size inputSize = ItsUtils.getMaxOutputSize(mCameraCharacteristics, inputFormat);
            JSONArray jsonOutputSpecs = ItsUtils.getOutputSpecs(params);
            prepareImageReadersWithOutputSpecs(jsonOutputSpecs, inputSize, inputFormat,
                    inputRequests.size(), /*backgroundRequest*/false);

            // Prepare a reprocessable session.
            int numOutputSurfaces = mOutputImageReaders.length;
            InputConfiguration inputConfig = new InputConfiguration(inputSize.getWidth(),
                    inputSize.getHeight(), inputFormat);
            List<Surface> outputSurfaces = new ArrayList<Surface>();
            boolean addSurfaceForInput = true;
            for (int i = 0; i < numOutputSurfaces; i++) {
                outputSurfaces.add(mOutputImageReaders[i].getSurface());
                if (mOutputImageReaders[i] == mInputImageReader) {
                    // If input and one of the outputs share the same image reader, avoid
                    // adding the same surfaces twice.
                    addSurfaceForInput = false;
                }
            }

            if (addSurfaceForInput) {
                // Besides the output surfaces specified in JSON object, add an additional one
                // for reprocess input.
                outputSurfaces.add(mInputImageReader.getSurface());
            }

            BlockingSessionCallback sessionListener = new BlockingSessionCallback();
            mCamera.createReprocessableCaptureSession(inputConfig, outputSurfaces, sessionListener,
                    mCameraHandler);
            mSession = sessionListener.waitAndGetSession(TIMEOUT_IDLE_MS);

            // Create an image writer for reprocess input.
            Surface inputSurface = mSession.getInputSurface();
            imageWriter = ImageWriter.newInstance(inputSurface, inputRequests.size());

            // Set up input reader listener and capture callback listener to get
            // reprocess input buffers and the results in order to create reprocess capture
            // requests.
            ImageReaderListenerWaiter inputReaderListener = new ImageReaderListenerWaiter();
            mInputImageReader.setOnImageAvailableListener(inputReaderListener, mSaveHandlers[0]);

            CaptureCallbackWaiter captureCallbackWaiter = new CaptureCallbackWaiter();
            // Prepare the reprocess input request
            for (CaptureRequest.Builder inputReqest : inputRequests) {
                // Remember and clear noise reduction, edge enhancement, and effective exposure
                // factors.
                noiseReductionModes.add(inputReqest.get(CaptureRequest.NOISE_REDUCTION_MODE));
                edgeModes.add(inputReqest.get(CaptureRequest.EDGE_MODE));
                effectiveExposureFactors.add(inputReqest.get(
                        CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR));

                inputReqest.set(CaptureRequest.NOISE_REDUCTION_MODE,
                        CaptureRequest.NOISE_REDUCTION_MODE_ZERO_SHUTTER_LAG);
                inputReqest.set(CaptureRequest.EDGE_MODE, CaptureRequest.EDGE_MODE_ZERO_SHUTTER_LAG);
                inputReqest.set(CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR, null);
                inputReqest.addTarget(mInputImageReader.getSurface());
                mSession.capture(inputReqest.build(), captureCallbackWaiter, mResultHandler);
            }

            // Wait for reprocess input images
            ArrayList<CaptureRequest.Builder> reprocessOutputRequests = new ArrayList<>();
            for (int i = 0; i < inputRequests.size(); i++) {
                TotalCaptureResult result =
                        captureCallbackWaiter.getResult(TIMEOUT_CALLBACK * 1000);
                reprocessOutputRequests.add(mCamera.createReprocessCaptureRequest(result));
                imageWriter.queueInputImage(inputReaderListener.getImage(TIMEOUT_CALLBACK * 1000));
            }

            // Start performing reprocess captures.

            mCaptureResults = new CaptureResult[inputRequests.size()];

            // Prepare reprocess capture requests.
            for (int i = 0; i < numOutputSurfaces; i++) {
                ImageReader.OnImageAvailableListener outputReaderListener =
                        createAvailableListener(mCaptureCallback);
                mOutputImageReaders[i].setOnImageAvailableListener(outputReaderListener,
                        mSaveHandlers[i]);
            }

            // Plan for how many callbacks need to be received throughout the duration of this
            // sequence of capture requests. There is one callback per image surface, and one
            // callback for the CaptureResult, for each capture.
            int numCaptures = reprocessOutputRequests.size();
            mCountCallbacksRemaining.set(numCaptures * (numOutputSurfaces + 1));

            // Initiate the captures.
            for (int i = 0; i < reprocessOutputRequests.size(); i++) {
                CaptureRequest.Builder req = reprocessOutputRequests.get(i);
                for (ImageReader outputImageReader : mOutputImageReaders) {
                    req.addTarget(outputImageReader.getSurface());
                }

                req.set(CaptureRequest.NOISE_REDUCTION_MODE, noiseReductionModes.get(i));
                req.set(CaptureRequest.EDGE_MODE, edgeModes.get(i));
                req.set(CaptureRequest.REPROCESS_EFFECTIVE_EXPOSURE_FACTOR,
                        effectiveExposureFactors.get(i));

                mSession.capture(req.build(), mCaptureResultListener, mResultHandler);
            }

            // Make sure all callbacks have been hit (wait until captures are done).
            // If no timeouts are received after a timeout, then fail.
            waitForCallbacks(TIMEOUT_CALLBACK * 1000);
        } catch (android.hardware.camera2.CameraAccessException e) {
            throw new ItsException("Access error: ", e);
        } finally {
            closeImageReaders();
            if (mSession != null) {
                mSession.close();
                mSession = null;
            }
            if (imageWriter != null) {
                imageWriter.close();
            }
        }
    }

    @Override
    public final void onAccuracyChanged(Sensor sensor, int accuracy) {
        Logt.i(TAG, "Sensor " + sensor.getName() + " accuracy changed to " + accuracy);
    }

    @Override
    public final void onSensorChanged(SensorEvent event) {
        synchronized(mEventLock) {
            if (mEventsEnabled) {
                MySensorEvent ev2 = new MySensorEvent();
                ev2.sensor = event.sensor;
                ev2.accuracy = event.accuracy;
                ev2.timestamp = event.timestamp;
                ev2.values = new float[event.values.length];
                System.arraycopy(event.values, 0, ev2.values, 0, event.values.length);
                mEvents.add(ev2);
            }
        }
    }

    private final CaptureCallback mCaptureCallback = new CaptureCallback() {
        @Override
        public void onCaptureAvailable(Image capture, String physicalCameraId) {
            try {
                int format = capture.getFormat();
                if (format == ImageFormat.JPEG) {
                    Logt.i(TAG, "Received JPEG capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountJpg.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("jpegImage"+physicalCameraId, buf);
                } else if (format == ImageFormat.YUV_420_888) {
                    Logt.i(TAG, "Received YUV capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    mSocketRunnableObj.sendResponseCaptureBuffer(
                            "yuvImage"+physicalCameraId, buf);
                } else if (format == ImageFormat.RAW10) {
                    Logt.i(TAG, "Received RAW10 capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountRaw10.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer(
                            "raw10Image"+physicalCameraId, buf);
                } else if (format == ImageFormat.RAW12) {
                    Logt.i(TAG, "Received RAW12 capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    int count = mCountRaw12.getAndIncrement();
                    mSocketRunnableObj.sendResponseCaptureBuffer("raw12Image"+physicalCameraId, buf);
                } else if (format == ImageFormat.RAW_SENSOR) {
                    Logt.i(TAG, "Received RAW16 capture");
                    int count = mCountRawOrDng.getAndIncrement();
                    if (! mCaptureRawIsDng) {
                        byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                        if (! mCaptureRawIsStats) {
                            ByteBuffer buf = ByteBuffer.wrap(img);
                            mSocketRunnableObj.sendResponseCaptureBuffer(
                                    "rawImage" + physicalCameraId, buf);
                        } else {
                            // Compute the requested stats on the raw frame, and return the results
                            // in a new "stats image".
                            long startTimeMs = SystemClock.elapsedRealtime();
                            int w = capture.getWidth();
                            int h = capture.getHeight();
                            int aaw = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .width();
                            int aah = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .height();
                            int aax = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .left;
                            int aay = ItsUtils.getActiveArrayCropRegion(mCameraCharacteristics)
                                              .top;

                            if (w == aaw) {
                                aax = 0;
                            }
                            if (h == aah) {
                                aay = 0;
                            }

                            int gw = mCaptureStatsGridWidth;
                            int gh = mCaptureStatsGridHeight;
                            float[] stats = StatsImage.computeStatsImage(
                                                             img, w, h, aax, aay, aaw, aah, gw, gh);
                            long endTimeMs = SystemClock.elapsedRealtime();
                            Log.e(TAG, "Raw stats computation takes " + (endTimeMs - startTimeMs) + " ms");
                            int statsImgSize = stats.length * 4;
                            if (mSocketQueueQuota != null) {
                                mSocketQueueQuota.release(img.length);
                                mSocketQueueQuota.acquire(statsImgSize);
                            }
                            ByteBuffer bBuf = ByteBuffer.allocate(statsImgSize);
                            bBuf.order(ByteOrder.nativeOrder());
                            FloatBuffer fBuf = bBuf.asFloatBuffer();
                            fBuf.put(stats);
                            fBuf.position(0);
                            mSocketRunnableObj.sendResponseCaptureBuffer(
                                    "rawStatsImage"+physicalCameraId, bBuf);
                        }
                    } else {
                        // Wait until the corresponding capture result is ready, up to a timeout.
                        long t0 = android.os.SystemClock.elapsedRealtime();
                        while (! mThreadExitFlag
                                && android.os.SystemClock.elapsedRealtime()-t0 < TIMEOUT_CAP_RES) {
                            if (mCaptureResults[count] != null) {
                                Logt.i(TAG, "Writing capture as DNG");
                                DngCreator dngCreator = new DngCreator(
                                        mCameraCharacteristics, mCaptureResults[count]);
                                ByteArrayOutputStream dngStream = new ByteArrayOutputStream();
                                dngCreator.writeImage(dngStream, capture);
                                byte[] dngArray = dngStream.toByteArray();
                                if (mSocketQueueQuota != null) {
                                    // Ideally we should acquire before allocating memory, but
                                    // here the DNG size is unknown before toByteArray call, so
                                    // we have to register the size afterward. This should still
                                    // works most of the time since all DNG images are handled by
                                    // the same handler thread, so we are at most one buffer over
                                    // the quota.
                                    mSocketQueueQuota.acquire(dngArray.length);
                                }
                                ByteBuffer dngBuf = ByteBuffer.wrap(dngArray);
                                mSocketRunnableObj.sendResponseCaptureBuffer("dngImage", dngBuf);
                                break;
                            } else {
                                Thread.sleep(1);
                            }
                        }
                    }
                } else if (format == ImageFormat.Y8) {
                    Logt.i(TAG, "Received Y8 capture");
                    byte[] img = ItsUtils.getDataFromImage(capture, mSocketQueueQuota);
                    ByteBuffer buf = ByteBuffer.wrap(img);
                    mSocketRunnableObj.sendResponseCaptureBuffer(
                            "y8Image"+physicalCameraId, buf);
                } else {
                    throw new ItsException("Unsupported image format: " + format);
                }

                synchronized(mCountCallbacksRemaining) {
                    mCountCallbacksRemaining.decrementAndGet();
                    mCountCallbacksRemaining.notify();
                }
            } catch (IOException e) {
                Logt.e(TAG, "Script error: ", e);
            } catch (InterruptedException e) {
                Logt.e(TAG, "Script error: ", e);
            } catch (ItsException e) {
                Logt.e(TAG, "Script error: ", e);
            }
        }
    };

    private static float r2f(Rational r) {
        return (float)r.getNumerator() / (float)r.getDenominator();
    }

    private boolean hasCapability(int capability) throws ItsException {
        int[] capabilities = mCameraCharacteristics.get(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES);
        if (capabilities == null) {
            throw new ItsException("Failed to get capabilities");
        }
        for (int c : capabilities) {
            if (c == capability) {
                return true;
            }
        }
        return false;
    }

    private String buildLogString(CaptureResult result) throws ItsException {
        StringBuilder logMsg = new StringBuilder();
        logMsg.append(String.format(
                "Capt result: AE=%d, AF=%d, AWB=%d, ",
                result.get(CaptureResult.CONTROL_AE_STATE),
                result.get(CaptureResult.CONTROL_AF_STATE),
                result.get(CaptureResult.CONTROL_AWB_STATE)));

        boolean readSensorSettings = hasCapability(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_READ_SENSOR_SETTINGS);

        if (readSensorSettings) {
            logMsg.append(String.format(
                    "sens=%d, exp=%.1fms, dur=%.1fms, ",
                    result.get(CaptureResult.SENSOR_SENSITIVITY),
                    result.get(CaptureResult.SENSOR_EXPOSURE_TIME).longValue() / 1000000.0f,
                    result.get(CaptureResult.SENSOR_FRAME_DURATION).longValue() /
                                1000000.0f));
        }
        if (result.get(CaptureResult.COLOR_CORRECTION_GAINS) != null) {
            logMsg.append(String.format(
                    "gains=[%.1f, %.1f, %.1f, %.1f], ",
                    result.get(CaptureResult.COLOR_CORRECTION_GAINS).getRed(),
                    result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenEven(),
                    result.get(CaptureResult.COLOR_CORRECTION_GAINS).getGreenOdd(),
                    result.get(CaptureResult.COLOR_CORRECTION_GAINS).getBlue()));
        } else {
            logMsg.append("gains=[], ");
        }
        if (result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) != null) {
            logMsg.append(String.format(
                    "xform=[%.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f, %.1f], ",
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,0)),
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,0)),
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,0)),
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,1)),
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,1)),
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,1)),
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(0,2)),
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(1,2)),
                    r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).getElement(2,2))));
        } else {
            logMsg.append("xform=[], ");
        }
        logMsg.append(String.format(
                "foc=%.1f",
                result.get(CaptureResult.LENS_FOCUS_DISTANCE)));
        return logMsg.toString();
    }

    private class ThreeAResultListener extends CaptureResultListener {
        private volatile boolean stopped = false;
        private boolean aeResultSent = false;
        private boolean awbResultSent = false;
        private boolean afResultSent = false;
        private CameraCharacteristics c = mCameraCharacteristics;
        private boolean isFixedFocusLens = isFixedFocusLens(c);

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                if (stopped) {
                    return;
                }

                if (request == null || result == null) {
                    throw new ItsException("Request/result is invalid");
                }

                Logt.i(TAG, buildLogString(result));

                synchronized(m3AStateLock) {
                    if (result.get(CaptureResult.CONTROL_AE_STATE) != null) {
                        mConvergedAE = result.get(CaptureResult.CONTROL_AE_STATE) ==
                                                  CaptureResult.CONTROL_AE_STATE_CONVERGED ||
                                       result.get(CaptureResult.CONTROL_AE_STATE) ==
                                                  CaptureResult.CONTROL_AE_STATE_FLASH_REQUIRED ||
                                       result.get(CaptureResult.CONTROL_AE_STATE) ==
                                                  CaptureResult.CONTROL_AE_STATE_LOCKED;
                        mLockedAE = result.get(CaptureResult.CONTROL_AE_STATE) ==
                                CaptureResult.CONTROL_AE_STATE_LOCKED;
                        if (!mPrecaptureTriggered) {
                            mPrecaptureTriggered = result.get(CaptureResult.CONTROL_AE_STATE) ==
                                    CaptureResult.CONTROL_AE_STATE_PRECAPTURE;
                        }
                        if (!mConvergeAETriggered) {
                            mConvergeAETriggered = mConvergedAE;
                        }
                    }
                    if (result.get(CaptureResult.CONTROL_AF_STATE) != null) {
                        mConvergedAF = result.get(CaptureResult.CONTROL_AF_STATE) ==
                                                  CaptureResult.CONTROL_AF_STATE_FOCUSED_LOCKED;
                    }
                    if (result.get(CaptureResult.CONTROL_AWB_STATE) != null) {
                        mConvergedAWB = result.get(CaptureResult.CONTROL_AWB_STATE) ==
                                                   CaptureResult.CONTROL_AWB_STATE_CONVERGED ||
                                        result.get(CaptureResult.CONTROL_AWB_STATE) ==
                                                   CaptureResult.CONTROL_AWB_STATE_LOCKED;
                        mLockedAWB = result.get(CaptureResult.CONTROL_AWB_STATE) ==
                                                CaptureResult.CONTROL_AWB_STATE_LOCKED;
                    }

                    if((mConvergedAE || !mDoAE) && mConvergedAWB &&
                            (!mDoAF || isFixedFocusLens || mConvergedAF)){
                        if ((!mNeedsLockedAE || mLockedAE) && !aeResultSent) {
                            aeResultSent = true;
                            if (result.get(CaptureResult.SENSOR_SENSITIVITY) != null
                                    && result.get(CaptureResult.SENSOR_EXPOSURE_TIME) != null) {
                                mSocketRunnableObj.sendResponse("aeResult", String.format("%d %d",
                                        result.get(CaptureResult.SENSOR_SENSITIVITY).intValue(),
                                        result.get(CaptureResult.SENSOR_EXPOSURE_TIME).intValue()
                                        ));
                            } else {
                                Logt.i(TAG, String.format(
                                        "AE converged but NULL exposure values, sensitivity:%b,"
                                        + " expTime:%b",
                                        result.get(CaptureResult.SENSOR_SENSITIVITY) == null,
                                        result.get(CaptureResult.SENSOR_EXPOSURE_TIME) == null));
                            }
                        }
                        if (!afResultSent) {
                            afResultSent = true;
                            if (result.get(CaptureResult.LENS_FOCUS_DISTANCE) != null) {
                                mSocketRunnableObj.sendResponse("afResult", String.format("%f",
                                        result.get(CaptureResult.LENS_FOCUS_DISTANCE)
                                        ));
                            } else {
                                Logt.i(TAG, "AF converged but NULL focus distance values");
                            }
                        }
                        if ((!mNeedsLockedAWB || mLockedAWB) && !awbResultSent) {
                            awbResultSent = true;
                            if (result.get(CaptureResult.COLOR_CORRECTION_GAINS) != null
                                    && result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) != null) {
                                mSocketRunnableObj.sendResponse("awbResult", String.format(
                                        "%f %f %f %f %f %f %f %f %f %f %f %f %f",
                                        result.get(CaptureResult.COLOR_CORRECTION_GAINS).
                                                getRed(),
                                        result.get(CaptureResult.COLOR_CORRECTION_GAINS).
                                                getGreenEven(),
                                        result.get(CaptureResult.COLOR_CORRECTION_GAINS).
                                                getGreenOdd(),
                                        result.get(CaptureResult.COLOR_CORRECTION_GAINS).
                                                getBlue(),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(0,0)),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(1,0)),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(2,0)),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(0,1)),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(1,1)),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(2,1)),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(0,2)),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(1,2)),
                                        r2f(result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM).
                                                getElement(2,2))));
                            } else {
                                Logt.i(TAG, String.format(
                                        "AWB converged but NULL color correction values, gains:%b,"
                                        + " ccm:%b",
                                        result.get(CaptureResult.COLOR_CORRECTION_GAINS) == null,
                                        result.get(CaptureResult.COLOR_CORRECTION_TRANSFORM) ==
                                                null));
                            }
                        }
                    }
                }

                mInterlock3A.open();
            } catch (ItsException e) {
                Logt.e(TAG, "Script error: ", e);
            } catch (Exception e) {
                Logt.e(TAG, "Script error: ", e);
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            Logt.e(TAG, "Script error: capture failed");
        }

        public void stop() {
            stopped = true;
        }
    }

    private final CaptureResultListener mCaptureResultListener = new CaptureResultListener() {
        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                if (request == null || result == null) {
                    throw new ItsException("Request/result is invalid");
                }

                Logt.i(TAG, buildLogString(result));

                int count = mCountCapRes.getAndIncrement();
                mCaptureResults[count] = result;
                mSocketRunnableObj.sendResponseCaptureResult(mCameraCharacteristics,
                        request, result, mOutputImageReaders);
                synchronized(mCountCallbacksRemaining) {
                    mCountCallbacksRemaining.decrementAndGet();
                    mCountCallbacksRemaining.notify();
                }
            } catch (ItsException e) {
                Logt.e(TAG, "Script error: ", e);
            } catch (Exception e) {
                Logt.e(TAG, "Script error: ", e);
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            Logt.e(TAG, "Script error: capture failed");
        }
    };

    private class CaptureCallbackWaiter extends CameraCaptureSession.CaptureCallback {
        private final LinkedBlockingQueue<TotalCaptureResult> mResultQueue =
                new LinkedBlockingQueue<>();

        @Override
        public void onCaptureStarted(CameraCaptureSession session, CaptureRequest request,
                long timestamp, long frameNumber) {
        }

        @Override
        public void onCaptureCompleted(CameraCaptureSession session, CaptureRequest request,
                TotalCaptureResult result) {
            try {
                mResultQueue.put(result);
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onImageAvailable");
            }
        }

        @Override
        public void onCaptureFailed(CameraCaptureSession session, CaptureRequest request,
                CaptureFailure failure) {
            Logt.e(TAG, "Script error: capture failed");
        }

        public TotalCaptureResult getResult(long timeoutMs) throws ItsException {
            TotalCaptureResult result;
            try {
                result = mResultQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new ItsException(e);
            }

            if (result == null) {
                throw new ItsException("Getting an image timed out after " + timeoutMs +
                        "ms");
            }

            return result;
        }
    }

    private static class ImageReaderListenerWaiter implements ImageReader.OnImageAvailableListener {
        private final LinkedBlockingQueue<Image> mImageQueue = new LinkedBlockingQueue<>();

        @Override
        public void onImageAvailable(ImageReader reader) {
            try {
                mImageQueue.put(reader.acquireNextImage());
            } catch (InterruptedException e) {
                throw new UnsupportedOperationException(
                        "Can't handle InterruptedException in onImageAvailable");
            }
        }

        public Image getImage(long timeoutMs) throws ItsException {
            Image image;
            try {
                image = mImageQueue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                throw new ItsException(e);
            }

            if (image == null) {
                throw new ItsException("Getting an image timed out after " + timeoutMs +
                        "ms");
            }
            return image;
        }
    }

    private int getReprocessInputFormat(JSONObject params) throws ItsException {
        String reprocessFormat;
        try {
            reprocessFormat = params.getString("reprocessFormat");
        } catch (org.json.JSONException e) {
            throw new ItsException("Error parsing reprocess format: " + e);
        }

        if (reprocessFormat.equals("yuv")) {
            return ImageFormat.YUV_420_888;
        } else if (reprocessFormat.equals("private")) {
            return ImageFormat.PRIVATE;
        }

        throw new ItsException("Uknown reprocess format: " + reprocessFormat);
    }

    private boolean isFixedFocusLens(CameraCharacteristics c) {
        Float minFocusDistance = c.get(
                CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE);
        return (minFocusDistance != null) && (minFocusDistance == 0.0);
    }
}
