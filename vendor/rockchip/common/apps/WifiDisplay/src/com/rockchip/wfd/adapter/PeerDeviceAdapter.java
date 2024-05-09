package com.rockchip.wfd.adapter;

import java.util.List;

import com.rockchip.wfd.R;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pDevice;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class PeerDeviceAdapter extends ArrayAdapter<WifiP2pDevice> {

	private LayoutInflater mInflater;
	private Context mContext;
	
	public PeerDeviceAdapter(Context context, List<WifiP2pDevice> objects) {
		super(context, 0, objects);
		mContext = context;
		mInflater = LayoutInflater.from(context);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		DeviceHolder deviceHolder;
		if(convertView==null){
			deviceHolder = new DeviceHolder();
			convertView = mInflater.inflate(R.layout.wfd_device_item, null);
			deviceHolder.stateText = (TextView)convertView.findViewById(R.id.wfd_txt_device_state);
			deviceHolder.nameText= (TextView)convertView.findViewById(R.id.wfd_txt_device_name);
			convertView.setTag(deviceHolder);
		}else{
			deviceHolder = (DeviceHolder)convertView.getTag();
		}
		WifiP2pDevice device = getItem(position);
		String[] statusArray = mContext.getResources().getStringArray(R.array.wifi_p2p_status);
		deviceHolder.stateText.setText(statusArray[device.status]);
		if (TextUtils.isEmpty(device.deviceName)) {
			deviceHolder.nameText.setText(device.deviceAddress);
        } else {
        	deviceHolder.nameText.setText(device.deviceName);
        }
		return convertView;
	}
	
	static class DeviceHolder {
		TextView stateText;
		TextView nameText;
	}

}
