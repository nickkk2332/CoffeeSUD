package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.TimeClock;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;

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
 * CR14: how much a room has been gathered from, and when it regrows, on game
 * time, kept in the save (YIELDS).  Each harvest takes a share off the next
 * (the skill's own, 20% for most); a room regrows 30 game minutes after its
 * first harvest.  Prop_Yield on the room or its area changes that:
 * HARVESTS= (how many before it is bare), DROP= (the share, as 20%), REGROW=
 * (how long after the last harvest, as 2 DAYS; a bare number is game hours)
 * and EMPTYMSG= (shown when someone tries a bare room).
 */
public final class Yields
{
	private Yields()
	{
	}

	/** how long a room takes to regrow when no Prop_Yield says, in game time */
	public static final long REGROW = 30L * 60L * 1000L;

	private static final class Record
	{
		private int		resource;
		private int		count;
		private long	until;
	}

	/** a Prop_Yield's rules */
	public static final class Rules
	{
		public int		harvests	= 0;
		public double	drop		= -1;
		public long		regrow		= -1;
		public String	emptyMsg	= "";
	}

	private static final Map<Room, Record>	records	= new HashMap<Room, Record>();

	private static final Pattern REGROW_PAT = Pattern.compile("(?i)\\bREGROW\\s*=\\s*\"?\\s*(\\d+)\\s*(HOURS?|DAYS?|MONTHS?|YEARS?)?");

	/**
	 * @param R a room
	 * @param n how many
	 * @param unit HOURS, DAYS, MONTHS or YEARS of the room's calendar
	 * @return that long in game time
	 */
	public static long gameTime(final Room R, final int n, final String unit)
	{
		final TimeClock C = ((R != null) && (R.getArea() != null)) ? R.getArea().getTimeObj() : CMLib.time().globalClock();
		long hours = n;
		final String u = (unit == null) ? "" : unit.toUpperCase();
		if(u.startsWith("DAY"))
			hours = (long)n * C.getHoursInDay();
		else
		if(u.startsWith("MONTH"))
			hours = (long)n * C.getHoursInDay() * C.getDaysInMonth();
		else
		if(u.startsWith("YEAR"))
			hours = (long)n * C.getHoursInDay() * C.getDaysInMonth() * C.getMonthsInYear();
		return hours * CMProps.getMillisPerMudHour();
	}

	/**
	 * @param text a Prop_Yield's text
	 * @param R the room it is for
	 * @return its rules
	 */
	public static Rules parse(final String text, final Room R)
	{
		final Rules ru = new Rules();
		ru.harvests = Math.max(0, CMParms.getParmInt(text, "HARVESTS", 0));
		final String drop = CMParms.getParmStr(text, "DROP", "").trim();
		if(drop.length() > 0)
			ru.drop = drop.endsWith("%") ? (CMath.s_double(drop.substring(0, drop.length() - 1)) / 100.0)
				: ((CMath.s_double(drop) > 1.0) ? (CMath.s_double(drop) / 100.0) : CMath.s_double(drop));
		final Matcher m = REGROW_PAT.matcher(text);
		if(m.find())
			ru.regrow = gameTime(R, CMath.s_int(m.group(1)), m.group(2));
		ru.emptyMsg = CMParms.getParmStr(text, "EMPTYMSG", "").trim();
		return ru;
	}

	private static Ability prop(final com.planet_ink.coffee_mud.core.interfaces.Physical P)
	{
		return (P == null) ? null : P.fetchEffect("Prop_Yield");
	}

	/**
	 * @param R a room
	 * @return the rules of the Prop_Yield on it or its area (or a parent), or null
	 */
	public static Rules rules(final Room R)
	{
		if(R == null)
			return null;
		Ability A = prop(R);
		if((A == null) && (R.getArea() != null))
		{
			final LinkedList<Area> todo = new LinkedList<Area>();
			final Set<Area> seen = new HashSet<Area>();
			todo.add(R.getArea());
			while((A == null) && (todo.size() > 0))
			{
				final Area a = todo.removeFirst();
				if(!seen.add(a))
					continue;
				A = prop(a);
				for(final Enumeration<Area> p = a.getParents(); p.hasMoreElements();)
					todo.add(p.nextElement());
			}
		}
		return (A == null) ? null : parse(A.text(), R);
	}

	private static Record current(final Room R)
	{
		final Record r = records.get(R);
		if((r != null) && ((GameTime.now() > r.until) || (r.resource != R.myResource())))
		{
			records.remove(R);
			return null;
		}
		return r;
	}

	/**
	 * A harvest: what the room gives this time, less the share taken off by
	 * those before.
	 * @param R the room
	 * @param initialYield the yield before
	 * @param defaultDrop the skill's own share
	 * @return the yield
	 */
	public static synchronized int harvest(final Room R, final int initialYield, final double defaultDrop)
	{
		if((R == null) || (initialYield == 0))
			return 0;
		final Rules ru = rules(R);
		final long now = GameTime.now();
		Record r = current(R);
		if(r == null)
		{
			r = new Record();
			r.resource = R.myResource();
			r.count = 1;
			r.until = now + (((ru != null) && (ru.regrow >= 0)) ? ru.regrow : REGROW);
			records.put(R, r);
			return initialYield;
		}
		if((ru != null) && (ru.harvests > 0) && (r.count >= ru.harvests))
			return 0;
		final double drop = ((ru != null) && (ru.drop >= 0)) ? ru.drop : defaultDrop;
		final double pctDrop = drop * r.count;
		r.count++;
		if((ru != null) && (ru.regrow >= 0))
			r.until = now + ru.regrow; // it regrows after the last harvest
		final int finalYield = (int)Math.round(initialYield - (initialYield * pctDrop));
		return (finalYield <= 0) ? 0 : finalYield;
	}

	/**
	 * @param R a room
	 * @param defaultDrop the skill's own share
	 * @return whether anything is left to gather; a bare room with EMPTYMSG shows it
	 */
	public static synchronized boolean anyLeft(final Room R, final double defaultDrop)
	{
		if(R == null)
			return false;
		final Record r = current(R);
		if(r == null)
			return true;
		final Rules ru = rules(R);
		if((ru != null) && (ru.harvests > 0) && (r.count >= ru.harvests))
		{
			if(ru.emptyMsg.length() > 0)
				R.showHappens(CMMsg.MSG_OK_VISUAL, ru.emptyMsg);
			return false;
		}
		final double drop = ((ru != null) && (ru.drop >= 0)) ? ru.drop : defaultDrop;
		return Math.round(10 - (10 * drop * r.count)) >= 1;
	}

	/**
	 * @param R a room
	 * @param defaultDrop the skill's own share
	 * @return the percent of its yield left, 100 when it is fresh
	 */
	public static synchronized int pctLeft(final Room R, final double defaultDrop)
	{
		if(R == null)
			return 0;
		final Record r = current(R);
		if(r == null)
			return 100;
		final Rules ru = rules(R);
		if((ru != null) && (ru.harvests > 0) && (r.count >= ru.harvests))
			return 0;
		final double drop = ((ru != null) && (ru.drop >= 0)) ? ru.drop : defaultDrop;
		return (int)Math.max(0, Math.round(100.0 - (100.0 * drop * r.count)));
	}

	/**
	 * @param R a room
	 * @return how many times it has been gathered from since it last regrew
	 */
	public static synchronized int harvests(final Room R)
	{
		final Record r = (R == null) ? null : current(R);
		return (r == null) ? 0 : r.count;
	}

	/**
	 * @return the YIELDS tag of the save, or ""
	 */
	public static synchronized String toXML()
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder("");
		final long now = GameTime.now();
		for(final Map.Entry<Room, Record> e : records.entrySet())
		{
			final Record r = e.getValue();
			final String id = CMLib.map().getExtendedRoomID(e.getKey());
			if((now > r.until) || (id == null) || (id.length() == 0))
				continue;
			str.append("<Y R=\"").append(xml.parseOutAngleBracketsAndQuotes(id))
			   .append("\" RSC=\"").append(r.resource)
			   .append("\" N=\"").append(r.count)
			   .append("\" LEFT=\"").append(r.until - now)
			   .append("\" />");
		}
		return (str.length() == 0) ? "" : ("<YIELDS>" + str.toString() + "</YIELDS>\n");
	}

	/**
	 * Puts back the records the save kept.
	 * @param tag the YIELDS tag, or null
	 */
	public static synchronized void fromXML(final XMLTag tag)
	{
		records.clear();
		if(tag == null)
			return;
		final XMLLibrary xml = CMLib.xml();
		final long now = GameTime.now();
		for(final XMLTag y : tag.contents())
		{
			if(!y.tag().equalsIgnoreCase("Y"))
				continue;
			final Room R = CMLib.map().getRoom(xml.restoreAngleBrackets(y.getParmValue("R")));
			if(R == null)
				continue;
			final Record r = new Record();
			r.resource = CMath.s_int(y.getParmValue("RSC"));
			r.count = CMath.s_int(y.getParmValue("N"));
			r.until = now + CMath.s_long(y.getParmValue("LEFT"));
			records.put(R, r);
		}
	}
}
