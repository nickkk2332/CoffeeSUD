package com.planet_ink.coffee_mud.core;

import java.lang.ref.WeakReference;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;
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
 * SCRIPTTRACE (DT18): the scripts of one host, of the hosts in a room, or in
 * an area, traced to the builder's own session, not the log: each trigger's
 * chance rolled and whether it ran, a trigger turned off, a host too busy to
 * be triggered, each line run and each variable changed.  One builder, one
 * trace at a time; not saved.
 */
public final class ScriptTrace
{
	private ScriptTrace()
	{
	}

	/** what a trace covers */
	public enum Kind
	{
		HOST, ROOM, AREA
	}

	private static volatile boolean				on			= false;
	private static WeakReference<MOB>			watcher		= null;
	private static WeakReference<Object>		target		= null;
	private static Kind							kind		= Kind.HOST;
	/** the trigger code traced, 0 for all */
	private static int							trigger		= 0;
	private static String						lastTold	= null;
	/** the host whose script runs on this thread, while a trace is on */
	private static final ThreadLocal<Object>	running		= new ThreadLocal<Object>();
	/** the host whose messages and ticks this thread is handling */
	private static final ThreadLocal<Object>	handling	= new ThreadLocal<Object>();

	/**
	 * Whether any trace is on (a check that costs nothing when none is).
	 * @return true if one is
	 */
	public static boolean isOn()
	{
		return on;
	}

	/**
	 * Starts a trace, replacing any other.
	 * @param who the builder told
	 * @param what the host, room or area
	 * @param k what it is
	 * @param triggerCode the trigger traced, 0 for all
	 */
	public static synchronized void start(final MOB who, final Object what, final Kind k, final int triggerCode)
	{
		watcher = new WeakReference<MOB>(who);
		target = new WeakReference<Object>(what);
		kind = k;
		trigger = triggerCode;
		lastTold = null;
		on = true;
	}

	/**
	 * Stops the trace.
	 */
	public static synchronized void stop()
	{
		on = false;
		watcher = null;
		target = null;
	}

	/**
	 * MPSETINTERNAL TRACE TRUE: a script traces its own host, to the builder
	 * (in builder mode or a DEV=1 game, the one playing).
	 * @param host the script's host
	 * @return true if there was a builder to tell
	 */
	public static boolean startFromScript(final Object host)
	{
		if((!SaveGame.isBuilderMode()) && (!SaveGame.isDevMode()))
			return false;
		for(final Session S : CMLib.sessions().allIterable())
		{
			if(S.mob() != null)
			{
				start(S.mob(), host, Kind.HOST, 0);
				return true;
			}
		}
		return false;
	}

	/**
	 * What the trace covers, for SCRIPTTRACE alone.
	 * @return a description, or "off"
	 */
	public static synchronized String describe()
	{
		final Object t = (target == null) ? null : target.get();
		if((!on) || (t == null))
			return "off";
		final String name = (t instanceof Environmental) ? ((Environmental)t).Name() : String.valueOf(t);
		return kind.name().toLowerCase() + " " + name + ((trigger > 0) ? (", " + com.planet_ink.coffee_mud.Common.interfaces.ScriptingEngine.progs[trigger - 1] + " only") : "");
	}

	/**
	 * Whether a host's trigger is traced.
	 * @param host the host
	 * @param triggerCode its trigger code (0 for any)
	 * @return true if it is
	 */
	public static boolean traces(final Object host, final int triggerCode)
	{
		if((!on) || (host == null))
			return false;
		final Object t = (target == null) ? null : target.get();
		if(t == null)
			return false;
		if((trigger > 0) && (triggerCode > 0) && (triggerCode != trigger))
			return false;
		switch(kind)
		{
		case HOST:
			return host == t;
		case ROOM:
			return (host == t) || ((host instanceof Environmental) && (CMLib.map().roomLocation((Environmental)host) == t));
		case AREA:
		{
			if(host == t)
				return true;
			final Room R = (host instanceof Environmental) ? CMLib.map().roomLocation((Environmental)host) : null;
			return (R != null) && (R.getArea() == t);
		}
		default:
			return false;
		}
	}

	/**
	 * Tells the builder a line of the trace (a line the same as the last is
	 * told once).
	 * @param host the host it is about
	 * @param line the line
	 */
	public static void tell(final Object host, final String line)
	{
		final MOB M;
		synchronized(ScriptTrace.class)
		{
			M = (watcher == null) ? null : watcher.get();
			if(M == null)
				return;
			final String name = (host instanceof Environmental) ? ((Environmental)host).Name() : "";
			final String full = "^w[trace " + name + "] " + line + "^N";
			if(full.equals(lastTold))
				return;
			lastTold = full;
			try
			{
				M.tell(full);
			}
			catch(final RuntimeException e)
			{
				// a builder who cannot be told misses the line
			}
		}
	}

	/**
	 * The script engine is about to run a host's script on this thread.
	 * @param host the host
	 * @return the host that was running before, to give back to {@link #endRun(Object)}
	 */
	public static Object beginRun(final Object host)
	{
		final Object was = running.get();
		running.set(host);
		return was;
	}

	/**
	 * The script ran.
	 * @param was what {@link #beginRun(Object)} returned
	 */
	public static void endRun(final Object was)
	{
		if(was == null)
			running.remove();
		else
			running.set(was);
	}

	/**
	 * A script variable changed: told if the script that changed it is traced.
	 * @param host the variable's host
	 * @param name its name
	 * @param value its new value
	 */
	public static void varChanged(final String host, final String name, final String value)
	{
		final Object r = running.get();
		if((r != null) && traces(r, 0))
			tell(r, "  " + host + " " + name.toUpperCase() + " = '" + value + "'");
	}

	/**
	 * The engine handles a host's message or tick on this thread.
	 * @param host the host
	 */
	public static void handling(final Object host)
	{
		handling.set(host);
	}

	/**
	 * The host whose message or tick this thread handles, if a trace is on.
	 * @return the host, or null
	 */
	public static Object handled()
	{
		return on ? handling.get() : null;
	}

	/**
	 * The area of a room, or the area itself.
	 * @param o a room or an area
	 * @return the area, or null
	 */
	public static Area areaOf(final Object o)
	{
		if(o instanceof Area)
			return (Area)o;
		if(o instanceof Room)
			return ((Room)o).getArea();
		return null;
	}
}
