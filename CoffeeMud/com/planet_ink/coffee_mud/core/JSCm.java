package com.planet_ink.coffee_mud.core;

import java.util.*;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.LegalBehavior;
import com.planet_ink.coffee_mud.Commands.EnvToolsCmd;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.Banker;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;

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
 * JS6: the cm object every JavaScript scope has.  Its members return the
 * game's own objects, so that a script need not know the engine's packages.
 * The host is the thing the script runs for, if any (a script's host, the
 * user of JRUN or JCONSOLE), and the player the character at the terminal.
 * Where a member takes a room, area or mob, null means the host's.
 */
public class JSCm
{
	/** the thing the script runs for, or null */
	protected final PhysicalAgent host;

	/** cm.lib: CMLib, the game's libraries (cm.lib.map(), cm.lib.law()...); set by JSScope */
	public Object lib = null;

	/** the scope this cm is in, where JS7's stores are made */
	protected Scriptable scope = null;

	/**
	 * A cm for a script.
	 * @param host the thing the script runs for, or null
	 */
	public JSCm(final PhysicalAgent host)
	{
		this.host = host;
	}

	/** @return the thing the script runs for, or null */
	public PhysicalAgent host()
	{
		return host;
	}

	// ---------------------------------------------------------------- finding

	/**
	 * @param id a room's ID, as Area#12
	 * @return the room, or null
	 */
	public Room room(final String id)
	{
		return (id == null) ? null : CMLib.map().getRoom(id);
	}

	/** @return the room the host is in, or the player's */
	public Room here()
	{
		Room R = (host == null) ? null : CMLib.map().roomLocation(host);
		if(R == null)
		{
			final MOB P = player();
			R = (P == null) ? null : P.location();
		}
		return R;
	}

	/**
	 * @param name an area's name, or nothing for the host's
	 * @return the area, or null
	 */
	public Area area(final String name)
	{
		if((name == null) || (name.trim().length() == 0))
		{
			final Room R = here();
			return (R == null) ? null : R.getArea();
		}
		return CMLib.map().findArea(name);
	}

	/** @return the host's area */
	public Area area()
	{
		return area(null);
	}

	/**
	 * @param name a mob's name
	 * @return the first mob of that name: in the host's room, its area, then the world
	 */
	public MOB findMob(final String name)
	{
		return findMob(name, null);
	}

	/**
	 * @param name a mob's name
	 * @param where a room or an area to look in alone, or null for the host's room, its area, then the world
	 * @return the first mob of that name, or null
	 */
	public MOB findMob(final String name, final Object where)
	{
		if(name == null)
			return null;
		if(where instanceof Room)
			return ((Room)where).fetchInhabitant(name);
		if(where instanceof Area)
			return CMLib.hunt().findFirstInhabitant(((Area)where).getProperMap(), null, name, 100);
		final Room R = here();
		if(R != null)
		{
			final MOB M = R.fetchInhabitant(name);
			if(M != null)
				return M;
			if(R.getArea() != null)
			{
				final MOB A = CMLib.hunt().findFirstInhabitant(R.getArea().getProperMap(), null, name, 100);
				if(A != null)
					return A;
			}
		}
		return CMLib.hunt().findFirstInhabitant(CMLib.map().rooms(), null, name, 100);
	}

	/**
	 * @param name an item's name
	 * @return the first item of that name: in the host's room, its area, then the world
	 */
	public Item findItem(final String name)
	{
		return findItem(name, null);
	}

	/**
	 * @param name an item's name
	 * @param where a room, an area or a mob to look in alone, or null for the host's room, its area, then the world
	 * @return the first item of that name, or null
	 */
	public Item findItem(final String name, final Object where)
	{
		if(name == null)
			return null;
		if(where instanceof MOB)
			return ((MOB)where).findItem(null, name);
		if(where instanceof Room)
			return ((Room)where).findItem(null, name);
		if(where instanceof Area)
		{
			final List<Item> L = CMLib.hunt().findRoomItems(((Area)where).getProperMap(), null, name, true, 100);
			return L.isEmpty() ? null : L.get(0);
		}
		final Room R = here();
		if(R != null)
		{
			final Item I = R.findItem(null, name);
			if(I != null)
				return I;
			if(R.getArea() != null)
			{
				final List<Item> L = CMLib.hunt().findRoomItems(R.getArea().getProperMap(), null, name, true, 100);
				if(!L.isEmpty())
					return L.get(0);
			}
		}
		final List<Item> L = CMLib.hunt().findRoomItems(CMLib.map().rooms(), null, name, true, 100);
		return L.isEmpty() ? null : L.get(0);
	}

