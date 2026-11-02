package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * C16: the death rules where one dies: a Prop_DeathRules on the room, else
 * on its area, before the game's PLAYERDEATH, MOBDEATH and ITEMLOOTPOLICY.
 */
public final class DeathRules
{
	private DeathRules()
	{
	}

	/**
	 * @param A a Prop_DeathRules
	 * @param M one who dies
	 * @return whether its MASK= fits them (no mask fits all)
	 */
	public static boolean fits(final Ability A, final MOB M)
	{
		if((A == null) || (M == null))
			return false;
		final String mask = CMParms.getParmStr(CMParms.parseExactParms(A.text()), "MASK", "").trim();
		return (mask.length() == 0) || CMLib.masking().maskCheck(CMLib.masking().getPreCompiledMask(mask), M, true);
	}

	/**
	 * The rules there that fit one: the room's Prop_DeathRules, then its
	 * area's, each if its MASK= fits them.
	 * @param M the one
	 * @return their parameters, the room's first
	 */
	public static List<Map<String, String>> rulesFor(final MOB M)
	{
		final List<Map<String, String>> list = new ArrayList<Map<String, String>>();
		final Room R = (M == null) ? null : M.location();
		if(R == null)
			return list;
		final Ability RA = R.fetchEffect("Prop_DeathRules");
		if(fits(RA, M))
			list.add(CMParms.parseExactParms(RA.text()));
		final Ability AA = (R.getArea() == null) ? null : R.getArea().fetchEffect("Prop_DeathRules");
		if(fits(AA, M))
			list.add(CMParms.parseExactParms(AA.text()));
		return list;
	}

	/** a key's value: the room's, else the area's, else the game's */
	protected static String rule(final MOB M, final String key, final String global)
	{
		for(final Map<String, String> q : rulesFor(M))
		{
			final String v = CMParms.getParmStr(q, key, "").trim();
			if(v.length() > 0)
				return v;
		}
		return global;
	}

	/**
	 * @param M a player's character who dies
	 * @return the PLAYERDEATH list there
	 */
	public static String playerDeath(final MOB M)
	{
		return rule(M, "PLAYERDEATH", CMProps.get((M == null) ? null : M.session()).getStr(CMProps.Str.PLAYERDEATH));
	}

	/**
	 * @param M a mob that dies
	 * @return the MOBDEATH list there
	 */
	public static String mobDeath(final MOB M)
	{
		return rule(M, "MOBDEATH", CMProps.getVar(CMProps.Str.MOBDEATH));
	}

	/**
	 * @param M a mob that dies
	 * @return the ITEMLOOTPOLICY there
	 */
	public static String itemLootPolicy(final MOB M)
	{
		return rule(M, "ITEMLOOTPOLICY", CMProps.getVar(CMProps.Str.ITEMLOOTPOLICY));
	}
}
