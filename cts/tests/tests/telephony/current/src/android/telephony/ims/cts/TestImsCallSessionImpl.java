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

package android.telephony.ims.cts;

import static org.junit.Assert.fail;

import android.os.Bundle;
import android.os.DeadObjectException;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.telephony.ims.ImsCallProfile;
import android.telephony.ims.ImsCallSessionListener;
import android.telephony.ims.ImsConferenceState;
import android.telephony.ims.ImsReasonInfo;
import android.telephony.ims.ImsStreamMediaProfile;
import android.telephony.ims.stub.ImsCallSessionImplBase;
import android.util.Log;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

public class TestImsCallSessionImpl extends ImsCallSessionImplBase {

    private static final String LOG_TAG = "CtsTestImsCallSessionImpl";
    private static final int LATCH_WAIT = 0;
    private static final int LATCH_MAX = 1;
    private static final int WAIT_FOR_STATE_CHANGE = 1500;
    private static final int WAIT_FOR_ESTABLISHING = 2000;

    private final String mCallId = String.valueOf(this.hashCode());
    private final Object mLock = new Object();

    private int mState = ImsCallSessionImplBase.State.IDLE;
    private ImsCallProfile mCallProfile;
    private ImsCallProfile mLocalCallProfile;
    private ImsCallSessionListener mListener;

    private final MessageExecutor mCallExecutor = new MessageExecutor("CallExecutor");
    private final MessageExecutor mCallBackExecutor = new MessageExecutor("CallBackExecutor");
    private static final CountDownLatch[] sLatches = new CountDownLatch[LATCH_MAX];
    static {
        for (int i = 0; i < LATCH_MAX; i++) {
            sLatches[i] = new CountDownLatch(1);
        }
    }

    public static final int TEST_TYPE_NONE = 0;
    public static final int TEST_TYPE_MO_ANSWER = 1 << 0;
    public static final int TEST_TYPE_MO_FAILED = 1 << 1;
    public static final int TEST_TYPE_HOLD_FAILED = 1 << 2;
    public static final int TEST_TYPE_RESUME_FAILED = 1 << 3;
    public static final int TEST_TYPE_CONFERENCE_FAILED = 1 << 4;
    public static final int TEST_TYPE_HOLD_NO_RESPONSE = 1 << 5;

    private int mTestType = TEST_TYPE_NONE;
    private boolean mIsOnHold = false;

    private TestImsCallSessionImpl mConfSession = null;
    private ImsCallProfile mConfCallProfile = null;
    private ConferenceHelper mConferenceHelper = null;
    private String mCallee = null;

