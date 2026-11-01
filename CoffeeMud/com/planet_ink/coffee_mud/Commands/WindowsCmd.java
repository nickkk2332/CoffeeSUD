package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * WINDOWS shows whether each of the player's characters in the world, but
 * for the one played on the terminal, has a window of their own; WINDOWS ON
 * and WINDOWS OFF change it, for those in the world now and those who come
 * in later.  A window shows what its character sees, and what is typed into
 * it is the character's commands.  See {@link Party#setWindows(boolean)}.
 * WINDOWS PANEL ON, OFF or a list of sections (STATUS, MAP, PARTY) gives
 * the windows a panel beside their text, or takes it away; see
 * {@link WindowPanel}.
 */
public class WindowsCmd extends StdCommand
{
	public WindowsCmd()
	{
	}

	private final String[] access = I(new String[] { "WINDOWS" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size() > 1) && commands.get(1).equalsIgnoreCase("PANEL"))
			return panel(mob, commands);
		if(commands.size() > 1)
		{
			final String onOff = commands.get(1).toUpperCase();
			if(onOff.equals("ON"))
			{
				if(!Party.setWindows(true))
				{
					mob.tell(L("There is no display here, so the windows cannot open."));
					return false;
				}
			}
			else
			if(onOff.equals("OFF"))
				Party.setWindows(false);
			else
			{
				mob.tell(L("WINDOWS ON or WINDOWS OFF?"));
				return false;
			}
		}
		if(Party.isWindows())
			mob.tell(L("Each of your characters in the world, but for the one you play here, has a window of their own: it shows what they see, and what you type into it is their command.  Closing one sends that character to rest.  WINDOWS OFF closes them."));
		else
			mob.tell(L("Your other characters in the world have no windows: AS NAME COMMAND shows you what comes of what they do.  WINDOWS ON gives each a window of their own."));
		return false;
	}

	/**
	 * WINDOWS PANEL, and what follows it: ON, OFF, or the sections to show.
	 */
	protected boolean panel(final MOB mob, final List<String> commands)
	{
		if(commands.size() > 2)
		{
			final String which = CMParms.combine(commands, 2);
			if(WindowPanel.setSections(which) == null)
			{
				mob.tell(L("WINDOWS PANEL ON, OFF, or the sections to show, of @x1?", String.join(", ", WindowPanel.SECTIONS)));
				return false;
			}
		}
		final String now = WindowPanel.sections();
		if(now.length() == 0)
			mob.tell(L("The windows have no panel.  WINDOWS PANEL ON gives each one beside its text, showing @x1.", String.join(", ", WindowPanel.SECTIONS)));
		else
			mob.tell(L("Each window has a panel beside its text, showing @x1, drawn again at each prompt.  WINDOWS PANEL OFF takes it away.", now.replace(",", ", ")));
		if(!Party.isWindows())
			mob.tell(L("(The panel is in the characters' windows, and they are off: WINDOWS ON opens them.)"));
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
		return CMProps.isSinglePlayer() && CMProps.isUsingAccountSystem();
	}
}
