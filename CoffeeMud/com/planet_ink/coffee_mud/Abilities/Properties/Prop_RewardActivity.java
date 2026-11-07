package com.planet_ink.coffee_mud.Abilities.Properties;

import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.CMLib;
import com.planet_ink.coffee_mud.core.CMParms;
import com.planet_ink.coffee_mud.core.RewardEscrow;
import com.planet_ink.coffee_mud.core.interfaces.Environmental;

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
 * I18: marks an area as the place of a reward-escrow activity (the optional
 * module rewards.escrow): ACTIVITY=[id].  A character with a run of the
 * activity under way who leaves the area gets the activity's on_leave, once
 * the move is over.
 */
public class Prop_RewardActivity extends StdAbility
{
	@Override
	public String ID()
	{
		return "Prop_RewardActivity";
	}

	@Override
	public String name()
	{
		return "Reward activity";
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_PROPERTY;
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_AREAS;
	}

	public String activity()
	{
		return CMParms.getParmStr(text(), "ACTIVITY", text().trim()).trim().toLowerCase();
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((msg.targetMinor() != CMMsg.TYP_LEAVE) || !(msg.target() instanceof Room) || !(affected instanceof Area) || (msg.source() == null))
			return;
		final Area A = (Area)affected;
		if(((Room)msg.target()).getArea() != A)
			return;
		final MOB M = msg.source();
		final String id = activity();
		if((id.length() == 0) || !RewardEscrow.enabled())
			return;
		// once the move is over: out of the area, the run gets the activity's on_leave
		CMLib.threads().executeRunnable(() ->
		{
			if(!M.amDestroyed() && ((M.location() == null) || (M.location().getArea() != A)))
				RewardEscrow.left(M, id);
		});
	}

	@Override
	public String accountForYourself()
	{
		return "The place of the reward activity " + activity();
	}
}
