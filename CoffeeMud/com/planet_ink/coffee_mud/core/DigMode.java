package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

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
 * B9: rooms made fast.  WORLD DIG ON (TYPE=locale or template)
 * (LINK=TWO-WAY|ONE-WAY): while it is on, a builder who walks where there is
 * no exit makes a room there first, as CREATE ROOM does, and goes into it.
 * WORLD PATH e,e,n,ne (TYPE=..) (NAME="..") (DESC="..") lays out a chain of
 * rooms in one command, following exits that are there already.  Each room
 * made is saved and tagged TODO, which WORLD CHECK lists until EDIT DESC
 * gives it its own description.
 */
public final class DigMode
{
	private DigMode()
	{
	}

	/** the tag a room made by WORLD DIG or PATH has until it is written */
	public static final String	TODO_TAG	= "TODO";

	private static final class Setting
	{
		final String	type;
		final boolean	twoWay;

		Setting(final String type, final boolean twoWay)
		{
			this.type = type;
			this.twoWay = twoWay;
		}
	}

	private static final Map<MOB, Setting>	digging	= Collections.synchronizedMap(new WeakHashMap<MOB, Setting>());
	private static final Pattern			ARG		= Pattern.compile("(?i)^(TYPE|LINK|NAME|DESC)=(.*)$");

	/**
	 * The KEY=value words of WORLD DIG and PATH, a value quoted or not, in one
	 * word or over several.
	 * @param words the words
	 * @return the values, by key in upper case, and the other words under ""
	 */
	public static Map<String, String> args(final List<String> words)
	{
		final Map<String, String> out = new HashMap<String, String>();
		final List<String> rest = new ArrayList<String>();
		for(int i = 0; i < words.size(); i++)
		{
			final Matcher m = ARG.matcher(words.get(i));
			if(!m.matches())
			{
				rest.add(words.get(i));
				continue;
			}
			String v = m.group(2);
			if(v.startsWith("\"") && ((v.length() < 2) || (!v.endsWith("\""))))
			{
				while(++i < words.size())
				{
					v = v + " " + words.get(i);
					if(words.get(i).endsWith("\""))
						break;
				}
			}
			if((v.length() >= 2) && v.startsWith("\"") && v.endsWith("\""))
				v = v.substring(1, v.length() - 1);
			out.put(m.group(1).toUpperCase(), v);
		}
		out.put("", String.join(" ", rest).trim());
		return out;
	}

	/**
	 * WORLD DIG ON: rooms are made where the builder walks and there is none.
	 * @param mob the builder
	 * @param type the locale class or template, or null for the room's own class
	 * @param twoWay true to link the new room back
	 */
	public static void on(final MOB mob, final String type, final boolean twoWay)
	{
		digging.put(mob, new Setting(type, twoWay));
	}

	/**
	 * WORLD DIG OFF.
	 * @param mob the builder
	 */
	public static void off(final MOB mob)
	{
		digging.remove(mob);
	}

	/**
	 * What WORLD DIG does for a builder.
	 * @param mob the builder
	 * @return the words
	 */
	public static String describe(final MOB mob)
	{
		final Setting S = digging.get(mob);
		if(S == null)
			return "WORLD DIG is off.";
		return "WORLD DIG is on: a walk where there is no exit makes a room of " + ((S.type == null) ? "this room's kind" : S.type)
			+ ", linked " + (S.twoWay ? "both ways" : "one way") + ".";
	}

	/**
	 * Before a builder walks: with WORLD DIG on and no exit that way, a room
	 * is made there first.
	 * @param mob the one walking
	 * @param dir the direction
	 */
	public static void beforeMove(final MOB mob, final int dir)
	{
		if((mob == null) || digging.isEmpty() || (!SaveGame.isBuilderMode()))
			return;
		final Setting S = digging.get(mob);
		final Room R = mob.location();
		if((S == null) || (R == null) || (dir < 0) || (dir >= Directions.NUM_DIRECTIONS()))
			return;
		if((R.rawDoors()[dir] != null) || (R.getRawExit(dir) != null))
			return;
		dig(mob, R, dir, S.type, S.twoWay, null, null);
	}

