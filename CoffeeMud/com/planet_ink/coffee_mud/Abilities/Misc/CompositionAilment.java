package com.planet_ink.coffee_mud.Abilities.Misc;
import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.CharState;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Common.interfaces.PhyStats;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.interfaces.*;
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
 * A12: a character's ailment meters from composed hits under a combat
 * profile.  Each ailment the profile declares has a meter that landed hits
 * fill and that drains each tick; at the threshold the meter empties and its
 * status holds for a number of ticks (its effects working through this one),
 * then the character is immune to more buildup of it for a while.  A hit
 * that consumes the status ends it early.  The whole state is its text, so a
 * save keeps it.  Taking it off (MPUNAFFECT) cleanses everything.
 */
public class CompositionAilment extends StdAbility implements Ailments.Holder
{
	@Override
	public String ID()
	{
		return "CompositionAilment";
	}

	@Override
	public String name()
	{
		return "Ailments";
	}

	@Override
	public String displayText()
	{
		final List<String> on = new ArrayList<String>();
		for(final Meter m : meters.values())
		{
			if(m.status > 0)
				on.add(m.title);
		}
		return on.isEmpty() ? "" : "(" + String.join(", ", on) + ")";
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_SKILL;
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	/** one ailment's state on the character, with the rule it was built under */
	protected static final class Meter
	{
		String					id		= "";
		String					title	= "";
		long					value	= 0;
		int						threshold	= 100;
		int						decay	= 0;
		int						statusTicks	= 1;
		int						immuneTicks	= 0;
		int						status	= 0;
		int						immune	= 0;
		String					effects	= "";
		final List<Ability>		fx		= new ArrayList<Ability>();

		JSONObject record()
		{
			final JSONObject o = new JSONObject();
			o.put("title", title);
			o.put("value", Long.valueOf(value));
			o.put("threshold", Long.valueOf(threshold));
			o.put("decay", Long.valueOf(decay));
			o.put("status_ticks", Long.valueOf(statusTicks));
			o.put("immune_ticks", Long.valueOf(immuneTicks));
			o.put("status", Long.valueOf(status));
			o.put("immune", Long.valueOf(immune));
			o.put("effects", effects);
			return o;
		}

		void parseEffects()
		{
			fx.clear();
			if(effects.trim().length() > 0)
			{
				for(final CMObject o : CMLib.coffeeMaker().getCodedSpellsOrBehaviors(effects))
				{
					if(o instanceof Ability)
						fx.add((Ability)o);
				}
			}
		}

		String describe()
		{
			if(status > 0)
				return title + " (" + status + " ticks left)";
			if(immune > 0)
				return "immune to " + id + " (" + immune + " ticks)";
			return id + " " + value + "/" + threshold + ((decay > 0) ? " (losing " + decay + " a tick)" : "");
		}
	}

	protected final Map<String, Meter> meters = new TreeMap<String, Meter>();

	@Override
	public void setMiscText(final String text)
	{
		super.setMiscText(text);
		meters.clear();
		try
		{
			final JSONObject data = ModuleDefinitions.parse(text);
			for(final String id : data.keySet())
			{
				final JSONObject o = ModuleDefinitions.object(data.get(id), "ailment " + id);
				final Meter m = new Meter();
				m.id = ModuleDefinitions.id(id);
				m.title = ModuleDefinitions.string(o.get("title"), "title");
				m.value = Math.max(0, ModuleDefinitions.count(o.get("value"), "value"));
				m.threshold = Math.max(1, (int)Math.min(Integer.MAX_VALUE, ModuleDefinitions.count(o.get("threshold"), "threshold")));
				m.decay = Math.max(0, (int)Math.min(Integer.MAX_VALUE, ModuleDefinitions.count(o.get("decay"), "decay")));
				m.statusTicks = Math.max(1, (int)Math.min(Integer.MAX_VALUE, ModuleDefinitions.count(o.get("status_ticks"), "status_ticks")));
				m.immuneTicks = Math.max(0, (int)Math.min(Integer.MAX_VALUE, ModuleDefinitions.count(o.get("immune_ticks"), "immune_ticks")));
				m.status = Math.max(0, (int)Math.min(Integer.MAX_VALUE, ModuleDefinitions.count(o.get("status"), "status")));
				m.immune = Math.max(0, (int)Math.min(Integer.MAX_VALUE, ModuleDefinitions.count(o.get("immune"), "immune")));
				m.effects = ModuleDefinitions.string(o.get("effects"), "effects");
				m.parseEffects();
				meters.put(m.id, m);
			}
		}
		catch(final IOException e)
		{
			meters.clear(); // an unreadable state is dropped, as an expired effect
		}
	}

	@Override
	public String text()
	{
		final JSONObject data = new JSONObject();
		for(final Meter m : meters.values())
			data.put(m.id, m.record());
		return ModuleDefinitions.json(data);
	}

	@Override
	public String accountForYourself()
	{
		final List<String> s = new ArrayList<String>();
		for(final Meter m : meters.values())
			s.add(m.describe());
		return "Ailments: " + String.join("; ", s);
	}

	@Override
	public boolean hasStatus(final String id)
	{
		final Meter m = meters.get(id);
		return (m != null) && (m.status > 0);
	}

	@Override
	public Set<String> statusConditions()
	{
		final Set<String> out = new TreeSet<String>();
		for(final Meter m : meters.values())
		{
			if(m.status > 0)
				out.add("status:" + m.id);
		}
		return out;
	}

	@Override
	public long meter(final String id)
	{
		final Meter m = meters.get(id);
		return (m == null) ? 0 : m.value;
	}

	/**
	 * A landed composed hit's buildup: each ailment the profile declares fills,
	 * unless its status is on or the target is immune; one that reaches its
	 * threshold empties and its status begins.
	 * @return what happened, for the trace
	 */
	@Override
	public List<String> build(final MOB target, final CombatRules.Profile profile, final Map<String, Long> buildup)
	{
		final List<String> lines = new ArrayList<String>();
		final CompositionAilment A = this;
		for(final Map.Entry<String, Long> e : buildup.entrySet())
		{
			final CombatRules.Ailment rule = profile.ailments.get(e.getKey());
			if(rule == null)
			{
				lines.add("Ailment " + e.getKey() + ": the profile has no such ailment; no buildup.");
				continue;
			}
			Meter m = A.meters.get(rule.id);
			if(m == null)
			{
				m = new Meter();
				m.id = rule.id;
				A.meters.put(m.id, m);
			}
			// the rule as the profile has it now
			m.title = rule.title;
			m.threshold = rule.threshold;
			m.decay = rule.decay;
			m.statusTicks = rule.statusTicks;
			m.immuneTicks = rule.immuneTicks;
			if(!m.effects.equals(rule.effects) && (m.status <= 0))
			{
				m.effects = rule.effects;
				m.parseEffects();
			}
			for(final Ability fx : m.fx)
				fx.setAffectedOne(target);
			if(m.status > 0)
			{
				lines.add("Ailment " + m.id + ": already " + m.title + "; no buildup.");
				continue;
			}
			if(m.immune > 0)
			{
				lines.add("Ailment " + m.id + ": immune for " + m.immune + " more ticks; no buildup.");
				continue;
			}
			m.value = Math.min(1000000, m.value + e.getValue().longValue());
			if(m.value >= m.threshold)
			{
				m.value = 0;
				m.status = m.statusTicks;
				final Room R = target.location();
				if(R != null)
					R.show(target, null, CMMsg.MSG_OK_VISUAL, "<S-NAME> <S-IS-ARE> " + m.title.toLowerCase() + "!");
				lines.add("Ailment " + m.id + ": the threshold " + m.threshold + " is reached; " + m.title + " for " + m.statusTicks + " ticks.");
				A.recover(target);
			}
			else
				lines.add("Ailment " + m.id + ": +" + e.getValue() + ", now " + m.value + "/" + m.threshold + ".");
		}
		return lines;
	}

	/**
	 * Ends statuses early (a hit that consumes them): the immunity window starts.
	 * @return what happened, for the trace
	 */
	@Override
	public List<String> consume(final MOB target, final Set<String> ids)
	{
		final List<String> lines = new ArrayList<String>();
		final CompositionAilment A = this;
		for(final String id : ids)
		{
			final Meter m = A.meters.get(id);
			if((m != null) && (m.status > 0))
			{
				A.end(target, m, "consumed");
				lines.add("Ailment " + id + ": " + m.title + " consumed.");
			}
		}
		return lines;
	}

	/**
	 * Cleanses ailments: their meters, statuses and immunity go (all of them
	 * when ids is null).
	 * @return whether anything was cleansed
	 */
	@Override
	public boolean cleanse(final MOB target, final Set<String> ids)
	{
		final CompositionAilment A = this;
		boolean any = false;
		for(final Iterator<Meter> i = A.meters.values().iterator(); i.hasNext();)
		{
			final Meter m = i.next();
			if((ids == null) || ids.contains(m.id))
			{
				i.remove();
				any = true;
			}
		}
		if(any)
			A.recover(target);
		if(A.meters.isEmpty())
			A.unInvoke();
		return any;
	}

	protected void end(final MOB host, final Meter m, final String why)
	{
		m.status = 0;
		m.immune = m.immuneTicks;
		final Room R = host.location();
		if((R != null) && (!why.equals("consumed")))
			R.show(host, null, CMMsg.MSG_OK_VISUAL, "<S-NAME> <S-IS-ARE> no longer " + m.title.toLowerCase() + ".");
		recover(host);
	}

	protected void recover(final MOB host)
	{
		host.recoverCharStats();
		host.recoverPhyStats();
		host.recoverMaxState();
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_MOB)
			return super.tick(ticking, tickID);
		if(!(affected instanceof MOB))
			return super.tick(ticking, tickID);
		final MOB host = (MOB)affected;
		boolean anything = false;
		for(final Meter m : meters.values())
		{
			if(m.status > 0)
			{
				for(final Ability A : m.fx)
					A.tick(ticking, tickID);
				if(--m.status <= 0)
					end(host, m, "ended");
			}
			else
			if(m.immune > 0)
				m.immune--;
			else
			if(m.value > 0)
				m.value = Math.max(0, m.value - m.decay);
			anything = anything || (m.status > 0) || (m.immune > 0) || (m.value > 0);
		}
		if(!anything)
		{
			unInvoke();
			return false;
		}
		return super.tick(ticking, tickID);
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		for(final Meter m : meters.values())
		{
			for(final Ability A : m.fx)
				A.setAffectedOne(P);
		}
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		for(final Meter m : meters.values())
		{
			if(m.status > 0)
				for(final Ability A : m.fx)
					A.affectPhyStats(host, stats);
		}
	}

	@Override
	public void affectCharStats(final MOB host, final CharStats stats)
	{
		super.affectCharStats(host, stats);
		for(final Meter m : meters.values())
		{
			if(m.status > 0)
				for(final Ability A : m.fx)
					A.affectCharStats(host, stats);
		}
	}

	@Override
	public void affectCharState(final MOB host, final CharState state)
	{
		super.affectCharState(host, state);
		for(final Meter m : meters.values())
		{
			if(m.status > 0)
				for(final Ability A : m.fx)
					A.affectCharState(host, state);
		}
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		for(final Meter m : meters.values())
		{
			if(m.status > 0)
				for(final Ability A : m.fx)
					if(!A.okMessage(myHost, msg))
						return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		for(final Meter m : meters.values())
		{
			if(m.status > 0)
				for(final Ability A : m.fx)
					A.executeMsg(myHost, msg);
		}
	}
}
