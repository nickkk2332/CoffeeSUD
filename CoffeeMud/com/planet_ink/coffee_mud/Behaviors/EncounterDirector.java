package com.planet_ink.coffee_mud.Behaviors;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.EncounterDirectors.Def;
import com.planet_ink.coffee_mud.core.EncounterDirectors.Phase;
import com.planet_ink.coffee_mud.core.EncounterDirectors.Spawn;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.ModuleContent.Entry;
import com.planet_ink.coffee_mud.core.interfaces.Tickable;

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
 * G03: runs an encounter of the optional module encounters.director in its
 * room: ENCOUNTER=[id].  One state machine, kept in a saved game: the pinned
 * definition, the stage (idle, active, complete, failed), the run, the
 * phase, the actors it spawned or bound (by instance ID, with their role
 * and whether they were defeated or removed), the participants, the scale
 * chosen at the start, the rewards given and the director actions used.  A
 * phase spawns once per run; a reward is offered once per run, through
 * rewards.escrow; a removed actor is not a defeated one; a wipe and a
 * retreat do what the encounter declares.
 */
public class EncounterDirector extends StdBehavior implements EncounterDirectors.Director
{
	@Override
	public String ID()
	{
		return "EncounterDirector";
	}

	@Override
	public String name()
	{
		return "an encounter director";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "directing an encounter";
	}

	/** one actor the director tracks */
	protected static final class Actor
	{
		String	ref;
		String	role;
		String	phase;
		String	state;	// alive, defeated, removed
		String	name;
	}

