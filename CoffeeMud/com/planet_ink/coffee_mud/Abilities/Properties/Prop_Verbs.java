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
 * ST7: verbs for an object without a script.  VERBS="turn,rotate" MSG="You
 * turn the wheel." OTHERS="&lt;S-NAME&gt; turns the wheel."  A command whose
 * first word is one of the verbs, and whose rest names the object (or any
 * rest, on a room), shows the messages instead of the command, skill or
 * social of that word; core.ScriptEvents.verb does it, before the game
 * looks for those.
 */
public class Prop_Verbs extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Verbs";
	}

	@Override
	public String name()
	{
		return "Local verbs for flavour";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS|Ability.CAN_ITEMS|Ability.CAN_ROOMS|Ability.CAN_EXITS|Ability.CAN_AREAS;
	}

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		ScriptEvents.propVerbsSeen();
	}

	@Override
	public String accountForYourself()
	{
		return "Verbs: " + CMParms.getParmStr(text(), "VERBS", "");
	}
}
