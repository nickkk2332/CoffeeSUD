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
 * A builder in PLAYTEST (DT8): while it is on them, their sysop powers are
 * suspended ({@link CMSecurity#setPlaytest(MOB, boolean)}), so that the game
 * treats them as a mortal, and a death takes them back, whole, to the room
 * where the playtest began (the text: that room's ID).  Not saved.
 */
public class Prop_Playtest extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Playtest";
	}

	@Override
	public String name()
	{
		return "Playtest";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	public boolean isSavable()
	{
		return false;
	}

	@Override
	public String accountForYourself()
	{
		return "Playtesting from " + text();
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		if((affected instanceof MOB) && (affected != P))
			CMSecurity.setPlaytest((MOB)affected, false);
		super.setAffectedOne(P);
		if(P instanceof MOB)
			CMSecurity.setPlaytest((MOB)P, true);
	}

	/**
	 * The room the playtest began in, which a death returns to.
	 * @return the room, or null if it is gone
	 */
	public Room restorePoint()
	{
		return CMLib.map().getRoom(text());
	}

	/**
	 * Makes a character whole: hit points, mana and movement, and peace.
	 * @param mob the character
	 */
	public static void makeWhole(final MOB mob)
	{
		mob.makePeace(true);
		mob.recoverMaxState();
		mob.resetToMaxState();
		mob.recoverPhyStats();
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if((affected instanceof MOB)
		&& (msg.sourceMinor() == CMMsg.TYP_DEATH)
		&& (msg.source() == affected))
		{
			final MOB mob = (MOB)affected;
			final Room R = restorePoint();
			makeWhole(mob);
			if((R != null) && (mob.location() != R))
				R.bringMobHere(mob, false);
			mob.tell(L("You die.  The playtest takes you back to where it began, whole."));
			if(mob.location() != null)
				CMLib.commands().postLook(mob, true);
			return false;
		}
		return super.okMessage(myHost, msg);
	}
}
