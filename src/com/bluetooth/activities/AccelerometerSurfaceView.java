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

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff.Mode;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.view.SurfaceHolder;
import android.view.SurfaceView;

public class AccelerometerSurfaceView extends SurfaceView implements Runnable
{
	private Thread thread = null;
	private SurfaceHolder surfaceHolder;

	private volatile boolean running = false;
	private PointF speedVector = new PointF(0, 0);
	private float mag;

	private Paint paint = new Paint();

	public AccelerometerSurfaceView(Context context)
	{
		super(context);
		surfaceHolder = getHolder();
	}

	public AccelerometerSurfaceView(Context context, AttributeSet attributeSet)
	{
		super(context, attributeSet);
		surfaceHolder = getHolder();
	}

	public AccelerometerSurfaceView(Context context, AttributeSet attributeSet, int defStyle)
	{
		super(context, attributeSet, defStyle);
		surfaceHolder = getHolder();
	}

	public void setVector(float x, float y)
	{
		// Normalize acceleration vector to 1
		speedVector.x = x / 5;
		speedVector.y = y / 5;
		if((mag = speedVector.x * speedVector.x + speedVector.y * speedVector.y) > 1)
		{
			mag = FloatMath.sqrt(mag);
			speedVector.x /= mag;
			speedVector.y /= mag;
		}
	}

	public void onResumeMySurfaceView()
	{
		running = true;
		thread = new Thread(this);
		thread.start();
	}

	public void onPauseMySurfaceView()
	{
		boolean retry = true;
		running = false;
		while(retry)
		{
			try
			{
				thread.join();
				retry = false;
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}

	public void run()
	{
		paint.setStyle(Paint.Style.STROKE);
		paint.setStrokeWidth(10);
		paint.setStrokeCap(Paint.Cap.ROUND);
		paint.setColor(0xffffffff);

		while(running)
		{
			if(surfaceHolder.getSurface().isValid())
			{
				Canvas canvas = surfaceHolder.lockCanvas();
				canvas.drawColor(0, Mode.CLEAR);
				if(mag > 0.001)
				{
					canvas.drawLine(this.getWidth() / 2, this.getHeight() / 2, this.getWidth() / 2 * (1 - speedVector.x), this.getHeight() / 2 + this.getWidth() / 2 * speedVector.y, paint);
				}

				surfaceHolder.unlockCanvasAndPost(canvas);
			}

			try
			{
				// 1/40ms = 25Hz
				Thread.sleep(40);
			}
			catch(InterruptedException e)
			{
				e.printStackTrace();
			}
		}
	}
}