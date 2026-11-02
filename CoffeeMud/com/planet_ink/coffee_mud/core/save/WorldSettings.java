package com.planet_ink.coffee_mud.core.save;

import java.io.*;
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
 * The settings that make a world the game it is, kept with the world in
 * {@code WORLDDIR/world.ini}: where characters start and go when they die,
 * what they start with, the calendar, the game's rules, its name.  They are
 * read after coffeemud.ini and before the command line, so a world brings
 * its own rules and the command line still has the last word.  Only the
 * keys listed here are taken from world.ini; the engine's own settings
 * (ports, the database, logs) stay in coffeemud.ini, and any other key is
 * reported and ignored.
 */
public final class WorldSettings
{
	/** the world settings file, in the world folder */
	public static final String	FILE	= "world.ini";

	/** where a setting in effect came from */
	public static final String	FROM_INI		= "coffeemud.ini";
	public static final String	FROM_WORLD		= FILE;
	public static final String	FROM_COMMAND	= "the command line";

	/** the keys a world may set */
	private static final String[]	KEYS	=
	{
		// the game
		"GAMENAME", "MUD_NAME", "MUDTHEME",
		// where characters start and go when they die
		"START", "DEATH", "MORGUE",
		// new characters
		"STARTINGITEMS", "STARTHP", "STARTMANA", "STARTMOVE", "STARTSTAT",
		"CHARCREATIONSCRIPTS", "RACEMIXING", "CLASSSYSTEM", "MAXSTATS",
		"BASEMINSTAT", "BASEMAXSTAT", "STATCOSTS", "TRAINCOSTS",
		// time and the calendar
		"TIMEMODE", "MILLISPERMUDHOUR", "HOURSINDAY", "DAYSINMONTH", "MONTHSINYEAR",
		"DAYSINWEEK", "DAWNHR", "DAYHR", "DUSKHR", "NIGHTHR", "YEARDESC",
		// saving and the party
		"SAVEMODE", "AUTOSAVE", "AUTOSAVEKEEP", "PARTYMEET", "PARTYFIGHT", "PARTYTURNS",
		// the rules
		"AWARERANGE", "DEITYPOLICY", "FACTIONS", "DISABLE", "ENABLE",
		"NEWACODES", "NEWDOMAINS", "DEFAULTABILITYARGS", "EXPRATE", "XPMOD",
		"LASTPLAYERLEVEL", "INJURYSYSTEM", "COMBATSYSTEM", "PLAYERDEATH",
		"MOBDEATH", "HUNGER", "THIRST", "MANACOST", "DIRECTIONS", "CHATDEFAULT",
		// quests
		"QUESTMARKERS", "QUESTNOTICES", "QUESTTRACK",
		// NPCs
		"CATCHUP", "DIALOGUETIME", "CHECKROLLS", "HINTTOPICS",
		// combat
		"DIFFICULTY",
		// money
		"DEFAULTCURRENCY",
	};

	/** the starts of key names a world may set, such as START_DWARF */
	private static final String[]	PREFIXES	=
	{
		"START_", "DEATH_", "MORGUE_", "FORMULA_", "ADDCHARSTAT_", "REPLACECHARSTAT_", "DIFFICULTY_", "MOBRANK_", "CURRENCY_"
	};

	/** the world settings this game started with, in file order */
	private static final Map<String, String>	worldValues	= new LinkedHashMap<String, String>();
	/** where each setting in effect came from */
	private static final Map<String, String>	sources		= new TreeMap<String, String>();

	private WorldSettings()
	{
	}

	/**
	 * Returns whether a world may set a key in world.ini.
	 * @param key the key, in any case
	 * @return true if it is a world setting
	 */
	public static boolean isWorldKey(final String key)
	{
		final String k = key.trim().toUpperCase();
		for(final String K : KEYS)
		{
			if(K.equals(k))
				return true;
		}
		for(final String P : PREFIXES)
		{
			if(k.startsWith(P) && (k.length() > P.length()))
				return true;
		}
		return false;
	}

