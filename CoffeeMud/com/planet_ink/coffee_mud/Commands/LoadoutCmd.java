package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.*;
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
 * A19: LOADOUT lists the loadouts kept; LOADOUT SAVE [name], LOADOUT [name]
 * (the swap, all or nothing), LOADOUT SHOW [name], LOADOUT FORGET [name].
 * Off unless the world's LOADOUTS= is above 0.  A swap costs LOADOUTCOST
 * actions for each thing it changes.
 */
public class LoadoutCmd extends StdCommand
{
	public LoadoutCmd()
	{
	}

	private final String[] access = I(new String[] { "LOADOUT", "LOADOUTS" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** the loadout a swap names, or null when the command is not a swap */
	private static String swapName(final List<String> commands)
	{
		if(commands.size() < 2)
			return null;
		final String w = commands.get(1).toUpperCase();
		if(w.equals("SAVE") || w.equals("SHOW") || w.equals("FORGET") || w.equals("LIST"))
			return null;
		return CMParms.combine(commands, (w.equals("USE") && (commands.size() > 2)) ? 2 : 1);
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> cmds)
	{
		final String name = Loadouts.enabled() ? swapName(cmds) : null;
		return (name == null) ? 0.0 : Loadouts.cost(mob, name);
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> cmds)
	{
		return actionsCost(mob, cmds);
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(!Loadouts.enabled())
		{
			mob.tell(L("There are no loadouts in this world."));
			return false;
		}
		if((commands.size() < 2) || commands.get(1).equalsIgnoreCase("LIST"))
		{
			mob.tell(Loadouts.list(mob));
			return false;
		}
		final String w = commands.get(1).toUpperCase();
		final String rest = CMParms.combine(commands, 2);
		if(w.equals("SAVE"))
			mob.tell(Loadouts.save(mob, rest));
		else
		if(w.equals("SHOW"))
			mob.tell(Loadouts.show(mob, rest));
		else
		if(w.equals("FORGET"))
			mob.tell(Loadouts.forget(mob, rest));
		else
			mob.tell(Loadouts.use(mob, swapName(commands)));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
