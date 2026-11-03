package com.planet_ink.coffee_mud.core;

import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.AbilityComponent;

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
 * P14: action costs and components kept in a generic ability's own record
 * (GenAbility, GenTweakAbility, GenCraftSkill, GenGatheringSkill): ACTIONCOST,
 * COMBATACTIONCOST and COMPONENTS.
 */
public class AbilityCosts
{
	private AbilityCosts()
	{
	}

	/**
	 * An ACTIONCOST or COMBATACTIONCOST as given: blank, or below 0, is -1, the
	 * default (coffeemud.ini's DEFABLETIME, DEFCOMABLETIME and their exceptions).
	 * @param val the stat value
	 * @return the cost in actions, or -1
	 */
	public static double parse(String val)
	{
		val=(val==null)?"":val.trim();
		if((val.length()==0)||(!CMath.isNumber(val)))
			return -1;
		final double d=CMath.s_double(val);
		return (d<0)?-1:d;
	}

	/**
	 * A cost as a stat value: -1 for the default.
	 * @param d the cost in actions, or below 0
	 * @return the stat value
	 */
	public static String text(final double d)
	{
		return (d<0)?"-1":Double.toString(d);
	}

	/**
	 * An ability's COMPONENTS changed: the component map is read again when next
	 * used, with this field in it.
	 * @param oldVal the field before
	 * @param newVal the field now
	 */
	public static void componentsChanged(final String oldVal, final String newVal)
	{
		if(((oldVal==null)?"":oldVal.trim()).equals((newVal==null)?"":newVal.trim()))
			return;
		Resources.removeResource("COMPONENT_MAP");
	}

	/**
	 * Called as the component map is read: each generic ability's COMPONENTS
	 * field takes the place of a components.txt line for the same ID.
	 * @param H the component map being read
	 */
	public static void loadInto(final Map<String, List<AbilityComponent>> H)
	{
		for(final Enumeration<Ability> a=CMClass.abilities();a.hasMoreElements();)
		{
			final Ability A=a.nextElement();
			if((A==null)||(!A.isGeneric())||(!CMParms.containsIgnoreCase(A.getStatCodes(), "COMPONENTS")))
				continue;
			final String comps=A.getStat("COMPONENTS").trim();
			if(comps.length()==0)
				continue;
			final String id=A.ID().toUpperCase().trim();
			final List<AbilityComponent> was=H.remove(id);
			final String error=CMLib.ableComponents().addAbilityComponent(id+"="+comps, H);
			if(error!=null)
			{
				Log.errOut("AbilityCosts", A.ID()+" COMPONENTS: "+error);
				H.remove(id);
				if(was!=null)
					H.put(id, was);
			}
		}
	}
}
