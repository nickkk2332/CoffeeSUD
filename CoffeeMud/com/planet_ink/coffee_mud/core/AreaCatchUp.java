package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Behaviors.interfaces.MobileBehavior;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;

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
 * N8: when an area wakes (PASSIVE, with no player in it for PASSIVEMINS, to
 * ACTIVE), what its NPCs would have done meanwhile is done at once: each
 * behaviour and effect of the area, its rooms, the mobs and the items lying
 * in them gets {@link Behavior#catchUp(PhysicalAgent, long)} or
 * {@link Ability#catchUp(com.planet_ink.coffee_mud.core.interfaces.Physical, long)}
 * with the ticks it slept, and a script's FUNCTION_PROG CATCHUP runs with
 * $g the ticks.  The wanderers (Mobile) go first, then the routines
 * (Schedule, Patroller, Sleeper, Prop_ClosedDayNight), so that a routine has
 * the last word.  CATCHUP=OFF in the world's settings turns it off; an
 * area with the tag NOCATCHUP is left as it was.
 */
public final class AreaCatchUp
{
	private AreaCatchUp()
	{
	}

	/** the tag that keeps an area from catching up */
	public static final String	NO_TAG		= "NOCATCHUP";

	/** the script function run on a wake */
	public static final String	FUNCTION	= "CATCHUP";

	/** what the last wake of each area did, for STAT and the tests: ticks, things */
	private static final Map<String, long[]>	last	= new Hashtable<String, long[]>();

	/**
	 * @return false if the world's CATCHUP setting is OFF
	 */
	public static boolean on()
	{
		final String s = CMProps.instance().getStr("CATCHUP");
		return (s == null) || (!s.trim().equalsIgnoreCase("OFF"));
	}

	/**
	 * @param A an area
	 * @return true if it catches up when it wakes
	 */
	public static boolean wanted(final Area A)
	{
		return (A != null) && on() && (!A.hasTag(NO_TAG));
	}

	/**
	 * @param A an area
	 * @return its last wake: the ticks it slept and how many things caught up, or null
	 */
	public static long[] lastWake(final Area A)
	{
		return (A == null) ? null : last.get(A.Name());
	}

	/**
	 * The area woke after sleeping this many ticks: everything in it catches up.
	 * @param A the area
	 * @param ticks how long it was passive, in ticks
	 * @return how many behaviours, effects and scripts were told
	 */
	public static int wake(final Area A, final long ticks)
	{
		if((ticks <= 0) || (!wanted(A)))
			return 0;
		// what is there now: catching up moves mobs from room to room
		final List<PhysicalAgent> things = new ArrayList<PhysicalAgent>();
		things.add(A);
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			things.add(R);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && (M.playerStats() == null) && (!M.amDead()))
					things.add(M);
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if((I != null) && (I.container() == null))
					things.add(I);
			}
		}
		int n = 0;
		for(final PhysicalAgent P : things) // the wanderers first
			n += behaviors(P, ticks, true);
		for(final PhysicalAgent P : things) // then the routines, the effects and the scripts
		{
			if((P instanceof MOB) && ((MOB)P).amDead())
				continue;
			n += behaviors(P, ticks, false);
			for(final Enumeration<Ability> a = P.effects(); a.hasMoreElements();)
			{
				final Ability E = a.nextElement();
				if(E != null)
				{
					E.catchUp(P, ticks);
					n++;
				}
			}
			for(final Enumeration<ScriptingEngine> s = P.scripts(); s.hasMoreElements();)
			{
				final ScriptingEngine S = s.nextElement();
				if((S != null) && (S.callFunction(P, FUNCTION, "" + ticks, null) != null))
					n++;
			}
		}
		last.put(A.Name(), new long[] { ticks, n });
		return n;
	}

	private static int behaviors(final PhysicalAgent P, final long ticks, final boolean wanderers)
	{
		int n = 0;
		for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if((B == null) || ((B instanceof MobileBehavior) != wanderers))
				continue;
			if(!BehaviorWhen.active(B, P)) // N1: one switched off now does nothing
				continue;
			if(B instanceof ScriptingEngine) // a Scriptable or a generic behaviour: its script's CATCHUP
			{
				if(((ScriptingEngine)B).callFunction(P, FUNCTION, "" + ticks, null) != null)
					n++;
			}
			else
			{
				B.catchUp(P, ticks);
				n++;
			}
		}
		return n;
	}
}
