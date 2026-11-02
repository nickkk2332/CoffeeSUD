package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
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
 * C23: an ambush in a room.  MOBS= the ambushers (name*dice+..., catalog:
 * optional, or encounter:table); CHANCE= (100); DETECT=WIS+Thief_DetectTraps:15
 * the party's best at spotting it (d20, a stat's (n-10)/2 and a skill's
 * proficiency/10, against the number); SURPRISE=n rounds the party loses
 * when it is not spotted (1); MSG= when they burst out; SPOTMSG= to a party
 * that spotted them, which then may strike first or walk on before WAIT=n
 * ticks (2) are up; ONCE (the default) or RESET=n ticks; MASK= on the one
 * who comes in.  AMBUSH_PROG SURPRISED, SPOTTED or AVOIDED runs on the
 * room's scripts and its NPCs'.
 */
public class Prop_Ambush extends Property
{
	@Override
	public String ID()
	{
		return "Prop_Ambush";
	}

	@Override
	public String name()
	{
		return "An ambush";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "An ambush lies in wait here";
	}

	/** the tag on the ambushers */
	public static final String TAG = "AMBUSH";

	protected String						mobs		= "";
	protected int							chance		= 100;
	protected String						detect		= "";
	protected int							dc			= 15;
	protected int							surprise	= 1;
	protected String						msgText		= "Figures burst from hiding!";
	protected String						spotMsg		= "You spot someone lying in wait.";
	protected int							resetTicks	= 0;
	protected int							wait		= 2;
	protected MaskingLibrary.CompiledZMask	mask		= null;

