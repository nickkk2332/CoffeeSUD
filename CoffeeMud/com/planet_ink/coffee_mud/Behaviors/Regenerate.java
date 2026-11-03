package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * G14: an area that re-rolls.  Regenerate builds the area again from FILE=
 * and ID= (with VARS="..."), or from its GENSOURCE (G13) when they are left
 * out, keeping its GENLOCK rooms, the rooms KEEP= names and the rooms a
 * running quest uses.  WHEN= says when, one or more of MUDDAY:n (every n
 * days of the area's calendar), MOON:FULL (or another phase, as it begins),
 * ONEMPTY (when the party has left) and SCRIPT (only MPREGENERATE).  SEED=
 * is RANDOM (a new one each time), DAILY or WEEKLY (one for the day or the
 * week, the same all that day), SAME (the GENSOURCE's) or a number.  It
 * waits while a player is inside, and runs GENERATED_PROG after.
 */
public class Regenerate extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Regenerate";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "re-rolling itself";
	}

	protected String			file		= "";
	protected String			id			= "";
	protected Map<String,String>vars		= new LinkedHashMap<String,String>();
	protected String			seedRule	= "RANDOM";
	protected int				everyDays	= 0;
	protected String			moonPhase	= null;
	protected boolean			onEmpty		= false;
	protected final Set<String>	keep		= new HashSet<String>();

	/** the day the days are counted from, or -1 until the first tick */
	protected long				sinceDay	= -1;
	/** the moon's phase at the last tick */
	protected String			lastMoon	= null;
	/** a player was inside at the last tick */
	protected boolean			wasOccupied	= false;
	/** due, waiting for the party to leave */
	protected boolean			pending		= false;
	/** the seed MPREGENERATE asked for, or null */
	protected String			pendingSeed	= null;
	/** what the last re-roll said */
	protected String			lastReport	= "";

	@Override
	public void setParms(final String parameters)
	{
		super.setParms(parameters);
		file = CMParms.getParmStr(parameters, "FILE", "").trim();
		id = CMParms.getParmStr(parameters, "ID", "").trim();
		vars = new LinkedHashMap<String,String>();
		for(final Map.Entry<String,String> e : CMParms.parseEQParms(CMParms.getParmStr(parameters, "VARS", "")).entrySet())
			vars.put(e.getKey().toLowerCase(), e.getValue());
		seedRule = CMParms.getParmStr(parameters, "SEED", "RANDOM").trim().toUpperCase();
		everyDays = 0;
		moonPhase = null;
		onEmpty = false;
		for(final String w : CMParms.parseCommas(CMParms.getParmStr(parameters, "WHEN", "SCRIPT").toUpperCase(), true))
		{
			if(w.startsWith("MUDDAY"))
				everyDays = Math.max(1, CMath.s_int(w.substring(w.indexOf(':') + 1).trim()));
			else
			if(w.startsWith("MOON"))
				moonPhase = (w.indexOf(':') > 0) ? w.substring(w.indexOf(':') + 1).trim() : "FULL";
			else
			if(w.equals("ONEMPTY"))
				onEmpty = true;
		}
		keep.clear();
		for(final String k : CMParms.getParmStr(parameters, "KEEP", "").split("[,\\s]+"))
		{
			if((k.trim().length() > 0) && (!k.trim().equalsIgnoreCase("GENLOCK")))
				keep.add(k.trim().toUpperCase());
		}
	}

	protected GenSource.Source source(final Area A)
	{
		if(file.length() == 0)
			return GenSource.get(A);
		final GenSource.Source old = GenSource.get(A);
		final StringBuilder enc = new StringBuilder(GenSource.encode(file, (id.length() > 0) ? id : ((old != null) ? old.id : ""), (old != null) ? old.seed : 0, vars));
		return GenSource.decode(enc.toString());
	}

	protected static long dayOf(final Area A)
	{
		final TimeClock C = A.getTimeObj();
		return (C == null) ? 0 : Holidays.dayNumber(C);
	}

	protected String moonOf(final Area A)
	{
		final TimeClock C = A.getTimeObj();
		if(C == null)
			return null;
		Room R = A.getRoom(A.Name() + "#0");
		if(R == null)
		{
			final Enumeration<Room> r = A.getProperMap();
			R = r.hasMoreElements() ? r.nextElement() : null;
		}
		final TimeClock.MoonPhase P = C.getMoonPhase(R);
		return (P == null) ? null : P.name();
	}

	/**
	 * @param A the area
	 * @param asked the seed MPREGENERATE asked for, or null
	 * @return the seed word for this re-roll: null for a new one, SAME, or a number
	 */
	protected String seedWord(final Area A, final String asked)
	{
		if((asked != null) && (asked.trim().length() > 0))
			return asked.trim();
		final long day = dayOf(A);
		final long name = A.Name().toUpperCase().hashCode();
		if(seedRule.equals("DAILY"))
			return "" + Math.abs((name * 31L) + day);
		if(seedRule.equals("WEEKLY"))
		{
			final TimeClock C = A.getTimeObj();
			final int w = ((C == null) || (C.getDaysInWeek() <= 0)) ? 7 : C.getDaysInWeek();
			return "" + Math.abs((name * 31L) + (day / w) + 7919L);
		}
		if(seedRule.equals("SAME") || CMath.isLong(seedRule))
			return seedRule;
		return null;
	}

	/**
	 * Re-rolls the area now, as MPREGENERATE asks, or once the party has left.
	 * @param A the area
	 * @param seed the seed asked for, or null for the behaviour's own
	 * @return what was done
	 */
	public String trigger(final Area A, final String seed)
	{
		if(GenSource.occupied(A))
		{
			pending = true;
			pendingSeed = seed;
			return "'" + A.Name() + "' re-rolls once the party has left.";
		}
		return regenerateNow(A, seed);
	}

	protected String regenerateNow(final Area A, final String seed)
	{
		pending = false;
		pendingSeed = null;
		sinceDay = dayOf(A);
		lastReport = GenSource.regenerate(null, A, source(A), seedWord(A, seed), null, false, keep);
		return lastReport;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		super.tick(ticking, tickID);
		if(!(ticking instanceof Area))
			return true;
		final Area A = (Area)ticking;
		final long day = dayOf(A);
		if(sinceDay < 0)
			sinceDay = day;
		if((everyDays > 0) && ((day - sinceDay) >= everyDays))
			pending = true;
		if(moonPhase != null)
		{
			final String now = moonOf(A);
			if((now != null) && now.equalsIgnoreCase(moonPhase) && (lastMoon != null) && (!lastMoon.equalsIgnoreCase(now)))
				pending = true;
			lastMoon = now;
		}
		if(onEmpty || pending)
		{
			final boolean occupied = GenSource.occupied(A);
			if(onEmpty && wasOccupied && !occupied)
				pending = true;
			wasOccupied = occupied;
			if(pending && !occupied)
				regenerateNow(A, pendingSeed);
		}
		return true;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("LASTREPORT"))
			return lastReport;
		if(code.equalsIgnoreCase("PENDING"))
			return "" + pending;
		return super.getStat(code);
	}

	/** G14: when the days are counted from, the moon last seen, the party inside, and a re-roll waiting, for a save */
	@Override
	protected String getRunState()
	{
		if((sinceDay < 0) && (lastMoon == null) && (!wasOccupied) && (!pending))
			return "";
		return "<REGENERATE" + ((sinceDay < 0) ? "" : (" SINCE=\"" + sinceDay + "\""))
			+ ((lastMoon == null) ? "" : (" MOON=\"" + lastMoon + "\""))
			+ (wasOccupied ? " OCC=\"TRUE\"" : "")
			+ (pending ? " PENDING=\"TRUE\"" : "")
			+ ((pendingSeed == null) ? "" : (" SEED=\"" + CMLib.xml().parseOutAngleBrackets(pendingSeed) + "\""))
			+ " />";
	}

	@Override
	protected void setRunState(final String xml)
	{
		for(final XMLLibrary.XMLTag tag : CMLib.xml().parseAllXML(xml))
		{
			if(tag.tag().equalsIgnoreCase("REGENERATE"))
			{
				final String since = tag.getParmValue("SINCE");
				sinceDay = ((since == null) || (since.length() == 0)) ? -1 : CMath.s_long(since);
				lastMoon = tag.getParmValue("MOON");
				wasOccupied = "TRUE".equalsIgnoreCase(tag.getParmValue("OCC"));
				pending = "TRUE".equalsIgnoreCase(tag.getParmValue("PENDING"));
				pendingSeed = tag.getParmValue("SEED");
			}
		}
	}
}
