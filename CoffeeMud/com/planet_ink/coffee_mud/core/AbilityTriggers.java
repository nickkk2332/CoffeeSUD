package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
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
 * A03: causal triggers, the optional module abilities.triggers.  A trigger,
 * granted by Prop_Triggers on a character or what they wear or hold, reacts
 * to an event (a block of a composed hit, a kill) with one action: a
 * composed skill, a gain of a named resource, healing, or a summoned helper
 * that belongs to its owner.  Every reaction carries the causal root of the
 * event that began it: a root's reactions share one action budget and a
 * depth limit, and a trigger runs at most once in a root, so effects that
 * trigger each other stop.  Reactions run after the action that caused them
 * (at the end of a composed cast, or on the game thread right after).
 * Cooldowns and the keys of committed events are saved, so a restored
 * campaign cannot run a committed trigger again.
 * Definitions: world/triggers/[id].v[N].json.
 */
public final class AbilityTriggers
{
	private AbilityTriggers()
	{
	}

	public static final String		MODULE	= "abilities.triggers";
	public static final String		FOLDER	= "triggers";
	public static final String		FAMILY	= "triggers";
	public static final String[]	EVENTS	= { "block", "kill" };
	/** the most committed event keys kept per character */
	public static final int			RECENT	= 32;

	/** one trigger definition */
	public static final class Trigger
	{
		public final String				on;
		public final int				priority;
		public final int				chance;
		public final long				cooldown;
		public final String				cooldownClock;
		public final Map<String, Long>	cost;
		public final boolean			includeSummons;
		public final String				action;
		public final String				skill;
		public final String				target;
		public final String				pool;
		public final long				amount;
		public final String				mob;
		public final int				ticks;

		Trigger(final Node n) throws IOException
		{
			n.allow("schema", "id", "version", "title", "on", "priority", "chance", "cooldown", "cost", "include_summons", "action");
			on = n.choice("on", "", EVENTS);
			priority = (int)n.integer("priority", 50, 0, 100);
			chance = (int)n.integer("chance", 100, 1, 100);
			final Node cd = n.object("cooldown");
			if(cd == null)
			{
				cooldown = 0;
				cooldownClock = "ticks";
			}
			else
			{
				cd.allow("amount", "clock");
				cooldown = cd.integer("amount", 1, 1000000);
				cooldownClock = cd.choice("clock", "ticks", NamedResources.CLOCKS);
			}
			cost = n.amounts("cost", 1, 1000000, 8);
			includeSummons = n.bool("include_summons", false);
			if(includeSummons && !on.equals("kill"))
				throw n.error("include_summons", "is for kill triggers");
			final Node a = n.required("action");
			action = a.choice("type", "", "cast", "gain", "heal", "summon", "set", "drain");
			if(action.equals("cast"))
			{
				a.allow("type", "skill", "target");
				skill = a.id("skill");
				target = a.choice("target", "other", "other", "self", "enemy");
				if(on.equals("kill") && target.equals("other"))
					throw a.error("target", "a kill's other is the dead; use enemy or self");
			}
			else
			{
				skill = "";
				target = "self";
			}
			if(action.equals("gain") || action.equals("drain"))
			{
				a.allow("type", "pool", "amount");
				pool = a.id("pool");
				amount = a.integer("amount", 1, 1000000);
			}
			else
			if(action.equals("set"))
			{
				// A20: set a pool to a value (a combo back to 0, a charge filled)
				a.allow("type", "pool", "amount");
				pool = a.id("pool");
				amount = a.integer("amount", 0, 1000000);
			}
			else
			if(action.equals("heal"))
			{
				a.allow("type", "amount");
				pool = "";
				amount = a.integer("amount", 1, 1000000);
			}
			else
			{
				pool = "";
				amount = 0;
			}
			if(action.equals("summon"))
			{
				a.allow("type", "mob", "ticks");
				mob = a.string("mob");
				if(mob.trim().length() == 0)
					throw a.error("mob", "required");
				ticks = (int)a.integer("ticks", 30, 1, 100000);
			}
			else
			{
				mob = "";
				ticks = 0;
			}
		}

		/** the action in words */
		public String actionText()
		{
			if(action.equals("cast"))
				return "uses " + skill + " on " + (target.equals("other") ? (on.equals("block") ? "the attacker" : "them") : target.equals("enemy") ? "their enemy" : "themselves");
			if(action.equals("gain"))
				return "gains " + amount + " " + pool;
			if(action.equals("set"))
				return "sets " + pool + " to " + amount;
			if(action.equals("drain"))
				return "loses up to " + amount + " " + pool;
			if(action.equals("heal"))
				return "heals " + amount;
			return "summons " + mob + " for " + ticks + " ticks";
		}
	}

