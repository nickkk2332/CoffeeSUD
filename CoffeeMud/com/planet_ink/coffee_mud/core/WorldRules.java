package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import com.planet_ink.coffee_mud.Common.interfaces.PlayerAccount;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
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
 * P10: the world's rules for the profile and the party, set in coffeemud.ini
 * or world.ini: PARTYMAX, PROFILEMAX, NEWCHARS (ALWAYS, FIRSTONLY, CHEAT or
 * MASK, with NEWCHARMASK checked on the leader and NEWCHARMESSAGE),
 * PARTYROLES, and CHEAT ON/OFF, kept in the save.
 */
public class WorldRules
{
	private WorldRules()
	{
	}

	private static volatile boolean cheats = false;

	/** @return true while CHEAT is ON */
	public static boolean cheatsOn()
	{
		return cheats;
	}

	/**
	 * CHEAT ON or OFF, kept in the save.
	 * @param on true for ON
	 */
	public static void setCheats(final boolean on)
	{
		cheats = on;
	}

	private static String str(final String key, final String dflt)
	{
		final CMProps P = CMProps.instance();
		return (P == null) ? dflt : P.getStr(key, dflt).trim();
	}

	/** @return PARTYMAX, the most characters in the party at once, 0 for no limit */
	public static int partyMax()
	{
		return Math.max(0, CMath.s_int(str("PARTYMAX", "0")));
	}

	/** @return the world limit on active profile characters, including separate groups. */
	public static int activeMax() { return Math.max(0,CMath.s_int(str("ACTIVECHARMAX","0"))); }

	/** All live profile characters, including separate groups and windows. */
	public static int activeCount(final PlayerAccount account)
	{
		final java.util.Set<MOB> live=new java.util.HashSet<MOB>();
		if(account!=null) for(final com.planet_ink.coffee_mud.Common.interfaces.Session S : CMLib.sessions().allIterable())
		{
			final MOB M=S.mob();
			if(M!=null&&Party.profileOf(M)==account&&CMLib.flags().isInTheGame(M,true)) live.add(M);
		}
		return live.size();
	}

	public static String activeRefusal(final PlayerAccount account)
	{
		final int max=activeMax();
		return max>0&&activeCount(account)>=max?CMLib.lang().L("This world allows at most @x1 of your characters in the world at once. Rest a character before bringing in another.",""+max):null;
	}

	/** The world's summoning rule; a player's PARTY MEET preference cannot relax it. */
	public static String partySummon()
	{
		final String rule = str("PARTYSUMMON", "ALLOW").toUpperCase();
		if(rule.equals("ALLOW") || rule.equals("MASK"))
			return rule;
		return "MEET";
	}

	/** Why a character elsewhere cannot be summoned here, or null. */
	public static String summonRefusal(final MOB caller)
	{
		if(partySummon().equals("MEET"))
			return CMLib.lang().L("This world requires you to meet your companions where they are.");
		if(partySummon().equals("MASK"))
		{
			final String mask = str("PARTYSUMMONMASK", "");
			if((mask.length() == 0) || (caller == null) || !CMLib.masking().maskCheck(mask, caller, true))
			{
				final String msg = str("PARTYSUMMONMESSAGE", "");
				return msg.length() > 0 ? msg : CMLib.lang().L("You cannot summon companions here.");
			}
		}
		return null;
	}

	/** Check the combined profile group after a FOLLOW, including the follower's followers. */
	public static String followRefusal(final MOB follower, final MOB leader)
	{
		final PlayerAccount acct = Party.profileOf(follower);
		if((acct == null) || (acct != Party.profileOf(leader)) || (partyMax() == 0))
			return null;
		final java.util.Set<MOB> group = leader.getGroupMembers(new java.util.HashSet<MOB>());
		group.addAll(follower.getGroupMembers(new java.util.HashSet<MOB>()));
		int count = 0;
		for(final MOB member : group)
			if(Party.profileOf(member) == acct)
				count++;
		return partyRefusal(acct, count - 1, 0, false);
	}

	/** @return PROFILEMAX, the most characters in the profile, 0 for no limit */
	public static int profileMax()
	{
		return Math.max(0, CMath.s_int(str("PROFILEMAX", "0")));
	}

	/** @return NEWCHARS: ALWAYS, FIRSTONLY, CHEAT or MASK */
	public static String newChars()
	{
		final String v = str("NEWCHARS", "ALWAYS").toUpperCase();
		if(v.startsWith("FIRST"))
			return "FIRSTONLY";
		if(v.startsWith("CHEAT"))
			return "CHEAT";
		if(v.startsWith("MASK"))
			return "MASK";
		return "ALWAYS";
	}

	/**
	 * The character a profile played last: the leader NEWCHARMASK is checked on.
	 * @param acct the profile
	 * @return the character, or null
	 */
	public static MOB lastPlayed(final PlayerAccount acct)
	{
		MOB best = null;
		long when = -1;
		if(acct == null)
			return null;
		for(final Enumeration<String> p = acct.getPlayers(); p.hasMoreElements();)
		{
			final MOB M = CMLib.players().getLoadPlayer(p.nextElement());
			if((M == null) || (M.playerStats() == null))
				continue;
			if(CMLib.flags().isInTheGame(M, true))
				return M;
			final long t = M.playerStats().getLastDateTime();
			if(t > when)
			{
				when = t;
				best = M;
			}
		}
		return best;
	}

