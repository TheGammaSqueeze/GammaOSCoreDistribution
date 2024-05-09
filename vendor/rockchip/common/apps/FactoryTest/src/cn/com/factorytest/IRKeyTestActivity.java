package cn.com.factorytest;

import java.util.HashMap;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;
import android.widget.TextView;
import java.io.FileReader;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;

public class IRKeyTestActivity extends Activity {
	
	  private static HashMap<Integer, Integer> keyAndIds = new HashMap();
	  private RelativeLayout layout;
	  private HashMap<Integer, Button> mapView = new HashMap();

	  static
	  {
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_DPAD_LEFT), Integer.valueOf(R.id.right));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_DPAD_DOWN), Integer.valueOf(R.id.up));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_DPAD_RIGHT), Integer.valueOf(R.id.left));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_DPAD_UP), Integer.valueOf(R.id.down));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_1), Integer.valueOf(R.id.button_a));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_2), Integer.valueOf(R.id.button_b));
	    //keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_BACK), Integer.valueOf(R.id.back));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_3), Integer.valueOf(R.id.button_x));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_4), Integer.valueOf(R.id.button_y));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_5), Integer.valueOf(R.id.va));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_6), Integer.valueOf(R.id.home));
	    keyAndIds.put(Integer.valueOf(KeyEvent.KEYCODE_7), Integer.valueOf(R.id.vs));
	  }

	public Button getCodeView(int paramInt)
	  {
	    Button localButton1 = (Button)this.mapView.get(Integer.valueOf(paramInt));
	    if (localButton1 != null)
	      return localButton1;
	    String str = KeyEvent.keyCodeToString(paramInt);
	    int i = this.layout.getChildCount();
	    for (int j = 0; ; j++)
	    {
	      if (j >= i)
	      {
	        Log.e("keytest", "not found " + str);
	        return null;
	      }
	      View localView = this.layout.getChildAt(j);
	      if ((localView instanceof Button))
	      {
	        Button localButton2 = (Button)localView;
	        if (str.contains(((Button)localView).getText()))
	        {
	          this.mapView.put(Integer.valueOf(paramInt), localButton2);
	          return localButton2;
	        }
	      }
	    }
	  }

	  public Button getViewByCode(int paramInt)
	  {
		if(!keyAndIds.containsKey(Integer.valueOf(paramInt)))
		{
			        return null;
		}
	    int i = ((Integer)keyAndIds.get(Integer.valueOf(paramInt))).intValue();
	    return (Button)this.layout.findViewById(i);
	  }

	  public void log(String paramString)
	  {
	    Log.i("keytest", paramString);
	  }
	  

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		// TODO Auto-generated method stub
        String pathname = "/sys/class/w25q128fw/key";
		try (FileReader reader = new FileReader(pathname);
			 BufferedReader br = new BufferedReader(reader)) {
			String line;
			while ((line = br.readLine()) != null) {
				int id = Integer.parseInt(line);
				log("  key id:" + id);
			}		
		} catch (IOException e) {
			e.printStackTrace();
		}		
		super.onCreate(savedInstanceState);
		setContentView(R.layout.keytest);
	    this.layout = ((RelativeLayout)findViewById(R.id.layout));
	}

	  public boolean onKeyDown(int paramInt, KeyEvent paramKeyEvent)
	  {
	    log("down:keyCode:" + paramInt + ",event=" + paramKeyEvent);
	    Button localButton = getViewByCode(paramInt);
	    if (localButton != null)
	      localButton.setBackgroundResource(R.drawable.key_down);
	    return true;
	  }

	  public boolean onKeyUp(int paramInt, KeyEvent paramKeyEvent)
	  {
		if(paramInt == KeyEvent.KEYCODE_6)
		{
				 log(" ok is finish this keytest acivity");
				 this.finish();
				 return true;
		}
	    log("  up:keyCode:" + paramInt + ",event=" + paramKeyEvent);
	    Button localButton = getViewByCode(paramInt);
	    if (localButton != null)
	      localButton.setBackgroundResource(R.drawable.key_up);
	    return true;
	  }
	
	@Override
	protected void onDestroy() {
		// TODO Auto-generated method stub
		super.onDestroy();
	}

	@Override
	protected void onResume() {
		// TODO Auto-generated method stub
		super.onResume();
	}

	
	
}
