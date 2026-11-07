package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.*;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * G03: the encounter director, the optional module encounters.director:
 * the definitions (world/directors/[id].v[N].json) of encounters built of
 * phases, each with how it begins, a warning, what it spawns and what it
 * gives; budgets for actors, director actions and rewards (Q08); a scaling
 * rule chosen once; and what a wipe and a retreat do.  The behavior
 * EncounterDirector runs one in a room, as one saved state machine.
 */
public final class EncounterDirectors
{
	private EncounterDirectors()
	{
	}

	public static final String		MODULE	= "encounters.director";
	public static final String		FOLDER	= "directors";
	public static final String		FAMILY	= "encounters";
	public static final String[]	ENTERS	= { "start", "hp_below", "cleared", "after" };

	/** what one phase spawns */
	public static final class Spawn
	{
		public final String	mob;
		public final int	count;
		public final String	role;
		/** A24: the traits its actors get, or null */
		public final EliteTraits.Rules traits;

		Spawn(final Node n) throws IOException
		{
			n.allow("mob", "count", "role", "traits");
			mob = n.string("mob");
			if(mob.trim().length() == 0)
				throw n.error("mob", "required");
			count = (int)n.integer("count", 1, 1, 20);
			role = n.string("role", "foe", 40);
			final Node t = n.object("traits");
			traits = (t == null) ? null : new EliteTraits.Rules(t);
		}
	}

	/** one phase */
	public static final class Phase
	{
		public final String			id;
		public final String			title;
		public final String			enter;
		public final String			role;
		public final int			percent;
		public final int			ticks;
		public final String			warning;
		public final List<Spawn>	spawns	= new ArrayList<Spawn>();
		public final List<String>	says	= new ArrayList<String>();
		public final String			reward;

		Phase(final Node n, final boolean first) throws IOException
		{
			n.allow("id", "title", "enter", "warning", "spawn", "say", "reward");
			id = n.id("id");
			title = n.string("title", id, 200);
			final Node e = n.required("enter");
			enter = e.choice("on", "", ENTERS);
			if(first != enter.equals("start"))
				throw e.error("on", first ? "the first phase enters on start" : "only the first phase enters on start");
			if(enter.equals("hp_below"))
			{
				e.allow("on", "role", "percent");
				role = e.string("role", "boss", 40);
				percent = (int)e.integer("percent", 1, 99);
			}
			else
			{
				role = "";
				percent = 0;
			}
			if(enter.equals("after"))
			{
				e.allow("on", "ticks");
				ticks = (int)e.integer("ticks", 1, 100000);
			}
			else
			{
				ticks = 0;
				if(!enter.equals("hp_below"))
					e.allow("on");
			}
			warning = n.string("warning", "", 400);
			for(final Node s : n.objects("spawn", 10))
				spawns.add(new Spawn(s));
			says.addAll(n.strings("say", 4, false));
			reward = n.has("reward") ? n.id("reward") : "";
		}
	}

	/** G04: a modifier a trial offers between rounds (G08: a device that changes the rewards) */
	public static final class Modifier
	{
		public final String			id;
		public final String			title;
		public final int			hp;
		public final List<String>	traits;
		public final String			reward;

		Modifier(final Node n) throws IOException
		{
			n.allow("id", "title", "hp", "traits", "reward");
			id = n.id("id");
			title = n.string("title", id, 80);
			hp = (int)n.integer("hp", 0, 0, 500);
			traits = Collections.unmodifiableList(n.strings("traits", 4, true));
			reward = n.has("reward") ? n.id("reward") : "";
		}
	}

