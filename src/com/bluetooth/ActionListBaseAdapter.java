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

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * Base adapter for the action list, uses custom rows to show a title and a
 * description. When an item is clicked the class attached to the element is
 * started.
 */
public class ActionListBaseAdapter extends BaseAdapter
{
	private static ArrayList<Action> actionArrayList;

	private LayoutInflater mInflater;

	public ActionListBaseAdapter(Context context, ArrayList<Action> results)
	{
		actionArrayList = results;
		mInflater = LayoutInflater.from(context);
	}

	public int getCount()
	{
		return actionArrayList.size();
	}

	public Object getItem(int position)
	{
		return actionArrayList.get(position);
	}

	public long getItemId(int position)
	{
		return position;
	}

	public View getView(int position, View convertView, ViewGroup parent)
	{
		ViewHolder holder;

		if(convertView == null)
		{
			convertView = mInflater.inflate(R.layout.action_row_view, null);
			holder = new ViewHolder();
			holder.tvAction = (TextView) convertView.findViewById(R.id.tvAction);
			holder.tvDescription = (TextView) convertView.findViewById(R.id.tvDescription);

			convertView.setTag(holder);
		}
		else
		{
			holder = (ViewHolder) convertView.getTag();
		}

		holder.tvAction.setText(actionArrayList.get(position).getAction());
		holder.tvDescription.setText(actionArrayList.get(position).getDescripiton());

		return convertView;
	}

	static class ViewHolder
	{
		TextView tvAction;
		TextView tvDescription;
	}
}
