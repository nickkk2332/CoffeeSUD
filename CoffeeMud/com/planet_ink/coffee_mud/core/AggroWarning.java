package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

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
 * N17: a warning before an aggressive NPC attacks.  WARN="line|line" on
 * Aggressive, VeryAggressive or MobileAggressive: one line a tick for
 * WARNTICKS=n ticks (the number of lines by default), then the attack, if
 * the intruder is still there.  BACKOFF=true: one who sits down, or holds
 * no weapon, is not attacked while they stay so, and is warned afresh if
 * they stop.  NEAR=1: the warning is heard next door too.
 */
public final class AggroWarning
{
	private AggroWarning()
	{
	}

	/** what a behaviour's WARN parameters say */
	public static final class Spec
	{
		/** the lines, one a tick */
		public String[]	lines	= new String[0];
		/** how many ticks of warning */
		public int		ticks	= 1;
		/** whether backing off holds the attack */
		public boolean	backoff	= false;
		/** whether next door hears it */
		public boolean	near	= false;
	}

	/** an observer's warnings: intruder's name, ticks warned (or -1 while they back off) */
	private static final Map<MOB, Map<String, int[]>>	warned	= new WeakHashMap<MOB, Map<String, int[]>>();

	/**
	 * @param parms a behaviour's parameters
	 * @return its warning, or null for none
	 */
	public static Spec parse(final String parms)
	{
		final String warn = CMParms.getParmStr(parms, "WARN", "");
		if(warn.trim().length() == 0)
			return null;
		final Spec S = new Spec();
		final List<String> lines = new ArrayList<String>();
		for(final String l : warn.split("\\|"))
		{
			if(l.trim().length() > 0)
				lines.add(l.trim());
		}
		if(lines.isEmpty())
			return null;
		S.lines = lines.toArray(new String[0]);
		S.ticks = Math.max(1, CMParms.getParmInt(parms, "WARNTICKS", S.lines.length));
		S.backoff = CMParms.getParmStr(parms, "BACKOFF", "false").equalsIgnoreCase("true");
		S.near = CMParms.getParmInt(parms, "NEAR", 0) > 0;
		return S;
	}

	private static Map<String, int[]> of(final MOB observer)
	{
		synchronized(warned)
		{
			Map<String, int[]> m = warned.get(observer);
			if(m == null)
			{
				m = new LinkedHashMap<String, int[]>();
				warned.put(observer, m);
			}
			return m;
		}
	}

	/** whether one warned is backing off: sitting, or no weapon in hand */
	private static boolean backingOff(final MOB M)
	{
		// what it wears at the wield place, not the mob's cached wielded item
		return CMLib.flags().isSitting(M) || CMLib.flags().isSleeping(M)
			|| (M.fetchFirstWornItem(com.planet_ink.coffee_mud.Items.interfaces.Wearable.WORN_WIELD) == null);
	}

	/** says a warning line, and next door too with NEAR */
	private static void say(final MOB observer, final MOB intruder, final Spec S, final int n)
	{
		final Room R = observer.location();
		if(R == null)
			return;
		final String line = S.lines[Math.min(n, S.lines.length - 1)];
		R.show(observer, intruder, null, CMMsg.MSG_OK_VISUAL, line);
		if(S.near)
		{
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Room R2 = R.getRoomInDir(d);
				if((R2 != null) && (R2 != R) && (R.getExitInDir(d) != null))
				{
					final String from = CMLib.directions().getFromCompassDirectionName(Directions.getOpDirectionCode(d));
					R2.showHappens(CMMsg.MSG_NOISE, CMLib.lang().L("From @x1: @x2", from, line));
				}
			}
		}
	}

	/**
	 * An aggressive NPC is about to attack: whether it may yet.  The first
	 * time it sees them it warns instead.
	 * @param observer the NPC
	 * @param intruder whom it would attack
	 * @param S its warning
	 * @return true if it has warned long enough
	 */
	public static boolean mayAttack(final MOB observer, final MOB intruder, final Spec S)
	{
		if((S == null) || (observer == null) || (intruder == null))
			return true;
		final Map<String, int[]> m = of(observer);
		synchronized(m)
		{
			final int[] c = m.get(intruder.Name());
			if(c == null)
			{
				if(S.backoff && backingOff(intruder))
				{
					m.put(intruder.Name(), new int[] { -1 });
					return false;
				}
				m.put(intruder.Name(), new int[] { 1 });
				say(observer, intruder, S, 0);
				return false;
			}
			return c[0] >= S.ticks;
		}
	}

	/**
	 * Each tick: those warned are warned again, or, their time up, are to be attacked.
	 * @param observer the NPC
	 * @param S its warning
	 * @return those to attack now
	 */
	public static List<MOB> tick(final MOB observer, final Spec S)
	{
		final List<MOB> attack = new ArrayList<MOB>();
		if((S == null) || (observer == null) || observer.isInCombat())
			return attack;
		final Room R = observer.location();
		final Map<String, int[]> m = of(observer);
		synchronized(m)
		{
			for(final Iterator<Map.Entry<String, int[]>> i = m.entrySet().iterator(); i.hasNext();)
			{
				final Map.Entry<String, int[]> e = i.next();
				final MOB M = (R == null) ? null : R.fetchInhabitantExact(e.getKey());
				if((M == null) || M.amDead())
				{
					i.remove(); // gone: forgotten
					continue;
				}
				final int[] c = e.getValue();
				if(S.backoff && backingOff(M))
				{
					c[0] = -1; // backing off: held
					continue;
				}
				if(c[0] < 0)
				{
					c[0] = 1; // no longer: warned afresh
					say(observer, M, S, 0);
					continue;
				}
				if(c[0] < S.ticks)
				{
					say(observer, M, S, c[0]);
					c[0]++;
					continue;
				}
				i.remove();
				attack.add(M);
			}
		}
		return attack;
	}

	/**
	 * @param observer an NPC
	 * @param who a name
	 * @return how many ticks it has warned them, -1 while they back off, or 0 if not
	 */
	public static int warnedFor(final MOB observer, final String who)
	{
		final Map<String, int[]> m = of(observer);
		synchronized(m)
		{
			final int[] c = m.get(who);
			return (c == null) ? 0 : c[0];
		}
	}
}
