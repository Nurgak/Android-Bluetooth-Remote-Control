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

import java.io.IOException;

import com.bluetooth.BluetoothActivity;
import com.bluetooth.BluetoothRemoteControlApp;
import com.bluetooth.R;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.hardware.Camera;
import android.hardware.Camera.PreviewCallback;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Display;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;

/**
 * This class uses the camera to detect a highly contrasted black line on a
 * white surface in order to follow it. It actually only uses the first pixel
 * row on the bottom of the screen, predicting a line revealed to be impossible
 * as the line was lost when the track curvature was too high. To optimally
 * follow a line on any kind of robot a PID controller was added.
 * <p>
 * Additional commands such as setting the black/while threshold and maximum
 * speed are available so that the application could be used in different light
 * conditions.
 * <p>
 * In the future developments of this activity one could imagine implementing
 * simulated annealing to find the optimal PID controller constants.
 */
public class LineFollower extends BluetoothActivity implements SurfaceHolder.Callback, PreviewCallback
{
	// User interface elements
	private Camera camera;
	private SurfaceView svPreview, svOverlay;
	private SurfaceHolder previewHolder, overlayHolder;
	private TextView tvWheelLeft, tvWheelRight;
	private Path path;

	private Boolean following = false;
	private int maxSpeed, threshold = 60;

	private Dialog pidSettingsDialog;
	private float pid_kp, pid_ki, pid_kd;

	private float control, control_p, control_i, control_d;
	private int wheelLeft, wheelRight, lastError;

