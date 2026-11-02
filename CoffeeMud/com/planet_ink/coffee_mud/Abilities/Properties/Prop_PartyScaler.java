package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * C6: a fight scaled to the party.  On a mob, or a room or area (whose NPCs
 * get it when they fight): BASE=n the party it is tuned for (3);
 * HP=+n%/MEMBER, DAMAGE=+n%/MEMBER and ATTACK=+n/MEMBER for each member over
 * (or under) BASE; LEVEL=AVG+n or MAX+n with MIN= and MAX=; ADDS="name/
 * MEMBER-ABOVE-n" one add (catalog or class) for each member over n.  The
 * party is the foe's group in the room, followers too.  It scales when the
 * fight starts and holds until it ends.
 */
public class Prop_PartyScaler extends Property
{
	@Override
	public String ID()
	{
		return "Prop_PartyScaler";
	}

	@Override
	public String name()
	{
		return "Scales to the party";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS | Ability.CAN_ROOMS | Ability.CAN_AREAS;
	}

	@Override
	public String accountForYourself()
	{
		return "Scales to the party it fights";
	}

	protected int		base		= 3;
	protected double	hpPct		= 0;
	protected double	dmgPct		= 0;
	protected int		attPer		= 0;
	protected String	levelFrom	= "";
	protected int		levelPlus	= 0;
	protected int		minLevel	= 0;
	protected int		maxLevel	= Integer.MAX_VALUE;
	protected String	adds		= "";
	protected int		addsAbove	= Integer.MAX_VALUE;

	// on a mob, while it fights
	protected int				members		= 0;
	protected int				partyLevel	= 0;
	protected int				outTicks	= 0;
	protected boolean			handed		= false;
	protected final List<MOB>	spawned		= new ArrayList<MOB>();

