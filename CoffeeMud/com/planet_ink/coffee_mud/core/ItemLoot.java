package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.ItemPossessor;
import com.planet_ink.coffee_mud.core.ItemModifierRules.Definition;
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
 * I02: loot policies, the optional module items.loot (beside items.modifiers).
 * A policy (world/loot/[id].v[N].json) makes a tracked item of one modifier
 * base: it draws a rarity by weight, and the rarity says how many modifiers
 * the item gets.  Each modifier is drawn, by its weight, among the base's pool
 * that the item can hold, that the item's level allows (the policy's tier
 * limits), that carry the policy's tags, and that fit its power budget (the
 * sum of the tiers).  After a run of drops without a rarity, the next is that
 * rarity (the guarantee).  Every draw comes from the campaign's loot stream
 * (Q09): a loot roll never moves other dice, a preview draws nothing, and a
 * sample runs on a cursor that never moves the stream, drawing exactly what
 * the real drops would.
 * A generator item takes MODBASE="POLICY=[id]".  The guarantee counter is the
 * campaign's (world state): a drop made with no one about (a mob's spawn loot)
 * counts, and a party shares one luck.
 * Q07: LOOT shows a container's or a corpse's tracked loot by rarity, in plain
 * text, through each character's own filter (character state).
 */
public final class ItemLoot
{
	private ItemLoot()
	{
	}

	public static final String	MODULE	= "items.loot";
	public static final String	FOLDER	= "loot";
	public static final String	FAMILY	= "loot";

	/** a rarity: its weight, and how many modifiers an item of it gets */
	public static final class Rarity
	{
		public final String	id, title;
		public final double	weight;
		public final int	modifiers;

		Rarity(final Node n) throws IOException
		{
			n.allow("id", "title", "weight", "modifiers");
			id = n.id("id");
			title = n.string("title", id, 40);
			weight = n.number("weight", 1, 0.000001, 1000000);
			modifiers = (int)n.integer("modifiers", 0, 0, ItemModifierRules.MAX_MODS);
		}
	}

	public static final class Policy
	{
		public final String			base;
		public final List<Rarity>	rarities	= new ArrayList<Rarity>();
		public final int[]			tierLevels, tierMax;
		public final List<String>	tags;
		public final int			budget;
		/** the rarity a run of drops without it ends with, and the run's length; "" when none */
		public final String			guarantee;
		public final int			after;

		Policy(final Node n) throws IOException
		{
			n.allow("schema", "id", "version", "title", "base", "rarities", "tiers", "tags", "budget", "guarantee");
			base = n.id("base");
			for(final Node r : n.objects("rarities", 8))
			{
				final Rarity x = new Rarity(r);
				if(rarity(x.id) != null)
					throw r.error("id", "rarity " + x.id + " is declared twice");
				rarities.add(x);
			}
			if(rarities.isEmpty())
				throw n.error("rarities", "a policy declares at least one rarity");
			final List<Node> tiers = n.objects("tiers", 16);
			tierLevels = new int[tiers.size()];
			tierMax = new int[tiers.size()];
			for(int i = 0; i < tiers.size(); i++)
			{
				final Node t = tiers.get(i);
				t.allow("level", "max_tier");
				tierLevels[i] = (int)t.integer("level", 1, 100000);
				tierMax[i] = (int)t.integer("max_tier", 1, 100);
				if((i > 0) && (tierLevels[i] <= tierLevels[i - 1]))
					throw t.error("level", "each tier limit begins at a higher level than the one before");
			}
			tags = n.strings("tags", 16, true);
			budget = (int)n.integer("budget", 0, 0, 600);
			if(n.has("guarantee"))
			{
				final Node g = n.object("guarantee");
				g.allow("rarity", "after");
				guarantee = g.id("rarity");
				if(rarity(guarantee) == null)
					throw g.error("rarity", "no rarity " + guarantee + " in this policy");
				after = (int)g.integer("after", 1, 1000);
			}
			else
			{
				guarantee = "";
				after = 0;
			}
		}

		public Rarity rarity(final String id)
		{
			for(final Rarity r : rarities)
			{
				if(r.id.equals(id))
					return r;
			}
			return null;
		}

