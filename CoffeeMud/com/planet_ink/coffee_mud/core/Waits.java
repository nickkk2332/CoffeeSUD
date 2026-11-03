package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.util.*;
import java.util.function.BooleanSupplier;

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
 * W4: letting time pass.  A wait runs the game's own ticks (so one heals,
 * shops restock, things respawn and TIME_PROGs fire), pausing a running
 * clock for it and starting it again after; it says each game hour that
 * passes, ends early when the one waiting is disturbed (as WAIT always
 * has), and ends at a condition: a time of day, an hour, a day, a vessel's
 * arrival, a variable, a quest's step.  WAIT, MPPASSTIME and MPINTERRUPT
 * use it; a wait in a Prop_InnRoom room gives a full rest.
 */
public final class Waits
{
	private Waits()
	{
	}

	/** the longest wait (three game days, by the default calendar) */
	public static final long	MAX_HOURS	= 72;

	private static volatile MOB		waiter		= null;
	private static volatile boolean	interrupted	= false;

	/** each vessel's last docking, by game time (ST8's DOCK_PROG) */
	private static final Map<Item, Long>	docked	= Collections.synchronizedMap(new WeakHashMap<Item, Long>());

	/**
	 * A vessel's anchor dropped (from ScriptEvents.vesselDocked).
	 * @param vessel the vessel
	 */
	public static void docked(final Item vessel)
	{
		if(vessel != null)
			docked.put(vessel, Long.valueOf(GameTime.now()));
	}

	/**
	 * @param mob a character
	 * @return true if they may wait more than 100 ticks: LONGWAIT=ALL (the default), or a builder
	 */
	public static boolean mayWaitLong(final MOB mob)
	{
		final String who = CMProps.instance().getProperty("LONGWAIT");
		if((who != null) && who.trim().equalsIgnoreCase("BUILDERS"))
			return SaveGame.isBuilderMode() || SaveGame.isDevMode();
		return true;
	}

	/** @return the one waiting now, or null */
	public static MOB waiter()
	{
		return waiter;
	}

	/**
	 * @param mob a character
	 * @return their area's clock
	 */
	public static TimeClock clock(final MOB mob)
	{
		final Room R = (mob == null) ? null : mob.location();
		final Area A = (R == null) ? null : R.getArea();
		return (A != null) ? A.getTimeObj() : CMLib.time().globalClock();
	}

	/**
	 * The test of a long wait that ends it when the one waiting is hurt,
	 * dies, leaves the room, is drawn into a fight or is spoken to.
	 * @param mob the one waiting
	 * @return the test
	 */
	public static BooleanSupplier disturbed(final MOB mob)
	{
		final Room startRoom = mob.location();
		final int startHP = mob.curState().getHitPoints();
		final boolean startFight = mob.isInCombat();
		final long spokenAt = DialogueTime.spokenTo(mob); // N10
		return new BooleanSupplier()
		{
			@Override
			public boolean getAsBoolean()
			{
				if(DialogueTime.spokenTo(mob) != spokenAt)
				{
					mob.tell(CMLib.lang().L("You stop waiting: you are spoken to."));
					return true;
				}
				if(mob.amDead()
				|| (mob.location() != startRoom)
				|| (mob.curState().getHitPoints() < startHP)
				|| (mob.isInCombat() && !startFight))
				{
					if(!mob.amDead())
						mob.tell(CMLib.lang().L("You stop waiting."));
					return true;
				}
				return false;
			}
		};
	}

	/**
	 * The vessel or mount a character is on, if any.
	 * @param mob the character
	 * @return the item, or null
	 */
	protected static Item vesselOf(final MOB mob)
	{
		final Room R = mob.location();
		final Area A = (R == null) ? null : R.getArea();
		if(A instanceof Boardable)
			return ((Boardable)A).getBoardableItem();
		final Rideable ride = mob.riding();
		return (ride instanceof Item) ? (Item)ride : null;
	}

