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

	/**
	 * WORLD CHECK (AREA name) (LOG) (ONLY=part,part): the world's lint, by
	 * severity ({@link WorldCheck}).
	 * @param mob the one asking
	 * @param words the words after CHECK
	 */
	protected void worldCheck(final MOB mob, final List<String> words)
	{
		Area only=null;
		boolean log=false;
		final Set<String> parts=new TreeSet<String>();
		for(int i=0;i<words.size();i++)
		{
			final String w=words.get(i);
			if(w.equalsIgnoreCase("LOG"))
				log=true;
			else
			if(w.toUpperCase().startsWith("ONLY="))
			{
				for(final String p : w.substring(5).split(","))
				{
					final String part=p.trim().toUpperCase();
					if(!Arrays.asList(WorldCheck.PARTS).contains(part))
					{
						mob.tell(L("'@x1' is not a part of WORLD CHECK: @x2.",p,String.join(", ",WorldCheck.PARTS).toLowerCase()));
						return;
					}
					parts.add(part);
				}
			}
			else
			if(w.equalsIgnoreCase("AREA"))
			{
				final String name=(i<words.size()-1)?words.get(++i):"";
				only=(name.length()>0)?CMLib.map().getArea(name):((mob.location()==null)?null:mob.location().getArea());
				if(only==null)
				{
					mob.tell(L("There is no area '@x1'.",name));
					return;
				}
			}
			else
			{
				mob.tell(L("WORLD CHECK (AREA [NAME]) (LOG) (ONLY=@x1)",String.join(",",WorldCheck.PARTS).toLowerCase()));
				return;
			}
		}
		final List<String> lines=WorldCheck.report(WorldCheck.run(only,parts));
		if(log)
		{
			for(final String line : lines)
				Log.sysOut("WorldCheck",line);
		}
		mob.tell(String.join("\n\r",lines));
	}

	/**
	 * WORLD DEV (DT27): the dev areas, or, in builder mode, WORLD DEV area
	 * ON|OFF makes an area one (tagged DEV, written to world/dev on SAVE,
	 * loaded only in builder mode and DEV=1 games) or a shipped one again.
	 * @param mob the builder
	 * @param words the words after DEV
	 */
	protected void worldDev(final MOB mob, final List<String> words)
	{
		final String last = words.isEmpty() ? "" : words.get(words.size() - 1).toUpperCase();
		if(words.isEmpty() || ((!last.equals("ON")) && (!last.equals("OFF"))))
		{
			final StringBuilder str = new StringBuilder(L("Dev areas (world/dev, loaded only in builder mode and DEV=1):"));
			int n = 0;
			for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			{
				final Area A = a.nextElement();
				if(com.planet_ink.coffee_mud.core.save.WorldFiles.isDevArea(A))
				{
					str.append("\n\r  ").append(A.Name()).append(" (").append(A.numberOfProperIDedRooms()).append(" rooms)");
					n++;
				}
			}
			if(n == 0)
				str.append(L(" none."));
			str.append("\n\r").append(L("WORLD DEV [AREA] ON/OFF makes an area one, or a shipped one again, in builder mode."));
			mob.tell(str.toString());
			return;
		}
		if(!SaveGame.isBuilderMode())
		{
			mob.tell(L("WORLD DEV ON and OFF move an area's file, so they are for builder mode."));
			return;
		}
		final String name = CMParms.combine(words.subList(0, words.size() - 1), 0);
		final Area A = (name.length() == 0) ? mob.location().getArea() : CMLib.map().getArea(name);
		if(A == null)
		{
			mob.tell(L("There is no area '@x1'.", name));
			return;
		}
		if(last.equals("ON"))
			A.addTag(com.planet_ink.coffee_mud.core.save.WorldFiles.DEV_TAG);
		else
			A.delTag(com.planet_ink.coffee_mud.core.save.WorldFiles.DEV_TAG);
		CMLib.database().DBUpdateArea(A.Name(), A);
		mob.tell(last.equals("ON")
			? L("@x1 is a dev area: SAVE writes it to world/dev, and normal games leave it out.", A.Name())
			: L("@x1 is a shipped area again: SAVE writes it to world/areas.", A.Name()));
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
		if(commands.get(1).equalsIgnoreCase("CHECK"))
		{
			if((!SaveGame.isBuilderMode()) && (!SaveGame.isDevMode()) && (!CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS)))
			{
				mob.tell(L("WORLD CHECK is for builder mode or a DEV=1 game."));
				return false;
			}
			worldCheck(mob, commands.subList(2, commands.size()));
			return false;
		}
		if(commands.get(1).equalsIgnoreCase("DEV"))
		{
			worldDev(mob, commands.subList(2, commands.size()));
			return false;
		}
		if(!commands.get(1).equalsIgnoreCase("SET"))
		{
			mob.tell(L("WORLD shows the world settings, WORLD PROBLEMS what did not load from the world files, WORLD CHECK what is wrong in the world, WORLD DEV the areas for building and testing only, and WORLD SET KEY=VALUE changes a setting in builder mode."));
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
