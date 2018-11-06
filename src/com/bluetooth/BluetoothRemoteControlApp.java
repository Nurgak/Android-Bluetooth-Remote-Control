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

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.util.UUID;
import android.app.Application;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.os.Handler;
import android.util.Log;

/**
 * This class does all the work for setting up and managing Bluetooth
 * connections with other devices. It has a thread for connecting with a device,
 * a thread for performing data transmissions when connected and a timeout
 * thread that cancels the connection when too much time has passed since last
 * communication.
 * <p>
 * The application is built around this Bluetooth managing class, where
 * different activities can use it to send and receive data by using the
 * setActivityHandler() function to set themselves as the current connection
 * user.
 * <p>
 * If the connection is lost/broken/has failed/disconnected, the current
 * activity that is using it (that has set activityHander) should finish (a
 * message is dispatched to the current activityHander Handler). Then the
 * application will start with the Bluetooth device activity over again.
 */
public class BluetoothRemoteControlApp extends Application
{
	private final static String TAG = "Blueberry";
	// Debug flag
	public final static boolean D = true;

	// Time between sending the idle filler to confirm communication, must be smaller than the timeout constant.
	private final int minCommInterval = 900;
	// Time after which the communication is deemed dead
	private final int timeout = 3000;
	private long lastComm;

	// Member fields
	private BluetoothThread bluetoothThread;
	private TimeoutThread timeoutThread;
	private Handler activityHandler;
	private int state;
	private boolean busy, stoppingConnection;

	// Constants to indicate message contents
	public static final int MSG_OK = 0;
	public static final int MSG_READ = 1;
	public static final int MSG_WRITE = 2;
	public static final int MSG_CANCEL = 3;
	public static final int MSG_CONNECTED = 4;

	// General purpose constants to be used inside activities as callback values
	public static final int MSG_1 = 10;
	public static final int MSG_2 = 11;
	public static final int MSG_3 = 12;

	// Constants that indicate the current connection state
	private static final int STATE_NONE = 0;
	private static final int STATE_CONNECTING = 1;
	private static final int STATE_CONNECTED = 2;

	/**
	 * Constructor. Prepares a new Bluetooth session.
	 */
	public BluetoothRemoteControlApp()
	{
		state = STATE_NONE;
		activityHandler = null;
	}

	/**
	 * Sets the current active activity hander so messages could be sent.
	 * 
	 * @param handler
	 *            The current activity hander
	 */
	public void setActivityHandler(Handler handler)
	{
		activityHandler = handler;
	}

	/**
	 * Sends a message to the current activity registered to the activityHandler
	 * variable.
	 * 
	 * @param type
	 *            Type of message, use the public MSG_* constants
	 * @param value
	 *            Optional object to attach to message
	 */
	private synchronized void sendMessage(int type, Object value)
	{
		// It might happen that there's no activity handler, but here it doesn't prevent application work flow
		if(activityHandler != null)
		{
			activityHandler.obtainMessage(type, value).sendToTarget();
		}
	}

	/**
	 * Set the current state of the chat connection
	 * 
	 * @param newState
	 *            An integer defining the new connection state
	 */
	private synchronized void setState(int newState)
	{
		if(D)
			Log.i(TAG, "Connection status: " + state + " -> " + newState);
		state = newState;
	}

	/**
	 * Updates the communication time counter, use with the timeout thread to
	 * check for broken connection.
	 */
	private synchronized void updateLastComm()
	{
		lastComm = System.currentTimeMillis();
	}

	/**
	 * Start the ConnectThread to initiate a connection to a remote device.
	 * 
	 * @param device
	 *            The BluetoothDevice to connect
	 */
	public synchronized void connect(BluetoothDevice device)
	{
		if(D)
			Log.i(TAG, "Connecting to " + device.getName());
		stoppingConnection = false;
		busy = false;

		// Cancel any thread currently running a connection
		if(bluetoothThread != null)
		{
			bluetoothThread.cancel();
			bluetoothThread = null;
		}

		setState(STATE_CONNECTING);

		// Start the thread to connect with the given device
		bluetoothThread = new BluetoothThread(device);
		bluetoothThread.start();

		// Start the timeout thread to check the connecting status
		timeoutThread = new TimeoutThread();
		timeoutThread.start();
	}

