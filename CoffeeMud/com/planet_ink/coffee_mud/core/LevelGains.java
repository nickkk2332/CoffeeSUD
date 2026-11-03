package com.planet_ink.coffee_mud.core;

import com.planet_ink.coffee_mud.CharClasses.interfaces.CharClass;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Races.interfaces.Race;
import com.planet_ink.coffee_mud.core.interfaces.CMObject;
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
 * P17: the practices and trains a level gives, as formulas:
 * FORMULA_PRACTICESPERLEVEL and FORMULA_TRAINSPERLEVEL (@x1 the new level,
 * @x2 WIS, @x3 INT, @x4 the class's bonus, @x5 the race's), TRAINSATLEVELS
 * ("1:3, 5:2": more trains at those levels), and the race's FSTTRAN, FSTPRAC,
 * LVLPRAC and LVLTRAIN with the class's LVLTRAIN.  With none set, a level
 * gives what it always did: WIS/6 plus the class's LVLPRAC practices, one train.
 */
public class LevelGains
{
	private LevelGains()
	{
	}

	private static String str(final String key)
	{
		final CMProps P = CMProps.instance();
		return (P == null) ? "" : P.getStr(key, "").trim();
	}

	/**
	 * @param o a race or class
	 * @param code a stat code
	 * @return its number for that code, 0 if it has none
	 */
	public static int field(final CMObject o, final String code)
	{
		if((o instanceof Race) && CMParms.contains(((Race)o).getStatCodes(), code))
			return CMath.s_int(((Race)o).getStat(code));
		if((o instanceof CharClass) && CMParms.contains(((CharClass)o).getStatCodes(), code))
			return CMath.s_int(((CharClass)o).getStat(code));
		return 0;
	}

	private static int wisdom(final MOB mob)
	{
		int w = mob.charStats().getStat(CharStats.STAT_WISDOM);
		final int max = CMProps.getIntVar(CMProps.Int.BASEMAXSTAT) + mob.charStats().getStat(CharStats.CODES.toMAXBASE(CharStats.STAT_WISDOM));
		if(w > max)
			w = max;
		return w;
	}

	private static int formula(final String f, final MOB mob, final int classBonus, final int raceBonus)
	{
		final double[] vars = new double[] {
			mob.basePhyStats().level(), wisdom(mob), mob.charStats().getStat(CharStats.STAT_INTELLIGENCE), classBonus, raceBonus
		};
		try
		{
			return Math.max(0, (int)Math.floor(CMath.parseMathExpression(f, vars)));
		}
		catch(final Exception e)
		{
			Log.errOut("LevelGains", "bad formula '" + f + "': " + e.getMessage());
			return -1;
		}
	}

	/**
	 * @param level a level
	 * @return the trains TRAINSATLEVELS gives at it, more than the usual
	 */
	public static int burst(final int level)
	{
		final String s = str("TRAINSATLEVELS");
		if(s.length() == 0)
			return 0;
		int n = 0;
		for(final String part : CMParms.parseCommas(s, true))
		{
			final int x = part.indexOf(':');
			if((x > 0) && (CMath.s_int(part.substring(0, x).trim()) == level))
				n += CMath.s_int(part.substring(x + 1).trim());
		}
		return n;
	}

	/**
	 * The practices the level the mob has just reached gives it.
	 * @param mob the mob, at its new level
	 * @param curClass the class it levelled in
	 * @return the practices
	 */
	public static int practices(final MOB mob, final CharClass curClass)
	{
		final int classBonus = (curClass == null) ? 0 : curClass.getBonusPracLevel();
		final int raceBonus = field(mob.baseCharStats().getMyRace(), "LVLPRAC");
		final String f = str("FORMULA_PRACTICESPERLEVEL");
		if(f.length() > 0)
		{
			final int n = formula(f, mob, classBonus, raceBonus);
			if(n >= 0)
				return n;
		}
		final int n = (int)Math.floor(CMath.div(wisdom(mob), 6.0)) + classBonus + raceBonus;
		return (n <= 0) ? 1 : n;
	}

	/**
	 * The trains the level the mob has just reached gives it.
	 * @param mob the mob, at its new level
	 * @param curClass the class it levelled in
	 * @return the trains
	 */
	public static int trains(final MOB mob, final CharClass curClass)
	{
		final int classBonus = field(curClass, "LVLTRAIN");
		final int raceBonus = field(mob.baseCharStats().getMyRace(), "LVLTRAIN");
		final int burst = burst(mob.basePhyStats().level());
		final String f = str("FORMULA_TRAINSPERLEVEL");
		if(f.length() > 0)
		{
			final int n = formula(f, mob, classBonus, raceBonus);
			if(n >= 0)
				return n + burst;
		}
		if((classBonus == 0) && (raceBonus == 0))
			return 1 + burst;
		return Math.max(0, 1 + classBonus + raceBonus) + burst;
	}

	/**
	 * At the end of creation: the race's FSTTRAN and FSTPRAC, and the trains
	 * TRAINSATLEVELS gives at level 1 (the class's FSTTRAN and FSTPRAC came
	 * with the class).
	 * @param mob the new character
	 */
	public static void firstLevel(final MOB mob)
	{
		final Race R = mob.baseCharStats().getMyRace();
		mob.setTrains(mob.getTrains() + field(R, "FSTTRAN") + burst(1));
		mob.setPractices(mob.getPractices() + field(R, "FSTPRAC"));
	}

	/**
	 * A line for a class's or race's description: what a level gives.
	 * @param o a race or class
	 * @return the text, or "" if nothing is set
	 */
	public static String describe(final CMObject o)
	{
		final StringBuilder s = new StringBuilder("");
		final int t = field(o, "LVLTRAIN");
		if(t != 0)
			s.append(((t > 0) ? "+" : "") + t + " trains/level ");
		if(o instanceof Race)
		{
			final int p = field(o, "LVLPRAC");
			if(p != 0)
				s.append(((p > 0) ? "+" : "") + p + " practices/level ");
			final int t1 = field(o, "FSTTRAN"), p1 = field(o, "FSTPRAC");
			if(t1 != 0)
				s.append(((t1 > 0) ? "+" : "") + t1 + " trains at first ");
			if(p1 != 0)
				s.append(((p1 > 0) ? "+" : "") + p1 + " practices at first ");
		}
		return s.toString().trim();
	}
}