	/** a number, signed, from "+25%/MEMBER" */
	protected static double perMember(final String s)
	{
		final String n = s.toUpperCase().replace("/MEMBER", "").replace("%", "").replace("+", "").trim();
		return CMath.s_double(n);
	}

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		final Map<String, String> q = CMParms.parseExactParms((newText == null) ? "" : newText);
		base = Math.max(1, CMParms.getParmInt(q, "BASE", 3));
		hpPct = perMember(CMParms.getParmStr(q, "HP", "0"));
		dmgPct = perMember(CMParms.getParmStr(q, "DAMAGE", "0"));
		attPer = (int)Math.round(perMember(CMParms.getParmStr(q, "ATTACK", "0")));
		final String lv = CMParms.getParmStr(q, "LEVEL", "").trim().toUpperCase().replace(" ", "");
		levelFrom = "";
		levelPlus = 0;
		if(lv.startsWith("AVG") || lv.startsWith("MAX"))
		{
			levelFrom = lv.substring(0, 3);
			final String r = lv.substring(3);
			levelPlus = r.startsWith("+") ? CMath.s_int(r.substring(1)) : (r.startsWith("-") ? -CMath.s_int(r.substring(1)) : 0);
		}
		minLevel = CMParms.getParmInt(q, "MIN", 0);
		maxLevel = CMParms.getParmInt(q, "MAX", Integer.MAX_VALUE);
		final String a = CMParms.getParmStr(q, "ADDS", "").trim();
		final int x = a.toUpperCase().indexOf("/MEMBER-ABOVE-");
		adds = (x < 0) ? a : a.substring(0, x).trim();
		addsAbove = (x < 0) ? (a.length() > 0 ? base : Integer.MAX_VALUE) : CMath.s_int(a.substring(x + 14).trim());
		handed = CMParms.getParmBool(q, "HANDED", false);
	}

	/** the party it fights: its foes' groups in the room, followers too */
	protected static List<MOB> party(final MOB mob)
	{
		final List<MOB> list = new ArrayList<MOB>();
		final Room R = mob.location();
		if(R == null)
			return list;
		final List<MOB> foes = new ArrayList<MOB>(Threat.enemies(mob));
		if((mob.getVictim() != null) && (!foes.contains(mob.getVictim())))
			foes.add(mob.getVictim());
		for(final MOB F : foes)
		{
			final MOB lead = F.getGroupLeader();
			for(final MOB G : ((lead != null) ? lead : F).getGroupMembers(new HashSet<MOB>()))
			{
				if((G != null) && (G.location() == R) && (!G.amDead()) && (!list.contains(G)) && (G != mob))
					list.add(G);
			}
			if(!list.contains(F))
				list.add(F);
		}
		return list;
	}

	/**
	 * @return how many it scaled to, or 0 when it does not fight
	 */
	public int members()
	{
		return members;
	}

	protected void engage(final MOB mob)
	{
		final List<MOB> party = party(mob);
		members = Math.max(1, party.size());
		int sum = 0, best = 0;
		for(final MOB M : party)
		{
			sum += M.phyStats().level();
			best = Math.max(best, M.phyStats().level());
		}
		partyLevel = levelFrom.equals("MAX") ? best : (party.isEmpty() ? 0 : (sum / party.size()));
		final int oldMax = Math.max(1, mob.maxState().getHitPoints());
		final int oldCur = mob.curState().getHitPoints();
		mob.recoverPhyStats();
		mob.recoverCharStats();
		mob.recoverMaxState();
		final int newMax = mob.maxState().getHitPoints();
		mob.curState().setHitPoints(Math.max(1, (int)Math.round((double)oldCur * newMax / oldMax)));
		// the adds
		final int n = (adds.length() == 0) ? 0 : Math.max(0, members - addsAbove);
		final Room R = mob.location();
		for(int i = 0; (i < n) && (R != null); i++)
		{
			for(final MOB A : Encounters.spawnSpec(adds, R, mob.getVictim()))
			{
				A.addTag("SUMMONED");
				A.setFollowing(mob);
				spawned.add(A);
				if(mob.getVictim() != null)
					CMLib.combat().postAttack(A, mob.getVictim(), A.fetchWieldedItem());
			}
		}
	}

	protected void disengage(final MOB mob)
	{
		members = 0;
		outTicks = 0;
		for(final MOB A : spawned)
		{
			if((!A.amDestroyed()) && (!A.amDead()))
			{
				if(A.location() != null)
					A.location().show(A, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> fade(s) away."));
				A.destroy();
			}
		}
		spawned.clear();
		mob.recoverPhyStats();
		mob.recoverCharStats();
		mob.recoverMaxState();
		if(mob.curState().getHitPoints() > mob.maxState().getHitPoints())
			mob.curState().setHitPoints(mob.maxState().getHitPoints());
		if(handed)
		{
			mob.delEffect(this); // a room's or area's: until the next fight
			setAffectedOne(null);
		}
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		if((members <= 0) || (!(host instanceof MOB)))
			return;
		final int delta = members - base;
		if(dmgPct != 0)
			stats.setDamage(Math.max(1, (int)Math.round(stats.damage() * Math.max(0.1, 1.0 + (dmgPct * delta / 100.0)))));
		if(attPer != 0)
			stats.setAttackAdjustment(stats.attackAdjustment() + (attPer * delta));
		if(levelFrom.length() > 0)
			stats.setLevel(Math.max(Math.max(1, minLevel), Math.min(maxLevel, partyLevel + levelPlus)));
	}

	@Override
	public void affectCharState(final MOB host, final CharState maxState)
	{
		super.affectCharState(host, maxState);
		if((members <= 0) || (hpPct == 0))
			return;
		final int delta = members - base;
		maxState.setHitPoints(Math.max(1, (int)Math.round(maxState.getHitPoints() * Math.max(0.1, 1.0 + (hpPct * delta / 100.0)))));
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		// on a room or an area: an NPC that fights the player's side gets it
		if(((affected instanceof Room) || (affected instanceof Area))
		&& (msg.target() instanceof MOB) && (msg.source() != null)
		&& CMath.bset(msg.targetMajor(), CMMsg.MASK_MALICIOUS))
		{
			final MOB T = (MOB)msg.target();
			final MOB S = msg.source();
			final MOB npc = GameDifficulty.playerSide(S) ? T : (GameDifficulty.playerSide(T) ? S : null);
			if((npc != null) && npc.isMonster() && (!GameDifficulty.playerSide(npc)) && (npc.fetchEffect(ID()) == null))
			{
				final Ability A = CMClass.getAbility(ID());
				A.setMiscText(text() + " HANDED=true");
				A.setSavable(false);
				npc.addEffect(A);
			}
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((tickID != Tickable.TICKID_MOB) || (!(affected instanceof MOB)))
			return true;
		final MOB mob = (MOB)affected;
		if(mob.isInCombat())
		{
			outTicks = 0;
			if(members <= 0)
				engage(mob);
		}
		else
		if((members > 0) && (++outTicks >= 2))
			disengage(mob); // the fight is over
		return true;
	}
}