	// ---------------------------------------------------------------- making

	/**
	 * A copy of the catalog's mob, in a room (SC3's caps hold).
	 * @param name the catalog mob's name
	 * @param where the room, or null for the host's
	 * @return the mob, or null
	 */
	public MOB loadMob(final String name, final Room where)
	{
		final Room R = (where != null) ? where : here();
		final Physical P = ScriptLib.catalogCopy(ScriptLib.catalogEntry(name, true), false);
		if(!(P instanceof MOB) || (R == null))
		{
			if(P != null)
				P.destroy();
			return null;
		}
		final MOB M = (MOB)P;
		M.setStartRoom(null);
		M.recoverPhyStats();
		M.recoverCharStats();
		M.resetToMaxState();
		M.bringToLife(R, true);
		return M;
	}

	/**
	 * @param name the catalog mob's name
	 * @return a copy of it in the host's room, or null
	 */
	public MOB loadMob(final String name)
	{
		return loadMob(name, null);
	}

	/**
	 * A copy of the catalog's item, in a room or with a mob (SC3's caps hold).
	 * @param name the catalog item's name
	 * @param where a room or a mob, or null for the host's room
	 * @return the item, or null
	 */
	public Item loadItem(final String name, final Object where)
	{
		final Physical P = ScriptLib.catalogCopy(ScriptLib.catalogEntry(name, false), false);
		if(!(P instanceof Item))
		{
			if(P != null)
				P.destroy();
			return null;
		}
		final Item I = (Item)P;
		if(where instanceof MOB)
			((MOB)where).addItem(I);
		else
		{
			final Room R = (where instanceof Room) ? (Room)where : here();
			if(R == null)
			{
				I.destroy();
				return null;
			}
			R.addItem(I, ItemPossessor.Expire.Monster_EQ);
		}
		I.recoverPhyStats();
		return I;
	}

	/**
	 * @param name the catalog item's name
	 * @return a copy of it in the host's room, or null
	 */
	public Item loadItem(final String name)
	{
		return loadItem(name, null);
	}

	// ---------------------------------------------------------------- saying and doing

	/**
	 * Shows text in a room, as MPECHO.
	 * @param where the room, or null for the host's
	 * @param text the text
	 */
	public void echo(final Room where, final String text)
	{
		final Room R = (where != null) ? where : here();
		if((R != null) && (text != null))
			R.showHappens(CMMsg.MSG_OK_ACTION, text);
	}

	/**
	 * Shows text in the host's room, as MPECHO.
	 * @param text the text
	 */
	public void echo(final String text)
	{
		echo(null, text);
	}

	/**
	 * Tells a mob something, as MPECHOAT.
	 * @param who the mob
	 * @param text the text
	 */
	public void tell(final MOB who, final String text)
	{
		if((who != null) && (text != null))
			who.tell(text);
	}

	/**
	 * A mob says something.
	 * @param who the mob
	 * @param text what it says
	 */
	public void say(final MOB who, final String text)
	{
		cmd(who, "SAY " + text);
	}

	/**
	 * A mob emotes.
	 * @param who the mob
	 * @param text the emote
	 */
	public void emote(final MOB who, final String text)
	{
		cmd(who, "EMOTE " + text);
	}

	/**
	 * A mob does a command, as MPFORCE.
	 * @param who the mob
	 * @param command the command
	 */
	public void cmd(final MOB who, final String command)
	{
		if((who == null) || (command == null))
			return;
		who.doCommand(CMParms.parse(command), MUDCmdProcessor.METAFLAG_FORCED);
	}

