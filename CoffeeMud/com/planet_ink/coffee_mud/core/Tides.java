package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;

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
 * W8: the tide for builders, as each area's clock works it out: low in the
 * hour the day starts, high in the dusk hour, its strength from the moon
 * (SPRING at new and full moon, NEAP at the quarters), no tide between.  A
 * span widens a tide to the hours either side of it.
 */
public final class Tides
{
	private Tides()
	{
	}

	/**
	 * The tide in a room now.
	 * @param R the room
	 * @return the phase, NO_TIDE where there is no clock
	 */
	public static TimeClock.TidePhase phase(final Room R)
	{
		final Area A = (R == null) ? null : R.getArea();
		final TimeClock C = (A == null) ? null : A.getTimeObj();
		return (C == null) ? TimeClock.TidePhase.NO_TIDE : C.getTidePhase(R);
	}

	private static int distance(final int a, final int b, final int hours)
	{
		final int d = Math.abs(a - b) % Math.max(1, hours);
		return Math.min(d, Math.max(1, hours) - d);
	}

	/**
	 * The tide in a room, with a span: within that many hours of the high or
	 * low tide's hour it counts as that tide.
	 * @param R the room
	 * @param span the hours either side, 0 for the clock's own
	 * @return the phase
	 */
	public static TimeClock.TidePhase phase(final Room R, final int span)
	{
		if(span <= 0)
			return phase(R);
		final Area A = (R == null) ? null : R.getArea();
		final TimeClock C = (A == null) ? null : A.getTimeObj();
		if(C == null)
			return TimeClock.TidePhase.NO_TIDE;
		final int[] dtd = C.getDawnToDusk();
		final int h = C.getHourOfDay();
		final int hours = C.getHoursInDay();
		final TimeClock.MoonPhase moon = C.getMoonPhase(R);
		if((dtd.length > 2) && (distance(h, dtd[2], hours) <= span))
			return moon.getHighTide();
		if((dtd.length > 1) && (distance(h, dtd[1], hours) <= span))
			return moon.getLowTide();
		return TimeClock.TidePhase.NO_TIDE;
	}

	/**
	 * Whether a phase is the one a word names: HIGH, LOW, NONE, SPRING, NEAP,
	 * NORMAL, or a phase's own name (SPRING_HIGH).
	 * @param p the phase
	 * @param which the word
	 * @return true if it is
	 */
	public static boolean matches(final TimeClock.TidePhase p, final String which)
	{
		final String w = (which == null) ? "" : which.trim().toUpperCase();
		if(w.equals(p.name()))
			return true;
		if(w.equals("HIGH"))
			return p.name().endsWith("_HIGH");
		if(w.equals("LOW"))
			return p.name().endsWith("_LOW");
		if(w.equals("NONE") || w.equals("NO_TIDE"))
			return p == TimeClock.TidePhase.NO_TIDE;
		return (w.length() > 0) && p.name().startsWith(w + "_");
	}

	/**
	 * @param R a room
	 * @param which HIGH, LOW..., or WHICH:SPAN
	 * @return true if the tide there is that one now
	 */
	public static boolean is(final Room R, final String which)
	{
		String w = (which == null) ? "" : which.trim();
		int span = 0;
		final int x = w.indexOf(':');
		if(x > 0)
		{
			span = CMath.s_int(w.substring(x + 1).trim());
			w = w.substring(0, x).trim();
		}
		return matches(phase(R, span), w);
	}
}
