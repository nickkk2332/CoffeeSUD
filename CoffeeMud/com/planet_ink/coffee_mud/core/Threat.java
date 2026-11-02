package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Libraries.interfaces.MaskingLibrary;
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
 * C18: an NPC's threat table: who it would turn on.  A mob with
 * CombatAbilities (or a preset) keeps its own, which retargets it; any
 * other NPC gets a light one, made when it is first hurt: twice the damage
 * dealt it, three times the healing of its victim.  Scripts read THREAT,
 * TOPTHREAT, WEAKEST and OPPONENT and change it with MPTHREAT; TargetPriority
 * TAUNTABLE follows it.
 */
public final class Threat
{
	private Threat()
	{
	}

	/** a behaviour that keeps an NPC's threat itself (CombatAbilities) */
	public interface Holder
	{
		/**
		 * @return its table, attacker to amount, or null for none yet
		 */
		public Map<MOB, int[]> threatTable();

		/**
		 * Changes one's threat, as its own reckoning would.
		 * @param host the NPC
		 * @param who whose threat
		 * @param delta how much more (or less)
		 */
		public void adjustThreat(MOB host, MOB who, int delta);
	}

	/** the light tables of NPCs without a holder */
	private static final Map<MOB, Map<MOB, int[]>>	light	= new WeakHashMap<MOB, Map<MOB, int[]>>();

