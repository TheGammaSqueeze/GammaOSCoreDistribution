package com.rockchip.wfd;

import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Enumeration;

import com.rockchip.p2p.WifiP2pPeer;
import com.rockchip.p2p.WifiP2pPersistentGroup;
import com.rockchip.wfd.adapter.PeerDeviceAdapter;
import com.rockchip.wfd.adapter.PeerGroupAdapter;

import android.net.NetworkInfo;
import android.net.wifi.WpsInfo;
import android.net.wifi.WifiManager;
import android.net.wifi.p2p.WifiP2pWfdInfo;
import android.net.wifi.p2p.WifiP2pConfig;
import android.net.wifi.p2p.WifiP2pDevice;
import android.net.wifi.p2p.WifiP2pDeviceList;
import android.net.wifi.p2p.WifiP2pGroup;
import android.net.wifi.p2p.WifiP2pGroupList;
import android.net.wifi.p2p.WifiP2pManager;
import android.net.wifi.p2p.WifiP2pManager.GroupInfoListener;
import android.net.wifi.p2p.WifiP2pManager.PeerListListener;
import android.net.wifi.p2p.WifiP2pManager.PersistentGroupInfoListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemProperties;
import android.preference.Preference;
import android.preference.PreferenceCategory;
import android.preference.PreferenceGroup;
import android.preference.PreferenceScreen;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnClickListener;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.GridView;
import android.widget.LinearLayout;
import android.widget.Toast;
import android.widget.LinearLayout.LayoutParams;
import android.widget.TextView;

public class WifiDisplayActivity extends Activity implements PeerListListener, PersistentGroupInfoListener, GroupInfoListener {

    private static final String TAG = "WifiDisplayActivity";
    private static final boolean DBG = true;
    private final IntentFilter mIntentFilter = new IntentFilter();
    private WifiP2pManager mWifiP2pManager;
    private WifiP2pManager.Channel mChannel;
    private OnClickListener mRenameListener;
    private OnClickListener mDisconnectListener;
    private OnClickListener mCancelConnectListener;
    private OnClickListener mDeleteGroupListener;
    private WifiP2pDevice mSelectedWifiPeer;
    private WifiP2pGroup mSelectedGroup;
    private EditText mDeviceNameText;

    private boolean mWifiP2pEnabled;
    private boolean mWifiP2pSearching;
    private boolean mWifiP2pConnecting;//用于显示连接中状态
    private int mConnectedDevices;
    private WifiP2pGroup mConnectedGroup;

    private static final int DIALOG_DISCONNECT  = 1;
    private static final int DIALOG_CANCEL_CONNECT = 2;
    private static final int DIALOG_RENAME = 3;
    private static final int DIALOG_DELETE_GROUP = 4;

    private static final String SAVE_DIALOG_PEER = "PEER_STATE";
    private static final String SAVE_DEVICE_NAME = "DEV_NAME";

    private WifiP2pDevice mThisDevice;
    private WifiP2pDeviceList mPeers = new WifiP2pDeviceList();

    private String mSavedDeviceName;
    
    private NetworkDetecting mNetworkDetecting;
    private TextView mDeviceText;
    private TextView mDeviceStatusText;
    private TextView mSearchSummaryText;
    private TextView mSearchBtn;
    private TextView mSettingBtn;
	private GridView mDeviceGridView;
	private GridView mGroupGridView;
	
	private boolean isShowingUI = false;//APP是否可见
	private Handler mMainHandler = new Handler();
	
