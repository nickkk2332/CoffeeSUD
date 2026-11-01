package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.function.Predicate;

import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerStats;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
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
 * Script triggers for things that happen without a message: a skill learned,
 * improved, mastered or failed (ST2, SKILL_PROG), a room or area first visited
 * (ST3, DISCOVER_PROG).  An event at a room reaches the scripts a message in
 * that room would: the room, its items, its mobs and what they carry, and its
 * area and the area's parents.  Each script engine there is handed the event
 * through {@link ScriptingEngine#eventTrigger}, which queues the scripts of
 * that trigger whose trigger line the event matches.  Nothing is done for a
 * trigger no script in the world has.
 */
public final class ScriptEvents
{
	private ScriptEvents()
	{
	}

	private static final BitSet	used	= new BitSet();

	/**
	 * Notes that a parsed script has a trigger, so that its events are looked
	 * for from now on.
	 * @param triggerCode the trigger's code, its place in ScriptingEngine.progs plus one
	 */
	public static void uses(final int triggerCode)
	{
		if(triggerCode > 0)
		{
			synchronized(used)
			{
				used.set(triggerCode);
			}
		}
	}

	/**
	 * Whether any script parsed since the game started has a trigger.
	 * @param triggerCode the trigger's code
	 * @return true if one has
	 */
	public static boolean isUsed(final int triggerCode)
	{
		synchronized(used)
		{
			return used.get(triggerCode);
		}
	}

	/**
	 * A trigger's code, from its name.
	 * @param name the trigger, such as SKILL_PROG
	 * @return its code, or 0
	 */
	public static int code(final String name)
	{
		for(int i = 0; i < ScriptingEngine.progs.length; i++)
		{
			if(ScriptingEngine.progs[i].equalsIgnoreCase(name))
				return i + 1;
		}
		return 0;
	}

	/** SKILL_PROG's code */
	public static final int	SKILL_PROG		= code("SKILL_PROG");
	/** DISCOVER_PROG's code */
	public static final int	DISCOVER_PROG	= code("DISCOVER_PROG");

	/** what SKILL_PROG tells apart */
	public static final String[]	SKILL_KINDS	= { "LEARN", "IMPROVE", "MASTER", "FAIL", "ANY" };

	/**
	 * The hosts an event in a room reaches: the room, its items, its mobs and
	 * what they carry, its area and the area's parents.
	 * @param R the room
	 * @return the hosts
	 */
	public static List<PhysicalAgent> hosts(final Room R)
	{
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		if(R == null)
			return hosts;
		hosts.add(R);
		for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if(I != null)
				hosts.add(I);
		}
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if(M == null)
				continue;
			hosts.add(M);
			for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if(I != null)
					hosts.add(I);
			}
		}
		final Area A = R.getArea();
		if(A != null)
		{
			hosts.add(A);
			for(final Area P : A.getParentsRecurse())
			{
				if((P != null) && (!hosts.contains(P)))
					hosts.add(P);
			}
		}
		return hosts;
	}

	/**
	 * Hands an event to the script engines of each host, as scripts and as
	 * behaviours.
	 * @param hosts the hosts
	 * @param triggerCode the trigger
	 * @param source who it happened to: $n
	 * @param target $t, or null for the host
	 * @param msg $g
	 * @param bits how the trigger line is split, as parseBits does
	 * @param test which trigger lines the event matches
	 */
	public static void fire(final Collection<PhysicalAgent> hosts, final int triggerCode, final MOB source, final Environmental target, final String msg, final String bits, final Predicate<String[]> test)
	{
		if((triggerCode <= 0) || (!isUsed(triggerCode)) || (source == null))
			return;
		for(final PhysicalAgent host : hosts)
		{
			if((host == null) || host.amDestroyed())
				continue;
			for(final Enumeration<ScriptingEngine> s = host.scripts(); s.hasMoreElements();)
			{
				final ScriptingEngine S = s.nextElement();
				if(S != null)
					S.eventTrigger(host, triggerCode, source, target, null, msg, bits, test);
			}
			for(final Enumeration<Behavior> b = host.behaviors(); b.hasMoreElements();)
			{
				final Behavior B = b.nextElement();
				if(B instanceof ScriptingEngine)
					((ScriptingEngine)B).eventTrigger(host, triggerCode, source, target, null, msg, bits, test);
			}
		}
	}

	private static boolean firstWord(final String s, final String word)
	{
		return s.toUpperCase().startsWith(word) && ((s.length() == word.length()) || Character.isWhitespace(s.charAt(word.length())));
	}

	/**
	 * ST2: a character learned, improved, mastered or failed a skill.
	 * SKILL_PROG LEARN|IMPROVE|MASTER|FAIL|ANY ability|* [mask] fires for the
	 * scripts around them; $g is the skill's proficiency now, $t its name.
	 * @param mob the character, a player's
	 * @param A the skill
	 * @param kind LEARN, IMPROVE, MASTER or FAIL
	 */
	public static void skill(final MOB mob, final Ability A, final String kind)
	{
		if((mob == null) || (A == null) || (!isUsed(SKILL_PROG)) || (mob.playerStats() == null) || (mob.location() == null))
			return;
		final Ability mine = mob.fetchAbility(A.ID());
		final int prof = (mine != null) ? mine.proficiency() : A.proficiency();
		fire(hosts(mob.location()), SKILL_PROG, mob, A, "" + prof, "CCcr", t -> skillMatches(t, mob, A, kind));
	}

	/**
	 * ST2: a skill a level brought, as CoffeeLevels tells it: SKILL_PROG
	 * LEARN, unless it came by addAbility, which told it already (a race's or
	 * clan's skills do not).
	 * @param mob the character
	 * @param A the skill
	 */
	public static void levelGrant(final MOB mob, final Ability A)
	{
		if((mob == null) || (A == null) || (!isUsed(SKILL_PROG)))
			return;
		for(final Enumeration<Ability> a = mob.abilities(); a.hasMoreElements();)
		{
			if(a.nextElement() == A)
				return;
		}
		skill(mob, A, "LEARN");
	}

	/**
	 * Whether a SKILL_PROG trigger line fits a skill event.
	 * @param t the trigger line: SKILL_PROG, the kind, the skill, the mask
	 * @param mob the learner
	 * @param A the skill
	 * @param kind what happened
	 * @return true if it fits
	 */
	public static boolean skillMatches(final String[] t, final MOB mob, final Ability A, final String kind)
	{
		if(t.length < 3)
			return false;
		final String k = t[1].toUpperCase().trim();
		if((!k.equals("ANY")) && (!k.equals(kind)))
			return false;
		final String which = t[2].trim();
		if(!(which.equals("*")
			|| which.equalsIgnoreCase("ALL")
			|| which.equalsIgnoreCase(A.ID())
			|| which.equalsIgnoreCase(A.Name())
			|| ((which.length() > 0) && CMLib.english().containsString(A.Name(), which))))
			return false;
		final String mask = (t.length > 3) ? t[3].trim() : "";
		return (mask.length() == 0) || CMLib.masking().maskCheck(mask, mob, true);
	}

	/**
	 * A visit to a room, for a player's character: marked in their player
	 * stats, and if it is their first, DISCOVER_PROG is fired.
	 * @param mob the character
	 * @param R the room
	 * @return true if it was their first visit
	 */
	public static boolean visit(final MOB mob, final Room R)
	{
		final PlayerStats ps = (mob == null) ? null : mob.playerStats();
		if((ps == null) || (R == null))
			return false;
		final boolean newArea = (R.getArea() != null) && (!ps.hasVisited(R.getArea()));
		if(!ps.addRoomVisit(R))
			return false;
		discovered(mob, R, newArea);
		return true;
	}

	/**
	 * ST3: a character's first visit to a room, and maybe its area.
	 * DISCOVER_PROG ROOM|AREA [PARTY] [mask] fires for the scripts around the
	 * room; with PARTY, only if no one else in the character's group had been
	 * there.  $g is the room's ID, $t the room.
	 * @param mob the character
	 * @param R the room
	 * @param newArea true if it is the first room of its area they visit
	 */
	public static void discovered(final MOB mob, final Room R, final boolean newArea)
	{
		if((mob == null) || (R == null) || (!isUsed(DISCOVER_PROG)))
			return;
		fire(hosts(R), DISCOVER_PROG, mob, R, CMLib.map().getExtendedRoomID(R), "CCr", t -> discoverMatches(t, mob, R, newArea));
	}

	/**
	 * Whether a DISCOVER_PROG trigger line fits a first visit.
	 * @param t the trigger line: DISCOVER_PROG, ROOM or AREA, the rest
	 * @param mob the character
	 * @param R the room
	 * @param newArea true if the area is new to them too
	 * @return true if it fits
	 */
	public static boolean discoverMatches(final String[] t, final MOB mob, final Room R, final boolean newArea)
	{
		if(t.length < 2)
			return false;
		final String kind = t[1].toUpperCase().trim();
		final boolean area = kind.equals("AREA");
		if(area)
		{
			if(!newArea)
				return false;
		}
		else
		if(!kind.equals("ROOM"))
			return false;
		String rest = (t.length > 2) ? t[2].trim() : "";
		if(firstWord(rest, "PARTY"))
		{
			rest = rest.substring(5).trim();
			for(final MOB M : mob.getGroupMembers(new HashSet<MOB>()))
			{
				if((M != null) && (M != mob) && (M.playerStats() != null)
				&& (area ? M.playerStats().hasVisited(R.getArea()) : M.playerStats().hasVisited(R)))
					return false;
			}
		}
		return (rest.length() == 0) || CMLib.masking().maskCheck(rest, mob, true);
	}
}