	/**
	 * Why a profile may not make a new character now: PROFILEMAX, or NEWCHARS
	 * once it has one.
	 * @param acct the profile
	 * @param leader its leader, for NEWCHARS=MASK, or null to find it
	 * @return what to say, or null if it may
	 */
	public static String newCharRefusal(final PlayerAccount acct, MOB leader)
	{
		if(acct == null)
			return null;
		final int have = acct.numPlayers();
		final int max = profileMax();
		if((max > 0) && (have >= max))
			return CMLib.lang().L("You may only have @x1 character@x2 in this world.", "" + max, (max == 1) ? "" : "s");
		if(have == 0)
			return null; // the first is always made
		final String msg = str("NEWCHARMESSAGE", "");
		final String activeLimit=activeRefusal(acct);
		if(activeLimit!=null) return activeLimit;
		final String rule = newChars();
		if(rule.equals("FIRSTONLY"))
			return (msg.length() > 0) ? msg : CMLib.lang().L("In this world you play the one character you made first.");
		if(rule.equals("CHEAT") && (!cheatsOn()))
			return (msg.length() > 0) ? msg : CMLib.lang().L("New characters are made only with CHEAT ON.");
		if(rule.equals("MASK"))
		{
			final String mask = str("NEWCHARMASK", "");
			if(mask.length() == 0)
				return null;
			if(leader == null)
				leader = lastPlayed(acct);
			if(leader == null)
				return null;
			final Room was = leader.location();
			if(was == null)
				leader.setLocation(leader.getStartRoom());
			try
			{
				if(!CMLib.masking().maskCheck(mask, leader, true))
					return (msg.length() > 0) ? msg : CMLib.lang().L("New characters can be made only when @x1.", CMLib.masking().maskDesc(mask, true));
			}
			finally
			{
				if(was == null)
					leader.setLocation(null);
			}
		}
		return null;
	}

	/**
	 * Why one more character cannot come into the party: PARTYMAX, or
	 * MAXCONNSPERACCOUNT for one not in the world yet.
	 * @param acct the profile
	 * @param partySize how many are in the party now
	 * @param inWorld how many of the profile's characters are in the world
	 * @param comesIn true if the character is not in the world yet
	 * @return what to say, or null if they may
	 */
	public static String partyRefusal(final PlayerAccount acct, final int partySize, final int inWorld, final boolean comesIn)
	{
		final int max = partyMax();
		if((max > 0) && (partySize >= max))
			return CMLib.lang().L("Your party may have at most @x1 character@x2 in it.", "" + max, (max == 1) ? "" : "s");
		if(comesIn && (acct != null))
		{
			final int activeMax = activeMax();
			if((activeMax > 0) && (inWorld >= activeMax))
				return CMLib.lang().L("This world allows at most @x1 of your characters in the world at once.", "" + activeMax);
			int conns = CMProps.getIntVar(CMProps.Int.MAXCONNSPERACCOUNT);
			if(conns > 0)
				conns += acct.getBonusCharsOnlineLimit();
			if((conns > 0) && (inWorld >= conns)
			&& (!CMSecurity.isDisabled(CMSecurity.DisFlag.MAXCONNSPERACCOUNT))
			&& (!acct.isSet(PlayerAccount.AccountFlag.MAXCONNSOVERRIDE)))
				return CMLib.lang().L("You may only have @x1 of your characters on at one time.", "" + CMProps.getIntVar(CMProps.Int.MAXCONNSPERACCOUNT));
		}
		return null;
	}

	/**
	 * @param acct a profile
	 * @return how many of its characters are in the world
	 */
	public static int inWorld(final PlayerAccount acct)
	{
		int n = 0;
		for(final Session S : CMLib.sessions().allIterable())
		{
			final MOB M = S.mob();
			if((M != null) && (M.playerStats() != null) && (M.playerStats().getAccount() == acct) && CMLib.flags().isInTheGame(M, true))
				n++;
		}
		return n;
	}

	/**
	 * The PARTYROLES no character of a group fills.
	 * @param mobs the party, or the profile's characters
	 * @return the roles' names
	 */
	public static List<String> missingRoles(final List<MOB> mobs)
	{
		final List<String> missing = new ArrayList<String>();
		for(final String entry : str("PARTYROLES", "").split(";"))
		{
			final int x = entry.indexOf(':');
			if(x <= 0)
				continue;
			final String role = entry.substring(0, x).trim();
			final String mask = entry.substring(x + 1).trim();
			boolean filled = false;
			for(final MOB M : mobs)
			{
				if((M != null) && ((mask.length() == 0) || CMLib.masking().maskCheck(mask, M, true)))
					filled = true;
			}
			if((!filled) && (role.length() > 0))
				missing.add(role);
		}
		return missing;
	}

	/**
	 * @param mobs the party, or the profile's characters
	 * @return a warning of the roles none of them fills, or ""
	 */
	public static String roleWarning(final List<MOB> mobs)
	{
		final List<String> missing = missingRoles(mobs);
		if(missing.size() == 0)
			return "";
		final StringBuilder s = new StringBuilder("");
		for(int i = 0; i < missing.size(); i++)
		{
			if(i > 0)
				s.append((i == missing.size() - 1) ? " or " : ", ");
			s.append(missing.get(i));
		}
		return CMLib.lang().L("Your party has no @x1.", s.toString());
	}

	/**
	 * @param acct a profile
	 * @return its characters, loaded
	 */
	public static List<MOB> profileCharacters(final PlayerAccount acct)
	{
		final List<MOB> list = new ArrayList<MOB>();
		if(acct == null)
			return list;
		for(final Enumeration<String> p = acct.getPlayers(); p.hasMoreElements();)
		{
			final MOB M = CMLib.players().getLoadPlayer(p.nextElement());
			if(M != null)
				list.add(M);
		}
		return list;
	}
}
