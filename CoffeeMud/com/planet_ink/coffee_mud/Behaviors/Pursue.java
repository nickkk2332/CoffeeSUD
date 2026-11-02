package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * C11: chases one who flees it, then goes home.  CHANCE=n the percent
 * chance it gives chase (60), MAXROOMS=n how many rooms it follows (3),
 * DELAY=n ticks before it follows (1), MASK= whom it chases, LEASH=n the
 * furthest from home it goes (no limit), STAY to stay where it gives up
 * rather than walk home, GIVEUPMSG= what it does then, REMEMBER=n: one who
 * fled it and comes back within n ticks is attacked on sight.
 */
public class Pursue extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Pursue";
	}

	@Override
	public String name()
	{
		return "chasing those who flee";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "chasing those who flee";
	}

	protected int							chance		= 60;
	protected int							maxRooms	= 3;
	protected int							delay		= 1;
	protected MaskingLibrary.CompiledZMask	mask		= null;
	protected int							leash		= 0;
	protected boolean						stay		= false;
	protected String						giveUpMsg	= "<S-NAME> give(s) up the chase.";
	protected int							remember	= 0;

	// the chase
	protected MOB							quarry		= null;
	protected MOB							lastQuarry	= null;
	protected int							delayDown	= 0;
	protected int							followed	= 0;
	protected int							fromHome	= 0;
	protected long							ticks		= 0;
	protected final Map<String, Long>		remembered	= new HashMap<String, Long>();
	protected MOB							onSight		= null;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String p = (newParms == null) ? "" : newParms;
		chance = CMParms.getParmInt(p, "CHANCE", 60);
		maxRooms = CMParms.getParmInt(p, "MAXROOMS", 3);
		delay = Math.max(0, CMParms.getParmInt(p, "DELAY", 1));
		final String m = CMParms.getParmStr(p, "MASK", "").trim();
		mask = (m.length() == 0) ? null : CMLib.masking().getPreCompiledMask(m);
		leash = CMParms.getParmInt(p, "LEASH", 0);
		stay = CMParms.parse(p.toUpperCase()).contains("STAY");
		giveUpMsg = CMParms.getParmStr(p, "GIVEUPMSG", "<S-NAME> give(s) up the chase.");
		remember = CMParms.getParmInt(p, "REMEMBER", 0);
		quarry = null;
		lastQuarry = null;
		followed = 0;
	}

	protected void attack(final MOB mob, final MOB target)
	{
		if((target == null) || target.amDead() || (target.location() != mob.location()) || (!CMLib.flags().canBeSeenBy(target, mob)))
			return;
		if(mob.getVictim() != target)
			CMLib.combat().postAttack(mob, target, mob.fetchWieldedItem());
	}

	protected void giveUp(final MOB mob)
	{
		quarry = null;
		lastQuarry = null;
		followed = 0;
		final Room R = mob.location();
		if((R != null) && (giveUpMsg.length() > 0))
			R.show(mob, null, CMMsg.MSG_OK_ACTION, giveUpMsg);
		if((!stay) && (mob.getStartRoom() != null) && (mob.getStartRoom() != R) && (mob.fetchEffect("WanderHomeLater") == null))
		{
			final Ability A = CMClass.getAbility("WanderHomeLater");
			if(A != null)
			{
				A.setMiscText("ONCE=true MINTICKS=1 MAXTICKS=1 IGNOREPCS=true");
				mob.addEffect(A);
				A.setSavable(false);
				A.makeLongLasting();
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(ticking instanceof MOB)))
			return true;
		final MOB mob = (MOB)ticking;
		final Room R = mob.location();
		ticks++;
		if(R == null)
			return true;
		if(R == mob.getStartRoom())
			fromHome = 0;
		if(onSight != null)
		{
			if(!mob.isInCombat())
				attack(mob, onSight);
			onSight = null;
		}
		if((quarry == null) && (!mob.isInCombat()))
		{
			lastQuarry = null; // the fight is over: a new chase counts its rooms afresh
			followed = 0;
		}
		if(quarry == null)
			return true;
		if(quarry.amDead() || quarry.amDestroyed())
		{
			quarry = null;
			return true;
		}
		if(mob.isInCombat() && (mob.getVictim() != quarry))
		{
			quarry = null; // busy with another
			return true;
		}
		if(delayDown > 0)
		{
			delayDown--;
			return true;
		}
		if(quarry.location() == R)
		{
			attack(mob, quarry); // caught: the rooms it took count on, should they flee again
			quarry = null;
			return true;
		}
		int dir = -1;
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			final Room R2 = R.getRoomInDir(d);
			final Exit E = R.getExitInDir(d);
			if((R2 != null) && (R2 == quarry.location()) && (E != null) && E.isOpen())
			{
				dir = d;
				break;
			}
		}
		if((dir < 0) || (followed >= maxRooms) || ((leash > 0) && (fromHome >= leash)))
		{
			giveUp(mob);
			return true;
		}
		mob.makePeace(false);
		if(CMLib.tracking().walk(mob, dir, false, false))
		{
			followed++;
			fromHome++;
			if(quarry.location() == mob.location())
				attack(mob, quarry);
		}
		else
			giveUp(mob);
		return true;
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		super.executeMsg(host, msg);
		if(!(host instanceof MOB))
			return;
		final MOB mob = (MOB)host;
		final MOB src = msg.source();
		if((src == null) || (src == mob))
			return;
		if((msg.sourceMinor() == CMMsg.TYP_FLEE) && ((mob.getVictim() == src) || (src.getVictim() == mob)))
		{
			if((mask != null) && (!CMLib.masking().maskCheck(mask, src, true)))
				return;
			if(remember > 0)
				remembered.put(src.Name(), Long.valueOf(ticks + remember));
			if(CMLib.dice().rollPercentage() <= chance)
			{
				if(lastQuarry != src)
					followed = 0;
				lastQuarry = src;
				quarry = src;
				delayDown = delay;
			}
			return;
		}
		if((remember > 0) && (msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() == mob.location()))
		{
			final Long until = remembered.get(src.Name());
			if(until != null)
			{
				remembered.remove(src.Name());
				if(ticks <= until.longValue())
					onSight = src;
			}
		}
	}
}
