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
 * Saved games on disk.  This knows only about files, so that the launcher
 * can list the saves before the engine starts.
 *
 * Each game runs on a working database, made fresh at every start from the
 * content tables of the cache that DBSERVICE names (see WorldFiles).  A save
 * keeps the tables of that database that hold the game's state (characters,
 * accounts, player data, journals, clans) along with a header and the live
 * world; restoring makes a fresh working database and puts the saved tables
 * in it.
 *
 * <pre>
 * SAVEDIR/
 *   NAME/              one saved game
 *     header.properties  who, where, when, the game clock and the dice
 *     db/                the saved tables, as fake database files
 *     ...                other files the save writes, such as the world
 *   .play-TIME/        the working database of a running game
 * </pre>
 */
public final class SaveSlots
{
	/** the fake database tables that hold the game's state, not its content */
	public static final String[] STATE_TABLES =
	{
		"CMCHAR", "CMCHAB", "CMCHIT", "CMCHFO", "CMCHCL",
		"CMACCT", "CMPDAT", "CMJRNL", "CMCLAN", "CMCLIT"
	};

	/** the file in a slot that describes it */
	public static final String HEADER_FILE = "header.properties";

	private static final String DB_DIR = "db";
	private static final String TABLE_PREFIX = "fakedb.data.";
	private static final String WORKING_PREFIX = ".play-";

	private SaveSlots()
	{
	}

	/**
	 * A saved game, as listed.
	 */
	public static final class Slot
	{
		/** the slot's name, which is its folder's name */
		public final String		name;
		/** what the save says about itself */
		public final Properties	header;

		Slot(final String name, final Properties header)
		{
			this.name = name;
			this.header = header;
		}

		/**
		 * Returns when it was saved, in system milliseconds.
		 * @return the time saved, or 0 if unknown
		 */
		public long savedAt()
		{
			try
			{
				return Long.parseLong(header.getProperty("saved", "0"));
			}
			catch(final NumberFormatException e)
			{
				return 0;
			}
		}
	}

	/**
	 * Returns whether the given name can name a slot: letters, digits,
	 * dashes and underscores, at most 32 of them, starting with a letter or
	 * digit.
	 * @param name the name
	 * @return true if it can
	 */
	public static boolean isValidName(final String name)
	{
		if((name == null) || (name.length() == 0) || (name.length() > 32))
			return false;
		if(!Character.isLetterOrDigit(name.charAt(0)))
			return false;
		for(int i = 0; i < name.length(); i++)
		{
			final char c = name.charAt(i);
			if((!Character.isLetterOrDigit(c)) && (c != '-') && (c != '_'))
				return false;
		}
		return true;
	}

	/**
	 * Lists the saved games, the most recently saved first.
	 * @param saveDir the folder that holds the saves
	 * @return the saves
	 */
	public static List<Slot> list(final File saveDir)
	{
		final List<Slot> slots = new ArrayList<Slot>();
		final File[] dirs = saveDir.listFiles();
		if(dirs == null)
			return slots;
		for(final File dir : dirs)
		{
			if(dir.isDirectory()
			&& isValidName(dir.getName())
			&& new File(dir, HEADER_FILE).isFile())
			{
				final Properties header = readHeader(dir);
				if(header != null)
					slots.add(new Slot(dir.getName(), header));
			}
		}
		Collections.sort(slots, new Comparator<Slot>()
		{
			@Override
			public int compare(final Slot o1, final Slot o2)
			{
				return Long.compare(o2.savedAt(), o1.savedAt());
			}
		});
		return slots;
	}

	/**
	 * Finds a saved game by name, ignoring case, or by its number in
	 * {@link #list(File)}, counting from 1.
	 * @param saveDir the folder that holds the saves
	 * @param nameOrNumber what the player typed
	 * @return the save, or null if there is none
	 */
	public static Slot find(final File saveDir, final String nameOrNumber)
	{
		final String key = (nameOrNumber == null) ? "" : nameOrNumber.trim();
		final List<Slot> slots = list(saveDir);
		for(final Slot slot : slots)
		{
			if(slot.name.equalsIgnoreCase(key))
				return slot;
		}
		try
		{
			final int n = Integer.parseInt(key);
			if((n >= 1) && (n <= slots.size()))
				return slots.get(n - 1);
		}
		catch(final NumberFormatException e)
		{
		}
		return null;
	}

	/**
	 * Describes a saved game in a line: who, where, and when it was saved.
	 * @param slot the save
	 * @return the description
	 */
	public static String describe(final Slot slot)
	{
		final Properties h = slot.header;
		final StringBuilder str = new StringBuilder(h.getProperty("character", "?"));
		final String level = h.getProperty("level", "");
		final String cls = h.getProperty("class", "");
		if((level.length() > 0) || (cls.length() > 0))
			str.append(", level ").append(level).append(' ').append(cls);
		final String place = h.getProperty("place", "");
		if(place.length() > 0)
			str.append(", in ").append(place);
		if(slot.savedAt() > 0)
			str.append(", saved ").append(new java.text.SimpleDateFormat("MMM d HH:mm").format(new Date(slot.savedAt())));
		return str.toString();
	}

	/**
	 * Reads the header of a saved game.
	 * @param saveDir the folder that holds the saves
	 * @param slot the save's name
	 * @return its header, or null if it cannot be read
	 */
	public static Properties readHeader(final File saveDir, final String slot)
	{
		return readHeader(new File(saveDir, slot));
	}

