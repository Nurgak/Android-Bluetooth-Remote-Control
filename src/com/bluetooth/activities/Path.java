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

import com.bluetooth.BluetoothRemoteControlApp;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff.Mode;
import android.os.Handler;
import android.os.Message;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

/**
 * This class takes in the YU12 format camera preview data and processes it to
 * find the line and calculate the optimal path. It sends by the error back via
 * a handler.
 */
class Path
{
	private final Handler parentHandler;
	private final int width, height, undersample = 8;
	private final SurfaceHolder holder;
	private Canvas overlayCanvas;
	private int x, y, rx, startPixel, finishPixel, threshold, error, avgerage, pathFound;

	final private Paint red, blue, yellow;

	public Path(int canvasWidth, int canvasHeight, SurfaceView canvasView, Handler callbackHandler)
	{
		parentHandler = callbackHandler;
		width = canvasWidth / undersample;
		height = canvasHeight / undersample;
		holder = canvasView.getHolder();
		threshold = 60;

		// ICS base colors
		yellow = new Paint();
		yellow.setColor(Color.parseColor("#ffbb33"));
		yellow.setAntiAlias(false);
		yellow.setDither(false);
		yellow.setFilterBitmap(false);
		red = new Paint();
		red.setColor(Color.parseColor("#ff4444"));
		red.setAntiAlias(false);
		red.setDither(false);
		red.setFilterBitmap(false);
		blue = new Paint();
		blue.setColor(Color.parseColor("#33b5e5"));
		blue.setAntiAlias(false);
		blue.setDither(false);
		blue.setFilterBitmap(false);
	}

	public void setThreshold(int newValue)
	{
		threshold = newValue;
	}

	public void processFrame(byte[] data)
	{
		overlayCanvas = holder.lockCanvas();
		overlayCanvas.drawColor(0, Mode.CLEAR);

		// If this is still 0 by the end of the search the value cannot be used
		pathFound = 0;

		// Threshold the camera preview based on the set threshold value
		for(y = width - 1; y > 0; y--)
		{
			startPixel = -1;
			finishPixel = height + 1;

			// From left to right and right to left at the same time
			for(x = 0, rx = height - 1; x < height; x++, rx--)
			{
				// Right to left: find the first pixel that is under the threshold and mark it as start
				if((0xff & ((int) data[x * undersample * width * undersample + y * undersample])) - 16 < threshold)
				{
					if(startPixel == -1)
					{
						startPixel = x;
					}
					// Draw all the pixels under the threshold
					overlayCanvas.drawPoint(height - x, y, yellow);
				}

				// Left to right: find the first pixel that is under the threshold and mark it as end
				if((0xff & ((int) data[rx * undersample * width * undersample + y * undersample])) - 16 < threshold)
				{
					if(finishPixel == height + 1)
					{
						finishPixel = rx;
					}
				}
			}

			// Take average of start and finish pixel position
			avgerage = height - (startPixel + finishPixel) / 2;

			// Draw path pixels for the fun of it
			if(startPixel != -1 || finishPixel != height + 1)
			{
				overlayCanvas.drawPoint(avgerage, y, red);

				// Use the first pixel row for the error calculation
				if(y == width - 2)
				{
					error = (int) (100 * (2 * (float) avgerage / height - 1));
					pathFound = 1;
				}
			}
			else
			{
				break;
			}
		}

		// Send the error (the value we want) back to parent with a reclaimed message and also if the value is usable
		Message.obtain(parentHandler, BluetoothRemoteControlApp.MSG_1, error, pathFound).sendToTarget();

		holder.unlockCanvasAndPost(overlayCanvas);
	}
}