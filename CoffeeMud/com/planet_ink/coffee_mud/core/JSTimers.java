package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.TimeClock;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

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
 * JS19: timers on the game's clock, looked at each tick (JSEvents' ticker),
 * so that they wait while the game is paused and step with its turns.
 * <ul>
 * <li>cm.every(spec, fn [, key]): again and again: spec is a number of
 * ticks, or '6 ticks', '6 hours', '1 day' (game hours and days);</li>
 * <li>cm.at(spec, fn [, key]): when the clock comes to it: 'hour 6',
 * 'day 1', 'month 3', 'season winter', or several, as 'day 7 hour 8' (day
 * is the day of the month);</li>
 * <li>cm.after(ticks, fn, data): once, that many ticks on, fn(data).  It is
 * kept in the save (in the store 'js:timers'), and after a restore fn is
 * made again from its text, so it should use only data and cm.</li>
 * </ul>
 * every and at are not saved: register them in world/js/init.js's onBoot.
 * cm.stopTimer(key) takes one away.
 */
public final class JSTimers
{
	/** the store that keeps the timers made by after() */
	public static final String	STORE	= "js:timers";

	private static final class Repeat
	{
		final String		key;
		final Function		fn;
		final Scriptable	scope;
		final long			ticks;
		final long			hours;
		final Map<String, Object>	at;
		long				nextTick;
		long				nextHour;
		boolean				matched;

		Repeat(final String key, final Function fn, final Scriptable scope, final long ticks, final long hours, final Map<String, Object> at)
		{
			this.key = key;
			this.fn = fn;
			this.scope = scope;
			this.ticks = ticks;
			this.hours = hours;
			this.at = at;
		}
	}

	private static final List<Repeat>			repeats	= new CopyOnWriteArrayList<Repeat>();
	/** after()'s functions made in this game, by id; after a restore, the text */
	private static final Map<String, Object[]>	live	= new HashMap<String, Object[]>();
	private static long							counter	= 0;

	private JSTimers()
	{
	}

	private static TimeClock clock()
	{
		final MOB P = JSCm.thePlayer();
		final Room R = (P == null) ? null : P.location();
		final Area A = (R == null) ? null : R.getArea();
		return (A != null) ? A.getTimeObj() : CMLib.time().globalClock();
	}

	private static long tickMs()
	{
		return Math.max(1, CMProps.getTickMillis());
	}

	private static String keyFor(final String key, final Function fn)
	{
		if((key != null) && (key.trim().length() > 0))
			return key.trim();
		try
		{
			return Context.toString(fn).replaceAll("\\s+", " ").trim();
		}
		catch(final RuntimeException e)
		{
			return "fn@" + System.identityHashCode(fn);
		}
	}

	/**
	 * cm.every.
	 * @param spec ticks, or 'N ticks', 'N hours', 'N days'
	 * @param fn the function
	 * @param scope its scope
	 * @param key its name, or null for its text
	 * @return its key
	 */
	public static String every(final Object spec, final Function fn, final Scriptable scope, final String key)
	{
		if(fn == null)
			throw ScriptRuntime.constructError("Error", "cm.every: no function");
		long ticks = 0, hours = 0;
		final String s = (spec instanceof Number) ? (((Number)spec).longValue() + " ticks") : String.valueOf(spec).trim().toLowerCase();
		final String[] w = s.split("\\s+");
		final long n = (w.length > 0) && CMath.isLong(w[0]) ? CMath.s_long(w[0]) : (w.length == 1 ? 1 : 0);
		final String unit = (w.length > 1) ? w[1] : (CMath.isLong(w[0]) ? "ticks" : w[0]);
		if(unit.startsWith("tick") || unit.startsWith("turn"))
			ticks = n;
		else
		if(unit.startsWith("hour"))
			hours = n;
		else
		if(unit.startsWith("day"))
			hours = n * clock().getHoursInDay();
		if((ticks <= 0) && (hours <= 0))
			throw ScriptRuntime.constructError("Error", "cm.every: '" + spec + "' is no time: as 10 (ticks), '6 hours' or '1 day'");
		final String k = keyFor(key, fn);
		stop(k);
		final Repeat r = new Repeat(k, fn, scope, ticks, hours, null);
		r.nextTick = GameTime.now() + (ticks * tickMs());
		r.nextHour = clock().toHoursSinceEpoc() + hours;
		repeats.add(r);
		JSEvents.startTicking();
		return k;
	}

