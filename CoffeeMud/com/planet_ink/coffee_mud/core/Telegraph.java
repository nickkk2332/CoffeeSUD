package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C5: telegraphed attacks.  Named alarms (MPALARM NAME=) live in the script
 * engines' queues; a telegraph (MPTELEGRAPH, or a GenAbility's WINDUP) is a
 * named alarm and an effect on the one winding up, which warns, can be
 * interrupted, and knows who got out of the way.
 */
public final class Telegraph
{
	private Telegraph()
	{
	}

	/** the first line of a named alarm's body, before its name */
	public static final String NAMED_ALARM = "FUNCTION_PROG ALARM_NAMED_";

	/** a telegraph's effect, as the script engine sees it */
	public interface Effect
	{
		/**
		 * @return its name, upper case: its alarm's
		 */
		public String telegraphName();

		/**
		 * @return those it hits now: the foes in the room who did not avoid it
		 */
		public List<MOB> telegraphTargets();

		/** it is done: the effect goes */
		public void telegraphDone();

		/** shows its warning to the room, as each tick */
		public void telegraphWarn();

		/**
		 * A10: what a foe's response does instead of the wind-up's own blow: a
		 * command to run for them ($n), or null for the blow itself.
		 * @param target a foe it hits
		 * @return the command, or null
		 */
		public default String telegraphCommand(final MOB target)
		{
			return null;
		}

		/**
		 * A10: the host script's function called once when it lands, which can
		 * start the next step of a chained attack.
		 * @return the function's name, or ""
		 */
		public default String telegraphThen()
		{
			return "";
		}
	}

	/** set while a wound-up ability is released, so that it does not wind up again */
	private static final ThreadLocal<Boolean> releasing = new ThreadLocal<Boolean>();

	/**
	 * @return whether a wound-up ability is being released now
	 */
	public static boolean releasing()
	{
		return Boolean.TRUE.equals(releasing.get());
	}

	/**
	 * Uses an ability whose windup is over, without winding up again.
	 * @param mob who uses it
	 * @param A the ability
	 * @param target its target, or null
	 * @return whether it worked
	 */
	public static boolean release(final MOB mob, final Ability A, final Physical target)
	{
		final Boolean was = releasing.get();
		releasing.set(Boolean.TRUE);
		try
		{
			final List<String> words = new ArrayList<String>();
			if(target != null)
				words.add(target.name());
			return A.invoke(mob, words, target, false, 0);
		}
		finally
		{
			releasing.set(was);
		}
	}

	/**
	 * Whether one can still finish what it winds up: alive, awake, not held,
	 * bound or paralysed.  A named alarm of one who cannot is cancelled.
	 * @param mob the one
	 * @return true if it can
	 */
	public static boolean canAct(final MOB mob)
	{
		return (mob != null) && (!mob.amDead()) && (!mob.amDestroyed()) && CMLib.flags().isAliveAwakeMobileUnbound(mob, true);
	}

	/**
	 * Cancels a named alarm on all the scripts of a host.
	 * @param host the host
	 * @param name the alarm's name
	 * @return whether there was one
	 */
	public static boolean cancel(final PhysicalAgent host, final String name)
	{
		boolean any = false;
		for(final ScriptingEngine S : ScriptEvents.engines(host))
			any = S.cancelAlarm(name) || any;
		return any;
	}

	/**
	 * @param host the host
	 * @param name a named alarm
	 * @return the ticks before it goes off, the soonest on its scripts, or -1 for none
	 */
	public static int ticksLeft(final PhysicalAgent host, final String name)
	{
		int best = -1;
		for(final ScriptingEngine S : ScriptEvents.engines(host))
		{
			final int t = S.alarmTicksLeft(name);
			if((t >= 0) && ((best < 0) || (t < best)))
				best = t;
		}
		return best;
	}

	/**
	 * @param mob who may be winding up
	 * @param name the telegraph's name
	 * @return its effect, or null
	 */
	public static Effect find(final MOB mob, final String name)
	{
		if(mob == null)
			return null;
		for(final Enumeration<Ability> a = mob.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if((A instanceof Effect) && ((Effect)A).telegraphName().equalsIgnoreCase(name))
				return (Effect)A;
		}
		return null;
	}

	/**
	 * Those a telegraph would hit: the others in the room on the other side,
	 * the player's side against NPCs, and anyone fighting it.
	 * @param mob who winds up
	 * @return them
	 */
	public static List<MOB> foesHere(final MOB mob)
	{
		final List<MOB> list = new ArrayList<MOB>();
		final Room R = (mob == null) ? null : mob.location();
		if(R == null)
			return list;
		final Set<MOB> mine = mob.getGroupMembers(new HashSet<MOB>());
		final boolean side = GameDifficulty.playerSide(mob);
		final List<MOB> fighting = Threat.enemies(mob);
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (M == mob) || M.amDead() || mine.contains(M))
				continue;
			if((GameDifficulty.playerSide(M) != side) || fighting.contains(M) || (M.getVictim() == mob))
				list.add(M);
		}
		return list;
	}
}
