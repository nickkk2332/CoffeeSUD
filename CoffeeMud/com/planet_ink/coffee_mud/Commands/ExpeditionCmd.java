package com.planet_ink.coffee_mud.Commands;

import java.util.ArrayList;
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
/**
 * G01: EXPEDITION PREVIEW [chart], OPEN [chart] ([modifier]...), STATUS and
 * RECOVER.  PREVIEW, STATUS and RECOVER take no game time.
 */
public class ExpeditionCmd extends StdCommand
{
	private final String[] access = I(new String[] { "EXPEDITION" });

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
		return ((words.size() > 1) && words.get(1).equalsIgnoreCase("OPEN")) ? 1.0 : 0;
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> words)
	{
		return actionsCost(mob, words);
	}

	/** the chart named by the words after the subcommand, and the modifier words after it */
	private Item chart(final MOB mob, final List<String> words, final List<String> rest)
	{
		// the longest name that is a carried chart; the words after it are modifiers
		for(int end = words.size(); end > 2; end--)
		{
			final Item I = mob.findItem(null, CMParms.combine(words.subList(0, end), 2));
			if((I != null) && (Expeditions.chartOf(I) != null))
			{
				rest.addAll(words.subList(end, words.size()));
				return I;
			}
		}
		return null;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> words, final int flags) throws java.io.IOException
	{
		final String sub = (words.size() > 1) ? words.get(1).toUpperCase() : "STATUS";
		try
		{
			if(sub.equals("STATUS"))
				mob.tell(Expeditions.status(mob));
			else
			if(sub.equals("RECOVER"))
				mob.tell(Expeditions.recover(mob));
			else
			if(sub.equals("PREVIEW") || sub.equals("OPEN"))
			{
				final List<String> mods = new ArrayList<String>();
				final Item I = chart(mob, words, mods);
				if(I == null)
				{
					mob.tell(L("You carry no chart by that name."));
					return false;
				}
				if(sub.equals("PREVIEW"))
					mob.tell(Expeditions.preview(mob, I));
				else
				{
					final String name = I.name();
					final long n = Expeditions.open(mob, I, mods);
					mob.tell(L("You open @x1: expedition @x2 begins. Go to its destination.", name, "" + n));
				}
			}
			else
				mob.tell(L("EXPEDITION PREVIEW [chart], OPEN [chart] ([modifier]...), STATUS or RECOVER."));
		}
		catch(final java.io.IOException e)
		{
			mob.tell(L("Expedition: @x1", e.getMessage()));
		}
		return false;
	}
}
