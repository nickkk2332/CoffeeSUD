package com.planet_ink.coffee_mud.core;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

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
 * The script variables a builder watches (VARS WATCH host name): each change
 * to one is told to them, with the scope, the host, the new value and where
 * the script that made it came from.  A host or a name of * matches any.
 */
public final class ScriptVarWatch
{
	private static final class Watch
	{
		final WeakReference<MOB>	who;
		final String				host;
		final String				name;

		Watch(final MOB who, final String host, final String name)
		{
			this.who = new WeakReference<MOB>(who);
			this.host = host;
			this.name = name;
		}
	}

	private static final List<Watch>	watches	= new ArrayList<Watch>();
	/** whether any watch is set, so that a change costs nothing when none is */
	private static volatile boolean		any		= false;

	private ScriptVarWatch()
	{
	}

	/**
	 * Starts telling a character of each change to a variable.
	 * @param who the character
	 * @param host the host, or * for any
	 * @param name the variable, or * for any
	 */
	public static void watch(final MOB who, final String host, final String name)
	{
		synchronized(watches)
		{
			watches.add(new Watch(who, host.trim(), name.trim().toUpperCase()));
			any = true;
		}
	}

	/**
	 * Stops all of a character's watches.
	 * @param who the character
	 * @return how many there were
	 */
	public static int unwatch(final MOB who)
	{
		int n = 0;
		synchronized(watches)
		{
			for(int i = watches.size() - 1; i >= 0; i--)
			{
				final MOB M = watches.get(i).who.get();
				if((M == null) || (M == who))
				{
					if(M == who)
						n++;
					watches.remove(i);
				}
			}
			any = watches.size() > 0;
		}
		return n;
	}

	/**
	 * Returns a character's watches, as "host name" lines.
	 * @param who the character
	 * @return the watches
	 */
	public static List<String> watchesOf(final MOB who)
	{
		final List<String> out = new ArrayList<String>();
		synchronized(watches)
		{
			for(final Watch W : watches)
			{
				if(W.who.get() == who)
					out.add(W.host + " " + W.name);
			}
		}
		return out;
	}

	/**
	 * A script variable changed: tells those who watch it.
	 * @param scope the variable's scope ("" for the global one)
	 * @param host its host
	 * @param name its name
	 * @param value its new value ("" once cleared)
	 * @param where where the script that changed it came from
	 */
	public static void changed(final String scope, final String host, final String name, final String value, final String where)
	{
		if(!any)
			return;
		final List<MOB> tell = new ArrayList<MOB>();
		synchronized(watches)
		{
			for(final Watch W : watches)
			{
				final MOB M = W.who.get();
				if((M != null)
				&&(W.host.equals("*") || W.host.equalsIgnoreCase(host))
				&&(W.name.equals("*") || W.name.equalsIgnoreCase(name))
				&&(!tell.contains(M)))
					tell.add(M);
			}
		}
		final String scopeName = ((scope == null) || (scope.length() == 0)) ? "GLOBAL" : scope;
		final String line = "^HVARS^N " + scopeName + " " + host + " " + name.toUpperCase() + " = '" + value + "'"
			+ ((where == null || where.length() == 0) ? "" : " (" + where + ")");
		for(final MOB M : tell)
			M.tell(line);
	}
}
