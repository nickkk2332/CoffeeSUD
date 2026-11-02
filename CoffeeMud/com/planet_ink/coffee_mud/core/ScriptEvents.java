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

	private static List<ScriptingEngine> engines(final PhysicalAgent host)
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
		if((mob == null) || (A == null) || (!isUsed(SKILL_PROG)) || (mob.playerStats() == null) || (mob.location() == null))
			return;
		final Ability mine = mob.fetchAbility(A.ID());
		final int prof = (mine != null) ? mine.proficiency() : A.proficiency();
		fire(hosts(mob.location()), SKILL_PROG, mob, A, "" + prof, null, "CCcr", t -> skillMatches(t, mob, A, kind));
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
