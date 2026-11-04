package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.SessionsList;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * Several of the player's characters in the world at once.  All of them are
 * in the player's profile (the one account; see CharCreation).  The one
 * being played has the terminal's session; each of the others in the world
 * has a session of its own with nothing behind it (Common/PartySession), so
 * that the engine treats it as a player like any other.  The party is the
 * group that follows one leader; a character can also be left in the world
 * on its own, or rest in the profile, out of the world.
 * <p>
 * In turn-based time only the party's leader moves the world.  What the
 * others are given to do that takes time waits as an order, carried out on
 * the leader's turn: in {@link Turns#LEADER} as soon as the leader acts,
 * before them; in {@link Turns#ALL} once every member the player has not
 * left to act on their own has an order, or has WAITed, all of them in the
 * party's order, after which the world moves on by a round.
 */
public final class Party
{
	/** how a party's turns go in turn-based time */
	public static enum Turns
	{
		/** the leader's actions move the world; the others' orders go first */
		LEADER,
		/** the world waits for an order for every member, then all act in order */
		ALL
	}

	/** what a command that takes time may do in turn-based time */
	public static enum Turn
	{
		/** go ahead, moving the world, as a leader or someone on their own */
		LEAD,
		/** go ahead without moving the world: an order on the leader's turn */
		ORDER,
		/** wait for the leader's turn */
		WAIT
	}

	/** how the party's turns go */
	private static volatile Turns		turns			= Turns.LEADER;
	/** the party's order, by name; the leader comes first unless placed */
	private static final List<String>	order			= new ArrayList<String>();
	/** members who act on their own rather than wait for orders, by name */
	private static final Set<String>	onTheirOwn		= new HashSet<String>();
	/** members who have WAITed this turn */
	private static final Set<MOB>		waited			= new HashSet<MOB>();
	/** members told that their order waits for the leader's turn */
	private static final Set<MOB>		told			= new HashSet<MOB>();
	/** whether the player was told whom the turn waits for */
	private static boolean				toldWaiting		= false;
	/** the member carrying out an order now */
	private static MOB					acting			= null;

	/** whether a character added to the party comes back where they last were */
	private static volatile boolean	meetWhereLeft	= false;
	/** whether the player's characters may fight each other */
	private static volatile boolean	fightEachOther	= false;
	/** whether the characters not played on the terminal have windows */
	private static volatile boolean	windows			= false;

	private Party()
	{
	}

	/**
	 * Returns whether a character added to the party comes back into the
	 * world where they last were, for the player to go and meet them, rather
	 * than beside the one who added them.
	 * @return true if they come back where they last were
	 */
	public static boolean isMeetWhereLeft()
	{
		return meetWhereLeft || WorldRules.partySummon().equals("MEET");
	}

	/**
	 * Sets whether a character added to the party comes back into the world
	 * where they last were.
	 * @param where true for where they last were, false for beside the player
	 */
	public static void setMeetWhereLeft(final boolean where)
	{
		meetWhereLeft = where;
	}

	/**
	 * Returns whether the player's characters in the world, but for the one
	 * played on the terminal, each have a window of their own.
	 * @return true if they do
	 */
	public static boolean isWindows()
	{
		return windows;
	}

	/**
	 * Sets whether the player's characters in the world, but for the one
	 * played on the terminal, each have a window of their own.  Those in the
	 * world now get a window, or lose theirs, at once.  Windows need a
	 * display.
	 * @param on true for windows
	 * @return false if they were wanted and cannot open here
	 */
	public static boolean setWindows(final boolean on)
	{
		if(on && (!CharacterWindow.canOpen()))
			return false;
		windows = on;
		SessionsList sessions = null;
		try
		{
			sessions = CMLib.sessions();
		}
		catch(final Exception e)
		{
			// the launcher sets it before the engine is up
		}
		if(sessions != null)
		{
			final List<MOB> moving = new ArrayList<MOB>();
			for(final Session S : sessions.allIterable())
			{
				if(isPartySession(S) && (S.mob() != null) && ("WindowSession".equals(S.ID()) != on))
					moving.add(S.mob());
			}
			for(final MOB M : moving)
				moveToNewSession(M);
		}
		return true;
	}

	/**
	 * Moves one of the player's characters in the world, not the one being
	 * played, to a new session of their own, with a window or without as
	 * windows are on or off.
	 * @param M the character
	 */
	private static void moveToNewSession(final MOB M)
	{
		final Session old = M.session();
		final Session S = (Session)CMClass.getCommon(windows ? "WindowSession" : "PartySession");
		if((S == null) || (!S.initialize(null, old.getGroupName())))
			return;
		S.setAccount(profileOf(M));
		old.setMob(null);
		S.setMob(M);
		M.setSession(S);
		S.setStatus(Session.SessionStatus.MAINLOOP);
		CMLib.sessions().add(S);
		old.stopSession(true, false, true, false);
		CMLib.sessions().remove(old);
		CMLib.commands().postLook(M, true);
	}

	/**
	 * Returns whether a session is one of a character in the world who is not
	 * played on the terminal: one with nothing behind it, or a window.
	 * @param S the session
	 * @return true if it is
	 */
	public static boolean isPartySession(final Session S)
	{
		return (S != null) && ("PartySession".equals(S.ID()) || "WindowSession".equals(S.ID()));
	}

	/**
	 * Returns whether a character has a window of their own.
	 * @param M the character
	 * @return true if they do
	 */
	public static boolean isWindowed(final MOB M)
	{
		final Session S = (M == null) ? null : M.session();
		return (S != null) && "WindowSession".equals(S.ID());
	}

	/**
	 * Returns whether the player's characters may fight each other.
	 * @return true if they may
	 */
	public static boolean isFightEachOther()
	{
		return fightEachOther;
	}

	/**
	 * Sets whether the player's characters may fight each other.  Between
	 * players the engine allows a fight when both have their PLAYERKILL
	 * option on, which the PLAYERKILL setting gives every character as they
	 * come into the world; so this sets both, the setting with no limit on
	 * how far apart in level they may be, since they are all the player's.
	 * @param fight true to let them fight
	 */
	public static void setFightEachOther(final boolean fight)
	{
		fightEachOther = fight;
		CMProps.setVar(CMProps.Str.PKILL, fight ? "ALWAYS-100000" : "NEVER");
		for(final Session S : CMLib.sessions().allIterable())
		{
			final MOB M = S.mob();
			if((M != null) && (M.playerStats() != null))
			{
				M.setAttribute(MOB.Attrib.PLAYERKILL, fight);
				// and a fight between them stops
				final MOB V = M.getVictim();
				if((!fight) && (V != null) && (V.playerStats() != null))
				{
					M.makePeace(true);
					V.makePeace(true);
				}
			}
		}
	}

	/**
	 * Returns how the party's turns go in turn-based time.
	 * @return the way
	 */
	public static Turns getTurns()
	{
		return turns;
	}

	/**
	 * Sets how the party's turns go in turn-based time.
	 * @param way the way
	 */
	public static void setTurns(final Turns way)
	{
		turns = way;
	}

	/**
	 * Returns the party's order, as a list of names.
	 * @return the names, comma separated
	 */
	public static synchronized String getOrder()
	{
		return String.join(",", order);
	}

	/**
	 * Sets the party's order.
	 * @param names the names, comma separated
	 */
	public static synchronized void setOrder(final String names)
	{
		order.clear();
		for(final String name : names.split(","))
		{
			if(name.trim().length() > 0)
				order.add(CMStrings.capitalizeAndLower(name.trim()));
		}
	}

	/**
	 * Returns the members who act on their own in turn-based time, rather
	 * than wait for orders, as a list of names.
	 * @return the names, comma separated
	 */
	public static synchronized String getOnTheirOwn()
	{
		final List<String> names = new ArrayList<String>(onTheirOwn);
		Collections.sort(names);
		return String.join(",", names);
	}

	/**
	 * Sets the members who act on their own in turn-based time.
	 * @param names the names, comma separated
	 */
	public static synchronized void setOnTheirOwn(final String names)
	{
		onTheirOwn.clear();
		for(final String name : names.split(","))
		{
			if(name.trim().length() > 0)
				onTheirOwn.add(CMStrings.capitalizeAndLower(name.trim()));
		}
	}

	/**
	 * Returns whether a member acts on their own in turn-based time: the
	 * turn does not wait for an order for them, and they do what followers do.
	 * @param M the member
	 * @return true if they act on their own
	 */
	public static synchronized boolean isOnTheirOwn(final MOB M)
	{
		return onTheirOwn.contains(M.Name());
	}

	/**
	 * Sets whether a member acts on their own in turn-based time.
	 * @param M the member
	 * @param own true for on their own, false to wait for orders
	 */
	public static synchronized void setOnTheirOwn(final MOB M, final boolean own)
	{
		if(own)
			onTheirOwn.add(M.Name());
		else
			onTheirOwn.remove(M.Name());
	}

	/**
	 * Returns a party, leader and members, in the party's order: those placed
	 * in it first, as placed, then the leader, then the rest.
	 * @param leader the leader
	 * @return the party, which is only the leader if they lead no one
	 */
	public static List<MOB> inOrder(final MOB leader)
	{
		final List<MOB> all = new ArrayList<MOB>();
		all.add(leader);
		for(final MOB M : othersInWorld(leader))
		{
			if(leaderOf(M) == leader)
				all.add(M);
		}
		final List<MOB> sorted = new ArrayList<MOB>(all);
		Collections.sort(sorted, (a, b) -> Integer.compare(place(a, leader, all), place(b, leader, all)));
		return sorted;
	}

	private static int place(final MOB M, final MOB leader, final List<MOB> all)
	{
		final int placed = order.indexOf(M.Name());
		if(placed >= 0)
			return placed;
		return order.size() + ((M == leader) ? 0 : 1 + all.indexOf(M));
	}

	/**
	 * Puts a character at the end of the party's order, if not already in it.
	 * @param M the character
	 */
	public static synchronized void addToOrder(final MOB M)
	{
		if(!order.contains(M.Name()))
			order.add(M.Name());
	}

	/**
	 * Returns whether a character leads a party of the player's characters.
	 * @param leader the character
	 * @return true if one of the player's other characters in the world follows them
	 */
	public static boolean leadsParty(final MOB leader)
	{
		if((leader == null) || (profileOf(leader) == null))
			return false;
		for(final MOB M : othersInWorld(leader))
		{
			if(leaderOf(M) == leader)
				return true;
		}
		return false;
	}

	/**
	 * In turn-based time, decides what a character's next command that takes
	 * time may do: go ahead and move the world (the leader, or anyone not in
	 * a party), go ahead as an order on the leader's turn, or wait for it.
	 * @param mob the character
	 * @return what the command may do
	 */
	public static Turn turnOf(final MOB mob)
	{
		if(acting != null)
			return (acting == mob) ? Turn.ORDER : Turn.WAIT;
		final MOB leader = leaderOf(mob);
		if(!leadsParty(leader))
			return Turn.LEAD;
		if(mob != leader)
		{
			if(told.add(mob))
				tellPlayer(mob, CMLib.lang().L("@x1 will do that on @x2's turn.", mob.Name(), leader.Name()));
			return Turn.WAIT;
		}
		if(turns == Turns.ALL)
		{
			final StringBuilder missing = new StringBuilder("");
			for(final MOB M : inOrder(leader))
			{
				if((M != leader) && (!isOnTheirOwn(M)) && (!M.amDead()) && (M.commandQueSize() == 0) && (!waited.contains(M)))
					missing.append((missing.length() > 0) ? ", " : "").append(M.Name());
			}
			if(missing.length() > 0)
			{
				if(!toldWaiting)
					tellPlayer(leader, CMLib.lang().L("The turn waits for @x1 to be given something to do (AS NAME COMMAND), or to WAIT.", missing.toString()));
				toldWaiting = true;
				return Turn.WAIT;
			}
		}
		return Turn.LEAD;
	}

	/**
	 * Called just before a party's leader does something that moves the
	 * world: the members whose orders come first carry them out.  In
	 * {@link Turns#LEADER} that is all of them; in {@link Turns#ALL}, those
	 * before the leader in the party's order.
	 * @param leader the leader
	 */
	public static void beforeLeaderActs(final MOB leader)
	{
		final List<MOB> all = inOrder(leader);
		if(all.size() < 2)
			return;
		final int at = all.indexOf(leader);
		for(int i = 0; i < all.size(); i++)
		{
			if((i != at) && ((turns == Turns.LEADER) || (i < at)))
				carryOut(all.get(i));
		}
	}

	/**
	 * Called just after a party's leader has done something that moves the
	 * world, before it moves: in {@link Turns#ALL}, the members after the
	 * leader in the party's order carry out their orders.  The turn is over.
	 * @param leader the leader
	 */
	public static void afterLeaderActs(final MOB leader)
	{
		if(turns == Turns.ALL)
		{
			final List<MOB> all = inOrder(leader);
			for(int i = all.indexOf(leader) + 1; i < all.size(); i++)
				carryOut(all.get(i));
		}
		endTurn();
		ScriptEvents.turn(leader); // ST6
	}

	/**
	 * Has a party's leader pass their turn, as WAIT does: every member with
	 * an order carries it out, in the party's order.
	 * @param leader the leader
	 */
	public static void passTurn(final MOB leader)
	{
		if(!leadsParty(leader))
		{
			// ST6: one on their own passes a turn too, in turn-based time
			if(GameTime.isTurnBased())
				ScriptEvents.turn(leader);
			return;
		}
		for(final MOB M : inOrder(leader))
		{
			if(M != leader)
				carryOut(M);
		}
		endTurn();
		if(GameTime.isTurnBased())
			ScriptEvents.turn(leader); // ST6
	}

	/**
	 * Has a party member WAIT: in {@link Turns#ALL} the turn need not wait for
	 * an order for them.
	 * @param M the member
	 * @return true if M is a member who is not the leader, so waits for the turn
	 */
	public static synchronized boolean waitForTurn(final MOB M)
	{
		final MOB leader = leaderOf(M);
		if((M == leader) || (!leadsParty(leader)))
			return false;
		waited.add(M);
		return true;
	}

	private static synchronized void endTurn()
	{
		waited.clear();
		told.clear();
		toldWaiting = false;
	}

	/**
	 * Has a member carry out their next order, if they have one and can do
	 * it now, shown on the terminal.  One still busy with a long action, or
	 * without the actions, is left for a later turn.
	 * @param M the member
	 */
	private static void carryOut(final MOB M)
	{
		if(M.amDead() || (M.commandQueSize() == 0))
			return;
		synchronized(Party.class)
		{
			acting = M;
		}
		try
		{
			actWithTerminal(M, new Runnable()
			{
				@Override
				public void run()
				{
					M.dequeCommand();
				}
			});
		}
		finally
		{
			synchronized(Party.class)
			{
				acting = null;
			}
		}
	}

	/**
	 * Returns how far the world moves on after a leader's action: by as long
	 * as it took, or, in {@link Turns#ALL}, by at least a round.
	 * @param leader the leader
	 * @param ms how long the action took
	 * @return how far the world moves, in milliseconds
	 */
	public static long turnMillis(final MOB leader, final long ms)
	{
		if((turns == Turns.ALL) && leadsParty(leader))
			return Math.max(ms, CMProps.getTickMillis());
		return ms;
	}

	/**
	 * Returns the terminal's session: the one the player types into.
	 * @param mob any of the player's characters
	 * @return the session, or null
	 */
	public static Session terminalOf(final MOB mob)
	{
		final PlayerAccount acct = profileOf(mob);
		for(final Session S : CMLib.sessions().allIterable())
		{
			if((!isPartySession(S)) && (S.mob() != null) && (profileOf(S.mob()) == acct))
				return S;
		}
		return null;
	}

	/**
	 * Tells the player something, on the terminal.
	 * @param mob any of the player's characters
	 * @param msg what to tell
	 */
	private static void tellPlayer(final MOB mob, final String msg)
	{
		if(isWindowed(mob))
		{
			mob.tell(msg);
			return;
		}
		final Session T = terminalOf(mob);
		if((T != null) && (T.mob() != null))
			T.mob().tell(msg);
	}

	/**
	 * Runs something one of the player's characters does with the terminal's
	 * session, so that the player sees it: the character and the one being
	 * played swap sessions for it.  One with a window of their own does it
	 * there.
	 * @param M the character
	 * @param r what they do
	 */
	public static void actWithTerminal(final MOB M, final Runnable r)
	{
		if(isWindowed(M))
		{
			r.run(); // their own window shows it
			return;
		}
		final Session T = terminalOf(M);
		final Session mine = M.session();
		final MOB played = (T == null) ? null : T.mob();
		if((T == null) || (mine == null) || (mine == T) || (played == null))
		{
			r.run();
			return;
		}
		synchronized(T)
		{
			M.setSession(T);
			T.setMob(M);
			played.setSession(mine);
			mine.setMob(played);
		}
		try
		{
			r.run();
		}
		finally
		{
			synchronized(T)
			{
				M.setSession(mine);
				mine.setMob(M);
				played.setSession(T);
				T.setMob(played);
			}
		}
	}

	/**
	 * Hands the lead of a party to one of its members: they follow no one,
	 * and the rest of the party, the old leader too, follow them.
	 * @param leader the party's leader
	 * @param to the new leader
	 */
	public static void handOver(final MOB leader, final MOB to)
	{
		final List<MOB> members = inOrder(leader);
		to.setFollowing(null);
		for(final MOB M : members)
		{
			if(M != to)
				M.setFollowing(to);
		}
		ScriptEvents.party("LEAD", to, to); // ST10
	}

	/**
	 * Returns whether a character is in the world without the terminal: a
	 * party member, or one left in the world.
	 * @param M the character
	 * @return true if it is on a session of its own
	 */
	public static boolean isBackground(final MOB M)
	{
		final Session S = (M == null) ? null : M.session();
		return isPartySession(S);
	}

	/**
	 * Returns the profile a character belongs to.
	 * @param mob the character
	 * @return the profile, or null
	 */
	public static PlayerAccount profileOf(final MOB mob)
	{
		return ((mob == null) || (mob.playerStats() == null)) ? null : mob.playerStats().getAccount();
	}

	/**
	 * Returns the characters of a character's profile who are in the world,
	 * other than that one.
	 * @param mob the character
	 * @return the others in the world
	 */
	public static List<MOB> othersInWorld(final MOB mob)
	{
		final List<MOB> others = new ArrayList<MOB>();
		final PlayerAccount acct = profileOf(mob);
		if(acct == null)
			return others;
		for(final Session S : CMLib.sessions().allIterable())
		{
			final MOB M = S.mob();
			if((M != null)
			&& (M != mob)
			&& (profileOf(M) == acct)
			&& (!others.contains(M))
			&& CMLib.flags().isInTheGame(M, true))
				others.add(M);
		}
		return others;
	}

	/**
	 * Finds one of a character's profile in the world by name, other than
	 * that one: the whole name first, then the start of one.
	 * @param mob the character
	 * @param name the name, or its start
	 * @return the one found, or null
	 */
	public static MOB findInWorld(final MOB mob, final String name)
	{
		final List<MOB> others = othersInWorld(mob);
		for(final MOB M : others)
		{
			if(M.Name().equalsIgnoreCase(name))
				return M;
		}
		for(final MOB M : others)
		{
			if(M.Name().toUpperCase().startsWith(name.toUpperCase()))
				return M;
		}
		return null;
	}

	/**
	 * Returns the leader of the party a character is in: the one at the head
	 * of the group they follow.
	 * @param mob the character
	 * @return the leader, which is the character if they follow no one
	 */
	public static MOB leaderOf(final MOB mob)
	{
		final MOB leader = mob.getGroupLeader();
		return (leader == null) ? mob : leader;
	}

	/**
	 * Returns whether a character is in a party: the leader of the group, or
	 * following them.
	 * @param leader the party's leader
	 * @param M the character
	 * @return true if M is in the leader's party
	 */
	public static boolean isInParty(final MOB leader, final MOB M)
	{
		return (M == leader) || (leaderOf(M) == leader);
	}

	/**
	 * Brings one of the profile's characters, resting out of the world, into
	 * it on a session of their own.
	 * @param mob a character of the profile who is in the world
	 * @param name the character to bring in
	 * @param room where they come in, or null for where they last were
	 * @return the character, now in the world, or null if they could not come
	 */
	public static MOB bringIn(final MOB mob, final String name, final Room room)
	{
		final PlayerAccount acct = profileOf(mob);
		final Session console = mob.session();
		if((acct == null) || (console == null))
			return null;
		final String limit=WorldRules.activeRefusal(acct);
		if(limit!=null) { mob.tell(limit); return null; }
		final MOB M = CMLib.players().getLoadPlayer(name);
		if((M == null) || (profileOf(M) != acct) || CMLib.flags().isInTheGame(M, true))
			return null;
		if(room != null)
			M.setLocation(room); // where the login puts them
		final Session S = (Session)CMClass.getCommon(windows ? "WindowSession" : "PartySession");
		if((S == null) || (!S.initialize(null, console.getGroupName())))
			return null;
		S.setAccount(acct);
		if(!S.autoLogin(M.Name(), null))
		{
			S.stopSession(true, false, false, false);
			return null;
		}
		CMLib.sessions().add(S);
		return M;
	}

	/**
	 * Sends a character in the world without the terminal back to rest in
	 * the profile, out of the world.
	 * @param M the character
	 */
	public static void rest(final MOB M)
	{
		if(!isBackground(M))
			return;
		final Session S = M.session();
		if(leadsParty(M))
		{
			final List<MOB> all = inOrder(M);
			all.remove(M);
			handOver(M, all.get(0));
		}
		ScriptEvents.party("REST", M, leaderOf(M)); // ST10
		M.setFollowing(null);
		CMLib.players().savePlayer(M);
		S.logout(true);
		S.stopSession(true, false, true, false);
		CMLib.sessions().remove(S);
	}

	/**
	 * Frees a character in the world from their own session, so that the
	 * terminal's session can play them.
	 * @param M the character
	 */
	public static void release(final MOB M)
	{
		if(!isBackground(M))
			return;
		final Session S = M.session();
		S.setMob(null);
		M.setSession(null);
		S.stopSession(true, false, true, false);
		CMLib.sessions().remove(S);
	}
}
