package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

/*
   Copyright 2026 CoffeeSUD contributors

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

	   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
/**
 * JS5: a behaviour for a room or an area that a JavaScript class in
 * world/js/behaviors/ extends (//extends
 * com.planet_ink.coffee_mud.Behaviors.JSWorldBehavior), defining the hooks
 * it wants: onEnter(mob, room), onLeave(mob, room), onWeather(area, was,
 * now) and onDaypart(area, was, now). Helpers: echo(text) to the host,
 * roll("3d6"), spawn(catalogName), room(), area() and parm(name).
 */
public class JSWorldBehavior extends StdBehavior
{
	@Override
	public String ID()
	{
		return "JSWorldBehavior";
	}

	@Override
	protected int canImproveCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "a world script";
	}

	/** the weather and the time of day last seen, -1 and null before the first tick */
	protected int		lastWeather	= -1;
	protected String	lastDaypart	= null;
	/** the room or area it is on */
	protected PhysicalAgent	host	= null;

	/**
	 * Someone came into a room it is on (or one of its area's).
	 * @param who who came
	 * @param room the room
	 */
	public void onEnter(final MOB who, final Room room)
	{
	}

	/**
	 * Someone left a room it is on (or one of its area's).
	 * @param who who left
	 * @param room the room
	 */
	public void onLeave(final MOB who, final Room room)
	{
	}

	/**
	 * The weather changed in its area.
	 * @param area the area
	 * @param was the weather before (Climate.WEATHER_DESCS)
	 * @param now the weather now
	 */
	public void onWeather(final Area area, final String was, final String now)
	{
	}

	/**
	 * The time of day changed in its area (DAWN, DAY, DUSK, NIGHT).
	 * @param area the area
	 * @param was the time of day before
	 * @param now the time of day now
	 */
	public void onDaypart(final Area area, final String was, final String now)
	{
	}

	/** @return the room it is on, or its area's first */
	public Room room()
	{
		if(host instanceof Room)
			return (Room)host;
		final Area A = area();
		return (A == null) ? null : A.getRandomProperRoom();
	}

	/** @return the area it is on, or its room's */
	public Area area()
	{
		if(host instanceof Area)
			return (Area)host;
		return (host instanceof Room) ? ((Room)host).getArea() : null;
	}

	/**
	 * A parameter of the behaviour's own text, as FARE=5.
	 * @param name the parameter's name
	 * @return its value, or ""
	 */
	public String parm(final String name)
	{
		return CMParms.getParmStr(getParms(), name, "");
	}

	/**
	 * Shows text to the host: its room, or every room of its area with someone in it.
	 * @param text the text
	 */
	public void echo(final String text)
	{
		if(host instanceof Room)
			((Room)host).showHappens(CMMsg.MSG_OK_ACTION, text);
		else
		if(host instanceof Area)
		{
			for(final java.util.Enumeration<Room> r = ((Area)host).getProperMap(); r.hasMoreElements();)
			{
				final Room R = r.nextElement();
				if((R != null) && (R.numInhabitants() > 0))
					R.showHappens(CMMsg.MSG_OK_ACTION, text);
			}
		}
	}

	/**
	 * @param dice as 3d6+2
	 * @return a roll
	 */
	public long roll(final String dice)
	{
		return ScriptLib.dice(dice);
	}

	/**
	 * A copy of a catalog mob in the host's room (or a room of its area);
	 * the catalog's cap holds.
	 * @param name the catalog mob's name
	 * @return the mob, or null
	 */
	public MOB spawn(final String name)
	{
		final Room R = room();
		final Physical P = ScriptLib.catalogCopy(ScriptLib.catalogEntry(name, true), false);
		if(!(P instanceof MOB) || (R == null))
			return null;
		final MOB M = (MOB)P;
		M.setStartRoom(null);
		M.bringToLife(R, true);
		return M;
	}

	@Override
	public void startBehavior(final PhysicalAgent forMe)
	{
		super.startBehavior(forMe);
		host = forMe;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(host == null)
			host = (myHost instanceof PhysicalAgent) ? (PhysicalAgent)myHost : null;
		if(!(msg.target() instanceof Room))
			return;
		final Room R = (Room)msg.target();
		if((R != myHost) && (!(myHost instanceof Area) || (R.getArea() != myHost)))
			return;
		if(msg.targetMinor() == CMMsg.TYP_ENTER)
			onEnter(msg.source(), R);
		else
		if(msg.targetMinor() == CMMsg.TYP_LEAVE)
			onLeave(msg.source(), R);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((host == null) && (ticking instanceof PhysicalAgent))
			host = (PhysicalAgent)ticking;
		final Area A = area();
		if(A == null)
			return true;
		final int weather = A.getClimateObj().weatherType(room());
		final String daypart = A.getTimeObj().getTODCode().name();
		if((lastWeather >= 0) && (weather != lastWeather))
			onWeather(A, Climate.WEATHER_DESCS[lastWeather], Climate.WEATHER_DESCS[weather]);
		if((lastDaypart != null) && (!daypart.equals(lastDaypart)))
			onDaypart(A, lastDaypart, daypart);
		lastWeather = weather;
		lastDaypart = daypart;
		return true;
	}
}