	/**
	 * @param npc an NPC
	 * @return its behaviour that keeps its threat, or null
	 */
	public static Holder holderOf(final MOB npc)
	{
		if(npc == null)
			return null;
		for(final Enumeration<Behavior> b = npc.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B instanceof Holder)
				return (Holder)B;
		}
		return null;
	}

	/**
	 * @param npc an NPC
	 * @return a copy of its table, attacker to amount
	 */
	public static Map<MOB, Integer> table(final MOB npc)
	{
		final Map<MOB, Integer> copy = new LinkedHashMap<MOB, Integer>();
		final Holder H = holderOf(npc);
		final Map<MOB, int[]> T;
		if(H != null)
			T = H.threatTable();
		else
		{
			synchronized(light)
			{
				T = light.get(npc);
			}
		}
		if(T != null)
		{
			synchronized(T)
			{
				for(final Map.Entry<MOB, int[]> e : T.entrySet())
					copy.put(e.getKey(), Integer.valueOf(e.getValue()[0]));
			}
		}
		return copy;
	}

	/**
	 * @param npc an NPC
	 * @param who someone
	 * @return their threat to it
	 */
	public static int get(final MOB npc, final MOB who)
	{
		final Integer I = table(npc).get(who);
		return (I == null) ? 0 : I.intValue();
	}

	/**
	 * Adds to one's threat: through the NPC's own reckoning if it has one.
	 * @param npc the NPC
	 * @param who whose threat
	 * @param delta how much
	 */
	public static void add(final MOB npc, final MOB who, final int delta)
	{
		if((npc == null) || (who == null) || (npc == who))
			return;
		final Holder H = holderOf(npc);
		if(H != null)
		{
			H.adjustThreat(npc, who, delta);
			return;
		}
		Map<MOB, int[]> T;
		synchronized(light)
		{
			T = light.get(npc);
			if(T == null)
			{
				T = new HashMap<MOB, int[]>();
				light.put(npc, T);
			}
		}
		synchronized(T)
		{
			// a fight before this one, or those gone: forgotten
			final Room R = npc.location();
			for(final Iterator<MOB> i = T.keySet().iterator(); i.hasNext();)
			{
				final MOB M = i.next();
				if((M != who) && (M.amDead() || M.amDestroyed() || (M.location() != R)))
					i.remove();
			}
			int[] I = T.get(who);
			if(I == null)
			{
				I = new int[] { 0 };
				T.put(who, I);
			}
			I[0] = Math.max(0, I[0] + delta);
		}
	}

	/**
	 * Sets one's threat.
	 * @param npc the NPC
	 * @param who whose threat
	 * @param amount the amount
	 */
	public static void set(final MOB npc, final MOB who, final int amount)
	{
		add(npc, who, amount - get(npc, who));
	}

	/**
	 * Forgets an NPC's light table (a fight over).
	 * @param npc the NPC
	 */
	public static void forget(final MOB npc)
	{
		synchronized(light)
		{
			light.remove(npc);
		}
	}

	/**
	 * Damage dealt an NPC without a holder: twice it, to the one who dealt it.
	 * @param attacker who dealt it
	 * @param target who took it
	 * @param damage how much
	 */
	public static void damaged(final MOB attacker, final MOB target, final int damage)
	{
		if((damage <= 0) || (attacker == null) || (target == null) || (attacker == target) || (!target.isMonster()) || (holderOf(target) != null))
			return;
		add(target, attacker, damage * 2);
	}

	/**
	 * Healing of someone: three times it, to the healer, for each NPC here
	 * without a holder that fights the one healed or their group.
	 * @param healer who healed
	 * @param target who was healed
	 * @param amount how much
	 */
	public static void healed(final MOB healer, final MOB target, final int amount)
	{
		if((amount <= 0) || (healer == null) || (target == null) || (target.location() == null))
			return;
		final Set<MOB> group = target.getGroupMembers(new HashSet<MOB>());
		for(final Enumeration<MOB> m = target.location().inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (M == healer) || (!M.isMonster()) || (holderOf(M) != null) || (M.getVictim() == null))
				continue;
			if((M.getVictim() == target) || group.contains(M.getVictim()))
				add(M, healer, amount * 3);
		}
	}

	/**
	 * @param npc an NPC
	 * @return those here who fight it or its group, or whom it or they fight
	 */
	public static List<MOB> enemies(final MOB npc)
	{
		final List<MOB> list = new ArrayList<MOB>();
		final Room R = (npc == null) ? null : npc.location();
		if(R == null)
			return list;
		final Set<MOB> mine = npc.getGroupMembers(new HashSet<MOB>());
		final MOB victim = npc.getVictim();
		final Set<MOB> theirs = (victim == null) ? new HashSet<MOB>() : victim.getGroupMembers(new HashSet<MOB>());
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (M == npc) || mine.contains(M) || M.amDead())
				continue;
			if((M == victim) || theirs.contains(M) || (M.getVictim() == npc) || ((M.getVictim() != null) && mine.contains(M.getVictim())))
				list.add(M);
		}
		return list;
	}

	/**
	 * @param npc an NPC
	 * @return the one here with the most threat to it, or null
	 */
	public static MOB top(final MOB npc)
	{
		MOB best = null;
		int bestAmt = 0;
		final Room R = (npc == null) ? null : npc.location();
		for(final Map.Entry<MOB, Integer> e : table(npc).entrySet())
		{
			final MOB M = e.getKey();
			if((M == null) || M.amDead() || (M.location() != R))
				continue;
			if(e.getValue().intValue() > bestAmt)
			{
				best = M;
				bestAmt = e.getValue().intValue();
			}
		}
		return best;
	}

	/**
	 * @param npc an NPC
	 * @return the one with a clear lead, half as much again as the next, or null
	 */
	public static MOB clearLead(final MOB npc)
	{
		final MOB top = top(npc);
		if(top == null)
			return null;
		final int t = get(npc, top);
		int next = 0;
		final Room R = npc.location();
		for(final Map.Entry<MOB, Integer> e : table(npc).entrySet())
		{
			if((e.getKey() != top) && (e.getKey().location() == R) && (!e.getKey().amDead()))
				next = Math.max(next, e.getValue().intValue());
		}
		return (t > next * 1.5) ? top : null;
	}

	/**
	 * @param npc an NPC
	 * @return the enemy here with the least of their hit points left, in percent, or null
	 */
	public static MOB weakest(final MOB npc)
	{
		MOB best = null;
		double low = Double.MAX_VALUE;
		for(final MOB M : enemies(npc))
		{
			final double p = SupportPicker.hpPct(M);
			if(p < low)
			{
				low = p;
				best = M;
			}
		}
		return best;
	}

	/**
	 * @param npc an NPC
	 * @param mask a zapper mask
	 * @return the first enemy here the mask fits, or null
	 */
	public static MOB opponent(final MOB npc, final String mask)
	{
		final MaskingLibrary.CompiledZMask Z = CMLib.masking().getPreCompiledMask(mask);
		for(final MOB M : enemies(npc))
		{
			if(CMLib.masking().maskCheck(Z, M, true))
				return M;
		}
		return null;
	}
}
