package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
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
 * Q14: a board of generated jobs, on an item or a room: COUNT=3
 * REFRESH="1 mudday" (or MUDDAY:1) TEMPLATE=normal VARS="..." FILE=...
 * TAGID=... LEVELRANGE=n, and RandomQuests' own parameters.  LOOK at it
 * (or READ it, a readable item; LOOK in the room, for a room) lists the
 * jobs the reader may take; ACCEPT n takes one.  See core/QuestBoards.
 */
public class Prop_QuestBoard extends Property
{
	@Override
	public String ID()
	{
		return "Prop_QuestBoard";
	}

	@Override
	public String name()
	{
		return "A board of jobs";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS|Ability.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Lists jobs to take on";
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost,msg);
		final Physical board=affected;
		final MOB M=msg.source();
		if((board==null)||(M==null)||(M.playerStats()==null)||(msg.target()!=board))
			return;
		final int how=msg.targetMinor();
		final boolean reads=(how==CMMsg.TYP_LOOK)||(how==CMMsg.TYP_EXAMINE)||((how==CMMsg.TYP_READ)&&(!(board instanceof Room)));
		if(!reads)
			return;
		// after what the LOOK or READ shows, to the reader alone
		final String shown=QuestBoards.show(board,text(),M);
		msg.addTrailerMsg(CMClass.getMsg(M,null,null,CMMsg.MSG_OK_VISUAL,shown,CMMsg.NO_EFFECT,null,CMMsg.NO_EFFECT,null));
	}
}