		/** the highest tier an item of this level may draw; with no tier limits, any */
		public int maxTier(final int level)
		{
			if(tierLevels.length == 0)
				return 100;
			int t = 0;
			for(int i = 0; i < tierLevels.length; i++)
			{
				if(level >= tierLevels[i])
					t = tierMax[i];
			}
			return t;
		}

		double totalWeight()
		{
			double t = 0;
			for(final Rarity r : rarities)
				t += r.weight;
			return t;
		}
	}

	private static Library<Policy> library = null;

	private static final ModuleContent.Reader<Policy> READER = new ModuleContent.Reader<Policy>()
	{
		@Override
		public Policy read(final Node node) throws IOException
		{
			return new Policy(node);
		}
	};

	public static Library<Policy> load()
	{
		return ModuleContent.load(ModuleContent.world(), FOLDER, "loot policy", READER);
	}

	public static synchronized Library<Policy> definitions()
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

	/** where a policy's draws come from: the live loot stream, or a cursor that never moves it */
	interface Source
	{
		long draw();
	}

	private static final Source LIVE = () -> RandomStreams.draw(RandomStreams.LOOT);

	private static double unit(final Source s)
	{
		return (s.draw() >>> 11) * 0x1.0p-53;
	}

	/** one drop: its rarity, whether the guarantee gave it, and its modifiers */
	public static final class Drop
	{
		public Rarity					rarity;
		public boolean					guaranteed;
		public final List<JSONObject>	mods	= new ArrayList<JSONObject>();
	}

	/** why an affix cannot be a policy's next pick for this record, or "" when it can */
	static String barred(final Policy p, final Definition a, final JSONObject record, final int level, final int used) throws IOException
	{
		final String why = ItemModifierRules.ineligible(record, a, level);
		if(why.length() > 0)
			return why;
		if(a.number("tier") > p.maxTier(level))
			return "tier " + a.number("tier") + " is above the limit of " + p.maxTier(level) + " at level " + level;
		if(!a.list("tags").containsAll(p.tags))
			return "lacks the policy's tags " + p.tags;
		if((p.budget > 0) && ((used + a.number("tier")) > p.budget))
			return "tier " + a.number("tier") + " would pass the power budget (" + used + " of " + p.budget + " used)";
		return "";
	}

	/**
	 * Draws one drop for a fresh record: the rarity (unless the guarantee forces
	 * one), then each modifier and its rolls.  The same source gives the same drop.
	 */
	static Drop roll(final Policy p, final Definition base, final ItemModifierRules.Library lib, final JSONObject record, final int level, final int limit,
					 final Source src, final String forced) throws IOException
	{
		final Drop d = new Drop();
		if(forced != null)
		{
			d.rarity = p.rarity(forced);
			d.guaranteed = true;
		}
		else
		{
			double at = unit(src) * p.totalWeight();
			d.rarity = p.rarities.get(p.rarities.size() - 1);
			for(final Rarity r : p.rarities)
			{
				at -= r.weight;
				if(at < 0)
				{
					d.rarity = r;
					break;
				}
			}
		}
		final JSONObject rec = ModuleDefinitions.copy(record);
		final ItemModifierRules.Roller dice = (min, max) -> min + (int)Long.remainderUnsigned(src.draw(), (long)max - min + 1);
		int used = 0;
		for(int n = 0; n < Math.min(d.rarity.modifiers, limit); n++)
		{
			final List<Definition> fit = new ArrayList<Definition>();
			double total = 0;
			for(final String id : base.list("pool"))
			{
				final Definition a = lib.find(id, 0, "affix");
				if(barred(p, a, rec, level, used).isEmpty())
				{
					fit.add(a);
					total += ((Number)a.raw.get("weight")).doubleValue();
				}
			}
			if(fit.isEmpty())
				break;
			double at = unit(src) * total;
			Definition chosen = fit.get(fit.size() - 1);
			for(final Definition a : fit)
			{
				at -= ((Number)a.raw.get("weight")).doubleValue();
				if(at < 0)
				{
					chosen = a;
					break;
				}
			}
			d.mods.add(ItemModifierRules.rolled(chosen, UUID.randomUUID().toString(), dice));
			rec.put("modifiers", d.mods.toArray());
			used += chosen.number("tier");
		}
		return d;
	}

