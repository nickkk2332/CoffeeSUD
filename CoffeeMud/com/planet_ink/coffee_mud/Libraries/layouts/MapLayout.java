package com.planet_ink.coffee_mud.Libraries.layouts;
import java.util.*;

import com.planet_ink.coffee_mud.core.Directions;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutNode;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutRuns;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutTypes;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutTags;
import com.planet_ink.coffee_mud.core.CMLib;

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
 * G8: layout="MAP", a floor plan drawn as characters, in an AREA's &lt;MAP&gt;
 * child or the file its MAPFILE names.  Each character that is not a link or
 * a space is a room: . a street, o an interior room, L a leaf, @ the
 * entrance, ^ and v stairs up and down, and any other a room whose GLYPH tag
 * names its template.  Rooms side by side are joined; - and | join rooms a
 * space apart, + does so with a door, and / and \ join them on the
 * diagonal.  A line of = starts the next floor up, and a ^ is joined to the
 * v in the same column on the floor above (the nearest, by row).  The size
 * of the area is the number of rooms drawn.
 */
public class MapLayout extends AbstractLayout
{
	@Override
	public String name()
	{
		return "MAP";
	}

	protected String								map		= "";
	protected final Map<LayoutNode, Character>		glyphs	= new HashMap<LayoutNode, Character>();
	protected final Map<LayoutNode, Set<Integer>>	doors	= new HashMap<LayoutNode, Set<Integer>>();
	protected final Map<LayoutNode, Integer>		floorOf	= new HashMap<LayoutNode, Integer>();

	/**
	 * G16: the floor a node was drawn on.
	 * @param n a node this made
	 * @return 1 for the first floor drawn, 2 for the one above, and so on
	 */
	public int levelOf(final LayoutNode n)
	{
		final Integer f = floorOf.get(n);
		return (f == null) ? 1 : (f.intValue() + 1);
	}

	/**
	 * @param text the map, as drawn
	 */
	public void setMap(final String text)
	{
		map = (text == null) ? "" : text;
	}

	/**
	 * @param n a node this made
	 * @return the character it was drawn with, or a space
	 */
	public char glyphOf(final LayoutNode n)
	{
		final Character c = glyphs.get(n);
		return (c == null) ? ' ' : c.charValue();
	}

	/**
	 * @param n a node this made
	 * @return the directions drawn with a door, as ,n,e,
	 */
	public String doorsOf(final LayoutNode n)
	{
		final StringBuilder str = new StringBuilder(",");
		final Set<Integer> s = doors.get(n);
		if(s != null)
		{
			for(final Integer d : new TreeSet<Integer>(s))
				str.append(CMLib.directions().getDirectionChar(d.intValue()).toLowerCase()).append(',');
		}
		return str.toString();
	}

	protected static boolean isRoom(final char c)
	{
		return (!Character.isWhitespace(c)) && ("-|+/\\=".indexOf(c) < 0);
	}

	/**
	 * @return the floors, each its lines, with the indentation all its lines share taken off
	 */
	protected List<List<String>> floors()
	{
		final List<List<String>> floors = new ArrayList<List<String>>();
		List<String> cur = new ArrayList<String>();
		for(final String line : map.replace("\r", "").split("\n", -1))
		{
			final String t = line.trim();
			if((t.length() > 0) && t.replace("=", "").length() == 0)
			{
				floors.add(cur);
				cur = new ArrayList<String>();
			}
			else
				cur.add(line.replace('\t', ' '));
		}
		floors.add(cur);
		int indent = Integer.MAX_VALUE;
		for(final List<String> f : floors)
		{
			for(final String l : f)
			{
				if(l.trim().length() == 0)
					continue;
				int i = 0;
				while((i < l.length()) && (l.charAt(i) == ' '))
					i++;
				indent = Math.min(indent, i);
			}
		}
		if(indent == Integer.MAX_VALUE)
			indent = 0;
		for(final List<String> f : floors)
		{
			while((f.size() > 0) && (f.get(0).trim().length() == 0))
				f.remove(0);
			for(int i = 0; i < f.size(); i++)
				f.set(i, (f.get(i).length() >= indent) ? f.get(i).substring(indent) : f.get(i).trim());
		}
		return floors;
	}

	/** @return how many rooms the map draws */
	public int roomCount()
	{
		int n = 0;
		for(final List<String> f : floors())
		{
			for(final String l : f)
			{
				for(int x = 0; x < l.length(); x++)
				{
					if(isRoom(l.charAt(x)))
						n++;
				}
			}
		}
		return n;
	}

	protected static char at(final List<String> f, final int x, final int y)
	{
		if((y < 0) || (y >= f.size()) || (x < 0))
			return ' ';
		final String l = f.get(y);
		return (x < l.length()) ? l.charAt(x) : ' ';
	}

	protected void link(final LayoutNode a, final int dir, final LayoutNode b, final boolean door)
	{
		if((a == null) || (b == null) || (a == b))
			return;
		a.links().put(Integer.valueOf(dir), b);
		b.links().put(Integer.valueOf(Directions.getOpDirectionCode(dir)), a);
		if(door)
		{
			doors.get(a).add(Integer.valueOf(dir));
			doors.get(b).add(Integer.valueOf(Directions.getOpDirectionCode(dir)));
		}
	}

