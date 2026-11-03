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
 * G16: RING, a loop with spokes, for wall walks: a square of streets around
 * an open middle, the entrance outside it, and short spokes in from its
 * sides.
 */
public class RingLayout extends GridLayoutBase
{
	@Override
	public String name()
	{
		return "RING";
	}

	protected static int dirTo(final long[] a, final long[] b)
	{
		if(b[0] > a[0])
			return Directions.EAST;
		if(b[0] < a[0])
			return Directions.WEST;
		if(b[1] > a[1])
			return Directions.SOUTH;
		return Directions.NORTH;
	}

	@Override
	public List<LayoutNode> generate(final int num, final int dir)
	{
		reset();
		final int ring = Math.max(4, (num * 3) / 4);
		final int s = Math.max(2, (ring / 4) + 1);
		final List<long[]> per = new ArrayList<long[]>();
		for(int x = 0; x < s; x++)
			per.add(new long[] { x, 0 });
		for(int y = 1; y < s; y++)
			per.add(new long[] { s - 1, y });
		for(int x = s - 2; x >= 0; x--)
			per.add(new long[] { x, s - 1 });
		for(int y = s - 2; y >= 1; y--)
			per.add(new long[] { 0, y });
		LayoutNode first = null, prev = null;
		for(final long[] c : per)
		{
			final LayoutNode n = add(c[0], c[1], LayoutTypes.street);
			if(first == null)
				first = n;
			if(prev != null)
				link(prev, dirTo(prev.coord(), n.coord()), n);
			prev = n;
		}
		if((prev != null) && (prev != first))
			link(prev, dirTo(prev.coord(), first.coord()), first);
		final int toward = Directions.getOpDirectionCode(away(dir));
		final long mid = s / 2;
		final long[] gate, onRing;
		switch(toward)
		{
		case Directions.SOUTH:
			gate = new long[] { mid, s };
			onRing = new long[] { mid, s - 1 };
			break;
		case Directions.NORTH:
			gate = new long[] { mid, -1 };
			onRing = new long[] { mid, 0 };
			break;
		case Directions.EAST:
			gate = new long[] { s, mid };
			onRing = new long[] { s - 1, mid };
			break;
		default:
			gate = new long[] { -1, mid };
			onRing = new long[] { 0, mid };
			break;
		}
		final LayoutNode ringNode = at(onRing[0], onRing[1]);
		final LayoutNode entry = add(gate[0], gate[1], LayoutTypes.street);
		link(entry, dirTo(gate, onRing), ringNode);
		int left = num - nodes.size();
		int guard = 0;
		while((left > 0) && (guard++ < (num * 20)))
		{
			final LayoutNode from = nodes.get(rnd(per.size()));
			if(from == ringNode)
				continue;
			final long[] c = from.coord();
			final int in;
			if((c[1] == 0) && (c[0] > 0) && (c[0] < s - 1))
				in = Directions.SOUTH;
			else
			if((c[1] == s - 1) && (c[0] > 0) && (c[0] < s - 1))
				in = Directions.NORTH;
			else
			if((c[0] == 0) && (c[1] > 0) && (c[1] < s - 1))
				in = Directions.EAST;
			else
			if((c[0] == s - 1) && (c[1] > 0) && (c[1] < s - 1))
				in = Directions.WEST;
			else
				continue;
			// in toward the middle when there is room, else out
			final int d = ((s > 4) || (rnd(2) == 0)) ? in : Directions.getOpDirectionCode(in);
			final long[] c1 = step(c, d);
			if((at(c1[0], c1[1]) != null) || (next(from, d) != null))
				continue;
			final LayoutNode n = add(c1[0], c1[1], LayoutTypes.leaf);
			link(from, d, n);
			left--;
			if((left > 0) && (s > 6) && (rnd(2) == 0))
			{
				final long[] c2 = step(c1, d);
				if(at(c2[0], c2[1]) == null)
				{
					link(n, d, add(c2[0], c2[1], LayoutTypes.leaf));
					left--;
				}
			}
		}
		return finish(entry, true);
	}
}
