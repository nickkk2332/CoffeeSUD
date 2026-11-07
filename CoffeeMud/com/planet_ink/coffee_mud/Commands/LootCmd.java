package com.planet_ink.coffee_mud.Commands;

import java.util.List;

import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;

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
/** Q07: LOOT [container or corpse] shows its tracked loot by rarity through your filter; LOOT FILTER sets it. No game time. */
public class LootCmd extends StdCommand
{
	private final String[] access = I(new String[] { "LOOT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> words)
	{
		return 0;
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> words)
	{
		return 0;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> words, final int flags) throws java.io.IOException
	{
		try
		{
			if((words.size() >= 2) && words.get(1).equalsIgnoreCase("FILTER"))
			{
				mob.tell(ItemLoot.setFilter(mob, words.subList(2, words.size())));
				return false;
			}
			if(words.size() < 2)
			{
				mob.tell(L("LOOT [container or corpse] shows its tracked loot by rarity; LOOT SELF shows what you carry; LOOT FILTER [rarity, UNTRACKED or TRACKED...] or LOOT FILTER ALL chooses what it shows."));
				return false;
			}
			if((words.size() == 2) && (words.get(1).equalsIgnoreCase("SELF") || words.get(1).equalsIgnoreCase("INVENTORY")))
			{
				mob.tell(ItemLoot.selfView(mob)); // I16: the same view over your own inventory
				return false;
			}
			final String name = CMParms.combine(words, 1);
			Item I = mob.findItem(null, name);
			if((I == null) && (mob.location() != null))
				I = mob.location().findItem(null, name);
			if(I == null)
			{
				mob.tell(L("You see no '@x1' here.", name));
				return false;
			}
			mob.tell(ItemLoot.view(mob, I));
		}
		catch(final java.io.IOException e)
		{
			mob.tell(L("Loot: @x1", e.getMessage()));
		}
		return false;
	}
}
