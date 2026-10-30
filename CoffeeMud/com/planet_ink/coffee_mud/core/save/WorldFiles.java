package com.planet_ink.coffee_mud.core.save;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.exceptions.CMException;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * The world as files: one .cmare file per area in the world folder (WORLDDIR,
 * "world" by default), holding the area, its rooms and exits, and the mobs
 * and items placed in them, in the format the builder's EXPORT writes and
 * IMPORT reads.  The files are the world; the database's world tables are a
 * copy of them, kept so that the rest of the engine can go on reading rooms
 * from the database.
 *
 * At boot, {@link #loadWorld()} compares the files with the checksum the
 * database keeps of the files it was built from.  If they match, the map loads
 * from the database as it always has.  If not (the files were edited, or
 * pulled from git), the database's world tables are emptied and the files
 * imported into them.  In builder mode, {@link #exportAll()} writes the world
 * back to the files: on the builder's SAVE, and when the game shuts down.
 *
 * The rest of the content (quests, custom races and classes, the builder's
 * characters) is still kept in the database.
 */
public final class WorldFiles
{
	/** the file, in the database's folder, with the checksum of the world files it holds */
	public static final String	SUM_FILE	= "world.sum";
	/** the extension of world files */
	public static final String	EXTENSION	= ".cmare";

	private static volatile File	worldDir	= new File("world");
	/** whether the world loaded whole, so that writing it out cannot lose an area */
	private static volatile boolean	loadedWhole	= false;
	/** true while the world files are imported */
	private static volatile boolean	importing	= false;

	private WorldFiles()
	{
	}

	/**
	 * Sets the folder of world files.  The launcher calls this before the
	 * engine starts.
	 * @param dir the folder
	 */
	public static void configure(final File dir)
	{
		worldDir = dir;
	}

	/**
	 * Returns the folder of world files.
	 * @return the folder
	 */
	public static File worldDir()
	{
		return worldDir;
	}

	/**
	 * Returns the world files, sorted by name.
	 * @return the files, none if the folder is missing
	 */
	public static List<File> files()
	{
		final List<File> files = new ArrayList<File>();
		final File[] all = worldDir.listFiles();
		if(all != null)
		{
			for(final File F : all)
			{
				if(F.isFile() && F.getName().toLowerCase().endsWith(EXTENSION))
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
	 * Loads the map, in place of the engine's own load from the database:
	 * from the database if it was built from these world files, else by
	 * importing them into it.  With no world files, the map loads from the
	 * database, and in builder mode the files are then written from it.
	 */
	public static void loadWorld()
	{
		loadedWhole = false;
		final File dbDir = SaveGame.databaseDir();
		final List<File> files = files();
		if(files.size() == 0)
		{
			CMLib.database().DBReadAllRooms(null);
			loadedWhole = true;
			if(SaveGame.isBuilderMode() && (CMLib.map().numAreas() > 0))
			{
				Log.sysOut("WorldFiles", "No world files yet: writing them from the database.");
				exportAll();
			}
			return;
		}
		final String sum = checksum(files);
		if((dbDir != null) && sum.equals(readSum(dbDir)))
		{
			CMLib.database().DBReadAllRooms(null);
			loadedWhole = true;
			return;
		}
		Log.sysOut("WorldFiles", "The world files have changed: importing " + files.size() + " of them.");
		System.out.println("The world files have changed: rebuilding the world from them...");
		try
		{
			CMLib.database().DBRawExecute("DELETE FROM CMROEX");
			CMLib.database().DBRawExecute("DELETE FROM CMROOM");
			CMLib.database().DBRawExecute("DELETE FROM CMAREA");
			// space objects keep their place in the room tables
			CMLib.database().DBRawExecute("DELETE FROM CMROCH WHERE CMROID<>'SPACE'");
			CMLib.database().DBRawExecute("DELETE FROM CMROIT WHERE CMROID<>'SPACE'");
		}
		catch(final CMException e)
		{
			Log.errOut("WorldFiles", e);
			System.out.println("The world could not be rebuilt: " + e.getMessage());
			CMLib.database().DBReadAllRooms(null);
			return;
		}
		boolean whole = true;
		importing = true;
		try
		{
			for(final File F : files)
			{
				final String problem = importFile(F);
				if(problem != null)
				{
					whole = false;
					Log.errOut("WorldFiles", F.getName() + ": " + problem);
					System.out.println("World file " + F.getName() + " could not be read: " + problem);
				}
			}
		}
		finally
		{
			importing = false;
		}
		loadedWhole = whole;
		if(whole && (dbDir != null))
			writeSum(dbDir, sum);
		if(!whole)
			System.out.println("Fix the world files named above.  Until then, builder mode will not write the world files.");
	}

	/**
	 * Returns whether the world files are being imported.  They are the
	 * game's own content, so what IMPORT refuses from files of unknown origin,
	 * such as the Archon's manual, is let in from them.
	 * @return true while importing them
	 */
	public static boolean isImporting()
	{
		return importing;
	}

	/**
	 * Imports one world file into the map and the database.
	 * @param F the file
	 * @return null if imported, or what went wrong
	 */
	private static String importFile(final File F)
	{
		final String text = readText(F);
		if(text == null)
			return "it cannot be read";
		final StringBuffer buf = new StringBuffer(text);
		CMStrings.dikufyLineEndings(buf);
		final List<XMLTag> areaData = new ArrayList<XMLTag>();
		// custom races, classes and abilities are still kept in the database,
		// so the copies in the file are not imported
		String problem = CMLib.coffeeMaker().fillAreaAndCustomVectorFromXML(buf.toString(), areaData, new ArrayList<CMObject>(), new HashMap<String, String>());
		if(problem.length() == 0)
			problem = CMLib.coffeeMaker().unpackAreaFromXML(areaData, null, null, true, true);
		return (problem.length() == 0) ? null : problem;
	}

	/**
	 * Writes the world to the world files, in builder mode: each area to its
	 * file, rewriting only files that changed, and deleting the files of
	 * areas that are gone.  Does nothing unless the world loaded whole, so
	 * that an area that failed to load is never written over.
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
		if(!worldDir.isDirectory() && !worldDir.mkdirs())
		{
			Log.errOut("WorldFiles", "Cannot make the world folder " + worldDir.getPath());
			return -1;
		}
		int changed = 0;
		final Set<String> written = new HashSet<String>();
		for(final Area A : areas)
		{
			final String fileName = fileNameFor(A, written);
			written.add(fileName);
			final String text = areaText(A);
			final File F = new File(worldDir, fileName);
			if(text.equals(readText(F)))
				continue;
			try(Writer out = new OutputStreamWriter(new FileOutputStream(F), "UTF-8"))
			{
				out.write(text);
				changed++;
			}
			catch(final IOException e)
			{
				Log.errOut("WorldFiles", e);
				return -1;
			}
		}
		for(final File F : files())
		{
			if(!written.contains(F.getName()))
			{
				if(F.delete())
					changed++;
			}
		}
		final File dbDir = SaveGame.databaseDir();
		if(dbDir != null)
			writeSum(dbDir, checksum(files()));
		if(changed > 0)
			Log.sysOut("WorldFiles", "Wrote the world files: " + changed + " changed.");
		return changed;
	}

	/**
	 * Returns the file name for an area: its name, lower case, with anything
	 * but letters and digits made a dash, made unique among those given.
	 * @param A the area
	 * @param taken names already used
	 * @return the file name
	 */
	private static String fileNameFor(final Area A, final Set<String> taken)
	{
		final StringBuilder base = new StringBuilder();
		for(final char c : A.Name().toLowerCase().toCharArray())
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
			base.append("area");
		String name = base.toString() + EXTENSION;
		for(int i = 2; taken.contains(name); i++)
			name = base.toString() + "-" + i + EXTENSION;
		return name;
	}

	/**
	 * Returns an area as the text of its world file: the area with its rooms
	 * and what is placed in them, then the custom races, classes and abilities
	 * it uses, laid out a tag to a line.
	 * @param A the area
	 * @return the text
	 */
	private static String areaText(final Area A)
	{
		final Set<CMObject> custom = new TreeSet<CMObject>(new Comparator<CMObject>()
		{
			@Override
			public int compare(final CMObject o1, final CMObject o2)
			{
				final int c = o1.getClass().getName().compareTo(o2.getClass().getName());
				return (c != 0) ? c : o1.ID().compareTo(o2.ID());
			}
		});
		final StringBuilder xml = new StringBuilder(CMLib.coffeeMaker().getAreaXML(A, null, custom, new HashSet<String>(), true));
		if(custom.size() > 0)
		{
			xml.append("<CUSTOM>");
			for(final CMObject o : custom)
			{
				if(o instanceof Race)
					xml.append(((Race)o).racialParms());
				else
				if(o instanceof CharClass)
					xml.append(((CharClass)o).classParms());
				else
				if(o instanceof Ability)
					xml.append(CMLib.coffeeMaker().getGenAbilityXML((Ability)o));
				else
				if(o instanceof Manufacturer)
					xml.append("<MANUFACTURER>").append(((Manufacturer)o).getXML()).append("</MANUFACTURER>");
			}
			xml.append("</CUSTOM>");
		}
		return layOut(xml.toString());
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
	 * Returns a checksum of the world files: their names and contents.
	 * @param files the files
	 * @return the checksum
	 */
	public static String checksum(final List<File> files)
	{
		final CRC32 crc = new CRC32();
		final byte[] buf = new byte[65536];
		for(final File F : files)
		{
			try(InputStream in = new FileInputStream(F))
			{
				crc.update(F.getName().getBytes("UTF-8"));
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
		return files.size() + "-" + Long.toHexString(crc.getValue());
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
