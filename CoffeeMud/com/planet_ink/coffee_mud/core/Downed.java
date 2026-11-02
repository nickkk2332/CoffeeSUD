package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
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
 * C8: a downed state, and what happens when the whole party falls.
 * PLAYERDEATH's DOWNED x (y): one of the player's side at 0 hit points falls
 * for x rounds (Prop_Downed); an ally's REVIVE, or a heal of y hit points
 * (1), brings them back; when the foes that downed them are gone or dead and
 * no one of the party there still fights, they get up at 1; if x runs out,
 * the rest of the PLAYERDEATH list applies.  When every one of the party in
 * the room is down or dead, and one at least is down: PARTYDEFEAT_PROG on
 * the room's and area's scripts, if they have one; else coffeemud.ini's
 * PARTYDEFEAT list, the first that applies: RESTORE (offers the last save),
 * CHECKPOINT (the party wakes together at the leader's respawn point,
 * PARTYDEFEATGOLD percent of their gold gone), WAIT (each lies there until
 * their time runs out), DEATH (the default, and the last word).
 */
public final class Downed
{
	private Downed()
	{
	}

	/** set while one whose downing ran out dies by the rest of the list */
	private static final ThreadLocal<Boolean>	dying		= new ThreadLocal<Boolean>();
	/** set while a party's defeat is dealt with */
	private static final ThreadLocal<Boolean>	defeating	= new ThreadLocal<Boolean>();
	/** who downed each one */
	private static final Map<MOB, List<MOB>>	foes		= Collections.synchronizedMap(new WeakHashMap<MOB, List<MOB>>());

	/**
	 * @param M one of the player's side
	 * @return DOWNED's rounds and heal from the PLAYERDEATH list there, or null
	 */
	public static int[] rule(final MOB M)
	{
		for(final String w : CMParms.parseCommas(DeathRules.playerDeath(M).toUpperCase(), true))
		{
			if(w.startsWith("DOWNED"))
			{
				final List<String> p = CMParms.parse(w);
				final int x = (p.size() > 1) ? CMath.s_int(p.get(1)) : 3;
				final int y = (p.size() > 2) ? CMath.s_int(p.get(2)) : 1;
				return new int[] { Math.max(1, x), Math.max(1, y) };
			}
		}
		return null;
	}

	/**
	 * @param M a character
	 * @return whether they are down
	 */
	public static boolean isDown(final MOB M)
	{
		return (M != null) && (M.fetchEffect("Prop_Downed") != null);
	}

