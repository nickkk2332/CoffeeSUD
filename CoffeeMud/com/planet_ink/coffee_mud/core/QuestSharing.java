package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.PlayerAccount;
import com.planet_ink.coffee_mud.Common.interfaces.Quest;
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
 * Q12: whose a quest is, as its SET SHARED says.  NONE: each character's
 * own, as before.  PARTY: accepting gives it to the party members present
 * too (the players' characters of the group), and MPQUESTWIN and
 * MPQUESTFAIL go to the party members on it.  PROFILE: a win, the status
 * and the wins of any character of the profile answer for all of them
 * (QUESTWINNER, QUESTSTATUS, QUESTWINS, the masks, REPEATABLE), and its
 * journal is read by all of them.
 */
public final class QuestSharing
{
	/** each character's own */
	public static final String	NONE	= "NONE";
	/** the party's */
	public static final String	PARTY	= "PARTY";
	/** the whole profile's */
	public static final String	PROFILE	= "PROFILE";

	private static final ThreadLocal<Boolean>	spreading	= ThreadLocal.withInitial(() -> Boolean.FALSE);

	private QuestSharing()
	{
	}

	/**
	 * @param Q a quest
	 * @return NONE, PARTY or PROFILE
	 */
	public static String of(final Quest Q)
	{
		if(Q == null)
			return NONE;
		final String s = Q.getStat("SHARED");
		return ((s == null) || (s.trim().length() == 0)) ? NONE : s.trim().toUpperCase();
	}

	/**
	 * @param quest a quest's name
	 * @return NONE, PARTY or PROFILE
	 */
	public static String of(final String quest)
	{
		return (quest == null) ? NONE : of(CMLib.quests().fetchQuest(quest));
	}

	/**
	 * @param quest a quest's name
	 * @return true if it is SHARED PROFILE
	 */
	public static boolean isProfile(final String quest)
	{
		return PROFILE.equals(of(quest));
	}

	/**
	 * Runs something that hands a quest on to the party, not handing on
	 * again from within it.
	 * @param r what to run
	 */
	public static void spread(final Runnable r)
	{
		if(spreading.get().booleanValue())
			return;
		spreading.set(Boolean.TRUE);
		try
		{
			r.run();
		}
		finally
		{
			spreading.set(Boolean.FALSE);
		}
	}

	// ------------------------------------------------------------ the party

	private static List<MOB> party(final MOB mob, final boolean here)
	{
		final List<MOB> L = new ArrayList<MOB>();
		if(mob == null)
			return L;
		for(final MOB M : mob.getGroupMembers(new HashSet<MOB>()))
		{
			if((M == null) || (M == mob) || M.amDestroyed() || (M.playerStats() == null))
				continue;
			if(here && ((M.location() == null) || (M.location() != mob.location())))
				continue;
			L.add(M);
		}
		Collections.sort(L, (a, b) -> a.Name().compareToIgnoreCase(b.Name()));
		return L;
	}

	/**
	 * PARTY: the others of a character's party in the room with them, who
	 * take the quest on with them.
	 * @param Q the quest
	 * @param mob the one who accepted it
	 * @return the others, none if the quest is not the party's
	 */
	public static List<MOB> partyHere(final Quest Q, final MOB mob)
	{
		if(!PARTY.equals(of(Q)))
			return new ArrayList<MOB>();
		return party(mob, true);
	}

	/**
	 * PARTY: the others of a character's party who are on the quest, who
	 * win or fail it with them.
	 * @param Q the quest
	 * @param mob the one named
	 * @return the others, none if the quest is not the party's
	 */
	public static List<MOB> partyOn(final Quest Q, final MOB mob)
	{
		final List<MOB> L = new ArrayList<MOB>();
		if(!PARTY.equals(of(Q)))
			return L;
		for(final MOB M : party(mob, false))
		{
			if(QuestMarkers.accepted(M, Q))
				L.add(M);
		}
		return L;
	}

	// ------------------------------------------------------------ the profile

	private static PlayerAccount profileOf(final String who)
	{
		final PlayerAccount A = Party.profileOf(CMLib.players().getPlayerAllHosts(who));
		if(A != null)
			return A;
		for(final Enumeration<PlayerAccount> e = CMLib.players().accounts(); e.hasMoreElements();)
		{
			final PlayerAccount P = e.nextElement();
			if((P != null) && (P.findPlayer(who) != null))
				return P;
		}
		return null;
	}

	/**
	 * @param who a character's name
	 * @return the names of the characters of their profile, theirs first
	 */
	public static List<String> profileNames(final String who)
	{
		final List<String> names = new ArrayList<String>();
		if((who == null) || (who.trim().length() == 0))
			return names;
		names.add(who);
		final PlayerAccount A = profileOf(who);
		if(A == null)
			return names;
		for(final Enumeration<String> e = A.getPlayers(); e.hasMoreElements();)
		{
			final String n = e.nextElement();
			boolean have = (n == null);
			for(final String o : names)
				have = have || o.equalsIgnoreCase(n);
			if(!have)
				names.add(n);
		}
		return names;
	}

	/**
	 * @param quest a quest's name
	 * @param who a character's name
	 * @return the names whose records answer for them: their profile's for
	 *         a PROFILE quest, else theirs
	 */
	public static List<String> creditNames(final String quest, final String who)
	{
		if(isProfile(quest))
			return profileNames(who);
		final List<String> one = new ArrayList<String>();
		if(who != null)
			one.add(who);
		return one;
	}

	/**
	 * @param mob a character
	 * @return the other characters of their profile in the game
	 */
	public static List<MOB> profileMates(final MOB mob)
	{
		final List<MOB> L = new ArrayList<MOB>();
		if(mob == null)
			return L;
		for(final String n : profileNames(mob.Name()))
		{
			if(n.equalsIgnoreCase(mob.Name()))
				continue;
			final MOB M = CMLib.players().getPlayerAllHosts(n);
			if((M != null) && (M != mob) && (!M.amDestroyed()))
				L.add(M);
		}
		return L;
	}
}
