package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
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
 * W1: warrants and fines for scripts, masks and JavaScript: whether a
 * character is wanted, and for what, what they owe, and pardons, fines,
 * releases and laws put aside for a while, quietly.
 */
public final class Laws
{
	private Laws()
	{
	}

	/** a law and the area it is the law of */
	public static final class Here
	{
		/** the legal behaviour */
		public final LegalBehavior	behavior;
		/** the area whose law it is */
		public final Area			area;

		Here(final LegalBehavior behavior, final Area area)
		{
			this.behavior = behavior;
			this.area = area;
		}

		/** @return the law itself, or null */
		public Law law()
		{
			return behavior.legalInfo(area);
		}
	}

	/**
	 * The law of a room, if it has one.
	 * @param R the room
	 * @return the law, or null
	 */
	public static Here of(final Room R)
	{
		if(R == null)
			return null;
		final LegalBehavior B = CMLib.law().getLegalBehavior(R);
		final Area A = CMLib.law().getLegalObject(R);
		return ((B == null) || (A == null)) ? null : new Here(B, A);
	}

	/**
	 * The law that counts for a host and a character: the host's room's,
	 * else the character's room's, else their start room's.
	 * @param host the script's host, or null
	 * @param who the character, or null
	 * @return the law, or null where there is none
	 */
	public static Here of(final Environmental host, final MOB who)
	{
		Here H = (host == null) ? null : of(CMLib.map().roomLocation(host));
		if((H == null) && (who != null))
			H = of(who.location());
		if((H == null) && (who != null))
			H = of(who.getStartRoom());
		return H;
	}

	/**
	 * Whether a warrant is for a crime: the law's crime of that key (ASSAULT,
	 * a tax law, a skill), else one whose text holds the words.
	 * @param L the law
	 * @param W the warrant
	 * @param crime the crime, or null or ALL for any
	 * @return true if it is
	 */
	public static boolean crimeIs(final Law L, final LegalWarrant W, final String crime)
	{
		if((crime == null) || (crime.trim().length() == 0) || crime.trim().equalsIgnoreCase("ALL"))
			return true;
		final String c = crime.trim().toUpperCase();
		final String wc = (W.crime() == null) ? "" : W.crime().toUpperCase();
		String[] info = (L == null) ? null : L.basicCrimes().get(c);
		if((info == null) && (L != null))
			info = L.abilityCrimes().get(c);
		if((info == null) && (L != null) && (L.taxLaws().get(c) instanceof String[]))
			info = (String[])L.taxLaws().get(c);
		if((info != null) && (info.length > Law.BIT_CRIMENAME) && (info[Law.BIT_CRIMENAME] != null)
		&& info[Law.BIT_CRIMENAME].trim().equalsIgnoreCase(wc.trim()))
			return true;
		return wc.equals(c) || wc.contains(c);
	}

	/**
	 * The warrants out against a character.
	 * @param H the law
	 * @param who the character
	 * @param crime a crime (its name, or a word of it), or null or ALL for any
	 * @return the warrants
	 */
	public static List<LegalWarrant> warrants(final Here H, final MOB who, final String crime)
	{
		final List<LegalWarrant> out = new ArrayList<LegalWarrant>();
		final Law L = (H == null) ? null : H.law();
		if((L == null) || (who == null))
			return out;
		for(final LegalWarrant W : new ArrayList<LegalWarrant>(L.warrants()))
		{
			if((W != null) && (W.criminal() == who) && crimeIs(L, W, crime))
				out.add(W);
		}
		return out;
	}

	/**
	 * @param H the law
	 * @param who the character
	 * @param crime a crime, or null for any
	 * @return true if a warrant is out against them
	 */
	public static boolean wanted(final Here H, final MOB who, final String crime)
	{
		return !warrants(H, who, crime).isEmpty();
	}

