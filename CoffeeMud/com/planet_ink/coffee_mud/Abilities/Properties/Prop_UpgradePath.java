package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
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
 * I27: an item's own way up, for an ItemUpgrader: TO= (a catalog item) or
 * ADJ= ("dam+2 att+5"), COST=, MATERIALS=, LEVEL=, KEEP=, MSG=, as the
 * upgrader's rows (but no FROM: it is this item).
 */
public class Prop_UpgradePath extends Property
{
	@Override
	public String ID()
	{
		return "Prop_UpgradePath";
	}

	@Override
	public String name()
	{
		return "A way up";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		final String to = CMParms.getParmStr(CMParms.parseExactParms(text()), "TO", "");
		return (to.length() > 0) ? ("Can be upgraded to " + (to.toLowerCase().startsWith("catalog:") ? to.substring(8) : to)) : "Can be upgraded";
	}
}