	private static JSONObject luck() throws IOException
	{
		final String raw = (String)OptionalModules.get(MODULE, OptionalModules.world()).get("guarantees");
		return ((raw == null) || raw.isEmpty()) ? new JSONObject() : ModuleDefinitions.parse(raw);
	}

	private static void storeLuck(final JSONObject all) throws IOException
	{
		final String text = ModuleDefinitions.json(all);
		if(text.length() > 4096)
			throw new IOException("too many loot policies keep a guarantee counter");
		final JSONObject rec = OptionalModules.get(MODULE, OptionalModules.world());
		rec.put("guarantees", text);
		OptionalModules.put(MODULE, OptionalModules.world(), rec);
	}

	/** drops since the policy's guaranteed rarity last fell */
	public static long since(final String policy) throws IOException
	{
		final Object o = luck().get(policy);
		return (o instanceof Number) ? ((Number)o).longValue() : 0;
	}

	private static String forced(final Policy p, final long since)
	{
		return ((p.guarantee.length() > 0) && ((since + 1) >= p.after)) ? p.guarantee : null;
	}

	/** a fresh record of a base, for previews and samples: no item is touched */
	private static JSONObject blank(final Definition base) throws IOException
	{
		return ItemModifierRules.record(base, "@instance:00000000-0000-0000-0000-000000000000", "an item", "An item lies here.", "preview");
	}

	private static Definition baseOf(final Entry<Policy> e) throws IOException
	{
		try
		{
			return ItemModifiers.definitions().find(e.value.base, 0, "base");
		}
		catch(final ItemModifiers.Unavailable u)
		{
			throw u;
		}
		catch(final IOException x)
		{
			throw new IOException(e.path + ": /base: no modifier base " + e.value.base + " in itemmods/");
		}
	}

	/**
	 * MODBASE="POLICY=[id]": makes a fresh unowned item a tracked item of the
	 * policy's base, with a drawn rarity and modifiers.  The guarantee counter
	 * moves only once the drop is made.  While the module is inactive, the drop
	 * is refused as unavailable, so a loot table keeps its roll.
	 */
	public static void initialize(final Item item, final String policyId, final String source) throws IOException
	{
		if(!enabled())
			throw new ItemModifiers.Unavailable(MODULE + " is inactive in this campaign");
		final Library<Policy> lib = definitions();
		final Entry<Policy> e = lib.latest(policyId);
		final Policy p = e.value;
		final Definition base = baseOf(e);
		if(!base.string("item_kind").equals(Affixes.kindOf(item)))
			throw new IOException("POLICY " + policyId + ": base " + base.key() + " is for " + base.string("item_kind") + ", the template is " + Affixes.kindOf(item));
		final JSONObject data = ItemModifierRules.record(base, InstanceIds.reference(item), item.Name(), item.displayText(), "x");
		final long since = since(e.id);
		final Drop d = roll(p, base, ItemModifiers.definitions(), data, item.basePhyStats().level(), ItemModifiers.limit(), LIVE, forced(p, since));
		data.put("modifiers", d.mods.toArray());
		data.put("source", ("loot:" + e.key() + ":" + d.rarity.id + " from " + source).substring(0, Math.min(500, ("loot:" + e.key() + ":" + d.rarity.id + " from " + source).length())));
		final Ability prop = CMClass.getAbility(ItemModifiers.PROPERTY);
		prop.setMiscText(ModuleDefinitions.json(ItemModifierRules.checked(data)));
		item.addNonUninvokableEffect(prop);
		try
		{
			ItemModifiers.install(item, ItemModifiers.names(item, data));
		}
		catch(final IOException | RuntimeException x)
		{
			item.delEffect(prop);
			throw x;
		}
		if(p.guarantee.length() > 0)
		{
			final JSONObject all = luck();
			all.put(e.id, Long.valueOf(d.rarity.id.equals(p.guarantee) ? 0 : (since + 1)));
			storeLuck(all);
		}
		ModuleStats.count(FAMILY, e.id, "drops");
		ModuleStats.count(FAMILY, e.id, "rarity " + d.rarity.id);
		if(d.guaranteed)
			ModuleStats.count(FAMILY, e.id, "guaranteed drops");
	}

