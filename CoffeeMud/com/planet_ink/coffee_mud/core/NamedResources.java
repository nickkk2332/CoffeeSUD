package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.ModuleContent.Entry;
import com.planet_ink.coffee_mud.core.ModuleContent.Library;
import com.planet_ink.coffee_mud.core.ModuleContent.Node;

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
 * A15: named resources, the optional module abilities.resources.  A pool
 * (heat, focus) or a count of charges belongs to a character, with a
 * maximum, a starting value, and an optional change over time on a chosen
 * clock: turns (turn-based play), ticks of the game clock, or game hours.
 * The change is settled lazily from the clock, so a save and restore leave
 * the same value as playing on.  Costs are all checked before anything is
 * spent; a lower maximum clamps the value when it is next settled, a higher
 * one adds nothing.  Definitions: world/pools/[id].v[N].json.
 */
public final class NamedResources
{
	private NamedResources()
	{
	}

	public static final String	MODULE	= "abilities.resources";
	public static final String	FOLDER	= "pools";
	public static final String	FAMILY	= "resources";
	/** the clocks a change can follow */
	public static final String[] CLOCKS	= { "turns", "ticks", "hours" };

	/** one pool or charge definition */
	public static final class Pool
	{
		public final boolean	charges;
		public final long		max;
		public final long		start;
		/** the change each period: negative cools or drains, positive refills */
		public final long		amount;
		public final long		every;
		public final String		clock;
		public final boolean	reservable;

		Pool(final Node n) throws IOException
		{
			n.allow("schema", "id", "version", "title", "kind", "max", "start", "change", "reservable");
			charges = n.choice("kind", "pool", "pool", "charges").equals("charges");
			max = n.integer("max", 1, charges ? 10 : 1000000);
			start = n.integer("start", 0, 0, max);
			final Node c = n.object("change");
			if(c == null)
			{
				amount = 0;
				every = 1;
				clock = "ticks";
			}
			else
			{
				c.allow("amount", "every", "clock");
				amount = c.integer("amount", -max, max);
				if(amount == 0)
					throw c.error("amount", "must not be 0; leave out change for a pool that keeps its value");
				every = c.integer("every", 1, 1, 1000000);
				clock = c.choice("clock", "ticks", CLOCKS);
			}
			reservable = n.bool("reservable", false);
		}

		public boolean changes()
		{
			return amount != 0;
		}

		/** the change in words */
		public String changeText()
		{
			if(amount == 0)
				return "";
			final String unit = clock.equals("turns") ? "turn" : clock.equals("hours") ? "game hour" : "tick";
			return ((amount < 0) ? "loses " + (-amount) : "gains " + amount) + " every " + ((every == 1) ? unit : (every + " " + unit + "s"));
		}
	}

	private static Library<Pool>	library	= null;

	private static final ModuleContent.Reader<Pool> READER = new ModuleContent.Reader<Pool>()
	{
		@Override
		public Pool read(final Node node) throws IOException
		{
			return new Pool(node);
		}
	};

	/** the installed definitions, freshly read */
	public static Library<Pool> load()
	{
		return ModuleContent.load(ModuleContent.world(), FOLDER, "pool", READER);
	}

	/** the loaded definitions */
	public static synchronized Library<Pool> definitions()
	{
		if(library == null)
			library = load();
		return library;
	}

	public static synchronized void reset()
	{
		library = null;
	}

	/** whether the module is active in this campaign */
	public static boolean enabled()
	{
		return OptionalModules.enabled(MODULE);
	}

	/** refuses when the module is not active */
	public static void require() throws IOException
	{
		if(!enabled())
			throw new IOException("named resources (" + MODULE + ") are not active in this campaign");
	}

	/** a clock's reading now */
	public static long reading(final String clock)
	{
		if(clock.equals("turns"))
			return ScriptEvents.turns();
		if(clock.equals("hours"))
			return (CMLib.time().globalClock() == null) ? 0 : CMLib.time().globalClock().toHoursSinceEpoc();
		return GameTime.now() / Math.max(1, CMProps.getTickMillis());
	}

