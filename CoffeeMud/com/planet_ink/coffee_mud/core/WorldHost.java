package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Common.interfaces.PhyStats;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

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
 * ST6: the world's own scripts.  Each world/scripts/*.script is put on one
 * hidden item, named WORLD (so its variables are WORLD's), in no room's list
 * but held by the player's room, and its scripts tick every tick wherever the
 * player goes.  Its triggers that fire anywhere (GAME_PROG, QUEST_PROG,
 * TURN_PROG and the like) hear the whole game; a script made for the world
 * would mostly use those, with DELAY_PROG and RAND_PROG.  Made at the first
 * login after the game starts, before NEWGAME or RESTORE fires.
 */
public final class WorldHost
{
	private WorldHost()
	{
	}

	/** the world host's name, and its variables' host */
	public static final String	NAME	= "WORLD";
	/** the folder in the world folder its scripts are in */
	public static final String	DIR		= "scripts";

	private static volatile Item	host	= null;
	private static volatile Ticker	ticker	= null;

	/**
	 * The world host, if there are world scripts.
	 * @return the host, or null
	 */
	public static Item host()
	{
		final Item I = host;
		return ((I == null) || I.amDestroyed()) ? null : I;
	}

	/**
	 * The world's script files, in name order.
	 * @return the files
	 */
	public static List<File> files()
	{
		final List<File> files = new ArrayList<File>();
		final File dir = new File(WorldFiles.worldDir(), DIR);
		final File[] list = dir.listFiles();
		if(list != null)
		{
			for(final File F : list)
			{
				if(F.isFile() && F.getName().toLowerCase().endsWith(".script"))
					files.add(F);
			}
		}
		Collections.sort(files);
		return files;
	}

	/**
	 * Makes the world host with the world's scripts, if there are any and it
	 * is not made already, held by the player's room.
	 * @param player the player
	 * @return the host, or null if there are no world scripts
	 */
	public static synchronized Item start(final MOB player)
	{
		if(host() != null)
		{
			follow(player);
			return host;
		}
		final List<File> files = files();
		if(files.isEmpty())
			return null;
		final Item I = CMClass.getItem("StdItem");
		I.setName(NAME);
		I.setDisplayText("");
		I.setDescription("");
		I.basePhyStats().setDisposition(I.basePhyStats().disposition() | PhyStats.IS_NOT_SEEN | PhyStats.IS_UNSAVABLE);
		I.recoverPhyStats();
		host = I;
		follow(player);
		for(final File F : files)
		{
			try
			{
				final String text = new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8);
				final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
				S.setScript(text);
				I.addScript(S);
			}
			catch(final java.io.IOException e)
			{
				Log.errOut("WorldHost", F.getName() + ": " + e.getMessage());
			}
		}
		if(ticker == null)
		{
			ticker = new Ticker();
			CMLib.threads().startTickDown(ticker, Tickable.TICKID_MISCELLANEOUS, 1);
		}
		return I;
	}

	/**
	 * Has the world host held by a character's room.
	 * @param player the character
	 */
	public static void follow(final MOB player)
	{
		final Item I = host();
		final Room R = (player == null) ? null : player.location();
		if((I != null) && (R != null) && (I.owner() != R))
			I.setOwner(R);
	}

	/**
	 * Forgets the world host of the game before a restart, whose engine is
	 * gone, so that the first login makes a new one.
	 */
	public static synchronized void forget()
	{
		host = null;
		ticker = null;
	}

	/**
	 * Takes the world host away (a restart makes a new one).
	 */
	public static synchronized void stop()
	{
		final Item I = host;
		host = null;
		if(I != null)
		{
			for(final Enumeration<ScriptingEngine> s = I.scripts(); s.hasMoreElements();)
			{
				final ScriptingEngine S = s.nextElement();
				if(S != null)
					I.delScript(S);
			}
			I.setOwner(null);
			I.destroy();
		}
	}

	private static MOB player()
	{
		for(final Session S : CMLib.sessions().localOnlineIterable())
		{
			if((S != null) && (S.mob() != null) && (S.mob().location() != null))
				return S.mob();
		}
		return null;
	}

	/** ticks the world host's scripts each tick, where the player is */
	private static final class Ticker implements Tickable
	{
		@Override
		public String ID()
		{
			return "WorldHost";
		}

		@Override
		public String name()
		{
			return ID();
		}

		@Override
		public CMObject newInstance()
		{
			return this;
		}

		@Override
		public CMObject copyOf()
		{
			return this;
		}

		@Override
		public void initializeClass()
		{
		}

		@Override
		public int compareTo(final CMObject o)
		{
			return (o == this) ? 0 : 1;
		}

		@Override
		public int getTickStatus()
		{
			return Tickable.STATUS_NOT;
		}

		@Override
		public boolean tick(final Tickable ticking, final int tickID)
		{
			final Item I = host();
			if(I == null)
			{
				ticker = null;
				return false;
			}
			follow(player());
			if(I.owner() == null)
				return true;
			for(final Enumeration<ScriptingEngine> s = I.scripts(); s.hasMoreElements();)
			{
				final ScriptingEngine S = s.nextElement();
				if(S != null)
					S.tick(I, Tickable.TICKID_ITEM_BEHAVIOR);
			}
			return true;
		}
	}
}
