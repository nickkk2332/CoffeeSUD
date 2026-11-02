package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * N15: one NPC's (or, on an area, one village's) opinion of each
 * character: START=n DECAY=n GIFT(item)=+n TALK(word)=+n QUESTWIN(quest)=+n
 * ATTACKED=-n STOLE=-n KILLED(mob)=-n BRIBE=points-per-gold
 * SHOPFACTOR=FRIENDLY:0.9,DEVOTED:0.8 REFUSE=HOSTILE REFUSEMSG="...".
 * See core/NpcAffinity.
 */
public class Prop_Affinity extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Affinity";
	}

	@Override
	public String name()
	{
		return "An opinion of each character";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Holds an opinion of each character";
	}

	protected final static Set<Integer>	TRADE	= new XHashSet<Integer>(new Integer[] {
		Integer.valueOf(CMMsg.TYP_BUY), Integer.valueOf(CMMsg.TYP_SELL), Integer.valueOf(CMMsg.TYP_LIST),
		Integer.valueOf(CMMsg.TYP_VALUE), Integer.valueOf(CMMsg.TYP_BID), Integer.valueOf(CMMsg.TYP_VIEW),
		Integer.valueOf(CMMsg.TYP_DEPOSIT), Integer.valueOf(CMMsg.TYP_WITHDRAW), Integer.valueOf(CMMsg.TYP_BORROW) });

	/**
	 * @param M a mob a message is about
	 * @return the NPC whose opinion it moves: the one this is on, or, on an
	 *         area, any NPC of it without its own
	 */
	protected MOB npcOf(final Environmental M)
	{
		if(!(M instanceof MOB) || (((MOB)M).playerStats() != null))
			return null;
		if(affected instanceof MOB)
			return (M == affected) ? (MOB)M : null;
		if((affected instanceof Area) && (((MOB)M).fetchEffect(ID()) == null))
			return (MOB)M;
		return null;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		final MOB npc = npcOf(msg.target());
		if((npc != null)
		&& TRADE.contains(Integer.valueOf(msg.targetMinor()))
		&& NpcAffinity.refuses(npc, msg.source()))
		{
			CMLib.commands().postSay(npc, msg.source(), NpcAffinity.refuseMessage(npc), false, false);
			return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		final MOB src = msg.source();
		if(src == null)
			return;
		// a death: the killer is the message's tool
		if(((msg.sourceMinor() == CMMsg.TYP_DEATH) || (msg.othersMinor() == CMMsg.TYP_DEATH)) && (msg.tool() instanceof MOB))
		{
			final MOB killer = (MOB)msg.tool();
			final MOB witness = (affected instanceof MOB) ? (MOB)affected : null;
			if((witness != null) && (witness != src) && (witness.location() == src.location()))
				NpcAffinity.event(witness, killer, "KILLED", src);
			else
			if(affected instanceof Area)
			{
				// the village: each of its NPCs here without its own view holds the area's, once is enough
				final Room R = src.location();
				if(R != null)
				{
					for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
					{
						final MOB M = m.nextElement();
						if((M != src) && (npcOf(M) != null))
						{
							NpcAffinity.event(M, killer, "KILLED", src);
							break;
						}
					}
				}
			}
			return;
		}
		final MOB npc = npcOf(msg.target());
		if((npc == null) || (src == npc) || (src.playerStats() == null))
			return;
		if((msg.targetMinor() == CMMsg.TYP_GIVE) && (msg.tool() instanceof Item))
			NpcAffinity.event(npc, src, "GIFT", msg.tool());
		else
		if((msg.targetMinor() == CMMsg.TYP_SPEAK) && (msg.sourceMessage() != null))
		{
			final String said = CMStrings.getSayFromMessage(msg.sourceMessage());
			if(said != null)
				NpcAffinity.event(npc, src, "TALK", said);
		}
		else
		if((msg.targetMinor() == CMMsg.TYP_JUSTICE) && CMath.bset(msg.targetMajor(), CMMsg.MASK_DELICATE))
			NpcAffinity.event(npc, src, "STOLE", null);
		else
		if(CMath.bset(msg.targetMajor(), CMMsg.MASK_MALICIOUS))
			NpcAffinity.event(npc, src, "ATTACKED", null);
	}
}
