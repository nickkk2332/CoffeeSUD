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
 * G16: CAVE, cellular-automata blobs and winding passages: a grid filled at
 * random and smoothed, its largest open region grown to the size, walked
 * from the entrance, each room joined to the one it was reached from and
 * to a quarter of its other open neighbours.
 */
public class CaveLayout extends GridLayoutBase
{
	@Override
	public String name()
	{
		return "CAVE";
	}

	@Override
	public List<LayoutNode> generate(final int num, final int dir)
	{
		reset();
		final int w = Math.max(6, (int)Math.ceil(Math.sqrt(num * 2.5)));
		final int h = w;
		boolean[][] open = new boolean[w][h];
		for(int x = 1; x < w - 1; x++)
		{
			for(int y = 1; y < h - 1; y++)
				open[x][y] = rnd(100) < 45;
		}
		for(int i = 0; i < 4; i++)
		{
			final boolean[][] o2 = new boolean[w][h];
			for(int x = 1; x < w - 1; x++)
			{
				for(int y = 1; y < h - 1; y++)
				{
					int c = 0;
					for(int dx = -1; dx <= 1; dx++)
					{
						for(int dy = -1; dy <= 1; dy++)
						{
							if(((dx != 0) || (dy != 0)) && open[x + dx][y + dy])
								c++;
						}
					}
					o2[x][y] = (c >= 5) || (open[x][y] && (c >= 4));
				}
			}
			open = o2;
		}
		// the largest region, by its four neighbours
		final int[][] region = new int[w][h];
		int best = 0, bestSize = 0, id = 0;
		for(int x = 0; x < w; x++)
		{
			for(int y = 0; y < h; y++)
			{
				if((!open[x][y]) || (region[x][y] != 0))
					continue;
				id++;
				int size = 0;
				final LinkedList<int[]> todo = new LinkedList<int[]>();
				todo.add(new int[] { x, y });
				region[x][y] = id;
				while(todo.size() > 0)
				{
					final int[] c = todo.removeFirst();
					size++;
					for(final int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } })
					{
						final int nx = c[0] + d[0], ny = c[1] + d[1];
						if((nx >= 0) && (ny >= 0) && (nx < w) && (ny < h) && open[nx][ny] && (region[nx][ny] == 0))
						{
							region[nx][ny] = id;
							todo.add(new int[] { nx, ny });
						}
					}
				}
				if(size > bestSize)
				{
					bestSize = size;
					best = id;
				}
			}
		}
		final List<int[]> cells = new ArrayList<int[]>();
		final Set<String> in = new HashSet<String>();
		for(int x = 0; x < w; x++)
		{
			for(int y = 0; y < h; y++)
			{
				if((best != 0) && (region[x][y] == best))
				{
					cells.add(new int[] { x, y });
					in.add(key(x, y));
				}
			}
		}
		if(cells.size() == 0)
		{
			cells.add(new int[] { w / 2, h / 2 });
			in.add(key(w / 2, h / 2));
		}
		// grown to the size, winding out from its edges
		int guard = 0;
		while((cells.size() < num) && (guard++ < (num * 100)))
		{
			final int[] c = cells.get(rnd(cells.size()));
			final int[] d = new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }[rnd(4)];
			final int nx = c[0] + d[0], ny = c[1] + d[1];
			if(in.contains(key(nx, ny)))
				continue;
			cells.add(new int[] { nx, ny });
			in.add(key(nx, ny));
		}
		// walked from the cell nearest the way in
		final int toward = Directions.getOpDirectionCode(away(dir));
		int[] start = cells.get(0);
		for(final int[] c : cells)
		{
			if(((toward == Directions.SOUTH) && (c[1] > start[1]))
			|| ((toward == Directions.NORTH) && (c[1] < start[1]))
			|| ((toward == Directions.EAST) && (c[0] > start[0]))
			|| ((toward == Directions.WEST) && (c[0] < start[0])))
				start = c;
		}
		final LayoutNode entry = add(start[0], start[1], LayoutTypes.street);
		final LinkedList<LayoutNode> todo = new LinkedList<LayoutNode>();
		todo.add(entry);
		while((todo.size() > 0) && (nodes.size() < num))
		{
			final LayoutNode n = todo.removeFirst();
			final List<Integer> dirs = new ArrayList<Integer>();
			for(final int d : NSEW)
				dirs.add(Integer.valueOf(d));
			Collections.shuffle(dirs, CMath.getRand());
			for(final Integer dI : dirs)
			{
				if(nodes.size() >= num)
					break;
				final int d = dI.intValue();
				if((n == entry) && (d == toward))
					continue;
				final long[] c = step(n.coord(), d);
				if((!in.contains(key(c[0], c[1]))) || (at(c[0], c[1]) != null))
					continue;
				final LayoutNode m = add(c[0], c[1], LayoutTypes.street);
				link(n, d, m);
				todo.add(m);
			}
		}
		for(final LayoutNode n : new ArrayList<LayoutNode>(nodes))
		{
			for(final int d : new int[] { Directions.EAST, Directions.SOUTH })
			{
				final LayoutNode m = next(n, d);
				if((m != null) && (!n.links().containsKey(Integer.valueOf(d))) && (rnd(100) < 25))
					link(n, d, m);
			}
		}
		return finish(entry, true);
	}
}
