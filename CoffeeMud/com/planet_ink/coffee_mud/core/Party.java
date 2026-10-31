package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.List;

import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 */
public final class Party
{
	/** whether a character added to the party comes back where they last were */
	private static volatile boolean	meetWhereLeft	= false;
	/** whether the player's characters may fight each other */
	private static volatile boolean	fightEachOther	= false;

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
		return meetWhereLeft;
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
	 * Returns whether a character is in the world without the terminal: a
	 * party member, or one left in the world.
	 * @param M the character
	 * @return true if it is on a session of its own
	 */
	public static boolean isBackground(final MOB M)
	{
		final Session S = (M == null) ? null : M.session();
		return (S != null) && "PartySession".equals(S.ID());
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
		final MOB M = CMLib.players().getLoadPlayer(name);
		if((M == null) || (profileOf(M) != acct) || CMLib.flags().isInTheGame(M, true))
			return null;
		if(room != null)
			M.setLocation(room); // where the login puts them
		final Session S = (Session)CMClass.getCommon("PartySession");
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
