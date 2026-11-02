package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
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
 * W13: the lines of Prop_SkyText for a room or an area: the room's own,
 * then its area's, then the area's parents'.
 */
public final class SkyText
{
	private SkyText()
	{
	}

	private static KeyedLines of(final Physical P)
	{
		if(P == null)
			return null;
		final Ability A = P.fetchEffect("Prop_SkyText");
		return (A instanceof KeyedLines) ? (KeyedLines)A : null; // its class is loaded apart: asked through the interface
	}

	private static String fromArea(final Area A, final String key, final int depth)
	{
		if((A == null) || (depth > 20))
			return null;
		final KeyedLines P = of(A);
		if((P != null) && P.has(key))
			return P.line(key);
		for(final Enumeration<Area> p = A.getParents(); p.hasMoreElements();)
		{
			final String s = fromArea(p.nextElement(), key, depth + 1);
			if(s != null)
				return s;
		}
		return null;
	}

	/**
	 * A line of the room's, else its area's (and parents').
	 * @param R the room, or null
	 * @param A the area, when there is no room
	 * @param key the key
	 * @return the line ("" for NONE), or null when none sets it
	 */
	public static String line(final Room R, final Area A, final String key)
	{
		final KeyedLines P = of(R);
		if((P != null) && P.has(key))
			return P.line(key);
		return fromArea((R != null) ? R.getArea() : A, key, 0);
	}

	/**
	 * A line of the room's own Prop_SkyText only.
	 * @param R the room
	 * @param key the key
	 * @return the line, or null
	 */
	public static String roomOnly(final Room R, final String key)
	{
		final KeyedLines P = of(R);
		return ((P != null) && P.has(key)) ? P.line(key) : null;
	}

	/**
	 * The time-of-day line for a room.
	 * @param R the room
	 * @param outside true under the sky
	 * @param tod the time of day
	 * @return the line, or null for lists.ini's
	 */
	public static String tod(final Room R, final boolean outside, final TimeClock.TimeOfDay tod)
	{
		return line(R, null, (outside ? "" : "IN") + tod.name());
	}

	/**
	 * @param weather a weather code
	 * @return its key name, as WEATHER lists it
	 */
	public static String weatherKey(final int weather)
	{
		return ((weather >= 0) && (weather < Climate.NUM_WEATHER)) ? Climate.WEATHER_DESCS[weather] : "?";
	}
}
