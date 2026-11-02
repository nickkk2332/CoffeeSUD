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
 * W20: named climates as data, world/climates/NAME.ini (else
 * resources/climates/NAME.ini), chosen by an area's WEATHERPROFILE (MODIFY
 * AREA WEATHERPROFILE name; child areas inherit it).  A climate gives a
 * weight for each weather in each season (SPRING=, SUMMER=, FALL=, WINTER=,
 * or ALL= for every season), TRANSITION= overrides of the change from one
 * weather to another, HOLD= the fewest game hours a weather lasts, ALLOW=
 * the only weathers it has, and TEXT_[WEATHER]= and STOP_[WEATHER]= lines
 * (alternatives between |, NONE for nothing) in place of lists.ini's.  The
 * area's climate bits (wet, cold, hot, dry, windy) still add to the
 * weights.
 */
public final class ClimateProfiles
{
	private ClimateProfiles()
	{
	}

	/** the area blurb flag that names an area's climate */
	public static final String	KEY	= "WEATHERPROFILE";

	/** a named climate, as read from its file */
	public static final class Profile
	{
		/** its name */
		public final String			name;
		/** each season's weights (Season order), or null where the file gives none */
		public final int[][]		seasons		= new int[TimeClock.Season.values().length][];
		/** from, to: a change's chance in place of the built-in one, or null */
		public final Integer[][]	transitions	= new Integer[Climate.NUM_WEATHER][Climate.NUM_WEATHER];
		/** the fewest game hours a weather lasts */
		public int					holdHours	= 0;
		/** the only weathers it has, or null for all */
		public boolean[]			allowed		= null;
		/** each weather's descriptions, or null */
		public final String[][]		texts		= new String[Climate.NUM_WEATHER][];
		/** each weather's ending lines, or null */
		public final String[][]		stops		= new String[Climate.NUM_WEATHER][];
		/** what in the file could not be read */
		public final List<String>	problems	= new ArrayList<String>();

		Profile(final String name)
		{
			this.name = name;
		}

		/**
		 * @param weather a weather code
		 * @return true if this climate has it
		 */
		public boolean allows(final int weather)
		{
			return (allowed == null) || ((weather >= 0) && (weather < allowed.length) && allowed[weather]);
		}
	}

	private static final Map<String, Profile>	cache	= new Hashtable<String, Profile>();
	private static final Set<String>			missing	= Collections.synchronizedSet(new HashSet<String>());

	/** drops the climates read, so their files are read again */
	public static void reload()
	{
		cache.clear();
		missing.clear();
	}

	/**
	 * The name of an area's climate: its own WEATHERPROFILE, else its
	 * nearest parent's.
	 * @param A the area
	 * @return the name, or null for the built-in climate
	 */
	public static String nameFor(final Area A)
	{
		return nameFor(A, 0);
	}

	private static String nameFor(final Area A, final int depth)
	{
		if((A == null) || (depth > 20))
			return null;
		final String own = A.getBlurbFlag(KEY);
		if((own != null) && (own.trim().length() > 0))
			return own.trim().equalsIgnoreCase("NONE") ? null : own.trim();
		for(final Enumeration<Area> p = A.getParents(); p.hasMoreElements();)
		{
			final String n = nameFor(p.nextElement(), depth + 1);
			if(n != null)
				return n;
		}
		return null;
	}

	/**
	 * @param A an area
	 * @return its climate, or null for the built-in one (or one whose file is missing)
	 */
	public static Profile of(final Area A)
	{
		final String n = nameFor(A);
		return (n == null) ? null : get(n);
	}

	/**
	 * A climate by name, read once.
	 * @param name its name
	 * @return the climate, or null if there is no such file
	 */
	public static Profile get(final String name)
	{
		final String key = name.trim().toLowerCase();
		final Profile P = cache.get(key);
		if(P != null)
			return P;
		if(missing.contains(key))
			return null;
		CMFile F = new CMFile("world/climates/" + key + ".ini", null);
		if(!F.exists())
			F = new CMFile(Resources.makeFileResourceName("climates/" + key + ".ini"), null);
		if((!F.exists()) || (!F.canRead()))
		{
			missing.add(key);
			Log.errOut("ClimateProfiles", "No climate file for WEATHERPROFILE " + name + " (world/climates/" + key + ".ini).");
			return null;
		}
		final Profile N = parse(key, F.text().toString());
		for(final String why : N.problems)
			Log.errOut("ClimateProfiles", "climates/" + key + ".ini: " + why);
		cache.put(key, N);
		return N;
	}