	private static Library<Trigger>	library	= null;

	private static final ModuleContent.Reader<Trigger> READER = new ModuleContent.Reader<Trigger>()
	{
		@Override
		public Trigger read(final Node node) throws IOException
		{
			return new Trigger(node);
		}
	};

	public static Library<Trigger> load()
	{
		return ModuleContent.load(ModuleContent.world(), FOLDER, "trigger", READER);
	}

	public static synchronized Library<Trigger> definitions()
	{
		if(library == null)
			library = load();
		return library;
	}

	public static synchronized void reset()
	{
		library = null;
		queue.clear();
		draining = false;
		drainQueued = false;
		castDepth = 0;
		rootSerial = 0;
	}

	public static boolean enabled()
	{
		return OptionalModules.enabled(MODULE);
	}

	private static int setting(final String key, final int fallback, final int min, final int max)
	{
		try
		{
			final Object v = OptionalModules.setting(MODULE, key);
			if(v instanceof Number)
				return Math.max(min, Math.min(max, ((Number)v).intValue()));
		}
		catch(final IOException e)
		{
			// the module's own default
		}
		return fallback;
	}

	/** the actions one causal root may take */
	public static int actionBudget()
	{
		return setting("action_budget", 8, 1, 64);
	}

	/** how deep reactions to reactions may go */
	public static int maxDepth()
	{
		return setting("max_depth", 3, 1, 8);
	}

	/** the trigger IDs a character has, from Prop_Triggers on them and on what they wear or hold */
	public static Set<String> grantedIDs(final MOB mob)
	{
		final Set<String> out = new TreeSet<String>();
		if(mob == null)
			return out;
		for(final Enumeration<Ability> e = mob.effects(); e.hasMoreElements();)
			grantsOf(e.nextElement(), out);
		for(int i = 0; i < mob.numItems(); i++)
		{
			final Item I = mob.getItem(i);
			if((I == null) || I.amWearingAt(Item.IN_INVENTORY))
				continue;
			for(final Enumeration<Ability> e = I.effects(); e.hasMoreElements();)
				grantsOf(e.nextElement(), out);
		}
		return out;
	}

	private static void grantsOf(final Ability A, final Set<String> out)
	{
		if((A == null) || !A.ID().equals("Prop_Triggers"))
			return;
		for(final String part : CMParms.parseCommas(CMParms.getParmStr(A.text(), "TRIGGERS", A.text()).replace(' ', ','), true))
		{
			final String id = part.trim().toLowerCase(Locale.ROOT);
			if(id.matches("[a-z][a-z0-9_.-]{0,95}"))
				out.add(id);
		}
	}

	/** a causal root's shared budget, and what has run in it */
	private static final class Root
	{
		final long			id;
		int					left;
		final Set<String>	consumed	= new HashSet<String>();

		Root(final long id, final int budget)
		{
			this.id = id;
			this.left = budget;
		}
	}

	/** where in a root a reaction is */
	private static final class Cause
	{
		final Root	root;
		final int	depth;

		Cause(final Root root, final int depth)
		{
			this.root = root;
			this.depth = depth;
		}
	}

	/** a reaction waiting to run */
	private static final class Pending
	{
		final Cause			cause;
		final MOB			owner;
		final Entry<Trigger>	trigger;
		final MOB			other;
		final String		key;

		Pending(final Cause cause, final MOB owner, final Entry<Trigger> trigger, final MOB other, final String key)
		{
			this.cause = cause;
			this.owner = owner;
			this.trigger = trigger;
			this.other = other;
			this.key = key;
		}
	}

	private static final Deque<Pending>			queue		= new ArrayDeque<Pending>();
	private static final ThreadLocal<Cause>		current		= new ThreadLocal<Cause>();
	private static boolean						draining	= false;
	private static boolean						drainQueued	= false;
	private static int							castDepth	= 0;
	private static long							rootSerial	= 0;
	/** the reactions of the last root, for inspection: id, depth, owner, trigger, outcome */
	private static final List<String>			lastRoot	= new ArrayList<String>();

	/** a composed cast begins; its reactions wait for it to end */
	public static synchronized void enterCast()
	{
		castDepth++;
	}

