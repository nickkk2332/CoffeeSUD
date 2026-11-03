package com.planet_ink.coffee_mud.core;

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
import java.util.function.BooleanSupplier;

/**
 * The game clock, in milliseconds.  Everything that times something in the
 * world (cooldowns, durations, contracts, decay, command pacing) reads this
 * instead of System.currentTimeMillis().  It runs with the system clock,
 * except that it stands still while the game is paused or turn-based, can be
 * moved forward on request, and can be set (loading a saved game).
 *
 * While the clock is stopped, it moves only when something asks it to: in
 * turn-based play, the player's actions ask for as much time as they take,
 * and WAIT asks for whole ticks.  The scheduler takes that time a step at a
 * time (see {@link #takeAdvance(long)}), so that everything due on the way
 * happens, in order.
 *
 * While it runs, it can run faster or slower than real time: TIMESCALE (see
 * {@link #setRate(double)}) sets how many game milliseconds pass in each
 * real one, from 0.1 to 20, and everything that reads the clock follows.
 *
 * Things that are about the real world stay on the system clock: logs,
 * network and input timeouts, idle detection, thread health checks, caches,
 * and dates shown to the player as real dates, such as the last login.
 */
public final class GameTime
{
	/** the clock's state; replaced whole, so that a reader never sees half a change */
	private static final class State
	{
		/** the game time at {@link #realAt}, or where it stands while stopped */
		final long		gameAt;
		/** the system time at which the game time was {@link #gameAt}, or 0 while it is stopped */
		final long		realAt;
		/** how many game milliseconds pass in each real one while it runs */
		final double	rate;

		State(final long gameAt, final long realAt, final double rate)
		{
			this.gameAt = gameAt;
			this.realAt = realAt;
			this.rate = rate;
		}

		long now(final long sysNow)
		{
			if(realAt == 0)
				return gameAt;
			if(rate == 1.0)
				return gameAt + (sysNow - realAt);
			return gameAt + (long)((sysNow - realAt) * rate);
		}
	}

	/** the clock at first: the system time, running at real time */
	private static State started()
	{
		final long sys = System.currentTimeMillis();
		return new State(sys, sys, 1.0);
	}

	/** the slowest TIMESCALE */
	public static final double MIN_RATE = 0.1;
	/** the fastest TIMESCALE */
	public static final double MAX_RATE = 20.0;

	private static volatile State	state		= started();
	/** told when the rate changes: the scheduler, whose sleeps were worked out at the old one */
	private static volatile Runnable rateListener = null;
	/** true while the player has paused the game */
	private static volatile boolean	paused		= false;
	/** true while time moves only when the player acts */
	private static volatile boolean	turnBased	= false;
	/** time asked for and not yet taken by the scheduler */
	private static volatile long	pending		= 0;
	/** when this says so, the time still pending is dropped */
	private static volatile BooleanSupplier stopWhen = null;

	private GameTime()
	{
	}

	/**
	 * Returns the current game time in milliseconds.  While the clock runs,
	 * this has the same scale and starting point as System.currentTimeMillis().
	 * @return the current game time
	 */
	public static long now()
	{
		return state.now(System.currentTimeMillis());
	}

	/**
	 * How fast the game clock runs against real time while it runs: 1 is real
	 * time, 8 is eight game seconds in each real one (TIMESCALE).
	 * @return the rate
	 */
	public static double rate()
	{
		return state.rate;
	}

	/**
	 * The rate as the prompt and TIMESCALE show it, such as x8 or x0.5.
	 * @return the words
	 */
	public static String rateText()
	{
		final double r = state.rate;
		if(r == Math.rint(r))
			return "x" + (long)r;
		return "x" + (Math.round(r * 100.0) / 100.0);
	}

	/**
	 * Sets how fast the game clock runs while it runs, from now on: the time
	 * already passed stays as it was.  Pause and turn-based time work as
	 * before, the rate waiting until the clock runs again.
	 * @param rate the rate, held to 0.1 to 20
	 * @return the rate set
	 */
	public static double setRate(final double rate)
	{
		final double r = Math.max(MIN_RATE, Math.min(MAX_RATE, rate));
		synchronized(GameTime.class)
		{
			final State s = state;
			final long sys = System.currentTimeMillis();
			state = new State(s.now(sys), (s.realAt == 0) ? 0 : sys, r);
		}
		final Runnable R = rateListener;
		if(R != null)
			R.run();
		return r;
	}

	/**
	 * Sets what is told when the rate changes.
	 * @param R what to run, or null
	 */
	public static void setRateListener(final Runnable R)
	{
		rateListener = R;
	}

	/**
	 * The real milliseconds in which so many game ones pass while the clock
	 * runs, for the scheduler's sleeps.
	 * @param gameMs the game milliseconds
	 * @return the real ones, at least 0
	 */
	public static long realMs(final long gameMs)
	{
		if(gameMs <= 0)
			return 0;
		final double r = state.rate;
		if(r == 1.0)
			return gameMs;
		return (long)Math.ceil(gameMs / r);
	}

	/**
	 * Returns whether the player has paused the game.  The clock is then
	 * stopped, and actions that take time wait until the game resumes.
	 * @return true if paused
	 */
	public static boolean isPaused()
	{
		return paused;
	}