	/**
	 * Runs MOBPROG lines for the host, as its script would ($-codes and all).
	 * @param lines the lines, as "MPECHO hello", one or more
	 * @return what they returned (RETURN), or ""
	 */
	public String mp(final String lines)
	{
		final PhysicalAgent H = (host != null) ? host : here();
		final ScriptingEngine S = engine();
		if((H == null) || (S == null) || (lines == null))
			return "";
		S.setScript("FUNCTION_PROG CMMP\n" + lines.replace(';', '\n') + "\n~\n");
		final String r = S.callFunction(H, "CMMP", "", player());
		return (r == null) ? "" : r;
	}

	/**
	 * Writes to the game's log.
	 * @param text what to write
	 */
	public void log(final String text)
	{
		Log.sysOut("JS", text);
	}

	// ---------------------------------------------------------------- dice

	/**
	 * @param spec dice, as 3d6+2
	 * @return a roll
	 */
	public long dice(final String spec)
	{
		return ScriptLib.dice(spec);
	}

	/**
	 * @param number how many dice
	 * @param sides their sides
	 * @param plus what is added
	 * @return a roll
	 */
	public int dice(final int number, final int sides, final int plus)
	{
		return CMLib.dice().roll(number, sides, plus);
	}

	/**
	 * @param pct a percentage
	 * @return true that often
	 */
	public boolean chance(final int pct)
	{
		return CMLib.dice().rollPercentage() <= pct;
	}

	// ---------------------------------------------------------------- time and weather

	/** @return the game's time, in game milliseconds; it stands still while the game is paused */
	public long now()
	{
		return GameTime.now();
	}

	/** @return the game's time in ticks (JS13): now() over a tick's length */
	public long ticks()
	{
		return GameTime.now() / Math.max(1, CMProps.getTickMillis());
	}

	/** @return the real time, in milliseconds, for what must not follow the game's clock (JS13) */
	public long realNow()
	{
		return System.currentTimeMillis();
	}

	/**
	 * @param where an area, or null for the host's
	 * @return its clock
	 */
	public Clock clock(final Area where)
	{
		final Area A = (where != null) ? where : area();
		return new Clock(A, (A != null) ? A.getTimeObj() : CMLib.time().globalClock());
	}

	/** @return the host's area's clock */
	public Clock clock()
	{
		return clock(null);
	}

	/** @return the host's area's calendar and clock, the engine's own TimeClock */
	public TimeClock calendar()
	{
		return clock(null).raw;
	}

	/**
	 * The clock goes forward, hour by hour, its events running (as TIME SET).
	 * @param hours how many hours
	 * @param where an area, or null for the host's
	 */
	public void passTime(final int hours, final Area where)
	{
		clock(where).pass(hours);
	}

	/**
	 * The host's area's clock goes forward, hour by hour.
	 * @param hours how many hours
	 */
	public void passTime(final int hours)
	{
		passTime(hours, null);
	}

	/**
	 * In turn-based time, the game goes on as many ticks as asked, what comes
	 * due on the way running, as WAIT does.
	 * @param ticks how many ticks
	 * @return false in real time, where it does nothing
	 */
	public boolean advance(final int ticks)
	{
		if((!GameTime.isTurnBased()) || (ticks <= 0))
			return false;
		GameTime.requestAdvance(ticks * CMProps.getTickMillis());
		return true;
	}

	/**
	 * @param where an area, or null for the host's
	 * @return its weather, or null
	 */
	public Weather weather(final Area where)
	{
		final Area A = (where != null) ? where : area();
		return (A == null) ? null : new Weather(A);
	}

	/** @return the host's area's weather, or null */
	public Weather weather()
	{
		return weather(null);
	}

	/** @return true in a turn-based game */
	public boolean isTurnBased()
	{
		return GameTime.isTurnBased();
	}

	// ---------------------------------------------------------------- the law, shops and banks

	/**
	 * @param where an area, a room, or null for the host's
	 * @return the law there, or null where there is none
	 */
	public LawView law(final Object where)
	{
		final LegalBehavior B;
		final Area A;
		if(where instanceof Room)
		{
			B = CMLib.law().getLegalBehavior((Room)where);
			A = CMLib.law().getLegalObject((Room)where);
		}
		else
		{
			final Area W = (where instanceof Area) ? (Area)where : area();
			B = (W == null) ? null : CMLib.law().getLegalBehavior(W);
			A = (W == null) ? null : CMLib.law().getLegalObject(W);
		}
		final Law L = ((B == null) || (A == null)) ? null : B.legalInfo(A);
		return (L == null) ? null : new LawView(B, A, L);
	}

