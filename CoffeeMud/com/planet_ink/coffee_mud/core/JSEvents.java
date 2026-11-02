package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.*;

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
 * JS6: cm.on(event, fn [, key]): JavaScript functions run when the game's
 * time, weather or course changes.  Time and weather are those of the
 * player's area, looked at each tick: hour, daypart, day, month, season and
 * year get (area, was, now); weather gets (area, was, now) as words.  The
 * game's course (GAME_PROG's events): newgame, restore, save, newchar,
 * switchto, switchfrom, pause, resume and timemode get (character, detail);
 * a save listener that returns false refuses the save.  tick runs every
 * tick; sud.time.waitUntil's are tick listeners that end once they return
 * true.  A listener with the key of one before replaces it; the key is the
 * function's own text if none is given, so a script that registers the same
 * function each time it runs keeps one.  Listeners are code, not state, and
 * are not saved: world/js/init.js's onBoot is where to register them.
 */
public final class JSEvents
{
	/** the events of the clock and the weather */
	public static final String[]	TIME_EVENTS	= { "hour", "daypart", "day", "month", "season", "year", "weather" };
	/** the events of the game's course, GAME_PROG's in lower case */
	public static final String[]	GAME_EVENTS	= { "newgame", "restore", "save", "newchar", "switchto", "switchfrom", "pause", "resume", "timemode" };
	/**
	 * JS8: what happens in the world, wherever it happens: death(victim,
	 * killer), beforeDeath (false keeps the victim alive, at 1 hit point),
	 * levelUp(mob, level), questWin(mob, quest), questEnd(quest, mob, why),
	 * enterRoom(mob, room, from), beforeEnterRoom (false keeps the mover
	 * out), enterArea(mob, area, from), itemGained(mob, item), itemLost(mob,
	 * item), factionChange(mob, faction, change) and turn(leader, count), each
	 * step of turn-based time.  Names are in any case.
	 */
	public static final String[]	WORLD_EVENTS	= { "death", "beforedeath", "levelup", "questwin", "questend", "enterroom", "beforeenterroom",
		"enterarea", "itemgained", "itemlost", "factionchange", "turn" };

	/** JS8: whether anything listens for itemGained or itemLost, looked at as a mob's items change */
	public static volatile boolean	itemEvents	= false;

	private static final class Listener
	{
		final String		event;
		final String		key;
		final Function		fn;
		final Scriptable	scope;
		final Function		then;

		Listener(final String event, final String key, final Function fn, final Scriptable scope, final Function then)
		{
			this.event = event;
			this.key = key;
			this.fn = fn;
			this.scope = scope;
			this.then = then;
		}
	}

	private static final List<Listener>	listeners	= new CopyOnWriteArrayList<Listener>();
	private static volatile Ticker		ticker		= null;
	/** what the player's area's clock and weather were at the last tick */
	private static Area		lastArea	= null;
	private static int[]	lastTime	= null;
	private static int		lastWeather	= -1;

	private JSEvents()
	{
	}

	/**
	 * @param event an event's name
	 * @return true if cm.on knows it
	 */
	public static boolean isEvent(final String event)
	{
		final String e = (event == null) ? "" : event.toLowerCase().trim();
		return e.equals("tick") || Arrays.asList(TIME_EVENTS).contains(e) || Arrays.asList(GAME_EVENTS).contains(e)
			|| Arrays.asList(WORLD_EVENTS).contains(e);
	}

	/**
	 * cm.on: adds a listener.
	 * @param event the event
	 * @param fn the function
	 * @param scope its scope
	 * @param key its key, or null for the function's own text
	 * @return the key
	 */
	public static String on(final String event, final Function fn, final Scriptable scope, final String key)
	{
		return add(event, fn, scope, key, null);
	}

	/**
	 * sud.time.waitUntil: test runs each tick until it returns true; then, if
	 * given, runs once after.
	 * @param test the test
	 * @param then what runs after, or null
	 * @param scope their scope
	 * @return the key
	 */
	public static String waitUntil(final Function test, final Function then, final Scriptable scope)
	{
		return add("tick", test, scope, null, (then == null) ? test : then);
	}

	private static String add(final String event, final Function fn, final Scriptable scope, final String key, final Function then)
	{
		final String e = (event == null) ? "" : event.toLowerCase().trim();
		if(!isEvent(e))
			throw ScriptRuntime.constructError("Error", "cm.on: no event '" + event + "': tick, " + String.join(", ", TIME_EVENTS)
				+ ", " + String.join(", ", GAME_EVENTS) + ", " + String.join(", ", WORLD_EVENTS));
		if(fn == null)
			throw ScriptRuntime.constructError("Error", "cm.on: no function");
		String k = ((key == null) || (key.trim().length() == 0)) ? defaultKey(fn) : key.trim();
		if(then != null)
			k = "waitUntil:" + k;
		off(e, k);
		listeners.add(new Listener(e, k, fn, scope, then));
		noteItemEvents();
		if(e.equals("tick") || Arrays.asList(TIME_EVENTS).contains(e))
			startTicking();
		return k;
	}

	private static String defaultKey(final Function fn)
	{
		String s;
		try
		{
			s = (fn instanceof BaseFunction) ? ((BaseFunction)fn).getFunctionName() + ":" + fn.toString() : fn.toString();
		}
		catch(final RuntimeException e)
		{
			s = "fn@" + System.identityHashCode(fn);
		}
		return s.replaceAll("\\s+", " ").trim();
	}