	/** what a policy would do at a level, in words; nothing is drawn */
	public static String preview(final String policyId, final int level) throws IOException
	{
		final Entry<Policy> e = definitions().latest(policyId);
		final Policy p = e.value;
		final Definition base = baseOf(e);
		final ItemModifierRules.Library lib = ItemModifiers.definitions();
		final StringBuilder s = new StringBuilder("Loot policy ").append(e.title).append(" (").append(e.key()).append("): base ").append(base.key()).append(", item level ").append(level);
		final double total = p.totalWeight();
		s.append("\nRarities:");
		for(final Rarity r : p.rarities)
			s.append("\n  ").append(r.title).append(" (").append(r.id).append("): ").append(String.format(Locale.ROOT, "%.1f%%", 100.0 * r.weight / total)).append(", up to ").append(r.modifiers).append(" modifier(s)");
		if(p.guarantee.length() > 0)
			s.append("\nGuarantee: after ").append(p.after - 1).append(" drop(s) without ").append(p.rarity(p.guarantee).title).append(", the next is ").append(p.rarity(p.guarantee).title)
			 .append(". Drops since the last: ").append(since(e.id)).append(forced(p, since(e.id)) != null ? " (the next drop is guaranteed)" : "").append('.');
		s.append("\nTier limit at level ").append(level).append(": ").append(p.tierLevels.length == 0 ? "none" : (p.maxTier(level) == 0 ? "no tier is allowed yet" : "tier " + p.maxTier(level) + " and below"));
		if(!p.tags.isEmpty())
			s.append("\nTags required: ").append(String.join(", ", p.tags));
		if(p.budget > 0)
			s.append("\nPower budget: the tiers of an item's modifiers add up to at most ").append(p.budget);
		s.append("\nModifiers the first pick can draw:");
		final JSONObject rec = blank(base);
		double fit = 0;
		for(final String id : base.list("pool"))
		{
			final Definition a = lib.find(id, 0, "affix");
			if(barred(p, a, rec, level, 0).isEmpty())
				fit += ((Number)a.raw.get("weight")).doubleValue();
		}
		for(final String id : base.list("pool"))
		{
			final Definition a = lib.find(id, 0, "affix");
			final String why = barred(p, a, rec, level, 0);
			s.append("\n  ").append(a.id).append(" (").append(a.name).append(", ").append(a.string("position")).append(" tier ").append(a.number("tier")).append("): ");
			if(why.isEmpty())
				s.append("eligible, ").append(String.format(Locale.ROOT, "%.1f%%", 100.0 * ((Number)a.raw.get("weight")).doubleValue() / fit)).append(" of first picks");
			else
				s.append("not eligible: ").append(why);
		}
		s.append("\nLater picks leave out the groups and slots already taken. Nothing was drawn.");
		return s.toString();
	}

	/** the first-pick eligible affixes of a policy at a level */
	public static Set<String> eligible(final String policyId, final int level) throws IOException
	{
		final Entry<Policy> e = definitions().latest(policyId);
		final Definition base = baseOf(e);
		final JSONObject rec = blank(base);
		final Set<String> out = new TreeSet<String>();
		for(final String id : base.list("pool"))
		{
			final Definition a = ItemModifiers.definitions().find(id, 0, "affix");
			if(barred(e.value, a, rec, level, 0).isEmpty())
				out.add(a.id);
		}
		return out;
	}

