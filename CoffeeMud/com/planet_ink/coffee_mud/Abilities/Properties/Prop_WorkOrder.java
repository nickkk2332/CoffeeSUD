package com.planet_ink.coffee_mud.Abilities.Properties;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;

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
 * I04: a work piece of a staged recipe (items.modifiers kind "recipe"). Its
 * text is the recipe's pin and the stage it is at, saved with the piece:
 * {"schema":1,"recipe":{"id":..,"version":..,"hash":..},"stage":0}. MODCRAFT
 * BEGIN makes it; each settled stage moves it on; the last makes the product
 * and takes the piece away. It does nothing else.
 */
public class Prop_WorkOrder extends Property
{
	@Override
	public String ID()
	{
		return "Prop_WorkOrder";
	}

	@Override
	public String name()
	{
		return "Work Order";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		try
		{
			final JSONObject o = ModuleDefinitions.parse(text());
			return "A work piece of " + ((JSONObject)o.get("recipe")).get("id") + ", " + o.get("stage") + " stage(s) done; MODCRAFT STATUS shows the next.";
		}
		catch(final java.io.IOException e)
		{
			return "A work piece (" + e.getMessage() + ")";
		}
	}
}
