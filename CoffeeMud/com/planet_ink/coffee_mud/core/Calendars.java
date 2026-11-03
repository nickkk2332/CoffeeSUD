package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;

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
 * W5: an area's calendar as world content: the area stat CALENDAR (a blurb
 * flag, so kept in the area's data in its .cmare), with HOURS=, DAYS=,
 * MONTHS="a,b", WEEK="a,b", YEARS="a,b", DAWNHR=, DAYHR=, DUSKHR=, NIGHTHR=
 * and START=Y/M/D/H.  An area with one has a clock of its own, which its
 * child areas share; what it leaves out comes from its parent's clock, else
 * the global one.  The saved date (TIMECLOCK, save state) goes on from
 * where it was; a clock with no saved date starts at START.  STARTDATE=
 * in coffeemud.ini (or world.ini) is the global clock's START.
 */
public final class Calendars
{
	private Calendars()
	{
	}

	/** the area blurb flag that holds an area's calendar */
	public static final String	KEY		= "CALENDAR";

	/** the keys a calendar may give */
	public static final String[] KEYS	= { "HOURS", "DAYS", "MONTHS", "WEEK", "YEARS", "DAWNHR", "DAYHR", "DUSKHR", "NIGHTHR", "START" };

	/** a calendar, as read */
	public static final class Spec
	{
		/** its text */
		public final String			text;
		/** hours in a day, days in a month, or null to inherit */
		public Integer				hours, days;
		/** month, weekday and year names, or null to inherit */
		public String[]				months, week, years;
		/** the hours of dawn, day, dusk and night, each null to inherit */
		public final Integer[]		tod			= new Integer[4];
		/** year, month, day, hour, or null */
		public int[]				start		= null;
		/** what could not be read */
		public final List<String>	problems	= new ArrayList<String>();

		Spec(final String text)
		{
			this.text = text;
		}
	}

	private static final Map<TimeClock, Spec>	specs	= Collections.synchronizedMap(new WeakHashMap<TimeClock, Spec>());
	private static final Map<TimeClock, Area>	homes	= Collections.synchronizedMap(new WeakHashMap<TimeClock, Area>());
	private static final Set<Area>				given	= Collections.synchronizedSet(Collections.newSetFromMap(new WeakHashMap<Area, Boolean>()));

	private static String[] list(final String s)
	{
		final List<String> L = CMParms.parseCommas(s, true);
		return L.toArray(new String[L.size()]);
	}

	/**
	 * A date, Y/M/D/H (the hour may be left out).
	 * @param s the text
	 * @return year, month, day, hour (-1 for none), or null
	 */
	public static int[] date(final String s)
	{
		if(s == null)
			return null;
		final String[] p = s.trim().split("\\s*/\\s*");
		if((p.length < 3) || (p.length > 4))
			return null;
		final int[] d = new int[] { 0, 0, 0, -1 };
		for(int i = 0; i < p.length; i++)
		{
			if(!CMath.isInteger(p[i]))
				return null;
			d[i] = CMath.s_int(p[i]);
		}
		return d;
	}

	/**
	 * Reads a calendar.
	 * @param text the CALENDAR text
	 * @return the calendar, with what could not be read in its problems
	 */
	public static Spec parse(final String text)
	{
		final Spec S = new Spec(text.trim());
		final Map<String, String> m = CMParms.parseEQParms(text);
		for(final String k : m.keySet())
		{
			final String v = m.get(k).trim();
			final int x = Arrays.asList(KEYS).indexOf(k);
			if(x < 0)
			{
				S.problems.add("unknown key " + k);
				continue;
			}
			if((x == 0) || (x == 1) || ((x >= 5) && (x <= 8)))
			{
				if(!CMath.isInteger(v))
				{
					S.problems.add(k + " is not a number: " + v);
					continue;
				}
				final int n = CMath.s_int(v);
				if(x == 0)
					S.hours = Integer.valueOf(n);
				else
				if(x == 1)
					S.days = Integer.valueOf(n);
				else
					S.tod[x - 5] = Integer.valueOf(n);
			}
			else
			if(x == 2)
				S.months = list(v);
			else
			if(x == 3)
				S.week = list(v);
			else
			if(x == 4)
				S.years = list(v);
			else
			{
				S.start = date(v);
				if(S.start == null)
					S.problems.add("START is not Y/M/D/H: " + v);
			}
		}
		if((S.hours != null) && (S.hours.intValue() < 1))
			S.problems.add("HOURS must be 1 or more");
		if((S.days != null) && (S.days.intValue() < 1))
			S.problems.add("DAYS must be 1 or more");
		if((S.months != null) && (S.months.length == 0))
			S.problems.add("MONTHS names no month");
		return S;
	}

