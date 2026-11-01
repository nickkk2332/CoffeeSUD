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
 * SCRIPTERRORS [number] [text], SCRIPTERRORS CLEAR: the script errors kept
 * ({@link ScriptErrors}), MOBPROG and JavaScript, each with its file and line,
 * the last ones, or those holding the text.
 */
public class ScriptErrorsCmd extends StdCommand
{
	public ScriptErrorsCmd()
	{
	}

	private final String[] access = I(new String[] { "SCRIPTERRORS" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size() > 1) && commands.get(1).equalsIgnoreCase("CLEAR"))
		{
			ScriptErrors.clear();
			mob.tell(L("The script errors kept are forgotten."));
			return false;
		}
		int max = 20;
		final List<String> words = new ArrayList<String>(commands.subList(1, commands.size()));
		if((!words.isEmpty()) && CMath.isInteger(words.get(0)))
			max = CMath.s_int(words.remove(0));
		final String holding = CMParms.combine(words, 0);
		final List<String> errors = ScriptErrors.recent(max, holding);
		if(errors.isEmpty())
			mob.tell((holding.length() > 0) ? L("No script error kept holds '@x1'.", holding) : L("No script errors since the game began."));
		else
			mob.tell(L("Script errors, oldest first:") + "\n\r" + String.join("\n\r", errors));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode()
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS)
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.JSCRIPTS);
	}
}