	/**
	 * What WAIT UNTIL or MPPASSTIME UNTIL waits for.
	 * @param mob the one waiting
	 * @param words DAWN, DAY, DUSK, NIGHT, MORNING, HOUR h, DAY d (of the
	 *        month), ARRIVAL, STOP, DOCK; for builders VAR host name op value,
	 *        QUEST name STEP n, QUEST name DONE
	 * @param why where the reason goes when the words are no condition
	 * @return the test, or null
	 */
	public static BooleanSupplier until(final MOB mob, final List<String> words, final String[] why)
	{
		if(words.isEmpty())
		{
			why[0] = "Wait until when?  DAWN, DAY, DUSK, NIGHT, HOUR [N], DAY [N], ARRIVAL or DOCK.";
			return null;
		}
		final TimeClock C = clock(mob);
		final String w = words.get(0).toUpperCase();
		final TimeClock.TimeOfDay tod = (TimeClock.TimeOfDay)CMath.s_valueOf(TimeClock.TimeOfDay.class, w.equals("MORNING") ? "DAY" : w);
		if((tod != null) && !(w.equals("DAY") && (words.size() > 1) && CMath.isInteger(words.get(1))))
		{
			// the next one: if it is that now, the one after
			final boolean[] left = { C.getTODCode() != tod };
			return () -> {
				if(C.getTODCode() != tod)
					left[0] = true;
				return left[0] && (C.getTODCode() == tod);
			};
		}
		if((w.equals("HOUR") || w.equals("DAY")) && (words.size() > 1) && CMath.isInteger(words.get(1)))
		{
			final int n = CMath.s_int(words.get(1));
			final boolean hour = w.equals("HOUR");
			if(hour && ((n < 0) || (n >= C.getHoursInDay())))
			{
				why[0] = "There are only " + C.getHoursInDay() + " hours in a day here (0 to " + (C.getHoursInDay() - 1) + ").";
				return null;
			}
			final boolean[] left = { (hour ? C.getHourOfDay() : C.getDayOfMonth()) != n };
			return () -> {
				final int now = hour ? C.getHourOfDay() : C.getDayOfMonth();
				if(now != n)
					left[0] = true;
				return left[0] && (now == n);
			};
		}
		if(w.equals("ARRIVAL") || w.equals("STOP") || w.equals("DOCK"))
		{
			final Item V = vesselOf(mob);
			if(V == null)
			{
				why[0] = "You are on no vessel or mount.";
				return null;
			}
			final long since = GameTime.now();
			final Room[] last = { CMLib.map().roomLocation(V) };
			final boolean[] moved = { false };
			final boolean dockOnly = w.equals("DOCK");
			return () -> {
				final Long D = docked.get(V);
				if((D != null) && (D.longValue() >= since))
					return true;
				if(dockOnly)
					return false;
				final Room now = CMLib.map().roomLocation(V);
				if(now != last[0])
				{
					moved[0] = true;
					last[0] = now;
					return false;
				}
				return moved[0];
			};
		}
		final boolean builder = SaveGame.isBuilderMode() || SaveGame.isDevMode();
		if(w.equals("VAR") && builder && (words.size() >= 5))
		{
			final String host = words.get(1), var = words.get(2), op = words.get(3), want = CMParms.combine(words, 4);
			final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			return () -> {
				final String have = S.getVar(host, var).trim();
				final boolean nums = CMath.isNumber(have) && CMath.isNumber(want);
				final int c = nums ? Double.compare(CMath.s_double(have), CMath.s_double(want)) : have.compareToIgnoreCase(want);
				switch(op)
				{
				case "!=":
					return c != 0;
				case ">":
					return c > 0;
				case "<":
					return c < 0;
				case ">=":
					return c >= 0;
				case "<=":
					return c <= 0;
				default:
					return c == 0;
				}
			};
		}
		if(w.equals("QUEST") && builder && (words.size() >= 3))
		{
			final Quest Q = CMLib.quests().fetchQuest(words.get(1));
			if(Q == null)
			{
				why[0] = "There is no quest " + words.get(1) + ".";
				return null;
			}
			if(words.get(2).equalsIgnoreCase("DONE"))
				return () -> !Q.running();
			if(words.get(2).equalsIgnoreCase("STEP") && (words.size() > 3) && CMath.isInteger(words.get(3)))
			{
				final int step = CMath.s_int(words.get(3));
				return () -> Q.running() && (Q.stepNumber() >= step);
			}
		}
		why[0] = "Wait until " + CMParms.combine(words, 0) + "?  DAWN, DAY, DUSK, NIGHT, HOUR [N], DAY [N], ARRIVAL, STOP or DOCK"
			+ (builder ? "; VAR [HOST] [NAME] [OP] [VALUE], QUEST [NAME] STEP [N] or QUEST [NAME] DONE." : ".");
		return null;
	}

