package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
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
 * I18: reward escrow, the optional module rewards.escrow.  A reward earned
 * during an activity (a trial, an encounter, an expedition) is first
 * provisional; banking makes it safe; leaving, failing, dying or restoring
 * make it banked, forfeited or kept as the activity declares; a banked
 * reward is claimed once, in one transaction that makes every item and pays
 * every sum together, with a receipt.  One event key gives one entry, so a
 * completion played again adds nothing.  Each character has their own
 * ledger: a party shares a completion, not a claim.
 * Definitions: world/rewards/[id].v[N].json, of kind bundle or activity.
 */
public final class RewardEscrow
{
	private RewardEscrow()
	{
	}

	public static final String		MODULE		= "rewards.escrow";
	public static final String		FOLDER		= "rewards";
	public static final String		FAMILY		= "rewards";
	public static final String[]	POLICIES	= { "forfeit", "bank", "keep" };
	/** the most entries a character's ledger keeps */
	public static final int			MAX_ENTRIES	= 24;
	/** the most event keys a character's ledger remembers */
	public static final int			MAX_KEYS	= 96;

	/** a reward bundle, or an activity's policies */
	public static final class Def
	{
		public final boolean			activity;
		/** bundle: catalog item names and counts */
		public final Map<String, Long>	items		= new LinkedHashMap<String, Long>();
		public final long				money;
		public final Map<String, Long>	resources;
		/** activity: what leaving, failing, dying and a restore do to provisional rewards */
		public final String				onLeave, onFail, onDeath, onRestore;
		public final List<String>		claimRooms;

		Def(final Node n) throws IOException
		{
			activity = n.choice("kind", "", "bundle", "activity").equals("activity");
			if(activity)
			{
				n.allow("schema", "id", "version", "title", "kind", "on_leave", "on_fail", "on_death", "on_restore", "claim_rooms");
				onLeave = n.choice("on_leave", "forfeit", POLICIES);
				onFail = n.choice("on_fail", "forfeit", POLICIES);
				onDeath = n.choice("on_death", "forfeit", POLICIES);
				onRestore = n.choice("on_restore", "keep", POLICIES);
				claimRooms = n.strings("claim_rooms", 16, false);
				money = 0;
				resources = new TreeMap<String, Long>();
			}
			else
			{
				n.allow("schema", "id", "version", "title", "kind", "items", "money", "resources");
				for(final Node i : n.objects("items", 16))
				{
					i.allow("name", "count");
					final String name = i.string("name");
					if(name.trim().length() == 0)
						throw i.error("name", "required");
					items.put(name, Long.valueOf(i.integer("count", 1, 1, 20)));
				}
				money = n.integer("money", 0, 0, 1000000000);
				resources = n.amounts("resources", 1, 1000000, 8);
				if(items.isEmpty() && (money == 0) && resources.isEmpty())
					throw n.error(null, "a bundle gives at least an item, money or a resource");
				onLeave = onFail = onDeath = onRestore = "keep";
				claimRooms = new ArrayList<String>();
			}
		}

		public String describe()
		{
			final List<String> parts = new ArrayList<String>();
			for(final Map.Entry<String, Long> e : items.entrySet())
				parts.add(((e.getValue().longValue() > 1) ? (e.getValue() + " x ") : "") + e.getKey());
			if(money > 0)
				parts.add(money + " in coin");
			for(final Map.Entry<String, Long> e : resources.entrySet())
				parts.add(e.getValue() + " " + e.getKey());
			return String.join(", ", parts);
		}
	}

	private static Library<Def> library = null;

	private static final ModuleContent.Reader<Def> READER = new ModuleContent.Reader<Def>()
	{
		@Override
		public Def read(final Node node) throws IOException
		{
			return new Def(node);
		}
	};

	public static Library<Def> load()
	{
		return ModuleContent.load(ModuleContent.world(), FOLDER, "reward", READER);
	}