	/** one encounter */
	public static final class Def
	{
		/** G04: an escalating trial: offers between rounds, or none */
		public final List<Modifier>	modifiers	= new ArrayList<Modifier>();
		public final int			offers;
		public final List<Phase>	phases	= new ArrayList<Phase>();
		public final String			start;
		public final String			complete;
		public final String			completeRole;
		public final int			surviveTicks;
		public final String			protect;
		public final List<String>	bind	= new ArrayList<String>();
		public final String			scaling;
		public final int			perMember;
		public final int			maxScale;
		public final int			budgetActors;
		public final int			budgetActions;
		public final int			budgetLoot;
		public final String			onWipe;
		public final String			onRetreat;
		public final int			retreatTicks;
		public final String			activity;
		public final String			reward;
		public final boolean		bank;

		Def(final Node n) throws IOException
		{
			n.allow("schema", "id", "version", "title", "kind", "start", "phases", "complete", "protect", "bind", "scaling", "budget", "on_wipe", "on_retreat",
				"retreat_ticks", "activity", "reward", "bank", "trial");
			final Node tr = n.object("trial");
			if(tr == null)
				offers = 0;
			else
			{
				tr.allow("offers", "modifiers");
				offers = (int)tr.integer("offers", 3, 1, 5);
				final Set<String> mids = new HashSet<String>();
				for(final Node m : tr.objects("modifiers", 12))
				{
					final Modifier mod = new Modifier(m);
					if(!mids.add(mod.id))
						throw m.error("id", "duplicate modifier " + mod.id);
					modifiers.add(mod);
				}
				if(modifiers.isEmpty())
					throw tr.error("modifiers", "a trial needs at least one modifier");
			}
			n.choice("kind", "boss", "boss", "waves", "event");
			start = n.choice("start", "enter", "enter", "script");
			final List<Node> ps = n.objects("phases", 8);
			if(ps.isEmpty())
				throw n.error("phases", "at least one phase");
			final Set<String> ids = new HashSet<String>();
			for(int i = 0; i < ps.size(); i++)
			{
				final Phase p = new Phase(ps.get(i), i == 0);
				if(!ids.add(p.id))
					throw ps.get(i).error("id", "duplicate phase " + p.id);
				phases.add(p);
			}
			final Node c = n.required("complete");
			complete = c.choice("when", "", "defeated", "cleared", "survive");
			if(complete.equals("defeated"))
			{
				c.allow("when", "role");
				completeRole = c.string("role", "boss", 40);
				surviveTicks = 0;
			}
			else
			if(complete.equals("survive"))
			{
				c.allow("when", "ticks");
				completeRole = "";
				surviveTicks = (int)c.integer("ticks", 1, 100000);
			}
			else
			{
				c.allow("when");
				completeRole = "";
				surviveTicks = 0;
			}
			protect = n.string("protect", "", 40);
			bind.addAll(n.strings("bind", 4, false));
			if((protect.length() > 0) && bind.isEmpty())
				throw n.error("protect", "names a role to protect: bind the mobs in the room to it (\"bind\": [\"keeper=the old keeper\"])");
			for(final String b : bind)
			{
				if(b.indexOf('=') <= 0)
					throw n.error("bind", "each is role=mob name");
			}
			final Node sc = n.object("scaling");
			if(sc == null)
			{
				scaling = "none";
				perMember = 0;
				maxScale = 1;
			}
			else
			{
				sc.allow("rule", "per_member", "max_members");
				scaling = sc.choice("rule", "none", "none", "party_size");
				perMember = (int)sc.integer("per_member", 25, 0, 200);
				maxScale = (int)sc.integer("max_members", 4, 1, 10);
			}
			final Node b = n.required("budget");
			final Node budget = b;
			b.allow("actors", "actions", "loot");
			budgetActors = (int)b.integer("actors", 1, 50);
			budgetActions = (int)b.integer("actions", 0, 0, 100);
			budgetLoot = (int)b.integer("loot", 0, 0, 20);
			onWipe = n.choice("on_wipe", "reset", "reset", "fail");
			onRetreat = n.choice("on_retreat", "reset", "reset", "pause", "fail");
			retreatTicks = (int)n.integer("retreat_ticks", 10, 1, 10000);
			activity = n.has("activity") ? n.id("activity") : "";
			reward = n.has("reward") ? n.id("reward") : "";
			bank = n.bool("bank", false);
			if(bank && activity.isEmpty())
				throw n.error("bank", "banks the rewards of an activity: name it");
			// Q08: a composition that cannot fit its budgets is refused at load
			int says = 0, rewards = reward.isEmpty() ? 0 : 1;
			for(final Phase p : phases)
			{
				says += p.says.size();
				if(!p.reward.isEmpty())
					rewards++;
				int at = 0;
				for(final Spawn s : p.spawns)
					at += s.count;
				if(at > budgetActors)
					throw budget.error("actors", "phase " + p.id + " spawns " + at + ", more than the budget of " + budgetActors + " actors at once");
			}
			if(says > budgetActions)
				throw budget.error("actions", "the phases declare " + says + " director actions, more than the budget of " + budgetActions);
			if(rewards > budgetLoot)
				throw budget.error("loot", "the encounter gives " + rewards + " rewards, more than the budget of " + budgetLoot);
			if(complete.equals("defeated"))
			{
				boolean spawned = false;
				for(final Phase p : phases)
					for(final Spawn s : p.spawns)
						spawned |= s.role.equals(completeRole);
				if(!spawned)
					throw c.error("role", "no phase spawns the role " + completeRole);
			}
			if((rewards > 0) && activity.isEmpty())
				throw n.error("activity", "rewards go through an activity of rewards.escrow: name it");
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
		return ModuleContent.load(ModuleContent.world(), FOLDER, "encounter", READER);
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
		EliteTraits.reset();
		synchronized(directors)
		{
			directors.clear();
		}
	}

