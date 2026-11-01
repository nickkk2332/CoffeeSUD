package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;

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
 * PUZZLE CHECK (DT33): the places, doors and things the world's scripts name
 * that are not there: a room ID for MPGOTO, MPAT or MPTRANSFER that is no
 * room; a direction for MPOPEN, MPCLOSE, MPLOCK or MPUNLOCK with no door in
 * the host's room; and an MPOLOAD or MPMLOAD name that is no class or catalog
 * entry.  Words with $-codes are left alone: what they name is known only as
 * the script runs.
 */
public final class PuzzleCheck
{
	private PuzzleCheck()
	{
	}

	private static List<ScriptingEngine> scriptsOf(final PhysicalAgent host)
	{
		final List<ScriptingEngine> list = new ArrayList<ScriptingEngine>();
		for(final Enumeration<ScriptingEngine> e = host.scripts(); e.hasMoreElements();)
			list.add(e.nextElement().baseEngine());
		for(final Enumeration<Behavior> e = host.behaviors(); e.hasMoreElements();)
		{
			final Behavior B = e.nextElement();
			if(B instanceof ScriptingEngine)
				list.add(((ScriptingEngine)B).baseEngine());
		}
		return list;
	}

	/** a script's lines, its LOAD= files read in, each with where it is written */
	private static void lines(final String where, final String text, final List<String[]> out, final Set<String> seen, final int depth)
	{
		final String[] ls = text.replace("\r\n", "\n").replace("\n\r", "\n").replace('\r', '\n').split("[\n;~]");
		int n = 0;
		for(final String l : ls)
		{
			n++;
			final String s = l.trim();
			final int x = s.toUpperCase().indexOf("LOAD=");
			if((x == 0) && (depth < 4))
			{
				String f = s.substring(5).trim();
				if(f.startsWith("/"))
					f = f.substring(1);
				final String path = f.toLowerCase().startsWith("resources/") ? ("/" + f) : ("/resources/" + f);
				if(seen.add(path.toUpperCase()))
				{
					final CMFile F = new CMFile(path, null, 0);
					if(F.exists())
						lines(f, F.text().toString(), out, seen, depth + 1);
				}
				continue;
			}
			out.add(new String[] { where + ":" + n, s });
		}
	}

	private static boolean variable(final String s)
	{
		return s.indexOf('$') >= 0;
	}

	/**
	 * The problems of one host's scripts.
	 * @param host the host
	 * @param R the room it is in, or null
	 * @return the problems
	 */
	public static List<String> checkHost(final PhysicalAgent host, final Room R)
	{
		final List<String> out = new ArrayList<String>();
		for(final ScriptingEngine S : scriptsOf(host))
		{
			final String script = S.getScript();
			if((script == null) || (script.trim().length() == 0))
				continue;
			final List<String[]> ls = new ArrayList<String[]>();
			lines("inline", script, ls, new HashSet<String>(), 0);
			for(final String[] l : ls)
			{
				final List<String> w = CMParms.parse(l[1]);
				if(w.size() < 2)
					continue;
				final String cmd = w.get(0).toUpperCase();
				final String at = host.Name() + " " + l[0] + ": ";
				if(cmd.equals("MPGOTO") || cmd.equals("MPAT") || cmd.equals("MPTRANSFER"))
				{
					final String room = cmd.equals("MPTRANSFER") ? ((w.size() > 2) ? w.get(2) : "") : w.get(1);
					if((room.indexOf('#') >= 0) && (!variable(room)))
					{
						Room to = CMLib.map().getRoom(room);
						if((to == null) && room.startsWith("#") && (R != null))
							to = CMLib.map().getRoom(R.getArea().Name() + room);
						if(to == null)
							out.add(at + cmd + " " + room + ": no such room");
					}
				}
				else
				if(cmd.equals("MPOPEN") || cmd.equals("MPCLOSE") || cmd.equals("MPLOCK") || cmd.equals("MPUNLOCK"))
				{
					final String what = CMParms.combine(w, 1);
					final int d = CMLib.directions().getGoodDirectionCode(what);
					if((d >= 0) && (R != null) && (!variable(what)))
					{
						final Exit E = R.getRawExit(d);
						if(E == null)
							out.add(at + cmd + " " + what + ": no exit that way from " + R.roomID());
						else
						if(!E.hasADoor())
							out.add(at + cmd + " " + what + ": the exit from " + R.roomID() + " has no door");
						else
						if((cmd.equals("MPLOCK") || cmd.equals("MPUNLOCK")) && (!E.hasALock()))
							out.add(at + cmd + " " + what + ": the door from " + R.roomID() + " has no lock");
					}
				}
				else
				if((cmd.equals("MPOLOAD") || cmd.equals("MPMLOAD")) && (!w.get(1).equalsIgnoreCase("FROMFILE")))
				{
					String name = CMParms.combine(w, 1);
					final int into = name.toUpperCase().indexOf(" INTO ");
					if(into > 0)
						name = name.substring(0, into).trim();
					if(variable(name))
						continue;
					final boolean found = cmd.equals("MPOLOAD")
						? ((CMClass.getItem(name) != null) || (CMLib.catalog().getCatalogItem(name) != null))
						: ((CMClass.getMOB(name) != null) || (CMLib.catalog().getCatalogMob(name) != null));
					if(!found)
						out.add(at + cmd + " " + name + ": no " + (cmd.equals("MPOLOAD") ? "item" : "mob")
							+ " class or catalog entry of that name (the engine then looks for one in the world)");
				}
			}
		}
		return out;
	}

	/**
	 * The problems of an area's scripts: the area's, its rooms', exits', and
	 * the mobs' and items' in them.
	 * @param A the area
	 * @return the problems, each once
	 */
	public static List<String> checkArea(final Area A)
	{
		final Set<String> out = new LinkedHashSet<String>();
		out.addAll(checkHost(A, null));
		for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			out.addAll(checkHost(R, R));
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Exit E = R.getRawExit(d);
				if(E != null)
					out.addAll(checkHost(E, R));
			}
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || (!M.isMonster()))
					continue;
				out.addAll(checkHost(M, R));
				for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
					out.addAll(checkHost(i.nextElement(), R));
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				out.addAll(checkHost(i.nextElement(), R));
		}
		return new ArrayList<String>(out);
	}

	/**
	 * The problems of the whole world's scripts.
	 * @return the problems, each once
	 */
	public static List<String> checkAll()
	{
		final Set<String> out = new LinkedHashSet<String>();
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			out.addAll(checkArea(a.nextElement()));
		return new ArrayList<String>(out);
	}
}
