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
 * C5: a mob winding up a telegraphed attack (MPTELEGRAPH, or a GenAbility's
 * WINDUP).  NAME= its name (TELEGRAPH), TICKS= how long (2), WARNING= shown
 * at the start and each tick while it waits, and LOOK at it shows that it
 * is gathering power.  INTERRUPT=STUN,DAMAGE&gt;n%,MOVE: what stops it (it is
 * always stopped when it dies, sleeps, or is held or bound), with
 * INTERRUPTMSG=.  AVOID=LEAVE,HIDE,COMMAND:word: those who leave the room,
 * hide, or type the word are not hit.  ABILITY=id TARGET=name: a GenAbility
 * with a windup, used on its target at the end.
 */
public class Prop_Telegraph extends Property implements Telegraph.Effect
{
	@Override
	public String ID()
	{
		return "Prop_Telegraph";
	}

	@Override
	public String name()
	{
		return "Gathering power";
	}

	@Override
	public String displayText()
	{
		return "(Gathering power)";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "Winding up an attack";
	}

	protected String			tgName			= "TELEGRAPH";
	protected int				ticksLeft		= 2;
	protected String			warning			= "";
	protected boolean			onDamage		= false;
	protected int				damagePct		= 0;
	protected boolean			onMove			= false;
	protected String			interruptMsg	= "<S-NAME> <S-IS-ARE> interrupted!";
	protected boolean			avoidHide		= false;
	protected String			avoidWord		= null;
	protected String			abilityID		= null;
	protected String			targetName		= null;