	/** @return the law of the host's area, or null */
	public LawView law()
	{
		return law(null);
	}

	/**
	 * @param keeper a shopkeeper
	 * @return its shop, or null if it keeps none
	 */
	public Shop shop(final Object keeper)
	{
		if(!(keeper instanceof Environmental))
			return null;
		final ShopKeeper SK = CMLib.coffeeShops().getShopKeeper((Environmental)keeper);
		return (SK == null) ? null : new Shop((Environmental)keeper, SK);
	}

	/**
	 * @param banker a banker
	 * @return its bank, or null if it is none
	 */
	public Bank bank(final Object banker)
	{
		return (banker instanceof Banker) ? new Bank((Banker)banker) : null;
	}

	// ---------------------------------------------------------------- the player and the game

	/** @return the character at the terminal, the one being played */
	public MOB player()
	{
		return thePlayer();
	}

	/** @return the character at the terminal, the one being played */
	static MOB thePlayer()
	{
		for(final Session S : CMLib.sessions().allIterable())
		{
			if((S != null) && (!Party.isPartySession(S)) && (S.mob() != null))
				return S.mob();
		}
		return null;
	}

	/** @return the party of the character at the terminal, the leader first */
	public List<MOB> party()
	{
		final MOB P = player();
		return (P == null) ? new ArrayList<MOB>() : ScriptEvents.group(P);
	}

	/** @return true in builder mode */
	public boolean isBuilder()
	{
		return SaveGame.isBuilderMode();
	}

	/** @return true in a developer game (dev mode) */
	public boolean isDev()
	{
		return SaveGame.isDevMode();
	}

	/** @return the save the game was last saved to or restored from, or "" */
	public String saveSlot()
	{
		final String s = SaveGame.currentSlot();
		return (s == null) ? "" : s;
	}

	// ---------------------------------------------------------------- variables and stats

	/**
	 * A script variable, as $&lt;host var&gt; reads it.
	 * @param holder the host's name, *, or an object (its own, as @)
	 * @param name the variable
	 * @return its value, or ""
	 */
	public String var(final Object holder, final String name)
	{
		final ScriptingEngine S = engine();
		return ((S == null) || (name == null)) ? "" : S.getVar(hostName(holder), name);
	}

	/**
	 * Sets a script variable, as MPSETVAR.
	 * @param holder the host's name, *, or an object (its own, as @)
	 * @param name the variable
	 * @param value the value; ++ -- + - * / and the like as MPSETVAR takes them
	 */
	public void var(final Object holder, final String name, final Object value)
	{
		final ScriptingEngine S = engine();
		if((S != null) && (name != null))
			S.setVar(hostName(holder), name.toUpperCase(), (value == null) ? "" : value.toString());
	}

	/**
	 * @param holder the host's name, *, or an object
	 * @param name the variable
	 * @return its value, or ""
	 */
	public String getVar(final Object holder, final String name)
	{
		return var(holder, name);
	}

	/**
	 * @param holder the host's name, *, or an object
	 * @param name the variable
	 * @param value the value
	 */
	public void setVar(final Object holder, final String name, final Object value)
	{
		var(holder, name, value);
	}

	/**
	 * @param holder the host's name, or an object (its own, as @)
	 * @return the names of its variables, sorted
	 */
	public List<String> vars(final Object holder)
	{
		final String h = hostName(holder);
		final TreeSet<String> names = new TreeSet<String>();
		if(ObjectVars.isHost(h))
			names.addAll(ObjectVars.vars(h).keySet());
		else
		{
			final Object store = Resources.getResource("VARSCOPE-*GLOBAL*");
			if(store instanceof Resources)
			{
				for(final Iterator<String> k = ((Resources)store)._findResourceKeys("SCRIPTVAR-"); k.hasNext();)
				{
					final String key = k.next();
					if(key.startsWith("SCRIPTVAR-") && key.substring(10).equalsIgnoreCase(h))
					{
						final Object o = ((Resources)store)._getResource(key);
						if(o instanceof Map)
						{
							for(final Object n : ((Map<?, ?>)o).keySet())
								names.add(n.toString());
						}
					}
				}
			}
		}
		return new ArrayList<String>(names);
	}

