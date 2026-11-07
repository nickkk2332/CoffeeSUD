package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.Clan;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.ModuleContent.Entry;
import com.planet_ink.coffee_mud.core.ModuleContent.Library;
import com.planet_ink.coffee_mud.core.ModuleContent.Node;
import com.planet_ink.coffee_mud.core.collections.Pair;

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
 * P01: progression tracks, the optional module progression.tracks.  A track
 * belongs to one kind of owner (a character, an organization or an item),
 * has its own points, earning rules (kills, composed casts, crafts,
 * contributions to an organization, script grants), a level curve with a
 * cap, milestones (combat stats for the owner or the item, unlock flags) and
 * a reset policy.  Each owner earns on their own record; one source key
 * grants once.  A track that replaces an old one takes over its records,
 * once and the same way each time.
 * Definitions: world/tracks/[id].v[N].json.
 */
public final class ProgressionTracks
{
	private ProgressionTracks()
	{
	}

	public static final String		MODULE	= "progression.tracks";
	public static final String		FOLDER	= "tracks";
	public static final String		FAMILY	= "tracks";
	public static final String[]	OWNERS	= { "character", "organization", "item" };
	public static final String[]	EVENTS	= { "kill", "cast", "craft", "contribute", "script" };
	/** the most source keys an owner's track remembers */
	public static final int			KEYS	= 24;

	/** one earning rule */
	public static final class Earn
	{
		public final String	on;
		public final long	amount;
		public final String	skill;
		public final long	moneyPerPoint;

		Earn(final Node n) throws IOException
		{
			n.allow("on", "amount", "skill", "money_per_point");
			on = n.choice("on", "", EVENTS);
			amount = n.integer("amount", 1, 0, 1000000);
			skill = n.has("skill") ? n.id("skill") : "";
			if(!skill.isEmpty() && !on.equals("cast"))
				throw n.error("skill", "is for cast");
			moneyPerPoint = n.integer("money_per_point", on.equals("contribute") ? 10 : 0, 0, 1000000);
			if(on.equals("contribute") && (moneyPerPoint < 1))
				throw n.error("money_per_point", "a contribution needs a price per point");
		}
	}

	/** one milestone */
	public static final class Milestone
	{
		public final int		level;
		public final String		title;
		public final String		stats;
		public final String		unlock;
		public final String		message;

		Milestone(final Node n) throws IOException
		{
			n.allow("level", "title", "stats", "unlock", "message");
			level = (int)n.integer("level", 1, 1000);
			title = n.string("title", "level " + level, 120);
			final Node st = n.object("stats");
			if(st == null)
				stats = "";
			else
			{
				stats = ModuleDefinitions.json(st.obj);
				try
				{
					CombatRules.bonuses(stats);
				}
				catch(final IOException e)
				{
					throw n.error("stats", e.getMessage());
				}
			}
			unlock = n.has("unlock") ? n.id("unlock") : "";
			message = n.string("message", "", 400);
		}

		public String describe()
		{
			final List<String> parts = new ArrayList<String>();
			if(!stats.isEmpty())
			{
				try
				{
					parts.add(CombatRules.bonuses(stats).describe());
				}
				catch(final IOException e)
				{
					parts.add("stats");
				}
			}
			if(!unlock.isEmpty())
				parts.add("unlocks " + unlock);
			return title + (parts.isEmpty() ? "" : " (" + String.join("; ", parts) + ")");
		}
	}

	/** one track */
	public static final class Track
	{
		public final String				owner;
		public final String				points;
		public final List<Earn>			earn		= new ArrayList<Earn>();
		/** the points each level needs, in total */
		public final List<Long>			levels		= new ArrayList<Long>();
		public final List<Milestone>	milestones	= new ArrayList<Milestone>();
		public final String				reset;
		public final List<String>		replaces;

