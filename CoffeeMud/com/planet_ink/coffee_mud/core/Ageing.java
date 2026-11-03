package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.List;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Races.interfaces.Race;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;
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
 * P29: ageing as data.  A GenRace's AGEEFFECTS ("MIDDLEAGED: STR-1 DEX-1
 * WIS+1; VENERABLE: ...") takes the place of the coded effects of each age;
 * AGEDISEASES ("VENERABLE:Disease_Cancer:0.01, ANCIENT:Disease_Arthritis:1")
 * the place of the coded ageing diseases, each a percent chance at each check
 * from that age on; AGE_PROG and the AGECATEGORY achievement event come on
 * each birthday and new age; STARTAGE_[NAME]="[mask]:[years]" starts a
 * character older or younger.
 */
public class Ageing
{
	private Ageing()
	{
	}

	/** the age categories' words, by Race.AGE_ constant */
	public static final String[] AGE_NAMES = { "INFANT", "TODDLER", "CHILD", "YOUNGADULT", "MATURE", "MIDDLEAGED", "OLD", "VENERABLE", "ANCIENT" };

	/**
	 * @param word an age category, by word (MIDDLEAGED, "middle-aged") or number
	 * @return its Race.AGE_ constant, or -1
	 */
	public static int category(final String word)
	{
		if(word == null)
			return -1;
		final String w = word.toUpperCase().replace(" ", "").replace("-", "").replace("_", "").trim();
		if(CMath.isInteger(w))
			return CMath.s_int(w);
		for(int i = 0; i < AGE_NAMES.length; i++)
		{
			if(AGE_NAMES[i].equals(w) || ((i < Race.AGE_DESCS.length) && Race.AGE_DESCS[i].toUpperCase().replace(" ", "").equals(w)))
				return i;
		}
		if(w.equals("ADULT"))
			return Race.AGE_MATURE;
		return -1;
	}

	/**
	 * @param cat a Race.AGE_ constant
	 * @return its word
	 */
	public static String name(final int cat)
	{
		return ((cat >= 0) && (cat < AGE_NAMES.length)) ? AGE_NAMES[cat] : "NONE";
	}

	private static int stat(final String name)
	{
		int code = CharStats.CODES.findWhole(name, true);
		if(code < 0)
		{
			for(final int c : CharStats.CODES.BASECODES())
			{
				if(CharStats.CODES.NAME(c).toUpperCase().startsWith(name.toUpperCase()))
					return c;
			}
			code = CharStats.CODES.findWhole(name.toLowerCase(), false);
		}
		return code;
	}

	/**
	 * In place of StdRace.agingAffects's own: the race's AGEEFFECTS for the age it is.
	 * @param R the race
	 * @param mob the mob
	 * @param baseStats its base stats
	 * @param charStats its stats, being worked out
	 * @return true if the race has AGEEFFECTS (so the coded effects are not wanted)
	 */
	public static boolean custom(final Race R, final MOB mob, final CharStats baseStats, final CharStats charStats)
	{
		if((R == null) || (!CMParms.contains(R.getStatCodes(), "AGEEFFECTS")))
			return false;
		final String spec = R.getStat("AGEEFFECTS");
		if((spec == null) || (spec.trim().length() == 0))
			return false;
		final int cat = baseStats.ageCategory();
		for(final String part : spec.split(";"))
		{
			final int colon = part.indexOf(':');
			if((colon <= 0) || (category(part.substring(0, colon)) != cat))
				continue;
			for(final String adj : part.substring(colon + 1).trim().split("[\\s,]+"))
			{
				final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([A-Za-z_]+)\\s*([+-]\\d+)$").matcher(adj.trim());
				if(!m.find())
					continue;
				final int code = stat(m.group(1));
				if(code >= 0)
					charStats.setStat(code, charStats.getStat(code) + CMath.s_int(m.group(2).replace("+", "")));
			}
		}
		return true;
	}

