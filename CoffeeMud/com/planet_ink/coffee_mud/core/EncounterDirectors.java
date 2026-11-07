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

		Spawn(final Node n) throws IOException
		{
			n.allow("mob", "count", "role");
			mob = n.string("mob");
			if(mob.trim().length() == 0)
				throw n.error("mob", "required");
			count = (int)n.integer("count", 1, 1, 20);
			role = n.string("role", "foe", 40);
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

	/** one encounter */
	public static final class Def
	{
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
				"retreat_ticks", "activity", "reward", "bank");
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
	}
}
