package com.planet_ink.coffee_mud.Commands;

import java.util.List;

import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.Physical;

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
 * Q05: LEDGER, what the optional module families hold for a character, an
 * item, a relationship and an expedition, with sources.  It takes no game
 * time and changes nothing.
 */
public class LedgerCmd extends StdCommand
{
	private final String[] access = I(new String[] { "LEDGER" });

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

	/** a character or an item here by name; "me" is the viewer */
	private static Physical find(final MOB mob, final String name)
	{
		if(name.equalsIgnoreCase("me") || name.equalsIgnoreCase("self"))
			return mob;
		Physical P = (mob.location() == null) ? null : mob.location().fetchInhabitant(name);
		if(P == null)
			P = mob.findItem(null, name);
		if((P == null) && (mob.location() != null))
			P = mob.location().findItem(null, name);
		return P;
	}

	/** a player may read their own ledger and their party's; a creator anyone's */
	private static boolean mayRead(final MOB viewer, final MOB who)
	{
		return (who == viewer) || BuildCompare.creatorView() || Party.isInParty(Party.leaderOf(viewer), who);
	}

	@Override
	public boolean execute(final MOB mob, final List<String> words, final int flags) throws java.io.IOException
	{
		try
		{
			final String sub = (words.size() > 1) ? words.get(1).toUpperCase(java.util.Locale.ROOT) : "";
			if(words.size() < 2)
				mob.tell(Ledgers.character(mob));
			else
			if(sub.equals("EXPEDITION"))
				mob.tell(Ledgers.expedition(mob));
			else
			if(sub.equals("ITEM") && (words.size() > 2))
			{
				final Physical P = find(mob, CMParms.combine(words, 2));
				if(!(P instanceof Item))
					throw new java.io.IOException("you see no such item");
				mob.tell(Ledgers.item((Item)P));
			}
			else
			if(sub.equals("BETWEEN") && (words.size() == 4))
			{
				final Physical a = find(mob, words.get(2)), b = find(mob, words.get(3));
				if((a == null) || (b == null))
					throw new java.io.IOException("you see no " + ((a == null) ? words.get(2) : words.get(3)) + " here");
				if(((a instanceof MOB) && !mayRead(mob, (MOB)a)) && ((b instanceof MOB) && !mayRead(mob, (MOB)b)))
					throw new java.io.IOException("you can read only your own ledger and your party's");
				mob.tell(Ledgers.between(a, b));
			}
			else
			{
				final Physical P = find(mob, CMParms.combine(words, 1));
				if(!(P instanceof MOB))
					throw new java.io.IOException("LEDGER, LEDGER [character], LEDGER ITEM [item], LEDGER BETWEEN [a] [b], LEDGER EXPEDITION");
				if(!mayRead(mob, (MOB)P))
					throw new java.io.IOException("you can read only your own ledger and your party's");
				mob.tell(Ledgers.character((MOB)P));
			}
		}
		catch(final java.io.IOException e)
		{
			mob.tell(L("Ledger: @x1", e.getMessage()));
		}
		return false;
	}
}
