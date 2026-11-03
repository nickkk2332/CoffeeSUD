package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;

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
 * W23: a quest's game-time window, SET SCHEDULE "rules": each must hold.
 * HOUR a-b, WEEKDAY name, EVERY n DAYS (AT DAWN|AT HOUR h), SEASON name,
 * MONTH name, MOON (moon) phase, HOLIDAY name, WEATHER state (in the quest's
 * area).  The quest starts as its window opens; SET DURATION UNTIL DUSK,
 * UNTIL HOUR h or UNTIL END ends it at dusk, at that hour, or as the window
 * closes.
 */
public final class Schedules
{
	private Schedules()
	{
	}

	/** the rules' words */
	public static final String[] RULES = { "HOUR", "WEEKDAY", "EVERY", "SEASON", "MONTH", "MOON", "HOLIDAY", "WEATHER" };

	/**
	 * Reads a schedule into its rules.
	 * @param text the schedule
	 * @return each rule: its word, then the rest of it
	 */
	public static List<String[]> parse(final String text)
	{
		final List<String[]> rules = new ArrayList<String[]>();
		String kind = null;
		final StringBuilder rest = new StringBuilder();
		for(String w : CMParms.parse(text.replace('"', ' ')))
		{
			w = w.trim();
			if(w.length() == 0)
				continue;
			final boolean atHour = w.equalsIgnoreCase("HOUR") && rest.toString().toUpperCase().endsWith(" AT"); // EVERY n DAYS AT HOUR h
			if((!atHour) && (Arrays.asList(RULES).contains(w.toUpperCase()) || w.equalsIgnoreCase("AND")))
			{
				if(kind != null)
					rules.add(new String[] { kind, rest.toString().trim() });
				kind = w.equalsIgnoreCase("AND") ? null : w.toUpperCase();
				rest.setLength(0);
			}
			else
				rest.append(" ").append(w);
		}
		if(kind != null)
			rules.add(new String[] { kind, rest.toString().trim() });
		return rules;
	}

	/**
	 * What is wrong with a schedule, for SCRIPTCHECK and the like.
	 * @param text the schedule
	 * @return the problems, or none
	 */
	public static List<String> problems(final String text)
	{
		final List<String> why = new ArrayList<String>();
		final List<String[]> rules = parse(text);
		if(rules.isEmpty())
			why.add("SCHEDULE has no rule: HOUR, WEEKDAY, EVERY, SEASON, MONTH, MOON, HOLIDAY or WEATHER");
		for(final String[] r : rules)
		{
			if(r[1].length() == 0)
				why.add("SCHEDULE " + r[0] + " says nothing");
			else
			if(r[0].equals("HOUR") && (hours(r[1]) == null))
				why.add("SCHEDULE HOUR is not a-b: " + r[1]);
			else
			if(r[0].equals("EVERY") && (every(r[1]) == null))
				why.add("SCHEDULE EVERY is not n DAYS (AT DAWN|AT HOUR h): " + r[1]);
		}
		return why;
	}

	private static int[] hours(final String s)
	{
		final String[] p = s.trim().split("\\s*-\\s*");
		if((p.length < 1) || (p.length > 2) || (!CMath.isInteger(p[0])) || ((p.length == 2) && (!CMath.isInteger(p[1]))))
			return null;
		return new int[] { CMath.s_int(p[0]), CMath.s_int(p[p.length - 1]) };
	}

	/** n, then the hour it opens (-1 for all day, -2 for dawn) */
	private static int[] every(final String s)
	{
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^(\\d+)\\s*(?:DAYS?)?(?:\\s+AT\\s+(DAWN|HOUR\\s+(\\d+)))?$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(s.trim());
		if(!m.find() || (CMath.s_int(m.group(1)) < 1))
			return null;
		final int at = (m.group(2) == null) ? -1 : m.group(2).toUpperCase().startsWith("DAWN") ? -2 : CMath.s_int(m.group(3));
		return new int[] { CMath.s_int(m.group(1)), at };
	}

	private static boolean holds(final String[] r, final TimeClock C, final Area A)
	{
		final String v = r[1];
		final int hour = C.getHourOfDay();
		switch(r[0])
		{
		case "HOUR":
		{
			final int[] h = hours(v);
			if(h == null)
				return false;
			return (h[0] <= h[1]) ? ((hour >= h[0]) && (hour <= h[1])) : ((hour >= h[0]) || (hour <= h[1]));
		}
		case "WEEKDAY":
			return Holidays.weekday(C).equalsIgnoreCase(v);
		case "EVERY":
		{
			final int[] e = every(v);
			if((e == null) || ((Holidays.dayNumber(C) % e[0]) != 0))
				return false;
			final int from = (e[1] == -2) ? C.getDawnToDusk()[TimeClock.TimeOfDay.DAWN.ordinal()] : e[1];
			return hour >= from;
		}
		case "SEASON":
		{
			final Calendars.SeasonNow N = (A == null) ? Calendars.season(C, 0) : Calendars.season(A);
			return N.name.equalsIgnoreCase(v) || N.counts.name().equalsIgnoreCase(v);
		}
		case "MONTH":
		{
			if(CMath.isInteger(v))
				return C.getMonth() == (CMath.s_int(v) - 1);
			final String[] names = C.getMonthNames();
			return (C.getMonth() >= 0) && (C.getMonth() < names.length) && names[C.getMonth()].equalsIgnoreCase(v);
		}
		case "MOON":
			return Holidays.moonIs(v, C);
		case "HOLIDAY":
			return Holidays.on(v);
		case "WEATHER":
		{
			if(A == null)
				return false;
			final int w = A.getClimateObj().weatherType(null);
			return (w >= 0) && (w < Climate.WEATHER_DESCS.length) && Climate.WEATHER_DESCS[w].startsWith(v.toUpperCase());
		}
		default:
			return false;
		}
	}

	/**
	 * Whether a schedule's window is open now.
	 * @param text the schedule
	 * @param C the quest's clock
	 * @param A the quest's area, or null
	 * @return true if every rule holds
	 */
	public static boolean open(final String text, final TimeClock C, final Area A)
	{
		final List<String[]> rules = parse(text);
		if(rules.isEmpty())
			return false;
		for(final String[] r : rules)
		{
			if(!holds(r, C, A))
				return false;
		}
		return true;
	}

	/**
	 * Whether a quest run UNTIL something is over.
	 * @param until DUSK, HOUR h or END
	 * @param C the quest's clock
	 * @param open whether its window is open now (for UNTIL END)
	 * @return true if it should end: DUSK at the dusk hour, HOUR h at that hour, END as the window closes
	 */
	public static boolean over(final String until, final TimeClock C, final boolean open)
	{
		final String u = until.trim().toUpperCase();
		if(u.equals("DUSK"))
		{
			final int[] d = (Calendars.daylight(C) != null) ? Calendars.daylight(C) : C.getDawnToDusk();
			return C.getHourOfDay() >= d[TimeClock.TimeOfDay.DUSK.ordinal()];
		}
		if(u.startsWith("HOUR"))
			return C.getHourOfDay() >= CMath.s_int(u.substring(4).trim());
		return !open;
	}
}
