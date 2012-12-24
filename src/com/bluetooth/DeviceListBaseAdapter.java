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

import com.bluetooth.R;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

/**
 * Device list base adapter to show the devices in a custom ListView.
 */
public class DeviceListBaseAdapter extends BaseAdapter
{
	private ArrayList<Device> deviceArrayList;

	private LayoutInflater mInflater;

	public DeviceListBaseAdapter(Context context, ArrayList<Device> results)
	{
		deviceArrayList = results;
		mInflater = LayoutInflater.from(context);
	}

	public int getCount()
	{
		return deviceArrayList.size();
	}

	public Object getItem(int position)
	{
		return deviceArrayList.get(position);
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
			convertView = mInflater.inflate(R.layout.device_row_view, null);
			holder = new ViewHolder();
			holder.tvName = (TextView) convertView.findViewById(R.id.tvName);
			holder.tvAddress = (TextView) convertView.findViewById(R.id.tvAddress);
			holder.tvSignal = (TextView) convertView.findViewById(R.id.tvSignal);

			convertView.setTag(holder);
		}
		else
		{
			holder = (ViewHolder) convertView.getTag();
		}

		holder.tvName.setText(deviceArrayList.get(position).getName());
		holder.tvAddress.setText(deviceArrayList.get(position).getAddress());
		if(!deviceArrayList.get(position).getSignal().equals("0"))
		{
			holder.tvSignal.setText(deviceArrayList.get(position).getSignal() + "dBm");
		}

		return convertView;
	}

	static class ViewHolder
	{
		TextView tvName;
		TextView tvAddress;
		TextView tvSignal;
	}
}