	/**
	 * Asked by postDeath: one of the player's side whose PLAYERDEATH list has
	 * DOWNED falls instead of dying.
	 * @param dead the one at 0 hit points
	 * @param killer who did it, or null
	 * @return true if they are down, not dead
	 */
	public static boolean down(final MOB dead, final MOB killer)
	{
		if((dead == null) || Boolean.TRUE.equals(dying.get()) || (!GameDifficulty.playerSide(dead)) || isDown(dead))
			return false;
		final int[] r = rule(dead);
		final Room R = dead.location();
		if((r == null) || (R == null))
			return false;
		final Ability A = CMClass.getAbility("Prop_Downed");
		if(A == null)
			return false;
		final List<MOB> them = new ArrayList<MOB>();
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB F = m.nextElement();
			if((F != null) && (F != dead) && ((F.getVictim() == dead) || (dead.getVictim() == F)) && (!them.contains(F)))
				them.add(F);
		}
		if((killer != null) && (killer != dead) && (!them.contains(killer)))
			them.add(killer);
		for(final MOB F : them)
		{
			if(F.getVictim() == dead)
				F.setVictim(null);
		}
		foes.put(dead, them);
		dead.makePeace(false);
		dead.curState().setHitPoints(1);
		A.setMiscText("TICKS=" + r[0] + " HEAL=" + r[1]);
		A.setSavable(false);
		dead.addEffect(A);
		dead.recoverPhyStats();
		R.show(dead, null, CMMsg.MSG_OK_VISUAL, CMLib.lang().L("<S-NAME> fall(s), downed!"));
		dead.tell(CMLib.lang().L("You are down!  An ally may REVIVE you, or heal you."));
		partyDefeatCheck(dead, R);
		return true;
	}

	/**
	 * Whether the fight that downed one is over: the foes that downed them
	 * are gone or dead, and no one of the party there still fights.
	 * @param M the one down
	 * @return true if it is over
	 */
	public static boolean fightOver(final MOB M)
	{
		final Room R = M.location();
		if(R == null)
			return true;
		final List<MOB> them = foes.get(M);
		if(them != null)
		{
			for(final MOB F : them)
			{
				if((F != null) && (!F.amDead()) && (!F.amDestroyed()) && (F.location() == R))
					return false;
			}
		}
		for(final MOB P : party(M, R))
		{
			if((P != M) && (!isDown(P)) && P.isInCombat())
				return false;
		}
		return true;
	}

	/**
	 * Brings one back up.
	 * @param M the one down
	 * @param hp their hit points after
	 */
	public static void revive(final MOB M, final int hp)
	{
		final Ability A = M.fetchEffect("Prop_Downed");
		if(A != null)
		{
			M.delEffect(A);
			A.setAffectedOne(null);
		}
		foes.remove(M);
		M.curState().setHitPoints(Math.max(1, Math.min(M.maxState().getHitPoints(), hp)));
		M.recoverPhyStats();
		if(M.location() != null)
			M.location().show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> get(s) back up!"));
	}

	/**
	 * Their time ran out: the rest of the PLAYERDEATH list applies.
	 * @param M the one down
	 */
	public static void die(final MOB M)
	{
		final Ability A = M.fetchEffect("Prop_Downed");
		if(A != null)
		{
			M.delEffect(A);
			A.setAffectedOne(null);
		}
		foes.remove(M);
		M.recoverPhyStats();
		M.curState().setHitPoints(0);
		final Boolean was = dying.get();
		dying.set(Boolean.TRUE);
		try
		{
			CMLib.combat().postDeath(null, M, null);
		}
		finally
		{
			dying.set(was);
		}
	}

	/**
	 * @param M one of the party
	 * @param R where they fell
	 * @return the party there: their group, followers too, in that room, and them
	 */
	public static List<MOB> party(final MOB M, final Room R)
	{
		final List<MOB> list = new ArrayList<MOB>();
		list.add(M);
		final MOB lead = M.getGroupLeader();
		for(final MOB G : ((lead != null) ? lead : M).getGroupMembers(new HashSet<MOB>()))
		{
			if((G != null) && (G.location() == R) && GameDifficulty.playerSide(G) && (!list.contains(G)))
				list.add(G);
		}
		return list;
	}

	/**
	 * Asked when one of the player's side died: if that was the last of the
	 * party standing, the party's defeat.
	 * @param M the one who died
	 * @param R where
	 */
	public static void afterDeath(final MOB M, final Room R)
	{
		if((M != null) && (R != null) && (!Boolean.TRUE.equals(dying.get())))
			partyDefeatCheck(M, R);
	}

	/**
	 * When one falls: if all of the party there is down or dead, and one at
	 * least is down, the party's defeat.
	 * @param M the one who fell
	 * @param R where
	 * @return true if it was the party's defeat
	 */
	public static boolean partyDefeatCheck(final MOB M, final Room R)
	{
		if((R == null) || Boolean.TRUE.equals(defeating.get()))
			return false;
		final List<MOB> party = party(M, R);
		boolean anyDown = false;
		for(final MOB P : party)
		{
			if(isDown(P))
				anyDown = true;
			else
			if((P != M) && (!P.amDead()))
				return false;
		}
		if(!anyDown)
			return false;
		defeating.set(Boolean.TRUE);
		try
		{
			for(final MOB P : party)
				P.tell(CMLib.lang().L("^HYour party has fallen.^?"));
			// a script of the room or area takes it
			if(ScriptEvents.partyDefeat(R, M))
				return true;
			final String rules = CMProps.instance().getProperty("PARTYDEFEAT");
			final List<String> list = CMParms.parseCommas((rules == null) ? "" : rules.toUpperCase(), true);
			list.add("DEATH");
			for(final String w : list)
			{
				if(w.equals("RESTORE"))
				{
					final String slot = SaveGame.currentSlot();
					if(slot != null)
					{
						for(final MOB P : party)
							P.tell(CMLib.lang().L("RESTORE @x1 brings back your last save.", slot));
						return true;
					}
				}
				else
				if(w.equals("CHECKPOINT"))
				{
					final MOB lead = (M.getGroupLeader() != null) ? M.getGroupLeader() : M;
					final Room to = Checkpoints.roomOf(lead);
					if(to != null)
					{
						final int pct = CMath.s_int(CMProps.instance().getProperty("PARTYDEFEATGOLD", "10"));
						for(final MOB P : party)
						{
							if(!isDown(P))
								continue;
							for(final MOB F : Threat.enemies(P))
							{
								if(F.getVictim() == P)
									F.setVictim(null);
							}
							P.makePeace(false);
							revive(P, 1);
							final double gold = CMLib.beanCounter().getTotalAbsoluteNativeValue(P);
							if((pct > 0) && (gold > 0))
								CMLib.beanCounter().subtractMoney(P, gold * pct / 100.0);
							to.bringMobHere(P, false);
							P.tell(CMLib.lang().L("You wake at @x1.", to.displayText(P)));
						}
						return true;
					}
				}
				else
				if(w.equals("WAIT"))
					return true;
				else
				if(w.equals("DEATH"))
				{
					for(final MOB P : party)
					{
						if(isDown(P))
							die(P);
					}
					return true;
				}
			}
			return true;
		}
		finally
		{
			defeating.set(null);
		}
	}
}