	/**
	 * This thread runs during a connection with a remote device. It handles the
	 * initial connection and all incoming and outgoing transmissions.
	 */
	private class BluetoothThread extends Thread
	{
		private BluetoothSocket socket;
		private InputStream inStream;
		private OutputStream outStream;

		public BluetoothThread(BluetoothDevice device)
		{
			try
			{
				// General purpose UUID
				socket = device.createInsecureRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
				if(D)
					Log.i(TAG, "Creating to socket");
			}
			catch(IOException e) {
				if (D)
					Log.e(TAG, "Could not create socket.");
				e.printStackTrace();
				disconnect();
			}
		}
		public void run()
		{
			try
			{
				if(D)
					Log.i(TAG, "Connecting to socket");
				socket.connect(); // Blocking function, needs the timeout
			}
			catch(IOException e) {
				if (D)
					Log.e(TAG, "Could not connect to socket. Retrying.");
				e.printStackTrace();
				try
				{
					// workaround for bluetooth 4.0 https://stackoverflow.com/questions/18657427
					Class<?> clazz = socket.getRemoteDevice().getClass();
					Class<?>[] paramTypes = new Class<?>[] {Integer.TYPE};

					Method m = clazz.getMethod("createRfcommSocket", paramTypes);
					Object[] params = new Object[] {Integer.valueOf(1)};

					socket = (BluetoothSocket) m.invoke(socket.getRemoteDevice(), params);
					socket.connect();
				}
				catch(IOException e1)
				{
					if(D)
						Log.e(TAG, "Could not create fallback socket");
					e1.printStackTrace();
					disconnect();
					return;
				}
				catch(java.lang.NoSuchMethodException e1)
				{
					if(D)
						Log.e(TAG, "Backup socket reflection failed. Could not create socket.");
					e1.printStackTrace();
					disconnect();
					return;
				}
				catch(java.lang.IllegalAccessException e1)
				{
					if(D)
						Log.e(TAG, "Backup socket reflection failed. Could not create socket");
					e1.printStackTrace();
					disconnect();
					return;
				}
				catch(java.lang.reflect.InvocationTargetException e1)
				{
					if(D)
						Log.e(TAG, "Backup socket reflection failed. Could not create socket");
					e1.printStackTrace();
					disconnect();
					return;
				}
			}

			// Connected
			setState(STATE_CONNECTED);
			updateLastComm();
			// Send message to activity to inform of success
			sendMessage(MSG_CONNECTED, null);

			// Get the BluetoothSocket input and output streams
			try
			{
				inStream = socket.getInputStream();
				outStream = socket.getOutputStream();
			}
			catch(IOException e)
			{
				// Failed to get the streams
				disconnect();
				e.printStackTrace();
				return;
			}

			byte[] buffer = new byte[1024];
			byte ch;
			int bytes;
			String input;

			// Keep listening to the InputStream while connected
			while(true)
			{
				try
				{
					// Make a packet, use \n (new line or NL) as packet end
					// println() used in Arduino code adds \r\n to the end of the stream
					bytes = 0;
					while((ch = (byte) inStream.read()) != '\n')
					{
						buffer[bytes++] = ch;
					}
					// Prevent read errors (if you mess enough with it)
					if(bytes > 0)
					{
						// The carriage return (\r) character has to be removed
						input = new String(buffer, "UTF-8").substring(0, bytes - 1);

						if(D)
							Log.v(TAG, "Read: " + input);

						// Empty character is considered as a filler to keep the connection alive, don't forward that to the activity
						if(!input.equals("0"))
						{
							// Send the obtained bytes to the UI Activity if any
							sendMessage(MSG_READ, input);
						}
					}
					busy = false;
					// Update last communication time to prevent timeout
					updateLastComm();

				}
				catch(IOException e)
				{
					// read() will inevitably throw an error, even when just disconnecting
					if(!stoppingConnection)
					{
						if(D)
							Log.e(TAG, "Failed to read");
						e.printStackTrace();
						disconnect();
					}
					break;
				}
			}
		}

