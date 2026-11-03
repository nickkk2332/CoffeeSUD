package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Commands.interfaces.Command;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
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
 * B4: in builder mode, the rooms whose mobs or items a building command
 * changed since they were last saved to their rooms (SAVE ROOM, SAVE AREA,
 * SAVE CHANGED).  While a building command runs (CREATE, MODIFY, DESTROY,
 * COPY, TRANSFER, RESTRING, EVERY, GENERATE), each room a mob or item it
 * puts in or takes out of is marked, and MODIFY or RESTRING of a mob or item
 * marks the builder's room; LIST UNSAVED lists them with the change in
 * their counts from what is saved, SAVE CHANGED saves exactly those, and
 * QUIT, plain SAVE and GMODIFY CHANGE= say so first.  BUILDERAUTOSAVE=1 in
 * the ini saves them after each command.  %U in a prompt is their number.
 */
public final class Unsaved
{
	private Unsaved()
	{
	}

	/** the building commands, by their classes' IDs */
	private static final Set<String>					BUILDING	= new HashSet<String>(Arrays.asList(
		"CREATE", "MODIFY", "DESTROY", "COPY", "TRANSFER", "RESTRING", "EVERY", "GENERATE", "REGENERATE"));
	/** the rooms, by room ID, each with the commands that changed them */
	private static final Map<String, Set<String>>		rooms		= new TreeMap<String, Set<String>>();
	/** the building command running on this thread, and how deep */
	private static final ThreadLocal<String>			running		= new ThreadLocal<String>();
	private static final ThreadLocal<int[]>				depth		= new ThreadLocal<int[]>()
	{
		@Override
		protected int[] initialValue()
		{
			return new int[1];
		}
	};
	/**
	 * Whether a command is a building command.
	 * @param C the command
	 * @return true if it is
	 */
	public static boolean isBuilding(final Object C)
	{
		return (C instanceof Command) && BUILDING.contains(((Command)C).ID().toUpperCase());
	}

	/**
	 * A building command starts: the rooms whose mobs or items it changes are
	 * marked, and MODIFY or RESTRING of a mob or item marks the builder's room.
	 * @param mob the builder
	 * @param C the command
	 * @param words its words
	 */
	public static void begin(final MOB mob, final Object C, final List<String> words)
	{
		if((!SaveGame.isBuilderMode()) || (!isBuilding(C)))
			return;
		if((depth.get()[0]++) > 0)
			return;
		final String id = ((Command)C).ID().toUpperCase();
		running.set(id);
		final Room R = (mob == null) ? null : mob.location();
		if((R != null) && (id.equals("MODIFY") || id.equals("RESTRING")) && (words.size() > 1))
		{
			final String w = words.get(1).toUpperCase();
			if(w.equals("MOB") || w.equals("ITEM"))
				mark(R, id);
			else
			{
				Environmental E = R.fetchFromRoomFavorMOBs(null, CMParms.combine(words, 1));
				if(E == null)
					E = R.fetchFromRoomFavorMOBs(null, words.get(1));
				if(((E instanceof MOB) && ((MOB)E).isMonster()) || (E instanceof Item))
					mark(R, id);
			}
		}
	}

	/**
	 * A building command ends; with BUILDERAUTOSAVE=1, the rooms are saved.
	 * @param mob the builder
	 * @param C the command
	 */
	public static void end(final MOB mob, final Object C)
	{
		if((!SaveGame.isBuilderMode()) || (!isBuilding(C)) || (depth.get()[0] == 0))
			return;
		if((--depth.get()[0]) > 0)
			return;
		running.remove();
		if(CMProps.instance().getStr("BUILDERAUTOSAVE", "0").trim().equals("1") && (count() > 0))
			saveAll(mob);
	}

	/**
	 * A room's mobs or items change: marked if a building command is running
	 * and the thing is one a room saves.
	 * @param R the room
	 * @param P the mob or item put in or taken out
	 */
	public static void touched(final Room R, final Physical P)
	{
		final String id = running.get();
		if((id == null) || (R == null))
			return;
		if((P instanceof MOB) && ((!((MOB)P).isMonster()) || (!((MOB)P).isSavable())))
			return;
		if((P instanceof Item) && (!((Item)P).isSavable()))
			return;
		mark(R, id);
	}

	/**
	 * Marks a room changed.
	 * @param R the room
	 * @param why the command that changed it
	 */
	public static void mark(final Room R, final String why)
	{
		if((R == null) || (!R.isSavable()))
			return;
		final String id = CMLib.map().getExtendedRoomID(R);
		if((id == null) || (id.length() == 0))
			return;
		synchronized(rooms)
		{
			if(!rooms.containsKey(id))
				rooms.put(id, new LinkedHashSet<String>());
			rooms.get(id).add(why);
		}
	}

	/**
	 * A room's mobs and items are saved.
	 * @param R the room
	 */
	public static void saved(final Room R)
	{
		if(R == null)
			return;
		synchronized(rooms)
		{
			rooms.remove(CMLib.map().getExtendedRoomID(R));
		}
	}

