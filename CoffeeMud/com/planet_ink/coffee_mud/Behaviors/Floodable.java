package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * W19: a room that floods, a preset of RoomStates with levels: 0 dry, 1
 * ankle-deep, 2 waist-deep (a swimming room; loose things weighing up to
 * FLOAT= float off to FLOWTO=), 3 full (its air water: one drowns as one's
 * breath runs out).  While its condition holds (RISE= a mask on someone
 * there, as -WEATHER +RAIN; ONVAR="[host] name==value"; TIDE=HIGH; any of
 * them) the water rises a level each RATE= ticks, else it drains a level
 * each DRAIN= ticks.  Rising water puts out lights and fires.  DESC1= to
 * DESC3= and MSG1= to MSG3= give each level's look and what those coming in
 * are told.  FLOODLEVEL() reads the level.
 */
public class Floodable extends RoomStates
{
	@Override
	public String ID()
	{
		return "Floodable";
	}

	@Override
	public String name()
	{
		return "a floodable room";
	}

	@Override
	public String accountForYourself()
	{
		return "flooding";
	}

	protected static final String[]	LEVELS		= { "dry", "ankle", "waist", "full" };

	protected String	ownParms	= "";
	protected String	riseMask	= null;
	protected String	onVar		= null;
	protected String	tide		= null;
	protected int		rate		= 10;
	protected int		drain		= 10;
	protected int		flowTo		= -1;
	protected int		floatWeight	= 2;
	protected int		count		= 0;

	private static String q(final String s)
	{
		return "\"" + s.replace("\"", "'") + "\"";
	}

	@Override
	public void setParms(final String parameters)
	{
		ownParms = parameters;
		final Map<String, String> P = CMParms.parseEQParms(parameters);
		riseMask = (P.containsKey("RISE") && (P.get("RISE").trim().length() > 0)) ? P.get("RISE") : null;
		onVar = (P.containsKey("ONVAR") && (P.get("ONVAR").trim().length() > 0)) ? P.get("ONVAR") : null;
		tide = (P.containsKey("TIDE") && (P.get("TIDE").trim().length() > 0)) ? P.get("TIDE").trim() : null;
		rate = Math.max(1, P.containsKey("RATE") ? CMath.s_int(P.get("RATE")) : 10);
		drain = Math.max(1, P.containsKey("DRAIN") ? CMath.s_int(P.get("DRAIN")) : rate);
		flowTo = P.containsKey("FLOWTO") ? CMLib.directions().getGoodDirectionCode(P.get("FLOWTO")) : -1;
		floatWeight = P.containsKey("FLOAT") ? CMath.s_int(P.get("FLOAT")) : 2;
		final String[] msgs = { "", "The water here is ankle-deep.", "The water here is waist-deep: you have to swim.", "The room is full of water!" };
		final StringBuilder s = new StringBuilder("STATE=dry;");
		for(int l = 1; l <= 3; l++)
		{
			s.append(" STATE=").append(LEVELS[l]);
			if(P.containsKey("TITLE" + l))
				s.append(" TITLE=").append(q(P.get("TITLE" + l)));
			if(P.containsKey("DESC" + l))
				s.append(" DESC=").append(q(P.get("DESC" + l)));
			if(l >= 2)
				s.append(" ADD=\"Chant_Flood()\"");
			if(l == 3)
				s.append(" ATMOSPHERE=WATER");
			s.append(" ENTERMSG=").append(q(P.containsKey("MSG" + l) ? P.get("MSG" + l) : msgs[l])).append(";");
		}
		s.append(" START=dry");
		super.setParms(s.toString());
	}

	@Override
	public String getParms()
	{
		return ownParms;
	}

	/** @return the water's level, 0 to 3 */
	public int level()
	{
		final int x = CMParms.indexOf(LEVELS, currentState());
		return Math.max(0, x);
	}

	/** @return true if the water rises now */
	protected boolean rising()
	{
		if((riseMask != null) && holds(riseMask))
			return true;
		if((onVar != null) && varHolds(onVar))
			return true;
		if(tide != null)
		{
			for(final Room R : rooms())
			{
				if(Tides.is(R, tide))
					return true;
			}
		}
		return false;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if((!(ticking instanceof Room)) && (!(ticking instanceof Area)))
			return true;
		if(!applied)
			apply();
		final int lvl = level();
		final boolean up = rising();
		if((up && (lvl >= 3)) || ((!up) && (lvl <= 0)))
		{
			count = 0;
			return true;
		}
		count++;
		if(count < (up ? rate : drain))
			return true;
		count = 0;
		changeState(LEVELS[lvl + (up ? 1 : -1)]);
		if(up)
			rose(lvl + 1);
		return true;
	}

	/**
	 * The water has risen: lights and fires go out, and at waist-deep the
	 * light loose things float off.
	 * @param lvl the new level
	 */
	protected void rose(final int lvl)
	{
		for(final Room R : rooms())
		{
			if(lvl >= 2)
			{
				for(final Item I : new XVector<Item>(R.items()))
				{
					if(I == null)
						continue;
					if((I instanceof Light) && ((Light)I).isLit())
					{
						((Light)I).light(false);
						R.showHappens(CMMsg.MSG_OK_VISUAL, L("The water puts out @x1.", I.name()));
					}
					else
					if((I.container() == null) && CMLib.flags().isGettable(I) && (I.phyStats().weight() <= floatWeight))
					{
						final Room to = (flowTo >= 0) ? R.getRoomInDir(flowTo) : null;
						R.showHappens(CMMsg.MSG_OK_VISUAL, L("@x1 floats away.", CMStrings.capitalizeFirstLetter(I.name())));
						if(to != null)
							to.moveItemTo(I, ItemPossessor.Expire.Player_Drop);
						else
							I.destroy();
					}
				}
			}
			if(lvl >= 2)
			{
				for(final MOB M : new XVector<MOB>(R.inhabitants()))
				{
					for(final Item I : new XVector<Item>(M.items()))
					{
						if((I instanceof Light) && ((Light)I).isLit() && ((Light)I).goesOutInTheRain())
						{
							((Light)I).light(false);
							I.recoverPhyStats();
							M.tell(L("The water puts out your @x1.", I.name()));
						}
					}
					M.recoverPhyStats();
				}
			}
			R.recoverPhyStats();
		}
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("FLOODLEVEL"))
			return "" + level();
		return super.getStat(code);
	}
}
