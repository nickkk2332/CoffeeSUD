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
 * B6, for builders: UNDO (n) puts back the rooms the last n building
 * commands changed (core/UndoLog), newest first; UNDO LIST (n) lists them,
 * newest first, as HISTORY would (HISTORY is the command history).
 */
public class UndoCmd extends StdCommand
{
	public UndoCmd()
	{
	}

	private final String[] access = I(new String[] { "UNDO" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size() > 1) && commands.get(1).equalsIgnoreCase("LIST"))
		{
			final int n = ((commands.size() > 2) && CMath.isInteger(commands.get(2))) ? CMath.s_int(commands.get(2)) : 20;
			mob.tell(UndoLog.history(Math.max(1, n)));
			return false;
		}
		if((commands.size() > 1) && (!CMath.isInteger(commands.get(1))))
		{
			mob.tell(L("UNDO ([NUMBER]), UNDO LIST ([NUMBER])"));
			return false;
		}
		final int n = (commands.size() > 1) ? CMath.s_int(commands.get(1)) : 1;
		mob.tell(UndoLog.undo(Math.max(1, Math.min(UndoLog.KEEP, n))));
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
		return SaveGame.isBuilderMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
