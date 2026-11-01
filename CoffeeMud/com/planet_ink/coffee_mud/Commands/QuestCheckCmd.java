package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * DT32, for builders: QUESTCHECK quest|file|ALL checks a quest's script
 * without starting it ({@link QuestCheck}): a loaded quest by name, a file
 * (world/quests/NAME.quest, or a path in the resources), or every file of
 * world/quests.
 */
public class QuestCheckCmd extends StdCommand
{
	public QuestCheckCmd()
	{
	}

	private final String[] access = I(new String[] { "QUESTCHECK" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * The report for a quest, a file or ALL.
	 * @param what the quest's name, a file, or ALL
	 * @return the report
	 */
	public static String report(final String what)
	{
		final List<String> problems;
		final String which;
		if(what.equalsIgnoreCase("ALL") || (what.length() == 0))
		{
			problems = QuestCheck.checkWorld();
			which = "world/quests";
		}
		else
		{
			final Quest Q = CMLib.quests().fetchQuest(what);
			File F = new File(new File(WorldFiles.worldDir(), "quests"), what.toLowerCase().endsWith(".quest") ? what : (what + ".quest"));
			if(!F.isFile())
				F = new File(new File(WorldFiles.worldDir(), "quests"), what);
			if(F.isFile())
			{
				problems = QuestCheck.checkFile(F);
				which = F.getName();
			}
			else
			if(Q != null)
			{
				problems = QuestCheck.checkText(Q.name(), Q.script());
				which = "the quest " + Q.name();
			}
			else
			{
				final CMFile C = new CMFile(what.startsWith("/") ? what : ("/resources/" + what), null, 0);
				if(!C.exists())
					return "There is no quest or quest file '" + what + "'.";
				problems = QuestCheck.checkText(what, C.text().toString());
				which = what;
			}
		}
		final StringBuilder str = new StringBuilder("QUESTCHECK " + which + ": " + problems.size() + " problems.");
		for(final String p : problems)
			str.append("\n\r  ").append(p);
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		mob.tell(report(CMParms.combine(commands, 1).trim()));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDQUESTS);
	}
}
