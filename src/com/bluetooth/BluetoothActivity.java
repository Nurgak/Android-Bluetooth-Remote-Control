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

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.MenuItem;

/**
 * This class is used to build different Bluetooth activities for the robot to
 * interact with. It contains all the necessary to deal with communication, the
 * front end and activity itself is left for the class extending it.
 */
public class BluetoothActivity extends Activity implements Handler.Callback
{
	private static BluetoothRemoteControlApp appState;
	// When launching a new activity and this one stops it doesn't mean something bad (no connection loss)
	protected boolean preventCancel;
	private static String TAG;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		// Launched when the activity is created
		super.onCreate(savedInstanceState);
		getActionBar().setDisplayHomeAsUpEnabled(true);
		appState = (BluetoothRemoteControlApp) getApplicationContext();
	}

	protected boolean write(String message)
	{
		// Send command to the Bluetooth device
		return appState.write(message);
	}

	protected void disconnect()
	{
		// Disconnect from the Bluetooth device
		if(BluetoothRemoteControlApp.D) Log.i(TAG, "Connection end request");
		appState.disconnect();
	}

	public boolean handleMessage(Message msg)
	{
		switch(msg.what)
		{
		case BluetoothRemoteControlApp.MSG_OK:
			// When a child activity returns safely
			if(BluetoothRemoteControlApp.D) Log.i(TAG, "Result of child activity OK");
			break;
		case BluetoothRemoteControlApp.MSG_CANCEL:
			// When a child activity returns after being canceled (ex: if the connection is lost) cancel this activity
			if(BluetoothRemoteControlApp.D) Log.e(TAG, "Got canceled");
			setResult(BluetoothRemoteControlApp.MSG_CANCEL, new Intent());
			finish();
			break;
		}
		return false;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		// Send activity result messages to the handler
		Message.obtain(new Handler(this), resultCode).sendToTarget();
	}

	@Override
	protected void onResume()
	{
		// This is called when the activity resumes
		TAG = getLocalClassName();
		if(BluetoothRemoteControlApp.D) Log.i(TAG, "Set handler");
		// Set the handler to receive messages from the main application class
		appState.setActivityHandler(new Handler(this));
		preventCancel = false;
		super.onResume();
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// When the user clicks the application icon on the top left
		if(item.getItemId() == android.R.id.home)
		{
			// Behave as if the back button was clicked
			onBackPressed();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onBackPressed()
	{
		super.onBackPressed();
		// Pressing the back button quits the activity and informs the parent activity
		if(BluetoothRemoteControlApp.D) Log.i(TAG, "Back pressed");
		setResult(BluetoothRemoteControlApp.MSG_OK, new Intent());
		finish();
	}
	
	@Override
	public void finish()
	{
		// Remove the handler from the main application class
		appState.setActivityHandler(null);
		super.finish();
	}

	@Override
	protected void onPause()
	{
		// Pausing an activity isn't allowed, unless it has been prevented
		if(!preventCancel)
		{
			// Tell itself to cancel
			Message.obtain(new Handler(this), BluetoothRemoteControlApp.MSG_CANCEL).sendToTarget();
		}
		super.onPause();
	}
}
