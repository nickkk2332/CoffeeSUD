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
 * JS ERRORS [n]: the most recent JavaScript errors, with where each script
 * is, its line, and what went wrong (see {@link JSRuntime}).
 */
public class JSCmd extends StdCommand
{
	public JSCmd()
	{
	}

	private final String[] access=I(new String[]{"JS"});

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size()<2)||(!commands.get(1).equalsIgnoreCase("ERRORS")))
		{
			mob.tell(L("JS ERRORS [number]: the most recent JavaScript errors."));
			return false;
		}
		int max=10;
		if((commands.size()>2)&&CMath.isInteger(commands.get(2)))
			max=Math.max(1,CMath.s_int(commands.get(2)));
		final List<String> errors=JSRuntime.recentErrors(max);
		if(errors.size()==0)
		{
			mob.tell(L("No JavaScript errors since the game started."));
			return false;
		}
		final StringBuilder str=new StringBuilder("");
		for(final String e : errors)
			str.append(e).append("\n\r");
		mob.tell(str.toString());
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
		return CMSecurity.isAllowed(mob,mob.location(),CMSecurity.SecFlag.JSCRIPTS);
	}

	@Override
	public int compareTo(final CMObject o)
	{
		return CMClass.classID(this).compareToIgnoreCase(CMClass.classID(o));
	}
}
