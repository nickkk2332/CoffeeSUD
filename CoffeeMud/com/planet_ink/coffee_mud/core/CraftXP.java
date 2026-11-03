package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
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
 * CR12: crafting and gathering XP for every class.  When a player
 * character's crafting, cooking or building skill makes something, it has an
 * XP value (what an Artisan earns for it); CRAFTXP=[PCT] in the ini gives
 * every player character that percent of it, and each Prop_CraftXP with
 * PCT= that applies adds its own.  When a gathering skill finds something,
 * GATHERXP=[N] and each Prop_CraftXP's GATHER= are XP for every unit found.
 * A Prop_CraftXP counts on the character, on an item they carry, on the
 * room or an item lying in it, and on the area or one of its parents.  The
 * XP goes through postExperience, so Prop_ModExperience and achievements
 * see it.  Both options are 0 when blank, so nothing changes until a world
 * sets them.
 */
public final class CraftXP
{
	private CraftXP()
	{
	}

	/** what Prop_CraftXP is, for the core */
	public interface Bonus
	{
		/**
		 * @param mob the crafter
		 * @param skill their skill
		 * @return the percent of a craft's XP value this adds for them, 0 if none
		 */
		public int pct(MOB mob, Ability skill);

		/**
		 * @param mob the gatherer
		 * @param skill their skill
		 * @return the XP a unit gathered this adds for them, 0 if none
		 */
		public int gather(MOB mob, Ability skill);
	}

	private static int ini(final String key)
	{
		final CMProps P = CMProps.instance();
		final String s = (P == null) ? "" : P.getStr(key, "").trim();
		return (s.length() == 0) ? 0 : CMath.s_int(s);
	}

	/** @return CRAFTXP, the percent of a craft's XP value for every player character */
	public static int craftPct()
	{
		return ini("CRAFTXP");
	}

	/** @return GATHERXP, the XP for every unit gathered */
	public static int gatherEach()
	{
		return ini("GATHERXP");
	}

	private static void effectsOf(final com.planet_ink.coffee_mud.core.interfaces.Physical P, final List<Bonus> L)
	{
		if(P == null)
			return;
		for(final Enumeration<Ability> e = P.effects(); e.hasMoreElements();)
		{
			final Ability A = e.nextElement();
			if(A instanceof Bonus)
				L.add((Bonus)A);
		}
	}

	/**
	 * @param mob a character
	 * @return the Prop_CraftXPs that count for them where they are
	 */
	public static List<Bonus> bonuses(final MOB mob)
	{
		final List<Bonus> L = new ArrayList<Bonus>(2);
		if(mob == null)
			return L;
		effectsOf(mob, L);
		for(final Enumeration<Item> i = mob.items(); i.hasMoreElements();)
			effectsOf(i.nextElement(), L);
		final Room R = mob.location();
		if(R != null)
		{
			effectsOf(R, L);
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				effectsOf(i.nextElement(), L);
			final Area A = R.getArea();
			if(A != null)
			{
				final Set<Area> seen = new HashSet<Area>();
				final LinkedList<Area> todo = new LinkedList<Area>();
				todo.add(A);
				while(todo.size() > 0)
				{
					final Area A2 = todo.removeFirst();
					if(!seen.add(A2))
						continue;
					effectsOf(A2, L);
					for(final Enumeration<Area> p = A2.getParents(); p.hasMoreElements();)
						todo.add(p.nextElement());
				}
			}
		}
		return L;
	}

	private static boolean isDomain(final Ability A, final int... domains)
	{
		final int d = A.classificationCode() & Ability.ALL_DOMAINS;
		for(final int x : domains)
		{
			if(d == x)
				return true;
		}
		return false;
	}

	/**
	 * @param mob a character
	 * @param skill a crafting skill
	 * @param value the craft's XP value
	 * @return the XP they earn for it
	 */
	public static int forCraft(final MOB mob, final Ability skill, final int value)
	{
		if(value <= 0)
			return 0;
		int pct = craftPct();
		for(final Bonus B : bonuses(mob))
			pct += B.pct(mob, skill);
		return (pct <= 0) ? 0 : (int)Math.round(value * (pct / 100.0));
	}

	/**
	 * @param mob a character
	 * @param skill a gathering skill
	 * @param units how much they found
	 * @return the XP they earn for it
	 */
	public static int forGathering(final MOB mob, final Ability skill, final int units)
	{
		if(units <= 0)
			return 0;
		int each = gatherEach();
		for(final Bonus B : bonuses(mob))
			each += B.gather(mob, skill);
		return (each <= 0) ? 0 : (each * units);
	}

	/**
	 * Called for every message a character hears: a craft or a find of
	 * their own earns them XP.
	 * @param mob the character
	 * @param msg the message
	 */
	public static void heard(final MOB mob, final CMMsg msg)
	{
		if((msg.source() != mob)
		||(msg.value() <= 0)
		||(!(msg.tool() instanceof Ability))
		||(mob.playerStats() == null))
			return;
		final int minor = msg.targetMinor();
		final Ability skill = (Ability)msg.tool();
		int xp = 0;
		if(minor == CMMsg.TYP_ITEMGENERATED)
		{
			if((msg.target() != null)
			&& isDomain(skill, Ability.DOMAIN_CRAFTINGSKILL, Ability.DOMAIN_EPICUREAN, Ability.DOMAIN_BUILDINGSKILL))
				xp = forCraft(mob, skill, msg.value());
		}
		else
		if(minor == CMMsg.TYP_ITEMSGENERATED)
		{
			if(isDomain(skill, Ability.DOMAIN_GATHERINGSKILL))
				xp = forGathering(mob, skill, msg.value());
		}
		else
			return;
		if(xp > 0)
			CMLib.leveler().postExperience(mob, "ABILITY:" + skill.ID(), null, null, xp, false);
	}
}
