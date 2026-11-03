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
 * G16: LINEAR, a road with side spurs: a street running away from the
 * entrance, two thirds of the rooms, with spurs of one or two rooms off
 * either side.
 */
public class LinearLayout extends GridLayoutBase
{
	@Override
	public String name()
	{
		return "LINEAR";
	}

	@Override
	public List<LayoutNode> generate(final int num, final int dir)
	{
		reset();
		final int way = away(dir);
		final int side1 = ((way == Directions.NORTH) || (way == Directions.SOUTH)) ? Directions.EAST : Directions.NORTH;
		final int side2 = Directions.getOpDirectionCode(side1);
		final int roadLen = Math.max(2, Math.min(num, ((num * 2) + 2) / 3));
		LayoutNode prev = add(0, 0, LayoutTypes.street);
		final LayoutNode entry = prev;
		final List<LayoutNode> road = new ArrayList<LayoutNode>();
		road.add(prev);
		for(int i = 1; i < roadLen; i++)
		{
			final long[] c = step(prev.coord(), way);
			final LayoutNode n = add(c[0], c[1], LayoutTypes.street);
			link(prev, way, n);
			road.add(n);
			prev = n;
		}
		int left = num - nodes.size();
		int guard = 0;
		while((left > 0) && (guard++ < (num * 20)))
		{
			final LayoutNode from = road.get(1 + rnd(road.size() - 1));
			final int s = (rnd(2) == 0) ? side1 : side2;
			final long[] c = step(from.coord(), s);
			if(at(c[0], c[1]) != null)
				continue;
			final LayoutNode n = add(c[0], c[1], LayoutTypes.leaf);
			link(from, s, n);
			left--;
			if((left > 0) && (rnd(100) < 35))
			{
				final long[] c2 = step(n.coord(), s);
				if(at(c2[0], c2[1]) == null)
				{
					link(n, s, add(c2[0], c2[1], LayoutTypes.leaf));
					left--;
				}
			}
		}
		return finish(entry, true);
	}
}
