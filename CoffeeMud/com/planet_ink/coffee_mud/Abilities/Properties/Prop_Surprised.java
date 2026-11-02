package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C23: caught by surprise (Prop_Ambush): for TICKS=n ticks (1) the mob can
 * neither move, strike, nor use its hands or voice to act; it can look.
 */
public class Prop_Surprised extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Surprised";
	}

	@Override
	public String name()
	{
		return "Caught by surprise";
	}

	@Override
	public String displayText()
	{
		return "(Caught by surprise)";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	protected int	ticksLeft	= 1;
	protected long	lastTold	= 0;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		ticksLeft = Math.max(1, CMParms.getParmInt(CMParms.parseExactParms((newText == null) ? "" : newText), "TICKS", 1));
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if((msg.source() == affected) && (ticksLeft > 0)
		&& ((msg.sourceMinor() == CMMsg.TYP_WEAPONATTACK)
			|| (msg.sourceMinor() == CMMsg.TYP_LEAVE)
			|| (msg.sourceMinor() == CMMsg.TYP_FLEE)
			|| (msg.sourceMinor() == CMMsg.TYP_ENTER)
			|| CMath.bset(msg.sourceMajor(), CMMsg.MASK_MALICIOUS)
			|| ((msg.sourceMinor() == CMMsg.TYP_CAST_SPELL))))
		{
			if(System.currentTimeMillis() > lastTold + 500)
			{
				msg.source().tell(CMLib.lang().L("You are caught by surprise!"));
				lastTold = System.currentTimeMillis();
			}
			return false;
		}
		return true;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if((tickID == Tickable.TICKID_MOB) && (affected instanceof MOB) && (--ticksLeft <= 0))
		{
			final MOB M = (MOB)affected;
			M.delEffect(this);
			setAffectedOne(null);
			M.recoverPhyStats();
		}
		return true;
	}
}