	/** a composed cast ends; at the outermost, its reactions run */
	public static void leaveCast()
	{
		synchronized(AbilityTriggers.class)
		{
			castDepth = Math.max(0, castDepth - 1);
			if(castDepth > 0)
				return;
		}
		drain();
	}

	/** A03 event: a composed hit was blocked by its target */
	public static void blocked(final MOB blocker, final MOB attacker, final String skill)
	{
		fire("block", blocker, attacker, null, false);
	}

	/** A03 event: a character died; their killer's triggers, and a summoned killer's owner's */
	public static void died(final MOB victim, final MOB killer)
	{
		if((victim == null) || (killer == null) || (killer == victim) || !enabled())
			return;
		final String key = "kill:" + InstanceIds.id(victim);
		fire("kill", killer, victim, key, false);
		final Ability summoned = killer.fetchEffect("Prop_Summoned");
		final MOB owner = (summoned == null) ? null : summoned.invoker();
		if((owner != null) && (owner != killer) && (owner != victim))
			fire("kill", owner, victim, key, true);
	}

	/**
	 * An event for one owner: their triggers for it wait in the queue, in
	 * the event's causal root (a new one, or the one of the reaction now
	 * running, a level deeper).
	 */
	static void fire(final String on, final MOB owner, final MOB other, final String key, final boolean viaSummon)
	{
		if((owner == null) || !enabled())
			return;
		final Library<Trigger> lib = definitions();
		final List<Entry<Trigger>> mine = new ArrayList<Entry<Trigger>>();
		for(final String id : grantedIDs(owner))
		{
			try
			{
				final Entry<Trigger> t = lib.latest(id);
				if(t.value.on.equals(on) && (!viaSummon || t.value.includeSummons))
					mine.add(t);
			}
			catch(final IOException e)
			{
				ModuleStats.count(FAMILY, id, "granted but not installed");
			}
		}
		if(mine.isEmpty())
			return;
		Collections.sort(mine, (a, b) -> (a.value.priority != b.value.priority) ? (b.value.priority - a.value.priority) : a.id.compareTo(b.id));
		synchronized(AbilityTriggers.class)
		{
			final Cause parent = current.get();
			final Cause cause;
			if(parent == null)
			{
				cause = new Cause(new Root(++rootSerial, actionBudget()), 0);
				lastRoot.clear();
				ModuleStats.count(FAMILY, on, "roots");
			}
			else
				cause = new Cause(parent.root, parent.depth + 1);
			for(final Entry<Trigger> t : mine)
				queue.add(new Pending(cause, owner, t, other, (key == null) ? null : (key + ":" + t.id)));
		}
		schedule();
	}

	/** has the queue run after what is happening now */
	private static void schedule()
	{
		synchronized(AbilityTriggers.class)
		{
			if((castDepth > 0) || draining || drainQueued)
				return;
			if(com.planet_ink.coffee_mud.core.threads.ServiceEngine.hasGameThread())
			{
				drainQueued = true;
				CMLib.threads().executeRunnable(() ->
				{
					synchronized(AbilityTriggers.class)
					{
						drainQueued = false;
					}
					drain();
				});
				return;
			}
		}
		drain();
	}

	/** runs the waiting reactions, and those they cause, in order */
	public static void drain()
	{
		synchronized(AbilityTriggers.class)
		{
			if(draining)
				return;
			draining = true;
		}
		try
		{
			while(true)
			{
				final Pending p;
				synchronized(AbilityTriggers.class)
				{
					p = queue.poll();
				}
				if(p == null)
					break;
				run(p);
			}
		}
		finally
		{
			synchronized(AbilityTriggers.class)
			{
				draining = false;
			}
		}
	}

	private static void note(final Pending p, final String outcome)
	{
		synchronized(lastRoot)
		{
			if(lastRoot.size() < 64)
				lastRoot.add("root " + p.cause.root.id + " depth " + p.cause.depth + ": " + p.owner.Name() + " / " + p.trigger.id + " (" + p.trigger.value.on + "): " + outcome);
		}
	}

	private static JSONObject state(final MOB mob) throws IOException
	{
		return OptionalModules.get(MODULE, OptionalModules.character(mob));
	}

	private static JSONObject field(final JSONObject rec, final String name) throws IOException
	{
		final String raw = (String)rec.get(name);
		return ((raw == null) || raw.isEmpty()) ? new JSONObject() : ModuleDefinitions.parse(raw);
	}

