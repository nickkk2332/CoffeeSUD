package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C10: which of its foes a monster attacks.  PREFER=an ordered list of
 * HEALER, CASTER, LOWESTHP, LOWESTARMOR, LEADER, LASTHITME, STRONGEST and
 * MASK:zapper (last, a mask may hold commas only in quotes): the first that
 * names someone here wins.  SWITCH=n the percent chance a tick that it
 * looks again (30), STICKY=n the ticks after a switch before the next (3),
 * TAUNTABLE: one with a clear lead in its threat (C18) comes first.
 */
public class TargetPriority extends StdBehavior
{
	@Override
	public String ID()
	{
		return "TargetPriority";
	}

	@Override
	public String name()
	{
		return "choosing whom to attack";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "choosing whom to attack";
	}

	protected final List<String>			prefer		= new ArrayList<String>();
	protected MaskingLibrary.CompiledZMask	mask		= null;
	protected int							switchPct	= 30;
	protected int							sticky		= 3;
	protected boolean						tauntable	= false;
	protected int							stickyDown	= 0;
	protected MOB							lastHitMe	= null;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String p = (newParms == null) ? "" : newParms;
		prefer.clear();
		mask = null;
		String list = CMParms.getParmStr(p, "PREFER", "");
		final int x = p.toUpperCase().indexOf("PREFER=");
		if((x >= 0) && (p.length() > x + 7) && (p.charAt(x + 7) == '"'))
		{
			final int e = p.indexOf('"', x + 8);
			if(e > 0)
				list = p.substring(x + 8, e);
		}
		final int m = list.toUpperCase().indexOf("MASK:");
		if(m >= 0)
		{
			String z = list.substring(m + 5).trim();
			if(z.startsWith("\"") && z.endsWith("\"") && (z.length() > 1))
				z = z.substring(1, z.length() - 1);
			mask = CMLib.masking().getPreCompiledMask(z);
			list = list.substring(0, m) + "MASK";
		}
		for(final String s : CMParms.parseCommas(list.toUpperCase(), true))
			prefer.add(s.trim());
		switchPct = CMParms.getParmInt(p, "SWITCH", 30);
		sticky = CMParms.getParmInt(p, "STICKY", 3);
		tauntable = p.toUpperCase().indexOf("TAUNTABLE") >= 0;
		stickyDown = 0;
	}

	/** how many of its abilities are of these kinds */
	protected static int count(final MOB M, final boolean heals)
	{
		int n = 0;
		for(final Enumeration<Ability> a = M.allAbilities(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if(A == null)
				continue;
			if(heals)
			{
				if(SupportPicker.isHeal(A))
					n++;
			}
			else
			{
				final int code = A.classificationCode() & Ability.ALL_ACODES;
				if((code == Ability.ACODE_SPELL) || (code == Ability.ACODE_PRAYER) || (code == Ability.ACODE_CHANT) || (code == Ability.ACODE_SONG))
					n++;
			}
		}
		return n;
	}

	/**
	 * @param mob the monster
	 * @return whom it should attack now, by its list, or null to stay as it is
	 */
	protected MOB choose(final MOB mob)
	{
		final List<MOB> foes = new ArrayList<MOB>();
		for(final MOB M : Threat.enemies(mob))
		{
			if(CMLib.flags().canBeSeenBy(M, mob))
				foes.add(M);
		}
		if(foes.isEmpty())
			return null;
		if(tauntable)
		{
			final MOB T = Threat.clearLead(mob);
			if((T != null) && foes.contains(T))
				return T;
		}
		for(final String p : prefer)
		{
			MOB best = null;
			double bestV = 0;
			for(final MOB M : foes)
			{
				double v;
				if(p.equals("HEALER"))
					v = count(M, true);
				else
				if(p.equals("CASTER"))
					v = count(M, false);
				else
				if(p.equals("LOWESTHP"))
					v = 1000.0 - SupportPicker.hpPct(M);
				else
				if(p.equals("LOWESTARMOR"))
					v = 1000.0 + M.phyStats().armor();
				else
				if(p.equals("LEADER"))
					v = (M.getGroupLeader() == M) && (M.getGroupMembers(new HashSet<MOB>()).size() > 1) ? 1 : 0;
				else
				if(p.equals("LASTHITME"))
					v = (M == lastHitMe) ? 1 : 0;
				else
				if(p.equals("STRONGEST"))
					v = 1 + CMLib.leveler().getPowerLevel(M);
				else
				if(p.equals("MASK"))
					v = ((mask != null) && CMLib.masking().maskCheck(mask, M, true)) ? 1 : 0;
				else
					v = 0;
				if(v > bestV)
				{
					bestV = v;
					best = M;
				}
			}
			if(best != null)
				return best;
		}
		return null;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(ticking instanceof MOB)))
			return true;
		final MOB mob = (MOB)ticking;
		if(!mob.isInCombat())
		{
			stickyDown = 0;
			lastHitMe = null;
			return true;
		}
		if(stickyDown > 0)
		{
			stickyDown--;
			return true;
		}
		if((switchPct < 100) && (CMLib.dice().rollPercentage() > switchPct))
			return true;
		final MOB choice = choose(mob);
		if((choice != null) && (choice != mob.getVictim()))
		{
			mob.setVictim(choice);
			stickyDown = sticky;
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental host, final CMMsg msg)
	{
		super.executeMsg(host, msg);
		if((msg.targetMinor() == CMMsg.TYP_DAMAGE) && (msg.target() == host) && (msg.source() != host) && (msg.value() > 0))
			lastHitMe = msg.source();
	}
}
