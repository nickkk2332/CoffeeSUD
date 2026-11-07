package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
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
 * I8: an item's powers on demand, with no class skill.  WORD="Ignis" (said
 * by its holder) and/or VERB=INVOKE (INVOKE item; the default when there is
 * no WORD); SPELLS="Spell_Fireball;LEVEL=12,Spell_Light" what it casts;
 * TARGET=SELF|ENEMY|ROOM; CHARGES=3 (charges left kept in the text, LEFT=);
 * RECHARGE=DAWN|DAILY|TICKS=200 fills them again; COOLDOWN=5 ticks between
 * uses; REQUIRE=WORN|HELD; MASK= who may; MSG= what the room sees,
 * EMPTYMSG= when it is spent.  IDENTIFY tells its charges and recharge.
 */
public class Prop_Invokable extends Property implements VerbTaker
{
	@Override
	public String ID()
	{
		return "Prop_Invokable";
	}

	@Override
	public String name()
	{
		return "Powers on demand";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		final StringBuilder b = new StringBuilder("Invokes ");
		for(int i = 0; i < spells.size(); i++)
			b.append((i > 0) ? ", " : "").append(spells.get(i)[0]);
		if(charges > 0)
			b.append("; ").append(left).append(" of ").append(charges).append(" charges");
		if(recharge.length() > 0)
			b.append("; recharges ").append(recharge.toLowerCase());
		if(cooldown > 0)
			b.append("; ").append(cooldown).append(" ticks between uses").append((coolLeft > 0) ? (", ready in " + coolLeft) : "");
		if(refuse.size() > 0)
			b.append("; does nothing (and is not spent) ").append(refuse.contains("HEALTHY") ? "at full health" : "")
				.append((refuse.size() > 1) ? " or " : "").append(refuse.contains("NOAILMENT") ? "with nothing to cure" : "");
		if(refillKill > 0)
			b.append("; each kill by its holder refills ").append(refillKill);
		if(autoBelow > 0)
			b.append("; used by itself when its holder falls below ").append(autoBelow).append("% health");
		if(word.length() > 0)
			b.append("; the word is '").append(word).append("'");
		return b.toString();
	}