		Track(final Node n) throws IOException
		{
			n.allow("schema", "id", "version", "title", "owner", "points", "earn", "curve", "milestones", "reset", "replaces");
			owner = n.choice("owner", "", OWNERS);
			points = n.string("points", "points", 40);
			for(final Node e : n.objects("earn", 12))
			{
				final Earn x = new Earn(e);
				if(x.on.equals("contribute") && !owner.equals("organization"))
					throw e.error("on", "contributions are for organization tracks");
				earn.add(x);
			}
			final Node c = n.required("curve");
			final String kind = c.choice("type", "", "table", "linear");
			if(kind.equals("table"))
			{
				c.allow("type", "points");
				long last = 0;
				final Object[] raw = c.has("points") && (c.obj.get("points") instanceof Object[]) ? (Object[])c.obj.get("points") : new Object[0];
				if((raw.length == 0) || (raw.length > 100))
					throw c.error("points", "1 to 100 levels");
				for(int i = 0; i < raw.length; i++)
				{
					if(!(raw[i] instanceof Number) || (((Number)raw[i]).longValue() <= last))
						throw new IOException(n.file + ": " + c.at("points") + "/" + i + ": each level needs more points than the one before");
					last = ((Number)raw[i]).longValue();
					levels.add(Long.valueOf(last));
				}
			}
			else
			{
				c.allow("type", "per_level", "cap");
				final long per = c.integer("per_level", 1, 1000000);
				final int cap = (int)c.integer("cap", 1, 100);
				for(int i = 1; i <= cap; i++)
					levels.add(Long.valueOf(per * i));
			}
			for(final Node m : n.objects("milestones", 32))
			{
				final Milestone x = new Milestone(m);
				if(x.level > levels.size())
					throw m.error("level", "the track's cap is level " + levels.size());
				if(!x.stats.isEmpty() && owner.equals("organization"))
					throw m.error("stats", "an organization has no combat stats; give an unlock");
				milestones.add(x);
			}
			reset = n.choice("reset", "never", "never", "command");
			replaces = n.strings("replaces", 8, true);
		}

		/** the level a total of points reaches */
		public int level(final long total)
		{
			int l = 0;
			while((l < levels.size()) && (total >= levels.get(l).longValue()))
				l++;
			return l;
		}

		public long cap()
		{
			return levels.get(levels.size() - 1).longValue();
		}
	}

	private static Library<Track> library = null;

	private static final ModuleContent.Reader<Track> READER = new ModuleContent.Reader<Track>()
	{
		@Override
		public Track read(final Node node) throws IOException
		{
			return new Track(node);
		}
	};

	public static Library<Track> load()
	{
		return ModuleContent.load(ModuleContent.world(), FOLDER, "track", READER);
	}

	public static synchronized Library<Track> definitions()
	{
		if(library == null)
			library = load();
		return library;
	}

	public static synchronized void reset()
	{
		library = null;
	}

	public static boolean enabled()
	{
		return OptionalModules.enabled(MODULE);
	}

	public static void require() throws IOException
	{
		if(!enabled())
			throw new IOException("progression tracks (" + MODULE + ") are not active in this campaign");
	}

	// ---- owners

	/** a track owner: a character, an organization or an item, with its module record */
	public static final class Owner
	{
		public final String					kind;
		public final OptionalModules.Owner	key;
		public final String					name;
		final MOB							mob;
		final Item							item;

		Owner(final String kind, final OptionalModules.Owner key, final String name, final MOB mob, final Item item)
		{
			this.kind = kind;
			this.key = key;
			this.name = name;
			this.mob = mob;
			this.item = item;
		}
	}

	public static Owner of(final MOB mob) throws IOException
	{
		return new Owner("character", OptionalModules.character(mob), mob.Name(), mob, null);
	}

	public static Owner of(final Clan clan) throws IOException
	{
		return new Owner("organization", OptionalModules.organization(clan), clan.getName(), null, null);
	}

	public static Owner of(final Item item) throws IOException
	{
		return new Owner("item", OptionalModules.item(item), item.name(), null, item);
	}

	private static JSONObject records(final Owner o) throws IOException
	{
		final JSONObject rec = OptionalModules.get(MODULE, o.key);
		final String raw = (String)rec.get("tracks");
		final JSONObject all = ((raw == null) || raw.isEmpty()) ? new JSONObject() : ModuleDefinitions.parse(raw);
		migrate(all);
		return all;
	}

	private static void store(final Owner o, final JSONObject all) throws IOException
	{
		final JSONObject rec = OptionalModules.get(MODULE, o.key);
		final String text = all.isEmpty() ? "" : ModuleDefinitions.json(all);
		if(text.length() > 4096)
			throw new IOException(o.name + " has too many tracks");
		rec.put("tracks", text);
		OptionalModules.put(MODULE, o.key, rec);
	}

