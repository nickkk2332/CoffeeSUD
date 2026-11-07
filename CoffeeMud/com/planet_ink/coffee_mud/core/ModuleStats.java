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
 * Q12: what the optional modules did in this session, counted by family,
 * source and measure: actors spawned, trigger roots and actions, budget
 * refusals, transactions prepared, committed, cancelled and refused,
 * duplicate events suppressed, catch-up steps, and the time some took.  The
 * counts are bounded, never saved, and name definitions, not characters.
 */
public final class ModuleStats
{
	private ModuleStats()
	{
	}

	/** the most distinct family/source/measure keys kept */
	public static final int	MAX_KEYS	= 1024;

	private static final class Count
	{
		long	n		= 0;
		long	nanos	= 0;
		long	max		= 0;
	}

	private static final Map<String, Count>	counts	= new TreeMap<String, Count>();
	private static long						dropped	= 0;
	/** declared limits: family/measure to the most allowed in one step */
	private static final Map<String, Long>	limits	= new TreeMap<String, Long>();
	/** each limit's highest reading */
	private static final Map<String, Long>	peaks	= new TreeMap<String, Long>();

	public static synchronized void reset()
	{
		counts.clear();
		peaks.clear();
		dropped = 0;
	}

	private static String key(final String family, final String source, final String measure)
	{
		return family + " | " + ((source == null) || (source.length() == 0) ? "-" : source) + " | " + measure;
	}

	private static Count get(final String key)
	{
		Count c = counts.get(key);
		if(c == null)
		{
			if(counts.size() >= MAX_KEYS)
			{
				dropped++;
				return null;
			}
			c = new Count();
			counts.put(key, c);
		}
		return c;
	}

	/** counts one of a measure */
	public static void count(final String family, final String source, final String measure)
	{
		add(family, source, measure, 1);
	}

	/** counts n of a measure */
	public static synchronized void add(final String family, final String source, final String measure, final long n)
	{
		final Count c = get(key(family, source, measure));
		if(c != null)
			c.n += n;
	}

	/** counts one of a measure that took some time */
	public static synchronized void time(final String family, final String source, final String measure, final long nanos)
	{
		final Count c = get(key(family, source, measure));
		if(c != null)
		{
			c.n++;
			c.nanos += nanos;
			c.max = Math.max(c.max, nanos);
		}
	}

	/** the count of a measure, for tests and limits */
	public static synchronized long value(final String family, final String source, final String measure)
	{
		final Count c = counts.get(key(family, source, measure));
		return (c == null) ? 0 : c.n;
	}

	/**
	 * Declares the most of a measure one step may do, such as the actors an
	 * encounter may have at once; {@link #reading} compares against it.
	 */
	public static synchronized void limit(final String family, final String measure, final long most)
	{
		limits.put(family + " | " + measure, Long.valueOf(most));
	}

	/**
	 * Records one step's reading of a limited measure.
	 * @return true if it was within the declared limit (or none is declared)
	 */
	public static synchronized boolean reading(final String family, final String source, final String measure, final long value)
	{
		final String k = family + " | " + measure;
		final Long peak = peaks.get(k + " | " + source);
		if((peak == null) || (value > peak.longValue()))
			peaks.put(k + " | " + source, Long.valueOf(value));
		final Long most = limits.get(k);
		if((most != null) && (value > most.longValue()))
		{
			add(family, source, measure + " over limit", 1);
			return false;
		}
		return true;
	}

	/** the report of WORLD MODULES REPORT */
	public static synchronized String report()
	{
		if(counts.isEmpty() && peaks.isEmpty())
			return "No optional module work has been counted in this session.";
		final StringBuilder s = new StringBuilder("Optional module work in this session (family | source | measure: count):");
		for(final Map.Entry<String, Count> e : counts.entrySet())
		{
			final Count c = e.getValue();
			s.append("\n  ").append(e.getKey()).append(": ").append(c.n);
			if(c.nanos > 0)
				s.append(" (").append(String.format(Locale.ROOT, "%.2f", c.nanos / 1e6)).append(" ms, longest ")
				 .append(String.format(Locale.ROOT, "%.2f", c.max / 1e6)).append(" ms)");
		}
		if(!peaks.isEmpty())
		{
			s.append("\nHighest readings against declared limits:");
			for(final Map.Entry<String, Long> e : peaks.entrySet())
			{
				final String k = e.getKey();
				final String limitKey = k.substring(0, k.lastIndexOf(" | "));
				final Long most = limits.get(limitKey);
				s.append("\n  ").append(k).append(": ").append(e.getValue()).append((most == null) ? " (no limit)" : (" of " + most));
			}
		}
		if(dropped > 0)
			s.append("\n(").append(dropped).append(" counts were not kept: more than ").append(MAX_KEYS).append(" kinds.)");
		return s.toString();
	}
}
