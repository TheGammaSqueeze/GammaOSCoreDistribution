package com.rockchip.wfd;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class WifiDisplayBootReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		if(Intent.ACTION_BOOT_COMPLETED.equals(intent.getAction())){
			Intent serviceIntent = new Intent();
			serviceIntent.setClass(context, WifiDisplayService.class);
			context.startService(serviceIntent);
		}
	}

}
