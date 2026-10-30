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
	protected synchronized int next(final int bits)
	{
		state = (state * MULTIPLIER + ADDEND) & MASK;
		return (int)(state >>> (48 - bits));
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
