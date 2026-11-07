package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
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
 * A12, A13: what core code does with a character's ailment meters
 * (CompositionAilment) and leech (CompositionLeech).  The effects are loaded
 * by the engine's own class loader, so core code reaches them only through
 * these interfaces, never by their classes.
 */
public final class Ailments
{
	private Ailments()
	{
	}

	/** what CompositionAilment does for core code */
	public interface Holder
	{
		/** @return whether a status is on */
		boolean hasStatus(String id);

		/** @return the statuses on, as modifier conditions (status:id) */
		Set<String> statusConditions();

		/** @return a meter's value */
		long meter(String id);

		/** a landed hit's buildup; @return the trace lines */
		List<String> build(MOB host, CombatRules.Profile profile, Map<String, Long> buildup);

		/** ends statuses early; @return the trace lines */
		List<String> consume(MOB host, Set<String> ids);

		/** sheds ailments (all of them for null); @return whether any went */
		boolean cleanse(MOB host, Set<String> ids);
	}

	/** what CompositionLeech does for core code */
	public interface Leech
	{
		/** adds what is to come back, and the most a tick */
		void add(long amount, int cap);

		/** @return what is still to come back */
		long pending();
	}

	private static Holder holder(final MOB target, final boolean create)
	{
		Ability A = target.fetchEffect("CompositionAilment");
		if((A == null) && create)
		{
			A = CMClass.getAbility("CompositionAilment");
			if(A == null)
				return null;
			A.setMiscText("{}");
			A.startTickDown(target, target, LIFETIME); // it ends itself sooner, when nothing is left
			A = target.fetchEffect("CompositionAilment");
		}
		return (A instanceof Holder) ? (Holder)A : null;
	}

	/** how long the effects may last at most, in ticks: far longer than any meter, and safe to save and restore */
	public static final int LIFETIME = 1000000;

	/** the statuses a target has now, as modifier conditions */
	public static Set<String> conditions(final MOB target)
	{
		final Holder H = holder(target, false);
		return (H == null) ? Collections.<String>emptySet() : H.statusConditions();
	}

	/** a landed composed hit's buildup on a target; @return the trace lines */
	public static List<String> build(final MOB target, final CombatRules.Profile profile, final Map<String, Long> buildup)
	{
		if(buildup.isEmpty())
			return new ArrayList<String>();
		final Holder H = holder(target, true);
		return (H == null) ? new ArrayList<String>(Arrays.asList("Ailments: the CompositionAilment effect is not installed.")) : H.build(target, profile, buildup);
	}

	/** a landed hit that consumes statuses; @return the trace lines */
	public static List<String> consume(final MOB target, final Set<String> ids)
	{
		final Holder H = holder(target, false);
		return ((H == null) || ids.isEmpty()) ? new ArrayList<String>() : H.consume(target, ids);
	}

	/** sheds ailments from a character (all of them for null); @return whether any went */
	public static boolean cleanse(final MOB target, final Set<String> ids)
	{
		final Holder H = holder(target, false);
		return (H != null) && H.cleanse(target, ids);
	}

	/**
	 * Life leech to come back to a character over time, at most cap a tick.
	 */
	public static void leech(final MOB caster, final long amount, final int cap)
	{
		if((caster == null) || (amount <= 0) || (cap <= 0))
			return;
		Ability A = caster.fetchEffect("CompositionLeech");
		if(!(A instanceof Leech))
		{
			A = CMClass.getAbility("CompositionLeech");
			if(A == null)
				return;
			A.setMiscText("{\"pending\":0,\"cap\":" + cap + "}");
			A.startTickDown(caster, caster, LIFETIME); // it ends itself sooner, when all has come back
			A = caster.fetchEffect("CompositionLeech");
			if(!(A instanceof Leech))
				return;
		}
		((Leech)A).add(amount, cap);
	}
}
