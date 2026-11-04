package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * I10: USE [ITEM] (ON [TARGET]): USE_PROG on either, then the table of
 * world/combinations.txt (core/Combinations).
 */
public class Use extends StdCommand
{
	public Use()
	{
	}

	private final String[] access = I(new String[] { "USE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(mob.location() == null)
			return false;
		final String rest = CMParms.combine(commands, 1).trim();
		if(rest.length() == 0)
		{
			mob.tell(L("Use what (on what)?"));
			return false;
		}
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(.+?)\\s+(?:on|with|in)\\s+(.+)$").matcher(rest);
		final String what = m.matches() ? m.group(1).trim() : rest;
		final String on = m.matches() ? m.group(2).trim() : "";
		final Environmental A = Waypoints.target(mob, what);
		if(on.length()==0&&Waypoints.use(mob,A)) return false;
		if(!(A instanceof Item))
		{
			mob.tell(L("You don't have '@x1'.", what));
			return false;
		}
		Environmental B = null;
		if(on.length() > 0)
		{
			B = Combinations.thing(mob, on);
			if(B == null)
			{
				mob.tell(L("You don't see '@x1' here.", on));
				return false;
			}
		}
		return Combinations.act(mob, "USE", A, B);
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> cmds)
	{
		return 1.0;
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		return 1.0;
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}
}
