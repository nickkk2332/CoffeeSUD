package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * P13: PERKS lists the perks a character has, can take and cannot yet (with
 * why), and the picks left; PERK [name] takes one (world/perks.ini).
 */
public class PerkCmd extends StdCommand
{
	public PerkCmd()
	{
	}

	private final String[] access = I(new String[] { "PERKS", "PERK" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size() < 2) || commands.get(0).toUpperCase().startsWith("PERKS"))
		{
			mob.tell(Perks.list(mob));
			return false;
		}
		final String name = CMParms.combine(commands, 1);
		final Perks.Perk K = Perks.perk(name);
		if(K == null)
		{
			mob.tell(L("There is no perk called '@x1'.  PERKS lists them.", name));
			return false;
		}
		final String why = Perks.why(mob, K, false);
		if(why != null)
		{
			mob.tell(L("You cannot take @x1: @x2.", K.name, why));
			return false;
		}
		Perks.give(mob, K, true);
		mob.tell(L("You take @x1.", K.name));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
