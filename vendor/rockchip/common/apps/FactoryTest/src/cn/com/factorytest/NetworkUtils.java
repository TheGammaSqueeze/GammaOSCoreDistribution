package cn.com.factorytest;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Iterator;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.LinkProperties;
import android.net.NetworkInfo;
import android.net.NetworkInfo.State;
//import android.app.SystemWriteManager;

public class NetworkUtils {

	private static final String eth_device_sysfs = "/sys/class/ethernet/linkspeed";

	public static String getMacAddress() {

		String strMacAddr = null;
		try {
			InetAddress ip = getLocalInetAddress();

			byte[] b = NetworkInterface.getByName("eth0").getHardwareAddress();
			StringBuffer buffer = new StringBuffer();
			for (int i = 0; i < b.length; i++) {
				if (i != 0) {
					buffer.append('-');
				}

				String str = Integer.toHexString(b[i] & 0xFF);
				buffer.append(str.length() == 1 ? 0 + str : str);
			}
			strMacAddr = buffer.toString().toUpperCase();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

		return strMacAddr;
	}

	private static InetAddress getLocalInetAddress() {
		InetAddress ip = null;
		try {
			Enumeration<NetworkInterface> en_netInterface = NetworkInterface
					.getNetworkInterfaces();
			while (en_netInterface.hasMoreElements()) {
				NetworkInterface ni = (NetworkInterface) en_netInterface
						.nextElement();
				Enumeration<InetAddress> en_ip = ni.getInetAddresses();
				while (en_ip.hasMoreElements()) {
					ip = en_ip.nextElement();
					if (!ip.isLoopbackAddress()
							&& ip.getHostAddress().indexOf(":") == -1)
						break;
					else
						ip = null;
				}

				if (ip != null) {
					break;
				}
			}
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return ip;
	}

	public static boolean isEthConnected(Context context) {
		ConnectivityManager connManager = (ConnectivityManager) context
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		State mEthState = connManager.getNetworkInfo(
				ConnectivityManager.TYPE_ETHERNET).getState();
		if (State.CONNECTED == mEthState) {
			return true;
		} else {
			return false;
		}
	}

	public static String int2ip(long ipInt) {
		StringBuilder sb = new StringBuilder();
		sb.append(ipInt & 0xFF).append(".");
		sb.append((ipInt >> 8) & 0xFF).append(".");
		sb.append((ipInt >> 16) & 0xFF).append(".");
		sb.append((ipInt >> 24) & 0xFF);
		return sb.toString();
	}

	public static boolean isIpAddress(String value) {
		if (null == value || value.length() == 0)
			return false;

		int start = 0;
		int end = value.indexOf('.');
		int numBlocks = 0;

		while (start < value.length()) {
			if (end == -1) {
				end = value.length();
			}

			try {
				int block = Integer.parseInt(value.substring(start, end));
				if ((block > 255) || (block < 0)) {
					return false;
				}
			} catch (NumberFormatException e) {
				return false;
			}

			numBlocks++;

			start = end + 1;
			end = value.indexOf('.', start);
		}
		return numBlocks == 4;
	}
/*
	public static boolean isEthDeviceAdded(Context c) {
		SystemWriteManager sw = (SystemWriteManager) c
				.getSystemService("system_write");
		String str = readSysFile(sw, eth_device_sysfs);
		if (str == null)
			return false;

		if (str.contains("unlink")) {
			return false;
		} else {
			return true;
		}
	}
*/
/*
	private static String readSysFile(SystemWriteManager sw, String path) {
		if (sw == null || path == null) {
			return null;
		}

		return sw.readSysfs(path);
	}
*/
	public static boolean isPppoeConnected(Context c) {
		ConnectivityManager connectivity = (ConnectivityManager) c
				.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo[] infos = connectivity.getAllNetworkInfo();
		for (NetworkInfo info : infos) {
			if (info.getTypeName().equals("pppoe")
					&& info.getState().toString().equals("CONNECTED")) {
				return true;
			}
		}

		return false;
	}
}