	private static Properties readHeader(final File slotDir)
	{
		final Properties header = new Properties();
		try(InputStream in = new FileInputStream(new File(slotDir, HEADER_FILE)))
		{
			header.load(in);
			return header;
		}
		catch(final IOException e)
		{
			return null;
		}
	}

	/**
	 * Reads a file a saved game wrote alongside its tables.
	 * @param saveDir the folder that holds the saves
	 * @param slot the save's name
	 * @param fileName the file's name
	 * @return its contents, or null if it is not there
	 */
	public static String readFile(final File saveDir, final String slot, final String fileName)
	{
		final File F = new File(new File(saveDir, slot), fileName);
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

	/**
	 * Makes the working database for a new game or a restored one: the
	 * content database's schema and the given tables from it, with a save's
	 * tables over them if one is given.  Tables not copied start empty.
	 * Working databases left from earlier games are deleted first, where they
	 * can be.
	 * @param contentDb the content database's folder
	 * @param saveDir the folder that holds the saves
	 * @param slot the save to restore, or null for a new game
	 * @param tables the tables to copy from the content database
	 * @return the working database's folder
	 * @throws IOException if it cannot be made
	 */
	public static File prepareWorkingDatabase(final File contentDb, final File saveDir, final String slot, final String[] tables) throws IOException
	{
		if(!saveDir.isDirectory() && !saveDir.mkdirs())
			throw new IOException("Cannot make the save folder " + saveDir.getPath());
		deleteWorkingDatabases(saveDir);
		File work = new File(saveDir, WORKING_PREFIX + System.currentTimeMillis());
		for(int i = 1; work.exists(); i++)
			work = new File(saveDir, WORKING_PREFIX + System.currentTimeMillis() + "-" + i);
		if(!work.mkdirs())
			throw new IOException("Cannot make the working database " + work.getPath());
		final File schema = new File(contentDb, "fakedb.schema");
		if(!schema.isFile())
			throw new IOException("There is no fake database in " + contentDb.getPath());
		copy(schema, new File(work, schema.getName()));
		for(final String table : tables)
		{
			final File F = new File(contentDb, TABLE_PREFIX + table);
			if(F.isFile())
				copy(F, new File(work, F.getName()));
		}
		if(slot != null)
		{
			final File savedTables = new File(new File(saveDir, slot), DB_DIR);
			final File[] saved = savedTables.listFiles();
			if(saved == null)
				throw new IOException("The save '" + slot + "' has no tables");
			for(final File F : saved)
			{
				if(F.isFile())
					copy(F, new File(work, F.getName()));
			}
		}
		return work;
	}

	/**
	 * Deletes the working databases of games that have ended, where they can
	 * be deleted.
	 * @param saveDir the folder that holds the saves
	 */
	public static void deleteWorkingDatabases(final File saveDir)
	{
		final File[] old = saveDir.listFiles();
		if(old != null)
		{
			for(final File F : old)
			{
				if(F.isDirectory() && F.getName().startsWith(WORKING_PREFIX))
					deleteTree(F);
			}
		}
	}

	/**
	 * Writes a saved game: the working database's state tables, the header,
	 * and any other files given.  It is written beside the old save of the
	 * same name, which it then replaces, so that a failed save leaves the
	 * old one whole.
	 * @param saveDir the folder that holds the saves
	 * @param slot the save's name
	 * @param workingDb the working database's folder
	 * @param header what the save says about itself
	 * @param files other files to write, by name
	 * @throws IOException if it cannot be written
	 */
	public static void write(final File saveDir, final String slot, final File workingDb,
							 final Properties header, final Map<String, String> files) throws IOException
	{
		if(!isValidName(slot))
			throw new IOException("Bad save name: " + slot);
		final File tmp = new File(saveDir, ".new-" + slot);
		deleteTree(tmp);
		final File tables = new File(tmp, DB_DIR);
		if(!tables.mkdirs())
			throw new IOException("Cannot make " + tables.getPath());
		for(final String table : STATE_TABLES)
		{
			final File F = new File(workingDb, TABLE_PREFIX + table);
			if(F.isFile())
				copy(F, new File(tables, F.getName()));
		}
		for(final Map.Entry<String, String> e : files.entrySet())
		{
			try(OutputStream out = new FileOutputStream(new File(tmp, e.getKey())))
			{
				out.write(e.getValue().getBytes("UTF-8"));
			}
		}
		try(OutputStream out = new FileOutputStream(new File(tmp, HEADER_FILE)))
		{
			header.store(out, "CoffeeSUD saved game");
		}
		final File target = new File(saveDir, slot);
		final File old = new File(saveDir, ".old-" + slot);
		deleteTree(old);
		if(target.exists() && !target.renameTo(old))
			throw new IOException("Cannot replace the save " + target.getPath());
		if(!tmp.renameTo(target))
		{
			old.renameTo(target);
			throw new IOException("Cannot write the save " + target.getPath());
		}
		deleteTree(old);
	}

	/**
	 * Deletes a saved game.
	 * @param saveDir the folder that holds the saves
	 * @param slot the save's name
	 * @return true if it is gone
	 */
	public static boolean delete(final File saveDir, final String slot)
	{
		if(!isValidName(slot))
			return false;
		final File F = new File(saveDir, slot);
		deleteTree(F);
		return !F.exists();
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

	private static void deleteTree(final File F)
	{
		if(F.isDirectory())
		{
			final File[] kids = F.listFiles();
			if(kids != null)
			{
				for(final File K : kids)
					deleteTree(K);
			}
		}
		F.delete();
	}
}
