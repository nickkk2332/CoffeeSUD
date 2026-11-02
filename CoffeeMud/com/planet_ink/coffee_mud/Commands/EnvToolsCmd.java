package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.Room.VariationCode;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * DT20, for builders: HAZARDS (the area's hazards, spawners and conditions
 * with their state), and what WEATHER SET, TIME SET and TIME SEASON do (the
 * WEATHER and TIME commands hand those words here).
 */
public class EnvToolsCmd extends StdCommand
{
	public EnvToolsCmd()
	{
	}

	private final String[] access = I(new String[] { "HAZARDS" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Whether a character may change the weather and the time.
	 * @param mob the character
	 * @return true in builder mode, a DEV=1 game, or with CMDAREAS
	 */
	public static boolean mayChange(final MOB mob)
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDAREAS);
	}

	/**
	 * The weather code of a word: a name as WEATHER lists them (CLEAR, RAIN,
	 * HEAT...), or a variation tag's (HEATWAVE, DUSTSTORM).
	 * @param word the word
	 * @return the code, or -1
	 */
	public static int weatherCode(final String word)
	{
		final String w = word.toUpperCase().trim();
		for(int i = 0; i < Climate.WEATHER_DESCS.length; i++)
		{
			if(Climate.WEATHER_DESCS[i].equals(w))
				return i;
		}
		final VariationCode V = (VariationCode)CMath.s_valueOf(VariationCode.class, w);
		if((V != null) && (V.c == 'W'))
			return V.num;
		return -1;
	}

	/**
	 * WEATHER SET weather [AREA name] [NEXT]: changes an area's weather now,
	 * as a change of weather comes (its messages and WEATHER triggers), or
	 * only what comes next.
	 * @param mob the one asking
	 * @param words the words after SET
	 * @return what was done
	 */
	public static String weatherSet(final MOB mob, List<String> words)
	{
		if(words.isEmpty())
			return "WEATHER SET [" + String.join("/", Climate.WEATHER_DESCS) + "] (AREA [NAME]) (NEXT)";
		final int code = weatherCode(words.get(0));
		if(code < 0)
			return "'" + words.get(0) + "' is no weather: " + String.join(", ", Climate.WEATHER_DESCS).toLowerCase() + ".";
		Area A = (mob.location() == null) ? null : mob.location().getArea();
		boolean next = false;
		// W12: FOR n HOURS|DAYS holds it there
		long holdMs = 0;
		for(int i = 1; i < words.size() - 2; i++)
		{
			if(words.get(i).equalsIgnoreCase("FOR") && CMath.isInteger(words.get(i + 1)))
			{
				holdMs = Laws.duration(words.get(i + 1) + " " + words.get(i + 2), (A == null) ? null : A.getTimeObj());
				if(holdMs < 0)
					return "FOR [N] HOURS/DAYS/WEEKS/MONTHS (or TICKS)";
				words = new ArrayList<String>(words);
				words.remove(i);
				words.remove(i);
				words.remove(i);
				break;
			}
		}
		for(int i = 1; i < words.size(); i++)
		{
			if(words.get(i).equalsIgnoreCase("NEXT"))
				next = true;
			else
			if(words.get(i).equalsIgnoreCase("AREA") && (i < words.size() - 1))
			{
				final String name = CMParms.combine(words.subList(i + 1, words.size()), 0).replaceAll("(?i)\\s+NEXT$", "");
				if(words.get(words.size() - 1).equalsIgnoreCase("NEXT"))
					next = true;
				A = CMLib.map().getArea(name);
				if(A == null)
					return "There is no area '" + name + "'.";
				break;
			}
		}
		if(A == null)
			return "There is no area here.";
		final Climate C = A.getClimateObj();
		WeatherControl.change(A, code, next, holdMs, false); // W12
		final String held = (holdMs > 0) ? (", held for " + (holdMs / Math.max(1, CMProps.getMillisPerMudHour())) + " game hours") : "";
		if(next)
			return "The weather in " + A.Name() + " turns " + Climate.WEATHER_DESCS[code].toLowerCase() + " when it next changes" + held + ".";
		return "The weather in " + A.Name() + " is now " + Climate.WEATHER_DESCS[C.weatherType(null)].toLowerCase() + held + ".";
	}