	/**
	 * Q06: a record under an old track ID moves to the track that replaces it,
	 * once: its points are kept (to the new cap), its keys too.  Two tracks
	 * replacing one ID take it in ID order, the first only.
	 */
	private static boolean migrate(final JSONObject all)
	{
		boolean moved = false;
		for(final Entry<Track> t : definitions().latestAll())
		{
			for(final String old : t.value.replaces)
			{
				if(!all.containsKey(old) || all.containsKey(t.id) || definitions().has(old))
					continue;
				final JSONObject r = (JSONObject)all.remove(old);
				r.put("pin", t.pin());
				r.put("from", old);
				all.put(t.id, r);
				moved = true;
				ModuleStats.count(FAMILY, t.id, "records migrated from " + old);
			}
		}
		return moved;
	}

	/** an owner's record of a track, or a new one */
	private static JSONObject record(final JSONObject all, final Entry<Track> t)
	{
		if(all.get(t.id) instanceof JSONObject)
			return (JSONObject)all.get(t.id);
		final JSONObject r = new JSONObject();
		r.put("pin", t.pin());
		r.put("points", Long.valueOf(0));
		r.put("level", Long.valueOf(0));
		all.put(t.id, r);
		return r;
	}

	private static List<String> keys(final JSONObject r)
	{
		final List<String> out = new ArrayList<String>();
		if(r.get("keys") instanceof Object[])
		{
			for(final Object o : (Object[])r.get("keys"))
				out.add(String.valueOf(o));
		}
		return out;
	}

	/**
	 * Gives an owner points on a track, to its cap.  A source key already
	 * counted adds nothing.  New milestones are told to the owner.
	 * @return the points added
	 */
	public static synchronized long earn(final Owner o, final String trackId, final long amount, final String key) throws IOException
	{
		require();
		final Entry<Track> t = definitions().latest(trackId);
		if(!t.value.owner.equals(o.kind))
			throw new IOException(trackId + " is a track of a " + t.value.owner + ", not a " + o.kind);
		if(amount < 0)
			throw new IOException("points cannot be negative");
		final JSONObject all = records(o);
		final JSONObject r = record(all, t);
		final List<String> seen = keys(r);
		if((key != null) && seen.contains(key))
		{
			ModuleStats.count(FAMILY, trackId, "duplicate sources suppressed");
			return 0;
		}
		final long before = ((Number)r.get("points")).longValue();
		final long to = Math.min(t.value.cap(), before + amount);
		final int oldLevel = ((Number)r.get("level")).intValue();
		final int newLevel = t.value.level(to);
		r.put("points", Long.valueOf(to));
		r.put("level", Long.valueOf(newLevel));
		r.put("pin", t.pin());
		if(key != null)
		{
			seen.add(key);
			while(seen.size() > KEYS)
				seen.remove(0);
			r.put("keys", seen.toArray(new String[0]));
		}
		store(o, all);
		ModuleStats.add(FAMILY, trackId, "points earned", to - before);
		for(final Milestone m : t.value.milestones)
		{
			if((m.level > oldLevel) && (m.level <= newLevel))
				reached(o, t, m);
		}
		return to - before;
	}

	private static void reached(final Owner o, final Entry<Track> t, final Milestone m)
	{
		ModuleStats.count(FAMILY, t.id, "milestones reached");
		final String text = t.title + ": " + m.title + "." + (m.message.isEmpty() ? "" : " " + m.message);
		if(o.mob != null)
		{
			o.mob.tell(text);
			if(!m.stats.isEmpty())
				statSource(o.mob);
		}
		if((o.item != null) && (o.item.owner() instanceof MOB))
			((MOB)o.item.owner()).tell(text);
	}

	/** a character's stat source for milestone stats, added once */
	private static void statSource(final MOB mob)
	{
		if(mob.fetchEffect("Prop_Tracks") == null)
		{
			final Ability A = CMClass.getAbility("Prop_Tracks");
			if(A != null)
				mob.addNonUninvokableEffect(A);
		}
	}

	/** an owner's points and level on a track, without writing */
	public static long[] progress(final Owner o, final String trackId) throws IOException
	{
		require();
		final JSONObject all = records(o);
		if(!(all.get(trackId) instanceof JSONObject))
			return new long[] { 0, 0 };
		final JSONObject r = (JSONObject)all.get(trackId);
		return new long[] { ((Number)r.get("points")).longValue(), ((Number)r.get("level")).longValue() };
	}

