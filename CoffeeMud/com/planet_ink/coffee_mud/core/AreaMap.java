package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
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
 * B10: a map of the rooms around a builder, or of a whole area, laid out by
 * walking their exits from the builder's room: each room a cell, north up,
 * the eight compass ways drawn between them.  With IDS each cell has the
 * number of its room, listed below with its ID and title; with FLAGS the
 * marks show what to look at: &gt; or &lt; (or v and ^) an exit that does not
 * lead back, + a locked door, @ an exit to another area, s a room with
 * scripts, and the rooms of the area not reached at all.  It may also be
 * written as a page of HTML, each room with its exits as links.
 */
public final class AreaMap
{
	private AreaMap()
	{
	}

	private static final int[]	DX		= new int[11];
	private static final int[]	DY		= new int[11];
	private static final int	CW		= 4;
	static
	{
		set(Directions.NORTH, 0, -1);
		set(Directions.SOUTH, 0, 1);
		set(Directions.EAST, 1, 0);
		set(Directions.WEST, -1, 0);
		set(Directions.NORTHEAST, 1, -1);
		set(Directions.NORTHWEST, -1, -1);
		set(Directions.SOUTHEAST, 1, 1);
		set(Directions.SOUTHWEST, -1, 1);
	}

	private static void set(final int d, final int dx, final int dy)
	{
		DX[d] = dx;
		DY[d] = dy;
	}

	private static boolean flat(final int d)
	{
		return (d >= 0) && (d < DX.length) && ((DX[d] != 0) || (DY[d] != 0));
	}

	/** where each room is, and what could not be placed */
	public static final class Layout
	{
		/** each room's cell, x and y */
		public final Map<Room, int[]>	at			= new LinkedHashMap<Room, int[]>();
		/** rooms reached whose cell was another's */
		public final List<Room>			overlaps	= new ArrayList<Room>();
		/** the area's rooms not reached from the start at all */
		public final List<Room>			unreached	= new ArrayList<Room>();
	}

