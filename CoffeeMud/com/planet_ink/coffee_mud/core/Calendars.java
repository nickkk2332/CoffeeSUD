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
	public static final String[] KEYS	= { "HOURS", "DAYS", "MONTHS", "WEEK", "YEARS", "DAWNHR", "DAYHR", "DUSKHR", "NIGHTHR", "START",
											"SEASONS", "SEASONOFFSET", "DAYLIGHT", "MOONS" };

	/** W22: the keys that are the area's own, not its clock's */
	private static final String[] AREA_KEYS = { "SEASONOFFSET" };

	/** W22: a named season: what it counts as for weather and the like, and its months */
	public static final class SeasonDef
	{
		/** its name */
		public final String				name;
		/** the season it counts as */
		public final TimeClock.Season	counts;
		/** its first and last month: a number from 1, or a month's name */
		public final String				from, to;

		SeasonDef(final String name, final TimeClock.Season counts, final String from, final String to)
		{
			this.name = name;
			this.counts = counts;
			this.from = from;
			this.to = to;
		}

		private static int month(final TimeClock C, final String m)
		{
			if(CMath.isInteger(m))
				return CMath.s_int(m) - 1;
			final String[] names = C.getMonthNames();
			for(int i = 0; i < names.length; i++)
			{
				if(names[i].equalsIgnoreCase(m))
					return i;
			}
			return -1;
		}

		/**
		 * @param C the clock
		 * @param m a month, from 0
		 * @return true if the month is in this season
		 */
		public boolean covers(final TimeClock C, final int m)
		{
			final int a = month(C, from), b = month(C, to);
			if((a < 0) || (b < 0))
				return false;
			if(a <= b)
				return (m >= a) && (m <= b);
			return (m >= a) || (m <= b);
		}
	}

	/** W22: a moon: its name, its cycle in days, and its offset in days */
	public static final class Moon
	{
		/** its name */
		public final String	name;
		/** its cycle, in days */
		public final int	cycle;
		/** its offset, in days */
		public final int	offset;

		Moon(final String name, final int cycle, final int offset)
		{
			this.name = name;
			this.cycle = cycle;
			this.offset = offset;
		}
	}

	/** W22: the season now: its name, and the season it counts as */
	public static final class SeasonNow
	{
		/** its name: a calendar's own, else the built-in one in lower case */
		public final String				name;
		/** the season it counts as */
		public final TimeClock.Season	counts;
		/** true if it is a calendar's own */
		public final boolean			named;

		SeasonNow(final String name, final TimeClock.Season counts, final boolean named)
		{
			this.name = name;
			this.counts = counts;
			this.named = named;
		}
	}

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
		/** W22: its named seasons, or null for the built-in four */
		public List<SeasonDef>		seasons		= null;
		/** W22: months the seasons are shifted by in this area, or null */
		public Integer				seasonOffset = null;
		/** W22: the dawn, day, dusk and night hours in a season, by its name (upper case), or null */
		public Map<String, int[]>	daylight	= null;
		/** W22: its moons, or null for the one built-in moon */
		public List<Moon>			moons		= null;
		/** each key as given */
		public final Map<String, String> raw	= new LinkedHashMap<String, String>();
		/** what could not be read */
		public final List<String>	problems	= new ArrayList<String>();

		Spec(final String text)
		{
			this.text = text;
		}

		/**
		 * @return true if it gives the clock something, not only the area's SEASONOFFSET
		 */
		public boolean needsClock()
		{
			for(final String k : raw.keySet())
			{
				if(!Arrays.asList(AREA_KEYS).contains(k))
					return true;
			}
			return false;
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
			S.raw.put(k, v);
			if(x >= 10)
			{
				parseW22(S, k, v);
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

	private static TimeClock.Season baseSeason(final String s)
	{
		for(final TimeClock.Season B : TimeClock.Season.values())
		{
			if(B.name().equalsIgnoreCase(s.trim()))
				return B;
		}
		return null;
	}

	private static void parseW22(final Spec S, final String k, final String v)
	{
		if(k.equals("SEASONOFFSET"))
		{
			if(CMath.isInteger(v))
				S.seasonOffset = Integer.valueOf(CMath.s_int(v));
			else
				S.problems.add("SEASONOFFSET is not a number: " + v);
			return;
		}
		for(final String part : v.split(","))
		{
			if(part.trim().length() == 0)
				continue;
			final String[] p = part.trim().split("\\s*:\\s*");
			if(k.equals("SEASONS"))
			{
				// NAME:COUNTS:FROM-TO (a month or a range of them, numbers from 1 or names)
				final TimeClock.Season B = (p.length == 3) ? baseSeason(p[1]) : null;
				if((B == null) || (p[0].length() == 0))
				{
					S.problems.add("SEASONS: '" + part.trim() + "' is not NAME:SPRING|SUMMER|FALL|WINTER:MONTHS");
					continue;
				}
				final String[] r = p[2].split("\\s*-\\s*");
				if(S.seasons == null)
					S.seasons = new ArrayList<SeasonDef>();
				S.seasons.add(new SeasonDef(p[0], B, r[0], (r.length > 1) ? r[1] : r[0]));
			}
			else
			if(k.equals("DAYLIGHT"))
			{
				// SEASON:DAWN-NIGHT, or SEASON:DAWN-DAY-DUSK-NIGHT
				final String[] h = (p.length == 2) ? p[1].split("\\s*-\\s*") : new String[0];
				boolean ok = (h.length == 2) || (h.length == 4);
				for(final String n : h)
					ok = ok && CMath.isInteger(n);
				if(!ok)
				{
					S.problems.add("DAYLIGHT: '" + part.trim() + "' is not SEASON:DAWN-NIGHT");
					continue;
				}
				final int[] d = new int[4];
				if(h.length == 4)
				{
					for(int i = 0; i < 4; i++)
						d[i] = CMath.s_int(h[i]);
				}
				else
				{
					d[0] = CMath.s_int(h[0]);
					d[3] = CMath.s_int(h[1]);
					d[1] = Math.min(d[0] + 1, d[3]);
					d[2] = ((d[3] - 1) > d[1]) ? (d[3] - 1) : d[3];
				}
				if(S.daylight == null)
					S.daylight = new HashMap<String, int[]>();
				S.daylight.put(p[0].toUpperCase(), d);
			}
			else
			if(k.equals("MOONS"))
			{
				// NAME:CYCLE(:OFFSET), in days
				if((p.length < 2) || (p.length > 3) || (p[0].length() == 0) || (!CMath.isInteger(p[1])) || (CMath.s_int(p[1]) < 1) || ((p.length == 3) && (!CMath.isInteger(p[2]))))
				{
					S.problems.add("MOONS: '" + part.trim() + "' is not NAME:DAYS(:OFFSET)");
					continue;
				}
				if(S.moons == null)
					S.moons = new ArrayList<Moon>();
				S.moons.add(new Moon(p[0], CMath.s_int(p[1]), (p.length == 3) ? CMath.s_int(p[2]) : 0));
			}
		}
	}

	private static final Map<String, Spec> parsed = new HashMap<String, Spec>();

	/**
	 * @param A an area
	 * @return its own calendar, or null
	 */
	public static Spec of(final Area A)
	{
		final String t = (A == null) ? null : A.getBlurbFlag(KEY);
		if((t == null) || (t.trim().length() == 0) || t.trim().equalsIgnoreCase("NONE"))
			return null;
		synchronized(parsed)
		{
			Spec S = parsed.get(t);
			if(S == null)
			{
				if(parsed.size() > 500)
					parsed.clear();
				S = parse(t);
				parsed.put(t, S);
			}
			return S;
		}
	}

	/**
	 * W22: the months an area's seasons are shifted by: its own SEASONOFFSET, else its nearest parent's.
	 * @param A the area
	 * @return the months
	 */
	public static int offset(final Area A)
	{
		return offset(A, 0);
	}

	private static int offset(final Area A, final int depth)
	{
		if((A == null) || (depth > 20))
			return 0;
		final Spec S = of(A);
		if((S != null) && (S.seasonOffset != null))
			return S.seasonOffset.intValue();
		for(final Enumeration<Area> p = A.getParents(); p.hasMoreElements();)
		{
			final int n = offset(p.nextElement(), depth + 1);
			if(n != 0)
				return n;
		}
		return 0;
	}

	private static TimeClock.Season builtIn(final TimeClock C, final int m)
	{
		final int div = C.getMonthsInSeason();
		if(m < div)
			return TimeClock.Season.WINTER;
		if(m < (div * 2))
			return TimeClock.Season.SPRING;
		if(m < (div * 3))
			return TimeClock.Season.SUMMER;
		return TimeClock.Season.FALL;
	}

	/**
	 * W22: the season an area is in: its calendar's named one, its SEASONOFFSET months on.
	 * @param A the area, or null for the global clock
	 * @return the season now
	 */
	public static SeasonNow season(final Area A)
	{
		final TimeClock C = (A == null) ? CMLib.time().globalClock() : A.getTimeObj();
		return season(C, offset(A));
	}

	/**
	 * W22: a clock's season, some months on.
	 * @param C the clock
	 * @param offset the months
	 * @return the season now
	 */
	public static SeasonNow season(final TimeClock C, final int offset)
	{
		final int n = Math.max(1, C.getMonthsInYear());
		final int m = Math.floorMod(C.getMonth() + offset, n);
		final Spec S = specs.get(C);
		if((S != null) && (S.seasons != null))
		{
			for(final SeasonDef D : S.seasons)
			{
				if(D.covers(C, m))
					return new SeasonNow(D.name, D.counts, true);
			}
		}
		final TimeClock.Season B = builtIn(C, m);
		return new SeasonNow(B.name().toLowerCase(), B, false);
	}

	/**
	 * W22: what a clock's named season in a month counts as, for the clock's own season.
	 * @param C the clock
	 * @param m the month, from 0
	 * @return the season it counts as, or null where the calendar names none
	 */
	public static TimeClock.Season countsAs(final TimeClock C, final int m)
	{
		final Spec S = specs.get(C);
		if((S == null) || (S.seasons == null))
			return null;
		for(final SeasonDef D : S.seasons)
		{
			if(D.covers(C, m))
				return D.counts;
		}
		return null;
	}

	/**
	 * W22: a clock's dawn, day, dusk and night hours in its season now (DAYLIGHT).
	 * @param C the clock
	 * @return the hours, or null for its own all year
	 */
	public static int[] daylight(final TimeClock C)
	{
		final Spec S = specs.get(C);
		if((S == null) || (S.daylight == null))
			return null;
		final SeasonNow N = season(C, 0);
		final int[] d = S.daylight.get(N.name.toUpperCase());
		return (d != null) ? d : S.daylight.get(N.counts.name());
	}

	/**
	 * W22: a clock's moons.
	 * @param C the clock
	 * @return its MOONS, or an empty list for the one built-in moon
	 */
	public static List<Moon> moons(final TimeClock C)
	{
		final Spec S = (C == null) ? null : specs.get(C);
		return ((S == null) || (S.moons == null)) ? new ArrayList<Moon>(0) : S.moons;
	}

	/**
	 * W22: a moon of a clock by name.
	 * @param C the clock
	 * @param name its name, or the start of it
	 * @return the moon, or null
	 */
	public static Moon moon(final TimeClock C, final String name)
	{
		for(final Moon M : moons(C))
		{
			if(M.name.equalsIgnoreCase(name.trim()))
				return M;
		}
		for(final Moon M : moons(C))
		{
			if(M.name.toUpperCase().startsWith(name.trim().toUpperCase()))
				return M;
		}
		return null;
	}

	/**
	 * W22: the phase a moon is in, from the days since the clock's start, before anything alters it.
	 * @param C the clock
	 * @param M the moon
	 * @return the phase's place, 0 (new) to 7
	 */
	public static int phaseIndex(final TimeClock C, final Moon M)
	{
		final long day = ((((long)C.getYear() * C.getMonthsInYear()) + C.getMonth()) * C.getDaysInMonth()) + (C.getDayOfMonth() - 1);
		final long at = Math.floorMod(day + M.offset, (long)M.cycle);
		return (int)Math.min(7, Math.floor((at * 8.0) / M.cycle));
	}

	/**
	 * W22: the phase a moon is in, here: moon-altering effects on the room and its area shift it.
	 * @param C the clock
	 * @param M the moon
	 * @param room the room, or null
	 * @return the phase
	 */
	public static TimeClock.MoonPhase phase(final TimeClock C, final Moon M, final Room room)
	{
		int dex = phaseIndex(C, M);
		if(room != null)
		{
			final Area area = room.getArea();
			final List<com.planet_ink.coffee_mud.Abilities.interfaces.Ability> fx = new ArrayList<com.planet_ink.coffee_mud.Abilities.interfaces.Ability>(1);
			if(room.numEffects() > 0)
				fx.addAll(CMLib.flags().domainAffects(room, com.planet_ink.coffee_mud.Abilities.interfaces.Ability.DOMAIN_MOONALTERING));
			if((area != null) && (area.numEffects() > 0))
				fx.addAll(CMLib.flags().domainAffects(area, com.planet_ink.coffee_mud.Abilities.interfaces.Ability.DOMAIN_MOONALTERING));
			for(final com.planet_ink.coffee_mud.Abilities.interfaces.Ability A : fx)
				dex = (dex + A.abilityCode()) % 8;
		}
		return TimeClock.MoonPhase.values()[Math.floorMod(dex, 8)];
	}

	/**
	 * W22: a moon's phase in words, as TIME says it.
	 * @param name the moon's name
	 * @param P its phase
	 * @return the line
	 */
	public static String phaseLine(final String name, final TimeClock.MoonPhase P)
	{
		final String n = name.substring(0, 1).toUpperCase() + name.substring(1);
		switch(P)
		{
		case NEW: return n + " is new.";
		case WAXCRESCENT: return n + " is a waxing crescent.";
		case WAXQUARTER: return n + " is in its first quarter.";
		case WAXGIBBOUS: return n + " is waxing gibbous, almost full.";
		case FULL: return n + " is full.";
		case WANEGIBBOUS: return n + " is waning gibbous, no longer full.";
		case WANEQUARTER: return n + " is in its last quarter.";
		case WANECRESCENT: return n + " is a waning crescent.";
		default: return n + " is " + P.name().toLowerCase() + ".";
		}
	}

	/**
	 * W22, for VARIES: a block in a season's own name, <the Burn>...</the Burn>,
	 * kept in that season and taken out in any other.
	 * @param A the area
	 * @param text the text
	 * @return the text, with its named-season blocks done
	 */
	public static String seasonBlocks(final Area A, final String text)
	{
		if((A == null) || (text == null) || (text.indexOf('<') < 0))
			return text;
		final Spec S = specs.get(A.getTimeObj());
		if((S == null) || (S.seasons == null))
			return text;
		final SeasonNow N = season(A);
		String t = text;
		for(final SeasonDef D : S.seasons)
		{
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("<" + java.util.regex.Pattern.quote(D.name) + ">(.*?)</" + java.util.regex.Pattern.quote(D.name) + ">",
				java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(t);
			final StringBuffer b = new StringBuffer();
			final boolean now = D.name.equalsIgnoreCase(N.name);
			while(m.find())
				m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(now ? m.group(1) : ""));
			m.appendTail(b);
			t = b.toString();
		}
		return t;
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
		C.setMonth(C.getMonth()); // W22: its season again, from its SEASONS
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
			if((S == null) || (!S.needsClock())) // W22: SEASONOFFSET alone is the area's, not a clock's
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
		if(old != null)
		{
			for(final String k : new String[] { "SEASONS", "SEASONOFFSET", "DAYLIGHT", "MOONS" }) // W22
			{
				if(old.raw.containsKey(k))
					str.append(" ").append(k).append("=\"").append(old.raw.get(k)).append("\"");
			}
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
			final SeasonNow N = season(A); // W22
			str.append("; the season ").append(N.name);
			if(N.named)
				str.append(" (as ").append(N.counts.name().toLowerCase()).append(")");
			for(final Moon M : moons(C))
				str.append("; ").append(phaseLine(M.name, phase(C, M, null)));
		}
		if(S != null)
		{
			for(final String why : S.problems)
				str.append("\n\r  ").append(why);
		}
		return str.toString();
	}
}