	/** whether an owner's tracks unlocked a flag */
	public static boolean unlocked(final Owner o, final String flag) throws IOException
	{
		if(!enabled())
			return false;
		final JSONObject all = records(o);
		for(final String id : all.keySet())
		{
			final Entry<Track> t;
			try
			{
				t = definitions().latest(id);
			}
			catch(final IOException e)
			{
				continue;
			}
			final int level = ((Number)((JSONObject)all.get(id)).get("level")).intValue();
			for(final Milestone m : t.value.milestones)
			{
				if((m.level <= level) && m.unlock.equals(flag))
					return true;
			}
		}
		return false;
	}

	/** whether a character or any of their organizations unlocked a flag */
	public static boolean unlockedFor(final MOB mob, final String flag) throws IOException
	{
		if(unlocked(of(mob), flag))
			return true;
		for(final Pair<Clan, Integer> c : mob.clans())
		{
			if(unlocked(of(c.first), flag))
				return true;
		}
		return false;
	}

	/** the combat stats an owner's milestones give, for Prop_Tracks */
	public static CombatRules.Stats stats(final Owner o) throws IOException
	{
		final List<CombatRules.Stats> parts = new ArrayList<CombatRules.Stats>();
		if(enabled())
		{
			final JSONObject all = records(o);
			for(final String id : all.keySet())
			{
				final Entry<Track> t;
				try
				{
					t = definitions().latest(id);
				}
				catch(final IOException e)
				{
					continue;
				}
				final int level = ((Number)((JSONObject)all.get(id)).get("level")).intValue();
				for(final Milestone m : t.value.milestones)
				{
					if((m.level <= level) && !m.stats.isEmpty())
						parts.add(CombatRules.bonuses(m.stats));
				}
			}
		}
		return CombatRules.sumBonuses(parts);
	}

	/** TRACKS RESET: a track with reset "command" goes back to nothing, its milestones with it */
	public static synchronized void resetTrack(final Owner o, final String trackId) throws IOException
	{
		require();
		final Entry<Track> t = definitions().latest(trackId);
		if(!t.value.reset.equals("command"))
			throw new IOException(t.title + " cannot be reset");
		final JSONObject all = records(o);
		final JSONObject r = record(all, t);
		r.put("points", Long.valueOf(0));
		r.put("level", Long.valueOf(0));
		r.put("resets", Long.valueOf(((r.get("resets") instanceof Number) ? ((Number)r.get("resets")).longValue() : 0) + 1));
		store(o, all);
		ModuleStats.count(FAMILY, trackId, "resets");
	}

	/** an organization member gives coin to an organization track */
	public static synchronized String contribute(final MOB mob, final String trackId, final long coin) throws IOException
	{
		require();
		ModuleContent.gameThread();
		final Entry<Track> t = definitions().latest(trackId);
		if(!t.value.owner.equals("organization"))
			throw new IOException(t.title + " is not an organization's track");
		Earn rule = null;
		for(final Earn e : t.value.earn)
		{
			if(e.on.equals("contribute"))
				rule = e;
		}
		if(rule == null)
			throw new IOException(t.title + " takes no contributions");
		Clan clan = null;
		for(final Pair<Clan, Integer> c : mob.clans())
			clan = c.first;
		if(clan == null)
			throw new IOException("you are in no organization");
		final long points = coin / rule.moneyPerPoint;
		if(points < 1)
			throw new IOException("a point costs " + rule.moneyPerPoint);
		final long cost = points * rule.moneyPerPoint;
		final String cur = CMLib.beanCounter().getCurrency(mob);
		if(CMLib.beanCounter().getTotalAbsoluteValue(mob, cur) < cost)
			throw new IOException("you do not have " + CMLib.beanCounter().nameCurrencyShort(mob, (double)cost));
		final Owner o = of(clan);
		final long[] before = progress(o, trackId);
		if(before[0] >= t.value.cap())
			throw new IOException(t.title + " is complete");
		final long room = t.value.cap() - before[0];
		final long take = Math.min(points, room);
		CMLib.beanCounter().subtractMoney(mob, cur, (double)(take * rule.moneyPerPoint));
		earn(o, trackId, take, null);
		return "You give " + CMLib.beanCounter().nameCurrencyShort(mob, (double)(take * rule.moneyPerPoint)) + " to " + t.title + " for " + clan.getName()
			+ ": " + take + " " + t.value.points + ".";
	}

	// ---- the events

