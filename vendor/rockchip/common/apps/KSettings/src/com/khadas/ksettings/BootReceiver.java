package com.khadas.ksettings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.SystemProperties;
import android.provider.Settings;
import android.util.Log;

import java.io.IOException;

public class BootReceiver extends BroadcastReceiver {
	private static final String TAG = "KSettingsBootReceiver";
	private String value;
	private int val ;

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		String action = intent.getAction();
		Log.e(TAG, "hlm5 action " + action);

		if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {
			Log.e(TAG, "hlm5 start Kedge2ToolsService");
			//context.startService(new Intent(context, Kedge2ToolsService.class));
			Settings.System.putInt(context.getContentResolver(),"user_rotation", SystemProperties.getInt("persist.sys.user_rotation",0));
			fan_control();
			led_control();
		}
	}

	private void fan_control() {
		switch (SystemProperties.getInt("persist.sys.fan_control", 1)){
			case 0:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo 0 > /sys/class/fan/enable"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 1:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo 1 > /sys/class/fan/enable"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 1 > /sys/class/fan/mode"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 2:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo 1 > /sys/class/fan/enable"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 0 > /sys/class/fan/mode"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 1 > /sys/class/fan/level"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 3:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo 1 > /sys/class/fan/enable"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 0 > /sys/class/fan/mode"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 2 > /sys/class/fan/level"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 4:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo 1 > /sys/class/fan/enable"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 0 > /sys/class/fan/mode"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 3 > /sys/class/fan/level"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 5:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo 1 > /sys/class/fan/enable"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 0 > /sys/class/fan/mode"});
					ComApi.execCommand(new String[]{"sh", "-c", "echo 4 > /sys/class/fan/level"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
		}
	}

	private void led_control() {
		value = SystemProperties.get("persist.sys.mcu_leds_on_modes_value");
		if (value.equals("")) {
			value = "0";
		}
		Log.e(TAG, "hlm mcu_leds_on_modes_value val " + val);
		try {
			ComApi.execCommand(new String[]{"sh", "-c", "echo 0x230" + value + " > /sys/class/mcu/mculed"});
		} catch (IOException e) {
			e.printStackTrace();
		}

		value = SystemProperties.get("persist.sys.mcu_red_on_bl_value");
		if (value.equals("")) {
			value = "255";
		}
		val = Integer.valueOf(value, 10);
		Log.e(TAG, "hlm mcu_red_on_bl_value val " + val);
		try {
			if (val >= 0 && val <= 15) {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x250" + val + " > /sys/class/mcu/mculed"});
			} else {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x25" + val + " > /sys/class/mcu/mculed"});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		value = SystemProperties.get("persist.sys.mcu_green_on_bl_value");
		if (value.equals("")) {
			value = "255";
		}
		val = Integer.valueOf(value, 10);
		try {
			if (val >= 0 && val <= 15) {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x260" + val + " > /sys/class/mcu/mculed"});
			} else {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x26" + val + " > /sys/class/mcu/mculed"});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		value = SystemProperties.get("persist.sys.mcu_blue_on_bl_value");
		if (value.equals("")) {
			value = "255";
		}
		val = Integer.valueOf(value, 10);
		try {
			if (val >= 0 && val <= 15) {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x270" + val + " > /sys/class/mcu/mculed"});
			} else {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x27" + val + " > /sys/class/mcu/mculed"});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		//System Shutdown muc led status
		value = SystemProperties.get("persist.sys.mcu_leds_off_modes_value");
		if (value.equals("")) {
			value = "1";
		}
		try {
			ComApi.execCommand(new String[]{"sh", "-c", "echo 0x240" + value + " > /sys/class/mcu/mculed"});
		} catch (IOException e) {
			e.printStackTrace();
		}
		value = SystemProperties.get("persist.sys.mcu_red_off_bl_value");
		if (value.equals("")) {
			value = "255";
		}
		val = Integer.valueOf(value, 10);
		try {
			if (val >= 0 && val <= 15) {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x280" + val + " > /sys/class/mcu/mculed"});
			} else {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x28" + val + " > /sys/class/mcu/mculed"});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		value = SystemProperties.get("persist.sys.mcu_green_off_bl_value");
		if (value.equals("")) {
			value = "0";
		}
		val = Integer.valueOf(value, 10);
		try {
			if (val >= 0 && val <= 15) {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x290" + val + " > /sys/class/mcu/mculed"});
			} else {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x29" + val + " > /sys/class/mcu/mculed"});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		value = SystemProperties.get("persist.sys.mcu_blue_off_bl_value");
		if (value.equals("")) {
			value = "0";
		}
		val = Integer.valueOf(value, 10);
		try {
			if (val >= 0 && val <= 15) {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x2A0" + val + " > /sys/class/mcu/mculed"});
			} else {
				ComApi.execCommand(new String[]{"sh", "-c", "echo 0x2A" + val + " > /sys/class/mcu/mculed"});
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
		//sys led
		value = SystemProperties.get("persist.sys.Red_led_control");
		if (value.equals("")) {
			value = "3";
		}
		val = Integer.valueOf(value, 16);
		switch (val) {
			case 0:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo off > /sys/class/leds/red_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 1:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo default-on > /sys/class/leds/red_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 2:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo timer > /sys/class/leds/red_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 3:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo heartbeat > /sys/class/leds/red_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
		}

		value = SystemProperties.get("persist.sys.Green_led_control");
		if (value.equals("")) {
			value = "3";
		}
		val = Integer.valueOf(value, 16);
		switch (val) {
			case 0:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo off > /sys/class/leds/green_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 1:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo default-on > /sys/class/leds/green_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 2:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo timer > /sys/class/leds/green_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 3:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo heartbeat > /sys/class/leds/green_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
		}

		value = SystemProperties.get("persist.sys.Blue_led_control");
		if (value.equals("")) {
			value = "3";
		}
		val = Integer.valueOf(value, 16);
		Log.e(TAG, "hlm persist.sys.Blue_led_control val " + val);
		switch (val) {
			case 0:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo off > /sys/class/leds/blue_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 1:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo default-on > /sys/class/leds/blue_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 2:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo timer > /sys/class/leds/blue_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
			case 3:
				try {
					ComApi.execCommand(new String[]{"sh", "-c", "echo heartbeat > /sys/class/leds/blue_led/trigger"});
				} catch (IOException e) {
					e.printStackTrace();
				}
				break;
		}
	}
}
