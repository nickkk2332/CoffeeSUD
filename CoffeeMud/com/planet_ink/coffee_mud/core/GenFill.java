package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.util.*;

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
 * G9: GENERATE FILL roomtemplate [FROM file] [VAR=VALUE ...] ROOM|AREA
 * [KEEPTEXT] [SEED=n]: a ROOM template run on rooms that are already there,
 * dug by hand.  Each room gets the layout's tags from its real exits
 * (ROOMTAG_NODETYPE leaf, street or square; NODERUN, NODEEXITS, NODEFLAGS;
 * the neighbours' NODETYPE_x, ROOMTITLE_x and ROOMLINK_x; the gate's
 * NODEGATEEXIT and GATEEXITROOM) and G3's depth tags from a walk from the
 * area's entrance; then the template sets its title and description
 * (unless KEEPTEXT, or the room is GENLOCK) and adds its mobs, items,
 * effects and behaviours.  Exits are left alone.
 */
public final class GenFill
{
	private GenFill()
	{
	}

	private static int[] step(final int d)
	{
		switch(d)
		{
		case Directions.NORTH:
			return new int[] { 0, -1 };
		case Directions.SOUTH:
			return new int[] { 0, 1 };
		case Directions.EAST:
			return new int[] { 1, 0 };
		case Directions.WEST:
			return new int[] { -1, 0 };
		case Directions.NORTHEAST:
			return new int[] { 1, -1 };
		case Directions.NORTHWEST:
			return new int[] { -1, -1 };
		case Directions.SOUTHEAST:
			return new int[] { 1, 1 };
		case Directions.SOUTHWEST:
			return new int[] { -1, 1 };
		default:
			return new int[] { 0, 0 };
		}
	}

