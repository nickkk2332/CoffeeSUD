package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * The panel beside the text of a character's window: what it shows, and
 * whether there is one.  WINDOWPANEL=STATUS,MAP,PARTY (in coffeemud.ini or on
 * the command line; world.ini does not take it, as it is not in
 * WorldSettings.KEYS) or WINDOWS PANEL ON turns it on, with
 * the sections given, in their order:
 * <ul>
 * <li>STATUS: world/text/panel.txt, with the prompt's codes (%h, %r, %z,
 * %{VAR name}, %{VAR host name} ...) and colour codes; without the file, the
 * character's points, room and area.</li>
 * <li>MAP: the map of the rooms about the character, as REGIONAL AWARENESS
 * draws it.</li>
 * <li>PARTY: the party, in its order, each with a bar of their hit points.</li>
 * </ul>
 * The panel is drawn again each time the window shows its prompt, so after
 * each command, and each move.
 */
public final class WindowPanel
{
	/** the sections a panel can have, in their usual order */
	public static final String[]	SECTIONS		= { "STATUS", "MAP", "PARTY" };
	/** what STATUS shows when the world has no world/text/panel.txt */
	private static final String		DEFAULT_STATUS	= "^w%r^N\n%z\n\n^hHP %h/%H^N\n^mMana %m/%M^N\n^vMoves %v/%V^N";
	/** how many rooms across the map is */
	private static final int		MAP_ROOMS		= 7;
	/** how many characters wide a party member's bar is */
	private static final int		BAR				= 10;

	/** the sections, comma separated, as set; null when not set, "" when off */
	private static volatile String	sections		= null;
	/** what draws each open window's panel again */
	private static final List<WeakReference<Runnable>> windows = new ArrayList<WeakReference<Runnable>>();

	private WindowPanel()
	{
	}

	/**
	 * Returns the sections the panel shows, comma separated, or "" if the
	 * windows have no panel.  Until set, from WINDOWPANEL.
	 * @return the sections
	 */
	public static String sections()
	{
		String s = sections;
		if(s == null)
		{
			s = normalize(CMProps.instance().getStr("WINDOWPANEL", ""));
			sections = s;
		}
		return s;
	}

	/**
	 * Sets the sections the panel shows, and draws every open window's panel
	 * again (or hides it).
	 * @param which the sections, comma separated: STATUS, MAP or PARTY, or
	 * ON for all, or OFF (or "") for none
	 * @return the sections now, or null if one was not a section
	 */
	public static String setSections(final String which)
	{
		final String s = normalize(which);
		if(s == null)
			return null;
		sections = s;
		refreshAll();
		return s;
	}

	/**
	 * Turns a list of sections into the form kept: upper case, known, comma
	 * separated; ON is all of them, OFF none.
	 * @param which the list
	 * @return the list, "" for none, or null if one is not a section
	 */
	private static String normalize(final String which)
	{
		final String w = (which == null) ? "" : which.trim().toUpperCase();
		if(w.length() == 0 || w.equals("OFF") || w.equals("0") || w.equals("NO") || w.equals("FALSE"))
			return "";
		if(w.equals("ON") || w.equals("1") || w.equals("YES") || w.equals("TRUE") || w.equals("ALL"))
			return String.join(",", SECTIONS);
		final List<String> found = new ArrayList<String>();
		for(final String part : w.split("[,; ]+"))
		{
			if(part.length() == 0)
				continue;
			boolean known = false;
			for(final String S : SECTIONS)
			{
				if(S.startsWith(part))
				{
					if(!found.contains(S))
						found.add(S);
					known = true;
					break;
				}
			}
			if(!known)
				return null;
		}
		return String.join(",", found);
	}

	/**
	 * A window tells the panel how to draw it again, for when the sections
	 * change.  Kept weakly: a window closed and forgotten drops out.
	 * @param refresh draws the window's panel again
	 */
	public static void addWindow(final Runnable refresh)
	{
		synchronized(windows)
		{
			windows.add(new WeakReference<Runnable>(refresh));
		}
	}