	// while it winds up
	protected int				damageTaken		= 0;
	protected final Set<MOB>	avoided			= new HashSet<MOB>();
	protected boolean			done			= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		tgName = CMParms.getParmStr(q, "NAME", "TELEGRAPH").trim().toUpperCase();
		ticksLeft = Math.max(0, CMParms.getParmInt(q, "TICKS", 2));
		warning = CMParms.getParmStr(q, "WARNING", "");
		interruptMsg = CMParms.getParmStr(q, "INTERRUPTMSG", "<S-NAME> <S-IS-ARE> interrupted!");
		onDamage = false;
		onMove = false;
		for(final String w : CMParms.parseCommas(CMParms.getParmStr(q, "INTERRUPT", "STUN").toUpperCase(), true))
		{
			if(w.startsWith("DAMAGE"))
			{
				onDamage = true;
				damagePct = CMath.s_int(w.substring(6).replace(">", "").replace("=", "").replace("%", "").trim());
			}
			else
			if(w.equals("MOVE"))
				onMove = true;
		}
		avoidHide = false;
		avoidWord = null;
		for(final String w : CMParms.parseCommas(CMParms.getParmStr(q, "AVOID", "LEAVE"), true))
		{
			if(w.equalsIgnoreCase("HIDE"))
				avoidHide = true;
			else
			if(w.toUpperCase().startsWith("COMMAND:"))
				avoidWord = w.substring(8).trim().toLowerCase();
		}
		final String a = CMParms.getParmStr(q, "ABILITY", "").trim();
		abilityID = (a.length() == 0) ? null : a;
		final String t = CMParms.getParmStr(q, "TARGET", "").trim();
		targetName = (t.length() == 0) ? null : t;
	}

	@Override
	public String telegraphName()
	{
		return tgName;
	}

	/** the target of a wound-up ability: by its name, here, or whom it fights */
	protected MOB abilityTarget(final MOB mob)
	{
		final Room R = mob.location();
		if((targetName != null) && (R != null))
		{
			final MOB M = R.fetchInhabitant(targetName);
			if(M != null)
				return M;
		}
		return mob.getVictim();
	}

	/**
	 * Shows the warning, as at the start.
	 * @param mob who winds up
	 */
	public void warn(final MOB mob)
	{
		final Room R = mob.location();
		if((R != null) && (warning.length() > 0))
			R.show(mob, (abilityID != null) ? abilityTarget(mob) : null, CMMsg.MSG_OK_VISUAL, warning);
	}

	@Override
	public void telegraphWarn()
	{
		if(affected instanceof MOB)
			warn((MOB)affected);
	}

	@Override
	public List<MOB> telegraphTargets()
	{
		final List<MOB> list = new ArrayList<MOB>();
		if(!(affected instanceof MOB))
			return list;
		for(final MOB M : Telegraph.foesHere((MOB)affected))
		{
			if(avoided.contains(M))
				continue;
			if(avoidHide && CMLib.flags().isHidden(M))
				continue;
			list.add(M);
		}
		return list;
	}

	@Override
	public void telegraphDone()
	{
		done = true;
		final Physical P = affected;
		if(P != null)
			P.delEffect(this);
		setAffectedOne(null);
	}

	/**
	 * Stops it: its alarm is cancelled, and the room is told.
	 * @param mob who wound up
	 */
	protected void interrupt(final MOB mob)
	{
		if(done)
			return;
		Telegraph.cancel(mob, tgName);
		final Room R = mob.location();
		if((R != null) && (interruptMsg.length() > 0) && (!mob.amDead()))
			R.show(mob, null, CMMsg.MSG_OK_VISUAL, interruptMsg);
		telegraphDone();
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(affected instanceof MOB)) || done)
			return true;
		final MOB mob = (MOB)affected;
		if(!Telegraph.canAct(mob))
		{
			interrupt(mob);
			return true;
		}
		ticksLeft--;
		if(abilityID != null)
		{
			if(ticksLeft > 0)
			{
				warn(mob);
				return true;
			}
			// the windup is over: the ability itself
			Ability A = mob.fetchAbility(abilityID);
			if(A == null)
				A = CMClass.getAbility(abilityID);
			final MOB target = abilityTarget(mob);
			telegraphDone();
			if(A != null)
				Telegraph.release(mob, A, target);
			return true;
		}
		if(ticksLeft > 0)
			warn(mob);
		else
		if((ticksLeft < -3) && (Telegraph.ticksLeft(mob, tgName) < 0))
			telegraphDone(); // its alarm is gone, and never came
		return true;
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		// AVOID=COMMAND:word: they brace, dive behind a pillar...
		if((avoidWord != null) && (!done)
		&& (msg.sourceMinor() == CMMsg.TYP_HUH)
		&& (msg.source() != affected)
		&& (msg.targetMessage() != null))
		{
			final List<String> w = CMParms.parse(msg.targetMessage());
			if((w.size() > 0) && w.get(0).equalsIgnoreCase(avoidWord))
			{
				avoided.add(msg.source());
				msg.source().tell(CMLib.lang().L("You @x1, ready for it.", avoidWord));
				final Room R = msg.source().location();
				if(R != null)
					R.show(msg.source(), null, null, CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null, CMMsg.MSG_OK_VISUAL,
						CMLib.lang().L("<S-NAME> @x1(s), ready for it.", avoidWord));
				return false;
			}
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((!(affected instanceof MOB)) || done)
			return;
		final MOB mob = (MOB)affected;
		if((msg.target() == mob) && (msg.targetMinor() == CMMsg.TYP_DAMAGE) && (msg.value() > 0))
		{
			damageTaken += msg.value();
			if(onDamage && ((damageTaken * 100) > (damagePct * mob.maxState().getHitPoints())))
				interrupt(mob);
		}
		else
		if(onMove && (msg.source() == mob) && ((msg.sourceMinor() == CMMsg.TYP_LEAVE) || (msg.sourceMinor() == CMMsg.TYP_FLEE)))
			interrupt(mob);
		else
		if(((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE))
		&& (msg.target() == mob) && (msg.source() != mob))
		{
			msg.addTrailerMsg(CMClass.getMsg(msg.source(), mob, null, CMMsg.MSG_OK_VISUAL | CMMsg.MASK_ALWAYS,
				"^H" + mob.name(msg.source()) + " is gathering power!^?", CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
		}
	}
}
