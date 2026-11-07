package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.Compiled;
import com.planet_ink.coffee_mud.core.save.SaveGame;

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
 * Q03: a build comparison, COMPOSE COMPARE: two of a character's composed
 * skills, with their supports, side by side against one target, with the
 * combat profile's exact chances.  Each outcome (hit or miss, blocked or not,
 * critical or not) is resolved by the profile's own resolver with the rolls
 * forced, and weighted by its exact probability: a roll succeeds on a whole
 * number from 1 up to its chance, out of 100.  Nothing is rolled, paid or
 * cooled down.  A player sees the chances and outcomes; the target's own stats
 * are shown only in a creator's view (builder mode or DEV=1).
 */
public final class BuildCompare
{
	private BuildCompare()
	{
	}

	/** one skill's outcomes against a target */
	public static final class Outcome
	{
		public String				id, name;
		public boolean				spell;
		public double				hit, block, critical;
		public double				expected;
		public Map<String, Integer>	lands	= new LinkedHashMap<String, Integer>();
		public Map<String, Integer>	crits	= new LinkedHashMap<String, Integer>();
		public Map<String, Integer>	blocked	= new LinkedHashMap<String, Integer>();
		public double				mana, movement;
	}

	private static CombatRules.Roller forced(final boolean hit, final boolean block, final boolean crit)
	{
		return reason -> {
			if(reason.equals("hit"))
				return hit ? 1 : 100;
			if(reason.equals("block"))
				return block ? 1 : 100;
			if(reason.equals("critical"))
				return crit ? 1 : 100;
			return 100;
		};
	}

	private static int total(final Map<String, Integer> m)
	{
		int t = 0;
		for(final Integer n : m.values())
			t += n.intValue();
		return t;
	}

	/** the exact outcomes of one composed skill against a target's stats */
	public static Outcome outcome(final MOB caster, final Compiled c, final CombatRules.Stats target, final CombatRules.Profile p) throws IOException
	{
		final Outcome o = new Outcome();
		o.id = c.skill.id;
		o.name = c.skill.name;
		o.spell = c.tags.contains("spell");
		o.mana = c.value("mana");
		o.movement = c.value("movement");
		final CombatRules.Stats attacker = CombatProfiles.stats(caster, p).stats;
		final CombatRules.Offense off = CombatRules.offense(c, Collections.<String>emptySet(), p.conversion);
		o.hit = o.spell ? 100 : CombatRules.hitChance(p, attacker, target);
		o.block = Math.min(p.blockCap, target.get(o.spell ? "spell_block" : "block"));
		o.critical = attacker.get("crit_chance");
		final double ph = CombatRules.probability(o.hit), pb = CombatRules.probability(o.block), pc = CombatRules.probability(o.critical);
		final boolean[] both = { true, false };
		for(final boolean h : both)
		{
			for(final boolean b : both)
			{
				for(final boolean k : both)
				{
					final double prob = (h ? ph : 1 - ph) * (b ? pb : 1 - pb) * (k ? pc : 1 - pc);
					final boolean shown = h && ((!b && !k) || (!b && k) || (b && !k));
					if((prob <= 0) && !shown)
						continue;
					final CombatRules.Resolution r = CombatRules.resolve(p, off.damage, attacker, target, o.spell, false, forced(h, b, k));
					if(prob > 0)
						o.expected += prob * total(r.damage);
					if(h && !b && !k)
						o.lands.putAll(r.damage);
					else
					if(h && !b && k)
						o.crits.putAll(r.damage);
					else
					if(h && b && !k)
						o.blocked.putAll(r.damage);
				}
			}
		}
		return o;
	}

	private static String damage(final Map<String, Integer> m)
	{
		final List<String> parts = new ArrayList<String>();
		for(final Map.Entry<String, Integer> e : m.entrySet())
		{
			if(e.getValue().intValue() > 0)
				parts.add(e.getKey() + " " + e.getValue());
		}
		return parts.isEmpty() ? "none" : String.join(", ", parts);
	}

	private static String pct(final double chance)
	{
		return String.format(Locale.ROOT, "%d%%", Integer.valueOf((int)Math.round(CombatRules.probability(chance) * 100)));
	}

	/** the creator's view: builder mode or a developer's game */
	public static boolean creatorView()
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode();
	}

	/** COMPOSE COMPARE [skill] [skill] ([target]): the two side by side; it changes nothing */
	public static String compare(final MOB mob, final String a, final String b, final MOB target) throws IOException
	{
		if(!OptionalModules.enabled(CompositionDefinitions.MODULE))
			throw new IOException("ability composition (" + CompositionDefinitions.MODULE + ") is not active in this campaign");
		if(!OptionalModules.enabled(CombatRules.MODULE))
			throw new IOException("a comparison needs the combat profile (" + CombatRules.MODULE + ")");
		final CombatRules.Profile p = CombatProfiles.current();
		final CombatRules.Stats stats = (target == null) ? p.defaults : CombatProfiles.stats(target, p).stats;
		final Outcome x = outcome(mob, AbilityComposition.compiled(mob, a), stats, p), y = outcome(mob, AbilityComposition.compiled(mob, b), stats, p);
		final StringBuilder s = new StringBuilder("Build comparison against ").append((target == null) ? "a target with the profile's defaults" : target.name())
			.append(" (profile ").append(p.key()).append("):");
		for(final Outcome o : new Outcome[] { x, y })
		{
			s.append("\n").append(o.id).append(" (").append(o.name).append("): ").append(o.spell ? "a spell" : "a weapon skill")
			 .append("; costs mana ").append((int)Math.ceil(o.mana)).append(", movement ").append((int)Math.ceil(o.movement))
			 .append("; cooldown ").append(AbilityComposition.remaining(mob, o.id)).append(" tick(s) left");
			s.append("\n  hit ").append(o.spell ? "no roll (a spell)" : pct(o.hit)).append("; blocked ").append(pct(o.block)).append("; critical ").append(pct(o.critical));
			s.append("\n  lands: ").append(damage(o.lands)).append("; critical: ").append(damage(o.crits)).append("; blocked: ").append(damage(o.blocked));
			s.append("\n  expected damage per cast: ").append(String.format(Locale.ROOT, "%.2f", Double.valueOf(o.expected)));
		}
		final double d = x.expected - y.expected;
		s.append("\n").append(Math.abs(d) < 0.005 ? "They deal the same expected damage per cast."
			: ((d > 0) ? x.id : y.id) + " deals " + String.format(Locale.ROOT, "%.2f", Double.valueOf(Math.abs(d))) + " more expected damage per cast.");
		if(creatorView())
			s.append("\nCreator view: the target's stats: ").append(stats.describe());
		s.append("\nChances are exact for this profile: a roll succeeds on 1 to the whole part of its chance, out of 100. Target conditions, the event budget and world rules can change a real cast. Nothing was rolled, paid or cooled down.");
		return s.toString();
	}
}
