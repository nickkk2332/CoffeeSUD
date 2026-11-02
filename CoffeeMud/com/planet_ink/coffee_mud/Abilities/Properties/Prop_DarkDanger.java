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
 * W10: dangers in the dark, on a room or an area, for a player the room is
 * dark to (dark, and they cannot see in the dark): LOST=n% a move goes a
 * random way, STUMBLE=n% a move fails and costs movement, LURKER=name and
 * LURK=n% something from the catalog comes out of the dark as they enter;
 * MSG=, STUMBLEMSG= and LURKMSG= say so.
 */
public class Prop_DarkDanger extends Property
{
	@Override
	public String ID()
	{
		return "Prop_DarkDanger";
	}

	@Override
	public String name()
	{
		return "Dangers in the dark";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Dangerous in the dark";
	}

	protected int		lost		= 0;
	protected int		stumble		= 0;
	protected int		lurkChance	= 0;
	protected String	lurker		= "";
	protected String	msgLost		= "You lose your way in the dark.";
	protected String	msgStumble	= "You stumble in the dark and fall.";
	protected String	msgLurk		= "Something comes at you out of the dark!";

	/** the players being sent a random way, so that the move does not roll again */
	protected static final Set<MOB>	redirecting	= Collections.synchronizedSet(new HashSet<MOB>());

	private static int pct(final Map<String, String> P, final String key)
	{
		final String v = P.get(key);
		return (v == null) ? 0 : CMath.s_int(v.replace("%", "").trim());
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		final Map<String, String> P = CMParms.parseEQParms(newMiscText);
		lost = pct(P, "LOST");
		stumble = pct(P, "STUMBLE");
		lurkChance = pct(P, "LURK");
		lurker = P.containsKey("LURKER") ? P.get("LURKER").trim() : "";
		if(lurker.toLowerCase().startsWith("catalog:"))
			lurker = lurker.substring(8).trim();
		if((lurkChance == 0) && (lurker.length() > 0))
			lurkChance = 3;
		if(P.containsKey("MSG"))
			msgLost = P.get("MSG");
		if(P.containsKey("STUMBLEMSG"))
			msgStumble = P.get("STUMBLEMSG");
		if(P.containsKey("LURKMSG"))
			msgLurk = P.get("LURKMSG");
	}

	/**
	 * @param M a character
	 * @param R a room
	 * @return true if the room is dark to them: dark, and they cannot see in the dark
	 */
	public static boolean darkTo(final MOB M, final Room R)
	{
		return (M != null) && (R != null) && CMLib.flags().isInDark(R) && (!CMLib.flags().canSeeInDark(M));
	}

	protected boolean mine(final Room R)
	{
		if(R == null)
			return false;
		if(affected instanceof Room)
			return R == affected;
		if(affected instanceof Area)
			return ((Area)affected).inMyMetroArea(R.getArea());
		return false;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if((msg.targetMinor() == CMMsg.TYP_LEAVE)
		&& (msg.target() instanceof Room)
		&& mine((Room)msg.target())
		&& msg.source().isPlayer()
		&& (!redirecting.contains(msg.source()))
		&& darkTo(msg.source(), (Room)msg.target()))
		{
			final MOB M = msg.source();
			final Room R = (Room)msg.target();
			if((stumble > 0) && (CMLib.dice().rollPercentage() <= stumble))
			{
				M.tell(msgStumble);
				M.curState().adjMovement(-5, M.maxState());
				return false;
			}
			if((lost > 0) && (CMLib.dice().rollPercentage() <= lost))
			{
				final List<Integer> ways = new ArrayList<Integer>();
				for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				{
					final Exit E = R.getExitInDir(d);
					if((E != null) && (R.getRoomInDir(d) != null) && E.isOpen())
						ways.add(Integer.valueOf(d));
				}
				if(ways.size() > 0)
				{
					final int d = ways.get(CMLib.dice().roll(1, ways.size(), -1)).intValue();
					M.tell(msgLost);
					redirecting.add(M);
					try
					{
						CMLib.tracking().walk(M, d, false, false);
					}
					finally
					{
						redirecting.remove(M);
					}
					return false;
				}
			}
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((msg.targetMinor() == CMMsg.TYP_ENTER)
		&& (msg.target() instanceof Room)
		&& mine((Room)msg.target())
		&& msg.source().isPlayer()
		&& (lurkChance > 0)
		&& (lurker.length() > 0)
		&& darkTo(msg.source(), (Room)msg.target())
		&& (CMLib.dice().rollPercentage() <= lurkChance))
		{
			final Room R = (Room)msg.target();
			MOB L = CMLib.catalog().getCatalogMob(lurker);
			if(L == null)
				L = CMClass.getMOB(lurker);
			if(L == null)
			{
				Log.errOut("Prop_DarkDanger", "No catalog mob '" + lurker + "' to come out of the dark.");
				return;
			}
			final MOB N = (MOB)L.copyOf();
			N.bringToLife(R, true);
			msg.source().tell(msgLurk);
		}
	}
}
