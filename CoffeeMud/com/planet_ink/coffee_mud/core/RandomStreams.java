package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

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
 * Q09: random streams for the optional modules, each its own sequence drawn
 * from the save's own seed: a loot roll never moves an encounter's dice, and
 * a stream's next draw is the same after a save and restore.  A draw's place
 * (seed, stream, counter) can be recorded and drawn again, for a scenario:
 * inspecting or previewing never draws.  Only engine draws are reproduced;
 * native and JavaScript code that uses other randomness is not.
 */
public final class RandomStreams
{
	private RandomStreams()
	{
	}

	/** the streams the modules use; others may be named by content */
	public static final String	LOOT		= "loot";
	public static final String	OFFERS		= "offers";
	public static final String	ENCOUNTERS	= "encounters";
	public static final String	EXPEDITIONS	= "expeditions";
	public static final String	PROJECTS	= "projects";
	public static final String	TRIGGERS	= "triggers";

	/** each stream's next counter */
	private static final Map<String, Long>	counters	= new TreeMap<String, Long>();
	/** the seed the streams are drawn from, fixed when first needed */
	private static Long						seed		= null;

	/** forgets the streams, at boot; a restore puts them back */
	public static synchronized void reset()
	{
		counters.clear();
		seed = null;
	}

	/** the streams' seed: the save's own, as SEED=GAME uses */
	public static synchronized long seed()
	{
		if(seed == null)
			seed = Long.valueOf(GenSeed.gameSeed());
		return seed.longValue();
	}

	private static String check(final String stream)
	{
		if((stream == null) || !stream.matches("[a-z][a-z0-9_.-]{0,63}"))
			throw new IllegalArgumentException("a stream name is lower-case letters, digits, '.', '_' and '-', starting with a letter");
		return stream;
	}

	/** SplitMix64's finalizer */
	private static long mix(long z)
	{
		z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
		z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
		return z ^ (z >>> 31);
	}

	/** a stable 64-bit hash of a stream name */
	private static long name(final String stream)
	{
		long h = 0xcbf29ce484222325L;
		for(final char c : stream.toCharArray())
		{
			h ^= c;
			h *= 0x100000001b3L;
		}
		return h;
	}

	/** the raw value at a place in a stream */
	public static long valueAt(final long seed, final String stream, final long counter)
	{
		return mix(mix(seed ^ name(check(stream))) + (counter * 0x9e3779b97f4a7c15L));
	}

	/** a whole number from 1 to n, from a raw value */
	public static int roll(final long raw, final int n)
	{
		if(n <= 1)
			return 1;
		return 1 + (int)Long.remainderUnsigned(raw, n);
	}

	/** where a stream's next draw is, without drawing */
	public static synchronized long counter(final String stream)
	{
		final Long c = counters.get(check(stream));
		return (c == null) ? 0 : c.longValue();
	}

	/** the next raw value of a stream; the stream moves on */
	public static synchronized long draw(final String stream)
	{
		final long c = counter(stream);
		counters.put(stream, Long.valueOf(c + 1));
		ModuleStats.count("streams", stream, "draws");
		return valueAt(seed(), stream, c);
	}

	/** the next whole number from 1 to n of a stream */
	public static int roll(final String stream, final int n)
	{
		return roll(draw(stream), n);
	}

	/** a percentage roll, 1 to 100 */
	public static int percent(final String stream)
	{
		return roll(stream, 100);
	}

	/**
	 * A cursor over a stream's draws from a place, that never moves the
	 * stream: for previews, samples and reproducing a scenario.
	 */
	public static final class Cursor
	{
		public final long	seed;
		public final String	stream;
		private long		at;

		public Cursor(final long seed, final String stream, final long counter)
		{
			this.seed = seed;
			this.stream = check(stream);
			this.at = counter;
		}

		public long draw()
		{
			return valueAt(seed, stream, at++);
		}

		public int roll(final int n)
		{
			return RandomStreams.roll(draw(), n);
		}

		public long counter()
		{
			return at;
		}
	}

	/** a cursor at a stream's next draw: what the stream would draw next */
	public static synchronized Cursor peek(final String stream)
	{
		return new Cursor(seed(), stream, counter(stream));
	}

	/**
	 * A scenario record: the place of a stream's next draw, with the rule
	 * versions it was made under, to draw the same values again.
	 * @param stream the stream
	 * @param rules the rule versions, such as module ID to version and pins
	 * @return the record
	 */
	public static synchronized JSONObject record(final String stream, final JSONObject rules)
	{
		final JSONObject r = new JSONObject();
		r.put("seed", Long.valueOf(seed()));
		r.put("stream", check(stream));
		r.put("counter", Long.valueOf(counter(stream)));
		r.put("rules", (rules == null) ? new JSONObject() : rules);
		return r;
	}

	/** a cursor at a recorded scenario's place */
	public static Cursor replay(final JSONObject record) throws IOException
	{
		ModuleDefinitions.keys(record, "scenario", "seed", "stream", "counter", "rules");
		if(!(record.get("seed") instanceof Number) || !(record.get("counter") instanceof Number))
			throw new IOException("a scenario's seed and counter must be numbers");
		final long counter = ((Number)record.get("counter")).longValue();
		if(counter < 0)
			throw new IOException("a scenario's counter cannot be negative");
		try
		{
			return new Cursor(((Number)record.get("seed")).longValue(), ModuleDefinitions.string(record.get("stream"), "stream"), counter);
		}
		catch(final IllegalArgumentException e)
		{
			throw new IOException(e.getMessage());
		}
	}

	/** the streams for the save's header: their counters, or "" when none drew */
	public static synchronized String saved()
	{
		if(counters.isEmpty())
			return "";
		final JSONObject o = new JSONObject();
		for(final Map.Entry<String, Long> e : counters.entrySet())
			o.put(e.getKey(), e.getValue());
		return ModuleDefinitions.json(o);
	}

	/** puts back the streams from the save's header (null or "" for none) */
	public static synchronized void restore(final String text)
	{
		counters.clear();
		seed = null;
		if((text == null) || (text.trim().length() == 0))
			return;
		try
		{
			final JSONObject o = ModuleDefinitions.parse(text);
			for(final String k : o.keySet())
			{
				final Object v = o.get(k);
				if((v instanceof Number) && (((Number)v).longValue() >= 0))
					counters.put(check(k), Long.valueOf(((Number)v).longValue()));
			}
		}
		catch(final Exception e)
		{
			Log.errOut("RandomStreams", "the save's random streams could not be read, and start again: " + e.getMessage());
			counters.clear();
		}
	}

	/** the streams, for inspection */
	public static synchronized String describe()
	{
		if(counters.isEmpty())
			return "No module random stream has drawn yet (seed " + seed() + ").";
		final StringBuilder s = new StringBuilder("Random streams (seed " + seed() + "):");
		for(final Map.Entry<String, Long> e : counters.entrySet())
			s.append("\n  ").append(e.getKey()).append(": ").append(e.getValue()).append(" drawn");
		return s.toString();
	}
}
