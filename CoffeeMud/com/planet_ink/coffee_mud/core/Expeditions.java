package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Areas.interfaces.AutoGenArea;
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
 * G01: itemized expeditions, the optional module expeditions.contracts.  A
 * chart (an item with Prop_ExpeditionChart) names an expedition and carries
 * a seed and a tier.  Opening it is one transaction: the chart and the costs
 * are consumed once, and a run is recorded with the seed, the modifiers
 * chosen from those the chart offers, the attempts and the party.  The run's
 * seed and modifiers make the generated instance when the party enters the
 * destination (a StdAutoGenInstance area, which a gated expedition keeps
 * closed to those without a run).  A death inside uses an attempt; with none
 * left the run fails and the party is returned.  The run keeps its
 * instance's identity across a restore; a missing instance or member takes
 * the recovery path.  Q03: PREVIEW shows all of it and changes nothing.
 * Definitions: world/expeditions/[id].v[N].json.
 */
public final class Expeditions
{
	private Expeditions()
	{
	}

	public static final String	MODULE		= "expeditions.contracts";
	public static final String	FOLDER		= "expeditions";
	public static final String	FAMILY		= "expeditions";
	/** the most runs kept, open ones first */
	public static final int		MAX_RUNS	= 12;

	/** one modifier a chart can offer */
	public static final class Modifier
	{
		public final String	id;
		public final String	title;
		public final int	mobHpPercent;
		public final int	mobLevel;
		public final String	effectId;
		public final String	effectText;

		Modifier(final Node n) throws IOException
		{
			n.allow("id", "title", "mob_hp_percent", "mob_level", "mob_effect");
			id = n.id("id");
			title = n.string("title", id, 120);
			mobHpPercent = (int)n.integer("mob_hp_percent", 0, -90, 500);
			mobLevel = (int)n.integer("mob_level", 0, -20, 50);
			final Node e = n.object("mob_effect");
			if(e == null)
			{
				effectId = "";
				effectText = "";
			}
			else
			{
				e.allow("id", "text");
				effectId = e.string("id");
				effectText = e.string("text", "", 4096);
			}
			if((mobHpPercent == 0) && (mobLevel == 0) && effectId.isEmpty())
				throw n.error(null, "a modifier changes something: mob_hp_percent, mob_level or mob_effect");
		}

		public String describe()
		{
			final List<String> parts = new ArrayList<String>();
			if(mobHpPercent != 0)
				parts.add("foes have " + ((mobHpPercent > 0) ? "+" : "") + mobHpPercent + "% hit points");
			if(mobLevel != 0)
				parts.add("foes are " + ((mobLevel > 0) ? "+" : "") + mobLevel + " levels");
			if(!effectId.isEmpty())
				parts.add("foes have " + effectId);
			return title + " (" + String.join(", ", parts) + ")";
		}
	}

	/** one expedition */
	public static final class Def
	{
		public final String				area;
		public final boolean			gated;
		public final String				returnRoom;
		public final List<Modifier>		modifiers	= new ArrayList<Modifier>();
		public final int				offer;
		public final int				choose;
		public final long				money;
		public final int				attempts;
		public final int				partyMax;
		public final int				tierMin;
		public final int				tierMax;
		public final String				activity;
		public final Map<String, String>	variables	= new TreeMap<String, String>();

