package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.save.SaveGame;

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
 * DEVLOG (DT9): the lines the engine logs, as they are logged, for the
 * builder, in their own session, one compact coloured line each, a repeat
 * shown once with a count.  It hears every line ({@link Log.Listener}),
 * whatever the log's own settings send to mud.log, and keeps the last ones
 * for DEVLOG LAST.  The setting is the level (ERROR, WARN, INFO or DEBUG, each
 * with those above it) and a mask of modules (Scripting,Quest*).  In builder
 * mode it is kept beside the database, else the DEVLOG= ini key gives it.
 */
public final class DevLog implements Log.Listener
{
	/** the levels, most serious first */
	public enum Level
	{
		ERROR, WARN, INFO, DEBUG
	}

	/** one line heard */
	public static final class Entry
	{
		/** its level */
		public final Level	level;
		/** the module that logged it */
		public final String	module;
		/** what it said */
		public final String	message;
		/** how many times in a row */
		public int			count	= 1;

		Entry(final Level level, final String module, final String message)
		{
			this.level = level;
			this.module = module;
			this.message = message;
		}

		boolean same(final Level l, final String mod, final String msg)
		{
			return (level == l) && module.equals(mod) && message.equals(msg);
		}

		/**
		 * The entry as DEVLOG shows it.
		 * @return the line
		 */
		public String line()
		{
			final String colour = (level == Level.ERROR) ? "^r" : (level == Level.WARN) ? "^y" : (level == Level.INFO) ? "^g" : "^w";
			return colour + "[" + level.name() + " " + module + "]^N " + message + ((count > 1) ? (" ^w(x" + count + ")^N") : "");
		}
	}

	/** the file beside the database that keeps the setting in builder mode */
	public static final String		SETTING_FILE	= "devlog.txt";
	/** the module of MPDEBUG's and debug()'s lines (DT24), which the watcher is told whatever it watches */
	public static final String		DEBUG_MODULE	= "MPDEBUG";
	/** how many lines LAST can replay */
	public static final int			KEEP			= 500;

	private static final DevLog		instance		= new DevLog();
	private static final LinkedList<Entry>	recent	= new LinkedList<Entry>();
	/** the one who reads it, their level and modules */
	private static WeakReference<MOB>	watcher		= null;
	private static Level				level		= Level.WARN;
	private static String				modules		= "*";
	/** the last line told, and how many times since */
	private static Entry				lastTold	= null;
	private static volatile CharacterWindow logWindow = null;
	private static Level windowLevel = Level.WARN;
	private static String windowModules = "*";

	/** Opens a separate, display-only log view, retaining the terminal watcher. */
	public static boolean openWindow(final Level severity, final String mask)
	{
		register();
		if(!CharacterWindow.canOpen())
			return false;
		closeWindow();
		final CharacterWindow W = CharacterWindow.open("Developer log", "UTF-8", "UTF-8", () -> closeWindow());
		if(W == null)
			return false;
		W.setInputEnabled(false);
		synchronized(DevLog.class)
		{
			windowLevel = severity;
			windowModules = mask;
			logWindow = W;
			for(final String line : last(KEEP, severity, mask))
				windowLine(line);
		}
		return true;
	}

	public static synchronized void closeWindow()
	{
		final CharacterWindow W = logWindow;
		logWindow = null;
		if(W != null && !W.isClosed())
			W.close();
	}

	public static boolean hasWindow()
	{
		return logWindow != null && !logWindow.isClosed();
	}

	private static void windowLine(final String text)
	{
		final CharacterWindow W = logWindow;
		if(W != null && !W.isClosed())
		{
			try { W.output().write((CMStrings.removeColors(text) + "\r\n").getBytes("UTF-8")); }
			catch(final java.io.IOException e) { closeWindow(); }
		}
	}
	private static boolean				registered	= false;
	/** so that what telling the watcher logs is not told again */
	private static final ThreadLocal<Boolean> telling = new ThreadLocal<Boolean>();

	private DevLog()
	{
	}

	private static synchronized void register()
	{
		if(!registered)
		{
			Log.addListener(instance);
			registered = true;
		}
	}

	/**
	 * The level of a log channel.
	 * @param type the channel
	 * @return its level
	 */
	public static Level levelOf(final Log.Type type)
	{
		switch(type)
		{
		case error:
			return Level.ERROR;
		case warning:
			return Level.WARN;
		case info:
			return Level.INFO;
		default:
			return Level.DEBUG;
		}
	}

	/**
	 * Whether a module matches a mask: names separated by commas, each with *
	 * for any letters.
	 * @param mask the mask
	 * @param module the module
	 * @return true if it matches
	 */
	public static boolean matches(final String mask, final String module)
	{
		if((mask == null) || (mask.trim().length() == 0) || mask.trim().equals("*"))
			return true;
		final String mod = (module == null) ? "" : module.toUpperCase();
		for(final String part : mask.split(","))
		{
			final String p = part.trim().toUpperCase();
			if(p.length() == 0)
				continue;
			final String regex = "\\Q" + p.replace("*", "\\E.*\\Q") + "\\E";
			if(mod.matches(regex))
				return true;
		}
		return false;
	}

