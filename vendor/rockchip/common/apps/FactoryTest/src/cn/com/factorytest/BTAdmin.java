package cn.com.factorytest;

import java.util.List;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BTAdmin {

	private final String TAG = "BTAdmin";
	private Context mContext;
	private BluetoothAdapter mBluetoothAdapter;
    
	public BTAdmin(){
     mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
	}

	public void OpenBT(){

		if(mBluetoothAdapter!=null){
			if (!mBluetoothAdapter.isEnabled()) {
				mBluetoothAdapter.enable();
			}
		}
	}

	public void CloseBT(){

		if(mBluetoothAdapter!=null){
			if (mBluetoothAdapter.isEnabled()) {
				mBluetoothAdapter.disable();
			 }
		}
	}

	 public boolean ScanBT(){

		  if(mBluetoothAdapter!=null){
			  if(mBluetoothAdapter.isDiscovering()){
				  mBluetoothAdapter.cancelDiscovery();
				}
			  mBluetoothAdapter.startDiscovery();
			  return true;
			}else{

              return false;
			}


	 }

}



