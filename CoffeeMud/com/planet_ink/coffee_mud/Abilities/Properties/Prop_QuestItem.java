package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * Q8: an item bound to a quest: QUEST=name (NODROP NOSELL NOGIVE)
 * (REMOVEON=WON,FAILED,ABANDONED,EXPIRED,ENDED, ALL by default, or NONE)
 * (KEEPONDEATH).  It refuses to be dropped, sold or given, as its words say,
 * stays with the character at death, and goes when the holder's quest
 * leaves ACTIVE as REMOVEON says (core/QuestItems).  QUESTS name lists it.
 */
public class Prop_QuestItem extends Property
{
	protected boolean	noDrop		= false;
	protected boolean	noSell		= false;
	protected boolean	noGive		= false;
	protected boolean	keepOnDeath	= false;

	@Override
	public String ID()
	{
		return "Prop_QuestItem";
	}

	@Override
	public String name()
	{
		return "A quest's item";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return "Bound to a quest";
	}

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		final Set<String> words = new HashSet<String>();
		for(final String w : CMParms.parse(newMiscText.toUpperCase()))
			words.add(w.trim());
		noDrop = words.contains("NODROP");
		noSell = words.contains("NOSELL");
		noGive = words.contains("NOGIVE");
		keepOnDeath = words.contains("KEEPONDEATH");
	}

	protected boolean refuse(final CMMsg msg, final String why)
	{
		msg.source().tell(L(why, affected.name(msg.source())));
		return false;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if(!(affected instanceof Item))
			return true;
		if(noDrop
		&&(msg.target()==affected)
		&&(msg.targetMinor()==CMMsg.TYP_DROP)
		&&(((Item)affected).owner()==msg.source()))
			return refuse(msg, "You cannot drop @x1: it is for a quest.");
		if(noSell
		&&(msg.tool()==affected)
		&&((msg.sourceMinor()==CMMsg.TYP_SELL)||(msg.targetMinor()==CMMsg.TYP_SELL)))
			return refuse(msg, "You cannot sell @x1: it is for a quest.");
		if(noGive
		&&(msg.tool()==affected)
		&&(msg.targetMinor()==CMMsg.TYP_GIVE)
		&&(((Item)affected).owner()==msg.source()))
			return refuse(msg, "You cannot give @x1 away: it is for a quest.");
		return true;
	}

	@Override
	public void affectPhyStats(final Physical affected, final PhyStats affectableStats)
	{
		super.affectPhyStats(affected, affectableStats);
		if(keepOnDeath
		&&(affected instanceof Item)
		&&(((Item)affected).owner() instanceof MOB))
			affectableStats.setSensesMask(affectableStats.sensesMask()|PhyStats.SENSE_ITEMDEATHKEEPER);
	}
}
