package com.planet_ink.coffee_mud.core;

import java.lang.ref.WeakReference;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;

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
 * TRACEB (DT19): the decisions of one host's timed behaviours, told to the
 * builder for a while: when each one's wait is up, whether it acts, could
 * not act, or lost its chance roll.
 */
public final class BehaviorTrace
{
	private BehaviorTrace()
	{
	}

	private static volatile boolean			on		= false;
	private static WeakReference<MOB>		watcher	= null;
	private static WeakReference<Object>	host	= null;
	private static long						until	= 0;

	/**
	 * Starts telling a builder a host's behaviours' decisions.
	 * @param who the builder
	 * @param what the host
	 * @param ticks for how many ticks of game time
	 */
	public static synchronized void start(final MOB who, final Object what, final int ticks)
	{
		watcher = new WeakReference<MOB>(who);
		host = new WeakReference<Object>(what);
		until = GameTime.now() + ((long)Math.max(1, ticks) * CMProps.getTickMillis());
		on = true;
	}

	/**
	 * Stops it.
	 */
	public static synchronized void stop()
	{
		on = false;
		watcher = null;
		host = null;
	}

	/**
	 * Whether a host's behaviours are traced now.
	 * @param what the host
	 * @return true if they are
	 */
	public static boolean traces(final Object what)
	{
		if(!on)
			return false;
		if(GameTime.now() > until)
		{
			final MOB M = (watcher == null) ? null : watcher.get();
			stop();
			if(M != null)
				M.tell("^w[traceb] the trace is over.^N");
			return false;
		}
		final Object h = (host == null) ? null : host.get();
		return (h != null) && (h == what);
	}

	/**
	 * Tells the builder a decision.
	 * @param what the host
	 * @param behavior the behaviour's ID
	 * @param line what it decided
	 */
	public static void tell(final Object what, final String behavior, final String line)
	{
		final MOB M = (watcher == null) ? null : watcher.get();
		if(M == null)
			return;
		final String name = (what instanceof Environmental) ? ((Environmental)what).Name() : "";
		M.tell("^w[traceb " + name + "] " + behavior + ": " + line + "^N");
	}
}
