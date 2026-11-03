package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutManager;
import com.planet_ink.coffee_mud.Libraries.interfaces.AreaGenerationLibrary.LayoutNode;
import com.planet_ink.coffee_mud.Libraries.layouts.LayoutTester;
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
 * G4: GENLAYOUT layout size [dir] [SEED=n] [LEVELS=n]: a layout drawn as an
 * ASCII map, with a count of each node type and the seed, building nothing.
 */
public class GenLayout extends StdCommand
{
	public GenLayout()
	{
	}

	private final String[]	access	= I(new String[] { "GENLAYOUT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 3)
		{
			mob.tell(L("Draw which layout? GENLAYOUT [LAYOUT] [SIZE] ([DIRECTION]) (SEED=[N]) (LEVELS=[N]).  Layouts: @x1",
				CMParms.toListString(CMLib.percolator().getLayoutManagerNames())));
			return false;
		}
		final String name = commands.get(1).toUpperCase();
		final int size = CMath.s_int(commands.get(2));
		int dir = Directions.NORTH;
		String seedWord = null;
		int levels = 1;
		for(int i = 3; i < commands.size(); i++)
		{
			final String w = commands.get(i);
			if(w.toUpperCase().startsWith("SEED="))
				seedWord = w.substring(5).trim();
			else
			if(w.toUpperCase().startsWith("LEVELS="))
				levels = Math.max(1, CMath.s_int(w.substring(7).trim()));
			else
			if(CMLib.directions().getGoodDirectionCode(w) >= 0)
				dir = CMLib.directions().getGoodDirectionCode(w);
		}
		if((size <= 0) || (CMLib.percolator().getLayoutManager(name) == null) || name.equals("MAP"))
		{
			mob.tell(L("GENLAYOUT needs a layout and a size: @x1 (MAP is drawn in its template).",
				CMParms.toListString(CMLib.percolator().getLayoutManagerNames())));
			return false;
		}
		final long seed = GenSeed.pick(seedWord, null);
		final StringBuilder str = new StringBuilder();
		final Long before = GenSeed.begin(seed);
		try
		{
			for(int l = 0; l < levels; l++)
			{
				final LayoutManager L = CMLib.percolator().getLayoutManager(name);
				final int n = Math.max(2, (size / levels) + ((l < (size % levels)) ? 1 : 0));
				final List<LayoutNode> V = L.generate(n, dir);
				if(levels > 1)
					str.append(L("Floor @x1:", "" + (l + 1))).append("\n\r");
				str.append(LayoutTester.drawText(L, V, dir));
			}
		}
		finally
		{
			GenSeed.end(before);
		}
		str.append(L("(O is the entrance; the letters are the node types and flags.)  Seed @x1: SEED=@x1 draws the same again.", "" + seed));
		mob.tell(str.toString().replace("\n", "\n\r").replace("\r\r", "\r"));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDAREAS);
	}
}
