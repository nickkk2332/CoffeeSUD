package com.planet_ink.coffee_mud.Commands;
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
 * CR13: HOME takes the whole party to the leader's home (a room with
 * Prop_HomeBeacon where they slept, or HOME SET), out of combat, in
 * HOMERECALL turns.  HOME SET makes the room here the party's home.
 */
public class Home extends StdCommand
{
	public Home()
	{
	}

	private final String[]	access	= I(new String[] { "HOME" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags) throws java.io.IOException
	{
		if(mob == null)
			return false;
		final String word = (commands.size() > 1) ? commands.get(1).toUpperCase().trim() : "";
		if(word.equals("SET"))
		{
			final Room R = mob.location();
			if(!com.planet_ink.coffee_mud.Abilities.Properties.Prop_HomeBeacon.isBeacon(R))
			{
				mob.tell(L("This is no home: a home is a room with Prop_HomeBeacon, or owned land where the world says so (HOMEBEACONS)."));
				return false;
			}
			if(!CMLib.law().doesHavePriviledgesHere(mob, R))
			{
				mob.tell(L("This is not yours to call home."));
				return false;
			}
			if(com.planet_ink.coffee_mud.Abilities.Properties.Prop_HomeBeacon.setHome(mob, R, false).size() == 0)
				mob.tell(L("This is already your home."));
			return false;
		}
		if(word.length() > 0)
		{
			mob.tell(L("HOME takes the party home; HOME SET makes this room its home."));
			return false;
		}
		final int turns = com.planet_ink.coffee_mud.Abilities.Properties.Prop_HomeBeacon.recallTurns();
		if(turns <= 0)
		{
			mob.tell(L("Going home is off in this world."));
			return false;
		}
		final MOB leader = Party.leaderOf(mob);
		final Room home = ((leader.playerStats() == null) ? mob : leader).getStartRoom();
		if((home == null) || (!com.planet_ink.coffee_mud.Abilities.Properties.Prop_HomeBeacon.isBeacon(home)))
		{
			mob.tell(L("You have no home yet: sleep in one, or HOME SET there."));
			return false;
		}
		if(mob.location() == home)
		{
			mob.tell(L("You are home."));
			return false;
		}
		for(final MOB M : com.planet_ink.coffee_mud.Abilities.Properties.Prop_HomeBeacon.party(mob))
		{
			if((M != null) && M.isInCombat())
			{
				mob.tell(L("Not while there is fighting."));
				return false;
			}
		}
		if(com.planet_ink.coffee_mud.Abilities.Misc.GoingHome.start(mob, home, turns) == null)
		{
			mob.tell(L("You are on your way home already."));
			return false;
		}
		mob.tell(L("You set off for home: @x1 turn(s).", "" + turns));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