	/**
	 * cm.at.
	 * @param spec 'hour 6', 'day 1', 'month 3', 'season winter', or several
	 * @param fn the function
	 * @param scope its scope
	 * @param key its name, or null for its text
	 * @return its key
	 */
	public static String at(final String spec, final Function fn, final Scriptable scope, final String key)
	{
		if(fn == null)
			throw ScriptRuntime.constructError("Error", "cm.at: no function");
		final Map<String, Object> at = new HashMap<String, Object>();
		final String[] w = String.valueOf(spec).trim().toLowerCase().split("\\s+");
		for(int i = 0; i + 1 < w.length; i += 2)
		{
			if(w[i].equals("season"))
				at.put("season", w[i + 1]);
			else
			if(w[i].equals("hour") || w[i].equals("day") || w[i].equals("month"))
				at.put(w[i], Integer.valueOf(CMath.s_int(w[i + 1])));
		}
		if(at.isEmpty() || ((w.length % 2) != 0))
			throw ScriptRuntime.constructError("Error", "cm.at: '" + spec + "' is no time: as 'hour 6', 'day 1 hour 8' or 'season winter'");
		final String k = keyFor(key, fn);
		stop(k);
		final Repeat r = new Repeat(k, fn, scope, 0, 0, at);
		r.matched = matches(at, clock());
		repeats.add(r);
		JSEvents.startTicking();
		return k;
	}

	private static boolean matches(final Map<String, Object> at, final TimeClock C)
	{
		for(final Map.Entry<String, Object> e : at.entrySet())
		{
			switch(e.getKey())
			{
			case "hour":
				if(C.getHourOfDay() != ((Integer)e.getValue()).intValue())
					return false;
				break;
			case "day":
				if(C.getDayOfMonth() != ((Integer)e.getValue()).intValue())
					return false;
				break;
			case "month":
				if(C.getMonth() != ((Integer)e.getValue()).intValue())
					return false;
				break;
			case "season":
				if(!C.getSeasonCode().name().equalsIgnoreCase(e.getValue().toString()))
					return false;
				break;
			default:
				break;
			}
		}
		return true;
	}

	/**
	 * cm.after: once, that many ticks on.
	 * @param cx the context
	 * @param scope the scope it is made in
	 * @param ticks how many ticks on
	 * @param fn the function
	 * @param data what it gets, kept with the save
	 * @return its id
	 */
	public static synchronized String after(final Context cx, final Scriptable scope, final long ticks, final Function fn, final Object data)
	{
		if(fn == null)
			throw ScriptRuntime.constructError("Error", "cm.after: no function");
		final Scriptable store = JSStore.store(cx, scope, STORE);
		Object list = ScriptableObject.getProperty(store, "pending");
		if(!(list instanceof Scriptable))
		{
			list = cx.newArray(ScriptableObject.getTopLevelScope(scope), 0);
			ScriptableObject.putProperty(store, "pending", list);
		}
		final Scriptable L = (Scriptable)list;
		final String id = "t" + GameTime.now() + "-" + (++counter);
		final Scriptable t = cx.newObject(ScriptableObject.getTopLevelScope(scope));
		ScriptableObject.putProperty(t, "id", id);
		ScriptableObject.putProperty(t, "due", Double.valueOf(GameTime.now() + (Math.max(1, ticks) * tickMs())));
		ScriptableObject.putProperty(t, "fn", Context.toString(fn));
		ScriptableObject.putProperty(t, "data", (data == null) ? null : data);
		final long n = (long)Context.toNumber(ScriptableObject.getProperty(L, "length"));
		L.put((int)n, L, t);
		live.put(id, new Object[] { fn, scope });
		JSEvents.startTicking();
		return id;
	}

	/**
	 * cm.stopTimer: takes away an every, an at, or an after.
	 * @param key its key or id
	 * @return true if there was one
	 */
	public static synchronized boolean stop(final String key)
	{
		boolean any = false;
		for(final Repeat r : repeats)
		{
			if(r.key.equals(key))
			{
				repeats.remove(r);
				any = true;
			}
		}
		if(live.remove(key) != null)
			any = true;
		if(JSStore.has(STORE))
		{
			final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
			try
			{
				final Scriptable top = cx.initSafeStandardObjects();
				final Scriptable store = JSStore.store(cx, top, STORE);
				final Object list = ScriptableObject.getProperty(store, "pending");
				if(list instanceof Scriptable)
				{
					final Scriptable L = (Scriptable)list;
					final List<Object> keep = new ArrayList<Object>();
					final long n = (long)Context.toNumber(ScriptableObject.getProperty(L, "length"));
					for(int i = 0; i < n; i++)
					{
						final Object t = L.get(i, L);
						if((t instanceof Scriptable) && key.equals(Context.toString(ScriptableObject.getProperty((Scriptable)t, "id"))))
							any = true;
						else
							keep.add(t);
					}
					ScriptableObject.putProperty(store, "pending", cx.newArray(ScriptableObject.getTopLevelScope(store), keep.toArray()));
				}
			}
			finally
			{
				Context.exit();
			}
		}
		return any;
	}

