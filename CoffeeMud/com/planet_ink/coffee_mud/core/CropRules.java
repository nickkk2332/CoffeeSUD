package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.Climate;
import com.planet_ink.coffee_mud.Common.interfaces.TimeClock;
import com.planet_ink.coffee_mud.Items.interfaces.RawMaterial;
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
 * CR16: crop rules as data, for Farming and Gardening: GROWHOURS= (game
 * hours to grow), SEASONS= (when it may be planted), NEEDSUN=true|false,
 * DOMAINS= (the kinds of room, as PLAINS,CAVE), WATER=n (that many water
 * rooms, here or next door, as Irrigation makes), YIELD= (dice, as 2d6+1)
 * and FAILMSG=.  They come from a world's crops.txt (a line each: the
 * resource or subtype, then the rules), then a Prop_CropRules on the area,
 * the room and the seed, each over the one before.  A crop with none grows
 * as before.
 */
public final class CropRules
{
	private CropRules()
	{
	}

	/** a crop's rules */
	public static final class Rules
	{
		public int						growHours	= -1;
		public Set<TimeClock.Season>	seasons		= EnumSet.noneOf(TimeClock.Season.class);
		public Boolean					needSun		= null;
		public Set<String>				domains		= null;
		public int						water		= -1;
		public String					yield		= "";
		public String					failMsg		= "";

		private static final String NONE = "\u0000";

		/**
		 * Puts the rules a text gives over these.
		 * @param text rules, as GROWHOURS=240 SEASONS=AUTUMN
		 */
		public void merge(final String text)
		{
			if((text == null) || (text.trim().length() == 0))
				return;
			String s = CMParms.getParmStr(text, "GROWHOURS", NONE);
			if(!s.equals(NONE))
				growHours = CMath.s_int(s.trim());
			s = CMParms.getParmStr(text, "SEASONS", CMParms.getParmStr(text, "SEASON", NONE));
			if(!s.equals(NONE))
			{
				seasons = EnumSet.noneOf(TimeClock.Season.class);
				for(final String x : CMParms.parseCommas(s, true))
				{
					final String u = x.trim().toUpperCase();
					final TimeClock.Season S = u.equals("AUTUMN") ? TimeClock.Season.FALL : (TimeClock.Season)CMath.s_valueOf(TimeClock.Season.class, u);
					if(S != null)
						seasons.add(S);
				}
			}
			s = CMParms.getParmStr(text, "NEEDSUN", NONE);
			if(!s.equals(NONE))
				needSun = Boolean.valueOf(!(s.trim().equalsIgnoreCase("false") || s.trim().equals("0") || s.trim().equalsIgnoreCase("no")));
			s = CMParms.getParmStr(text, "DOMAINS", CMParms.getParmStr(text, "DOMAIN", NONE));
			if(!s.equals(NONE))
			{
				domains = new TreeSet<String>();
				for(final String x : CMParms.parseCommas(s, true))
					domains.add(x.trim().toUpperCase());
			}
			s = CMParms.getParmStr(text, "WATER", NONE);
			if(!s.equals(NONE))
				water = CMath.s_int(s.trim());
			s = CMParms.getParmStr(text, "YIELD", NONE);
			if(!s.equals(NONE))
				yield = s.trim();
			s = CMParms.getParmStr(text, "FAILMSG", NONE);
			if(!s.equals(NONE))
				failMsg = s.trim();
		}
	}

	private static Map<String, String>	rows		= new HashMap<String, String>();
	private static String				loadedKey	= null;

	/** @return where a world's crops.txt is */
	public static File file()
	{
		return new File(com.planet_ink.coffee_mud.core.save.WorldFiles.worldDir(), "crops.txt");
	}

