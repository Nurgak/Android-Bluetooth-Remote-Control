/**
 * This program is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, either version 3 of the License, or (at your option) any later
 * version.
 * 
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License for more
 * details.
 * 
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <http://www.gnu.org/licenses/>.
 */

package com.bluetooth;

import java.util.ArrayList;

import com.bluetooth.R;

import android.content.Intent;
import android.os.Bundle;
import android.os.Message;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;

/**
 * This class lets the user select the way to interact with the robot. It's just
 * a list of items with a title, a small description of what the activity does
 * and the class name that will be started when the element gets clicked.
 */
public class ActionListActivity extends BluetoothActivity
{
	private ArrayList<Action> activityList = new ArrayList<Action>();
	private ListView lvActionList;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.action_select);

		lvActionList = (ListView) findViewById(R.id.lvActionList);

		// Remote control activities
		activityList.add(new Action("Accelerometer Control", "Control your robot by tilting the phone", "AccelerometerControl"));
		activityList.add(new Action("Touch Control", "Control robot's movements by touch", "TouchControl"));
		//activityList.add(new Action("Arrow Control", "Simplistic control with arrows", "ArrowControl"));
		activityList.add(new Action("Voice Control", "Control robot with oral instructions", "VoiceControl"));
		//activityList.add(new Action("Program", "Save and replay a set of instructions", "Program"));
		//activityList.add(new Action("Keypad", "Send numbers from 1 to 9 for custom actions", "Keypad"));
		activityList.add(new Action("Wi-Fi Control", "Use a computer to remotely control from a browser", "WiFiControl"));
		
		// Autonomous control
		activityList.add(new Action("Line Follower", "Use the phone's camera to follow a black line", "LineFollower"));
		activityList.add(new Action("Kill All Humans", "Use face detection to run down humans", "KillAllHumans"));
		
		// TODO: Organize this into better categories
		// Miscellaneous
		activityList.add(new Action("Send Data", "Send custom commands to robot", "SendData"));
		// Maybe put this one in the API
		//activityList.add(new Action("Transmit Data", "Transmit data over Wi-Fi via a GET requests", "TransmitData"));
		//activityList.add(new Action("Sound", "Make sounds by toggling the motor direction", "Sound"));
		//activityList.add(new Action("GPS Position", "Send the robot anywhere on earth", "GPSPosition"));
		//activityList.add(new Action("Motion Detection", "Use the camera to sense movement", "MotionDetection"));
		//activityList.add(new Action("Oscilloscope", "Use the ADC as an oscilloscope", "Oscilloscope"));
		//activityList.add(new Action("Function Generator", "Use the PWM as a square function generator", "FunctionGenerator"));

		// TODO: Make this API
		//activityList.add(new Action("API", "Send commands from device to Android", "API"));
		// Internet request (w,request : w,http://www.google.com?test)
		// - and send reply to device (w,reply : w,5)
		// call number (c,number : c,0000000000)
		// send sms (s,number,message : s,0000000000,Hello)
		// send e-mail (e,address,subject,message : e,me.myself@mymail.com,Hello,Hello world)
		// take picture and save (p)
		// take picture and send via email (q,email,subject,message : p,me.myself@mymail.com,Took a picture,Hello here's a picture)
		// take picture and send via mms (m,number : p,mms,0000000000)
		// take picture and upload (u,address : u,http://www.mywebsite.com)

		lvActionList.setAdapter(new ActionListBaseAdapter(this, activityList));
		lvActionList.setOnItemClickListener(new OnItemClickListener()
		{
			public void onItemClick(AdapterView<?> arg0, View v, int position, long id)
			{
				String activity = activityList.get(position).getClassName();

				try
				{
					// Start the selected activity and prevent quitting
					preventCancel = true;
					Class<?> activityClass = Class.forName("com.bluetooth.activities." + activity);
					Intent intent = new Intent(ActionListActivity.this, activityClass);
					startActivityForResult(intent, 0);
				}
				catch(ClassNotFoundException e)
				{
					e.printStackTrace();
				}
			}
		});
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		super.handleMessage(msg);
		// When a child activity returns it passes ok or cancel message
		if(msg.what == BluetoothRemoteControlApp.MSG_OK)
		{
			// When quitting an activity automatically reset the robot
			write("r");
		}
		return false;
	}

	@Override
	public void onBackPressed()
	{
		// When quitting the activity select reset and disconnect from the device
		disconnect();
		super.onBackPressed();
	}
}