	/**
	 * TIME SET hour|DAWN|DAY|DUSK|NIGHT: the area's clock goes forward to that
	 * hour, hour by hour as TICKTOCK goes, so the time's events run.
	 * @param mob the one asking
	 * @param what the hour or time of day
	 * @return what was done
	 */
	public static String timeSet(final MOB mob, final String what)
	{
		final Room R = mob.location();
		if(R == null)
			return "";
		final TimeClock C = R.getArea().getTimeObj();
		int hour = -1;
		if(CMath.isInteger(what))
			hour = CMath.s_int(what);
		else
		{
			final TimeClock.TimeOfDay T = (TimeClock.TimeOfDay)CMath.s_valueOf(TimeClock.TimeOfDay.class, what.toUpperCase().trim());
			if(T == null)
				return "TIME SET [HOUR/DAWN/DAY/DUSK/NIGHT]";
			hour = C.getDawnToDusk()[T.ordinal()];
		}
		if((hour < 0) || (hour >= C.getHoursInDay()))
			return "A day here has hours 0 to " + (C.getHoursInDay() - 1) + ".";
		final int ahead = (hour - C.getHourOfDay() + C.getHoursInDay()) % C.getHoursInDay();
		if(ahead > 0)
		{
			C.tickTock(ahead);
			C.save();
		}
		return "It is now hour " + C.getHourOfDay() + " (" + C.getTODCode().name().toLowerCase() + ") in " + R.getArea().Name() + ", "
			+ ahead + " hours on.";
	}

	/**
	 * TIME SEASON season: the clock goes forward a month at a time, its events
	 * running, until the season comes.
	 * @param mob the one asking
	 * @param what the season
	 * @return what was done
	 */
	public static String timeSeason(final MOB mob, final String what)
	{
		final Room R = mob.location();
		if(R == null)
			return "";
		final TimeClock.Season S = (TimeClock.Season)CMath.s_valueOf(TimeClock.Season.class, what.toUpperCase().trim());
		if(S == null)
			return "TIME SEASON [SPRING/SUMMER/FALL/WINTER]";
		final TimeClock C = R.getArea().getTimeObj();
		int months = 0;
		while((C.getSeasonCode() != S) && (months < 24))
		{
			C.tickTock(C.getHoursInDay() * C.getDaysInMonth());
			months++;
		}
		C.save();
		return "It is now " + C.getSeasonCode().name().toLowerCase() + " in " + R.getArea().Name() + ", " + months + " months on.";
	}

	private static boolean isHazard(final Ability A)
	{
		if(A == null)
			return false;
		if((A.classificationCode() & Ability.ALL_ACODES) == Ability.ACODE_TRAP)
			return true;
		final String id = A.ID();
		return id.equals("Prop_InstantDeath") || id.equals("Prop_NarrowLedge") || id.equals("Prop_Weather");
	}

	private static void listHost(final StringBuilder str, final String where, final PhysicalAgent P, final boolean allEffects)
	{
		for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			str.append("\n\r").append(where).append(" ").append(B.ID()).append(": ").append(B.getStat("DEBUG"));
		}
		for(final Enumeration<Ability> a = P.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if((A != null) && (allEffects || isHazard(A)) && (!A.canBeUninvoked()))
				str.append("\n\r").append(where).append(" ").append(A.ID()).append(": ").append((A.text().length() == 0) ? "(no parameters)" : A.text());
		}
	}

	/**
	 * HAZARDS: the area's behaviours and effects (spawners, weather and the
	 * like), and in its rooms their behaviours, traps and deadly properties, on
	 * the room, its exits and the items in it, each with its state.
	 * @param A the area
	 * @return the list
	 */
	public static String hazards(final Area A)
	{
		final StringBuilder str = new StringBuilder("Hazards, spawners and conditions in " + A.Name() + ":");
		final int before = str.length();
		listHost(str, A.Name() + " (the area)", A, true);
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			final String id = CMLib.map().getExtendedRoomID(R);
			listHost(str, id, R, false);
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Exit E = R.getRawExit(d);
				if(E != null)
					listHost(str, id + " " + CMLib.directions().getDirectionName(d).toLowerCase(), E, false);
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				listHost(str, id + " " + I.Name(), I, false);
			}
		}
		if(str.length() == before)
			str.append(" none.");
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		Area A = (mob.location() == null) ? null : mob.location().getArea();
		if(commands.size() > 1)
		{
			A = CMLib.map().getArea(CMParms.combine(commands, 1));
			if(A == null)
			{
				mob.tell(L("There is no area '@x1'.", CMParms.combine(commands, 1)));
				return false;
			}
		}
		if(A != null)
			mob.tell(hazards(A));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return mayChange(mob) || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