	/** the item-owner tracks an item carries, from its Prop_Tracks */
	public static List<String> itemTracks(final Item I)
	{
		final List<String> out = new ArrayList<String>();
		final Ability A = (I == null) ? null : I.fetchEffect("Prop_Tracks");
		if(A != null)
		{
			for(final String s : CMParms.parseCommas(CMParms.getParmStr(A.text(), "TRACKS", A.text()).replace(' ', ','), true))
			{
				if(s.trim().length() > 0)
					out.add(s.trim().toLowerCase(Locale.ROOT));
			}
		}
		return out;
	}

	private static void apply(final String on, final MOB actor, final String skill, final String key)
	{
		if((actor == null) || !enabled())
			return;
		try
		{
			for(final Entry<Track> t : definitions().latestAll())
			{
				for(final Earn e : t.value.earn)
				{
					if(!e.on.equals(on) || ((e.skill.length() > 0) && !e.skill.equals(skill)) || (e.amount <= 0))
						continue;
					if(t.value.owner.equals("character"))
						earn(of(actor), t.id, e.amount, key);
					else
					if(t.value.owner.equals("organization"))
					{
						for(final Pair<Clan, Integer> c : actor.clans())
							earn(of(c.first), t.id, e.amount, key);
					}
					else
					{
						for(int i = 0; i < actor.numItems(); i++)
						{
							final Item I = actor.getItem(i);
							if((I != null) && !I.amWearingAt(Item.IN_INVENTORY) && itemTracks(I).contains(t.id))
								earn(of(I), t.id, e.amount, key);
						}
					}
				}
			}
		}
		catch(final IOException ex)
		{
			Log.errOut("ProgressionTracks", on + " by " + actor.Name() + ": " + ex.getMessage());
		}
	}

	/** a kill: the killer's tracks, their organizations' and their equipment's */
	public static void died(final MOB victim, final MOB killer)
	{
		if((victim == null) || (killer == null) || (killer == victim))
			return;
		apply("kill", killer, "", "kill:" + InstanceIds.id(victim));
	}


	/** a composed cast resolved */
	public static void cast(final MOB caster, final String skill)
	{
		apply("cast", caster, skill, null);
	}

	/** a craft settled, by its receipt */
	public static void crafted(final MOB crafter, final String receiptKey)
	{
		apply("craft", crafter, "", "craft:" + receiptKey);
	}

	/** the text tree of an owner's tracks (Q07): each milestone marked reached [x] or not [ ] */
	public static String tree(final Owner o) throws IOException
	{
		require();
		final JSONObject all = records(o);
		final StringBuilder s = new StringBuilder();
		for(final String id : new TreeSet<String>(all.keySet()))
		{
			final JSONObject r = (JSONObject)all.get(id);
			Entry<Track> t;
			try
			{
				t = definitions().latest(id);
			}
			catch(final IOException e)
			{
				s.append("\n").append(id).append(": unavailable (").append(e.getMessage()).append(")");
				continue;
			}
			final long points = ((Number)r.get("points")).longValue();
			final int level = ((Number)r.get("level")).intValue();
			s.append("\n").append(t.title).append(": level ").append(level).append(" of ").append(t.value.levels.size()).append(", ").append(points).append(" ").append(t.value.points);
			if(level < t.value.levels.size())
				s.append(" (").append(t.value.levels.get(level).longValue() - points).append(" to level ").append(level + 1).append(")");
			else
				s.append(" (complete)");
			for(final Milestone m : t.value.milestones)
				s.append("\n  ").append((m.level <= level) ? "[x] " : "[ ] ").append(m.level).append(". ").append(m.describe());
		}
		return s.toString();
	}

	/** TRACKS: a character's tracks, their organizations' and their equipment's */
	public static String describe(final MOB mob) throws IOException
	{
		require();
		final StringBuilder s = new StringBuilder();
		final String mine = tree(of(mob));
		if(mine.length() > 0)
			s.append("Your tracks:").append(mine);
		for(final Pair<Clan, Integer> c : mob.clans())
		{
			final String theirs = tree(of(c.first));
			if(theirs.length() > 0)
				s.append(s.length() > 0 ? "\n" : "").append(c.first.getName()).append("'s tracks:").append(theirs);
		}
		for(int i = 0; i < mob.numItems(); i++)
		{
			final Item I = mob.getItem(i);
			if((I == null) || itemTracks(I).isEmpty())
				continue;
			final String its = tree(of(I));
			if(its.length() > 0)
				s.append(s.length() > 0 ? "\n" : "").append(I.name()).append("'s tracks:").append(its);
		}
		return (s.length() == 0) ? "You have no progression tracks yet." : s.toString();
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
			return "TRACKS";
		}

