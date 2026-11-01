package com.planet_ink.coffee_mud.core;

import java.util.*;

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
 * SCRIPTPROF (DT34): for each script file and trigger line, how often it
 * ran, for how long in all and at most, and the host of its worst run; and,
 * with SCRIPTSLOW= in the ini (milliseconds), a warning in mud.log for each
 * trigger run longer than that.  Only whole trigger runs are timed, not the
 * functions and loops they call, which count in their trigger's time.
 */
public final class ScriptProf
{
	private ScriptProf()
	{
	}

	/** what is known of one trigger line */
	public static final class Entry
	{
		/** file:line and the trigger */
		public final String	key;
		/** the runs timed */
		public long			runs		= 0;
		/** nanoseconds, in all */
		public long			totalNs		= 0;
		/** nanoseconds, the longest run */
		public long			maxNs		= 0;
		/** the host of the longest run, and where it was */
		public String		worstHost	= "";
		/** the area of the last host to run it */
		public String		area		= "";

		Entry(final String key)
		{
			this.key = key;
		}
	}

	private static volatile boolean			on		= false;
	private static final Map<String, Entry>	entries	= new HashMap<String, Entry>();
	private static final ThreadLocal<int[]>	depth	= new ThreadLocal<int[]>()
	{
		@Override
		protected int[] initialValue()
		{
			return new int[1];
		}
	};
	private static volatile long			slowMs	= -1;

	/**
	 * Whether runs are being counted (SCRIPTPROF ON).
	 * @return true if they are
	 */
	public static boolean isOn()
	{
		return on;
	}

	/**
	 * Starts or stops the counting.
	 * @param yes true to count
	 */
	public static void setOn(final boolean yes)
	{
		on = yes;
	}

	/**
	 * Forgets what was counted.
	 */
	public static synchronized void reset()
	{
		entries.clear();
	}

	/**
	 * SCRIPTSLOW= in the ini: a trigger run longer than this many
	 * milliseconds is logged.
	 * @return the milliseconds, or 0 for none
	 */
	public static long slowMs()
	{
		if(slowMs < 0)
			slowMs = Math.max(0, CMath.s_long(CMProps.instance().getStr("SCRIPTSLOW", "0").trim()));
		return slowMs;
	}

	/**
	 * Sets SCRIPTSLOW for this game.
	 * @param ms the milliseconds, or 0 for none
	 */
	public static void setSlowMs(final long ms)
	{
		slowMs = Math.max(0, ms);
	}

	/**
	 * Whether a run should be timed at all.
	 * @return true while counting or with SCRIPTSLOW set
	 */
	public static boolean wanted()
	{
		return on || (slowMs() > 0);
	}

	/**
	 * A run starts: whether it is a trigger's own, not one inside another's.
	 * @return true for the outermost run on this thread
	 */
	public static boolean enter()
	{
		return (depth.get()[0]++) == 0;
	}

	/**
	 * A run ends.
	 */
	public static void leave()
	{
		depth.get()[0]--;
	}

	/**
	 * Counts a trigger's run, and logs it if it was slow.
	 * @param key the trigger's file:line and line
	 * @param host the host and its room
	 * @param area the host's area
	 * @param ns how long it took
	 */
	public static void record(final String key, final String host, final String area, final long ns)
	{
		if(on)
		{
			synchronized(ScriptProf.class)
			{
				Entry E = entries.get(key);
				if(E == null)
				{
					E = new Entry(key);
					entries.put(key, E);
				}
				E.runs++;
				E.totalNs += ns;
				if(ns >= E.maxNs)
				{
					E.maxNs = ns;
					E.worstHost = host;
				}
				E.area = area;
			}
		}
		final long slow = slowMs();
		if((slow > 0) && (ns >= slow * 1000000L))
			Log.warnOut("ScriptProf", "slow script: " + (ns / 1000000L) + "ms, " + key + " (" + host + ")");
	}

	private static String ms(final long ns)
	{
		final double d = ns / 1000000.0;
		return (d >= 10) ? Long.toString(Math.round(d)) : String.format(Locale.ROOT, "%.2f", Double.valueOf(d));
	}

	/**
	 * SCRIPTPROF REPORT: the trigger lines that took longest in all.
	 * @param n how many
	 * @param area only those whose host was last in this area, or null
	 * @return the lines
	 */
	public static synchronized String report(final int n, final String area)
	{
		final List<Entry> list = new ArrayList<Entry>();
		long all = 0;
		for(final Entry E : entries.values())
		{
			if((area != null) && (!E.area.equalsIgnoreCase(area)))
				continue;
			list.add(E);
			all += E.totalNs;
		}
		Collections.sort(list, new Comparator<Entry>()
		{
			@Override
			public int compare(final Entry a, final Entry b)
			{
				return Long.compare(b.totalNs, a.totalNs);
			}
		});
		final StringBuilder str = new StringBuilder("SCRIPTPROF" + (on ? "" : " (off)") + ": " + list.size() + " trigger lines, " + ms(all) + "ms in all"
			+ ((area == null) ? "" : (" in " + area)) + ".");
		for(int i = 0; (i < list.size()) && (i < n); i++)
		{
			final Entry E = list.get(i);
			final long pct = (all <= 0) ? 0 : Math.round(100.0 * E.totalNs / all);
			str.append("\n\r  ").append(pct).append("% ").append(ms(E.totalNs)).append("ms in ").append(E.runs).append(" runs, at most ")
				.append(ms(E.maxNs)).append("ms (").append(E.worstHost).append("): ").append(E.key);
		}
		return str.toString();
	}
}