	/** @return the world's crops.txt rules, by resource or subtype, read again when it changes */
	public static synchronized Map<String, String> rows()
	{
		final File F = file();
		final String key = F.isFile() ? (F.getAbsolutePath() + "|" + F.lastModified() + "|" + F.length()) : "";
		if(key.equals(loadedKey))
			return rows;
		loadedKey = key;
		final Map<String, String> M = new HashMap<String, String>();
		if(F.isFile())
		{
			try
			{
				for(final String line : java.nio.file.Files.readAllLines(F.toPath(), java.nio.charset.StandardCharsets.UTF_8))
				{
					final String t = line.trim();
					if((t.length() == 0) || t.startsWith("#"))
						continue;
					int x = 0;
					while((x < t.length()) && (!Character.isWhitespace(t.charAt(x))))
						x++;
					M.put(t.substring(0, x).toUpperCase(), t.substring(x).trim());
				}
			}
			catch(final Exception e)
			{
				Log.errOut("CropRules", F.getPath() + ": " + e.getMessage());
			}
		}
		rows = M;
		return rows;
	}

	/** @return whether the world has a crops.txt with any rows */
	public static boolean anyRows()
	{
		return rows().size() > 0;
	}

	private static String propText(final com.planet_ink.coffee_mud.core.interfaces.Physical P)
	{
		final Ability A = (P == null) ? null : P.fetchEffect("Prop_CropRules");
		return (A == null) ? "" : A.text();
	}

	/**
	 * @param R the room
	 * @param resource the crop's resource name, or ""
	 * @param subType its subtype, or ""
	 * @param seedRules the seed's own Prop_CropRules text, or ""
	 * @return the crop's rules there
	 */
	public static Rules of(final Room R, final String resource, final String subType, final String seedRules)
	{
		final Rules ru = new Rules();
		final Map<String, String> M = rows();
		if((resource != null) && (resource.length() > 0))
			ru.merge(M.get(resource.toUpperCase()));
		if((subType != null) && (subType.length() > 0))
			ru.merge(M.get(subType.toUpperCase()));
		if(R != null)
		{
			final Area A = R.getArea();
			if(A != null)
			{
				final List<Area> chain = new ArrayList<Area>(); // the area and its parents, the farthest first
				final LinkedList<Area> todo = new LinkedList<Area>();
				final Set<Area> seen = new HashSet<Area>();
				todo.add(A);
				while(todo.size() > 0)
				{
					final Area a = todo.removeFirst();
					if(!seen.add(a))
						continue;
					chain.add(0, a);
					for(final Enumeration<Area> p = a.getParents(); p.hasMoreElements();)
						todo.add(p.nextElement());
				}
				for(final Area a : chain)
					ru.merge(propText(a));
			}
			ru.merge(propText(R));
		}
		ru.merge(seedRules);
		return ru;
	}

	/**
	 * @param R a room
	 * @return its kind, as PLAINS or CAVE
	 */
	public static String domainName(final Room R)
	{
		final int d = R.domainType() & (~Room.INDOORS);
		if((R.domainType() & Room.INDOORS) == 0)
			return (d < Room.DOMAIN_OUTDOOR_DESCS.length) ? Room.DOMAIN_OUTDOOR_DESCS[d].toUpperCase() : "";
		return (d < Room.DOMAIN_INDOORS_DESCS.length) ? Room.DOMAIN_INDOORS_DESCS[d].toUpperCase() : "";
	}

	/**
	 * @param R a room
	 * @param ru the crop's rules
	 * @return whether the crop's DOMAINS name the room's kind
	 */
	public static boolean domainNamed(final Room R, final Rules ru)
	{
		return (R != null) && (ru.domains != null) && (ru.domains.contains(domainName(R)) || ru.domains.contains("ANY"));
	}

	/**
	 * @param R a room
	 * @param n how many
	 * @return whether that many of it and the rooms next to it are water
	 */
	public static boolean waterNear(final Room R, final int n)
	{
		int found = CMLib.flags().isWateryRoom(R) ? 1 : 0;
		for(int d = 0; (d < Directions.NUM_DIRECTIONS()) && (found < n); d++)
		{
			final Room R2 = R.getRoomInDir(d);
			if((R2 != null) && CMLib.flags().isWateryRoom(R2))
				found++;
		}
		return found >= n;
	}

