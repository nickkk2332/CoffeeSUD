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
 * G16: CLUMP, a square with random dead ends: a block of rooms all joined to
 * their neighbours, the entrance outside it, and short dead ends off its
 * sides.
 */
public class ClumpLayout extends GridLayoutBase
{
	@Override
	public String name()
	{
		return "CLUMP";
	}

	@Override
	public List<LayoutNode> generate(final int num, final int dir)
	{
		reset();
		int side = Math.max(2, (int)Math.round(Math.sqrt(Math.max(4, (num * 2) / 3))));
		while((side > 2) && ((side * side) > (num - 1)))
			side--;
		for(int y = 0; y < side; y++)
		{
			for(int x = 0; x < side; x++)
			{
				final LayoutNode n = add(x, y, LayoutTypes.interior);
				link(at(x - 1, y), Directions.EAST, n);
				link(at(x, y - 1), Directions.SOUTH, n);
			}
		}
		final List<LayoutNode> block = new ArrayList<LayoutNode>(nodes);
		final int toward = Directions.getOpDirectionCode(away(dir));
		final long mid = side / 2;
		final LayoutNode onBlock;
		final LayoutNode entry;
		switch(toward)
		{
		case Directions.SOUTH:
			onBlock = at(mid, side - 1);
			entry = add(mid, side, LayoutTypes.street);
			break;
		case Directions.NORTH:
			onBlock = at(mid, 0);
			entry = add(mid, -1, LayoutTypes.street);
			break;
		case Directions.EAST:
			onBlock = at(side - 1, mid);
			entry = add(side, mid, LayoutTypes.street);
			break;
		default:
			onBlock = at(0, mid);
			entry = add(-1, mid, LayoutTypes.street);
			break;
		}
		link(entry, away(dir), onBlock);
		int left = num - nodes.size();
		int guard = 0;
		while((left > 0) && (guard++ < (num * 30)))
		{
			final LayoutNode from = block.get(rnd(block.size()));
			final int d = NSEW[rnd(4)];
			if(next(from, d) != null)
				continue;
			final long[] c = step(from.coord(), d);
			final LayoutNode n = add(c[0], c[1], LayoutTypes.leaf);
			if(n == null)
				continue;
			link(from, d, n);
			left--;
			LayoutNode tip = n;
			while((left > 0) && (rnd(100) < 40))
			{
				final long[] c2 = step(tip.coord(), d);
				if(at(c2[0], c2[1]) != null)
					break;
				final LayoutNode t2 = add(c2[0], c2[1], LayoutTypes.leaf);
				link(tip, d, t2);
				tip = t2;
				left--;
			}
		}
		return finish(entry, true);
	}
}
