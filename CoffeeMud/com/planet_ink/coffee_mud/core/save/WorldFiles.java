package com.planet_ink.coffee_mud.core.save;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.CatalogLibrary.CataData;
import com.planet_ink.coffee_mud.Libraries.interfaces.DatabaseEngine;
import com.planet_ink.coffee_mud.Libraries.interfaces.DatabaseEngine.AckRecord;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

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
 * The game's content as files, in the world folder (WORLDDIR, "world" by
 * default):
 * <pre>
 * areas/NAME.cmare   one area: its rooms and exits, and the mobs and items
 *                    placed in them, as the builder's EXPORT writes them
 * custom.cmare       the custom races, classes, abilities and commands
 * catalog.cmare      the builder's catalog of mobs and items
 * quests/NAME.quest  one quest's script, as the quest list holds it
 * dev/NAME.cmare     an area for building and testing only: loaded in builder
 *                    mode and DEV=1 games, never in a normal one (DT27)
 * space.cmare        the things in space and in no room (ships, stations,
 *                    stars), with where they are and their courses; put in
 *                    space at each boot, as no database table holds them
 * </pre>
 * The files are the content.  The database the engine reads is built from
 * them, and the content database (DBSERVICE) is only a cache of that: it
 * holds the tables made from the files and a checksum of the files they were
 * made from ({@link #SUM_FILE}), and builder mode keeps its characters there.
 *
 * Before each boot, {@link SaveGame} compares the files with the cache's
 * checksum.  If they match, the game plays on a copy of the cache's content
 * tables.  If not (the files were edited, or pulled from git), it starts from
 * an empty database and this class builds it from the files during the boot:
 * the custom objects right after the database connects, so that the engine
 * loads them with its classes, then the areas, the catalog and the quests
 * when the map loads.  A normal game then copies what it built back to the
 * cache, so that the next start need not build it again.
 *
 * In builder mode, {@link #exportAll()} writes the content back to the files,
 * on the builder's SAVE and when the game shuts down.
 */
public final class WorldFiles
{
	/** the file, in a database's folder, with the checksum of the files it was built from */
	public static final String		SUM_FILE		= "world.sum";
	/** the tables built from the files */
	public static final String[]	CONTENT_TABLES	=
	{
		"CMAREA", "CMROOM", "CMROEX", "CMROCH", "CMROIT",
		"CMGRAC", "CMCCAC", "CMGAAC", "CMCDAC", "CMQUESTS"
	};

	private static final String	AREAS_DIR		= "areas";
	/** the folder of the areas for building and testing only (DT27) */
	public static final String	DEV_DIR			= "dev";
	/** the tag that makes an area a dev area: written to dev/, loaded only in builder mode and DEV=1 */
	public static final String	DEV_TAG			= "DEV";
	/** the file, in builder mode's database folder, with the checksum of the dev files its dev areas match */
	public static final String	DEV_SUM_FILE	= "dev.sum";
	/** whether the dev areas loaded whole, so that writing them out cannot lose any */
	private static volatile boolean	devLoadedWhole	= false;
	private static final String	QUESTS_DIR		= "quests";
	private static final String	CUSTOM_FILE		= "custom.cmare";
	private static final String	CATALOG_FILE	= "catalog.cmare";
	private static final String	AREA_EXT		= ".cmare";
	private static final String	QUEST_EXT		= ".quest";
	private static final String	TABLE_PREFIX	= "fakedb.data.";

	private static volatile File	worldDir	= new File("world");
	/** whether this boot builds its database from the files */
	private static volatile boolean	rebuild		= false;
	/** the checksum of the files this boot plays */
	private static volatile String	bootSum		= "";
	/** the checksum of each area file this boot plays, by file name */
	private static volatile Map<String, String>	bootAreaSums	= new TreeMap<String, String>();
	/** the cache to copy a database built from the files to, or null */
	private static volatile File	refreshCache= null;
	/** whether the content loaded whole, so that writing it out cannot lose any */
	private static volatile boolean	loadedWhole	= false;
	/**
	 * What went wrong in loading each world file that loaded only in part
	 * (an object of an unknown class skipped, say), by its path in the world
	 * folder; kept in the database's folder, as the cache was built from them
	 */
	private static final Map<String, List<String>>	problems	= new TreeMap<String, List<String>>();
	/** the files the last export did not write, as they loaded only in part */
	private static final List<String>				heldBack	= new ArrayList<String>();
	/**
	 * Each world file's checksum as the game last loaded or wrote it (B2), by
	 * its path in the world folder: a file that no longer matches was changed
	 * outside the game, and the export neither writes over it nor deletes it
	 */
	private static volatile Map<String, String>        knownSums    = new TreeMap<String, String>();
	/** the files the last export did not write or delete, as they were changed outside the game */
	private static final List<String>                keptOutside    = new ArrayList<String>();
	/** what is added to a file's name for the game's copy of it, beside a file changed outside the game */
	public static final String                        BUILDER_EXT    = ".builder";
	/** true while an export writes the files changed outside the game too (SAVE FORCE) */
	private static volatile boolean                    forcing        = false;
	/** the files an export left as the game has them, written, deleted or the same */
	private static volatile Set<String>                settled        = null;
	/** while WORLD STATUS asks what an export would do: that, noted in place of doing it */
	private static volatile ExportPlan                dryRun        = null;
	/** the file in the database's folder that keeps what went wrong */
	public static final String						PROBLEMS_FILE	= "worldproblems.txt";
	/** true while the files are imported */
	private static volatile boolean	importing	= false;

	private WorldFiles()
	{
	}

	/**
	 * Sets the world folder.  The launcher calls this before the engine
	 * starts.
	 * @param dir the folder
	 */
	public static void configure(final File dir)
	{
		worldDir = dir;
	}

	/**
	 * Returns the world folder.
	 * @return the folder
	 */
	public static File worldDir()
	{
		return worldDir;
	}

	/**
	 * Returns whether the files are being imported.  They are the game's own
	 * content, so what IMPORT refuses from files of unknown origin, such as
	 * the Archon's manual, is let in from them.
	 * @return true while importing them
	 */
	public static boolean isImporting()
	{
		return importing;
	}

	/**
	 * Returns whether a cache holds the content tables built from the files as
	 * they are now.
	 * @param cacheDir the cache's folder
	 * @return true if it does
	 */
	public static boolean isCacheCurrent(final File cacheDir)
	{
		return checksum().equals(readSum(cacheDir));
	}

	/**
	 * Called before each boot, once the database for it is ready: says whether
	 * the boot builds the database from the files.
	 * @param build true if the database starts empty and is built from the files
	 * @param cacheToRefresh where to copy the content tables once built, or null
	 */
	public static void beginBoot(final boolean build, final File cacheToRefresh)
	{
		rebuild = build;
		bootSum = checksum();
		bootAreaSums = areaSums();
		knownSums = fileSums();
		synchronized(keptOutside)
		{
			keptOutside.clear();
		}
		refreshCache = cacheToRefresh;
		loadedWhole = false;
	}

	/**
	 * Deletes a database's content tables, so that the next boot builds them
	 * from the files.  The database must not be open.
	 * @param dbDir the database's folder
	 */
	public static void clearContentTables(final File dbDir)
	{
		for(final String table : CONTENT_TABLES)
			new File(dbDir, TABLE_PREFIX + table).delete();
		new File(dbDir, SUM_FILE).delete();
		new File(dbDir, DEV_SUM_FILE).delete();
	}

	/**
	 * Called during the boot as soon as the database connects, before the
	 * engine loads its classes: when building from the files, puts the custom
	 * races, classes, abilities and commands into the database, where the
	 * engine loads them from with the rest of its classes.
	 */
	public static void afterDatabaseConnect()
	{
		if(!rebuild)
			return;
		int made = 0;
		for(final String[] element : customElements())
		{
			final String tag = element[0];
			final String whole = element[1];
			final String attrs = element[2];
			final String inner = element[3];
			if(tag.equals("RACE"))
				CMLib.database().DBCreateRace(firstValue(inner, "ID"), whole);
			else
			if(tag.equals("CCLASS"))
				CMLib.database().DBCreateClass(firstValue(inner, "ID"), whole);
			else
			if(tag.equals("ABILITY"))
				CMLib.database().DBCreateAbility(attribute(attrs, "ID"), attribute(attrs, "TYPE"), inner.trim());
			else
			if(tag.equals("COMMAND"))
				CMLib.database().DBCreateCommand(attribute(attrs, "ID"), attribute(attrs, "BASE"), inner.trim());
			else
			if(tag.equals("BEHAVIOR")) // N7
				CMLib.database().DBCreateCommand(attribute(attrs, "ID"), GenBehaviors.BASE, inner.trim());
			else
				continue;
			made++;
		}
		if(made > 0)
			Log.sysOut("WorldFiles", "Custom objects from the world files: " + made);
	}

	/**
	 * Loads the map, in place of the engine's own load from the database.  When
	 * building from the files, imports the areas, the catalog and the quests,
	 * then records the files' checksum and copies what was built to the cache.
	 */
	public static void loadWorld()
	{
		// W2: the world's currency file, now that the world's folder is known
		if(CMLib.beanCounter() instanceof com.planet_ink.coffee_mud.Libraries.BeanCounter)
			((com.planet_ink.coffee_mud.Libraries.BeanCounter)CMLib.beanCounter()).reloadCurrencies();
		loadWorldFiles();
		loadDevAreas();
		Calendars.applyAll(); // W5: the areas' CALENDARs
	}

	/**
	 * Whether an area is a dev area (DT27): tagged DEV, as those from dev/ are.
	 * @param A the area
	 * @return true if it is
	 */
	public static boolean isDevArea(final Area A)
	{
		return (A != null) && A.hasTag(DEV_TAG);
	}

	/**
	 * Whether this game loads the dev areas: builder mode and DEV=1 games.
	 * @return true if it does
	 */
	public static boolean devAreasWanted()
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode();
	}

	/**
	 * A checksum of the dev area files, kept apart from {@link #checksum()} so
	 * that they do not make a normal game rebuild its world.
	 * @return the checksum
	 */
	public static String devChecksum()
	{
		final CRC32 crc = new CRC32();
		int n = 0;
		for(final File F : listFiles(new File(worldDir, DEV_DIR), AREA_EXT))
		{
			n++;
			crc.update(F.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			try
			{
				crc.update(java.nio.file.Files.readAllBytes(F.toPath()));
			}
			catch(final IOException e)
			{
				crc.update(1);
			}
		}
		return "dev-" + n + "-" + Long.toHexString(crc.getValue());
	}

	/**
	 * After the map has loaded (DT27).  A normal game leaves out any dev area
	 * the cache holds (builder mode plays on the cache and keeps them there).
	 * Builder mode and DEV=1 load the dev/ files, but builder mode keeps the
	 * dev areas its cache holds when they match the files, so that what was
	 * built and not yet saved is kept, as the shipped areas' are.
	 */
	public static void loadDevAreas()
	{
		devLoadedWhole = false;
		final List<Area> have = new ArrayList<Area>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			if(isDevArea(A))
				have.add(A);
		}
		if(!devAreasWanted())
		{
			// only this game's working copy of the database loses them
			for(final Area A : have)
				CMLib.map().obliterateMapArea(A);
			if(have.size() > 0)
				Log.sysOut("WorldFiles", "Dev areas left out of a normal game: " + have.size());
			return;
		}
		final File dbDir = SaveGame.databaseDir();
		final String sum = devChecksum();
		if(SaveGame.isBuilderMode() && (dbDir != null) && sum.equals(trimmed(readText(new File(dbDir, DEV_SUM_FILE)))))
		{
			devLoadedWhole = true;
			return;
		}
		for(final Area A : have)
			CMLib.map().obliterateMapArea(A);
		boolean whole = true;
		int loaded = 0;
		importing = true;
		try
		{
			for(final File F : listFiles(new File(worldDir, DEV_DIR), AREA_EXT))
			{
				final String name = areaNameIn(F);
				final boolean ok = report(F, imported(DEV_DIR + "/" + F.getName(), () -> importArea(F)));
				whole = ok && whole;
				final Area A = (name == null) ? null : CMLib.map().getArea(name);
				if(A != null)
				{
					loaded++;
					if(!A.hasTag(DEV_TAG))
					{
						A.addTag(DEV_TAG);
						CMLib.database().DBUpdateArea(A.Name(), A);
					}
				}
			}
		}
		finally
		{
			importing = false;
		}
		devLoadedWhole = whole;
		if(loaded > 0)
			Log.sysOut("WorldFiles", "Dev areas loaded from " + DEV_DIR + "/: " + loaded);
		if(SaveGame.isBuilderMode() && (dbDir != null) && whole)
			writeText(new File(dbDir, DEV_SUM_FILE), sum + "\n");
	}

	private static String trimmed(final String s)
	{
		return (s == null) ? "" : s.trim();
	}

	/** the name an area file gives its area, or null */
	private static String areaNameIn(final File F)
	{
		final String text = readText(F);
		if(text == null)
			return null;
		final int x = text.indexOf("<ANAME>");
		final int y = (x < 0) ? -1 : text.indexOf("</ANAME>", x);
		if((x < 0) || (y < 0))
			return null;
		return CMLib.xml().restoreAngleBrackets(text.substring(x + 7, y)).trim();
	}

	private static void writeText(final File F, final String text)
	{
		try(Writer out = new OutputStreamWriter(new FileOutputStream(F), "UTF-8"))
		{
			out.write(text);
		}
		catch(final IOException e)
		{
			Log.errOut("WorldFiles", e);
		}
	}

	/** Clan memberships in room/catalog XML resolve against these canonical objects. */
	private static void preloadClans()
	{
		for(final Clan C : CMLib.database().DBReadAllClans())
			if(CMLib.clans().getClan(C.clanID())==null) CMLib.clans().addClan(C);
	}

	private static void loadWorldFiles()
	{
		com.planet_ink.coffee_mud.Behaviors.MovingRoom.clearAll();
		loadedWhole = false;
		if(!rebuild)
		{
			if((!SaveGame.isBuilderMode())&&(!SaveGame.isRestoring())) importExtras(false);
			preloadClans();
			CMLib.database().DBReadAllRooms(null);
			com.planet_ink.coffee_mud.Behaviors.MovingRoom.restoreAll();
			loadedWhole = true;
			// what went wrong when the cache was built from the files
			loadProblems();
			// a new game starts with the world's templates, clans, journals and
			// files; a restored one has them in its save, as it left them
			return;
		}
		Log.sysOut("WorldFiles", "Building the world from the files in " + worldDir.getPath());
		System.out.println("The world files have changed: rebuilding the world from them...");
		boolean whole = true;
		importing = true;
		try
		{
			synchronized(problems)
			{
				problems.clear();
			}
			if(!SaveGame.isRestoring()) importExtras(SaveGame.isBuilderMode());
			preloadClans();
			for(final File F : listFiles(new File(worldDir, AREAS_DIR), AREA_EXT))
				whole = report(F, imported(AREAS_DIR + "/" + F.getName(), () -> importArea(F))) && whole;
			final File catalog = new File(worldDir, CATALOG_FILE);
			if(catalog.isFile())
				whole = report(catalog, imported(CATALOG_FILE, () -> importCatalog(catalog))) && whole;
			com.planet_ink.coffee_mud.core.Uniques.registerWorld(); // item 1: the copies placed in the areas, read before the catalog
			whole = report(new File(worldDir, QUESTS_DIR), imported(QUESTS_DIR, () -> importQuests())) && whole;

		}
		finally
		{
			importing = false;
		}
		com.planet_ink.coffee_mud.Behaviors.MovingRoom.restoreAll();
		loadedWhole = whole;
		saveProblems();
		final String summary = problemSummary(10);
		if(summary.length() > 0)
			System.out.println(summary);
		if(!whole)
		{
			System.out.println("Fix the world files named above.  Until then, builder mode will not write the world files.");
			return;
		}
		final File dbDir = SaveGame.databaseDir();
		if(dbDir != null)
		{
			writeSum(dbDir, bootSum);
			final File cache = refreshCache;
			if((cache != null) && cache.isDirectory())
			{
				// a normal game built this from the files: the next one can copy it
				try
				{
					for(final String table : CONTENT_TABLES)
					{
						final File F = new File(dbDir, TABLE_PREFIX + table);
						if(F.isFile())
							copy(F, new File(cache, F.getName()));
					}
					writeSum(cache, bootSum);
				}
				catch(final IOException e)
				{
					Log.errOut("WorldFiles", e);
				}
			}
		}
	}

	/**
	 * Imports a world file, keeping what the engine logs as going wrong on
	 * the way (an object of an unknown class skipped, say) as the file's
	 * problems.
	 * @param key the file's path in the world folder
	 * @param importer the import
	 * @return as the import returns: null if it imported
	 */
	private static String imported(final String key, final java.util.function.Supplier<String> importer)
	{
		Log.startCapture();
		try
		{
			return importer.get();
		}
		finally
		{
			final List<String> got = Log.stopCapture();
			if(!got.isEmpty())
			{
				synchronized(problems)
				{
					problems.put(key, got);
				}
			}
		}
	}

	/**
	 * Returns what went wrong in loading the world files that loaded only in
	 * part, by each file's path in the world folder.
	 * @return the problems
	 */
	public static Map<String, List<String>> problems()
	{
		synchronized(problems)
		{
			final Map<String, List<String>> copy = new TreeMap<String, List<String>>();
			for(final String key : problems.keySet())
				copy.put(key, new ArrayList<String>(problems.get(key)));
			return copy;
		}
	}

	/**
	 * Returns a summary of the world files that loaded only in part, for the
	 * builder, or "" if none did.
	 * @param maxLines how many problems to name at most
	 * @return the summary
	 */
	public static String problemSummary(final int maxLines)
	{
		final Map<String, List<String>> all = problems();
		if(all.isEmpty())
			return "";
		int n = 0;
		for(final List<String> L : all.values())
			n += L.size();
		final StringBuilder str = new StringBuilder("World file problems: " + n + " in " + all.size()
			+ ((all.size() == 1) ? " file, which builder mode will not write until it is fixed" : " files, which builder mode will not write until they are fixed")
			+ " (or SAVE FORCE).  WORLD PROBLEMS lists them.");
		int shown = 0;
		for(final String key : all.keySet())
		{
			for(final String line : all.get(key))
			{
				if(shown++ >= maxLines)
					break;
				str.append("\n  ").append(key).append(": ").append(line);
			}
		}
		if(n > maxLines)
			str.append("\n  ...");
		return str.toString();
	}

	/** keeps the problems in the database's folder */
	private static void saveProblems()
	{
		final File dbDir = SaveGame.databaseDir();
		if(dbDir == null)
			return;
		final File F = new File(dbDir, PROBLEMS_FILE);
		final StringBuilder str = new StringBuilder();
		for(final Map.Entry<String, List<String>> e : problems().entrySet())
		{
			for(final String line : e.getValue())
				str.append(e.getKey()).append('\t').append(line.replace('\n', ' ').replace('\r', ' ')).append('\n');
		}
		try
		{
			if(str.length() == 0)
			{
				if(F.exists())
					F.delete();
			}
			else
				Files.write(F.toPath(), str.toString().getBytes(StandardCharsets.UTF_8));
		}
		catch(final IOException e)
		{
			Log.errOut("WorldFiles", e);
		}
	}

	/** reads the problems kept in the database's folder */
	private static void loadProblems()
	{
		synchronized(problems)
		{
			problems.clear();
			final File dbDir = SaveGame.databaseDir();
			if(dbDir == null)
				return;
			final File F = new File(dbDir, PROBLEMS_FILE);
			if(!F.isFile())
				return;
			try
			{
				for(final String line : Files.readAllLines(F.toPath(), StandardCharsets.UTF_8))
				{
					final int x = line.indexOf('\t');
					if(x > 0)
					{
						if(!problems.containsKey(line.substring(0, x)))
							problems.put(line.substring(0, x), new ArrayList<String>());
						problems.get(line.substring(0, x)).add(line.substring(x + 1));
					}
				}
			}
			catch(final IOException e)
			{
				Log.errOut("WorldFiles", e);
			}
		}
	}

	/**
	 * Returns the files the last export did not write, as they loaded only in
	 * part, by their paths in the world folder.
	 * @return the files
	 */
	public static List<String> heldBack()
	{
		synchronized(heldBack)
		{
			return new ArrayList<String>(heldBack);
		}
	}

	/**
	 * What an export would do (B2), as WORLD STATUS and WORLD DIFF show it.
	 */
	public static final class ExportPlan
	{
		/** the files it would write, by their paths in the world folder */
		public final List<String>            writes        = new ArrayList<String>();
		/** the files it would delete */
		public final List<String>            deletes        = new ArrayList<String>();
		/** the files it would leave as they are, as they were changed outside the game */
		public final List<String>            kept        = new ArrayList<String>();
		/** the files it would leave as they are, as they loaded only in part */
		public final List<String>            heldBack    = new ArrayList<String>();
		/** the text it would write to each file it writes, or would write but keeps */
		public final Map<String, String>    texts        = new TreeMap<String, String>();
	}

	/**
	 * Returns the files the last export did not write or delete, as they were
	 * changed outside the game (B2), by their paths in the world folder.
	 * @return the files
	 */
	public static List<String> keptOutside()
	{
		synchronized(keptOutside)
		{
			return new ArrayList<String>(keptOutside);
		}
	}

	/**
	 * Each world file the export writes, with a checksum of its bytes (B2).
	 * @return the checksums, by path in the world folder
	 */
	public static Map<String, String> fileSums()
	{
		final Map<String, String> sums = new TreeMap<String, String>();
		for(final String path : trackedPaths())
		{
			final String sum = sumOf(new File(worldDir, path));
			if(sum != null)
				sums.put(path, sum);
		}
		return sums;
	}

	/**
	 * The world files changed outside the game since it loaded or last wrote
	 * them (B2).
	 * @return each one's path in the world folder, with "edited", "added" or
	 * "deleted"
	 */
	public static Map<String, String> changedOutside()
	{
		final Map<String, String> known = knownSums;
		final Map<String, String> now = fileSums();
		final Map<String, String> out = new TreeMap<String, String>();
		for(final Map.Entry<String, String> e : now.entrySet())
		{
			final String was = known.get(e.getKey());
			if(was == null)
				out.put(e.getKey(), "added");
			else
			if(!was.equals(e.getValue()))
				out.put(e.getKey(), "edited");
		}
		for(final String path : known.keySet())
		{
			if(!now.containsKey(path))
				out.put(path, "deleted");
		}
		return out;
	}

	/**
	 * Whether a world file was changed outside the game (B2): edited, added or
	 * deleted since the game loaded or last wrote it.
	 * @param path its path in the world folder
	 * @return true if it was
	 */
	public static boolean changedOutside(final String path)
	{
		final String known = knownSums.get(path);
		final String now = sumOf(new File(worldDir, path));
		return (known == null) ? (now != null) : (!known.equals(now));
	}

	/**
	 * Finds a world file by what a builder calls it (WORLD DIFF): its path in
	 * the world folder, with or without the extension, its name, or an
	 * area's name.
	 * @param what what the builder called it
	 * @param plan what the export would do, for files it would make, or null
	 * @return its path in the world folder, or null if there is none
	 */
	public static String findPath(final String what, final ExportPlan plan)
	{
		final Set<String> all = new TreeSet<String>(trackedPaths());
		if(plan != null)
		{
			all.addAll(plan.writes);
			all.addAll(plan.deletes);
			all.addAll(plan.kept);
		}
		all.addAll(knownSums.keySet());
		String w = what.trim().replace('\\', '/');
		if(w.toLowerCase().startsWith("world/"))
			w = w.substring(6);
		for(final String path : all)
		{
			if(path.equalsIgnoreCase(w))
				return path;
		}
		final String asName = fileNameFor(w, "", new HashSet<String>());
		for(final String path : all)
		{
			final String name = path.substring(path.lastIndexOf('/') + 1);
			final int dot = name.indexOf('.');
			final String base = (dot > 0) ? name.substring(0, dot) : name;
			final int pdot = path.lastIndexOf('.');
			if(name.equalsIgnoreCase(w) || base.equalsIgnoreCase(w) || base.equals(asName)
			|| ((pdot > 0) && path.substring(0, pdot).equalsIgnoreCase(w)))
				return path;
		}
		return null;
	}

	/**
	 * The world file an area is written to (B5): areas/NAME.cmare, or in
	 * dev/ for a dev area.
	 * @param A the area
	 * @return its path in the world folder
	 */
	public static String areaPathFor(final Area A)
	{
		return (isDevArea(A) ? DEV_DIR : AREAS_DIR) + "/" + areaFileFor(A);
	}

	/**
	 * The name of the area a world file holds (B5).
	 * @param path its path in the world folder
	 * @return the name, or null if it holds none
	 */
	public static String areaNameOf(final String path)
	{
		return areaNameIn(new File(worldDir, path));
	}

	/**
	 * The world file a quest is written to (B5).
	 * @param name the quest's name
	 * @return its path in the world folder
	 */
	public static String questPathFor(final String name)
	{
		return QUESTS_DIR + "/" + fileNameFor(name, QUEST_EXT, new HashSet<String>());
	}

	/**
	 * A quest's script as its world file has it (B5), as the boot reads it.
	 * @param path the file's path in the world folder
	 * @return the script, or null if there is no such file
	 */
	public static String questScriptIn(final String path)
	{
		final String text = readText(new File(worldDir, path));
		return (text == null) ? null : stripFinalNewline(text);
	}

	/**
	 * Marks a world file as the game has it (B2, B5): loaded again, so no
	 * longer changed outside the game.
	 * @param path its path in the world folder
	 */
	public static void markLoaded(final String path)
	{
		final Map<String, String> next = new TreeMap<String, String>(knownSums);
		final String sum = sumOf(new File(worldDir, path));
		if(sum == null)
			next.remove(path);
		else
			next.put(path, sum);
		knownSums = next;
	}

	/**
	 * Loads an area from its file into the running world (B5, WORLD
	 * RELOAD), as the boot does; the caller takes the area as the game has
	 * it out first.  A dev area is tagged so again, and the file is known as
	 * loaded.
	 * @param path the file's path in the world folder
	 * @return null if it loaded whole, or what went wrong
	 */
	public static String loadAreaFile(final String path)
	{
		final File F = new File(worldDir, path);
		if(!F.isFile())
			return path + " is not there";
		final String name = areaNameIn(F);
		synchronized(problems)
		{
			problems.remove(path);
		}
		String problem;
		importing = true;
		try
		{
			problem = imported(path, () -> importArea(F));
		}
		finally
		{
			importing = false;
		}
		final Area A = (name == null) ? null : CMLib.map().getArea(name);
		if((A != null) && path.startsWith(DEV_DIR + "/") && (!A.hasTag(DEV_TAG)))
		{
			A.addTag(DEV_TAG);
			CMLib.database().DBUpdateArea(A.Name(), A);
		}
		markLoaded(path);
		boolean part;
		synchronized(problems)
		{
			part = problems.containsKey(path);
		}
		saveProblems();
		if((problem == null) && part)
			problem = "it loaded only in part (WORLD PROBLEMS)";
		return problem;
	}

	/**
	 * Loads the catalog file again into the running catalog (B5, WORLD
	 * RELOAD CATALOG): each entry it has is put in place of the one of that
	 * name, or added; entries it does not have are kept.
	 * @return null if loaded, or what went wrong
	 */
	public static String reloadCatalog()
	{
		final File F = new File(worldDir, CATALOG_FILE);
		if(!F.isFile())
			return CATALOG_FILE + " is not there";
		final String problem = importCatalog(F, true);
		markLoaded(CATALOG_FILE);
		return problem;
	}

	/** the folder in the world folder the export's backups are kept in (B6) */
	public static final String    BACKUP_DIR    = ".backup";

	/** a backup's name: the file's path, its folders joined with ~, and the time */
	private static String backupBase(final String path)
	{
		return path.replace('/', '~') + ".";
	}

	/**
	 * B6: a file as it was, copied to world/.backup/ before the export
	 * writes over it or deletes it; the last BACKUPS= (in the ini, 5 if not
	 * set, 0 for none) of each file are kept.
	 * @param F the file
	 * @return true, to go on
	 */
	private static boolean backup(final File F)
	{
		final String path = pathIn(F);
		if((path == null) || (!F.isFile()))
			return true;
		final int keep = CMath.s_int(CMProps.instance().getStr("BACKUPS", "5").trim());
		if(keep <= 0)
			return true;
		final File D = new File(worldDir, BACKUP_DIR);
		try
		{
			if(!D.isDirectory())
			{
				D.mkdirs();
				// not part of the world: git leaves it out
				Files.write(new File(D, ".gitignore").toPath(), "*\n".getBytes(StandardCharsets.UTF_8));
			}
			final String stamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss-SSS").format(new Date());
			Files.copy(F.toPath(), new File(D, backupBase(path) + stamp).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			final List<String> stamps = backupsOf(path);
			for(int i = 0; i < stamps.size() - keep; i++)
				new File(D, backupBase(path) + stamps.get(i)).delete();
		}
		catch(final IOException e)
		{
			Log.errOut("WorldFiles", "backup of " + path + ": " + e.getMessage());
		}
		return true;
	}

	/**
	 * The times of a file's backups (B6, WORLD BACKUPS), oldest first.
	 * @param path the file's path in the world folder
	 * @return the times, as the backups are named
	 */
	public static List<String> backupsOf(final String path)
	{
		final List<String> stamps = new ArrayList<String>();
		final File[] fs = new File(worldDir, BACKUP_DIR).listFiles();
		if(fs == null)
			return stamps;
		final String base = backupBase(path);
		for(final File F : fs)
		{
			if(F.getName().startsWith(base) && (F.getName().indexOf('.', base.length()) < 0))
				stamps.add(F.getName().substring(base.length()));
		}
		Collections.sort(stamps);
		return stamps;
	}

	/**
	 * The files that have backups (B6, WORLD BACKUPS).
	 * @return their paths in the world folder
	 */
	public static List<String> backedUp()
	{
		final Set<String> paths = new TreeSet<String>();
		final File[] fs = new File(worldDir, BACKUP_DIR).listFiles();
		if(fs != null)
		{
			for(final File F : fs)
			{
				final String n = F.getName();
				final int x = n.lastIndexOf('.');
				if((x > 0) && (!n.startsWith(".")) && n.substring(x + 1).matches("\\d{8}-\\d{6}-\\d{3}"))
					paths.add(n.substring(0, x).replace('~', '/'));
			}
		}
		return new ArrayList<String>(paths);
	}

	/**
	 * Puts a file back as a backup has it (B6, WORLD RESTOREFILE).  It is
	 * then changed outside the game, as B2 sees it: WORLD RELOAD loads it,
	 * and SAVE leaves it unless forced.
	 * @param path the file's path in the world folder
	 * @param stamp the backup's time, or null for the newest
	 * @return null if put back, or what went wrong
	 */
	public static String restoreBackup(final String path, final String stamp)
	{
		final List<String> stamps = backupsOf(path);
		if(stamps.isEmpty())
			return path + " has no backups";
		String which = null;
		if(stamp == null)
			which = stamps.get(stamps.size() - 1);
		else
		{
			for(final String s : stamps)
			{
				if(s.startsWith(stamp) && ((which == null) || (!which.equals(stamp))))
					which = s;
			}
		}
		if(which == null)
			return path + " has no backup of " + stamp;
		final File to = new File(worldDir, path);
		try
		{
			if((to.getParentFile() != null) && (!to.getParentFile().isDirectory()))
				to.getParentFile().mkdirs();
			Files.copy(new File(new File(worldDir, BACKUP_DIR), backupBase(path) + which).toPath(), to.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
		}
		catch(final IOException e)
		{
			return e.getMessage();
		}
		return null;
	}

	/** a world file's path in the world folder, or null if it is not in it */
	private static String pathIn(final File F)
	{
		final String root = worldDir.getPath() + File.separator;
		final String p = F.getPath();
		return p.startsWith(root) ? p.substring(root.length()).replace(File.separatorChar, '/') : null;
	}

	/** a checksum of a file's bytes, or null if there is no such file */
	private static String sumOf(final File F)
	{
		if(!F.isFile())
			return null;
		final CRC32 crc = new CRC32();
		try
		{
			crc.update(Files.readAllBytes(F.toPath()));
		}
		catch(final IOException e)
		{
			return "?";
		}
		return F.length() + "-" + Long.toHexString(crc.getValue());
	}

	/** the export left a file as the game has it: it is known so, and the game's copy beside it is gone */
	private static void settle(final File F)
	{
		final Set<String> into = settled;
		if(into == null)
			return;
		final String path = pathIn(F);
		if(path != null)
			into.add(path);
		final File copy = new File(F.getPath() + BUILDER_EXT);
		if(copy.isFile())
			copy.delete();
	}

	/**
	 * Whether the export may write or delete a file (B2): not one changed
	 * outside the game since the game loaded or last wrote it, unless forced
	 * (SAVE FORCE).  Such a file is left as it is, and what the export would
	 * write goes beside it, as NAME.builder.  In a dry run (WORLD STATUS),
	 * notes what would be done and does nothing.
	 * @param F the file
	 * @param data what the export would write, or null to delete it
	 * @return true to go on and write or delete it
	 * @throws IOException if the copy cannot be written
	 */
	private static boolean mayChange(final File F, final byte[] data) throws IOException
	{
		final String path = pathIn(F);
		final boolean keep = (path != null) && (!forcing) && changedOutside(path);
		final ExportPlan plan = dryRun;
		if(plan != null)
		{
			if(path != null)
			{
				if(keep)
					plan.kept.add(path);
				else
				if(data == null)
					plan.deletes.add(path);
				else
					plan.writes.add(path);
				if(data != null)
					plan.texts.put(path, new String(data, StandardCharsets.UTF_8));
			}
			return false;
		}
		if(!keep)
		{
			settle(F);
			return true;
		}
		synchronized(keptOutside)
		{
			keptOutside.add(path);
		}
		final File copy = new File(F.getPath() + BUILDER_EXT);
		if(data != null)
		{
			if((copy.getParentFile() != null) && (!copy.getParentFile().isDirectory()))
				copy.getParentFile().mkdirs();
			Files.write(copy.toPath(), data);
		}
		else
		if(copy.isFile())
			copy.delete();
		return false;
	}

	private static boolean report(final File F, final String problem)
	{
		if(problem == null)
			return true;
		Log.errOut("WorldFiles", F.getName() + ": " + problem);
		System.out.println("World file " + F.getName() + " could not be read: " + problem);
		return false;
	}

	/**
	 * Imports one area file into the map and the database.
	 * @param F the file
	 * @return null if imported, or what went wrong
	 */
	private static String importArea(final File F)
	{
		final String text = readWorldXML(F);
		if(text == null)
			return "it cannot be read";
		final StringBuffer buf = new StringBuffer(text);
		CMStrings.dikufyLineEndings(buf);
		final List<XMLTag> areaData = new ArrayList<XMLTag>();
		// the custom objects listed in an area file are in custom.cmare, which
		// went into the database before the engine loaded its classes
		String problem = CMLib.coffeeMaker().fillAreaAndCustomVectorFromXML(buf.toString(), areaData, new ArrayList<CMObject>(), new HashMap<String, String>());
		if(problem.length() == 0)
			problem = CMLib.coffeeMaker().unpackAreaFromXML(areaData, null, null, true, true);
		return (problem.length() == 0) ? null : problem;
	}

	/**
	 * Imports the catalog file: each mob and item, with its category and its
	 * catalog data.
	 * @param F the file
	 * @return null if imported, or what went wrong
	 */
	private static String importCatalog(final File F)
	{
		return importCatalog(F, false);
	}

	/**
	 * Imports the catalog file, as {@link #importCatalog(File)}; with
	 * replace (B5, WORLD RELOAD CATALOG), an entry of a name the catalog has
	 * is put in place of that one, and others are added.
	 * @param F the file
	 * @param replace true to put entries in place of those of their names
	 * @return null if imported, or what went wrong
	 */
	private static String importCatalog(final File F, final boolean replace)
	{
		final String text = readWorldXML(F);
		if(text == null)
			return "it cannot be read";
		final XMLLibrary xml = CMLib.xml();
		final List<XMLTag> all = xml.parseAllXML(text);
		if((all.size() == 0) || (!all.get(0).tag().equalsIgnoreCase("CATALOG")))
			return "it does not start with CATALOG";
		for(final XMLTag entry : all.get(0).contents())
		{
			final Physical P;
			if(entry.tag().equalsIgnoreCase("CATMOB"))
			{
				final XMLTag tag = entry.getPieceFromPieces("MOB");
				final MOB M = (tag == null) ? null : CMClass.getMOB(tag.getValFromPieces("MCLAS"));
				if(M == null)
					return "a mob of no known class";
				M.setMiscText(xml.restoreAngleBrackets(tag.getValFromPieces("MTEXT")));
				M.basePhyStats().setLevel(tag.getIntFromPieces("MLEVL"));
				M.basePhyStats().setAbility(tag.getIntFromPieces("MABLE"));
				M.basePhyStats().setRejuv(tag.getIntFromPieces("MREJV"));
				M.recoverCharStats();
				M.recoverPhyStats();
				M.recoverMaxState();
				P = M;
			}
			else
			if(entry.tag().equalsIgnoreCase("CATITEM"))
			{
				final XMLTag tag = entry.getPieceFromPieces("ITEM");
				final Item I = (tag == null) ? null : CMClass.getItem(tag.getValFromPieces("ICLAS"));
				if(I == null)
					return "an item of no known class";
				I.basePhyStats().setLevel(tag.getIntFromPieces("ILEVL"));
				I.basePhyStats().setAbility(tag.getIntFromPieces("IABLE"));
				I.basePhyStats().setRejuv(tag.getIntFromPieces("IREJV"));
				I.setUsesRemaining(tag.getIntFromPieces("IUSES"));
				I.setMiscText(xml.restoreAngleBrackets(tag.getValFromPieces("ITEXT")));
				I.recoverPhyStats();
				P = I;
			}
			else
				continue;
			final String category = xml.restoreAngleBrackets(entry.getValFromPieces("CATEGORY"));
			final Physical had = (!replace) ? null
				: (P instanceof MOB) ? CMLib.catalog().getCatalogMob(P.Name()) : CMLib.catalog().getCatalogItem(P.Name());
			if(had != null)
			{
				// the catalog's own entry takes the file's, and is saved so
				had.setMiscText(P.text());
				had.basePhyStats().setLevel(P.basePhyStats().level());
				had.basePhyStats().setAbility(P.basePhyStats().ability());
				had.basePhyStats().setRejuv(P.basePhyStats().rejuv());
				if((had instanceof Item) && (P instanceof Item))
					((Item)had).setUsesRemaining(((Item)P).usesRemaining());
				had.recoverPhyStats();
				CMLib.catalog().updateCatalog(had);
			}
			else
				CMLib.catalog().addCatalog((category.length() > 0) ? category : null, P);
			final CataData data = CMLib.catalog().getCatalogData(P);
			final String dataXML = xml.restoreAngleBrackets(entry.getValFromPieces("CATADATA"));
			if((data != null) && (dataXML.length() > 0))
			{
				data.build(dataXML);
				CMLib.catalog().updateCatalogCategory(P, data.category());
			}
			P.destroy();
		}
		return null;
	}

	/**
	 * Puts the quests from their files into the quest list in the database,
	 * which the boot loads them from after the map.
	 * @return null if done, or what went wrong
	 */
	private static String importQuests()
	{
		final List<Quest> quests = new ArrayList<Quest>();
		for(final File F : listFiles(new File(worldDir, QUESTS_DIR), QUEST_EXT))
		{
			final String script = readText(F);
			final Quest Q = (Quest)CMClass.getCommon("DefaultQuest");
			if((script == null) || (Q == null))
				return F.getName() + " cannot be read";
			Q.setScript(stripFinalNewline(script), true);
			if(Q.name().trim().length() == 0)
				return F.getName() + " names no quest";
			quests.add(Q);
		}
		if(quests.size() > 0)
			CMLib.database().DBUpdateQuests(quests);
		return null;
	}

	/**
	 * Writes the content to the files, in builder mode: each area, the custom
	 * objects, the catalog and the quests, rewriting only files that changed
	 * and deleting those of things that are gone, then records in the
	 * database the checksum of the files it now matches.  Does nothing unless
	 * the content loaded whole, so that a file that failed to load is never
	 * written over.
	 * @return how many files were written or deleted, or -1 if nothing was
	 * written because it could not be
	 */
	public static synchronized int exportAll()
	{
		return exportAll(false);
	}

	/**
	 * Writes the content to the files, in builder mode, as {@link #exportAll()};
	 * a file that loaded only in part (see {@link #problems()}) is written only
	 * when forced, as the builder's SAVE FORCE does, and is then taken as
	 * loaded whole.
	 * @param force true to write the files that loaded only in part too
	 * @return as for {@link #exportAll()}
	 */
	public static synchronized int exportAll(final boolean force)
	{
		synchronized(heldBack)
		{
			heldBack.clear();
		}
		if((!SaveGame.isBuilderMode()) || (!loadedWhole))
			return -1;
		// things' own texts as XML, one element to a line, to read and diff
		CMLib.coffeeMaker().setReadableText(true);
		try
		{
			final int changed = exportAllReadable(force);
			if(force && (changed >= 0))
			{
				// written as the world has them: they hold what loaded, no more
				synchronized(problems)
				{
					problems.clear();
				}
				saveProblems();
			}
			return changed;
		}
		finally
		{
			forcing = false;
			settled = null;
			CMLib.coffeeMaker().setReadableText(false);
		}
	}

	/**
	 * What the next SAVE would do to the world files (B2, WORLD STATUS and
	 * WORLD DIFF): the export, run with nothing written, deleted or cached.
	 * @return the plan, or null if SAVE writes nothing (not builder mode, or
	 * the world did not load whole)
	 */
	public static synchronized ExportPlan plan()
	{
		if((!SaveGame.isBuilderMode()) || (!loadedWhole))
			return null;
		final List<String> held;
		synchronized(heldBack)
		{
			held = new ArrayList<String>(heldBack);
			heldBack.clear();
		}
		final ExportPlan plan = new ExportPlan();
		dryRun = plan;
		CMLib.coffeeMaker().setReadableText(true);
		try
		{
			if(exportAllReadable(false) < 0)
				return null;
			synchronized(heldBack)
			{
				plan.heldBack.addAll(heldBack);
			}
			return plan;
		}
		finally
		{
			dryRun = null;
			forcing = false;
			CMLib.coffeeMaker().setReadableText(false);
			synchronized(heldBack)
			{
				heldBack.clear();
				heldBack.addAll(held);
			}
		}
	}

	/** whether a file that loaded only in part is kept as it is, not written */
	private static boolean holdBack(final String key, final boolean force)
	{
		if(force)
			return false;
		synchronized(problems)
		{
			if(!problems.containsKey(key))
				return false;
		}
		synchronized(heldBack)
		{
			heldBack.add(key);
		}
		return true;
	}

	private static int exportAllReadable(final boolean force)
	{
		final List<Area> areas = new ArrayList<Area>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			if((A != null)
			&& (!A.amDestroyed())
			&& (!CMath.bset(A.flags(), Area.FLAG_INSTANCE_CHILD))
			&& CMLib.flags().isSavable(A))
				areas.add(A);
		}
		if(areas.size() == 0)
			return -1;
		Collections.sort(areas, new Comparator<Area>()
		{
			@Override
			public int compare(final Area o1, final Area o2)
			{
				return o1.Name().compareToIgnoreCase(o2.Name());
			}
		});
		final int[] changed = { 0 };
		// B2: a dry run notes what it would do; a real one keeps what was changed outside the game
		final boolean dry = (dryRun != null);
		forcing = force;
		settled = dry ? null : new HashSet<String>();
		if(!dry)
		{
			synchronized(keptOutside)
			{
				keptOutside.clear();
			}
		}
		try
		{
			final Map<String, String> areaFiles = new LinkedHashMap<String, String>();
			// the dev areas (DT27), tagged DEV, go to dev/ instead
			final Map<String, String> devFiles = new LinkedHashMap<String, String>();
			final List<Area> changedAreas = new ArrayList<Area>();
			for(final Area A : areas)
			{
				final boolean dev = isDevArea(A);
				final Map<String, String> into = dev ? devFiles : areaFiles;
				final String dirName = dev ? DEV_DIR : AREAS_DIR;
				final String fileName = fileNameFor(A.Name(), AREA_EXT, into.keySet());
				final String was = readText(new File(new File(worldDir, dirName), fileName));
				if((was != null) && holdBack(dirName + "/" + fileName, force))
				{
					// loaded only in part: kept as it is, not written short
					into.put(fileName, was);
					continue;
				}
				final String text = layOut(CMLib.coffeeMaker().getAreaXML(A, null, new HashSet<CMObject>(), new HashSet<String>(), true));
				into.put(fileName, text);
				// compared as the engine reads them, so that a file written the
				// old way, escaped, is not taken for a changed area
				if((was == null) || (!collapseText(text).equals(collapseText(was))))
					changedAreas.add(A);
			}
			writeFolder(new File(worldDir, AREAS_DIR), AREA_EXT, areaFiles, changed);
			// dev/ is written only if its areas loaded whole, so that a file that
			// failed to load is not taken for an area deleted
			if(devLoadedWhole && ((devFiles.size() > 0) || new File(worldDir, DEV_DIR).isDirectory()))
				writeFolder(new File(worldDir, DEV_DIR), AREA_EXT, devFiles, changed);
			// the files are written from the live world, and the database is a
			// cache of the files: an area changed without the builder commands
			// (by JRUN, say) is put in the cache too, so the next game has it
			if(!dry)
			{
				CMLib.coffeeMaker().setReadableText(false); // the database holds texts as the engine reads them
				try
				{
					for(final Area A : changedAreas)
						cacheArea(A);
				}
				finally
				{
					CMLib.coffeeMaker().setReadableText(true);
				}
			}
			// the custom objects, one file each, in place of the old custom.cmare
			writeOrDelete(new File(worldDir, CUSTOM_FILE), null, changed);
			final Map<String, Map<String, String>> custom = customFiles();
			for(final String kind : CUSTOM_KINDS)
			{
				final File dir = new File(new File(worldDir, CUSTOM_DIR), kind);
				writeFolder(dir, AREA_EXT, custom.get(kind), changed);
				writeFolder(dir, SCRIPT_EXT, custom.get(kind), changed);
			}
			if(!holdBack(CATALOG_FILE, force))
				writeOrDelete(new File(worldDir, CATALOG_FILE), catalogText(), changed);
			if(!holdBack(QUESTS_DIR, force))
			{
				final Map<String, String> questFiles = new LinkedHashMap<String, String>();
				for(final String[] quest : questRows())
					questFiles.put(fileNameFor(quest[0], QUEST_EXT, questFiles.keySet()), quest[1] + "\n");
				writeFolder(new File(worldDir, QUESTS_DIR), QUEST_EXT, questFiles, changed);
			}
			writeOrDelete(new File(worldDir, TEMPLATES_FILE), templatesText(), changed);
			writeOrDelete(new File(worldDir, CLANS_FILE), clansText(), changed);
			writeOrDelete(new File(worldDir, SPACE_FILE), spaceText(), changed);
			writeFolder(new File(worldDir, JOURNALS_DIR), JOURNAL_EXT, journalFiles(), changed);
			writeVFS(changed);
		}
		catch(final IOException e)
		{
			Log.errOut("WorldFiles", e);
			return -1;
		}
		catch(final CMException e)
		{
			Log.errOut("WorldFiles", e);
			return -1;
		}
		if(dry)
			return changed[0];
		// B2: the files left as the game has them are known as they now are;
		// one changed outside the game stays known as it was, so stays kept
		final Map<String, String> next = new TreeMap<String, String>(knownSums);
		for(final String path : settled)
		{
			final String sum = sumOf(new File(worldDir, path));
			if(sum == null)
				next.remove(path);
			else
				next.put(path, sum);
		}
		knownSums = next;
		final List<String> kept = keptOutside();
		final File dbDir = SaveGame.databaseDir();
		if(dbDir != null)
		{
			if(kept.size() > 0)
			{
				// the cache holds the game's copy of those files, not what they
				// now hold: the next game builds from the files
				new File(dbDir, SUM_FILE).delete();
				new File(dbDir, DEV_SUM_FILE).delete();
			}
			else
			{
				writeSum(dbDir, checksum());
				if(devLoadedWhole)
					writeText(new File(dbDir, DEV_SUM_FILE), devChecksum() + "\n");
			}
		}
		if(kept.size() > 0)
			Log.sysOut("WorldFiles", "Not written, as changed outside the game: " + String.join(", ", kept) + " (the game's copies are beside them, as " + BUILDER_EXT + ").");
		if(changed[0] > 0)
			Log.sysOut("WorldFiles", "Wrote the world files: " + changed[0] + " changed.");
		return changed[0];
	}

	/** the builder templates, all shared */
	public static final String		TEMPLATES_FILE		= "templates.cmare";
	/** the clans with no player members */
	public static final String		CLANS_FILE			= "clans.cmare";
	/** the things in space that are not in a room, and that no character or clan owns */
	public static final String		SPACE_FILE			= "space.cmare";

	/**
	 * Returns the things in space that the world's files hold: each item in
	 * space (a ship, a star, an asteroid, a station) that no character or
	 * clan owns, whose text holds where it is, its speed and its course.
	 * @return the items
	 */
	public static List<Item> spaceItems()
	{
		final Set<Item> owned = Collections.newSetFromMap(new IdentityHashMap<Item, Boolean>());
		for(final Enumeration<MOB> p = CMLib.players().players(); p.hasMoreElements();)
		{
			final MOB M = p.nextElement();
			if((M != null) && (M.playerStats() != null))
			{
				for(final Enumeration<Item> i = M.playerStats().getExtItems().items(); i.hasMoreElements();)
					owned.add(i.nextElement());
			}
		}
		for(final Enumeration<Clan> c = CMLib.clans().clans(); c.hasMoreElements();)
		{
			final Clan C = c.nextElement();
			if(C != null)
			{
				for(final Enumeration<Item> i = C.getExtItems().items(); i.hasMoreElements();)
					owned.add(i.nextElement());
			}
		}
		final List<Item> items = new ArrayList<Item>();
		for(final Enumeration<SpaceObject> s = CMLib.space().getSpaceObjects(); s.hasMoreElements();)
		{
			final SpaceObject O = s.nextElement();
			if((O instanceof Item) && (!((Item)O).amDestroyed()) && (!owned.contains(O)))
				items.add((Item)O);
		}
		return items;
	}

	/**
	 * Returns the text of space.cmare: each of {@link #spaceItems()} as item
	 * XML, or null if there are none.
	 * @return the text, or null
	 */
	private static String spaceText()
	{
		final List<Item> items = spaceItems();
		if(items.size() == 0)
			return null;
		final StringBuilder str = new StringBuilder("<SPACE>\n");
		for(final Item I : items)
			str.append(CMLib.coffeeMaker().getItemXML(I));
		return str.append("</SPACE>\n").toString();
	}

	/**
	 * Puts the things of space.cmare in space, at each boot: they are in no
	 * table of the database, so the files are where they are kept.  A saved
	 * game then puts them where it had them.
	 */
	public static void loadSpace()
	{
		final String text = readWorldXML(new File(worldDir, SPACE_FILE));
		if(text == null)
			return;
		int n = 0;
		for(final XMLLibrary.XMLTag tag : CMLib.xml().parseAllXML(text))
		{
			if(!tag.tag().equalsIgnoreCase("SPACE"))
				continue;
			for(final XMLLibrary.XMLTag itemTag : tag.contents())
			{
				final Item I = CMLib.coffeeMaker().unpackItemFromXML(itemTag.toString());
				if(!(I instanceof SpaceObject))
				{
					if(I != null)
						I.destroy();
					continue;
				}
				CMLib.space().addObjectToSpace((SpaceObject)I, ((SpaceObject)I).coordinates());
				CMLib.map().registerWorldObjectLoaded(null, null, I);
				n++;
			}
		}
		Log.sysOut("WorldFiles", "Things in space from " + SPACE_FILE + ": " + n);
	}
	/** the journals of the world's journal items and SYSTEM_CRON, one file each */
	public static final String		JOURNALS_DIR		= "journals";
	private static final String		JOURNAL_EXT			= ".xml";
	/** the files in the database's file system, one file each, with an index */
	public static final String		VFS_DIR				= "vfs";
	private static final String		VFS_INDEX			= ".index";
	private static final String		TEMPLATE_PERSONAL	= "SYSTEM_BTEMPLATE_PERSONAL";
	private static final String		TEMPLATE_SHARED		= "SYSTEM_BTEMPLATE_SHARED";

	private static String esc(final String s)
	{
		return CMLib.xml().parseOutAngleBrackets((s == null) ? "" : s);
	}

	private static String unesc(final String s)
	{
		return CMLib.xml().restoreAngleBrackets((s == null) ? "" : s);
	}

	/**
	 * Writes the builder templates: every builder's, personal and shared.
	 * @return the text, or null if there are none
	 */
	private static String templatesText()
	{
		final List<DatabaseEngine.PlayerData> rows = new ArrayList<DatabaseEngine.PlayerData>();
		rows.addAll(CMLib.database().DBReadPlayerSectionData(TEMPLATE_PERSONAL));
		rows.addAll(CMLib.database().DBReadPlayerSectionData(TEMPLATE_SHARED));
		if(rows.size() == 0)
			return null;
		final TreeMap<String, String> sorted = new TreeMap<String, String>();
		for(final DatabaseEngine.PlayerData PD : rows)
			sorted.put(PD.key().toUpperCase(), "<TEMPLATE WHO=\"" + esc(PD.who()) + "\" KEY=\"" + esc(PD.key()) + "\">" + esc(PD.xml()) + "</TEMPLATE>\n");
		final StringBuilder str = new StringBuilder("<TEMPLATES>\n");
		for(final String t : sorted.values())
			str.append(t);
		return str.append("</TEMPLATES>\n").toString();
	}

	/**
	 * Writes the clans that have no player members: the world's own, such as
	 * an orc horde that conquers.
	 * @return the text, or null if there are none
	 */
	private static String clansText()
	{
		final TreeMap<String, String> sorted = new TreeMap<String, String>();
		for(final Enumeration<Clan> c = CMLib.clans().clans(); c.hasMoreElements();)
		{
			final Clan C = c.nextElement();
			if((C == null) || ((!Organizations.npcRun(C))&&(!CMLib.database().DBReadClanMembers(C.clanID()).isEmpty())))
				continue;
			final XMLLibrary xml = CMLib.xml();
			final StringBuilder str = new StringBuilder("<CLAN>");
			str.append(xml.convertXMLtoTag("ID", esc(C.clanID())));
			str.append(xml.convertXMLtoTag("PREMISE", esc(C.getPremise())));
			str.append(xml.convertXMLtoTag("ACCEPT", esc(C.getAcceptanceSettings())));
			str.append(xml.convertXMLtoTag("STATUS", C.getStatus()));
			str.append(xml.convertXMLtoTag("DATA", esc(C.getDataXML())));
			str.append(xml.convertXMLtoTag("RECALL", esc(C.getRecall())));
			str.append(xml.convertXMLtoTag("DONATION", esc(C.getDonation())));
			str.append(xml.convertXMLtoTag("MORGUE", esc(C.getMorgue())));
			str.append(xml.convertXMLtoTag("TROPHIES", C.getTrophies()));
			sorted.put(C.clanID().toUpperCase(), str.append("</CLAN>\n").toString());
		}
		if(sorted.size() == 0)
			return null;
		final StringBuilder str = new StringBuilder("<CLANS>\n");
		for(final String t : sorted.values())
			str.append(t);
		return str.append("</CLANS>\n").toString();
	}

	/**
	 * Returns the journals the world has: those its journal items read
	 * (notice boards, books), and the cron jobs' (SYSTEM_CRON).
	 * @return the journals' names
	 */
	private static Set<String> worldJournals()
	{
		final Set<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
		names.add("SYSTEM_CRON");
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			for(int i = 0; i < R.numItems(); i++)
			{
				final Item I = R.getItem(i);
				if((I != null) && CMClass.classID(I).toUpperCase().contains("JOURNAL"))
					names.add(I.Name());
			}
		}
		return names;
	}

	/**
	 * Writes each of the world's journals that has entries, one file each.
	 * @return the files, by name
	 */
	private static Map<String, String> journalFiles()
	{
		final Map<String, String> files = new LinkedHashMap<String, String>();
		final XMLLibrary xml = CMLib.xml();
		for(final String name : worldJournals())
		{
			final List<JournalEntry> entries = CMLib.database().DBReadJournalMsgsByUpdateDate(name, true);
			if((entries == null) || (entries.size() == 0))
				continue;
			final StringBuilder str = new StringBuilder("<JOURNAL NAME=\"" + esc(name) + "\">\n");
			for(final JournalEntry E : entries)
			{
				str.append("<ENTRY>");
				str.append(xml.convertXMLtoTag("KEY", esc(E.key())));
				str.append(xml.convertXMLtoTag("FROM", esc(E.from())));
				str.append(xml.convertXMLtoTag("TO", esc(E.to())));
				str.append(xml.convertXMLtoTag("DATE", esc(E.dateStr())));
				str.append(xml.convertXMLtoTag("UPDATE", E.update()));
				str.append(xml.convertXMLtoTag("EXPIRES", E.expiration()));
				str.append(xml.convertXMLtoTag("PARENT", esc(E.parent())));
				str.append(xml.convertXMLtoTag("ATTRS", E.attributes()));
				str.append(xml.convertXMLtoTag("DATA", esc(E.data())));
				str.append(xml.convertXMLtoTag("ICON", esc(E.msgIcon())));
				str.append(xml.convertXMLtoTag("SUBJ", esc(E.subj())));
				str.append(xml.convertXMLtoTag("MSG", esc(E.msg())));
				str.append("</ENTRY>\n");
			}
			files.put(fileNameFor(name, JOURNAL_EXT, files.keySet()), str.append("</JOURNAL>\n").toString());
		}
		return files;
	}

	/**
	 * Writes the database's file system (files written with SHELL ::), one
	 * file each under world/vfs/, with an index of who wrote each and its
	 * flags.
	 * @param changed counts the files written or deleted
	 * @throws IOException if they cannot be written
	 */
	private static void writeVFS(final int[] changed) throws IOException
	{
		final File dir = new File(worldDir, VFS_DIR);
		List<String[]> rows;
		try
		{
			rows = CMLib.database().DBRawQuery("SELECT * FROM CMVFS");
		}
		catch(final CMException e)
		{
			rows = new ArrayList<String[]>();
		}
		final Set<String> kept = new HashSet<String>();
		final StringBuilder index = new StringBuilder();
		// a line of the index stays as it was while its file's data is the
		// same: the engine writes some of the files again with other owners
		final Map<String, String> oldLines = new HashMap<String, String>();
		final String oldIndex = readText(new File(dir, VFS_INDEX));
		if(oldIndex != null)
		{
			for(final String line : oldIndex.split("\n"))
			{
				final int tab = line.indexOf('\t');
				if(tab > 0)
					oldLines.put(line.substring(0, tab), line);
			}
		}
		final TreeMap<String, String> lines = new TreeMap<String, String>();
		if(rows.size() > 1)
		{
			final String[] header = rows.get(0);
			int nameCol = -1, typeCol = -1, whoCol = -1, dataCol = -1;
			for(int c = 0; c < header.length; c++)
			{
				final String h = header[c].trim().toUpperCase();
				if(h.equals("CMFNAM"))
					nameCol = c;
				else
				if(h.equals("CMDTYP"))
					typeCol = c;
				else
				if(h.equals("CMWHOM"))
					whoCol = c;
				else
				if(h.equals("CMDATA"))
					dataCol = c;
			}
			for(final String[] row : rows.subList(1, rows.size()))
			{
				if((nameCol < 0) || (dataCol < 0) || (row.length <= Math.max(nameCol, dataCol)))
					continue;
				final String path = row[nameCol].trim();
				if((path.length() == 0) || path.contains("..") || path.startsWith("/") || path.equals(VFS_INDEX))
					continue;
				final byte[] data = B64Encoder.B64decode(row[dataCol].trim());
				final File F = new File(dir, path);
				final byte[] old = F.isFile() ? java.nio.file.Files.readAllBytes(F.toPath()) : null;
				final byte[] bytes = (data == null) ? new byte[0] : data;
				final boolean same = (old != null) && Arrays.equals(old, bytes);
				if(same)
					settle(F);
				else
				if(mayChange(F, bytes))
				{
					backup(F);
					if((F.getParentFile() != null) && (!F.getParentFile().isDirectory()))
						F.getParentFile().mkdirs();
					java.nio.file.Files.write(F.toPath(), bytes);
					changed[0]++;
				}
				kept.add(F.getCanonicalPath());
				if(same && oldLines.containsKey(path))
					lines.put(path, oldLines.get(path));
				else
					lines.put(path, path + "\t" + ((typeCol >= 0) ? row[typeCol].trim() : "0") + "\t" + ((whoCol >= 0) ? row[whoCol].trim() : ""));
			}
		}
		for(final String line : lines.values())
			index.append(line).append('\n');
		writeOrDelete(new File(dir, VFS_INDEX), (index.length() == 0) ? null : index.toString(), changed);
		// files of the file system that are gone
		if(dir.isDirectory())
		{
			final List<File> all = new ArrayList<File>();
			collectFiles(dir, all);
			for(final File F : all)
			{
				if((!F.getName().equals(VFS_INDEX)) && (!F.getName().endsWith(BUILDER_EXT)) && (!kept.contains(F.getCanonicalPath())) && mayChange(F, null) && backup(F) && F.delete())
					changed[0]++;
			}
		}
	}

	private static void collectFiles(final File dir, final List<File> into)
	{
		final File[] list = dir.listFiles();
		if(list == null)
			return;
		for(final File F : list)
		{
			if(F.isDirectory())
				collectFiles(F, into);
			else
				into.add(F);
		}
	}

	/**
	 * Puts the world's templates, clans, journals and files into the
	 * database: for a new game, and when builder mode rebuilds from the files.
	 * @param replace true to take the database's out first, as a rebuild does
	 */
	private static void importExtras(final boolean replace)
	{
		final XMLLibrary xml = CMLib.xml();
		// the file system's files
		final File vfs = new File(worldDir, VFS_DIR);
		if(vfs.isDirectory())
		{
			final Map<String, String[]> index = new HashMap<String, String[]>();
			final String idx = readText(new File(vfs, VFS_INDEX));
			if(idx != null)
			{
				for(final String line : idx.split("\n"))
				{
					final String[] parts = line.split("\t", -1);
					if(parts.length >= 3)
						index.put(parts[0], parts);
				}
			}
			final List<File> all = new ArrayList<File>();
			collectFiles(vfs, all);
			final String root = vfs.getPath() + File.separator;
			for(final File F : all)
			{
				if(F.getName().equals(VFS_INDEX))
					continue;
				final String path = F.getPath().startsWith(root) ? F.getPath().substring(root.length()).replace(File.separatorChar, '/') : F.getName();
				final String[] meta = index.get(path);
				try
				{
					CMLib.database().DBUpSertVFSFile(path, (meta == null) ? 0 : CMath.s_int(meta[1]),
						(meta == null) ? "World" : meta[2], F.lastModified(), java.nio.file.Files.readAllBytes(F.toPath()));
				}
				catch(final IOException e)
				{
					Log.errOut("WorldFiles", F.getPath() + ": " + e.getMessage());
				}
			}
			// the file system was read from the database already: read it again
			CMFile.unloadVFS();
		}
		// templates, shared, so every builder has them
		final String templates = readWorldXML(new File(worldDir, TEMPLATES_FILE));
		if(templates != null)
		{
			int n = 0;
			for(final XMLTag tag : xml.parseAllXML(templates))
			{
				if(!tag.tag().equalsIgnoreCase("TEMPLATES"))
					continue;
				for(final XMLTag t : tag.contents())
				{
					final String who = unesc(t.getParmValue("WHO"));
					final String key = unesc(t.getParmValue("KEY"));
					if(replace)
					{
						CMLib.database().DBDeletePlayerData(who, TEMPLATE_PERSONAL, key);
						CMLib.database().DBDeletePlayerData(who, TEMPLATE_SHARED, key);
					}
					CMLib.database().DBCreatePlayerData(who, TEMPLATE_SHARED, key, unesc(t.value()));
					n++;
				}
			}
			Log.sysOut("WorldFiles", "Templates from " + TEMPLATES_FILE + ": " + n);
		}
		// clans with no player members
		final String clans = readWorldXML(new File(worldDir, CLANS_FILE));
		if(clans != null)
		{
			final Set<String> have = new HashSet<String>();
			try
			{
				final List<String[]> rows = CMLib.database().DBRawQuery("SELECT CMCLID FROM CMCLAN");
				for(final String[] row : rows.subList(Math.min(1, rows.size()), rows.size()))
				{
					if(row.length > 0)
						have.add(row[0].trim().toUpperCase());
				}
			}
			catch(final CMException e)
			{
			}
			for(final XMLTag tag : xml.parseAllXML(clans))
			{
				if(!tag.tag().equalsIgnoreCase("CLANS"))
					continue;
				for(final XMLTag c : tag.contents())
				{
					final Clan C = (Clan)CMClass.getCommon("DefaultClan");
					C.setName(unesc(c.getValFromPieces("ID")));
					C.setPremise(unesc(c.getValFromPieces("PREMISE")));
					C.setAcceptanceSettings(unesc(c.getValFromPieces("ACCEPT")));
					C.setStatus(c.getIntFromPieces("STATUS"));
					C.setDataXML(unesc(c.getValFromPieces("DATA")));
					C.setRecall(unesc(c.getValFromPieces("RECALL")));
					C.setDonation(unesc(c.getValFromPieces("DONATION")));
					C.setMorgue(unesc(c.getValFromPieces("MORGUE")));
					C.setTrophies(c.getIntFromPieces("TROPHIES"));
					if(have.contains(C.clanID().toUpperCase()))
					{
						if(replace)
							CMLib.database().DBUpdateClan(C);
					}
					else
						CMLib.database().DBCreateClan(C);
				}
			}
		}
		// journals
		for(final File F : listFiles(new File(worldDir, JOURNALS_DIR), JOURNAL_EXT))
		{
			final String text = readText(F);
			if(text == null)
				continue;
			for(final XMLTag tag : xml.parseAllXML(text))
			{
				if(!tag.tag().equalsIgnoreCase("JOURNAL"))
					continue;
				final String name = unesc(tag.getParmValue("NAME"));
				if(name.length() == 0)
					continue;
				if(replace)
					CMLib.database().DBDeleteJournal(name, null);
				for(final XMLTag e : tag.contents())
				{
					final JournalEntry E = (JournalEntry)CMClass.getCommon("DefaultJournalEntry");
					E.key(unesc(e.getValFromPieces("KEY")));
					E.from(unesc(e.getValFromPieces("FROM")));
					E.to(unesc(e.getValFromPieces("TO")));
					E.dateStr(unesc(e.getValFromPieces("DATE")));
					E.update(e.getLongFromPieces("UPDATE", 0));
					E.expiration(e.getLongFromPieces("EXPIRES", 0));
					E.parent(unesc(e.getValFromPieces("PARENT")));
					E.attributes(e.getLongFromPieces("ATTRS", 0));
					E.data(unesc(e.getValFromPieces("DATA")));
					E.msgIcon(unesc(e.getValFromPieces("ICON")));
					E.subj(unesc(e.getValFromPieces("SUBJ")));
					E.msg(unesc(e.getValFromPieces("MSG")));
					CMLib.database().DBWriteJournal(name, E);
				}
			}
		}
	}

	/**
	 * Writes an area as it is in the world to the database, the cache of the
	 * world files: the area, and each of its rooms with its exits, mobs and
	 * items, as the builder commands do.
	 * @param A the area
	 */
	private static void cacheArea(final Area A)
	{
		CMLib.database().DBUpdateArea(A.Name(), A);
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if((R == null) || R.amDestroyed() || (R.roomID().length() == 0))
				continue;
			CMLib.database().DBUpdateRoom(R);
			CMLib.database().DBUpdateExits(R);
			CMLib.database().DBUpdateMOBs(R);
			CMLib.database().DBUpdateItems(R);
		}
	}

	/**
	 * Returns the quests the database holds, as their names and scripts,
	 * sorted by name.  Read from the table itself: the engine's own reader
	 * skips quests already loaded, which in a running game is all of them.
	 * @return name and script pairs
	 * @throws CMException if the table cannot be read
	 */
	private static List<String[]> questRows() throws CMException
	{
		final List<String[]> rows = CMLib.database().DBRawQuery("SELECT * FROM CMQUESTS");
		final List<String[]> quests = new ArrayList<String[]>();
		if(rows.size() == 0)
			return quests;
		final String[] header = rows.get(0);
		int nameCol = -1;
		int scriptCol = -1;
		for(int c = 0; c < header.length; c++)
		{
			if(header[c].trim().equalsIgnoreCase("CMQUESID"))
				nameCol = c;
			else
			if(header[c].trim().equalsIgnoreCase("CMQSCRPT"))
				scriptCol = c;
		}
		if((nameCol < 0) || (scriptCol < 0))
			throw new CMException("The quest table has no name or script column");
		for(final String[] row : rows.subList(1, rows.size()))
		{
			if((row.length > Math.max(nameCol, scriptCol)) && (!row[scriptCol].equals("null")))
				quests.add(new String[] { row[nameCol].trim(), row[scriptCol].trim() });
		}
		Collections.sort(quests, new Comparator<String[]>()
		{
			@Override
			public int compare(final String[] o1, final String[] o2)
			{
				return o1[0].compareToIgnoreCase(o2[0]);
			}
		});
		return quests;
	}

	/** the custom objects' folder, a folder to each kind */
	public static final String		CUSTOM_DIR		= "custom";
	/** the kinds of custom object, each a folder of custom/ */
	private static final String[]	CUSTOM_KINDS	= { "races", "classes", "abilities", "commands", "behaviors" };
	/** a custom ability's or command's script of more than one line, beside it */
	private static final String		SCRIPT_EXT		= ".script";
	/** how a custom object's SCRIPT names the file beside it */
	private static final String		SCRIPT_REF		= "FILE:";

	/**
	 * Writes the custom races, classes, abilities and commands, as the
	 * database holds them, one file each, by kind: the element the old
	 * custom.cmare had for it, laid out.  An ability's or command's SCRIPT of
	 * more than one line goes to a file beside it, as it is, which the
	 * element names (SCRIPT=FILE:name.script).
	 * @return for each kind, its files' names and texts
	 */
	private static Map<String, Map<String, String>> customFiles()
	{
		final Map<String, Map<String, String>> kinds = new HashMap<String, Map<String, String>>();
		for(final String kind : CUSTOM_KINDS)
			kinds.put(kind, new TreeMap<String, String>());
		for(final AckRecord rec : sortedRecords(CMLib.database().DBReadRaces()))
			putCustom(kinds.get("races"), rec.ID(), rec.data().trim().startsWith("<RACE") ? rec.data() : ("<RACE>" + rec.data() + "</RACE>"));
		for(final AckRecord rec : sortedRecords(CMLib.database().DBReadClasses()))
			putCustom(kinds.get("classes"), rec.ID(), rec.data().trim().startsWith("<CCLASS") ? rec.data() : ("<CCLASS>" + rec.data() + "</CCLASS>"));
		for(final AckRecord rec : sortedRecords(CMLib.database().DBReadAbilities()))
			putCustom(kinds.get("abilities"), rec.ID(), "<ABILITY ID=\"" + rec.ID() + "\" TYPE=\"" + rec.typeClass() + "\">" + rec.data() + "</ABILITY>");
		for(final AckRecord rec : sortedRecords(CMLib.database().DBReadCommands()))
		{
			if(GenBehaviors.isBehaviorRecord(rec)) // N7: the generic behaviours, kept with the commands
				putCustom(kinds.get("behaviors"), rec.ID(), "<BEHAVIOR ID=\"" + rec.ID() + "\">" + rec.data() + "</BEHAVIOR>");
			else
				putCustom(kinds.get("commands"), rec.ID(), "<COMMAND ID=\"" + rec.ID() + "\" BASE=\"" + rec.typeClass() + "\">" + rec.data() + "</COMMAND>");
		}
		return kinds;
	}

	/**
	 * Adds a custom object's file, and its script's beside it if its SCRIPT
	 * has more than one line, to its kind's files.
	 * @param files the kind's files
	 * @param id the object's ID
	 * @param element its element
	 */
	private static void putCustom(final Map<String, String> files, final String id, String element)
	{
		final String fileName = fileNameFor(id, AREA_EXT, files.keySet());
		final int s = element.indexOf("<SCRIPT>");
		final int e = (s < 0) ? -1 : element.indexOf("</SCRIPT>", s);
		if((s >= 0) && (e > s))
		{
			// as it is: only the angle brackets the XML escapes come back, which
			// escape again to the very same text
			final String script = element.substring(s + "<SCRIPT>".length(), e).replace("&lt;", "<").replace("&gt;", ">");
			if(script.trim().indexOf('\n') >= 0)
			{
				final String scriptName = fileName.substring(0, fileName.length() - AREA_EXT.length()) + SCRIPT_EXT;
				files.put(scriptName, script);
				element = element.substring(0, s) + "<SCRIPT>" + SCRIPT_REF + scriptName + element.substring(e);
			}
		}
		files.put(fileName, layOut(element));
	}

	/**
	 * Returns the custom objects' elements, as the old custom.cmare had them:
	 * from that file if there is one, then from the files of custom/, each
	 * with its script read back in from the file beside it.
	 * @return the elements: tag, whole, attributes, inside
	 */
	private static List<String[]> customElements()
	{
		final List<String[]> elements = new ArrayList<String[]>();
		final String old = readWorldXML(new File(worldDir, CUSTOM_FILE));
		if(old != null)
			elements.addAll(topElements(innerOf(old, "CUSTOM")));
		for(final String kind : CUSTOM_KINDS)
		{
			final File dir = new File(new File(worldDir, CUSTOM_DIR), kind);
			for(final File F : listFiles(dir, AREA_EXT))
			{
				String text = readWorldXML(F);
				if(text == null)
					continue;
				final int s = text.indexOf("<SCRIPT>" + SCRIPT_REF);
				final int e = (s < 0) ? -1 : text.indexOf("</SCRIPT>", s);
				if((s >= 0) && (e > s))
				{
					final String scriptName = text.substring(s + "<SCRIPT>".length() + SCRIPT_REF.length(), e).trim();
					final String script = readText(new File(dir, scriptName));
					if(script != null)
						text = text.substring(0, s) + "<SCRIPT>" + escapeAngles(script) + text.substring(e);
				}
				elements.addAll(topElements(text));
			}
		}
		return elements;
	}

	/**
	 * Writes the custom races, classes, abilities and commands, as the
	 * database holds them, in the CUSTOM section the builder's EXPORT writes,
	 * with commands, which it does not write, as COMMAND elements.  The
	 * world's files have them one to a file now (see {@link #customFiles()}).
	 * @return the text, or null if there are none
	 */
	@SuppressWarnings("unused")
	private static String customText()
	{
		final StringBuilder xml = new StringBuilder();
		for(final AckRecord rec : sortedRecords(CMLib.database().DBReadRaces()))
			xml.append(rec.data().trim().startsWith("<RACE") ? rec.data() : ("<RACE>" + rec.data() + "</RACE>"));
		for(final AckRecord rec : sortedRecords(CMLib.database().DBReadClasses()))
			xml.append(rec.data().trim().startsWith("<CCLASS") ? rec.data() : ("<CCLASS>" + rec.data() + "</CCLASS>"));
		for(final AckRecord rec : sortedRecords(CMLib.database().DBReadAbilities()))
			xml.append("<ABILITY ID=\"").append(rec.ID()).append("\" TYPE=\"").append(rec.typeClass()).append("\">").append(rec.data()).append("</ABILITY>");
		for(final AckRecord rec : sortedRecords(CMLib.database().DBReadCommands()))
			xml.append("<COMMAND ID=\"").append(rec.ID()).append("\" BASE=\"").append(rec.typeClass()).append("\">").append(rec.data()).append("</COMMAND>");
		if(xml.length() == 0)
			return null;
		return layOut("<CUSTOM>" + xml.toString() + "</CUSTOM>");
	}

	private static List<AckRecord> sortedRecords(final List<AckRecord> recs)
	{
		final List<AckRecord> sorted = new ArrayList<AckRecord>((recs == null) ? new ArrayList<AckRecord>() : recs);
		Collections.sort(sorted, new Comparator<AckRecord>()
		{
			@Override
			public int compare(final AckRecord o1, final AckRecord o2)
			{
				return o1.ID().compareToIgnoreCase(o2.ID());
			}
		});
		return sorted;
	}

	/**
	 * Writes the catalog: each mob and item as the database holds it, with
	 * its category and catalog data.
	 * @return the text, or null if the catalog is empty
	 */
	private static String catalogText()
	{
		final XMLLibrary xml = CMLib.xml();
		final StringBuilder str = new StringBuilder();
		final List<Physical> all = new ArrayList<Physical>();
		all.addAll(Arrays.asList(CMLib.catalog().getCatalogMobs()));
		all.addAll(Arrays.asList(CMLib.catalog().getCatalogItems()));
		for(final Physical P : all)
		{
			final CataData data = CMLib.catalog().getCatalogData(P);
			final String kind = (P instanceof MOB) ? "CATMOB" : "CATITEM";
			str.append("<").append(kind).append(">");
			if(P instanceof MOB)
				str.append(CMLib.coffeeMaker().getMobXML((MOB)P));
			else
				str.append(CMLib.coffeeMaker().getItemXML((Item)P));
			if(data != null)
			{
				str.append(xml.convertXMLtoTag("CATEGORY", xml.parseOutAngleBrackets(data.category())));
				str.append(CMLib.coffeeMaker().textTag("CATADATA", data.data(P.Name())));
			}
			str.append("</").append(kind).append(">");
		}
		if(str.length() == 0)
			return null;
		return layOut("<CATALOG>" + str.toString() + "</CATALOG>");
	}

	private static void writeFolder(final File dir, final String ext, final Map<String, String> files, final int[] changed) throws IOException
	{
		for(final Map.Entry<String, String> e : files.entrySet())
			writeOrDelete(new File(dir, e.getKey()), e.getValue(), changed);
		for(final File F : listFiles(dir, ext))
		{
			if(!files.containsKey(F.getName()))
				writeOrDelete(F, null, changed);
		}
		// B2: a known file gone from both the disk and the game is settled too
		final String at = pathIn(dir);
		if(at != null)
		{
			for(final String path : new ArrayList<String>(knownSums.keySet()))
			{
				if(path.startsWith(at + "/") && path.endsWith(ext) && (path.indexOf('/', at.length() + 1) < 0)
				&& (!files.containsKey(path.substring(at.length() + 1))) && (!new File(worldDir, path).exists()))
					settle(new File(worldDir, path));
			}
		}
	}

	/**
	 * Writes a file if its text changed, or deletes it if there is no text.
	 * @param F the file
	 * @param text its text, or null for none
	 * @param changed counts the files written or deleted
	 * @throws IOException if it cannot be written
	 */
	private static void writeOrDelete(final File F, final String text, final int[] changed) throws IOException
	{
		if(text == null)
		{
			if(!F.isFile())
				settle(F);
			else
			if(mayChange(F, null) && backup(F) && F.delete())
				changed[0]++;
			return;
		}
		if(text.equals(readText(F)))
		{
			settle(F);
			return;
		}
		if(!mayChange(F, text.getBytes(StandardCharsets.UTF_8)))
			return;
		backup(F);
		if((F.getParentFile() != null) && (!F.getParentFile().isDirectory()) && (!F.getParentFile().mkdirs()))
			throw new IOException("Cannot make " + F.getParentFile().getPath());
		try(Writer out = new OutputStreamWriter(new FileOutputStream(F), "UTF-8"))
		{
			out.write(text);
		}
		changed[0]++;
	}

	/**
	 * Returns a file name for a thing: its name, lower case, with anything but
	 * letters and digits made a dash, made unique among those given.
	 * @param name the thing's name
	 * @param ext the file's extension
	 * @param taken names already used
	 * @return the file name
	 */
	private static String fileNameFor(final String name, final String ext, final Set<String> taken)
	{
		final StringBuilder base = new StringBuilder();
		for(final char c : name.toLowerCase().toCharArray())
		{
			if(Character.isLetterOrDigit(c) && (c < 128))
				base.append(c);
			else
			if((base.length() > 0) && (base.charAt(base.length() - 1) != '-'))
				base.append('-');
		}
		while((base.length() > 0) && (base.charAt(base.length() - 1) == '-'))
			base.setLength(base.length() - 1);
		if(base.length() == 0)
			base.append("unnamed");
		String fileName = base.toString() + ext;
		for(int i = 2; taken.contains(fileName); i++)
			fileName = base.toString() + "-" + i + ext;
		return fileName;
	}

	/**
	 * Lays XML out a tag to a line, indented, so that a change to a world file
	 * shows as a change to the lines it touches.  An element holding only text
	 * stays on one line, so no value gains or loses a character; only the
	 * space between tags changes, which the reader ignores.
	 * @param xml the XML
	 * @return the laid out XML, ending in a newline
	 */
	static String layOut(final String xml)
	{
		// split into tags and the text between them
		final List<String> parts = new ArrayList<String>();
		int i = 0;
		while(i < xml.length())
		{
			final int lt = xml.indexOf('<', i);
			if(lt < 0)
			{
				parts.add(xml.substring(i));
				break;
			}
			if(lt > i)
				parts.add(xml.substring(i, lt));
			final int gt = xml.startsWith("<![CDATA[", lt) ? (xml.indexOf("]]>", lt) + 2) : xml.indexOf('>', lt);
			if(gt < 0)
			{
				parts.add(xml.substring(lt));
				break;
			}
			final String opening = xml.substring(lt, gt + 1);
			final String valueName = tagName(opening);
			// Presentation markup in a value is literal data, not file structure.
			// Indenting it changes its contents and hides a leading <VARIES>.
			if(isLiteralValue(valueName) && (!opening.endsWith("/>")))
			{
				final String close = "</" + valueName + ">";
				final int end = xml.indexOf(close, gt + 1);
				if(end >= 0)
				{
					parts.add(xml.substring(lt, end + close.length()));
					i = end + close.length();
					continue;
				}
			}
			parts.add(opening);
			i = gt + 1;
		}
		final StringBuilder out = new StringBuilder();
		int depth = 0;
		for(int p = 0; p < parts.size(); p++)
		{
			final String part = parts.get(p);
			if(!part.startsWith("<"))
			{
				if(part.trim().length() > 0)
					indent(out, depth).append(part).append('\n'); // text among elements: kept, on its own
				continue;
			}
			if(part.startsWith("</"))
			{
				depth = Math.max(0, depth - 1);
				indent(out, depth).append(part).append('\n');
				continue;
			}
			if(part.endsWith("/>") || part.startsWith("<?") || part.startsWith("<!"))
			{
				indent(out, depth).append(part).append('\n');
				continue;
			}
			// an element holding only text, or nothing, goes on one line
			final String name = tagName(part);
			if(isLiteralValue(name) && part.endsWith("</" + name + ">"))
			{
				indent(out, depth).append(part).append('\n');
				continue;
			}
			if((p + 1 < parts.size()) && parts.get(p + 1).equals("</" + name + ">"))
			{
				indent(out, depth).append(part).append(parts.get(p + 1)).append('\n');
				p++;
				continue;
			}
			if((p + 2 < parts.size())
			&& (!parts.get(p + 1).startsWith("<"))
			&& parts.get(p + 2).equals("</" + name + ">"))
			{
				indent(out, depth).append(part).append(parts.get(p + 1)).append(parts.get(p + 2)).append('\n');
				p += 2;
				continue;
			}
			indent(out, depth).append(part).append('\n');
			depth++;
		}
		return out.toString();
	}

	private static boolean isLiteralValue(final String name)
	{
		return name.equals("RDESC") || name.equals("RDISP")
			|| name.equals("NAME") || name.equals("DESC") || name.equals("DISP")
			|| name.equals("ATEXT");
	}

	private static String tagName(final String tag)
	{
		int end = 1;
		while((end < tag.length()) && (!Character.isWhitespace(tag.charAt(end))) && (tag.charAt(end) != '>') && (tag.charAt(end) != '/'))
			end++;
		return tag.substring(1, end);
	}

	private static StringBuilder indent(final StringBuilder out, final int depth)
	{
		for(int d = 0; d < depth; d++)
			out.append(' ');
		return out;
	}

	/**
	 * Splits XML into its top elements: for each, its tag name, the whole
	 * element, the attributes in its start tag, and what is inside it.  Used
	 * before the engine's XML library can be.
	 * @param xml the XML
	 * @return the elements
	 */
	static List<String[]> topElements(final String xml)
	{
		final List<String[]> elements = new ArrayList<String[]>();
		int i = 0;
		while(true)
		{
			final int start = xml.indexOf('<', i);
			if((start < 0) || (start + 1 >= xml.length()))
				break;
			final int startEnd = xml.indexOf('>', start);
			if(startEnd < 0)
				break;
			final String startTag = xml.substring(start + 1, startEnd);
			int nameEnd = 0;
			while((nameEnd < startTag.length()) && (!Character.isWhitespace(startTag.charAt(nameEnd))) && (startTag.charAt(nameEnd) != '/'))
				nameEnd++;
			final String name = startTag.substring(0, nameEnd);
			if(startTag.endsWith("/"))
			{
				elements.add(new String[] { name.toUpperCase(), xml.substring(start, startEnd + 1), startTag.substring(nameEnd), "" });
				i = startEnd + 1;
				continue;
			}
			// find the matching end tag, counting elements of the same name inside
			int depth = 1;
			int scan = startEnd + 1;
			int end = -1;
			while(depth > 0)
			{
				final int nextOpen = indexOfTag(xml, "<" + name, scan);
				final int nextClose = xml.indexOf("</" + name + ">", scan);
				if(nextClose < 0)
					break;
				if((nextOpen >= 0) && (nextOpen < nextClose))
				{
					final int openEnd = xml.indexOf('>', nextOpen);
					if((openEnd > 0) && (xml.charAt(openEnd - 1) != '/'))
						depth++;
					scan = openEnd + 1;
				}
				else
				{
					depth--;
					end = nextClose;
					scan = nextClose + name.length() + 3;
				}
			}
			if(end < 0)
				break;
			final int wholeEnd = end + name.length() + 3;
			elements.add(new String[] { name.toUpperCase(), xml.substring(start, wholeEnd), startTag.substring(nameEnd), xml.substring(startEnd + 1, end) });
			i = wholeEnd;
		}
		return elements;
	}

	/** finds a start tag of the given name, not a longer name that begins with it */
	private static int indexOfTag(final String xml, final String open, int from)
	{
		while(true)
		{
			final int x = xml.indexOf(open, from);
			if((x < 0) || (x + open.length() >= xml.length()))
				return -1;
			final char c = xml.charAt(x + open.length());
			if((c == '>') || (c == '/') || Character.isWhitespace(c))
				return x;
			from = x + 1;
		}
	}

	private static String innerOf(final String xml, final String tag)
	{
		for(final String[] element : topElements(xml))
		{
			if(element[0].equalsIgnoreCase(tag))
				return element[3];
		}
		return "";
	}

	private static String firstValue(final String xml, final String tag)
	{
		final int x = xml.indexOf("<" + tag + ">");
		if(x < 0)
			return "";
		final int y = xml.indexOf("</" + tag + ">", x);
		return (y < 0) ? "" : xml.substring(x + tag.length() + 2, y).trim();
	}

	private static String attribute(final String attrs, final String name)
	{
		final String key = name + "=\"";
		final int x = attrs.indexOf(key);
		if(x < 0)
			return "";
		final int y = attrs.indexOf('"', x + key.length());
		return (y < 0) ? "" : attrs.substring(x + key.length(), y);
	}

	private static String stripFinalNewline(final String s)
	{
		String t = s;
		while(t.endsWith("\n") || t.endsWith("\r"))
			t = t.substring(0, t.length() - 1);
		return t;
	}

	/**
	 * Returns the files of one kind in a folder, sorted by name.
	 * @param dir the folder
	 * @param ext their extension
	 * @return the files, none if the folder is missing
	 */
	private static List<File> listFiles(final File dir, final String ext)
	{
		final List<File> files = new ArrayList<File>();
		final File[] all = dir.listFiles();
		if(all != null)
		{
			for(final File F : all)
			{
				if(F.isFile() && F.getName().toLowerCase().endsWith(ext))
					files.add(F);
			}
		}
		Collections.sort(files, new Comparator<File>()
		{
			@Override
			public int compare(final File o1, final File o2)
			{
				return o1.getName().compareTo(o2.getName());
			}
		});
		return files;
	}

	/**
	 * Returns the checksum of the world files this game booted from: the
	 * world version a save records.
	 * @return the checksum
	 */
	public static String bootChecksum()
	{
		return bootSum;
	}

	/**
	 * Returns the checksum of each area file this game booted from.
	 * @return the checksums, by file name
	 */
	public static Map<String, String> bootAreaSums()
	{
		return new TreeMap<String, String>(bootAreaSums);
	}

	public static Map<String,String> loadedAreaSums()
	{
		final Map<String,String> sums = new TreeMap<String,String>();
		for(final Map.Entry<String,String> entry : knownSums.entrySet())
			if(entry.getKey().startsWith(AREAS_DIR + "/"))
			{
				final int dash = entry.getValue().indexOf('-');
				sums.put(entry.getKey().substring(AREAS_DIR.length()+1), dash < 0 ? entry.getValue() : entry.getValue().substring(dash+1));
			}
		return sums;
	}

	/**
	 * Returns a checksum of each area file in the world folder.
	 * @return the checksums, by file name
	 */
	public static Map<String, String> areaSums()
	{
		final Map<String, String> sums = new TreeMap<String, String>();
		for(final File F : listFiles(new File(worldDir, AREAS_DIR), AREA_EXT))
		{
			final CRC32 crc = new CRC32();
			try
			{
				crc.update(java.nio.file.Files.readAllBytes(F.toPath()));
			}
			catch(final IOException e)
			{
				crc.update(1);
			}
			sums.put(F.getName(), Long.toHexString(crc.getValue()));
		}
		return sums;
	}

	/**
	 * Returns the name of the file an area is written to.
	 * @param A the area
	 * @return the file's name
	 */
	public static String areaFileFor(final Area A)
	{
		return fileNameFor(A.Name(), AREA_EXT, new HashSet<String>());
	}

	/**
	 * W21: the file an area's own law (Arrest custom) is kept in as content,
	 * world/laws/[area].ini, named as the area's own file is.
	 * @param areaName the area's name
	 * @return the file, or null when the world's folder is not known
	 */
	public static File lawFile(final String areaName)
	{
		if(worldDir == null)
			return null;
		return new File(new File(worldDir, "laws"), fileNameFor(areaName, ".ini", new HashSet<String>()));
	}

	/**
	 * W21: writes an area's own law to its world file, from the law's text as
	 * the law book keeps it (lines joined by ~, ' as `).
	 * @param areaName the area's name
	 * @param raw the law's text
	 * @return true if it was written
	 */
	public static boolean writeLaw(final String areaName, final String raw)
	{
		final File F = lawFile(areaName);
		if(F == null)
			return false;
		final StringBuilder text = new StringBuilder("# The law of ").append(areaName)
			.append(" (its Arrest behaviour's custom law), as its law book left it in builder mode.\n");
		final List<String> lines = new ArrayList<String>();
		for(final String line : raw.replace('`', '\'').split("~"))
		{
			if((line.trim().length() > 0) && (!line.trim().startsWith("#")))
				lines.add(line.trim());
		}
		Collections.sort(lines); // the same law, the same file
		for(final String line : lines)
			text.append(line).append("\n");
		try
		{
			F.getParentFile().mkdirs();
			java.nio.file.Files.write(F.toPath(), text.toString().getBytes("UTF-8"));
			return true;
		}
		catch(final IOException e)
		{
			Log.errOut("WorldFiles", "Unable to write " + F + ": " + e.getMessage());
			return false;
		}
	}

	/**
	 * The content files' paths in the world folder, as {@link #checksum()}
	 * takes them.
	 * @return the paths
	 */
	private static List<String> contentPaths()
	{
		final List<String> paths = new ArrayList<String>();
		for(final File F : listFiles(new File(worldDir, AREAS_DIR), AREA_EXT))
			paths.add(AREAS_DIR + "/" + F.getName());
		for(final File F : listFiles(new File(worldDir, QUESTS_DIR), QUEST_EXT))
			paths.add(QUESTS_DIR + "/" + F.getName());
		if(new File(worldDir, CUSTOM_FILE).isFile())
			paths.add(CUSTOM_FILE);
		for(final String kind : CUSTOM_KINDS)
		{
			final File dir = new File(new File(worldDir, CUSTOM_DIR), kind);
			for(final File F : listFiles(dir, AREA_EXT))
				paths.add(CUSTOM_DIR + "/" + kind + "/" + F.getName());
			for(final File F : listFiles(dir, SCRIPT_EXT))
				paths.add(CUSTOM_DIR + "/" + kind + "/" + F.getName());
		}
		// JS5: the world's JavaScript and script files
		for(final String sub : new String[] { "js", "scripts", "routes" })
		{
			final File dir = new File(worldDir, sub);
			if(!dir.isDirectory())
				continue;
			final List<File> all = new ArrayList<File>();
			collectFiles(dir, all);
			final String root = dir.getPath() + File.separator;
			for(final File F : all)
			{
				if(F.getPath().startsWith(root) && (!F.getName().endsWith(BUILDER_EXT)))
					paths.add(sub + "/" + F.getPath().substring(root.length()).replace(File.separatorChar, '/'));
			}
		}
		if(new File(worldDir, CATALOG_FILE).isFile())
			paths.add(CATALOG_FILE);
		if(new File(worldDir, WorldSettings.FILE).isFile())
			paths.add(WorldSettings.FILE);
		if(new File(worldDir, TEMPLATES_FILE).isFile())
			paths.add(TEMPLATES_FILE);
		if(new File(worldDir, CLANS_FILE).isFile())
			paths.add(CLANS_FILE);
		if(new File(worldDir, SPACE_FILE).isFile())
			paths.add(SPACE_FILE);
		for(final File F : listFiles(new File(worldDir, JOURNALS_DIR), JOURNAL_EXT))
			paths.add(JOURNALS_DIR + "/" + F.getName());
		final File vfs = new File(worldDir, VFS_DIR);
		if(vfs.isDirectory())
		{
			final List<File> all = new ArrayList<File>();
			collectFiles(vfs, all);
			final String root = vfs.getPath() + File.separator;
			for(final File F : all)
			{
				if(F.getPath().startsWith(root) && (!F.getName().endsWith(BUILDER_EXT)))
					paths.add(VFS_DIR + "/" + F.getPath().substring(root.length()).replace(File.separatorChar, '/'));
			}
		}
		return paths;
	}

	/**
	 * The files the export writes (B2): the content files, less the world's
	 * settings, and the dev areas'.
	 * @return their paths in the world folder
	 */
	private static List<String> managedPaths()
	{
		final List<String> paths = contentPaths();
		paths.remove(WorldSettings.FILE);
		for(final File F : listFiles(new File(worldDir, DEV_DIR), AREA_EXT))
			paths.add(DEV_DIR + "/" + F.getName());
		return paths;
	}

    private static List<String> trackedPaths()
    {
        final List<String> paths=managedPaths();
        paths.addAll(com.planet_ink.coffee_mud.core.WorldAssets.paths());
        return paths;
    }
    public static synchronized void acceptAssetChanges()
    {
        final Map<String,String> next=new TreeMap<String,String>(knownSums);
        next.keySet().removeIf(com.planet_ink.coffee_mud.core.WorldAssets::reloadable);
        for(final String path:com.planet_ink.coffee_mud.core.WorldAssets.paths())
        {
            if(!com.planet_ink.coffee_mud.core.WorldAssets.reloadable(path))continue;
            final String sum=sumOf(new File(worldDir,path));
            if(sum!=null) next.put(path,sum);
        }
        knownSums=next;
    }

    /** An explicit successful module reload accepts only the manifests it validated, including removals. */
    public static synchronized void acceptModuleChanges()
    {
        final Map<String,String> next = new TreeMap<String,String>(knownSums);
        next.keySet().removeIf(path -> path.startsWith("modules/") && path.endsWith(".json") && path.substring("modules/".length()).indexOf('/') < 0);
        for(final String path : com.planet_ink.coffee_mud.core.WorldAssets.paths())
            if(path.startsWith("modules/") && path.endsWith(".json") && path.substring("modules/".length()).indexOf('/') < 0)
            {
                final String sum = sumOf(new File(worldDir,path));
                if(sum != null) next.put(path,sum);
            }
        knownSums=next;
    }

    /** Only the flat JSON assets actually validated by WORLD COMPOSITIONS RELOAD. */
    public static synchronized void acceptCompositionChanges()
    {
        final Map<String,String> next = new TreeMap<String,String>(knownSums);
        next.keySet().removeIf(path -> path.startsWith("compositions/") && path.endsWith(".json") && path.substring(13).indexOf('/') < 0);
        for(final String path : com.planet_ink.coffee_mud.core.WorldAssets.paths())
            if(path.startsWith("compositions/") && path.endsWith(".json") && path.substring(13).indexOf('/') < 0)
            {
                final String sum = sumOf(new File(worldDir,path));
                if(sum != null) next.put(path,sum);
            }
        knownSums = next;
    }

    /** Only a successful explicit reload acknowledges these hand-authored rule profiles. */
    public static synchronized void acceptCombatChanges()
    {
        final Map<String,String> next = new TreeMap<String,String>(knownSums);
        next.keySet().removeIf(path -> path.startsWith("combat/") && path.endsWith(".json") && path.substring(7).indexOf('/') < 0);
        for(final String path : com.planet_ink.coffee_mud.core.WorldAssets.paths())
            if(path.startsWith("combat/") && path.endsWith(".json") && path.substring(7).indexOf('/') < 0)
            {
                final String sum = sumOf(new File(worldDir,path)); if(sum != null) next.put(path,sum);
            }
        knownSums = next;
    }

    /** Item rules and their shared world affix file are accepted only by explicit family reload. */
    public static synchronized void acceptItemModifierChanges()
    {
        final Map<String,String> next=new TreeMap<String,String>(knownSums);
        next.keySet().removeIf(p->p.equals("affixes.xml")||p.startsWith("itemmods/")&&p.endsWith(".json")&&p.substring(9).indexOf('/')<0);
        for(final String p:com.planet_ink.coffee_mud.core.WorldAssets.paths())if(p.equals("affixes.xml")||p.startsWith("itemmods/")&&p.endsWith(".json")&&p.substring(9).indexOf('/')<0){final String sum=sumOf(new File(worldDir,p));if(sum!=null)next.put(p,sum);}
        knownSums=next;
    }

	/**
	 * Returns a checksum of the content files: their paths in the world folder
	 * and their contents.
	 * @return the checksum
	 */
	public static String checksum()
	{
		return manifestChecksum(fileSums());
	}

	/** The definitions actually accepted by this process, including explicit live reloads. */
	public static String loadedChecksum()
	{
		return manifestChecksum(knownSums);
	}

	public static Map<String,String> loadedManifest()
	{
		return new TreeMap<String,String>(knownSums);
	}

	private static String manifestChecksum(final Map<String,String> manifest)
	{
		final CRC32 crc = new CRC32();
		int count = 0;
		for(final Map.Entry<String,String> entry : new TreeMap<String,String>(manifest).entrySet())
		{
			if(entry.getKey().startsWith(DEV_DIR + "/")) continue;
			crc.update(entry.getKey().getBytes(java.nio.charset.StandardCharsets.UTF_8)); crc.update(0);
			crc.update(entry.getValue().getBytes(java.nio.charset.StandardCharsets.UTF_8)); crc.update(0);
			count++;
		}
		return "3-" + count + "-" + Long.toHexString(crc.getValue());
	}

	private static String readSum(final File dbDir)
	{
		final String sum = readText(new File(dbDir, SUM_FILE));
		return (sum == null) ? "" : sum.trim();
	}

	private static void writeSum(final File dbDir, final String sum)
	{
		try(Writer out = new OutputStreamWriter(new FileOutputStream(new File(dbDir, SUM_FILE)), "UTF-8"))
		{
			out.write(sum + "\n");
		}
		catch(final IOException e)
		{
			Log.errOut("WorldFiles", e);
		}
	}

	private static void copy(final File from, final File to) throws IOException
	{
		try(InputStream in = new FileInputStream(from); OutputStream out = new FileOutputStream(to))
		{
			final byte[] buf = new byte[65536];
			int n;
			while((n = in.read(buf)) > 0)
				out.write(buf, 0, n);
		}
	}

	/**
	 * Reads a world file of XML, with each thing's own text that was written
	 * readable (an element marked xml="1") escaped back onto one line, as
	 * the rest of the engine reads it.  A file written the old way reads as
	 * it is.
	 * @param F the file
	 * @return its XML, or null if there is no such file
	 */
	private static String readWorldXML(final File F)
	{
		return collapseText(readText(F));
	}

	/** what marks a thing's own text written as XML */
	private static final String	READABLE_MARK	= " xml=\"1\">";

	/**
	 * Escapes back each thing's own text written readable: an element marked
	 * xml="1" becomes the element with its contents, less the line breaks
	 * and indents between its tags that the layout added, escaped onto one
	 * line.
	 * @param xml the XML of a world file
	 * @return the XML as the engine reads it
	 */
	static String collapseText(final String xml)
	{
		if((xml == null) || (xml.indexOf(READABLE_MARK) < 0))
			return xml;
		final StringBuilder out = new StringBuilder(xml.length());
		int i = 0;
		while(true)
		{
			final int mark = xml.indexOf(READABLE_MARK, i);
			if(mark < 0)
			{
				out.append(xml, i, xml.length());
				break;
			}
			final int lt = xml.lastIndexOf('<', mark);
			if((lt < i) || (lt < 0))
				return xml;
			final String name = xml.substring(lt + 1, mark);
			final int innerStart = mark + READABLE_MARK.length();
			int depth = 1;
			int j = innerStart;
			int end = -1;
			while(depth > 0)
			{
				final int close = xml.indexOf("</" + name + ">", j);
				if(close < 0)
					return xml;
				int open = xml.indexOf("<" + name, j);
				while((open >= 0) && (open < close))
				{
					final char after = xml.charAt(open + name.length() + 1);
					if((after == '>') || (after == ' '))
						break;
					open = xml.indexOf("<" + name, open + 1);
				}
				if((open >= 0) && (open < close))
				{
					depth++;
					j = open + 1;
				}
				else
				{
					depth--;
					end = close;
					j = close + 1;
				}
			}
			// a script kept as CDATA is left as it is while the layout goes
			final List<String> cdata = new ArrayList<String>();
			final StringBuilder held = new StringBuilder();
			final String within = xml.substring(innerStart, end);
			int c = 0;
			while(true)
			{
				final int cs = within.indexOf("<![CDATA[", c);
				final int ce = (cs < 0) ? -1 : within.indexOf("]]>", cs);
				if(ce < 0)
				{
					held.append(within, c, within.length());
					break;
				}
				held.append(within, c, cs).append("\u0000").append(cdata.size()).append("\u0000");
				cdata.add(within.substring(cs + "<![CDATA[".length(), ce));
				c = ce + 3;
			}
			String inner = held.toString()
				.replaceAll(">[ \\t]*\\r?\\n[ \\t\\r\\n]*<", "><")
				.replaceAll(">[ \\t]*\\r?\\n[ \\t\\r\\n]*\u0000", ">\u0000")
				.replaceAll("\u0000([0-9]+)\u0000[ \\t]*\\r?\\n[ \\t\\r\\n]*<", "\u0000$1\u0000<")
				.replaceAll("^[ \\t\\r\\n]+<", "<")
				.replaceAll(">[ \\t\\r\\n]+$", ">");
			inner = propBack(inner);
			for(int k = 0; k < cdata.size(); k++)
				inner = inner.replace("\u0000" + k + "\u0000", escapeAngles(cdata.get(k)));
			out.append(xml, i, lt).append('<').append(name).append('>')
			   .append(escapeAngles(inner)).append("</").append(name).append('>');
			i = end + name.length() + 3;
		}
		return out.toString();
	}

	/** the names of the base stats a PROP holds, in its order, as CoffeeMaker writes them readable */
	private static final String[] PROP_NAMES = { "ABILITY", "ARMOR", "ATTACK", "DAMAGE", "DISPOSITION", "LEVEL", "REJUV", "SPEED", "WEIGHT", "HEIGHT", "SENSES" };

	/**
	 * Turns each PROP written readable, with named base stats, back into the
	 * values the engine reads, each followed by a bar.
	 * @param xml a thing's own text
	 * @return the text with PROP as the engine has it
	 */
	private static String propBack(final String xml)
	{
		if(xml.indexOf("<PROP ") < 0)
			return xml;
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("<PROP((?: [A-Z]+=\"[^\"]*\")+) ?/>").matcher(xml);
		final StringBuffer out = new StringBuffer();
		while(m.find())
		{
			final Map<String, String> vals = new HashMap<String, String>();
			final java.util.regex.Matcher a = java.util.regex.Pattern.compile("([A-Z]+)=\"([^\"]*)\"").matcher(m.group(1));
			while(a.find())
				vals.put(a.group(1), a.group(2));
			final StringBuilder prop = new StringBuilder("<PROP>");
			for(final String name : PROP_NAMES)
				prop.append(vals.containsKey(name) ? vals.get(name) : "0").append('|');
			m.appendReplacement(out, java.util.regex.Matcher.quoteReplacement(prop.append("</PROP>").toString()));
		}
		m.appendTail(out);
		return out.toString();
	}

	/**
	 * Escapes the angle brackets of a text, as the engine's XML library does
	 * (parseOutAngleBrackets), for use before that library is up.
	 * @param s the text
	 * @return the text with &lt; and &gt;
	 */
	private static String escapeAngles(final String s)
	{
		return s.replace("<", "&lt;").replace(">", "&gt;");
	}

	private static String readText(final File F)
	{
		if(!F.isFile())
			return null;
		try(InputStream in = new FileInputStream(F))
		{
			final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
			final byte[] buf = new byte[65536];
			int n;
			while((n = in.read(buf)) > 0)
				bytes.write(buf, 0, n);
			return new String(bytes.toByteArray(), "UTF-8");
		}
		catch(final IOException e)
		{
			return null;
		}
	}
}
