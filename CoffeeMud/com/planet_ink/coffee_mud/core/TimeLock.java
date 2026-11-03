package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * W28: zones of Prop_TimeMode (a room's, else its area's): a character
 * played coming into one switches time to its MODE (turn-based or real
 * time), as TIMEMODE does, and the mode before is put back as they leave
 * (RESTORE, by default); ONLYCOMBAT holds it only while they fight there.
 * TIMEMODE says the mode is held while it is.
 */
public final class TimeLock
{
	private TimeLock()
	{
	}

	private static volatile Ability		zone		= null;
	private static volatile Boolean		previous	= null;
	private static volatile boolean		held		= false;

	/**
	 * @param R a room
	 * @return its Prop_TimeMode, else its area's, or null
	 */
	public static Ability zoneOf(final Room R)
	{
		if(R == null)
			return null;
		final Ability A = R.fetchEffect("Prop_TimeMode");
		if(A != null)
			return A;
		final Area AR = R.getArea();
		return (AR == null) ? null : AR.fetchEffect("Prop_TimeMode");
	}

	/** @return the zone's property holding time now, or null */
	public static Ability heldBy()
	{
		return held ? zone : null;
	}

	/** @return the mode before the zone: TRUE turn-based, FALSE real time, null for none */
	public static Boolean previous()
	{
		return previous;
	}

	/**
	 * After a restore: the mode the zone took over from, as the save had it.
	 * @param before TURNS, REALTIME, or null or "" for none
	 */
	public static void restore(final String before)
	{
		zone = null;
		held = false;
		previous = ((before == null) || (before.length() == 0)) ? null : Boolean.valueOf(before.equalsIgnoreCase("TURNS"));
	}

	private static boolean turns(final Ability A)
	{
		return !CMParms.getParmStr(A.text(), "MODE", "TURNS").toUpperCase().startsWith("REAL");
	}

	private static boolean onlyCombat(final Ability A)
	{
		return CMath.s_bool(CMParms.getParmStr(A.text(), "ONLYCOMBAT", "false"));
	}

	private static void hold(final MOB mob, final Ability A)
	{
		if(previous == null)
			previous = Boolean.valueOf(GameTime.isTurnBased());
		final boolean want = turns(A);
		held = true;
		if(GameTime.isTurnBased() != want)
		{
			GameTime.setTurnBased(want);
			final String msg = CMParms.getParmStr(A.text(), "MSG", "");
			if((mob != null) && (msg.length() > 0))
				mob.tell(msg);
			ScriptEvents.game("TIMEMODE", mob, want ? "TURNS" : "REALTIME");
		}
	}

	private static void release(final MOB mob, final Ability A)
	{
		held = false;
		final Boolean before = previous;
		previous = null;
		final boolean restore = (A == null) || CMath.s_bool(CMParms.getParmStr(A.text(), "RESTORE", "true"));
		if(restore && (before != null) && (GameTime.isTurnBased() != before.booleanValue()))
		{
			GameTime.setTurnBased(before.booleanValue());
			if(mob != null)
				mob.tell(before.booleanValue() ? CMLib.lang().L("Time is turn-based again.") : CMLib.lang().L("Time runs on its own again."));
			ScriptEvents.game("TIMEMODE", mob, before.booleanValue() ? "TURNS" : "REALTIME");
		}
	}

	/**
	 * Where a character played is now (after a move, or as a fight starts
	 * or ends): into a zone, out of one, or still in it.
	 * @param mob the character
	 */
	public static void check(final MOB mob)
	{
		if((mob == null) || (!mob.isPlayer()))
			return;
		final Ability A = zoneOf(mob.location());
		if(A == null)
		{
			if(zone != null)
			{
				final Ability was = zone;
				zone = null;
				if(held)
					release(mob, was);
				else
					previous = null;
			}
			return;
		}
		if(zone != A)
		{
			if(held && (zone != null))
				release(mob, zone);
			zone = A;
		}
		final boolean want = (!onlyCombat(A)) || mob.isInCombat();
		if(want && !held)
			hold(mob, A);
		else
		if((!want) && held)
			release(mob, A);
	}
}
