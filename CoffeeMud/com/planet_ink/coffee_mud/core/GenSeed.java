package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;

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
 * G1: seeds for repeatable generation.  A seeded run keeps the shared
 * generator's state, seeds it, builds, then puts the state back, so the
 * same seed builds the same thing and the game's own rolls go on as if
 * nothing had been built.  GENERATE SEED=n, a seed="..." attribute on an
 * AREA, ROOM, MOB or ITEM template (math, with its variables: $[$GAME_DAY]
 * for one a day), and the SEED variable of StdAutoGenInstance and AutoGrid
 * (a number, GAME for the save's own seed, ENTRY for a new one each time).
 */
public final class GenSeed
{
	private GenSeed()
	{
	}

	/** the save's own seed, for SEED=GAME, or null until one is needed */
	private static volatile Long	gameSeed	= null;
	/** how many seeded runs are under way */
	private static volatile int		seededRuns	= 0;
	/** the seed of the last seeded run */
	private static volatile long	last		= 0;
	/** this thread's seeded runs, inside each other: what each was inside, its seed, its restarts */
	private static final ThreadLocal<Deque<Object[]>>	ownRuns	= ThreadLocal.withInitial(() -> new ArrayDeque<Object[]>());

	/**
	 * @return a new seed, short enough to read and type
	 */
	public static long fresh()
	{
		return 1 + CMath.getRand().nextInt(999999);
	}

	/**
	 * @return the save's own seed, made the first time it is asked for
	 */
	public static long gameSeed()
	{
		if(gameSeed == null)
			gameSeed = Long.valueOf(fresh());
		return gameSeed.longValue();
	}

	/**
	 * @return the save's own seed, for the save's header, or ""
	 */
	public static String saved()
	{
		return (gameSeed == null) ? "" : gameSeed.toString();
	}

	/**
	 * Puts back the save's own seed, from the save's header.
	 * @param s the header's value, or null for a new game
	 */
	public static void restore(final String s)
	{
		gameSeed = ((s != null) && CMath.isLong(s.trim())) ? Long.valueOf(s.trim()) : null;
	}

	/**
	 * A seed from its text: a number or math, GAME (the save's own seed, with
	 * the name, so each thing named has its own), ENTRY or RANDOM or nothing
	 * (a new one), else the text's own hash.
	 * @param text the text
	 * @param name a name to mix into GAME, or null
	 * @return the seed
	 */
	public static long pick(final String text, final String name)
	{
		final String t = (text == null) ? "" : text.trim();
		if((t.length() == 0) || t.equalsIgnoreCase("ENTRY") || t.equalsIgnoreCase("RANDOM"))
			return fresh();
		if(t.equalsIgnoreCase("GAME"))
			return (gameSeed() ^ ((name == null) ? 0 : name.toUpperCase().hashCode())) & 0x7fffffffL;
		if(CMath.isLong(t))
			return CMath.s_long(t);
		if(CMath.isMathExpression(t))
			return CMath.s_parseLongExpression(t);
		return t.hashCode() & 0x7fffffffL;
	}

	/**
	 * Starts a seeded run: the shared generator's state is kept, and it is
	 * seeded.  End it with {@link #end(Long)}, in a finally.
	 * @param seed the seed
	 * @return the state to put back
	 */
	public static synchronized Long begin(final long seed)
	{
		final Long before = CMath.getRandState();
		seededRuns++;
		last = seed;
		final Random r = CMath.getRand();
		if(r instanceof GameRandom) // its own rolls on this thread, which other threads cannot shift
			ownRuns.get().push(new Object[] { ((GameRandom)r).beginOwn(seed), Long.valueOf(seed), new long[] { 0 } });
		else
			CMath.seedRand(seed);
		return before;
	}

	/**
	 * G1: inside a seeded run on this thread, starts its rolls again from a
	 * value its seed gives (and how many times this was done), so that a step
	 * run after other work, such as the generator's post-processing after
	 * the new place is linked and shown, rolls the same whatever that work
	 * rolled.  Nothing outside a seeded run.
	 */
	public static void restart()
	{
		final Deque<Object[]> runs = ownRuns.get();
		final Random r = CMath.getRand();
		if(runs.isEmpty() || !(r instanceof GameRandom))
			return;
		final Object[] run = runs.peek();
		final long n = ++((long[])run[2])[0];
		((GameRandom)r).restartOwn((((Long)run[1]).longValue() * 0x9E3779B97F4A7C15L) + n);
	}

	/**
	 * G1: runs something with the game's shared rolls, outside this thread's
	 * seeded run, so that a one-time setup it does (a crafting skill's worker
	 * put in a random room) does not use up the run's rolls the first time
	 * only, which made the first build of a seed differ from the next.
	 * @param <T> what it gives
	 * @param what the thing to run
	 * @return what it gave
	 */
	public static <T> T unseeded(final java.util.function.Supplier<T> what)
	{
		final Random r = CMath.getRand();
		if(!(r instanceof GameRandom))
			return what.get();
		final long[] cur = ((GameRandom)r).pauseOwn();
		try
		{
			return what.get();
		}
		finally
		{
			((GameRandom)r).endOwn(cur);
		}
	}

	/**
	 * Ends a seeded run, putting back the shared generator's state.
	 * @param before what {@link #begin(long)} gave
	 */
	public static synchronized void end(final Long before)
	{
		seededRuns = Math.max(0, seededRuns - 1);
		final Random r = CMath.getRand();
		final Deque<Object[]> runs = ownRuns.get();
		if((r instanceof GameRandom) && (runs.size() > 0))
			((GameRandom)r).endOwn((long[])runs.pop()[0]);
		if(before != null)
			CMath.setRandState(before.longValue());
	}

	/**
	 * @return true while a seeded run is under way: the generator then skips its item cache, which would roll otherwise
	 */
	public static boolean seeded()
	{
		return seededRuns > 0;
	}

	/**
	 * @return the seed of the last seeded run
	 */
	public static long last()
	{
		return last;
	}

	/**
	 * The variables every generation sees: GAME_DAY, the global clock's day
	 * counted from its year 0 (for a dungeon of the day).
	 * @param defined the generator's variables
	 */
	public static void context(final java.util.Map<String, Object> defined)
	{
		if(!defined.containsKey("GAME_DAY"))
		{
			final com.planet_ink.coffee_mud.Common.interfaces.TimeClock C = CMLib.time().globalClock();
			defined.put("GAME_DAY", "" + Holidays.dayNumber(C));
		}
	}
}
