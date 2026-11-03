package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.List;

import com.planet_ink.coffee_mud.Common.interfaces.CharState;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Common.interfaces.PhyStats;
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
 * P26: what added (or any) stats do.  STATEFFECT_[STAT] in the ini or
 * world.ini: "per point over 10: +1 SAVES, +1 CRIT_CHANCE_PCT_WEAPON" (or
 * "per 2 points over 12: ...", or "per point: ..."), each part +N or -N of a
 * char stat (a CharStats code: a save, a crit, another stat; SAVES is all the
 * saves), ATTACK, ARMOR, DAMAGE or SPEED, or HITPOINTS, MANA or MOVES,
 * applied as the mob's stats are worked out.  And @STAT(NAME) and
 * @BASESTAT(NAME) in formulas (CMath), for the mob a formula is for.
 */
public class StatEffects
{
	private StatEffects()
	{
	}

	private static final int KIND_CHAR = 0, KIND_PHY = 1, KIND_STATE = 2;

	private static final class Part
	{
		final int kind;
		final int[] codes;
		final String field;
		final double amount;

		Part(final int kind, final int[] codes, final String field, final double amount)
		{
			this.kind = kind;
			this.codes = codes;
			this.field = field;
			this.amount = amount;
		}
	}

	private static final class Rule
	{
		final int stat;
		final double per;
		final double over;
		final List<Part> parts = new ArrayList<Part>();

		Rule(final int stat, final double per, final double over)
		{
			this.stat = stat;
			this.per = per;
			this.over = over;
		}

		double points(final CharStats cs)
		{
			final double v = cs.getStat(stat) - over;
			return (v <= 0) ? 0 : Math.floor(v / Math.max(1, per));
		}
	}

	private static List<Rule> rules = new ArrayList<Rule>(0);
	private static int rulesHash = 0;
	private static long rulesRead = 0;

	/** Reads the STATEFFECT_ entries again at the next recover (they are otherwise read every few seconds). */
	public static void reload()
	{
		rulesRead = 0;
	}

	private static List<Rule> rules()
	{
		final CMProps P = CMProps.instance();
		final long now = System.currentTimeMillis();
		if((P == null) || ((now - rulesRead) < 3000))
			return rules;
		rulesRead = now;
		final String[][] all = P.getStrsStarting("STATEFFECT_");
		final StringBuilder key = new StringBuilder("");
		for(final String[] kv : all)
			key.append(kv[0]).append('=').append(kv[1]).append('\n');
		if(key.toString().hashCode() == rulesHash)
			return rules;
		final List<Rule> L = new ArrayList<Rule>();
		for(final String[] kv : all)
		{
			final int stat = CharStats.CODES.findWhole(kv[0], true);
			if((stat < 0) || (kv[1] == null) || (kv[1].trim().length() == 0))
				continue;
			final Rule R = parse(stat, kv[1]);
			if(R != null)
				L.add(R);
		}
		rules = L;
		rulesHash = key.toString().hashCode();
		return rules;
	}

	private static Rule parse(final int stat, final String spec)
	{
		String s = spec.trim();
		if(s.startsWith("\"") && s.endsWith("\"") && (s.length() >= 2))
			s = s.substring(1, s.length() - 1).trim();
		double per = 1, over = 0;
		final int colon = s.indexOf(':');
		if(colon >= 0)
		{
			final String head = s.substring(0, colon).toUpperCase().trim();
			s = s.substring(colon + 1).trim();
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("PER\\s+(\\d+(?:\\.\\d+)?)?\\s*POINTS?(?:\\s+OVER\\s+(-?\\d+))?").matcher(head);
			if(m.find())
			{
				if(m.group(1) != null)
					per = CMath.s_double(m.group(1));
				if(m.group(2) != null)
					over = CMath.s_int(m.group(2));
			}
		}
		final Rule R = new Rule(stat, per, over);
		for(final String raw : CMParms.parseCommas(s, true))
		{
			final String p = raw.trim();
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^([+-]\\s*\\d+(?:\\.\\d+)?)\\s*(\\S+)$|^(\\S+?)\\s*([+-]\\s*\\d+(?:\\.\\d+)?)$").matcher(p);
			if(!m.find())
			{
				Log.errOut("StatEffects", "STATEFFECT_" + CharStats.CODES.NAME(stat) + ": not +N NAME: " + p);
				continue;
			}
			final double amt = CMath.s_double(((m.group(1) != null) ? m.group(1) : m.group(4)).replace(" ", "").replace("+", ""));
			final String name = ((m.group(2) != null) ? m.group(2) : m.group(3)).toUpperCase().replace("%", "");
			if(name.equals("SAVES"))
				R.parts.add(new Part(KIND_CHAR, CharStats.CODES.SAVING_THROWS(), null, amt));
			else
			if(name.equals("CRITCHANCE"))
				R.parts.add(new Part(KIND_CHAR, new int[] { CharStats.STAT_CRIT_CHANCE_PCT_WEAPON, CharStats.STAT_CRIT_CHANCE_PCT_MAGIC }, null, amt));
			else
			if(name.equals("ATTACK") || name.equals("ARMOR") || name.equals("DAMAGE") || name.equals("SPEED"))
				R.parts.add(new Part(KIND_PHY, null, name, amt));
			else
			if(name.equals("HITPOINTS") || name.equals("HP") || name.equals("MANA") || name.equals("MOVES") || name.equals("MOVEMENT"))
				R.parts.add(new Part(KIND_STATE, null, name, amt));
			else
			{
				final int code = CharStats.CODES.findWhole(name, true);
				if(code >= 0)
					R.parts.add(new Part(KIND_CHAR, new int[] { code }, null, amt));
				else
					Log.errOut("StatEffects", "STATEFFECT_" + CharStats.CODES.NAME(stat) + ": no stat " + name);
			}
		}
		return R;
	}

