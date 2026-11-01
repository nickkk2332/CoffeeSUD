package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
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
 * DT23, for builders and DEV=1 games: TIMESCALE [rate] shows or sets how fast
 * the game clock runs against real time, from 0.1 to 20 (see
 * {@link GameTime#setRate(double)}).  Everything that reads the clock follows:
 * ticks, cooldowns, decay, effects and the calendar.
 */
public class TimeScaleCmd extends StdCommand
{
	public TimeScaleCmd()
	{
	}

	private final String[] access = I(new String[] { "TIMESCALE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * How fast time runs, in words.
	 * @return the words
	 */
	public static String describe()
	{
		final StringBuilder str = new StringBuilder();
		if(GameTime.rate() == 1.0)
			str.append("Time runs at real time (x1).");
		else
			str.append("Time runs at ").append(GameTime.rateText()).append(": ").append(GameTime.rateText().substring(1))
				.append(" game seconds in each real one.");
		if(GameTime.isTurnBased())
			str.append(" The game is turn-based, so time moves only when you act, as before.");
		else
		if(GameTime.isPaused())
			str.append(" The game is paused.");
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(describe());
			return false;
		}
		final String w = commands.get(1).trim();
		if(!CMath.isNumber(w))
		{
			mob.tell(L("TIMESCALE [0.1 TO 20]"));
			return false;
		}
		final double rate = CMath.s_double(w);
		if((rate < GameTime.MIN_RATE) || (rate > GameTime.MAX_RATE))
		{
			mob.tell(L("TIMESCALE is from 0.1 to 20."));
			return false;
		}
		GameTime.setRate(rate);
		mob.tell(describe());
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
		return SaveGame.isDevMode() || SaveGame.isBuilderMode();
	}
}
