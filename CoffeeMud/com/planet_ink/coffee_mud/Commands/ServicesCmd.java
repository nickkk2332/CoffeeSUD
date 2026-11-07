package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
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
 * P04 and P08: SERVICES lists the services of those here (core/Services);
 * POOLS shows one's organizations' shared pools, and POOLS GIVE [n] [pool]
 * (TO [organization]) gives to one (core/OrgPools).
 */
public class ServicesCmd extends StdCommand
{
	private final String[] access=I(new String[]{"SERVICES","POOLS"});
	@Override public String[] getAccessWords()
	{
		return access;
	}
	@Override public boolean canBeOrdered()
	{
		return false;
	}
	@Override public double actionsCost(final MOB mob,final List<String> words)
	{
		return 0;
	}
	@Override public double combatActionsCost(final MOB mob,final List<String> words)
	{
		return 0;
	}
	@Override public boolean execute(final MOB mob,final List<String> words,final int flags)throws java.io.IOException
	{
		try
		{
			if(words.get(0).toUpperCase(Locale.ROOT).startsWith("S"))
				mob.tell(Services.list(mob));
			else
			if((words.size()>=4)&&words.get(1).equalsIgnoreCase("GIVE"))
			{
				Clan C=null;
				final int to=words.size()>5&&words.get(4).equalsIgnoreCase("TO")?5:-1;
				if(to>0)
					C=CMLib.clans().findClan(CMParms.combine(words,to));
				else
					C=Organizations.firstClan(mob);
				if(C==null)
					throw new java.io.IOException("give to an organization you are in: POOLS GIVE [n] [pool] (TO [organization])");
				mob.tell(OrgPools.give(mob,C,words.get(3),CMath.s_long(words.get(2))));
			}
			else
				mob.tell(OrgPools.show(mob));
		}
		catch(final java.io.IOException e)
		{
			mob.tell("Pools: "+e.getMessage());
		}
		return false;
	}
}