	// the state
	protected boolean						used		= false;
	protected int							resetDown	= 0;
	protected MOB							pending		= null;
	protected int							standoff	= 0;
	protected final List<MOB>				ambushers	= new ArrayList<MOB>();
	protected boolean						ticking		= false;
	protected String						lastOutcome	= "";

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		mobs = CMParms.getParmStr(q, "MOBS", "").trim();
		chance = CMath.s_int(CMParms.getParmStr(q, "CHANCE", "100").replace("%", "").trim());
		final String d = CMParms.getParmStr(q, "DETECT", "").trim();
		final int x = d.lastIndexOf(':');
		detect = (x < 0) ? d : d.substring(0, x).trim();
		dc = (x < 0) ? 15 : CMath.s_int(d.substring(x + 1).trim());
		surprise = Math.max(0, CMParms.getParmInt(q, "SURPRISE", 1));
		msgText = CMParms.getParmStr(q, "MSG", "Figures burst from hiding!");
		spotMsg = CMParms.getParmStr(q, "SPOTMSG", "You spot someone lying in wait.");
		resetTicks = Math.max(0, CMParms.getParmInt(q, "RESET", 0));
		wait = Math.max(1, CMParms.getParmInt(q, "WAIT", 2));
		final String m = CMParms.getParmStr(q, "MASK", "").trim();
		mask = (m.length() == 0) ? null : CMLib.masking().getPreCompiledMask(m);
	}

	/** the player's side in the room */
	protected static List<MOB> party(final Room R)
	{
		final List<MOB> list = new ArrayList<MOB>();
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && (!M.amDead()) && GameDifficulty.playerSide(M))
				list.add(M);
		}
		return list;
	}

	/**
	 * One's bonus to spot it: each part of DETECT, a stat's (n-10)/2 or a
	 * skill's proficiency/10, added up.
	 * @param M the one
	 * @param parts DETECT's parts, + between them
	 * @return the bonus
	 */
	public static int bonus(final MOB M, final String parts)
	{
		int b = 0;
		for(final String raw : parts.split("\\+"))
		{
			final String p = raw.trim();
			if(p.length() == 0)
				continue;
			int stat = -1;
			for(final int c : CharStats.CODES.BASECODES())
			{
				final String nm = CharStats.CODES.NAME(c);
				if(nm.equalsIgnoreCase(p) || CharStats.CODES.SHORTNAME(c).equalsIgnoreCase(p)
				|| ((p.length() >= 3) && nm.toUpperCase().startsWith(p.toUpperCase())))
				{
					stat = c;
					break;
				}
			}
			if(stat >= 0)
				b += (M.charStats().getStat(stat) - 10) / 2;
			else
			{
				final Ability A = M.fetchAbility(p);
				if(A != null)
					b += A.proficiency() / 10;
			}
		}
		return b;
	}

	/** whether it may spring now */
	protected boolean armed()
	{
		return (!used) && (mobs.length() > 0);
	}

	protected void needTicks()
	{
		if(!ticking)
		{
			ticking = true;
			CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
		}
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((!(affected instanceof Room)) || (msg.targetMinor() != CMMsg.TYP_ENTER) || (msg.target() != affected) || (!armed()) || (pending != null))
			return;
		final MOB who = msg.source();
		if((who == null) || (!GameDifficulty.playerSide(who)))
			return;
		final MOB lead = who.amFollowing();
		if((lead != null) && (lead != who) && GameDifficulty.playerSide(lead) && (lead.location() == affected))
			return; // the party's leader came in first
		if((mask != null) && (!CMLib.masking().maskCheck(mask, who, true)))
			return;
		if(CMLib.dice().rollPercentage() > chance)
			return;
		pending = who; // on its next tick, the move done
		needTicks();
	}

	/**
	 * Springs it on the party that came in.
	 * @param R the room
	 * @param who the one who came in
	 * @return SPOTTED or SURPRISED
	 */
	public String spring(final Room R, final MOB who)
	{
		used = true;
		resetDown = resetTicks;
		final List<MOB> party = party(R);
		if(!party.contains(who))
			party.add(0, who);
		int best = Integer.MIN_VALUE;
		for(final MOB M : party)
			best = Math.max(best, bonus(M, detect));
		final boolean spotted = (detect.length() > 0) && ((CMLib.dice().roll(1, 20, 0) + best) >= dc);
		ambushers.clear();
		for(final MOB A : Encounters.spawnSpec(mobs, R, who))
		{
			A.addTag(TAG);
			ambushers.add(A);
		}
		if(spotted)
		{
			for(final MOB M : party)
				M.tell(M, null, null, spotMsg);
			standoff = wait;
			lastOutcome = "SPOTTED";
		}
		else
		{
			R.showHappens(CMMsg.MSG_OK_ACTION, msgText);
			for(final MOB M : party)
			{
				if(surprise <= 0)
					break;
				final Ability S = CMClass.getAbility("Prop_Surprised");
				if(S != null)
				{
					S.setMiscText("TICKS=" + surprise);
					S.setSavable(false);
					M.addEffect(S);
				}
			}
			strike(R);
			lastOutcome = "SURPRISED";
		}
		ScriptEvents.ambush(R, lastOutcome, who);
		return lastOutcome;
	}

	/** the ambushers fall on the party */
	protected void strike(final Room R)
	{
		final List<MOB> party = party(R);
		if(party.isEmpty())
			return;
		for(final MOB A : ambushers)
		{
			if((!A.amDead()) && (A.location() == R) && (!A.isInCombat()))
				CMLib.combat().postAttack(A, party.get(CMLib.dice().roll(1, party.size(), -1)), A.fetchWieldedItem());
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return true;
		if(!(affected instanceof Room))
		{
			this.ticking = false;
			return false;
		}
		final Room R = (Room)affected;
		if(pending != null)
		{
			final MOB who = pending;
			pending = null;
			if((who.location() == R) && (!who.amDead()))
				spring(R, who);
		}
		else
		if(standoff > 0)
		{
			for(final Iterator<MOB> i = ambushers.iterator(); i.hasNext();)
			{
				if(i.next().amDestroyed())
					i.remove();
			}
			boolean fighting = false;
			for(final MOB A : ambushers)
				fighting = fighting || A.isInCombat();
			if(fighting)
				standoff = 0; // the party struck first
			else
			if(party(R).isEmpty())
			{
				// they walked on: it is avoided
				standoff = 0;
				for(final MOB A : ambushers)
				{
					if(!A.amDead())
						A.destroy();
				}
				ambushers.clear();
				lastOutcome = "AVOIDED";
				ScriptEvents.ambush(R, lastOutcome, null);
			}
			else
			if(--standoff <= 0)
				strike(R);
		}
		else
		if(used && (resetTicks > 0) && (--resetDown <= 0))
		{
			used = false;
			ambushers.clear();
		}
		final boolean more = (pending != null) || (standoff > 0) || (used && (resetTicks > 0));
		if(!more)
			this.ticking = false;
		return more;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("OUTCOME"))
			return lastOutcome;
		if(code.equalsIgnoreCase("ARMED"))
			return "" + armed();
		return super.getStat(code);
	}
}
