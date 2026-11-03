package com.planet_ink.coffee_mud.Libraries.layouts;
import java.util.*;

import com.planet_ink.coffee_mud.core.Directions;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutNode;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutRuns;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutTypes;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutTags;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.CMath;

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
 * G16: what the grid layouts (CAVE, CLUMP, LINEAR, RING, ISLANDS) share:
 * their own map of cells to nodes, links both ways, the entrance on the
 * side the area is entered from, straight streets' runs, and the layout's
 * flags filled in at the end.
 */
public abstract class GridLayoutBase extends AbstractLayout
{
	protected final Map<String, LayoutNode>	grid	= new HashMap<String, LayoutNode>();
	protected final List<LayoutNode>		nodes	= new Vector<LayoutNode>();

	protected static final int[]	NSEW	= { Directions.NORTH, Directions.SOUTH, Directions.EAST, Directions.WEST };

	protected static String key(final long x, final long y)
	{
		return x + "," + y;
	}

	protected void reset()
	{
		grid.clear();
		nodes.clear();
	}

	protected LayoutNode at(final long x, final long y)
	{
		return grid.get(key(x, y));
	}

	protected LayoutNode add(final long x, final long y, final LayoutTypes t)
	{
		if(at(x, y) != null)
			return null;
		final LayoutNode n = new DefaultLayoutNode(new long[] { x, y });
		n.reType(t);
		grid.put(key(x, y), n);
		nodes.add(n);
		return n;
	}

	protected static long[] step(final long[] c, final int d)
	{
		switch(d)
		{
		case Directions.NORTH:
			return new long[] { c[0], c[1] - 1 };
		case Directions.SOUTH:
			return new long[] { c[0], c[1] + 1 };
		case Directions.EAST:
			return new long[] { c[0] + 1, c[1] };
		case Directions.WEST:
			return new long[] { c[0] - 1, c[1] };
		default:
			return new long[] { c[0], c[1] };
		}
	}

	protected LayoutNode next(final LayoutNode n, final int d)
	{
		final long[] c = step(n.coord(), d);
		return at(c[0], c[1]);
	}

	protected static void link(final LayoutNode a, final int d, final LayoutNode b)
	{
		if((a == null) || (b == null) || (a == b))
			return;
		a.links().put(Integer.valueOf(d), b);
		b.links().put(Integer.valueOf(Directions.getOpDirectionCode(d)), a);
	}

	/** @return 0 to n-1, from the game's (or a seeded build's) rolls */
	protected static int rnd(final int n)
	{
		return (n <= 1) ? 0 : CMLib.dice().roll(1, n, -1);
	}

	/**
	 * @param dir the direction the area is made in
	 * @return the way the layout runs away from the entrance: dir if it is a compass one, else north
	 */
	protected static int away(final int dir)
	{
		for(final int d : NSEW)
		{
			if(d == dir)
				return d;
		}
		return Directions.NORTH;
	}

	/**
	 * @param dir the direction the area is made in
	 * @return the node furthest toward the way in, which has nothing on that side
	 */
	protected LayoutNode entrance(final int dir)
	{
		final int toward = Directions.getOpDirectionCode(away(dir));
		LayoutNode best = null;
		for(final LayoutNode n : nodes)
		{
			if(best == null)
			{
				best = n;
				continue;
			}
			final long[] c = n.coord(), b = best.coord();
			switch(toward)
			{
			case Directions.SOUTH:
				if(c[1] > b[1])
					best = n;
				break;
			case Directions.NORTH:
				if(c[1] < b[1])
					best = n;
				break;
			case Directions.EAST:
				if(c[0] > b[0])
					best = n;
				break;
			default:
				if(c[0] < b[0])
					best = n;
				break;
			}
		}
		return best;
	}

	/**
	 * The types from the links where none was set to stay, straight streets'
	 * runs, the flags, and the entrance first.
	 * @param entry the entrance
	 * @param retype true to type every node by its links (leaf, street, square)
	 * @return the nodes
	 */
	protected List<LayoutNode> finish(final LayoutNode entry, final boolean retype)
	{
		for(final LayoutNode n : nodes)
		{
			final int k = n.links().size();
			if(retype)
				n.reType((k <= 1) ? LayoutTypes.leaf : (k == 2) ? LayoutTypes.street : LayoutTypes.square);
			if(k == 2)
			{
				final Iterator<Integer> i = n.links().keySet().iterator();
				final int a = i.next().intValue(), b = i.next().intValue();
				if(Directions.getOpDirectionCode(a) == b)
					n.flagRun(((a == Directions.NORTH) || (a == Directions.SOUTH)) ? LayoutRuns.ns : LayoutRuns.ew);
			}
		}
		new LayoutSet(nodes, Math.max(1, nodes.size())).fillInFlags();
		final List<LayoutNode> out = new Vector<LayoutNode>(nodes);
		if(entry != null)
		{
			out.remove(entry);
			out.add(0, entry);
		}
		return out;
	}

	/**
	 * Grows a blob of cells from one, at random, each joined to the one it
	 * grew from, and to some of its other neighbours.
	 * @param x where it starts
	 * @param y where it starts
	 * @param count how many
	 * @param extraPct the chance, for a neighbour already in, of a second link
	 * @param box cells it may use, or null for any
	 * @return the cells made
	 */
	protected List<LayoutNode> blob(final long x, final long y, final int count, final int extraPct, final Set<String> box)
	{
		final List<LayoutNode> made = new ArrayList<LayoutNode>();
		final LayoutNode first = add(x, y, LayoutTypes.street);
		if(first == null)
			return made;
		made.add(first);
		int guard = 0;
		while((made.size() < count) && (guard++ < (count * 50)))
		{
			final LayoutNode from = made.get(rnd(made.size()));
			final int d = NSEW[rnd(4)];
			final long[] c = step(from.coord(), d);
			if((box != null) && (!box.contains(key(c[0], c[1]))))
				continue;
			if(at(c[0], c[1]) != null)
				continue;
			final LayoutNode n = add(c[0], c[1], LayoutTypes.street);
			link(from, d, n);
			for(final int d2 : NSEW)
			{
				final LayoutNode m = next(n, d2);
				if((m != null) && (m != from) && made.contains(m) && (rnd(100) < extraPct))
					link(n, d2, m);
			}
			made.add(n);
		}
		return made;
	}
}
