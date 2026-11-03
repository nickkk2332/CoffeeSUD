package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * W8: a shore room the tide floods, a preset of RoomStates: at high tide
 * (SPAN= hours either side) it is a swimming room (TITLE=, DESC=,
 * FLOODMSG= for those coming in), at the spring tides with DEEP its air is
 * water, and WASH=n washes away the loose things there weighing up to n as
 * the water comes; at the ebb it is itself again.
 */
public class TidalRoom extends RoomStates
{
	@Override
	public String ID()
	{
		return "TidalRoom";
	}

	@Override
	public String name()
	{
		return "a tidal room";
	}

	@Override
	public String accountForYourself()
	{
		return "flooded at high tide";
	}

	protected int		wash		= 0;
	protected String	ownParms	= "";

	private static String q(final String s)
	{
		return "\"" + s.replace("\"", "'") + "\"";
	}

	@Override
	public void setParms(final String parameters)
	{
		ownParms = parameters;
		final Map<String, String> P = CMParms.parseEQParms(parameters);
		final int span = P.containsKey("SPAN") ? CMath.s_int(P.get("SPAN")) : 0;
		wash = P.containsKey("WASH") ? CMath.s_int(P.get("WASH")) : 0;
		final boolean deep = CMParms.parse(parameters.toUpperCase()).contains("DEEP");
		final String looks = (P.containsKey("TITLE") ? (" TITLE=" + q(P.get("TITLE"))) : "") + (P.containsKey("DESC") ? (" DESC=" + q(P.get("DESC"))) : "");
		final String enter = " ENTERMSG=" + q(P.containsKey("FLOODMSG") ? P.get("FLOODMSG") : "The tide is in: the sea swirls about you.");
		final StringBuilder s = new StringBuilder("STATE=ebb;");
		s.append(" STATE=flooded").append(looks).append(" ADD=\"Chant_Flood()\"").append(enter).append(";");
		if(deep)
		{
			s.append(" STATE=deep").append(looks).append(" ATMOSPHERE=WATER ADD=\"Chant_Flood()\"").append(enter).append(";");
			s.append(" TO=deep FROM=ebb,flooded WHEN=\"-TIDE +SPRING_HIGH:").append(span).append("\";");
		}
		s.append(" TO=flooded FROM=ebb").append(deep ? ",deep" : "").append(" WHEN=\"-TIDE +").append(deep ? "NORMAL_HIGH:" + span + " +NEAP_HIGH:" : "HIGH:").append(span).append("\";");
		s.append(" TO=ebb FROM=flooded").append(deep ? ",deep" : "").append(" WHEN=\"+TIDE -HIGH:").append(span).append("\";");
		s.append(" START=ebb");
		super.setParms(s.toString());
	}

	@Override
	public String getParms()
	{
		return ownParms;
	}

	@Override
	public boolean changeState(final String name)
	{
		final boolean ok = super.changeState(name);
		if(ok && (wash > 0) && (!currentState().equals("ebb")))
		{
			for(final Room R : rooms())
			{
				for(final Item I : new XVector<Item>(R.items()))
				{
					if((I != null) && (I.container() == null) && CMLib.flags().isGettable(I) && (I.phyStats().weight() <= wash))
					{
						R.showHappens(CMMsg.MSG_OK_VISUAL, L("The tide washes @x1 away.", I.name()));
						I.destroy();
					}
				}
			}
		}
		return ok;
	}
}