	/**
	 * Forgets them all.
	 */
	public static void clear()
	{
		synchronized(rooms)
		{
			rooms.clear();
		}
	}

	/**
	 * How many rooms have unsaved changes.
	 * @return the number
	 */
	public static int count()
	{
		synchronized(rooms)
		{
			return rooms.size();
		}
	}

	/**
	 * The rooms with unsaved changes that are still in the world.
	 * @return the rooms
	 */
	public static List<Room> rooms()
	{
		final List<String> ids;
		synchronized(rooms)
		{
			ids = new ArrayList<String>(rooms.keySet());
		}
		final List<Room> list = new ArrayList<Room>();
		for(final String id : ids)
		{
			final Room R = CMLib.map().getRoom(id);
			if(R != null)
				list.add(R);
		}
		return list;
	}

	/**
	 * The change in a room's counts of mobs and items from what is saved.
	 * @param R the room
	 * @return "+1 mob, +2 items", or "changed" if the counts are the same
	 */
	public static String change(final Room R)
	{
		int mobs = 0;
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && M.isMonster() && M.isSavable() && ((M.getStartRoom() == null) || (M.getStartRoom() == R)))
				mobs++;
		}
		if(R.getArea() != null)
		{
			// this room's own that have wandered off are saved here too (B1)
			for(final Enumeration<Room> r = R.getArea().getProperMap(); r.hasMoreElements();)
			{
				final Room R2 = r.nextElement();
				if((R2 == null) || (R2 == R))
					continue;
				for(final Enumeration<MOB> m = R2.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					if((M != null) && M.isMonster() && M.isSavable() && (M.getStartRoom() == R))
						mobs++;
				}
			}
		}
		int items = 0;
		for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if((I != null) && I.isSavable())
				items++;
		}
		final int[] saved = CMLib.database().DBCountRoomMobsItems(CMLib.map().getExtendedRoomID(R));
		final List<String> parts = new ArrayList<String>();
		final int dm = mobs - saved[0];
		final int di = items - saved[1];
		if(dm != 0)
			parts.add(((dm > 0) ? "+" : "") + dm + ((Math.abs(dm) == 1) ? " mob" : " mobs"));
		if(di != 0)
			parts.add(((di > 0) ? "+" : "") + di + ((Math.abs(di) == 1) ? " item" : " items"));
		return parts.isEmpty() ? "changed" : String.join(", ", parts);
	}

	/**
	 * The rooms in a line, for QUIT and SAVE to name.
	 * @param max the most to name
	 * @return "Town#12 (+1 mob, +2 items), ..."
	 */
	public static String summary(final int max)
	{
		final List<Room> list = rooms();
		final List<String> parts = new ArrayList<String>();
		for(final Room R : list)
		{
			if(parts.size() >= max)
			{
				parts.add("and " + (list.size() - max) + " more");
				break;
			}
			parts.add(CMLib.map().getExtendedRoomID(R) + " (" + change(R) + ")");
		}
		return String.join(", ", parts);
	}

	/**
	 * LIST UNSAVED: each room with unsaved changes, the commands that made
	 * them, and the change in its counts.
	 * @return the report
	 */
	public static String report()
	{
		if(!SaveGame.isBuilderMode())
			return "LIST UNSAVED is for builder mode, where SAVE writes the rooms to the world files.";
		final Map<String, Set<String>> copy = new TreeMap<String, Set<String>>();
		synchronized(rooms)
		{
			for(final Map.Entry<String, Set<String>> e : rooms.entrySet())
				copy.put(e.getKey(), new LinkedHashSet<String>(e.getValue()));
		}
		if(copy.isEmpty())
			return "No room has unsaved mobs or items.";
		final StringBuilder str = new StringBuilder("Rooms with unsaved mobs or items (SAVE CHANGED saves them):");
		for(final Map.Entry<String, Set<String>> e : copy.entrySet())
		{
			final Room R = CMLib.map().getRoom(e.getKey());
			str.append("\n\r  ").append(e.getKey()).append(": ").append(String.join(", ", e.getValue()));
			str.append(" (").append((R == null) ? "gone" : change(R)).append(")");
		}
		return str.toString();
	}

	/**
	 * Saves each room with unsaved changes, as SAVE ROOM does.
	 * @param mob the builder, or null
	 * @return how many were saved
	 */
	public static int saveAll(final MOB mob)
	{
		// the command's class is the class loader's, so it is called by name
		final Object S = CMClass.getCommand("Save");
		final java.lang.reflect.Method save;
		try
		{
			save = (S == null) ? null : S.getClass().getMethod("saveChangedRoom", MOB.class, Room.class);
		}
		catch(final NoSuchMethodException e)
		{
			Log.errOut("Unsaved", "the Save command has no saveChangedRoom");
			return 0;
		}
		if(save == null)
			return 0;
		int n = 0;
		for(final Room R : rooms())
		{
			try
			{
				save.invoke(S, mob, R);
			}
			catch(final Exception e)
			{
				Log.errOut("Unsaved", e);
				continue;
			}
			saved(R);
			n++;
		}
		clear();
		return n;
	}
}
