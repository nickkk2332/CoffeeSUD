package com.planet_ink.coffee_mud.Abilities.Properties;

import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;

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
 * A03: grants causal triggers (the optional module abilities.triggers) while
 * on a character or on an item they wear or hold: TRIGGERS=id,id (or the IDs
 * alone).  The module reads it; it does nothing by itself.
 */
public class Prop_Triggers extends StdAbility
{
	@Override
	public String ID()
	{
		return "Prop_Triggers";
	}

	@Override
	public String name()
	{
		return "Triggered reactions";
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_PROPERTY;
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Triggered reactions: " + text();
	}
}