		public boolean write(String out)
		{
			if(outStream == null)
			{
				return false;
			}

			if(D)
				Log.v(TAG, "Write: " + out);
			try
			{
				if(out != null)
				{
					// Show sent message to the active activity
					sendMessage(MSG_WRITE, out);
					outStream.write(out.getBytes());
				}
				else
				{
					// This is a special case for the filler
					outStream.write(0);
				}
				// End packet with a new line
				outStream.write('\n');
				return true;
			}
			catch(IOException e)
			{
				e.printStackTrace();
			}
			return false;
		}

		public void cancel()
		{
			try
			{
				if(inStream != null)
				{
					inStream.close();
				}
				if(outStream != null)
				{
					outStream.close();
				}
				if(socket != null)
				{
					socket.close();
				}
			}
			catch(IOException e)
			{
				if(D)
					Log.e(TAG, "Couldn't cancel bluetoothThread");
				e.printStackTrace();
			}
		}
	}

	/**
	 * Thread that checks communication status every 50 milliseconds. Used to
	 * make sure the communication is and stays alive.
	 */
	private class TimeoutThread extends Thread
	{
		public TimeoutThread()
		{
			// Set the time before first check
			if(D)
				Log.i(TAG, "Started timeout thread");
			updateLastComm();
		}

		public void run()
		{
			while(state == STATE_CONNECTING || state == STATE_CONNECTED)
			{
				// I'm not sure that it's needed here, but it works
				synchronized(BluetoothRemoteControlApp.this)
				{
					// Filler hash to confirm communication with device when idle
					if(System.currentTimeMillis() - lastComm > minCommInterval && !busy && state == STATE_CONNECTED)
					{
						write(null);
					}

					// Communication timed out. Extra big when connecting.
					if(System.currentTimeMillis() - lastComm > (state == STATE_CONNECTED ? timeout : timeout*4))
					{
						if(D)
							Log.e(TAG, "Timeout");
						disconnect();
						break;
					}
				}

				// This thread should not run all the time
				try
				{
					Thread.sleep(50);
				}
				catch(InterruptedException e)
				{
					e.printStackTrace();
				}
			}
		}
	}

	/**
	 * This method sends data to the Bluetooth device in an unsynchronized
	 * manner, actually it calls the write() method inside the connected thread,
	 * but it also makes sure the device is not busy. If "r" is sent (reset
	 * flag) it will pass all flags and will be sent even if the device is busy.
	 * 
	 * @param out
	 *            String to send to the Bluetooth device
	 * @return Success of failure to write
	 */
	public boolean write(String out)
	{
		// The device hasn't finished processing last command, reset commands ("r") it always get sent
		if(busy && !out.equals(out))
		{
			if(D)
				Log.v(TAG, "Busy");
			return false;
		}
		busy = true;

		// Create temporary object
		BluetoothThread r;
		// Synchronize a copy of the BluetoothThread
		synchronized(this)
		{
			// Make sure the connection is live
			if(state != STATE_CONNECTED)
			{
				return false;
			}
			r = bluetoothThread;
		}
		// Perform the write unsynchronized
		return r.write(out);
	}

	/**
	 * Stop all threads
	 */
	public synchronized void disconnect()
	{
		// Do not stop twice
		if(!stoppingConnection)
		{
			stoppingConnection = true;
			if(D)
				Log.i(TAG, "Stop");
			if(bluetoothThread != null)
			{
				bluetoothThread.cancel();
				bluetoothThread = null;
			}
			setState(STATE_NONE);
			sendMessage(MSG_CANCEL, "Connection ended");
		}
	}
}
