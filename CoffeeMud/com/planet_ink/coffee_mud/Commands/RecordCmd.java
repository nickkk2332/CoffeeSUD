package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.File;
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
 * DT25, in DEV=1 games and builder mode: RECORD INPUT file|OFF writes what the
 * player types to a playtest file ({@link InputRecord}), and RECORD MARK text
 * marks the place.  RECORD with other words is the Archon's RECORD skill, as
 * before.
 */
public class RecordCmd extends StdCommand
{
	public RecordCmd()
	{
	}

	private final String[] access = I(new String[] { "RECORD" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String what = (commands.size() > 1) ? commands.get(1).toUpperCase() : "";
		if(what.equals("INPUT"))
		{
			final String rest = CMParms.combine(commands, 2).trim();
			if(rest.length() == 0)
			{
				if(InputRecord.isRecording())
					mob.tell(L("Recording what is typed to @x1: @x2 lines so far.", InputRecord.file().getPath(), "" + InputRecord.lines()));
				else
					mob.tell(L("Nothing is being recorded.  RECORD INPUT [FILE] starts it, RECORD INPUT OFF stops it."));
				return false;
			}
			if(rest.equalsIgnoreCase("OFF"))
			{
				if(!InputRecord.isRecording())
				{
					mob.tell(L("Nothing is being recorded."));
					return false;
				}
				final File F = InputRecord.file();
				final int lines = InputRecord.lines();
				InputRecord.stop();
				mob.tell(L("Stopped recording: @x1 lines in @x2.", "" + lines, F.getPath()));
				return false;
			}
			final File F = InputRecord.fileFor(rest);
			final String why = InputRecord.start(F, mob);
			if(why != null)
				mob.tell(L("Cannot record to @x1: @x2", F.getPath(), why));
			else
				mob.tell(L("Recording what is typed to @x1.  RECORD MARK [TEXT] marks a place, RECORD INPUT OFF stops it.", F.getPath()));
			return false;
		}
		if(what.equals("MARK"))
		{
			if(!InputRecord.mark(mob, CMParms.combine(commands, 2)))
				mob.tell(L("Nothing is being recorded.  RECORD INPUT [FILE] starts it."));
			else
				mob.tell(L("Marked."));
			return false;
		}
		// anything else is the Archon's RECORD skill, as before
		final Ability A = mob.fetchAbility("Archon_Record");
		if(A != null)
		{
			A.invoke(mob, new ArrayList<String>(commands.subList(1, commands.size())), null, false, 0);
			return false;
		}
		mob.tell(L("RECORD INPUT [FILE]/OFF, RECORD MARK [TEXT]"));
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