	private static String hostName(final Object holder)
	{
		if(holder instanceof Room)
			return CMLib.map().getExtendedRoomID((Room)holder);
		if(holder instanceof Physical)
			return ObjectVars.hostOf((Physical)holder);
		return (holder == null) ? "*" : holder.toString();
	}

	private static ScriptingEngine engine()
	{
		final Object S = CMClass.getCommon("DefaultScriptingEngine");
		return (S instanceof ScriptingEngine) ? (ScriptingEngine)S : null;
	}

	/**
	 * @param thing a mob, item, room or area
	 * @param stat one of its stats, as STAT() reads them
	 * @return the value, or ""
	 */
	public String stat(final Object thing, final String stat)
	{
		if((!(thing instanceof Modifiable)) || (stat == null))
			return "";
		final String s = CMLib.coffeeMaker().getAnyGenStat((Modifiable)thing, stat.toUpperCase());
		return (s == null) ? "" : s;
	}

	// ---------------------------------------------------------------- stores (JS7)

	/**
	 * A plain JavaScript object kept with the game: its fields are saved as
	 * JSON and come back on restore.
	 * @param holder a key ('world', 'quest:name', any string) or a room, mob, item, area or quest
	 * @return the store, the same object each time until the game is restored
	 */
	public Scriptable store(final Object holder)
	{
		return JSStore.store(Context.getCurrentContext(), scope, JSStore.keyOf(holder));
	}

	/** @return the world's store, cm.store('world') */
	public Scriptable store()
	{
		return store(null);
	}

	/**
	 * @param holder a key, or a room, mob, item, area or quest
	 * @return the key its store has
	 */
	public String storeKey(final Object holder)
	{
		return JSStore.keyOf(holder);
	}

	/** @return the keys of the stores there are */
	public List<String> stores()
	{
		return JSStore.keys();
	}

	// ---------------------------------------------------------------- timers (JS19)

	/**
	 * Again and again on the game's clock (not saved: register in init.js's onBoot).
	 * @param spec ticks, or '6 ticks', '6 hours', '1 day'
	 * @param fn the function
	 * @param key a name, or null for its text
	 * @return its key
	 */
	public String every(final Object spec, final Function fn, final String key)
	{
		return JSTimers.every(spec, fn, (fn == null) ? null : ScriptableObject.getTopLevelScope(fn), key);
	}

	/**
	 * @param spec ticks, or '6 hours', '1 day'
	 * @param fn the function
	 * @return its key
	 */
	public String every(final Object spec, final Function fn)
	{
		return every(spec, fn, null);
	}

	/**
	 * When the game's clock comes to it (not saved: register in init.js's onBoot).
	 * @param spec 'hour 6', 'day 1', 'month 3', 'season winter', or several
	 * @param fn the function
	 * @param key a name, or null for its text
	 * @return its key
	 */
	public String at(final String spec, final Function fn, final String key)
	{
		return JSTimers.at(spec, fn, (fn == null) ? null : ScriptableObject.getTopLevelScope(fn), key);
	}

	/**
	 * @param spec 'hour 6', 'day 1 hour 8'...
	 * @param fn the function
	 * @return its key
	 */
	public String at(final String spec, final Function fn)
	{
		return at(spec, fn, null);
	}

	/**
	 * Once, that many ticks on, fn(data); kept with the save.
	 * @param ticks how many ticks on
	 * @param fn the function, which after a restore is made again from its text
	 * @param data what it gets: data only, as JSON keeps it
	 * @return its id
	 */
	public String after(final Object ticks, final Function fn, final Object data)
	{
		final long t = (ticks instanceof Number) ? ((Number)ticks).longValue() : CMath.s_long(String.valueOf(ticks));
		return JSTimers.after(Context.getCurrentContext(), scope, t, fn, data);
	}

	/**
	 * @param ticks how many ticks on
	 * @param fn the function
	 * @return its id
	 */
	public String after(final Object ticks, final Function fn)
	{
		return after(ticks, fn, null);
	}

	/**
	 * Takes away an every, an at or an after.
	 * @param key its key or id
	 * @return true if there was one
	 */
	public boolean stopTimer(final String key)
	{
		return JSTimers.stop(key);
	}

