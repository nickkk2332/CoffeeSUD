package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * I26: how fast things wear, and what becomes of them at the end:
 * coffeemud.ini's ITEMWEAR (a percent, 100 as before, 0 for none) and
 * ITEMBREAK (DESTROY as before, or BREAK: unusable until mended), and an
 * item's own Prop_Durability (MULT=, UNBREAKABLE, NOREPAIR, BROKENNAME=,
 * BREAK=).  StdArmor's wear and the combat library's item damage ask it.
 */
public final class Durability
{
	private Durability()
	{
	}

	protected static java.util.Map<String, String> rules(final Item I)
	{
		final Ability A = (I == null) ? null : I.fetchEffect("Prop_Durability");
		return (A == null) ? null : CMParms.parseExactParms(A.text());
	}

	/**
	 * @param I an item
	 * @return the percent of the usual wear it takes
	 */
	public static int wearPct(final Item I)
	{
		int pct = CMath.s_int(CMProps.instance().getProperty("ITEMWEAR", "100").trim());
		final java.util.Map<String, String> r = rules(I);
		if(r != null)
		{
			if(CMParms.getParmBool(r, "UNBREAKABLE", false) || I.fetchEffect("Prop_Durability").text().toUpperCase().matches("(^|.*\\s)UNBREAKABLE(\\s.*|$)"))
				return 0;
			pct = pct * CMParms.getParmInt(r, "MULT", 100) / 100;
		}
		return Math.max(0, pct);
	}

	/**
	 * Some wear, as ITEMWEAR and MULT say.
	 * @param I the item
	 * @param loss the wear it would take as before
	 * @return the wear it takes
	 */
	public static int scale(final Item I, final int loss)
	{
		final int pct = wearPct(I);
		if((pct == 100) || (loss <= 0))
			return loss;
		final double d = loss * pct / 100.0;
		int n = (int)Math.floor(d);
		if(CMLib.dice().rollPercentage() <= (int)Math.round((d - n) * 100))
			n++;
		return n;
	}

	/**
	 * @param I an item worn out
	 * @return true if it breaks rather than goes
	 */
	public static boolean breaks(final Item I)
	{
		final java.util.Map<String, String> r = rules(I);
		if((r != null) && (CMParms.getParmBool(r, "BREAK", false) || (CMParms.getParmStr(r, "BROKENNAME", "").length() > 0)))
			return true;
		return CMProps.instance().getProperty("ITEMBREAK", "DESTROY").trim().equalsIgnoreCase("BREAK");
	}

	/**
	 * Breaks an item: it stays, unusable and without its bonuses, until it
	 * is mended (Prop_Broken).
	 * @param I the item
	 * @param owner who has it, or null
	 */
	public static void breakItem(final Item I, final MOB owner)
	{
		if(I.fetchEffect("Prop_Broken") != null)
			return;
		I.setUsesRemaining(0);
		I.unWear();
		final java.util.Map<String, String> r = rules(I);
		final String bn = (r == null) ? "" : CMParms.getParmStr(r, "BROKENNAME", "");
		final Ability A = CMClass.getAbility("Prop_Broken");
		if(A == null)
			return;
		A.setMiscText("ORIGINAL=\"" + I.Name() + "\"");
		I.addNonUninvokableEffect(A);
		if(bn.length() > 0)
			I.setName(bn.contains("%s") ? bn.replace("%s", I.Name().replaceFirst("^(?i)(a|an|the) ", "")) : bn);
		I.recoverPhyStats();
		if(owner != null)
		{
			owner.tell(owner, I, null, CMLib.lang().L("^I<T-NAME> breaks!^?"));
			owner.recoverPhyStats();
			owner.recoverCharStats();
			owner.recoverMaxState();
		}
	}
}
