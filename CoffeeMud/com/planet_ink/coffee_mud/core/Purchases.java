package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.CharClasses.interfaces.CharClass;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.collections.Pair;
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
 * P23: what a character bought with trains and practices, so that a respec
 * can give back what each cost.  Kept in a hidden Prop_Purchases effect on
 * the character, saved with it: a line a purchase, KIND|ID|TRAINS|PRACTICES|
 * LEVEL|EXTRA, KIND one of SKILL, EXPERTISE, STAT (EXTRA the gain) or CLASS
 * (EXTRA the class before).
 */
public class Purchases
{
	private Purchases()
	{
	}

	/** One purchase. */
	public static class Bought
	{
		public final String kind, id, extra;
		public final int trains, practices, level;

		Bought(final String kind, final String id, final int trains, final int practices, final int level, final String extra)
		{
			this.kind = kind;
			this.id = id;
			this.trains = trains;
			this.practices = practices;
			this.level = level;
			this.extra = (extra == null) ? "" : extra;
		}

		@Override
		public String toString()
		{
			return kind + "|" + id + "|" + trains + "|" + practices + "|" + level + "|" + extra;
		}

		/** @return its name, for lists */
		public String name()
		{
			if(kind.equals("SKILL"))
			{
				final Ability A = CMClass.getAbility(id);
				return (A == null) ? id : A.name();
			}
			if(kind.equals("EXPERTISE"))
			{
				final com.planet_ink.coffee_mud.Libraries.interfaces.ExpertiseLibrary.ExpertiseDefinition D = CMLib.expertises().getDefinition(id);
				return (D == null) ? id : D.name();
			}
			if(kind.equals("CLASS"))
			{
				final CharClass C = CMClass.getCharClass(id);
				return (C == null) ? id : C.name();
			}
			return CMStrings.capitalizeAndLower(id) + " +" + extra;
		}

		/** @return what it cost, as words */
		public String cost()
		{
			final StringBuilder s = new StringBuilder("");
			if(trains > 0)
				s.append(trains).append((trains == 1) ? " train" : " trains");
			if(practices > 0)
				s.append((s.length() > 0) ? ", " : "").append(practices).append((practices == 1) ? " practice" : " practices");
			return (s.length() == 0) ? "nothing" : s.toString();
		}
	}

	private static Ability holder(final MOB mob, final boolean make)
	{
		if(mob == null)
			return null;
		Ability A = mob.fetchEffect("Prop_Purchases");
		if((A == null) && make)
		{
			A = CMClass.getAbility("Prop_Purchases");
			if(A != null)
			{
				A.setMiscText("");
				mob.addNonUninvokableEffect(A);
			}
		}
		return A;
	}

	/**
	 * @param mob a character
	 * @return its trains and practices now, for spent()
	 */
	public static int[] snapshot(final MOB mob)
	{
		return (mob == null) ? new int[2] : new int[] { mob.getTrains(), mob.getPractices() };
	}

	/**
	 * Records what a purchase cost, from what the character had before it.
	 * @param mob the character
	 * @param kind SKILL, EXPERTISE, STAT or CLASS
	 * @param id what was bought
	 * @param before snapshot() before the spending
	 * @param extra the gain, or the class before
	 */
	public static void spent(final MOB mob, final String kind, final String id, final int[] before, final String extra)
	{
		if((mob == null) || (mob.playerStats() == null) || (before == null))
			return;
		final int t = Math.max(0, before[0] - mob.getTrains()), p = Math.max(0, before[1] - mob.getPractices());
		if((t == 0) && (p == 0) && (!kind.equals("CLASS")))
			return;
		record(mob, new Bought(kind, id, t, p, mob.basePhyStats().level(), extra));
	}

	private static void record(final MOB mob, final Bought b)
	{
		final Ability A = holder(mob, true);
		if(A == null)
			return;
		final String t = A.text().trim();
		A.setMiscText((t.length() == 0) ? b.toString() : (t + "\n" + b.toString()));
	}

