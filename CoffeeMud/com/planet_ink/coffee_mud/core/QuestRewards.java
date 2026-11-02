package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary.Award;
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
 * Q10: rewards written down, as a quest's SET REWARDS and MPREWARD give
 * them: the achievements' award words (100 CURRENCY, 50 XP, 2 QP, TITLE,
 * ABILITY, EXPERTISE, STAT, TATTOO, ITEM, MOB), and CoffeeSUD's own:
 * 15% XP (of a level), FACTION:id:+200, and ITEM:catalog name (or
 * ITEM:"catalog name").  Granted with one message, and described for offers,
 * boards and QUESTS.
 */
public final class QuestRewards
{
	private QuestRewards()
	{
	}

	/** a reward string split: the achievements' part, and CoffeeSUD's own */
	public static final class Parsed
	{
		/** the words the achievements read */
		public final StringBuilder	awards		= new StringBuilder("");
		/** percents of a level of experience */
		public int					xpPercent	= 0;
		/** faction ID to change */
		public final Map<String, Integer>	factions	= new LinkedHashMap<String, Integer>();
		/** catalog item names */
		public final List<String>	items		= new ArrayList<String>();
	}

	/**
	 * @param rewards the reward words
	 * @return them split
	 */
	public static Parsed parse(final String rewards)
	{
		final Parsed P = new Parsed();
		String r = (rewards == null) ? "" : rewards.trim();
		if((r.length() > 1) && r.startsWith("\"") && r.endsWith("\"") && (r.indexOf('"', 1) == r.length() - 1))
			r = r.substring(1, r.length() - 1); // MPREWARD who "100 CURRENCY ..."
		final List<String> words = CMParms.parse(r);
		for(int i = 0; i < words.size(); i++)
		{
			final String w = words.get(i);
			final String W = w.toUpperCase();
			if(W.startsWith("FACTION:"))
			{
				final String[] p = w.substring(8).split(":");
				if(p.length >= 2)
					P.factions.put(p[0].trim(), Integer.valueOf(CMath.s_int(p[1].trim().startsWith("+") ? p[1].trim().substring(1) : p[1].trim())));
				continue;
			}
			if(W.startsWith("ITEM:") && (w.length() > 5))
			{
				P.items.add(w.substring(5).trim().replace('_', ' '));
				continue;
			}
			if(W.endsWith("%") && CMath.isInteger(W.substring(0, W.length() - 1)) && (i + 1 < words.size())
			&& (words.get(i + 1).equalsIgnoreCase("XP") || words.get(i + 1).equalsIgnoreCase("EXP")))
			{
				P.xpPercent += CMath.s_int(W.substring(0, W.length() - 1));
				i++;
				continue;
			}
			P.awards.append((P.awards.length() > 0) ? " " : "").append((w.indexOf(' ') >= 0) ? ("\"" + w + "\"") : w);
		}
		return P;
	}

	/**
	 * A reward described, for an offer, a board, QUESTS.
	 * @param rewards the reward words
	 * @return as "100 gold, 15% of a level of experience, a silver bell charm"
	 */
	public static String describe(final String rewards)
	{
		if((rewards == null) || (rewards.trim().length() == 0))
			return "";
		final Parsed P = parse(rewards);
		final List<String> parts = new ArrayList<String>();
		if(P.awards.length() > 0)
		{
			for(final Award A : CMLib.achievements().parseAwardsList(P.awards.toString()))
				parts.add(A.getDescription());
		}
		if(P.xpPercent > 0)
			parts.add(CMLib.lang().L("@x1% of a level of experience", "" + P.xpPercent));
		for(final String name : P.items)
		{
			final Item I = CMLib.catalog().getCatalogItem(name);
			parts.add((I != null) ? I.name() : name);
		}
		for(final Map.Entry<String, Integer> e : P.factions.entrySet())
		{
			final Faction F = faction(e.getKey());
			final int n = e.getValue().intValue();
			parts.add(((n >= 0) ? "+" : "") + n + " " + CMLib.lang().L("with @x1", (F != null) ? F.name() : e.getKey()));
		}
		return String.join(", ", parts);
	}

	private static Faction faction(final String id)
	{
		Faction F = CMLib.factions().getFaction(id);
		if(F == null)
			F = CMLib.factions().getFactionByName(id);
		return F;
	}

	/**
	 * Gives a character a reward, with one message.
	 * @param mob the character
	 * @param source what for, as an experience source (QUEST:name)
	 * @param rewards the reward words
	 * @return what was given, described
	 */
	public static String give(final MOB mob, final String source, final String rewards)
	{
		if((mob == null) || (rewards == null) || (rewards.trim().length() == 0))
			return "";
		final Parsed P = parse(rewards);
		final List<String> given = new ArrayList<String>();
		if(P.awards.length() > 0)
		{
			for(final Award A : CMLib.achievements().parseAwardsList(P.awards.toString()))
				given.add(A.getDescription());
			CMLib.achievements().giveAwards(mob, P.awards.toString());
		}
		if(P.xpPercent > 0)
		{
			int span = mob.getExpNextLevel() - mob.getExpPrevLevel();
			if(span <= 0)
				span = mob.getExpNeededLevel();
			final int xp = Math.max(1, (span * P.xpPercent) / 100);
			CMLib.leveler().postExperience(mob, (source == null) ? "QUEST:" : source, null, "", xp, true);
			given.add(CMLib.lang().L("@x1 experience", "" + xp));
		}
		for(final String name : P.items)
		{
			final Item I = CMLib.catalog().getCatalogItem(name);
			if(I == null)
			{
				Log.errOut("QuestRewards", "No catalog item '" + name + "' to give " + mob.Name());
				continue;
			}
			final Item copy = (Item)I.copyOf();
			CMLib.catalog().changeCatalogUsage(copy, true);
			mob.addItem(copy);
			given.add(copy.name());
		}
		for(final Map.Entry<String, Integer> e : P.factions.entrySet())
		{
			final Faction F = faction(e.getKey());
			if(F == null)
			{
				Log.errOut("QuestRewards", "No faction '" + e.getKey() + "' to change for " + mob.Name());
				continue;
			}
			mob.adjustFaction(F.factionID(), e.getValue().intValue());
			final int n = e.getValue().intValue();
			given.add(((n >= 0) ? "+" : "") + n + " " + CMLib.lang().L("with @x1", F.name()));
		}
		final String all = String.join(", ", given);
		if(all.length() > 0)
			mob.tell(CMLib.lang().L("^YYour reward: ^w@x1^Y.^N", all));
		return all;
	}
}
