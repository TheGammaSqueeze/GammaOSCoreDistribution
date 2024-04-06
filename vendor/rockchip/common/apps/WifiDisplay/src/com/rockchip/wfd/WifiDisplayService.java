package com.rockchip.wfd;

import java.util.ArrayList;

import com.rockchip.wfd.consts.WFD;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pInfo;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.Channel;
import android.net.wifi.p2p.WifiP2pManager.ConnectionInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.os.Handler;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.os.SystemProperties;
import android.os.IBinder;
import android.os.UserHandle;
import android.preference.PreferenceManager;
import android.util.Log;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.content.SharedPreferences;
import android.widget.Toast;

public class WifiDisplayService extends Service {
	
    public static final String STOP_ACTION = "com.rockchip.internal.appservicecommand.stop";
    public static final String CMDFROM = "commandfrom";
    public static final String CMDFROM_WFD = "wfd";
    
	public static final boolean DBG = true;
	public static final int DISCOVER_PEER_DELAY_MILLIS = 10000;
	public static final int NEXT_DISCOVER_PEER_DELAY_MILLIS = 8000;
	public static final String TAG = "RKWifiDisplayService";
	private NetworkDetecting mNetworkDetecting;
	private boolean isIniting = false;
	private boolean isInited = false;
	private WifiP2pManager mWifiP2pManager;
    private WifiP2pManager.Channel mChannel;
	private boolean mWifiDisplayOnSetting = true;
	private boolean mWifiP2pEnabled = false;
	private boolean mWfdEnabling = false;
	private boolean mWfdEnabled = false;
	private boolean mWfdServiceEnabled = false;
	private boolean mWifiP2pSearching = false;
	private final ArrayList<WifiP2pDevice> mWifiDisplayPeers = new ArrayList<WifiP2pDevice>();
	private Handler mMainHandler;
	PowerManager mPowerManager = null;
	WakeLock mWakeLock = null;
	private static final boolean ENABLE_SEARCH = false;
        Thread mwfdSinkThread;

        public native int native_startWFDSink(String devaddr, boolean go);

        static {
           System.loadLibrary("wfdsink_jni");
        }

