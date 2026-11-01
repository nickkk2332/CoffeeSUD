package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
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
 * DT35, for builders: COMBATDEBUG ON|OFF [target] explains each attack by or
 * on a character (or every attack) on one line ({@link CombatDebug}), and
 * COMBATDEBUG LOG file|OFF writes the lines to a file as well.
 */
public class CombatDebugCmd extends StdCommand
{
	public CombatDebugCmd()
	{
	}

	private final String[] access = I(new String[] { "COMBATDEBUG" });

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
		final String rest = (commands.size() > 2) ? CMParms.combine(commands, 2) : "";
		if(what.equals("ON") || what.equals("OFF"))
		{
			MOB target = null;
			if(rest.length() > 0)
			{
				target = (mob.location() == null) ? null : mob.location().fetchInhabitant(rest);
				if(target == null)
				{
					mob.tell(L("There is no '@x1' here.", rest));
					return false;
				}
			}
			if(what.equals("ON"))
				CombatDebug.watch(mob, target);
			else
				CombatDebug.unwatch(target);
			mob.tell(L("COMBATDEBUG: @x1.", CombatDebug.watching()));
			return false;
		}
		if(what.equals("LOG"))
		{
			if(rest.equalsIgnoreCase("OFF") || (rest.length() == 0))
			{
				CombatDebug.setLogFile(null);
				mob.tell(L("COMBATDEBUG writes to no file."));
			}
			else
			{
				final File F = new File(rest);
				CombatDebug.setLogFile(F);
				mob.tell(L("COMBATDEBUG writes its lines to @x1 as well.", F.getPath()));
			}
			return false;
		}
		mob.tell(L("COMBATDEBUG: @x1@x2.  COMBATDEBUG ON/OFF ([TARGET]), COMBATDEBUG LOG [FILE]/OFF.", CombatDebug.watching(),
			(CombatDebug.logFile() == null) ? "" : (", to " + CombatDebug.logFile().getPath() + " too")));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS);
	}
}