	/**
	 * @param H the law
	 * @param who the character
	 * @return the fines they owe there, in base value
	 */
	public static double fines(final Here H, final MOB who)
	{
		return ((H == null) || (who == null)) ? 0.0 : H.behavior.finesOwed(who);
	}

	/**
	 * Changes the fines a character owes (MPFINE).
	 * @param H the law
	 * @param who the character
	 * @param amount the amount
	 * @param relative true to add it (a minus takes away), false to set it
	 * @return what they owe now
	 */
	public static double fine(final Here H, final MOB who, final double amount, final boolean relative)
	{
		if((H == null) || (who == null))
			return 0.0;
		final double now = Math.max(0.0, relative ? (H.behavior.finesOwed(who) + amount) : amount);
		H.behavior.modifyAssessedFines(now, who);
		return now;
	}

	/**
	 * Takes warrants away, quietly (MPPARDON).
	 * @param H the law
	 * @param who the character
	 * @param crime a crime, or null or ALL for all of them
	 * @return how many
	 */
	public static int pardon(final Here H, final MOB who, final String crime)
	{
		int n = 0;
		for(final LegalWarrant W : warrants(H, who, crime))
		{
			H.behavior.deleteWarrant(H.area, W);
			n++;
		}
		return n;
	}

	/**
	 * Lets a character go (MPRELEASE): out of their cell, as if their time
	 * were served (the guards then take them to the release room, and the
	 * warrants that put them there are done), and out of the hands of the
	 * guards who were taking them in; their other warrants stay.
	 * @param H the law, or null for the prisoner's time only
	 * @param who the character
	 * @return true if anything was done
	 */
	public static boolean release(final Here H, final MOB who)
	{
		if(who == null)
			return false;
		boolean did = false;
		for(final String id : new String[] { "Prisoner", "Skill_HandCuff" })
		{
			final Ability A = who.fetchEffect(id);
			if(A != null)
			{
				A.unInvoke();
				who.delEffect(A);
				did = true;
			}
		}
		for(final LegalWarrant W : warrants(H, who, null))
		{
			if(W.state() != Law.STATE_RELEASE)
			{
				H.behavior.release(H.area, W);
				did = true;
			}
		}
		if(did)
		{
			who.recoverPhyStats();
			who.recoverCharStats();
		}
		return did;
	}

	/**
	 * Game time from words: n, n TICKS, n HOURS, DAYS, WEEKS or MONTHS (of
	 * the area's clock).
	 * @param words the words
	 * @param clock the clock, or null for the world's
	 * @return the milliseconds, or -1 if they are no time
	 */
	public static long duration(final String words, final TimeClock clock)
	{
		final List<String> w = CMParms.parse((words == null) ? "" : words.trim());
		if(w.isEmpty() || (!CMath.isInteger(w.get(0))))
			return -1;
		final long n = CMath.s_long(w.get(0));
		final String unit = (w.size() > 1) ? w.get(1).toUpperCase() : "TICKS";
		if(unit.startsWith("TICK"))
			return n * CMProps.getTickMillis();
		if(unit.startsWith("HOUR") || unit.startsWith("DAY") || unit.startsWith("WEEK") || unit.startsWith("MONTH"))
		{
			final TimeClock C = (clock == null) ? CMLib.time().globalClock() : clock;
			return ScriptEvents.everyHours(n, unit, C) * CMProps.getMillisPerMudHour();
		}
		return -1;
	}

	/**
	 * Puts a crime, or ALL, aside for a while (MPSUPPRESSLAW): no warrant
	 * is made for it until then.
	 * @param H the law
	 * @param crime the crime, or ALL
	 * @param millis how long, in game time; 0 or less ends it
	 */
	public static void suppress(final Here H, final String crime, final long millis)
	{
		if((H == null) || (crime == null) || (crime.trim().length() == 0))
			return;
		H.behavior.suppressLaws(crime.trim(), (millis > 0) ? Long.valueOf(GameTime.now() + millis) : null);
	}
}