	public static synchronized Library<Def> definitions()
	{
		if(library == null)
			library = load();
		return library;
	}

	/** a token for this boot: a run begun under another was restored (or the game restarted) since */
	private static String bootToken = Long.toString(System.nanoTime(), 36);

	public static synchronized void reset()
	{
		library = null;
		bootToken = Long.toString(System.nanoTime(), 36);
	}

	public static boolean enabled()
	{
		return OptionalModules.enabled(MODULE);
	}

	public static void require() throws IOException
	{
		if(!enabled())
			throw new IOException("reward escrow (" + MODULE + ") is not active in this campaign");
	}

	private static Entry<Def> activityDef(final String id) throws IOException
	{
		final Entry<Def> e = definitions().latest(id);
		if(!e.value.activity)
			throw new IOException(id + " is a reward bundle, not an activity");
		return e;
	}

	private static Entry<Def> bundleDef(final String id) throws IOException
	{
		final Entry<Def> e = definitions().latest(id);
		if(e.value.activity)
			throw new IOException(id + " is an activity, not a reward bundle");
		return e;
	}

	/** a character's escrow: its runs, entries and remembered keys */
	private static final class Ledger
	{
		final MOB			mob;
		final JSONObject	rec;
		final JSONObject	runs;
		final JSONObject	entries;
		final List<String>	keys	= new ArrayList<String>();
		long				serial;
		long				runSerial;
		boolean				dirty	= false;

		Ledger(final MOB mob) throws IOException
		{
			this.mob = mob;
			rec = OptionalModules.get(MODULE, OptionalModules.character(mob));
			runs = parse(rec, "runs");
			final JSONObject l = parse(rec, "ledger");
			entries = (l.get("entries") instanceof JSONObject) ? (JSONObject)l.get("entries") : new JSONObject();
			serial = (l.get("serial") instanceof Number) ? ((Number)l.get("serial")).longValue() : 0;
			runSerial = (l.get("runs") instanceof Number) ? ((Number)l.get("runs")).longValue() : 0;
			final JSONObject k = parse(rec, "keys");
			if(k.get("keys") instanceof Object[])
			{
				for(final Object o : (Object[])k.get("keys"))
					keys.add(String.valueOf(o));
			}
			restoreRuns();
		}

		private static JSONObject parse(final JSONObject rec, final String field) throws IOException
		{
			final String raw = (String)rec.get(field);
			return ((raw == null) || raw.isEmpty()) ? new JSONObject() : ModuleDefinitions.parse(raw);
		}

		/** runs begun under another boot: their activity's on_restore, once */
		private void restoreRuns() throws IOException
		{
			for(final String activity : new ArrayList<String>(runs.keySet()))
			{
				final JSONObject run = (JSONObject)runs.get(activity);
				if(bootToken.equals(run.get("boot")))
					continue;
				String policy = "keep";
				try
				{
					policy = activityDef(activity).value.onRestore;
				}
				catch(final IOException e)
				{
					// an activity no longer installed keeps what it had
				}
				run.put("boot", bootToken);
				dirty = true;
				if(!policy.equals("keep"))
					settle(activity, policy, "restore");
			}
		}

		JSONObject run(final String activity)
		{
			return (runs.get(activity) instanceof JSONObject) ? (JSONObject)runs.get(activity) : null;
		}

		/** applies a policy to an activity's provisional entries, and closes its run unless kept */
		int settle(final String activity, final String policy, final String why)
		{
			if(policy.equals("keep"))
				return 0;
			final JSONObject run = run(activity);
			final String runKey = (run == null) ? null : (activity + "#" + run.get("run"));
			int n = 0;
			for(final String k : entries.keySet())
			{
				final JSONObject e = (JSONObject)entries.get(k);
				if(e.get("state").equals("provisional") && (runKey != null) && runKey.equals(e.get("run")))
				{
					e.put("state", policy.equals("bank") ? "banked" : "forfeited");
					e.put("why", why);
					n++;
				}
			}
			runs.remove(activity);
			dirty = true;
			ModuleStats.add(FAMILY, activity, policy.equals("bank") ? "banked" : "forfeited", n);
			return n;
		}

