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
 * C15: RANK=MINION|NORMAL|ELITE|BOSS (or a rank the ini defines): the mob
 * gets the rank's hit points, damage, attack, armor, speed, resistances and
 * experience (CoffeeLevels asks MobRanks.xpFor), read from MOBRANK_NAME in coffeemud.ini each time its stats
 * are worked out, so one edit retunes every mob of the rank.  LOOK and
 * CONSIDER show the rank's tag.  See core/MobRanks.
 */
public class Prop_MobRank extends Property
{
	@Override
	public String ID()
	{
		return "Prop_MobRank";
	}

	@Override
	public String name()
	{
		return "A mob's rank";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "Ranked " + CMStrings.capitalizeAndLower(MobRanks.nameIn(text()));
	}

	protected MobRanks.Rank rank()
	{
		return MobRanks.rank(MobRanks.nameIn(text()));
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		final MobRanks.Rank R = rank();
		if(R.dmgPct != 0)
			stats.setDamage((int)Math.max(0, Math.round(stats.damage() * (100 + R.dmgPct) / 100.0)));
		if(R.attack != 0)
			stats.setAttackAdjustment(stats.attackAdjustment() + R.attack);
		if(R.armor != 0)
			stats.setArmor(stats.armor() + R.armor);
		if(R.speed != 0)
			stats.setSpeed(Math.max(0.1, stats.speed() + R.speed));
	}

	@Override
	public void affectCharStats(final MOB host, final CharStats stats)
	{
		super.affectCharStats(host, stats);
		for(final int[] r : rank().resists)
			stats.setStat(r[0], stats.getStat(r[0]) + r[1]);
	}

	@Override
	public void affectCharState(final MOB host, final CharState state)
	{
		super.affectCharState(host, state);
		final MobRanks.Rank R = rank();
		if(R.hpPct != 0)
			state.setHitPoints((int)Math.max(1, Math.round(state.getHitPoints() * (100 + R.hpPct) / 100.0)));
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE))
		&& (msg.target() == affected) && (affected instanceof MOB) && (msg.source() != affected))
		{
			final String tag = rank().tag;
			if(tag.length() > 0)
			{
				// after what they see of it, to them alone
				msg.addTrailerMsg(CMClass.getMsg(msg.source(), (MOB)affected, null, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS,
					"^H" + ((MOB)affected).name(msg.source()) + " " + tag + "^?", CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
			}
		}
	}
}