	private static List<Integer> exitsOf(final Room R)
	{
		final List<Integer> dirs = new ArrayList<Integer>();
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			if(R.getRoomInDir(d) != null)
				dirs.add(Integer.valueOf(d));
		}
		return dirs;
	}

	/**
	 * @param R a room
	 * @return its node type from its exits: leaf (one or none), street (two) or square
	 */
	public static String nodeType(final Room R)
	{
		final int n = exitsOf(R).size();
		return (n <= 1) ? "leaf" : (n == 2) ? "street" : "square";
	}

	private static String run(final List<Integer> dirs)
	{
		if(dirs.size() != 2)
			return null;
		final int a = dirs.get(0).intValue(), b = dirs.get(1).intValue();
		if(Directions.getOpDirectionCode(a) != b)
			return null;
		final Set<Integer> s = new HashSet<Integer>(dirs);
		if(s.contains(Integer.valueOf(Directions.NORTH)))
			return "ns";
		if(s.contains(Integer.valueOf(Directions.EAST)))
			return "ew";
		if(s.contains(Integer.valueOf(Directions.UP)))
			return "ud";
		if(s.contains(Integer.valueOf(Directions.NORTHEAST)))
			return "nesw";
		if(s.contains(Integer.valueOf(Directions.NORTHWEST)))
			return "nwse";
		return null;
	}

	/**
	 * The layout's tags for a room, from its real exits.
	 * @param R the room
	 * @param A its area
	 * @param defined where they go
	 */
	public static void nodeTags(final Room R, final Area A, final Map<String, Object> defined)
	{
		final List<Integer> dirs = exitsOf(R);
		defined.put("ROOMTAG_NODETYPE", nodeType(R));
		final StringBuilder ex = new StringBuilder(",");
		for(final Integer d : dirs)
			ex.append(CMLib.directions().getDirectionChar(d.intValue()).toLowerCase()).append(',');
		defined.put("ROOMTAG_NODEEXITS", ex.toString());
		final String run = run(dirs);
		if(run != null)
			defined.put("ROOMTAG_NODERUN", run);
		final StringBuilder flags = new StringBuilder(",");
		if((dirs.size() == 2) && (run == null))
			flags.append("corner,");
		if(dirs.size() == 3)
			flags.append("tee,");
		if(dirs.size() >= 4)
			flags.append("intersection,");
		for(final Integer d : dirs)
		{
			final Room N = R.getRoomInDir(d.intValue());
			final String c = CMLib.directions().getDirectionChar(d.intValue()).toUpperCase();
			if(N.getArea() != A)
			{
				if(flags.indexOf(",gate,") < 0)
					flags.append("gate,");
				if(!defined.containsKey("ROOMTAG_NODEGATEEXIT"))
				{
					defined.put("ROOMTAG_NODEGATEEXIT", CMLib.directions().getDirectionName(d.intValue()).toLowerCase());
					defined.put("ROOMTAG_GATEEXITROOM", N);
				}
				continue;
			}
			defined.put("NODETYPE_" + c, nodeType(N));
			defined.put("ROOMTITLE_" + c, N.displayText(null));
			defined.put("ROOMLINK_" + c, "true");
		}
		if(flags.length() > 1)
			defined.put("ROOMTAG_NODEFLAGS", flags.toString());
	}

	/**
	 * G3's tags for the rooms of an area, from a walk from its entrance.
	 * @param A the area
	 * @param defined where AREA_MAXDEPTH and AREA_NUMROOMS go
	 * @return each room's DEPTH, DEPTHPCT, ISDEEPEST, INDEX, X and Y
	 */
	public static Map<Room, Map<String, String>> depthTags(final Area A, final Map<String, Object> defined)
	{
		final List<Room> rooms = new ArrayList<Room>();
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
			rooms.add(r.nextElement());
		Room entry = A.getRoom(A.Name() + "#0");
		for(final Room R : rooms)
		{
			if(entry != null)
				break;
			for(final Integer d : exitsOf(R))
			{
				if(R.getRoomInDir(d.intValue()).getArea() != A)
					entry = R;
			}
		}
		if((entry == null) && (rooms.size() > 0))
			entry = rooms.get(0);
		final Map<Room, Integer> depth = new HashMap<Room, Integer>();
		final Map<Room, int[]> at = new HashMap<Room, int[]>();
		final List<Room> order = new ArrayList<Room>();
		int max = 0;
		if(entry != null)
		{
			final LinkedList<Room> todo = new LinkedList<Room>();
			depth.put(entry, Integer.valueOf(0));
			at.put(entry, new int[] { 0, 0 });
			todo.add(entry);
			while(todo.size() > 0)
			{
				final Room R = todo.removeFirst();
				order.add(R);
				final int d = depth.get(R).intValue();
				max = Math.max(max, d);
				for(final Integer dir : exitsOf(R))
				{
					final Room N = R.getRoomInDir(dir.intValue());
					if((N.getArea() == A) && (!depth.containsKey(N)))
					{
						depth.put(N, Integer.valueOf(d + 1));
						final int[] p = at.get(R), s = step(dir.intValue());
						at.put(N, new int[] { p[0] + s[0], p[1] + s[1] });
						todo.add(N);
					}
				}
			}
		}
		for(final Room R : rooms)
		{
			if(!order.contains(R))
				order.add(R);
		}
		final Map<Room, Map<String, String>> tags = new HashMap<Room, Map<String, String>>();
		for(int i = 0; i < order.size(); i++)
		{
			final Room R = order.get(i);
			final int d = depth.containsKey(R) ? depth.get(R).intValue() : max;
			final int[] p = at.containsKey(R) ? at.get(R) : new int[] { 0, 0 };
			final Map<String, String> t = new HashMap<String, String>();
			t.put("DEPTH", "" + d);
			t.put("DEPTHPCT", "" + ((max == 0) ? 0 : Math.round((100.0 * d) / max)));
			t.put("ISDEEPEST", (d == max) ? "true" : "false");
			t.put("INDEX", "" + i);
			t.put("X", "" + p[0]);
			t.put("Y", "" + p[1]);
			tags.put(R, t);
		}
		defined.put("AREA_MAXDEPTH", "" + max);
		defined.put("AREA_NUMROOMS", "" + rooms.size());
		return tags;
	}

	/**
	 * GENERATE FILL.
	 * @param mob the builder
	 * @param file the template file
	 * @param id the ROOM template's id
	 * @param vars the variables given
	 * @param wholeArea true for every room of the builder's area, false for the builder's room
	 * @param keepText true to keep the rooms' titles and descriptions
	 * @param seedWord the seed, or null for a new one
	 * @param save true to write the rooms through the database
	 * @return what was done
	 */
	public static String fill(final MOB mob, final String file, final String id, final Map<String, String> vars, final boolean wholeArea, final boolean keepText, final String seedWord, final boolean save)
	{
		final Room here = mob.location();
		if(here == null)
			return "You are nowhere.";
		final Area A = here.getArea();
		final CMFile F = new CMFile(Resources.buildResourcePath(file), mob);
		if(!F.canRead())
			return "Random data file '" + file + "' not found.";
		final List<XMLTag> xmlRoot = CMLib.xml().parseAllXML(F.textUnformatted());
		final Hashtable<String, Object> defined = new Hashtable<String, Object>();
		for(final Map.Entry<String, String> e : vars.entrySet())
			defined.put(e.getKey().toUpperCase(), e.getValue());
		CMLib.percolator().buildDefinedIDSet(xmlRoot, defined, new XTreeSet<String>(defined.keySet()));
		final Object o = defined.get(id.toUpperCase().trim());
		if((!(o instanceof XMLTag)) || (!((XMLTag)o).tag().equalsIgnoreCase("ROOM")))
			return "There is no ROOM '" + id + "' in " + file + ".";
		final XMLTag piece = (XMLTag)o;
		for(final Map.Entry<String, String> e : vars.entrySet())
			defined.put(e.getKey().toUpperCase(), e.getValue());
		try
		{
			CMLib.percolator().checkRequirements(piece, defined);
		}
		catch(final CMException cme)
		{
			return "Required ids for " + id + " were missing: " + cme.getMessage();
		}
		final long seed = GenSeed.pick(seedWord, null);
		defined.put("GEN_SEED", "" + seed);
		defined.put("AREA_NAME", A.Name());
		GenSeed.context(defined);
		final Map<Room, Map<String, String>> depth = depthTags(A, defined);
		final List<Room> targets = new ArrayList<Room>();
		if(wholeArea)
		{
			for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
				targets.add(r.nextElement());
			Collections.sort(targets, new Comparator<Room>()
			{
				@Override
				public int compare(final Room a, final Room b)
				{
					final int ia = depth.containsKey(a) ? CMath.s_int(depth.get(a).get("INDEX")) : 0;
					final int ib = depth.containsKey(b) ? CMath.s_int(depth.get(b).get("INDEX")) : 0;
					return Integer.compare(ia, ib);
				}
			});
		}
		else
			targets.add(here);
		int filled = 0, kept = 0, none = 0;
		final List<String> problems = new ArrayList<String>();
		final Long before = GenSeed.begin(seed);
		try
		{
			for(final Room R : targets)
			{
				final Hashtable<String, Object> rd = new Hashtable<String, Object>(defined);
				nodeTags(R, A, rd);
				if(depth.containsKey(R))
				{
					for(final Map.Entry<String, String> e : depth.get(R).entrySet())
						rd.put("ROOMTAG_" + e.getKey(), e.getValue());
				}
				rd.put("ROOM_ID", R.roomID());
				try
				{
					final List<XMLTag> choices = CMLib.percolator().getAllChoices("ROOM", piece, rd);
					if((choices == null) || (choices.size() == 0))
					{
						none++;
						continue;
					}
					final XMLTag valPiece = choices.get(CMLib.dice().roll(1, choices.size(), -1));
					final boolean keep = keepText || GenSource.isLocked(R);
					CMLib.percolator().fillRoom(R, valPiece, rd, keep);
					CMLib.percolator().postProcess(rd);
					if(!GenSource.backticks())
						GenSource.untick(R);
					if(keep)
						kept++;
					filled++;
					Unsaved.mark(R, "GENERATE");
					if(save)
					{
						CMLib.database().DBUpdateRoom(R);
						CMLib.database().DBUpdateItems(R);
						CMLib.database().DBUpdateMOBs(R);
					}
				}
				catch(final CMException cex)
				{
					problems.add(R.roomID() + ": " + cex.getMessage());
				}
			}
		}
		finally
		{
			GenSeed.end(before);
		}
		Log.sysOut("Generate", mob.Name() + " filled " + filled + " rooms of " + A.Name() + " from " + id);
		final StringBuilder str = new StringBuilder("Filled " + filled + " room" + ((filled == 1) ? "" : "s") + " from " + id + " (seed " + seed + ")");
		if(kept > 0)
			str.append(", ").append(kept).append(" keeping their text");
		if(none > 0)
			str.append(", ").append(none).append(" the template had nothing for");
		str.append('.');
		for(final String p : problems)
			str.append("\n\r").append(p);
		return str.toString();
	}
}
