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
 * DT34, for builders: SCRIPTPROF ON|OFF|RESET|REPORT [n] [AREA] counts and
 * times each script trigger's runs ({@link ScriptProf}), and REPORT lists the
 * trigger lines that took longest in all, here or in the whole world.
 */
public class ScriptProfCmd extends StdCommand
{
	public ScriptProfCmd()
	{
	}

	private final String[] access = I(new String[] { "SCRIPTPROF" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String what = (commands.size() > 1) ? commands.get(1).toUpperCase() : "REPORT";
		if(what.equals("ON"))
		{
			ScriptProf.setOn(true);
			mob.tell(L("SCRIPTPROF is on: each script trigger's runs are counted and timed.  SCRIPTPROF REPORT lists them."));
		}
		else
		if(what.equals("OFF"))
		{
			ScriptProf.setOn(false);
			mob.tell(L("SCRIPTPROF is off; what it counted stays until SCRIPTPROF RESET."));
		}
		else
		if(what.equals("RESET"))
		{
			ScriptProf.reset();
			mob.tell(L("SCRIPTPROF forgot what it counted."));
		}
		else
		if(what.equals("REPORT"))
		{
			int n = 10;
			String area = null;
			for(int i = 2; i < commands.size(); i++)
			{
				if(CMath.isInteger(commands.get(i)))
					n = CMath.s_int(commands.get(i));
				else
				if(commands.get(i).equalsIgnoreCase("AREA") && (mob.location() != null))
					area = mob.location().getArea().Name();
			}
			mob.tell(ScriptProf.report(Math.max(1, n), area));
		}
		else
			mob.tell(L("SCRIPTPROF ON/OFF/RESET, SCRIPTPROF REPORT ([NUMBER]) (AREA)"));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
