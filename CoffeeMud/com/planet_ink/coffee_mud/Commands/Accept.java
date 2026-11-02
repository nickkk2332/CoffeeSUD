package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
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
 * Q14: ACCEPT [NUMBER/NAME]: takes on a job from the quest board read
 * last (Prop_QuestBoard), or else a quest offered (QUESTS ACCEPT).
 */
public class Accept extends StdCommand
{
	public Accept()
	{
	}

	private final String[]	access	= I(new String[] { "ACCEPT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String rest=CMParms.combine(commands,1).trim();
		if(rest.length()==0)
		{
			mob.tell(L("Accept which job?  LOOK at a quest board for its jobs, or enter QUESTS OFFERED."));
			return false;
		}
		final Quest Q=QuestBoards.chosen(mob,rest);
		if(Q==null)
		{
			final Command C=CMClass.getCommand("Questwins");
			if(C!=null)
				return C.execute(mob,new XVector<String>("QUESTS","ACCEPT",rest),metaFlags);
			return false;
		}
		final String name=(Q.displayName().trim().length()>0)?Q.displayName():Q.name();
		if(!Q.canAcceptQuest(mob))
		{
			mob.tell(L("You cannot take on '@x1' now.",name));
			return false;
		}
		Q.acceptQuest(mob);
		if(QuestMarkers.accepted(mob,Q))
			mob.tell(L("You are now on the job '@x1'.",name));
		else
			mob.tell(L("You could not take on '@x1'.",name));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return true;
	}
}