	private static List<String> recent(final JSONObject rec) throws IOException
	{
		final List<String> out = new ArrayList<String>();
		final JSONObject r = field(rec, "recent");
		if(r.get("keys") instanceof Object[])
		{
			for(final Object o : (Object[])r.get("keys"))
				out.add(String.valueOf(o));
		}
		return out;
	}

	/** whether a trigger is cooling down for a character, as the reading it is ready at, or -1 */
	public static long readyAt(final MOB mob, final String trigger) throws IOException
	{
		final JSONObject cds = field(state(mob), "cooldowns");
		if(!(cds.get(trigger) instanceof JSONObject))
			return -1;
		final JSONObject cd = (JSONObject)cds.get(trigger);
		final long ready = ((Number)cd.get("ready")).longValue();
		return (NamedResources.reading((String)cd.get("clock")) >= ready) ? -1 : ready;
	}

	private static void run(final Pending p)
	{
		final Entry<Trigger> t = p.trigger;
		final MOB owner = p.owner;
		try
		{
			if(!enabled() || owner.amDead() || owner.amDestroyed() || (owner.location() == null))
			{
				note(p, "owner unavailable; not run");
				return;
			}
			if(p.cause.depth >= maxDepth())
			{
				ModuleStats.count(FAMILY, t.id, "refused: depth");
				note(p, "refused: deeper than " + maxDepth());
				return;
			}
			final String consumedKey = InstanceIds.id(owner) + "/" + t.id;
			if(p.cause.root.consumed.contains(consumedKey))
			{
				ModuleStats.count(FAMILY, t.id, "refused: already run in this root");
				note(p, "refused: already ran in this root");
				return;
			}
			if(p.cause.root.left <= 0)
			{
				ModuleStats.count(FAMILY, t.id, "refused: budget");
				note(p, "refused: the root's action budget is spent");
				return;
			}
			final JSONObject rec = state(owner);
			final List<String> seen = recent(rec);
			if((p.key != null) && seen.contains(p.key))
			{
				ModuleStats.count(FAMILY, t.id, "duplicate events suppressed");
				note(p, "refused: this event was already committed");
				return;
			}
			if(readyAt(owner, t.id) >= 0)
			{
				ModuleStats.count(FAMILY, t.id, "refused: cooldown");
				note(p, "refused: cooling down");
				return;
			}
			if(!t.value.cost.isEmpty())
			{
				if(!NamedResources.enabled() || (NamedResources.unaffordable(owner, t.value.cost).length() > 0))
				{
					ModuleStats.count(FAMILY, t.id, "refused: cost");
					note(p, "refused: cannot pay " + t.value.cost);
					return;
				}
			}
			if((t.value.chance < 100) && (RandomStreams.percent(RandomStreams.TRIGGERS) > t.value.chance))
			{
				p.cause.root.consumed.add(consumedKey);
				note(p, "chance " + t.value.chance + "% missed");
				return;
			}
			// commit: the cost, the cooldown, the event key, the budget; then the action
			if(!t.value.cost.isEmpty())
				NamedResources.spend(owner, t.value.cost, t.id);
			final JSONObject fresh = state(owner);
			if(t.value.cooldown > 0)
			{
				final JSONObject cds = field(fresh, "cooldowns");
				final JSONObject cd = new JSONObject();
				cd.put("clock", t.value.cooldownClock);
				cd.put("ready", Long.valueOf(NamedResources.reading(t.value.cooldownClock) + t.value.cooldown));
				cds.put(t.id, cd);
				fresh.put("cooldowns", ModuleDefinitions.json(cds));
			}
			if(p.key != null)
			{
				final List<String> keys = recent(fresh);
				keys.add(p.key);
				while(keys.size() > RECENT)
					keys.remove(0);
				final JSONObject r = new JSONObject();
				r.put("keys", keys.toArray(new String[0]));
				fresh.put("recent", ModuleDefinitions.json(r));
			}
			OptionalModules.put(MODULE, OptionalModules.character(owner), fresh);
			p.cause.root.consumed.add(consumedKey);
			p.cause.root.left--;
			ModuleStats.count(FAMILY, t.id, "actions");
			ModuleStats.reading(FAMILY, t.id, "actions in one root", actionBudget() - p.cause.root.left); // Q12
			ModuleStats.reading(FAMILY, t.id, "depth", p.cause.depth);
			Provenance.event(owner, MODULE, p.trigger.key(), t.value.actionText() + " (on " + t.value.on + ")"); // Q05
			final Cause before = current.get();
			current.set(p.cause);
			try
			{
				note(p, act(p));
			}
			finally
			{
				if(before == null)
					current.remove();
				else
					current.set(before);
			}
		}
		catch(final IOException e)
		{
			ModuleStats.count(FAMILY, t.id, "failed");
			note(p, "failed: " + e.getMessage());
		}
	}

