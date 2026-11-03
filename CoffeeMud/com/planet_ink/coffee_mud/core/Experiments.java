package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

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
 * CR18: [SKILL] EXPERIMENT [MATERIAL] [MATERIAL]..., discovering a recipe
 * by trying materials together.  A recipe of the skill marked HIDDEN=1 with
 * EXPERIMENT=honey,apple,mead (what reveals it) is found when the crafter,
 * at its level or above, offers just those (an item carried for each,
 * by name): it is known from then on (KnownRecipes) and gives XP= (or ten
 * per level of the recipe).  Offering at least half of them is a near miss:
 * its HINT= is told, if it has one.  Anything else is a miss.  A miss, near
 * or not, uses up one of the things offered.  CRAFTEXPERIMENTS=n in the ini
 * is how many tries a character has a game day (10 when blank, 0 for no
 * limit).
 */
public final class Experiments
{
	private Experiments()
	{
	}

	private static final Map<String, int[]>	tries	= new HashMap<String, int[]>();

	/**
	 * @return how many tries a game day (CRAFTEXPERIMENTS), 0 for no limit
	 */
	public static int perDay()
	{
		final CMProps P = CMProps.instance();
		final String s = (P == null) ? "" : P.getStr("CRAFTEXPERIMENTS", "").trim();
		return (s.length() == 0) ? 10 : Math.max(0, CMath.s_int(s));
	}

	private static int today(final MOB mob)
	{
		final com.planet_ink.coffee_mud.Common.interfaces.TimeClock C = ((mob.location() != null) && (mob.location().getArea() != null))
			? mob.location().getArea().getTimeObj() : CMLib.time().globalClock();
		return (C.getYear() * 10000) + (C.getMonth() * 100) + C.getDayOfMonth();
	}

	@SuppressWarnings("unchecked")
	private static List<List<String>> recipesOf(final Ability skill)
	{
		try
		{
			final Object o = skill.getClass().getMethod("fetchRecipes").invoke(skill);
			if(o instanceof List)
				return (List<List<String>>)o;
		}
		catch(final Exception e)
		{
		}
		return new ArrayList<List<String>>();
	}

	private static int levelOf(final MOB mob, final Ability skill)
	{
		try
		{
			return ((Integer)skill.getClass().getMethod("xlevel", MOB.class).invoke(skill, mob)).intValue();
		}
		catch(final Exception e)
		{
			return mob.phyStats().level();
		}
	}

	private static boolean fits(final Item I, final String ingredient)
	{
		final String n = I.Name().toLowerCase();
		final String w = ingredient.trim().toLowerCase();
		return (w.length() > 0) && (n.indexOf(w) >= 0);
	}

	/**
	 * Runs an experiment.
	 * @param mob the crafter
	 * @param skill their crafting skill
	 * @param words what they offer, the word EXPERIMENT first
	 * @return what came of it: FOUND, NEAR, MISS or NONE (nothing done)
	 */
	public static String run(final MOB mob, final Ability skill, final List<String> words)
	{
		if((mob == null) || (skill == null) || (words == null) || (words.size() < 2))
		{
			if(mob != null)
				mob.tell(CMLib.lang().L("Experiment with what?  Name the things you carry to try together."));
			return "NONE";
		}
		final List<Item> offered = new ArrayList<Item>();
		for(int w = 1; w < words.size(); w++)
		{
			final Item I = mob.findItem(null, words.get(w));
			if((I == null) || offered.contains(I))
			{
				mob.tell(CMLib.lang().L("You don't have any @x1 to try.", words.get(w)));
				return "NONE";
			}
			offered.add(I);
		}
		final int limit = perDay();
		final String key = mob.Name() + "|" + today(mob);
		synchronized(tries)
		{
			final int[] n = tries.get(key);
			if((limit > 0) && (n != null) && (n[0] >= limit))
			{
				mob.tell(CMLib.lang().L("You have tried enough for one day."));
				return "NONE";
			}
			if(n == null)
				tries.put(key, new int[] { 1 });
			else
				n[0]++;
		}
		final int level = levelOf(mob, skill);
		List<String> near = null;
		for(final List<String> row : recipesOf(skill))
		{
			if(row.size() < 2)
				continue;
			final Map<String, String> k = RecipeKeys.of(row);
			final String h = k.containsKey("HIDDEN") ? k.get("HIDDEN").trim() : "";
			final String exp = k.get("EXPERIMENT");
			if((exp == null) || (!(h.equals("1") || h.equalsIgnoreCase("true"))) || (CMath.s_int(row.get(1)) > level)
			|| KnownRecipes.knows(mob, skill.ID(), row.get(0)))
				continue;
			final List<String> ingredients = CMParms.parseCommas(exp, true);
			int found = 0;
			final Set<Item> used = new HashSet<Item>();
			for(final String ing : ingredients)
			{
				for(final Item I : offered)
				{
					if((!used.contains(I)) && fits(I, ing))
					{
						used.add(I);
						found++;
						break;
					}
				}
			}
			if((found == ingredients.size()) && (used.size() == offered.size()))
			{
				final KnownRecipes.Known K = KnownRecipes.of(mob, true);
				if(K != null)
					K.add(skill.ID(), row.get(0));
				final int xp = k.containsKey("XP") ? CMath.s_int(k.get("XP").trim()) : (10 * Math.max(1, CMath.s_int(row.get(1))));
				if(xp > 0)
					CMLib.leveler().postExperience(mob, "ABILITY:" + skill.ID(), null, null, xp, false);
				mob.tell(CMLib.lang().L("You discover how to make @x1!", row.get(0).replace("%", "").trim()));
				return "FOUND";
			}
			if((found * 2 >= ingredients.size()) && (found > 0) && (near == null))
				near = row;
		}
		final Item lost = offered.get(0);
		if(near != null)
		{
			final String hint = RecipeKeys.of(near).get("HINT");
			mob.tell(((hint != null) && (hint.length() > 0)) ? hint : CMLib.lang().L("You feel you are close to something."));
		}
		else
			mob.tell(CMLib.lang().L("Nothing comes of it."));
		mob.tell(CMLib.lang().L("@x1 is used up.", CMStrings.capitalizeFirstLetter(lost.name(mob))));
		lost.destroy();
		return (near != null) ? "NEAR" : "MISS";
	}
}
