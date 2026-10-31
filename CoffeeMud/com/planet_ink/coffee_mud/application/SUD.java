package com.planet_ink.coffee_mud.application;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

import com.planet_ink.coffee_mud.core.CMProps;
import com.planet_ink.coffee_mud.core.Party;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.SaveSlots;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

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
 * The single-player entry point.  Boots the CoffeeMud engine with no network
 * listener, plays one character through this process's console, and exits
 * when that character quits.
 *
 * Before the engine starts, it lists the saved games and asks which to
 * restore, or whether to start a new game.  These settings, from the ini file
 * or the command line, change that:
 * <ul>
 * <li>RESTORE=NAME restores that save without asking; RESTORE=NEW starts a
 * new game without asking.</li>
 * <li>SAVEDIR=PATH is where saves go; the default is "saves".</li>
 * <li>BUILDER=1 plays on the database cache itself, as an Archon, to change
 * the world files, with no saves.</li>
 * <li>WORLDDIR=PATH is the folder of world files; the default is "world".</li>
 * <li>WINDOWS=1 gives each of the player's characters in the world, but for
 * the one played here, a window of their own.</li>
 * </ul>
 *
 * Any NAME=VALUE argument overrides the ini file, as with {@link MUD}.
 */
public class SUD
{
	private static final String[] SINGLE_PLAYER_SETTINGS =
	{
		// the profile is an account holding all the player's characters
		"COMMONACCOUNTSYSTEM=50",
		"EMAILREQ=DISABLED",
		"IDLETIMERS=0 0",
		"SYSMSGS=FILE",
		"ERRMSGS=FILE",
		"WRNMSGS=FILE",
		"DBGMSGS=FILE",
	};

	public static void main(final String[] args)
	{
		CMProps.setSinglePlayer(true);
		MUD.consoleMode = true;
		final Properties settings = readSettings(args);
		final String builder = settings.getProperty("BUILDER", "").trim().toUpperCase();
		final boolean builderMode = builder.equals("1") || builder.startsWith("Y") || builder.startsWith("T") || builder.equals("ON");
		final File saveDir = new File(settings.getProperty("SAVEDIR", "saves").trim());
		SaveGame.configure(saveDir, builderMode);
		WorldFiles.configure(new File(settings.getProperty("WORLDDIR", "world").trim()));
		final String windows = settings.getProperty("WINDOWS", "").trim().toUpperCase();
		if(windows.equals("1") || windows.startsWith("Y") || windows.startsWith("T") || windows.equals("ON"))
		{
			if(!Party.setWindows(true))
				System.out.println("There is no display here, so the characters' windows (WINDOWS=1) are off.");
		}
		if(builderMode)
			System.out.println("Builder mode: changes go straight into the world, and there are no saved games.");
		else
		{
			final String restore = settings.getProperty("RESTORE", "").trim();
			if(restore.equalsIgnoreCase("NEW"))
				SaveGame.setNextBoot(null);
			else
			if(restore.length() > 0)
			{
				final SaveSlots.Slot slot = SaveSlots.find(saveDir, restore);
				if(slot == null)
				{
					System.out.println("There is no saved game called '" + restore + "'.");
					System.exit(1);
				}
				SaveGame.setNextBoot(slot.name);
			}
			else
				SaveGame.setNextBoot(chooseSave(saveDir));
		}
		final List<String> fullArgs = new ArrayList<String>();
		fullArgs.add("CoffeeSUD");
		fullArgs.addAll(Arrays.asList(SINGLE_PLAYER_SETTINGS));
		fullArgs.addAll(Arrays.asList(args));
		System.out.println("Loading the world...");
		MUD.main(fullArgs.toArray(new String[fullArgs.size()]));
		if(!builderMode)
			SaveSlots.deleteWorkingDatabases(saveDir);
		System.exit(0);
	}

	/**
	 * Reads the ini file, with the command line's NAME=VALUE arguments over
	 * it, for the settings needed before the engine starts.
	 * @param args the command line
	 * @return the settings
	 */
	protected static Properties readSettings(final String[] args)
	{
		final Properties settings = new Properties();
		// read as plain NAME=VALUE lines, not as a Java properties file, which
		// would take the backslashes in a Windows path for escapes
		try(BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream("coffeemud.ini"), "UTF-8")))
		{
			String line;
			while((line = in.readLine()) != null)
			{
				line = line.trim();
				final int x = line.indexOf('=');
				if((x > 0) && (!line.startsWith("#")) && (!line.startsWith("!")))
					settings.setProperty(line.substring(0, x).trim().toUpperCase(), line.substring(x + 1).trim());
			}
		}
		catch(final IOException e)
		{
		}
		for(final String arg : args)
		{
			final int x = arg.indexOf('=');
			if(x > 0)
				settings.setProperty(arg.substring(0, x).trim().toUpperCase(), arg.substring(x + 1));
		}
		return settings;
	}

	/**
	 * Lists the saved games and asks which to restore.
	 * @param saveDir the folder that holds the saves
	 * @return the save to restore, or null for a new game
	 */
	protected static String chooseSave(final File saveDir)
	{
		final List<SaveSlots.Slot> slots = SaveSlots.list(saveDir);
		if(slots.size() == 0)
			return null;
		System.out.println("Saved games:");
		for(int i = 0; i < slots.size(); i++)
		{
			final SaveSlots.Slot slot = slots.get(i);
			System.out.println(String.format("  %2d) %-12s %s", Integer.valueOf(i + 1), slot.name, SaveSlots.describe(slot)));
		}
		while(true)
		{
			System.out.print("Enter a number or name to restore a game, or just Enter for a new game: ");
			System.out.flush();
			final String line = readLine();
			if(line == null)
				return null;
			if((line.trim().length() == 0) || line.trim().equalsIgnoreCase("NEW"))
				return null;
			final SaveSlots.Slot slot = SaveSlots.find(saveDir, line);
			if(slot != null)
				return slot.name;
			System.out.println("There is no saved game called '" + line.trim() + "'.");
		}
	}

	/**
	 * Reads a line from the console a byte at a time, so that nothing after
	 * it is taken from the stream the game reads next.
	 * @return the line, or null at the end of input
	 */
	protected static String readLine()
	{
		final StringBuilder line = new StringBuilder();
		try
		{
			int b;
			while((b = System.in.read()) >= 0)
			{
				if(b == '\n')
					return line.toString();
				if(b != '\r')
					line.append((char)b);
			}
		}
		catch(final IOException e)
		{
		}
		return (line.length() > 0) ? line.toString() : null;
	}
}
