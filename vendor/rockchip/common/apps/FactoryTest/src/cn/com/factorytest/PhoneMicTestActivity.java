package cn.com.factorytest;

import java.util.HashMap;
import android.widget.RelativeLayout;
import java.io.File;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.os.StatFs;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import cn.com.factorytest.helper.ControlButtonUtil;
import cn.com.factorytest.helper.Recorder;
import cn.com.factorytest.helper.VUMeter;

public class PhoneMicTestActivity extends Activity implements OnClickListener{
	private static final String TAG = PhoneMicTestActivity.class
			.getSimpleName();
	
	private final static String ERRMSG = "Record error";
	private final static int RECORD_TIME = 3;
	private static final int MSG_TEST_MIC_ING = 8738;
	private static final int MSG_TEST_MIC_OVER = 13107;
	private static final int MSG_TEST_MIC_START = 4369;
	private boolean isSDcardTestOk = false;
	private AudioManager mAudioManager;
	private Handler mHandler;
	private int mOldVolume;
	private Recorder mRecorder;
	private TextView mResult;
	boolean mSpeakerOn = false;
	private TextView mText;
	int mTimes;
	TextView mTitle;
	private Button mBtnRetest;
	private VUMeter mVUMeter;

	public PhoneMicTestActivity() {
		this.mHandler = new MyHandler();
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {

		super.onCreate(savedInstanceState);

		requestWindowFeature(Window.FEATURE_NO_TITLE);

		getWindow().addFlags(1152);
		setContentView(R.layout.phonemictest);

		mVUMeter = (VUMeter) findViewById(R.id.uvMeter);
		this.mResult = (TextView) findViewById(R.id.phoneresultText);
		this.mResult.setVisibility(View.VISIBLE);
		this.mResult.setGravity(17);
		ControlButtonUtil.initControlButtonView(this);
		mBtnRetest = (Button)findViewById(R.id.btn_retest);
		mBtnRetest.setOnClickListener(this);
		mBtnRetest.setEnabled(false);
		this.mRecorder = new Recorder();
		this.mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
	    mVUMeter.setRecorder(mRecorder);
	}

	@Override
	protected void onResume() {

		super.onResume();

		this.isSDcardTestOk = false;
		if (!Environment.getExternalStorageState().equals(
				Environment.MEDIA_MOUNTED)) {
			this.mResult.setText(R.string.InsertSdCard);
			return;
		}

		if (!isSDcardHasSpace()) {
			this.mResult.setText(R.string.SdCardNospace);
			stopMediaPlayBack();
			return;
		}
		stopMediaPlayBack();
		this.isSDcardTestOk = true;

		this.mOldVolume = this.mAudioManager
				.getStreamVolume(AudioManager.STREAM_MUSIC);
		int maxVolume = this.mAudioManager
				.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
		this.mAudioManager.setStreamVolume(AudioManager.STREAM_MUSIC,
				maxVolume, 0);

		this.mSpeakerOn = mAudioManager.isSpeakerphoneOn();

		if (!this.mSpeakerOn) {
			this.mAudioManager.setSpeakerphoneOn(true);
		}
		this.mHandler.sendEmptyMessage(MSG_TEST_MIC_START);

	}

	@Override
	protected void onPause() {

		super.onPause();
		Tools.writeFile("/sys/class/w25q128fw/buzzer", "0");

		if (this.isSDcardTestOk) {

			switch (this.mRecorder.state()) {

			case Recorder.IDLE_STATE:
				this.mRecorder.delete();
				break;
			case Recorder.PLAYING_STATE:
				this.mRecorder.stop();
				this.mRecorder.delete();
				break;
			case Recorder.RECORDING_STATE:
				this.mRecorder.stop();
				this.mRecorder.clear();
				break;
			}

			
		    mAudioManager.setStreamVolume(3, mOldVolume, 0);
		      
			if (mSpeakerOn) {
				mAudioManager.setSpeakerphoneOn(false);

			}
		}

	}

	public void stopMediaPlayBack() {
		Intent localIntent = new Intent("com.android.music.musicservicecommand");
		localIntent.putExtra("command", "pause");
		sendBroadcast(localIntent);
	}

	public boolean isSDcardHasSpace() {
		File pathFile = android.os.Environment.getExternalStorageDirectory();

		StatFs statfs = new StatFs(pathFile.getPath());

		if (statfs.getAvailableBlocks() > 1) {

			return true;

		}

		return false;

	}

	class MyHandler extends Handler {
		MyHandler() {
		}

		@Override
		public void handleMessage(Message msg) {

			
			switch (msg.what) {
			default:
			case MSG_TEST_MIC_START:
				Tools.writeFile("/sys/class/w25q128fw/buzzer", "1");

				removeMessages(MSG_TEST_MIC_START);
				mTimes = RECORD_TIME;

				mResult.setText("  "+mTimes+" ");
				mRecorder.startRecording(3, ".amr");
				sendEmptyMessageDelayed(MSG_TEST_MIC_ING, 1000L);
				break;
			case MSG_TEST_MIC_ING:

				

				if (mTimes > 0) {

					mResult.setText("  "+mTimes+" ");
					mTimes--;
					Log.i(TAG, "mTimes=" + mTimes);
					sendEmptyMessageDelayed(MSG_TEST_MIC_ING, 1000L);
				} else {
					removeMessages(MSG_TEST_MIC_ING);
					sendEmptyMessage(MSG_TEST_MIC_OVER);					
					Tools.writeFile("/sys/class/w25q128fw/buzzer", "0");
				}

				break;
			case MSG_TEST_MIC_OVER:
				removeMessages(MSG_TEST_MIC_OVER);
				mRecorder.stopRecording();
				if (mRecorder.sampleLength() > 0) {
					mResult.setText(R.string.HeadsetRecodrSuccess);
					mRecorder.startPlayback();
				} else {
					mResult.setText(R.string.RecordError);
				}
				mBtnRetest.setEnabled(true);
				break;
			}
			
			mVUMeter.invalidate();
		}

	}

    public void onClick(View v) {
        switch (this.mRecorder.state()) {

            case Recorder.IDLE_STATE:
                this.mRecorder.delete();
                break;
            case Recorder.PLAYING_STATE:
                this.mRecorder.stop();
                this.mRecorder.delete();
                break;
        }
        mRecorder.stopPlayback();
        mBtnRetest.setEnabled(false);
        this.mHandler.sendEmptyMessage(MSG_TEST_MIC_START);
        
    }
	public boolean dispatchKeyEvent(KeyEvent event) {
		if (event.getKeyCode() == KeyEvent.KEYCODE_BACK) {
			return false;
		}
		return super.dispatchKeyEvent(event);
	}
}
