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
/** P01: TRACKS (the tree of your tracks), TRACKS CONTRIBUTE [track] [coin], TRACKS RESET [track]. */
public class TracksCmd extends StdCommand
{
	private final String[] access = I(new String[] { "TRACKS" });

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
			if((words.size() >= 4) && words.get(1).equalsIgnoreCase("CONTRIBUTE") && CMath.isLong(words.get(3)))
				mob.tell(ProgressionTracks.contribute(mob, words.get(2).toLowerCase(), CMath.s_long(words.get(3)),
					(words.size() > 4) ? CMParms.combine(words, 4) : null));
			else
			if((words.size() == 3) && words.get(1).equalsIgnoreCase("RESET"))
			{
				ProgressionTracks.resetTrack(ProgressionTracks.of(mob), words.get(2).toLowerCase());
				mob.tell(L("@x1 is reset.", words.get(2)));
			}
			else
			if(words.size() == 1)
				mob.tell(ProgressionTracks.describe(mob));
			else
				mob.tell(L("TRACKS, TRACKS CONTRIBUTE [track] [coin], or TRACKS RESET [track]."));
		}
		catch(final java.io.IOException e)
		{
			mob.tell(L("Tracks: @x1", e.getMessage()));
		}
		return false;
	}
}
