package com.planet_ink.coffee_mud.core.save;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.threads.ServiceEngine;
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
	private static final Map<String, String> bootOverrides = new Hashtable<String, String>();

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
		workingDb = null;
		databaseDir = null;
		restoring = null;
		final String slot = nextBoot;
		nextBoot = null;
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
		GameTime.setTurnBased("TURNS".equalsIgnoreCase(header.getProperty("timemode")));
		if("true".equalsIgnoreCase(header.getProperty("paused")))
			GameTime.pause();
		final long gameTime = parseLong(header.getProperty("gametime"), 0);
		if(gameTime > 0)
			GameTime.set(gameTime);
		Party.setMeetWhereLeft("true".equalsIgnoreCase(header.getProperty("partymeet")));
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
			}
		});
		final Properties header = restoring;
		if(header == null)
			return;
		restoring = null;
		final String world = SaveSlots.readFile(saveDir, currentSlot, WorldState.FILE);
		if(world != null)
		{
			runOnGameThread(new Runnable()
			{
				@Override
				public void run()
				{
					WorldState.apply(world);
				}
			});
		}
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
		// a character coming into the world can act at once, rather than only
		// after its first tick: otherwise the first command that takes time
		// waits, and what is typed after it, such as the answer to its
		// question, is taken for the next command
		if(mob.actions() < mob.phyStats().speed())
			mob.setActions(mob.phyStats().speed());
		WorldState.finishRestore(mob);
		if(builderMode)
			makeArchon(mob);
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
			mob.tell(CMLib.lang().L("^HThis game plays on a copy of the world.^?  Mobs and items you make, change or "
				+ "destroy stay that way in this game's saves, but changes to rooms, exits, areas and the like last "
				+ "only until the game ends.  Start CoffeeSUD with BUILDER=1 to change the world itself."));
		}
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
	public static String save(final MOB mob, String slot)
	{
		if(builderMode)
			return CMLib.lang().L("There are no saved games in builder mode: changes go straight into the world.");
		if(workingDb == null)
			return CMLib.lang().L("Saved games are off.");
		if(slot == null)
			slot = defaultSlot(mob);
		if(!SaveSlots.isValidName(slot))
			return CMLib.lang().L("A save's name is letters, digits, dashes and underscores, at most 32 of them.");
		CMLib.players().savePlayer(mob);
		for(final MOB M : Party.othersInWorld(mob))
			CMLib.players().savePlayer(M);
		saveClocks();
		ServiceEngine.beforeSleep(); // database work queued on the game thread
		final Properties header = new Properties();
		header.setProperty("version", Integer.toString(VERSION));
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
		header.setProperty("paused", Boolean.toString(GameTime.isPaused()));
		header.setProperty("partymeet", Boolean.toString(Party.isMeetWhereLeft()));
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
