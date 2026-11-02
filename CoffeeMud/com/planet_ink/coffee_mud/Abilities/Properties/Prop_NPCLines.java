package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;

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
 * N12: an NPC's own words for the stock lines it says, as a shopkeeper,
 * banker, postman or hireling: NOTBUYING="Bah! I've no use for that
 * junk."|"Take that rubbish elsewhere." STOLEN="I know where that came
 * from, $n."  On an area, the whole town's.  See core/NpcLines.
 */
public class Prop_NPCLines extends Property
{
	@Override
	public String ID()
	{
		return "Prop_NPCLines";
	}

	@Override
	public String name()
	{
		return "An NPC's own words";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Says its stock lines in its own words";
	}
}
