package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
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
 * BUILD command, in a PLAYTEST: runs one command with the builder's powers
 * back ({@link CMSecurity#runAsBuilder(MOB, Runnable)}).  Only while
 * playtesting, so that BUILD is the Building skill otherwise; in a playtest
 * that skill is BUILDING.  An editor that asks for input after its command
 * returns may refuse.
 */
public class BuildCmd extends StdCommand
{
	public BuildCmd()
	{
	}

	private final String[] access = I(new String[] { "BUILD" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("BUILD [COMMAND]: runs a command with your powers, in a playtest."));
			return false;
		}
		final List<String> words = new ArrayList<String>(commands.subList(1, commands.size()));
		final boolean[] ran = new boolean[] { false };
		final java.io.IOException[] err = new java.io.IOException[1];
		CMSecurity.runAsBuilder(mob, new Runnable()
		{
			@Override
			public void run()
			{
				final CMObject O = CMLib.english().findCommand(mob, words);
				if((O instanceof Command) && ((Command)O).securityCheck(mob))
				{
					ran[0] = true;
					try
					{
						((Command)O).execute(mob, words, metaFlags);
					}
					catch(final java.io.IOException e)
					{
						err[0] = e;
					}
				}
			}
		});
		if(err[0] != null)
			throw err[0];
		if(!ran[0])
			mob.tell(L("BUILD knows no command '@x1' you may use.", commands.get(1)));
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
		return SaveGame.isBuilderMode() && CMSecurity.isPlaytesting(mob);
	}
}
