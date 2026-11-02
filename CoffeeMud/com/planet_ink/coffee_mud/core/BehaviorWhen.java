package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Common.interfaces.TimeClock;
import com.planet_ink.coffee_mud.core.interfaces.*;

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
 * N1: a condition on any behaviour, in its parameters: WHEN="zapper mask",
 * tested on the host (mob, item, room, area or exit), TOD=DAWN|DAY|DUSK|NIGHT
 * (several with commas), and HOURS=a-b (on the host's clock, round midnight
 * if a is past b).  While it does not hold, the behaviour neither ticks nor
 * hears messages.  The hosts ask {@link #active(Behavior, Object)} before
 * they tick a behaviour or pass it a message.  Scripts (Scriptable) are not
 * gated: their parameters are the script.
 */
public final class BehaviorWhen
{
	private BehaviorWhen()
	{
	}

	/** a behaviour's condition, read from its parameters */
	public static final class Gate
	{
		/** the WHEN= mask, or null */
		public String					mask	= null;
		/** the TOD= times of day, or null */
		public Set<TimeClock.TimeOfDay>	tod		= null;
		/** HOURS=a-b, or -1 */
		public int						from	= -1;
		/** HOURS=a-b, or -1 */
		public int						to		= -1;

		/** @return true if it says anything */
		public boolean any()
		{
			return (mask != null) || (tod != null) || (from >= 0);
		}
	}

	/** the keys any behaviour may have for its condition */
	public static final Set<String>			KEYS	= Collections.unmodifiableSet(new HashSet<String>(Arrays.asList("WHEN", "TOD", "HOURS")));

	private static final Gate				NONE	= new Gate();
	private static final Map<String, Gate>	gates	= new ConcurrentHashMap<String, Gate>();

	/**
	 * @param parms a behaviour's parameters
	 * @return its condition, NONE-like (any() false) if it has none
	 */
	public static Gate gateOf(final String parms)
	{
		if((parms == null) || (parms.length() == 0))
			return NONE;
		final Gate known = gates.get(parms);
		if(known != null)
			return known;
		final String up = parms.toUpperCase();
		Gate G = NONE;
		if((up.indexOf("WHEN=") >= 0) || (up.indexOf("TOD=") >= 0) || (up.indexOf("HOURS=") >= 0))
		{
			G = new Gate();
			final Map<String, String> P = CMParms.parseEQParms(parms);
			for(final String k : KEYS) // "min=1 TOD=NIGHT;howls." (Emoter and the like): the value ends at the ';'
			{
				final String v = P.get(k);
				if((v != null) && (v.indexOf(';') >= 0))
					P.put(k, v.substring(0, v.indexOf(';')));
			}
			final String when = P.get("WHEN");
			if((when != null) && (when.trim().length() > 0))
				G.mask = when.trim();
			final String tod = P.get("TOD");
			if((tod != null) && (tod.trim().length() > 0))
			{
				G.tod = EnumSet.noneOf(TimeClock.TimeOfDay.class);
				for(final String t : CMParms.parseCommas(tod.toUpperCase(), true))
				{
					final TimeClock.TimeOfDay T = (TimeClock.TimeOfDay)CMath.s_valueOf(TimeClock.TimeOfDay.class, t.trim());
					if(T != null)
						G.tod.add(T);
					else
						Log.errOut("BehaviorWhen", "TOD=" + tod + ": " + t + " is not DAWN, DAY, DUSK or NIGHT");
				}
			}
			final String hours = P.get("HOURS");
			if((hours != null) && (hours.indexOf('-') > 0))
			{
				final int x = hours.indexOf('-');
				if(CMath.isInteger(hours.substring(0, x).trim()) && CMath.isInteger(hours.substring(x + 1).trim()))
				{
					G.from = CMath.s_int(hours.substring(0, x).trim());
					G.to = CMath.s_int(hours.substring(x + 1).trim());
				}
			}
			if(!G.any())
				G = NONE;
		}
		if(gates.size() > 10000)
			gates.clear();
		gates.put(parms, G);
		return G;
	}

	/**
	 * @param parms a behaviour's parameters
	 * @param host its host
	 * @return true if its condition holds now (or it has none)
	 */
	public static boolean holds(final String parms, final Object host)
	{
		final Gate G = gateOf(parms);
		if(!G.any())
			return true;
		final Physical P = (host instanceof Physical) ? (Physical)host : null;
		if((G.tod != null) || (G.from >= 0))
		{
			final TimeClock C = (P != null) ? CMLib.time().localClock(P) : CMLib.time().globalClock();
			if(C != null)
			{
				if((G.tod != null) && (!G.tod.contains(C.getTODCode())))
					return false;
				if(G.from >= 0)
				{
					final int h = C.getHourOfDay();
					final boolean in = (G.from <= G.to) ? ((h >= G.from) && (h <= G.to)) : ((h >= G.from) || (h <= G.to));
					if(!in)
						return false;
				}
			}
		}
		if((G.mask != null) && (P instanceof Environmental))
		{
			if(!CMLib.masking().maskCheck(G.mask, P, true))
				return false;
		}
		return true;
	}

	/**
	 * What a host asks before it ticks a behaviour or passes it a message.
	 * @param B the behaviour
	 * @param host its host
	 * @return true to let it
	 */
	public static boolean active(final Behavior B, final Object host)
	{
		if((B == null) || (B instanceof ScriptingEngine))
			return true;
		return holds(B.getParms(), host);
	}

	/**
	 * For STAT BEHAVIORS: whether the condition holds now.
	 * @param B the behaviour
	 * @param host its host
	 * @return "" if it has none, else "; WHEN ...: on now" or "off now"
	 */
	public static String state(final Behavior B, final Object host)
	{
		if((B == null) || (B instanceof ScriptingEngine) || (!gateOf(B.getParms()).any()))
			return "";
		return "; its condition (WHEN, TOD or HOURS) is " + (holds(B.getParms(), host) ? "met: it is on now" : "not met: it is off now");
	}
}
