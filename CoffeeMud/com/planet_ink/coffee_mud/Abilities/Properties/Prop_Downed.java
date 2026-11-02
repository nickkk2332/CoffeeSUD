package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C8: down (PLAYERDEATH's DOWNED x y, core/Downed): for TICKS=x rounds the
 * character lies there, unable to act, and is not attacked.  REVIVE by an
 * ally, or a heal of HEAL=y hit points, brings them back; when the foes
 * that downed them are gone or dead, and no one of the party here still
 * fights, they get up at 1; when the time runs out the rest of the
 * PLAYERDEATH list applies.
 */
public class Prop_Downed extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Downed";
	}

	@Override
	public String name()
	{
		return "Down";
	}

	@Override
	public String displayText()
	{
		return "(Down)";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	protected int	ticksLeft	= 3;
	protected int	healTo		= 1;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		ticksLeft = Math.max(1, CMParms.getParmInt(q, "TICKS", 3));
		healTo = Math.max(1, CMParms.getParmInt(q, "HEAL", 1));
	}

	/**
	 * @return the rounds left
	 */
	public int ticksLeft()
	{
		return ticksLeft;
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		stats.setDisposition(stats.disposition() | PhyStats.IS_SITTING);
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if(!(affected instanceof MOB))
			return true;
		final MOB M = (MOB)affected;
		// not attacked while down
		if((msg.target() == M) && (msg.source() != M) && CMath.bset(msg.targetMajor(), CMMsg.MASK_MALICIOUS))
		{
			if(msg.source() != null)
			{
				msg.source().tell(msg.source(), M, null, CMLib.lang().L("<T-NAME> is down."));
				if(msg.source().getVictim() == M)
					msg.source().setVictim(null);
			}
			return false;
		}
		// and acts not at all
		if((msg.source() == M)
		&& ((msg.sourceMinor() == CMMsg.TYP_WEAPONATTACK) || (msg.sourceMinor() == CMMsg.TYP_LEAVE)
			|| (msg.sourceMinor() == CMMsg.TYP_ENTER) || (msg.sourceMinor() == CMMsg.TYP_FLEE)
			|| (msg.sourceMinor() == CMMsg.TYP_CAST_SPELL) || CMath.bset(msg.sourceMajor(), CMMsg.MASK_MALICIOUS)
			|| (msg.sourceMinor() == CMMsg.TYP_STAND)))
		{
			M.tell(CMLib.lang().L("You are down."));
			return false;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((affected instanceof MOB) && (msg.target() == affected) && (msg.targetMinor() == CMMsg.TYP_HEALING) && (msg.value() >= healTo))
		{
			final MOB M = (MOB)affected;
			Downed.revive(M, M.curState().getHitPoints());
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if((tickID != Tickable.TICKID_MOB) || (!(affected instanceof MOB)))
			return true;
		final MOB M = (MOB)affected;
		// the fight is over: up at 1
		if(Downed.fightOver(M))
		{
			Downed.revive(M, 1);
			return true;
		}
		if(--ticksLeft <= 0)
			Downed.die(M);
		return true;
	}
}