    public boolean imsCallSessionLatchCountdown(int latchIndex, int waitMs) {
        boolean complete = false;
        try {
            CountDownLatch latch;
            synchronized (mLock) {
                latch = sLatches[latchIndex];
            }
            complete = latch.await(waitMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
             //complete == false
        }
        synchronized (mLock) {
            sLatches[latchIndex] = new CountDownLatch(1);
        }
        return complete;
    }

    public void countDownLatch(int latchIndex) {
        synchronized (mLock) {
            sLatches[latchIndex].countDown();
        }
    }

    public TestImsCallSessionImpl(ImsCallProfile profile) {
        mCallProfile = profile;
    }

    @Override
    public String getCallId() {
        return mCallId;
    }

    @Override
    public ImsCallProfile getCallProfile() {
        return mCallProfile;
    }

    @Override
    public ImsCallProfile getLocalCallProfile() {
        return mLocalCallProfile;
    }

    @Override
    public int getState() {
        return mState;
    }

    @Override
    public boolean isInCall() {
        return (mState == ImsCallSessionImplBase.State.ESTABLISHED) ? true : false;
    }

    @Override
    public void setListener(ImsCallSessionListener listener) {
        mListener = listener;
    }

    @Override
    public boolean isMultiparty() {
        boolean isMultiparty = (mCallProfile != null)
                ? mCallProfile.getCallExtraBoolean(ImsCallProfile.EXTRA_CONFERENCE) : false;
        return isMultiparty;
    }

    @Override
    public void start(String callee, ImsCallProfile profile) {
        mCallee = callee;
        mLocalCallProfile = profile;
        int state = getState();

        if ((state != ImsCallSessionImplBase.State.IDLE)
                && (state != ImsCallSessionImplBase.State.INITIATED)) {
            Log.d(LOG_TAG, "start :: Illegal state; callId = " + getCallId()
                    + ", state=" + getState());
        }

        mCallExecutor.execute(() -> {
            imsCallSessionLatchCountdown(LATCH_WAIT, 500);
            if (isTestType(TEST_TYPE_MO_FAILED)) {
                startFailed();
            } else {
                startInternal();
            }
        });
    }

    void startInternal() {
        imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_STATE_CHANGE);
        postAndRunTask(() -> {
            try {
                if (mListener == null) {
                    return;
                }
                Log.d(LOG_TAG, "invokeInitiating mCallId = " + mCallId);
                mListener.callSessionInitiating(mCallProfile);
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
        setState(ImsCallSessionImplBase.State.INITIATED);

        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);

        ImsCallProfile profile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.CALL_TYPE_VOICE, new Bundle(), mediaProfile);
        mCallProfile.updateMediaProfile(profile);

        imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_STATE_CHANGE);
        postAndRunTask(() -> {
            try {
                if (mListener == null) {
                    return;
                }
                Log.d(LOG_TAG, "invokeProgressing mCallId = " + mCallId);
                mListener.callSessionProgressing(mCallProfile.getMediaProfile());
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
        setState(ImsCallSessionImplBase.State.ESTABLISHING);

        imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_ESTABLISHING);
        postAndRunTask(() -> {
            try {
                if (mListener == null) {
                    return;
                }
                Log.d(LOG_TAG, "invokeStarted mCallId = " + mCallId);
                mListener.callSessionInitiated(mCallProfile);
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
        setState(ImsCallSessionImplBase.State.ESTABLISHED);
    }

    void startFailed() {
        imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_STATE_CHANGE);
        postAndRunTask(() -> {
            try {
                if (mListener == null) {
                    return;
                }
                Log.d(LOG_TAG, "invokestartFailed mCallId = " + mCallId);
                mListener.callSessionInitiatingFailed(getReasonInfo(
                        ImsReasonInfo.CODE_LOCAL_INTERNAL_ERROR, ImsReasonInfo.CODE_UNSPECIFIED));
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
        setState(ImsCallSessionImplBase.State.TERMINATED);
    }

    @Override
    public void accept(int callType, ImsStreamMediaProfile profile) {
        Log.i(LOG_TAG, "Accept Call");
        postAndRunTask(() -> {
            try {
                if (mListener == null) {
                    return;
                }
                Log.d(LOG_TAG, "invokeStarted mCallId = " + mCallId);
                mListener.callSessionInitiated(mCallProfile);
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
        setState(ImsCallSessionImplBase.State.ESTABLISHED);
    }

    @Override
    public void reject(int reason) {
        int state = getState();
        if (state == ImsCallSessionImplBase.State.ESTABLISHED) {
            postAndRunTask(() -> {
                try {
                    if (mListener == null) {
                        return;
                    }
                    Log.d(LOG_TAG, "invokeTerminated mCallId = " + mCallId);
                    mListener.callSessionTerminated(getReasonInfo(
                            ImsReasonInfo.CODE_USER_TERMINATED, ImsReasonInfo.CODE_UNSPECIFIED));
                } catch (Throwable t) {
                    Throwable cause = t.getCause();
                    if (t instanceof DeadObjectException
                            || (cause != null && cause instanceof DeadObjectException)) {
                        fail("starting cause Throwable to be thrown: " + t);
                    }
                }
            });
            setState(ImsCallSessionImplBase.State.TERMINATED);
        }
    }

    @Override
    public void terminate(int reason) {
        int state = getState();
        postAndRunTask(() -> {
            try {
                if (mListener == null) {
                    return;
                }
                Log.d(LOG_TAG, "invokeTerminated mCallId = " + mCallId);
                mListener.callSessionTerminated(getReasonInfo(ImsReasonInfo.CODE_USER_TERMINATED,
                        ImsReasonInfo.CODE_UNSPECIFIED));
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
        setState(ImsCallSessionImplBase.State.TERMINATED);
    }

    // End the Incoming Call
    public void terminateIncomingCall() {
        postAndRunTask(() -> {
            try {
                if (mListener == null) {
                    return;
                }
                Log.d(LOG_TAG, "invokeTerminated mCallId = " + mCallId);
                mListener.callSessionTerminated(getReasonInfo(
                        ImsReasonInfo.CODE_USER_TERMINATED_BY_REMOTE,
                        ImsReasonInfo.CODE_UNSPECIFIED));
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
        setState(ImsCallSessionImplBase.State.TERMINATED);
    }

    @Override
    public void hold(ImsStreamMediaProfile profile) {
        if (isTestType(TEST_TYPE_HOLD_FAILED)) {
            holdFailed(profile);
        } else {
            int audioDirection = profile.getAudioDirection();
            if (audioDirection == ImsStreamMediaProfile.DIRECTION_SEND) {
                ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                        ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                        ImsStreamMediaProfile.DIRECTION_RECEIVE,
                        ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                        ImsStreamMediaProfile.DIRECTION_INVALID,
                        ImsStreamMediaProfile.RTT_MODE_DISABLED);
                ImsCallProfile mprofile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                        ImsCallProfile.CALL_TYPE_VOICE, new Bundle(), mediaProfile);
                mCallProfile.updateMediaProfile(mprofile);
            }
            setState(ImsCallSessionImplBase.State.RENEGOTIATING);

            if (!isTestType(TEST_TYPE_HOLD_NO_RESPONSE)) sendHoldResponse();
        }
    }

    @Override
    public void resume(ImsStreamMediaProfile profile) {
        if (isTestType(TEST_TYPE_RESUME_FAILED)) {
            resumeFailed(profile);
        } else {
            int audioDirection = profile.getAudioDirection();
            if (audioDirection == ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE) {
                ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                        ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                        ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                        ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                        ImsStreamMediaProfile.DIRECTION_INVALID,
                        ImsStreamMediaProfile.RTT_MODE_DISABLED);
                ImsCallProfile mprofile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                        ImsCallProfile.CALL_TYPE_VOICE, new Bundle(), mediaProfile);
                mCallProfile.updateMediaProfile(mprofile);
            }
            setState(ImsCallSessionImplBase.State.RENEGOTIATING);

            postAndRunTask(() -> {
                imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_ESTABLISHING);
                try {
                    if (mListener == null) {
                        return;
                    }
                    Log.d(LOG_TAG, "invokeResume mCallId = " + mCallId);
                    mListener.callSessionResumed(mCallProfile);
                    mIsOnHold = false;
                } catch (Throwable t) {
                    Throwable cause = t.getCause();
                    if (t instanceof DeadObjectException
                            || (cause != null && cause instanceof DeadObjectException)) {
                        fail("starting cause Throwable to be thrown: " + t);
                    }
                }
            });
            setState(ImsCallSessionImplBase.State.ESTABLISHED);
        }
    }

    private void holdFailed(ImsStreamMediaProfile profile) {
        int audioDirection = profile.getAudioDirection();
        if (audioDirection == ImsStreamMediaProfile.DIRECTION_SEND) {
            postAndRunTask(() -> {
                imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_ESTABLISHING);
                try {
                    if (mListener == null) {
                        return;
                    }
                    Log.d(LOG_TAG, "invokeHoldFailed mCallId = " + mCallId);
                    mListener.callSessionHoldFailed(getReasonInfo(ImsReasonInfo
                            .CODE_SESSION_MODIFICATION_FAILED, ImsReasonInfo.CODE_UNSPECIFIED));
                } catch (Throwable t) {
                    Throwable cause = t.getCause();
                    if (t instanceof DeadObjectException
                            || (cause != null && cause instanceof DeadObjectException)) {
                        fail("starting cause Throwable to be thrown: " + t);
                    }
                }
            });
            setState(ImsCallSessionImplBase.State.ESTABLISHED);
        }
    }

    private void resumeFailed(ImsStreamMediaProfile profile) {
        int audioDirection = profile.getAudioDirection();
        if (audioDirection == ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE) {
            postAndRunTask(() -> {
                imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_ESTABLISHING);
                try {
                    if (mListener == null) {
                        return;
                    }
                    Log.d(LOG_TAG, "invokeResumeFailed mCallId = " + mCallId);
                    mListener.callSessionResumeFailed(getReasonInfo(ImsReasonInfo
                            .CODE_SESSION_MODIFICATION_FAILED, ImsReasonInfo.CODE_UNSPECIFIED));
                } catch (Throwable t) {
                    Throwable cause = t.getCause();
                    if (t instanceof DeadObjectException
                            || (cause != null && cause instanceof DeadObjectException)) {
                        fail("starting cause Throwable to be thrown: " + t);
                    }
                }
            });
            setState(ImsCallSessionImplBase.State.ESTABLISHED);
        }
    }

    @Override
    public void merge() {
        if (isTestType(TEST_TYPE_CONFERENCE_FAILED)) {
            mergeFailed();
        } else {
            createConferenceSession();
            imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_ESTABLISHING);
            mConfSession.setState(ImsCallSessionImplBase.State.ESTABLISHED);

            postAndRunTask(() -> {
                try {
                    if (mListener == null) {
                        return;
                    }
                    mConferenceHelper.getBackGroundSession().invokeSessionTerminated();
                    Log.d(LOG_TAG, "invokeCallSessionMergeComplete");
                    mListener.callSessionMergeComplete(mConfSession);
                } catch (Throwable t) {
                    Throwable cause = t.getCause();
                    if (t instanceof DeadObjectException
                            || (cause != null && cause instanceof DeadObjectException)) {
                        fail("starting cause Throwable to be thrown: " + t);
                    }
                }
            });

            postAndRunTask(() -> {
                try {
                    if (mListener == null) {
                        return;
                    }
                    mConfSession.sendConferenceStateUpdatd("connected");
                } catch (Throwable t) {
                    Throwable cause = t.getCause();
                    if (t instanceof DeadObjectException
                            || (cause != null && cause instanceof DeadObjectException)) {
                        fail("starting cause Throwable to be thrown: " + t);
                    }
                }
            });
        }
    }

    private void mergeFailed() {
        createConferenceSession();
        imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_ESTABLISHING);

        postAndRunTask(() -> {
            try {
                if (mListener == null) {
                    return;
                }

                Log.d(LOG_TAG, "invokeCallSessionMergeStarted");
                mListener.callSessionMergeStarted(mConfSession, mConfCallProfile);
                imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_STATE_CHANGE);
                Log.d(LOG_TAG, "invokeCallSessionMergeFailed");
                mListener.callSessionMergeFailed(getReasonInfo(
                        ImsReasonInfo.CODE_REJECT_ONGOING_CONFERENCE_CALL,
                        ImsReasonInfo.CODE_UNSPECIFIED));
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
    }

    private void createConferenceSession() {
        mConferenceHelper.setForeGroundSession(this);
        mConferenceHelper.setBackGroundSession(mConferenceHelper.getHoldSession());

        ImsStreamMediaProfile mediaProfile = new ImsStreamMediaProfile(
                ImsStreamMediaProfile.AUDIO_QUALITY_AMR,
                ImsStreamMediaProfile.DIRECTION_SEND_RECEIVE,
                ImsStreamMediaProfile.VIDEO_QUALITY_NONE,
                ImsStreamMediaProfile.DIRECTION_INVALID,
                ImsStreamMediaProfile.RTT_MODE_DISABLED);

        mConfCallProfile = new ImsCallProfile(ImsCallProfile.SERVICE_TYPE_NORMAL,
                ImsCallProfile.CALL_TYPE_VOICE, new Bundle(), mediaProfile);
        mConfCallProfile.setCallExtraBoolean(ImsCallProfile.EXTRA_CONFERENCE, true);

        mConfSession = new TestImsCallSessionImpl(mConfCallProfile);
        mConfSession.setConferenceHelper(mConferenceHelper);
        mConferenceHelper.addSession(mConfSession);
        mConferenceHelper.setConferenceSession(mConfSession);
    }

    private void invokeSessionTerminated() {
        Log.d(LOG_TAG, "invokeCallSessionTerminated");
        mListener.callSessionTerminated(getReasonInfo(
                ImsReasonInfo.CODE_LOCAL_ENDED_BY_CONFERENCE_MERGE,
                ImsReasonInfo.CODE_UNSPECIFIED));
    }

    public void sendConferenceStateUpdatd(String state) {
        ImsConferenceState confState = new ImsConferenceState();
        int counter = 5553639;
        for (int i = 0; i < 2; ++i) {
            confState.mParticipants.put((String.valueOf(++counter)),
                    createConferenceParticipant(("tel:" + String.valueOf(++counter)),
                    ("tel:" + String.valueOf(++counter)), (String.valueOf(++counter)), state, 200));
        }

        imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_STATE_CHANGE);
        Log.d(LOG_TAG, "invokeCallSessionConferenceStateUpdated");
        mListener.callSessionConferenceStateUpdated(confState);
    }

    /**
     * Send a hold response for this listener.
     */
    public void sendHoldResponse() {
        postAndRunTask(() -> {
            imsCallSessionLatchCountdown(LATCH_WAIT, WAIT_FOR_ESTABLISHING);
            try {
                if (mListener == null) {
                    return;
                }
                Log.d(LOG_TAG, "invokeHeld mCallId = " + mCallId);
                mListener.callSessionHeld(mCallProfile);
                mIsOnHold = true;
            } catch (Throwable t) {
                Throwable cause = t.getCause();
                if (t instanceof DeadObjectException
                        || (cause != null && cause instanceof DeadObjectException)) {
                    fail("starting cause Throwable to be thrown: " + t);
                }
            }
        });
        setState(ImsCallSessionImplBase.State.ESTABLISHED);
    }

    public Bundle createConferenceParticipant(String user, String endpoint,
            String displayText, String status, int sipStatusCode) {
        Bundle participant = new Bundle();

        participant.putString(ImsConferenceState.STATUS, status);
        participant.putString(ImsConferenceState.USER, user);
        participant.putString(ImsConferenceState.ENDPOINT, endpoint);
        participant.putString(ImsConferenceState.DISPLAY_TEXT, displayText);
        participant.putInt(ImsConferenceState.SIP_STATUS_CODE, sipStatusCode);
        return participant;
    }

    public void setConferenceHelper(ConferenceHelper confHelper) {
        mConferenceHelper = confHelper;
    }

    public boolean isSessionOnHold() {
        return mIsOnHold;
    }

    private void setState(int state) {
        if (mState != state) {
            Log.d(LOG_TAG, "ImsCallSession :: " + mState + " >> " + state);
            mState = state;
        }
    }

    public boolean isInTerminated() {
        return (mState == ImsCallSessionImplBase.State.TERMINATED) ? true : false;
    }

    private ImsReasonInfo getReasonInfo(int code, int extraCode) {
        ImsReasonInfo reasonInfo = new ImsReasonInfo(code, extraCode, "");
        return reasonInfo;
    }

    public void addTestType(int type) {
        mTestType |= type;
    }

    public void removeTestType(int type) {
        mTestType &= ~type;
    }

    public boolean isTestType(int type) {
        return  ((mTestType & type) == type);
    }

    public Executor getExecutor() {
        return mCallBackExecutor;
    }

    private void postAndRunTask(Runnable task) {
        mCallBackExecutor.execute(task);
    }

    private static Looper createLooper(String name) {
        HandlerThread thread = new HandlerThread(name);
        thread.start();

        Looper looper = thread.getLooper();

        if (looper == null) {
            return Looper.getMainLooper();
        }
        return looper;
    }
     /**
     * Executes the tasks in the other thread rather than the calling thread.
     */
    public class MessageExecutor extends Handler implements Executor {
        public MessageExecutor(String name) {
            super(createLooper(name));
        }

        @Override
        public void execute(Runnable r) {
            Message m = Message.obtain(this, 0 /* don't care */, r);
            m.sendToTarget();
        }

        @Override
        public void handleMessage(Message msg) {
            if (msg.obj instanceof Runnable) {
                executeInternal((Runnable) msg.obj);
            } else {
                Log.d(LOG_TAG, "[MessageExecutor] handleMessage :: "
                        + "Not runnable object; ignore the msg=" + msg);
            }
        }

        private void executeInternal(Runnable r) {
            try {
                r.run();
            } catch (Throwable t) {
                Log.d(LOG_TAG, "[MessageExecutor] executeInternal :: run task=" + r);
                t.printStackTrace();
            }
        }
    }
}
