package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
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
 * C17: an essential or yielding mob.  MODE=YIELD: under AT=n% of its hit
 * points (10) it stops fighting, says MSG=, and its scripts' YIELD_PROG
 * runs; MODE=KNOCKOUT: a blow that would kill it leaves it at 1 hit point,
 * knocked out, and DEFEAT_PROG runs.  Either way, for TICKS=n ticks (20)
 * attacks on it are refused (REFUSEMSG=), and the one who beat it gains
 * the experience of a kill, unless NOXP.
 */
public class Prop_Essential extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Essential";
	}

	@Override
	public String name()
	{
		return "Essential, or yielding";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return knockout ? "Cannot be killed" : "Yields when beaten";
	}

	protected boolean	knockout	= false;
	protected int		at			= 10;
	protected int		ticks		= 20;
	protected String	msgText		= "";
	protected String	refuseMsg	= "<T-NAME> has yielded.";
	protected boolean	xp			= true;

	// what has happened
	protected int		sparedFor	= 0;
	protected MOB		beatenBy	= null;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String p = (newText == null) ? "" : newText;
		final Map<String, String> q = CMParms.parseExactParms(p); // exact keys: AT is not found inside MSG
		knockout = CMParms.getParmStr(q, "MODE", "YIELD").trim().equalsIgnoreCase("KNOCKOUT");
		at = CMath.s_int(CMParms.getParmStr(q, "AT", "10").replace("%", ""));
		ticks = Math.max(1, CMParms.getParmInt(q, "TICKS", 20));
		msgText = CMParms.getParmStr(q, "MSG", knockout ? "<S-NAME> collapse(s), out cold." : "<S-NAME> drop(s) to one knee and yield(s)!");
		refuseMsg = CMParms.getParmStr(q, "REFUSEMSG", knockout ? "<T-NAME> is out cold." : "<T-NAME> has yielded.");
		xp = !CMParms.parse(p.toUpperCase()).contains("NOXP");
	}

	/** the fight ends; it is spared for a while; the one who beat it is told the story */
	protected void beaten(final MOB mob, final MOB by)
	{
		final Room R = mob.location();
		if(R == null)
			return;
		final List<MOB> foes = Threat.enemies(mob);
		mob.makePeace(true);
		for(final MOB F : foes)
		{
			if(F.getVictim() == mob)
				F.makePeace(true);
		}
		sparedFor = ticks;
		beatenBy = by;
		if(msgText.length() > 0)
			R.show(mob, by, CMMsg.MSG_OK_ACTION, msgText);
		if(knockout)
		{
			mob.basePhyStats().setDisposition(mob.basePhyStats().disposition() | PhyStats.IS_SLEEPING);
			mob.recoverPhyStats();
		}
		if(xp && (by != null) && (by != mob))
		{
			final CharClass C = by.charStats().getCurrentClass();
			CMLib.combat().dispenseExperience(CMLib.combat().getCombatBeneficiaries(by, mob, C), CMLib.combat().getCombatDividers(by, mob, C), mob);
		}
		if(knockout)
			ScriptEvents.defeated(mob, by);
		else
			ScriptEvents.yielded(mob, by);
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if(!(affected instanceof MOB))
			return true;
		final MOB mob = (MOB)affected;
		if((msg.target() == mob) && (msg.source() != mob))
		{
			// spared: not to be attacked
			if((sparedFor > 0) && CMath.bset(msg.targetMajor(), CMMsg.MASK_MALICIOUS))
			{
				if((msg.source() != null) && (msg.source().location() != null))
					msg.source().tell(msg.source(), mob, null, refuseMsg);
				msg.source().makePeace(true);
				return false;
			}
			// a blow that would kill it: it is left at 1
			if(knockout && (msg.targetMinor() == CMMsg.TYP_DAMAGE) && (msg.value() >= mob.curState().getHitPoints()))
			{
				msg.setValue(Math.max(0, mob.curState().getHitPoints() - 1));
				beatenBy = msg.source();
			}
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(!(affected instanceof MOB))
			return;
		final MOB mob = (MOB)affected;
		if((msg.target() != mob) || (msg.targetMinor() != CMMsg.TYP_DAMAGE) || (sparedFor > 0) || (msg.source() == mob))
			return;
		if(knockout)
		{
			if((beatenBy == msg.source()) && (mob.curState().getHitPoints() <= 1))
				beaten(mob, msg.source());
		}
		else
		if(SupportPicker.hpPct(mob) < at)
			beaten(mob, msg.source());
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID == Tickable.TICKID_MOB) && (affected instanceof MOB) && (sparedFor > 0))
		{
			if(--sparedFor == 0)
			{
				final MOB mob = (MOB)affected;
				if(knockout && CMath.bset(mob.basePhyStats().disposition(), PhyStats.IS_SLEEPING))
				{
					mob.basePhyStats().setDisposition(mob.basePhyStats().disposition() & ~PhyStats.IS_SLEEPING);
					mob.recoverPhyStats();
					if(mob.location() != null)
						mob.location().show(mob, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> come(s) to."));
				}
				beatenBy = null;
			}
		}
		return true;
	}
}