	/**
	 * @param A an area
	 * @return its own calendar, or null
	 */
	public static Spec of(final Area A)
	{
		final String t = (A == null) ? null : A.getBlurbFlag(KEY);
		if((t == null) || (t.trim().length() == 0) || t.trim().equalsIgnoreCase("NONE"))
			return null;
		return parse(t);
	}

	/**
	 * @param C a clock
	 * @return true if its calendar is an area's CALENDAR, so that only its date is save state
	 */
	public static boolean isContent(final TimeClock C)
	{
		return (C != null) && specs.containsKey(C);
	}

	/**
	 * @param C a clock
	 * @return the CALENDAR it keeps, or null
	 */
	public static Spec specOf(final TimeClock C)
	{
		return (C == null) ? null : specs.get(C);
	}

	private static TimeClock parentClock(final Area A)
	{
		for(final Enumeration<Area> p = A.getParents(); p.hasMoreElements();)
		{
			final Area P = p.nextElement();
			if((P != null) && (P.getTimeObj() != null))
				return P.getTimeObj();
		}
		return CMLib.time().globalClock();
	}

	private static int depth(final Area A, final int n)
	{
		int d = 0;
		if(n > 20)
			return d;
		for(final Enumeration<Area> p = A.getParents(); p.hasMoreElements();)
			d = Math.max(d, 1 + depth(p.nextElement(), n + 1));
		return d;
	}

	/**
	 * Sets a clock's calendar: each thing the calendar gives, the rest from
	 * the base clock.
	 * @param C the clock
	 * @param S the calendar
	 * @param base where the rest comes from
	 */
	public static void configure(final TimeClock C, final Spec S, final TimeClock base)
	{
		final TimeClock B = (base == null) || (base == C) ? CMLib.time().globalClock() : base;
		C.setHoursInDay((S.hours != null) ? S.hours.intValue() : B.getHoursInDay());
		C.setDaysInMonth((S.days != null) ? S.days.intValue() : B.getDaysInMonth());
		C.setMonthsInYear((S.months != null) ? S.months : B.getMonthNames());
		C.setWeekNames((S.week != null) ? S.week : B.getWeekNames());
		C.setYearNames((S.years != null) ? S.years : B.getYearNames());
		final int[] b = B.getDawnToDusk();
		final int[] t = new int[4];
		for(int i = 0; i < 4; i++)
			t[i] = (S.tod[i] != null) ? S.tod[i].intValue() : b[i];
		C.setDawnToDusk(t[TimeClock.TimeOfDay.DAWN.ordinal()], t[TimeClock.TimeOfDay.DAY.ordinal()],
						t[TimeClock.TimeOfDay.DUSK.ordinal()], t[TimeClock.TimeOfDay.NIGHT.ordinal()]);
	}

	/**
	 * Puts a clock at a date: year, month (from 1), day, hour (-1 for the dawn hour).
	 * @param C the clock
	 * @param d the date
	 */
	public static void setDate(final TimeClock C, final int[] d)
	{
		if((C == null) || (d == null))
			return;
		C.setYear(d[0]);
		C.setMonth(Math.max(0, Math.min(C.getMonthsInYear() - 1, d[1] - 1))); // the clock counts months from 0
		C.setDayOfMonth(Math.max(1, Math.min(C.getDaysInMonth(), d[2])));
		final int h = (d[3] < 0) ? C.getDawnToDusk()[TimeClock.TimeOfDay.DAWN.ordinal()] : d[3];
		C.setHourOfDay(Math.max(0, Math.min(C.getHoursInDay() - 1, h)));
	}

	/**
	 * Called by a clock as it has read its saved date (or found none): its
	 * CALENDAR's fields again, and with no saved date, its START (for the
	 * global clock, STARTDATE).
	 * @param C the clock
	 * @param hadDate true if a saved date was read
	 */
	public static void loaded(final TimeClock C, final boolean hadDate)
	{
		final Spec S = specs.get(C);
		if(S != null)
		{
			final Area home = homes.get(C);
			configure(C, S, (home == null) ? null : parentClock(home));
			if((!hadDate) && (S.start != null))
				setDate(C, S.start);
		}
		else
		if((!hadDate) && (C == CMLib.time().globalClock()))
		{
			final String s = CMProps.instance().getProperty("STARTDATE");
			final int[] d = date(s);
			if(d != null)
				setDate(C, d);
			else
			if((s != null) && (s.trim().length() > 0))
				Log.errOut("Calendars", "STARTDATE is not Y/M/D/H: " + s);
		}
	}

