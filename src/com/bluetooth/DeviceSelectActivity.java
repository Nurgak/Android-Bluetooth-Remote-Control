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
import java.util.Set;

import com.bluetooth.Device;
import com.bluetooth.DeviceListBaseAdapter;
import com.bluetooth.R;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.BroadcastReceiver;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.DialogInterface.OnCancelListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

/**
 * This class manages the initial Bluetooth device discovery and shows them in a
 * list. Paired devices and available devices are in different lists, available
 * devices also show the signal strength.
 */
public class DeviceSelectActivity extends Activity implements Handler.Callback
{
	private ArrayList<Device> devAvailableList, devPairedList;
	private DeviceListBaseAdapter devAvailableListAdapter, devPairedListAdapter;
	private ListView devAvailableListView, devPairedListView;
	private ProgressDialog connectionProgressDialog;
	private Button bFindDevices;
	private BluetoothAdapter bluetoothAdapter;
	private BluetoothRemoteControlApp appState;

	private static final String TAG = "DeviceSelect";

	private static final int ACTION_LIST = 0;
	private static final int BT_ENABLE = 1;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		// Request the spinner in upper right corner
		requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
		setContentView(R.layout.device_select);

		// Setup Bluetooth devices lists with custom rows
		devPairedListView = (ListView) findViewById(R.id.lvPairedDevices);
		devPairedList = new ArrayList<Device>();
		devPairedListAdapter = new DeviceListBaseAdapter(this, devPairedList);
		devPairedListView.setAdapter(devPairedListAdapter);
		devPairedListView.setOnItemClickListener(deviceClickListener);

		devAvailableListView = (ListView) findViewById(R.id.lvAvailableDevices);
		devAvailableList = new ArrayList<Device>();
		devAvailableListAdapter = new DeviceListBaseAdapter(this, devAvailableList);
		devAvailableListView.setAdapter(devAvailableListAdapter);
		devAvailableListView.setOnItemClickListener(deviceClickListener);

		appState = (BluetoothRemoteControlApp) getApplicationContext();

		bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		// Register a receiver to handle Bluetooth actions
		registerReceiver(Receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
		registerReceiver(Receiver, new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED));

		// Setup bottom button to search for devices
		bFindDevices = (Button) findViewById(R.id.bFindDevices);
		bFindDevices.setOnClickListener(new Button.OnClickListener()
		{
			public void onClick(View v)
			{
				startDiscovery();
			}
		});

