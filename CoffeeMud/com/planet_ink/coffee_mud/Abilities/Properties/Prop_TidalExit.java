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
 * W8: an exit passable only at one tide (OPEN=LOW, or HIGH), SPAN= hours
 * either side of it; MSG= what one is told the rest of the time.
 */
public class Prop_TidalExit extends Property
{
	@Override
	public String ID()
	{
		return "Prop_TidalExit";
	}

	@Override
	public String name()
	{
		return "A way open at one tide";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_EXITS;
	}

	@Override
	public String accountForYourself()
	{
		return "passable only at " + open.toLowerCase() + " tide";
	}

	protected String	open	= "LOW";
	protected int		span	= 0;
	protected String	msg		= "The way is under water; it is passable only at low tide.";

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		final Map<String, String> P = CMParms.parseEQParms(newMiscText);
		open = P.containsKey("OPEN") ? P.get("OPEN").trim().toUpperCase() : "LOW";
		span = P.containsKey("SPAN") ? CMath.s_int(P.get("SPAN")) : 0;
		msg = P.containsKey("MSG") ? P.get("MSG") : ("The way is under water; it is passable only at " + open.toLowerCase() + " tide.");
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if((affected instanceof Exit)
		&& (msg.tool() == affected)
		&& ((msg.targetMinor() == CMMsg.TYP_ENTER) || (msg.targetMinor() == CMMsg.TYP_LEAVE)))
		{
			final Room R = msg.source().location();
			if((R != null) && (!Tides.is(R, open + ":" + span)))
			{
				msg.source().tell(this.msg);
				return false;
			}
		}
		return true;
	}
}