	/** @return the timers there are */
	public List<String> timers()
	{
		return JSTimers.listing();
	}

	// ---------------------------------------------------------------- events

	/**
	 * Runs a function when something happens (see JSEvents): tick, hour,
	 * daypart, day, month, season, year, weather, newgame, restore, save,
	 * newchar, switchto, switchfrom, pause, resume, timemode.
	 * @param event the event
	 * @param fn the function
	 * @param key a name for it, so that a later one of the same name replaces it, or null
	 * @return its key
	 */
	public String on(final String event, final Function fn, final String key)
	{
		return JSEvents.on(event, fn, (fn == null) ? null : ScriptableObject.getTopLevelScope(fn), key);
	}

	/**
	 * @param event the event
	 * @param fn the function
	 * @return its key
	 */
	public String on(final String event, final Function fn)
	{
		return on(event, fn, null);
	}

	/**
	 * Takes away what on() added.
	 * @param key its key, as on() returned it
	 * @return how many were taken away
	 */
	public int off(final String key)
	{
		return JSEvents.off(null, key);
	}

	/**
	 * Takes away what on(event, fn) added without a key (JS8).
	 * @param event the event
	 * @param fn the same function
	 * @return how many were taken away
	 */
	public int off(final String event, final Function fn)
	{
		return JSEvents.off(event, fn);
	}

	/**
	 * @param event an event, or null for all
	 * @return what listens, as "event key"
	 */
	public List<String> listening(final String event)
	{
		return JSEvents.listening(event);
	}

	/**
	 * sud.time.waitUntil: test runs each tick until it returns true, then then, if given.
	 * @param test the test
	 * @param then what runs after, or null
	 * @return its key
	 */
	public String waitUntil(final Function test, final Function then)
	{
		return JSEvents.waitUntil(test, then, (test == null) ? null : ScriptableObject.getTopLevelScope(test));
	}

	// ---------------------------------------------------------------- what the world members return

	/** cm.clock(area): an area's calendar and clock */
	public static final class Clock
	{
		/** the engine's own clock */
		public final TimeClock	raw;
		private final Area		area;

		Clock(final Area area, final TimeClock raw)
		{
			this.area = area;
			this.raw = raw;
		}

		/** @return the hour of the day, from 0 */
		public int hour()
		{
			return raw.getHourOfDay();
		}

		/** @return the day of the month, from 1 */
		public int day()
		{
			return raw.getDayOfMonth();
		}

		/** @return the month, from 1 */
		public int month()
		{
			return raw.getMonth();
		}

		/** @return the year */
		public int year()
		{
			return raw.getYear();
		}

		/** @return dawn, day, dusk or night */
		public String daypart()
		{
			return raw.getTODCode().name().toLowerCase();
		}

		/** @return spring, summer, fall or winter */
		public String season()
		{
			return raw.getSeasonCode().name().toLowerCase();
		}

		/** @return the moon's phase, as new or full */
		public String moon()
		{
			final Room R = (area == null) ? null : area.getRandomProperRoom();
			return raw.getMoonPhase(R).name().toLowerCase();
		}

		/** @return the hours since the calendar began */
		public long absHours()
		{
			return raw.toHoursSinceEpoc();
		}

		/** @return the hours in a day */
		public int hoursInDay()
		{
			return raw.getHoursInDay();
		}

		/** @return the days in a month */
		public int daysInMonth()
		{
			return raw.getDaysInMonth();
		}

		/** @return the months in a year */
		public int monthsInYear()
		{
			return raw.getMonthsInYear();
		}

		/** @return the time, as the TIME command gives it in short */
		public String text()
		{
			return raw.getShortTimeDescription();
		}

		/**
		 * The clock goes forward, hour by hour, its events running (as TIME SET).
		 * @param hours how many hours
		 */
		public void pass(final int hours)
		{
			if(hours <= 0)
				return;
			raw.tickTock(hours);
			raw.save();
		}

		@Override
		public String toString()
		{
			return text();
		}
	}

	/** cm.weather(area): an area's weather */
	public static final class Weather
	{
		/** the engine's own weather */
		public final Climate	raw;
		private final Area		area;

		Weather(final Area area)
		{
			this.area = area;
			this.raw = area.getClimateObj();
		}

