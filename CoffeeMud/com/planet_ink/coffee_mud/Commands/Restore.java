package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldState;
import com.planet_ink.coffee_mud.core.save.SaveTools;
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
		if((commands.size() >= 3) && commands.get(0).toUpperCase().startsWith("SAVE"))
		{
			final String sub = commands.get(1).toUpperCase();
			if(sub.equals("INFO"))
			{
				final SaveSlots.Slot slot = SaveSlots.find(SaveGame.saveDir(), CMParms.combine(commands, 2).trim());
				final String info = (slot == null) ? null : SaveTools.info(slot.name);
				mob.tell((info == null) ? L("There is no saved game called '@x1'.  SAVES lists them.", CMParms.combine(commands, 2)) : info);
				return false;
			}
			if(sub.equals("DELETE"))
			{
				final SaveSlots.Slot slot = SaveSlots.find(SaveGame.saveDir(), CMParms.combine(commands, 2).trim());
				if(slot == null)
				{
					mob.tell(L("There is no saved game called '@x1'.  SAVES lists them.", CMParms.combine(commands, 2)));
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
						session.promptPrint(L("Delete the saved game '@x1' for good (y/N)? ", slot.name));
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
						if(SaveSlots.delete(SaveGame.saveDir(), slot.name))
							mob.tell(L("Saved game '@x1' deleted.", slot.name));
						else
							mob.tell(L("Saved game '@x1' could not be deleted.", slot.name));
					}
				});
				return false;
			}
			if((sub.equals("COPY") || sub.equals("DIFF")) && (commands.size() >= 4))
			{
				final SaveSlots.Slot a = SaveSlots.find(SaveGame.saveDir(), commands.get(2));
				if(a == null)
				{
					mob.tell(L("There is no saved game called '@x1'.  SAVES lists them.", commands.get(2)));
					return false;
				}
				if(sub.equals("COPY"))
				{
					final String problem = SaveTools.copy(a.name, commands.get(3));
					mob.tell((problem == null) ? L("Saved game '@x1' copied to '@x2'.", a.name, commands.get(3)) : problem);
					return false;
				}
				final SaveSlots.Slot b = SaveSlots.find(SaveGame.saveDir(), commands.get(3));
				if(b == null)
				{
					mob.tell(L("There is no saved game called '@x1'.  SAVES lists them.", commands.get(3)));
					return false;
				}
				final List<String> lines = SaveTools.diff(a.name, b.name);
				if((lines == null) || (lines.size() == 0))
					mob.tell(L("'@x1' and '@x2' hold the same.", a.name, b.name));
				else
				{
					final StringBuilder str = new StringBuilder(L("From '@x1' to '@x2':\n\r", a.name, b.name));
					for(final String line : lines)
						str.append("  ").append(line).append("\n\r");
					mob.tell(str.toString());
				}
				return false;
			}
		}
		if((commands.size() < 2) || commands.get(0).toUpperCase().startsWith("SAVE"))
		{
			mob.tell(SaveGame.describeSlots());
			mob.tell(L("SAVE (NAME) saves this game, and RESTORE NAME or RESTORE NUMBER restores one.  "
				+ "SAVES INFO, DELETE, COPY and DIFF look after them, and SAVES CHECK says what a save would not keep."));
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
