package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * P10: CHEAT ON/OFF, kept in the save and shown in SAVES: it unlocks the
 * world's options marked as cheats (NEWCHARS=CHEAT).
 */
public class CheatCmd extends StdCommand
{
	public CheatCmd()
	{
	}

	private final String[] access = I(new String[] { "CHEAT" });

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
		if(what.equals("ON") || what.equals("OFF"))
		{
			WorldRules.setCheats(what.equals("ON"));
			mob.tell(what.equals("ON")
				? L("Cheats are on: the world's cheat options are open, and saves are marked [cheat].")
				: L("Cheats are off."));
			return false;
		}
		mob.tell(WorldRules.cheatsOn() ? L("Cheats are on.  CHEAT OFF to stop.") : L("Cheats are off.  CHEAT ON to start."));
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
		return CMProps.isSinglePlayer() || (!SaveGame.isBuilderMode());
	}
}