	/**
	 * The char stat parts, at the end of recoverCharStats.
	 * @param mob the mob
	 * @param cs its char stats as worked out so far
	 */
	public static void charStats(final MOB mob, final CharStats cs)
	{
		final List<Rule> L = rules();
		if(L.isEmpty())
			return;
		final double[] points = new double[L.size()];
		for(int i = 0; i < L.size(); i++)
			points[i] = L.get(i).points(cs);
		for(int i = 0; i < L.size(); i++)
		{
			if(points[i] <= 0)
				continue;
			for(final Part P : L.get(i).parts)
			{
				if(P.kind != KIND_CHAR)
					continue;
				for(final int c : P.codes)
					cs.setStat(c, cs.getStat(c) + (int)Math.round(P.amount * points[i]));
			}
		}
	}

	/**
	 * The phy stat parts (ATTACK, ARMOR, DAMAGE, SPEED), at the end of recoverPhyStats.
	 * @param mob the mob
	 * @param ps its phy stats as worked out so far
	 */
	public static void phyStats(final MOB mob, final PhyStats ps)
	{
		final List<Rule> L = rules();
		if(L.isEmpty() || (mob.charStats() == null))
			return;
		for(final Rule R : L)
		{
			final double pts = R.points(mob.charStats());
			if(pts <= 0)
				continue;
			for(final Part P : R.parts)
			{
				if(P.kind != KIND_PHY)
					continue;
				final double n = P.amount * pts;
				if(P.field.equals("ATTACK"))
					ps.setAttackAdjustment(ps.attackAdjustment() + (int)Math.round(n));
				else
				if(P.field.equals("ARMOR"))
					ps.setArmor(ps.armor() - (int)Math.round(n)); // lower armor is better: +N is N better
				else
				if(P.field.equals("DAMAGE"))
					ps.setDamage(ps.damage() + (int)Math.round(n));
				else
				if(P.field.equals("SPEED"))
					ps.setSpeed(ps.speed() + n);
			}
		}
	}

	/**
	 * The state parts (HITPOINTS, MANA, MOVES), in recoverMaxState.
	 * @param mob the mob
	 * @param st its max state as worked out so far
	 */
	public static void state(final MOB mob, final CharState st)
	{
		final List<Rule> L = rules();
		if(L.isEmpty() || (mob.charStats() == null))
			return;
		for(final Rule R : L)
		{
			final double pts = R.points(mob.charStats());
			if(pts <= 0)
				continue;
			for(final Part P : R.parts)
			{
				if(P.kind != KIND_STATE)
					continue;
				final int n = (int)Math.round(P.amount * pts);
				if(P.field.startsWith("H"))
					st.setHitPoints(st.getHitPoints() + n);
				else
				if(P.field.startsWith("MA"))
					st.setMana(st.getMana() + n);
				else
					st.setMovement(st.getMovement() + n);
			}
		}
	}

	/**
	 * For @STAT(NAME) and @BASESTAT(NAME) in a formula.
	 * @param subject the mob the formula is for, or null
	 * @param code the stat
	 * @param base true for the base stat
	 * @return its value, 0 with no mob
	 */
	public static double statOf(final Object subject, final int code, final boolean base)
	{
		if(!(subject instanceof MOB))
			return 0;
		final MOB M = (MOB)subject;
		final CharStats cs = base ? M.baseCharStats() : M.charStats();
		return (cs == null) ? 0 : cs.getStat(code);
	}
}