	/** the bonuses to a pool's maximum, from Prop_ResourceMax on the character and what they wear or hold */
	public static long maxBonus(final MOB mob, final String pool)
	{
		long bonus = 0;
		if(mob == null)
			return 0;
		for(final Enumeration<Ability> e = mob.effects(); e.hasMoreElements();)
			bonus += bonusOf(e.nextElement(), pool);
		for(int i = 0; i < mob.numItems(); i++)
		{
			final Item I = mob.getItem(i);
			if((I == null) || I.amWearingAt(Item.IN_INVENTORY))
				continue;
			for(final Enumeration<Ability> e = I.effects(); e.hasMoreElements();)
				bonus += bonusOf(e.nextElement(), pool);
		}
		return bonus;
	}

	private static long bonusOf(final Ability A, final String pool)
	{
		if((A == null) || !A.ID().equals("Prop_ResourceMax"))
			return 0;
		for(final String part : CMParms.parseSpaces(A.text(), true))
		{
			final int x = part.indexOf('=');
			if((x > 0) && part.substring(0, x).equalsIgnoreCase(pool))
			{
				final String n = part.substring(x + 1).replace("+", "");
				if(CMath.isLong(n))
					return CMath.s_long(n);
			}
		}
		return 0;
	}

	/** a pool's maximum for a character: its definition's and the bonuses, never below 1 */
	public static long maximum(final MOB mob, final Entry<Pool> def)
	{
		final long most = def.value.max + maxBonus(mob, def.id);
		return Math.max(1, def.value.charges ? Math.min(most, 100) : most);
	}

	private static JSONObject ledger(final MOB mob) throws IOException
	{
		final JSONObject rec = OptionalModules.get(MODULE, OptionalModules.character(mob));
		final String raw = (String)rec.get("pools");
		return ((raw == null) || raw.isEmpty()) ? new JSONObject() : ModuleDefinitions.parse(raw);
	}

	private static void store(final MOB mob, final JSONObject pools) throws IOException
	{
		final JSONObject rec = OptionalModules.get(MODULE, OptionalModules.character(mob));
		final String text = pools.isEmpty() ? "" : ModuleDefinitions.json(pools);
		if(text.length() > 4096)
			throw new IOException("too many pools for one character");
		rec.put("pools", text);
		OptionalModules.put(MODULE, OptionalModules.character(mob), rec);
	}

	/** one pool of a character, settled to now */
	public static final class Held
	{
		public final Entry<Pool>	def;
		public final long			value;
		public final long			reserved;
		public final long			max;
		final long					at;
		final boolean				changed;

		Held(final Entry<Pool> def, final long value, final long reserved, final long max, final long at, final boolean changed)
		{
			this.def = def;
			this.value = value;
			this.reserved = reserved;
			this.max = max;
			this.at = at;
			this.changed = changed;
		}

		/** what can be spent: the value less what is reserved */
		public long available()
		{
			return Math.max(0, value - reserved);
		}

		JSONObject record()
		{
			final JSONObject o = new JSONObject();
			o.put("pin", def.pin());
			o.put("value", Long.valueOf(value));
			o.put("at", Long.valueOf(at));
			if(reserved > 0)
				o.put("reserved", Long.valueOf(reserved));
			return o;
		}

		public String describe()
		{
			final StringBuilder s = new StringBuilder(def.title).append(def.value.charges ? " charges: " : ": ").append(value).append(" of ").append(max);
			if(reserved > 0)
				s.append(", ").append(reserved).append(" reserved");
			if(def.value.changes())
				s.append(" (").append(def.value.changeText()).append(")");
			return s.toString();
		}
	}