	/**
	 * Returns whether time moves only when the player acts.
	 * @return true if turn-based
	 */
	public static boolean isTurnBased()
	{
		return turnBased;
	}

	/**
	 * Returns whether the clock is stopped, because the game is paused or
	 * turn-based.  It then moves only when asked to.
	 * @return true if stopped
	 */
	public static boolean isStopped()
	{
		return state.realAt == 0;
	}

	/**
	 * Pauses the game, stopping the clock.  Does nothing if the game is
	 * turn-based, since the clock is already stopped.
	 */
	public static synchronized void pause()
	{
		if(turnBased)
			return;
		paused = true;
		stop();
	}

	/**
	 * Unpauses the game, starting the clock again from where it stopped.
	 */
	public static synchronized void resume()
	{
		paused = false;
		if(!turnBased)
			start();
	}

	/**
	 * Makes time move only when the player acts, or run on its own again.
	 * Either way, the game is no longer paused.
	 * @param on true for turn-based, false for real time
	 */
	public static synchronized void setTurnBased(final boolean on)
	{
		turnBased = on;
		paused = false;
		if(on)
			stop();
		else
			start();
	}

	private static void stop()
	{
		final State s = state;
		if(s.realAt != 0)
			state = new State(s.now(System.currentTimeMillis()), 0, s.rate);
	}

	private static void start()
	{
		final State s = state;
		if(s.realAt == 0)
			state = new State(s.gameAt, System.currentTimeMillis(), s.rate);
		pending = 0;
		stopWhen = null;
	}

	/**
	 * Moves the game clock forward at once, whether it is running or stopped.
	 * Nothing that comes due on the way is run first; to move a stopped clock
	 * through time, use {@link #requestAdvance(long)}.
	 * @param ms how many milliseconds to move it
	 */
	public static synchronized void advance(final long ms)
	{
		if(ms <= 0)
			return;
		final State s = state;
		if(s.realAt == 0)
			state = new State(s.gameAt + ms, 0, s.rate);
		else
		{
			final long sys = System.currentTimeMillis();
			state = new State(s.now(sys) + ms, sys, s.rate);
		}
	}

	/**
	 * Asks for the stopped clock to move forward.  The scheduler moves it
	 * soon after, running whatever comes due on the way.  Does nothing while
	 * the clock runs.
	 * @param ms how many milliseconds to move it
	 */
	public static void requestAdvance(final long ms)
	{
		requestAdvance(ms, null);
	}

	/**
	 * Asks for the stopped clock to move forward, and to stop moving early
	 * once the given test says so.  The scheduler checks it after each step.
	 * Does nothing while the clock runs.
	 * @param ms how many milliseconds to move it
	 * @param until the test, or null to take all of the time
	 */
	public static synchronized void requestAdvance(final long ms, final BooleanSupplier until)
	{
		if((ms <= 0) || (state.realAt != 0))
			return;
		pending += ms;
		if(until != null)
			stopWhen = until;
	}

	/** W4: what to do once the time asked for is all taken, or dropped */
	private static Runnable afterAdvance = null;

	/**
	 * W4: as requestAdvance, and runs a hook once the time is all taken or
	 * dropped (a wait that paused the running clock starts it again).
	 * @param ms how many milliseconds to move it
	 * @param until the test, or null to take all of the time
	 * @param after the hook, or null
	 */
	public static synchronized void requestAdvance(final long ms, final BooleanSupplier until, final Runnable after)
	{
		if((ms <= 0) || (state.realAt != 0))
		{
			if(after != null)
				after.run();
			return;
		}
		requestAdvance(ms, until);
		afterAdvance = after;
	}

	private static void runAfter()
	{
		final Runnable R = afterAdvance;
		afterAdvance = null;
		if(R != null)
			R.run();
	}

	/**
	 * Returns whether time asked for has not all been taken yet.
	 * @return true while the clock is still being moved
	 */
	public static boolean isAdvancing()
	{
		return pending > 0;
	}

	/**
	 * Drops whatever time is still asked for.
	 */
	public static synchronized void cancelAdvance()
	{
		pending = 0;
		stopWhen = null;
		runAfter(); // W4
	}

	/**
	 * Moves the stopped clock forward by some of the time asked for, but no
	 * more than the given step.  The scheduler calls this with the time until
	 * the next thing due, runs what is due, then calls it again.
	 * @param maxStep the most to move it
	 * @return how far it moved
	 */
	public static synchronized long takeAdvance(final long maxStep)
	{
		final long step = Math.min(pending, Math.max(0, maxStep));
		pending -= step;
		advance(step);
		if(pending == 0)
		{
			stopWhen = null;
			runAfter(); // W4
		}
		return step;
	}

	/**
	 * Called by the scheduler after each step: drops the time still asked for
	 * if the test given with the request says to stop.
	 */
	public static void checkStop()
	{
		final BooleanSupplier until = stopWhen;
		if((until != null) && (pending > 0) && until.getAsBoolean())
			cancelAdvance();
	}

	/**
	 * Sets the game clock, keeping it running or stopped as it was.
	 * @param gameMs the game time it should now read
	 */
	public static synchronized void set(final long gameMs)
	{
		final State s = state;
		state = new State(gameMs, (s.realAt == 0) ? 0 : System.currentTimeMillis(), s.rate);
	}
}