	@Override
	public List<LayoutNode> generate(final int num, final int dir)
	{
		glyphs.clear();
		doors.clear();
		floorOf.clear();
		final List<List<String>> floors = floors();
		final Map<String, LayoutNode> nodes = new HashMap<String, LayoutNode>();
		final List<LayoutNode> set = new Vector<LayoutNode>();
		LayoutNode entry = null;
		for(int fl = 0; fl < floors.size(); fl++)
		{
			final List<String> f = floors.get(fl);
			for(int y = 0; y < f.size(); y++)
			{
				for(int x = 0; x < f.get(y).length(); x++)
				{
					final char c = at(f, x, y);
					if(!isRoom(c))
						continue;
					final LayoutNode n = new DefaultLayoutNode(new long[] { x, y + (fl * 1000) });
					switch(c)
					{
					case '.':
						n.reType(LayoutTypes.street);
						break;
					case 'o':
						n.reType(LayoutTypes.interior);
						break;
					case 'L':
						n.reType(LayoutTypes.leaf);
						break;
					default:
						break;
					}
					nodes.put(fl + "," + x + "," + y, n);
					glyphs.put(n, Character.valueOf(c));
					floorOf.put(n, Integer.valueOf(fl));
					doors.put(n, new HashSet<Integer>());
					set.add(n);
					if((c == '@') && (entry == null))
						entry = n;
				}
			}
		}
		for(int fl = 0; fl < floors.size(); fl++)
		{
			final List<String> f = floors.get(fl);
			for(int y = 0; y < f.size(); y++)
			{
				for(int x = 0; x < f.get(y).length(); x++)
				{
					final LayoutNode n = nodes.get(fl + "," + x + "," + y);
					if(n == null)
						continue;
					if(isRoom(at(f, x + 1, y)))
						link(n, Directions.EAST, nodes.get(fl + "," + (x + 1) + "," + y), false);
					else
					if(("-+".indexOf(at(f, x + 1, y)) >= 0) && isRoom(at(f, x + 2, y)))
						link(n, Directions.EAST, nodes.get(fl + "," + (x + 2) + "," + y), at(f, x + 1, y) == '+');
					if(isRoom(at(f, x, y + 1)))
						link(n, Directions.SOUTH, nodes.get(fl + "," + x + "," + (y + 1)), false);
					else
					if(("|+".indexOf(at(f, x, y + 1)) >= 0) && isRoom(at(f, x, y + 2)))
						link(n, Directions.SOUTH, nodes.get(fl + "," + x + "," + (y + 2)), at(f, x, y + 1) == '+');
					if((at(f, x + 1, y + 1) == '\\') && isRoom(at(f, x + 2, y + 2)))
						link(n, Directions.SOUTHEAST, nodes.get(fl + "," + (x + 2) + "," + (y + 2)), false);
					if((at(f, x - 1, y + 1) == '/') && isRoom(at(f, x - 2, y + 2)))
						link(n, Directions.SOUTHWEST, nodes.get(fl + "," + (x - 2) + "," + (y + 2)), false);
					if((at(f, x, y) == '^') && (fl + 1 < floors.size()))
					{
						// the v in the same column on the floor above, the nearest row
						final List<String> up = floors.get(fl + 1);
						LayoutNode best = null;
						int bestD = Integer.MAX_VALUE;
						for(int y2 = 0; y2 < up.size(); y2++)
						{
							if((at(up, x, y2) == 'v') && (Math.abs(y2 - y) < bestD))
							{
								bestD = Math.abs(y2 - y);
								best = nodes.get((fl + 1) + "," + x + "," + y2);
							}
						}
						link(n, Directions.UP, best, false);
					}
				}
			}
		}
		for(final LayoutNode n : set)
		{
			if(!n.tags().containsKey(LayoutTags.NODETYPE))
				n.reType((n.links().size() <= 1) ? LayoutTypes.leaf : (n.links().size() == 2) ? LayoutTypes.street : LayoutTypes.square);
			if(n.links().size() == 2)
			{
				final Iterator<Integer> i = n.links().keySet().iterator();
				final int a = i.next().intValue(), b = i.next().intValue();
				if(Directions.getOpDirectionCode(a) == b)
				{
					if((a == Directions.NORTH) || (a == Directions.SOUTH))
						n.flagRun(LayoutRuns.ns);
					else
					if((a == Directions.EAST) || (a == Directions.WEST))
						n.flagRun(LayoutRuns.ew);
					else
					if((a == Directions.UP) || (a == Directions.DOWN))
						n.flagRun(LayoutRuns.ud);
					else
					if((a == Directions.NORTHEAST) || (a == Directions.SOUTHWEST))
						n.flagRun(LayoutRuns.nesw);
					else
						n.flagRun(LayoutRuns.nwse);
				}
			}
		}
		new LayoutSet(set, Math.max(1, set.size())).fillInFlags();
		if((entry == null) && (set.size() > 0))
			entry = set.get(0);
		if(entry != null)
		{
			set.remove(entry);
			set.add(0, entry);
		}
		return set;
	}
}
