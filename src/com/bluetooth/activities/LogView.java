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
import android.text.Editable;
import android.text.TextWatcher;
import android.text.method.ScrollingMovementMethod;
import android.util.AttributeSet;
import android.view.Gravity;
import android.widget.TextView;

/**
 * This class is used in activities that show a data log to the user. It will
 * limit its data because an overflow might happen when too much data is shown.
 */
public class LogView extends TextView implements TextWatcher
{
	// Allow a maximum number of 30 lines
	private static final int maxLines = 30;
	private String data;

	public LogView(Context context)
	{
		super(context);
		init();
	}

	public LogView(Context context, AttributeSet attrs)
	{
		super(context, attrs);
		init();
	}

	public LogView(Context context, AttributeSet attrs, int defStyle)
	{
		super(context, attrs, defStyle);
		init();
	}

	private void init()
	{
		// When text is changed check for data length
		addTextChangedListener(this);
		// If the content is too long: scroll
		setMovementMethod(new ScrollingMovementMethod());
		// Make the text appear at the bottom
		setGravity(Gravity.BOTTOM);
	}

	public void afterTextChanged(Editable s)
	{
		// Limit lines
		data = getText().toString();
		if(data.split("\n").length > maxLines)
		{
			data = data.substring(data.indexOf("\n") + 1);
			// Replace all text with the same text minus the first line
			s.replace(0, length(), data);
		}
	}

	public void beforeTextChanged(CharSequence s, int start, int count, int after)
	{
		// Not used
	}

	public void onTextChanged(CharSequence s, int start, int before, int count)
	{
		// Not used
	}
}
