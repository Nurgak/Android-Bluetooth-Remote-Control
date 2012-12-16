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

package com.bluetooth.activities;

import com.bluetooth.BluetoothActivity;
import com.bluetooth.BluetoothRemoteControlApp;
import com.bluetooth.R;

import android.os.Bundle;
import android.os.Message;
import android.view.KeyEvent;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;

/**
 * This activity lets the user send absolutely any data to the robot and display
 * whatever the robot response, basically it's an echo.
 */
public class SendData extends BluetoothActivity
{
	private LogView tvData;
	private EditText etCommand;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		this.setContentView(R.layout.send_data);

		// Prepare the UI
		tvData = (LogView) findViewById(R.id.tvData);

		etCommand = (EditText) findViewById(R.id.etCommand);
		etCommand.setOnEditorActionListener(new TextView.OnEditorActionListener()
		{
			public boolean onEditorAction(TextView v, int actionId, KeyEvent event)
			{
				// When the enter button of the keyboard is pressed the message is sent
				if(actionId == EditorInfo.IME_ACTION_SEND)
				{
					// Sending a command clears the input
					String msg = etCommand.getText().toString();
					if(!msg.equals(""))
					{
						write(msg);
						etCommand.setText("");
					}
				}
				// Must return true or the keyboard will be hidden
				return true;
			}
		});
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		super.handleMessage(msg);
		switch(msg.what)
		{
		case BluetoothRemoteControlApp.MSG_READ:
			tvData.append("R: " + msg.obj + "\n");
			break;
		case BluetoothRemoteControlApp.MSG_WRITE:
			tvData.append("S: " + msg.obj + "\n");
			break;
		}
		return false;
	}
}
