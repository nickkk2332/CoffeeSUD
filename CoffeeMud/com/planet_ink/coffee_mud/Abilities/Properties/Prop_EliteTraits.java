package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import java.io.IOException;
import java.util.*;

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
 * A24: an elite actor's traits (EliteTraits): each one's effects work
 * through this property, LOOK shows each one's line, and a trait that
 * possesses (G08) passes, when its host dies, to a living member of the same
 * pack in the room.  Its text (JSON: pack, traits) is all of its state.
 */
public class Prop_EliteTraits extends Property
{
	@Override
	public String ID()
	{
		return "Prop_EliteTraits";
	}

	@Override
	public String name()
	{
		return "Elite traits";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	protected String					pack	= "";
	protected final List<JSONObject>	traits	= new ArrayList<JSONObject>();
	protected final List<Ability>		effects	= new ArrayList<Ability>();

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		traits.clear();
		effects.clear();
		pack = "";
		try
		{
			final JSONObject data = ModuleDefinitions.parse((newText == null) || (newText.trim().length() == 0) ? "{}" : newText);
			pack = data.containsKey("pack") ? String.valueOf(data.get("pack")) : "";
			if(data.containsKey("traits"))
			{
				for(final Object o : ModuleDefinitions.array(data.get("traits"), "traits"))
					traits.add(ModuleDefinitions.object(o, "trait"));
			}
		}
		catch(final IOException e)
		{
			traits.clear();
		}
		for(final JSONObject t : traits)
		{
			final String spec = String.valueOf(t.get("effects"));
			if(spec.trim().length() == 0)
				continue;
			for(final CMObject o : CMLib.coffeeMaker().getCodedSpellsOrBehaviors(spec))
			{
				if(o instanceof Ability)
				{
					((Ability)o).setAffectedOne(affected);
					effects.add((Ability)o);
				}
			}
		}
	}

	/** @return the IDs of its traits */
	public List<String> traitIDs()
	{
		final List<String> out = new ArrayList<String>();
		for(final JSONObject t : traits)
			out.add(String.valueOf(t.get("id")));
		return out;
	}

	/** @return its pack */
	public String pack()
	{
		return pack;
	}

	/** @return the titles, for names and inspection */
	public String titles()
	{
		final List<String> out = new ArrayList<String>();
		for(final JSONObject t : traits)
			out.add(String.valueOf(t.get("title")));
		return String.join(", ", out);
	}

	@Override
	public String accountForYourself()
	{
		return "Elite: " + titles();
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		for(final Ability A : effects)
			A.setAffectedOne(P);
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		for(final Ability A : effects)
			A.affectPhyStats(host, stats);
	}

	@Override
	public void affectCharStats(final MOB host, final CharStats stats)
	{
		super.affectCharStats(host, stats);
		for(final Ability A : effects)
			A.affectCharStats(host, stats);
	}

	@Override
	public void affectCharState(final MOB host, final CharState state)
	{
		super.affectCharState(host, state);
		for(final Ability A : effects)
			A.affectCharState(host, state);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		for(final Ability A : effects)
			A.tick(ticking, tickID);
		return true;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		for(final Ability A : effects)
		{
			if(!A.okMessage(myHost, msg))
				return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		for(final Ability A : effects)
			A.executeMsg(myHost, msg);
		if(!(affected instanceof MOB))
			return;
		final MOB mob = (MOB)affected;
		if(((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE))
		&& (msg.target() == mob) && (msg.source() != mob) && (!traits.isEmpty()))
		{
			final StringBuilder s = new StringBuilder("^H").append(mob.name(msg.source())).append(" is ").append(titles()).append(".^?");
			for(final JSONObject t : traits)
			{
				final String line = String.valueOf(t.get("line"));
				if(line.length() > 0)
					s.append(" ").append(line);
			}
			msg.addTrailerMsg(CMClass.getMsg(msg.source(), mob, null, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS, s.toString(), CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
		}
		else
		if((msg.sourceMinor() == CMMsg.TYP_DEATH) && (msg.source() == mob))
			possess(mob);
	}

	/** G08: the traits that possess pass to a living member of the pack here */
	protected void possess(final MOB dying)
	{
		final List<String> passing = new ArrayList<String>();
		for(final JSONObject t : traits)
		{
			if(Boolean.TRUE.equals(t.get("possess")))
				passing.add(String.valueOf(t.get("id")));
		}
		final Room R = dying.location();
		if(passing.isEmpty() || (R == null) || (pack.length() == 0))
			return;
		for(int i = 0; i < R.numInhabitants(); i++)
		{
			final MOB M = R.fetchInhabitant(i);
			if((M == null) || (M == dying) || M.amDead() || M.amDestroyed())
				continue;
			final Ability A = M.fetchEffect("Prop_EliteTraits");
			if(!(A instanceof Prop_EliteTraits) || !pack.equals(((Prop_EliteTraits)A).pack()))
				continue;
			final List<String> moving = new ArrayList<String>();
			for(final String id : passing)
				if(!((Prop_EliteTraits)A).traitIDs().contains(id))
					moving.add(id);
			if(moving.isEmpty())
				continue;
			try
			{
				final JSONObject data = ModuleDefinitions.parse(A.text());
				final List<Object> list = new ArrayList<Object>(Arrays.asList(ModuleDefinitions.array(data.get("traits"), "traits")));
				for(final JSONObject t : traits)
					if(moving.contains(String.valueOf(t.get("id"))))
						list.add(t);
				data.put("traits", list.toArray());
				A.setMiscText(ModuleDefinitions.json(data));
				M.recoverCharStats();
				M.recoverPhyStats();
				M.recoverMaxState();
				R.show(M, dying, CMMsg.MSG_OK_VISUAL, CMLib.lang().L("The power of <T-NAME> passes into <S-NAME>!"));
				ModuleStats.count(EncounterDirectors.FAMILY, pack, "possessions");
			}
			catch(final IOException e)
			{
				// a pack member whose traits cannot be read is passed over
				continue;
			}
			return;
		}
	}
}