	@Override
	public void onCreate() {
		super.onCreate();
		mMainHandler = new Handler();
		mNetworkDetecting = new NetworkDetecting(WifiDisplayService.this);
		if(mNetworkDetecting.isWifiEnabled()){
			initWifiDisplayService();
		}
		IntentFilter filter = new IntentFilter();
		filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
		filter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
		filter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
		filter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
		filter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
		filter.addAction(STOP_ACTION);
		registerReceiver(mWifiDisplayReceiver, filter);
		
		mPowerManager = (PowerManager)this.getSystemService(Context.POWER_SERVICE);
		mWakeLock = mPowerManager.newWakeLock(PowerManager.FULL_WAKE_LOCK, "PowerLock");
	}
	
	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		return super.onStartCommand(intent, flags, startId);
	}

	@Override
	public IBinder onBind(Intent arg0) {
		return null;
	}
	
	@Override
	public void onDestroy() {
                Log.d(TAG, "destroyed service");
		super.onDestroy();
		unregisterReceiver(mWifiDisplayReceiver);
	}
	
	//Init wfd service
	private void initWifiDisplayService(){
		if(isInited||isIniting) return;
		isIniting = true;
		mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (mWifiP2pManager != null) {
        	mChannel = mWifiP2pManager.initialize(this, getMainLooper(), null);
        	startSearchDelay();
            isInited = true;
            initDeviceName();//需在wifi打开状态下，才能成功初始化设备名称
            if(DBG) Log.d(TAG, "Init wfd service.");
        }else{
        	isInited = false;
        }
        isIniting = false;
	}
	
	//Init device name for custom config name
	private void initDeviceName(){
		SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
		String wfdDeviceName = pref.getString("WFDDeviceName", null);
		if(wfdDeviceName==null||"".equals(wfdDeviceName)){
			wfdDeviceName = (String)SystemProperties.get("ro.wfd.devicename");
			if(wfdDeviceName!=null&&!"".equals(wfdDeviceName)){//在系统中配置默认名称
				final String tWfdName = wfdDeviceName;
				Runnable setDeviceNameAction = new Runnable(){
					public void run(){
						mWifiP2pManager.setDeviceName(mChannel,
								tWfdName,
			                    new WifiP2pManager.ActionListener() {
				                    public void onSuccess() {
				                        Log.d(TAG, "init device name success");
				                    }
				                    public void onFailure(int reason) {
				                    	Toast.makeText(WifiDisplayService.this,
		                                        R.string.wifi_p2p_failed_initname_message,
		                                        Toast.LENGTH_LONG).show();
				                    	Log.d(TAG, "init device name failed");
				                    }
			            		});
					}
				};
				mMainHandler.postDelayed(setDeviceNameAction, 100);//提高设置成功率
			}else{
				wfdDeviceName = "SYS_DEFAULT";
			}
			pref.edit().putString("WFDDeviceName", wfdDeviceName).commit();
		}
	}
	
	//Wifi P2P State Changed
	private void handleStateChanged(boolean enabled) {
		mWifiP2pSearching = false;
        mWifiP2pEnabled = enabled;
        updateWfdEnableState();
    }
	
	private void updateWfdEnableState() {
        if (mWifiDisplayOnSetting && mWifiP2pEnabled) {
            // WFD should be enabled.
            if (!mWfdEnabled && !mWfdEnabling) {
                mWfdEnabling = true;
                //By fxw 20130913 解决sony手机会检测HDCP位
                //final int deviceInfo = 0x0111;//WFD Device Info:   2byte. 0111. primary sink, session available, content protection using HDCP2.x
                //WifiP2pWfdInfo wfdInfo = new WifiP2pWfdInfo(deviceInfo, WFD.WFD_CONTROL_PORT, WFD.WFD_MAX_THROUGHPUT);
                //wfdInfo.setMaxThroughput(WFD.WFD_MAX_THROUGHPUT);
                WifiP2pWfdInfo wfdInfo = new WifiP2pWfdInfo();
                wfdInfo.setEnabled(true);
                wfdInfo.setDeviceType(WifiP2pWfdInfo.DEVICE_TYPE_PRIMARY_SINK);
                wfdInfo.setSessionAvailable(true);
                wfdInfo.setControlPort(WFD.WFD_CONTROL_PORT);
                wfdInfo.setMaxThroughput(WFD.WFD_MAX_THROUGHPUT);
                mWifiP2pManager.setWfdInfo(mChannel, wfdInfo, new WifiP2pManager.ActionListener() {
                    public void onSuccess() {
                        if (DBG) Log.d(TAG, "Successfully set WFD info.");
                        if (mWfdEnabling) {
                            mWfdEnabling = false;
                            mWfdEnabled = true;
                        }
                    }

                    public void onFailure(int reason) {
                        if (DBG) {
                        	Log.d(TAG, "Failed to set WFD info with reason " + reason + ".");
                        }
                        mWfdEnabling = false;
                    }
                });
            }
        } else {
            // WFD should be disabled.
            mWfdEnabling = false;
            mWfdEnabled = false;
        }
    }
	
	private void handlePeersChanged() {
        // Even if wfd is disabled, it is best to get the latest set of peers to
        // keep in sync with the p2p framework
        requestPeers();
    }
	private void requestPeers() {
        mWifiP2pManager.requestPeers(mChannel, new PeerListListener() {
            @Override
            public void onPeersAvailable(WifiP2pDeviceList peers) {
                mWifiDisplayPeers.clear();
                for (WifiP2pDevice device : peers.getDeviceList()) {
                    if (isWifiDisplaySource(device)) {
                        mWifiDisplayPeers.add(device);
                    }
                    if (DBG) Log.d(TAG, "WifiDisplay peers size: "+mWifiDisplayPeers.size());
                }
            }
        });
    }
	
	public static boolean isWifiDisplaySource(WifiP2pDevice device) {
        WifiP2pWfdInfo wfdInfo = device.getWfdInfo();
        return wfdInfo != null
                && wfdInfo.isEnabled()
                && isSourceDeviceType(wfdInfo.getDeviceType());
    }

    private static boolean isSourceDeviceType(int deviceType) {
        return deviceType == WifiP2pWfdInfo.DEVICE_TYPE_WFD_SOURCE
                || deviceType == WifiP2pWfdInfo.DEVICE_TYPE_SOURCE_OR_PRIMARY_SINK;
    }
	
	//WFD Receiver
	private BroadcastReceiver mWifiDisplayReceiver = new BroadcastReceiver(){
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if(WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)){
				int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
	                    WifiManager.WIFI_STATE_UNKNOWN);
				if(WifiManager.WIFI_STATE_ENABLED == wifiState){
					if(!isInited){
						initWifiDisplayService();
					}else{
						startSearchDelay();
					}
				}
			}else if(!isInited){// if havn't inited
				return;
			}else if(action.equals(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)) {
                // This broadcast is sticky so we'll always get the initial Wifi P2P state on startup.
                boolean enabled = (intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                        WifiP2pManager.WIFI_P2P_STATE_DISABLED)) ==
                        WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                if (DBG) {
                    Log.d(TAG, "Received WIFI_P2P_STATE_CHANGED_ACTION: enabled="
                            + enabled);
                }

                handleStateChanged(enabled);
            } else if (action.equals(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)) {
                if (DBG) {
                    Log.d(TAG, "Received WIFI_P2P_PEERS_CHANGED_ACTION.");
                }

                handlePeersChanged();
            } else if (action.equals(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)) {
                NetworkInfo networkInfo = (NetworkInfo)intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_NETWORK_INFO);
				WifiP2pDevice p2pDeviceInfo = (WifiP2pDevice)intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                if (DBG) {
                    Log.d(TAG, "Received WIFI_P2P_CONNECTION_CHANGED_ACTION: networkInfo="
                            + networkInfo+" P2pDeviceInfo="+p2pDeviceInfo);
                }

				handleWFDConnectionChanged(networkInfo,p2pDeviceInfo);
            }else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                int discoveryState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE,
                        WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                    if (DBG) Log.d(TAG, "Discovery state changed: " + discoveryState);
                    if (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    	mWifiP2pSearching = true;
                    } else {
                    	mWifiP2pSearching = false;
                    	if(!mWfdServiceEnabled){
                    		startSearchDelay();
	                    	if (DBG) Log.d(TAG, "Discovery finished, try to discovery after " + NEXT_DISCOVER_PEER_DELAY_MILLIS);
                    	}
                    }
             }else if(STOP_ACTION.equals(action)){
            	 String cmdfrom = intent.getStringExtra(CMDFROM);
            	 if(!CMDFROM_WFD.equals(cmdfrom)){
            		 if(mWfdServiceEnabled){
            			 Log.d(TAG, "Receive the boardcast of STOP, from "+cmdfrom);
            			 mWfdServiceEnabled = false;
            			 if (mWifiP2pManager != null&&mChannel!=null) {
                             mWifiP2pManager.removeGroup(mChannel, null);
            			 }
            			 SystemProperties.set("ctl.stop","wfd");
            			 releaseWakeLock();
            		 }
            	 }
             }
		}
    };
    
    //Handle wfd connection changed
    private void handleWFDConnectionChanged(NetworkInfo networkInfo, WifiP2pDevice p2pDeviceInfo){
		if (mWfdEnabled && networkInfo.isConnected()) {
			mMainHandler.removeCallbacks(mSearchAction);
			stopSearch();
			mWifiP2pManager.requestConnectionInfo(mChannel, new ConnectionInfoListener(){
				public void onConnectionInfoAvailable(WifiP2pInfo info){
					if(DBG) Log.d(TAG,"####onConnectionInfoAvailable(),info = "+info);
					WifiP2pDevice connectedDevice = null;
					for (WifiP2pDevice device : mWifiDisplayPeers) {
						if(DBG) Log.d(TAG,"Connected device check: ");
						if(device.status==WifiP2pDevice.CONNECTED){
							connectedDevice = device;
							break;
						}
					}
					
					if(connectedDevice == null){
						if(DBG) Log.d(TAG,"Connected device is null");
						return ;
					}else{
						if(DBG) Log.d(TAG,"Connected device: "+connectedDevice);

					}

					//Check whether wfd connection
                                        WifiP2pWfdInfo wfdInfo = connectedDevice.getWfdInfo();
					if(wfdInfo==null/*||!connectedDevice.wfdInfo.isWfdEnabled()*/){
						/*if(DBG)*/ Log.d(TAG,"Connected device isn't wfd connection. ");
						return;
					}else{
						/*if(DBG)*/ Log.d(TAG,"WFD connecting. ");
					}
					
					//Stop other app
					// Tell the music playback service to pause
				//	Intent i = new Intent("com.android.music.musicservicecommand");
			        //i.putExtra("command", "pause");
			        //WifiDisplayService.this.sendBroadcastAsUser(i, UserHandle.ALL);
			        //关闭外部应用 如DLNA
				//	Intent itt2 = new Intent(STOP_ACTION);
				//	itt2.putExtra(CMDFROM, CMDFROM_WFD);
				//	WifiDisplayService.this.sendBroadcastAsUser(itt2, UserHandle.ALL);
					
					//String command = "wfd:-s "+connectedDevice.deviceAddress+":"+connectedDevice.wfdInfo.getControlPort();
                                 //String command = "wfd"; 
                                 final String cmdargs = (info.isGroupOwner?connectedDevice.deviceAddress:info.groupOwnerAddress) + 
                                                     ":" + wfdInfo.getControlPort();
                                 final boolean isgo = info.isGroupOwner;
                                     //  cmdargs = connectedDevice.deviceAddress + ":" + connectedDevice.wfdInfo.getControlPort();
				    if(DBG) Log.d(TAG,"######inited setprop command wfd: "+cmdargs);
                                    mwfdSinkThread = new Thread("wfdSink thread") {
                                        @Override
                                        public void run() {
                                            int ret = native_startWFDSink(cmdargs, isgo);
                                            if (ret < 0) {
                                               Log.e(TAG, "error, start wfdsink thread failed");
                                            }
                                            Log.d(TAG, "wfdsink thread exit");
                                        }
                                    };
                                    mwfdSinkThread.start();
                                    
                                    if (DBG) Log.d(TAG, "native wfd method invoked"); 
				
				    //SystemProperties.set("ctl.start",command);
                                    //SystemProperties.set("net.wfd.addr",cmdargs);
				    mWfdServiceEnabled = true;
				    acquireWakeLock();
				}
			});				
		}else{
			if(!networkInfo.isConnected()){
				if(mWfdServiceEnabled){
					startSearch();
				}else{
					startSearchDelay();
				}
			}
			if(mWfdServiceEnabled==true){
				mWfdServiceEnabled = false;
				//SystemProperties.set("ctl.stop","wfd");
				releaseWakeLock();
				// Tell the music playback service to play
				//Intent i = new Intent("com.android.music.musicservicecommand");
		        //i.putExtra("command", "play");
		        //WifiDisplayService.this.sendBroadcastAsUser(i, UserHandle.ALL);
			}
		}
	}
    
    //Start search
    private void startSearch() {
    	if(!ENABLE_SEARCH) return;
        if (mWifiP2pManager != null && !mWifiP2pSearching && mNetworkDetecting.isWifiEnabled()) {
            mWifiP2pManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                public void onSuccess() {
                }
                public void onFailure(int reason) {
                    if (DBG) Log.d(TAG, " discover fail " + reason);
                }
            });
        }
    }
    private void startSearchDelay(){
    	if(!ENABLE_SEARCH) return;
    	mMainHandler.removeCallbacks(mSearchAction);
    	mMainHandler.postDelayed(mSearchAction, DISCOVER_PEER_DELAY_MILLIS);
    }
    private Runnable mSearchAction = new Runnable(){
		public void run() {
			startSearch();
		}
    };
    
    //Stop search
    private void stopSearch(){
    	if (mWifiP2pManager != null && mWifiP2pSearching) {
            mWifiP2pManager.stopPeerDiscovery(mChannel, new WifiP2pManager.ActionListener() {
                public void onSuccess() {
                }
                public void onFailure(int reason) {
                    if (DBG) Log.d(TAG, " stop discover fail " + reason);
                }
            });
        }
    }
    
    public void acquireWakeLock() {
		if (mWakeLock != null) {
			try {
				if (mWakeLock.isHeld() == false)
					mWakeLock.acquire();
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
	public void releaseWakeLock() { 
		if (mWakeLock != null) {
			try {
				if (mWakeLock.isHeld()) {
					mWakeLock.release();
					mWakeLock.setReferenceCounted(false);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
		}
	}
}
