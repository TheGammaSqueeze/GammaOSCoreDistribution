package cn.com.factorytest.helper;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;

//import cn.com.factorytest.factorytest;

import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;

public class Recorder implements MediaPlayer.OnCompletionListener,
		MediaPlayer.OnErrorListener {
	public static final int IDLE_STATE = 0;
	public static final int INTERNAL_ERROR = 2;
	public static final int NO_ERROR = 0;
	public static final int PLAYING_STATE = 2;
	public static final int RECORDING_STATE = 1;
	static final String SAMPLE_LENGTH_KEY = "sample_length";
	static final String SAMPLE_PATH_KEY = "sample_path";
	static final String SAMPLE_PREFIX = "recording";
	private static final String TEST_FILE_PATH = "/data/data/cn.com.factorytest/" + "test";
	public static final int SDCARD_ACCESS_ERROR = 1;
	OnStateChangedListener mOnStateChangedListener = null;
	MediaPlayer mPlayer = null;
	MediaRecorder mRecorder = null;
	File mSampleFile = null;
	int mSampleLength;
	long mSampleStart = 0L;
	int mState;

	private void setError(int paramInt) {
		if (this.mOnStateChangedListener == null) {
			return;
		}

		this.mOnStateChangedListener.onError(paramInt);
	}

	private void setState(int state) {
		if (this.state() == state) {
			return;

		}

		this.mState = state;

		signalStateChanged(this.mState);

	}

	private void signalStateChanged(int paramInt) {
		if (this.mOnStateChangedListener == null)
			return;
		this.mOnStateChangedListener.onStateChanged(paramInt);
	}

	public void clear() {
		stop();
		this.mSampleLength = 0;
		signalStateChanged(0);
	}

	public void delete() {
		int i = 0;
		stop();
		if (this.mSampleFile != null)
			this.mSampleFile.delete();
		this.mSampleFile = null;
		this.mSampleLength = i;
		signalStateChanged(i);
	}


	  public int getMaxAmplitude()
	  {
	 		if (this.mState == Recorder.RECORDING_STATE) {
	 			return this.mRecorder.getMaxAmplitude();
	 		}
	 		return 0;
	 	}

	public void onCompletion(MediaPlayer paramMediaPlayer) {
		stop();
	}

	public boolean onError(MediaPlayer paramMediaPlayer, int paramInt1,
			int paramInt2) {
		stop();
		setError(1);
		return true;
	}

	public int progress() {
		if (this.state() != Recorder.PLAYING_STATE) {
			return 0;
		}
		long currentTime = System.currentTimeMillis();

		return (int) ((currentTime - this.mSampleStart) / 1000L);

	}

	public File sampleFile() {
		return this.mSampleFile;
	}

	public int sampleLength() {
		return this.mSampleLength;
	}

	public void setOnStateChangedListener(
			OnStateChangedListener paramOnStateChangedListener) {
		this.mOnStateChangedListener = paramOnStateChangedListener;
	}

	public void startPlayback() {

		stop();

		this.mPlayer = new MediaPlayer();
		try {
			FileInputStream fis = new FileInputStream(mSampleFile);
			this.mPlayer.setDataSource(fis.getFD());
			this.mPlayer.setOnCompletionListener(this);
			this.mPlayer.setOnErrorListener(this);
			this.mPlayer.prepare();
			this.mPlayer.start();

			this.mSampleStart = System.currentTimeMillis();
			setState(Recorder.PLAYING_STATE);

		} catch (Exception e) {
			setError(Recorder.PLAYING_STATE);
			setState(Recorder.IDLE_STATE);

		}
	}

	public void startRecording(int paramInt, String paramString) {

		stop();
		try {
			Log.i("Jeffy","Create new file:" + TEST_FILE_PATH);
			this.mSampleFile = new File(TEST_FILE_PATH);
			this.mSampleFile.createNewFile();
			Log.i("Jeffy","new file created,now create recorder");
			this.mRecorder = new MediaRecorder();
			this.mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
			this.mRecorder.setOutputFormat(paramInt);
			this.mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);

			String str = this.mSampleFile.getAbsolutePath();
			mRecorder.setOutputFile(str);
			Log.i("Jeffy","start to record");
			
			this.mRecorder.prepare();
			this.mRecorder.start();

			this.mSampleStart = System.currentTimeMillis();
			setState(Recorder.RECORDING_STATE);
		} catch (Exception e) {
			e.printStackTrace();
			setError(Recorder.IDLE_STATE);
			if(mRecorder != null) {
				this.mRecorder.reset();
				this.mRecorder.release();
				this.mRecorder = null;
			}

		}

	}

	public int state() {
		return this.mState;
	}

	public void stop() {
		stopRecording();
		stopPlayback();
	}

	public void stopPlayback() {
		if (this.mPlayer == null) {
			return;
		}
		this.mPlayer.stop();
		this.mPlayer.release();
		this.mPlayer = null;
		setState(Recorder.IDLE_STATE);

	}

	public void stopRecording() {
		if (this.mRecorder == null) {
			return;
		}
		this.mRecorder.stop();
		this.mRecorder.release();
		this.mRecorder = null;

		this.mSampleLength = (int) ((System.currentTimeMillis() - mSampleStart) / 1000L);
		setState(Recorder.IDLE_STATE);
	}
}

abstract interface OnStateChangedListener {
	public abstract void onError(int paramInt);

	public abstract void onStateChanged(int paramInt);
}
