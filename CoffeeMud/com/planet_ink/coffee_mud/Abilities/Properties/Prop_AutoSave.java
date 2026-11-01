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

import com.planet_ink.coffee_mud.core.save.SaveGame;

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
 * Saves the game when the player comes into a room, or into an area from
 * outside it.  Parameters:
 * <ul>
 * <li>ONCE=CHAR saves only the first time each character comes in; without
 * it, every time;</li>
 * <li>SLOT= the save's name (default auto); with ROTATE=n, the saves go in
 * turn to SLOT1 to SLOTn, so that the last n are kept;</li>
 * <li>MSG= what the player is told (default [Autosaved]).</li>
 * </ul>
 * An autosave follows the game's save rules for fights (it is skipped while
 * the party fights), but not for places, since a builder put it here.  In
 * an ironman game it saves the game's one save.
 */
public class Prop_AutoSave extends Property
{
	@Override
	public String ID()
	{
		return "Prop_AutoSave";
	}

	@Override
	public String name()
	{
		return "Autosave";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS|Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "The game saves itself here";
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost,msg);
		if((msg.targetMinor()!=CMMsg.TYP_ENTER)
		||(!(msg.target() instanceof Room))
		||(msg.source().isMonster())
		||(affected==null))
			return;
		final Room R=(Room)msg.target();
		if(affected instanceof Room)
		{
			if(R!=affected)
				return;
		}
		else
		if(affected instanceof Area)
		{
			if((!((Area)affected).inMyMetroArea(R.getArea()))
			||((msg.source().location()!=null)&&(((Area)affected).inMyMetroArea(msg.source().location().getArea()))))
				return;
		}
		else
			return;
		final MOB mob=msg.source();
		final Session T=Party.terminalOf(mob);
		if((T==null)||(T.mob()!=mob))
			return; // a party member, not the one being played
		final String once=CMParms.getParmStr(text(), "ONCE", "").toUpperCase().trim();
		final String tattoo="AUTOSAVE:"+(((affected instanceof Room)?CMLib.map().getExtendedRoomID((Room)affected):affected.Name()).toUpperCase());
		if(once.startsWith("CHAR")&&(mob.findTattoo(tattoo)!=null))
			return;
		msg.addTrailerRunnable(new Runnable()
		{
			@Override
			public void run()
			{
				if((mob.location()!=R)||(mob.amDead()))
					return;
				if(once.startsWith("CHAR"))
					mob.addTattoo(tattoo);
				final String problem=SaveGame.autoSave(mob, nextSlot());
				if(problem==null)
				{
					final String msgStr=CMParms.getParmStr(text(), "MSG", "");
					mob.tell((msgStr.length()>0)?msgStr:L("[Autosaved]"));
				}
			}
		});
	}

	/**
	 * Returns the save to write next: the slot, or with ROTATE the next one
	 * in turn, which is the oldest of them.
	 * @return the save's name
	 */
	protected String nextSlot()
	{
		final String slot=CMParms.getParmStr(text(), "SLOT", "auto").trim();
		final int rotate=CMParms.getParmInt(text(), "ROTATE", 0);
		if(rotate<=1)
			return slot;
		String oldest=slot+"1";
		long oldestTime=Long.MAX_VALUE;
		for(int i=1;i<=rotate;i++)
		{
			final long when=SaveGame.savedAt(slot+i);
			if(when<oldestTime)
			{
				oldestTime=when;
				oldest=slot+i;
			}
		}
		return oldest;
	}
}
