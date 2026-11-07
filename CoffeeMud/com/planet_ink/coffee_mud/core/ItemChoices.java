package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * I07 and I17: saved choices, the optional module items.choices (beside
 * items.modifiers).  A choice is a few options drawn once, from the
 * campaign's offers stream, and kept until one is chosen: opening it again,
 * or restoring a save, shows the same options, and choosing settles it once.
 * <ul>
 * <li>A reveal (world/offerings/[id].v[N].json, kind reveal), at its station,
 * draws for one veiled modifier of an item (a loot rarity's "veiled") up to
 * three legal affixes of its position, each rolled; the options are kept on
 * the item.  The first time a character reveals an affix, it goes in their
 * codex and the reveal's discovery reward is given; never again for it.</li>
 * <li>An offering (kind offering) takes an item it accepts, at once and for
 * good, and draws up to three replacements of its result; they are kept on
 * the character until one is chosen, or the offering is abandoned, by its
 * refund rule (none, or the cost back).</li>
 * </ul>
 */
public final class ItemChoices
{
	private ItemChoices()
	{
	}

	public static final String	MODULE	= "items.choices";
	public static final String	FOLDER	= "offerings";
	public static final String	FAMILY	= "choices";
	private static final String	ZERO	= "@instance:00000000-0000-0000-0000-000000000000";

	/** a reveal or an offering */
	public static final class Def
	{
		public final String			kind, station, material, discovery, refund, mask, catalog, base, policy;
		public final int			units, options, modifiers;
		public final List<String>	affixes, modifierTags, tags;

		Def(final Node n) throws IOException
		{
			n.allow("schema", "id", "version", "title", "kind", "station", "cost", "options", "affixes", "modifier_tags", "discovery", "accepts", "result", "refund");
			kind = n.choice("kind", "", "reveal", "offering");
			station = n.id("station");
			final Node cost = n.object("cost");
			if(cost != null)
			{
				cost.allow("material", "units");
				material = cost.string("material");
				if(!material.matches("[A-Z][A-Z0-9 _-]{0,79}"))
					throw cost.error("material", "an uppercase resource name");
				units = (int)cost.integer("units", 1, 1000);
			}
			else
			{
				material = "";
				units = 0;
			}
			options = (int)n.integer("options", 3, 1, 5);
			if(kind.equals("reveal"))
			{
				for(final String k : new String[] { "accepts", "result", "refund" })
				{
					if(n.has(k))
						throw n.error(k, "belongs to an offering");
				}
				affixes = n.strings("affixes", 32, true);
				modifierTags = n.strings("modifier_tags", 16, true);
				discovery = n.string("discovery", "", 400);
				if(discovery.length() > 0)
					QuestRewards.parse(discovery);
				refund = "none";
				mask = "";
				tags = Collections.emptyList();
				catalog = "";
				base = "";
				policy = "";
				modifiers = 0;
			}
			else
			{
				for(final String k : new String[] { "affixes", "modifier_tags", "discovery" })
				{
					if(n.has(k))
						throw n.error(k, "belongs to a reveal");
				}
				affixes = Collections.emptyList();
				modifierTags = Collections.emptyList();
				discovery = "";
				final Node a = n.required("accepts");
				a.allow("tags", "mask");
				tags = a.strings("tags", 16, true);
				mask = a.string("mask", "", 400);
				if(tags.isEmpty() && mask.isEmpty())
					throw a.error("tags", "an offering accepts by tags (a tracked item's), a mask, or both");
				final Node r = n.required("result");
				r.allow("catalog", "base", "modifiers", "policy");
				catalog = r.string("catalog", "", 160);
				if(catalog.isEmpty())
					throw r.error("catalog", "required");
				if(r.has("policy") == r.has("base"))
					throw r.error("base", "a result names a base (with modifiers) or a loot policy");
				base = r.has("base") ? r.id("base") : "";
				policy = r.has("policy") ? r.id("policy") : "";
				if(r.has("policy") && r.has("modifiers"))
					throw r.error("modifiers", "a policy's rarity decides the modifiers");
				// the open offering is kept in 4,096 characters: three options of up to three modifiers
				if(options > 3)
					throw n.error("options", "an offering offers 1 to 3 options");
				modifiers = (int)r.integer("modifiers", 0, 0, 3);
				refund = n.choice("refund", "none", "none", "cost");
				if(refund.equals("cost") && (cost == null))
					throw n.error("refund", "the cost back needs a cost");
			}
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
		return ModuleContent.load(ModuleContent.world(), FOLDER, "saved choice", READER);
	}

	public static synchronized Library<Def> definitions()
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

	private static void require() throws IOException
	{
		if(!enabled())
			throw new ItemModifiers.Unavailable(MODULE + " is inactive in this campaign");
		ItemModifiers.require();
		if(!definitions().errors.isEmpty())
			throw new ItemModifiers.Unavailable(definitions().report());
	}

	/** draws from the offers stream: a cursor while the choice is made, then the stream is moved on by what it drew */
	private static final class Draws
	{
		final RandomStreams.Cursor	c		= RandomStreams.peek(RandomStreams.OFFERS);
		final long					start	= c.counter();

		double unit()
		{
			return (c.draw() >>> 11) * 0x1.0p-53;
		}

		int roll(final int min, final int max)
		{
			return min + (int)Long.remainderUnsigned(c.draw(), (long)max - min + 1);
		}

		void settle()
		{
			for(long i = start; i < c.counter(); i++)
				RandomStreams.draw(RandomStreams.OFFERS);
		}
	}

	private static JSONObject state(final MOB mob) throws IOException
	{
		return OptionalModules.get(MODULE, OptionalModules.character(mob));
	}

	private static JSONObject field(final JSONObject state, final String name) throws IOException
	{
		final Object raw = state.get(name);
		final String s = (raw == null) ? "" : raw.toString();
		return s.isEmpty() ? new JSONObject() : ModuleDefinitions.parse(s);
	}

	private static void put(final MOB mob, final JSONObject state, final String name, final JSONObject value) throws IOException
	{
		final String text = value.isEmpty() ? "" : ModuleDefinitions.json(value);
		if(text.length() > 4096)
			throw new IOException("the choice is too large to keep (4,096 characters): fewer options or modifiers");
		state.put(name, text);
		OptionalModules.put(MODULE, OptionalModules.character(mob), state);
	}

	private static void where(final MOB mob, final Def d) throws IOException
	{
		if((mob == null) || mob.amDead() || (mob.location() == null) || mob.isInCombat())
			throw new IOException("choose alive, in a room, outside combat");
		if(!Workstations.has(mob.location(), d.station))
			throw new IOException("required station: " + d.station);
	}

	private static Map<String, Integer> cost(final Def d)
	{
		final Map<String, Integer> out = new LinkedHashMap<String, Integer>();
		if(d.units > 0)
			out.put(d.material, Integer.valueOf(d.units));
		return out;
	}

	private static String costWords(final Def d)
	{
		return (d.units > 0) ? d.units + " weight unit(s) of plain " + d.material : "nothing";
	}

	/** the veil a reveal is for: the number given, or the first not yet revealed */
	private static int veilIndex(final JSONObject record, final String input) throws IOException
	{
		final List<JSONObject> veils = ItemModifierRules.veils(record);
		if(veils.isEmpty())
			throw new IOException("the item has no veiled modifier");
		if(input.trim().length() > 0)
		{
			final int n = CMath.s_int(input.trim());
			if((n < 1) || (n > veils.size()))
				throw new IOException("no veiled modifier " + input.trim() + " (ITEMMODS numbers them)");
			return n - 1;
		}
		for(int i = 0; i < veils.size(); i++)
		{
			if(!veils.get(i).containsKey("options"))
				return i;
		}
		throw new IOException("every veiled modifier is revealed already: its options are kept; CHOICES PICK settles one");
	}

	/** what a reveal can draw for a veil: the base's affixes of its position the item could hold were the veil gone */
	private static List<ItemModifierRules.Odds> revealOdds(final Def d, final JSONObject record, final JSONObject veil, final int level) throws IOException
	{
		final ItemModifierRules.Library lib = ItemModifiers.definitions();
		final List<JSONObject> others = ItemModifierRules.veils(record);
		others.removeIf(v -> v.get("uid").equals(veil.get("uid")));
		final JSONObject without = ItemModifierRules.withVeils(record, others);
		final String position = (String)veil.get("position");
		final ItemModifierRules.Filter filter = (a, r, u) -> !a.string("position").equals(position) ? "not a " + position
			: (!d.affixes.isEmpty() && !d.affixes.contains(a.id)) ? "outside this reveal's affixes"
			: !a.list("tags").containsAll(d.modifierTags) ? "lacks the reveal's required tags " + d.modifierTags : "";
		return ItemModifierRules.odds(without, ItemModifierRules.drawPool(record, lib), ItemModifierRules.Bias.NONE, false, filter, 0, level);
	}

	/** up to n distinct affixes by weight, without replacement, each rolled */
	private static List<JSONObject> distinct(final List<ItemModifierRules.Odds> odds, final int n, final Draws dr) throws IOException
	{
		final List<ItemModifierRules.Odds> left = new ArrayList<ItemModifierRules.Odds>();
		for(final ItemModifierRules.Odds o : odds)
		{
			if(o.why.isEmpty())
				left.add(o);
		}
		final List<JSONObject> out = new ArrayList<JSONObject>();
		while((out.size() < n) && !left.isEmpty())
		{
			double total = 0;
			for(final ItemModifierRules.Odds o : left)
				total += o.weight;
			double at = dr.unit() * total;
			ItemModifierRules.Odds chosen = left.get(left.size() - 1);
			for(final ItemModifierRules.Odds o : left)
			{
				at -= o.weight;
				if(at < 0)
				{
					chosen = o;
					break;
				}
			}
			left.remove(chosen);
			out.add(ItemModifierRules.rolled(chosen.affix, UUID.randomUUID().toString(), dr::roll));
		}
		return out;
	}

	private static Entry<Def> def(final String id) throws IOException
	{
		return definitions().latest(id);
	}

	/** what an offering's options are made of: the catalog item, its base and its level */
	private static Item proto(final Def d) throws IOException
	{
		final Item p = CMLib.catalog().getCatalogItem(d.catalog);
		if(p == null)
			throw new IOException("no catalog item '" + d.catalog + "' for the result");
		return p;
	}

	private static Definition resultBase(final Def d) throws IOException
	{
		return d.policy.isEmpty() ? ItemModifiers.definitions().find(d.base, 0, "base") : ItemLoot.base(d.policy);
	}

	/** why an offering will not take an item, or "" */
	private static String refuses(final Def d, final Item item) throws IOException
	{
		if(!d.tags.isEmpty())
		{
			if(!ItemModifiers.isManaged(item))
				return "it takes tracked items tagged " + d.tags;
			final List<String> have = new ArrayList<String>();
			for(final Object t : ModuleDefinitions.array(ItemModifiers.data(item).get("tags"), "tags"))
				have.add(String.valueOf(t));
			if(!have.containsAll(d.tags))
				return "it takes items tagged " + d.tags + "; " + item.Name() + " is tagged " + have;
		}
		if(!d.mask.isEmpty() && !CMLib.masking().maskCheck(d.mask, item, true))
			return "it does not take " + item.Name() + " (" + CMLib.masking().maskDesc(d.mask) + ")";
		return "";
	}

	/** CHOICES PREVIEW: what a reveal or an offering would do; nothing is paid or drawn */
	public static String preview(final MOB mob, final String id, final Item item, final String input) throws IOException
	{
		require();
		final Entry<Def> e = def(id);
		final Def d = e.value;
		where(mob, d);
		ItemCrafting.custody(mob, item);
		ItemCrafting.payAll(mob, item, cost(d));
		final StringBuilder s = new StringBuilder(e.title).append(" (").append(e.key()).append(") on ").append(item.Name()).append("; cost ").append(costWords(d))
			.append(" at ").append(d.station).append('.');
		if(d.kind.equals("reveal"))
		{
			final JSONObject rec = ItemModifiers.data(item);
			final int at = veilIndex(rec, input);
			final JSONObject veil = ItemModifierRules.veils(rec).get(at);
			if(veil.containsKey("options"))
				throw new IOException("veiled modifier " + (at + 1) + " is revealed already; CHOICES PICK \"" + item.Name() + "\" [number] settles it");
			final List<ItemModifierRules.Odds> odds = revealOdds(d, rec, veil, item.basePhyStats().level());
			if(ItemModifierRules.total(odds) <= 0)
				throw new IOException("nothing could be revealed for veiled modifier " + (at + 1) + ":" + ItemModifierRules.describe(odds));
			s.append("\nIt reveals veiled modifier ").append(at + 1).append(" (a ").append(veil.get("position")).append("): up to ").append(d.options)
				.append(" different affixes, drawn once and kept on the item. Each affix's chance to be among them, first:")
				.append(ItemModifierRules.describe(odds));
			if(d.discovery.length() > 0)
				s.append("\nThe first time you reveal an affix: ").append(QuestRewards.describe(d.discovery)).append('.');
		}
		else
		{
			final String why = refuses(d, item);
			if(why.length() > 0)
				throw new IOException(why);
			if(!field(state(mob), "offering").isEmpty())
				throw new IOException("an offering is open already; CHOICES PICK OFFERING [number] or CHOICES ABANDON first");
			final Item p = proto(d);
			s.append("\nIt takes ").append(item.Name()).append(" for good, and offers ").append(d.options).append(" of ").append(p.Name())
				.append(", drawn once and kept until you choose. Abandoning gives back ").append(d.refund.equals("cost") ? "the cost" : "nothing").append('.')
				.append("\nThe first modifier of each:")
				.append(ItemModifierRules.describe(ItemModifiers.firstPick(d.policy.isEmpty() ? d.base : "POLICY=" + d.policy, p.basePhyStats().level(), ItemModifierRules.Bias.NONE)));
		}
		return s.append("\nNothing was paid or drawn.").toString();
	}

	/** CHOICES OPEN: a reveal or an offering, paid and drawn once */
	public static String open(final MOB mob, final String id, final Item item, final String input) throws IOException
	{
		require();
		ItemModifiers.gameThread();
		final Entry<Def> e = def(id);
		final Def d = e.value;
		where(mob, d);
		ItemCrafting.custody(mob, item);
		return d.kind.equals("reveal") ? reveal(mob, e, item, input) : offer(mob, e, item);
	}

	private static void spend(final MOB mob, final List<ItemCrafting.Cost> costs)
	{
		for(final ItemCrafting.Cost c : costs)
		{
			if(c.amount < c.weight)
				c.spend();
			else
			{
				mob.delItem(c.item);
				c.item.setOwner(null);
			}
		}
	}

	private static void unspend(final MOB mob, final List<ItemCrafting.Cost> costs)
	{
		for(final ItemCrafting.Cost c : costs)
		{
			c.restore();
			if(c.item.owner() != mob)
				mob.addItem(c.item);
		}
	}

	private static void consumed(final List<ItemCrafting.Cost> costs)
	{
		for(final ItemCrafting.Cost c : costs)
		{
			if((c.amount == c.weight) && !c.item.amDestroyed())
				c.item.destroy();
		}
	}

	private static String menu(final List<JSONObject> options)
	{
		final StringBuilder s = new StringBuilder();
		for(int i = 0; i < options.size(); i++)
		{
			final JSONObject m = options.get(i);
			s.append("\n  ").append(i + 1).append(") ").append(m.get("name")).append(" (").append(((JSONObject)m.get("pin")).get("id")).append(", tier ")
				.append(m.get("tier")).append("): ").append(ModuleDefinitions.json((JSONObject)m.get("values")));
		}
		return s.toString();
	}

	private static String reveal(final MOB mob, final Entry<Def> e, final Item item, final String input) throws IOException
	{
		final Def d = e.value;
		final JSONObject rec = ItemModifiers.data(item);
		final int at = veilIndex(rec, input);
		final List<JSONObject> veils = ItemModifierRules.veils(rec);
		final JSONObject veil = veils.get(at);
		if(veil.containsKey("options"))
			throw new IOException("veiled modifier " + (at + 1) + " is revealed already: its options are kept; CHOICES PICK \"" + item.Name() + "\" [number] settles it");
		final List<ItemModifierRules.Odds> odds = revealOdds(d, rec, veil, item.basePhyStats().level());
		if(ItemModifierRules.total(odds) <= 0)
			throw new IOException("nothing could be revealed for veiled modifier " + (at + 1) + ":" + ItemModifierRules.describe(odds));
		final List<ItemCrafting.Cost> costs = ItemCrafting.payAll(mob, item, cost(d));
		final Draws dr = new Draws();
		final List<JSONObject> options = distinct(odds, d.options, dr);
		veil.put("options", options.toArray());
		veil.put("by", e.pin());
		final JSONObject next = ItemModifierRules.withVeils(rec, veils);
		next.put("revision", Long.valueOf(((Number)rec.get("revision")).longValue() + 1));
		final String oldText = ItemModifiers.property(item).text();
		spend(mob, costs);
		try
		{
			ItemModifiers.store(item, ItemModifierRules.checked(next));
		}
		catch(final IOException | RuntimeException x)
		{
			ItemModifiers.property(item).setMiscText(oldText);
			unspend(mob, costs);
			throw x;
		}
		consumed(costs);
		dr.settle();
		ModuleStats.count(FAMILY, e.id, "reveals");
		Provenance.event(mob, MODULE, "reveal", (e.key() + " on " + item.Name()).substring(0, Math.min(160, (e.key() + " on " + item.Name()).length())));
		return "You reveal veiled modifier " + (at + 1) + " of " + item.Name() + ". It could be:" + menu(options)
			+ "\nThe options are kept on the item. CHOICES PICK \"" + item.Name() + "\" [number] settles one; the others are gone then.";
	}

	private static String offer(final MOB mob, final Entry<Def> e, final Item item) throws IOException
	{
		final Def d = e.value;
		final JSONObject st = state(mob);
		if(!field(st, "offering").isEmpty())
			throw new IOException("an offering is open already; CHOICES PICK OFFERING [number] or CHOICES ABANDON first");
		final String why = refuses(d, item);
		if(why.length() > 0)
			throw new IOException(why);
		final Item p = proto(d);
		final Definition base = resultBase(d);
		if(!base.string("item_kind").equals(Affixes.kindOf(p)))
			throw new IOException("base " + base.key() + " is for " + base.string("item_kind") + ", the catalog item is " + Affixes.kindOf(p));
		final List<ItemCrafting.Cost> costs = ItemCrafting.payAll(mob, item, cost(d));
		final Draws dr = new Draws();
		final List<Object> options = new ArrayList<Object>();
		final int level = p.basePhyStats().level();
		for(int i = 0; i < d.options; i++)
		{
			final JSONObject o = new JSONObject();
			final List<JSONObject> mods, veils;
			String rarity = "";
			if(d.policy.isEmpty())
			{
				final JSONObject blank = ItemModifierRules.record(base, ZERO, "an item", "An item lies here.", "offering");
				mods = ItemModifierRules.draw(blank, ItemModifierRules.drawPool(blank, ItemModifiers.definitions()), ItemModifierRules.Bias.NONE, level,
											  Math.min(d.modifiers, ItemModifiers.limit()), ItemModifierRules.OPEN, dr::unit, dr::roll, () -> UUID.randomUUID().toString());
				veils = new ArrayList<JSONObject>();
			}
			else
			{
				final ItemLoot.Drop drop = ItemLoot.offer(d.policy, level, dr.c::draw);
				mods = drop.mods;
				veils = drop.veils;
				rarity = drop.rarity.title;
			}
			final JSONObject preview = ItemModifierRules.record(base, ZERO, p.Name(), p.displayText(), "offering");
			preview.put("modifiers", mods.toArray());
			o.put("name", ItemModifierRules.renderedName(preview));
			o.put("rarity", rarity);
			o.put("modifiers", mods.toArray());
			o.put("veiled", veils.toArray());
			options.add(o);
		}
		final JSONObject open = new JSONObject();
		open.put("def", e.pin());
		open.put("offered", item.Name());
		open.put("catalog", d.catalog);
		open.put("base", ItemModifierRules.pin(base));
		open.put("options", options.toArray());
		final JSONObject paid = new JSONObject();
		for(final Map.Entry<String, Integer> c : cost(d).entrySet())
			paid.put(c.getKey(), Long.valueOf(c.getValue().intValue()));
		open.put("paid", paid);
		open.put("refund", d.refund);
		final String before = (st.get("offering") == null) ? "" : st.get("offering").toString();
		spend(mob, costs);
		try
		{
			put(mob, st, "offering", open);
			mob.delItem(item);
		}
		catch(final IOException | RuntimeException x)
		{
			st.put("offering", before);
			OptionalModules.put(MODULE, OptionalModules.character(mob), st);
			if(item.owner() != mob)
				mob.addItem(item);
			unspend(mob, costs);
			throw x;
		}
		consumed(costs);
		item.destroy(); // given for good: it cannot also stay in the inventory
		dr.settle();
		ModuleStats.count(FAMILY, e.id, "offerings");
		Provenance.event(mob, MODULE, "offering", (e.key() + " took " + item.Name()).substring(0, Math.min(160, (e.key() + " took " + item.Name()).length())));
		return "You offer " + item.Name() + "; it is gone. " + e.title + " offers:" + offerMenu(open)
			+ "\nThe options are kept until you choose. CHOICES PICK OFFERING [number] takes one; CHOICES ABANDON gives back "
			+ (d.refund.equals("cost") ? "the cost" : "nothing") + ".";
	}

	private static String offerMenu(final JSONObject open) throws IOException
	{
		final StringBuilder s = new StringBuilder();
		final Object[] options = ModuleDefinitions.array(open.get("options"), "options");
		for(int i = 0; i < options.length; i++)
		{
			final JSONObject o = (JSONObject)options[i];
			final List<String> mods = new ArrayList<String>();
			for(final Object m : ModuleDefinitions.array(o.get("modifiers"), "modifiers"))
				mods.add(((JSONObject)m).get("name") + " " + ModuleDefinitions.json((JSONObject)((JSONObject)m).get("values")));
			final int veiled = ModuleDefinitions.array(o.get("veiled"), "veiled").length;
			s.append("\n  ").append(i + 1).append(") ").append(o.get("name")).append(String.valueOf(o.get("rarity")).isEmpty() ? "" : " (" + o.get("rarity") + ")")
				.append(mods.isEmpty() ? ", no modifiers" : ": " + String.join("; ", mods)).append(veiled > 0 ? "; " + veiled + " veiled" : "");
		}
		return s.toString();
	}

	/** CHOICES PICK "[item]" [n]: settles a revealed veil with one of its options, once */
	public static String pick(final MOB mob, final Item item, final int n) throws IOException
	{
		require();
		ItemModifiers.gameThread();
		if((mob == null) || mob.amDead())
			throw new IOException("choose alive");
		if((item == null) || (item.owner() != mob))
			throw new IOException("choose for an item you carry");
		final JSONObject rec = ItemModifiers.data(item);
		final List<JSONObject> veils = ItemModifierRules.veils(rec);
		int at = -1;
		for(int i = 0; (i < veils.size()) && (at < 0); i++)
		{
			if(veils.get(i).containsKey("options"))
				at = i;
		}
		if(at < 0)
			throw new IOException(veils.isEmpty() ? item.Name() + " has no veiled modifier" : "no veiled modifier of " + item.Name() + " is revealed yet; CHOICES OPEN a reveal first");
		final JSONObject veil = veils.get(at);
		final Object[] options = ModuleDefinitions.array(veil.get("options"), "options");
		if((n < 1) || (n > options.length))
			throw new IOException("choose 1 to " + options.length);
		final JSONObject chosen = ModuleDefinitions.copy((JSONObject)options[n - 1]);
		final ItemModifierRules.Library lib = ItemModifiers.definitions();
		final Definition affix = lib.pinned(chosen.get("pin"), "affix");
		veils.remove(at);
		final JSONObject without = ItemModifierRules.withVeils(rec, veils);
		final String why = ItemModifierRules.ineligible(without, affix, item.basePhyStats().level());
		if(why.length() > 0)
			throw new IOException(chosen.get("name") + " no longer fits: " + why + "; choose another");
		final List<JSONObject> mods = ItemModifierRules.modifiers(without);
		mods.add(chosen);
		without.put("modifiers", mods.toArray());
		without.put("revision", Long.valueOf(((Number)rec.get("revision")).longValue() + 1));
		final JSONObject st = state(mob), codex = field(st, "codex");
		final boolean first = !codex.containsKey(affix.id);
		Entry<Def> by = null;
		try
		{
			by = definitions().pinned(veil.get("by"));
		}
		catch(final IOException x)
		{
			by = null; // the reveal changed or went: no discovery reward
		}
		ItemModifiers.install(item, ItemModifiers.names(item, ItemModifierRules.checked(without)));
		String reward = "";
		boolean noted = false;
		if(first)
		{
			codex.put(affix.id, chosen.get("name"));
			// the codex keeps each affix once, so a reward is given once; a full codex notes nothing more, and rewards nothing
			if(ModuleDefinitions.json(codex).length() <= 4096)
			{
				put(mob, st, "codex", codex);
				noted = true;
				if((by != null) && (by.value.discovery.length() > 0))
					reward = QuestRewards.give(mob, "the reveal of " + affix.name, by.value.discovery);
			}
		}
		ModuleStats.count(FAMILY, (by == null) ? "reveal" : by.id, "picks");
		return "You settle " + item.Name() + "'s veiled " + chosen.get("position") + ": " + chosen.get("name") + ". The other options are gone."
			+ (noted ? " " + affix.name + " goes in your codex" + (reward.isEmpty() ? "." : ": " + reward) : first ? " Your codex is full." : " You knew it already.");
	}

	/** CHOICES PICK OFFERING [n]: takes one of the open offering's options, once */
	public static String pickOffering(final MOB mob, final int n) throws IOException
	{
		require();
		ItemModifiers.gameThread();
		if((mob == null) || mob.amDead())
			throw new IOException("choose alive");
		final JSONObject st = state(mob), open = field(st, "offering");
		if(open.isEmpty())
			throw new IOException("no offering is open");
		final Object[] options = ModuleDefinitions.array(open.get("options"), "options");
		if((n < 1) || (n > options.length))
			throw new IOException("choose 1 to " + options.length);
		final JSONObject o = (JSONObject)options[n - 1];
		final Item p = CMLib.catalog().getCatalogItem((String)open.get("catalog"));
		if(p == null)
			throw new IOException("no catalog item '" + open.get("catalog") + "' to make the option of; the offering stays open");
		final Definition base = ItemModifiers.definitions().pinned(open.get("base"), "base");
		final Item made = (Item)p.copyOf();
		made.setOwner(null);
		made.setContainer(null);
		made.unWear();
		final List<JSONObject> mods = new ArrayList<JSONObject>(), veils = new ArrayList<JSONObject>();
		for(final Object m : ModuleDefinitions.array(o.get("modifiers"), "modifiers"))
			mods.add((JSONObject)m);
		for(final Object v : ModuleDefinitions.array(o.get("veiled"), "veiled"))
			veils.add((JSONObject)v);
		try
		{
			ItemModifiers.initializeWith(made, base, mods, veils, ("offering:" + ((JSONObject)open.get("def")).get("id")));
			put(mob, st, "offering", new JSONObject());
		}
		catch(final IOException | RuntimeException x)
		{
			made.destroy();
			throw x;
		}
		mob.addItem(made);
		ModuleStats.count(FAMILY, String.valueOf(((JSONObject)open.get("def")).get("id")), "picks");
		return "You take " + made.Name() + ". The other options are gone.";
	}

	/** CHOICES ABANDON: closes the open offering by its refund rule */
	public static String abandon(final MOB mob) throws IOException
	{
		require();
		ItemModifiers.gameThread();
		final JSONObject st = state(mob), open = field(st, "offering");
		if(open.isEmpty())
			throw new IOException("no offering is open");
		final List<Item> back = new ArrayList<Item>();
		if("cost".equals(open.get("refund")))
		{
			final JSONObject paid = (JSONObject)open.get("paid");
			for(final String material : paid.keySet())
			{
				final int code = RawMaterial.CODES.FIND_CaseSensitive(material);
				final Item pile = (code < 0) ? null : CMLib.materials().makeItemResource(code);
				if(pile == null)
					continue;
				pile.basePhyStats().setWeight(((Number)paid.get(material)).intValue());
				CMLib.materials().adjustResourceName(pile);
				pile.recoverPhyStats();
				back.add(pile);
			}
		}
		put(mob, st, "offering", new JSONObject());
		for(final Item I : back)
			mob.addItem(I);
		ModuleStats.count(FAMILY, String.valueOf(((JSONObject)open.get("def")).get("id")), "abandoned");
		return "You abandon the offering of " + open.get("offered") + ". " + (back.isEmpty() ? "Nothing comes back." : "The cost comes back.");
	}

	/** CHOICES: the open offering, the revealed veils on what you carry, and your codex */
	public static String list(final MOB mob) throws IOException
	{
		require();
		final StringBuilder s = new StringBuilder();
		final JSONObject st = state(mob), open = field(st, "offering");
		if(!open.isEmpty())
			s.append("Open offering of ").append(open.get("offered")).append(" (").append(((JSONObject)open.get("def")).get("id")).append("):").append(offerMenu(open))
				.append("\nCHOICES PICK OFFERING [number] takes one; CHOICES ABANDON gives back ").append("cost".equals(open.get("refund")) ? "the cost" : "nothing").append('.');
		for(final Enumeration<Item> e = mob.items(); e.hasMoreElements();)
		{
			final Item I = e.nextElement();
			if(!ItemModifiers.isManaged(I))
				continue;
			final List<JSONObject> veils = ItemModifierRules.veils(ItemModifiers.data(I));
			for(int i = 0; i < veils.size(); i++)
			{
				final JSONObject v = veils.get(i);
				s.append(s.length() > 0 ? "\n" : "").append(I.Name()).append(", veiled ").append(i + 1).append(" (a ").append(v.get("position")).append("): ");
				if(!v.containsKey("options"))
					s.append("not yet revealed");
				else
				{
					final List<JSONObject> options = new ArrayList<JSONObject>();
					for(final Object o : (Object[])v.get("options"))
						options.add((JSONObject)o);
					s.append("revealed; CHOICES PICK \"").append(I.Name()).append("\" [number]:").append(menu(options));
				}
			}
		}
		final JSONObject codex = field(st, "codex");
		if(!codex.isEmpty())
		{
			final List<String> known = new ArrayList<String>();
			for(final String k : codex.keySet())
				known.add(codex.get(k) + " (" + k + ")");
			s.append(s.length() > 0 ? "\n" : "").append("Your codex: ").append(String.join(", ", known)).append('.');
		}
		return (s.length() == 0) ? "You have no open choice." : s.toString();
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
			return "CHOICES";
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
			return Arrays.asList("offering", "codex");
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
			ItemChoices.reset();
		}

		@Override
		public List<String> check()
		{
			final Library<Def> lib = load();
			final List<String> out = new ArrayList<String>(lib.errors);
			final ItemModifierRules.Library mods = ItemModifiers.check();
			for(final Entry<Def> e : lib.entries.values())
			{
				final Def d = e.value;
				if((d.units > 0) && (RawMaterial.CODES.FIND_CaseSensitive(d.material) < 0))
					out.add(e.path + ": /cost/material: unknown registered material " + d.material);
				try
				{
					for(final String a : d.affixes)
						mods.find(a, 0, "affix");
					if(!d.base.isEmpty())
						mods.find(d.base, 0, "base");
				}
				catch(final IOException x)
				{
					out.add(e.path + ": " + x.getMessage());
				}
				if(!d.policy.isEmpty() && !ItemLoot.load().has(d.policy))
					out.add(e.path + ": /result/policy: no loot policy " + d.policy + " in loot/");
				else if(!d.policy.isEmpty())
				{
					try
					{
						for(final ItemLoot.Rarity r : ItemLoot.load().latest(d.policy).value.rarities)
						{
							if(r.modifiers > 3)
								out.add(e.path + ": /result/policy: rarity " + r.id + " of " + d.policy + " gives " + r.modifiers + " modifiers; an offering keeps options of up to 3");
						}
					}
					catch(final IOException x)
					{
						out.add(e.path + ": /result/policy: " + x.getMessage());
					}
				}
			}
			return out;
		}

		@Override
		public String list() throws IOException
		{
			final Library<Def> lib = load();
			if(lib.entries.isEmpty())
				return "No reveals or offerings are installed in " + FOLDER + "/." + (lib.errors.isEmpty() ? "" : "\n" + String.join("\n", lib.errors));
			final StringBuilder s = new StringBuilder("Saved choices:");
			for(final Entry<Def> e : lib.entries.values())
			{
				final Def d = e.value;
				s.append("\n  ").append(e.key()).append(" (").append(e.title).append("): ").append(d.kind).append(" at ").append(d.station).append(", ")
					.append(d.options).append(" option(s), cost ").append(costWords(d));
				if(d.kind.equals("offering"))
					s.append("; makes ").append(d.catalog).append(" by ").append(d.policy.isEmpty() ? "base " + d.base : "policy " + d.policy).append("; abandoning gives back ")
						.append(d.refund.equals("cost") ? "the cost" : "nothing");
				else if(d.discovery.length() > 0)
					s.append("; a first reveal of an affix gives ").append(d.discovery);
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
			return null;
		}
	};

	static
	{
		ModuleContent.register(FAMILY_ENTRY);
	}
}
