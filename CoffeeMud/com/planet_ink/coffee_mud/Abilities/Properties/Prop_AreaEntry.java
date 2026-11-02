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
 * ST16 (with PR4): text on an area's border, for a player who comes in from
 * another area (or into the world) or leaves it.  ENTER="..." each time,
 * FIRST="..." the first time, LEAVE="..." on leaving, TITLE="..." (or
 * SHOWNAME, the area's name) and TEXT="..." as a banner, DESC for the area's
 * description, ONCE for the banner only the first time, MASK="..." for whom,
 * COLOR="^H" for the title.  core.ScriptEvents.moved shows it.
 */
public class Prop_AreaEntry extends Property
{
	@Override
	public String ID()
	{
		return "Prop_AreaEntry";
	}

	@Override
	public String name()
	{
		return "Text on an area's border";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Text on coming into the area or leaving it";
	}
}