	/** @return the timers there are, as "every key", "at key" and "after id" */
	public static List<String> listing()
	{
		final List<String> out = new ArrayList<String>();
		for(final Repeat r : repeats)
			out.add(((r.at != null) ? "at " : "every ") + r.key);
		for(final String id : pendingIds())
			out.add("after " + id);
		return out;
	}

	private static List<String> pendingIds()
	{
		final List<String> out = new ArrayList<String>();
		if(!JSStore.has(STORE))
			return out;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Scriptable store = JSStore.store(cx, cx.initSafeStandardObjects(), STORE);
			final Object list = ScriptableObject.getProperty(store, "pending");
			if(list instanceof Scriptable)
			{
				final Scriptable L = (Scriptable)list;
				final long n = (long)Context.toNumber(ScriptableObject.getProperty(L, "length"));
				for(int i = 0; i < n; i++)
				{
					final Object t = L.get(i, L);
					if(t instanceof Scriptable)
						out.add(Context.toString(ScriptableObject.getProperty((Scriptable)t, "id")));
				}
			}
		}
		finally
		{
			Context.exit();
		}
		return out;
	}

	/** a restore or a new game: after()'s functions are made again from their text */
	static synchronized void forget()
	{
		live.clear();
	}

	/** takes every timer away (a test, or a new world) */
	public static synchronized void clear()
	{
		repeats.clear();
		live.clear();
		JSStore.remove(STORE);
	}

	/** each tick, from JSEvents' ticker */
	public static void tick()
	{
		if(repeats.isEmpty() && (!JSStore.has(STORE)))
			return;
		final long now = GameTime.now();
		final TimeClock C = clock();
		final long hour = C.toHoursSinceEpoc();
		for(final Repeat r : repeats)
		{
			boolean due = false;
			if(r.at != null)
			{
				final boolean m = matches(r.at, C);
				due = m && !r.matched;
				r.matched = m;
			}
			else
			if((r.ticks > 0) && (now >= r.nextTick))
			{
				due = true;
				r.nextTick = now + (r.ticks * tickMs());
			}
			else
			if((r.hours > 0) && (hour >= r.nextHour))
			{
				due = true;
				r.nextHour = hour + r.hours;
			}
			if(due)
				run(r.fn, r.scope, "cm." + ((r.at != null) ? "at" : "every") + "('" + r.key + "')");
		}
		runDue(now);
	}

	private static void runDue(final long now)
	{
		if(!JSStore.has(STORE))
			return;
		final List<Object[]> due = new ArrayList<Object[]>();
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Scriptable top = cx.initSafeStandardObjects();
			final Scriptable store = JSStore.store(cx, top, STORE);
			final Object list = ScriptableObject.getProperty(store, "pending");
			if(!(list instanceof Scriptable))
				return;
			final Scriptable L = (Scriptable)list;
			final List<Object> keep = new ArrayList<Object>();
			final long n = (long)Context.toNumber(ScriptableObject.getProperty(L, "length"));
			for(int i = 0; i < n; i++)
			{
				final Object t = L.get(i, L);
				if(!(t instanceof Scriptable))
					continue;
				final Scriptable T = (Scriptable)t;
				if(Context.toNumber(ScriptableObject.getProperty(T, "due")) <= now)
					due.add(new Object[] { Context.toString(ScriptableObject.getProperty(T, "id")), Context.toString(ScriptableObject.getProperty(T, "fn")),
						ScriptableObject.getProperty(T, "data") });
				else
					keep.add(T);
			}
			if(due.isEmpty())
				return;
			ScriptableObject.putProperty(store, "pending", cx.newArray(ScriptableObject.getTopLevelScope(store), keep.toArray()));
		}
		finally
		{
			Context.exit();
		}
		for(final Object[] d : due)
		{
			final Object[] L;
			synchronized(JSTimers.class)
			{
				L = live.remove(d[0]);
			}
			final Object data = (d[2] instanceof UniqueTag) ? null : d[2];
			if(L != null)
				run((Function)L[0], (Scriptable)L[1], "cm.after", data);
			else
				runText((String)d[1], data);
		}
	}

	private static void run(final Function fn, final Scriptable scope, final String where, final Object... args)
	{
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			fn.call(cx, scope, scope, args);
		}
		catch(final Exception e)
		{
			JSRuntime.report(where, e);
		}
		finally
		{
			Context.exit();
		}
	}

	/** after a restore: the function made again from its text, in a scope of its own */
	private static void runText(final String text, final Object data)
	{
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final ImporterTopLevel top = new ImporterTopLevel(cx);
			JSScope.prepare(cx, top, null);
			final Object f = cx.evaluateString(top, "(" + text + ")", "cm.after", 1, null);
			if(f instanceof Function)
				((Function)f).call(cx, top, top, new Object[] { data });
		}
		catch(final Exception e)
		{
			JSRuntime.report("cm.after, after the restore", e);
		}
		finally
		{
			Context.exit();
		}
	}
}
