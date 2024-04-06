package cn.com.factorytest;

import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.graphics.Color;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.EditText;
import android.widget.TextView;
import android.view.View;

public class WriteMacActivity extends Activity {
	private static final String TAG = "FactoryTest";
	
	EditText m_EditMac;
	TextView m_MacAddr;
	TextView m_SnAddr;
	TextView m_UsidAddr;
	TextView m_DeviceidAddr;
	
	TextView m_MacAddr_Title;
	TextView m_SnAddr_Title;
	TextView m_UsidAddr_Title;
	TextView m_DeviceidAddr_Title;
	
	private boolean bIsKeyDown = false;

	private boolean SN_SHOW = false;
	private boolean USID_SHOW = false;
	private boolean DEVICE_ID_SHOW = false;
	private boolean WriteMac_ok_flag = false;
	private int MAC_LENGTH = 17;

	private final int MSG_TIME = 777;
	private TimeHandler mHandler = new TimeHandler();	
    private class TimeHandler extends Handler {        
    	@Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            		case MSG_TIME:
            		{
            			if(bIsKeyDown)
            			{
            				bIsKeyDown = false;
            				mHandler.removeMessages(MSG_TIME);
            				mHandler.sendEmptyMessageDelayed(MSG_TIME, 1 * 1000); 
            			}
            			else
            			{
            				mHandler.removeMessages(MSG_TIME);
            				OnScanText();  
            			}
            		}
            		break;            		
            }
        }    	
    }
    
    private void OnScanText()
    {
    	int nTextlen = m_EditMac.getText().toString().length();


    	if(getResources().getInteger(R.integer.config_mac_length) == nTextlen)
    	{
			OnWriteMac(false);
    	}
    	if(getResources().getInteger(R.integer.config_mac_length2) == nTextlen)
    	{
			OnWriteMac(true);
    	}
    	else if(getResources().getInteger(R.integer.config_usid_length) == nTextlen)
    	{
    		OnWriteUsid();
    	}
    	else if(getResources().getInteger(R.integer.config_sn_length) == nTextlen)
    	{
    		OnWriteSn();
    	}    	
    	else if(getResources().getInteger(R.integer.config_deviceid_length) == nTextlen)
    	{
    		OnWriteDeviceid();
    	}
    	else
    	{
    		m_EditMac.setText("");
    		m_EditMac.requestFocus();
    		
//    		Toast.makeText(this, R.string.ScanError, Toast.LENGTH_SHORT).show();
    	}
    }

	public void OnWriteMac(boolean is_otp)
	{
		Log.e(TAG, "public void OnWriteMac()");
		
		String strMac = m_EditMac.getText().toString();
		if(strMac.isEmpty() ) { return; }
		Log.e(TAG, "is_otp = " + is_otp+ " strMac : " + strMac);
		
		WriteMac(strMac);
		if (is_otp) {
			ShowMac_OTP();
		} else {
			ShowMac();
		}
				
		m_EditMac.setText("");
		m_EditMac.requestFocus();
		
		if(WriteMac_ok_flag)
			this.finish();
	}
	
	public void OnWriteSn()
	{
		Log.e(TAG, "public void OnWriteSn()");
		
		String strSn = m_EditMac.getText().toString();
		if(strSn.isEmpty() ) { return; }
		Log.e(TAG, "strSn : " + strSn);
		
		WriteSn(strSn);
		ShowSn();	
		
		m_EditMac.setText("");
		m_EditMac.requestFocus();
	}
	
	public void OnWriteUsid()
	{
		Log.e(TAG, "public void OnWriteUsid()");
		
		String strUsid = m_EditMac.getText().toString();
		if(strUsid.isEmpty() ) { return; }
		Log.e(TAG, "strUsid : " + strUsid);
		//截取前20位
		//805写入usid不管多少位，最终生成的usid都会自动加上mac的12位
	//	905写入的usid他不会自动去加mac了 所以不用截取前20位
		String newstrUsid = strUsid.substring(0, 20);
		Log.e(TAG, "newstrUsid : " + newstrUsid);
		if(Tools.isGxbaby()){
			WriteUsid(strUsid);
		} else {
			WriteUsid(newstrUsid);
		}
		ShowUsid();	
		
		m_EditMac.setText("");
		m_EditMac.requestFocus();
	}
	
	public void OnWriteDeviceid()
	{
		Log.e(TAG, "public void OnWriteDeviceid()");
		
		String strDeviceid = m_EditMac.getText().toString();
		if(strDeviceid.isEmpty() ) { return; }
		Log.e(TAG, "strDeviceid : " + strDeviceid);
		
		WriteDeviceid(strDeviceid);
		ShowDeviceid();	
		
		m_EditMac.setText("");
		m_EditMac.requestFocus();
	}
	
	public void WriteMac(String strMac)
	{	

		if (getResources().getBoolean(R.bool.config_write_mac_in_otp)) {
			if (strMac.length() == 17) {
				if((':' == strMac.charAt(2) ) && (':' == strMac.charAt(5) ) && (':' == strMac.charAt(8) ) && (':' == strMac.charAt(11) ) && (':' == strMac.charAt(14) ) ) {
					String mac = strMac.replaceAll(":","");
					int length = mac.length();
					boolean format_err = true;
					Log.e(TAG, "OTP MAC= " + mac);
					for (int i=0; i< length; i++) {
						int value = (int)mac.charAt(i);
						if(((value > 0x2f) && (value < 0x3a)) || ((value > 0x40) && (value < 0x47)) || ((value > 0x60) && (value < 0x67))) {
							if (i == 1) {
								if (value > 0x2f && value < 0x3a) {
									if (value%2 == 1) {
										format_err = true;
										break;
									}
								} else {
									if (value%2 == 0) {
										format_err = true;
										break;
									}
								}
							}
							format_err = false;
						} else {
							format_err = true;
							break;
						}

					}
					Log.d(TAG,"MAC ="+ mac + " format_err= "+format_err);
					if (!format_err) {
						Tools.writeFile(Tools.Key_OTP_Mac, mac);
						WriteMac_ok_flag = true;
					}
				}
			}
			return;
		}

		Tools.writeFile(Tools.Key_Name, Tools.Key_Mac);
		
		String strTmpMac = "";
		int nLength = strMac.length();
		
		if(getResources().getInteger(R.integer.config_mac_length) == nLength)
		{
			for(int i = 0; i < nLength; i += 2)
			{
				strTmpMac += strMac.substring(i, (i + 2) < nLength ? (i + 2) :  nLength );
				
				if( (i + 2) < nLength) strTmpMac += ':';
			}
		}
		else if(getResources().getInteger(R.integer.config_mac_length2) == nLength)
		{
			if( (':' == strMac.charAt(2) ) && (':' == strMac.charAt(5) ) && (':' == strMac.charAt(8) ) && (':' == strMac.charAt(11) ) && (':' == strMac.charAt(14) ) )
			{
				strTmpMac = strMac;
			}
			else
			{
				strTmpMac = "";
			}				
		}
		else
		{
			strTmpMac = "";
		}
		
		Log.e(TAG, "strTmpMac : " + strTmpMac);
		
		String strNewMac = CHexConver.str2HexStr(strTmpMac);
		Log.e(TAG, "strNewMac : " + strNewMac);
		Tools.writeFile(Tools.Key_Write,  strNewMac);
	}
	
	public void WriteSn(String strSn)
	{	
		Tools.writeFile(Tools.Key_Name, Tools.Key_Sn);
		String strNewSn = CHexConver.str2HexStr(strSn);
		Log.e(TAG, "strNewSn : " + strNewSn);
		 Tools.writeFile(Tools.Key_Write, strNewSn);		 
	}
	
	public static void WriteUsid(String strUsid)
	{	
		Tools.writeFile(Tools.Key_Name, Tools.Key_Usid);
		String strNewUsid = CHexConver.str2HexStr(strUsid);
		Log.e(TAG, " : " + strNewUsid);
		 Tools.writeFile(Tools.Key_Write, strNewUsid);		 
	}
	
	public static void WriteDeviceid(String strDeviceid)
	{	
		Tools.writeFile(Tools.Key_Name, Tools.Key_Deviceid);
		String strNewDeviceid = CHexConver.str2HexStr(strDeviceid);
		Log.e(TAG, " : " + strNewDeviceid);
		 Tools.writeFile(Tools.Key_Write, strNewDeviceid);		 
	}
	
	public void ShowMac()
	{	
		Tools.writeFile(Tools.Key_Name, Tools.Key_Mac);
		String strMac =  Tools.readFile(Tools.Key_Read);
		
		Log.e(TAG, "strMac : " + strMac  + ";  length    : " + strMac.length() );				
		m_MacAddr.setText(CHexConver.hexStr2Str(strMac) );
	}

	public void ShowMac_OTP()
	{
		String strTmpMac = "";
		String strMac = Tools.readFile(Tools.Key_OTP_Mac);
		Log.e(TAG, "strMac : " + strMac  + ";  length    : " + strMac.length() );

		int length = strMac.length();
		if (length != 12) {
			m_MacAddr.setTextColor(Color.RED);
			m_MacAddr.setText("ERR");

		} else {
			for(int i = 0; i < length; i += 2) {

				strTmpMac += strMac.substring(i, (i + 2) < length ? (i + 2) :  length );
				if( (i + 2) < length) strTmpMac += ':';
				}
				m_MacAddr.setText(strTmpMac);
		}
	}
	
	public void ShowSn()
	{
		Tools.writeFile(Tools.Key_Name, Tools.Key_Sn);
		String strSn =  Tools.readFile(Tools.Key_Read);
		
		Log.e(TAG, "strSn : " + strSn);
		m_SnAddr.setText(CHexConver.hexStr2Str(strSn) );
	}
	
	public void ShowUsid()
	{
		Tools.writeFile(Tools.Key_Name, Tools.Key_Usid);
		String strUsid =  Tools.readFile(Tools.Key_Read);
		
		Log.e(TAG, "strUsid : " + strUsid);
		m_UsidAddr.setText(CHexConver.hexStr2Str(strUsid) );
	}
	
	public void ShowDeviceid()
	{
		Tools.writeFile(Tools.Key_Name, Tools.Key_Deviceid);
		String strDeviceid =  Tools.readFile(Tools.Key_Read);
		
		Log.e(TAG, "strDeviceid : " + strDeviceid);
		m_DeviceidAddr.setText(CHexConver.hexStr2Str(strDeviceid) );
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.write_mac);
		WriteMac_ok_flag = false;
		m_EditMac = (EditText)findViewById(R.id.EditTextMac);		
		m_EditMac.setInputType(InputType.TYPE_NULL);
		m_EditMac.addTextChangedListener(mTextWatcher);
		
		m_MacAddr = (TextView)findViewById(R.id.TextView_mac);
		m_SnAddr = (TextView)findViewById(R.id.TextView_sn);
		m_UsidAddr = (TextView)findViewById(R.id.TextView_usid);
		m_DeviceidAddr = (TextView)findViewById(R.id.TextView_deviceid);
				
		m_MacAddr_Title = (TextView)findViewById(R.id.TextView_mac_title);
		m_MacAddr_Title.setText(m_MacAddr_Title.getText().toString() 
				+ "\t\t\t" + getResources().getInteger(R.integer.config_mac_length) + getResources().getString(R.string.showLength) );
		
		m_SnAddr_Title = (TextView)findViewById(R.id.TextView_sn_title);
		m_SnAddr_Title.setText(m_SnAddr_Title.getText().toString() 
				+ "\t\t\t" + getResources().getInteger(R.integer.config_sn_length) + getResources().getString(R.string.showLength) );
		
		m_UsidAddr_Title = (TextView)findViewById(R.id.TextView_usid_title);
		m_UsidAddr_Title.setText(m_UsidAddr_Title.getText().toString() 
				+ "\t\t\t" + getResources().getInteger(R.integer.config_usid_length) + getResources().getString(R.string.showLength) );
		
		m_DeviceidAddr_Title = (TextView)findViewById(R.id.TextView_deviceid_title);
		m_DeviceidAddr_Title.setText(m_DeviceidAddr_Title.getText().toString() 
				+ "\t\t\t" + getResources().getInteger(R.integer.config_deviceid_length) + getResources().getString(R.string.showLength) );
		
		if (!USID_SHOW) {
			m_UsidAddr_Title.setVisibility(View.GONE);
			m_UsidAddr.setVisibility(View.GONE);
		}
		if (!DEVICE_ID_SHOW) {
			m_DeviceidAddr_Title.setVisibility(View.GONE);
			m_DeviceidAddr.setVisibility(View.GONE);
		}

		if (getResources().getBoolean(R.bool.config_write_mac_in_otp)) {
			String str_mac = Tools.readFile(Tools.Key_OTP_Mac);
			ShowMac_OTP();

		} else {
			if(Tools.isGxbaby()){
				Tools.writeFile(Tools.Key_Attach, Tools.Key_Attach_Value);
			}
			String strKeyList = Tools.readFile(Tools.Key_List);
			
			Log.e(TAG, strKeyList);
			if(-1 != strKeyList.indexOf(Tools.Key_Mac) )
			{
				ShowMac();
			}			
			if(-1 != strKeyList.indexOf(Tools.Key_Sn) )
			{
				ShowSn();
			}			
			if(-1 != strKeyList.indexOf(Tools.Key_Usid) )
			{
				ShowUsid();
			}
			if(-1 != strKeyList.indexOf(Tools.Key_Deviceid) )
			{
				ShowDeviceid();
			}
			
		}
	}
	
	TextWatcher mTextWatcher = new TextWatcher() 
	{  
        @Override  
        public void onTextChanged(CharSequence s, int start, int before, int count) 
        {  
        }  
          
        @Override  
        public void beforeTextChanged(CharSequence s, int start, int count,  
                int after)
        {  
        }  
          
        @Override  
        public void afterTextChanged(Editable s)
        {
    		bIsKeyDown = true;
    		
    		mHandler.sendEmptyMessageDelayed(MSG_TIME, 1 * 1000); 
         }  
     };
}