	protected String						base		= "";
	protected Map<String, String>			parms		= new HashMap<String, String>();
	protected String						word		= "";
	protected String						verb		= "";
	protected final List<String[]>			spells		= new ArrayList<String[]>();
	protected String						target		= "SELF";
	protected int							charges		= 0;
	protected int							left		= 0;
	protected String						recharge	= "";
	protected int							rechargeTicks	= 0;
	protected int							rechargeDown	= 0;
	protected int							cooldown	= 0;
	protected int							coolLeft	= 0;
	protected String						require		= "";
	protected MaskingLibrary.CompiledZMask	mask		= null;
	protected String						lastTod		= "";
	protected int							lastDay		= -1;
	protected boolean						ticking		= false;
	/** A18: REFUSE=HEALTHY,NOAILMENT: when it does nothing, and is not spent */
	protected final Set<String>				refuse		= new HashSet<String>();
	/** A18: REFILL=KILL:n: charges back for each kill by its holder */
	protected int							refillKill	= 0;
	/** A18: AUTO=HP<n: used by itself when its holder is below n% health */
	protected int							autoBelow	= 0;
	/** A18: used this tick already, so a manual and an automatic use cannot both spend */
	protected boolean						usedThisTick	= false;

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final String t = (newText == null) ? "" : newText;
		parms = CMParms.parseExactParms(t);
		word = CMParms.getParmStr(parms, "WORD", "").trim();
		verb = CMParms.getParmStr(parms, "VERB", (word.length() > 0) ? "" : "INVOKE").trim().toLowerCase();
		spells.clear();
		for(final String s : CMParms.getParmStr(parms, "SPELLS", "").split(","))
		{
			final String[] p = s.trim().split(";");
			if(p[0].trim().length() == 0)
				continue;
			int lvl = 0;
			for(int i = 1; i < p.length; i++)
			{
				if(p[i].trim().toUpperCase().startsWith("LEVEL="))
					lvl = CMath.s_int(p[i].trim().substring(6));
			}
			spells.add(new String[] { p[0].trim(), "" + lvl });
		}
		target = CMParms.getParmStr(parms, "TARGET", "SELF").toUpperCase();
		charges = Math.max(0, CMParms.getParmInt(parms, "CHARGES", 0));
		left = Math.max(0, Math.min(charges, CMParms.getParmInt(parms, "LEFT", charges)));
		recharge = CMParms.getParmStr(parms, "RECHARGE", "").trim().toUpperCase();
		rechargeTicks = recharge.startsWith("TICKS=") ? CMath.s_int(recharge.substring(6)) : CMParms.getParmInt(parms, "TICKS", 0);
		if(recharge.equals("TICKS") && (rechargeTicks <= 0))
			rechargeTicks = 200;
		rechargeDown = rechargeTicks;
		cooldown = Math.max(0, CMParms.getParmInt(parms, "COOLDOWN", 0));
		// from a save: the cooldown still to run, and how long until the next recharge
		coolLeft = Math.max(0, Math.min(cooldown, CMParms.getParmInt(parms, "COOLLEFT", 0)));
		if((rechargeTicks > 0) && parms.containsKey("RECHARGEIN"))
			rechargeDown = Math.max(1, Math.min(rechargeTicks, CMParms.getParmInt(parms, "RECHARGEIN", rechargeTicks)));
		require = CMParms.getParmStr(parms, "REQUIRE", "").toUpperCase();
		refuse.clear();
		for(final String r : CMParms.parseCommas(CMParms.getParmStr(parms, "REFUSE", "").toUpperCase(), true))
			refuse.add(r);
		final String refill = CMParms.getParmStr(parms, "REFILL", "").toUpperCase().trim();
		refillKill = refill.startsWith("KILL") ? Math.max(1, CMath.s_int(refill.replaceAll("[^0-9]", ""))) : 0;
		final String auto = CMParms.getParmStr(parms, "AUTO", "").toUpperCase().replace(" ", "");
		autoBelow = auto.startsWith("HP<") ? Math.max(0, Math.min(99, CMath.s_int(auto.substring(3).replace("%", "")))) : 0;
		final String m = CMParms.getParmStr(parms, "MASK", "").trim();
		mask = (m.length() == 0) ? null : CMLib.masking().getPreCompiledMask(m);
		base = t.replaceAll("(?i)\\s*\\b(LEFT|COOLLEFT|RECHARGEIN)=\\S*", "").trim();
		if(verb.length() > 0)
			ScriptEvents.propVerbsSeen();
	}

	@Override
	public String text()
	{
		final StringBuilder s = new StringBuilder(base);
		if((charges > 0) && (left != charges))
			s.append(" LEFT=").append(left);
		if(coolLeft > 0)
			s.append(" COOLLEFT=").append(coolLeft);
		if((rechargeTicks > 0) && (charges > 0) && (left < charges) && (rechargeDown != rechargeTicks))
			s.append(" RECHARGEIN=").append(rechargeDown);
		return s.toString();
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		if((P != null) && (!ticking) && ((recharge.length() > 0) || (cooldown > 0) || (autoBelow > 0)))
		{
			ticking = true;
			CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
		}
	}

	/**
	 * @return the charges left
	 */
	public int left()
	{
		return left;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("LEFT"))
			return "" + left;
		return super.getStat(code);
	}

	/** whether one may use it, as it is placed */
	protected boolean placed(final MOB mob)
	{
		if(!(affected instanceof Item))
			return false;
		final Item I = (Item)affected;
		if(I.owner() != mob)
			return false;
		if(require.equals("WORN"))
			return !I.amWearingAt(Wearable.IN_INVENTORY);
		if(require.equals("HELD"))
			return I.amWearingAt(Wearable.WORN_HELD) || I.amWearingAt(Wearable.WORN_WIELD);
		return true;
	}

	/**
	 * Uses it.
	 * @param mob who
	 * @return true if it was used
	 */
	public boolean use(final MOB mob)
	{
		final Item I = (Item)affected;
		final Room R = mob.location();
		if((R == null) || (!placed(mob)))
			return false;
		if((mask != null) && (!CMLib.masking().maskCheck(mask, mob, true)))
		{
			mob.tell(mob, I, null, CMLib.lang().L("<T-NAME> does nothing for you."));
			return true;
		}
		if((charges > 0) && (left <= 0))
		{
			mob.tell(mob, I, null, CMParms.getParmStr(parms, "EMPTYMSG", CMLib.lang().L("<T-NAME> is spent.")));
			return true;
		}
		if(coolLeft > 0)
		{
			mob.tell(mob, I, null, CMLib.lang().L("<T-NAME> is not ready yet."));
			return true;
		}
		// A18: nothing to do: refused, and nothing is spent
		final String refusal = refusal(mob);
		if(refusal != null)
		{
			mob.tell(mob, I, null, CMParms.getParmStr(parms, "REFUSEMSG", refusal));
			return true;
		}
		MOB victim = null;
		if(target.equals("ENEMY"))
		{
			victim = mob.getVictim();
			if(victim == null)
			{
				mob.tell(CMLib.lang().L("There is no foe to turn it on."));
				return true;
			}
		}
		final String m = CMParms.getParmStr(parms, "MSG", CMLib.lang().L("<T-NAME> flares with power!"));
		R.show(mob, I, CMMsg.MSG_OK_ACTION, m);
		for(final String[] s : spells)
		{
			final Ability A = CMClass.getAbility(s[0]);
			if(A == null)
				continue;
			final int lvl = CMath.s_int(s[1]);
			final Physical tgt = target.equals("SELF") ? mob : (target.equals("ENEMY") ? victim : null);
			A.invoke(mob, new ArrayList<String>(), tgt, true, (lvl > 0) ? lvl : I.phyStats().level());
		}
		if(charges > 0)
			left--;
		if(cooldown > 0)
			coolLeft = cooldown;
		usedThisTick = true;
		return true;
	}

	/**
	 * A18: why it would do nothing for someone now, or null if it would work.
	 * @param mob who would use it
	 * @return the reason, or null
	 */
	public String refusal(final MOB mob)
	{
		if(refuse.contains("HEALTHY") && (mob.curState().getHitPoints() >= mob.maxState().getHitPoints()))
			return CMLib.lang().L("You are not hurt; <T-NAME> would do nothing.");
		if(refuse.contains("NOAILMENT"))
		{
			boolean ailing = false;
			for(final Enumeration<Ability> e = mob.effects(); e.hasMoreElements();)
			{
				final Ability A = e.nextElement();
				ailing = ailing || ((A != null) && (A.abstractQuality() == Ability.QUALITY_MALICIOUS) && A.canBeUninvoked());
			}
			if(!ailing)
				return CMLib.lang().L("Nothing ails you; <T-NAME> would do nothing.");
		}
		return null;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		// A18: REFILL=KILL:n: a kill by its holder (a death names its killer as the tool)
		if((refillKill > 0) && (charges > 0) && (affected instanceof Item)
		&& (msg.sourceMinor() == CMMsg.TYP_DEATH) && (msg.tool() != null)
		&& (msg.tool() == ((Item)affected).owner()) && (msg.source() != msg.tool()))
			left = Math.min(charges, left + refillKill);
		if((word.length() == 0) || (!(affected instanceof Item)) || (msg.sourceMinor() != CMMsg.TYP_SPEAK) || (msg.sourceMessage() == null))
			return;
		final Item I = (Item)affected;
		if(I.owner() != msg.source())
			return;
		final String said = CMStrings.getSayFromMessage(msg.sourceMessage());
		if((said != null) && (" " + said.toLowerCase() + " ").replaceAll("[^a-z0-9 ]", " ").contains(" " + word.toLowerCase() + " "))
			use(msg.source());
	}

	@Override
	public boolean takeVerb(final MOB mob, final PhysicalAgent host, final String v, final String rest)
	{
		if((host != affected) || (verb.length() == 0) || (!v.equalsIgnoreCase(verb)) || (!ScriptEvents.names(host, rest.trim())))
			return false;
		if(!placed(mob))
		{
			mob.tell(mob, host, null, require.equals("WORN") ? CMLib.lang().L("You must wear <T-NAME> for that.") : CMLib.lang().L("You must hold <T-NAME> for that."));
			return true;
		}
		return use(mob);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((!(affected instanceof Item)) || ((Item)affected).amDestroyed())
		{
			this.ticking = false;
			return false;
		}
		if(coolLeft > 0)
			coolLeft--;
		final boolean usedNow = usedThisTick;
		usedThisTick = false;
		// A18: AUTO=HP<n: its holder hurt badly enough uses it by itself, as a manual use would, but not in a tick
		// it was already used in by hand
		if((autoBelow > 0) && (!usedNow) && (((Item)affected).owner() instanceof MOB))
		{
			final MOB holder = (MOB)((Item)affected).owner();
			if((holder.maxState().getHitPoints() > 0)
			&& ((holder.curState().getHitPoints() * 100) < (autoBelow * holder.maxState().getHitPoints()))
			&& ((charges <= 0) || (left > 0)) && (coolLeft <= 0) && placed(holder) && (refusal(holder) == null))
				use(holder);
		}
		if(left >= charges)
			return true;
		if(rechargeTicks > 0)
		{
			if(--rechargeDown <= 0)
			{
				rechargeDown = rechargeTicks;
				left = charges;
			}
			return true;
		}
		final Room R = CMLib.map().roomLocation(affected);
		final TimeClock C = (R != null) ? CMLib.time().localClock(R) : CMLib.time().globalClock();
		if(C == null)
			return true;
		if(recharge.equals("DAWN"))
		{
			final String tod = C.getTODCode().name();
			if(tod.equals("DAWN") && (!lastTod.equals("DAWN")) && (lastTod.length() > 0))
				left = charges;
			lastTod = tod;
		}
		else
		if(recharge.equals("DAILY"))
		{
			final int day = C.getDayOfMonth() + (C.getMonth() * 100) + (C.getYear() * 10000);
			if((lastDay >= 0) && (day != lastDay))
				left = charges;
			lastDay = day;
		}
		return true;
	}
}