	/**
	 * Samples n drops at a level on a cursor at the loot stream's next draw: what
	 * the next n real drops would be (with the guarantee counter as it is now),
	 * while the stream and the counter do not move.  It checks the sample against
	 * the preview: every sampled modifier must be one the preview lists as eligible.
	 */
	public static String sample(final String policyId, final int n, final int level) throws IOException
	{
		final Entry<Policy> e = definitions().latest(policyId);
		final Policy p = e.value;
		final Definition base = baseOf(e);
		final RandomStreams.Cursor c = RandomStreams.peek(RandomStreams.LOOT);
		final long start = c.counter();
		final Source src = c::draw;
		final Map<String, Integer> rarities = new LinkedHashMap<String, Integer>();
		for(final Rarity r : p.rarities)
			rarities.put(r.id, Integer.valueOf(0));
		final Map<String, Integer> affixes = new TreeMap<String, Integer>();
		long since = since(e.id);
		int guaranteed = 0, mods = 0;
		for(int i = 0; i < n; i++)
		{
			final Drop d = roll(p, base, ItemModifiers.definitions(), blank(base), level, ItemModifiers.limit(), src, forced(p, since));
			rarities.put(d.rarity.id, Integer.valueOf(rarities.get(d.rarity.id).intValue() + 1));
			if(d.guaranteed)
				guaranteed++;
			if(p.guarantee.length() > 0)
				since = d.rarity.id.equals(p.guarantee) ? 0 : (since + 1);
			for(final JSONObject m : d.mods)
			{
				final String id = (String)((JSONObject)m.get("pin")).get("id");
				affixes.put(id, Integer.valueOf(affixes.containsKey(id) ? affixes.get(id).intValue() + 1 : 1));
				mods++;
			}
		}
		final double total = p.totalWeight();
		final StringBuilder s = new StringBuilder("Sampled ").append(n).append(" drop(s) of ").append(e.key()).append(" at level ").append(level)
			.append(" on a cursor at loot draw ").append(start).append(" (the stream and the guarantee counter do not move):");
		for(final Rarity r : p.rarities)
		{
			final int k = rarities.get(r.id).intValue();
			s.append("\n  ").append(r.title).append(": ").append(k).append(String.format(Locale.ROOT, " (%.1f%%; chance %.1f%%)", (n == 0) ? 0.0 : 100.0 * k / n, 100.0 * r.weight / total));
		}
		if(p.guarantee.length() > 0)
			s.append("\n  given by the guarantee: ").append(guaranteed);
		s.append("\nModifiers drawn: ").append(mods);
		for(final Map.Entry<String, Integer> a : affixes.entrySet())
			s.append("\n  ").append(a.getKey()).append(": ").append(a.getValue());
		final Set<String> allowed = eligible(policyId, level);
		final List<String> outside = new ArrayList<String>();
		for(final String id : affixes.keySet())
		{
			if(!allowed.contains(id))
				outside.add(id);
		}
		final List<String> never = new ArrayList<String>();
		for(final String id : allowed)
		{
			if(!affixes.containsKey(id))
				never.add(id);
		}
		s.append(outside.isEmpty() ? "\nEvery sampled modifier is one the preview lists as eligible." : "\nDISAGREES with the preview: sampled but not eligible: " + String.join(", ", outside));
		if(!never.isEmpty())
			s.append("\nEligible but not drawn in this sample: ").append(String.join(", ", never));
		return s.toString();
	}

	/** the policy and rarity a tracked item was dropped with, or null */
	public static String[] dropOf(final Item I)
	{
		try
		{
			if(!ItemModifiers.isManaged(I))
				return null;
			final String source = (String)ItemModifierRules.parse(I.fetchEffect(ItemModifiers.PROPERTY).text()).get("source");
			final java.util.regex.Matcher m = java.util.regex.Pattern.compile("^loot:([a-z0-9][a-z0-9_.-]*)\\.v([0-9]+):([a-z0-9][a-z0-9_.-]*)").matcher(source);
			return m.find() ? new String[] { m.group(1), m.group(2), m.group(3) } : null;
		}
		catch(final IOException | RuntimeException e)
		{
			return null;
		}
	}

	private static List<String> filterOf(final MOB mob) throws IOException
	{
		final String raw = (String)OptionalModules.get(MODULE, OptionalModules.character(mob)).get("filter");
		final List<String> out = new ArrayList<String>();
		if(raw != null)
		{
			for(final String w : raw.split(","))
			{
				if(w.trim().length() > 0)
					out.add(w.trim());
			}
		}
		return out;
	}

