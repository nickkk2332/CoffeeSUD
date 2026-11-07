package com.planet_ink.coffee_mud.Abilities.Properties;

import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.core.CMParms;

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
 * G01: makes an item a chart of an expedition (the optional module
 * expeditions.contracts): EXPEDITION=[id] SEED=[n] TIER=[n].  SEED=0 is a
 * chart whose seed is drawn from the campaign's expeditions stream when it
 * is first opened, and written into it.  EXPEDITION PREVIEW and OPEN use it.
 */
public class Prop_ExpeditionChart extends StdAbility
{
	@Override
	public String ID()
	{
		return "Prop_ExpeditionChart";
	}

	@Override
	public String name()
	{
		return "An expedition chart";
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_PROPERTY;
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		final long seed = CMParms.getParmLong(text(), "SEED", 0);
		return "A chart of the expedition " + CMParms.getParmStr(text(), "EXPEDITION", "?") + ((seed > 0) ? ", seed " + seed : ", not yet read")
			+ ", tier " + CMParms.getParmInt(text(), "TIER", 1);
	}
}