	/**
	 * A pool settled to now, from its saved record (or a new one at its
	 * start), without writing anything: reading never changes state.  A
	 * newer installed version replaces the pinned one, and the value clamps
	 * to its maximum.
	 */
	private static Held settle(final MOB mob, final String id, final JSONObject rec) throws IOException
	{
		final Library<Pool> lib = definitions();
		Entry<Pool> def;
		long value, at, reserved = 0;
		boolean changed = false;
		if(rec == null)
		{
			def = lib.latest(id);
			value = def.value.start;
			at = reading(def.value.clock);
			changed = true;
		}
		else
		{
			final JSONObject pin = ModuleContent.checkPin(rec.get("pin"));
			final int pinnedVersion = ((Number)pin.get("version")).intValue();
			try
			{
				def = lib.pinned(pin);
			}
			catch(final IOException e)
			{
				// a version edited in place is refused; one no longer installed gives way to a newer one
				final Entry<Pool> newest = lib.has(id) ? lib.latest(id) : null;
				if(lib.entries.containsKey(id + ".v" + pinnedVersion) || (newest == null) || (newest.version < pinnedVersion))
					throw e;
				def = newest;
				changed = true;
			}
			final Entry<Pool> newest = lib.latest(id);
			if(newest.version > def.version)
			{
				def = newest;
				changed = true;
			}
			value = ((Number)rec.get("value")).longValue();
			at = ((Number)rec.get("at")).longValue();
			if(rec.get("reserved") instanceof Number)
				reserved = ((Number)rec.get("reserved")).longValue();
		}
		final long max = maximum(mob, def);
		if(def.value.changes())
		{
			final long now = reading(def.value.clock);
			if(now < at)
			{
				at = now; // the clock went back, as after switching clocks; nothing is owed
				changed = true;
			}
			else
			{
				final long periods = (now - at) / def.value.every;
				if(periods > 0)
				{
					final long step = def.value.amount;
					final long delta = (Math.abs(step) > 0) && (periods > Long.MAX_VALUE / Math.abs(step)) ? (step > 0 ? Long.MAX_VALUE : Long.MIN_VALUE) : periods * step;
					value = Math.max(0, Math.min(max, (delta > 0) ? ((value > Long.MAX_VALUE - delta) ? Long.MAX_VALUE : value + delta) : value + delta));
					at += periods * def.value.every;
					changed = true;
					ModuleStats.add(FAMILY, def.id, "settled periods", periods);
				}
			}
		}
		if(value > max)
		{
			value = max;
			changed = true;
		}
		if(value < 0)
		{
			value = 0;
			changed = true;
		}
		if(reserved > value)
		{
			reserved = value;
			changed = true;
		}
		return new Held(def, value, reserved, max, at, changed);
	}

	private static JSONObject recordOf(final JSONObject pools, final String id) throws IOException
	{
		return pools.containsKey(id) ? ModuleDefinitions.object(pools.get(id), "pool record") : null;
	}

	/** a character's pool now, or null when they have none of it; reading changes nothing */
	public static Held held(final MOB mob, final String id) throws IOException
	{
		require();
		final JSONObject pools = ledger(mob);
		final JSONObject rec = recordOf(pools, id);
		return (rec == null) ? null : settle(mob, id, rec);
	}

	/** a character's pool's value now, 0 when they have none */
	public static long value(final MOB mob, final String id) throws IOException
	{
		final Held h = held(mob, id);
		return (h == null) ? 0 : h.value;
	}

	/** gives a character a pool at its start value, if they do not have it; true if new */
	public static synchronized boolean grant(final MOB mob, final String id) throws IOException
	{
		require();
		final JSONObject pools = ledger(mob);
		if(pools.containsKey(id))
			return false;
		final Held h = settle(mob, id, null);
		pools.put(id, h.record());
		store(mob, pools);
		return true;
	}

	/**
	 * Adds to a pool, up to its maximum; a character without the pool gets it.
	 * @return what was added
	 */
	public static synchronized long gain(final MOB mob, final String id, final long n, final String cause) throws IOException
	{
		require();
		if(n < 0)
			throw new IOException("a gain cannot be negative");
		final JSONObject pools = ledger(mob);
		final Held h = settle(mob, id, recordOf(pools, id));
		final long to = Math.min(h.max, h.value + n);
		pools.put(id, new Held(h.def, to, h.reserved, h.max, h.at, true).record());
		store(mob, pools);
		ModuleStats.add(FAMILY, id, "gained", to - h.value);
		return to - h.value;
	}

	/** adds to a pool, for scripts */
	public static long gain(final MOB mob, final String id, final long n) throws IOException
	{
		return gain(mob, id, n, "script");
	}

	/**
	 * Whether a character can pay all these costs, as a reason, or "" when
	 * they can.  A cost of a pool they do not have is not met.
	 */
	public static String unaffordable(final MOB mob, final Map<String, Long> costs) throws IOException
	{
		if(costs.isEmpty())
			return "";
		require();
		final JSONObject pools = ledger(mob);
		for(final Map.Entry<String, Long> c : costs.entrySet())
		{
			final JSONObject rec = recordOf(pools, c.getKey());
			if(rec == null)
				return "you have no " + definitions().latest(c.getKey()).title;
			final Held h = settle(mob, c.getKey(), rec);
			if(h.available() < c.getValue().longValue())
				return "you need " + c.getValue() + " " + h.def.title + " (you have " + h.available() + ")";
		}
		return "";
	}

