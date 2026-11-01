package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;

import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

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
 * RECORD INPUT (DT25): each line the player types, in every window, written
 * to a playtest file (.play, as tools/playtest.py plays them, see DT15) after
 * a header of what the game started from: the seed, the time mode, the save
 * or new game, and the world's checksum.  Before each line, a comment gives
 * the character and the game time; RECORD MARK puts a comment of its own.
 * RECORDINPUT=1 in the ini starts it when the player comes into the world.
 */
public final class InputRecord
{
	/** the folder, in the saves folder, a name with no path goes in */
	public static final String	FOLDER	= "recordings";

	private static PrintWriter	out		= null;
	private static File			file	= null;
	private static long			started	= 0;
	private static int			lines	= 0;

	private InputRecord()
	{
	}

	/**
	 * Whether input is being recorded.
	 * @return true if it is
	 */
	public static synchronized boolean isRecording()
	{
		return out != null;
	}

	/**
	 * The file being written.
	 * @return the file, or null
	 */
	public static synchronized File file()
	{
		return file;
	}

	/**
	 * How many lines typed have been written.
	 * @return the count
	 */
	public static synchronized int lines()
	{
		return lines;
	}

	/**
	 * The file a name means: as given if it has a folder in it, else in the
	 * saves folder's recordings, ending .play.
	 * @param name the name
	 * @return the file
	 */
	public static File fileFor(final String name)
	{
		String n = name.trim();
		if(!n.toLowerCase().endsWith(".play"))
			n = n + ".play";
		if((n.indexOf('/') >= 0) || (n.indexOf(File.separatorChar) >= 0))
			return new File(n);
		final File saves = SaveGame.saveDir();
		return new File(new File((saves == null) ? new File(".") : saves, FOLDER), n);
	}

	/**
	 * The header a recording starts with: what the game started from, as the
	 * playtest runner's lines say it.
	 * @param mob the player, for #char
	 * @return the lines
	 */
	public static String header(final MOB mob)
	{
		final StringBuilder str = new StringBuilder();
		str.append("# Recorded input (RECORD INPUT), ").append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date())).append("\n");
		final String seed = CMProps.instance().getStr("RANDOMSEED", "").trim();
		if(seed.length() > 0)
			str.append("#seed ").append(seed).append("\n");
		else
			str.append("# no RANDOMSEED: the dice were not seeded, so a replay may roll otherwise\n");
		if(!GameTime.isTurnBased())
			str.append("#realtime\n");
		if(SaveGame.isBuilderMode())
			str.append("#builder\n");
		else
		if(SaveGame.currentSlot() != null)
			str.append("#restore ").append(SaveGame.currentSlot()).append("\n");
		else
		{
			str.append("#new\n");
			if(mob != null)
			{
				str.append("#char ").append(mob.Name()).append(",").append(mob.baseCharStats().getMyRace().name())
					.append(",").append(mob.baseCharStats().getCurrentClass().name()).append(",")
					.append((char)mob.baseCharStats().getStat(CharStats.STAT_GENDER)).append(",")
					.append(CMLib.flags().getAlignmentName(mob).toLowerCase()).append("\n");
			}
		}
		str.append("# world ").append(WorldFiles.checksum()).append("\n");
		str.append("# game time ").append(GameTime.now()).append(" at the start\n");
		return str.toString();
	}

	/**
	 * Starts writing typed lines to a file, after the header.
	 * @param F the file
	 * @param mob the player
	 * @return null if it started, else why not
	 */
	public static synchronized String start(final File F, final MOB mob)
	{
		stop();
		try
		{
			final File dir = F.getAbsoluteFile().getParentFile();
			if((dir != null) && (!dir.isDirectory()) && (!dir.mkdirs()))
				return "Cannot make the folder " + dir.getPath() + ".";
			out = new PrintWriter(new OutputStreamWriter(new FileOutputStream(F, false), StandardCharsets.UTF_8));
			file = F;
			started = GameTime.now();
			lines = 0;
			out.print(header(mob));
			out.flush();
			return null;
		}
		catch(final IOException e)
		{
			out = null;
			file = null;
			return e.getMessage();
		}
	}

	/**
	 * Stops writing, and closes the file.
	 */
	public static synchronized void stop()
	{
		if(out != null)
		{
			out.println("# end of the recording, " + lines + " lines");
			out.close();
		}
		out = null;
		file = null;
	}

	private static String clock(final long ms)
	{
		final long s = Math.max(0, ms) / 1000;
		return String.format("%d:%02d:%02d", Long.valueOf(s / 3600), Long.valueOf((s / 60) % 60), Long.valueOf(s % 60));
	}

	/**
	 * Writes a line typed, after a comment of who typed it and when.  A line
	 * that would read as one of the runner's own (# first) is written as a
	 * comment, since the runner could not send it.
	 * @param mob the character it was typed for, or null
	 * @param line the line
	 */
	public static void record(final MOB mob, final String line)
	{
		if((line == null) || (line.trim().length() == 0))
			return;
		synchronized(InputRecord.class)
		{
			if(out == null)
				return;
			out.println("# " + clock(GameTime.now() - started) + " " + ((mob == null) ? "(no character)" : mob.Name()));
			if(line.trim().startsWith("#"))
				out.println("# (typed) " + line);
			else
				out.println(line);
			lines++;
			out.flush();
		}
	}

	/**
	 * RECORD MARK: a comment in the file, where the bug was seen.
	 * @param mob the one marking
	 * @param text what to say
	 * @return false if nothing is being recorded
	 */
	public static synchronized boolean mark(final MOB mob, final String text)
	{
		if(out == null)
			return false;
		out.println("# MARK " + clock(GameTime.now() - started) + " " + ((mob == null) ? "" : (mob.Name() + ": ")) + text);
		out.flush();
		return true;
	}

	/**
	 * At the player's coming into the world: RECORDINPUT=1 starts a recording
	 * of its own, named for the date and time, unless one runs already.
	 * @param mob the player
	 */
	public static void startFor(final MOB mob)
	{
		if(isRecording())
			return;
		final String on = CMProps.instance().getStr("RECORDINPUT", "").trim();
		if(!(on.equals("1") || on.equalsIgnoreCase("ON") || on.equalsIgnoreCase("TRUE")))
			return;
		final File F = fileFor("input-" + new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date()));
		final String why = start(F, mob);
		if(why != null)
			Log.errOut("InputRecord", "RECORDINPUT: " + why);
		else
		if(mob != null)
			mob.tell("Recording what you type to " + F.getPath() + " (RECORD INPUT OFF stops it).");
	}
}
