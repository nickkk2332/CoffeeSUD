package com.planet_ink.coffee_mud.Abilities.Misc;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
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
 * N5: an NPC an alarm has roused (core/NpcAlarm): for its time it heads for
 * the alarm room, or for the intruder if it knows where they are, and
 * attacks them, or any of their party, when it finds them.  Schedule and
 * Patroller wait while it lasts, and Mobile is held off.  Text: ROOM="room
 * ID" INTRUDER="name".
 */
public class Alerted extends StdAbility
{
	@Override
	public String ID()
	{
		return "Alerted";
	}

	private final static String	localizedName	= CMLib.lang().L("Alerted");

	@Override
	public String name()
	{
		return localizedName;
	}

	private final static String	localizedStaticDisplay	= CMLib.lang().L("(Alert)");

	@Override
	public String displayText()
	{
		return localizedStaticDisplay;
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_PROPERTY;
	}

	@Override
	public int abstractQuality()
	{
		return Ability.QUALITY_INDIFFERENT;
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	protected int canTargetCode()
	{
		return 0;
	}

	@Override
	protected int addedTickTime(final MOB invokerMOB, final int baseTickTime)
	{
		return 0; // as long as ALERTTICKS says
	}

	protected String	roomID		= "";
	protected String	intruder	= "";

	@Override
	public void setMiscText(final String newMiscText)
	{
		super.setMiscText(newMiscText);
		roomID = CMParms.getParmStr(newMiscText, "ROOM", "");
		intruder = CMParms.getParmStr(newMiscText, "INTRUDER", "");
	}

	/** someone to fight here: the intruder, one of their party, or, with none named, a player's character */
	protected MOB foe(final MOB mob, final Room R)
	{
		MOB I = null;
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (M == mob) || M.amDead() || (!CMLib.flags().canBeSeenBy(M, mob)))
				continue;
			if(intruder.length() > 0)
			{
				if(M.Name().equalsIgnoreCase(intruder))
					return M;
				final MOB leader = M.getGroupLeader();
				if(((leader != null) && leader.Name().equalsIgnoreCase(intruder))
				|| ((M.playerStats() != null) && (I == null) && (CMLib.players().getPlayerAllHosts(intruder) != null)
					&& M.getGroupMembers(new HashSet<MOB>()).contains(CMLib.players().getPlayerAllHosts(intruder))))
					I = M;
			}
			else
			if(M.playerStats() != null)
				return M;
		}
		return I;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(affected instanceof MOB)))
			return true;
		final MOB mob = (MOB)affected;
		final Room here = mob.location();
		if(mob.amDead() || (here == null) || mob.isInCombat())
			return true;
		for(final Enumeration<Behavior> b = mob.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B instanceof MobileBehavior)
				((MobileBehavior)B).suspendMobility(2);
		}
		if(!CMLib.flags().isAliveAwakeMobile(mob, true))
			CMLib.commands().postStand(mob, true, false);
		if(!CMLib.flags().isAliveAwakeMobile(mob, true))
			return true;
		final MOB foe = foe(mob, here);
		if(foe != null)
		{
			CMLib.combat().postAttack(mob, foe, mob.fetchWieldedItem());
			return true;
		}
		Room dest = CMLib.map().getRoom(roomID);
		final MOB I = (intruder.length() > 0) ? CMLib.players().getPlayerAllHosts(intruder) : null;
		if((I != null) && (I.location() != null) && (dest != null) && (I.location().getArea() == dest.getArea()))
			dest = I.location();
		if((dest == null) || (dest == here))
			return true;
		final List<Room> trail = CMLib.tracking().findTrailToRoom(here, dest, CMLib.tracking().newFlags(), 30);
		final int dir = ((trail == null) || trail.isEmpty()) ? -1 : CMLib.tracking().trackNextDirectionFromHere(trail, here, true);
		if(dir >= 0)
			CMLib.tracking().walk(mob, dir, false, false);
		return true;
	}
}
