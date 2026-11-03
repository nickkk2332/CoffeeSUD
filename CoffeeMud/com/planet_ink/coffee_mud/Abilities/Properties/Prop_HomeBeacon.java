package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import java.util.*;

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
 * CR13: a home.  Sleeping here (or HOME SET) makes it the start room of
 * every character of the party who has privileges here, so Recall and HOME
 * bring them back to it.  With HOMEBEACONS=true in the ini, every room with
 * a land title that is owned counts as one.  HOMERECALL=n (3 by default, 0
 * for none) is how many turns HOME takes.
 */
public class Prop_HomeBeacon extends Property
{
	@Override
	public String ID()
	{
		return "Prop_HomeBeacon";
	}

	@Override
	public String name()
	{
		return "Home";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "A home: sleeping here makes it the party's start room";
	}

	/**
	 * @param R a room
	 * @return whether it is a home: Prop_HomeBeacon, or owned land under HOMEBEACONS=true
	 */
	public static boolean isBeacon(final Room R)
	{
		if(R == null)
			return false;
		if(R.fetchEffect("Prop_HomeBeacon") != null)
			return true;
		final CMProps P = CMProps.instance();
		if((P == null) || (!CMath.s_bool(P.getStr("HOMEBEACONS", "false").trim())))
			return false;
		final LandTitle T = CMLib.law().getLandTitle(R);
		return (T != null) && (T.getOwnerName() != null) && (T.getOwnerName().length() > 0);
	}

	/**
	 * @return how many turns HOME takes: HOMERECALL in the ini, 3 by default, 0 for no HOME
	 */
	public static int recallTurns()
	{
		final CMProps P = CMProps.instance();
		final String s = (P == null) ? "" : P.getStr("HOMERECALL", "").trim();
		return (s.length() == 0) ? 3 : Math.max(0, CMath.s_int(s));
	}

	/**
	 * @param mob one of a party
	 * @return the party: its leader and characters (Party), and everyone in the leader's group
	 */
	public static List<MOB> party(final MOB mob)
	{
		final Set<MOB> all = new LinkedHashSet<MOB>();
		if(mob == null)
			return new ArrayList<MOB>(all);
		final MOB leader = Party.leaderOf(mob);
		all.addAll(Party.inOrder(leader));
		all.addAll(leader.getGroupMembers(new LinkedHashSet<MOB>()));
		all.add(mob);
		return new ArrayList<MOB>(all);
	}

	/**
	 * Makes a home the start room of the party's characters with privileges there.
	 * @param mob one of the party
	 * @param R the home
	 * @param quiet true to tell no one
	 * @return the characters whose home it now is
	 */
	public static List<MOB> setHome(final MOB mob, final Room R, final boolean quiet)
	{
		final List<MOB> set = new ArrayList<MOB>();
		if((mob == null) || (R == null))
			return set;
		for(final MOB M : party(mob))
		{
			if((M == null) || (M.playerStats() == null) || (!CMLib.law().doesHavePriviledgesHere(M, R)))
				continue;
			if(M.getStartRoom() != R)
			{
				M.setStartRoom(R);
				set.add(M);
				if(!quiet)
					M.tell(CMLib.lang().L("@x1 is your home now.", R.displayText(M)));
			}
		}
		return set;
	}

	/**
	 * After SLEEP: a home of the sleeper's is the party's.
	 * @param mob who went to sleep
	 */
	public static void slept(final MOB mob)
	{
		if((mob == null) || (mob.playerStats() == null) || (!CMLib.flags().isSleeping(mob)))
			return;
		final Room R = mob.location();
		if(isBeacon(R) && CMLib.law().doesHavePriviledgesHere(mob, R))
			setHome(mob, R, false);
	}
}
