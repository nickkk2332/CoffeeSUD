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
 * Q2: a mark in room listings for a quest's giver or turn-in, by how the
 * quest stands for the one looking: QUEST=name (or *, every running quest it
 * has a script from, is first quest mob of or gives), AVAILABLE="(!)",
 * ACTIVE="(?)", READY="(?)" and READYFUNC=name, a FUNCTION_PROG of the
 * host's that returns something when the viewer can turn in.  The work is
 * core/QuestMarkers', from Sense.getDispositionBlurbs.
 */
public class Prop_QuestMarker extends Property
{
	@Override
	public String ID()
	{
		return "Prop_QuestMarker";
	}

	@Override
	public String name()
	{
		return "A quest marker";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS|Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Marked for a quest";
	}
}
