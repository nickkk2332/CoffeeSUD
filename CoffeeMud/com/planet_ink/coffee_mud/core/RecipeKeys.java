package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.collections.XVector;

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
 * CR5: recipe requirement keys, in GenCraftSkill's KEY_VALUE_PARMS column
 * or, for any crafting skill, in a column of its own that starts with one
 * of them: MASK= (a zapper mask the crafter must pass; the recipe is not
 * listed or known otherwise), ROOMMASK= (a zapper mask about where the
 * crafter is, as -LOCATION +ROOMID(Deepforge#3) or -AREA +Deepforge),
 * TOOL= (an item held, carried or in the room, by name), FAILMSG= (what a
 * crafter is told when ROOMMASK or TOOL fails), XP= (experience on each
 * success), TATTOO= (a tattoo given on the first success) and STATION= (CR3:
 * a workstation of that tag in the room) and HIDDEN=1 (CR4: only for those who
 * know it, KnownRecipes), EXPERIMENT= and HINT= (CR18: Experiments) and
 * NOQUALITY=1 (CR17: no quality tier).  None of them becomes an item stat.
 */
public final class RecipeKeys
{
	private RecipeKeys()
	{
	}

	/** the keys, none of which is an item stat */
	public static final String[]	KEYS	= { "MASK", "ROOMMASK", "TOOL", "FAILMSG", "XP", "TATTOO", "STATION", "HIDDEN", "EXPERIMENT", "HINT", "NOQUALITY" };

	private static final Pattern	KV		= Pattern.compile("^\\s*[A-Za-z_]+\\s*=");

	/**
	 * @param row a recipe
	 * @return its keys, upper case, or an empty map
	 */
	public static Map<String, String> of(final List<String> row)
	{
		final Map<String, String> found = new TreeMap<String, String>();
		if(row == null)
			return found;
		for(int i = 1; i < row.size(); i++)
		{
			final String col = row.get(i);
			if((col == null) || (col.indexOf('=') < 0) || (!KV.matcher(col).find()))
				continue;
			final Map<String, String> p = CMParms.parseEQParms(col);
			for(final Map.Entry<String, String> e : p.entrySet())
			{
				final String k = e.getKey().toUpperCase().trim();
				for(final String key : KEYS)
				{
					if(key.equals(k) && (!found.containsKey(k)))
						found.put(k, e.getValue());
				}
			}
		}
		return found;
	}

	/**
	 * @param kv a KEY_VALUE_PARMS map, to become item stats
	 */
	public static void strip(final Map<String, String> kv)
	{
		if(kv == null)
			return;
		for(final Iterator<String> i = kv.keySet().iterator(); i.hasNext();)
		{
			final String k = i.next().toUpperCase().trim();
			for(final String key : KEYS)
			{
				if(key.equals(k))
				{
					i.remove();
					break;
				}
			}
		}
	}

	/**
	 * @param mob a crafter
	 * @param row a recipe
	 * @return whether its MASK keeps it from them
	 */
	public static boolean hidden(final MOB mob, final List<String> row)
	{
		return hidden(mob, null, row);
	}

	/**
	 * @param mob a crafter
	 * @param skillID their skill, for HIDDEN=1, or null
	 * @param row a recipe
	 * @return whether its MASK keeps it from them, or (CR4) it is HIDDEN=1 and they do not know it
	 */
	public static boolean hidden(final MOB mob, final String skillID, final List<String> row)
	{
		if(mob == null)
			return false;
		final Map<String, String> k = of(row);
		final String mask = k.get("MASK");
		if((mask != null) && (mask.trim().length() > 0) && (!CMLib.masking().maskCheck(mask, mob, true)))
			return true;
		final String h = k.containsKey("HIDDEN") ? k.get("HIDDEN").trim() : "";
		return (skillID != null) && (h.equals("1") || h.equalsIgnoreCase("true") || h.equalsIgnoreCase("yes"))
			&& (row.size() > 0) && (!KnownRecipes.knows(mob, skillID, row.get(0)));
	}

	/**
	 * @param mob a crafter
	 * @param recipes the recipes
	 * @return those the crafter may see and use (all of them, the same list, if MASK keeps none away)
	 */
	public static List<List<String>> visible(final MOB mob, final List<List<String>> recipes)
	{
		return visible(mob, null, recipes);
	}

	/**
	 * @param mob a crafter
	 * @param skillID their skill, for HIDDEN=1, or null
	 * @param recipes the recipes
	 * @return those the crafter may see and use
	 */
	public static List<List<String>> visible(final MOB mob, final String skillID, final List<List<String>> recipes)
	{
		if((mob == null) || (recipes == null))
			return recipes;
		List<List<String>> kept = null;
		for(int r = 0; r < recipes.size(); r++)
		{
			if(hidden(mob, skillID, recipes.get(r)))
			{
				if(kept == null)
				{
					kept = new XVector<List<String>>();
					for(int i = 0; i < r; i++)
						kept.add(recipes.get(i));
				}
			}
			else
			if(kept != null)
				kept.add(recipes.get(r));
		}
		return (kept == null) ? recipes : kept;
	}

	/**
	 * @param mob a crafter
	 * @param row a recipe they ask for
	 * @return null if they may make it here and now, else what they are told
	 */
	public static String refusal(final MOB mob, final List<String> row)
	{
		return refusal(mob, null, row);
	}

	/**
	 * @param mob a crafter
	 * @param skillID their skill, for HIDDEN=1, or null
	 * @param row a recipe they ask for
	 * @return null if they may make it here and now, else what they are told
	 */
	public static String refusal(final MOB mob, final String skillID, final List<String> row)
	{
		if(mob == null)
			return null;
		final Map<String, String> k = of(row);
		if(k.isEmpty())
			return null;
		final String fail = k.get("FAILMSG");
		final String roomMask = k.get("ROOMMASK");
		if((roomMask != null) && (roomMask.trim().length() > 0) && (!CMLib.masking().maskCheck(roomMask, mob, true)))
			return ((fail != null) && (fail.length() > 0)) ? fail : CMLib.lang().L("You can't make that here.");
		final String tool = k.get("TOOL");
		if((tool != null) && (tool.trim().length() > 0) && (!hasTool(mob, tool.trim())))
			return ((fail != null) && (fail.length() > 0)) ? fail : CMLib.lang().L("You need @x1 to make that.", CMLib.english().startWithAorAn(tool.trim()));
		if(hidden(mob, skillID, row))
			return ((fail != null) && (fail.length() > 0)) ? fail : CMLib.lang().L("You don't know how to make that.");
		final String station = k.get("STATION"); // CR3: a workstation in the room
		if((station != null) && (station.trim().length() > 0) && (Workstations.refusal(mob, station) != null))
			return ((fail != null) && (fail.length() > 0)) ? fail : Workstations.refusal(mob, station);
		return null;
	}

	/**
	 * @param mob a crafter
	 * @param tool an item name
	 * @return whether they hold or carry it, or it is in the room
	 */
	public static boolean hasTool(final MOB mob, final String tool)
	{
		if(mob.findItem(null, tool) != null)
			return true;
		final Room R = mob.location();
		return (R != null) && (R.findItem(null, tool) != null);
	}

	/**
	 * @param rows the recipes a craft was asked for
	 * @param built what was made
	 * @return the recipe it was made from, or null
	 */
	public static List<String> recipeOf(final List<List<String>> rows, final Item built)
	{
		if((rows == null) || (built == null))
			return null;
		final String made = built.Name().toLowerCase();
		for(final List<String> row : rows)
		{
			if(row.size() == 0)
				continue;
			boolean all = true;
			int at = 0;
			for(final String part : row.get(0).toLowerCase().split("%"))
			{
				final String p = part.trim();
				if(p.length() == 0)
					continue;
				final int x = made.indexOf(p, at);
				if(x < 0)
				{
					all = false;
					break;
				}
				at = x + p.length();
			}
			if(all)
				return row;
		}
		return (rows.size() == 1) ? rows.get(0) : null;
	}

	/**
	 * CR17: whether what was made came from a recipe with NOQUALITY=1.
	 * @param rows the recipes the craft was asked for
	 * @param built what was made
	 * @return true if it rolls no quality tier
	 */
	public static boolean noQuality(final List<List<String>> rows, final Item built)
	{
		final List<String> row = recipeOf(rows, built);
		if(row == null)
			return false;
		final String v = of(row).get("NOQUALITY");
		return (v != null) && (v.trim().equals("1") || v.trim().equalsIgnoreCase("true"));
	}

	/**
	 * After a success: XP= and, the first time, TATTOO=.
	 * @param mob the crafter
	 * @param skillID the skill
	 * @param rows the recipes the craft was asked for
	 * @param built what was made
	 */
	public static void reward(final MOB mob, final String skillID, final List<List<String>> rows, final Item built)
	{
		final List<String> row = recipeOf(rows, built);
		if((mob == null) || (row == null))
			return;
		final Map<String, String> k = of(row);
		final int xp = CMath.s_int(k.containsKey("XP") ? k.get("XP").trim() : "0");
		if(xp > 0)
			CMLib.leveler().postExperience(mob, "ABILITY:" + skillID, null, null, xp, false);
		final String tattoo = k.get("TATTOO");
		if((tattoo != null) && (tattoo.trim().length() > 0) && (mob.findTattoo(tattoo.trim()) == null))
			mob.addTattoo(tattoo.trim().toUpperCase());
	}
}