	@Override
	public void logged(final Log.Type type, final String module, final String message)
	{
		if(telling.get() != null)
			return;
		final Level l = levelOf(type);
		final String mod = (module == null) ? "" : module;
		final String msg = (message == null) ? "" : message.replace('\n', ' ').replace('\r', ' ').trim();
		final MOB M;
		final List<String> say = new ArrayList<String>();
		synchronized(DevLog.class)
		{
			final Entry last = recent.isEmpty() ? null : recent.getLast();
			if((last != null) && last.same(l, mod, msg))
				last.count++;
			else
			{
				recent.add(new Entry(l, mod, msg));
				while(recent.size() > KEEP)
					recent.removeFirst();
			}
			if(hasWindow() && l.ordinal() <= windowLevel.ordinal() && matches(windowModules, mod))
				windowLine(new Entry(l, mod, msg).line());
			M = (watcher == null) ? null : watcher.get();
			if((M == null) || ((!DEBUG_MODULE.equals(mod)) && ((l.ordinal() > level.ordinal()) || (!matches(modules, mod)))))
				return;
			if((lastTold != null) && lastTold.same(l, mod, msg))
			{
				lastTold.count++;
				return;
			}
			if((lastTold != null) && (lastTold.count > 1))
				say.add("^w(the last line " + (lastTold.count - 1) + " more times)^N");
			lastTold = new Entry(l, mod, msg);
			say.add(lastTold.line());
		}
		telling.set(Boolean.TRUE);
		try
		{
			for(final String s : say)
				M.tell(s);
		}
		catch(final Exception e)
		{
			// a session that cannot take the line misses it
		}
		finally
		{
			telling.remove();
		}
	}

	/**
	 * Starts telling a builder the lines logged.
	 * @param mob the builder
	 * @param newLevel the level, and those above it
	 * @param newModules the modules' mask, * for all
	 */
	public static void watch(final MOB mob, final Level newLevel, final String newModules)
	{
		register();
		synchronized(DevLog.class)
		{
			watcher = new WeakReference<MOB>(mob);
			level = (newLevel == null) ? Level.WARN : newLevel;
			modules = ((newModules == null) || (newModules.trim().length() == 0)) ? "*" : newModules.trim();
			lastTold = null;
		}
		saveSetting();
	}

	/**
	 * Stops telling the builder.
	 */
	public static void unwatch()
	{
		synchronized(DevLog.class)
		{
			watcher = null;
			lastTold = null;
		}
		saveSetting();
	}

	/**
	 * Who is told, if anyone.
	 * @return the builder, or null
	 */
	/**
	 * Whether MPDEBUG and debug() write: in builder mode and DEV=1 games, so
	 * that a line left in a normal game's script does nothing.
	 * @return true if they write
	 */
	public static boolean debugEnabled()
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode();
	}

	/**
	 * A line from MPDEBUG or debug() (DT24): "[debug where] text" to the debug
	 * log, which DEVLOG LAST keeps and the watcher is told whatever level and
	 * modules it watches.  Nothing in a normal game.
	 * @param where the host, room, file and line
	 * @param text what to write
	 */
	public static void debug(final String where, final String text)
	{
		if(!debugEnabled())
			return;
		Log.debugOut(DEBUG_MODULE, "[debug " + where + "] " + text);
	}

	public static synchronized MOB watcher()
	{
		return (watcher == null) ? null : watcher.get();
	}

	/**
	 * The setting, as DEVLOG shows it and the file keeps it: OFF, or the level
	 * and the modules.
	 * @return the setting
	 */
	public static synchronized String setting()
	{
		if(watcher() == null)
			return "OFF";
		return level.name() + " " + modules;
	}

	/**
	 * The lines heard lately that pass a level and a mask, oldest first.
	 * @param n how many at most
	 * @param atLeast the level, and those above it
	 * @param mask the modules, * for all
	 * @return the lines
	 */
	public static synchronized List<String> last(final int n, final Level atLeast, final String mask)
	{
		register();
		final List<String> out = new ArrayList<String>();
		for(final Entry E : recent)
		{
			if((E.level.ordinal() <= atLeast.ordinal()) && matches(mask, E.module))
				out.add(E.line());
		}
		while(out.size() > n)
			out.remove(0);
		return out;
	}

	/**
	 * Forgets the lines heard.
	 */
	public static synchronized void clear()
	{
		recent.clear();
		lastTold = null;
	}

	/**
	 * Starts hearing (so that LAST has lines from the start), and in builder
	 * mode, or with the DEVLOG= ini key, starts telling the builder as they
	 * were set.
	 * @param mob the builder who logged in
	 */
	public static void startFor(final MOB mob)
	{
		register();
		String set = null;
		final File dbDir = SaveGame.databaseDir();
		if(SaveGame.isBuilderMode() && (dbDir != null))
		{
			final File F = new File(dbDir, SETTING_FILE);
			if(F.exists())
			{
				try
				{
					set = new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8).trim();
				}
				catch(final IOException e)
				{
					set = null;
				}
			}
		}
		if(set == null)
			set = CMProps.instance().getStr("DEVLOG", "").trim();
		if((set.length() == 0) || set.equalsIgnoreCase("OFF"))
			return;
		final String[] parts = set.split("\\s+", 2);
		final Level L = (Level)CMath.s_valueOf(Level.class, parts[0].toUpperCase());
		synchronized(DevLog.class)
		{
			watcher = new WeakReference<MOB>(mob);
			level = (L == null) ? Level.WARN : L;
			modules = (parts.length > 1) ? parts[1].trim() : "*";
			lastTold = null;
		}
	}

	/** in builder mode, keeps the setting beside the database */
	private static void saveSetting()
	{
		final File dbDir = SaveGame.databaseDir();
		if((!SaveGame.isBuilderMode()) || (dbDir == null))
			return;
		try
		{
			Files.write(new File(dbDir, SETTING_FILE).toPath(), (setting() + "\n").getBytes(StandardCharsets.UTF_8));
		}
		catch(final IOException e)
		{
			Log.errOut("DevLog", e.getMessage());
		}
	}
}
