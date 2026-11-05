package com.planet_ink.coffee_mud.application;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import com.planet_ink.coffee_mud.core.CMProps;
import com.planet_ink.coffee_mud.core.Party;
import com.planet_ink.coffee_mud.core.WorldCheck;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.SaveSlots;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.core.save.WorldSettings;

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
 * <li>DEV=1 makes a developer's game: a normal game, with saves, in which
 * each character has the developer security group (DEVGROUP, DEV by
 * default); saves made in it are marked [dev].</li>
 * <li>CHECK=1 loads the world as a new game would, on a scratch copy, runs
 * WORLD CHECK with no player, prints it, and exits 1 if it found errors.</li>
 * </ul>
 *
 * Any NAME=VALUE argument overrides the ini file, as with {@link MUD}.
 */
public class SUD
{
	/** the class path settings a world can add its own classes to, in WORLDDIR/classes/[key in lower case] */
	private static final String[] CLASS_KEYS =
	{
		"COMMON", "ITEMS", "WEAPONS", "ARMOR", "MISCMAGIC", "CLANITEMS", "MOBS", "BEHAVIORS", "ABILITIES",
		"CHARCLASSES", "RACES", "COMMANDS", "LOCALES", "AREAS", "EXITS", "TECH", "COMPTECH", "SOFTWARE"
	};

	/** JS5: the folders of JavaScript classes in WORLDDIR/js/, and the class path settings they join */
	private static final String[][] JS_KINDS =
	{
		{ "behaviors", "BEHAVIORS" }, { "abilities", "ABILITIES" }, { "properties", "ABILITIES" }, { "items", "ITEMS" },
		{ "mobs", "MOBS" }, { "races", "RACES" }, { "charclasses", "CHARCLASSES" }, { "commands", "COMMANDS" },
		{ "common", "COMMON" }, { "locales", "LOCALES" }, { "exits", "EXITS" }, { "areas", "AREAS" }
	};

	/**
	 * The class path settings a world adds its own classes to: its
	 * classes/[kind]/ folders, and (JS5) its js/[kind]/ folders of JavaScript
	 * classes, after the stock ones.
	 * @param settings the ini's settings
	 * @return the settings to give the engine, by name
	 */
	public static Map<String, String> worldClassPaths(final Properties settings)
	{
		final Map<String, String> paths = new LinkedHashMap<String, String>();
		final File world = WorldFiles.worldDir();
		for(final String key : CLASS_KEYS)
		{
			if(new File(world, "classes/" + key.toLowerCase()).isDirectory())
				paths.put(key, settings.getProperty(key, "%DEFAULT%").trim() + ";world/classes/" + key.toLowerCase());
		}
		for(final String[] k : JS_KINDS)
		{
			if(new File(world, "js/" + k[0]).isDirectory())
			{
				final String was = paths.containsKey(k[1]) ? paths.get(k[1]) : settings.getProperty(k[1], "%DEFAULT%").trim();
				paths.put(k[1], was + ";world/js/" + k[0]);
			}
		}
		return paths;
	}

