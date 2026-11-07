package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * P04: an NPC's service records (core/Services).  Saying a service's word to
 * the NPC (SAYTO, as TALK's menu does) is refused, before its behaviours and
 * scripts hear it, when the record says one may not have it now; otherwise
 * its cost is paid once the words are said.  Words said to the room, or to
 * someone else, are not asking it.
 */
public class Prop_Service extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Service";
	}

	@Override
	public String name()
	{
		return "Service records";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		final String why = Services.problem(text());
		return why.isEmpty() ? "Offers services: SERVICES lists them" : "Service records that cannot be read: " + why;
	}

	private Services.Service asked(final CMMsg msg)
	{
		if(!(affected instanceof MOB) || (msg.source() == affected) || (msg.sourceMinor() != CMMsg.TYP_SPEAK)
		|| (msg.target() != affected) || (msg.sourceMessage() == null))
			return null;
		final String said = CMStrings.getSayFromMessage(msg.sourceMessage());
		return (said == null) ? null : Services.asked((MOB)affected, said);
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		final Services.Service s = asked(msg);
		if(s == null)
			return true;
		final String why = Services.why(msg.source(), (MOB)affected, s);
		if(why.isEmpty())
			return true;
		msg.source().tell(CMLib.lang().L("@x1: not now, @x2.", s.title, why));
		return false;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		final Services.Service s = asked(msg);
		if(s == null)
			return;
		try
		{
			final String paid = Services.pay(msg.source(), (MOB)affected, s);
			if(paid.length() > 0)
				msg.source().tell(paid);
		}
		catch(final java.io.IOException e)
		{
			msg.source().tell(e.getMessage());
		}
	}
}
