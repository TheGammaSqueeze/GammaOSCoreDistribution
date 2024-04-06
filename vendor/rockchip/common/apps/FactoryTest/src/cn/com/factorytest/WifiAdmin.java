package cn.com.factorytest;

import java.util.List;

import android.content.Context;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

public class WifiAdmin {
	private WifiManager mWifiManager;
	private WifiInfo mWifiInfo;
	private List<WifiConfiguration> mWifiConfiguration;
	private List<ScanResult> mWifiList;
	
    public WifiAdmin(Context context) {
        mWifiManager = (WifiManager)context.getSystemService("wifi");
        mWifiInfo = mWifiManager.getConnectionInfo();
    }
    
    public void openWifi() {
        if(!mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(true);
        }
    }
    
    public void closeWifi() {
        if(mWifiManager.isWifiEnabled()) {
            mWifiManager.setWifiEnabled(false);
        }
    }
    
    public void startScan() {
        mWifiManager.startScan();
        mWifiList = mWifiManager.getScanResults();
        mWifiConfiguration = mWifiManager.getConfiguredNetworks();
    }
    
    public List getWifiList() {
        return mWifiList;
    }
    
	
    public int getRssi() {
        mWifiInfo = mWifiManager.getConnectionInfo();
        return mWifiInfo.getRssi();
    }
}
