package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
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
 * C25: CONSIDER ALL: the whole fight in one word.  The foes here (those
 * fighting the party, or aggressive; else every NPC not on the player's
 * side) against the party here, by power level (CoffeeLevels), the foes'
 * times their rank's POWER (C15).
 */
public final class FightOdds
{
	private FightOdds()
	{
	}

	/** the verdicts, by the foes' power over the party's */
	private static final double[]	CUTS	= { 0.25, 0.5, 0.9, 1.4, 2.2 };
	private static final String[]	WORDS	= { "trivial", "an easy fight", "a fair fight", "a hard fight", "a very hard fight", "deadly" };

	/**
	 * @param mob who asks
	 * @return the party here: their group, them first
	 */
	public static List<MOB> party(final MOB mob)
	{
		final List<MOB> list = new ArrayList<MOB>();
		list.add(mob);
		final Room R = mob.location();
		final MOB lead = mob.getGroupLeader();
		for(final MOB G : ((lead != null) ? lead : mob).getGroupMembers(new HashSet<MOB>()))
		{
			if((G != null) && (G.location() == R) && (!G.amDead()) && (!list.contains(G)))
				list.add(G);
		}
		return list;
	}

	protected static boolean aggressive(final MOB M)
	{
		for(final Enumeration<Behavior> b = M.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if((B != null) && B.ID().toUpperCase().indexOf("AGGRESSIVE") >= 0)
				return true;
		}
		return false;
	}

	/**
	 * @param mob who asks
	 * @param party their party here
	 * @return the foes here
	 */
	public static List<MOB> foes(final MOB mob, final List<MOB> party)
	{
		final List<MOB> hostile = new ArrayList<MOB>();
		final List<MOB> others = new ArrayList<MOB>();
		final Room R = mob.location();
		if(R == null)
			return hostile;
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || M.amDead() || party.contains(M) || GameDifficulty.playerSide(M) || (!CMLib.flags().canBeSeenBy(M, mob)))
				continue;
			final MOB v = M.getVictim();
			if(((v != null) && party.contains(v)) || aggressive(M))
				hostile.add(M);
			else
				others.add(M);
		}
		return hostile.isEmpty() ? others : hostile;
	}

	/**
	 * @param M a foe
	 * @return its power: its power level, times its rank's POWER
	 */
	public static double power(final MOB M)
	{
		return CMLib.leveler().getPowerLevel(M) * MobRanks.powerOf(M);
	}

	/**
	 * @param ratio the foes' power over the party's
	 * @return the verdict
	 */
	public static String verdict(final double ratio)
	{
		for(int i = 0; i < CUTS.length; i++)
		{
			if(ratio < CUTS[i])
				return WORDS[i];
		}
		return WORDS[WORDS.length - 1];
	}

	/**
	 * The whole fight in a line: "Four guards and a sergeant [elite]: a hard
	 * fight for your party of three."
	 * @param mob who asks
	 * @return the line
	 */
	public static String report(final MOB mob)
	{
		final List<MOB> party = party(mob);
		final List<MOB> foes = foes(mob, party);
		if(foes.isEmpty())
			return CMLib.lang().L("There is no one here to fight.");
		double foePower = 0, partyPower = 0;
		boolean scales = false;
		final Map<String, Integer> counts = new LinkedHashMap<String, Integer>();
		for(final MOB F : foes)
		{
			foePower += power(F);
			scales = scales || PartyScale.scales(F);
			final String tag = MobRanks.tagOf(F);
			final String key = F.name(mob) + ((tag.length() > 0) ? (" " + tag) : "");
			counts.put(key, Integer.valueOf(counts.containsKey(key) ? (counts.get(key).intValue() + 1) : 1));
		}
		for(final MOB P : party)
			partyPower += CMLib.leveler().getPowerLevel(P);
		final List<String> names = new ArrayList<String>();
		for(final Map.Entry<String, Integer> e : counts.entrySet())
		{
			final int n = e.getValue().intValue();
			if(n == 1)
				names.add(e.getKey());
			else
			{
				String name = e.getKey(), tag = "";
				final int x = name.lastIndexOf(" [");
				if(x > 0)
				{
					tag = name.substring(x);
					name = name.substring(0, x);
				}
				names.add(CMLib.english().makeNumberWords(n, 0) + " " + CMLib.english().makePlural(CMLib.english().removeArticleLead(name)) + tag);
			}
		}
		final String who = (party.size() == 1) ? CMLib.lang().L("for you alone")
			: CMLib.lang().L("for your party of @x1", CMLib.english().makeNumberWords(party.size(), 0));
		final String line = CMStrings.capitalizeFirstLetter(CMLib.english().toEnglishStringList(names)) + ": "
			+ verdict(foePower / Math.max(1.0, partyPower)) + " " + who + ".";
		return scales ? (line + " " + CMLib.lang().L("They scale to your party.")) : line;
	}
}