	/** LOOT FILTER: the rarities a character's LOOT shows; none means all */
	public static String setFilter(final MOB mob, final List<String> words) throws IOException
	{
		if(!enabled())
			throw new IOException("loot policies (" + MODULE + ") are not active in this campaign");
		final List<String> ids = new ArrayList<String>();
		final boolean all = words.isEmpty() || ((words.size() == 1) && words.get(0).equalsIgnoreCase("ALL"));
		if(!all)
		{
			final Set<String> known = new TreeSet<String>();
			for(final Entry<Policy> e : definitions().latestAll())
			{
				for(final Rarity r : e.value.rarities)
					known.add(r.id);
			}
			for(final String w : words)
			{
				final String id = w.toLowerCase(Locale.ROOT);
				if(!known.contains(id))
					throw new IOException("no installed loot policy has a rarity '" + id + "'; rarities: " + String.join(", ", known));
				if(!ids.contains(id))
					ids.add(id);
			}
		}
		final String text = String.join(",", ids);
		if(text.length() > 256)
			throw new IOException("that filter is too long");
		final JSONObject rec = OptionalModules.get(MODULE, OptionalModules.character(mob));
		rec.put("filter", text);
		OptionalModules.put(MODULE, OptionalModules.character(mob), rec);
		return all ? "LOOT now shows every item." : "LOOT now shows " + String.join(", ", ids) + " items, and counts the rest.";
	}

	private static String values(final JSONObject m)
	{
		final List<String> out = new ArrayList<String>();
		final JSONObject v = (JSONObject)m.get("values");
		for(final String k : new TreeSet<String>(v.keySet()))
			out.add(k.replace('.', ' ').replace('_', ' ') + " " + v.get(k));
		return String.join(", ", out);
	}

	/**
	 * Q07: the loot in a container or a corpse, in plain text: each item the
	 * viewer's filter shows, by rarity, with its modifiers; the rest counted.
	 * It takes no game time and changes nothing.
	 */
	public static String view(final MOB viewer, final Item host) throws IOException
	{
		if(!enabled())
			throw new IOException("loot policies (" + MODULE + ") are not active in this campaign");
		if(!(host instanceof Container))
			throw new IOException(host.Name() + " holds nothing");
		final Container C = (Container)host;
		if(C.hasADoor() && !C.isOpen() && !(host instanceof DeadBody))
			return host.Name() + " is closed.";
		final List<Item> inside = new ArrayList<Item>();
		final ItemPossessor owner = host.owner();
		if(owner instanceof Room)
		{
			for(final Enumeration<Item> e = ((Room)owner).items(); e.hasMoreElements();)
			{
				final Item I = e.nextElement();
				if(I.container() == host)
					inside.add(I);
			}
		}
		else
		if(owner instanceof MOB)
		{
			for(final Enumeration<Item> e = ((MOB)owner).items(); e.hasMoreElements();)
			{
				final Item I = e.nextElement();
				if(I.container() == host)
					inside.add(I);
			}
		}
		final List<String> filter = filterOf(viewer);
		final Library<Policy> lib = definitions();
		final StringBuilder s = new StringBuilder("In ").append(host.Name()).append(':');
		int hidden = 0, plain = 0, shown = 0;
		for(final Item I : inside)
		{
			final String[] drop = dropOf(I);
			if(!ItemModifiers.isManaged(I))
			{
				plain++;
				continue;
			}
			final String rarity = (drop == null) ? "" : drop[2];
			if(!filter.isEmpty() && !filter.contains(rarity))
			{
				hidden++;
				continue;
			}
			String title = (drop == null) ? "Tracked" : rarity;
			if(drop != null)
			{
				for(final Entry<Policy> e : lib.entries.values())
				{
					if(e.id.equals(drop[0]) && (e.value.rarity(rarity) != null))
						title = e.value.rarity(rarity).title;
				}
			}
			shown++;
			s.append("\n  ").append(title).append(": ").append(I.Name());
			final List<String> mods = new ArrayList<String>();
			for(final JSONObject m : ItemModifierRules.modifiers(ItemModifierRules.parse(I.fetchEffect(ItemModifiers.PROPERTY).text())))
				mods.add(m.get("name") + " (" + values(m) + ")");
			s.append(mods.isEmpty() ? ", no modifiers" : "; " + String.join("; ", mods));
		}
		if(shown == 0)
			s.append("\n  nothing your filter shows");
		if(hidden > 0)
			s.append("\n").append(hidden).append(" tracked item(s) your filter leaves out (LOOT FILTER ALL shows them).");
		if(plain > 0)
			s.append("\n").append(plain).append(" other item(s).");
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
			return "LOOT";
		}

