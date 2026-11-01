package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
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
 * SCRIPTCHECK file|target|AREA|ALL: checks MOBPROG scripts without running
 * them ({@link ScriptCheck}), and lists what would go wrong by file and line.
 * A target is as for SCRIPT: a mob or item here or carried, ROOM, name@room.
 */
public class ScriptCheckCmd extends StdCommand
{
	public ScriptCheckCmd()
	{
	}

	private final String[] access = I(new String[] { "SCRIPTCHECK" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Checks what SCRIPTCHECK was given.
	 * @param mob the one asking
	 * @param what a script file, a target, AREA or ALL
	 * @return the problems, or null if there is no such thing
	 */
	public static List<String> check(final MOB mob, final String what)
	{
		if(what.equalsIgnoreCase("ALL"))
			return ScriptCheck.checkAll();
		if(what.equalsIgnoreCase("AREA"))
			return (mob.location() == null) ? null : ScriptCheck.checkArea(mob.location().getArea(), null);
		final CMFile F = new CMFile(Resources.makeFileResourceName(what), null, CMFile.FLAG_FORCEALLOW);
		if(F.exists() && (!F.isDirectory()))
			return ScriptCheck.checkFile(what);
		final PhysicalAgent host = ScriptCmd.findTarget(mob, what);
		if(host == null)
			return null;
		return ScriptCheck.checkHost(host, null);
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("SCRIPTCHECK [FILE/TARGET/AREA/ALL]"));
			return false;
		}
		final String what = CMParms.combine(commands, 1);
		final List<String> problems = check(mob, what);
		if(problems == null)
		{
			mob.tell(L("There is no script file, mob or item '@x1' (AREA and ALL check more).", what));
			return false;
		}
		if(problems.isEmpty())
			mob.tell(L("SCRIPTCHECK @x1: no problems found.", what));
		else
			mob.tell(L("SCRIPTCHECK @x1: @x2 problems:", what, "" + problems.size()) + "\n\r" + String.join("\n\r", problems));
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
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS)
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDITEMS)
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
