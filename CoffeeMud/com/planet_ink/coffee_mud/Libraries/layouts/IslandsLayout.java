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
 * G16: ISLANDS, clusters joined by single bridges: blobs of rooms in a row
 * running away from the entrance, each joined to the next by one bridge
 * room.
 */
public class IslandsLayout extends GridLayoutBase
{
	@Override
	public String name()
	{
		return "ISLANDS";
	}

	/** from along (u) and across (v) the way the islands run, to the grid */
	protected static long[] xy(final int way, final long u, final long v)
	{
		switch(way)
		{
		case Directions.SOUTH:
			return new long[] { v, u };
		case Directions.EAST:
			return new long[] { u, v };
		case Directions.WEST:
			return new long[] { -u, v };
		default:
			return new long[] { v, -u };
		}
	}

	@Override
	public List<LayoutNode> generate(final int num, final int dir)
	{
		reset();
		final int way = away(dir);
		final int k = Math.max(2, num / 7);
		final int bridges = k - 1;
		final int each = Math.max(2, (num - bridges) / k);
		final int span = (int)Math.ceil(Math.sqrt(each)) + 1;
		LayoutNode entry = null;
		long startV = span / 2;
		for(int i = 0; i < k; i++)
		{
			final long u0 = i * (long)(span + 1);
			final long u1 = u0 + span - 1;
			final Set<String> box = new HashSet<String>();
			for(long u = u0; u <= u1; u++)
			{
				for(long v = 0; v < span; v++)
				{
					final long[] c = xy(way, u, v);
					box.add(key(c[0], c[1]));
				}
			}
			final int count = (i == k - 1) ? Math.max(2, num - nodes.size()) : each;
			final long[] s = xy(way, u0, startV);
			final List<LayoutNode> island = blob(s[0], s[1], count, 30, box);
			if(island.size() == 0)
				break;
			if(entry == null)
				entry = island.get(0);
			else
			{
				// the bridge from the last island's far edge
				final long[] b = xy(way, u0 - 1, startV);
				final LayoutNode bridge = add(b[0], b[1], LayoutTypes.street);
				final long[] back = xy(way, u0 - 2, startV);
				link(at(back[0], back[1]), way, bridge);
				link(bridge, way, island.get(0));
			}
			if(i == k - 1)
				break;
			// a room on this island's far edge, for the next bridge
			LayoutNode far = null;
			long farU = Long.MIN_VALUE;
			for(final LayoutNode n : island)
			{
				final long u = (way == Directions.NORTH) ? -n.coord()[1] : (way == Directions.SOUTH) ? n.coord()[1] : (way == Directions.EAST) ? n.coord()[0] : -n.coord()[0];
				if(u > farU)
				{
					farU = u;
					far = n;
				}
			}
			while((far != null) && (farU < u1))
			{
				final long[] c = step(far.coord(), way);
				LayoutNode n = at(c[0], c[1]);
				if(n == null)
					n = add(c[0], c[1], LayoutTypes.street);
				link(far, way, n);
				far = n;
				farU++;
			}
			if(far == null)
				break;
			startV = ((way == Directions.NORTH) || (way == Directions.SOUTH)) ? far.coord()[0] : far.coord()[1];
		}
		return finish(entry, true);
	}
}
