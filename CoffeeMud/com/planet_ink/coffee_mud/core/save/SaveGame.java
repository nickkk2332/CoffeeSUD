package com.planet_ink.coffee_mud.core.save;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.threads.ServiceEngine;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.*;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

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
 * Saving and restoring single-player games.  See {@link SaveSlots} for what
 * a save holds on disk, and {@link WorldState} for the live world in it.
 *
 * The launcher picks what the first boot plays: a new game or a save.  Before
 * each boot, {@link #beforeBoot(String)} makes the working database and sets
 * the game clock; after it, {@link #afterBoot(Session)} has the world's clocks
 * read their dates, restores the dice, and logs the saved character in.
 * Restoring during play asks the engine to restart in the same process, which
 * boots again with the chosen save.
 *
 * The content database is a cache of what is built from the world files (see
 * {@link WorldFiles}).  A normal game plays on a copy of its content tables,
 * or, when the files have changed, on a database built from them.  In builder
 * mode the game runs on the cache itself, makes the player an Archon, and has
 * no saves; what the builder changes goes back to the world files.
 */
public final class SaveGame
{
	/** the version of the save format this writes */
	public static final int VERSION = 1;

	private static final String FAKEDB = "jdbc:fakedb:";

	private static volatile boolean		builderMode	= false;
	private static volatile File		saveDir		= new File("saves");
	/** the save the next boot restores, or null for a new game */
	private static volatile String		nextBoot	= null;
	/** the save this game was restored from or last saved to */
	private static volatile String		currentSlot	= null;
	/** the working database of this game, or null if saves are off */
	private static volatile File			workingDb	= null;
	/** the folder of the fake database this game plays on, or null if it is not one */
	private static volatile File			databaseDir	= null;
	/** the header of the save this boot restored, until play starts */
	private static volatile Properties	restoring	= null;
	/** ST6: NEWGAME or RESTORE, for the first login after the game starts */
	private static volatile String	bootEvent	= null;
	private static final Map<String, String> bootOverrides = new Hashtable<String, String>();

	/**
	 * The game's save rules, set when a new game starts (SAVEMODE) and kept
	 * in its saves.
	 */
	public static enum SaveMode
	{
		/** saving anywhere, at any time */
		ANYWHERE,
		/** not while the party is fighting */
		NOTINCOMBAT,
		/** only at save points (Prop_SavePoint), and not while fighting */
		SAVEPOINTS,
		/** one save, which the game keeps when the player quits */
		IRONMAN
	}

	private static volatile SaveMode saveMode = SaveMode.ANYWHERE;

	/** FRESH= of the next restore: CHANGED, ALL, or area names */
	private static volatile String nextFresh = null;
	/** what changed in the world since the restoring save was made, told at login */
	private static final List<String> worldReport = new ArrayList<String>();

	/**
	 * Sets which areas the next restore takes from the world as it is now,
	 * rather than from the save: CHANGED (those changed since the save), ALL,
	 * or a list of area names.
	 * @param fresh the spec, or null for none
	 */
	public static void setNextFresh(final String fresh)
	{
		nextFresh = ((fresh == null) || (fresh.trim().length() == 0)) ? null : fresh.trim();
	}

	/**
	 * Notes something that changed in the world since the save being restored
	 * was made, for the report the player gets on coming in.
	 * @param line what changed
	 */
	public static void noteWorldChange(final String line)
	{
		synchronized(worldReport)
		{
			if(worldReport.size() < 60)
				worldReport.add(line);
			else
			if(worldReport.size() == 60)
				worldReport.add(text("... and more."));
		}
	}

	/** the AUTOSAVE ticker of this game, if any */
	private static volatile SaveTools.AutoSaver autoSaver = null;

	/** true while a restored game boots, until the saved world is put back */
	private static volatile boolean worldPending = false;

	/**
	 * Returns whether a restored game is still booting: the world is not yet
	 * as it was saved, so scripts wait, rather than run what they already
	 * ran.
	 * @return true until the saved world is back
	 */
	public static boolean isWorldPending()
	{
		return worldPending;
	}

	private SaveGame()
	{
	}

	/**
	 * Sets where saves go, and whether this is builder mode.  The launcher
	 * calls this before the engine starts.
	 * @param dir the folder for saves
	 * @param builder true for builder mode
	 */
	public static void configure(final File dir, final boolean builder)
	{
		saveDir = dir;
		builderMode = builder;
	}

	/** whether this is a developer's game (DEV=1): a normal game, with saves, and powers to test with */
	private static volatile boolean devMode = false;

	/**
	 * Sets whether this is a developer's game (DEV=1): each character has the
	 * developer security group (DEVGROUP, DEV by default) without a change
	 * of class, and saves are marked [dev].  Not in builder mode, which has
	 * every power already.
	 * @param dev true for a developer's game
	 */
	public static void setDevMode(final boolean dev)
	{
		devMode = dev;
	}

	/**
	 * Returns whether this is a developer's game (DEV=1); scripts ask with ISDEV().
	 * @return true if it is
	 */
	public static boolean isDevMode()
	{
		return devMode && (!builderMode);
	}

	/**
	 * Returns the name of the developer security group: DEVGROUP, without
	 * any GROUP_ before it, or DEV.
	 * @return the group's name
	 */
	public static String devGroup()
	{
		String group = CMProps.instance().getStr("DEVGROUP", "").trim().toUpperCase();
		if(group.startsWith("GROUP_"))
			group = group.substring(6);
		return (group.length() == 0) ? "DEV" : group;
	}

	/**
	 * Gives a character the developer security group in a developer's game,
	 * and takes it away in any other, so that a save made in one does not
	 * keep the powers in a normal game.
	 * @param mob the character
	 */
	private static void applyDevGroup(final MOB mob)
	{
		if(mob.playerStats() == null)
			return;
		final String group = devGroup();
		final List<String> flags = CMParms.parseSemicolons(mob.playerStats().getSetSecurityFlags(null), true);
		boolean has = false;
		for(final Iterator<String> i = flags.iterator(); i.hasNext();)
		{
			if(i.next().trim().equalsIgnoreCase(group))
			{
				has = true;
				if(!isDevMode())
					i.remove();
			}
		}
		if(isDevMode() && (!has))
		{
			flags.add(group);
			mob.playerStats().getSetSecurityFlags(CMParms.combineWith(flags, ';'));
			mob.tell(CMLib.lang().L("Developer's game (DEV=1): you have the @x1 security group, to test with, and saves are marked [dev].", group));
		}
		else
		if((!isDevMode()) && has)
			mob.playerStats().getSetSecurityFlags(CMParms.combineWith(flags, ';'));
	}

	/** whether PUZZLE CHECK has run in this game (DT33) */
	private static volatile boolean puzzleChecked = false;

	/**
	 * Returns whether the game runs in builder mode, on the content database.
	 * @return true in builder mode
	 */
	public static boolean isBuilderMode()
	{
		return builderMode;
	}

	/**
	 * Returns whether this game can be saved.
	 * @return true if it can
	 */
	public static boolean isAvailable()
	{
		return (!builderMode) && (workingDb != null);
	}

	/**
	 * Returns the folder of the fake database this game plays on: the
	 * content's in builder mode, else this game's working copy.
	 * @return the folder, or null if the database is not a fake one
	 */
	public static File databaseDir()
	{
		return databaseDir;
	}

	/**
	 * Returns the folder that holds the saves.
	 * @return the folder
	 */
	public static File saveDir()
	{
		return saveDir;
	}

	/**
	 * Sets what the next boot plays.
	 * @param slot the save to restore, or null for a new game
	 */
	public static void setNextBoot(final String slot)
	{
		nextBoot = slot;
	}

	/**
	 * Returns the save this game was restored from or last saved to.
	 * @return its name, or null if it has not been saved yet
	 */
	public static String currentSlot()
	{
		return currentSlot;
	}

	/**
	 * Returns the settings the next boot uses in place of the ini file's:
	 * the working database.
	 * @return the settings, by name
	 */
	public static Map<String, String> bootOverrides()
	{
		return bootOverrides;
	}

	/**
	 * Returns whether this boot restores a save.  True from the boot until
	 * play starts.
	 * @return true while restoring
	 */
	public static boolean isRestoring()
	{
		return restoring != null;
	}

	/**
	 * Gets ready to boot: makes the working database, from the content and,
	 * when restoring, the save's tables, and sets the game clock, the time
	 * mode and the pause the save had.
	 * @param contentDbService the DBSERVICE setting, naming the content
	 */
	public static void beforeBoot(final String contentDbService)
	{
		bootOverrides.clear();
		saveMode = SaveMode.ANYWHERE;
		if(autoSaver != null)
		{
			autoSaver.stop();
			autoSaver = null;
		}
		worldPending = false;
		workingDb = null;
		databaseDir = null;
		restoring = null;
		final String slot = nextBoot;
		nextBoot = null;
		final String fresh = nextFresh;
		nextFresh = null;
		synchronized(worldReport)
		{
			worldReport.clear();
		}
		WorldState.setFresh(null, null);
		final boolean fakeDb = (contentDbService != null) && contentDbService.startsWith(FAKEDB);
		if(!fakeDb)
		{
			System.out.println("The world files and saved games need the fake database, so they are off.");
			Log.errOut("SaveGame", "The world files and saved games need a fake database, but DBSERVICE is " + contentDbService);
			WorldFiles.beginBoot(false, null);
			return;
		}
		// the content database is a cache of what is built from the world files
		final File cache = new File(contentDbService.substring(FAKEDB.length()));
		final boolean current = WorldFiles.isCacheCurrent(cache);
		if(builderMode)
		{
			// builder mode plays on the cache, and keeps its characters there
			databaseDir = cache;
			if(!current)
				WorldFiles.clearContentTables(cache);
			WorldFiles.beginBoot(!current, null);
			return;
		}
		try
		{
			workingDb = SaveSlots.prepareWorkingDatabase(cache, saveDir, slot, current ? WorldFiles.CONTENT_TABLES : new String[0]);
		}
		catch(final IOException e)
		{
			System.out.println("Saved games are off: " + e.getMessage());
			Log.errOut("SaveGame", e);
			databaseDir = cache;
			if(!current)
				WorldFiles.clearContentTables(cache);
			WorldFiles.beginBoot(!current, null);
			return;
		}
		databaseDir = workingDb;
		bootOverrides.put("DBSERVICE", FAKEDB + workingDb.getPath());
		WorldFiles.beginBoot(!current, cache);
		if(slot == null)
		{
			currentSlot = null;
			return;
		}
		final Properties header = SaveSlots.readHeader(saveDir, slot);
		if(header == null)
			return;
		currentSlot = slot;
		restoring = header;
		worldPending = true;
		compareWorlds(header, fresh);
		GameTime.setTurnBased("TURNS".equalsIgnoreCase(header.getProperty("timemode")));
		TimeLock.restore(header.getProperty("timemodebefore")); // W28: the mode a zone took over from
		saveMode = parseSaveMode(header.getProperty("savemode", "ANYWHERE"));
		if("true".equalsIgnoreCase(header.getProperty("paused")))
			GameTime.pause();
		final long gameTime = parseLong(header.getProperty("gametime"), 0);
		if(gameTime > 0)
			GameTime.set(gameTime);
		Party.setMeetWhereLeft("true".equalsIgnoreCase(header.getProperty("partymeet")));
		Party.setTurns("ALL".equalsIgnoreCase(header.getProperty("partyturns")) ? Party.Turns.ALL : Party.Turns.LEADER);
		Party.setOrder(header.getProperty("partyorder", ""));
		Party.setOnTheirOwn(header.getProperty("partyalone", ""));
	}

	/**
	 * Called when the console session is ready and before it logs in: has
	 * the world's clocks read their dates and, when restoring, puts the live
	 * world back as it was saved, then the dice, and has the session log the
	 * saved character in.
	 * @param S the console session
	 */
	public static void afterBoot(final Session S)
	{
		runOnGameThread(new Runnable()
		{
			@Override
			public void run()
			{
				loadClocks();
				// what the world's files gave, before a save adds to it
				WorldFiles.loadSpace();
				WorldState.noteContentQuests();
				if(!isBuilderMode())
					WorldState.noteContentShapes();
				// ST6: world/js/init.js read afresh, and its onBoot()
				WorldJS.reset();
				JSProgression.reset(); // JS23
				QuestTimers.startTicking(); // Q11: deadlines
				WorldJS.call("onBoot");
			}
		});
		final Properties header = restoring;
		// ST6: the world host of the game before is gone; GAME_PROG NEWGAME or RESTORE at the first login; the turns
		WorldHost.forget();
		bootEvent = (header == null) ? "NEWGAME" : "RESTORE";
		ScriptEvents.setTurns((header == null) ? 0 : parseLong(header.getProperty("turns"), 0));
		GameDifficulty.restore((header == null) ? null : header.getProperty("difficulty")); // C13
		Exchange.restore((header == null) ? null : header.getProperty("exchange")); // W29
		GenSeed.restore((header == null) ? null : header.getProperty("genseed")); // G1: SEED=GAME's
		if(header == null)
			return;
		restoring = null;
		// after the boot, which reads the PLAYERKILL setting, and before anyone logs in
		Party.setFightEachOther("true".equalsIgnoreCase(header.getProperty("partyfight")));
		final String world = SaveSlots.readFile(saveDir, currentSlot, WorldState.FILE);
		if(world != null)
		{
			runOnGameThread(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						WorldState.apply(world);
					}
					finally
					{
						worldPending = false;
					}
				}
			});
		}
		worldPending = false;
		// last, so that play rolls on exactly where the saved game left off
		final String dice = header.getProperty("dice");
		if(dice != null)
			CMath.setRandState(parseLong(dice, 0));
		final String name = header.getProperty("character", "");
		if(name.length() > 0)
			S.setStat("LOGIN_ACCOUNT", name);
	}

	/**
	 * Called when a player enters the game.  After a restore, puts back what
	 * the database does not keep: their hit points and timed effects, and
	 * the timed effects on the rest of the world.
	 * @param mob the player
	 */
	public static void playerLoggedIn(final MOB mob)
	{
		applyDevGroup(mob);
		// a character coming into the world can act at once, rather than only
		// after its first tick: otherwise the first command that takes time
		// waits, and what is typed after it, such as the answer to its
		// question, is taken for the next command
		if(mob.actions() < mob.phyStats().speed())
			mob.setActions(mob.phyStats().speed());
		WorldState.finishRestore(mob);
		final List<String> report;
		synchronized(worldReport)
		{
			report = new ArrayList<String>(worldReport);
			worldReport.clear();
		}
		if(report.size() > 0)
		{
			final StringBuilder str = new StringBuilder(CMLib.lang().L("This save was made with another version of the world:\n\r"));
			for(final String line : report)
				str.append("  ").append(line).append("\n\r");
			str.append(CMLib.lang().L("RESTORE name FRESH=CHANGED takes the changed areas' rooms as they are now.\n\r"));
			mob.tell(str.toString());
		}
		if(builderMode || isDevMode())
			DevLog.startFor(mob); // DEVLOG as it was left, or as the ini's DEVLOG= says
		InputRecord.startFor(mob); // RECORDINPUT=1 (DT25)
		if(builderMode)
		{
			// the world's quests read through at the start (DT32)
			final int questProblems = QuestCheck.checkWorld().size();
			mob.tell(CMLib.lang().L("QUESTCHECK: @x1 problems in world/quests@x2.", "" + questProblems, (questProblems > 0) ? " (QUESTCHECK ALL lists them)" : ""));
		}
		// the world's scripts' rooms, doors and things, once the world is whole (DT33): to mud.log
		if(!puzzleChecked)
		{
			puzzleChecked = true;
			final List<String> puzzles = PuzzleCheck.checkAll();
			for(final String p : puzzles)
				Log.warnOut("PuzzleCheck", p);
			if(builderMode && (puzzles.size() > 0))
				mob.tell(CMLib.lang().L("PUZZLE CHECK: @x1 problems in the world's scripts (PUZZLE CHECK lists them).", "" + puzzles.size()));
		}
		if(builderMode)
		{
			makeArchon(mob);
			final String problems = WorldFiles.problemSummary(10);
			if(problems.length() > 0)
				mob.tell(problems.replace("\n", "\n\r"));
			for(final String line : builderSummary())
				mob.tell(line);
		}
		else
			startAutoSaver(mob);
		// ST6: the world's scripts, then the game's start, once
		WorldHost.start(mob);
		ScriptEvents.moved(mob, null, mob.location()); // ST16: into the area, from nowhere
		final String ev = bootEvent;
		bootEvent = null;
		if(ev != null)
		{
			if(builderMode)
				WorldJS.call("onBuilderStart", mob);
			ScriptEvents.game(ev, mob, ev.equals("RESTORE") ? currentSlot : "");
		}
		WorldJS.call("onPlayerEnter", mob);
	}

	/**
	 * ST6: a character was made, after it entered the world: GAME_PROG
	 * NEWCHAR.
	 * @param mob the character
	 */
	public static void characterCreated(final MOB mob)
	{
		JSProgression.call("onCreated", mob); // JS23
		ScriptEvents.game("NEWCHAR", mob, "");
	}

	/**
	 * B13: the lines builder mode's login ends with: whether the world files
	 * loaded whole, what was changed outside the game or is unsaved, and
	 * where the way of building here is told.
	 * @return the lines
	 */
	public static List<String> builderSummary()
	{
		final List<String> lines = new ArrayList<String>();
		if(WorldFiles.problems().isEmpty())
			lines.add(CMLib.lang().L("The world files loaded whole."));
		final int outside = WorldFiles.changedOutside().size();
		if(outside > 0)
			lines.add(CMLib.lang().L("@x1 world files were changed outside the game since it loaded them: WORLD STATUS lists them.", "" + outside));
		final int unsaved = Unsaved.count();
		if(unsaved > 0)
			lines.add(CMLib.lang().L("@x1 rooms have unsaved mobs or items: LIST UNSAVED lists them.", "" + unsaved));
		lines.add(CMLib.lang().L("AHELP BUILDING tells how building works here: what SAVE ROOM keeps, the world folder, and the checks."));
		return lines;
	}

	/**
	 * Starts this game's AUTOSAVE, once, for the player: every so many game
	 * minutes (AUTOSAVE=30), or on going into another area
	 * (AUTOSAVE=ON-AREA-CHANGE), in AUTOSAVEKEEP saves that take turns.
	 * @param mob the player
	 */
	private static void startAutoSaver(final MOB mob)
	{
		final String setting = CMProps.instance().getStr("AUTOSAVE", "").trim();
		if((setting.length() == 0) || setting.equals("0") || (workingDb == null) || (Party.isBackground(mob)))
			return;
		synchronized(SaveGame.class)
		{
			if(autoSaver != null)
				autoSaver.stop();
			autoSaver = new SaveTools.AutoSaver(mob, setting, CMath.s_int(CMProps.instance().getStr("AUTOSAVEKEEP", "3")));
			CMLib.threads().startTickDown(autoSaver, Tickable.TICKID_MISCELLANEOUS, 1);
		}
	}

	/**
	 * In builder mode, makes the player an Archon, with the in-game editors,
	 * as reading the Archon's manual does: by having them read one.
	 * @param mob the player
	 */
	private static void makeArchon(final MOB mob)
	{
		final CharClass current = mob.baseCharStats().getCurrentClass();
		if((current != null) && current.ID().equals("Archon"))
			return;
		final Item manual = CMClass.getMiscMagic("ManualArchon");
		if(manual == null)
			return;
		mob.tell(CMLib.lang().L("Builder mode makes you an Archon, with the in-game editors."));
		mob.addItem(manual);
		manual.executeMsg(mob, CMClass.getMsg(mob, manual, null, CMMsg.MSG_READ, null));
		if(!manual.amDestroyed())
			manual.destroy();
	}

	/**
	 * In a normal game, tells a builder that what they are about to change
	 * may not last: the game plays on a copy of the world, and its saves keep
	 * what is in the rooms, not the rooms themselves.
	 * @param mob the builder
	 */
	public static void warnAboutBuilding(final MOB mob)
	{
		if(CMProps.isSinglePlayer() && (!builderMode) && (workingDb != null))
		{
			mob.tell(CMLib.lang().L("^HThis game plays on a copy of the world.^?  Mobs, items, rooms, exits and areas "
				+ "you make, change or destroy stay that way in this game and its saves, but not in the world, which "
				+ "other games start from.  The editors of the game's data files (help, socials, achievements, titles, "
				+ "awards, factions, holidays, expertises, components and planes) are refused, as what they write "
				+ "every game reads.  Start CoffeeSUD with BUILDER=1 to change the world itself."));
		}
	}

	/** the editors that write the game's data files, in resources/, which every game reads */
	private static final Set<String> DATA_FILE_EDITORS = new HashSet<String>(Arrays.asList(
		"HELP", "AHELP", "ACHIEVEMENT", "TITLE", "AWARD", "FACTION", "SOCIAL", "HOLIDAY", "EXPERTISE", "COMPONENT", "PLANE"));

	/**
	 * In a normal game, refuses an editor of the game's data files (CREATE,
	 * MODIFY or DESTROY of HELP, SOCIAL, ACHIEVEMENT and the like), and says
	 * why: it writes files in resources/ that every game reads, which a save
	 * does not keep.  Builder mode writes them to the world's own copies.
	 * @param mob the builder
	 * @param what what is to be made, changed or destroyed, in upper case
	 * @return true if refused
	 */
	public static boolean refusesDataFileEditor(final MOB mob, final String what)
	{
		if(CMProps.isSinglePlayer() && (!builderMode) && (workingDb != null) && DATA_FILE_EDITORS.contains(what))
		{
			mob.tell(text("^HThe @x1 editor is refused in a normal game:^? it writes the game's data files, which every "
				+ "game reads and a save does not keep.  Start CoffeeSUD with BUILDER=1 to change the world's own "
				+ "copies of them.", what.toLowerCase()));
			return true;
		}
		return false;
	}

	/**
	 * Returns a message in the player's language, or as written while the
	 * game boots, before the language library is up.
	 * @param msg the message, with @x1 and the like
	 * @param xs what goes in it
	 * @return the message
	 */
	private static String text(final String msg, final String... xs)
	{
		if(CMLib.lang() != null)
			return CMLib.lang().L(msg, xs);
		String s = msg;
		for(int i = 0; i < xs.length; i++)
			s = s.replace("@x" + (i + 1), xs[i]);
		return s;
	}

	/**
	 * Compares the world a save was made with to the world this game boots
	 * from: notes the area files changed, added and gone for the player's
	 * report, and sets the areas a FRESH= restore takes as they are now.
	 * @param header the save's header
	 * @param fresh the FRESH= spec, or null
	 */
	private static void compareWorlds(final Properties header, final String fresh)
	{
		final String savedSum = header.getProperty("worldsum", "");
		final Map<String, String> saved = new TreeMap<String, String>();
		for(final String pair : header.getProperty("areasums", "").split(";"))
		{
			final int x = pair.indexOf('=');
			if(x > 0)
				saved.put(pair.substring(0, x), pair.substring(x + 1));
		}
		final Map<String, String> now = WorldFiles.bootAreaSums();
		final Set<String> changed = new TreeSet<String>();
		if((savedSum.length() > 0) && (!savedSum.equals(WorldFiles.bootChecksum())))
		{
			noteWorldChange(text("The world has changed since this game was saved."));
			for(final Map.Entry<String, String> e : now.entrySet())
			{
				final String was = saved.get(e.getKey());
				if(was == null)
				{
					changed.add(e.getKey());
					noteWorldChange(text("New area file: @x1", e.getKey()));
				}
				else
				if(!was.equals(e.getValue()))
				{
					changed.add(e.getKey());
					noteWorldChange(text("Changed area file: @x1", e.getKey()));
				}
			}
			for(final String file : saved.keySet())
			{
				if(!now.containsKey(file))
					noteWorldChange(text("Area file gone: @x1", file));
			}
		}
		if(fresh != null)
		{
			if(fresh.equalsIgnoreCase("ALL"))
				WorldState.setFresh(null, Boolean.TRUE);
			else
			if(fresh.equalsIgnoreCase("CHANGED"))
				WorldState.setFresh(changed, null);
			else
			{
				final Set<String> names = new TreeSet<String>(String.CASE_INSENSITIVE_ORDER);
				for(final String name : CMParms.parseCommas(fresh, true))
					names.add(name);
				WorldState.setFresh(names, null);
			}
		}
	}

	/**
	 * Returns the game's save rules.
	 * @return the save mode
	 */
	public static SaveMode saveMode()
	{
		return saveMode;
	}

	/**
	 * Sets the game's save rules, as a new game does from its SAVEMODE
	 * setting.
	 * @param mode the save mode
	 */
	public static void setSaveMode(final SaveMode mode)
	{
		saveMode = (mode == null) ? SaveMode.ANYWHERE : mode;
	}

	/**
	 * Reads a SAVEMODE setting.
	 * @param s the setting
	 * @return its save mode, ANYWHERE if blank or not known
	 */
	public static SaveMode parseSaveMode(final String s)
	{
		final String name = (s == null) ? "" : s.trim().toUpperCase();
		for(final SaveMode mode : SaveMode.values())
		{
			if(mode.name().equals(name))
				return mode;
		}
		if(name.length() > 0)
			Log.errOut("SaveGame", "SAVEMODE is '" + s + "', which is not ANYWHERE, NOTINCOMBAT, SAVEPOINTS or IRONMAN, so it is ANYWHERE.");
		return SaveMode.ANYWHERE;
	}

	/**
	 * Returns an effect with the given ID on a room or on its area or the
	 * areas above that: how save points and no-save places are found.
	 * @param R the room
	 * @param ID the effect's ID
	 * @return the effect, or null
	 */
	private static Ability placeEffect(final Room R, final String ID)
	{
		if(R == null)
			return null;
		Ability A = R.fetchEffect(ID);
		if(A != null)
			return A;
		final Area area = R.getArea();
		if(area == null)
			return null;
		A = area.fetchEffect(ID);
		if(A != null)
			return A;
		for(final Area parent : area.getParentsRecurse())
		{
			A = parent.fetchEffect(ID);
			if(A != null)
				return A;
		}
		return null;
	}

	/**
	 * Returns whether the player, any of their characters in the world, or
	 * anyone in their group is fighting.
	 * @param mob the player
	 * @return true if the party is in a fight
	 */
	private static boolean partyFighting(final MOB mob)
	{
		if(mob.isInCombat())
			return true;
		for(final MOB M : Party.othersInWorld(mob))
		{
			if(M.isInCombat())
				return true;
		}
		for(final MOB M : mob.getGroupMembers(new HashSet<MOB>()))
		{
			if(M.isInCombat())
				return true;
		}
		return false;
	}

	/**
	 * Returns the one save of an ironman game.
	 * @param mob the player
	 * @return its name
	 */
	public static String ironmanSlot(final MOB mob)
	{
		return defaultSlot(mob);
	}

	/**
	 * Returns why the game's save rules do not let the player save here and
	 * now, or null if they do.  SAVE, QUIT's offer to save, and autosaves all
	 * ask this.
	 * @param mob the player
	 * @param slot the save asked for, or null for the usual one
	 * @param quitting true if the game is saving as the player quits
	 * @return null if the game can be saved, or why not
	 */
	public static String whyNotSave(final MOB mob, final String slot, final boolean quitting)
	{
		final SaveMode mode = saveMode;
		if(mode == SaveMode.IRONMAN)
		{
			if((slot != null) && (!slot.equalsIgnoreCase(ironmanSlot(mob))))
				return CMLib.lang().L("This is an ironman game: it has one save, '@x1', which it keeps for you.", ironmanSlot(mob));
			if(quitting)
				return null; // the game is kept when the player stops, wherever they are
		}
		final Ability noSave = placeEffect(mob.location(), "Prop_NoSave");
		if(noSave != null)
		{
			final String msg = CMParms.getParmStr(noSave.text(), "MSG", "");
			return (msg.length() > 0) ? msg : CMLib.lang().L("You cannot save the game here.");
		}
		if((mode != SaveMode.ANYWHERE) && partyFighting(mob))
			return CMLib.lang().L("You cannot save the game during a fight.");
		if((mode == SaveMode.SAVEPOINTS) && (placeEffect(mob.location(), "Prop_SavePoint") == null))
			return CMLib.lang().L("You can only save the game at a save point.");
		return null;
	}

	/**
	 * Returns what a save point here says when the game is saved at it.
	 * @param mob the player
	 * @return the message, or ""
	 */
	public static String savePointMessage(final MOB mob)
	{
		final Ability point = placeEffect(mob.location(), "Prop_SavePoint");
		return (point == null) ? "" : CMParms.getParmStr(point.text(), "MSG", "");
	}

	/**
	 * Returns when a save was written, for autosaves that take turns.
	 * @param slot the save's name
	 * @return when, in milliseconds, or 0 if there is no such save
	 */
	public static long savedAt(final String slot)
	{
		final Properties header = SaveSlots.isValidName(slot) ? SaveSlots.readHeader(saveDir, slot) : null;
		return (header == null) ? 0 : parseLong(header.getProperty("saved"), 0);
	}

	/**
	 * Saves the game for an autosave (Prop_AutoSave): skipped while the
	 * party fights, but wherever the builder put it.  An ironman game saves
	 * its one save.
	 * @param mob the player
	 * @param slot the save's name
	 * @return null if saved, or why not
	 */
	public static String autoSave(final MOB mob, final String slot)
	{
		if((saveMode != SaveMode.ANYWHERE) && partyFighting(mob))
			return CMLib.lang().L("You cannot save the game during a fight.");
		return write(mob, (saveMode == SaveMode.IRONMAN) ? ironmanSlot(mob) : slot);
	}

	/**
	 * Returns the save a player's game goes to when they do not name one:
	 * the one it came from or was last saved to, else their name.
	 * @param mob the player
	 * @return the save's name
	 */
	public static String defaultSlot(final MOB mob)
	{
		final String slot = currentSlot;
		if(slot != null)
			return slot;
		final StringBuilder name = new StringBuilder();
		for(final char c : mob.Name().toLowerCase().toCharArray())
		{
			if(Character.isLetterOrDigit(c) || (c == '-') || (c == '_'))
				name.append(c);
		}
		return (name.length() > 0) ? name.toString() : "game";
	}

	/**
	 * Saves the game.  Runs on the game thread, so the world stands still
	 * while it saves.
	 * @param mob the player
	 * @param slot the save's name, or null for {@link #defaultSlot(MOB)}
	 * @return null if saved, or why not
	 */
	public static String save(final MOB mob, final String slot)
	{
		return save(mob, slot, false);
	}

	/**
	 * Saves the game, if its save rules allow it here and now.  Runs on the
	 * game thread, so the world stands still while it saves.
	 * @param mob the player
	 * @param slot the save's name, or null for {@link #defaultSlot(MOB)}
	 * @param quitting true if saving as the player quits
	 * @return null if saved, or why not
	 */
	public static String save(final MOB mob, final String slot, final boolean quitting)
	{
		if((!builderMode) && (workingDb != null))
		{
			final String why = whyNotSave(mob, slot, quitting);
			if(why != null)
				return why;
		}
		return write(mob, slot);
	}

	/**
	 * Writes the game to a save.
	 * @param mob the player
	 * @param slot the save's name, or null for {@link #defaultSlot(MOB)}
	 * @return null if saved, or why not
	 */
	private static String write(final MOB mob, String slot)
	{
		if(builderMode)
			return CMLib.lang().L("There are no saved games in builder mode: changes go straight into the world.");
		if(workingDb == null)
			return CMLib.lang().L("Saved games are off.");
		if(slot == null)
			slot = defaultSlot(mob);
		if(!SaveSlots.isValidName(slot))
			return CMLib.lang().L("A save's name is letters, digits, dashes and underscores, at most 32 of them.");
		// ST6: onBeforeSave and GAME_PROG SAVE, before the snapshot, either of which may refuse it
		if(!ScriptEvents.gameCheck(mob, slot))
			return CMLib.lang().L("The game was not saved.");
		CMLib.players().savePlayer(mob);
		for(final MOB M : Party.othersInWorld(mob))
			CMLib.players().savePlayer(M);
		saveClocks();
		ServiceEngine.beforeSleep(); // database work queued on the game thread
		final Properties header = new Properties();
		header.setProperty("version", Integer.toString(VERSION));
		if(isDevMode())
			header.setProperty("dev", "true");
		header.setProperty("character", mob.Name());
		header.setProperty("level", Integer.toString(mob.phyStats().level()));
		header.setProperty("class", mob.charStats().getCurrentClass().name(mob.charStats().getCurrentClassLevel()));
		header.setProperty("race", mob.charStats().getMyRace().name());
		final StringBuilder party = new StringBuilder("");
		for(final MOB M : Party.othersInWorld(mob))
			party.append((party.length() > 0) ? ", " : "").append(M.Name());
		if(party.length() > 0)
			header.setProperty("party", party.toString());
		final Room R = mob.location();
		if(R != null)
		{
			header.setProperty("place", CMStrings.removeColors(R.displayText(mob)));
			if(R.getArea() != null)
				header.setProperty("area", R.getArea().name());
		}
		header.setProperty("saved", Long.toString(System.currentTimeMillis()));
		header.setProperty("gametime", Long.toString(GameTime.now()));
		header.setProperty("timemode", GameTime.isTurnBased() ? "TURNS" : "REALTIME");
		header.setProperty("timemodebefore", (TimeLock.previous() == null) ? "" : (TimeLock.previous().booleanValue() ? "TURNS" : "REALTIME")); // W28
		header.setProperty("turns", Long.toString(ScriptEvents.turns()));
		header.setProperty("difficulty", GameDifficulty.current()); // C13
		header.setProperty("exchange", Exchange.saved()); // W29
		header.setProperty("genseed", GenSeed.saved()); // G1
		header.setProperty("savemode", saveMode.name());
		if(CMLib.time().globalClock() != null)
			header.setProperty("gamedate", CMLib.time().globalClock().getShortTimeDescription());
		// the world this game plays on, so a restore can say what changed since
		header.setProperty("worldsum", WorldFiles.bootChecksum());
		final StringBuilder areaSums = new StringBuilder();
		for(final Map.Entry<String, String> e : WorldFiles.bootAreaSums().entrySet())
			areaSums.append((areaSums.length() > 0) ? ";" : "").append(e.getKey()).append('=').append(e.getValue());
		header.setProperty("areasums", areaSums.toString());
		header.setProperty("paused", Boolean.toString(GameTime.isPaused()));
		header.setProperty("partymeet", Boolean.toString(Party.isMeetWhereLeft()));
		header.setProperty("partyfight", Boolean.toString(Party.isFightEachOther()));
		header.setProperty("partyturns", Party.getTurns().name());
		header.setProperty("partyorder", Party.getOrder());
		header.setProperty("partyalone", Party.getOnTheirOwn());
		final Long dice = CMath.getRandState();
		if(dice != null)
			header.setProperty("dice", dice.toString());
		final Map<String, String> files = new HashMap<String, String>();
		files.put(WorldState.FILE, WorldState.capture(mob));
		try
		{
			SaveSlots.write(saveDir, slot, workingDb, header, files);
		}
		catch(final IOException e)
		{
			Log.errOut("SaveGame", e);
			return CMLib.lang().L("The game could not be saved: @x1", e.getMessage());
		}
		currentSlot = slot;
		return null;
	}

	/**
	 * Restores a saved game: the engine restarts in this process and boots
	 * with the save.  Whatever has not been saved is lost.
	 * @param slot the save's name or number
	 * @return null if restoring, or why not
	 */
	public static String restore(final String slot)
	{
		if(builderMode)
			return CMLib.lang().L("There are no saved games in builder mode.");
		if(saveMode == SaveMode.IRONMAN)
			return CMLib.lang().L("This is an ironman game: there is no going back to a save.  QUIT keeps the game.");
		final SaveSlots.Slot found = SaveSlots.find(saveDir, slot);
		if(found == null)
			return CMLib.lang().L("There is no saved game called '@x1'.", slot);
		final List<MudHost> hosts = CMLib.hosts();
		if(hosts.size() == 0)
			return CMLib.lang().L("The game cannot restart.");
		nextBoot = found.name;
		final MudHost host = hosts.get(0);
		// the restart stops the game thread, so it runs on its own
		final Thread T = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				host.shutdown(null, false, null);
			}
		}, "Restore");
		T.start();
		return null;
	}

	/**
	 * Lists the saved games for the player, numbered.
	 * @return the list, or a line saying there are none
	 */
	public static String describeSlots()
	{
		final List<SaveSlots.Slot> slots = SaveSlots.list(saveDir);
		if(slots.size() == 0)
			return CMLib.lang().L("There are no saved games.");
		final StringBuilder str = new StringBuilder(CMLib.lang().L("Saved games:")).append("\n\r");
		for(int i = 0; i < slots.size(); i++)
		{
			final SaveSlots.Slot slot = slots.get(i);
			str.append(String.format("  %2d) %-12s %s", Integer.valueOf(i + 1), slot.name, SaveSlots.describe(slot)));
			if(slot.name.equals(currentSlot))
				str.append(CMLib.lang().L(" (this game)"));
			final String sum = slot.header.getProperty("worldsum", "");
			if((sum.length() > 0) && (!sum.equals(WorldFiles.bootChecksum())))
				str.append(CMLib.lang().L(" (older world)"));
			str.append("\n\r");
		}
		return str.toString();
	}

	/**
	 * Runs a job on the game thread, as soon as it has started, and waits
	 * for it to finish, so that nothing else in the world happens while it
	 * runs.
	 * @param R the job
	 */
	private static void runOnGameThread(final Runnable R)
	{
		for(int i = 0; (i < 200) && (!ServiceEngine.hasGameThread()); i++)
			CMLib.s_sleep(50);
		final CountDownLatch done = new CountDownLatch(1);
		CMLib.threads().executeRunnable(new Runnable()
		{
			@Override
			public void run()
			{
				try
				{
					R.run();
				}
				finally
				{
					done.countDown();
				}
			}
		});
		try
		{
			done.await(120, TimeUnit.SECONDS);
		}
		catch(final InterruptedException e)
		{
		}
	}

	/**
	 * Has every clock read its saved date now.  Otherwise it does so at its
	 * first tick, which in turn-based play may be a while off, and until then
	 * TIME shows the wrong date.
	 */
	private static void loadClocks()
	{
		final Set<TimeClock> done = new HashSet<TimeClock>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			final TimeClock C = A.getTimeObj();
			if((C != null) && done.add(C))
				C.tick(A, Tickable.TICKID_AREA);
		}
	}

	private static void saveClocks()
	{
		final Set<TimeClock> done = new HashSet<TimeClock>();
		final TimeClock global = CMLib.time().globalClock();
		if(global != null)
		{
			done.add(global);
			global.save();
		}
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final TimeClock C = a.nextElement().getTimeObj();
			if((C != null) && done.add(C))
				C.save();
		}
	}

	private static long parseLong(final String s, final long dflt)
	{
		try
		{
			return (s == null) ? dflt : Long.parseLong(s.trim());
		}
		catch(final NumberFormatException e)
		{
			return dflt;
		}
	}
}