	/**
	 * Lets game time pass: the clock stopped (paused for it if it ran), the
	 * ticks run until the time is up or the test says so.
	 * @param mob the one waiting (told the hours), or null
	 * @param ms how long, at most, in game time
	 * @param until a condition that ends it, or null
	 * @param interruptible true to end it as WAIT ends: hurt, attacked, moved
	 * @param quiet true for no line each hour
	 * @return false if time is already passing
	 */
	public static boolean start(final MOB mob, final long ms, final BooleanSupplier until, final boolean interruptible, final boolean quiet)
	{
		if(GameTime.isAdvancing() || (ms <= 0))
			return false;
		final boolean wasRunning = !GameTime.isStopped();
		if(wasRunning)
			GameTime.pause();
		final TimeClock C = clock(mob);
		final int[] lastHour = { C.getHourOfDay() };
		final long startedAt = GameTime.now();
		final BooleanSupplier dist = (interruptible && (mob != null)) ? disturbed(mob) : null;
		interrupted = false;
		waiter = mob;
		final BooleanSupplier test = () -> {
			if(interrupted)
				return true;
			if((!quiet) && (mob != null) && (C.getHourOfDay() != lastHour[0]))
			{
				lastHour[0] = C.getHourOfDay();
				mob.tell(CMLib.lang().L("An hour passes.  It is now @x1 o'clock.", "" + lastHour[0]));
			}
			if((dist != null) && dist.getAsBoolean())
				return true;
			return (until != null) && until.getAsBoolean();
		};
		GameTime.requestAdvance(ms, test, () -> {
			waiter = null;
			if(wasRunning)
				GameTime.resume();
			if(mob != null)
				innRest(mob, GameTime.now() - startedAt);
		});
		return true;
	}

	/**
	 * After a wait: in a room with Prop_InnRoom, a wait of its HOURS (1 by
	 * default) or more gives a full rest.
	 * @param mob the one who waited
	 * @param waited how long, in game time
	 */
	public static void innRest(final MOB mob, final long waited)
	{
		final Room R = mob.location();
		final com.planet_ink.coffee_mud.Abilities.interfaces.Ability A = (R == null) ? null : R.fetchEffect("Prop_InnRoom");
		if((A == null) || mob.amDead())
			return;
		final int hours = Math.max(0, CMParms.getParmInt(A.text(), "HOURS", 1));
		if(waited < (hours * CMProps.getMillisPerMudHour()))
			return;
		mob.resetToMaxState();
		mob.tell(CMParms.getParmStr(A.text(), "MSG", CMLib.lang().L("You are well rested.")));
	}

	/**
	 * Ends a wait or a journey under way (MPINTERRUPT).
	 * @param who whose wait (one waiting, or of their party), or null for any
	 * @param message what they are told, or null
	 * @return true if one ended
	 */
	public static boolean interrupt(final MOB who, final String message)
	{
		final MOB W = waiter;
		if(!GameTime.isAdvancing())
			return false;
		if((who != null) && (W != null) && (W != who) && (Party.leaderOf(W) != Party.leaderOf(who)))
			return false;
		interrupted = true;
		GameTime.cancelAdvance();
		if((message != null) && (message.length() > 0) && (W != null))
			W.tell(message);
		return true;
	}
}
