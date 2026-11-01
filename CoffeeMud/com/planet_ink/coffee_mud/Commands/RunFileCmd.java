package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * B11, for builders: RUNFILE name (args) runs world/macros/NAME.cmd, a game
 * command to each line, as the builder typed it: $1 to $9 are the args,
 * $room the builder's room's ID and $area its area's name; '#' starts a
 * comment line; a line STOPONERROR stops at the first line that fails.  A
 * line fails if no command, social or skill the builder may use has its
 * first word, or if it throws.  At the end, a line of what ran and what
 * failed.
 */
public class RunFileCmd extends StdCommand
{
	public RunFileCmd()
	{
	}

	private final String[] access = I(new String[] { "RUNFILE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** the folder in the world folder the macros are in */
	public static final String	DIR	= "macros";

	/**
	 * The file a macro is in.
	 * @param name its name, with or without .cmd
	 * @return the file
	 */
	public static File fileFor(final String name)
	{
		final String n = new File(name).getName();
		return new File(new File(WorldFiles.worldDir(), DIR), n.toLowerCase().endsWith(".cmd") ? n : (n + ".cmd"));
	}

	/**
	 * A line with $1..$9, $room and $area put in.
	 * @param line the line
	 * @param mob the builder
	 * @param args the args
	 * @return the line
	 */
	public static String fill(final String line, final MOB mob, final List<String> args)
	{
		final Room R = mob.location();
		String s = line;
		s = s.replace("$room", (R == null) ? "" : CMLib.map().getExtendedRoomID(R));
		s = s.replace("$area", ((R == null) || (R.getArea() == null)) ? "" : R.getArea().Name());
		for(int i = 9; i >= 1; i--)
			s = s.replace("$" + i, (i <= args.size()) ? args.get(i - 1) : "");
		return s;
	}

	/**
	 * Runs a macro.
	 * @param mob the builder
	 * @param name the macro's name
	 * @param args its args
	 * @return the summary
	 */
	public static String run(final MOB mob, final String name, final List<String> args)
	{
		final File F = fileFor(name);
		if(!F.isFile())
			return "There is no world/" + DIR + "/" + F.getName() + ".";
		final List<String> lines;
		try
		{
			lines = Files.readAllLines(F.toPath(), StandardCharsets.UTF_8);
		}
		catch(final java.io.IOException e)
		{
			return F.getName() + ": " + e.getMessage();
		}
		boolean stopOnError = false;
		int ran = 0;
		final List<String> failed = new ArrayList<String>();
		boolean stopped = false;
		for(int i = 0; i < lines.size(); i++)
		{
			final String raw = lines.get(i).trim();
			if((raw.length() == 0) || raw.startsWith("#"))
				continue;
			if(raw.equalsIgnoreCase("STOPONERROR"))
			{
				stopOnError = true;
				continue;
			}
			final String line = fill(raw, mob, args);
			final List<String> words = CMParms.parse(line);
			if(words.isEmpty())
				continue;
			ran++;
			String why = null;
			final CMObject O = CMLib.english().findCommand(mob, new ArrayList<String>(words));
			if(O == null)
				why = "no command '" + words.get(0) + "' you may use";
			else
			{
				try
				{
					mob.doCommand(words, 0);
				}
				catch(final Exception e)
				{
					why = e.getClass().getSimpleName() + ((e.getMessage() == null) ? "" : (": " + e.getMessage()));
				}
			}
			if(why != null)
			{
				failed.add("line " + (i + 1) + " '" + line + "': " + why);
				if(stopOnError)
				{
					stopped = true;
					break;
				}
			}
		}
		final StringBuilder str = new StringBuilder("RUNFILE " + name + ": " + ran + " lines run, " + failed.size() + " failed");
		if(stopped)
			str.append(", and it stopped there (STOPONERROR)");
		str.append(failed.isEmpty() ? "." : ":");
		for(final String f : failed)
			str.append("\n\r  ").append(f);
		return str.toString();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		if(commands.size() < 2)
		{
			mob.tell(L("RUNFILE [NAME] ([ARGS]): runs world/macros/NAME.cmd, a command to each line."));
			return false;
		}
		mob.tell(run(mob, commands.get(1), new ArrayList<String>(commands.subList(2, commands.size()))));
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
		return SaveGame.isBuilderMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
