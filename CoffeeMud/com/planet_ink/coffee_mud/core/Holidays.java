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
 * W6: holidays and other game-dated quests.  A quest (a holiday is one)
 * may start on more than a MUDDAY: SET WEEKDAY name (each week), SET EVERY
 * n DAYS FROM y/m/d, SET SEASONSTART season (the first day of it, a
 * calendar's own season by name too) and SET MOON [moon] phase (the first
 * day of that phase; a calendar's named moon, else the first).  SET HOLIDAY
 * name has a quest run exactly while that holiday does.  ISHOLIDAY(name),
 * HOLIDAY(), +HOLIDAY and -HOLIDAY, VARIES' &lt;HOLIDAY name&gt; and CALENDAR
 * HOLIDAYS see them by name.
 */
public final class Holidays
{
	private Holidays()
	{
	}

	/** the start-date kinds W6 adds to MUDDAY and DATE */
	public static final String[] RECURRENCES = { "WEEKDAY", "EVERY", "SEASONSTART", "MOON" };

	/**
	 * @param startDate a quest's start date
	 * @return true if it is one of W6's recurrences
	 */
	public static boolean isRecurrence(final String startDate)
	{
		final String u = (startDate == null) ? "" : startDate.trim().toUpperCase();
		for(final String r : RECURRENCES)
		{
			if(u.startsWith(r + " "))
				return true;
		}
		return false;
	}

	/**
	 * @param C a clock
	 * @return its day, counted from its year 0
	 */
	public static long dayNumber(final TimeClock C)
	{
		return ((((long)C.getYear() * C.getMonthsInYear()) + C.getMonth()) * C.getDaysInMonth()) + (C.getDayOfMonth() - 1);
	}

	/**
	 * A day on, at the same hour (the clock's own bumpDays skips the 1st as a month turns).
	 * @param C the clock
	 */
	public static void nextDay(final TimeClock C)
	{
		int d = C.getDayOfMonth() + 1, m = C.getMonth(), y = C.getYear();
		if(d > C.getDaysInMonth())
		{
			d = 1;
			m++;
			if(m >= C.getMonthsInYear())
			{
				m = 0;
				y++;
			}
		}
		C.setYear(y);
		C.setMonth(m);
		C.setDayOfMonth(d);
	}

	/**
	 * A day back, at the same hour.
	 * @param C the clock
	 */
	public static void prevDay(final TimeClock C)
	{
		int d = C.getDayOfMonth() - 1, m = C.getMonth(), y = C.getYear();
		if(d < 1)
		{
			m--;
			if(m < 0)
			{
				m = C.getMonthsInYear() - 1;
				y--;
			}
			d = C.getDaysInMonth();
		}
		C.setYear(y);
		C.setMonth(m);
		C.setDayOfMonth(d);
	}

	private static String weekday(final TimeClock C)
	{
		final String[] w = C.getWeekNames();
		if((w == null) || (w.length == 0))
			return "";
		// as TimeClock's own description counts it
		final long x = (((long)C.getYear()) * C.getMonthsInYear() * C.getDaysInMonth()) + (((long)C.getMonth()) * C.getDaysInMonth()) + C.getDayOfMonth();
		return w[(int)Math.floorMod(x, (long)w.length)];
	}

	private static boolean moonIs(final String rest, final TimeClock C)
	{
		String phase = rest.trim().toUpperCase();
		TimeClock.MoonPhase P = null;
		for(final Calendars.Moon M : Calendars.moons(C))
		{
			if(phase.startsWith(M.name.toUpperCase() + " "))
			{
				P = Calendars.phase(C, M, null);
				phase = phase.substring(M.name.length()).trim();
				break;
			}
		}
		if(P == null)
			P = C.getMoonPhase(null);
		return (phase.length() > 0) && P.name().startsWith(phase);
	}

	/**
	 * Whether a day is one a recurrence falls on.
	 * @param spec the recurrence, as SET gives it (WEEKDAY Firesday, MOON Ember FULL ...)
	 * @param C the day
	 * @return true if it falls on it
	 */
	public static boolean matches(final String spec, final TimeClock C)
	{
		final String s = spec.trim();
		final int x = s.indexOf(' ');
		final String kind = ((x < 0) ? s : s.substring(0, x)).toUpperCase();
		final String rest = (x < 0) ? "" : s.substring(x + 1).trim();
		if(kind.equals("WEEKDAY"))
			return weekday(C).equalsIgnoreCase(rest);
		if(kind.equals("EVERY"))
		{
			final int[] e = every(rest);
			if(e == null)
				return false;
			final long from = ((((long)e[1] * C.getMonthsInYear()) + (e[2] - 1)) * C.getDaysInMonth()) + (e[3] - 1);
			final long diff = dayNumber(C) - from;
			return (diff >= 0) && ((diff % e[0]) == 0);
		}
		final TimeClock P = (TimeClock)C.copyOf();
		prevDay(P);
		if(kind.equals("SEASONSTART"))
		{
			final Calendars.SeasonNow now = Calendars.season(C, 0);
			if(!(now.name.equalsIgnoreCase(rest) || now.counts.name().equalsIgnoreCase(rest)))
				return false;
			return !Calendars.season(P, 0).name.equalsIgnoreCase(now.name);
		}
		if(kind.equals("MOON"))
			return moonIs(rest, C) && (!moonIs(rest, P));
		return false;
	}

	/**
	 * EVERY's text: n (DAYS) FROM y/m/d.
	 * @param rest the text after EVERY
	 * @return n, then the year, month (from 1) and day, or null
	 */
	public static int[] every(final String rest)
	{
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\d+)\\s*(?:DAYS?)?\\s+FROM\\s+(\\S+)\\s*$", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(rest);
		if(!m.find())
			return null;
		final int[] d = Calendars.date(m.group(2));
		final int n = CMath.s_int(m.group(1));
		if((d == null) || (n < 1))
			return null;
		return new int[] { n, d[0], d[1], d[2] };
	}

	/**
	 * The next day a recurrence (or a MUDDAY m-d) falls on, at its first hour.
	 * @param spec the recurrence
	 * @param NOW the clock now
	 * @param todayOk true if today counts, being one it falls on
	 * @return the day, or null if it falls on none in three years
	 */
	public static TimeClock next(final String spec, final TimeClock NOW, final boolean todayOk)
	{
		final TimeClock C = (TimeClock)NOW.copyOf();
		C.setHourOfDay(0);
		if(!todayOk)
			nextDay(C);
		final String s = spec.trim();
		final boolean mudday = s.toUpperCase().startsWith("MUDDAY ");
		int month = -1, day = -1;
		if(mudday)
		{
			final String[] md = s.substring(7).trim().split("\\s*[-/]\\s*");
			if((md.length != 2) || (!CMath.isMathExpression(md[0])) || (!CMath.isMathExpression(md[1])))
				return null;
			month = CMath.s_parseIntExpression(md[0]);
			day = CMath.s_parseIntExpression(md[1]);
		}
		final int most = (Math.max(1, C.getDaysInMonth()) * Math.max(1, C.getMonthsInYear()) * 3) + 1;
		for(int i = 0; i < most; i++)
		{
			if(mudday ? ((C.getMonth() == month) && (C.getDayOfMonth() == day)) : matches(s, C))
				return C;
			nextDay(C);
		}
		return null;
	}

	/**
	 * @param spec a quest's start date (MUDDAY or a recurrence)
	 * @param NOW the clock now
	 * @return the game days until it next starts (0 for today), or -1
	 */
	public static int daysUntil(final String spec, final TimeClock NOW)
	{
		final TimeClock C = next(spec, NOW, true);
		return (C == null) ? -1 : (int)(dayNumber(C) - dayNumber(NOW));
	}

	/**
	 * DURATION in game days and hours: DAYS and HOURS are the game's (MUDDAYS, MUDHOURS).
	 * @param val the duration
	 * @return the duration as the tick expressions read it
	 */
	public static String gameUnits(final String val)
	{
		return java.util.regex.Pattern.compile("(?<![A-Za-z])(DAYS?|HOURS?)(?![A-Za-z])", java.util.regex.Pattern.CASE_INSENSITIVE)
			.matcher(val).replaceAll("MUD$1").toUpperCase();
	}

	private static String baseName(final String n)
	{
		final int x = n.lastIndexOf('#');
		return ((x > 0) && CMath.isInteger(n.substring(x + 1))) ? n.substring(0, x) : n;
	}

	/**
	 * A quest by name (or a copy of one, name#n), a running one first.
	 * @param name the name
	 * @return the quest, or null
	 */
	public static Quest quest(final String name)
	{
		Quest found = null;
		for(final Enumeration<Quest> e = CMLib.quests().enumQuests(); e.hasMoreElements();)
		{
			final Quest Q = e.nextElement();
			if((Q != null) && baseName(Q.name()).equalsIgnoreCase(name.trim()))
			{
				if(Q.running())
					return Q;
				found = Q;
			}
		}
		return found;
	}

	/**
	 * @param name a holiday's (a quest's) name
	 * @return true while it runs
	 */
	public static boolean on(final String name)
	{
		final Quest Q = quest(name);
		return (Q != null) && Q.running();
	}

	private static Set<String> holidayNames()
	{
		final Set<String> names = new HashSet<String>();
		try
		{
			for(final JournalEntry J : CMLib.quests().getHolidayEntries(false))
				names.add(J.subj().trim().toUpperCase());
		}
		catch(final Exception e)
		{
		}
		return names;
	}

	/**
	 * The holidays on now: running quests from the holiday file, or with a game date to start on.
	 * @return their names
	 */
	public static List<String> current()
	{
		final List<String> names = new ArrayList<String>();
		final Set<String> holidays = holidayNames();
		for(final Enumeration<Quest> e = CMLib.quests().enumQuests(); e.hasMoreElements();)
		{
			final Quest Q = e.nextElement();
			if((Q == null) || (!Q.running()))
				continue;
			final String n = baseName(Q.name());
			if((holidays.contains(n.toUpperCase()) || (Q.startDate().trim().length() > 0)) && (!names.contains(n)))
				names.add(n);
		}
		return names;
	}

	/**
	 * For VARIES: &lt;HOLIDAY name&gt;...&lt;/HOLIDAY&gt; kept while that holiday is on, taken out otherwise.
	 * @param text the text
	 * @return the text, its holiday blocks done
	 */
	public static String blocks(final String text)
	{
		if((text == null) || (text.toUpperCase().indexOf("<HOLIDAY ") < 0))
			return text;
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("<HOLIDAY\\s+([^>]+)>(.*?)</HOLIDAY>",
			java.util.regex.Pattern.CASE_INSENSITIVE | java.util.regex.Pattern.DOTALL).matcher(text);
		final StringBuffer b = new StringBuffer();
		while(m.find())
			m.appendReplacement(b, java.util.regex.Matcher.quoteReplacement(on(m.group(1).trim().replaceAll("^[\"']|[\"']$", "")) ? m.group(2) : ""));
		m.appendTail(b);
		return b.toString();
	}

	/**
	 * CALENDAR HOLIDAYS: each game-dated quest, on now or in how many game days.
	 * @param C the clock to count on
	 * @return the list
	 */
	public static String list(final TimeClock C)
	{
		final StringBuilder str = new StringBuilder();
		final Set<String> seen = new HashSet<String>();
		for(final Enumeration<Quest> e = CMLib.quests().enumQuests(); e.hasMoreElements();)
		{
			final Quest Q = e.nextElement();
			if(Q == null)
				continue;
			final String n = baseName(Q.name());
			final String when = Q.startDate().trim();
			final boolean game = isRecurrence(when) || when.toUpperCase().startsWith("MUDDAY ");
			if(((!game) && (!Q.running())) || (!seen.add(n.toUpperCase())))
				continue;
			if(Q.running() && (when.length() > 0 || holidayNames().contains(n.toUpperCase())))
				str.append(n).append(": on now\n\r");
			else
			if(game)
			{
				final int d = daysUntil(when, C);
				str.append(n).append(": ").append((d < 0) ? "never" : (d == 0) ? "today" : (d == 1) ? "tomorrow" : ("in " + d + " days")).append(" (").append(when.toLowerCase()).append(")\n\r");
			}
		}
		return (str.length() == 0) ? "No holidays have a game date.\n\r" : str.toString();
	}
}
