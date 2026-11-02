package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Libraries.interfaces.MaskingLibrary;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
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
 * C1: healing and support aimed at the ally who needs it, for
 * CombatAbilities (and its presets), Healer and NiceAbilities: whom a
 * mob helps (SUPPORT=SELF|LEADER|GROUP|MASK:zapper, TARGET= on Healer), the
 * most hurt first, healing only under HEALAT=n%, cures first (CUREFIRST=
 * Poison,Disease) and buffs before a fight.
 */
public final class SupportPicker
{
	private SupportPicker()
	{
	}

	/** whom a mob helps */
	public static final class Support
	{
		/** SELF, LEADER, GROUP or MASK */
		public String							mode	= "LEADER";
		/** MASK's mask */
		public MaskingLibrary.CompiledZMask	mask	= null;
		/** the mask's text */
		public String							maskText	= "";
	}

	private static final Pattern	SUPPORT	= Pattern.compile("(?i)\\b(SUPPORT|TARGET)\\s*=\\s*(?:\"(MASK:[^\"]*)\"|(MASK:\"[^\"]*\")|(\\S+))");

	/**
	 * Reads SUPPORT= (or, with key TARGET, Healer's TARGET=).
	 * @param parms a behaviour's parameters
	 * @param key SUPPORT or TARGET
	 * @param dflt the mode when it is not given
	 * @return whom it helps
	 */
	public static Support parse(final String parms, final String key, final String dflt)
	{
		final Support S = new Support();
		S.mode = dflt;
		final Matcher m = SUPPORT.matcher((parms == null) ? "" : parms);
		while(m.find())
		{
			if(!m.group(1).equalsIgnoreCase(key))
				continue;
			String v = (m.group(2) != null) ? m.group(2) : ((m.group(3) != null) ? m.group(3) : m.group(4));
			if(v.toUpperCase().startsWith("MASK:"))
			{
				v = v.substring(5).trim();
				if(v.startsWith("\"") && v.endsWith("\"") && (v.length() > 1))
					v = v.substring(1, v.length() - 1);
				S.mode = "MASK";
				S.maskText = v;
				S.mask = CMLib.masking().getPreCompiledMask(v);
			}
			else
				S.mode = v.toUpperCase();
		}
		return S;
	}

	/**
	 * @param M a mob
	 * @return its hit points, in percent of its most
	 */
	public static double hpPct(final MOB M)
	{
		return 100.0 * M.curState().getHitPoints() / Math.max(1, M.maxState().getHitPoints());
	}

	/**
	 * @param A an ability
	 * @return true if it heals
	 */
	public static boolean isHeal(final Ability A)
	{
		return (A != null) && CMath.bset(A.flags(), Ability.FLAG_HEALINGMAGIC);
	}

	/**
	 * @param mob who would cast it
	 * @param A the ability
	 * @param ally on whom
	 * @return true if it would help them
	 */
	public static boolean helps(final MOB mob, final Ability A, final MOB ally)
	{
		final int q = A.castingQuality(mob, ally);
		if(ally == mob)
			return (q == Ability.QUALITY_BENEFICIAL_SELF) || (q == Ability.QUALITY_BENEFICIAL_OTHERS);
		return q == Ability.QUALITY_BENEFICIAL_OTHERS;
	}

	/**
	 * @param M a mob
	 * @param kind POISON, DISEASE, or a word of an effect's name
	 * @return true if it suffers from such an effect
	 */
	public static boolean afflicted(final MOB M, final String kind)
	{
		final String k = kind.trim().toUpperCase();
		for(final Enumeration<Ability> a = M.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if((A == null) || A.isNowAnAutoEffect() || (A.canBeUninvoked() == false))
				continue;
			final int code = A.classificationCode() & Ability.ALL_ACODES;
			if(k.equals("POISON") && (code == Ability.ACODE_POISON))
				return true;
			if(k.equals("DISEASE") && (code == Ability.ACODE_DISEASE))
				return true;
			if(A.ID().toUpperCase().indexOf(k) >= 0)
				return true;
		}
		return false;
	}

	/**
	 * @param A an ability
	 * @param kind POISON, DISEASE, or a word
	 * @return true if it is meant for that: its ID or name has the word
	 */
	public static boolean treats(final Ability A, final String kind)
	{
		final String k = kind.trim().toUpperCase();
		return (A.ID().toUpperCase().indexOf(k) >= 0) || (A.name().toUpperCase().indexOf(k) >= 0);
	}

	/**
	 * @param mob who helps
	 * @param S whom it helps
	 * @return those it may help here, itself among them, the most hurt first
	 */
	public static List<MOB> allies(final MOB mob, final Support S)
	{
		final List<MOB> list = new ArrayList<MOB>();
		final Room R = mob.location();
		list.add(mob);
		if(R != null)
		{
			if(S.mode.equals("LEADER"))
			{
				final MOB L = mob.amFollowing();
				if((L != null) && (L.location() == R) && (!list.contains(L)))
					list.add(L);
			}
			else
			if(S.mode.equals("GROUP"))
			{
				for(final MOB M : mob.getGroupMembers(new HashSet<MOB>()))
				{
					if((M != null) && (M.location() == R) && (!list.contains(M)))
						list.add(M);
				}
			}
			else
			if(S.mode.equals("MASK") && (S.mask != null))
			{
				for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					if((M == null) || list.contains(M) || M.amDead() || (M.getVictim() == mob) || (mob.getVictim() == M))
						continue;
					if(CMLib.masking().maskCheck(S.mask, M, false))
						list.add(M);
				}
			}
		}
		Collections.sort(list, new Comparator<MOB>()
		{
			@Override
			public int compare(final MOB a, final MOB b)
			{
				return Double.compare(hpPct(a), hpPct(b));
			}
		});
		return list;
	}

	/**
	 * Which ability, on whom: cures first, then a heal for one under the
	 * threshold, most hurt first, then (out of a fight) a buff one lacks.
	 * @param mob who helps
	 * @param skills its abilities to choose from
	 * @param allies whom it may help, most hurt first
	 * @param healAt heal only those under this percent, 0 to heal no one first
	 * @param cureFirst what to cure first, or null
	 * @param buff true to buff those who lack it
	 * @return the ability and its target, or null
	 */
	public static Object[] pick(final MOB mob, final List<Ability> skills, final List<MOB> allies, final int healAt, final List<String> cureFirst, final boolean buff)
	{
		if(cureFirst != null)
		{
			for(final MOB ally : allies)
			{
				for(final String kind : cureFirst)
				{
					if(!afflicted(ally, kind))
						continue;
					for(final Ability A : skills)
					{
						if(treats(A, kind) && helps(mob, A, ally))
							return new Object[] { A, ally };
					}
				}
			}
		}
		if(healAt > 0)
		{
			for(final MOB ally : allies)
			{
				if(hpPct(ally) >= healAt)
					continue;
				for(final Ability A : skills)
				{
					if(isHeal(A) && helps(mob, A, ally))
						return new Object[] { A, ally };
				}
			}
		}
		if(buff)
		{
			final List<Ability> shuffled = new ArrayList<Ability>(skills);
			for(int i = shuffled.size() - 1; i > 0; i--)
				Collections.swap(shuffled, i, CMLib.dice().roll(1, i + 1, -1));
			for(final MOB ally : allies)
			{
				for(final Ability A : shuffled)
				{
					if((!isHeal(A)) && (ally.fetchEffect(A.ID()) == null) && helps(mob, A, ally))
						return new Object[] { A, ally };
				}
			}
		}
		return null;
	}
}
