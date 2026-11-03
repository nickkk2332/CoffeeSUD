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
 * P23: RESPEC ([THING]|ALL): where a Prop_Respec is (the room, someone or
 * something here, or something carried), lists what may be undone, or undoes
 * it and gives back the trains and practices it cost.
 */
public class RespecCmd extends StdCommand
{
	public RespecCmd()
	{
	}

	private final String[] access = I(new String[] { "RESPEC", "RETRAIN" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * @param mob who asks
	 * @return the retraining property where they are, or null
	 */
	public static Ability respecHere(final MOB mob)
	{
		final Room R = mob.location();
		if(R == null)
			return null;
		Ability A = R.fetchEffect("Prop_Respec");
		if(A != null)
			return A;
		for(int i = 0; i < R.numInhabitants(); i++)
		{
			final MOB M = R.fetchInhabitant(i);
			if((M != null) && (M != mob) && ((A = M.fetchEffect("Prop_Respec")) != null))
				return A;
		}
		for(int i = 0; i < R.numItems(); i++)
		{
			final Item I = R.getItem(i);
			if((I != null) && ((A = I.fetchEffect("Prop_Respec")) != null))
				return A;
		}
		for(int i = 0; i < mob.numItems(); i++)
		{
			final Item I = mob.getItem(i);
			if((I != null) && ((A = I.fetchEffect("Prop_Respec")) != null))
				return A;
		}
		return null;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final Ability A = respecHere(mob);
		if(!(A instanceof com.planet_ink.coffee_mud.Abilities.Properties.Prop_Respec))
		{
			mob.tell(L("There is no one and nothing here to retrain you."));
			return false;
		}
		final com.planet_ink.coffee_mud.Abilities.Properties.Prop_Respec P = (com.planet_ink.coffee_mud.Abilities.Properties.Prop_Respec)A;
		final String why = P.refusal(mob);
		if(why != null)
		{
			mob.tell(why);
			return false;
		}
		final String what = P.what();
		if(commands.size() < 2)
		{
			final String list = Purchases.show(mob, what);
			if(list.length() == 0)
				mob.tell(L("You have nothing to retrain here."));
			else
			{
				final double one = P.price(1);
				mob.tell(L("^HYou may undo, and have back what it cost:^N\n\r") + list
					+ ((one > 0) ? L("It costs @x1@x2.\n\r", CMLib.beanCounter().nameCurrencyShort(mob, one), (P.price(2) > one) ? L(" each") : "") : "")
					+ L("RESPEC [name] undoes one, RESPEC ALL undoes them all."));
			}
			return false;
		}
		final String which = CMParms.combine(commands, 1);
		final int count = which.equalsIgnoreCase("ALL") ? Purchases.refundable(mob, what).size() : 1;
		if(count == 0)
		{
			mob.tell(L("You have nothing to retrain here."));
			return false;
		}
		final double price = P.price(count);
		if((price > 0) && (CMLib.beanCounter().getTotalAbsoluteNativeValue(mob) < price))
		{
			mob.tell(L("That costs @x1.", CMLib.beanCounter().nameCurrencyShort(mob, price)));
			return false;
		}
		final List<String> done = Purchases.respec(mob, what, which);
		if(done.isEmpty())
		{
			mob.tell(L("You have nothing called '@x1' to retrain.", which));
			return false;
		}
		P.paid(mob, done.size());
		final StringBuilder s = new StringBuilder(L("You unlearn:\n\r"));
		for(final String d : done)
			s.append("  ").append(d).append("\n\r");
		mob.tell(s.toString());
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