	public static boolean enabled()
	{
		return OptionalModules.enabled(MODULE);
	}

	/** what the registry calls on a director: a death it may own */
	public interface Director
	{
		boolean died(MOB victim);

		Room room();

		String describe(MOB viewer, boolean creator);

		/** G04: TRIALS, TRIALS CHOOSE n, TRIALS STOP; @return what to tell them */
		String trial(MOB mob, String what);
	}

	/** the directors running, for the death hook and inspection */
	private static final List<WeakReference<Director>> directors = new ArrayList<WeakReference<Director>>();

	public static void register(final Director d)
	{
		synchronized(directors)
		{
			for(final Iterator<WeakReference<Director>> i = directors.iterator(); i.hasNext();)
			{
				final Director x = i.next().get();
				if(x == null)
					i.remove();
				else
				if(x == d)
					return;
			}
			directors.add(new WeakReference<Director>(d));
		}
	}

	public static List<Director> running()
	{
		final List<Director> out = new ArrayList<Director>();
		synchronized(directors)
		{
			for(final WeakReference<Director> w : directors)
			{
				final Director d = w.get();
				if((d != null) && onRoom(d))
					out.add(d);
			}
		}
		return out;
	}

	/** whether a director is still on its room: one taken off (or replaced) counts nothing */
	private static boolean onRoom(final Director d)
	{
		final Room R = d.room();
		if((R == null) || R.amDestroyed())
			return false;
		for(final Enumeration<Behavior> e = R.behaviors(); e.hasMoreElements();)
		{
			if(e.nextElement() == d)
				return true;
		}
		return false;
	}

	/** a character died: the director that spawned or bound them counts it */
	public static void died(final MOB victim)
	{
		if((victim == null) || !enabled())
			return;
		for(final Director d : running())
		{
			if(d.died(victim))
				return;
		}
	}

	/** the director in a room, or null */
	public static Director in(final Room R)
	{
		if(R == null)
			return null;
		for(final Enumeration<Behavior> e = R.behaviors(); e.hasMoreElements();)
		{
			final Behavior B = e.nextElement();
			if(B instanceof Director)
				return (Director)B;
		}
		return null;
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
			return "DIRECTORS";
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
			return new ArrayList<String>();
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
			EncounterDirectors.reset();
		}

