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
 * C16: death and defeat rules of an area or a room.  PLAYERDEATH=,
 * MOBDEATH= and ITEMLOOTPOLICY= as in coffeemud.ini, in place of the game's
 * (core/DeathRules).  TO=room: a player's side character who would die here
 * is defeated instead: MSG= shows, its gear goes to GEAR=CONTAINER:room/
 * container, it wakes in that room with HEAL=n% of its hit points (1 if 0),
 * its followers with it.  MASK= whom these rules are for.
 */
public class Prop_DeathRules extends Property
{
	@Override
	public String ID()
	{
		return "Prop_DeathRules";
	}

	@Override
	public String name()
	{
		return "Rules of death here";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Death works differently here";
	}

	protected String	to		= "";
	protected String	gear	= "";
	protected int		heal	= 0;
	protected String	msgText	= "<S-NAME> <S-IS-ARE> beaten!";

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		to = CMParms.getParmStr(q, "TO", "").trim();
		gear = CMParms.getParmStr(q, "GEAR", "").trim();
		heal = Math.max(0, CMath.s_int(CMParms.getParmStr(q, "HEAL", "0").replace("%", "").trim()));
		msgText = CMParms.getParmStr(q, "MSG", "<S-NAME> <S-IS-ARE> beaten!");
	}

	/** where GEAR=CONTAINER:room/container sends it, or null */
	protected Container gearContainer()
	{
		if(!gear.toUpperCase().startsWith("CONTAINER:"))
			return null;
		final String spec = gear.substring(10).trim();
		final int x = spec.indexOf('/');
		if(x < 0)
			return null;
		final Room R = CMLib.map().getRoom(spec.substring(0, x).trim());
		if(R == null)
			return null;
		final Item I = R.findItem(null, spec.substring(x + 1).trim());
		return (I instanceof Container) ? (Container)I : null;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if((to.length() == 0) || (msg.sourceMinor() != CMMsg.TYP_DEATH) || (msg.source() == null))
			return true;
		final MOB M = msg.source();
		if((!GameDifficulty.playerSide(M)) || (!DeathRules.fits(this, M)))
			return true;
		final Room toR = CMLib.map().getRoom(to);
		final Room deathRoom = M.location();
		if((toR == null) || (deathRoom == null))
			return true;
		// defeated, not dead
		if(msgText.length() > 0)
			deathRoom.show(M, null, CMMsg.MSG_OK_VISUAL, msgText);
		final MOB killer = (msg.tool() instanceof MOB) ? (MOB)msg.tool() : null;
		for(final MOB F : Threat.enemies(M))
		{
			if(F.getVictim() == M)
				F.makePeace(true);
		}
		M.makePeace(true);
		if(killer != null)
			killer.makePeace(true);
		final Container C = gearContainer();
		if(C != null)
		{
			final Room CR = CMLib.map().roomLocation(C);
			final List<Item> carried = new ArrayList<Item>();
			for(final Enumeration<Item> e = M.items(); e.hasMoreElements();)
				carried.add(e.nextElement());
			for(final Item I : carried)
			{
				if((I == null) || (I.container() != null))
					continue; // what is in a bag goes with the bag
				I.unWear();
				M.delItem(I);
				if(CR != null)
					CR.addItem(I, ItemPossessor.Expire.Never);
				I.setContainer(C);
				for(final Item I2 : carried)
				{
					if((I2 != I) && (I2.container() == I))
					{
						M.delItem(I2);
						if(CR != null)
							CR.addItem(I2, ItemPossessor.Expire.Never);
						I2.setContainer((Container)I);
					}
				}
			}
			M.recoverPhyStats();
			M.recoverCharStats();
			M.recoverMaxState();
		}
		toR.bringMobHere(M, true); // its followers with it
		M.curState().setHitPoints(Math.max(1, (int)Math.round(M.maxState().getHitPoints() * heal / 100.0)));
		toR.showOthers(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> <S-IS-ARE> brought in."));
		return false;
	}
}
