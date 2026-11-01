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
 * B12, for builders: SANDBOX ENTER|LEAVE|CLEAR|PREVIEW name, the scratch
 * area (core/Sandbox) that is never saved.
 */
public class SandboxCmd extends StdCommand
{
	public SandboxCmd()
	{
	}

	private final String[] access = I(new String[] { "SANDBOX" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String w = (commands.size() > 1) ? commands.get(1).toUpperCase() : "";
		if(w.equals("ENTER"))
			mob.tell(Sandbox.enter(mob));
		else
		if(w.equals("LEAVE"))
			mob.tell(Sandbox.leave(mob));
		else
		if(w.equals("CLEAR"))
			mob.tell(Sandbox.clear());
		else
		if(w.equals("PREVIEW") && (commands.size() > 2))
			mob.tell(Sandbox.preview(mob, CMParms.combine(commands, 2)));
		else
			mob.tell(L("SANDBOX ENTER, SANDBOX LEAVE, SANDBOX CLEAR, SANDBOX PREVIEW [CATALOG NAME/TEMPLATE]; GENERATE ... SANDBOX generates there.  @x1",
				Sandbox.isIn(mob) ? L("You are in the sandbox.") : L("You are not in the sandbox.")));
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
