package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * P23: a place, item or NPC where a character may RESPEC: undo what it
 * bought with trains and practices and have them back.  WHAT=STATS, SKILLS,
 * EXPERTISES, CLASS or ALL (by commas); COST="500" (money for the whole) or
 * COST="500 per skill" (for each thing undone); ONCE=YES (once a character);
 * MASK="..." (who may).
 */
public class Prop_Respec extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Respec";
	}

	@Override
	public String name()
	{
		return "a place of retraining";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS | Ability.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Retraining here: " + CMParms.getParmStr(text(), "WHAT", "ALL");
	}

	/** @return the kinds it undoes */
	public String what()
	{
		return CMParms.getParmStr(text(), "WHAT", "ALL").toUpperCase().trim();
	}

	/**
	 * @param mob who asks
	 * @return why they may not, or null
	 */
	public String refusal(final MOB mob)
	{
		final String mask = CMParms.getParmStr(text(), "MASK", "");
		if((mask.trim().length() > 0) && (!CMLib.masking().maskCheck(mask, mob, true)))
			return L("You may not retrain here.");
		if(CMParms.getParmStr(text(), "ONCE", "NO").toUpperCase().startsWith("Y") && (mob.findTattoo(onceTattoo()) != null))
			return L("You have retrained here already.");
		return null;
	}

	/** @return the tattoo ONCE leaves */
	public String onceTattoo()
	{
		final Physical P = affected;
		String key = (P instanceof Room) ? CMLib.map().getExtendedRoomID((Room)P) : ((P == null) ? "" : P.Name());
		key = key.toUpperCase().replaceAll("[^A-Z0-9]", "_");
		return "RESPEC_" + key;
	}

	/**
	 * @param things how many things are undone
	 * @return the price
	 */
	public double price(final int things)
	{
		final String c = CMParms.getParmStr(text(), "COST", "").trim().toUpperCase();
		if(c.length() == 0)
			return 0;
		final int sp = c.indexOf(' ');
		final double n = CMath.s_double((sp < 0) ? c : c.substring(0, sp));
		return (c.indexOf(" PER ") >= 0) ? n * Math.max(1, things) : n;
	}

	/**
	 * After a respec here.
	 * @param mob who retrained
	 * @param things how many things were undone
	 */
	public void paid(final MOB mob, final int things)
	{
		final double p = price(things);
		if(p > 0)
			CMLib.beanCounter().subtractMoney(mob, p);
		if(CMParms.getParmStr(text(), "ONCE", "NO").toUpperCase().startsWith("Y"))
			mob.addTattoo(onceTattoo());
	}
}
