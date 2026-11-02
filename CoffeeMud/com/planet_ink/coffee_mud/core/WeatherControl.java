package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Commands.EnvToolsCmd;
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
 * W12: weather changed on purpose (MPWEATHER, WEATHER SET, WEATHER NEXT,
 * cm.weather()): now with the usual messages, or quietly, or only what comes
 * next, and held for a while; and the forecast, with the odds of the step
 * after next.
 */
public final class WeatherControl
{
	private WeatherControl()
	{
	}

	/**
	 * @param word a weather, as WEATHER lists them (CLEAR, RAIN, HEAT...)
	 * @return its code, or -1
	 */
	public static int code(final String word)
	{
		return (word == null) ? -1 : EnvToolsCmd.weatherCode(word);
	}

	/**
	 * @param code a weather code
	 * @return its name, in lower case
	 */
	public static String name(final int code)
	{
		return ((code >= 0) && (code < Climate.NUM_WEATHER)) ? Climate.WEATHER_DESCS[code].toLowerCase() : "?";
	}

	/**
	 * Changes an area's weather.  A hold an earlier change made ends first.
	 * @param A the area
	 * @param code the weather
	 * @param next true to set only what comes next
	 * @param holdMillis how long, in game time, to hold it there, or 0
	 * @param quiet true for no end and start messages (WEATHER_PROGs still run)
	 */
	public static void change(final Area A, final int code, final boolean next, final long holdMillis, final boolean quiet)
	{
		if((A == null) || (code < 0) || (code >= Climate.NUM_WEATHER))
			return;
		final Climate C = A.getClimateObj();
		C.holdWeather(-1, 0);
		if(next)
			C.setNextWeatherType(code);
		else
		if(quiet)
		{
			final int old = C.weatherType(null);
			C.setCurrentWeatherType(code);
			if(old != code)
				ScriptEvents.weatherChanged(A, old, code, C.nextWeatherType(null));
		}
		else
		{
			C.setNextWeatherType(code);
			C.forceWeatherTick(A);
		}
		if(holdMillis > 0)
		{
			C.holdWeather(code, GameTime.now() + holdMillis);
			C.setNextWeatherType(code);
		}
	}

	/**
	 * One step of an area's weather now (WEATHER NEXT): what was coming comes.
	 * @param A the area
	 */
	public static void step(final Area A)
	{
		if(A != null)
			A.getClimateObj().forceWeatherTick(A);
	}

	/**
	 * The odds of each weather the step after next, from what comes next.
	 * @param A the area
	 * @return weather names to percents, most likely first; empty if the climate does not say
	 */
	public static Map<String, Integer> chances(final Area A)
	{
		final Map<String, Integer> out = new LinkedHashMap<String, Integer>();
		if(A == null)
			return out;
		final Climate C = A.getClimateObj();
		final int[] chart = C.weatherChances(A, C.nextWeatherType(null));
		if(chart == null)
			return out;
		int total = 0;
		for(final int c : chart)
			total += Math.max(0, c);
		if(total <= 0)
			return out;
		final List<Integer> order = new ArrayList<Integer>();
		for(int i = 0; i < chart.length; i++)
		{
			if(chart[i] > 0)
				order.add(Integer.valueOf(i));
		}
		Collections.sort(order, new Comparator<Integer>()
		{
			@Override
			public int compare(final Integer a, final Integer b)
			{
				return Integer.compare(chart[b.intValue()], chart[a.intValue()]);
			}
		});
		for(final Integer i : order)
			out.put(name(i.intValue()), Integer.valueOf((int)Math.round(100.0 * chart[i.intValue()] / total)));
		return out;
	}

	/**
	 * WEATHER FORECAST: the weather now, what comes next, the odds of the
	 * step after, and a hold if there is one.
	 * @param A the area
	 * @return the lines
	 */
	public static String forecast(final Area A)
	{
		final Climate C = A.getClimateObj();
		final StringBuilder str = new StringBuilder("Weather in " + A.Name() + ": " + name(C.weatherType(null))
			+ ", then " + name(C.nextWeatherType(null)) + ".");
		if((C.heldWeather() >= 0) && (C.heldUntil() > GameTime.now()))
		{
			final long hours = (C.heldUntil() - GameTime.now()) / Math.max(1, CMProps.getMillisPerMudHour());
			str.append("\n\rHeld at ").append(name(C.heldWeather())).append(" for ").append(hours).append(" more game hours.");
		}
		final Map<String, Integer> odds = chances(A);
		if(odds.size() > 0)
		{
			str.append("\n\rAfter that: ");
			boolean first = true;
			for(final Map.Entry<String, Integer> e : odds.entrySet())
			{
				str.append(first ? "" : ", ").append(e.getKey()).append(" ").append(e.getValue()).append("%");
				first = false;
			}
			str.append(".");
		}
		return str.toString();
	}
}