	/**
	 * Lays rooms out by walking their exits from a room, within an area.
	 * @param start the room
	 * @param A the area kept to
	 * @param radius how many steps out, or -1 for all
	 * @return the layout
	 */
	public static Layout layout(final Room start, final Area A, final int radius)
	{
		final Layout L = new Layout();
		final Map<String, Room> taken = new HashMap<String, Room>();
		final LinkedList<Object[]> queue = new LinkedList<Object[]>();
		final Set<Room> seen = new HashSet<Room>();
		L.at.put(start, new int[] { 0, 0 });
		taken.put("0,0", start);
		seen.add(start);
		queue.add(new Object[] { start, Integer.valueOf(0) });
		while(!queue.isEmpty())
		{
			final Object[] o = queue.removeFirst();
			final Room R = (Room)o[0];
			final int steps = ((Integer)o[1]).intValue();
			if((radius >= 0) && (steps >= radius))
				continue;
			final int[] p = L.at.get(R);
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Room R2 = R.rawDoors()[d];
				if((R2 == null) || seen.contains(R2) || (R2.getArea() != A) || (!flat(d)) || (!real(R2)))
					continue;
				seen.add(R2);
				final int x = p[0] + DX[d];
				final int y = p[1] + DY[d];
				final String key = x + "," + y;
				if(taken.containsKey(key))
				{
					L.overlaps.add(R2);
					continue;
				}
				taken.put(key, R2);
				L.at.put(R2, new int[] { x, y });
				queue.add(new Object[] { R2, Integer.valueOf(steps + 1) });
			}
			// a room above or below is reached, but has no cell
			for(final int d : new int[] { Directions.UP, Directions.DOWN })
			{
				final Room R2 = R.rawDoors()[d];
				if((R2 != null) && (!seen.contains(R2)) && (R2.getArea() == A) && real(R2))
				{
					seen.add(R2);
					L.overlaps.add(R2);
				}
			}
		}
		if(radius < 0)
		{
			for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
			{
				final Room R = r.nextElement();
				if((R != null) && real(R) && (!seen.contains(R)) && (!reachable(start, R, A)))
					L.unreached.add(R);
			}
		}
		return L;
	}

	/** whether a room is one of the map's, with an ID, not a sky or such made on the fly */
	private static boolean real(final Room R)
	{
		final String id = CMLib.map().getExtendedRoomID(R);
		return (id != null) && (id.length() > 0);
	}

	/** whether a room can be walked to from another in an area, any way at all */
	private static boolean reachable(final Room from, final Room to, final Area A)
	{
		final Set<Room> seen = new HashSet<Room>();
		final LinkedList<Room> queue = new LinkedList<Room>();
		queue.add(from);
		seen.add(from);
		while(!queue.isEmpty())
		{
			final Room R = queue.removeFirst();
			if(R == to)
				return true;
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Room R2 = R.rawDoors()[d];
				if((R2 != null) && (R2.getArea() == A) && real(R2) && seen.add(R2))
					queue.add(R2);
			}
		}
		return false;
	}

	/**
	 * Whether a room, or a mob in it, has a script.
	 * @param R the room
	 * @return true if so
	 */
	public static boolean scripted(final Room R)
	{
		if(R.numScripts() > 0)
			return true;
		for(final Enumeration<Behavior> b = R.behaviors(); b.hasMoreElements();)
		{
			if(b.nextElement() instanceof ScriptingEngine)
				return true;
		}
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (!M.isMonster()))
				continue;
			if(M.numScripts() > 0)
				return true;
			for(final Enumeration<Behavior> b = M.behaviors(); b.hasMoreElements();)
			{
				if(b.nextElement() instanceof ScriptingEngine)
					return true;
			}
		}
		return false;
	}

	private static String number(final Room R, final int index)
	{
		final String id = CMLib.map().getExtendedRoomID(R);
		final int x = id.lastIndexOf('#');
		final String n = (x >= 0) ? id.substring(x + 1) : "";
		return (CMath.isInteger(n) && (n.length() <= 3)) ? n : ("" + index);
	}

	private static void put(final char[][] grid, final int row, final int col, final char c)
	{
		if((row >= 0) && (row < grid.length) && (col >= 0) && (col < grid[row].length) && (grid[row][col] == ' '))
			grid[row][col] = c;
	}

	/**
	 * The map as text.
	 * @param here the builder's room, marked *
	 * @param L the layout
	 * @param A the area
	 * @param ids true to number the rooms and list them
	 * @param flags true to mark what to look at
	 * @return the map
	 */
	public static String ascii(final Room here, final Layout L, final Area A, final boolean ids, final boolean flags)
	{
		int minX = 0, maxX = 0, minY = 0, maxY = 0;
		for(final int[] p : L.at.values())
		{
			minX = Math.min(minX, p[0]);
			maxX = Math.max(maxX, p[0]);
			minY = Math.min(minY, p[1]);
			maxY = Math.max(maxY, p[1]);
		}
		final int cols = (maxX - minX + 1) * (CW + 1);
		final int rows = (maxY - minY + 1) * 2;
		final char[][] grid = new char[rows][cols];
		for(final char[] row : grid)
			Arrays.fill(row, ' ');
		final List<String> legend = new ArrayList<String>();
		int index = 0;
		for(final Map.Entry<Room, int[]> e : L.at.entrySet())
		{
			final Room R = e.getKey();
			final int row = (e.getValue()[1] - minY) * 2;
			final int col = (e.getValue()[0] - minX) * (CW + 1);
			index++;
			String label = (R == here) ? "*" : "o";
			if(ids)
			{
				label = number(R, index);
				legend.add(CMStrings.padRight(label, 4) + CMLib.map().getExtendedRoomID(R) + "  " + CMStrings.removeColors(R.displayText()));
				if(R == here)
					label = label + "*";
			}
			for(int i = 0; (i < label.length()) && (i < CW - 1); i++)
				grid[row][col + i] = label.charAt(i);
			if(flags && scripted(R))
				grid[row][col + CW - 1] = 's';
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				if(!flat(d))
					continue;
				final Room R2 = R.rawDoors()[d];
				final Exit X = R.getRawExit(d);
				if((R2 == null) && (X == null))
					continue;
				// where the way is drawn: beside, below, or the corners
				final int cr = row + ((DY[d] > 0) ? 1 : ((DY[d] < 0) ? -1 : 0));
				final int cc = (DX[d] > 0) ? (col + CW) : ((DX[d] < 0) ? (col - 1) : (col + 1));
				char c;
				if(DX[d] == 0)
					c = '|';
				else
				if(DY[d] == 0)
					c = '-';
				else
					c = ((DX[d] * DY[d]) > 0) ? '\\' : '/';
				if(flags)
				{
					final boolean back = (R2 != null) && (R2.rawDoors()[Directions.getOpDirectionCode(d)] == R);
					if((R2 != null) && (R2.getArea() != A))
						c = '@';
					else
					if((X != null) && X.hasALock())
						c = '+';
					else
					if((R2 != null) && (!back) && (DY[d] == 0))
						c = (DX[d] > 0) ? '>' : '<';
					else
					if((R2 != null) && (!back) && (DX[d] == 0))
						c = (DY[d] > 0) ? 'v' : '^';
				}
				put(grid, cr, cc, c);
			}
		}
		final StringBuilder str = new StringBuilder();
		for(final char[] row : grid)
		{
			final String line = new String(row).replaceAll("\\s+$", "");
			str.append(line).append("\n\r");
		}
		while(str.toString().endsWith("\n\r\n\r"))
			str.setLength(str.length() - 2);
		if(ids)
		{
			str.append("\n\r");
			for(final String l : legend)
				str.append(l).append("\n\r");
		}
		if(L.overlaps.size() > 0)
		{
			final List<String> names = new ArrayList<String>();
			for(final Room R : L.overlaps)
				names.add(CMLib.map().getExtendedRoomID(R));
			str.append("Not drawn (above, below, or where another room is): ").append(String.join(", ", names)).append("\n\r");
		}
		if(flags && (L.unreached.size() > 0))
		{
			final List<String> names = new ArrayList<String>();
			for(final Room R : L.unreached)
				names.add(CMLib.map().getExtendedRoomID(R));
			str.append("! Not reached from here at all: ").append(String.join(", ", names)).append("\n\r");
		}
		return str.toString();
	}

	private static String esc(final String s)
	{
		return CMStrings.removeColors((s == null) ? "" : s).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
	}

	/**
	 * The map as a page of HTML: a table, a cell for each room with its
	 * title, ID and exits, each a link to its room's cell.
	 * @param L the layout
	 * @param A the area
	 * @return the page
	 */
	public static String html(final Layout L, final Area A)
	{
		int minX = 0, maxX = 0, minY = 0, maxY = 0;
		for(final int[] p : L.at.values())
		{
			minX = Math.min(minX, p[0]);
			maxX = Math.max(maxX, p[0]);
			minY = Math.min(minY, p[1]);
			maxY = Math.max(maxY, p[1]);
		}
		final Map<String, Room> cell = new HashMap<String, Room>();
		for(final Map.Entry<Room, int[]> e : L.at.entrySet())
			cell.put(e.getValue()[0] + "," + e.getValue()[1], e.getKey());
		final StringBuilder str = new StringBuilder();
		str.append("<!DOCTYPE html>\n<html><head><meta charset=\"utf-8\"><title>").append(esc(A.Name())).append("</title>\n");
		str.append("<style>body{font-family:sans-serif;background:#fff;color:#222}table{border-collapse:collapse}td{border:1px solid #ccc;"
			+ "vertical-align:top;padding:4px;min-width:9em;font-size:12px}td.e{border:none}.id{color:#666}a{color:#24c}</style>\n");
		str.append("</head><body><h1>").append(esc(A.Name())).append("</h1>\n<table>\n");
		for(int y = minY; y <= maxY; y++)
		{
			str.append("<tr>");
			for(int x = minX; x <= maxX; x++)
			{
				final Room R = cell.get(x + "," + y);
				if(R == null)
				{
					str.append("<td class=\"e\"></td>");
					continue;
				}
				final String id = CMLib.map().getExtendedRoomID(R);
				str.append("<td id=\"").append(esc(id)).append("\"><b>").append(esc(R.displayText())).append("</b><br><span class=\"id\">")
					.append(esc(id)).append("</span>");
				for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				{
					final Room R2 = R.rawDoors()[d];
					if(R2 == null)
						continue;
					final String to = CMLib.map().getExtendedRoomID(R2);
					str.append("<br>").append(esc(CMLib.directions().getDirectionName(d))).append(": <a href=\"#").append(esc(to)).append("\">")
						.append(esc(to)).append("</a>");
				}
				str.append("</td>");
			}
			str.append("</tr>\n");
		}
		str.append("</table>\n");
		if(L.unreached.size() > 0)
		{
			str.append("<p>Not reached from the start: ");
			final List<String> names = new ArrayList<String>();
			for(final Room R : L.unreached)
				names.add(esc(CMLib.map().getExtendedRoomID(R)));
			str.append(String.join(", ", names)).append("</p>\n");
		}
		str.append("</body></html>\n");
		return str.toString();
	}
}