	/**
	 * A window no longer wants its panel drawn again.
	 * @param refresh what it gave {@link #addWindow(Runnable)}
	 */
	public static void removeWindow(final Runnable refresh)
	{
		synchronized(windows)
		{
			for(int i = windows.size() - 1; i >= 0; i--)
			{
				final Runnable R = windows.get(i).get();
				if((R == null) || (R == refresh))
					windows.remove(i);
			}
		}
	}

	private static void refreshAll()
	{
		final List<Runnable> all = new ArrayList<Runnable>();
		synchronized(windows)
		{
			for(int i = windows.size() - 1; i >= 0; i--)
			{
				final Runnable R = windows.get(i).get();
				if(R == null)
					windows.remove(i);
				else
					all.add(R);
			}
		}
		for(final Runnable R : all)
			R.run();
	}

	/**
	 * Returns what a character's panel shows, with the game's colour codes
	 * (^h, ^N ...), for the session to turn into its own.
	 * @param mob the window's character
	 * @param which the sections, comma separated
	 * @return the text, lines ended with \n
	 */
	public static String text(final MOB mob, final String which)
	{
		final StringBuilder out = new StringBuilder();
		if(mob == null)
			return "";
		for(final String section : which.split(","))
		{
			final String part;
			if(section.equals("STATUS"))
				part = status(mob);
			else
			if(section.equals("MAP"))
				part = map(mob);
			else
			if(section.equals("PARTY"))
				part = party(mob);
			else
				part = "";
			if(part.length() == 0)
				continue;
			if(out.length() > 0)
				out.append("\n\n");
			out.append(part);
		}
		return out.toString();
	}

	/**
	 * The STATUS section: world/text/panel.txt, or the default, with the
	 * prompt's codes filled in.
	 */
	private static String status(final MOB mob)
	{
		String template = DEFAULT_STATUS;
		final File dir = WorldFiles.worldDir();
		if(dir != null)
		{
			final File f = new File(new File(dir, "text"), "panel.txt");
			if(f.isFile())
			{
				try
				{
					template = new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
				}
				catch(final Exception e)
				{
					Log.errOut("WindowPanel", "Could not read " + f + ": " + e);
				}
			}
		}
		final StringBuilder out = new StringBuilder();
		for(final String line : template.replace("\r", "").split("\n", -1))
		{
			if(out.length() > 0)
				out.append('\n');
			// the prompt's codes, a line at a time (the builder starts its prompt on a new line)
			String built = (line.indexOf('%') < 0) ? line : CMLib.utensils().buildPrompt(mob, line);
			while(built.startsWith("\n") || built.startsWith("\r"))
				built = built.substring(1);
			out.append(built);
		}
		return out.toString().replaceAll("\\s+$", "");
	}

	/**
	 * The MAP section: the rooms about the character, as REGIONAL AWARENESS
	 * draws them (the skill is asked by reflection, as the engine's classes
	 * are not the core's to name).
	 */
	private static String map(final MOB mob)
	{
        return PlayerMaps.render(mob,mob.location(),Math.max(1,MAP_ROOMS/2));
	}

	/**
	 * The PARTY section: each of the party, in its order, with a bar of their
	 * hit points.
	 */
	private static String party(final MOB mob)
	{
		final MOB leader = Party.leaderOf(mob);
		final List<MOB> all = Party.inOrder(leader);
		if(!all.contains(mob))
			all.add(0, mob);
		final StringBuilder out = new StringBuilder();
		for(final MOB M : all)
		{
			if(out.length() > 0)
				out.append('\n');
			final int hp = Math.max(0, M.curState().getHitPoints());
			final int max = Math.max(1, M.maxState().getHitPoints());
			final int filled = Math.min(BAR, (int)Math.round((double)hp * BAR / max));
			final String color = (hp * 4 < max) ? "^r" : ((hp * 2 < max) ? "^y" : "^g");
			out.append((M == mob) ? "^w" : "^N").append(M.Name()).append("^N\n");
			out.append("[").append(color).append(repeat('#', filled)).append("^N").append(repeat('-', BAR - filled)).append("] ")
				.append(hp).append('/').append(max);
		}
		return out.toString();
	}

	private static String repeat(final char c, final int n)
	{
		final StringBuilder s = new StringBuilder();
		for(int i = 0; i < n; i++)
			s.append(c);
		return s.toString();
	}
}