		/** @return the weather, as WEATHER lists it: clear, rain, heat... */
		public String get()
		{
			return Climate.WEATHER_DESCS[raw.weatherType(null)].toLowerCase();
		}

		/** @return the weather that comes next */
		public String next()
		{
			return Climate.WEATHER_DESCS[raw.nextWeatherType(null)].toLowerCase();
		}

		/** @return the weather, as a player is told it */
		public String describe()
		{
			return raw.getWeatherDescription(area);
		}

		/** @return what the weather will do, as a player is told it */
		public String forecast()
		{
			return raw.getNextWeatherDescription(area);
		}

		/**
		 * The weather changes now, as a change of weather comes (WEATHER SET).
		 * @param word the weather, as WEATHER lists it
		 * @return false if it is no weather
		 */
		public boolean set(final String word)
		{
			final int code = (word == null) ? -1 : EnvToolsCmd.weatherCode(word);
			if(code < 0)
				return false;
			raw.setNextWeatherType(code);
			raw.forceWeatherTick(area);
			return true;
		}

		/**
		 * What the weather turns to when it next changes (WEATHER SET ... NEXT).
		 * @param word the weather
		 * @return false if it is no weather
		 */
		public boolean setNext(final String word)
		{
			final int code = (word == null) ? -1 : EnvToolsCmd.weatherCode(word);
			if(code < 0)
				return false;
			raw.setNextWeatherType(code);
			return true;
		}

		@Override
		public String toString()
		{
			return get();
		}
	}

	/** cm.law(area): the law of an area (LAW does the same for builders) */
	public static final class LawView
	{
		/** the law's behaviour */
		public final LegalBehavior	behavior;
		/** the area whose law it is */
		public final Area			area;
		/** the law itself */
		public final Law			raw;

		LawView(final LegalBehavior behavior, final Area area, final Law raw)
		{
			this.behavior = behavior;
			this.area = area;
			this.raw = raw;
		}

		/** @return every warrant */
		public List<LegalWarrant> warrants()
		{
			return new ArrayList<LegalWarrant>(raw.warrants());
		}

		/**
		 * @param who a mob
		 * @return the warrants against it
		 */
		public List<LegalWarrant> warrants(final MOB who)
		{
			final List<LegalWarrant> out = new ArrayList<LegalWarrant>();
			for(final LegalWarrant W : raw.warrants())
			{
				if(W.criminal() == who)
					out.add(W);
			}
			return out;
		}

		/** @return the crimes it knows, as LAW CRIMES names them */
		public List<String> crimes()
		{
			final TreeSet<String> all = new TreeSet<String>();
			all.addAll(raw.basicCrimes().keySet());
			all.addAll(raw.abilityCrimes().keySet());
			return new ArrayList<String>(all);
		}

		/**
		 * Takes warrants away (LAW PARDON).
		 * @param who the mob
		 * @param crime a crime, or null or ALL for every one
		 * @return how many
		 */
		public int pardon(final MOB who, final String crime)
		{
			int n = 0;
			for(final LegalWarrant W : new ArrayList<LegalWarrant>(raw.warrants()))
			{
				if((W.criminal() == who)
				&& ((crime == null) || (crime.length() == 0) || crime.equalsIgnoreCase("ALL") || W.crime().equalsIgnoreCase(crime)))
				{
					behavior.deleteWarrant(area, W);
					n++;
				}
			}
			return n;
		}

		/**
		 * @param who the mob
		 * @return how many warrants were taken away
		 */
		public int pardon(final MOB who)
		{
			return pardon(who, null);
		}

		/**
		 * Makes a warrant, as a witness would (LAW ACCUSE).
		 * @param who the mob
		 * @param crime the crime
		 * @return false if it is no crime here, or the law is off
		 */
		public boolean accuse(final MOB who, final String crime)
		{
			if((who == null) || (crime == null))
				return false;
			final int before = raw.warrants().size();
			behavior.accuse(area, who, null, new String[] { crime.toUpperCase() });
			return raw.warrants().size() > before;
		}

