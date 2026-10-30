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
import java.util.function.BooleanSupplier;

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
 * WAIT [n] lets n ticks (one by default) of game time pass while the clock
 * is stopped: in turn-based play, or while the game is paused.  A wait of
 * several ticks ends early if the player is hurt, attacked, or moved.
 */
public class Wait extends StdCommand
{
	public Wait()
	{
	}

	private final String[] access = I(new String[] { "WAIT" });

	/** the most ticks one WAIT may ask for */
	private static final int MAX_TICKS = 100;

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		int ticks = 1;
		if(commands.size() > 1)
		{
			if(!CMath.isInteger(commands.get(1)) || (CMath.s_int(commands.get(1)) < 1))
			{
				mob.tell(L("Wait how many ticks?"));
				return false;
			}
			ticks = Math.min(MAX_TICKS, CMath.s_int(commands.get(1)));
		}
		if(!GameTime.isStopped())
		{
			mob.tell(L("Time passes on its own.  PAUSE stops it, and TIMEMODE TURNS makes it wait for you."));
			return false;
		}
		if(GameTime.isAdvancing())
		{
			mob.tell(L("Time is already passing."));
			return false;
		}
		if(ticks == 1)
			mob.tell(L("You wait."));
		else
			mob.tell(L("You wait for @x1 ticks.", "" + ticks));
		GameTime.requestAdvance(ticks * CMProps.getTickMillis(), (ticks > 1) ? disturbed(mob) : null);
		return false;
	}

	/**
	 * Returns a test that says when a long wait should end: when the mob is
	 * hurt, dies, leaves the room, or is drawn into a fight.
	 * @param mob the one waiting
	 * @return the test
	 */
	protected BooleanSupplier disturbed(final MOB mob)
	{
		final Room startRoom = mob.location();
		final int startHP = mob.curState().getHitPoints();
		final boolean startFight = mob.isInCombat();
		return new BooleanSupplier()
		{
			@Override
			public boolean getAsBoolean()
			{
				if(mob.amDead()
				|| (mob.location() != startRoom)
				|| (mob.curState().getHitPoints() < startHP)
				|| (mob.isInCombat() && !startFight))
				{
					if(!mob.amDead())
						mob.tell(L("You stop waiting."));
					return true;
				}
				return false;
			}
		};
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return CMProps.isSinglePlayer();
	}
}