	private static String act(final Pending p) throws IOException
	{
		final Trigger t = p.trigger.value;
		final MOB owner = p.owner;
		final Room R = owner.location();
		if(t.action.equals("gain"))
		{
			final long n = NamedResources.gain(owner, t.pool, t.amount, p.trigger.id);
			return "gained " + n + " " + t.pool;
		}
		if(t.action.equals("set"))
		{
			final long n = NamedResources.set(owner, t.pool, t.amount);
			return "set " + t.pool + " to " + n;
		}
		if(t.action.equals("drain"))
		{
			// A20: loses what it can, up to the amount; a character without the pool loses nothing
			final NamedResources.Held h = NamedResources.held(owner, t.pool);
			final long n = (h == null) ? 0 : Math.min(h.available(), t.amount);
			if(n > 0)
				NamedResources.spend(owner, t.pool, n, p.trigger.id);
			return "drained " + n + " " + t.pool;
		}
		if(t.action.equals("heal"))
		{
			final int before = owner.curState().getHitPoints();
			owner.curState().adjHitPoints((int)Math.min(Integer.MAX_VALUE, t.amount), owner.maxState());
			R.show(owner, null, CMMsg.MSG_OK_VISUAL, "<S-NAME> recover(s) (" + p.trigger.title + ").");
			return "healed " + (owner.curState().getHitPoints() - before);
		}
		if(t.action.equals("summon"))
		{
			final MOB proto = CMLib.catalog().getCatalogMob(t.mob);
			if(proto == null)
				throw new IOException("no catalog mob '" + t.mob + "'");
			final MOB M = (MOB)proto.copyOf();
			M.basePhyStats().setDisposition(M.basePhyStats().disposition() | com.planet_ink.coffee_mud.Common.interfaces.PhyStats.IS_UNSAVABLE);
			M.recoverPhyStats();
			M.bringToLife(R, true);
			// the summon is the owner's: it follows them, and its Prop_Summoned names them (after it comes to life, which resets a copy's effects)
			final Ability S = CMClass.getAbility("Prop_Summoned");
			S.setMiscText("ID=trigger:" + p.trigger.id + " TICKS=" + t.ticks + " FOLLOW BIND");
			S.setInvoker(owner);
			M.addNonUninvokableEffect(S);
			M.setFollowing(owner);
			R.show(M, null, CMMsg.MSG_OK_VISUAL, "<S-NAME> appear(s) at " + owner.name() + "'s side.");
			ModuleStats.count(FAMILY, p.trigger.id, "summons");
			return "summoned " + M.Name();
		}
		// a composed skill, at the other, the owner's enemy or the owner
		final MOB target = t.target.equals("self") ? owner : t.target.equals("enemy") ? owner.getVictim() : p.other;
		if((target == null) || target.amDead() || (target.location() != R))
			return "no target for " + t.skill;
		final Ability compose = owner.fetchAbility("Skill_Compose");
		if(compose == null)
			throw new IOException("the owner cannot compose skills");
		final boolean ok = compose.invoke(owner, CMParms.parse("CAST " + t.skill), target, true, 0);
		return (ok ? "used " : "could not use ") + t.skill + " on " + target.Name();
	}

	/** the reactions of the last causal root, for COMBATRESULT TRIGGERS and tests */
	public static List<String> lastRoot()
	{
		synchronized(lastRoot)
		{
			return new ArrayList<String>(lastRoot);
		}
	}

