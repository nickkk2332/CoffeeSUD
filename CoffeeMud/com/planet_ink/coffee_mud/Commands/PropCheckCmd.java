package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * PROPCHECK [ROOM|AREA [name]|ALL|CATALOG|target]: reads every effect's text
 * again and lists what would do nothing or not what was meant
 * ({@link PropCheck}).
 */
public class PropCheckCmd extends StdCommand
{
	public PropCheckCmd()
	{
	}

	private final String[] access = I(new String[] { "PROPCHECK" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String what = (commands.size() > 1) ? commands.get(1).toUpperCase() : "ROOM";
		final String name = (commands.size() > 2) ? CMParms.combine(commands, 2) : "";
		final List<String> problems;
		final String label;
		if(what.equals("ALL"))
		{
			problems = PropCheck.checkAll();
			label = L("the world");
		}
		else
		if(what.equals("CATALOG"))
		{
			problems = PropCheck.checkCatalog();
			label = L("the catalog");
		}
		else
		if(what.equals("AREA"))
		{
			final Area A = (name.length() > 0) ? CMLib.map().getArea(name) : ((mob.location() == null) ? null : mob.location().getArea());
			if(A == null)
			{
				mob.tell(L("There is no area '@x1'.", name));
				return false;
			}
			problems = PropCheck.checkArea(A);
			label = A.Name();
		}
		else
		if(what.equals("ROOM") || what.equals("HERE"))
		{
			problems = PropCheck.checkRoom(mob.location());
			label = L("this room");
		}
		else
		{
			final PhysicalAgent host = ScriptCmd.findTarget(mob, CMParms.combine(commands, 1));
			if(host == null)
			{
				mob.tell(L("PROPCHECK (ROOM/AREA (NAME)/ALL/CATALOG/[MOB OR ITEM])"));
				return false;
			}
			problems = PropCheck.checkHost(host);
			label = host.Name();
		}
		if(problems.isEmpty())
			mob.tell(L("PROPCHECK @x1: no problems found.", label));
		else
			mob.tell(L("PROPCHECK @x1: @x2 problems:", label, "" + problems.size()) + "\n\r" + String.join("\n\r", problems));
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
