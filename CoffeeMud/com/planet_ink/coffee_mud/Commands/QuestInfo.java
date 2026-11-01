package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * QUESTINFO name (or number, as LIST QUESTS gives it): a quest on one screen,
 * for testing it: its state and step, its time, variables, the things it
 * designated and holds, what it added, its winners and its last errors.  See
 * {@link Quest#inspection()}.  MODIFY QUEST changes the step, the winners and
 * the variables, and reloads it.
 */
public class QuestInfo extends StdCommand
{
	public QuestInfo()
	{
	}

	private final String[] access = I(new String[] { "QUESTINFO" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Finds a quest by its name, or its number as LIST QUESTS gives it.
	 * @param name the name or number
	 * @return the quest, or null
	 */
	public static Quest findQuest(final String name)
	{
		Quest Q = null;
		if(CMath.isInteger(name))
			Q = CMLib.quests().fetchQuest(CMath.s_int(name) - 1);
		if(Q == null)
			Q = CMLib.quests().fetchQuest(name);
		return Q;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("QUESTINFO which quest?  LIST QUESTS gives them."));
			return false;
		}
		final String name = CMParms.combine(commands, 1);
		final Quest Q = findQuest(name);
		if(Q == null)
		{
			mob.tell(L("Quest '@x1' is unknown.  LIST QUESTS gives them.", name));
			return false;
		}
		mob.tell(Q.inspection());
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
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDQUESTS);
	}
}