	/**
	 * @param mob a character
	 * @return what it has bought, oldest first
	 */
	public static List<Bought> list(final MOB mob)
	{
		final List<Bought> L = new ArrayList<Bought>();
		final Ability A = holder(mob, false);
		if(A == null)
			return L;
		for(final String line : A.text().split("\n"))
		{
			final String[] f = line.trim().split("\\|", -1);
			if(f.length >= 5)
				L.add(new Bought(f[0], f[1], CMath.s_int(f[2]), CMath.s_int(f[3]), CMath.s_int(f[4]), (f.length > 5) ? f[5] : ""));
		}
		return L;
	}

	private static void store(final MOB mob, final List<Bought> L)
	{
		final Ability A = holder(mob, !L.isEmpty());
		if(A == null)
			return;
		final StringBuilder s = new StringBuilder("");
		for(final Bought b : L)
			s.append((s.length() > 0) ? "\n" : "").append(b.toString());
		A.setMiscText(s.toString());
	}

	private static boolean kindOf(final String what, final String kind)
	{
		final String w = (what == null) ? "ALL" : what.toUpperCase().trim();
		if(w.equals("ALL") || w.length() == 0)
			return true;
		for(final String one : CMParms.parseCommas(w.replace(' ', ','), true))
		{
			if((one.equals("SKILLS") || one.equals("SKILL")) && kind.equals("SKILL"))
				return true;
			if((one.equals("EXPERTISES") || one.equals("EXPERTISE")) && kind.equals("EXPERTISE"))
				return true;
			if((one.equals("STATS") || one.equals("STAT")) && kind.equals("STAT"))
				return true;
			if(one.equals("CLASS") && kind.equals("CLASS"))
				return true;
		}
		return false;
	}

	private static boolean autoGained(final MOB mob, final String abilityID)
	{
		for(int c = 0; c < mob.baseCharStats().numClasses(); c++)
		{
			final CharClass C = mob.baseCharStats().getMyClass(c);
			if((C != null) && (CMLib.ableMapper().getQualifyingLevel(C.ID(), true, abilityID) >= 0)
			&& CMLib.ableMapper().getDefaultGain(C.ID(), true, abilityID))
				return true;
		}
		final com.planet_ink.coffee_mud.Races.interfaces.Race R = mob.baseCharStats().getMyRace();
		return (R != null) && (CMLib.ableMapper().getQualifyingLevel(R.ID(), false, abilityID) >= 0);
	}

	/**
	 * What a respec of this kind could undo: each thing once, its costs added
	 * up; skills that come by themselves stay.
	 * @param mob the character
	 * @param what SKILLS, EXPERTISES, STATS, CLASS or ALL (several by commas)
	 * @return by name: kind and id, and the purchases
	 */
	public static Map<String, List<Bought>> refundable(final MOB mob, final String what)
	{
		final Map<String, List<Bought>> m = new LinkedHashMap<String, List<Bought>>();
		if(mob == null)
			return m;
		for(final Bought b : list(mob))
		{
			if(!kindOf(what, b.kind))
				continue;
			if(b.kind.equals("SKILL") && ((mob.fetchAbility(b.id) == null) || autoGained(mob, b.id)))
				continue;
			if(b.kind.equals("EXPERTISE") && (mob.fetchExpertise(b.id) == null))
				continue;
			if(b.kind.equals("CLASS") && ((!mob.baseCharStats().getCurrentClass().ID().equals(b.id)) || (mob.baseCharStats().getClassLevel(b.id) > 0)))
				continue;
			final String key = b.kind + " " + b.id;
			if(!m.containsKey(key))
				m.put(key, new ArrayList<Bought>());
			m.get(key).add(b);
		}
		return m;
	}

