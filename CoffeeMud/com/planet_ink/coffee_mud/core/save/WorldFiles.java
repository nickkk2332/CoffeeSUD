package com.planet_ink.coffee_mud.core.save;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.CatalogLibrary.CataData;
import com.planet_ink.coffee_mud.Libraries.interfaces.DatabaseEngine.AckRecord;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.*;
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
	/** the cache to copy a database built from the files to, or null */
	private static volatile File	refreshCache= null;
	/** whether the content loaded whole, so that writing it out cannot lose any */
	private static volatile boolean	loadedWhole	= false;
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
		final String text = readText(new File(worldDir, CUSTOM_FILE));
		if(text == null)
			return;
		int made = 0;
		for(final String[] element : topElements(innerOf(text, "CUSTOM")))
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
				continue;
			made++;
		}
		Log.sysOut("WorldFiles", "Custom objects from " + CUSTOM_FILE + ": " + made);
	}

	/**
	 * Loads the map, in place of the engine's own load from the database.  When
	 * building from the files, imports the areas, the catalog and the quests,
	 * then records the files' checksum and copies what was built to the cache.
	 */
	public static void loadWorld()
	{
		loadedWhole = false;
		if(!rebuild)
		{
			CMLib.database().DBReadAllRooms(null);
			loadedWhole = true;
			return;
		}
		Log.sysOut("WorldFiles", "Building the world from the files in " + worldDir.getPath());
		System.out.println("The world files have changed: rebuilding the world from them...");
		boolean whole = true;
		importing = true;
		try
		{
			for(final File F : listFiles(new File(worldDir, AREAS_DIR), AREA_EXT))
				whole = report(F, importArea(F)) && whole;
			final File catalog = new File(worldDir, CATALOG_FILE);
			if(catalog.isFile())
				whole = report(catalog, importCatalog(catalog)) && whole;
			whole = report(new File(worldDir, QUESTS_DIR), importQuests()) && whole;
		}
		finally
		{
			importing = false;
		}
		loadedWhole = whole;
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
		final String text = readText(F);
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
		final String text = readText(F);
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
		if((!SaveGame.isBuilderMode()) || (!loadedWhole))
			return -1;
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
		try
		{
			final Map<String, String> areaFiles = new LinkedHashMap<String, String>();
			for(final Area A : areas)
				areaFiles.put(fileNameFor(A.Name(), AREA_EXT, areaFiles.keySet()), layOut(CMLib.coffeeMaker().getAreaXML(A, null, new HashSet<CMObject>(), new HashSet<String>(), true)));
			writeFolder(new File(worldDir, AREAS_DIR), AREA_EXT, areaFiles, changed);
			writeOrDelete(new File(worldDir, CUSTOM_FILE), customText(), changed);
			writeOrDelete(new File(worldDir, CATALOG_FILE), catalogText(), changed);
			final Map<String, String> questFiles = new LinkedHashMap<String, String>();
			for(final String[] quest : questRows())
				questFiles.put(fileNameFor(quest[0], QUEST_EXT, questFiles.keySet()), quest[1] + "\n");
			writeFolder(new File(worldDir, QUESTS_DIR), QUEST_EXT, questFiles, changed);
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
		final File dbDir = SaveGame.databaseDir();
		if(dbDir != null)
			writeSum(dbDir, checksum());
		if(changed[0] > 0)
			Log.sysOut("WorldFiles", "Wrote the world files: " + changed[0] + " changed.");
		return changed[0];
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

	/**
	 * Writes the custom races, classes, abilities and commands, as the
	 * database holds them, in the CUSTOM section the builder's EXPORT writes,
	 * with commands, which it does not write, as COMMAND elements.
	 * @return the text, or null if there are none
	 */
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
				str.append(xml.convertXMLtoTag("CATADATA", xml.parseOutAngleBrackets(data.data(P.Name()))));
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
			if((!files.containsKey(F.getName())) && F.delete())
				changed[0]++;
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
			if(F.isFile() && F.delete())
				changed[0]++;
			return;
		}
		if(text.equals(readText(F)))
			return;
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
			final int gt = xml.indexOf('>', lt);
			if(gt < 0)
			{
				parts.add(xml.substring(lt));
				break;
			}
			parts.add(xml.substring(lt, gt + 1));
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
	 * Returns a checksum of the content files: their paths in the world folder
	 * and their contents.
	 * @return the checksum
	 */
	public static String checksum()
	{
		final List<String> paths = new ArrayList<String>();
		for(final File F : listFiles(new File(worldDir, AREAS_DIR), AREA_EXT))
			paths.add(AREAS_DIR + "/" + F.getName());
		for(final File F : listFiles(new File(worldDir, QUESTS_DIR), QUEST_EXT))
			paths.add(QUESTS_DIR + "/" + F.getName());
		if(new File(worldDir, CUSTOM_FILE).isFile())
			paths.add(CUSTOM_FILE);
		if(new File(worldDir, CATALOG_FILE).isFile())
			paths.add(CATALOG_FILE);
		Collections.sort(paths);
		final CRC32 crc = new CRC32();
		final byte[] buf = new byte[65536];
		for(final String path : paths)
		{
			try(InputStream in = new FileInputStream(new File(worldDir, path)))
			{
				crc.update(path.getBytes("UTF-8"));
				crc.update(0);
				int n;
				while((n = in.read(buf)) > 0)
					crc.update(buf, 0, n);
				crc.update(0);
			}
			catch(final IOException e)
			{
				crc.update(1);
			}
		}
		return "2-" + paths.size() + "-" + Long.toHexString(crc.getValue());
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