	/**
	 * cm.off: takes listeners away.
	 * @param event the event, or null for any
	 * @param key the key
	 * @return how many were taken away
	 */
	public static int off(final String event, final String key)
	{
		int n = 0;
		for(final Listener L : listeners)
		{
			if(((event == null) || L.event.equals(event.toLowerCase().trim())) && L.key.equals(key))
			{
				listeners.remove(L);
				n++;
			}
		}
		noteItemEvents();
		return n;
	}

	/**
	 * cm.off(event, fn): takes away a listener added without a key.
	 * @param event the event
	 * @param fn the function, as it was given
	 * @return how many were taken away
	 */
	public static int off(final String event, final Function fn)
	{
		return (fn == null) ? 0 : off(event, defaultKey(fn));
	}

	private static void noteItemEvents()
	{
		boolean any = false;
		for(final Listener L : listeners)
			any = any || L.event.equals("itemgained") || L.event.equals("itemlost");
		itemEvents = any;
	}

	/**
	 * @param event an event, or null for all
	 * @return the keys of its listeners, as "event key"
	 */
	public static List<String> listening(final String event)
	{
		final List<String> out = new ArrayList<String>();
		for(final Listener L : listeners)
		{
			if((event == null) || L.event.equalsIgnoreCase(event))
				out.add(L.event + " " + L.key);
		}
		return out;
	}

	/** takes every listener away (a test, or a new world) */
	public static void clear()
	{
		listeners.clear();
		itemEvents = false;
		lastArea = null;
		lastTime = null;
		lastWeather = -1;
	}

	/**
	 * Runs an event's listeners.
	 * @param event the event
	 * @param args what they get, Java objects
	 * @return false if a listener returned false
	 */
	public static boolean fire(final String event, final Object... args)
	{
		boolean ok = true;
		for(final Listener L : listeners)
		{
			if(L.event.equals(event))
			{
				final Object r = call(L, L.fn, args);
				if(Boolean.FALSE.equals(r))
					ok = false;
			}
		}
		return ok;
	}

	private static Object call(final Listener L, final Function fn, final Object[] args)
	{
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Object[] jsArgs = new Object[args.length];
			for(int i = 0; i < args.length; i++)
				jsArgs[i] = Context.javaToJS(args[i], L.scope);
			return Context.jsToJava(fn.call(cx, L.scope, L.scope, jsArgs), Object.class);
		}
		catch(final Exception e)
		{
			JSRuntime.report("cm.on('" + L.event + "')", e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	/** each tick (and a test): the tick listeners, then the clock and the weather of the player's area */
	public static void tick()
	{
		JSTimers.tick(); // JS19
		for(final Listener L : listeners)
		{
			if(!L.event.equals("tick"))
				continue;
			final Object r = call(L, L.fn, new Object[0]);
			if((L.then != null) && isTrue(r))
			{
				listeners.remove(L);
				if(L.then != L.fn)
					call(L, L.then, new Object[0]);
			}
		}
		final MOB P = JSCm.thePlayer();
		final Room R = (P == null) ? null : P.location();
		final Area A = (R == null) ? null : R.getArea();
		if(A == null)
			return;
		final TimeClock C = A.getTimeObj();
		final int[] now = { C.getHourOfDay(), C.getTODCode().ordinal(), C.getDayOfMonth(), C.getMonth(), C.getSeasonCode().ordinal(), C.getYear() };
		final int weather = A.getClimateObj().weatherType(R);
		if((A == lastArea) && (lastTime != null))
		{
			final String[] names = { "hour", "daypart", "day", "month", "season", "year" };
			for(int i = 0; i < names.length; i++)
			{
				if(now[i] != lastTime[i])
					fire(names[i], A, timeWord(i, lastTime[i]), timeWord(i, now[i]));
			}
			if((lastWeather >= 0) && (weather != lastWeather))
				fire("weather", A, Climate.WEATHER_DESCS[lastWeather].toLowerCase(), Climate.WEATHER_DESCS[weather].toLowerCase());
		}
		lastArea = A;
		lastTime = now;
		lastWeather = weather;
	}

	private static Object timeWord(final int which, final int value)
	{
		if(which == 1)
			return TimeClock.TimeOfDay.values()[value].name().toLowerCase();
		if(which == 4)
			return TimeClock.Season.values()[value].name().toLowerCase();
		return Integer.valueOf(value);
	}

	private static boolean isTrue(final Object r)
	{
		if(r instanceof Boolean)
			return ((Boolean)r).booleanValue();
		if(r instanceof Number)
			return ((Number)r).doubleValue() != 0;
		return (r != null) && (!(r instanceof Undefined)) && (r.toString().length() > 0);
	}

	static synchronized void startTicking()
	{
		if(ticker == null)
		{
			ticker = new Ticker();
			CMLib.threads().startTickDown(ticker, Tickable.TICKID_MISCELLANEOUS, 1);
		}
	}

	private static final class Ticker implements Tickable
	{
		@Override
		public String ID()
		{
			return "JSEvents";
		}

		@Override
		public String name()
		{
			return ID();
		}

		@Override
		public CMObject newInstance()
		{
			return this;
		}

		@Override
		public CMObject copyOf()
		{
			return this;
		}

		@Override
		public void initializeClass()
		{
		}

		@Override
		public int compareTo(final CMObject o)
		{
			return (o == this) ? 0 : 1;
		}

		@Override
		public int getTickStatus()
		{
			return Tickable.STATUS_NOT;
		}

		@Override
		public boolean tick(final Tickable ticking, final int tickID)
		{
			try
			{
				JSEvents.tick();
			}
			catch(final RuntimeException e)
			{
				Log.errOut("JSEvents", e);
			}
			return true;
		}
	}
}
