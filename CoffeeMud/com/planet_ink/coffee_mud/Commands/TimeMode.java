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
 * TIMEMODE shows how time passes; TIMEMODE REALTIME and TIMEMODE TURNS
 * change it.  In real time the world runs on its own, and PAUSE stops it.
 * When turn-based, it moves only when the player does something that takes
 * time, by as long as that takes, or WAITs.
 */
public class TimeMode extends StdCommand
{
	public TimeMode()
	{
	}

	private final String[] access = I(new String[] { "TIMEMODE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Reads the name of a time mode.
	 * @param word the name, as typed
	 * @return TRUE for turn-based, FALSE for real time, null if neither
	 */
	protected Boolean parseTurnBased(final String word)
	{
		final String w = (word == null) ? "" : word.trim().toUpperCase();
		if(w.equals("TURNS") || w.equals("TURN") || w.equals("TURNBASED") || w.equals("TURN-BASED"))
			return Boolean.TRUE;
		if(w.equals("REALTIME") || w.equals("REAL") || w.equals("REAL-TIME"))
			return Boolean.FALSE;
		return null;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			if(GameTime.isTurnBased())
				mob.tell(L("Time is turn-based: the world moves only when you act, or WAIT."));
			else
			if(GameTime.isPaused())
				mob.tell(L("Time runs in real time, and the game is paused."));
			else
				mob.tell(L("Time runs in real time."));
			mob.tell(L("TIMEMODE REALTIME or TIMEMODE TURNS changes it."));
			return false;
		}
		final Boolean turns = parseTurnBased(CMParms.combine(commands, 1));
		if(turns == null)
			mob.tell(L("TIMEMODE REALTIME or TIMEMODE TURNS?"));
		else
		if(turns.booleanValue())
		{
			GameTime.setTurnBased(true);
			mob.tell(L("Time is now turn-based.  The world moves only when you do something that takes time, by as long as it takes, or when you WAIT."));
		}
		else
		{
			GameTime.setTurnBased(false);
			mob.tell(L("Time now runs in real time.  PAUSE stops it."));
		}
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
