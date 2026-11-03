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
		// W4: WAIT n HOURS|DAYS|WEEKS and WAIT UNTIL ...: the game's own ticks, the clock paused for it if it runs
		if((commands.size() > 1)
		&& (commands.get(1).equalsIgnoreCase("UNTIL")
			|| ((commands.size() > 2) && CMath.isInteger(commands.get(1)) && (!commands.get(2).toUpperCase().startsWith("TICK")))))
			return longWait(mob, new ArrayList<String>(commands.subList(1, commands.size())));
		int ticks = 1;
		if(commands.size() > 1)
		{
			if(!CMath.isInteger(commands.get(1)) || (CMath.s_int(commands.get(1)) < 1))
			{
				mob.tell(L("Wait how many ticks?  Or WAIT [N] HOURS, DAYS or WEEKS, or WAIT UNTIL DAWN, DUSK, HOUR [N]..."));
				return false;
			}
			ticks = CMath.s_int(commands.get(1));
			if(ticks > MAX_TICKS)
			{
				if(Waits.mayWaitLong(mob))
					return longWait(mob, new ArrayList<String>(Arrays.asList(commands.get(1), "TICKS")));
				ticks = MAX_TICKS;
			}
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
		// in a party, only the leader moves the world; the others wait for its turn
		if(Party.waitForTurn(mob))
		{
			mob.tell(L("You wait for @x1's turn.", Party.leaderOf(mob).Name()));
			return false;
		}
		if(ticks == 1)
			mob.tell(L("You wait."));
		else
			mob.tell(L("You wait for @x1 ticks.", "" + ticks));
		Party.passTurn(mob);
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
		return Waits.disturbed(mob);
	}

	/**
	 * W4: WAIT n TICKS|HOURS|DAYS|WEEKS, or WAIT UNTIL a time, a day or an
	 * arrival: at most three game days, through core/Waits.
	 * @param mob the one waiting
	 * @param words the words after WAIT
	 * @return false, a command that takes no time of its own
	 */
	protected boolean longWait(final MOB mob, final List<String> words)
	{
		if(!Waits.mayWaitLong(mob))
		{
			mob.tell(L("Only builders may wait so long in this game (LONGWAIT=BUILDERS)."));
			return false;
		}
		if(GameTime.isAdvancing())
		{
			mob.tell(L("Time is already passing."));
			return false;
		}
		if(Party.waitForTurn(mob))
		{
			mob.tell(L("You wait for @x1's turn.", Party.leaderOf(mob).Name()));
			return false;
		}
		final TimeClock C = Waits.clock(mob);
		final long max = Waits.MAX_HOURS * CMProps.getMillisPerMudHour();
		long ms = max;
		BooleanSupplier until = null;
		String what;
		if(words.get(0).equalsIgnoreCase("UNTIL"))
		{
			final String[] why = { "" };
			until = Waits.until(mob, words.subList(1, words.size()), why);
			if(until == null)
			{
				mob.tell(why[0]);
				return false;
			}
			what = L("You wait until @x1.", CMParms.combine(words, 1).toLowerCase());
		}
		else
		{
			ms = Laws.duration(words.get(0) + " " + words.get(1), C);
			if(ms <= 0)
			{
				mob.tell(L("Wait how long?  [N] TICKS, HOURS, DAYS or WEEKS."));
				return false;
			}
			if(ms > max)
			{
				mob.tell(L("You can wait three days at most."));
				ms = max;
			}
			what = L("You wait @x1 @x2.", words.get(0), words.get(1).toLowerCase());
		}
		mob.tell(what);
		Party.passTurn(mob);
		Waits.start(mob, ms, until, true, false);
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
		return CMProps.isSinglePlayer();
	}
}
