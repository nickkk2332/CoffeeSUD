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
 * PARTYTURNS shows how a party's turns go in turn-based time;
 * PARTYTURNS LEADER and PARTYTURNS ALL change it.  Only the party's leader
 * moves the world.  With LEADER, the others' orders are carried out as soon
 * as the leader acts, before them.  With ALL, the leader's action waits
 * until every member not acting on their own has an order, or has WAITed,
 * then all act in the party's order, and the world moves on by a round.
 * See {@link Party}.
 */
public class PartyTurnsCmd extends StdCommand
{
	public PartyTurnsCmd()
	{
	}

	private final String[] access = I(new String[] { "PARTYTURNS" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() > 1)
		{
			final String way = commands.get(1).toUpperCase();
			if(way.equals("LEADER") || way.equals("LEAD"))
				Party.setTurns(Party.Turns.LEADER);
			else
			if(way.equals("ALL"))
				Party.setTurns(Party.Turns.ALL);
			else
			{
				mob.tell(L("PARTYTURNS LEADER or PARTYTURNS ALL?"));
				return false;
			}
		}
		if(Party.getTurns() == Party.Turns.ALL)
			mob.tell(L("In turn-based time, the party's turn waits until each of them has something to do (AS NAME COMMAND), or WAITs, then they act in order (PARTY ORDER), and the world moves on by a round.  PARTYTURNS LEADER has the others' orders carried out whenever the leader acts."));
		else
			mob.tell(L("In turn-based time, the party's leader moves the world: whenever they act, the others carry out their orders (AS NAME COMMAND) first.  PARTYTURNS ALL has the turn wait for an order for each of them."));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMProps.isSinglePlayer() && CMProps.isUsingAccountSystem();
	}
}