		@Override
		public String folder()
		{
			return FOLDER;
		}

		@Override
		public List<String> scopes()
		{
			return Arrays.asList("character", "organization", "item");
		}

		@Override
		public List<String> stringState()
		{
			return Arrays.asList("tracks");
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
			ProgressionTracks.reset();
		}

		@Override
		public List<String> check()
		{
			final Library<Track> lib = load();
			final List<String> out = new ArrayList<String>(lib.errors);
			final Set<String> skills = new TreeSet<String>();
			for(final CompositionDefinitions.Definition d : CompositionDefinitions.load(ModuleContent.world()).definitions.values())
				skills.add(d.id);
			final Map<String, String> replacedBy = new TreeMap<String, String>();
			for(final Entry<Track> e : lib.latestAll())
			{
				for(int i = 0; i < e.value.earn.size(); i++)
				{
					final Earn x = e.value.earn.get(i);
					if(!x.skill.isEmpty() && !skills.contains(x.skill))
						out.add(e.path + ": /earn/" + i + "/skill: no composition " + x.skill + " is installed in compositions/");
				}
				for(final String old : e.value.replaces)
				{
					if(lib.has(old))
						out.add(e.path + ": /replaces: " + old + " is still installed; a replaced track must be retired");
					if(replacedBy.containsKey(old))
						out.add(e.path + ": /replaces: " + old + " is replaced by " + replacedBy.get(old) + " too; the first in ID order takes it");
					else
						replacedBy.put(old, e.id);
				}
			}
			return out;
		}

		@Override
		public String list() throws IOException
		{
			final Library<Track> lib = load();
			if(lib.entries.isEmpty())
				return "No tracks are installed in " + FOLDER + "/." + (lib.errors.isEmpty() ? "" : "\n" + String.join("\n", lib.errors));
			final StringBuilder s = new StringBuilder("Tracks:");
			for(final Entry<Track> e : lib.entries.values())
				s.append("\n  ").append(e.key()).append(" (").append(e.title).append("): ").append(e.value.owner).append(", ").append(e.value.levels.size())
				 .append(" levels to ").append(e.value.cap()).append(" ").append(e.value.points).append(", ").append(e.value.milestones.size()).append(" milestones")
				 .append(e.value.replaces.isEmpty() ? "" : ", replaces " + e.value.replaces);
			if(!lib.errors.isEmpty())
				s.append("\n").append(String.join("\n", lib.errors));
			return s.toString();
		}

		@Override
		public String reload() throws IOException
		{
			synchronized(ProgressionTracks.class)
			{
				library = null;
			}
			return definitions().report();
		}

		@Override
		public String world(final MOB mob, final List<String> words) throws IOException
		{
			// WORLD TRACKS GRANT [track] [n] ([key]): points to yourself (or your organization, or your wielded item)
			if((words.size() >= 3) && words.get(0).equalsIgnoreCase("GRANT") && CMath.isLong(words.get(2)))
			{
				final Entry<Track> t = definitions().latest(words.get(1).toLowerCase(Locale.ROOT));
				Owner o = of(mob);
				if(t.value.owner.equals("organization"))
				{
					Clan clan = null;
					for(final Pair<Clan, Integer> c : mob.clans())
						clan = c.first;
					if(clan == null)
						throw new IOException("you are in no organization");
					o = of(clan);
				}
				else
				if(t.value.owner.equals("item"))
				{
					Item held = null;
					for(int i = 0; i < mob.numItems(); i++)
					{
						final Item I = mob.getItem(i);
						if((I != null) && !I.amWearingAt(Item.IN_INVENTORY) && itemTracks(I).contains(t.id))
							held = I;
					}
					if(held == null)
						throw new IOException("you wear or hold no item with " + t.id);
					o = of(held);
				}
				final long n = earn(o, t.id, CMath.s_long(words.get(2)), (words.size() > 3) ? words.get(3) : null);
				return "Granted " + n + " " + t.value.points + " to " + o.name + ".";
			}
			return null;
		}
	};

	static
	{
		ModuleContent.register(FAMILY_ENTRY);
	}
}