	/**
	 * A room made from one in a direction, as CREATE ROOM makes it: saved,
	 * linked, and tagged TODO.
	 * @param mob the builder
	 * @param from the room it is made from
	 * @param dir the direction
	 * @param type the locale class or template, or null for from's class
	 * @param twoWay true to link it back
	 * @param name its title, or null for what CREATE gives it
	 * @param desc its description, or null
	 * @return the room, or null if none was made
	 */
	public static Room dig(final MOB mob, final Room from, final int dir, final String type, final boolean twoWay, final String name, final String desc)
	{
		final Room was = mob.location();
		if(was != from)
			from.bringMobHere(mob, false);
		final List<String> words = new ArrayList<String>(Arrays.asList("CREATE", "ROOM",
			CMLib.directions().getDirectionName(dir), (type == null) ? CMClass.classID(from) : type));
		try
		{
			CMClass.getCommand("Create").execute(mob, words, 0);
		}
		catch(final java.io.IOException e)
		{
			Log.errOut("DigMode", e);
		}
		final Room made = from.rawDoors()[dir];
		if((was != from) && (was != null))
			was.bringMobHere(mob, false);
		if(made == null)
			return null;
		final int back = Directions.getOpDirectionCode(dir);
		if((!twoWay) && (back >= 0) && (made.rawDoors()[back] == from))
		{
			made.rawDoors()[back] = null;
			made.setRawExit(back, null);
			CMLib.database().DBUpdateExits(made);
		}
		if(name != null)
			made.setDisplayText(name);
		if(desc != null)
			made.setDescription(desc);
		made.addTag(TODO_TAG);
		CMLib.database().DBUpdateRoom(made);
		return made;
	}

	/**
	 * WORLD PATH: a chain of rooms, one for each direction given, following
	 * those there already; the builder stays where they are.
	 * @param mob the builder
	 * @param dirs the directions, by comma or space
	 * @param type the locale class or template, or null for the room's own
	 * @param twoWay true to link each back
	 * @param name each new room's title, or null
	 * @param desc each new room's description, or null
	 * @return what was done
	 */
	public static String path(final MOB mob, final String dirs, final String type, final boolean twoWay, final String name, final String desc)
	{
		final Room start = mob.location();
		if(start == null)
			return "You are nowhere.";
		final List<Integer> codes = new ArrayList<Integer>();
		for(final String w : dirs.split("[,\\s]+"))
		{
			if(w.trim().length() == 0)
				continue;
			final int d = CMLib.directions().getGoodDirectionCode(w.trim());
			if(d < 0)
				return "'" + w + "' is no direction.";
			codes.add(Integer.valueOf(d));
		}
		if(codes.isEmpty())
			return "WORLD PATH [DIRECTIONS] (TYPE=..) (NAME=\"..\") (DESC=\"..\"): which ways?";
		Room cursor = start;
		int made = 0;
		int followed = 0;
		for(final Integer D : codes)
		{
			final int d = D.intValue();
			final Room next = cursor.rawDoors()[d];
			if(next != null)
			{
				cursor = next;
				followed++;
				continue;
			}
			if(cursor.getRawExit(d) != null)
				return "The path stops at " + CMLib.map().getExtendedRoomID(cursor) + ": an exit " + CMLib.directions().getDirectionName(d) + " leads nowhere.";
			final Room R = dig(mob, cursor, d, type, twoWay, name, desc);
			if(R == null)
				return "The path stops at " + CMLib.map().getExtendedRoomID(cursor) + ": no room could be made " + CMLib.directions().getDirectionName(d) + ".";
			cursor = R;
			made++;
		}
		if(mob.location() != start)
			start.bringMobHere(mob, false);
		return "WORLD PATH made " + made + " rooms" + ((followed > 0) ? (" and followed " + followed + " exits") : "")
			+ ", to " + CMLib.map().getExtendedRoomID(cursor) + "; those made are tagged TODO for WORLD CHECK.";
	}
}
