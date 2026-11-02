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
		if((commands.size()>1)&&commands.get(1).equalsIgnoreCase("STATE"))
			return state(mob,commands);
		if((commands.size()<2)||(!commands.get(1).equalsIgnoreCase("ERRORS")))
		{
			mob.tell(L("JS ERRORS [number]: the most recent JavaScript errors.\n\r"
				+"JS STATE ([key] (= {value}/DELETE)): the JavaScript stores (cm.store), in builder mode."));
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

	/**
	 * JS7: JS STATE lists the stores; JS STATE key shows one; JS STATE key =
	 * {value} replaces it (JSON, or a JavaScript object as {gold: 5}); JS
	 * STATE key DELETE takes it away.  Builder and developer games only.
	 * @param mob the builder
	 * @param commands the words
	 * @return false
	 */
	protected boolean state(final MOB mob, final List<String> commands)
	{
		if(!(com.planet_ink.coffee_mud.core.save.SaveGame.isBuilderMode()||com.planet_ink.coffee_mud.core.save.SaveGame.isDevMode()))
		{
			mob.tell(L("JS STATE is for builder mode."));
			return false;
		}
		if(commands.size()<3)
		{
			final List<String> keys=JSStore.keys();
			if(keys.size()==0)
			{
				mob.tell(L("There are no JavaScript stores."));
				return false;
			}
			final StringBuilder str=new StringBuilder(L("JavaScript stores:"));
			for(final String k : keys)
			{
				final String j=JSStore.json(k,0);
				str.append("\n\r  ").append(k).append(": ").append((j==null)?"":CMStrings.ellipse(j,70));
			}
			mob.tell(str.toString());
			return false;
		}
		final String rest=CMParms.combine(commands,2);
		final int eq=rest.indexOf('=');
		String key=((eq<0)?rest:rest.substring(0,eq)).trim();
		if(eq>=0)
		{
			final String why=JSStore.set(key,rest.substring(eq+1).trim());
			mob.tell((why==null)?L("Store '@x1' set.",key):L("Store '@x1' not set: @x2",key,why));
			return false;
		}
		if(key.toUpperCase().endsWith(" DELETE"))
		{
			key=key.substring(0,key.length()-7).trim();
			mob.tell(JSStore.remove(key)?L("Store '@x1' taken away.",key):L("There is no store '@x1'.",key));
			return false;
		}
		final String j=JSStore.json(key,2);
		mob.tell((j==null)?L("There is no store '@x1'. JS STATE lists them.",key):(key+" = "+j));
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
