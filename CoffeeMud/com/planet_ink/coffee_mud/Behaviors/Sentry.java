package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
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
 * N5: a lookout.  It sees an intruder (a player's character or their
 * follower, or those INTRUDERS="mask" fits) here or next door; one hidden
 * or sneaking it spots at SPOT=n% (25) a tick, less their Hide or Sneak.
 * Then, or when attacked, it raises the alarm: ALARM="RADIUS=6 MASK=...
 * MSG=..." (core/NpcAlarm): a shout, and the NPCs within the radius that
 * fit the mask are woken and Alerted for ALERTTICKS=n (40): they head for
 * the alarm and attack the intruder's party.  ALARMITEM="a brass gong" is
 * an item in its room that anyone may PUSH or PULL to raise it.  It raises
 * the alarm at most once in ALERTTICKS.
 */
public class Sentry extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Sentry";
	}

	@Override
	public String name()
	{
		return "a lookout";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "keeping a lookout";
	}

	protected int		spot		= 25;
	protected String	alarmSpec	= "";
	protected int		alertTicks	= NpcAlarm.DEFAULT_ALERT;
	protected String	alarmItem	= "";
	protected String	intruders	= "";
	protected int		cooldown	= 0;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String p = (newParms == null) ? "" : newParms;
		final Map<String, String> P = CMParms.parseEQParms(p);
		spot = CMParms.getParmInt(p, "SPOT", 25);
		alarmSpec = P.containsKey("ALARM") ? P.get("ALARM") : "";
		alertTicks = CMParms.getParmInt(p, "ALERTTICKS", NpcAlarm.DEFAULT_ALERT);
		alarmItem = P.containsKey("ALARMITEM") ? P.get("ALARMITEM").trim() : "";
		intruders = P.containsKey("INTRUDERS") ? P.get("INTRUDERS").trim() : "";
	}

	/** whether M is one it raises the alarm for */
	protected boolean intruder(final MOB mob, final MOB M)
	{
		if((M == null) || (M == mob) || M.amDead())
			return false;
		if(intruders.length() > 0)
			return CMLib.masking().maskCheck(intruders, M, true);
		if(M.playerStats() != null)
			return true;
		final MOB leader = M.getGroupLeader();
		return (leader != null) && (leader != M) && (leader.playerStats() != null);
	}

	/** what sneaking or hiding takes off the chance to be spotted */
	protected int stealthOf(final MOB M)
	{
		int best = 0;
		for(final String id : new String[] { "Thief_Sneak", "Thief_Hide", "Skill_Sneak" })
		{
			final Ability A = M.fetchAbility(id);
			if(A != null)
				best = Math.max(best, A.proficiency());
		}
		return best;
	}

	/** whether it notices M now */
	protected boolean notices(final MOB mob, final MOB M, final boolean nextDoor)
	{
		if(CMLib.flags().isHidden(M) || CMLib.flags().isSneaking(M))
		{
			int chance = (spot * (100 - stealthOf(M))) / 100;
			if(nextDoor)
				chance = chance / 2;
			return (chance > 0) && (CMLib.dice().rollPercentage() <= chance);
		}
		if(nextDoor)
			return CMLib.flags().canSee(mob) && CMLib.flags().isSeeable(M) && (!CMLib.flags().isInDark(M.location()));
		return CMLib.flags().canBeSeenBy(M, mob);
	}

	/**
	 * Raises the alarm.
	 * @param mob the lookout
	 * @param who the intruder, or null
	 */
	protected void alarm(final MOB mob, final MOB who)
	{
		if(cooldown > 0)
			return;
		cooldown = Math.max(1, alertTicks);
		NpcAlarm.raise(mob, who, alarmSpec);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(ticking instanceof MOB)))
			return true;
		final MOB mob = (MOB)ticking;
		final Room here = mob.location();
		if(cooldown > 0)
		{
			cooldown--;
			return true;
		}
		if(mob.amDead() || (here == null) || (!CMLib.flags().isAliveAwakeMobile(mob, true)) || (!CMLib.flags().canSee(mob)))
			return true;
		for(int r = -1; r < Directions.NUM_DIRECTIONS(); r++)
		{
			final Room R = (r < 0) ? here : here.getRoomInDir(r);
			if((R == null) || ((r >= 0) && (R == here)))
				continue;
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if(intruder(mob, M) && notices(mob, M, r >= 0))
				{
					alarm(mob, M);
					return true;
				}
			}
		}
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
		if((msg.target() == mob) && CMath.bset(msg.targetMajor(), CMMsg.MASK_MALICIOUS))
		{
			alarm(mob, src);
			return;
		}
		if((alarmItem.length() > 0)
		&& (msg.target() instanceof Item)
		&& ((msg.targetMinor() == CMMsg.TYP_PUSH) || (msg.targetMinor() == CMMsg.TYP_PULL))
		&& (CMLib.map().roomLocation((Item)msg.target()) == mob.location())
		&& (((Item)msg.target()).Name().equalsIgnoreCase(alarmItem) || CMLib.english().containsString(((Item)msg.target()).Name(), alarmItem)))
			alarm(mob, intruder(mob, src) ? src : null);
	}

	@Override
	protected String debugState()
	{
		return super.debugState() + ((cooldown > 0) ? ("; alarm raised, quiet for " + cooldown + " more ticks") : "; watching");
	}
}