	/**
	 * Returns the keys a world may set, then the starts of keys it may set.
	 * @return the keys, and the starts of keys ending in *
	 */
	public static List<String> keys()
	{
		final List<String> all = new ArrayList<String>(Arrays.asList(KEYS));
		for(final String P : PREFIXES)
			all.add(P + "*");
		return all;
	}

	/**
	 * Reads a world's world.ini: its world settings, in the order written.
	 * Keys that are not world settings are left out, and named in
	 * {@code ignored}.
	 * @param worldDir the world folder
	 * @param ignored where to put the keys left out, or null
	 * @return the settings, keys in upper case
	 */
	public static Map<String, String> read(final File worldDir, final List<String> ignored)
	{
		final Map<String, String> settings = new LinkedHashMap<String, String>();
		final File F = new File(worldDir, FILE);
		if(!F.isFile())
			return settings;
		// read as plain NAME=VALUE lines, as coffeemud.ini is
		try(BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(F), "UTF-8")))
		{
			String line;
			while((line = in.readLine()) != null)
			{
				line = line.trim();
				final int x = line.indexOf('=');
				if((x <= 0) || line.startsWith("#") || line.startsWith("!"))
					continue;
				final String key = line.substring(0, x).trim().toUpperCase();
				if(isWorldKey(key))
					settings.put(key, line.substring(x + 1).trim());
				else
				if(ignored != null)
					ignored.add(key);
			}
		}
		catch(final IOException e)
		{
			if(ignored != null)
				ignored.add("(" + FILE + " could not be read: " + e.getMessage() + ")");
		}
		return settings;
	}

	/**
	 * Notes the world settings this game started with, and where each
	 * setting in effect came from, for LIST WORLDINI.
	 * @param world the world's settings, from world.ini
	 * @param from where each setting came from
	 */
	public static synchronized void noteStarted(final Map<String, String> world, final Map<String, String> from)
	{
		worldValues.clear();
		worldValues.putAll(world);
		sources.clear();
		sources.putAll(from);
	}

	/**
	 * Returns where a setting in effect came from.
	 * @param key the key
	 * @return coffeemud.ini, world.ini or the command line, or "" if none set it
	 */
	public static synchronized String sourceOf(final String key)
	{
		final String from = sources.get(key.toUpperCase());
		return (from == null) ? "" : from;
	}

	/**
	 * Returns the world settings this game started with, from world.ini.
	 * @return the settings
	 */
	public static synchronized Map<String, String> worldValues()
	{
		return new LinkedHashMap<String, String>(worldValues);
	}

	/**
	 * Sets a world setting in world.ini: changes its line, or adds one at
	 * the end, keeping the rest of the file as it is.  A blank value takes
	 * the line out.  It takes effect when the game next starts.
	 * @param worldDir the world folder
	 * @param key the key
	 * @param value its value, or "" to take it out
	 * @throws IOException if the file cannot be written
	 */
	public static void set(final File worldDir, final String key, final String value) throws IOException
	{
		final String k = key.trim().toUpperCase();
		final File F = new File(worldDir, FILE);
		final List<String> lines = new ArrayList<String>();
		if(F.isFile())
		{
			try(BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(F), "UTF-8")))
			{
				String line;
				while((line = in.readLine()) != null)
					lines.add(line);
			}
		}
		else
		{
			lines.add("# The settings that make this world the game it is.  See LIST WORLDINI");
			lines.add("# for the keys a world may set, and coffeemud.ini for what each does.");
		}
		boolean done = false;
		for(int i = 0; i < lines.size(); i++)
		{
			final String line = lines.get(i).trim();
			final int x = line.indexOf('=');
			if((x <= 0) || line.startsWith("#") || line.startsWith("!"))
				continue;
			if(line.substring(0, x).trim().toUpperCase().equals(k))
			{
				if(done || (value.trim().length() == 0))
				{
					lines.remove(i);
					i--;
				}
				else
					lines.set(i, k + "=" + value.trim());
				done = true;
			}
		}
		if((!done) && (value.trim().length() > 0))
			lines.add(k + "=" + value.trim());
		worldDir.mkdirs();
		try(Writer out = new OutputStreamWriter(new FileOutputStream(F), "UTF-8"))
		{
			for(final String line : lines)
				out.write(line + "\n");
		}
	}
}