	/** TRIGGERS: a character's triggers and their state, in words */
	public static String describe(final MOB mob) throws IOException
	{
		if(!enabled())
			throw new IOException("causal triggers (" + MODULE + ") are not active in this campaign");
		final Set<String> ids = grantedIDs(mob);
		if(ids.isEmpty())
			return "You have no triggered reactions.";
		final StringBuilder s = new StringBuilder("Your triggered reactions:");
		for(final String id : ids)
		{
			try
			{
				final Entry<Trigger> t = definitions().latest(id);
				s.append("\n  ").append(t.title).append(" (").append(id).append("): on ").append(t.value.on).append(", ").append(t.value.actionText());
				if(t.value.chance < 100)
					s.append(", ").append(t.value.chance).append("% chance");
				if(!t.value.cost.isEmpty())
					s.append(", costs ").append(t.value.cost);
				if(t.value.cooldown > 0)
				{
					final long ready = readyAt(mob, id);
					s.append(", cooldown ").append(t.value.cooldown).append(" ").append(t.value.cooldownClock)
					 .append((ready >= 0) ? (" (ready at " + t.value.cooldownClock + " " + ready + ")") : " (ready)");
				}
			}
			catch(final IOException e)
			{
				s.append("\n  ").append(id).append(": unavailable (").append(e.getMessage()).append(")");
			}
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
			return "TRIGGERS";
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
			return Arrays.asList("cooldowns", "recent");
		}

		@Override
		public List<String> requires()
		{
			return new ArrayList<String>();
		}

		@Override
		public Map<String, long[]> integerSettings()
		{
			final Map<String, long[]> m = new TreeMap<String, long[]>();
			m.put("action_budget", new long[] { 1, 64 });
			m.put("max_depth", new long[] { 1, 8 });
			return m;
		}

		@Override
		public void reset()
		{
			AbilityTriggers.reset();
		}

		@Override
		public List<String> check()
		{
			final Library<Trigger> lib = load();
			final List<String> out = new ArrayList<String>(lib.errors);
			final Set<String> skills = new TreeSet<String>();
			for(final CompositionDefinitions.Definition d : CompositionDefinitions.load(ModuleContent.world()).definitions.values())
			{
				if(d.kind.equals("skill"))
					skills.add(d.id);
			}
			final Library<NamedResources.Pool> pools = NamedResources.load();
			for(final Entry<Trigger> e : lib.entries.values())
			{
				final Trigger t = e.value;
				if(t.action.equals("cast") && !skills.contains(t.skill))
					out.add(e.path + ": /action/skill: no composed skill " + t.skill + " is installed in compositions/");
				if((t.action.equals("gain") || t.action.equals("set") || t.action.equals("drain")) && !pools.has(t.pool))
					out.add(e.path + ": /action/pool: no pool " + t.pool + " is installed in pools/");
				for(final String p : t.cost.keySet())
				{
					if(!pools.has(p))
						out.add(e.path + ": /cost/" + p + ": no pool " + p + " is installed in pools/");
				}
				if(t.action.equals("summon") && (CMLib.catalog() != null) && (CMLib.catalog().getCatalogMob(t.mob) == null))
					out.add(e.path + ": /action/mob: no catalog mob '" + t.mob + "'");
			}
			return out;
		}

		@Override
		public String list() throws IOException
		{
			final Library<Trigger> lib = load();
			if(lib.entries.isEmpty())
				return "No triggers are installed in " + FOLDER + "/." + (lib.errors.isEmpty() ? "" : "\n" + String.join("\n", lib.errors));
			final StringBuilder s = new StringBuilder("Triggers (action budget " + actionBudget() + " per root, depth " + maxDepth() + "):");
			for(final Entry<Trigger> e : lib.entries.values())
				s.append("\n  ").append(e.key()).append(" (").append(e.title).append("): on ").append(e.value.on).append(", priority ")
				 .append(e.value.priority).append(", ").append(e.value.actionText());
			if(!lib.errors.isEmpty())
				s.append("\n").append(String.join("\n", lib.errors));
			return s.toString();
		}

		@Override
		public String reload() throws IOException
		{
			synchronized(AbilityTriggers.class)
			{
				library = null;
			}
			return definitions().report();
		}

		@Override
		public String world(final MOB mob, final List<String> words) throws IOException
		{
			if((words.size() == 1) && words.get(0).equalsIgnoreCase("LAST"))
			{
				final List<String> last = lastRoot();
				return last.isEmpty() ? "No triggered reaction has run in this session." : ("The last causal root:\n  " + String.join("\n  ", last));
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
				return describe(who).replace("Your triggered", who.Name() + "'s triggered").replace("You have", who.Name() + " has");
			}
			return null;
		}
	};

	static
	{
		ModuleContent.register(FAMILY_ENTRY);
		ModuleStats.limit(FAMILY, "actions in one root", 64); // Q12: the most action_budget allows
		ModuleStats.limit(FAMILY, "depth", 8);
	}
}