	/**
	 * Pays all these costs at once, or none: every cost is checked first.
	 * @throws IOException with the reason, having spent nothing, when one cannot be paid
	 */
	public static synchronized void spend(final MOB mob, final Map<String, Long> costs, final String cause) throws IOException
	{
		final String problem = unaffordable(mob, costs);
		if(problem.length() > 0)
		{
			ModuleStats.count(FAMILY, cause, "refused costs");
			throw new IOException(problem);
		}
		final JSONObject pools = ledger(mob);
		for(final Map.Entry<String, Long> c : costs.entrySet())
		{
			final Held h = settle(mob, c.getKey(), recordOf(pools, c.getKey()));
			pools.put(c.getKey(), new Held(h.def, h.value - c.getValue().longValue(), h.reserved, h.max, h.at, true).record());
			ModuleStats.add(FAMILY, c.getKey(), "spent", c.getValue().longValue());
		}
		store(mob, pools);
	}

	/** spends one pool */
	public static void spend(final MOB mob, final String id, final long n, final String cause) throws IOException
	{
		final Map<String, Long> one = new TreeMap<String, Long>();
		one.put(id, Long.valueOf(n));
		spend(mob, one, cause);
	}

	/** sets a pool's value, clamped, for scripts and builders */
	public static synchronized long set(final MOB mob, final String id, final long n) throws IOException
	{
		require();
		final JSONObject pools = ledger(mob);
		final Held h = settle(mob, id, recordOf(pools, id));
		final long to = Math.max(0, Math.min(h.max, n));
		pools.put(id, new Held(h.def, to, Math.min(h.reserved, to), h.max, h.at, true).record());
		store(mob, pools);
		return to;
	}

	/**
	 * Reserves part of a reservable pool, so it cannot be spent until it is
	 * released (an aura's upkeep, say).
	 */
	public static synchronized void reserve(final MOB mob, final String id, final long n) throws IOException
	{
		require();
		final JSONObject pools = ledger(mob);
		final JSONObject rec = recordOf(pools, id);
		if(rec == null)
			throw new IOException("no pool " + id);
		final Held h = settle(mob, id, rec);
		if(!h.def.value.reservable)
			throw new IOException(h.def.title + " cannot be reserved");
		if((n < 0) || (h.available() < n))
			throw new IOException("only " + h.available() + " " + h.def.title + " can be reserved");
		pools.put(id, new Held(h.def, h.value, h.reserved + n, h.max, h.at, true).record());
		store(mob, pools);
	}

	/** releases a reservation */
	public static synchronized void release(final MOB mob, final String id, final long n) throws IOException
	{
		require();
		final JSONObject pools = ledger(mob);
		final JSONObject rec = recordOf(pools, id);
		if(rec == null)
			return;
		final Held h = settle(mob, id, rec);
		pools.put(id, new Held(h.def, h.value, Math.max(0, h.reserved - Math.max(0, n)), h.max, h.at, true).record());
		store(mob, pools);
	}

	/** every pool of a character, settled, without writing */
	public static List<Held> all(final MOB mob) throws IOException
	{
		require();
		final List<Held> out = new ArrayList<Held>();
		final JSONObject pools = ledger(mob);
		for(final String id : new TreeSet<String>(pools.keySet()))
		{
			try
			{
				out.add(settle(mob, id, recordOf(pools, id)));
			}
			catch(final IOException e)
			{
				// a pool whose definition is gone keeps its record and is shown as unavailable
			}
		}
		return out;
	}

	/** RESOURCES: a character's pools, in words */
	public static String describe(final MOB mob) throws IOException
	{
		require();
		final JSONObject pools = ledger(mob);
		if(pools.isEmpty())
			return "You have no named resources.";
		final StringBuilder s = new StringBuilder("Your resources:");
		for(final String id : new TreeSet<String>(pools.keySet()))
		{
			try
			{
				s.append("\n  ").append(settle(mob, id, recordOf(pools, id)).describe());
			}
			catch(final IOException e)
			{
				s.append("\n  ").append(id).append(": unavailable (").append(e.getMessage()).append(")");
			}
		}
		return s.toString();
	}