	private static int weather(final String word)
	{
		final String w = word.trim().toUpperCase();
		for(int i = 0; i < Climate.WEATHER_DESCS.length; i++)
		{
			if(Climate.WEATHER_DESCS[i].equals(w))
				return i;
		}
		if(w.equals("THUNDER") || w.equals("STORM"))
			return Climate.WEATHER_THUNDERSTORM;
		if(w.equals("DUSTSTORM"))
			return Climate.WEATHER_DUSTSTORM;
		if(w.equals("WINTER") || w.equals("WINTERCOLD"))
			return Climate.WEATHER_WINTER_COLD;
		return -1;
	}

	/**
	 * Reads a climate's text.
	 * @param name its name
	 * @param text the file's text
	 * @return the climate
	 */
	public static Profile parse(final String name, final String text)
	{
		final Profile P = new Profile(name);
		int[] all = null;
		for(String line : text.split("\\r?\\n"))
		{
			line = line.trim();
			final int x = line.indexOf('=');
			if((line.length() == 0) || line.startsWith("#") || (x <= 0))
				continue;
			final String key = line.substring(0, x).trim().toUpperCase();
			final String val = line.substring(x + 1).trim();
			int season = -1;
			for(final TimeClock.Season S : TimeClock.Season.values())
			{
				if(S.name().equals(key))
					season = S.ordinal();
			}
			if((season >= 0) || key.equals("ALL"))
			{
				final int[] w = new int[Climate.NUM_WEATHER];
				for(final String part : val.split(","))
				{
					final String[] nv = part.trim().split("[\\s:=]+");
					final int code = (nv.length == 2) ? weather(nv[0]) : -1;
					if((code < 0) || (!CMath.isInteger(nv[1].trim())))
					{
						if(part.trim().length() > 0)
							P.problems.add(key + ": '" + part.trim() + "' is not WEATHER N");
						continue;
					}
					w[code] = CMath.s_int(nv[1].trim());
				}
				if(season >= 0)
					P.seasons[season] = w;
				else
					all = w;
			}
			else
			if(key.equals("TRANSITION"))
			{
				for(final String part : val.split(","))
				{
					final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^\\s*(\\w+)\\s*->\\s*(\\w+)\\s*[:= ]\\s*(-?\\d+)\\s*$").matcher(part);
					if(!m.find() || (weather(m.group(1)) < 0) || (weather(m.group(2)) < 0))
					{
						if(part.trim().length() > 0)
							P.problems.add("TRANSITION: '" + part.trim() + "' is not FROM->TO=N");
						continue;
					}
					P.transitions[weather(m.group(1))][weather(m.group(2))] = Integer.valueOf(CMath.s_int(m.group(3)));
				}
			}
			else
			if(key.equals("HOLD"))
				P.holdHours = Math.max(0, CMath.s_int(val));
			else
			if(key.equals("ALLOW"))
			{
				P.allowed = new boolean[Climate.NUM_WEATHER];
				for(final String part : val.split("[,\\s]+"))
				{
					final int code = weather(part);
					if(code >= 0)
						P.allowed[code] = true;
					else
					if(part.trim().length() > 0)
						P.problems.add("ALLOW: '" + part.trim() + "' is no weather");
				}
			}
			else
			if(key.startsWith("TEXT_") || key.startsWith("STOP_"))
			{
				final int code = weather(key.substring(5));
				if(code < 0)
				{
					P.problems.add(key + ": no such weather");
					continue;
				}
				final String[] alts = val.split("\\|");
				for(int i = 0; i < alts.length; i++)
					alts[i] = alts[i].trim().equalsIgnoreCase("NONE") ? "" : alts[i].trim();
				if(key.startsWith("TEXT_"))
					P.texts[code] = alts;
				else
					P.stops[code] = alts;
			}
			else
				P.problems.add("unknown key " + key);
		}
		if(all != null)
		{
			for(int s = 0; s < P.seasons.length; s++)
			{
				if(P.seasons[s] == null)
					P.seasons[s] = all;
			}
		}
		return P;
	}

	/**
	 * One of a set of lines, by the game's dice.
	 * @param alts the lines, or null
	 * @return one, or null where there are none
	 */
	public static String pick(final String[] alts)
	{
		if((alts == null) || (alts.length == 0))
			return null;
		return alts[CMLib.dice().roll(1, alts.length, -1)];
	}
}
