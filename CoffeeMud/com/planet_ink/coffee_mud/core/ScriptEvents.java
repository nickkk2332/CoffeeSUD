package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.function.Predicate;

import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.Climate;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerStats;
import com.planet_ink.coffee_mud.Common.interfaces.Quest;
import com.planet_ink.coffee_mud.Common.interfaces.TimeClock;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Items.interfaces.Boardable;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Libraries.interfaces.WorldMap;

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
 * (ST3, DISCOVER_PROG), the weather and the moon (ST4), a quest's course
 * (ST5, QUEST_PROG, heard by every host with the trigger), the game's course
 * and its turns (ST6, GAME_PROG and TURN_PROG).  An event at a room reaches the scripts a message in
 * that room would: the room, its items, its mobs and what they carry, and its
 * area and the area's parents.  Each script engine there is handed the event
 * through {@link ScriptingEngine#eventTrigger}, which queues the scripts of
 * that trigger whose trigger line the event matches.  Nothing is done for a
 * trigger no script in the world has.
 */
public final class ScriptEvents
{
	/**
	 * JS11: what a JavaScript-scripted behaviour (JScriptable) offers the
	 * script engine: its functions, for MPCALLFUNC and CALLFUNC, and the
	 * commands typed near its host.
	 */
	public interface JSFunctions
	{
		/**
		 * @param on the host
		 * @param fn the function
		 * @param args its arguments, as one string
		 * @param source who calls
		 * @return what it returned, "" if nothing, or null if there is no such function
		 */
		public String callFunction(PhysicalAgent on, String fn, String args, MOB source);

		/**
		 * @param who who typed it
		 * @param verb its first word
		 * @param rest the rest
		 * @return true if it was handled
		 */
		public boolean command(MOB who, String verb, String rest);

		/**
		 * JS18: calls a handler, if the script defines it.
		 * @param fn the handler
		 * @param args its arguments
		 * @return what it returned, or null
		 */
		public default Object callHandler(final String fn, final Object... args)
		{
			return null;
		}
	}

	/** JS11: whether some JScriptable has an onCommand, so that commands are shown to them */
	public static volatile boolean jsCommands = false;

	/**
	 * JS11: calls a function of a host's JavaScript-scripted behaviours.
	 * @param host the host
	 * @param function the function
	 * @param args its arguments, as one string
	 * @param source who calls
	 * @return what it returned, or null if none has it
	 */
	public static String jsFunction(final PhysicalAgent host, final String function, final String args, final MOB source)
	{
		if(host == null)
			return null;
		for(final java.util.Enumeration<com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior> b = host.behaviors(); b.hasMoreElements();)
		{
			final Object B = b.nextElement();
			if(B instanceof JSFunctions)
			{
				final String r = ((JSFunctions)B).callFunction(host, function, args, source);
				if(r != null)
					return r;
			}
		}
		return null;
	}

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

	private static final Map<Integer, Set<PhysicalAgent>>	registry	= new HashMap<Integer, Set<PhysicalAgent>>();

	/**
	 * Notes a host whose script has a trigger, for the events that fire
	 * anywhere in the world (ST5 and on), heard by every host that has their
	 * trigger.  The host is held weakly: a host gone from the game drops out.
	 * @param host the host
	 * @param triggerCode the trigger
	 */
	public static void register(final PhysicalAgent host, final int triggerCode)
	{
		if((host == null) || (triggerCode <= 0))
			return;
		synchronized(registry)
		{
			Set<PhysicalAgent> hosts = registry.get(Integer.valueOf(triggerCode));
			if(hosts == null)
			{
				hosts = Collections.newSetFromMap(new WeakHashMap<PhysicalAgent, Boolean>());
				registry.put(Integer.valueOf(triggerCode), hosts);
			}
			hosts.add(host);
		}
	}

	/**
	 * The hosts in the game whose scripts have a trigger.
	 * @param triggerCode the trigger
	 * @return the hosts
	 */
	public static List<PhysicalAgent> registered(final int triggerCode)
	{
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		synchronized(registry)
		{
			final Set<PhysicalAgent> set = registry.get(Integer.valueOf(triggerCode));
			if(set != null)
			{
				for(final PhysicalAgent P : set)
				{
					if((P != null) && (!P.amDestroyed()))
						hosts.add(P);
				}
			}
		}
		return hosts;
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
		roomHosts(R, hosts);
		areaHosts(R.getArea(), hosts);
		return hosts;
	}

	/**
	 * The hosts an event in a whole area reaches (ST4): each of its rooms,
	 * their items, their mobs and what those carry, and the area and its
	 * parents.
	 * @param A the area
	 * @return the hosts
	 */
	public static List<PhysicalAgent> hosts(final Area A)
	{
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		if(A == null)
			return hosts;
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R != null)
				roomHosts(R, hosts);
		}
		areaHosts(A, hosts);
		return hosts;
	}

	private static void roomHosts(final Room R, final List<PhysicalAgent> hosts)
	{
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
	}

	private static void areaHosts(final Area A, final List<PhysicalAgent> hosts)
	{
		if(A == null)
			return;
		hosts.add(A);
		for(final Area P : A.getParentsRecurse())
		{
			if((P != null) && (!hosts.contains(P)))
				hosts.add(P);
		}
	}

	/**
	 * Hands a message to the script engines of each host, as a message in
	 * their room would reach them (EXECMSG_PROG), for an event with no one
	 * room (ST4).
	 * @param hosts the hosts
	 * @param msg the message
	 */
	public static void deliver(final Collection<PhysicalAgent> hosts, final CMMsg msg)
	{
		for(final PhysicalAgent host : hosts)
		{
			if((host == null) || host.amDestroyed())
				continue;
			for(final Enumeration<ScriptingEngine> s = host.scripts(); s.hasMoreElements();)
			{
				final ScriptingEngine S = s.nextElement();
				if(S != null)
					S.executeMsg(host, msg);
			}
			for(final Enumeration<Behavior> b = host.behaviors(); b.hasMoreElements();)
			{
				final Behavior B = b.nextElement();
				if(B instanceof ScriptingEngine)
					B.executeMsg(host, msg);
			}
		}
	}

	private static final Map<String, MOB>	sources	= new HashMap<String, MOB>();

	/**
	 * The stand-in that an area-wide event comes from, $n to a script: "the
	 * weather" or "the sky", put in one of the area's rooms.
	 * @param name its name
	 * @param A the area
	 * @return the stand-in, or null if the area has no rooms
	 */
	public static MOB source(final String name, final Area A)
	{
		Room R = null;
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements() && (R == null);)
			R = r.nextElement();
		if(R == null)
			return null;
		synchronized(sources)
		{
			MOB M = sources.get(name);
			if((M == null) || M.amDestroyed())
			{
				M = CMClass.getMOB("StdMOB");
				M.setName(name);
				M.setDisplayText("");
				sources.put(name, M);
			}
			M.setLocation(R);
			return M;
		}
	}

	/**
	 * A stand-in that an event with no one behind it comes from, $n to a
	 * script, in no room: "the quest".
	 * @param name its name
	 * @return the stand-in
	 */
	public static MOB standIn(final String name)
	{
		synchronized(sources)
		{
			MOB M = sources.get(name);
			if((M == null) || M.amDestroyed())
			{
				M = CMClass.getMOB("StdMOB");
				M.setName(name);
				M.setDisplayText("");
				sources.put(name, M);
			}
			return M;
		}
	}

	/** QUEST_PROG's code */
	public static final int	QUEST_PROG	= code("QUEST_PROG");

	/**
	 * ST5: something happened to a quest: QUEST_PROG
	 * START|ACCEPT|STEP|WIN|FAIL|END|ANY quest|* [detail] fires for every host
	 * with that trigger, wherever it is.  $n is the player (the accepter, the
	 * winner, the one who dropped it) or "the quest", $g the quest's name, $0
	 * the detail: the step's number for STEP; for FAIL and END the outcome:
	 * WON, EXPIRED, STOPPED or DROPPED.
	 * @param Q the quest
	 * @param kind START, ACCEPT, STEP, WIN, FAIL or END
	 * @param who the player, or null
	 * @param detail the step or outcome, or ""
	 */
	public static void quest(final Quest Q, final String kind, final MOB who, final String detail)
	{
		JSQuests.event(Q, kind, who, detail); // JS18: the quest's JavaScript, and its objects'
		if((Q != null) && kind.equals("WIN"))
			JSEvents.fire("questwin", who, Q); // JS8
		else
		if((Q != null) && kind.equals("END"))
			JSEvents.fire("questend", Q, who, (detail == null) ? "" : detail); // JS8
		if((Q == null) || (!isUsed(QUEST_PROG)))
			return;
		final List<PhysicalAgent> hosts = registered(QUEST_PROG);
		if(hosts.isEmpty())
			return;
		final String name = Q.name();
		final String d = (detail == null) ? "" : detail;
		fire(hosts, QUEST_PROG, (who != null) ? who : standIn("the quest"), null, name, new Object[] { d }, "CCcr", t -> questMatches(t, kind, name, d));
	}

	/**
	 * Whether a QUEST_PROG trigger line fits a quest event.
	 * @param t the trigger line: QUEST_PROG, the kind, the quest, the detail
	 * @param kind what happened
	 * @param name the quest's name
	 * @param detail the step or outcome
	 * @return true if it fits
	 */
	public static boolean questMatches(final String[] t, final String kind, final String name, final String detail)
	{
		if(t.length < 3)
			return false;
		if((!t[1].equals("ANY")) && (!t[1].equals(kind)))
			return false;
		final String q = t[2].trim();
		if(!(q.equals("*") || q.equalsIgnoreCase("ALL") || q.equalsIgnoreCase(name)))
			return false;
		final String d = (t.length > 3) ? t[3].trim() : "";
		return (d.length() == 0) || d.equalsIgnoreCase(detail);
	}

	/** Q4: QUESTFAIL_PROG's code */
	public static final int	QUESTFAIL_PROG	= code("QUESTFAIL_PROG");

	/**
	 * Q4: a quest failed for a character (MPQUESTFAIL, a KEYPLAYER FAIL
	 * death): QUESTFAIL_PROG quest|* fires for every host with the trigger,
	 * the character's own quest scripts too.  $n is the character, $g the
	 * quest's name, $0 why.
	 * @param Q the quest
	 * @param who the character
	 * @param reason why
	 */
	public static void questFail(final Quest Q, final MOB who, final String reason)
	{
		if((Q == null) || (!isUsed(QUESTFAIL_PROG)))
			return;
		final List<PhysicalAgent> hosts = registered(QUESTFAIL_PROG);
		if(hosts.isEmpty())
			return;
		final String name = Q.name();
		fire(hosts, QUESTFAIL_PROG, (who != null) ? who : standIn("the quest"), null, name, new Object[] { (reason == null) ? "" : reason }, "CCcr",
			t -> (t.length < 2) || t[1].trim().equals("*") || t[1].trim().equalsIgnoreCase("ALL") || t[1].trim().equalsIgnoreCase(name));
	}

	/** Q11: QUESTTIMEOUT_PROG's code */
	public static final int	QUESTTIMEOUT_PROG	= code("QUESTTIMEOUT_PROG");

	/**
	 * Q11: a character's deadline for a quest (MPQTIMER) came:
	 * QUESTTIMEOUT_PROG quest|* runs at once on the character's own scripts
	 * (a bare QUESTTIMEOUT_PROG on those the quest gave them), busy or not.
	 * $n is the character, $g and $0 the quest's name.
	 * @param mob the character
	 * @param name the quest's name
	 * @return true if a script heard it; if none did, the quest fails, EXPIRED
	 */
	public static boolean questTimeout(final MOB mob, final String name)
	{
		if((mob == null) || (name == null) || (!isUsed(QUESTTIMEOUT_PROG)))
			return false;
		final boolean[] heard = { false };
		for(final ScriptingEngine S : engines(mob))
		{
			final String own = S.defaultQuestName();
			S.eventCheck(mob, QUESTTIMEOUT_PROG, mob, null, null, name, new Object[] { name }, "Cr", t -> {
				final String q = (t.length < 2) ? "" : t[1].trim();
				final boolean fits = (q.length() == 0) ? ((own != null) && own.equalsIgnoreCase(name))
					: (q.equals("*") || q.equalsIgnoreCase("ALL") || q.equalsIgnoreCase(name));
				if(fits)
					heard[0] = true;
				return fits;
			});
		}
		return heard[0];
	}

	/** N9: TALK_PROG's code */
	public static final int	TALK_PROG	= code("TALK_PROG");

	/** C20: WAVE_PROG's code */
	public static final int	WAVE_PROG		= code("WAVE_PROG");
	/** C20: ARENAWIN_PROG's code */
	public static final int	ARENAWIN_PROG	= code("ARENAWIN_PROG");
	/** C20: ARENALOSE_PROG's code */
	public static final int	ARENALOSE_PROG	= code("ARENALOSE_PROG");

	/** C20: an arena event on the room's scripts and its NPCs' */
	private static void arena(final Room R, final int code, final MOB who, final String n)
	{
		if((R == null) || (!isUsed(code)))
			return;
		final MOB src = (who != null) ? who : CMLib.map().getFactoryMOB(R);
		try
		{
			final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
			hosts.add(R);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && (!GameDifficulty.playerSide(M)))
					hosts.add(M);
			}
			for(final PhysicalAgent H : hosts)
			{
				for(final ScriptingEngine S : engines(H))
				{
					S.eventCheck(H, code, src, H, null, n, new Object[] { n }, "Cr", t -> {
						final String w = (t.length < 2) ? "" : t[1].trim();
						return (w.length() == 0) || w.equalsIgnoreCase("ALL") || w.equals(n);
					});
				}
			}
		}
		finally
		{
			if(who == null)
				src.destroy();
		}
	}

	/**
	 * C20: a wave of an arena (WaveSpawner) came: WAVE_PROG [n] runs on the
	 * room's scripts and its NPCs', $n one of the party, $0 the wave.
	 * @param R the arena
	 * @param wave the wave, from 1
	 * @param who one of the party, or null
	 */
	public static void wave(final Room R, final int wave, final MOB who)
	{
		arena(R, WAVE_PROG, who, "" + wave);
	}

	/**
	 * C20: the party beat an arena's last wave: ARENAWIN_PROG runs.
	 * @param R the arena
	 * @param who the winner, or null
	 */
	public static void arenaWin(final Room R, final MOB who)
	{
		arena(R, ARENAWIN_PROG, who, "");
	}

	/**
	 * C20: the party left or fell in an arena: ARENALOSE_PROG runs.
	 * @param R the arena
	 * @param who who started it, or null
	 */
	public static void arenaLose(final Room R, final MOB who)
	{
		arena(R, ARENALOSE_PROG, who, "");
	}

	/** I9: SWITCH_PROG's code */
	public static final int	SWITCH_PROG	= code("SWITCH_PROG");

	/**
	 * I9: a switch moved (Prop_Switch): SWITCH_PROG [state|ALL] runs on the
	 * scripts of the thing and of its room, $n who moved it, $0 the state.
	 * @param host the switch, an item or an exit
	 * @param R its room
	 * @param state the state it moved to
	 * @param who who moved it, or null
	 */
	public static void switched(final PhysicalAgent host, final Room R, final String state, final MOB who)
	{
		if(!isUsed(SWITCH_PROG))
			return;
		final MOB src = (who != null) ? who : CMLib.map().getFactoryMOB(R);
		try
		{
			final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
			if(host != null)
				hosts.add(host);
			if((R != null) && (R != host))
				hosts.add(R);
			for(final PhysicalAgent H : hosts)
			{
				for(final ScriptingEngine S : engines(H))
				{
					S.eventCheck(H, SWITCH_PROG, src, host, (host instanceof Item) ? (Item)host : null, state, new Object[] { state }, "Cr", t -> {
						final String w = (t.length < 2) ? "" : t[1].trim();
						return (w.length() == 0) || w.equalsIgnoreCase("ALL") || w.equalsIgnoreCase(state);
					});
				}
			}
		}
		finally
		{
			if(who == null)
				src.destroy();
		}
	}

	/** C8: PARTYDEFEAT_PROG's code */
	public static final int	PARTYDEFEAT_PROG	= code("PARTYDEFEAT_PROG");

	/**
	 * C8: the whole party is down or dead: PARTYDEFEAT_PROG runs on the scripts
	 * of the room and its area that have one, $n the last to fall.
	 * @param R the room
	 * @param who the last to fall
	 * @return true if a script there has one
	 */
	public static boolean partyDefeat(final Room R, final MOB who)
	{
		if((R == null) || (who == null))
			return false;
		boolean any = false;
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		hosts.add(R);
		if(R.getArea() != null)
			hosts.add(R.getArea());
		for(final PhysicalAgent H : hosts)
		{
			for(final ScriptingEngine S : engines(H))
			{
				if(!S.triggerLines(H, PARTYDEFEAT_PROG, "C").isEmpty())
				{
					any = true;
					S.eventCheck(H, PARTYDEFEAT_PROG, who, H, null, "", null, "C", t -> true);
				}
			}
		}
		return any;
	}

	/** C23: AMBUSH_PROG's code */
	public static final int	AMBUSH_PROG		= code("AMBUSH_PROG");

	/**
	 * C23: an ambush (Prop_Ambush) sprang: AMBUSH_PROG [outcome] runs on the
	 * room's scripts and its NPCs' (the ambushers'), $0 SURPRISED, SPOTTED or
	 * AVOIDED, $n the one who came in.
	 * @param R the room
	 * @param outcome SURPRISED, SPOTTED or AVOIDED
	 * @param who the one who came in, or null
	 */
	public static void ambush(final Room R, final String outcome, final MOB who)
	{
		arena(R, AMBUSH_PROG, who, outcome);
	}

	/** C3: PHASE_PROG's code */
	public static final int	PHASE_PROG	= code("PHASE_PROG");

	/**
	 * C3: a boss entered a phase (BossPhases): PHASE_PROG [n] runs on its
	 * scripts, $g and $0 the phase.
	 * @param npc the boss
	 * @param phase the phase it entered
	 */
	public static void phase(final MOB npc, final int phase)
	{
		if((npc == null) || (!isUsed(PHASE_PROG)))
			return;
		final String n = "" + phase;
		final MOB foe = (npc.getVictim() != null) ? npc.getVictim() : npc;
		for(final ScriptingEngine S : engines(npc))
		{
			S.eventCheck(npc, PHASE_PROG, foe, npc, null, n, new Object[] { n }, "Cr", t -> {
				final String w = (t.length < 2) ? "" : t[1].trim();
				return (w.length() == 0) || w.equalsIgnoreCase("ALL") || w.equals(n);
			});
		}
	}

	/** C17: YIELD_PROG's code */
	public static final int	YIELD_PROG	= code("YIELD_PROG");
	/** C17: DEFEAT_PROG's code */
	public static final int	DEFEAT_PROG	= code("DEFEAT_PROG");

	/**
	 * C17: an NPC yielded (Prop_Essential MODE=YIELD): YIELD_PROG runs on
	 * its scripts, $n who beat it.
	 * @param npc the NPC
	 * @param by who beat it, or null
	 */
	public static void yielded(final MOB npc, final MOB by)
	{
		if((npc == null) || (!isUsed(YIELD_PROG)))
			return;
		for(final ScriptingEngine S : engines(npc))
			S.eventCheck(npc, YIELD_PROG, (by == null) ? npc : by, npc, null, "", null, "C", t -> true);
	}

	/**
	 * C17: an NPC was knocked out rather than killed (Prop_Essential
	 * MODE=KNOCKOUT): DEFEAT_PROG runs on its scripts, $n who beat it.
	 * @param npc the NPC
	 * @param by who beat it, or null
	 */
	public static void defeated(final MOB npc, final MOB by)
	{
		if((npc == null) || (!isUsed(DEFEAT_PROG)))
			return;
		for(final ScriptingEngine S : engines(npc))
			S.eventCheck(npc, DEFEAT_PROG, (by == null) ? npc : by, npc, null, "", null, "C", t -> true);
	}

	/** C2: SURRENDER_PROG's code */
	public static final int	SURRENDER_PROG	= code("SURRENDER_PROG");

	/**
	 * C2: an NPC surrendered (Morale SURRENDERAT=): SURRENDER_PROG runs on
	 * its scripts, $n one it fought.
	 * @param npc the NPC
	 * @param to one it fought
	 */
	public static void surrender(final MOB npc, final MOB to)
	{
		if((npc == null) || (!isUsed(SURRENDER_PROG)))
			return;
		for(final ScriptingEngine S : engines(npc))
			S.eventCheck(npc, SURRENDER_PROG, (to == null) ? npc : to, npc, null, "", null, "C", t -> true);
	}

	/** C21: RESET_PROG's code */
	public static final int	RESET_PROG	= code("RESET_PROG");

	/**
	 * C21: an NPC was reset after its foes left (EncounterReset): RESET_PROG
	 * runs on its scripts, $n and $i itself.
	 * @param npc the NPC
	 */
	public static void encounterReset(final MOB npc)
	{
		if((npc == null) || (!isUsed(RESET_PROG)))
			return;
		for(final ScriptingEngine S : engines(npc))
			S.eventCheck(npc, RESET_PROG, npc, npc, null, "", null, "C", t -> true);
	}

	/**
	 * N9: TALK npc [ABOUT topic]: TALK_PROG [topic words|ALL] runs at once on
	 * the NPC's scripts, $n the speaker, $g and $0 the topic.
	 * @param npc the NPC
	 * @param who the speaker
	 * @param topic the topic, or ""
	 * @return true if a script heard it
	 */
	public static boolean talk(final MOB npc, final MOB who, final String topic)
	{
		if((npc == null) || (who == null) || (!isUsed(TALK_PROG)))
			return false;
		final String t = (topic == null) ? "" : topic.trim();
		final boolean[] heard = { false };
		for(final ScriptingEngine S : engines(npc))
		{
			S.eventCheck(npc, TALK_PROG, who, npc, null, t, new Object[] { t }, "Cr", line -> {
				final boolean fits = talkFits((line.length < 2) ? "" : line[1], t);
				if(fits)
					heard[0] = true;
				return fits;
			});
		}
		return heard[0];
	}

	/**
	 * N9: whether a TALK_PROG line fits a topic: none, ALL or * any talk;
	 * P phrase the phrase in the topic; else any of its words.
	 * @param words the trigger line after TALK_PROG
	 * @param topic the topic, or ""
	 * @return true if it fits
	 */
	public static boolean talkFits(final String words, final String topic)
	{
		final String w = (words == null) ? "" : words.trim().toUpperCase();
		if((w.length() == 0) || w.equals("ALL") || w.equals("*"))
			return true;
		if((topic == null) || (topic.trim().length() == 0))
			return false;
		final String t = " " + topic.toUpperCase().replaceAll("[^A-Z0-9]+", " ").trim() + " ";
		if(w.startsWith("P "))
			return t.indexOf(" " + w.substring(2).replaceAll("[^A-Z0-9]+", " ").trim() + " ") >= 0;
		for(final String word : w.split("[\\s,]+"))
		{
			if((word.length() > 0) && (t.indexOf(" " + word + " ") >= 0))
				return true;
		}
		return false;
	}

	/** GAME_PROG's code */
	public static final int	GAME_PROG	= code("GAME_PROG");
	/** TURN_PROG's code */
	public static final int	TURN_PROG	= code("TURN_PROG");

	/** what GAME_PROG tells apart */
	public static final String[]	GAME_EVENTS	= { "NEWGAME", "RESTORE", "SAVE", "NEWCHAR", "SWITCHTO", "SWITCHFROM", "PAUSE", "RESUME", "TIMEMODE", "ANY" };

	private static volatile long	turns	= 0;

	/**
	 * The turns since the game began, TURN_PROG's $g, kept in the save.
	 * @return the count
	 */
	public static long turns()
	{
		return turns;
	}

	/**
	 * Sets the turn count, from a save, or 0 for a new game.
	 * @param n the count
	 */
	public static void setTurns(final long n)
	{
		turns = Math.max(0, n);
	}

	/**
	 * ST6: the game's course: GAME_PROG NEWGAME|RESTORE|SAVE|NEWCHAR|SWITCHTO|
	 * SWITCHFROM|PAUSE|RESUME|TIMEMODE|ANY [mask] fires for every host with
	 * the trigger, and the world JavaScript's hook (world/js/init.js).  $n is
	 * the character, $g the save's slot for SAVE and RESTORE and otherwise the
	 * event, $0 the event, $1 the detail.
	 * @param kind the event
	 * @param mob the character, or null
	 * @param detail the slot, the time mode, or ""
	 */
	public static void game(final String kind, final MOB mob, final String detail)
	{
		final String d = (detail == null) ? "" : detail;
		if(kind.equals("NEWGAME"))
		{
			JSStore.newGame(); // JS7: world/js/state.json
			QuestRecords.newGame(); // Q4
		}
		JSEvents.fire(kind.toLowerCase(), mob, d); // JS6: cm.on
		switch(kind)
		{
		case "NEWGAME":
			WorldJS.call("onNewGame", mob);
			break;
		case "RESTORE":
			WorldJS.call("onRestore", mob, d);
			break;
		default:
			break;
		}
		if(!isUsed(GAME_PROG))
			return;
		final List<PhysicalAgent> hosts = registered(GAME_PROG);
		if(hosts.isEmpty())
			return;
		final MOB src = (mob != null) ? mob : standIn("the game");
		final String g = (kind.equals("SAVE") || kind.equals("RESTORE")) ? d : kind;
		fire(hosts, GAME_PROG, src, null, g, new Object[] { kind, d }, "CCr", t -> gameMatches(t, kind, mob));
	}

	/**
	 * ST6: the game is about to save: onBeforeSave(slot) in the world
	 * JavaScript, then GAME_PROG SAVE, run at once, so that what they set is
	 * saved; either may refuse the save, the JavaScript by returning false and
	 * a script by RETURN CANCEL (saying why itself).
	 * @param mob the player
	 * @param slot the save's slot
	 * @return true to save, false if refused
	 */
	public static boolean gameCheck(final MOB mob, final String slot)
	{
		if(Boolean.FALSE.equals(WorldJS.call("onBeforeSave", slot)))
			return false;
		if(!JSEvents.fire("save", mob, slot)) // JS6: cm.on('save', fn) returning false refuses
			return false;
		if(!isUsed(GAME_PROG))
			return true;
		final MOB src = (mob != null) ? mob : standIn("the game");
		for(final PhysicalAgent host : registered(GAME_PROG))
		{
			for(final ScriptingEngine S : engines(host))
			{
				if(!S.eventCheck(host, GAME_PROG, src, null, null, slot, new Object[] { "SAVE", slot }, "CCr", t -> gameMatches(t, "SAVE", mob)))
					return false;
			}
		}
		return true;
	}

	/**
	 * Whether a GAME_PROG trigger line fits an event of the game.
	 * @param t the trigger line: GAME_PROG, the event, the mask
	 * @param kind the event
	 * @param mob the character, or null
	 * @return true if it fits
	 */
	public static boolean gameMatches(final String[] t, final String kind, final MOB mob)
	{
		if(t.length < 2)
			return false;
		if((!t[1].equals("ANY")) && (!t[1].equals(kind)))
			return false;
		final String mask = (t.length > 2) ? t[2].trim() : "";
		return (mask.length() == 0) || ((mob != null) && CMLib.masking().maskCheck(mask, mob, true));
	}

	/**
	 * ST6: a turn is over: a party's turn in turn-based time, or a command
	 * that took time in real time.  The turn count goes up, and TURN_PROG
	 * [every N] [mask] fires for the hosts with the trigger in the leader's
	 * area, the area and its parents, and the world host.  $n is the leader,
	 * $g the turn count.
	 * @param leader who took the turn
	 */
	public static void turn(final MOB leader)
	{
		final long n = ++turns;
		JSEvents.fire("turn", leader, Long.valueOf(n)); // JS8
		if((leader == null) || (!isUsed(TURN_PROG)))
			return;
		final Room R = leader.location();
		final Area A = (R == null) ? null : R.getArea();
		final List<PhysicalAgent> areaChain = new ArrayList<PhysicalAgent>();
		areaHosts(A, areaChain);
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		for(final PhysicalAgent P : registered(TURN_PROG))
		{
			if((P == WorldHost.host()) || areaChain.contains(P))
				hosts.add(P);
			else
			if(!(P instanceof Area))
			{
				final Room PR = CMLib.map().roomLocation(P);
				if((PR != null) && (PR.getArea() == A))
					hosts.add(P);
			}
		}
		fire(hosts, TURN_PROG, leader, null, "" + n, null, "Ccr", t -> turnMatches(t, leader, n));
	}

	/**
	 * Whether a TURN_PROG trigger line fits a turn.
	 * @param t the trigger line: TURN_PROG, every how many turns, the mask
	 * @param leader who took the turn
	 * @param n the turn count
	 * @return true if it fits
	 */
	public static boolean turnMatches(final String[] t, final MOB leader, final long n)
	{
		final String first = (t.length > 1) ? t[1].trim() : "";
		final String rest = (t.length > 2) ? t[2].trim() : "";
		long every = 1;
		String mask;
		if(CMath.isLong(first))
		{
			every = Math.max(1, CMath.s_long(first));
			mask = rest;
		}
		else
			mask = (first + " " + rest).trim();
		return ((n % every) == 0) && ((mask.length() == 0) || CMLib.masking().maskCheck(mask, leader, true));
	}

	/**
	 * @param host a scripted thing
	 * @return its scripts: those it has and its behaviours that are scripts
	 */
	public static List<ScriptingEngine> engines(final PhysicalAgent host)
	{
		final List<ScriptingEngine> list = new ArrayList<ScriptingEngine>();
		if((host == null) || host.amDestroyed())
			return list;
		for(final Enumeration<ScriptingEngine> s = host.scripts(); s.hasMoreElements();)
		{
			final ScriptingEngine S = s.nextElement();
			if(S != null)
				list.add(S);
		}
		for(final Enumeration<Behavior> b = host.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B instanceof ScriptingEngine)
				list.add((ScriptingEngine)B);
		}
		return list;
	}

	/** VERB_PROG's code */
	public static final int	VERB_PROG	= code("VERB_PROG");

	/**
	 * What is within a character's reach for a verb (ST7): what they carry,
	 * the room's items and other mobs, its exits, the room, and its area.
	 * @param mob the character
	 * @return the objects, nearest first
	 */
	public static List<PhysicalAgent> reach(final MOB mob)
	{
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		final Room R = mob.location();
		if(R == null)
			return hosts;
		for(final Enumeration<Item> i = mob.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if(I != null)
				hosts.add(I);
		}
		for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if(I != null)
				hosts.add(I);
		}
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (M != mob))
				hosts.add(M);
		}
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			final Exit E = R.getExitInDir(d);
			if((E != null) && (!hosts.contains(E)))
				hosts.add(E);
		}
		hosts.add(R);
		if(R.getArea() != null)
			hosts.add(R.getArea());
		return hosts;
	}

	/**
	 * ST7: a character's command, before the game looks for a command, skill
	 * or social with its first word: VERB_PROG verb[,alias...] [P phrase |
	 * keywords] on something within reach, or Prop_Verbs on it, takes the
	 * line.  A script that returns CONTINUE hands it back.
	 * @param mob the character
	 * @param commands the command's words
	 * @return true if a verb took the line
	 */
	public static boolean verb(final MOB mob, final List<String> commands)
	{
		if((mob == null) || (commands == null) || (commands.size() == 0) || (mob.location() == null))
			return false;
		final String verb = commands.get(0).trim();
		if((verb.length() == 0) || (!Character.isLetter(verb.charAt(0))))
			return false;
		if(jsCommands) // JS11: a JScriptable's onCommand near the one who typed it
		{
			final String line = CMParms.combine(commands, 1).trim();
			for(final PhysicalAgent host : reach(mob))
			{
				for(final java.util.Enumeration<com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior> b = host.behaviors(); b.hasMoreElements();)
				{
					final Object B = b.nextElement();
					if((B instanceof JSFunctions) && ((JSFunctions)B).command(mob, verb, line))
						return true;
				}
			}
		}
		// JS20: cm.verb's phrases near what is within reach
		if(JSVerbs.any() && JSVerbs.handle(mob, verb, CMParms.combine(commands, 1).trim(), reach(mob)))
			return true;
		final boolean scripts = isUsed(VERB_PROG);
		if((!scripts) && (!propVerbsSeen))
			return false;
		final String rest = CMParms.combine(commands, 1).trim();
		for(final PhysicalAgent host : reach(mob))
		{
			if(scripts)
			{
				for(final ScriptingEngine S : engines(host))
				{
					final String resp = S.eventRun(host, VERB_PROG, mob, host, null, rest, null, "CCr", t -> verbMatches(t, verb, rest, host));
					if((resp != null) && (!resp.trim().equalsIgnoreCase("CONTINUE")))
						return true;
				}
			}
			if(propVerb(mob, host, verb, rest))
				return true;
			// I9: a puzzle property on it (Prop_Switch and its kin)
			for(final Enumeration<Ability> a = host.effects(); a.hasMoreElements();)
			{
				final Ability A = a.nextElement();
				if((A instanceof VerbTaker) && ((VerbTaker)A).takeVerb(mob, host, verb, rest))
					return true;
			}
		}
		return false;
	}

	private static volatile boolean	propVerbsSeen	= false;

	/**
	 * Notes that a Prop_Verbs is in the game, so commands are looked at for
	 * its verbs from now on.
	 */
	public static void propVerbsSeen()
	{
		propVerbsSeen = true;
	}

	private static boolean hasVerb(final String verbs, final String verb)
	{
		for(final String v : verbs.split(","))
		{
			if(v.trim().equalsIgnoreCase(verb))
				return true;
		}
		return false;
	}

	/**
	 * Whether the rest of a line names an object.
	 * @param host the object
	 * @param rest the words after the verb
	 * @return true if they name it, or it is a room or an area, which need no naming
	 */
	public static boolean names(final PhysicalAgent host, final String rest)
	{
		if((host instanceof Room) || (host instanceof Area))
			return true;
		if(rest.length() == 0)
			return false;
		return CMLib.english().containsString(host.name(), rest)
			|| CMLib.english().containsString(host.Name(), rest)
			|| ((host instanceof Exit) && CMLib.english().containsString(((Exit)host).doorName(), rest));
	}

	/**
	 * Whether a VERB_PROG trigger line fits a command.
	 * @param t the trigger line: VERB_PROG, the verbs, then P and a phrase, or keywords
	 * @param verb the command's first word
	 * @param rest the rest of the line
	 * @param host the object the script is on
	 * @return true if it fits
	 */
	public static boolean verbMatches(final String[] t, final String verb, final String rest, final PhysicalAgent host)
	{
		if((t.length < 2) || (!hasVerb(t[1], verb)))
			return false;
		final String kw = (t.length > 2) ? t[2].trim() : "";
		if(kw.length() == 0)
			return names(host, rest);
		if(firstWord(kw, "P"))
		{
			String phrase = kw.substring(1).trim();
			if((phrase.length() > 1) && phrase.startsWith("'") && phrase.endsWith("'"))
				phrase = phrase.substring(1, phrase.length() - 1);
			return (phrase.length() == 0) ? names(host, rest) : CMLib.english().containsString(rest, phrase);
		}
		for(final String k : CMParms.parse(kw))
		{
			if(CMLib.english().containsString(rest, k))
				return true;
		}
		return false;
	}

	/**
	 * Prop_Verbs on an object (ST7): VERBS="turn,rotate" MSG="what the one
	 * who does it sees" OTHERS="what the others see", for a verb with no
	 * script; the rest of the line must name the object, unless it is a room.
	 * @param mob the character
	 * @param host the object
	 * @param verb the command's first word
	 * @param rest the rest of the line
	 * @return true if it took the line
	 */
	public static boolean propVerb(final MOB mob, final PhysicalAgent host, final String verb, final String rest)
	{
		final Ability A = host.fetchEffect("Prop_Verbs");
		if(A == null)
			return false;
		final String parms = A.text();
		if((!hasVerb(CMParms.getParmStr(parms, "VERBS", ""), verb)) || (!names(host, rest)))
			return false;
		final String you = CMParms.getParmStr(parms, "MSG", "");
		final String others = CMParms.getParmStr(parms, "OTHERS", "");
		final Room R = mob.location();
		final CMMsg msg = CMClass.getMsg(mob, host, null,
			CMMsg.MSG_OK_VISUAL, (you.length() > 0) ? you : null,
			CMMsg.MSG_OK_VISUAL, null,
			CMMsg.MSG_OK_VISUAL, (others.length() > 0) ? others : null);
		if(R.okMessage(mob, msg))
			R.send(mob, msg);
		return true;
	}

	/**
	 * LIST VERBS (ST7): the verbs of what is in a room, its mobs and their
	 * things, and its exits, from VERB_PROG and Prop_Verbs.
	 * @param R the room
	 * @return the list
	 */
	public static String listVerbs(final Room R)
	{
		final StringBuilder str = new StringBuilder();
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		roomHosts(R, hosts);
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			final Exit E = R.getExitInDir(d);
			if((E != null) && (!hosts.contains(E)))
				hosts.add(E);
		}
		if(R.getArea() != null)
			hosts.add(R.getArea());
		for(final PhysicalAgent host : hosts)
		{
			final List<String> verbs = new ArrayList<String>();
			for(final ScriptingEngine S : engines(host))
			{
				for(final String line : CMParms.parseAny(S.getScript(), '\n', true))
				{
					final String l = line.trim();
					if(l.toUpperCase().startsWith("VERB_PROG "))
						verbs.add(l.substring(10).trim());
				}
			}
			final Ability A = host.fetchEffect("Prop_Verbs");
			if(A != null)
				verbs.add(CMParms.getParmStr(A.text(), "VERBS", "") + " (Prop_Verbs)");
			if(verbs.size() > 0)
				str.append(host.name()).append(": ").append(String.join("; ", verbs)).append("\n\r");
		}
		return (str.length() == 0) ? "No verbs here." : ("Verbs here:\n\r" + str.toString());
	}

	/** PARTY_PROG's code */
	public static final int	PARTY_PROG	= code("PARTY_PROG");

	/**
	 * ST10: the party changed: PARTY_PROG JOIN|LEAVE|REST|LEAD|SWITCH|ANY
	 * [mask on the member] fires for every host with the trigger, and
	 * onPartyChange(leader) in the world JavaScript.  $n is the member, $t
	 * the leader, $g the event.
	 * @param kind JOIN, LEAVE, REST, LEAD or SWITCH
	 * @param member the member
	 * @param leader the party's leader
	 */
	public static void party(final String kind, final MOB member, final MOB leader)
	{
		if(member == null)
			return;
		WorldJS.call("onPartyChange", (leader != null) ? leader : member);
		if(!isUsed(PARTY_PROG))
			return;
		final List<PhysicalAgent> hosts = registered(PARTY_PROG);
		if(hosts.isEmpty())
			return;
		fire(hosts, PARTY_PROG, member, (leader != null) ? leader : member, kind, new Object[] { kind }, "CCr", t -> partyMatches(t, kind, member));
	}

	/**
	 * Whether a PARTY_PROG trigger line fits a change of the party.
	 * @param t the trigger line: PARTY_PROG, the event, the mask
	 * @param kind the event
	 * @param member the member
	 * @return true if it fits
	 */
	public static boolean partyMatches(final String[] t, final String kind, final MOB member)
	{
		if(t.length < 2)
			return false;
		if((!t[1].equals("ANY")) && (!t[1].equals(kind)))
			return false;
		final String mask = (t.length > 2) ? t[2].trim() : "";
		return (mask.length() == 0) || CMLib.masking().maskCheck(mask, member, true);
	}

	/**
	 * The group a character is in (ST10's functions): the party's
	 * characters, and the followers.
	 * @param M the character
	 * @return the group, the leader first
	 */
	public static List<MOB> group(final MOB M)
	{
		final List<MOB> list = new ArrayList<MOB>();
		if(M == null)
			return list;
		final MOB leader = M.getGroupLeader();
		if(leader != null)
			list.add(leader);
		for(final MOB G : M.getGroupMembers(new LinkedHashSet<MOB>()))
		{
			if((G != null) && (!list.contains(G)))
				list.add(G);
		}
		return list;
	}

	/** SEASON_PROG's code */
	public static final int	SEASON_PROG		= code("SEASON_PROG");
	/** MONTH_PROG's code */
	public static final int	MONTH_PROG		= code("MONTH_PROG");
	/** YEAR_PROG's code */
	public static final int	YEAR_PROG		= code("YEAR_PROG");
	/** WEEKDAY_PROG's code */
	public static final int	WEEKDAY_PROG	= code("WEEKDAY_PROG");

	/**
	 * A clock's weekday's name, as TIME tells it.
	 * @param C the clock
	 * @return the name, or "" if the clock has no weeks
	 */
	public static String weekday(final TimeClock C)
	{
		if((C == null) || (C.getDaysInWeek() <= 0))
			return "";
		long x = ((long)C.getYear()) * ((long)C.getMonthsInYear()) * C.getDaysInMonth();
		x = x + ((long)C.getMonth()) * ((long)C.getDaysInMonth());
		x = x + C.getDayOfMonth();
		final int i = (int)(x % C.getDaysInWeek());
		final String[] names = C.getWeekNames();
		return ((i >= 0) && (i < names.length)) ? names[i] : "";
	}

	/**
	 * Whether a number is in a range as TIME_PROG and DAY_PROG take it (ST11):
	 * a number, a range "6-18", or a range with a step "6-18/3".
	 * @param spec the range
	 * @param n the number
	 * @return true if it is in it
	 */
	public static boolean rangeMatches(final String spec, final int n)
	{
		String s = spec.trim();
		int step = 1;
		final int slash = s.indexOf('/');
		if(slash > 0)
		{
			step = Math.max(1, CMath.s_int(s.substring(slash + 1).trim()));
			s = s.substring(0, slash).trim();
		}
		final int dash = s.indexOf('-', 1);
		if(dash > 0)
		{
			final int a = CMath.s_int(s.substring(0, dash).trim());
			final int b = CMath.s_int(s.substring(dash + 1).trim());
			return (n >= a) && (n <= b) && (((n - a) % step) == 0);
		}
		return CMath.isInteger(s) && (CMath.s_int(s) == n);
	}

	/**
	 * Whether an hour fits a TIME_PROG argument (ST11): a range as
	 * {@link #rangeMatches} takes it, or DAWN, DAY, DUSK or NIGHT, the hour
	 * the clock's time of day begins.
	 * @param spec the argument
	 * @param hour the hour
	 * @param C the area's clock
	 * @return true if it fits
	 */
	public static boolean hourMatches(final String spec, final int hour, final TimeClock C)
	{
		final String s = spec.trim().toUpperCase();
		for(final TimeClock.TimeOfDay tod : TimeClock.TimeOfDay.values())
		{
			if(tod.name().equals(s))
				return (C != null) && (C.getDawnToDusk()[tod.ordinal()] == hour);
		}
		return rangeMatches(s, hour);
	}

	/**
	 * The hosts with a trigger in areas on a clock, and the world host.
	 * @param triggerCode the trigger
	 * @param C the clock
	 * @return the hosts
	 */
	public static List<PhysicalAgent> onClock(final int triggerCode, final TimeClock C)
	{
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		for(final PhysicalAgent P : registered(triggerCode))
		{
			if(P == WorldHost.host())
				hosts.add(P);
			else
			{
				final Area A = (P instanceof Area) ? (Area)P : ((CMLib.map().roomLocation(P) == null) ? null : CMLib.map().roomLocation(P).getArea());
				if((A != null) && (A.getTimeObj() == C))
					hosts.add(P);
			}
		}
		return hosts;
	}

	private static boolean anyOf(final String list, final String[] values)
	{
		for(final String w : CMParms.parseAny(list.replace(',', ' '), ' ', true))
		{
			if(w.equalsIgnoreCase("ANY"))
				return true;
			for(final String v : values)
			{
				if((v != null) && (v.length() > 0) && w.equalsIgnoreCase(v))
					return true;
			}
		}
		return false;
	}

	private static void calendar(final int code, final TimeClock C, final String now, final String before, final String[] values)
	{
		if(!isUsed(code))
			return;
		final List<PhysicalAgent> hosts = onClock(code, C);
		if(hosts.isEmpty())
			return;
		fire(hosts, code, standIn("the sky"), null, now, new Object[] { before }, "Cr", t -> (t.length > 1) && anyOf(t[1], values));
	}

	/**
	 * ST11: a clock's new day, as far as the calendar goes: SEASON_PROG
	 * season|ANY, MONTH_PROG numbers or names|ANY, YEAR_PROG year|ANY, each on
	 * its change, and WEEKDAY_PROG names|ANY each day, for hosts in areas on
	 * the clock and the world's scripts.  $g is the new one, $0 the one before.
	 * @param C the clock
	 * @param oldMonth the month before
	 * @param oldYear the year before
	 * @param oldSeason the season before
	 * @param oldWeekday the weekday before
	 */
	public static void calendarChanged(final TimeClock C, final int oldMonth, final int oldYear, final TimeClock.Season oldSeason, final String oldWeekday)
	{
		if(C == null)
			return;
		final TimeClock.Season season = C.getSeasonCode();
		if(season != oldSeason)
			calendar(SEASON_PROG, C, season.name(), (oldSeason == null) ? "" : oldSeason.name(), new String[] { season.name() });
		if(C.getMonth() != oldMonth)
		{
			final String[] names = C.getMonthNames();
			final int m = C.getMonth();
			final String name = ((m >= 0) && (m < names.length)) ? names[m] : "";
			calendar(MONTH_PROG, C, "" + m, "" + oldMonth, new String[] { "" + m, name });
		}
		if(C.getYear() != oldYear)
			calendar(YEAR_PROG, C, "" + C.getYear(), "" + oldYear, new String[] { "" + C.getYear() });
		final String day = weekday(C);
		if(day.length() > 0)
			calendar(WEEKDAY_PROG, C, day, (oldWeekday == null) ? "" : oldWeekday, new String[] { day });
	}

	/** CLAN_PROG's code */
	public static final int	CLAN_PROG	= code("CLAN_PROG");

	/**
	 * ST9: something happened to a clan: CLAN_PROG JOIN|LEAVE|PROMOTE|DEMOTE|
	 * EXILE|RELATION|CONQUER|LOSEAREA|LEVEL|ALL [clan|ALL] fires for hosts
	 * with the trigger in the member's room, the clan's recall room, or the
	 * area won or lost, and the world's scripts.  $n is the member (or a
	 * stand-in named for the clan), $t the area for CONQUER and LOSEAREA, $g
	 * the new rank, relation or level, $0 the event, $1 the clan, $2 the
	 * detail, $3 the other clan or the area.
	 * @param kind the event
	 * @param clanID the clan
	 * @param member the member, or null
	 * @param area the area won or lost, or null
	 * @param detail the new rank, relation or level
	 * @param other the other clan's or the area's name, or ""
	 */
	public static void clan(final String kind, final String clanID, final MOB member, final Area area, final String detail, final String other)
	{
		if((clanID == null) || (!isUsed(CLAN_PROG)))
			return;
		final com.planet_ink.coffee_mud.Common.interfaces.Clan C = CMLib.clans().getClanAnyHost(clanID);
		final Room recall = (C == null) ? null : CMLib.map().getRoom(C.getRecall());
		final Room memberRoom = (member == null) ? null : member.location();
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		for(final PhysicalAgent P : registered(CLAN_PROG))
		{
			final Room PR = (P instanceof Area) ? null : CMLib.map().roomLocation(P);
			if((P == WorldHost.host())
			|| ((PR != null) && ((PR == memberRoom) || (PR == recall)))
			|| ((area != null) && ((P == area) || ((PR != null) && (PR.getArea() == area)))))
				hosts.add(P);
		}
		if(hosts.isEmpty())
			return;
		final String name = (C == null) ? clanID : C.clanID();
		final String d = (detail == null) ? "" : detail;
		final MOB src = (member != null) ? member : standIn(name);
		fire(hosts, CLAN_PROG, src, area, d, new Object[] { kind, name, d, (other == null) ? "" : other }, "CCr", t -> clanMatches(t, kind, C, name));
	}

	/**
	 * Whether a CLAN_PROG trigger line fits a clan event.
	 * @param t the trigger line: CLAN_PROG, the event, the clan
	 * @param kind the event
	 * @param C the clan, or null
	 * @param name its ID
	 * @return true if it fits
	 */
	public static boolean clanMatches(final String[] t, final String kind, final com.planet_ink.coffee_mud.Common.interfaces.Clan C, final String name)
	{
		if(t.length < 2)
			return false;
		if((!t[1].equals("ALL")) && (!t[1].equals("ANY")) && (!t[1].equals(kind)))
			return false;
		final String which = (t.length > 2) ? t[2].trim() : "";
		return (which.length() == 0) || which.equalsIgnoreCase("ALL") || which.equalsIgnoreCase(name)
			|| ((C != null) && which.equalsIgnoreCase(C.getName()));
	}

	/**
	 * ST12: an event for the hosts around it, and for every other host whose
	 * trigger line starts with @GLOBAL, wherever it is.
	 * @param local the hosts around the event
	 * @param triggerCode the trigger
	 * @param source $n
	 * @param target $t, or null for the host
	 * @param msg $g
	 * @param args $0 and on, or null
	 * @param bits how the trigger line is split, @GLOBAL left out
	 * @param test which trigger lines the event fits
	 */
	public static void fireAround(final List<PhysicalAgent> local, final int triggerCode, final MOB source, final Environmental target, final String msg, final Object[] args, final String bits, final Predicate<String[]> test)
	{
		if((triggerCode <= 0) || (!isUsed(triggerCode)))
			return;
		fire(local, triggerCode, source, target, msg, args, bits, test);
		final List<PhysicalAgent> others = new ArrayList<PhysicalAgent>();
		for(final PhysicalAgent P : registered(triggerCode))
		{
			if(!local.contains(P))
				others.add(P);
		}
		if(others.size() > 0)
			fire(others, triggerCode, source, target, msg, args, "@" + bits, test);
	}

	/**
	 * Whether a CRAFT_PROG trigger line fits a thing made (ST13).
	 * @param t the trigger line: CRAFT_PROG, the skill, the item words, RUINED or SUCCESS
	 * @param A the skill
	 * @param I the thing made
	 * @return true if it fits
	 */
	public static boolean craftMatches(final String[] t, final Ability A, final Item I)
	{
		if((t.length < 3) || (!skillNamed(t[1], A)) || (!wordsName(t[2], I)))
			return false;
		final String how = (t.length > 3) ? t[3].trim().toUpperCase() : "";
		final boolean ruined = I.ID().toUpperCase().indexOf("RUINED") >= 0;
		if(how.equals("RUINED"))
			return ruined;
		if(how.equals("SUCCESS"))
			return !ruined;
		return true;
	}

	/**
	 * Whether a RECIPE_PROG trigger line fits a recipe learned (ST13).
	 * @param t the trigger line: RECIPE_PROG, the skill, the recipe words
	 * @param A the skill
	 * @param I the thing the recipe makes
	 * @return true if it fits
	 */
	public static boolean recipeMatches(final String[] t, final Ability A, final Item I)
	{
		return (t.length > 2) && skillNamed(t[1], A) && wordsName(t[2], I);
	}

	private static boolean skillNamed(final String which, final Ability A)
	{
		final String w = which.trim();
		return w.equals("*") || w.equalsIgnoreCase("ALL") || w.equalsIgnoreCase(A.ID()) || w.equalsIgnoreCase(A.Name());
	}

	private static boolean wordsName(final String words, final Item I)
	{
		final String w = words.trim();
		return (w.length() == 0) || w.equals("*") || w.equalsIgnoreCase("ALL") || CMLib.english().containsString(I.name(), w);
	}

	/** FACTION_PROG's code */
	public static final int	FACTION_PROG	= code("FACTION_PROG");

	/**
	 * ST14: a character's standing in a faction changed: FACTION_PROG
	 * faction range|ANY|UP|DOWN [mask] fires for the room and area, and
	 * anywhere with @GLOBAL; a range name fires only on entering it.  $g is
	 * the new range, $0 the change, $1 the range before, $2 the faction.
	 * @param mob the character
	 * @param F the faction
	 * @param before the value before
	 * @param after the value now
	 */
	public static void faction(final MOB mob, final com.planet_ink.coffee_mud.Common.interfaces.Faction F, final int before, final int after)
	{
		if((mob != null) && (F != null) && (before != after) && (mob.location() != null))
			JSEvents.fire("factionchange", mob, F, Integer.valueOf(after - before)); // JS8
		if((mob == null) || (F == null) || (before == after) || (!isUsed(FACTION_PROG)) || (mob.location() == null))
			return;
		final com.planet_ink.coffee_mud.Common.interfaces.Faction.FRange oldR = F.fetchRange(before);
		final com.planet_ink.coffee_mud.Common.interfaces.Faction.FRange newR = F.fetchRange(after);
		final String newName = (newR == null) ? "" : newR.name();
		final String oldName = (oldR == null) ? "" : oldR.name();
		final int amount = after - before;
		fireAround(hosts(mob.location()), FACTION_PROG, mob, null, newName, new Object[] { "" + amount, oldName, F.factionID() }, "Ccr",
			t -> factionMatches(t, F, oldR, newR, amount, mob));
	}

	/**
	 * Whether a FACTION_PROG trigger line fits a change of standing.
	 * @param t the trigger line: FACTION_PROG, the faction, then the range, ANY, UP or DOWN, and a mask
	 * @param F the faction
	 * @param oldR the range before
	 * @param newR the range now
	 * @param amount the change
	 * @param mob the character
	 * @return true if it fits
	 */
	public static boolean factionMatches(final String[] t, final com.planet_ink.coffee_mud.Common.interfaces.Faction F,
		final com.planet_ink.coffee_mud.Common.interfaces.Faction.FRange oldR, final com.planet_ink.coffee_mud.Common.interfaces.Faction.FRange newR,
		final int amount, final MOB mob)
	{
		if(t.length < 2)
			return false;
		final String f = t[1].trim();
		final String id = F.factionID();
		final String bare = id.toLowerCase().endsWith(".ini") ? id.substring(0, id.length() - 4) : id;
		if(!(f.equalsIgnoreCase(id) || f.equalsIgnoreCase(bare) || f.equalsIgnoreCase(F.name())))
			return false;
		final String rest = (t.length > 2) ? t[2].trim() : "";
		final String kind = CMParms.getCleanBit(rest, 0).trim();
		final String mask = CMParms.getPastBitClean(rest, 0).trim();
		final boolean fits;
		if((kind.length() == 0) || kind.equalsIgnoreCase("ANY"))
			fits = true;
		else
		if(kind.equalsIgnoreCase("UP"))
			fits = amount > 0;
		else
		if(kind.equalsIgnoreCase("DOWN"))
			fits = amount < 0;
		else
			fits = (newR != null) && (newR != oldR) && (kind.equalsIgnoreCase(newR.name()) || kind.equalsIgnoreCase(newR.codeName()));
		return fits && ((mask.length() == 0) || CMLib.masking().maskCheck(mask, mob, true));
	}

	/** AREA_ENTER_PROG's code */
	public static final int	AREA_ENTER_PROG	= code("AREA_ENTER_PROG");
	/** AREA_LEAVE_PROG's code */
	public static final int	AREA_LEAVE_PROG	= code("AREA_LEAVE_PROG");

	private static final Map<MOB, Area>	firstEntered	= new WeakHashMap<MOB, Area>();

	private static boolean within(final Area A, final Area H)
	{
		return (A != null) && (H != null) && ((A == H) || A.getParentsRecurse().contains(H));
	}

	/**
	 * ST16: a character went from one room to another (or came into the
	 * world, from no room): if the areas differ, AREA_LEAVE_PROG for the hosts
	 * of the area left and AREA_ENTER_PROG for those of the area entered
	 * ([ALL] [pct|mask]; without ALL, a move between an area and one inside
	 * it does not count for the outer one), and the areas' Prop_AreaEntry or
	 * Prop_AreaBanner.  $n is the mover, $g the other area's name, $0 the
	 * other room's ID.
	 * @param mob the mover
	 * @param from the room left, or null
	 * @param to the room entered
	 */
	public static void moved(final MOB mob, final Room from, final Room to)
	{
		if((mob == null) || (to == null))
			return;
		JSEvents.fire("enterroom", mob, to, from); // JS8
		final Area fromA = (from == null) ? null : from.getArea();
		final Area toA = to.getArea();
		if((toA == null) || (fromA == toA))
			return;
		JSEvents.fire("enterarea", mob, toA, fromA); // JS8
		final boolean firstTime;
		synchronized(firstEntered)
		{
			firstTime = (firstEntered.remove(mob) == toA);
		}
		if(mob.playerStats() != null)
		{
			if(fromA != null)
				areaText(mob, fromA, false, false);
			areaText(mob, toA, true, firstTime);
		}
		if(fromA != null)
			areaHop(AREA_LEAVE_PROG, mob, fromA, toA, to);
		areaHop(AREA_ENTER_PROG, mob, toA, fromA, from);
		// ST8: on and off vessels
		if(fromA instanceof Boardable)
			boarded(mob, fromA, to, false);
		if(toA instanceof Boardable)
			boarded(mob, toA, from, true);
	}

	private static void areaHop(final int code, final MOB mob, final Area mine, final Area other, final Room otherRoom)
	{
		if(!isUsed(code))
			return;
		final String otherName = (other == null) ? "" : other.Name();
		final String otherID = (otherRoom == null) ? "" : CMLib.map().getExtendedRoomID(otherRoom);
		for(final PhysicalAgent P : registered(code))
		{
			final Area H = (P instanceof Area) ? (Area)P : ((CMLib.map().roomLocation(P) == null) ? null : CMLib.map().roomLocation(P).getArea());
			if(!within(mine, H))
				continue;
			final boolean inside = within(other, H);
			fire(Collections.singletonList(P), code, mob, null, otherName, new Object[] { otherID }, "Cr", t -> areaMatches(t, mob, inside));
		}
	}

	/**
	 * Whether an AREA_ENTER_PROG or AREA_LEAVE_PROG trigger line fits.
	 * @param t the trigger line: the trigger, then ALL, a chance or a mask
	 * @param mob the mover
	 * @param inside true if the other room is inside the host's area too
	 * @return true if it fits
	 */
	public static boolean areaMatches(final String[] t, final MOB mob, final boolean inside)
	{
		String rest = (t.length > 1) ? t[1].trim() : "";
		final boolean all = firstWord(rest, "ALL");
		if(all)
			rest = rest.substring(3).trim();
		if(inside && (!all))
			return false;
		if(rest.length() == 0)
			return true;
		if(CMath.isInteger(rest))
			return CMLib.dice().rollPercentage() <= CMath.s_int(rest);
		return CMLib.masking().maskCheck(rest, mob, true);
	}

	/**
	 * Prop_AreaEntry and Prop_AreaBanner (ST16, with PR4): text on coming
	 * into an area or leaving it.  ENTER="..." FIRST="..." (the first time)
	 * LEAVE="..." TITLE="..." (or SHOWNAME for the area's name) TEXT="..."
	 * DESC (the area's description) ONCE (the title and text only the first
	 * time) MASK="..." COLOR="^H".
	 * @param mob the character
	 * @param A the area
	 * @param entering true coming in, false leaving
	 * @param firstTime true if it is their first room there
	 */
	public static void areaText(final MOB mob, final Area A, final boolean entering, final boolean firstTime)
	{
		Ability P = A.fetchEffect("Prop_AreaEntry");
		if(P == null)
			P = A.fetchEffect("Prop_AreaBanner");
		if(P == null)
			return;
		final String parms = P.text();
		final String mask = CMParms.getParmStr(parms, "MASK", "");
		if((mask.length() > 0) && (!CMLib.masking().maskCheck(mask, mob, true)))
			return;
		if(!entering)
		{
			final String leave = CMParms.getParmStr(parms, "LEAVE", "");
			if(leave.length() > 0)
				mob.tell(leave);
			return;
		}
		final boolean banner = P.ID().equals("Prop_AreaBanner");
		final boolean once = CMParms.getParmBool(parms, "ONCE", false);
		final String color = CMParms.getParmStr(parms, "COLOR", "^H");
		final StringBuilder str = new StringBuilder();
		if((!once) || firstTime)
		{
			String title = CMParms.getParmStr(parms, "TITLE", "");
			if((title.length() == 0) && (banner || CMParms.getParmBool(parms, "SHOWNAME", false)))
				title = A.name().toUpperCase();
			if(title.length() > 0)
				str.append(color).append(title).append("^N\n\r");
			final String text = CMParms.getParmStr(parms, "TEXT", "");
			if(text.length() > 0)
				str.append(text).append("\n\r");
			if((banner || CMParms.getParmBool(parms, "DESC", false)) && (A.description().length() > 0))
				str.append(A.description()).append("\n\r");
		}
		final String enter = CMParms.getParmStr(parms, "ENTER", "");
		if(enter.length() > 0)
			str.append(enter).append("\n\r");
		final String first = CMParms.getParmStr(parms, "FIRST", "");
		if(firstTime && (first.length() > 0))
			str.append(first).append("\n\r");
		if(str.length() > 0)
			mob.tell(str.toString());
	}

	/** BOARD_PROG's code */
	public static final int	BOARD_PROG			= code("BOARD_PROG");
	/** DISEMBARK_PROG's code */
	public static final int	DISEMBARK_PROG		= code("DISEMBARK_PROG");
	/** SAIL_PROG's code */
	public static final int	SAIL_PROG			= code("SAIL_PROG");
	/** VESSEL_ARRIVE_PROG's code */
	public static final int	VESSEL_ARRIVE_PROG	= code("VESSEL_ARRIVE_PROG");
	/** VESSEL_GREET_PROG's code */
	public static final int	VESSEL_GREET_PROG	= code("VESSEL_GREET_PROG");
	/** DOCK_PROG's code */
	public static final int	DOCK_PROG			= code("DOCK_PROG");

	/**
	 * The vessel whose own area an area is (ST8).
	 * @param A the area
	 * @return the vessel's item, or null
	 */
	public static Item vesselOf(final Area A)
	{
		return (A instanceof Boardable) ? ((Boardable)A).getBoardableItem() : null;
	}

	/**
	 * A vessel and what is aboard (ST8): the vessel's item, and its own
	 * area's rooms, things and crew.
	 * @param vessel the vessel's item
	 * @return the hosts
	 */
	public static List<PhysicalAgent> aboard(final Item vessel)
	{
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		if(vessel == null)
			return hosts;
		hosts.add(vessel);
		if((vessel instanceof Boardable) && (((Boardable)vessel).getArea() != null))
		{
			for(final PhysicalAgent P : hosts(((Boardable)vessel).getArea()))
			{
				if(!hosts.contains(P))
					hosts.add(P);
			}
		}
		return hosts;
	}

	private static boolean restFits(final String[] t, final int i, final MOB mob)
	{
		final String rest = (t.length > i) ? t[i].trim() : "";
		return (rest.length() == 0) || rest.equalsIgnoreCase("ALL") || ((mob != null) && CMLib.masking().maskCheck(rest, mob, true));
	}

	/**
	 * ST8: someone boarded a vessel or left it: BOARD_PROG or DISEMBARK_PROG
	 * [mask] for the vessel, what is aboard, and the room outside.  $n is the
	 * one, $t the vessel, $g its name.
	 * @param mob the one
	 * @param shipArea the vessel's own area
	 * @param outside the room outside, or null
	 * @param on true boarding, false leaving
	 */
	public static void boarded(final MOB mob, final Area shipArea, final Room outside, final boolean on)
	{
		final int code = on ? BOARD_PROG : DISEMBARK_PROG;
		final Item vessel = vesselOf(shipArea);
		if((vessel == null) || (!isUsed(code)))
			return;
		final List<PhysicalAgent> hosts = aboard(vessel);
		if(outside != null)
		{
			final List<PhysicalAgent> out = new ArrayList<PhysicalAgent>();
			roomHosts(outside, out);
			for(final PhysicalAgent P : out)
			{
				if(!hosts.contains(P))
					hosts.add(P);
			}
		}
		fire(hosts, code, mob, vessel, vessel.Name(), null, "Cr", t -> restFits(t, 1, mob));
	}

	/**
	 * ST8: an order to a vessel from one allowed to give it: SAIL_PROG
	 * COURSE|ANCHOR|STEER|ALL (or the order's own word) for the vessel and
	 * what is aboard.  $n is the helmsman, $t the vessel, $g the order, $0 its
	 * kind.
	 * @param vessel the vessel
	 * @param helm the helmsman
	 * @param command the order's command (RAISE_ANCHOR, STEER, COURSE...)
	 * @param order the order's words
	 */
	public static void sail(final Item vessel, final MOB helm, final String command, final String order)
	{
		if((vessel == null) || (!isUsed(SAIL_PROG)))
			return;
		final String kind;
		if(command.endsWith("ANCHOR"))
			kind = "ANCHOR";
		else
		if(command.equals("STEER") || command.equals("NAVIGATE"))
			kind = "STEER";
		else
		if(command.endsWith("COURSE"))
			kind = "COURSE";
		else
			kind = command;
		fire(aboard(vessel), SAIL_PROG, helm, vessel, order, new Object[] { kind }, "CCr",
			t -> (t.length > 1) && (t[1].equals("ALL") || t[1].equals("ANY") || t[1].equals(kind) || t[1].equals(command)));
	}

	/**
	 * ST8: a vessel came into a room: VESSEL_ARRIVE_PROG [room ID|area|ALL]
	 * for the vessel and what is aboard, and VESSEL_GREET_PROG [vessel
	 * words|ALL] for the room and what is in it.  $n is a stand-in named for
	 * the vessel, $t the vessel, $g the room's ID, $0 its area.
	 * @param vessel the vessel
	 * @param to the room
	 */
	public static void vesselArrived(final Item vessel, final Room to)
	{
		if((vessel == null) || (to == null) || ((!isUsed(VESSEL_ARRIVE_PROG)) && (!isUsed(VESSEL_GREET_PROG))))
			return;
		final String id = CMLib.map().getExtendedRoomID(to);
		final String area = (to.getArea() == null) ? "" : to.getArea().Name();
		final MOB src = standIn(vessel.Name());
		fire(aboard(vessel), VESSEL_ARRIVE_PROG, src, vessel, id, new Object[] { area }, "Cr", t -> {
			final String w = (t.length > 1) ? t[1].trim() : "";
			return (w.length() == 0) || w.equalsIgnoreCase("ALL") || w.equalsIgnoreCase(id) || w.equalsIgnoreCase(area);
		});
		final List<PhysicalAgent> here = new ArrayList<PhysicalAgent>();
		roomHosts(to, here);
		here.remove(vessel);
		fire(here, VESSEL_GREET_PROG, src, vessel, id, new Object[] { area }, "Cr", t -> {
			final String w = (t.length > 1) ? t[1].trim() : "";
			return (w.length() == 0) || w.equalsIgnoreCase("ALL") || CMLib.english().containsString(vessel.name(), w);
		});
	}

	/**
	 * ST8: a vessel's anchor dropped: DOCK_PROG for the vessel, what is
	 * aboard, and the room it is in.  $n is who dropped it, $t the vessel,
	 * $g the room's ID.
	 * @param vessel the vessel
	 * @param mob who dropped it
	 * @param R the room
	 */
	public static void vesselDocked(final Item vessel, final MOB mob, final Room R)
	{
		if((vessel == null) || (R == null) || (!isUsed(DOCK_PROG)))
			return;
		final List<PhysicalAgent> hosts = aboard(vessel);
		final List<PhysicalAgent> here = new ArrayList<PhysicalAgent>();
		roomHosts(R, here);
		for(final PhysicalAgent P : here)
		{
			if(!hosts.contains(P))
				hosts.add(P);
		}
		fire(hosts, DOCK_PROG, (mob != null) ? mob : standIn(vessel.Name()), vessel, CMLib.map().getExtendedRoomID(R), null, "Cr", t -> true);
	}

	/**
	 * Whether a READ_PROG trigger line fits a thing read (ST19): ALL or
	 * nothing for any, P and a phrase, or words naming it.
	 * @param rest the trigger line after READ_PROG
	 * @param I the thing read
	 * @return true if it fits
	 */
	public static boolean readMatches(final String rest, final Item I)
	{
		String r = rest.trim();
		if((r.length() == 0) || r.equalsIgnoreCase("ALL"))
			return true;
		if(firstWord(r, "P"))
			r = r.substring(1).trim();
		if((r.length() > 1) && r.startsWith("'") && r.endsWith("'"))
			r = r.substring(1, r.length() - 1);
		return CMLib.english().containsString(I.name(), r) || CMLib.english().containsString(I.Name(), r);
	}

	/** GENERATED_PROG's code */
	public static final int	GENERATED_PROG	= code("GENERATED_PROG");

	private static final Map<String, Map<String, String>>	genVars	= new HashMap<String, Map<String, String>>();

	/**
	 * GENVAR(area name): a generator's variable for an area it made (ST20).
	 * @param area the area's name
	 * @param name the variable, such as AREA_SIZE or __boss
	 * @return the value, or ""
	 */
	public static String genVar(final String area, final String name)
	{
		synchronized(genVars)
		{
			final Map<String, String> vars = genVars.get(area.toUpperCase().trim());
			if(vars == null)
				return "";
			final String v = vars.get(name.toUpperCase().trim());
			return (v == null) ? "" : v;
		}
	}

	/**
	 * ST20: a generator made an area, or a room: GENERATED to the scripts of
	 * the area, its rooms and what is in them (or the room and what is in it),
	 * and GENERATED_PROG [pct], $g the generator's ID; the generator's
	 * variables kept for GENVAR(); and an area's onGenerate script run once.
	 * @param A the area made, or null
	 * @param R the room made, if not an area
	 * @param id the generator's ID
	 * @param defined the generator's variables
	 */
	public static void generated(final Area A, final Room R, final String id, final Map<String, Object> defined)
	{
		final Area keyArea = (A != null) ? A : ((R == null) ? null : R.getArea());
		if((defined != null) && (keyArea != null))
		{
			final Map<String, String> vars = new HashMap<String, String>();
			for(final Map.Entry<String, Object> e : defined.entrySet())
			{
				final Object v = e.getValue();
				if((v instanceof String) || (v instanceof Number) || (v instanceof Boolean))
					vars.put(e.getKey().toUpperCase(), v.toString());
			}
			synchronized(genVars)
			{
				genVars.put(keyArea.Name().toUpperCase(), vars);
			}
		}
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		if(A != null)
			hosts.addAll(hosts(A));
		else
		if(R != null)
			roomHosts(R, hosts);
		if(hosts.isEmpty())
			return;
		final MOB src = standIn("the generator");
		src.setLocation((R != null) ? R : null);
		if(isUsed(EXECMSG_PROG))
			deliver(hosts, CMClass.getMsg(src, null, null, CMMsg.MSG_GENERATED, id));
		fire(hosts, GENERATED_PROG, src, null, id, null, "Cr", t -> {
			final String w = (t.length > 1) ? t[1].trim() : "";
			return (w.length() == 0) || (!CMath.isInteger(w)) || (CMLib.dice().rollPercentage() <= CMath.s_int(w));
		});
		final Object onGen = (defined == null) ? null : defined.get("AREA_ONGENERATE");
		if((A != null) && (onGen instanceof String) && (((String)onGen).trim().length() > 0))
		{
			String text = ((String)onGen).trim();
			if(!text.toUpperCase().startsWith("LOAD="))
				text = "GENERATED_PROG\n" + text.replace(';', '\n') + "\n~\n";
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			S.setScript(text);
			S.eventRun(A, GENERATED_PROG, src, null, null, id, null, "Cr", t -> true);
		}
	}

	/** FIRSTGREET_PROG's code */
	public static final int	FIRSTGREET_PROG	= code("FIRSTGREET_PROG");
	/** REGREET_PROG's code */
	public static final int	REGREET_PROG	= code("REGREET_PROG");

	private static final Map<MOB, Object>	lastMeeting	= new WeakHashMap<MOB, Object>();
	private static final Map<MOB, long[]>	lastBefore	= new WeakHashMap<MOB, long[]>();

	private static ScriptingEngine varStore()
	{
		return (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
	}

	private static String memHost(final MOB npc)
	{
		return "MEMORY:" + npc.Name();
	}

	/**
	 * ST21: an NPC's record of a character: [times met, first meeting, last
	 * meeting] in game time, kept in the game's script variables (so saved).
	 * @param npc the NPC
	 * @param who the character's name
	 * @return the record
	 */
	public static long[] memory(final MOB npc, final String who)
	{
		final ScriptingEngine S = varStore();
		final String h = memHost(npc);
		final String k = who.toUpperCase().replace(' ', '_');
		return new long[] {
			CMath.s_long(S.getVar(h, k + "_COUNT")),
			CMath.s_long(S.getVar(h, k + "_FIRST")),
			CMath.s_long(S.getVar(h, k + "_LAST")) };
	}

	/**
	 * ST21: the last topic an NPC and a character talked of.
	 * @param npc the NPC
	 * @param who the character's name
	 * @return the topic, or ""
	 */
	public static String lastTopic(final MOB npc, final String who)
	{
		return varStore().getVar(memHost(npc), who.toUpperCase().replace(' ', '_') + "_TOPIC");
	}

	/**
	 * ST21: notes the topic an NPC and a character talk of (for N9's TALK).
	 * @param npc the NPC
	 * @param who the character
	 * @param topic the topic
	 */
	public static void talked(final MOB npc, final MOB who, final String topic)
	{
		met(npc, who, topic);
		varStore().setVar(memHost(npc), who.Name().toUpperCase().replace(' ', '_') + "_TOPIC", (topic == null) ? "" : topic);
	}

	/**
	 * ST21: an NPC saw a character arrive (or talked with them): the record
	 * is brought up to date, once for each arrival however many scripts the
	 * NPC has.
	 * @param npc the NPC
	 * @param who the character
	 * @param event the arrival's message, or anything that marks this meeting
	 * @return the record as it was before: times met, first, last
	 */
	public static long[] met(final MOB npc, final MOB who, final Object event)
	{
		synchronized(lastMeeting)
		{
			if((lastMeeting.get(npc) == event) && lastBefore.containsKey(npc))
				return lastBefore.get(npc);
		}
		final long[] before = memory(npc, who.Name());
		final long now = GameTime.now();
		final ScriptingEngine S = varStore();
		final String h = memHost(npc);
		final String k = who.Name().toUpperCase().replace(' ', '_');
		S.setVar(h, k + "_COUNT", "" + (before[0] + 1));
		if(before[0] == 0)
			S.setVar(h, k + "_FIRST", "" + now);
		S.setVar(h, k + "_LAST", "" + now);
		synchronized(lastMeeting)
		{
			lastMeeting.put(npc, event);
			lastBefore.put(npc, before);
		}
		return before;
	}

	/**
	 * Game milliseconds in a game day on the world's clock.
	 * @return the milliseconds
	 */
	public static long gameDayMillis()
	{
		final TimeClock C = CMLib.time().globalClock();
		final int hours = ((C == null) || (C.getHoursInDay() <= 0)) ? 24 : C.getHoursInDay();
		return CMProps.getMillisPerMudHour() * hours;
	}

	/**
	 * Whether a SOCIAL_PROG trigger line fits a social (ST22): [EXACT] [ME]
	 * and the name; EXACT matches the whole name, not its start, and ME only
	 * a social aimed at the scripted mob.
	 * @param rest the trigger line after SOCIAL_PROG, in upper case
	 * @param social the social's name
	 * @param atMe true if the social is aimed at the scripted mob
	 * @return true if it fits
	 */
	public static boolean socialMatches(final String rest, final String social, final boolean atMe)
	{
		final List<String> words = CMParms.parse(rest.toUpperCase());
		boolean exact = false;
		boolean me = false;
		while(words.size() > 0)
		{
			if(words.get(0).equals("EXACT"))
				exact = true;
			else
			if(words.get(0).equals("ME"))
				me = true;
			else
				break;
			words.remove(0);
		}
		if(me && (!atMe))
			return false;
		final String name = String.join(" ", words);
		final String s = social.toUpperCase();
		if(!exact)
			return s.startsWith(name);
		if(words.size() <= 1)
			return CMParms.getCleanBit(s, 0).equals(name);
		return s.equals(name);
	}

	/**
	 * ST24: a clock's time in hours since its year 0, for EVERY_PROG.
	 * @param C the clock
	 * @return the hours
	 */
	public static long clockHours(final TimeClock C)
	{
		if(C == null)
			return GameTime.now() / Math.max(1, CMProps.getMillisPerMudHour());
		return ((((long)C.getYear() * C.getMonthsInYear()) + C.getMonth()) * C.getDaysInMonth() + C.getDayOfMonth()) * C.getHoursInDay() + C.getHourOfDay();
	}

	/**
	 * ST24: EVERY_PROG's interval in a clock's hours.
	 * @param n how many
	 * @param unit HOURS, DAYS, WEEKS or MONTHS (HOURS if not one of them)
	 * @param C the clock
	 * @return the hours, at least 1
	 */
	public static long everyHours(final long n, final String unit, final TimeClock C)
	{
		final int hoursInDay = ((C == null) || (C.getHoursInDay() <= 0)) ? 24 : C.getHoursInDay();
		final String u = unit.toUpperCase();
		long h = n;
		if(u.startsWith("DAY"))
			h = n * hoursInDay;
		else
		if(u.startsWith("WEEK"))
			h = n * hoursInDay * (((C == null) || (C.getDaysInWeek() <= 0)) ? 7 : C.getDaysInWeek());
		else
		if(u.startsWith("MONTH"))
			h = n * hoursInDay * (((C == null) || (C.getDaysInMonth() <= 0)) ? 30 : C.getDaysInMonth());
		return Math.max(1, h);
	}

	/** VAR_PROG's code */
	public static final int	VAR_PROG	= code("VAR_PROG");

	private static boolean varTest(final String value, final String cmp, final String against)
	{
		final String v = (value == null) ? "" : value.trim();
		final String a = against.trim();
		final boolean nums = CMath.isNumber(v) && CMath.isNumber(a);
		final int c = nums ? Double.compare(CMath.s_double(v), CMath.s_double(a)) : v.compareToIgnoreCase(a);
		switch(cmp)
		{
		case "==": case "=": return c == 0;
		case "!=": case "<>": return c != 0;
		case ">": return c > 0;
		case "<": return c < 0;
		case ">=": case "=>": return c >= 0;
		case "<=": case "=<": return c <= 0;
		default: return false;
		}
	}

	/**
	 * ST25: a script variable changed: VAR_PROG host|*|quest var cmp value
	 * fires, for every host with the trigger, when the condition comes true.
	 * $g is the new value, $0 the old.
	 * @param scope the variable's scope (a quest's name, or "")
	 * @param host the variable's host
	 * @param var the variable
	 * @param before the value before
	 * @param now the value now
	 */
	public static void varChanged(final String scope, final String host, final String var, final String before, final String now)
	{
		if(!isUsed(VAR_PROG))
			return;
		final String b = (before == null) ? "" : before;
		final String n = (now == null) ? "" : now;
		if(b.equals(n))
			return;
		final List<PhysicalAgent> hosts = registered(VAR_PROG);
		if(hosts.isEmpty())
			return;
		fire(hosts, VAR_PROG, standIn("the world"), null, n, new Object[] { b, host, var }, "Ccccr", t -> {
			if(t.length < 5)
				return false;
			final String h = t[1].trim();
			if(!(h.equals("*") || h.equalsIgnoreCase(host) || ((scope != null) && (scope.length() > 0) && h.equalsIgnoreCase(scope))))
				return false;
			if(!t[2].trim().equalsIgnoreCase(var))
				return false;
			return varTest(n, t[3].trim(), t[4]) && (!varTest(b, t[3].trim(), t[4]));
		});
	}

	/** AFFECT_PROG's code */
	public static final int	AFFECT_PROG	= code("AFFECT_PROG");

	/**
	 * ST26: an effect was put on something or came off it: AFFECT_PROG
	 * ADD|REMOVE|ANY ability|* for the thing, its room, and an item's owner.
	 * $t is the thing, $g the effect's ID, $n its invoker.
	 * @param host the thing affected
	 * @param A the effect
	 * @param added true if put on, false if off
	 */
	public static void affected(final Physical host, final Ability A, final boolean added)
	{
		if((host == null) || (A == null) || (!isUsed(AFFECT_PROG)) || com.planet_ink.coffee_mud.core.save.SaveGame.isWorldPending())
			return;
		final Room R = CMLib.map().roomLocation(host);
		if(R == null)
			return;
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		if(host instanceof PhysicalAgent)
			hosts.add((PhysicalAgent)host);
		if(!hosts.contains(R))
			hosts.add(R);
		if((host instanceof Item) && (((Item)host).owner() instanceof MOB) && (!hosts.contains(((Item)host).owner())))
			hosts.add((MOB)((Item)host).owner());
		final String kind = added ? "ADD" : "REMOVE";
		final MOB inv = (A.invoker() != null) ? A.invoker() : standIn("the world");
		fire(hosts, AFFECT_PROG, inv, host, A.ID(), new Object[] { kind }, "CCr", t -> {
			if(t.length < 2)
				return false;
			if((!t[1].equals("ANY")) && (!t[1].equals(kind)))
				return false;
			final String w = (t.length > 2) ? t[2].trim() : "";
			return (w.length() == 0) || w.equals("*") || w.equalsIgnoreCase(A.ID()) || CMLib.english().containsString(A.Name(), w);
		});
	}

	/** DESTROY_PROG's code */
	public static final int	DESTROY_PROG	= code("DESTROY_PROG");
	/** DECAY_PROG's code */
	public static final int	DECAY_PROG		= code("DECAY_PROG");

	private static final ThreadLocal<String>	destroyReason	= new ThreadLocal<String>();

	/**
	 * ST27: why the items destroyed on this thread go, until set again:
	 * JUNKED, BROKEN, CONSUMED, or null.
	 * @param why the reason
	 */
	public static void destroyReason(final String why)
	{
		if(why == null)
			destroyReason.remove();
		else
			destroyReason.set(why);
	}

	/**
	 * ST27: an item is being destroyed: DESTROY_PROG reason|ALL and, for rot
	 * and expiry, DECAY_PROG, run at once on the item, its room and its owner
	 * (the item's scripts are gone after).  $g is DECAY, BROKEN, CONSUMED,
	 * JUNKED or "".
	 * @param I the item
	 */
	public static void destroyed(final Item I)
	{
		if((I == null) || ((!isUsed(DESTROY_PROG)) && (!isUsed(DECAY_PROG))))
			return;
		final Room R = CMLib.map().roomLocation(I);
		if(R == null)
			return;
		String why = destroyReason.get();
		if((why == null) && (I.expirationDate() != 0) && (GameTime.now() > I.expirationDate()))
			why = "DECAY";
		final String reason = (why == null) ? "" : why;
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		hosts.add(I);
		hosts.add(R);
		if((I.owner() instanceof MOB) && (!hosts.contains(I.owner())))
			hosts.add((MOB)I.owner());
		final MOB src = standIn("the world");
		for(final PhysicalAgent host : hosts)
		{
			for(final ScriptingEngine S : engines(host))
			{
				S.eventCheck(host, DESTROY_PROG, src, I, I, reason, null, "Cr", t -> {
					final String w = (t.length > 1) ? t[1].trim() : "";
					return (w.length() == 0) || w.equalsIgnoreCase("ALL") || w.equalsIgnoreCase(reason) || CMLib.english().containsString(I.name(), w);
				});
				if(reason.equals("DECAY"))
					S.eventCheck(host, DECAY_PROG, src, I, I, reason, null, "C", t -> true);
			}
		}
	}

	/** SIGNAL_PROG's code */
	public static final int	SIGNAL_PROG	= code("SIGNAL_PROG");

	/**
	 * SC6: who a signal is for: ROOM (what is in the sender's room), AREA
	 * (all of its area), WORLD (every scripted thing), TAG:tag (the things so
	 * tagged in the sender's area; TAG:tag@WORLD anywhere), a room's ID, a
	 * room's exit (room:north, here:north), or the one thing a script named.
	 * @param spec the target's words
	 * @param from the sender
	 * @param thing the one thing, if the words named one, else null
	 * @return the hosts
	 */
	public static List<PhysicalAgent> signalHosts(final String spec, final Environmental from, final Environmental thing)
	{
		final List<PhysicalAgent> hosts = new ArrayList<PhysicalAgent>();
		final String s = (spec == null) ? "" : spec.trim();
		final String up = s.toUpperCase();
		final Room here = CMLib.map().roomLocation(from);
		if(up.equals("ROOM"))
			roomHosts(here, hosts);
		else
		if(up.equals("AREA"))
			hosts.addAll(hosts((here != null) ? here.getArea() : null));
		else
		if(up.equals("WORLD"))
		{
			for(final Enumeration<WorldMap.LocatedPair> e = CMLib.map().scriptHosts(null); e.hasMoreElements();)
			{
				final WorldMap.LocatedPair p = e.nextElement();
				if((p != null) && (p.obj() != null) && (!hosts.contains(p.obj())))
					hosts.add(p.obj());
			}
		}
		else
		if(up.startsWith("TAG:"))
		{
			String tag = s.substring(4).trim();
			final boolean world = tag.toUpperCase().endsWith("@WORLD");
			if(world)
				tag = tag.substring(0, tag.length() - 6).trim();
			final Area A = (here != null) ? here.getArea() : null;
			for(final Enumeration<Taggable> e = CMLib.map().getTaggedObjects(tag); e.hasMoreElements();)
			{
				final Taggable T = e.nextElement();
				if(!(T instanceof PhysicalAgent))
					continue;
				final Room R = CMLib.map().roomLocation((PhysicalAgent)T);
				if(world || (A == null) || ((R != null) && (R.getArea() == A)) || (T == A))
					hosts.add((PhysicalAgent)T);
			}
		}
		else
		if(thing instanceof PhysicalAgent)
			hosts.add((PhysicalAgent)thing);
		else
		if(s.indexOf(':') > 0)
		{
			final int x = s.lastIndexOf(':');
			final String roomID = s.substring(0, x).trim();
			final Room R = (roomID.equalsIgnoreCase("HERE") || (roomID.length() == 0)) ? here : CMLib.map().getRoom(roomID);
			final int dir = CMLib.directions().getGoodDirectionCode(s.substring(x + 1).trim());
			if((R != null) && (dir >= 0) && (R.getRawExit(dir) != null))
				hosts.add(R.getRawExit(dir));
		}
		else
		{
			final Room R = CMLib.map().getRoom(s);
			if(R != null)
				hosts.add(R);
		}
		return hosts;
	}

	/**
	 * SC6: sends a signal: SIGNAL_PROG name|ALL on each host's scripts, queued
	 * to run after so many ticks; $g is the arguments, $n the actor, $t the
	 * sender (and $o, if it is an item).
	 * @param hosts who it is for
	 * @param name the signal
	 * @param args its arguments
	 * @param actor who caused it
	 * @param sender the scripted thing that sent it
	 * @param ticks how many ticks until it is heard
	 */
	public static void signal(final List<PhysicalAgent> hosts, final String name, final String args, final MOB actor, final Environmental sender, final int ticks)
	{
		if((actor == null) || (name == null))
			return;
		for(final PhysicalAgent host : hosts)
		{
			for(final ScriptingEngine S : engines(host))
			{
				S.eventTrigger(host, SIGNAL_PROG, actor, sender, (sender instanceof Item) ? (Item)sender : null, (args == null) ? "" : args, null, "Cr", t -> {
					if((t.length < 2) || (t[1].trim().length() == 0))
						return true;
					final List<String> names = CMParms.parse(t[1] + ((t.length > 2) ? (" " + t[2]) : ""));
					for(final String w : names)
					{
						if(w.equalsIgnoreCase(name) || w.equalsIgnoreCase("ALL"))
							return true;
					}
					return false;
				}, ticks);
			}
		}
	}

	/**
	 * SC6: calls a function of a host's scripts (MPCALLFUNC target func).
	 * @param host the thing with the scripts
	 * @param function the function's name
	 * @param args its arguments, $g
	 * @param source $n
	 * @return what it returned, or null if it has no such function
	 */
	public static String callFunction(final PhysicalAgent host, final String function, final String args, final MOB source)
	{
		for(final ScriptingEngine S : engines(host))
		{
			final String r = S.callFunction(host, function, args, source);
			if(r != null)
				return r;
		}
		return jsFunction(host, function, args, source); // JS11: a JScriptable's function
	}

	/** ACHIEVE_PROG's code */
	public static final int	ACHIEVE_PROG	= code("ACHIEVE_PROG");

	/**
	 * ST29: an achievement was earned: ACHIEVE_PROG ID|* [mask] for every
	 * host with the trigger.  $n is the earner, $g the achievement's ID.
	 * @param mob the earner
	 * @param id the achievement's ID
	 */
	public static void achieved(final MOB mob, final String id)
	{
		if((mob == null) || (id == null) || (!isUsed(ACHIEVE_PROG)))
			return;
		final List<PhysicalAgent> hosts = registered(ACHIEVE_PROG);
		if(hosts.isEmpty())
			return;
		fire(hosts, ACHIEVE_PROG, mob, null, id, null, "Ccr", t -> {
			if(t.length < 2)
				return false;
			final String w = t[1].trim();
			if(!(w.equals("*") || w.equalsIgnoreCase("ALL") || w.equalsIgnoreCase(id)))
				return false;
			final String mask = (t.length > 2) ? t[2].trim() : "";
			return (mask.length() == 0) || CMLib.masking().maskCheck(mask, mob, true);
		});
	}

	/** WEATHER_PROG's code */
	public static final int	WEATHER_PROG	= code("WEATHER_PROG");
	/** MOON_PROG's code */
	public static final int	MOON_PROG		= code("MOON_PROG");
	/** EXECMSG_PROG's code */
	public static final int	EXECMSG_PROG	= code("EXECMSG_PROG");

	private static String weatherName(final int code)
	{
		return ((code >= 0) && (code < Climate.WEATHER_DESCS.length)) ? Climate.WEATHER_DESCS[code] : "";
	}

	/**
	 * ST4: an area's weather changed: WEATHERCHANGE to its scripts, the words
	 * "new old forecast", and WEATHER_PROG new|ANY [old], with $g the new
	 * weather, $0 the old and $1 the forecast.
	 * @param A the area
	 * @param oldWeather the weather it was
	 * @param newWeather the weather now
	 * @param nextWeather the weather to come
	 */
	public static void weatherChanged(final Area A, final int oldWeather, final int newWeather, final int nextWeather)
	{
		if((A == null) || (oldWeather == newWeather) || ((!isUsed(WEATHER_PROG)) && (!isUsed(EXECMSG_PROG))))
			return;
		final MOB src = source("the weather", A);
		if(src == null)
			return;
		final String o = weatherName(oldWeather);
		final String n = weatherName(newWeather);
		final String f = weatherName(nextWeather);
		final List<PhysicalAgent> hosts = hosts(A);
		if(isUsed(EXECMSG_PROG))
			deliver(hosts, CMClass.getMsg(src, null, null, CMMsg.MSG_WEATHERCHANGE, n + " " + o + " " + f));
		fire(hosts, WEATHER_PROG, src, null, n, new Object[] { o, f }, "CCC", t -> weatherMatches(t, n, o));
	}

	/**
	 * Whether a WEATHER_PROG trigger line fits a change of weather.
	 * @param t the trigger line: WEATHER_PROG, the new weather or ANY, the old
	 * @param n the new weather
	 * @param o the old
	 * @return true if it fits
	 */
	public static boolean weatherMatches(final String[] t, final String n, final String o)
	{
		if(t.length < 2)
			return false;
		if((!t[1].equals("ANY")) && (!t[1].equals(n)))
			return false;
		return (t.length < 3) || (t[2].length() == 0) || t[2].equals("ANY") || t[2].equals(o);
	}

	private static List<Area> areasOn(final TimeClock C)
	{
		final List<Area> areas = new ArrayList<Area>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			if((A != null) && (A.getTimeObj() == C))
				areas.add(A);
		}
		return areas;
	}

	/**
	 * ST4: a clock's time of day changed: TODCHANGE to the scripts of every
	 * area on it, the word DAWN, DAY, DUSK or NIGHT.
	 * @param C the clock
	 */
	public static void timeOfDayChanged(final TimeClock C)
	{
		if((C == null) || (!isUsed(EXECMSG_PROG)))
			return;
		final String tod = C.getTODCode().name();
		for(final Area A : areasOn(C))
		{
			final MOB src = source("the sky", A);
			if(src != null)
				deliver(hosts(A), CMClass.getMsg(src, null, null, CMMsg.MSG_TODCHANGE, tod));
		}
	}

	/**
	 * ST4: a clock's day changed: DATECHANGE to the scripts of every area on
	 * it, the words "day month season year", and if the moon's phase changed,
	 * MOON_PROG phase|ANY, with $g the phase and $0 the one before.
	 * @param C the clock
	 * @param oldMoon the moon's phase before
	 */
	public static void dateChanged(final TimeClock C, final TimeClock.MoonPhase oldMoon)
	{
		if((C == null) || ((!isUsed(EXECMSG_PROG)) && (!isUsed(MOON_PROG))))
			return;
		final String date = C.getDayOfMonth() + " " + C.getMonth() + " " + C.getSeasonCode().name() + " " + C.getYear();
		final TimeClock.MoonPhase moon = C.getMoonPhase(null);
		for(final Area A : areasOn(C))
		{
			final MOB src = source("the sky", A);
			if(src == null)
				continue;
			final List<PhysicalAgent> hosts = hosts(A);
			if(isUsed(EXECMSG_PROG))
				deliver(hosts, CMClass.getMsg(src, null, null, CMMsg.MSG_DATECHANGE, date));
			if(moon != oldMoon)
				fire(hosts, MOON_PROG, src, null, moon.name(), new Object[] { (oldMoon == null) ? "" : oldMoon.name() }, "CC", t -> (t.length > 1) && (t[1].equals("ANY") || t[1].equals(moon.name())));
		}
	}

	/**
	 * Hands an event to the script engines of each host, as scripts and as
	 * behaviours.
	 * @param hosts the hosts
	 * @param triggerCode the trigger
	 * @param source who it happened to: $n
	 * @param target $t, or null for the host
	 * @param msg $g
	 * @param args $0 to $8, or null
	 * @param bits how the trigger line is split, as parseBits does
	 * @param test which trigger lines the event matches
	 */
	public static void fire(final Collection<PhysicalAgent> hosts, final int triggerCode, final MOB source, final Environmental target, final String msg, final Object[] args, final String bits, final Predicate<String[]> test)
	{
		if((triggerCode <= 0) || (!isUsed(triggerCode)) || (source == null))
			return;
		for(final PhysicalAgent host : hosts)
		{
			for(final ScriptingEngine S : engines(host))
				S.eventTrigger(host, triggerCode, source, target, null, msg, args, bits, test);
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
		if((mob == null) || (A == null) || (mob.playerStats() == null))
			return;
		if(kind.equals("LEARN"))
			JSProgression.call("onLearn", mob, A.ID()); // JS23
		if((!isUsed(SKILL_PROG)) || (mob.location() == null))
			return;
		final Ability mine = mob.fetchAbility(A.ID());
		final int prof = (mine != null) ? mine.proficiency() : A.proficiency();
		fireAround(hosts(mob.location()), SKILL_PROG, mob, A, "" + prof, null, "CCcr", t -> skillMatches(t, mob, A, kind));
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
		if((mob == null) || (A == null) || ((!isUsed(SKILL_PROG)) && (!JSProgression.has("onLearn"))))
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
		if((mob == null) || (R == null))
			return;
		if(newArea && (R.getArea() != null))
		{
			synchronized(firstEntered)
			{
				firstEntered.put(mob, R.getArea()); // ST16: Prop_AreaEntry's FIRST and ONCE
			}
		}
		if(!isUsed(DISCOVER_PROG))
			return;
		fire(hosts(R), DISCOVER_PROG, mob, R, CMLib.map().getExtendedRoomID(R), null, "CCr", t -> discoverMatches(t, mob, R, newArea));
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