	/**
	 * Gives each area with a CALENDAR a clock of its own (parents first, so
	 * a child with its own calendar keeps it), and an area whose CALENDAR
	 * was taken away its parent's clock again.  Run as the world loads, and
	 * after MODIFY AREA CALENDAR and WORLD RELOAD.
	 */
	public static void applyAll()
	{
		final List<Area> areas = new ArrayList<Area>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			areas.add(a.nextElement());
		final Map<Area, Integer> depths = new HashMap<Area, Integer>();
		for(final Area A : areas)
			depths.put(A, Integer.valueOf(depth(A, 0)));
		Collections.sort(areas, new Comparator<Area>()
		{
			@Override
			public int compare(final Area o1, final Area o2)
			{
				return depths.get(o1).compareTo(depths.get(o2));
			}
		});
		for(final Area A : areas)
		{
			final Spec S = of(A);
			if(S == null)
			{
				if(given.remove(A))
				{
					specs.remove(A.getTimeObj());
					A.setTimeObj(parentClock(A));
				}
				else
				if(specs.containsKey(A.getTimeObj()) && (A.getTimeObj() != parentClock(A)))
					specs.remove(A.getTimeObj()); // a StdTimeZone's own clock, its CALENDAR taken away
				continue;
			}
			for(final String why : S.problems)
				Log.errOut("Calendars", A.Name() + " CALENDAR: " + why);
			apply(A, S);
		}
	}

	private static void apply(final Area A, final Spec S)
	{
		final TimeClock global = CMLib.time().globalClock();
		final TimeClock parent = parentClock(A);
		TimeClock C = A.getTimeObj();
		final boolean fresh = (C == null) || (C == global) || (C == parent);
		if(fresh)
		{
			C = (TimeClock)CMClass.getCommon("DefaultTimeClock");
			C.setLoadName(A.Name());
			A.setTimeObj(C);
			given.add(A);
		}
		specs.put(C, S);
		homes.put(C, A);
		configure(C, S, parent);
		if(fresh)
			C.tick(A, Tickable.TICKID_AREA); // reads its saved date now, or starts at START
	}

	/**
	 * A calendar's text from a clock's own settings, for the area editor's
	 * calendar screen (and the START of the one it had).
	 * @param C the clock
	 * @param old the calendar it had, or null
	 * @return the CALENDAR text
	 */
	public static String textOf(final TimeClock C, final Spec old)
	{
		final int[] t = C.getDawnToDusk();
		final StringBuilder str = new StringBuilder();
		str.append("HOURS=").append(C.getHoursInDay()).append(" DAYS=").append(C.getDaysInMonth());
		str.append(" MONTHS=\"").append(CMParms.toListString(C.getMonthNames())).append("\"");
		str.append(" WEEK=\"").append(CMParms.toListString(C.getWeekNames())).append("\"");
		str.append(" YEARS=\"").append(CMParms.toListString(C.getYearNames())).append("\"");
		str.append(" DAWNHR=").append(t[TimeClock.TimeOfDay.DAWN.ordinal()]);
		str.append(" DAYHR=").append(t[TimeClock.TimeOfDay.DAY.ordinal()]);
		str.append(" DUSKHR=").append(t[TimeClock.TimeOfDay.DUSK.ordinal()]);
		str.append(" NIGHTHR=").append(t[TimeClock.TimeOfDay.NIGHT.ordinal()]);
		if((old != null) && (old.start != null))
		{
			str.append(" START=").append(old.start[0]).append("/").append(old.start[1]).append("/").append(old.start[2]);
			if(old.start[3] >= 0)
				str.append("/").append(old.start[3]);
		}
		return str.toString();
	}

	/**
	 * In words, for MODIFY AREA CALENDAR.
	 * @param A the area
	 * @return its calendar
	 */
	public static String describe(final Area A)
	{
		final TimeClock C = A.getTimeObj();
		final Spec S = of(A);
		final StringBuilder str = new StringBuilder();
		if(S == null)
			str.append(A.Name()).append(" has no CALENDAR of its own; its clock is ").append((C == CMLib.time().globalClock()) ? "the global one" : "its parent's or its own");
		else
			str.append(A.Name()).append(" CALENDAR ").append(S.text);
		if(C != null)
		{
			str.append(": ").append(C.getHoursInDay()).append("-hour days, ").append(C.getDaysInMonth()).append(" days a month, months ")
			   .append(CMParms.toListString(C.getMonthNames())).append("; now ").append(C.getShortTimeDescription());
		}
		if(S != null)
		{
			for(final String why : S.problems)
				str.append("\n\r  ").append(why);
		}
		return str.toString();
	}
}