		Def(final Node n) throws IOException
		{
			n.allow("schema", "id", "version", "title", "destination", "return_room", "modifiers", "offer", "choose", "costs", "attempts", "party", "tier", "activity", "variables");
			final Node d = n.required("destination");
			d.allow("area", "gated");
			area = d.string("area");
			if(area.trim().isEmpty())
				throw d.error("area", "required");
			gated = d.bool("gated", true);
			returnRoom = n.string("return_room");
			for(final Node m : n.objects("modifiers", 16))
				modifiers.add(new Modifier(m));
			final Set<String> ids = new HashSet<String>();
			for(final Modifier m : modifiers)
			{
				if(!ids.add(m.id))
					throw n.error("modifiers", "duplicate modifier " + m.id);
			}
			offer = (int)n.integer("offer", Math.min(2, modifiers.size()), 0, 8);
			choose = (int)n.integer("choose", Math.min(1, offer), 0, 8);
			if(offer > modifiers.size())
				throw n.error("offer", "offers " + offer + " of " + modifiers.size() + " modifiers");
			if(choose > offer)
				throw n.error("choose", "chooses " + choose + " of the " + offer + " offered");
			final Node c = n.object("costs");
			if(c == null)
				money = 0;
			else
			{
				c.allow("money");
				money = c.integer("money", 0, 0, 1000000000);
			}
			attempts = (int)n.integer("attempts", 1, 1, 20);
			final Node p = n.object("party");
			if(p == null)
				partyMax = 4;
			else
			{
				p.allow("max");
				partyMax = (int)p.integer("max", 4, 1, 12);
			}
			final Node t = n.object("tier");
			if(t == null)
			{
				tierMin = 1;
				tierMax = 1;
			}
			else
			{
				t.allow("min", "max");
				tierMin = (int)t.integer("min", 1, 1, 100);
				tierMax = (int)t.integer("max", tierMin, tierMin, 100);
			}
			activity = n.has("activity") ? n.id("activity") : "";
			final Node v = n.object("variables");
			if(v != null)
			{
				for(final String k : v.obj.keySet())
				{
					if(!k.matches("[A-Z][A-Z0-9_]{0,31}") || k.equals("SEED"))
						throw v.error(k, "a variable name is upper case, and the seed is the chart's own");
					variables.put(k, v.string(k, "", 200));
				}
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
		return ModuleContent.load(ModuleContent.world(), FOLDER, "expedition", READER);
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

	public static void require() throws IOException
	{
		if(!enabled())
			throw new IOException("expeditions (" + MODULE + ") are not active in this campaign");
	}

	// ---- the campaign's runs

	private static JSONObject state() throws IOException
	{
		final JSONObject rec = OptionalModules.get(MODULE, OptionalModules.world());
		final String raw = (String)rec.get("runs");
		return ((raw == null) || raw.isEmpty()) ? new JSONObject() : ModuleDefinitions.parse(raw);
	}

	private static JSONObject runs(final JSONObject st)
	{
		if(!(st.get("runs") instanceof JSONObject))
			st.put("runs", new JSONObject());
		return (JSONObject)st.get("runs");
	}

	private static void store(final JSONObject st) throws IOException
	{
		// keep the open runs; drop the oldest finished ones past the limit
		final JSONObject rs = runs(st);
		final List<String> order = new ArrayList<String>(rs.keySet());
		Collections.sort(order, (a, b) -> Long.compare(Long.parseLong(a), Long.parseLong(b)));
		for(final String k : order)
		{
			if(rs.size() <= MAX_RUNS)
				break;
			if(!open((JSONObject)rs.get(k)))
				rs.remove(k);
		}
		final String text = ModuleDefinitions.json(st);
		if(text.length() > 4096)
			throw new IOException("too many expedition runs are open in this campaign");
		final JSONObject rec = OptionalModules.get(MODULE, OptionalModules.world());
		rec.put("runs", text);
		OptionalModules.put(MODULE, OptionalModules.world(), rec);
	}

	private static boolean open(final JSONObject run)
	{
		final String st = (String)run.get("status");
		return st.equals("open") || st.equals("entered");
	}

	private static List<String> party(final JSONObject run)
	{
		final List<String> out = new ArrayList<String>();
		if(run.get("party") instanceof Object[])
		{
			for(final Object o : (Object[])run.get("party"))
				out.add(String.valueOf(o));
		}
		return out;
	}

	/** Q05: the open run a character is part of, as its number and record, or null; it reads only */
	public static Map.Entry<String, JSONObject> runOfCharacter(final MOB mob) throws IOException
	{
		return runOf(state(), mob);
	}

	/** the open run a character is part of, as its number and record, or null */
	private static Map.Entry<String, JSONObject> runOf(final JSONObject st, final MOB mob)
	{
		final String ref = InstanceIds.reference(mob);
		for(final String k : runs(st).keySet())
		{
			final JSONObject run = (JSONObject)runs(st).get(k);
			if(open(run) && party(run).contains(ref))
				return new AbstractMap.SimpleEntry<String, JSONObject>(k, run);
		}
		return null;
	}

	// ---- charts

	/** the chart property of an item, or null */
	public static Ability chartOf(final Item I)
	{
		return (I == null) ? null : I.fetchEffect("Prop_ExpeditionChart");
	}

	/** a chart's seed, drawing it from the expeditions stream the first time (written into the chart) */
	public static long seed(final Item chart, final boolean draw) throws IOException
	{
		final Ability A = chartOf(chart);
		if(A == null)
			throw new IOException(chart.name() + " is not an expedition chart");
		final long s = CMParms.getParmLong(A.text(), "SEED", 0);
		if((s > 0) || !draw)
			return s;
		final long drawn = chartSeed(RandomStreams.draw(RandomStreams.EXPEDITIONS));
		final String t = A.text();
		A.setMiscText(t.matches("(?is).*\\bSEED=\\S*.*") ? t.replaceAll("(?i)\\bSEED=\\S*", "SEED=" + drawn) : (t.trim() + " SEED=" + drawn));
		return drawn;
	}

	/** a chart seed from a draw of the expeditions stream: positive, below 2^31 */
	public static long chartSeed(final long draw)
	{
		return 1 + (draw & 0x7fffffffL);
	}

	/** the seed an unread chart would have if it were opened now, without drawing it */
	public static long nextSeed()
	{
		return chartSeed(RandomStreams.peek(RandomStreams.EXPEDITIONS).draw());
	}

	/** the modifiers a chart offers: drawn from its seed, the same each time */
	public static List<Modifier> offered(final Def d, final long seed)
	{
		final List<Modifier> pool = new ArrayList<Modifier>(d.modifiers);
		final List<Modifier> out = new ArrayList<Modifier>();
		final RandomStreams.Cursor c = new RandomStreams.Cursor(seed, "expedition-offers", 0);
		while((out.size() < d.offer) && !pool.isEmpty())
			out.add(pool.remove(c.roll(pool.size()) - 1));
		return out;
	}

	private static Entry<Def> defOf(final Item chart) throws IOException
	{
		final Ability A = chartOf(chart);
		if(A == null)
			throw new IOException(chart.name() + " is not an expedition chart");
		final String id = CMParms.getParmStr(A.text(), "EXPEDITION", "").toLowerCase(Locale.ROOT);
		return definitions().latest(id);
	}

	private static int tierOf(final Item chart, final Def d)
	{
		final int t = CMParms.getParmInt(chartOf(chart).text(), "TIER", d.tierMin);
		return Math.max(d.tierMin, Math.min(d.tierMax, t));
	}

	/** the party that would go: the character and those of their party here, up to the limit */
	private static List<MOB> partyHere(final MOB mob, final int max)
	{
		final List<MOB> out = new ArrayList<MOB>(Party.here(mob));
		out.remove(mob);
		out.add(0, mob);
		while(out.size() > Math.max(1, max))
			out.remove(out.size() - 1);
		return out;
	}

	/**
	 * Q03: the expedition a chart opens, in words: destination, seed, tier,
	 * the modifiers it offers, costs and whether they can be paid, attempts,
	 * who would go, and what would refuse it.  It changes nothing: an undrawn
	 * seed is shown as it would be drawn now, without drawing it.
	 */
	public static String preview(final MOB mob, final Item chart) throws IOException
	{
		require();
		final Entry<Def> e = defOf(chart);
		final Def d = e.value;
		long seed = seed(chart, false);
		final boolean undrawn = seed <= 0;
		if(undrawn)
			seed = nextSeed();
		final StringBuilder s = new StringBuilder("Expedition: ").append(e.title).append(" (").append(e.key()).append(")");
		s.append("\nDestination: ").append(d.area).append(d.gated ? " (opened only by a chart)" : "");
		s.append("\nSeed: ").append(seed).append(undrawn ? " (not yet drawn: this is the seed it would have if opened now)" : "");
		s.append("\nTier: ").append(tierOf(chart, d));
		final List<Modifier> offered = offered(d, seed);
		if(offered.isEmpty())
			s.append("\nModifiers: none");
		else
		{
			s.append("\nModifiers offered (choose ").append(d.choose).append("):");
			for(final Modifier m : offered)
				s.append("\n  ").append(m.id).append(": ").append(m.describe());
		}
		final double have = CMLib.beanCounter().getTotalAbsoluteValue(mob, CMLib.beanCounter().getCurrency(mob));
		s.append("\nCost: the chart").append((d.money > 0) ? (", and " + CMLib.beanCounter().nameCurrencyShort(mob, (double)d.money) + ((have >= d.money) ? "" : " (you cannot pay it)")) : "");
		s.append("\nAttempts: ").append(d.attempts);
		final List<MOB> go = partyHere(mob, d.partyMax);
		final List<String> names = new ArrayList<String>();
		for(final MOB M : go)
			names.add(M.Name());
		s.append("\nParty: ").append(String.join(", ", names)).append(" (at most ").append(d.partyMax).append(")");
		final String refusal = refusal(mob, d, go);
		s.append("\n").append(refusal.isEmpty() ? "It can be opened here." : ("It cannot be opened: " + refusal + "."));
		return s.toString();
	}

	private static String refusal(final MOB mob, final Def d, final List<MOB> go) throws IOException
	{
		final Area A = CMLib.map().getArea(d.area);
		if(!(A instanceof AutoGenArea))
			return "the destination " + d.area + " is not a generated area in this world";
		if((d.returnRoom.length() > 0) && (CMLib.map().getRoom(d.returnRoom) == null))
			return "its return room " + d.returnRoom + " is not in this world";
		final JSONObject st = state();
		for(final MOB M : go)
		{
			if(runOf(st, M) != null)
				return M.Name() + " is already on an expedition";
		}
		if((d.money > 0) && (CMLib.beanCounter().getTotalAbsoluteValue(mob, CMLib.beanCounter().getCurrency(mob)) < d.money))
			return "it costs " + CMLib.beanCounter().nameCurrencyShort(mob, (double)d.money);
		return "";
	}

	/**
	 * Opens a chart: one transaction that checks everything first, then
	 * consumes the chart and the cost once and records the run.
	 * @param chosen the modifier IDs chosen from those offered
	 * @return the run's number
	 */
	public static synchronized long open(final MOB mob, final Item chart, final List<String> chosen) throws IOException
	{
		require();
		ModuleContent.gameThread();
		final Entry<Def> e = defOf(chart);
		final Def d = e.value;
		if(chart.owner() != mob)
			throw new IOException("you must carry the chart");
		final List<MOB> go = partyHere(mob, d.partyMax);
		final String refusal = refusal(mob, d, go);
		if(refusal.length() > 0)
		{
			Provenance.refused(mob, MODULE, e.key(), refusal, "EXPEDITION PREVIEW shows what opening needs; the chart and its cost were not taken"); // Q05
			throw new IOException(refusal);
		}
		// the chart's seed: drawn now if it never was, the scenario recorded first so it can be drawn again
		final JSONObject rules = new JSONObject();
		rules.put("expedition", e.pin());
		final JSONObject scenario = (seed(chart, false) > 0) ? null : RandomStreams.record(RandomStreams.EXPEDITIONS, rules);
		final long seed = seed(chart, true);
		final List<Modifier> offered = offered(d, seed);
		final List<String> mods = new ArrayList<String>();
		for(final String c : chosen)
		{
			boolean found = false;
			for(final Modifier m : offered)
				found |= m.id.equalsIgnoreCase(c);
			if(!found)
				throw new IOException(c + " is not one of the modifiers this chart offers");
			if(!mods.contains(c.toLowerCase(Locale.ROOT)))
				mods.add(c.toLowerCase(Locale.ROOT));
		}
		if(mods.size() != d.choose)
			throw new IOException("choose " + d.choose + " of the offered modifiers");
		// commit: the cost and the chart, once; then the run
		if(d.money > 0)
			CMLib.beanCounter().subtractMoney(mob, CMLib.beanCounter().getCurrency(mob), d.money);
		final JSONObject st = state();
		final long n = ((st.get("serial") instanceof Number) ? ((Number)st.get("serial")).longValue() : 0) + 1;
		st.put("serial", Long.valueOf(n));
		final JSONObject run = new JSONObject();
		run.put("exp", e.pin());
		run.put("seed", Long.valueOf(seed));
		run.put("tier", Long.valueOf(tierOf(chart, d)));
		run.put("mods", mods.toArray(new String[0]));
		run.put("left", Long.valueOf(d.attempts));
		final List<String> refs = new ArrayList<String>();
		for(final MOB M : go)
			refs.add(InstanceIds.reference(M));
		run.put("party", refs.toArray(new String[0]));
		run.put("status", "open");
		run.put("instance", "");
		if(scenario != null)
			run.put("scenario", scenario);
		runs(st).put(Long.toString(n), run);
		store(st);
		chart.destroy();
		ModuleStats.count(FAMILY, e.id, "opened");
		for(final MOB M : go)
			Provenance.event(M, MODULE, e.key(), "expedition " + n + " opened by " + mob.Name()); // Q05
		for(final MOB M : go)
		{
			if(M != mob)
				M.tell(mob.Name() + " opens " + e.title + " for the party.");
		}
		return n;
	}

	private static Entry<Def> defOfRun(final JSONObject run) throws IOException
	{
		return definitions().pinned(run.get("exp"));
	}

	// ---- the hooks of StdAutoGenInstance

	/** a gated destination refuses a group with no open run for it: the reason, or "" */
	public static String gate(final MOB mob, final Area parent)
	{
		if(!enabled() || (mob == null))
			return "";
		try
		{
			boolean destination = false;
			for(final Entry<Def> e : definitions().latestAll())
				destination |= e.value.gated && e.value.area.equalsIgnoreCase(parent.Name());
			if(!destination)
				return "";
			final JSONObject st = state();
			for(final MOB M : mob.getGroupMembers(new HashSet<MOB>()))
			{
				final Map.Entry<String, JSONObject> r = runOf(st, M);
				if((r != null) && defOfRun(r.getValue()).value.area.equalsIgnoreCase(parent.Name()))
					return "";
			}
			return "Only an opened expedition chart leads there.";
		}
		catch(final IOException e)
		{
			return "The way there is closed (" + e.getMessage() + ").";
		}
	}

	/** the existing instance of the group's run, to rejoin, or null for a new one */
	public static String existing(final MOB mob, final Area parent)
	{
		try
		{
			if(!enabled())
				return null;
			final Map.Entry<String, JSONObject> r = runOf(state(), mob);
			if((r == null) || !defOfRun(r.getValue()).value.area.equalsIgnoreCase(parent.Name()))
				return null;
			final String name = (String)r.getValue().get("instance");
			return ((name.length() > 0) && (CMLib.map().getArea(name) != null)) ? name : null;
		}
		catch(final IOException e)
		{
			return null;
		}
	}

	/** puts the run's seed and variables over the area's own, for a new instance */
	public static void variables(final MOB mob, final Area parent, final Map<String, Object> defined)
	{
		try
		{
			if(!enabled())
				return;
			final Map.Entry<String, JSONObject> r = runOf(state(), mob);
			if(r == null)
				return;
			final Def d = defOfRun(r.getValue()).value;
			if(!d.area.equalsIgnoreCase(parent.Name()))
				return;
			for(final Map.Entry<String, String> v : d.variables.entrySet())
				defined.put(v.getKey(), v.getValue());
			defined.put("SEED", r.getValue().get("seed").toString());
			defined.put("TIER", r.getValue().get("tier").toString());
		}
		catch(final IOException e)
		{
			Log.errOut("Expeditions", "variables: " + e.getMessage());
		}
	}

	/** a new instance was made for the group's run: it is the run's, with its modifiers */
	public static void made(final MOB mob, final Area parent, final Area child)
	{
		try
		{
			if(!enabled())
				return;
			final JSONObject st = state();
			final Map.Entry<String, JSONObject> r = runOf(st, mob);
			if(r == null)
				return;
			final JSONObject run = r.getValue();
			final Def d = defOfRun(run).value;
			if(!d.area.equalsIgnoreCase(parent.Name()))
				return;
			final boolean again = (((String)run.get("instance")).length() > 0) || Boolean.TRUE.equals(run.get("again"));
			run.remove("again");
			run.put("instance", child.Name());
			run.put("status", "entered");
			store(st);
			final Set<String> mods = new HashSet<String>();
			for(final Object o : (Object[])run.get("mods"))
				mods.add(String.valueOf(o));
			for(final Modifier m : d.modifiers)
			{
				if(mods.contains(m.id))
					apply(m, child);
			}
			ModuleStats.count(FAMILY, defOfRun(run).id, again ? "instances made again from the seed" : "instances made");
			if(again)
				mob.tell("The way has changed since; it is laid out again from the chart.");
		}
		catch(final IOException e)
		{
			Log.errOut("Expeditions", "made: " + e.getMessage());
		}
	}

	private static void apply(final Modifier m, final Area child)
	{
		for(final Enumeration<Room> e = child.getProperMap(); e.hasMoreElements();)
		{
			final Room R = e.nextElement();
			for(int i = 0; i < R.numInhabitants(); i++)
			{
				final MOB M = R.fetchInhabitant(i);
				if((M == null) || !M.isMonster())
					continue;
				if(m.mobLevel != 0)
				{
					M.basePhyStats().setLevel(Math.max(1, M.basePhyStats().level() + m.mobLevel));
					M.recoverPhyStats();
				}
				if(m.mobHpPercent != 0)
				{
					M.baseState().setHitPoints((int)Math.max(1, ((long)M.baseState().getHitPoints() * (100 + m.mobHpPercent)) / 100));
					M.recoverMaxState();
					M.resetToMaxState();
				}
				if(!m.effectId.isEmpty())
				{
					final Ability A = CMClass.getAbility(m.effectId);
					if(A != null)
					{
						A.setMiscText(m.effectText);
						M.addNonUninvokableEffect(A);
					}
				}
			}
		}
	}

	/** a character died: in their run's instance, it uses an attempt; the last one fails the run */
	public static void died(final MOB victim)
	{
		if((victim == null) || !enabled() || (victim.location() == null))
			return;
		try
		{
			final JSONObject st = state();
			final Map.Entry<String, JSONObject> r = runOf(st, victim);
			if(r == null)
				return;
			final JSONObject run = r.getValue();
			if(!victim.location().getArea().Name().equals(run.get("instance")))
				return;
			final long left = ((Number)run.get("left")).longValue() - 1;
			run.put("left", Long.valueOf(Math.max(0, left)));
			ModuleStats.count(FAMILY, defOfRun(run).id, "attempts used");
			Provenance.event(victim, MODULE, defOfRun(run).key(), "died in expedition " + r.getKey() + ": " + Math.max(0, left) + " attempt(s) left"); // Q05
			if(left > 0)
			{
				store(st);
				victim.tell("The expedition has " + left + " attempt" + ((left == 1) ? "" : "s") + " left.");
				return;
			}
			run.put("status", "failed");
			store(st);
			final String instance = (String)run.get("instance");
			final Def d = defOfRun(run).value;
			CMLib.threads().executeRunnable(() -> closeInstance(instance, d));
			if(!d.activity.isEmpty() && RewardEscrow.enabled())
			{
				for(final String ref : party(run))
				{
					final Object o = InstanceIds.find(ref);
					if(o instanceof MOB)
						RewardEscrow.end((MOB)o, d.activity, "fail");
				}
			}
			ModuleStats.count(FAMILY, defOfRun(run).id, "failed");
		}
		catch(final IOException e)
		{
			Log.errOut("Expeditions", victim.Name() + "'s death: " + e.getMessage());
		}
	}

	/** the party out, the instance gone (after the death in progress is over) */
	private static void closeInstance(final String instance, final Def d)
	{
		final Area A = CMLib.map().getArea(instance);
		final Room back = (d.returnRoom.length() > 0) ? CMLib.map().getRoom(d.returnRoom) : null;
		if(A instanceof AutoGenArea)
			((AutoGenArea)A).resetInstance(back);
	}

	/** the encounter or content in a run's instance completed it */
	public static void completed(final Room R)
	{
		if((R == null) || !enabled())
			return;
		try
		{
			final JSONObject st = state();
			for(final String k : runs(st).keySet())
			{
				final JSONObject run = (JSONObject)runs(st).get(k);
				if(open(run) && R.getArea().Name().equals(run.get("instance")))
				{
					run.put("status", "complete");
					store(st);
					ModuleStats.count(FAMILY, defOfRun(run).id, "completed");
					return;
				}
			}
		}
		catch(final IOException e)
		{
			Log.errOut("Expeditions", "completed: " + e.getMessage());
		}
	}

	/**
	 * The recovery path: the character's run goes on if it can.  Members whose
	 * instance is gone, or who are nowhere, are brought to the return room; a
	 * run whose instance is gone is laid out again from its seed on the next
	 * entry; a run with no member left is closed.
	 */
	public static synchronized String recover(final MOB mob) throws IOException
	{
		require();
		final JSONObject st = state();
		final Map.Entry<String, JSONObject> r = runOf(st, mob);
		if(r == null)
			return "You are not on an expedition.";
		final JSONObject run = r.getValue();
		final Def d = defOfRun(run).value;
		final Room back = (d.returnRoom.length() > 0) ? CMLib.map().getRoom(d.returnRoom) : null;
		final String instance = (String)run.get("instance");
		final boolean gone = (instance.length() > 0) && (CMLib.map().getArea(instance) == null);
		final StringBuilder s = new StringBuilder();
		int members = 0;
		for(final String ref : party(run))
		{
			final Object o = InstanceIds.find(ref);
			if(!(o instanceof MOB))
				continue;
			final MOB M = (MOB)o;
			members++;
			final boolean lost = (M.location() == null) || (gone && (M.location().getArea() == null));
			if(lost && (back != null))
			{
				back.bringMobHere(M, false);
				s.append(M.Name()).append(" is brought back. ");
			}
		}
		if(gone)
		{
			run.put("instance", "");
			run.put("status", "open");
			run.put("again", Boolean.TRUE); // the next entry lays it out again, and says so
			s.append("The way is gone; it will be laid out again from the chart. ");
		}
		if(members == 0)
		{
			run.put("status", "recovered");
			s.append("No one of the party is left; the expedition is closed. ");
		}
		store(st);
		ModuleStats.count(FAMILY, defOfRun(run).id, "recoveries");
		return s.length() == 0 ? "Everyone is where they should be; the expedition goes on." : s.toString().trim();
	}

	/** EXPEDITION STATUS: the character's run in words */
	public static String status(final MOB mob) throws IOException
	{
		require();
		final Map.Entry<String, JSONObject> r = runOf(state(), mob);
		if(r == null)
			return "You are not on an expedition.";
		final JSONObject run = r.getValue();
		final Entry<Def> e = defOfRun(run);
		final StringBuilder s = new StringBuilder("Expedition ").append(r.getKey()).append(": ").append(e.title).append(", ").append(run.get("status"));
		s.append("\nSeed ").append(run.get("seed")).append(", tier ").append(run.get("tier")).append(", attempts left ").append(run.get("left"));
		final List<String> mods = new ArrayList<String>();
		for(final Object o : (Object[])run.get("mods"))
			mods.add(String.valueOf(o));
		s.append("\nModifiers: ").append(mods.isEmpty() ? "none" : String.join(", ", mods));
		final String instance = (String)run.get("instance");
		s.append("\nInstance: ").append(instance.isEmpty() ? "not entered yet" : (instance + ((CMLib.map().getArea(instance) == null) ? " (gone: EXPEDITION RECOVER)" : "")));
		final List<String> names = new ArrayList<String>();
		for(final String ref : party(run))
		{
			final Object o = InstanceIds.find(ref);
			names.add((o instanceof MOB) ? ((MOB)o).Name() : "(missing)");
		}
		s.append("\nParty: ").append(String.join(", ", names));
		return s.toString();
	}

	/** a run's record, for Q05 inspection and Q09 replay */
	public static JSONObject runRecord(final long n) throws IOException
	{
		final Object o = runs(state()).get(Long.toString(n));
		return (o instanceof JSONObject) ? (JSONObject)o : null;
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
			return "EXPEDITIONS";
		}

		@Override
		public String folder()
		{
			return FOLDER;
		}

		@Override
		public List<String> scopes()
		{
			return Arrays.asList("world");
		}

		@Override
		public List<String> stringState()
		{
			return Arrays.asList("runs");
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
			Expeditions.reset();
		}

		@Override
		public List<String> check()
		{
			final Library<Def> lib = load();
			final List<String> out = new ArrayList<String>(lib.errors);
			final Library<RewardEscrow.Def> rewards = RewardEscrow.load();
			for(final Entry<Def> e : lib.entries.values())
			{
				final Def d = e.value;
				final Area A = CMLib.map().getArea(d.area);
				if(A == null)
					out.add(e.path + ": /destination/area: no area " + d.area + " in this world");
				else
				if(!(A instanceof AutoGenArea))
					out.add(e.path + ": /destination/area: " + d.area + " is not a generated area (StdAutoGenInstance)");
				if(CMLib.map().getRoom(d.returnRoom) == null)
					out.add(e.path + ": /return_room: no room " + d.returnRoom);
				for(int i = 0; i < d.modifiers.size(); i++)
				{
					final Modifier m = d.modifiers.get(i);
					if(!m.effectId.isEmpty() && (CMClass.getAbility(m.effectId) == null))
						out.add(e.path + ": /modifiers/" + i + "/mob_effect/id: no ability or property " + m.effectId);
				}
				if(!d.activity.isEmpty() && !rewards.has(d.activity))
					out.add(e.path + ": /activity: no activity " + d.activity + " is installed in rewards/");
			}
			return out;
		}

		@Override
		public String list() throws IOException
		{
			final Library<Def> lib = load();
			if(lib.entries.isEmpty())
				return "No expeditions are installed in " + FOLDER + "/." + (lib.errors.isEmpty() ? "" : "\n" + String.join("\n", lib.errors));
			final StringBuilder s = new StringBuilder("Expeditions:");
			for(final Entry<Def> e : lib.entries.values())
				s.append("\n  ").append(e.key()).append(" (").append(e.title).append("): to ").append(e.value.area).append(e.value.gated ? " (gated)" : "")
				 .append(", ").append(e.value.modifiers.size()).append(" modifiers, offers ").append(e.value.offer).append(", choose ").append(e.value.choose)
				 .append(", ").append(e.value.attempts).append(" attempts");
			if(!lib.errors.isEmpty())
				s.append("\n").append(String.join("\n", lib.errors));
			return s.toString();
		}

		@Override
		public String reload() throws IOException
		{
			synchronized(Expeditions.class)
			{
				library = null;
			}
			return definitions().report();
		}

		@Override
		public String world(final MOB mob, final List<String> words) throws IOException
		{
			if((words.size() == 1) && words.get(0).equalsIgnoreCase("RUNS"))
			{
				final JSONObject st = state();
				if(runs(st).isEmpty())
					return "No expedition has been opened in this campaign.";
				final StringBuilder s = new StringBuilder("Expedition runs:");
				for(final String k : runs(st).keySet())
				{
					final JSONObject run = (JSONObject)runs(st).get(k);
					s.append("\n  ").append(k).append(": ").append(((JSONObject)run.get("exp")).get("id")).append(", ").append(run.get("status")).append(", seed ")
					 .append(run.get("seed")).append(", attempts left ").append(run.get("left")).append(", instance ").append(run.get("instance"));
				}
				return s.toString();
			}
			// WORLD EXPEDITIONS CHART [id] ([tier]): a builder's test chart, its seed drawn when opened
			if((words.size() >= 2) && words.get(0).equalsIgnoreCase("CHART"))
			{
				final Entry<Def> e = definitions().latest(words.get(1).toLowerCase(Locale.ROOT));
				final Item I = CMClass.getItem("GenItem");
				I.setName("a chart of " + e.title);
				I.setDisplayText("A chart lies here.");
				final Ability A = CMClass.getAbility("Prop_ExpeditionChart");
				A.setMiscText("EXPEDITION=" + e.id + " SEED=0 TIER=" + ((words.size() > 2) ? words.get(2) : Integer.toString(e.value.tierMin)));
				I.addNonUninvokableEffect(A);
				mob.addItem(I);
				return "You have " + I.name() + ".";
			}
			return null;
		}
	};

	static
	{
		ModuleContent.register(FAMILY_ENTRY);
	}
}