	protected String			encounter	= "";
	protected JSONObject		pin			= null;
	protected String			stage		= "idle";
	protected int				run			= 0;
	protected int				phase		= -1;
	protected int				ticks		= 0;
	protected int				phaseTick	= 0;
	protected int				scale		= 100;
	protected int				awayTicks	= 0;
	protected int				actionsUsed	= 0;
	protected int				lootUsed	= 0;
	protected final List<String>	participants	= new ArrayList<String>();
	protected final List<Actor>		actors			= new ArrayList<Actor>();
	protected final Set<String>		spawned			= new TreeSet<String>();
	protected final Set<String>		granted			= new TreeSet<String>();
	protected final List<String>	notes			= new ArrayList<String>();
	protected Room					host			= null;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		encounter = CMParms.getParmStr((newParms == null) ? "" : newParms, "ENCOUNTER", (newParms == null) ? "" : newParms.trim()).trim().toLowerCase(Locale.ROOT);
		EncounterDirectors.register(this);
	}

	@Override
	public Room room()
	{
		return host;
	}

	protected void note(final String s)
	{
		notes.add(s);
		while(notes.size() > 12)
			notes.remove(0);
	}

	/** the pinned definition; a new run pins the latest */
	protected Entry<Def> def() throws IOException
	{
		if(pin != null)
			return EncounterDirectors.definitions().pinned(pin);
		return EncounterDirectors.definitions().latest(encounter);
	}

	protected static MOB resolve(final String ref)
	{
		final Object o = InstanceIds.find(ref);
		return (o instanceof MOB) ? (MOB)o : null;
	}

	protected boolean here(final MOB M)
	{
		return (M != null) && !M.amDestroyed() && (M.location() == host);
	}

	protected List<MOB> players()
	{
		final List<MOB> out = new ArrayList<MOB>();
		if(host == null)
			return out;
		for(int i = 0; i < host.numInhabitants(); i++)
		{
			final MOB M = host.fetchInhabitant(i);
			if((M != null) && M.isPlayer() && !M.amDead())
				out.add(M);
		}
		return out;
	}

	protected List<MOB> present()
	{
		final List<MOB> out = new ArrayList<MOB>();
		for(final String ref : participants)
		{
			final MOB M = resolve(ref);
			if(here(M))
				out.add(M);
		}
		return out;
	}

	/** tells the room, as plain text that keeps its meaning without colour */
	protected void tellRoom(final String text)
	{
		if((host != null) && (text.length() > 0))
			host.showHappens(CMMsg.MSG_OK_VISUAL, text);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if(!(ticking instanceof Room))
			return true;
		host = (Room)ticking;
		EncounterDirectors.register(this);
		if(!EncounterDirectors.enabled())
			return true;
		try
		{
			if(stage.equals("idle"))
			{
				if(def().value.start.equals("enter") && !players().isEmpty())
					start();
			}
			else
			if(stage.equals("active"))
				step();
		}
		catch(final IOException e)
		{
			note("stopped: " + e.getMessage());
			ModuleStats.count(EncounterDirectors.FAMILY, encounter, "errors");
		}
		return true;
	}

	/** begins a run: the participants, the scale (chosen once), the bound actors, the first phase */
	public synchronized void start() throws IOException
	{
		if(host == null)
			throw new IOException("the director is not in a room yet");
		if(stage.equals("active"))
			return;
		pin = null;
		final Entry<Def> d = def();
		pin = d.pin();
		run++;
		stage = "active";
		ticks = 0;
		phase = -1;
		awayTicks = 0;
		actionsUsed = 0;
		lootUsed = 0;
		participants.clear();
		actors.clear();
		spawned.clear();
		notes.clear();
		for(final MOB P : players())
			participants.add(InstanceIds.reference(P));
		if(d.value.scaling.equals("party_size"))
			scale = 100 + (d.value.perMember * (Math.min(participants.size(), d.value.maxScale) - 1));
		else
			scale = 100;
		for(final String b : d.value.bind)
		{
			final String role = b.substring(0, b.indexOf('=')).trim();
			final MOB M = host.fetchInhabitant(b.substring(b.indexOf('=') + 1).trim());
			if(M == null)
			{
				note("bind: no " + b.substring(b.indexOf('=') + 1).trim() + " here for " + role);
				continue;
			}
			final Actor a = new Actor();
			a.ref = InstanceIds.reference(M);
			a.role = role;
			a.phase = "";
			a.state = "alive";
			a.name = M.Name();
			actors.add(a);
		}
		ModuleStats.count(EncounterDirectors.FAMILY, encounter, "runs");
		note("run " + run + " began with " + participants.size() + " participant(s), scale " + scale + "%");
		enter(0);
	}

	protected int alive()
	{
		int n = 0;
		for(final Actor a : actors)
		{
			if(a.state.equals("alive") && !a.phase.isEmpty())
				n++;
		}
		return n;
	}

	/** enters a phase: its warning, its director actions and its spawns, each once per run */
	protected void enter(final int i) throws IOException
	{
		final Def d = def().value;
		if(phase >= 0)
			reward(d.phases.get(phase).reward, "phase-" + d.phases.get(phase).id);
		phase = i;
		phaseTick = ticks;
		final Phase p = d.phases.get(i);
		ModuleStats.count(EncounterDirectors.FAMILY, encounter, "phases entered");
		if(p.warning.length() > 0)
			tellRoom("Warning: " + p.warning);
		for(final String line : p.says)
		{
			if(actionsUsed >= d.budgetActions)
			{
				note("action budget " + d.budgetActions + " reached: \"" + line + "\" not done");
				break;
			}
			actionsUsed++;
			tellRoom(line);
		}
		if(!spawned.add(p.id))
			return; // spawned already in this run: never again
		for(int si = 0; si < p.spawns.size(); si++)
		{
			final Spawn s = p.spawns.get(si);
			// A24: a pack's traits, the same for all of it; each actor's own, picked within its budget
			final String packKey = encounter + "#" + run; // the encounter's actors are one pack
			for(int n = 0; n < s.count; n++)
			{
				if(!ModuleStats.reading(EncounterDirectors.FAMILY, encounter, "actors at once", alive() + 1) || (alive() >= d.budgetActors))
				{
					note("actor budget " + d.budgetActors + " reached: " + (s.count - n) + " of " + s.count + " " + s.mob + " not spawned");
					break;
				}
				final MOB proto = CMLib.catalog().getCatalogMob(s.mob);
				if(proto == null)
				{
					note("no catalog mob '" + s.mob + "'");
					break;
				}
				final MOB M = (MOB)proto.copyOf();
				M.bringToLife(host, true);
				if(scale != 100)
				{
					M.baseState().setHitPoints((int)Math.max(1, ((long)M.baseState().getHitPoints() * scale) / 100));
					M.recoverMaxState();
					M.resetToMaxState();
				}
				if(s.traits != null)
				{
					try
					{
						final List<String> mine = new ArrayList<String>(s.traits.pack);
						mine.addAll(EliteTraits.pick(s.traits, s.traits.pack));
						EliteTraits.apply(M, mine, packKey);
						if(!mine.isEmpty())
							note(M.Name() + " has " + mine);
					}
					catch(final IOException e)
					{
						note("traits for " + M.Name() + " not given: " + e.getMessage());
					}
				}
				final Actor a = new Actor();
				a.ref = InstanceIds.reference(M);
				a.role = s.role;
				a.phase = p.id;
				a.state = "alive";
				a.name = M.Name();
				actors.add(a);
				ModuleStats.count(EncounterDirectors.FAMILY, encounter, "actors spawned");
				final List<MOB> foes = present();
				if(!foes.isEmpty())
					CMLib.combat().postAttack(M, foes.get(0), M.fetchWieldedItem());
			}
		}
	}

	/** offers a reward once per run and key, within the loot budget, to each participant here */
	protected void reward(final String reward, final String key) throws IOException
	{
		if(reward.isEmpty())
			return;
		final Def d = def().value;
		final String full = run + ":" + key;
		if(!granted.add(full))
			return;
		if(lootUsed >= d.budgetLoot)
		{
			note("loot budget " + d.budgetLoot + " reached: " + reward + " not given");
			return;
		}
		lootUsed++;
		if(!RewardEscrow.enabled())
		{
			note("rewards.escrow is not active: " + reward + " not given");
			return;
		}
		for(final MOB P : present())
			RewardEscrow.offer(P, d.activity, reward, "encounter:" + encounter + "#" + run + ":" + key);
		ModuleStats.count(EncounterDirectors.FAMILY, encounter, "rewards offered");
	}

	protected boolean allResolved(final String phaseId)
	{
		boolean any = false;
		for(final Actor a : actors)
		{
			if(!a.phase.equals(phaseId))
				continue;
			any = true;
			if(a.state.equals("alive"))
				return false;
		}
		return any;
	}

	/** checks each actor: one gone without a recorded death was removed, not defeated */
	protected void refresh()
	{
		for(final Actor a : actors)
		{
			if(!a.state.equals("alive"))
				continue;
			final MOB M = resolve(a.ref);
			if((M == null) || M.amDestroyed() || M.amDead() || (M.location() != host))
			{
				a.state = "removed";
				note(a.name + " (" + a.role + ") was removed, not defeated");
				ModuleStats.count(EncounterDirectors.FAMILY, encounter, "actors removed");
			}
		}
	}

	protected void step() throws IOException
	{
		final Def d = def().value;
		ticks++;
		// a player who comes in joins the run; the scale chosen at the start stays
		for(final MOB P : players())
		{
			final String ref = InstanceIds.reference(P);
			if(!participants.contains(ref))
				participants.add(ref);
		}
		refresh();
		final List<MOB> here = present();
		// a wipe: everyone who took part is dead or down
		boolean anyUp = false;
		for(final String ref : participants)
		{
			final MOB M = resolve(ref);
			if((M != null) && !M.amDead() && !M.amDestroyed() && !Downed.isDown(M))
				anyUp = true;
		}
		if(!participants.isEmpty() && !anyUp)
		{
			ModuleStats.count(EncounterDirectors.FAMILY, encounter, "wipes");
			finish(d.onWipe, "wipe");
			return;
		}
		// a retreat: no one who takes part is here
		if(here.isEmpty())
		{
			if(++awayTicks >= d.retreatTicks)
			{
				ModuleStats.count(EncounterDirectors.FAMILY, encounter, "retreats");
				if(d.onRetreat.equals("pause"))
					return;
				finish(d.onRetreat, "retreat");
			}
			return;
		}
		awayTicks = 0;
		// a protected role defeated fails the encounter
		if(d.protect.length() > 0)
		{
			for(final Actor a : actors)
			{
				if(a.role.equals(d.protect) && a.state.equals("defeated"))
				{
					tellRoom(a.name + " has fallen.");
					finish("fail", "the " + d.protect + " fell");
					return;
				}
			}
		}
		if(complete(d))
		{
			stage = "complete";
			final Phase last = d.phases.get(phase);
			reward(last.reward, "phase-" + last.id);
			reward(d.reward, "complete");
			if(d.bank && RewardEscrow.enabled())
			{
				for(final MOB P : present())
					RewardEscrow.bank(P, d.activity);
			}
			tellRoom("The encounter is over.");
			Expeditions.completed(host); // G01: an encounter in an expedition's instance completes it
			note("run " + run + " complete");
			ModuleStats.count(EncounterDirectors.FAMILY, encounter, "completions");
			hook("DIRECTOR_COMPLETE");
			return;
		}
		if(phase + 1 < d.phases.size())
		{
			final Phase next = d.phases.get(phase + 1);
			if(begins(next))
				enter(phase + 1);
		}
	}

	protected boolean complete(final Def d)
	{
		if(d.complete.equals("survive"))
			return ticks >= d.surviveTicks;
		if(d.complete.equals("cleared"))
			return (phase == d.phases.size() - 1) && allResolved(d.phases.get(phase).id);
		boolean any = false;
		for(final Actor a : actors)
		{
			if(!a.role.equals(d.completeRole))
				continue;
			any = true;
			if(!a.state.equals("defeated"))
				return false;
		}
		return any;
	}

	protected boolean begins(final Phase next) throws IOException
	{
		if(next.enter.equals("after"))
			return ticks - phaseTick >= next.ticks;
		if(next.enter.equals("cleared"))
			return allResolved(def().value.phases.get(phase).id);
		if(next.enter.equals("hp_below"))
		{
			for(final Actor a : actors)
			{
				if(!a.role.equals(next.role) || !a.state.equals("alive"))
					continue;
				final MOB M = resolve(a.ref);
				if((M != null) && (M.maxState().getHitPoints() > 0)
				&& ((100L * M.curState().getHitPoints()) < ((long)next.percent * M.maxState().getHitPoints())))
					return true;
			}
		}
		return false;
	}

	/** a wipe, retreat or failure: reset (the spawned leave; a new run may begin) or fail */
	protected void finish(final String policy, final String why) throws IOException
	{
		if(policy.equals("fail"))
		{
			stage = "failed";
			note("run " + run + " failed: " + why);
			final Def d = def().value;
			if(!d.activity.isEmpty() && RewardEscrow.enabled())
			{
				for(final String ref : participants)
				{
					final MOB M = resolve(ref);
					if(M != null)
						RewardEscrow.end(M, d.activity, "fail");
				}
			}
			tellRoom("The encounter is lost.");
			hook("DIRECTOR_FAILED");
			return;
		}
		resetEncounter(why);
	}

	/**
	 * G06: the scripts here (the room, what is in it, its area) hear the end
	 * of a run: SIGNAL_PROG DIRECTOR_COMPLETE or DIRECTOR_FAILED, with the
	 * encounter's ID and the run as its arguments ($g), a participant as $n.
	 */
	protected void hook(final String signal)
	{
		if(host == null)
			return;
		final List<MOB> here = present();
		final MOB actor = here.isEmpty() ? (host.numInhabitants() > 0 ? host.fetchInhabitant(0) : null) : here.get(0);
		if(actor != null)
			ScriptEvents.signal(ScriptEvents.hosts(host), signal, encounter + " " + run, actor, host, 0);
	}

	/** ends the run: the actors it spawned leave, and the encounter waits for a new one */
	public synchronized void resetEncounter(final String why)
	{
		for(final Actor a : actors)
		{
			if(a.phase.isEmpty())
				continue;
			final MOB M = resolve(a.ref);
			if((M != null) && !M.amDestroyed() && !M.amDead())
				M.destroy();
		}
		actors.clear();
		participants.clear();
		spawned.clear();
		stage = "idle";
		phase = -1;
		awayTicks = 0;
		pin = null;
		note("run " + run + " reset: " + why);
		ModuleStats.count(EncounterDirectors.FAMILY, encounter, "resets");
	}

	@Override
	public synchronized boolean died(final MOB victim)
	{
		final String ref = InstanceIds.reference(victim);
		for(final Actor a : actors)
		{
			if(a.ref.equals(ref) && a.state.equals("alive"))
			{
				a.state = "defeated";
				ModuleStats.count(EncounterDirectors.FAMILY, encounter, "actors defeated");
				return true;
			}
		}
		return false;
	}

	/** the current phase's warning, objective and actors, in plain text (Q07) */
	@Override
	public synchronized String describe(final MOB viewer, final boolean creator)
	{
		final StringBuilder s = new StringBuilder();
		try
		{
			final Entry<Def> e = def();
			final Def d = e.value;
			s.append("Encounter: ").append(e.title).append(" (").append(stage).append(")");
			if((phase >= 0) && (phase < d.phases.size()))
			{
				final Phase p = d.phases.get(phase);
				s.append("\nPhase ").append(phase + 1).append(" of ").append(d.phases.size()).append(": ").append(p.title);
				if(p.warning.length() > 0)
					s.append("\nWarning: ").append(p.warning);
			}
			s.append("\nObjective: ");
			if(d.complete.equals("survive"))
				s.append("survive ").append(Math.max(0, d.surviveTicks - ticks)).append(" more ticks");
			else
			if(d.complete.equals("cleared"))
				s.append("defeat every foe of the last phase");
			else
				s.append("defeat the ").append(d.completeRole);
			if(d.protect.length() > 0)
				s.append(", and keep the ").append(d.protect).append(" alive");
			for(final Actor a : actors)
			{
				s.append("\n  ").append(a.name).append(" (").append(a.role).append("): ").append(a.state);
				final MOB M = resolve(a.ref);
				if(a.state.equals("alive") && (M != null) && (M.maxState().getHitPoints() > 0))
					s.append(", ").append((100L * M.curState().getHitPoints()) / M.maxState().getHitPoints()).append("% health");
			}
			if(creator)
			{
				s.append("\nDefinition ").append(e.key()).append(", run ").append(run).append(", tick ").append(ticks).append(", scale ").append(scale)
				 .append("%, participants ").append(participants.size()).append(", budgets: actors ").append(alive()).append(" of ").append(d.budgetActors)
				 .append(", actions ").append(actionsUsed).append(" of ").append(d.budgetActions).append(", rewards ").append(lootUsed).append(" of ").append(d.budgetLoot);
				for(final String n : notes)
					s.append("\n  note: ").append(n);
			}
		}
		catch(final IOException ex)
		{
			s.append("Encounter ").append(encounter).append(": unavailable (").append(ex.getMessage()).append(")");
		}
		return s.toString();
	}

	@Override
	protected String getRunState()
	{
		if(stage.equals("idle") && (run == 0))
			return "";
		final JSONObject o = new JSONObject();
		if(pin != null)
			o.put("pin", pin);
		o.put("stage", stage);
		o.put("run", Long.valueOf(run));
		o.put("phase", Long.valueOf(phase));
		o.put("ticks", Long.valueOf(ticks));
		o.put("phase_tick", Long.valueOf(phaseTick));
		o.put("scale", Long.valueOf(scale));
		o.put("away", Long.valueOf(awayTicks));
		o.put("actions", Long.valueOf(actionsUsed));
		o.put("loot", Long.valueOf(lootUsed));
		o.put("participants", participants.toArray(new String[0]));
		final List<JSONObject> as = new ArrayList<JSONObject>();
		for(final Actor a : actors)
		{
			final JSONObject j = new JSONObject();
			j.put("ref", a.ref);
			j.put("role", a.role);
			j.put("phase", a.phase);
			j.put("state", a.state);
			j.put("name", a.name);
			as.add(j);
		}
		o.put("actors", as.toArray(new JSONObject[0]));
		o.put("spawned", spawned.toArray(new String[0]));
		o.put("granted", granted.toArray(new String[0]));
		return "<DIRECTOR>" + CMLib.xml().parseOutAngleBrackets(ModuleDefinitions.json(o)) + "</DIRECTOR>";
	}

	@Override
	protected void setRunState(final String xml)
	{
		if((xml == null) || (xml.trim().length() == 0))
			return;
		try
		{
			final List<XMLLibrary.XMLTag> V = CMLib.xml().parseAllXML(xml);
			if((V == null) || V.isEmpty())
				return;
			final JSONObject o = ModuleDefinitions.parse(CMLib.xml().restoreAngleBrackets(V.get(0).value()));
			pin = (o.get("pin") instanceof JSONObject) ? (JSONObject)o.get("pin") : null;
			stage = (String)o.get("stage");
			run = ((Number)o.get("run")).intValue();
			phase = ((Number)o.get("phase")).intValue();
			ticks = ((Number)o.get("ticks")).intValue();
			phaseTick = ((Number)o.get("phase_tick")).intValue();
			scale = ((Number)o.get("scale")).intValue();
			awayTicks = ((Number)o.get("away")).intValue();
			actionsUsed = ((Number)o.get("actions")).intValue();
			lootUsed = ((Number)o.get("loot")).intValue();
			participants.clear();
			for(final Object p : (Object[])o.get("participants"))
				participants.add(String.valueOf(p));
			actors.clear();
			for(final Object raw : (Object[])o.get("actors"))
			{
				final JSONObject j = (JSONObject)raw;
				final Actor a = new Actor();
				a.ref = (String)j.get("ref");
				a.role = (String)j.get("role");
				a.phase = (String)j.get("phase");
				a.state = (String)j.get("state");
				a.name = (String)j.get("name");
				actors.add(a);
			}
			spawned.clear();
			for(final Object p : (Object[])o.get("spawned"))
				spawned.add(String.valueOf(p));
			granted.clear();
			for(final Object p : (Object[])o.get("granted"))
				granted.add(String.valueOf(p));
		}
		catch(final Exception e)
		{
			Log.errOut("EncounterDirector", "the saved state of " + encounter + " could not be read: " + e.getMessage());
		}
	}

	@Override
	protected String debugState()
	{
		return super.debugState() + "; " + encounter + " " + stage + ", run " + run + ", phase " + (phase + 1) + ", tick " + ticks;
	}
}
