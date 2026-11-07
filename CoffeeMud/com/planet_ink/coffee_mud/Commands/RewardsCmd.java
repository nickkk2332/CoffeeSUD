package com.planet_ink.coffee_mud.Commands;

import java.util.List;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;

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
/** I18: REWARDS, the character's rewards in escrow, and REWARDS CLAIM.  It takes no game time. */
public class RewardsCmd extends StdCommand
{
	private final String[] access = I(new String[] { "REWARDS" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public double actionsCost(final MOB mob, final List<String> words)
	{
		return 0;
	}

	@Override
	public double combatActionsCost(final MOB mob, final List<String> words)
	{
		return 0;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> words, final int flags) throws java.io.IOException
	{
		try
		{
			if((words.size() >= 2) && words.get(1).equalsIgnoreCase("CLAIM"))
			{
				final String which = (words.size() > 2) ? words.get(2) : "ALL";
				if(!which.equalsIgnoreCase("ALL") && !CMath.isLong(which))
				{
					mob.tell(L("REWARDS CLAIM [number|ALL]"));
					return false;
				}
				mob.tell(RewardEscrow.claim(mob, which.equalsIgnoreCase("ALL") ? 0 : CMath.s_long(which)));
			}
			else
				mob.tell(RewardEscrow.describe(mob));
		}
		catch(final java.io.IOException e)
		{
			mob.tell(L("Rewards: @x1", e.getMessage()));
		}
		return false;
	}
}