		// Automatically start discovery the first time the application starts
		startDiscovery();
	}

	/**
	 * When a Bluetooth item is clicked this method tries to connect to it. It
	 * shows a dialog that informs the user it's trying to establish a
	 * connection.
	 */
	final OnItemClickListener deviceClickListener = new OnItemClickListener()
	{
		public void onItemClick(AdapterView<?> parent, View view, int position, long id)
		{
			// Cancel discovery because it's costly and we're about to connect
			bluetoothAdapter.cancelDiscovery();

			// Get the selected device
			Device device = (Device) parent.getItemAtPosition(position);

			// Show connection dialog and make so that the connection it can be canceled
			connectionProgressDialog = ProgressDialog.show(DeviceSelectActivity.this, "", "Establishing connection...", false, true);
			connectionProgressDialog.setOnCancelListener(new OnCancelListener()
			{
				public void onCancel(DialogInterface di)
				{
					// The user can back out at any moment
					connectionProgressDialog.dismiss();
					appState.disconnect();
					if(BluetoothRemoteControlApp.D) Log.i(TAG, "Canceled connection progress");
					return;
				}
			});

			// Try to connect to the selected device, once connected the handler will do the rest
			appState.connect(BluetoothAdapter.getDefaultAdapter().getRemoteDevice(device.getAddress()));
		}
	};

	/**
	 * When a message comes from either the Bluetooth activity (when enabling
	 * Bluetooth) or the child activity it's processed here.
	 * 
	 * @return
	 */
	public boolean handleMessage(Message msg)
	{
		// In case the connection dialog hasn't disappeared yet
		if(connectionProgressDialog != null)
		{
			connectionProgressDialog.dismiss();
		}

		switch(msg.what)
		{
		case BluetoothRemoteControlApp.MSG_OK:
			// The child activity ended gracefully
			break;
		case BluetoothRemoteControlApp.MSG_CANCEL:
			// The child activity did not end gracefully (connection lost, failed...)
			if(msg.obj != null)
			{
				// If a some text came with the message show in a toast 
				if(BluetoothRemoteControlApp.D) Log.i(TAG, "Message: " + msg.obj);
				Toast.makeText(DeviceSelectActivity.this, (String) msg.obj, Toast.LENGTH_SHORT).show();
			}
			break;
		case BluetoothRemoteControlApp.MSG_CONNECTED:
			// When connected to a device start the activity select
			if(BluetoothRemoteControlApp.D) Log.i(TAG, "Connection successful to " + msg.obj);
			startActivityForResult(new Intent(getApplicationContext(), ActionListActivity.class), ACTION_LIST);
			break;
		}
		return false;
	}

	/**
	 * Start discovering Bluetooth devices. It will check if Bluetooth is
	 * enabled and then disable the search button before searching for visible
	 * devices.
	 */
	public void startDiscovery()
	{
		// Prevent phones without Bluetooth from using this application
		if(!checkBlueToothState())
		{
			finish();
			return;
		}

		// Show search progress spinner
		setProgressBarIndeterminateVisibility(true);
		// Disable button
		bFindDevices.setText(R.string.searching);
		bFindDevices.setEnabled(false);

		// Remove title for available devices
		findViewById(R.id.tvAvailableDevices).setVisibility(View.GONE);

		devPairedList.clear();
		devPairedListAdapter.notifyDataSetChanged();

		// Show already paired devices in the upper list
		Set<BluetoothDevice> pairedDevices = bluetoothAdapter.getBondedDevices();
		if(pairedDevices.size() > 0)
		{
			findViewById(R.id.tvPairedDevices).setVisibility(View.VISIBLE);
			for(BluetoothDevice device : pairedDevices)
			{
				// Signal strength isn't available for paired devices
				devPairedList.add(new Device(device.getName(), device.getAddress(), (short) 0));
			}
			// Tell the list adapter that its data has changed so it would update itself
			devPairedListAdapter.notifyDataSetChanged();
		}

		devAvailableList.clear();
		devAvailableListAdapter.notifyDataSetChanged();

		bluetoothAdapter.startDiscovery();
	}

	// Add found device to the devices list
	private final BroadcastReceiver Receiver = new BroadcastReceiver()
	{
		@Override
		public void onReceive(Context context, Intent intent)
		{
			String action = intent.getAction();
			if(BluetoothDevice.ACTION_FOUND.equals(action))
			{
				// Found a device in range
				BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
				Device foundDevice = new Device(device.getName(), device.getAddress(), intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE));
				// If it's not a paired device add it to the list
				if(device.getBondState() != BluetoothDevice.BOND_BONDED)
				{
					devAvailableList.add(foundDevice);
					// Signal list content change
					devAvailableListAdapter.notifyDataSetChanged();
					// Make the available devices title visible
					findViewById(R.id.tvAvailableDevices).setVisibility(View.VISIBLE);
				}
			}
			else if(BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action))
			{
				// When finished (timeout) remove the progress indicator and re-enable button
				setProgressBarIndeterminateVisibility(false);
				bFindDevices.setText(R.string.search);
				bFindDevices.setEnabled(true);
			}
		}
	};

	/**
	 * This method tells the caller if the Bluetooth is enabled or if it even
	 * exists on the phone.
	 * 
	 * @return State of Bluetooth
	 */
	private boolean checkBlueToothState()
	{
		// Inform user that the phone does not have Bluetooth
		if(bluetoothAdapter == null)
		{
			Toast.makeText(getApplicationContext(), "Bluetooth not available.", Toast.LENGTH_SHORT).show();
			return false;
		}
		else if(!bluetoothAdapter.isEnabled())
		{
			startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), BT_ENABLE);
			return false;
		}
		return true;
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data)
	{
		super.onActivityResult(requestCode, resultCode, data);
		switch(requestCode)
		{
		case ACTION_LIST:
			// Send messages from the parent to the handler
			Message.obtain(new Handler(this), resultCode);
			break;
		case BT_ENABLE:
			// For the Bluetooth connection handle messages here
			if(!bluetoothAdapter.isEnabled())
			{
				Toast.makeText(getApplicationContext(), "Bluetooth must be enabled", Toast.LENGTH_SHORT).show();
			}
			else
			{
				startDiscovery();
			}
			break;
		}
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();

		// Make sure we're not doing discovery anymore
		if(bluetoothAdapter != null)
		{
			bluetoothAdapter.cancelDiscovery();
		}

		// Unregister broadcast listeners
		this.unregisterReceiver(Receiver);
	}

	@Override
	protected void onResume()
	{
		if(BluetoothRemoteControlApp.D) Log.i(TAG, "Set handler");
		appState.setActivityHandler(new Handler(this));
		super.onResume();
	}

	@Override
	protected void onPause()
	{
		bluetoothAdapter.cancelDiscovery();
		super.onPause();
	}
}