package cn.com.factorytest;

import java.io.File;
import java.io.IOException;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

public class FactoryReceiver2 extends BroadcastReceiver{
	private static final String TAG = Tools.TAG;
	//检测U盘 udiskfile 启动产测apk
	private static final String udiskfile = "khadas_test.xml";
	private static final String ageing_udiskfile4 = "khadas_test_4.xml";
	private static final String ageing_udiskfile8 = "khadas_test_8.xml";
	private static final String ageing_udiskfile12 = "khadas_test_12.xml";
	private static final String ageing_udiskfile24 = "khadas_test_24.xml";
	private static final String rebootfile = "khadas_reboot.xml";
	private static final String rst_mcu_file = "khadas_rst_mcu.xml";
	public static int ageing_flag = 0;
	public static int ageing_time = 0;

	@Override
	public void onReceive(Context context, Intent intent) {
		// TODO Auto-generated method stub
		String action = intent.getAction();
		Log.e(TAG, "hlm3 action " + action);
		
		if (Intent.ACTION_BOOT_COMPLETED.equals(action)) {

			File file2 = new File("storage");		
			File[] fileList = file2.listFiles();
			
			for (int j = 0; j < fileList.length; j++) {           
				if (fileList[j].isDirectory()) {
					String path = "/storage/"+fileList[j].getName();  
					Log.e(TAG, "hlm path=" + path);		

					String rst_mcu_fullpath = path+"/"+rst_mcu_file;
					File rstfile = new File(rst_mcu_fullpath);
					if (rstfile.exists() && rstfile.isFile()) {
						Tools.writeFile("/sys/class/wol/rst_mcu", "0");
					}

					String rebootfullpath = path+"/"+rebootfile;
					File rebootfile = new File(rebootfullpath);
					if(rebootfile.exists() && rebootfile.isFile()){
						try {
							Thread.sleep(10000);
							Intent intent1 = new Intent(Intent.ACTION_REBOOT);
							intent1.putExtra("nowait", 1);
							intent1.putExtra("interval", 1);
							intent1.putExtra("window", 0);
							context.sendBroadcast(intent1);
						} catch (Exception e){
							e.printStackTrace();
						}
						return;
					}
					String fullpath = path+"/"+udiskfile;
					File file = new File(fullpath);
					 if(file.exists() && file.isFile()){
						 try {
							ageing_flag = 0;
							Log.e(TAG, "hlm Thread.sleep(2000) " + ageing_flag);
							Thread.sleep(20);
						 } catch (InterruptedException e) {
							 e.printStackTrace();
						 }
						 Intent i = new Intent();
						 i.setClassName("cn.com.factorytest", "cn.com.factorytest.MainActivity");
						 i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
						 Log.e(TAG, "hlm startActivity " + ageing_flag);
						 context.startActivity(i);
					 }
					 else{
						 String ageing_fullpath4 = path+"/"+ageing_udiskfile4;
						 File ageing_file4 = new File(ageing_fullpath4);
						  if(ageing_file4.exists() && ageing_file4.isFile()){
							  try {
								 ageing_flag = 1;
								 ageing_time = 4;
								 Log.e(TAG, "hlm Thread.sleep(2000) ageing_flag" + ageing_flag);
								 Thread.sleep(20);
							  } catch (InterruptedException e) {
								  e.printStackTrace();
							  }
							  Intent i = new Intent();
							  i.setClassName("cn.com.factorytest", "cn.com.factorytest.MainActivity");
							  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
							  Log.e(TAG, "hlm startActivity ageing_time" + ageing_time);
							  context.startActivity(i);
						  }
						  else{
							 String ageing_fullpath12 = path+"/"+ageing_udiskfile12;
							 File ageing_file12 = new File(ageing_fullpath12);
							  if(ageing_file12.exists() && ageing_file12.isFile()){
								  try {
									 ageing_flag = 1;
									 ageing_time = 12;
								  Log.e(TAG, "hlm Thread.sleep(2000) ageing_flag" + ageing_flag);
									 Thread.sleep(20);
								  } catch (InterruptedException e) {
									  e.printStackTrace();
								  }
								  Intent i = new Intent();
								  i.setClassName("cn.com.factorytest", "cn.com.factorytest.MainActivity");
								  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
								  Log.e(TAG, "hlm startActivity ageing_time" + ageing_time);
								  context.startActivity(i);
							  }
							  else{
								 String ageing_fullpath24 = path+"/"+ageing_udiskfile24;
								 File ageing_file24 = new File(ageing_fullpath24);
								  if(ageing_file24.exists() && ageing_file24.isFile()){
									  try {
										 ageing_flag = 1;
										 ageing_time = 24;
									  Log.e(TAG, "hlm Thread.sleep(2000) ageing_flag" + ageing_flag);
										 Thread.sleep(20);
									  } catch (InterruptedException e) {
										  e.printStackTrace();
									  }
									  Intent i = new Intent();
									  i.setClassName("cn.com.factorytest", "cn.com.factorytest.MainActivity");
									  i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
									  Log.e(TAG, "hlm startActivity ageing_time" + ageing_time);
									  context.startActivity(i);
								  }
								   else{
									  String ageing_fullpath8 = path+"/"+ageing_udiskfile8;
									  File ageing_file8 = new File(ageing_fullpath8);
									   if(ageing_file8.exists() && ageing_file8.isFile()){
										   try {
											  ageing_flag = 1;
											  ageing_time = 8;
										   Log.e(TAG, "hlm Thread.sleep(2000) ageing_flag" + ageing_flag);
											  Thread.sleep(20);
										   } catch (InterruptedException e) {
											   e.printStackTrace();
										   }
										   Intent i = new Intent();
										   i.setClassName("cn.com.factorytest", "cn.com.factorytest.MainActivity");
										   i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
										   Log.e(TAG, "hlm startActivity ageing_time" + ageing_time);
										   context.startActivity(i);
									   }
								  }
							 }
						 }
					 }				
				}
			}
		}
		
	}
	
}