	/** the world's own settings, from WORLDDIR/world.ini */
	private static Map<String, String> worldSettings = new LinkedHashMap<String, String>();

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
		String gameName = settings.getProperty("GAMENAME", "").trim();
		if(gameName.length() == 0)
			gameName = "CoffeeSUD";
		SaveGame.configure(saveDir, builderMode);
		if(!builderMode)
		{
			try { SaveSlots.acquire(saveDir); SaveSlots.recover(saveDir); }
			catch(final java.io.IOException e) { System.out.println(e.getMessage()); System.exit(1); return; }
		}
		WorldFiles.configure(new File(settings.getProperty("WORLDDIR", "world").trim()));
		final String dev = settings.getProperty("DEV", "").trim().toUpperCase();
		if(dev.equals("1") || dev.startsWith("Y") || dev.startsWith("T") || dev.equals("ON"))
		{
			if(builderMode)
				System.out.println("DEV=1 is for a normal game: builder mode has every power already.");
			else
			{
				SaveGame.setDevMode(true);
				System.out.println("A developer's game (DEV=1): each character has the developer security group, and saves are marked [dev].");
			}
		}
		final String windows = settings.getProperty("WINDOWS", "").trim().toUpperCase();
		if(windows.equals("1") || windows.startsWith("Y") || windows.startsWith("T") || windows.equals("ON"))
		{
			if(!Party.setWindows(true))
				System.out.println("There is no display here, so the characters' windows (WINDOWS=1) are off.");
		}
		final String check = settings.getProperty("CHECK", "").trim().toUpperCase();
		final boolean checkOnly = check.equals("1") || check.startsWith("Y") || check.startsWith("T") || check.equals("ON");
		if(checkOnly)
		{
			// a new game's world, on a scratch copy, checked with no one in it
			WorldCheck.setHeadless(true);
			System.out.println("World check (CHECK=1): the world is loaded, checked, and the game ends.");
			SaveGame.setNextBoot(null);
		}
		else
			showTitle(gameName);
		if(checkOnly)
		{
			// nothing to choose
		}
		else
		if(builderMode)
			System.out.println("Builder mode: changes go straight into the world, and there are no saved games.");
		else
		{
			final String restore = settings.getProperty("RESTORE", "").trim();
			if(restore.equalsIgnoreCase("NEW"))
			{
				SaveGame.setNextBoot(null);
				showPrologue();
			}
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
				try { SaveSlots.validateFormat(saveDir, slot.name); }
				catch(final java.io.IOException e) { System.out.println(e.getMessage()); System.exit(1); return; }
				SaveGame.setNextFresh(settings.getProperty("FRESH", ""));
			}
			else
			{
				final String chosen = chooseSave(saveDir);
				if(chosen != null)
				{
					try { SaveSlots.validateFormat(saveDir, chosen); }
					catch(final java.io.IOException e) { System.out.println(e.getMessage()); System.exit(1); return; }
				}
				SaveGame.setNextBoot(chosen);
				if(chosen == null)
					showPrologue();
			}
		}
		final String moduleProblem = SaveGame.moduleStartupProblem(settings);
		if(moduleProblem != null)
		{
			System.out.println(moduleProblem);
			if(!builderMode) SaveSlots.release(saveDir);
			System.exit(1);
			return;
		}
		final List<String> fullArgs = new ArrayList<String>();
		fullArgs.add(gameName);
		fullArgs.addAll(Arrays.asList(SINGLE_PLAYER_SETTINGS));
		// the world's settings over coffeemud.ini, and the command line over them
		for(final Map.Entry<String, String> e : worldSettings.entrySet())
			fullArgs.add(e.getKey() + "=" + e.getValue());
		// the world's own classes, in WORLDDIR/classes/<kind>/ and (JS5) WORLDDIR/js/<kind>/, after the stock ones
		for(final Map.Entry<String, String> e : worldClassPaths(settings).entrySet())
			fullArgs.add(e.getKey() + "=" + e.getValue());
		fullArgs.addAll(Arrays.asList(args));
		System.out.println("Loading the world...");
		try { MUD.main(fullArgs.toArray(new String[fullArgs.size()])); }
		finally { if(!builderMode) SaveSlots.release(saveDir); }
		System.exit(checkOnly ? WorldCheck.exitCode() : 0);
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
		final Map<String, String> from = new HashMap<String, String>();
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
				{
					final String key = line.substring(0, x).trim().toUpperCase();
					final String value = line.substring(x + 1).trim();
					settings.setProperty(key, value);
					if(value.length() > 0)
						from.put(key, WorldSettings.FROM_INI);
				}
			}
		}
		catch(final IOException e)
		{
		}
		final Map<String, String> commandLine = new LinkedHashMap<String, String>();
		for(final String arg : args)
		{
			final int x = arg.indexOf('=');
			if(x > 0)
				commandLine.put(arg.substring(0, x).trim().toUpperCase(), arg.substring(x + 1));
		}
		// the world's own settings, between the two
		String worldDir = commandLine.get("WORLDDIR");
		if(worldDir == null)
			worldDir = settings.getProperty("WORLDDIR", "world");
		final List<String> ignored = new ArrayList<String>();
		worldSettings = WorldSettings.read(new File(worldDir.trim()), ignored);
		if(ignored.size() > 0)
			System.out.println(WorldSettings.FILE + ": " + String.join(", ", ignored) + (ignored.size() == 1 ? " is" : " are")
				+ " not a world setting, so ignored (the engine's own settings go in coffeemud.ini).");
		for(final Map.Entry<String, String> e : worldSettings.entrySet())
		{
			settings.setProperty(e.getKey(), e.getValue());
			from.put(e.getKey(), WorldSettings.FROM_WORLD);
		}
		for(final Map.Entry<String, String> e : commandLine.entrySet())
		{
			settings.setProperty(e.getKey(), e.getValue());
			from.put(e.getKey(), WorldSettings.FROM_COMMAND);
		}
		WorldSettings.noteStarted(worldSettings, from);
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
		// newest first, so the first is the game to continue
		final SaveSlots.Slot latest = slots.get(0);
		System.out.println("  C) Continue - " + latest.name + ", " + SaveSlots.describe(latest));
		System.out.println("  N) New game");
		System.out.println("  Q) Quit");
		System.out.println("Saved games:");
		for(int i = 0; i < slots.size(); i++)
		{
			final SaveSlots.Slot slot = slots.get(i);
			System.out.println(String.format("  %2d) %-12s %s", Integer.valueOf(i + 1), slot.name, SaveSlots.describe(slot)));
		}
		while(true)
		{
			System.out.print("Choose C, N or Q, or a saved game's number or name (Enter continues): ");
			System.out.flush();
			final String line = readLine();
			if(line == null)
				return null;
			final String choice = line.trim();
			if((choice.length() == 0) || choice.equalsIgnoreCase("C") || choice.equalsIgnoreCase("CONTINUE"))
				return latest.name;
			if(choice.equalsIgnoreCase("N") || choice.equalsIgnoreCase("NEW"))
				return null;
			if(choice.equalsIgnoreCase("Q") || choice.equalsIgnoreCase("QUIT"))
				System.exit(0);
			final SaveSlots.Slot slot = SaveSlots.find(saveDir, choice);
			if(slot != null)
				return slot.name;
			System.out.println("There is no saved game called '" + choice + "'.");
		}
	}

	/**
	 * Returns a text file of the world's, world/text/NAME, or null if the
	 * world has none.
	 * @param name the file's name
	 * @return its text, or null
	 */
	protected static String worldText(final String name)
	{
		final File dir = WorldFiles.worldDir();
		if(dir == null)
			return null;
		final File f = new File(new File(dir, "text"), name);
		if(!f.isFile())
			return null;
		try
		{
			return new String(java.nio.file.Files.readAllBytes(f.toPath()), "UTF-8");
		}
		catch(final IOException e)
		{
			return null;
		}
	}

	/**
	 * Shows the world's title screen, world/text/title.txt, if it has one,
	 * with @MudInfo?NAME@ as the game's name and colour codes in colour.
	 * @param gameName the game's name
	 */
	protected static void showTitle(final String gameName)
	{
		final String text = worldText("title.txt");
		if(text == null)
			return;
		System.out.println(colorize(text.replace("@MudInfo?NAME@", gameName)));
	}

	/**
	 * Shows the world's prologue, world/text/prologue.txt, if it has one, a
	 * page at a time: pages are separated by a line of three or more dashes.
	 */
	protected static void showPrologue()
	{
		final String text = worldText("prologue.txt");
		if(text == null)
			return;
		final String[] pages = text.split("(?m)^-{3,}[ \\t]*\\r?$");
		for(int i = 0; i < pages.length; i++)
		{
			System.out.println(colorize(pages[i].replaceAll("^\\s*\\n", "").replaceAll("\\s+$", "")));
			if(i < pages.length - 1)
			{
				System.out.print("[Enter] ");
				System.out.flush();
				if(readLine() == null)
					return;
			}
		}
		System.out.println();
	}

	/**
	 * Turns the game's colour codes (^r, ^G, ^N, ...) into the console's,
	 * for text shown before the engine starts.
	 * @param text the text
	 * @return the text in colour, ending in the normal colour
	 */
	protected static String colorize(final String text)
	{
		final String codes = "wrgybpckWRGYBPCK";
		final String[] ansi = {"0;37","0;31","0;32","0;33","0;34","0;35","0;36","0;30",
							   "1;37","1;31","1;32","1;33","1;34","1;35","1;36","1;30"};
		final StringBuilder str = new StringBuilder();
		for(int i = 0; i < text.length(); i++)
		{
			final char c = text.charAt(i);
			if((c == '^') && (i + 1 < text.length()))
			{
				final char n = text.charAt(i + 1);
				final int x = codes.indexOf(n);
				if(x >= 0)
				{
					str.append("\033[").append(ansi[x]).append('m');
					i++;
					continue;
				}
				if((n == 'N') || (n == '?'))
				{
					str.append("\033[0m");
					i++;
					continue;
				}
				if(n == '^')
				{
					str.append('^');
					i++;
					continue;
				}
			}
			str.append(c);
		}
		return str.append("\033[0m").toString();
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