		/**
		 * Takes a fine from a mob's money, in the area's currency.
		 * @param who the mob
		 * @param amount how much, in base value
		 * @return false if it has not that much (and nothing is taken)
		 */
		public boolean fine(final MOB who, final double amount)
		{
			if((who == null) || (amount <= 0))
				return false;
			final String currency = CMLib.beanCounter().getCurrency(area);
			if(CMLib.beanCounter().getTotalAbsoluteValue(who, currency) < amount)
				return false;
			CMLib.beanCounter().subtractMoney(who, currency, amount);
			return true;
		}
	}

	/** cm.shop(keeper): what a shopkeeper sells, and for how much */
	public static final class Shop
	{
		/** the shopkeeper */
		public final Environmental	keeper;
		/** its shop's keeper interface */
		public final ShopKeeper		raw;

		Shop(final Environmental keeper, final ShopKeeper raw)
		{
			this.keeper = keeper;
			this.raw = raw;
		}

		/** @return what it has for sale */
		public List<Environmental> stock()
		{
			final List<Environmental> out = new ArrayList<Environmental>();
			for(final Iterator<Environmental> i = raw.getShop().getStoreInventory(); i.hasNext();)
				out.add(i.next());
			return out;
		}

		/**
		 * @param name a thing's name
		 * @return the first thing of that name for sale, or null
		 */
		public Environmental find(final String name)
		{
			for(final Environmental E : stock())
			{
				if(CMLib.english().containsString(E.name(), name))
					return E;
			}
			return null;
		}

		/**
		 * What it asks for a thing (PRICECHECK's "sells for").
		 * @param product the thing, or its name in the stock
		 * @param customer who buys, or null for the player
		 * @return the price in base value, or -1
		 */
		public double price(final Object product, final MOB customer)
		{
			final Environmental E = (product instanceof Environmental) ? (Environmental)product : find(String.valueOf(product));
			final MOB C = (customer != null) ? customer : thePlayer();
			if((E == null) || (C == null) || !(keeper instanceof MOB))
				return -1;
			return CMLib.coffeeShops().sellingPrice((MOB)keeper, C, E, raw, raw.getShop(C), true).absoluteGoldPrice;
		}

		/**
		 * @param product the thing, or its name in the stock
		 * @return what it asks the player for it, or -1
		 */
		public double price(final Object product)
		{
			return price(product, null);
		}

		/**
		 * What it pays for a thing (PRICECHECK's "pays").
		 * @param product the thing
		 * @param customer who sells, or null for the player
		 * @return the price in base value, or -1
		 */
		public double pays(final Environmental product, final MOB customer)
		{
			final MOB C = (customer != null) ? customer : thePlayer();
			if((product == null) || (C == null) || !(keeper instanceof MOB))
				return -1;
			return CMLib.coffeeShops().pawningPrice((MOB)keeper, C, product, raw).absoluteGoldPrice;
		}
	}

	/** cm.bank(banker): what is kept at a bank */
	public static final class Bank
	{
		/** the banker */
		public final Banker	raw;

		Bank(final Banker raw)
		{
			this.raw = raw;
		}

		private static String who(final Object depositor)
		{
			if(depositor instanceof MOB)
				return ((MOB)depositor).Name();
			final MOB P = thePlayer();
			return (depositor != null) ? depositor.toString() : ((P == null) ? "" : P.Name());
		}

		/**
		 * @param depositor a mob or its name, or null for the player
		 * @return the money it has here, in base value
		 */
		public double balance(final Object depositor)
		{
			return raw.getBalance(who(depositor));
		}

		/**
		 * @param depositor a mob or its name, or null for the player
		 * @return the things it keeps here
		 */
		public List<Item> items(final Object depositor)
		{
			return raw.getDepositedItems(who(depositor));
		}

		/**
		 * @param depositor a mob or its name, or null for the player
		 * @return what the things it keeps here are worth
		 */
		public double worth(final Object depositor)
		{
			return raw.totalItemsWorth(who(depositor));
		}
	}

	/**
	 * For JSScope: the scope's cm, with cm.lib.
	 * @param scope the scope
	 * @param host the host, or null
	 * @return the cm
	 */
	static JSCm forScope(final Scriptable scope, final PhysicalAgent host)
	{
		final JSCm cm = new JSCm(host);
		cm.lib = new org.mozilla.javascript.NativeJavaClass(scope, CMLib.class);
		cm.scope = scope;
		return cm;
	}
}