	/**
	 * Why a crop will not grow in a room just now, or null if it will: its
	 * sun, its kind of room, the weather and its water.
	 * @param R the room
	 * @param ru the crop's rules
	 * @param sunMsg the skill's word for no sun
	 * @param landMsg its word for the wrong land
	 * @param droughtMsg its word for drought
	 * @return the refusal, or null
	 */
	public static String refusal(final Room R, final Rules ru, final String sunMsg, final String landMsg, final String droughtMsg)
	{
		final Area A = R.getArea();
		if(A == null)
			return landMsg;
		if(((ru.needSun == null) || ru.needSun.booleanValue()) && (!A.getClimateObj().canSeeTheSun(R)))
			return (ru.failMsg.length() > 0) ? ru.failMsg : sunMsg;
		final boolean land;
		if(ru.domains != null)
			land = domainNamed(R, ru) || (R.myResource() == RawMaterial.RESOURCE_DIRT);
		else
			land = (R.domainType() == Room.DOMAIN_OUTDOORS_HILLS)
				|| (R.domainType() == Room.DOMAIN_OUTDOORS_PLAINS)
				|| (R.domainType() == Room.DOMAIN_OUTDOORS_WOODS)
				|| (R.domainType() == Room.DOMAIN_OUTDOORS_JUNGLE)
				|| (R.domainType() == Room.DOMAIN_OUTDOORS_SWAMP)
				|| (R.myResource() == RawMaterial.RESOURCE_DIRT);
		if(!land)
			return (ru.failMsg.length() > 0) ? ru.failMsg : landMsg;
		if(A.getClimateObj().weatherType(R) == Climate.WEATHER_DROUGHT)
			return droughtMsg;
		if((ru.water > 0) && (!waterNear(R, ru.water)))
			return (ru.failMsg.length() > 0) ? ru.failMsg : CMLib.lang().L("There is not enough water nearby for that to grow.");
		return null;
	}

	/**
	 * @param R the room
	 * @param ru the crop's rules
	 * @return why it may not be planted in this season, or null
	 */
	public static String seasonRefusal(final Room R, final Rules ru)
	{
		if(ru.seasons.isEmpty() || (R == null) || (R.getArea() == null))
			return null;
		if(ru.seasons.contains(R.getArea().getTimeObj().getSeasonCode()))
			return null;
		return (ru.failMsg.length() > 0) ? ru.failMsg : CMLib.lang().L("This is not the season for planting that.");
	}

	/**
	 * @param R a room
	 * @return whether it is night there
	 */
	public static boolean isNight(final Room R)
	{
		return (R != null) && (R.getArea() != null) && (R.getArea().getTimeObj().getTODCode() == TimeClock.TimeOfDay.NIGHT);
	}

	/**
	 * @param ru the crop's rules
	 * @param dflt the skill's own ticks
	 * @return the ticks it takes to grow
	 */
	public static int growTicks(final Rules ru, final int dflt)
	{
		if(ru.growHours <= 0)
			return dflt;
		return (int)Math.max(1, Math.min(Integer.MAX_VALUE, ru.growHours * CMProps.getTicksPerMudHour()));
	}

	/**
	 * @param ru the crop's rules
	 * @param dflt the skill's own roll
	 * @return the yield's roll, by YIELD= when it has one
	 */
	public static int yieldRoll(final Rules ru, final int dflt)
	{
		final String y = ru.yield.replace(" ", "");
		if(y.length() == 0)
			return dflt;
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^(\\d+)d(\\d+)([+-]\\d+)?$").matcher(y);
		if(m.matches())
			return Math.max(0, CMLib.dice().roll(CMath.s_int(m.group(1)), CMath.s_int(m.group(2)), (m.group(3) == null) ? 0 : CMath.s_int(m.group(3).replace("+", ""))));
		return Math.max(0, CMath.s_parseIntExpression(y));
	}
}
