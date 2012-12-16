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
 * This class holds an activity with its title, description and class name, it's
 * used to show the user the available activities in the ActionSelectActivity
 * class.
 */
public class Action
{
	private String action = "";
	private String descripiton = "";
	private String className = "";

	public Action(String name, String descripiton, String className)
	{
		this.action = name;
		this.descripiton = descripiton;
		this.className = className;
	}

	public String getAction()
	{
		return action;
	}

	public String getDescripiton()
	{
		return descripiton;
	}

	public String getClassName()
	{
		return className;
	}
}
