package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.save.WorldSettings;
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
 * WORLD shows the world settings in effect (as LIST WORLDINI does), and in
 * builder mode WORLD SET KEY=VALUE writes one to the world's world.ini.
 */
public class WorldCmd extends StdCommand
{
	public WorldCmd()
	{
	}

	private final String[] access=I(new String[]{"WORLD"});

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * Returns the world settings in effect: each key a world may set that is
	 * set, with its value and where it came from, then those not set.
	 * @return the listing
	 */
	public static String listing()
	{
		final StringBuilder str=new StringBuilder(CMLib.lang().L("World settings, which a world sets in @x1:\n\r", WorldSettings.FILE));
		final List<String> notSet=new ArrayList<String>();
		final Set<String> shown=new HashSet<String>();
		final List<String> keys=new ArrayList<String>();
		for(final String key : WorldSettings.keys())
		{
			if(!key.endsWith("*"))
				keys.add(key);
		}
		// the prefixed ones that are set, such as START_DWARF
		final List<String> prefixed=new ArrayList<String>();
		for(final Map.Entry<String,String> e : WorldSettings.worldValues().entrySet())
		{
			if(!keys.contains(e.getKey()))
				prefixed.add(e.getKey());
		}
		Collections.sort(prefixed);
		keys.addAll(prefixed);
		for(final String key : keys)
		{
			if(!shown.add(key))
				continue;
			final String value=CMProps.instance().getStr(key, "");
			final String from=WorldSettings.sourceOf(key);
			if((value.length()==0)&&(from.length()==0))
			{
				notSet.add(key);
				continue;
			}
			str.append("  ").append(CMStrings.padRight(key, 20)).append(" = ").append(value);
			if(from.length()>0)
				str.append("  (").append(from).append(")");
			str.append("\n\r");
		}
		if(notSet.size()>0)
			str.append(CMLib.lang().L("Not set: @x1\n\r", CMParms.toListString(notSet.iterator())));
		str.append(CMLib.lang().L("Also keys starting @x1.\n\r", CMParms.toListString(prefixesOf(WorldSettings.keys()).iterator())));
		str.append(CMLib.lang().L("Each takes effect when the game starts.  In builder mode, WORLD SET KEY=VALUE changes one in @x1.\n\r", WorldSettings.FILE));
		return str.toString();
	}

	private static List<String> prefixesOf(final List<String> keys)
	{
		final List<String> prefixes=new ArrayList<String>();
		for(final String key : keys)
		{
			if(key.endsWith("*"))
				prefixes.add(key.substring(0,key.length()-1));
		}
		return prefixes;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if((commands.size()<2)||(commands.get(1).equalsIgnoreCase("LIST")))
		{
			mob.tell(listing());
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("PROBLEMS"))
		{
			final Map<String,List<String>> problems=com.planet_ink.coffee_mud.core.save.WorldFiles.problems();
			if(problems.isEmpty())
			{
				mob.tell(L("The world files loaded whole."));
				return false;
			}
			final StringBuilder str=new StringBuilder(L("These world files loaded only in part, so builder mode does not write them until they are fixed (and the world is loaded again), or SAVE FORCE writes them as the world has them:\n\r"));
			for(final String file : problems.keySet())
			{
				str.append(file).append(":\n\r");
				for(final String line : problems.get(file))
					str.append("  ").append(line).append("\n\r");
			}
			mob.tell(str.toString());
			return false;
		}
		if(!commands.get(1).equalsIgnoreCase("SET"))
		{
			mob.tell(L("WORLD shows the world settings, WORLD PROBLEMS what did not load from the world files, and WORLD SET KEY=VALUE changes a setting in builder mode."));
			return false;
		}
		if(!SaveGame.isBuilderMode())
		{
			mob.tell(L("WORLD SET changes the world's own files, so it is for builder mode: start CoffeeSUD with BUILDER=1."));
			return false;
		}
		if(!CMSecurity.isASysOp(mob))
		{
			mob.tell(L("You are not allowed to do that."));
			return false;
		}
		final String rest=CMParms.combine(commands,2).trim();
		final int x=rest.indexOf('=');
		if(x<=0)
		{
			mob.tell(L("WORLD SET KEY=VALUE, such as WORLD SET START=Emberfall#0.  A blank value takes the key out."));
			return false;
		}
		final String key=rest.substring(0,x).trim().toUpperCase();
		final String value=rest.substring(x+1).trim();
		if(!WorldSettings.isWorldKey(key))
		{
			mob.tell(L("@x1 is not a world setting: those are listed by WORLD, and the engine's own settings stay in coffeemud.ini.", key));
			return false;
		}
		WorldSettings.set(WorldFiles.worldDir(), key, value);
		if(value.length()==0)
			mob.tell(L("@x1 is taken out of @x2.  It takes effect the next time the game starts.", key, WorldSettings.FILE));
		else
			mob.tell(L("@x1=@x2 is set in @x3.  It takes effect the next time the game starts.", key, value, WorldSettings.FILE));
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}
}
