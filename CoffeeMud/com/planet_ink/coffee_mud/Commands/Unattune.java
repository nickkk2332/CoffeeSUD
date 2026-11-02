package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
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
 * I23: UNATTUNE [ITEM]: frees an item one is attuned to (Prop_Attunement),
 * and a place among those they may be attuned to.
 */
public class Unattune extends StdCommand
{
	public Unattune()
	{
	}

	private final String[] access = I(new String[] { "UNATTUNE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String name = CMParms.combine(commands, 1).trim();
		if(name.length() == 0)
		{
			mob.tell(L("Unattune from what?"));
			return false;
		}
		final Item I = mob.fetchItem(null, Wearable.FILTER_ANY, name);
		final Ability A = (I == null) ? null : I.fetchEffect("Prop_Attunement");
		if((A == null) || (!A.getStat("ATTUNED").equalsIgnoreCase(mob.Name())))
		{
			mob.tell(L("You are not attuned to '@x1'.", name));
			return false;
		}
		A.setStat("UNATTUNE", "true");
		mob.tell(mob, I, null, L("You let go of your bond with <T-NAME>."));
		return true;
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
}
