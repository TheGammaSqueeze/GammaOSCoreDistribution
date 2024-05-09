package com.rockchip.wfd.adapter;

import java.util.List;

import com.rockchip.wfd.R;

import android.content.Context;
import android.net.wifi.p2p.WifiP2pGroup;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;

public class PeerGroupAdapter extends ArrayAdapter<WifiP2pGroup> {

	private LayoutInflater mInflater;
	
	public PeerGroupAdapter(Context context, List<WifiP2pGroup> objects) {
		super(context, 0, objects);
		mInflater = LayoutInflater.from(context);
	}
	
	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
		DeviceHolder deviceHolder;
		if(convertView==null){
			deviceHolder = new DeviceHolder();
			convertView = mInflater.inflate(R.layout.wfd_group_item, null);
			deviceHolder.nameText= (TextView)convertView.findViewById(R.id.wfd_txt_group_name);
			convertView.setTag(deviceHolder);
		}else{
			deviceHolder = (DeviceHolder)convertView.getTag();
		}
		WifiP2pGroup group = getItem(position);
		deviceHolder.nameText.setText(group.getNetworkName());
		return convertView;
	}
	
	static class DeviceHolder {
		TextView nameText;
	}

}
