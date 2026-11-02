package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;

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
 * JS9: a behaviour written in JavaScript: SCRIPT=world:js/behaviors/x.js
 * and parameters, as Prop_JScript (see JSHooks).  A behaviour is never asked
 * for stat changes, so the file may define only okMessage(host, msg),
 * executeMsg(host, msg), tick(host, tickID), onAdded(host) and
 * onRemoved(host); Prop_JScript has the stat hooks.  What a hook changes in
 * parms is written back to the behaviour's parameters.
 */
public class JSBehavior extends StdBehavior implements JSReload.Holder
{
	@Override
	public String ID()
	{
		return "JSBehavior";
	}

	@Override
	protected int canImproveCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS | Ability.CAN_EXITS | Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "scripted behaviour";
	}

	protected JSHooks		hooks	= null;
	protected PhysicalAgent	host	= null;

	/** @return its file's hooks, read from its parameters */
	public JSHooks hooks()
	{
		if(hooks == null)
			hooks = new JSHooks(ID());
		hooks.load(getParms(), host);
		return hooks;
	}

	@Override
	public void setParms(final String parameters)
	{
		super.setParms(parameters);
		hooks = null;
	}

	@Override
	public String scriptPath()
	{
		return hooks().path();
	}

	@Override
	public void reloadScript()
	{
		hooks().reload(getParms(), host);
		keepParms();
	}

	/** writes parms back to the parameters, as a hook left them */
	protected void keepParms()
	{
		if(hooks != null)
			parms = hooks.textNow(parms);
	}

	@Override
	public void startBehavior(final PhysicalAgent forMe)
	{
		super.startBehavior(forMe);
		host = forMe;
		hooks = null;
		hooks().call("onAdded", forMe);
		keepParms();
	}

	@Override
	public void endBehavior(final PhysicalAgent forMe)
	{
		if(hooks != null)
		{
			hooks.call("onRemoved", forMe);
			keepParms();
		}
		super.endBehavior(forMe);
	}

	/** N8: its area woke: the file's catchUp(host, ticks), if it has one */
	@Override
	public void catchUp(final PhysicalAgent host, final long ticks)
	{
		final JSHooks H = hooks();
		if(H.has("catchUp"))
		{
			H.call("catchUp", host, Long.valueOf(ticks));
			keepParms();
		}
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		final JSHooks H = hooks();
		if(!H.has("okMessage"))
			return true;
		final Object r = H.call("okMessage", myHost, msg);
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
			H.call("executeMsg", myHost, msg);
			keepParms();
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((host == null) && (ticking instanceof PhysicalAgent))
			host = (PhysicalAgent)ticking;
		final JSHooks H = hooks();
		if(H.has("tick"))
		{
			H.call("tick", ticking, Integer.valueOf(tickID));
			keepParms();
		}
		return true;
	}
}