	/**
	 * Undoes one thing bought (all its purchases) and gives back what it cost.
	 * @param mob the character
	 * @param buys its purchases, from refundable()
	 * @return what was undone, for the character
	 */
	public static String undo(final MOB mob, final List<Bought> buys)
	{
		if((buys == null) || buys.isEmpty())
			return "";
		final Bought b = buys.get(0);
		int t = 0, p = 0;
		for(final Bought one : buys)
		{
			t += one.trains;
			p += one.practices;
		}
		if(b.kind.equals("SKILL"))
		{
			final Ability A = mob.fetchAbility(b.id);
			if(A != null)
				A.unlearn(mob);
		}
		else
		if(b.kind.equals("EXPERTISE"))
			mob.delExpertise(b.id);
		else
		if(b.kind.equals("STAT"))
		{
			for(final Bought one : buys)
			{
				final int gain = CMath.s_int(one.extra);
				if(one.id.equals("HITPOINTS"))
					mob.baseState().setHitPoints(mob.baseState().getHitPoints() - gain);
				else
				if(one.id.equals("MANA"))
					mob.baseState().setMana(mob.baseState().getMana() - gain);
				else
				if(one.id.equals("MOVES"))
					mob.baseState().setMovement(mob.baseState().getMovement() - gain);
				else
				{
					final int code = CharStats.CODES.findWhole(one.id, true);
					if(code >= 0)
						mob.baseCharStats().setStat(code, mob.baseCharStats().getStat(code) - gain);
				}
			}
		}
		else
		if(b.kind.equals("CLASS"))
		{
			final CharClass was = CMClass.getCharClass(b.extra);
			final CharClass now = mob.baseCharStats().getCurrentClass();
			if((was != null) && (now != null) && now.ID().equals(b.id))
			{
				now.endCharacter(mob);
				final Pair<String, String> info = mob.baseCharStats().getAllClassInfo();
				final List<String> cs = CMParms.parseSemicolons(info.first, true), ls = CMParms.parseSemicolons(info.second, true);
				final StringBuilder nc = new StringBuilder(""), nl = new StringBuilder("");
				for(int i = 0; i < cs.size(); i++)
				{
					if(cs.get(i).equalsIgnoreCase(b.id))
						continue;
					nc.append((nc.length() > 0) ? ";" : "").append(cs.get(i));
					nl.append((nl.length() > 0) ? ";" : "").append((i < ls.size()) ? ls.get(i) : "0");
				}
				mob.baseCharStats().setAllClassInfo(nc.toString(), nl.toString());
				mob.baseCharStats().setCurrentClass(was);
			}
		}
		mob.setTrains(mob.getTrains() + t);
		mob.setPractices(mob.getPractices() + p);
		final List<Bought> left = list(mob);
		for(final Bought one : buys)
		{
			for(int i = 0; i < left.size(); i++)
			{
				if(left.get(i).toString().equals(one.toString()))
				{
					left.remove(i);
					break;
				}
			}
		}
		store(mob, left);
		mob.recoverCharStats();
		mob.recoverPhyStats();
		mob.recoverMaxState();
		return CMLib.lang().L("@x1 (@x2 back)", b.name(), new Bought("", "", t, p, 0, "").cost());
	}

	/**
	 * A respec: undoes what is named (or ALL of the kinds allowed).
	 * @param mob the character
	 * @param what the kinds allowed
	 * @param which a name, or ALL
	 * @return what was undone, one a line, or "" for nothing
	 */
	public static List<String> respec(final MOB mob, final String what, final String which)
	{
		final List<String> done = new ArrayList<String>();
		final Map<String, List<Bought>> m = refundable(mob, what);
		final String w = (which == null) ? "ALL" : which.trim();
		for(final Map.Entry<String, List<Bought>> e : m.entrySet())
		{
			final Bought b = e.getValue().get(0);
			if(w.equalsIgnoreCase("ALL") || b.id.equalsIgnoreCase(w) || b.name().equalsIgnoreCase(w)
			|| CMLib.english().containsString(b.name(), w))
			{
				done.add(undo(mob, e.getValue()));
				if(!w.equalsIgnoreCase("ALL"))
					break;
			}
		}
		return done;
	}

	/**
	 * @param mob a character
	 * @param what the kinds
	 * @return the list RESPEC shows
	 */
	public static String show(final MOB mob, final String what)
	{
		final StringBuilder s = new StringBuilder("");
		for(final List<Bought> L : refundable(mob, what).values())
		{
			int t = 0, p = 0;
			for(final Bought b : L)
			{
				t += b.trains;
				p += b.practices;
			}
			s.append("  ^H").append(L.get(0).name()).append("^N (").append(new Bought("", "", t, p, 0, "").cost()).append(")\n\r");
		}
		return s.toString();
	}
}