		void save() throws IOException
		{
			if(!dirty)
				return;
			// keep the open entries; drop the oldest closed ones past the limit
			final List<String> order = new ArrayList<String>(entries.keySet());
			Collections.sort(order, (a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)));
			for(final String k : order)
			{
				if(entries.size() <= MAX_ENTRIES)
					break;
				final String st = (String)((JSONObject)entries.get(k)).get("state");
				if(st.equals("claimed") || st.equals("forfeited"))
					entries.remove(k);
			}
			while(keys.size() > MAX_KEYS)
				keys.remove(0);
			final JSONObject l = new JSONObject();
			l.put("serial", Long.valueOf(serial));
			l.put("runs", Long.valueOf(runSerial));
			l.put("entries", entries);
			final JSONObject k = new JSONObject();
			k.put("keys", keys.toArray(new String[0]));
			final String ledgerText = ModuleDefinitions.json(l), runsText = runs.isEmpty() ? "" : ModuleDefinitions.json(runs), keysText = keys.isEmpty() ? "" : ModuleDefinitions.json(k);
			if((ledgerText.length() > 4096) || (runsText.length() > 4096) || (keysText.length() > 4096))
				throw new IOException("this character's reward ledger is full; claim or forfeit rewards first");
			rec.put("ledger", entries.isEmpty() && (serial == 0) ? "" : ledgerText);
			rec.put("runs", runsText);
			rec.put("keys", keysText);
			OptionalModules.put(MODULE, OptionalModules.character(mob), rec);
			dirty = false;
		}
	}

	/** reading a ledger writes only what a restore owes it (an activity's on_restore) */
	private static Ledger ledger(final MOB mob) throws IOException
	{
		require();
		final Ledger L = new Ledger(mob);
		if(L.dirty)
			L.save();
		return L;
	}

	/**
	 * Begins (or continues) a character's run of an activity.
	 * @return the run's number
	 */
	public static synchronized long begin(final MOB mob, final String activity) throws IOException
	{
		activityDef(activity);
		final Ledger L = ledger(mob);
		JSONObject run = L.run(activity);
		if(run == null)
		{
			run = new JSONObject();
			run.put("run", Long.valueOf(++L.runSerial));
			run.put("boot", bootToken);
			L.runs.put(activity, run);
			L.dirty = true;
			L.save();
			ModuleStats.count(FAMILY, activity, "runs begun");
		}
		return ((Number)run.get("run")).longValue();
	}

	/** whether a character has a run of an activity under way */
	public static boolean inRun(final MOB mob, final String activity) throws IOException
	{
		return ledger(mob).run(activity) != null;
	}

	/**
	 * Offers a provisional reward in a character's run of an activity (begun
	 * if need be).  One key gives one entry: the same key again adds nothing.
	 * @return the entry's number, or 0 when the key was already rewarded
	 */
	public static synchronized long offer(final MOB mob, final String activity, final String reward, final String key) throws IOException
	{
		ModuleContent.gameThread();
		final Entry<Def> bundle = bundleDef(reward);
		final long runNo = begin(mob, activity);
		final Ledger L = ledger(mob);
		final String fullKey = activity + "#" + runNo + ":" + ((key == null) ? reward : key);
		if(L.keys.contains(fullKey))
		{
			ModuleStats.count(FAMILY, activity, "duplicate events suppressed");
			return 0;
		}
		int open = 0;
		for(final String k : L.entries.keySet())
		{
			final String st = (String)((JSONObject)L.entries.get(k)).get("state");
			if(st.equals("provisional") || st.equals("banked"))
				open++;
		}
		if(open >= MAX_ENTRIES)
			throw new IOException("too many unclaimed rewards; claim some first");
		final JSONObject e = new JSONObject();
		e.put("run", activity + "#" + runNo);
		e.put("reward", bundle.pin());
		e.put("state", "provisional");
		e.put("key", fullKey);
		final long n = ++L.serial;
		L.entries.put(Long.toString(n), e);
		L.keys.add(fullKey);
		L.dirty = true;
		L.save();
		ModuleStats.count(FAMILY, activity, "offered");
		ModuleStats.reading(FAMILY, activity, "ledger entries", L.entries.size()); // Q12
		Provenance.event(mob, MODULE, bundle.key(), "reward " + n + " offered in " + activity + ", provisional"); // Q05
		return n;
	}

	/** offers the reward to every member of a party in the room, each their own entry */
	public static List<Long> offerParty(final MOB leader, final String activity, final String reward, final String key) throws IOException
	{
		final List<Long> out = new ArrayList<Long>();
		for(final MOB M : Party.here(leader))
			out.add(Long.valueOf(offer(M, activity, reward, key)));
		return out;
	}

	/** banks a character's provisional rewards in an activity's run; the run goes on */
	public static synchronized int bank(final MOB mob, final String activity) throws IOException
	{
		final Ledger L = ledger(mob);
		final JSONObject run = L.run(activity);
		if(run == null)
			return 0;
		final String runKey = activity + "#" + run.get("run");
		int n = 0;
		for(final String k : L.entries.keySet())
		{
			final JSONObject e = (JSONObject)L.entries.get(k);
			if(e.get("state").equals("provisional") && runKey.equals(e.get("run")))
			{
				e.put("state", "banked");
				e.put("why", "banked");
				n++;
			}
		}
		L.dirty = n > 0;
		L.save();
		ModuleStats.add(FAMILY, activity, "banked", n);
		return n;
	}

	/**
	 * Ends a character's run of an activity: complete banks its provisional
	 * rewards; fail, leave and death do what the activity declares.  A kept
	 * run goes on.
	 * @return how many rewards were banked or forfeited
	 */
	public static synchronized int end(final MOB mob, final String activity, final String outcome) throws IOException
	{
		final Def a = activityDef(activity).value;
		final String policy = outcome.equalsIgnoreCase("complete") ? "bank" : outcome.equalsIgnoreCase("fail") ? a.onFail
			: outcome.equalsIgnoreCase("leave") ? a.onLeave : outcome.equalsIgnoreCase("death") ? a.onDeath : null;
		if(policy == null)
			throw new IOException("an outcome is complete, fail, leave or death");
		final Ledger L = ledger(mob);
		if(L.run(activity) == null)
			return 0;
		final int n = L.settle(activity, policy, outcome.toLowerCase(Locale.ROOT));
		L.save();
		return n;
	}

	/** a character died: each run under way does what its activity declares for a death */
	public static void died(final MOB victim)
	{
		if((victim == null) || !enabled())
			return;
		try
		{
			final Ledger L = ledger(victim);
			for(final String activity : new ArrayList<String>(L.runs.keySet()))
				end(victim, activity, "death");
		}
		catch(final IOException e)
		{
			Log.errOut("RewardEscrow", victim.Name() + "'s death: " + e.getMessage());
		}
	}

	/** a character left an activity's place: what the activity declares for leaving */
	public static void left(final MOB mob, final String activity)
	{
		try
		{
			if(enabled() && inRun(mob, activity))
				end(mob, activity, "leave");
		}
		catch(final IOException e)
		{
			Log.errOut("RewardEscrow", mob.Name() + " leaving " + activity + ": " + e.getMessage());
		}
	}

	/** where a character may claim an entry's reward, as a reason it cannot here, or "" */
	private static String claimPlace(final MOB mob, final JSONObject e)
	{
		final String run = (String)e.get("run");
		final String activity = run.substring(0, run.lastIndexOf('#'));
		try
		{
			final List<String> rooms = activityDef(activity).value.claimRooms;
			if(rooms.isEmpty())
				return "";
			final String here = (mob.location() == null) ? "" : CMLib.map().getExtendedRoomID(mob.location());
			for(final String r : rooms)
			{
				if(here.equalsIgnoreCase(r) || here.toUpperCase(Locale.ROOT).endsWith(r.toUpperCase(Locale.ROOT)))
					return "";
			}
			return "this reward is claimed at " + String.join(" or ", rooms);
		}
		catch(final IOException ex)
		{
			return "";
		}
	}

	/**
	 * Claims banked rewards: every item, coin and resource of each is made and
	 * given in one transaction, with a receipt; any problem gives nothing.
	 * @param which an entry's number, or 0 for every banked reward claimable here
	 * @return the receipt in words
	 */
	/** claims banked rewards; a refusal is noted (Q05) with what to do, and gives nothing */
	public static synchronized String claim(final MOB mob, final long which) throws IOException
	{
		try
		{
			return claimChecked(mob, which);
		}
		catch(final IOException e)
		{
			Provenance.refused(mob, MODULE, (which == 0) ? "claim of every banked reward" : "claim of reward " + which, e.getMessage(),
				"put right what stops it (room to carry, the place it is claimed at, a reward still provisional), then REWARDS CLAIM again; nothing was given");
			throw e;
		}
	}

	private static String claimChecked(final MOB mob, final long which) throws IOException
	{
		ModuleContent.gameThread();
		final Ledger L = ledger(mob);
		final List<String> chosen = new ArrayList<String>();
		final List<String> elsewhere = new ArrayList<String>();
		for(final String k : L.entries.keySet())
		{
			final JSONObject e = (JSONObject)L.entries.get(k);
			if((which != 0) && !k.equals(Long.toString(which)))
				continue;
			if(which != 0)
			{
				final String st = (String)e.get("state");
				if(st.equals("claimed"))
					throw new IOException("reward " + which + " was already claimed");
				if(!st.equals("banked"))
					throw new IOException("reward " + which + " is " + st + ", not banked");
			}
			if(!e.get("state").equals("banked"))
				continue;
			final String place = claimPlace(mob, e);
			if(place.length() > 0)
			{
				if(which != 0)
					throw new IOException(place);
				elsewhere.add(k);
				continue;
			}
			chosen.add(k);
		}
		if((which != 0) && chosen.isEmpty())
			throw new IOException("no reward " + which);
		if(chosen.isEmpty())
			return elsewhere.isEmpty() ? "You have no banked rewards to claim." : "Your banked rewards are claimed elsewhere.";
		// prepare: every definition pinned and every item available, before anything is made
		final List<Entry<Def>> bundles = new ArrayList<Entry<Def>>();
		for(final String k : chosen)
			bundles.add(definitions().pinned(((JSONObject)L.entries.get(k)).get("reward")));
		final List<Item> made = new ArrayList<Item>();
		long money = 0;
		final Map<String, Long> gains = new TreeMap<String, Long>();
		try
		{
			for(final Entry<Def> b : bundles)
			{
				for(final Map.Entry<String, Long> i : b.value.items.entrySet())
				{
					final Item proto = CMLib.catalog().getCatalogItem(i.getKey());
					if(proto == null)
						throw new IOException(b.path + ": no catalog item '" + i.getKey() + "'");
					for(int n = 0; n < i.getValue().longValue(); n++)
					{
						final Item I = (Item)proto.copyOf();
						made.add(I);
					}
				}
				money += b.value.money;
				for(final Map.Entry<String, Long> g : b.value.resources.entrySet())
					gains.put(g.getKey(), Long.valueOf((gains.containsKey(g.getKey()) ? gains.get(g.getKey()).longValue() : 0) + g.getValue().longValue()));
			}
			if(!gains.isEmpty() && !NamedResources.enabled())
				throw new IOException("these rewards give named resources, which are not active in this campaign");
			for(final String pool : gains.keySet())
				NamedResources.definitions().latest(pool);
		}
		catch(final IOException e)
		{
			for(final Item I : made)
				I.destroy();
			ModuleStats.count(FAMILY, "claim", "refused");
			throw e;
		}
		// commit: mark the entries claimed, then give everything, in this one game-thread step
		final List<String> given = new ArrayList<String>();
		for(final String k : chosen)
		{
			final JSONObject e = (JSONObject)L.entries.get(k);
			e.put("state", "claimed");
			e.put("why", "claimed");
		}
		L.dirty = true;
		L.save();
		for(final Item I : made)
		{
			mob.addItem(I);
			given.add(I.name());
		}
		if(money > 0)
		{
			CMLib.beanCounter().addMoney(mob, CMLib.beanCounter().getCurrency(mob), (double)money);
			given.add(CMLib.beanCounter().nameCurrencyShort(mob, (double)money));
		}
		for(final Map.Entry<String, Long> g : gains.entrySet())
		{
			NamedResources.gain(mob, g.getKey(), g.getValue().longValue(), "rewards");
			given.add(g.getValue() + " " + g.getKey());
		}
		mob.recoverPhyStats();
		ModuleStats.add(FAMILY, "claim", "claimed", chosen.size());
		Provenance.event(mob, MODULE, "claim", "claimed " + String.join(", ", chosen) + ": " + String.join(", ", given)); // Q05
		return "Claimed reward" + ((chosen.size() > 1) ? "s " : " ") + String.join(", ", chosen) + ": " + String.join(", ", given) + "."
			+ (elsewhere.isEmpty() ? "" : " (" + elsewhere.size() + " more are claimed elsewhere.)");
	}

	/** an entry's state, for scripts and tests: provisional, banked, forfeited, claimed or "" */
	public static String state(final MOB mob, final long entry) throws IOException
	{
		final JSONObject e = (JSONObject)ledger(mob).entries.get(Long.toString(entry));
		return (e == null) ? "" : (String)e.get("state");
	}

	/** how many of a character's entries are in a state */
	public static int count(final MOB mob, final String state) throws IOException
	{
		int n = 0;
		final Ledger L = ledger(mob);
		for(final String k : L.entries.keySet())
		{
			if(state.equals(((JSONObject)L.entries.get(k)).get("state")))
				n++;
		}
		return n;
	}

	/** REWARDS: a character's ledger in words */
	public static String describe(final MOB mob) throws IOException
	{
		final Ledger L = ledger(mob);
		if(L.entries.isEmpty() && L.runs.isEmpty())
			return "You have no rewards in escrow.";
		final StringBuilder s = new StringBuilder("Your rewards:");
		for(final String activity : new TreeSet<String>(L.runs.keySet()))
		{
			String title = activity;
			try
			{
				title = activityDef(activity).title;
			}
			catch(final IOException e)
			{
				// shown by its ID
			}
			s.append("\n  Under way: ").append(title).append(" (run ").append(((JSONObject)L.runs.get(activity)).get("run")).append(")");
		}
		final List<String> order = new ArrayList<String>(L.entries.keySet());
		Collections.sort(order, (a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)));
		for(final String k : order)
		{
			final JSONObject e = (JSONObject)L.entries.get(k);
			String what;
			try
			{
				final Entry<Def> b = definitions().pinned(e.get("reward"));
				what = b.title + ": " + b.value.describe();
			}
			catch(final IOException ex)
			{
				what = "unavailable (" + ex.getMessage() + ")";
			}
			s.append("\n  ").append(k).append(". ").append(e.get("state")).append(", ").append(what).append(" (").append(e.get("run")).append(")");
		}
		return s.toString();
	}

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
			return "REWARDS";
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
			return Arrays.asList("ledger", "runs", "keys");
		}

		@Override
		public List<String> requires()
		{
			return new ArrayList<String>();
		}

		@Override
		public Map<String, long[]> integerSettings()
		{
			return new TreeMap<String, long[]>();
		}

		@Override
		public void reset()
		{
			RewardEscrow.reset();
		}

		@Override
		public List<String> check()
		{
			final Library<Def> lib = load();
			final List<String> out = new ArrayList<String>(lib.errors);
			final Library<NamedResources.Pool> pools = NamedResources.load();
			for(final Entry<Def> e : lib.entries.values())
			{
				for(final String name : e.value.items.keySet())
				{
					if((CMLib.catalog() != null) && (CMLib.catalog().getCatalogItem(name) == null))
						out.add(e.path + ": /items: no catalog item '" + name + "'");
				}
				for(final String p : e.value.resources.keySet())
				{
					if(!pools.has(p))
						out.add(e.path + ": /resources/" + p + ": no pool " + p + " is installed in pools/");
				}
				for(final String r : e.value.claimRooms)
				{
					if(CMLib.map().getRoom(r) == null)
						out.add(e.path + ": /claim_rooms: no room " + r);
				}
			}
			return out;
		}

		@Override
		public String list() throws IOException
		{
			final Library<Def> lib = load();
			if(lib.entries.isEmpty())
				return "No rewards or activities are installed in " + FOLDER + "/." + (lib.errors.isEmpty() ? "" : "\n" + String.join("\n", lib.errors));
			final StringBuilder s = new StringBuilder("Rewards and activities:");
			for(final Entry<Def> e : lib.entries.values())
			{
				s.append("\n  ").append(e.key()).append(" (").append(e.title).append("): ");
				if(e.value.activity)
					s.append("activity; leave ").append(e.value.onLeave).append(", fail ").append(e.value.onFail).append(", death ").append(e.value.onDeath)
					 .append(", restore ").append(e.value.onRestore).append(e.value.claimRooms.isEmpty() ? "" : ", claimed at " + e.value.claimRooms);
				else
					s.append(e.value.describe());
			}
			if(!lib.errors.isEmpty())
				s.append("\n").append(String.join("\n", lib.errors));
			return s.toString();
		}

		@Override
		public String reload() throws IOException
		{
			synchronized(RewardEscrow.class)
			{
				library = null;
			}
			return definitions().report();
		}

		@Override
		public String world(final MOB mob, final List<String> words) throws IOException
		{
			if((words.size() >= 1) && words.get(0).equalsIgnoreCase("SHOW"))
			{
				MOB who = mob;
				if(words.size() > 1)
				{
					who = (mob.location() == null) ? null : mob.location().fetchInhabitant(CMParms.combine(words, 1));
					if(who == null)
						throw new IOException("no such character here");
				}
				return describe(who).replace("Your rewards", who.Name() + "'s rewards").replace("You have", who.Name() + " has");
			}
			// WORLD REWARDS OFFER [activity] [reward] ([key]): a builder's test offer to themselves
			if((words.size() >= 3) && words.get(0).equalsIgnoreCase("OFFER"))
			{
				final long n = offer(mob, words.get(1).toLowerCase(Locale.ROOT), words.get(2).toLowerCase(Locale.ROOT), (words.size() > 3) ? words.get(3) : null);
				return (n == 0) ? "That key was already rewarded in this run." : ("Offered as reward " + n + ".");
			}
			if((words.size() == 3) && words.get(0).equalsIgnoreCase("END"))
				return "Settled " + end(mob, words.get(1).toLowerCase(Locale.ROOT), words.get(2)) + " rewards.";
			if((words.size() == 2) && words.get(0).equalsIgnoreCase("BANK"))
				return "Banked " + bank(mob, words.get(1).toLowerCase(Locale.ROOT)) + " rewards.";
			return null;
		}
	};

	static
	{
		ModuleContent.register(FAMILY_ENTRY);
		ModuleStats.limit(FAMILY, "ledger entries", MAX_ENTRIES); // Q12
	}

	/** the area an activity's Prop_RewardActivity marks, for leaving */
	public static boolean inActivityPlace(final MOB mob, final Area A)
	{
		return (mob != null) && (mob.location() != null) && (mob.location().getArea() == A);
	}
}