	private static byte[] previewData;

	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.line_follower);

		tvWheelLeft = (TextView) findViewById(R.id.tvWheelLeft);
		tvWheelRight = (TextView) findViewById(R.id.tvWheelRight);

		final Button bToggle = (Button) findViewById(R.id.bToggle);
		bToggle.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				following = !following;
				if(following)
				{
					// Focus the camera
					camera.autoFocus(null);
					// Reset all control values
					lastError = 0;
					control = 0;
					control_p = 0;
					control_i = 0;
					control_d = 0;
					bToggle.setText(R.string.stop);
				}
				else
				{
					write("r");
					bToggle.setText(R.string.start);
					tvWheelLeft.setText("");
					tvWheelRight.setText("");
				}
			}
		});

		SeekBar sbThreshold = (SeekBar) findViewById(R.id.sbThreshold);
		sbThreshold.setProgress(threshold);
		sbThreshold.setOnSeekBarChangeListener(new OnSeekBarChangeListener()
		{
			public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser)
			{
				path.setThreshold(progress);
			}

			public void onStartTrackingTouch(SeekBar arg0)
			{

			}

			public void onStopTrackingTouch(SeekBar arg0)
			{

			}
		});

		// Load last saved values or set defaults
		SharedPreferences lineFollowerPID = this.getSharedPreferences("lineFollowerPID", MODE_PRIVATE);
		pid_kp = lineFollowerPID.getFloat("kp", 1);
		pid_ki = lineFollowerPID.getFloat("ki", 0);
		pid_kd = lineFollowerPID.getFloat("kd", 0);
		maxSpeed = lineFollowerPID.getInt("maxSpeed", 50);

		pidSettingsDialog = new PIDSettings(LineFollower.this, pid_kp, pid_ki, pid_kd, maxSpeed, new Handler(this));

		svPreview = (SurfaceView) findViewById(R.id.svPreview);
		previewHolder = svPreview.getHolder();
		previewHolder.addCallback(this);
		previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);

		svOverlay = (SurfaceView) findViewById(R.id.svOverlay);
		overlayHolder = svOverlay.getHolder();
		overlayHolder.setFormat(PixelFormat.TRANSPARENT);
	}

	public void surfaceCreated(SurfaceHolder arg0)
	{
		Display display = getWindowManager().getDefaultDisplay();
		Point displaySize = new Point();
		display.getSize(displaySize);

		// Configure camera
		camera = Camera.open();
		Camera.Parameters parameters = camera.getParameters();

		// Set the closest preview size that fits the screen
		Camera.Size cameraSize = null;
		for(Camera.Size size : parameters.getSupportedPreviewSizes())
		{
			if(size.width <= displaySize.y && size.height <= displaySize.x)
			{
				if(cameraSize == null)
				{
					cameraSize = size;
				}
				else
				{
					// The ratio is not always the same so make sure the new size is actually bigger
					int resultArea = cameraSize.width * cameraSize.height;
					int newArea = size.width * size.height;

					if(newArea > resultArea)
					{
						cameraSize = size;
					}
				}
			}
		}

		overlayHolder.setFixedSize(cameraSize.height / 8, cameraSize.width / 8);

		parameters.setPreviewSize(cameraSize.width, cameraSize.height);
		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		camera.setParameters(parameters);

		try
		{
			// Set where the camera preview should be shown
			camera.setPreviewDisplay(previewHolder);
			// Show in portrait mode
			camera.setDisplayOrientation(90);
			camera.startPreview();
		}
		catch(IOException e)
		{
			e.printStackTrace();
			camera.stopPreview();
			camera.release();
		}

		// Every frame send image data to path processing thread
		path = new Path(cameraSize.width, cameraSize.height, svOverlay, new Handler(this));

		// Make only one buffer since we're going to work on a single image each time
		previewData = new byte[cameraSize.width * cameraSize.height * ImageFormat.getBitsPerPixel(ImageFormat.YV12) / 8];
		camera.addCallbackBuffer(previewData);

		camera.setPreviewCallbackWithBuffer(this);
	}

	public void surfaceDestroyed(SurfaceHolder arg0)
	{
		camera.setPreviewCallback(null);
		camera.stopPreview();
		camera.release();
		camera = null;
	}

	public void surfaceChanged(SurfaceHolder arg0, int arg1, int arg2, int arg3)
	{

	}

	@Override
	public boolean handleMessage(Message msg)
	{
		// Set the new speed using the data from PathThread
		if(msg.what == BluetoothRemoteControlApp.MSG_1)
		{
			// The preview buffer is removed from the callback when used, put it back after processing
			camera.addCallbackBuffer(previewData);

			// msg.arg1: Deviation from center (error) normalized from -100 to 100
			// msg.arg2: 1 if path has been found, 0 if not (stop)
			if(msg.arg2 == 1)
			{
				control_p = msg.arg1 * pid_kp;
				control_i = (control_i + msg.arg1) * pid_ki;
				control_d = (msg.arg1 - lastError) * pid_kd;
				lastError = msg.arg1;

				control = control_p + control_i + control_d;

				wheelLeft = maxSpeed + (int) control;
				wheelRight = maxSpeed - (int) control;
			}
			else
			{
				wheelLeft = 0;
				wheelRight = 0;
			}
			
			tvWheelLeft.setText("L: " + wheelLeft);
			tvWheelRight.setText("R: " + wheelRight);
			
			if(following)
			{
				write("s," + wheelLeft + "," + wheelRight);
			}
		}
		else if(msg.what == BluetoothRemoteControlApp.MSG_2)
		{
			// Set the new PID constant values
			float[] control = (float[]) msg.obj;
			pid_kp = control[0];
			pid_ki = control[1];
			pid_kd = control[2];
			maxSpeed = (int) control[3];

			// Save them for next time the activity is run
			SharedPreferences lineFollowerPID = this.getSharedPreferences("lineFollowerPID", MODE_PRIVATE);
			Editor edit = lineFollowerPID.edit();
			edit.putFloat("kp", pid_kp);
			edit.putFloat("ki", pid_ki);
			edit.putFloat("kd", pid_kd);
			edit.putInt("maxSpeed", maxSpeed);
			edit.commit();
		}
		return super.handleMessage(msg);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Add the settings icon in the ActionBar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.settings, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Show the settings dialog when settings button is clicked
		if(item.getItemId() == R.id.iSettings)
		{
			pidSettingsDialog.show();
		}
		return super.onOptionsItemSelected(item);
	}

	public void onPreviewFrame(final byte[] data, Camera camera)
	{
		// Process image in another thread to free the UI
		Runnable process = new Runnable()
		{
			public void run()
			{
				path.processFrame(previewData);
			}
		};
		process.run();
	}
}
