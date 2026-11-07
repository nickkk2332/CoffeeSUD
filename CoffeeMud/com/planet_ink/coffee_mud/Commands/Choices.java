package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * I07 and I17: CHOICES, the saved choices of the optional module
 * items.choices: reveals of veiled modifiers and offerings (ItemChoices).
 */
public class Choices extends StdCommand
{
	private final String[] access=I(new String[]{"CHOICES"});
	@Override public String[] getAccessWords()
	{
		return access;
	}
	@Override public boolean canBeOrdered()
	{
		return false;
	}
	@Override public boolean securityCheck(final MOB mob)
	{
		return CMProps.isSinglePlayer();
	}
	@Override public double actionsCost(final MOB mob,final List<String> words)
	{
		return 0;
	}
	@Override public double combatActionsCost(final MOB mob,final List<String> words)
	{
		return 0;
	}
	private static Item carried(final MOB mob,final String name)throws java.io.IOException
	{
		final Item item=mob.findItem(null,name);
		if(item==null)
			throw new java.io.IOException("no such carried item; quote a multiword name");
		return item;
	}
	@Override public boolean execute(final MOB mob,final List<String> words,final int flags)throws java.io.IOException
	{
		try
		{
			final String sub=words.size()>1?words.get(1).toUpperCase(Locale.ROOT):"LIST";
			if(sub.equals("LIST"))
				mob.tell(ItemChoices.list(mob));
			else if((sub.equals("PREVIEW")||sub.equals("OPEN"))&&words.size()>=4)
			{
				// CHOICES PREVIEW/OPEN [reveal or offering] "[item]" ([veiled number])
				final String id=words.get(2).toLowerCase(Locale.ROOT);
				final Item item=carried(mob,words.get(3));
				final String rest=words.size()>4?CMParms.combine(words,4):"";
				try
				{
					mob.tell(sub.equals("PREVIEW")?ItemChoices.preview(mob,id,item,rest):ItemChoices.open(mob,id,item,rest));
				}
				catch(final java.io.IOException e)
				{
					throw new java.io.IOException(e.getMessage()+". Nothing was paid or drawn.");
				}
			}
			else if(sub.equals("PICK")&&words.size()==4&&words.get(2).equalsIgnoreCase("OFFERING"))
				mob.tell(ItemChoices.pickOffering(mob,CMath.s_int(words.get(3))));
			else if(sub.equals("PICK")&&words.size()==4)
				mob.tell(ItemChoices.pick(mob,carried(mob,words.get(2)),CMath.s_int(words.get(3))));
			else if(sub.equals("ABANDON")&&words.size()==2)
				mob.tell(ItemChoices.abandon(mob));
			else
				mob.tell("CHOICES (LIST) / PREVIEW or OPEN [reveal or offering ID] \"[item]\" ([veiled number]) / PICK \"[item]\" [number] / PICK OFFERING [number] / ABANDON. ITEMMODS numbers an item's veiled modifiers.");
		}
		catch(final java.io.IOException e)
		{
			mob.tell("Choices: "+e.getMessage());
		}
		return false;
	}
}