		@Override
		public List<String> check()
		{
			final Library<Def> lib = load();
			final List<String> out = new ArrayList<String>(lib.errors);
			EliteTraits.reset();
			out.addAll(EliteTraits.definitions().errors); // A24
			final Library<RewardEscrow.Def> rewards = RewardEscrow.load();
			for(final Entry<Def> e : lib.entries.values())
			{
				final Def d = e.value;
				for(int i = 0; i < d.phases.size(); i++)
				{
					final Phase p = d.phases.get(i);
					for(int j = 0; j < p.spawns.size(); j++)
					{
						if((CMLib.catalog() != null) && (CMLib.catalog().getCatalogMob(p.spawns.get(j).mob) == null))
							out.add(e.path + ": /phases/" + i + "/spawn/" + j + "/mob: no catalog mob '" + p.spawns.get(j).mob + "'");
						if(p.spawns.get(j).traits != null)
							out.addAll(EliteTraits.check(e.path + ": /phases/" + i + "/spawn/" + j, p.spawns.get(j).traits));
					}
					if(!p.reward.isEmpty() && !rewards.has(p.reward))
						out.add(e.path + ": /phases/" + i + "/reward: no reward " + p.reward + " is installed in rewards/");
					if(p.enter.equals("hp_below"))
					{
						boolean has = false;
						for(int k = 0; k < i; k++)
							for(final Spawn s : d.phases.get(k).spawns)
								has |= s.role.equals(p.role);
						if(!has)
							out.add(e.path + ": /phases/" + i + "/enter/role: no earlier phase spawns the role " + p.role);
					}
				}
				if(!d.reward.isEmpty() && !rewards.has(d.reward))
					out.add(e.path + ": /reward: no reward " + d.reward + " is installed in rewards/");
				for(int m = 0; m < d.modifiers.size(); m++)
				{
					final Modifier mod = d.modifiers.get(m);
					if(!mod.reward.isEmpty() && !rewards.has(mod.reward))
						out.add(e.path + ": /trial/modifiers/" + m + "/reward: no reward " + mod.reward + " is installed in rewards/");
					for(final String t : mod.traits)
						if(!EliteTraits.definitions().has(t))
							out.add(e.path + ": /trial/modifiers/" + m + "/traits: no trait " + t + " is installed in traits/");
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
				return "No encounters are installed in " + FOLDER + "/." + (lib.errors.isEmpty() ? "" : "\n" + String.join("\n", lib.errors));
			final StringBuilder s = new StringBuilder("Encounters:");
			for(final Entry<Def> e : lib.entries.values())
			{
				s.append("\n  ").append(e.key()).append(" (").append(e.title).append("): ").append(e.value.phases.size()).append(" phases, complete when ")
				 .append(e.value.complete).append(", budget ").append(e.value.budgetActors).append(" actors / ").append(e.value.budgetActions).append(" actions / ")
				 .append(e.value.budgetLoot).append(" rewards");
			}
			for(final Director d : running())
				s.append("\n  running in ").append(CMLib.map().getExtendedRoomID(d.room()));
			if(!lib.errors.isEmpty())
				s.append("\n").append(String.join("\n", lib.errors));
			return s.toString();
		}

		@Override
		public String reload() throws IOException
		{
			synchronized(EncounterDirectors.class)
			{
				library = null;
			}
			return definitions().report();
		}

		@Override
		public String world(final MOB mob, final List<String> words) throws IOException
		{
			if((words.size() == 1) && words.get(0).equalsIgnoreCase("SHOW"))
			{
				final Director d = in(mob.location());
				return (d == null) ? "No encounter director is in this room." : d.describe(mob, true);
			}
			return null;
		}
	};

	static
	{
		ModuleContent.register(FAMILY_ENTRY);
		ModuleStats.limit(FAMILY, "actors at once", 50); // Q12: the most a budget allows
	}
}
