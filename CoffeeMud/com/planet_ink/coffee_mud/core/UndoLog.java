package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Commands.interfaces.Command;
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
 * B6: in builder mode, the last 50 building commands (those B4 tracks, and
 * GMODIFY), each with the rooms it changed as they were before it: their
 * XML with their mobs and items, taken when the command starts (the
 * builder's room) and before each room's mobs or items first change.  An
 * entry is kept only if a room changed.  They are kept in memory and in
 * world/.history/, a file each.  HISTORY lists them, UNDO (n) puts the last
 * n back, newest first: each room is made again from its XML, those in it
 * waiting outside, the exits into it from other rooms linked again.
 */
public final class UndoLog
{
	private UndoLog()
	{
	}

	/** how many entries are kept */
	public static final int		KEEP	= 50;
	/** the folder in the world folder they are kept in */
	public static final String	DIR		= ".history";

	/** the commands logged, by their classes' IDs: B4's, and GMODIFY */
	private static final Set<String>	LOGGED	= new HashSet<String>(Arrays.asList(
		"CREATE", "MODIFY", "DESTROY", "COPY", "TRANSFER", "RESTRING", "EVERY", "GENERATE", "GMODIFY"));

	/** a command, and the rooms it changed as they were before it */
	public static final class Entry
	{
		/** what the command was, and where */
		public final String					what;
		/** when it ran */
		public final long					when;
		/** each room's XML before it, by room ID */
		public final Map<String, String>	rooms	= new LinkedHashMap<String, String>();
		/** its file, once kept */
		File								file	= null;

		Entry(final String what, final long when)
		{
			this.what = what;
			this.when = when;
		}
	}

	private static final LinkedList<Entry>	entries	= new LinkedList<Entry>();
	private static volatile boolean			read	= false;
	private static final ThreadLocal<Entry>	open	= new ThreadLocal<Entry>();
	private static final ThreadLocal<int[]>	depth	= new ThreadLocal<int[]>()
	{
		@Override
		protected int[] initialValue()
		{
			return new int[1];
		}
	};

	/** while a room's XML is being written */
	private static final ThreadLocal<int[]>	busy	= new ThreadLocal<int[]>()
	{
		@Override
		protected int[] initialValue()
		{
			return new int[1];
		}
	};

	/** the builder's room XML of the room as it is, not as the database has it */
	private static volatile java.lang.reflect.Method	liveXML	= null;

	private static String xmlOf(final Room R)
	{
		try
		{
			if(liveXML == null)
			{
				for(Class<?> c = CMLib.coffeeMaker().getClass(); (c != null) && (liveXML == null); c = c.getSuperclass())
				{
					try
					{
						final java.lang.reflect.Method m = c.getDeclaredMethod("getRoomXML", Room.class, Set.class, Set.class,
							boolean.class, boolean.class, boolean.class);
						m.setAccessible(true);
						liveXML = m;
					}
					catch(final NoSuchMethodException e)
					{
					}
				}
			}
			if(liveXML != null)
				return (String)liveXML.invoke(CMLib.coffeeMaker(), R, new HashSet<CMObject>(), new HashSet<String>(), Boolean.TRUE, Boolean.FALSE, Boolean.FALSE);
		}
		catch(final Exception e)
		{
			Log.errOut("UndoLog", e);
		}
		return CMLib.coffeeMaker().getRoomXML(R, new HashSet<CMObject>(), new HashSet<String>(), true);
	}

	private static String idOf(final Room R)
	{
		final String id = (R == null) ? null : CMLib.map().getExtendedRoomID(R);
		return ((id == null) || (id.length() == 0)) ? null : id;
	}

	/**
	 * A logged command starts: its entry, with the builder's room as it is.
	 * @param mob the builder
	 * @param C the command
	 * @param words its words
	 */
	public static void begin(final MOB mob, final Object C, final List<String> words)
	{
		if((!SaveGame.isBuilderMode()) || (!(C instanceof Command)) || (!LOGGED.contains(((Command)C).ID().toUpperCase())))
			return;
		if((depth.get()[0]++) > 0)
			return;
		final Room R = (mob == null) ? null : mob.location();
		final String id = idOf(R);
		open.set(new Entry(CMParms.combine(words, 0) + ((id == null) ? "" : (" @" + id)), System.currentTimeMillis()));
		snapshot(R);
	}

	/**
	 * A room is about to change: kept as it is, once an entry.
	 * @param R the room
	 */
	public static void snapshot(final Room R)
	{
		final Entry e = open.get();
		if((e == null) || (R == null) || (!R.isSavable()))
			return;
		final String id = idOf(R);
		if((id == null) || e.rooms.containsKey(id) || (busy.get()[0] > 0))
			return;
		// writing a room's XML may put things in it and take them out again
		busy.get()[0]++;
		try
		{
			e.rooms.put(id, xmlOf(R));
		}
		finally
		{
			busy.get()[0]--;
		}
	}

	/**
	 * A logged command ends: its entry is kept if a room changed.
	 * @param C the command
	 */
	public static void end(final Object C)
	{
		if((!SaveGame.isBuilderMode()) || (!(C instanceof Command)) || (!LOGGED.contains(((Command)C).ID().toUpperCase()))
		|| (depth.get()[0] == 0))
			return;
		if((--depth.get()[0]) > 0)
			return;
		final Entry e = open.get();
		open.remove();
		if(e == null)
			return;
		for(final Iterator<Map.Entry<String, String>> i = e.rooms.entrySet().iterator(); i.hasNext();)
		{
			final Map.Entry<String, String> r = i.next();
			final Room R = CMLib.map().getRoom(r.getKey());
			if((R != null) && xmlOf(R).equals(r.getValue()))
				i.remove();
		}
		if(e.rooms.isEmpty())
			return;
		add(e);
	}

	private static File dir()
	{
		final File world = WorldFiles.worldDir();
		return (world == null) ? null : new File(world, DIR);
	}

	/** the entries kept in world/.history/, read at the first use */
	private static void readKept()
	{
		if(read)
			return;
		read = true;
		final File D = dir();
		final File[] fs = (D == null) ? null : D.listFiles();
		if(fs == null)
			return;
		Arrays.sort(fs);
		for(final File F : fs)
		{
			if(!F.getName().endsWith(".undo"))
				continue;
			try
			{
				final Entry e = parse(new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8));
				if(e != null)
				{
					e.file = F;
					entries.add(e);
				}
			}
			catch(final IOException ex)
			{
				Log.errOut("UndoLog", F.getName() + ": " + ex.getMessage());
			}
		}
	}

	private static String format(final Entry e)
	{
		final StringBuilder str = new StringBuilder();
		str.append("WHAT ").append(e.what.replace('\n', ' ')).append("\n");
		str.append("WHEN ").append(e.when).append("\n");
		for(final Map.Entry<String, String> r : e.rooms.entrySet())
			str.append("ROOM ").append(r.getValue().length()).append(" ").append(r.getKey()).append("\n").append(r.getValue()).append("\n");
		return str.toString();
	}

	private static Entry parse(final String text)
	{
		int pos = 0;
		String what = null;
		long when = 0;
		Entry e = null;
		while(pos < text.length())
		{
			final int nl = text.indexOf('\n', pos);
			if(nl < 0)
				break;
			final String line = text.substring(pos, nl);
			pos = nl + 1;
			if(line.startsWith("WHAT "))
				what = line.substring(5);
			else
			if(line.startsWith("WHEN "))
				when = CMath.s_long(line.substring(5).trim());
			else
			if(line.startsWith("ROOM "))
			{
				if(e == null)
					e = new Entry((what == null) ? "?" : what, when);
				final String rest = line.substring(5);
				final int sp = rest.indexOf(' ');
				final int len = CMath.s_int(rest.substring(0, sp));
				final String id = rest.substring(sp + 1);
				if(pos + len > text.length())
					break;
				e.rooms.put(id, text.substring(pos, pos + len));
				pos = pos + len + 1;
			}
		}
		return e;
	}

	private static synchronized void add(final Entry e)
	{
		readKept();
		entries.add(e);
		final File D = dir();
		if(D != null)
		{
			try
			{
				if(!D.isDirectory())
				{
					D.mkdirs();
					// not part of the world: git leaves it out
					Files.write(new File(D, ".gitignore").toPath(), "*\n".getBytes(StandardCharsets.UTF_8));
				}
				final File F = new File(D, String.format("%015d", Long.valueOf(e.when)) + "-" + entries.size() + ".undo");
				Files.write(F.toPath(), format(e).getBytes(StandardCharsets.UTF_8));
				e.file = F;
			}
			catch(final IOException ex)
			{
				Log.errOut("UndoLog", ex.getMessage());
			}
		}
		while(entries.size() > KEEP)
			drop(entries.removeFirst());
	}

	private static void drop(final Entry e)
	{
		if((e.file != null) && e.file.isFile())
			e.file.delete();
	}

	/**
	 * The entries, newest first.
	 * @return the entries
	 */
	public static synchronized List<Entry> entries()
	{
		readKept();
		final List<Entry> list = new ArrayList<Entry>(entries);
		Collections.reverse(list);
		return list;
	}

	/**
	 * HISTORY: the entries, newest first, numbered as UNDO counts them.
	 * @param max the most to list
	 * @return the list
	 */
	public static String history(final int max)
	{
		if(!SaveGame.isBuilderMode())
			return "HISTORY is for builder mode.";
		final List<Entry> list = entries();
		if(list.isEmpty())
			return "No building command is logged to undo.";
		final StringBuilder str = new StringBuilder("Building commands, newest first (UNDO n puts the last n back):");
		final java.text.SimpleDateFormat time = new java.text.SimpleDateFormat("MM-dd HH:mm");
		for(int i = 0; (i < list.size()) && (i < max); i++)
		{
			final Entry e = list.get(i);
			str.append("\n\r  ").append(i + 1).append(". ").append(e.what).append(" (").append(time.format(new Date(e.when)))
				.append(", ").append(e.rooms.size()).append((e.rooms.size() == 1) ? " room" : " rooms").append(")");
		}
		return str.toString();
	}

	/** a room outside the given one, for those in it to wait in */
	private static Room outside(final Room R)
	{
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			final Room to = R.rawDoors()[d];
			if((to != null) && (to != R))
				return to;
		}
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R2 = r.nextElement();
			if((R2 != null) && (R2 != R))
				return R2;
		}
		return null;
	}

	/**
	 * Puts a room back as its XML has it: made again from it, those in it
	 * waiting outside, the exits into it from other rooms linked again.
	 * @param id the room's ID
	 * @param xml its XML, with its mobs and items
	 * @return null if put back, or what went wrong
	 */
	public static String restoreRoom(final String id, final String xml)
	{
		final Room R = CMLib.map().getRoom(id);
		final List<String[]> links = new ArrayList<String[]>();
		final List<MOB> moved = new ArrayList<MOB>();
		Room wait = null;
		if(R != null)
		{
			for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
			{
				final Room R2 = r.nextElement();
				if((R2 == null) || (R2 == R))
					continue;
				for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				{
					if(R2.rawDoors()[d] == R)
						links.add(new String[] { CMLib.map().getExtendedRoomID(R2), "" + d });
				}
			}
			wait = outside(R);
			for(final MOB M : new ArrayList<MOB>(new com.planet_ink.coffee_mud.core.collections.XVector<MOB>(R.inhabitants())))
			{
				final MOB lead = (M == null) ? null : M.getGroupLeader();
				if((M != null) && ((M.playerStats() != null) || ((lead != null) && (lead.playerStats() != null))))
					moved.add(M);
			}
			if((wait == null) && (moved.size() > 0))
				return "there is no room outside " + id + " to wait in";
			for(final MOB M : moved)
				wait.bringMobHere(M, false);
			CMLib.map().obliterateMapRoom(R);
		}
		final String problem = CMLib.coffeeMaker().unpackRoomFromXML(xml, true);
		final Room R2 = CMLib.map().getRoom(id);
		for(final String[] link : links)
		{
			final Room from = CMLib.map().getRoom(link[0]);
			if(from != null)
				from.rawDoors()[CMath.s_int(link[1])] = R2;
		}
		for(final MOB M : moved)
			((R2 != null) ? R2 : wait).bringMobHere(M, false);
		if(R2 != null)
			Unsaved.saved(R2);
		return ((problem == null) || (problem.length() == 0)) ? ((R2 == null) ? ("no room " + id + " came of it") : null) : problem;
	}

	/**
	 * UNDO: the last n entries put back, newest first, and forgotten.
	 * @param n how many
	 * @return what was done
	 */
	public static String undo(final int n)
	{
		if(!SaveGame.isBuilderMode())
			return "UNDO is for builder mode.";
		final StringBuilder str = new StringBuilder();
		for(int i = 0; i < n; i++)
		{
			final Entry e;
			synchronized(UndoLog.class)
			{
				readKept();
				if(entries.isEmpty())
					break;
				e = entries.removeLast();
			}
			final List<String> problems = new ArrayList<String>();
			for(final Map.Entry<String, String> r : e.rooms.entrySet())
			{
				final String problem = restoreRoom(r.getKey(), r.getValue());
				if(problem != null)
					problems.add(r.getKey() + ": " + problem);
			}
			drop(e);
			str.append((str.length() > 0) ? "\n\r" : "").append("Undone: ").append(e.what).append(" (")
				.append(e.rooms.size()).append((e.rooms.size() == 1) ? " room" : " rooms").append(" put back")
				.append(problems.isEmpty() ? "" : ("; " + String.join("; ", problems))).append(").");
		}
		return (str.length() == 0) ? "There is nothing to undo." : str.toString();
	}

	/**
	 * Forgets them all, and their files.
	 */
	public static synchronized void clear()
	{
		readKept();
		for(final Entry e : entries)
			drop(e);
		entries.clear();
	}
}
