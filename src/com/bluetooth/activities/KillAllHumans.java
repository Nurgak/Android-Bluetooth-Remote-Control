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
import java.util.Random;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.PixelFormat;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.hardware.Camera;
import android.hardware.Camera.Face;
import android.media.MediaPlayer;
import android.media.MediaPlayer.OnCompletionListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

import com.bluetooth.BluetoothActivity;
import com.bluetooth.BluetoothRemoteControlApp;
import com.bluetooth.R;

/**
 * This activity uses the camera's face detection feature to detect human faces
 * and then charge towards them in order to kill (make the face stop looking
 * like a human one). Once can easily see the potential in up scaling the mobile
 * platform and adding some water guns/circular saws/machine guns.
 * <p>
 * This activity also lets the user enable sound effects borrowed from the
 * loving the turret gun from the popular Valve game named Portal.
 */
public class KillAllHumans extends BluetoothActivity implements SurfaceHolder.Callback, Camera.FaceDetectionListener
{
	private Camera camera;
	private SurfaceView svPreview, svOverlay;
	private SurfaceHolder previewHolder, overlayHolder;
	private TextView tvInfo, tvWheelLeft, tvWheelRight;
	private MenuItem soundMenu;
	private Button bToggle;
	private Paint red, textPaint;
	private Random randomInt = new Random();

	private MediaPlayer mediaPlayer;
	private String[] sleep = {"good_bye", "nap_time", "shutting_down", "sleep_mode"};
	private String[] wakeup = {"activated"};
	private String[] search = {"anyone_there", "are_you_still_there", "searching"};
	private String[] kill = {"i_see_you", "target_aquired", "there_you_are"};

	private static final int SLEEP = 0;
	private static final int WAKEUP = 1;
	private static final int SEARCH = 2;
	private static final int KILL = 3;

	private Dialog pidSettingsDialog;
	private int targets, state, maxSpeed;
	private String[] actions = {"Sleep", "Wakeup", "Search", "Kill"};
	private boolean previewing, soundEnabled;

