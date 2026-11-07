package com.planet_ink.coffee_mud.Abilities.Misc;
import java.io.IOException;

import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
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
 * A13: life leech from composed hits, recovered over time: what landed hits
 * leeched waits here, and comes back to the character at most the profile's
 * cap_per_tick each tick.  Its text ({"pending":n,"cap":n}) is its state.
 */
public class CompositionLeech extends StdAbility implements Ailments.Leech
{
	@Override
	public String ID()
	{
		return "CompositionLeech";
	}

	@Override
	public String name()
	{
		return "Leeching";
	}

	@Override
	public String displayText()
	{
		return "(Leeching)";
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

	protected long	pending	= 0;
	protected int	cap		= 1;

	@Override
	public void setMiscText(final String text)
	{
		super.setMiscText(text);
		pending = 0;
		cap = 1;
		try
		{
			final JSONObject o = ModuleDefinitions.parse(text);
			pending = ModuleDefinitions.count(o.get("pending"), "pending");
			cap = (int)Math.max(1, Math.min(100000, ModuleDefinitions.count(o.get("cap"), "cap")));
		}
		catch(final IOException e)
		{
			pending = 0;
		}
	}

	@Override
	public String text()
	{
		final JSONObject o = new JSONObject();
		o.put("pending", Long.valueOf(pending));
		o.put("cap", Long.valueOf(cap));
		return ModuleDefinitions.json(o);
	}

	@Override
	public String accountForYourself()
	{
		return "Leeching: " + pending + " hit points to come back, at most " + cap + " a tick";
	}

	@Override
	public long pending()
	{
		return pending;
	}

	@Override
	public void add(final long amount, final int cap)
	{
		pending = Math.min(10000000, pending + Math.max(0, amount));
		this.cap = Math.max(1, cap);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if((tickID == Tickable.TICKID_MOB) && (affected instanceof MOB))
		{
			final MOB host = (MOB)affected;
			if(host.amDead() || (pending <= 0))
			{
				unInvoke();
				return false;
			}
			final int give = (int)Math.min(pending, cap);
			host.curState().adjHitPoints(give, host.maxState());
			pending -= give;
			if(pending <= 0)
			{
				unInvoke();
				return false;
			}
		}
		return super.tick(ticking, tickID);
	}
}