		@Override
		public String folder()
		{
			return FOLDER;
		}

		@Override
		public List<String> scopes()
		{
			return Arrays.asList("world", "character");
		}

		@Override
		public List<String> stringState()
		{
			return Arrays.asList("guarantees", "filter");
		}

		@Override
		public List<String> requires()
		{
			return Arrays.asList(ItemModifierRules.MODULE);
		}

		@Override
		public Map<String, long[]> integerSettings()
		{
			return new TreeMap<String, long[]>();
		}

		@Override
		public void reset()
		{
			ItemLoot.reset();
		}

		@Override
		public List<String> check()
		{
			final Library<Policy> lib = load();
			final List<String> out = new ArrayList<String>(lib.errors);
			final ItemModifierRules.Library mods = ItemModifiers.check();
			for(final Entry<Policy> e : lib.entries.values())
			{
				final Definition base;
				try
				{
					base = mods.find(e.value.base, 0, "base");
				}
				catch(final IOException x)
				{
					out.add(e.path + ": /base: no modifier base " + e.value.base + " in itemmods/");
					continue;
				}
				try
				{
					boolean any = false;
					for(final String id : base.list("pool"))
					{
						final Definition a = mods.find(id, 0, "affix");
						any |= a.list("tags").containsAll(e.value.tags);
					}
					if(!any && !e.value.tags.isEmpty())
						out.add(e.path + ": /tags: no affix in " + base.key() + "'s pool carries " + e.value.tags);
				}
				catch(final IOException x)
				{
					out.add(e.path + ": /base: " + x.getMessage());
				}
			}
			return out;
		}

		@Override
		public String list() throws IOException
		{
			final Library<Policy> lib = load();
			if(lib.entries.isEmpty())
				return "No loot policies are installed in " + FOLDER + "/." + (lib.errors.isEmpty() ? "" : "\n" + String.join("\n", lib.errors));
			final StringBuilder s = new StringBuilder("Loot policies:");
			for(final Entry<Policy> e : lib.entries.values())
			{
				s.append("\n  ").append(e.key()).append(" (").append(e.title).append("): base ").append(e.value.base).append("; rarities");
				for(final Rarity r : e.value.rarities)
					s.append(' ').append(r.id).append('=').append(r.modifiers);
				if(e.value.guarantee.length() > 0)
					s.append("; ").append(e.value.guarantee).append(" at least every ").append(e.value.after).append(" drops");
			}
			if(!lib.errors.isEmpty())
				s.append("\n").append(String.join("\n", lib.errors));
			return s.toString();
		}

		@Override
		public String reload() throws IOException
		{
			reset();
			return definitions().report();
		}

		@Override
		public String world(final MOB mob, final List<String> words) throws IOException
		{
			// WORLD LOOT PREVIEW [policy] ([level]) / SAMPLE [policy] [n] ([level])
			if((words.size() >= 2) && words.get(0).equalsIgnoreCase("PREVIEW"))
				return preview(words.get(1).toLowerCase(Locale.ROOT), (words.size() > 2) ? CMath.s_int(words.get(2)) : 1);
			if((words.size() >= 3) && words.get(0).equalsIgnoreCase("SAMPLE"))
			{
				final int n = CMath.s_int(words.get(2));
				if((n < 1) || (n > 10000))
					throw new IOException("sample 1 to 10000 drops");
				return sample(words.get(1).toLowerCase(Locale.ROOT), n, (words.size() > 3) ? CMath.s_int(words.get(3)) : 1);
			}
			return null;
		}
	};

	static
	{
		ModuleContent.register(FAMILY_ENTRY);
	}
}
