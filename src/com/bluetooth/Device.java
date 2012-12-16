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

/**
 * Class to hold information about a device in order to show it in a ListView:
 * name, MAC address and the signal strength.
 */
public class Device
{
	private String name = "";
	private String address = "";
	private String signal = "";

	public Device(String name, String address, Short signal)
	{
		this.name = name;
		this.address = address;
		this.signal = Short.toString(signal);
	}

	public String getName()
	{
		return name;
	}

	public String getAddress()
	{
		return address;
	}

	public String getSignal()
	{
		return signal;
	}
}
