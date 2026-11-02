package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * C2: morale.  The mob's nerve breaks when its hit points fall under
 * FLEEAT=n%, when its foes here outnumber its side OUTNUMBERED=x to one,
 * or when BREAK= says: LEADERDEAD (its leader, the mob it follows or the
 * one LEADER= names, dies), n%DEAD (that share of its group fell) or
 * HP&lt;n% (its group's hit points together).  Then, at CHANCE=n% a tick
 * (100), it does ACTION=FLEE (to RALLY=room, where it waits REGROUP=n
 * ticks and comes back with the allies it finds), SURRENDER or BERSERK
 * (damage +30%, attack -10%), saying MSG=.  RALLY=HOME is its start room.  SURRENDERAT=n%: under that it
 * gives up whatever else: drops its weapon, makes peace, says
 * SURRENDERMSG= and its scripts' SURRENDER_PROG runs.  BRAVE=mask: never
 * breaks.  Its group is its followers, its leader's, and NPCs of the same
 * race in the room who are fighting.
 */
public class Morale extends StdBehavior
{
	@Override
	public String ID()
	{
		return "Morale";
	}

	@Override
	public String name()
	{
		return "a nerve that can break";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS;
	}

	@Override
	public String accountForYourself()
	{
		return "a nerve that can break";
	}

	protected int							fleeAt			= 0;
	protected double						outnumbered		= 0;
	protected final List<String>			breaks			= new ArrayList<String>();
	protected MaskingLibrary.CompiledZMask	leaderMask		= null;
	protected String						action			= "FLEE";
	protected int							chance			= 100;
	protected String						msgText			= "";
	protected String						rally			= "";
	protected int							regroup			= 10;
	protected int							surrenderAt		= 0;
	protected String						surrenderMsg	= "I yield! Mercy!";
	protected MaskingLibrary.CompiledZMask	brave			= null;

	// the fight
	protected boolean						broken			= false;
	protected boolean						leaderDied		= false;
	protected MOB							leader			= null;
	protected int							groupAtStart	= 0;
	protected int							groupHpAtStart	= 0;
	protected boolean						surrendered		= false;
	protected Ability						berserk			= null;
	protected Room							fightRoom		= null;
	protected int							regroupDown		= -1;
	protected int							returnTicks		= 0;
	protected final List<MOB>				fledFrom		= new ArrayList<MOB>();
	protected String						state			= "";

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String p = (newParms == null) ? "" : newParms;
		final Map<String, String> q = CMParms.parseExactParms(p); // exact keys: LEADER is not in BREAK=LEADERDEAD
		fleeAt = CMath.s_int(CMParms.getParmStr(q, "FLEEAT", "0").replace("%", ""));
		outnumbered = CMath.s_double(CMParms.getParmStr(q, "OUTNUMBERED", "0"));
		breaks.clear();
		for(final String b : CMParms.parseCommas(CMParms.getParmStr(q, "BREAK", "").toUpperCase(), true))
			breaks.add(b.replace(" ", ""));
		final String lm = CMParms.getParmStr(q, "LEADER", "").trim();
		leaderMask = (lm.length() == 0) ? null : CMLib.masking().getPreCompiledMask(lm);
		action = CMParms.getParmStr(q, "ACTION", "FLEE").toUpperCase().trim();
		chance = CMParms.getParmInt(q, "CHANCE", 100);
		msgText = CMParms.getParmStr(q, "MSG", "");
		rally = CMParms.getParmStr(q, "RALLY", "").trim();
		regroup = CMParms.getParmInt(q, "REGROUP", 10);
		surrenderAt = CMath.s_int(CMParms.getParmStr(q, "SURRENDERAT", "0").replace("%", ""));
		surrenderMsg = CMParms.getParmStr(q, "SURRENDERMSG", "I yield! Mercy!");
		final String bm = CMParms.getParmStr(q, "BRAVE", "").trim();
		brave = (bm.length() == 0) ? null : CMLib.masking().getPreCompiledMask(bm);
	}

	/** its leader: by LEADER=, else the mob it follows */
	protected MOB leaderOf(final MOB mob)
	{
		final Room R = mob.location();
		if((leaderMask != null) && (R != null))
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && (M != mob) && CMLib.masking().maskCheck(leaderMask, M, true))
					return M;
			}
			return null;
		}
		final MOB L = mob.amFollowing();
		return (L != null) && (L != mob) ? L : null;
	}

	/** its side here: its group, and NPCs of its race who fight, not on the player's side */
	protected List<MOB> side(final MOB mob)
	{
		final List<MOB> list = new ArrayList<MOB>();
		final Room R = mob.location();
		list.add(mob);
		if(R == null)
			return list;
		final Set<MOB> group = mob.getGroupMembers(new HashSet<MOB>());
		final MOB L = leaderOf(mob);
		if(L != null)
			group.addAll(L.getGroupMembers(new HashSet<MOB>()));
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M == null) || (M == mob) || M.amDead() || list.contains(M) || GameDifficulty.playerSide(M))
				continue;
			if(group.contains(M) || (M.isMonster() && M.isInCombat() && M.charStats().raceName().equals(mob.charStats().raceName())))
				list.add(M);
		}
		return list;
	}

	/** whether its nerve breaks now, and why, or null */
	protected String breaking(final MOB mob)
	{
		if((brave != null) && CMLib.masking().maskCheck(brave, mob, true))
			return null;
		if((fleeAt > 0) && (SupportPicker.hpPct(mob) < fleeAt))
			return "HP";
		final List<MOB> side = side(mob);
		if(outnumbered > 0)
		{
			final int foes = Threat.enemies(mob).size();
			if(foes >= outnumbered * side.size())
				return "OUTNUMBERED";
		}
		for(final String b : breaks)
		{
			if(b.equals("LEADERDEAD") && leaderDied)
				return b;
			if(b.endsWith("%DEAD") && (groupAtStart > 1))
			{
				final int pct = CMath.s_int(b.substring(0, b.length() - 5));
				if((groupAtStart - side.size()) * 100 >= pct * groupAtStart)
					return b;
			}
			if(b.startsWith("HP<") && (groupHpAtStart > 0))
			{
				final int pct = CMath.s_int(b.substring(3).replace("%", ""));
				int hp = 0;
				for(final MOB M : side)
					hp += M.curState().getHitPoints();
				if(hp * 100 < pct * groupHpAtStart)
					return b;
			}
		}
		return null;
	}

	protected void say(final MOB mob, final String text)
	{
		if((text == null) || (text.trim().length() == 0) || (mob.location() == null))
			return;
		if(text.startsWith(":"))
			mob.location().show(mob, null, CMMsg.MSG_OK_ACTION, "<S-NAME> " + text.substring(1).trim());
		else
			CMLib.commands().postSay(mob, null, text, false, false);
	}

	/** gives up: drops its weapon, makes peace, says so, SURRENDER_PROG */
	protected void surrender(final MOB mob)
	{
		final Room R = mob.location();
		if(R == null)
			return;
		surrendered = true;
		final Item W = mob.fetchWieldedItem();
		if(W != null)
			CMLib.commands().postDrop(mob, W, true, false, false);
		final List<MOB> foes = Threat.enemies(mob);
		mob.makePeace(true);
		for(final MOB F : foes)
		{
			if(F.getVictim() == mob)
				F.makePeace(true);
		}
		say(mob, surrenderMsg);
		ScriptEvents.surrender(mob, foes.isEmpty() ? mob : foes.get(0));
	}

	protected void goBerserk(final MOB mob)
	{
		if(berserk != null)
			return;
		final Ability A = CMClass.getAbility("Prop_Adjuster");
		if(A == null)
			return;
		final int dmg = (int)Math.round(mob.basePhyStats().damage() * 0.3);
		final int att = (int)Math.round(Math.abs(mob.basePhyStats().attackAdjustment()) * 0.1);
		A.setMiscText("damage+" + Math.max(1, dmg) + " attacks-" + att);
		A.setSavable(false);
		mob.addEffect(A);
		A.makeLongLasting();
		berserk = A;
		mob.recoverPhyStats();
	}

	protected void calmDown(final MOB mob)
	{
		if(berserk != null)
		{
			mob.delEffect(berserk);
			berserk = null;
			mob.recoverPhyStats();
		}
	}

	protected Room rallyRoom(final MOB mob)
	{
		if(rally.length() == 0)
			return null;
		if(rally.equalsIgnoreCase("HOME"))
			return mob.getStartRoom();
		return CMLib.map().getRoom(rally);
	}

	/** the fight is over: its counts and rage go */
	protected void endFight(final MOB mob)
	{
		groupAtStart = 0;
		groupHpAtStart = 0;
		leaderDied = false;
		leader = null;
		broken = false;
		calmDown(mob);
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
		if(R == null)
			return true;
		final MOB L = leaderOf(mob);
		if(L != null)
			leader = L; // remembered: a dying leader's followers are let go before they hear of it
		// regrouping at the rally point
		if(regroupDown >= 0)
		{
			final Room RR = rallyRoom(mob);
			if((RR != null) && (R != RR) && (++returnTicks < 60))
				return true; // still on its way
			if(regroupDown > 0)
			{
				regroupDown--;
				state = "regrouping";
				return true;
			}
			regroupDown = -1;
			if((fightRoom != null) && (fightRoom != R))
			{
				for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					if((M != null) && (M != mob) && M.isMonster() && (!GameDifficulty.playerSide(M)) && (!M.isInCombat()) && (M.amFollowing() == null))
						M.setFollowing(mob);
				}
				say(mob, ":rallies <S-HIS-HER> allies and charges back!");
				CMLib.tracking().autoTrack(mob, fightRoom);
				state = "returning";
				returnTicks = 0;
			}
			else
				state = "";
			broken = false;
			return true;
		}
		if(!mob.isInCombat())
		{
			if("returning".equals(state))
			{
				if((R != fightRoom) && (++returnTicks < 60))
					return true; // on its way back
				// back where it ran from: at them again
				state = "";
				endFight(mob);
				if(R == fightRoom)
				{
					for(final MOB F : fledFrom)
					{
						if((F.location() == R) && (!F.amDead()) && CMLib.flags().canBeSeenBy(F, mob))
						{
							CMLib.combat().postAttack(mob, F, mob.fetchWieldedItem());
							break;
						}
					}
				}
				fledFrom.clear();
				return true;
			}
			if(groupAtStart > 0)
			{
				endFight(mob); // the fight is over
				state = "";
				fledFrom.clear();
			}
			return true;
		}
		surrendered = false;
		if(groupAtStart == 0)
		{
			final List<MOB> side = side(mob);
			groupAtStart = side.size();
			for(final MOB M : side)
				groupHpAtStart += M.maxState().getHitPoints();
			fightRoom = R;
		}
		if((surrenderAt > 0) && (SupportPicker.hpPct(mob) < surrenderAt))
		{
			surrender(mob);
			return true;
		}
		if(broken && (!action.equals("BERSERK")))
			return true;
		final String why = breaking(mob);
		if(why == null)
			return true;
		if((chance < 100) && (CMLib.dice().rollPercentage() > chance))
			return true;
		broken = true;
		state = why;
		say(mob, msgText);
		if(action.equals("SURRENDER"))
			surrender(mob);
		else
		if(action.equals("BERSERK"))
			goBerserk(mob);
		else
		{
			final Room RR = rallyRoom(mob);
			fightRoom = R;
			fledFrom.clear();
			fledFrom.addAll(Threat.enemies(mob));
			mob.doCommand(new XVector<String>("FLEE"), MUDCmdProcessor.METAFLAG_FORCED);
			if((RR != null) && (mob.location() != R))
			{
				regroupDown = regroup;
				returnTicks = 0;
				if(mob.location() != RR)
					CMLib.tracking().autoTrack(mob, RR);
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
		if(((msg.sourceMinor() == CMMsg.TYP_DEATH) || (msg.othersMinor() == CMMsg.TYP_DEATH)) && (msg.source() != mob))
		{
			if((msg.source() == leader) || (msg.source() == leaderOf(mob)))
				leaderDied = true;
		}
	}
}
