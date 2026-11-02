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
 * Q3: a character's quest journal, kept by the game (core/QuestJournal) as
 * the text of this hidden effect, so that it is saved with the character:
 * each quest's objectives and the entries written for it.  Not for builders
 * to give.
 */
public class Prop_QuestLog extends Property
{
	@Override
	public String ID()
	{
		return "Prop_QuestLog";
	}

	@Override
	public String name()
	{
		return "A quest journal";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "Keeps a quest journal";
	}
}
