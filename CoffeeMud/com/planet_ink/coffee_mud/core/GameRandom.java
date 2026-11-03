package com.planet_ink.coffee_mud.core;

import java.util.Random;

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
 * The game's random number generator.  It rolls exactly as java.util.Random
 * does for the same seed, but its state can be read and set, so that a saved
 * game goes on rolling where it left off instead of starting over.
 */
public class GameRandom extends Random
{
	private static final long	serialVersionUID	= 1L;
	private static final long	MULTIPLIER			= 0x5DEECE66DL;
	private static final long	ADDEND				= 0xBL;
	private static final long	MASK				= (1L << 48) - 1;

	/** the generator's state; set by setSeed, which Random's constructor calls */
	private long				state;

	/** G1: a seeded run's own state, on the thread doing it, so that other threads' rolls cannot shift it */
	private final transient ThreadLocal<long[]>	own	= new ThreadLocal<long[]>();

	/**
	 * Makes a generator from the given seed.
	 * @param seed the seed
	 */
	public GameRandom(final long seed)
	{
		super(seed);
	}

	@Override
	public synchronized void setSeed(final long seed)
	{
		state = (seed ^ MULTIPLIER) & MASK;
		super.setSeed(seed);
	}

	@Override
	protected int next(final int bits)
	{
		final long[] o = (own == null) ? null : own.get();
		if(o != null)
		{
			o[0] = (o[0] * MULTIPLIER + ADDEND) & MASK;
			return (int)(o[0] >>> (48 - bits));
		}
		synchronized(this)
		{
			state = (state * MULTIPLIER + ADDEND) & MASK;
			return (int)(state >>> (48 - bits));
		}
	}

	/**
	 * G1: starts a seeded run on this thread.  Its rolls come from their own
	 * state, started from the seed, and leave the shared state alone, so
	 * that the game's other threads, rolling meanwhile, cannot change what
	 * the run makes.
	 * @param seed the seed
	 * @return the run this one is inside, or null, for {@link #endOwn(long[])}
	 */
	public long[] beginOwn(final long seed)
	{
		final long[] prev = own.get();
		own.set(new long[] { (seed ^ MULTIPLIER) & MASK });
		return prev;
	}

	/**
	 * G1: steps out of this thread's seeded run, for {@link #endOwn(long[])}
	 * to step back in.
	 * @return the run stepped out of, or null
	 */
	public long[] pauseOwn()
	{
		final long[] cur = own.get();
		own.remove();
		return cur;
	}

	/**
	 * G1: starts this thread's seeded run's rolls again from the given seed;
	 * nothing outside one.
	 * @param seed the seed
	 */
	public void restartOwn(final long seed)
	{
		final long[] o = own.get();
		if(o != null)
			o[0] = (seed ^ MULTIPLIER) & MASK;
	}

	/**
	 * G1: ends a seeded run on this thread, going back to the run it was in,
	 * or to the shared state.
	 * @param prev what {@link #beginOwn(long)} gave
	 */
	public void endOwn(final long[] prev)
	{
		if(prev == null)
			own.remove();
		else
			own.set(prev);
	}

	/**
	 * Returns the generator's state, to save it.
	 * @return the state
	 */
	public synchronized long getState()
	{
		return state;
	}

	/**
	 * Sets the generator's state, as returned by {@link #getState()}.
	 * @param newState the state
	 */
	public synchronized void setState(final long newState)
	{
		state = newState & MASK;
	}
}
