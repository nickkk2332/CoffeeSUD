package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
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
 * N4: a sleeping NPC that noise wakes.  SLEEPS=ALWAYS (the default),
 * DAWN, DAY, DUSK, NIGHT or hours (22-6), with BED="item" to sleep on.
 * WAKEON=ATTACK,NOISE,ENTER,LIGHT,TOUCH (all, by default) and WAKECHANCE=n
 * (50) per event: a fight here makes it more likely and one next door
 * less; someone entering sneaking or hidden makes it less, by their Sneak
 * or Hide; a light makes it more.  DEEP: only an attack wakes it.  On
 * waking: GRUMBLE="text", AWAKEFOR=n ticks (20) before it sleeps again, and
 * ALARM, which raises the alarm (its Sentry's ALARM=, if it has one).  LOOK
 * at it shows "is sleeping soundly", or "stirs" when something nearly woke it.
 */
public class Sleeper extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Sleeper";
	}

	@Override
	public String name()
	{
		return "a sleeper";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "sleeping, until something wakes it";
	}

	protected Set<TimeClock.TimeOfDay>	tod			= null;
	protected int						from		= -1;
	protected int						to			= -1;
	protected String					bed			= "";
	protected Set<String>				wakeOn		= new HashSet<String>(Arrays.asList("ATTACK", "NOISE", "ENTER", "LIGHT", "TOUCH"));
	protected int						wakeChance	= 50;
	protected boolean					deep		= false;
	protected String					grumble		= null;
	protected int						awakeFor	= 20;
	protected boolean					alarm		= false;

	protected int						awakeTicks	= 0;
	protected boolean					sleptByMe	= false;
	protected long						lastStir	= 0;

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String p = (newParms == null) ? "" : newParms;
		final Map<String, String> P = CMParms.parseEQParms(p);
		final List<String> words = CMParms.parse(p.toUpperCase());
		tod = null;
		from = to = -1;
		String sleeps = P.containsKey("SLEEPS") ? P.get("SLEEPS").toUpperCase().trim() : "ALWAYS";
		if(words.contains("ALWAYS"))
			sleeps = "ALWAYS";
		final TimeClock.TimeOfDay T = (TimeClock.TimeOfDay)CMath.s_valueOf(TimeClock.TimeOfDay.class, sleeps);
		if(T != null)
			tod = EnumSet.of(T);
		else
		if(sleeps.indexOf('-') > 0)
		{
			from = CMath.s_int(sleeps.substring(0, sleeps.indexOf('-')).trim());
			to = CMath.s_int(sleeps.substring(sleeps.indexOf('-') + 1).trim());
		}
		bed = P.containsKey("BED") ? P.get("BED").trim() : "";
		if(P.containsKey("WAKEON"))
		{
			wakeOn = new HashSet<String>();
			for(final String w : CMParms.parseCommas(P.get("WAKEON").toUpperCase(), true))
				wakeOn.add(w.trim());
		}
		wakeChance = CMParms.getParmInt(p, "WAKECHANCE", 50);
		deep = words.contains("DEEP");
		grumble = P.containsKey("GRUMBLE") ? P.get("GRUMBLE") : null;
		awakeFor = CMParms.getParmInt(p, "AWAKEFOR", 20);
		alarm = words.contains("ALARM");
	}

	/** whether it is a time to sleep */
	protected boolean sleepTime(final MOB mob)
	{
		if((tod == null) && (from < 0))
			return true;
		final TimeClock C = CMLib.time().localClock(mob);
		if(C == null)
			return true;
		if((tod != null) && (!tod.contains(C.getTODCode())))
			return false;
		if(from >= 0)
		{
			final int h = C.getHourOfDay();
			return (from <= to) ? ((h >= from) && (h <= to)) : ((h >= from) || (h <= to));
		}
		return true;
	}

	protected void goToSleep(final MOB mob)
	{
		if(CMLib.flags().isSleeping(mob))
			return;
		final Room R = mob.location();
		if((bed.length() > 0) && (R != null) && (R.findItem(null, bed) != null))
			mob.doCommand(CMParms.parse("SLEEP \"" + bed + "\""), MUDCmdProcessor.METAFLAG_FORCED);
		if(!CMLib.flags().isSleeping(mob))
			mob.doCommand(CMParms.parse("SLEEP"), MUDCmdProcessor.METAFLAG_FORCED);
		sleptByMe = CMLib.flags().isSleeping(mob);
	}

	/**
	 * Wakes it, with its grumble and alarm.
	 * @param mob the sleeper
	 * @param by who woke it, or null
	 */
	protected void wake(final MOB mob, final MOB by)
	{
		if(!CMLib.flags().isSleeping(mob))
			return;
		CMLib.commands().postStand(mob, true, false);
		awakeTicks = awakeFor;
		sleptByMe = false;
		if(grumble != null)
			CMLib.commands().postSay(mob, null, grumble, false, false);
		if(alarm)
			NpcAlarm.raise(mob, by, null);
	}

	/**
	 * N8: its area woke: asleep or up as the hour says now.
	 */
	@Override
	public void catchUp(final PhysicalAgent host, final long ticks)
	{
		if(!(host instanceof MOB))
			return;
		final MOB mob = (MOB)host;
		if(mob.amDead() || (mob.location() == null) || mob.isInCombat())
			return;
		awakeTicks = (int)Math.max(0, awakeTicks - ticks);
		if(awakeTicks > 0)
			return;
		final boolean time = sleepTime(mob);
		if(time && (!CMLib.flags().isSleeping(mob)))
			goToSleep(mob);
		else
		if((!time) && sleptByMe && CMLib.flags().isSleeping(mob))
		{
			CMLib.commands().postStand(mob, true, false);
			sleptByMe = false;
		}
	}

	/** a chance roll for something that may wake it; a miss makes it stir */
	protected void maybeWake(final MOB mob, final MOB by, final int chance)
	{
		if(CMLib.dice().rollPercentage() <= chance)
			wake(mob, by);
		else
			lastStir = GameTime.now();
	}

	/** what sneaking or hiding takes off the chance */
	protected int stealthOf(final MOB M)
	{
		if((M == null) || ((!CMLib.flags().isSneaking(M)) && (!CMLib.flags().isHidden(M))))
			return 0;
		int best = 0;
		for(final String id : new String[] { "Thief_Sneak", "Thief_Hide", "Skill_Sneak" })
		{
			final Ability A = M.fetchAbility(id);
			if(A != null)
				best = Math.max(best, A.proficiency());
		}
		return Math.max(best, 50);
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
		if(mob.amDead() || (here == null))
			return true;
		if(awakeTicks > 0)
		{
			awakeTicks--;
			return true;
		}
		if(mob.isInCombat() || (mob.fetchEffect("Alerted") != null))
			return true;
		final boolean time = sleepTime(mob);
		if(time && (!CMLib.flags().isSleeping(mob)))
			goToSleep(mob);
		else
		if((!time) && sleptByMe && CMLib.flags().isSleeping(mob))
		{
			CMLib.commands().postStand(mob, true, false); // its time is over: up
			sleptByMe = false;
		}
		// a fight here or next door
		if(CMLib.flags().isSleeping(mob) && (!deep) && wakeOn.contains("NOISE"))
		{
			int chance = 0;
			for(final Enumeration<MOB> m = here.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && (M != mob) && M.isInCombat())
					chance = Math.max(chance, wakeChance + 20);
			}
			for(int d = 0; (d < Directions.NUM_DIRECTIONS()) && (chance == 0); d++)
			{
				final Room R2 = here.getRoomInDir(d);
				if(R2 == null)
					continue;
				for(final Enumeration<MOB> m = R2.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					if((M != null) && M.isInCombat())
						chance = Math.max(chance, wakeChance / 2);
				}
			}
			if(chance > 0)
				maybeWake(mob, null, chance);
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
		if((src == null) || (src == mob) || (mob.location() == null))
			return;
		// LOOK at it: soundly, or stirring
		if((msg.target() == mob) && ((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE))
		&& CMLib.flags().isSleeping(mob))
		{
			final boolean stirs = (GameTime.now() - lastStir) < (3L * CMProps.getTickMillis());
			msg.addTrailerMsg(CMClass.getMsg(src, mob, null, CMMsg.MSG_OK_VISUAL,
				stirs ? L("<T-NAME> stirs.") : L("<T-NAME> is sleeping soundly."), CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
			return;
		}
		if(!CMLib.flags().isSleeping(mob))
			return;
		final boolean atMe = (msg.target() == mob);
		if(atMe && CMath.bset(msg.targetMajor(), CMMsg.MASK_MALICIOUS))
		{
			if(wakeOn.contains("ATTACK") || deep)
				wake(mob, src);
			return;
		}
		if(deep)
			return;
		if(atMe && wakeOn.contains("TOUCH")
		&& (msg.targetMinor() != CMMsg.TYP_LOOK) && (msg.targetMinor() != CMMsg.TYP_EXAMINE) && (msg.targetMinor() != CMMsg.TYP_SNIFF))
		{
			maybeWake(mob, src, Math.min(100, wakeChance * 2));
			return;
		}
		if((msg.targetMinor() == CMMsg.TYP_ENTER) && (msg.target() == mob.location())
		&& (wakeOn.contains("ENTER") || wakeOn.contains("LIGHT")))
		{
			int chance = wakeOn.contains("ENTER") ? wakeChance : 0;
			if(wakeOn.contains("LIGHT") && CMLib.flags().isLightSource(src))
				chance += 20;
			chance = (chance * (100 - stealthOf(src))) / 100;
			if(chance <= 0)
				lastStir = GameTime.now();
			else
				maybeWake(mob, src, Math.min(100, chance));
			return;
		}
		if(wakeOn.contains("NOISE")
		&& ((msg.othersMinor() == CMMsg.TYP_NOISE) || (msg.sourceMinor() == CMMsg.TYP_NOISE) || (msg.othersMinor() == CMMsg.TYP_WEAPONATTACK)))
			maybeWake(mob, src, Math.min(100, wakeChance + 20));
	}

	private final static String[]	CODES	= { "ROUSED" };

	@Override
	public String[] getStatCodes()
	{
		final String[] sup = super.getStatCodes();
		final String[] all = Arrays.copyOf(sup, sup.length + CODES.length);
		System.arraycopy(CODES, 0, all, sup.length, CODES.length);
		return all;
	}

	@Override
	public String getStat(final String code)
	{
		if("ROUSED".equalsIgnoreCase(code))
			return "" + awakeTicks;
		return super.getStat(code);
	}

	@Override
	public void setStat(final String code, final String val)
	{
		if("ROUSED".equalsIgnoreCase(code))
		{
			awakeTicks = Math.max(awakeTicks, awakeFor);
			sleptByMe = false;
			return;
		}
		super.setStat(code, val);
	}

	@Override
	protected String debugState()
	{
		return super.debugState() + ((awakeTicks > 0) ? ("; awake for " + awakeTicks + " more ticks") : "") + (sleptByMe ? "; put to sleep by its time" : "");
	}
}
