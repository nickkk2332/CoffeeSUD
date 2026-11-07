package com.planet_ink.coffee_mud.core.save;

import java.io.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
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
 *     header.properties  who, where, when, the game clock and the dice, and
 *                        "version" (see SaveGame.VERSION, MODULE_FORMAT_VERSION)
 *     db/                the saved tables, as fake database files
 *     world.xml          the live world (WorldState)
 *     world-manifest.properties
 *                        the world files' checksums the game had loaded
 *     modules.json       the optional modules' state, if any are on
 *     .complete          written last: each file's digest; a folder whose
 *                        files do not match it is not a complete save
 *   .play-TIME/        the working database of a running game
 *   .new-NAME/         a save being written; .old-NAME/ the one it replaces
 *                      until the swap is done (recover() repairs an
 *                      interrupted swap when the folder is next opened)
 *   .damaged-NAME-TIME/ a save found incomplete, put aside
 *   .owner.lock        held while a game owns the whole SAVEDIR
 * </pre>
 * Three unrelated numbers are called versions: SaveGame.VERSION (1) is the
 * save's header format, MODULE_FORMAT_VERSION (2) is the header version of a
 * save that carries module state (older readers refuse it), and
 * WorldState.VERSION (1) is the world.xml's own format.
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

	/**
	 * The header "version" of a save that carries module state (modules.json);
	 * saves without modules stay at SaveGame.VERSION (1).  Saves carrying module
	 * state require a reader that understands its lifecycle.  Not the same
	 * number as SaveGame.VERSION or WorldState.VERSION.
	 */
	public static final int MODULE_FORMAT_VERSION = 2;

	private static final String DB_DIR = "db";
	private static final String TABLE_PREFIX = "fakedb.data.";
	private static final String WORKING_PREFIX = ".play-";
	private static final String COMPLETE_FILE = ".complete";
	private static final Map<String, FileChannel> owners = new HashMap<String, FileChannel>();
	private static final Set<String> activeWorking = new HashSet<String>();
	static
	{
		Runtime.getRuntime().addShutdownHook(new Thread(() -> closeAll(), "save-folder-locks"));
	}

	/** Own the save folder for the lifetime of this process, including its startup menu. */
	public static synchronized void acquire(final File saveDir) throws IOException
	{
		final String key = saveDir.getCanonicalPath();
		if(owners.containsKey(key))
			return;
		if(!saveDir.isDirectory() && !saveDir.mkdirs())
			throw new IOException("Cannot make the save folder " + key);
		final FileChannel channel = FileChannel.open(new File(saveDir, ".owner.lock").toPath(), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		try
		{
			if(channel.tryLock() == null)
				throw new IOException("Another game is using the save folder " + key + ". Close it or choose a different SAVEDIR.");
			owners.put(key, channel);
		}
		catch(final IOException | OverlappingFileLockException e)
		{
			channel.close();
			throw new IOException(e.getMessage(), e);
		}
	}

	/** Called only after the database has closed, including an in-process restore. */
	public static synchronized void releaseWorkingDatabase(final File work)
	{
		if(work == null)
			return;
		try { activeWorking.remove(work.getCanonicalPath()); }
		catch(final IOException e) { return; }
		deleteTree(work);
	}

	/** Release ownership after the game has closed its database. Never unlink the lock file. */
	public static synchronized void release(final File saveDir)
	{
		try
		{
			final String key = saveDir.getCanonicalPath();
			if(!owners.containsKey(key))
				return;
			for(final String path : new ArrayList<String>(activeWorking))
				if(new File(path).getParentFile().getCanonicalPath().equals(key))
					releaseWorkingDatabase(new File(path));
			owners.remove(key).close();
		}
		catch(final IOException e)
		{
			// The OS also releases the advisory lock when the process exits.
		}
	}

	private static synchronized void closeAll()
	{
		for(final FileChannel channel : owners.values())
			try { channel.close(); } catch(final IOException e) { }
		owners.clear();
	}

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
	public static synchronized List<Slot> list(final File saveDir)
	{
		try { acquire(saveDir); recover(saveDir); } catch(final IOException e) { /* read-only listing if another process owns it */ }
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
		final String party = h.getProperty("party", "");
		if(party.length() > 0)
			str.append(", with ").append(party);
		final String place = h.getProperty("place", "");
		if(place.length() > 0)
			str.append(", in ").append(place);
		if(slot.savedAt() > 0)
			str.append(", saved ").append(new java.text.SimpleDateFormat("MMM d HH:mm").format(new Date(slot.savedAt())));
		if("true".equalsIgnoreCase(h.getProperty("dev", "")))
			str.append(" [dev]");
		if("true".equalsIgnoreCase(h.getProperty("cheat", "")))
			str.append(" [cheat]"); // P10
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
		catch(final IOException | IllegalArgumentException e)
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
	public static synchronized File prepareWorkingDatabase(final File contentDb, final File saveDir, final String slot, final String[] tables) throws IOException
	{
		acquire(saveDir);
		recover(saveDir);
		if(slot != null)
			validateFormat(saveDir, slot);
		deleteWorkingDatabases(saveDir);
		File work = new File(saveDir, WORKING_PREFIX + System.currentTimeMillis());
		for(int i = 1; work.exists(); i++)
			work = new File(saveDir, WORKING_PREFIX + System.currentTimeMillis() + "-" + i);
		if(!work.mkdirs())
			throw new IOException("Cannot make the working database " + work.getPath());
		synchronized(SaveSlots.class) { activeWorking.add(work.getCanonicalPath()); }
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
	public static synchronized void deleteWorkingDatabases(final File saveDir)
	{
		try { acquire(saveDir); } catch(final IOException e) { return; }
		final File[] old = saveDir.listFiles();
		if(old != null)
		{
			for(final File F : old)
			{
				try
				{
					if(F.isDirectory() && F.getName().startsWith(WORKING_PREFIX)
					&& !activeWorking.contains(F.getCanonicalPath())) deleteTree(F);
				}
				catch(final IOException e) { /* An uncertain owner is never removed. */ }
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
	public static synchronized void write(final File saveDir, final String slot, final File workingDb,
							 final Properties header, final Map<String, String> files) throws IOException
	{
		if(!isValidName(slot))
			throw new IOException("Bad save name: " + slot);
		acquire(saveDir);
		recover(saveDir);
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
			try(FileOutputStream out = new FileOutputStream(new File(tmp, e.getKey())))
			{
				out.write(e.getValue().getBytes("UTF-8"));
				out.getFD().sync();
			}
		}
		try(FileOutputStream out = new FileOutputStream(new File(tmp, HEADER_FILE)))
		{
			header.store(out, "CoffeeSUD saved game");
			out.getFD().sync();
		}
		final Properties complete = new Properties();
		for(final File file : snapshotFiles(tmp))
			complete.setProperty(tmp.toPath().relativize(file.toPath()).toString().replace(File.separatorChar,'/'), digest(file));
		try(FileOutputStream out = new FileOutputStream(new File(tmp, COMPLETE_FILE)))
		{
			complete.store(out, "Complete snapshot: SHA-256 per file");
			out.getFD().sync();
		}
		syncDirectory(tables);
		syncDirectory(tmp);
		final File target = new File(saveDir, slot);
		final File old = new File(saveDir, ".old-" + slot);
		deleteTree(old);
		if(target.exists() && !target.renameTo(old))
			throw new IOException("Cannot replace the save " + target.getPath());
		syncDirectory(saveDir);
		if(!tmp.renameTo(target))
		{
			old.renameTo(target);
			throw new IOException("Cannot write the save " + target.getPath());
		}
		syncDirectory(saveDir);
		// Keep the previous complete generation until the next successful write.
	}

	/**
	 * Deletes a saved game.
	 * @param saveDir the folder that holds the saves
	 * @param slot the save's name
	 * @return true if it is gone
	 */
	public static synchronized boolean delete(final File saveDir, final String slot)
	{
		if(!isValidName(slot))
			return false;
		try { acquire(saveDir); recover(saveDir); } catch(final IOException e) { return false; }
		final File F = new File(saveDir, slot);
		deleteTree(F);
		deleteTree(new File(saveDir, ".old-" + slot));
		deleteTree(new File(saveDir, ".new-" + slot));
		return !F.exists();
	}

	private static void copy(final File from, final File to) throws IOException
	{
		try(InputStream in = new FileInputStream(from); FileOutputStream out = new FileOutputStream(to))
		{
			final byte[] buf = new byte[65536];
			int n;
			while((n = in.read(buf)) > 0)
				out.write(buf, 0, n);
			out.getFD().sync();
		}
	}

	private static List<File> snapshotFiles(final File root)
	{
		final List<File> files = new ArrayList<File>();
		final File[] children = root.listFiles();
		if(children != null)
			for(final File child : children)
				if(child.isDirectory()) files.addAll(snapshotFiles(child));
				else if(!child.getName().equals(COMPLETE_FILE)) files.add(child);
		return files;
	}

	private static String digest(final File file) throws IOException
	{
		try
		{
			final MessageDigest digest = MessageDigest.getInstance("SHA-256");
			try(InputStream in = new FileInputStream(file))
			{
				final byte[] bytes = new byte[65536]; int n;
				while((n = in.read(bytes)) > 0) digest.update(bytes, 0, n);
			}
			final StringBuilder result = new StringBuilder();
			for(final byte b : digest.digest()) result.append(String.format("%02x", b & 255));
			return result.toString();
		}
		catch(final NoSuchAlgorithmException e) { throw new IOException(e); }
	}

	private static boolean complete(final File dir)
	{
		final Properties header = readHeader(dir);
		if(header == null || header.isEmpty() || !new File(dir, DB_DIR).isDirectory() || !new File(dir, "world.xml").isFile())
			return false;
		final File marker = new File(dir, COMPLETE_FILE);
		if(!marker.isFile()) return true; // Legacy version-1 snapshots predate the manifest.
		try(InputStream in = new FileInputStream(marker))
		{
			final Properties expected = new Properties(); expected.load(in);
			if(expected.isEmpty()) return false;
			// Every file the manifest lists must be there unchanged. A file it does not list (a
			// desktop's .DS_Store, a sync tool's marker) is not part of the save, and must not make a
			// complete save look damaged, which would roll it back to the previous generation.
			for(final String name : expected.stringPropertyNames())
			{
				final File file = new File(dir, name.replace('/', File.separatorChar));
				if(!file.isFile() || !digest(file).equals(expected.getProperty(name))) return false;
			}
			return true;
		}
		catch(final IOException | IllegalArgumentException e) { return false; }
	}

	/** Repair interrupted renames under exclusive folder ownership. Prefer the last public/old generation. */
	public static synchronized void recover(final File saveDir) throws IOException
	{
		acquire(saveDir);
		final File[] dirs = saveDir.listFiles();
		if(dirs == null) return;
		final Set<String> names = new TreeSet<String>();
		for(final File dir : dirs)
			if(dir.getName().startsWith(".old-") || dir.getName().startsWith(".new-"))
				names.add(dir.getName().substring(5));
		for(final String name : names)
		{
			if(!isValidName(name)) continue;
			final File target = new File(saveDir, name), old = new File(saveDir, ".old-" + name), next = new File(saveDir, ".new-" + name);
			if(complete(target)) continue;
			// With no public generation left (a crash between the two renames), the new generation,
			// written in full, is the save the player asked for; otherwise the last public one.
			final boolean nextComplete = new File(next, COMPLETE_FILE).isFile() && complete(next);
			final File candidate = ((!target.exists()) && nextComplete) ? next : complete(old) ? old : (nextComplete ? next : null);
			if(candidate == null) continue;
			if(target.exists())
			{
				final File damaged = new File(saveDir, ".damaged-" + name + "-" + System.currentTimeMillis());
				if(!target.renameTo(damaged)) throw new IOException("Cannot preserve damaged save " + name);
			}
			if(!candidate.renameTo(target)) throw new IOException("Cannot recover save " + name);
			syncDirectory(saveDir);
		}
	}

	/** Validate compatibility before replacing any live state. Version-less legacy data is version 1. */
	public static void validateFormat(final File saveDir, final String slot) throws IOException
	{
		final File dir = new File(saveDir, slot);
		if(!complete(dir)) throw new IOException("The save '" + slot + "' is incomplete or damaged.");
		final Properties header = readHeader(dir);
        final String format = header.getProperty("version", "1");
        if(!format.equals("1") && !format.equals(Integer.toString(MODULE_FORMAT_VERSION)))
            throw new IOException("Unsupported save format " + format + " in '" + slot + "'. This engine supports versions 1 and 2.");
        if(format.equals(Integer.toString(MODULE_FORMAT_VERSION)) && (!"1".equals(header.getProperty("moduleformat")) || !new File(dir, "modules.json").isFile()))
            throw new IOException("The module-aware save '" + slot + "' is missing its module metadata.");
		final String world = readFile(saveDir, slot, "world.xml");
		if(world == null || !world.trim().matches("(?is)<WORLD(?:\\s[^>]*)?(?:/>|>.*</WORLD\\s*>)"))
			throw new IOException("The world snapshot in '" + slot + "' is incomplete or damaged.");
		final java.util.regex.Matcher version = java.util.regex.Pattern.compile("(?i)<WORLD\\b[^>]*\\bVERSION\\s*=\\s*[\"']([^\"']+)[\"']").matcher(world);
		if(version.find() && !version.group(1).equals("1"))
			throw new IOException("Unsupported world snapshot version " + version.group(1) + " in '" + slot + "'.");
	}

	private static void syncDirectory(final File dir)
	{
		try(FileChannel channel = FileChannel.open(dir.toPath(), StandardOpenOption.READ)) { channel.force(true); }
		catch(final IOException | UnsupportedOperationException e) { /* Directory fsync is not supported on every platform. */ }
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
