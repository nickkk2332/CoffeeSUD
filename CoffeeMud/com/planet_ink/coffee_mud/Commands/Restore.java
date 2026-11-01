package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldState;
import com.planet_ink.coffee_mud.core.save.SaveSlots;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.Session.InputCallback;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * SAVES lists the saved games.  RESTORE NAME (or its number in that list)
 * restores one: the world is built again from its content and the save is
 * put over it, so whatever has not been saved is lost.
 */
public class Restore extends StdCommand
{
	public Restore()
	{
	}

	private final String[] access = I(new String[] { "RESTORE", "SAVES" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(SaveGame.isBuilderMode())
		{
			mob.tell(L("There are no saved games in builder mode."));
			return false;
		}
		if((commands.size() >= 2) && commands.get(0).toUpperCase().startsWith("SAVE") && commands.get(1).equalsIgnoreCase("CHECK"))
		{
			final List<String> lines = WorldState.notKept(mob);
			if(lines.size() == 0)
				mob.tell(L("A save now would keep everything in the world."));
			else
			{
				final StringBuilder str = new StringBuilder(L("A save now would not keep:\n\r"));
				for(final String line : lines)
					str.append("  ").append(line).append("\n\r");
				mob.tell(str.toString());
			}
			return false;
		}
		if((commands.size() < 2) || commands.get(0).toUpperCase().startsWith("SAVE"))
		{
			mob.tell(SaveGame.describeSlots());
			mob.tell(L("SAVE (NAME) saves this game, and RESTORE NAME or RESTORE NUMBER restores one."));
			return false;
		}
		if(SaveGame.saveMode() == SaveGame.SaveMode.IRONMAN)
		{
			mob.tell(L("This is an ironman game: there is no going back to a save.  QUIT keeps the game."));
			return false;
		}
		String which = CMParms.combine(commands, 1).trim();
		String fresh = null;
		final int freshDex = which.toUpperCase().indexOf("FRESH=");
		if(freshDex >= 0)
		{
			fresh = which.substring(freshDex + 6).trim();
			which = which.substring(0, freshDex).trim();
		}
		final String freshSpec = fresh;
		final SaveSlots.Slot slot = SaveSlots.find(SaveGame.saveDir(), which);
		if(slot == null)
		{
			mob.tell(L("There is no saved game called '@x1'.  SAVES lists them.", which));
			return false;
		}
		final Session session = mob.session();
		if(session == null)
			return false;
		session.prompt(new InputCallback(InputCallback.Type.CONFIRM, "N", 0)
		{
			@Override
			public void showPrompt()
			{
				session.promptPrint(L("Restore '@x1'?  Anything since you last saved will be lost (y/N)? ", slot.name));
			}

			@Override
			public void timedOut()
			{
			}

			@Override
			public void callBack()
			{
				if(!this.confirmed)
					return;
				SaveGame.setNextFresh(freshSpec);
				final String problem = SaveGame.restore(slot.name);
				if(problem != null)
				{
					SaveGame.setNextFresh(null);
					mob.tell(problem);
				}
				else
					mob.tell(L("Restoring '@x1'...", slot.name));
			}
		});
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
		return CMProps.isSinglePlayer();
	}
}