	/**
	 * In place of the coded ageing diseases, when AGEDISEASES is set: each
	 * entry's chance, from its age on.
	 * @param mob the ageing mob
	 * @return true if AGEDISEASES is set
	 */
	public static boolean diseases(final MOB mob)
	{
		final CMProps P = CMProps.instance();
		final String spec = (P == null) ? "" : P.getStr("AGEDISEASES", "").trim();
		if(spec.length() == 0)
			return false;
		if(CMSecurity.isDisabled(CMSecurity.DisFlag.AUTODISEASE))
			return true;
		final int cat = mob.baseCharStats().ageCategory();
		for(final String entry : CMParms.parseCommas(spec, true))
		{
			final String[] f = entry.trim().split(":");
			if(f.length < 2)
				continue;
			final int from = category(f[0]);
			if((from < 0) || (cat < from))
				continue;
			final double pct = (f.length > 2) ? CMath.s_double(f[2].trim()) : 1.0;
			if((CMLib.dice().rollPercentage() * 1.0) > pct)
				continue;
			final Ability A = CMClass.getAbility(f[1].trim());
			if((A != null) && (mob.fetchEffect(A.ID()) == null) && (!CMSecurity.isAbilityDisabled(A.ID())))
				A.invoke(mob, mob, true, 0);
		}
		return true;
	}

	/**
	 * After a birthday: AGE_PROG BIRTHDAY, and AGE_PROG CATEGORY with the
	 * AGECATEGORY achievement event when the age category changed.  $0 is the
	 * age category, $1 the age, $2 BIRTHDAY or CATEGORY.
	 * @param mob the mob
	 * @param oldCat its age category before
	 */
	public static void aged(final MOB mob, final int oldCat)
	{
		if(mob == null)
			return;
		final int cat = mob.baseCharStats().ageCategory();
		fire(mob, cat, "BIRTHDAY");
		if(cat != oldCat)
		{
			fire(mob, cat, "CATEGORY");
			if(cat > oldCat)
				CMLib.achievements().possiblyBumpAchievement(mob, AchievementLibrary.Event.AGECATEGORY, cat - Math.max(oldCat, 0), name(cat));
		}
	}

	private static void fire(final MOB mob, final int cat, final String kind)
	{
		final int code = ScriptEvents.code("AGE_PROG");
		if((code <= 0) || (!ScriptEvents.isUsed(code)))
			return;
		final String age = "" + mob.baseCharStats().getStat(CharStats.STAT_AGE);
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		hosts.add(mob);
		if(mob.location() != null)
		{
			for(final PhysicalAgent H : ScriptEvents.hosts(mob.location()))
			{
				if(!hosts.contains(H))
					hosts.add(H);
			}
		}
		for(final PhysicalAgent H : hosts)
		{
			for(final ScriptingEngine S : ScriptEvents.engines(H))
			{
				S.eventCheck(H, code, mob, mob, null, name(cat), new Object[] { name(cat), age, kind }, "Cr", t -> {
					final String w = (t.length < 2) ? "" : t[1].trim().toUpperCase();
					return (w.length() == 0) || w.equals("ALL") || w.equals(kind) || (category(w) == cat);
				});
			}
		}
	}

	/**
	 * At the end of creation: the first STARTAGE_ entry whose mask the
	 * character meets sets its age.
	 * @param mob the new character
	 */
	public static void startAge(final MOB mob)
	{
		final CMProps P = CMProps.instance();
		if((P == null) || (mob == null) || (mob.baseCharStats().getStat(CharStats.STAT_AGE) <= 0))
			return;
		for(final String[] kv : P.getStrsStarting("STARTAGE_"))
		{
			String v = (kv[1] == null) ? "" : kv[1].trim();
			if(v.startsWith("\"") && v.endsWith("\"") && (v.length() >= 2))
				v = v.substring(1, v.length() - 1).trim();
			final int x = v.lastIndexOf(':');
			if(x < 0)
				continue;
			final String mask = v.substring(0, x).trim();
			final int years = CMath.s_int(v.substring(x + 1).trim());
			if((years > 0) && ((mask.length() == 0) || CMLib.masking().maskCheck(mask, mob, true)))
			{
				mob.baseCharStats().setStat(CharStats.STAT_AGE, years);
				mob.recoverCharStats();
				return;
			}
		}
	}
}
