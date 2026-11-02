package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * JS9: a property whose stat changes and message handling are written in
 * JavaScript: SCRIPT=world:js/props/bloodblade.js KILLS=0 (see JSHooks).
 * The file may define affectPhyStats(host, affected, stats),
 * affectCharStats(host, mob, stats), affectCharState(host, mob, state),
 * okMessage(host, msg) (false refuses the message), executeMsg(host, msg),
 * tick(host, tickID), onAdded(host) and onRemoved(host).  What a hook changes
 * in parms is written back to this property's text, and so saved with the
 * host.
 */
public class Prop_JScript extends Property
{
	@Override
	public String ID()
	{
		return "Prop_JScript";
	}

	@Override
	public String name()
	{
		return "a JavaScript effect";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS | Ability.CAN_EXITS | Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Scripted (" + hooks().path() + ")";
	}

	protected JSHooks	hooks	= null;

	/** @return its file's hooks, read from its text */
	public JSHooks hooks()
	{
		if(hooks == null)
			hooks = new JSHooks(ID());
		hooks.load(text(), (affected instanceof PhysicalAgent) ? (PhysicalAgent)affected : null);
		return hooks;
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		hooks = null;
	}

	/** writes parms back to the text, as a hook left them */
	protected void keepParms()
	{
		if(hooks != null)
			miscText = hooks.textNow(miscText);
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		final Physical was = affected;
		super.setAffectedOne(P);
		if((P != null) && (P != was))
		{
			hooks = null;
			hooks().call("onAdded", P);
			keepParms();
		}
		else
		if((P == null) && (was != null) && (hooks != null))
		{
			hooks.call("onRemoved", was);
			keepParms();
		}
	}

	@Override
	public void affectPhyStats(final Physical affected, final PhyStats affectableStats)
	{
		super.affectPhyStats(affected, affectableStats);
		final JSHooks H = hooks();
		if(H.has("affectPhyStats"))
			H.call("affectPhyStats", this.affected, affected, affectableStats);
	}

	@Override
	public void affectCharStats(final MOB affectedMob, final CharStats affectableStats)
	{
		super.affectCharStats(affectedMob, affectableStats);
		final JSHooks H = hooks();
		if(H.has("affectCharStats"))
			H.call("affectCharStats", this.affected, affectedMob, affectableStats);
	}

	@Override
	public void affectCharState(final MOB affectedMob, final CharState affectableMaxState)
	{
		super.affectCharState(affectedMob, affectableMaxState);
		final JSHooks H = hooks();
		if(H.has("affectCharState"))
			H.call("affectCharState", this.affected, affectedMob, affectableMaxState);
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		final JSHooks H = hooks();
		if(!H.has("okMessage"))
			return true;
		final Object r = H.call("okMessage", affected, msg);
		keepParms();
		return !Boolean.FALSE.equals(r);
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		final JSHooks H = hooks();
		if(H.has("executeMsg"))
		{
			H.call("executeMsg", affected, msg);
			keepParms();
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		final JSHooks H = hooks();
		if(H.has("tick"))
		{
			final Object r = H.call("tick", affected, Integer.valueOf(tickID));
			keepParms();
			return !Boolean.FALSE.equals(r);
		}
		return true;
	}
}