	private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if(WifiManager.WIFI_STATE_CHANGED_ACTION.equals(action)){
				int wifiState = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
	                    WifiManager.WIFI_STATE_UNKNOWN);
				if(WifiManager.WIFI_STATE_ENABLED == wifiState){
					updateDevicePref();
				}
			}else if (WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION.equals(action)) {
                mWifiP2pEnabled = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE,
                    WifiP2pManager.WIFI_P2P_STATE_DISABLED) == WifiP2pManager.WIFI_P2P_STATE_ENABLED;
                handleP2pStateChanged();
            } else if (WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION.equals(action)) {
                if (mWifiP2pManager != null) {
                    mWifiP2pManager.requestPeers(mChannel, WifiDisplayActivity.this);
                }
            } else if (WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION.equals(action)) {
                if (mWifiP2pManager == null) return;
                Log.d(TAG, "receive action:"+action);
                NetworkInfo networkInfo = (NetworkInfo) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_NETWORK_INFO);
                if (mWifiP2pManager != null) {
                    mWifiP2pManager.requestGroupInfo(mChannel, WifiDisplayActivity.this);
                }
                mMainHandler.removeCallbacks(mDelaySearchAction);
                if (networkInfo.isConnected()) {
                    if (DBG) Log.d(TAG, "Connected");
                } else {
                    //start a search when we are disconnected
                    Log.d(TAG, "###222222start seartch");
                    startSearch();
                }
            } else if (WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION.equals(action)) {
                mThisDevice = (WifiP2pDevice) intent.getParcelableExtra(
                        WifiP2pManager.EXTRA_WIFI_P2P_DEVICE);
                Log.d(TAG, "action:"+action+"name:"+mThisDevice.deviceName);
                updateDevicePref();
            } else if (WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION.equals(action)) {
                int discoveryState = intent.getIntExtra(WifiP2pManager.EXTRA_DISCOVERY_STATE,
                    WifiP2pManager.WIFI_P2P_DISCOVERY_STOPPED);
                if (DBG) Log.d(TAG, "Discovery state changed: " + discoveryState);
                if (discoveryState == WifiP2pManager.WIFI_P2P_DISCOVERY_STARTED) {
                    updateSearchMenu(true, true);
                } else {
                    updateSearchMenu(false, true);
                }
            } else if (WifiP2pManager.ACTION_WIFI_P2P_PERSISTENT_GROUPS_CHANGED.equals(action)) {
                if (mWifiP2pManager != null) {
                    mWifiP2pManager.requestPersistentGroupInfo(mChannel, WifiDisplayActivity.this);
                }
            }
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.wifidisplay);
        mDeviceText = (TextView)findViewById(R.id.wfd_txt_device_name);
        mDeviceStatusText = (TextView)findViewById(R.id.wfd_txt_device_status);
        mSearchBtn = (TextView)findViewById(R.id.wfd_search);
        mSettingBtn = (TextView)findViewById(R.id.wfd_setting);
        mSearchSummaryText = (TextView)findViewById(R.id.wfd_search_summary);
        mDeviceGridView = (GridView)findViewById(R.id.wfd_grid_device);
        mGroupGridView = (GridView)findViewById(R.id.wfd_grid_group);
        mDeviceGridView.setOnItemClickListener(mDeviceItemClickListener);
        mGroupGridView.setOnItemClickListener(mGroupItemClickListener);
        mSearchBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if(mNetworkDetecting.detectWifiEnable()){
					boolean forceSearch = true;
                                       Log.d(TAG, "333startsearch");
					startSearch(forceSearch);
				}
			}
		});
        mSettingBtn.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if(mNetworkDetecting.detectWifiEnable()){
					showDialog(DIALOG_RENAME);
				}
			}
		});
        mNetworkDetecting = new NetworkDetecting(this);
        startService(new Intent(this, WifiDisplayService.class));
        initContext(savedInstanceState);
    }
    
    private void initContext(Bundle savedInstanceState){
    	mIntentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
    	mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION);
    	mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.WIFI_P2P_DISCOVERY_CHANGED_ACTION);
        mIntentFilter.addAction(WifiP2pManager.ACTION_WIFI_P2P_PERSISTENT_GROUPS_CHANGED);

        mWifiP2pManager = (WifiP2pManager) getSystemService(Context.WIFI_P2P_SERVICE);
        if (mWifiP2pManager != null) {
            mChannel = mWifiP2pManager.initialize(this, getMainLooper(), null);
            if (mChannel == null) {
                //Failure to set up connection
                Log.e(TAG, "Failed to set up connection with wifi p2p service");
                mWifiP2pManager = null;
            }
        } else {
            Log.e(TAG, "mWifiP2pManager is null !");
        }

        Log.e(TAG, "init Context with wifi p2p service****************");

        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_DIALOG_PEER)) {
            WifiP2pDevice device = savedInstanceState.getParcelable(SAVE_DIALOG_PEER);
            mSelectedWifiPeer = device;
        }
        if (savedInstanceState != null && savedInstanceState.containsKey(SAVE_DEVICE_NAME)) {
            mSavedDeviceName = savedInstanceState.getString(SAVE_DEVICE_NAME);
        }

        mRenameListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.setDeviceName(mChannel,
                                mDeviceNameText.getText().toString(),
                                new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " device rename success");
                            }
                            public void onFailure(int reason) {
                                Toast.makeText(WifiDisplayActivity.this,
                                        R.string.wifi_p2p_failed_rename_message,
                                        Toast.LENGTH_LONG).show();
                            }
                        });
                    }
                }
            }
        };

        //disconnect dialog listener
        mDisconnectListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.removeGroup(mChannel, new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " remove group success");
                            }
                            public void onFailure(int reason) {
                                if (DBG) Log.d(TAG, " remove group fail " + reason);
                            }
                        });
                    }
                }
            }
        };

        //cancel connect dialog listener
        mCancelConnectListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.cancelConnect(mChannel,
                                new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " cancel connect success");
                            }
                            public void onFailure(int reason) {
                                if (DBG) Log.d(TAG, " cancel connect fail " + reason);
                            }
                        });
                    }
                }
            }
        };

        //delete persistent group dialog listener
        mDeleteGroupListener = new OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (which == DialogInterface.BUTTON_POSITIVE) {
                    if (mWifiP2pManager != null) {
                        mWifiP2pManager.deletePersistentGroup(mChannel,
                                mSelectedGroup.getNetworkId(),
                                new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " delete group success");
                            }
                            public void onFailure(int reason) {
                                if (DBG) Log.d(TAG, " delete group fail " + reason);
                            }
                        });
                    }
                }
            }
        };
    }
    
    
    @Override
    protected void onResume() {
        Log.d(TAG, "onResume wifidisplay");
    	super.onResume();
    	mNetworkDetecting.detectWifiEnable();
        registerReceiver(mReceiver, mIntentFilter);
        //若直接设置为true，在弹出连接对话框时，会进行research
        //mMainHandler.postDelayed(mSetShowingUIAction, 1000);
        isShowingUI = true;
    }
    
    private Runnable mSetShowingUIAction = new Runnable(){
    	public void run() {
    		isShowingUI = true;
    	}
    };
    
    @Override
    protected void onPause() {
        Log.d(TAG, "onPause Wifidisplay");
    	super.onPause();
    	//mWifiP2pManager.stopPeerDiscovery(mChannel, null);
        unregisterReceiver(mReceiver);
        mMainHandler.removeCallbacks(mSetShowingUIAction);
        isShowingUI = false;
    }
    
    @Override
    protected void onDestroy() {
     Log.d(TAG, "onDestroy WifidisplayService");
    	super.onDestroy();
   final Intent intent = new Intent(this, WifiDisplayService.class);
stopService(intent);
        Log.d(TAG, " stop WifiDisplayService service");
    }
    
    
    @Override
    public Dialog onCreateDialog(int id, Bundle args) {
        if (id == DIALOG_DISCONNECT) {
            String deviceName = TextUtils.isEmpty(mSelectedWifiPeer.deviceName) ?
                    mSelectedWifiPeer.deviceAddress :
                    mSelectedWifiPeer.deviceName;
            String msg;
            if (mConnectedDevices > 1) {
                msg = getString(R.string.wifi_p2p_disconnect_multiple_message,
                        deviceName, mConnectedDevices - 1);
            } else {
                msg = getString(R.string.wifi_p2p_disconnect_message, deviceName);
            }
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.wifi_p2p_disconnect_title)
                .setMessage(msg)
                .setPositiveButton(getString(R.string.dlg_ok), mDisconnectListener)
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .create();
            return dialog;
        } else if (id == DIALOG_CANCEL_CONNECT) {
            int stringId = R.string.wifi_p2p_cancel_connect_message;
            String deviceName = TextUtils.isEmpty(mSelectedWifiPeer.deviceName) ?
                    mSelectedWifiPeer.deviceAddress :
                    mSelectedWifiPeer.deviceName;

            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.wifi_p2p_cancel_connect_title)
                .setMessage(getString(stringId, deviceName))
                .setPositiveButton(getString(R.string.dlg_ok), mCancelConnectListener)
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .create();
            return dialog;
        } else if (id == DIALOG_RENAME) {
            mDeviceNameText = new EditText(this);
            if (mSavedDeviceName != null) {
                mDeviceNameText.setText(mSavedDeviceName);
                mDeviceNameText.setSelection(mSavedDeviceName.length());
            } else if (mThisDevice != null && !TextUtils.isEmpty(mThisDevice.deviceName)) {
                mDeviceNameText.setText(mThisDevice.deviceName);
                mDeviceNameText.setSelection(0, mThisDevice.deviceName.length());
            }
            mSavedDeviceName = null;
            AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.wifi_p2p_menu_rename)
                .setView(mDeviceNameText)
                .setPositiveButton(getString(R.string.dlg_ok), mRenameListener)
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .create();
            return dialog;
        } else if (id == DIALOG_DELETE_GROUP) {
            int stringId = R.string.wifi_p2p_delete_group_message;

            AlertDialog dialog = new AlertDialog.Builder(this)
                .setMessage(getString(stringId))
                .setPositiveButton(getString(R.string.dlg_ok), mDeleteGroupListener)
                .setNegativeButton(getString(R.string.dlg_cancel), null)
                .create();
            return dialog;
        }
        return super.onCreateDialog(id, null);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        if (mSelectedWifiPeer != null) {
            outState.putParcelable(SAVE_DIALOG_PEER, mSelectedWifiPeer);
        }
        if (mDeviceNameText != null) {
            outState.putString(SAVE_DEVICE_NAME, mDeviceNameText.getText().toString());
        }
    }

    public void onPeersAvailable(WifiP2pDeviceList peers) {
        mPeers = peers;
        mConnectedDevices = 0;
        ArrayList<WifiP2pDevice> deviceList = new ArrayList<WifiP2pDevice>();
        for (WifiP2pDevice peer: peers.getDeviceList()) {
            if (DBG) Log.d(TAG, " peer " + peer);
            if(WifiDisplayService.isWifiDisplaySource(peer)){
	            deviceList.add(peer);
	            if (peer.status == WifiP2pDevice.CONNECTED) mConnectedDevices++;
            }
        }
        if (DBG) Log.d(TAG, " mConnectedDevices " + mConnectedDevices);
        updateDeviceStatus(false);
        setDeviceAdapter(deviceList);
    }

    public void onPersistentGroupInfoAvailable(WifiP2pGroupList groups) {
    	ArrayList<WifiP2pGroup> groupList = new ArrayList<WifiP2pGroup>();

        for (WifiP2pGroup group: groups.getGroupList()) {
            if (DBG) Log.d(TAG, " group " + group);
            groupList.add(group);
        }
        setGroupAdapter(groupList);
    }

    public void onGroupInfoAvailable(WifiP2pGroup group) {
        if (DBG) Log.d(TAG, " group " + group);
        mConnectedGroup = group;
        updateDevicePref();
    }

    private void handleP2pStateChanged() {
        updateSearchMenu(false, true);
        if (mWifiP2pEnabled) {
            /* Request latest set of peers */
            mWifiP2pManager.requestPeers(mChannel, WifiDisplayActivity.this);
        }
    }

    private void updateSearchMenu(boolean searching, boolean isDiscoveryChanged) {
       mWifiP2pSearching = searching;
       updateDeviceStatus(isDiscoveryChanged);
//       mSearchSummaryText.setVisibility(mWifiP2pSearching?View.VISIBLE:View.INVISIBLE);
    }

    private void startSearch() {
    	mMainHandler.removeCallbacks(mDelaySearchAction);
    	startSearch(false);
    }
    private void startDelaySearch() {
    	mMainHandler.removeCallbacks(mDelaySearchAction);
    	mMainHandler.postDelayed(mDelaySearchAction, 1500);
    }
    private Runnable mDelaySearchAction = new Runnable(){
    	public void run(){
    		if(hasP2pInterface()){//Connecting...Don't search..
    			mWifiP2pConnecting = true;
    			updateDeviceStatus(false);
    			mWifiP2pConnecting = false;
    		}else{
    			startSearch(false);
    			Log.d(TAG, "Do search action.");
    		}
    	}
    };
    
    private void startSearch(boolean forceSearch) {
        if (mWifiP2pManager != null && (!mWifiP2pSearching||forceSearch)) {
            mWifiP2pManager.discoverPeers(mChannel, new WifiP2pManager.ActionListener() {
                public void onSuccess() {
                }
                public void onFailure(int reason) {
                    if (DBG) Log.d(TAG, " discover fail " + reason);
                }
            });
        }
    }

    private void updateDevicePref() {
        if (mThisDevice != null) {
        	//初始化界面时,如果wifi未打开，则不进行设置设备名称。
        	//避免自定义名称ro.wfd.devicename因未打开WIFI未进行设置，但已生成一个随机名称。采用不显示来处理
        	if (!mNetworkDetecting.isWifiEnabled()&&TextUtils.isEmpty(mDeviceText.getText())) {
        		return;
        	}
            if (TextUtils.isEmpty(mThisDevice.deviceName)) {
                mDeviceText.setText(mThisDevice.deviceAddress);
            } else {
            	mDeviceText.setText(mThisDevice.deviceName);
            }
            Log.d(TAG, "updateDevicepref:"+mThisDevice.deviceName);
        }
    }
    
    private void updateDeviceStatus(boolean isDiscoveryChanged){
    	int status = WifiP2pDevice.AVAILABLE;
    	for (WifiP2pDevice peer: mPeers.getDeviceList()) {
            if (peer.status == WifiP2pDevice.CONNECTED || peer.status == WifiP2pDevice.INVITED){
            	status = peer.status;
            	break;
            }
        }
    	
    	TextView statusTxt = mSearchSummaryText;//mDeviceStatusText
    	if(status==WifiP2pDevice.INVITED){
    		statusTxt.setText(R.string.wfd_status_connecting);
    	}else if(status==WifiP2pDevice.CONNECTED){
    		statusTxt.setText(R.string.wfd_status_connected);
    	}else if(mWifiP2pSearching){
    		statusTxt.setText(R.string.wfd_searching);
    	}else if(mWifiP2pConnecting){
    		statusTxt.setText(R.string.wfd_status_connecting);
    	}else{
    		statusTxt.setText("");
    		//在APP界面上，若已停止搜索,　则继续搜索，避免使用手动搜索　by fxw 20130913
    		if(isShowingUI&&isDiscoveryChanged){
                       Log.d(TAG, "startdelaysearch");
    			startDelaySearch();
    		}
    	}
    }
    
    /**
     * 由于底层没有将正在连接的状态上报应用层。应用通过判断是否已经开启p2p端口来判定是否已经正在连接
     * @return
     */
    private boolean hasP2pInterface(){
    	try{
    		Enumeration<NetworkInterface> nis = NetworkInterface.getNetworkInterfaces();
			while (nis.hasMoreElements()){
				NetworkInterface ni = (NetworkInterface)nis.nextElement();
				if(ni!=null&&ni.getName()!=null&&ni.getName().indexOf("p2p-p2p0")!=-1){
					return true;
				}
			}
			return false;
		}catch(Exception e){
			return false;
		}
    }
    
    //Set Device GridView Adapter
    private void setDeviceAdapter(ArrayList<WifiP2pDevice> deviceList){
    	PeerDeviceAdapter deviceAdapter = new PeerDeviceAdapter(this, deviceList);
    	int itemWidth = getResources().getDimensionPixelSize(R.dimen.wfd_grid_item_width);
        int itemSpacing = getResources().getDimensionPixelSize(R.dimen.wfd_grid_item_spacing);
        LayoutParams params = new LayoutParams(deviceList.size() * (itemWidth + itemSpacing), LayoutParams.WRAP_CONTENT);  
        mDeviceGridView.setLayoutParams(params);  
        mDeviceGridView.setNumColumns(deviceList.size());
        mDeviceGridView.setAdapter(deviceAdapter);
    }
    
    //Set Group GridView Adapter
    private void setGroupAdapter(ArrayList<WifiP2pGroup> groupList){
        PeerGroupAdapter groupAdapter = new PeerGroupAdapter(this, groupList);
        int itemWidth = getResources().getDimensionPixelSize(R.dimen.wfd_grid_item_width);
        int itemSpacing = getResources().getDimensionPixelSize(R.dimen.wfd_grid_item_spacing);
        LayoutParams params = new LayoutParams(groupList.size() * (itemWidth + itemSpacing), LayoutParams.WRAP_CONTENT);  
        mGroupGridView.setLayoutParams(params);  
        mGroupGridView.setNumColumns(groupList.size());
        mGroupGridView.setAdapter(groupAdapter);
    }
    
    //Device Grid onItemClick
    OnItemClickListener mDeviceItemClickListener = new OnItemClickListener(){
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			mSelectedWifiPeer = (WifiP2pDevice) parent.getItemAtPosition(position);
            if (mSelectedWifiPeer.status == WifiP2pDevice.CONNECTED) {
                showDialog(DIALOG_DISCONNECT);
            } else if (mSelectedWifiPeer.status == WifiP2pDevice.INVITED) {
                showDialog(DIALOG_CANCEL_CONNECT);
            } else {
            	/*
                WifiP2pConfig config = new WifiP2pConfig();
                config.deviceAddress = mSelectedWifiPeer.deviceAddress;
                
                int forceWps = SystemProperties.getInt("wifidirect.wps", -1);

                if (forceWps != -1) {
                    config.wps.setup = forceWps;
                } else {
                    if (mSelectedWifiPeer.wpsPbcSupported()) {
                        config.wps.setup = WpsInfo.PBC;
                    } else if (mSelectedWifiPeer.wpsKeypadSupported()) {
                        config.wps.setup = WpsInfo.KEYPAD;
                    } else {
                        config.wps.setup = WpsInfo.DISPLAY;
                    }
                }

                mWifiP2pManager.connect(mChannel, config,
                        new WifiP2pManager.ActionListener() {
                            public void onSuccess() {
                                if (DBG) Log.d(TAG, " connect success");
                            }
                            public void onFailure(int reason) {
                                Log.e(TAG, " connect fail " + reason);
                                Toast.makeText(WifiDisplayActivity.this,
                                        R.string.wifi_p2p_failed_connect_message,
                                        Toast.LENGTH_SHORT).show();
                            }
                    });
                 */
            }
		}
    };
    
    //Group Grid onItemClick
    OnItemClickListener mGroupItemClickListener = new OnItemClickListener(){
		public void onItemClick(AdapterView<?> parent, View view, int position,
				long id) {
			mSelectedGroup = (WifiP2pGroup) parent.getItemAtPosition(position);
            showDialog(DIALOG_DELETE_GROUP);
		}
	};

}