	private float pid_kp, pid_ki, pid_kd;
	private float control, control_p, control_i, control_d;
	private int i, left, top, error, wheelLeft, wheelRight, lastError;

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.kill_all_humans);

		svPreview = (SurfaceView) findViewById(R.id.svPreview);
		svOverlay = (SurfaceView) findViewById(R.id.svOverlay);

		tvInfo = (TextView) findViewById(R.id.tvInfo);
		tvWheelLeft = (TextView) findViewById(R.id.tvWheelLeft);
		tvWheelRight = (TextView) findViewById(R.id.tvWheelRight);

		bToggle = (Button) findViewById(R.id.bToggle);
		bToggle.setOnClickListener(new OnClickListener()
		{
			public void onClick(View v)
			{
				if(state == SLEEP)
				{
					lastError = 0;
					control = 0;
					control_p = 0;
					control_i = 0;
					control_d = 0;
					setState(WAKEUP);
					camera.autoFocus(null);
					bToggle.setText(R.string.stop);
				}
				else
				{
					write("s,0,0");
					targets = 0;
					setState(SLEEP);
					bToggle.setText(R.string.start);
				}
			}
		});
		// Get saved PID values
		SharedPreferences killAllHumansPID = this.getSharedPreferences("killAllHumansPID", MODE_PRIVATE);
		pid_kp = killAllHumansPID.getFloat("kp", 1);
		pid_ki = killAllHumansPID.getFloat("ki", 0);
		pid_kd = killAllHumansPID.getFloat("kd", 0);
		maxSpeed = killAllHumansPID.getInt("maxSpeed", 50);

		pidSettingsDialog = new PIDSettings(KillAllHumans.this, pid_kp, pid_ki, pid_kd, maxSpeed, new Handler(this));

		red = new Paint();
		red.setColor(Color.RED);
		textPaint = new Paint();
		textPaint.setColor(Color.RED);
		textPaint.setTextSize(20);
		textPaint.setTextAlign(Align.CENTER);

		previewHolder = svPreview.getHolder();
		previewHolder.addCallback(this);
		previewHolder.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);
		overlayHolder = svOverlay.getHolder();
		overlayHolder.setFormat(PixelFormat.TRANSPARENT);
	}

	@Override
	public boolean handleMessage(Message msg)
	{
		if(msg.what == BluetoothRemoteControlApp.MSG_2)
		{
			// Set the new PID constant values
			float[] control = (float[]) msg.obj;
			pid_kp = control[0];
			pid_ki = control[1];
			pid_kd = control[2];
			maxSpeed = (int) control[3];

			// Save them for next time the activity is run
			SharedPreferences killAllHumansPID = this.getSharedPreferences("killAllHumansPID", MODE_PRIVATE);
			Editor edit = killAllHumansPID.edit();
			edit.putFloat("kp", pid_kp);
			edit.putFloat("ki", pid_ki);
			edit.putFloat("kd", pid_kd);
			edit.putInt("maxSpeed", maxSpeed);
			edit.commit();
		}
		return super.handleMessage(msg);
	}

	@Override
	protected void onResume()
	{
		super.onResume();
		state = SLEEP;
		targets = 0;
		soundEnabled = false;
	}

	public void surfaceCreated(SurfaceHolder arg0)
	{
		camera = Camera.open();
		camera.setDisplayOrientation(90);
		camera.setFaceDetectionListener(this);
		try
		{
			camera.setPreviewDisplay(previewHolder);
		}
		catch(IOException e)
		{
			e.printStackTrace();
		}
	}

	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height)
	{
		Camera.Parameters parameters = camera.getParameters();
		Camera.Size result = null;
		for(Camera.Size size : parameters.getSupportedPreviewSizes())
		{
			if(size.width <= width && size.height <= height)
			{
				if(result == null)
				{
					result = size;
				}
				else
				{
					int resultArea = result.width * result.height;
					int newArea = size.width * size.height;

					if(newArea > resultArea)
					{
						result = size;
					}
				}
			}
		}
		Camera.Size size = result;
		parameters.setPreviewSize(size.width, size.height);
		parameters.setFocusMode(Camera.Parameters.FOCUS_MODE_AUTO);
		camera.setParameters(parameters);
		camera.startPreview();
		previewing = true;
		camera.startFaceDetection();
	}

	public void surfaceDestroyed(SurfaceHolder sh)
	{
		previewing = false;
		camera.stopFaceDetection();
		camera.stopPreview();
		camera.release();
		camera = null;
	}

	public void onFaceDetection(Face[] faces, Camera camera)
	{
		if(previewing == false)
		{
			return;
		}

		Canvas canvas = overlayHolder.lockCanvas();
		canvas.drawColor(0, Mode.CLEAR);
		if(state != SLEEP)
		{
			targets = faces.length;
			if(targets > 0)
			{
				setState(KILL);

				for(i = 0; i < targets; i++)
				{
					Rect face = faces[i].rect;

					left = canvas.getWidth() - (face.centerY() + 1000) * canvas.getWidth() / 2000;
					top = (face.centerX() + 1000) * canvas.getHeight() / 2000;
					// Write "target" over each human face
					canvas.drawText("Target", left, top, textPaint);

					if(i == 0)
					{
						// Attack the first detected face
						error = (int) (100 * (2 * (float) left / canvas.getWidth() - 1));
						control_p = error * pid_kp;
						control_i = (control_i + error) * pid_ki;
						control_d = (error - lastError) * pid_kd;
						lastError = error;

						control = control_p + control_i + control_d;

						wheelLeft = maxSpeed + (int) control;
						wheelRight = maxSpeed - (int) control;

						write("s," + wheelLeft + "," + wheelRight);
						tvWheelLeft.setText("L: " + wheelLeft);
						tvWheelRight.setText("R: " + wheelRight);
					}
				}
			}
			else if(state != SEARCH)
			{
				write("r");
				setState(SEARCH);
			}
		}
		overlayHolder.unlockCanvasAndPost(canvas);

		tvInfo.setText("Targets: " + targets + "\nAction: " + actions[state]);
	}

	/**
	 * The state change is mostly serves to play the sound effects when enabled.
	 * 
	 * @param newState
	 */
	private void setState(final int newState)
	{
		if(soundEnabled && mediaPlayer == null && state != newState)
		{
			String soundName = null;
			switch(newState)
			{
			case SLEEP:
				soundName = sleep[randomInt.nextInt(sleep.length)];
				break;
			case WAKEUP:
				soundName = wakeup[randomInt.nextInt(wakeup.length)];
				break;
			case SEARCH:
				soundName = search[randomInt.nextInt(search.length)];
				break;
			case KILL:
				soundName = kill[randomInt.nextInt(kill.length)];
				break;
			}

			mediaPlayer = MediaPlayer.create(getApplicationContext(), getResources().getIdentifier(soundName, "raw", this.getPackageName()));
			mediaPlayer.setOnCompletionListener(new OnCompletionListener()
			{
				public void onCompletion(MediaPlayer mp)
				{
					// Once the sound has finished a new one can be played
					mediaPlayer.release();
					mediaPlayer = null;
				}
			});
			mediaPlayer.start();
		}

		state = newState;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		// Add the settings icon in the ActionBar
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.sound, menu);
		// Save reference to toggle icon
		soundMenu = menu.findItem(R.id.iSound);
		inflater.inflate(R.menu.settings, menu);
		return super.onCreateOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		// Show the settings dialog when settings button is clicked
		switch(item.getItemId())
		{
		case R.id.iSound:
			soundEnabled = !soundEnabled;
			if(soundEnabled)
			{
				soundMenu.setIcon(R.drawable.sound_off);
			}
			else
			{
				soundMenu.setIcon(R.drawable.sound_on);
			}
			break;
		case R.id.iSettings:
			pidSettingsDialog.show();
			break;
		}
		return super.onOptionsItemSelected(item);
	}
}