	/** the family, for WORLD POOLS and WORLD CHECK ONLY=POOLS */
	public static final ModuleContent.Family FAMILY_ENTRY = new ModuleContent.Family()
	{
		@Override
		public String module()
		{
			return MODULE;
		}

		@Override
		public String part()
		{
			return "POOLS";
		}

		@Override
		public String folder()
		{
			return FOLDER;
		}

		@Override
		public List<String> scopes()
		{
			return Arrays.asList("character");
		}

		@Override
		public List<String> stringState()
		{
			return Arrays.asList("pools");
		}

		@Override
		public List<String> requires()
		{
			return new ArrayList<String>();
		}

		@Override
		public void reset()
		{
			NamedResources.reset();
		}

		@Override
		public List<String> check()
		{
			final Library<Pool> lib = load();
			final List<String> out = new ArrayList<String>(lib.errors);
			// the pools composed skills and supports cost and gain must be installed
			for(final CompositionDefinitions.Definition d : CompositionDefinitions.load(ModuleContent.world()).definitions.values())
			{
				final Set<String> used = new TreeSet<String>(d.resourceCost.keySet());
				used.addAll(d.resourceGain.keySet());
				for(final String p : used)
				{
					if(!lib.has(p))
						out.add(d.path + ": /resources: no pool " + p + " is installed in " + FOLDER + "/");
				}
			}
			// a turns clock never moves in real time
			final ModuleDefinitions.Definition manifest = OptionalModules.configuredPlan().definitions.get(MODULE);
			if((manifest != null) && manifest.modes.contains("REALTIME"))
			{
				for(final Entry<Pool> e : lib.entries.values())
				{
					if(e.value.changes() && e.value.clock.equals("turns"))
						out.add(e.path + ": /change/clock: turns do not pass in real time, which " + MODULE + " allows; use ticks for a pool that changes in both");
				}
			}
			return out;
		}

		@Override
		public String list() throws IOException
		{
			final Library<Pool> lib = load();
			if(lib.entries.isEmpty())
				return "No pools are installed in " + FOLDER + "/." + (lib.errors.isEmpty() ? "" : "\n" + String.join("\n", lib.errors));
			final StringBuilder s = new StringBuilder("Pools:");
			for(final Entry<Pool> e : lib.entries.values())
			{
				s.append("\n  ").append(e.key()).append(" (").append(e.title).append("): ").append(e.value.charges ? "charges" : "pool")
				 .append(", max ").append(e.value.max).append(", start ").append(e.value.start);
				if(e.value.changes())
					s.append(", ").append(e.value.changeText());
				if(e.value.reservable)
					s.append(", reservable");
			}
			if(!lib.errors.isEmpty())
				s.append("\n").append(String.join("\n", lib.errors));
			return s.toString();
		}

		@Override
		public String reload() throws IOException
		{
			NamedResources.reset();
			return definitions().report();
		}

		@Override
		public String world(final MOB mob, final List<String> words) throws IOException
		{
			// WORLD POOLS SET [pool] [n] (character): a builder's test value
			if((words.size() >= 3) && words.get(0).equalsIgnoreCase("SET") && CMath.isLong(words.get(2)))
			{
				MOB who = mob;
				if(words.size() > 3)
				{
					who = (mob.location() == null) ? null : mob.location().fetchInhabitant(CMParms.combine(words, 3));
					if(who == null)
						throw new IOException("no such character here");
				}
				final long to = set(who, words.get(1).toLowerCase(Locale.ROOT), CMath.s_long(words.get(2)));
				return who.Name() + "'s " + words.get(1) + " is now " + to + ".";
			}
			if((words.size() >= 1) && words.get(0).equalsIgnoreCase("SHOW"))
			{
				MOB who = mob;
				if(words.size() > 1)
				{
					who = (mob.location() == null) ? null : mob.location().fetchInhabitant(CMParms.combine(words, 1));
					if(who == null)
						throw new IOException("no such character here");
				}
				return describe(who).replace("Your resources", who.Name() + "'s resources").replace("You have", who.Name() + " has");
			}
			return null;
		}
	};

	static
	{
		ModuleContent.register(FAMILY_ENTRY);
	}
}
