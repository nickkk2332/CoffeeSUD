package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Common.DefaultScriptingEngine;
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
 * SCRIPTTRACE target|ROOM|AREA|OFF [trigger] (DT18): traces the scripts of a
 * host, of the hosts in this room, or in this area, to your session only
 * ({@link ScriptTrace}).
 */
public class ScriptTraceCmd extends StdCommand
{
	public ScriptTraceCmd()
	{
	}

	private final String[] access = I(new String[] { "SCRIPTTRACE" });

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
			mob.tell(L("SCRIPTTRACE is @x1.  SCRIPTTRACE [TARGET/ROOM/AREA/OFF] ([TRIGGER])", ScriptTrace.describe()));
			return false;
		}
		final String what = commands.get(1);
		if(what.equalsIgnoreCase("OFF"))
		{
			ScriptTrace.stop();
			mob.tell(L("SCRIPTTRACE is off."));
			return false;
		}
		int code = 0;
		if(commands.size() > 2)
		{
			code = DefaultScriptingEngine.triggerCodeOf(commands.get(2));
			if(code <= 0)
			{
				mob.tell(L("'@x1' is not a trigger.", commands.get(2)));
				return false;
			}
		}
		final Object target;
		final ScriptTrace.Kind kind;
		if(what.equalsIgnoreCase("ROOM") || what.equalsIgnoreCase("HERE"))
		{
			target = mob.location();
			kind = ScriptTrace.Kind.ROOM;
		}
		else
		if(what.equalsIgnoreCase("AREA"))
		{
			target = (mob.location() == null) ? null : mob.location().getArea();
			kind = ScriptTrace.Kind.AREA;
		}
		else
		{
			target = ScriptCmd.findTarget(mob, what);
			kind = ScriptTrace.Kind.HOST;
		}
		if(target == null)
		{
			mob.tell(L("There is no '@x1' here.", what));
			return false;
		}
		ScriptTrace.start(mob, target, kind, code);
		mob.tell(L("SCRIPTTRACE: @x1, to you alone, until SCRIPTTRACE OFF.", ScriptTrace.describe()));
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
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
