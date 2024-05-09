package com.khadas.ksettings;

import android.content.Context;
import android.os.Handler;
import android.os.Message;
import android.os.SystemProperties;
import android.preference.DialogPreference;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.view.View;

import java.io.IOException;

public class MCUBlueOffStatusSeekBarPreference extends DialogPreference implements OnSeekBarChangeListener{

    private SeekBar seekBar;
    private TextView textView;

    private String value = "0";
	private String val;


    private final int MSG_WHAT_SET_BACKLIGHT  = 0;

    private Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_WHAT_SET_BACKLIGHT:
				SystemProperties.set("persist.sys.mcu_blue_off_bl_value", String.valueOf(msg.arg1));
				val = Integer.toHexString(msg.arg1);

                    try {
						if(msg.arg1>=0 && msg.arg1 <=15){
							ComApi.execCommand(new String[]{"sh", "-c", "echo 0x2A0"+ val +" > /sys/class/mcu/mculed"});
						}else{
							ComApi.execCommand(new String[]{"sh", "-c", "echo 0x2A"+ val +" > /sys/class/mcu/mculed"});
						}
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public MCUBlueOffStatusSeekBarPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
        // TODO Auto-generated constructor stub
    }

    @Override
    protected void onBindDialogView(View view) {
        // TODO Auto-generated method stub
        super.onBindDialogView(view);
        seekBar = (SeekBar) view.findViewById(R.id.seekBar1);
        textView = (TextView) view.findViewById(R.id.textView1);
        seekBar.setOnSeekBarChangeListener(this);

        value = SystemProperties.get("persist.sys.mcu_blue_off_bl_value");
        //Log.d("hlm1","Mipi=" + value);
        if(value.equals("")){
            value = "0";
        }
        textView.setText(value);
        if(Integer.parseInt(value) >= 0 && Integer.parseInt(value) <= 255) {
            seekBar.setProgress(Integer.parseInt(value));
        }
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        // TODO Auto-generated method stub
        if (positiveResult) {
            Log.i("Dialog closed", "You click positive button");
        } else {
            Log.i("Dialog closed", "You click negative button");
        }
    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser) {
        textView.setText("" + progress);
        mHandler.removeMessages(MSG_WHAT_SET_BACKLIGHT);
        Message msg = new Message();
        msg.what = MSG_WHAT_SET_BACKLIGHT;
        msg.arg1 = progress;
        mHandler.sendMessageDelayed(msg,100);
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub

    }

}
