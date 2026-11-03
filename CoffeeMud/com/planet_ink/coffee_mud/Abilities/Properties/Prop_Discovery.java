package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

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
 * P11: a discovery: XP=[n or n%] ON=ENTER|LOOK|READ|OPEN|GET|FIND|TALK
 * ONCE=CHARACTER|PARTY|PROFILE MESSAGE="..." TATTOO=[name] MASK="..."
 * JOURNAL="...".  The first time the event happens for a character (or their
 * party, or profile) it pays the experience, tagged DISCOVERY:, and marks it.
 */
public class Prop_Discovery extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Discovery";
	}

	@Override
	public String name()
	{
		return "A discovery";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_EXITS | Ability.CAN_ITEMS | Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "A discovery, with experience for whoever makes it";
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((affected == null) || (msg.source() == affected) || (msg.target() != affected))
			return;
		final String event;
		switch(msg.targetMinor())
		{
		case CMMsg.TYP_ENTER:
			event = "ENTER";
			break;
		case CMMsg.TYP_LOOK:
		case CMMsg.TYP_EXAMINE:
			event = "LOOK";
			break;
		case CMMsg.TYP_READ:
			event = "READ";
			break;
		case CMMsg.TYP_OPEN:
			event = "OPEN";
			break;
		case CMMsg.TYP_GET:
			event = "GET";
			break;
		case CMMsg.TYP_SPEAK:
			event = "TALK";
			break;
		default:
			return;
		}
		Discovery.fire(msg.source(), affected, text(), event);
	}
}
