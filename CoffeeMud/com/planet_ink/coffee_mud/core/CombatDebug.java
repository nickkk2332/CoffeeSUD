package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.lang.ref.WeakReference;
import java.nio.charset.StandardCharsets;
import java.util.*;

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
 * COMBATDEBUG (DT35): each attack involving a watched character, explained
 * on one line to the builder who asked: the attack against the armor, the
 * hit chance and the roll, the damage of each formula (TARGETED, CRIT,
 * FUDGE), each effect that changed the damage and what it made of it, and
 * what was dealt.  The combat library and the characters note the steps
 * while an attack is explained; a line may also go to a file, for long runs.
 */
public final class CombatDebug
{
	private CombatDebug()
	{
	}

	private static volatile boolean						on			= false;
	private static volatile boolean						everyone	= false;
	private static final Map<MOB, Boolean>				watched		= Collections.synchronizedMap(new WeakHashMap<MOB, Boolean>());
	private static volatile WeakReference<MOB>			watcher		= null;
	private static volatile File						logFile		= null;
	private static final ThreadLocal<StringBuilder>		line		= new ThreadLocal<StringBuilder>();
	private static final ThreadLocal<int[]>				depth		= new ThreadLocal<int[]>()
	{
		@Override
		protected int[] initialValue()
		{
			return new int[1];
		}
	};

	/**
	 * Whether anything is being explained.
	 * @return true if COMBATDEBUG is on
	 */
	public static boolean isOn()
	{
		return on;
	}

	/**
	 * COMBATDEBUG ON: attacks by or on a character, or all, are explained to
	 * the builder.
	 * @param builder who is told
	 * @param target the character, or null for every attack
	 */
	public static synchronized void watch(final MOB builder, final MOB target)
	{
		watcher = new WeakReference<MOB>(builder);
		if(target == null)
			everyone = true;
		else
			watched.put(target, Boolean.TRUE);
		on = true;
	}

	/**
	 * COMBATDEBUG OFF: a character no longer watched, or none.
	 * @param target the character, or null for all
	 */
	public static synchronized void unwatch(final MOB target)
	{
		if(target == null)
		{
			watched.clear();
			everyone = false;
		}
		else
			watched.remove(target);
		on = everyone || (!watched.isEmpty());
	}

	/**
	 * Sets the file the lines go to as well, or none.
	 * @param F the file, or null
	 */
	public static void setLogFile(final File F)
	{
		logFile = F;
	}

	/**
	 * The file the lines go to as well.
	 * @return the file, or null
	 */
	public static File logFile()
	{
		return logFile;
	}

	/**
	 * Who is watched, as COMBATDEBUG shows it.
	 * @return the words
	 */
	public static synchronized String watching()
	{
		if(!on)
			return "off";
		if(everyone)
			return "every attack";
		final List<String> names = new ArrayList<String>();
		for(final MOB M : new ArrayList<MOB>(watched.keySet()))
		{
			if(M != null)
				names.add(M.name());
		}
		return "attacks by or on " + String.join(", ", names);
	}

	/**
	 * Whether an attack between two is explained.
	 * @param a the attacker
	 * @param b the one attacked
	 * @return true if it is
	 */
	public static boolean involves(final MOB a, final MOB b)
	{
		if(!on)
			return false;
		return everyone || ((a != null) && watched.containsKey(a)) || ((b != null) && watched.containsKey(b));
	}

	/**
	 * An attack starts: its steps go on one line until it ends.
	 * @param a the attacker
	 * @param b the one attacked
	 */
	public static void begin(final MOB a, final MOB b)
	{
		if(!involves(a, b))
			return;
		if((depth.get()[0]++) == 0)
			line.set(new StringBuilder("[dbg] " + a.name() + " -> " + b.name() + ":"));
	}

	/**
	 * An attack ends: its line is told.
	 * @param a the attacker
	 * @param b the one attacked
	 */
	public static void end(final MOB a, final MOB b)
	{
		if(line.get() == null)
			return;
		if((--depth.get()[0]) > 0)
			return;
		final StringBuilder L = line.get();
		line.remove();
		depth.get()[0] = 0;
		if(L.toString().endsWith(":"))
			return; // nothing came of it
		emit(L.toString());
	}

	/**
	 * A step of an attack, or of damage done outside one (a spell's).
	 * @param a the attacker
	 * @param b the one attacked
	 * @param what the step
	 */
	public static void note(final MOB a, final MOB b, final String what)
	{
		final StringBuilder L = line.get();
		if(L != null)
		{
			L.append(L.toString().endsWith(":") ? " " : "; ").append(what);
			return;
		}
		if(involves(a, b))
			emit("[dbg] " + ((a == null) ? "?" : a.name()) + " -> " + ((b == null) ? "?" : b.name()) + ": " + what);
	}

	private static void emit(final String s)
	{
		final WeakReference<MOB> W = watcher;
		final MOB M = (W == null) ? null : W.get();
		if(M != null)
			M.tell(s);
		final File F = logFile;
		if(F != null)
		{
			try(Writer out = new OutputStreamWriter(new FileOutputStream(F, true), StandardCharsets.UTF_8))
			{
				out.write(s + "\n");
			}
			catch(final IOException e)
			{
				Log.errOut("CombatDebug", F.getPath() + ": " + e.getMessage());
				logFile = null;
			}
		}
	}
}
