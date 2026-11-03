package com.planet_ink.coffee_mud.Abilities.Misc;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
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
 * CR13: HOME under way.  After its turns (HOMERECALL) the whole party is
 * brought to the leader's home, its followers with them; a fight first
 * stops it.
 */
public class GoingHome extends StdAbility
{
	@Override
	public String ID()
	{
		return "GoingHome";
	}

	@Override
	public String name()
	{
		return "Going home";
	}

	@Override
	public String displayText()
	{
		return "(Going home)";
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
	public int abstractQuality()
	{
		return Ability.QUALITY_INDIFFERENT;
	}

	@Override
	public int classificationCode()
	{
		return Ability.ACODE_PROPERTY;
	}

	protected Room	home	= null;
	protected boolean	stopped	= false;

	/**
	 * Sets HOME off.
	 * @param mob who goes
	 * @param home where
	 * @param turns how many turns it takes
	 * @return the effect, or null if they are going already
	 */
	public static GoingHome start(final MOB mob, final Room home, final int turns)
	{
		if((mob == null) || (home == null) || (mob.fetchEffect("GoingHome") != null))
			return null;
		final GoingHome A = new GoingHome();
		A.home = home;
		A.startTickDown(mob, mob, Math.max(1, turns));
		A.tickDown = Math.max(1, turns);
		return A;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if((affected instanceof MOB) && (tickID == Tickable.TICKID_MOB))
		{
			for(final MOB M : com.planet_ink.coffee_mud.Abilities.Properties.Prop_HomeBeacon.party((MOB)affected))
			{
				if((M != null) && M.isInCombat())
				{
					((MOB)affected).tell(CMLib.lang().L("There is fighting: you do not go home."));
					stopped = true;
					unInvoke();
					return false;
				}
			}
		}
		return super.tick(ticking, tickID);
	}

	@Override
	public void unInvoke()
	{
		if(!(affected instanceof MOB))
		{
			super.unInvoke();
			return;
		}
		final MOB mob = (MOB)affected;
		final boolean arrive = canBeUninvoked() && (!stopped) && (home != null);
		super.unInvoke();
		if(!arrive)
			return;
		goNow(mob, home);
	}

	/**
	 * Brings the party home now.
	 * @param mob one of the party
	 * @param home where
	 * @return how many came
	 */
	public static int goNow(final MOB mob, final Room home)
	{
		int n = 0;
		for(final MOB M : com.planet_ink.coffee_mud.Abilities.Properties.Prop_HomeBeacon.party(mob))
		{
			if((M == null) || (M.location() == home) || M.isInCombat() || (M.location() == null))
				continue;
			final Room from = M.location();
			from.show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> head(s) for home."));
			home.bringMobHere(M, true);
			home.show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> arrive(s) home."));
			CMLib.commands().postLook(M, true);
			n++;
		}
		return n;
	}
}
