package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
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
 * C21: leash and reset for bosses.  When none of those it fought has been
 * within RADIUS=n rooms (1) for IDLE=n ticks (5), the mob goes back to its
 * start room (HOME=true), heals HEAL=n% of its hit points (100) and its
 * mana and moves, sheds what was cast on it to harm it, loses the adds it
 * summoned (DESPAWNADDS=true: NPCs following it that have no home or are
 * tagged SUMMONED), forgets its threat, and its scripts' RESET_PROG runs.
 * ROOM=true: every NPC of its room is reset with it; on a room, every NPC
 * there.
 */
public class EncounterReset extends StdBehavior
{
	@Override
	public String ID()
	{
		return "EncounterReset";
	}

	@Override
	public String name()
	{
		return "resetting when its foes leave";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_MOBS | Behavior.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "resetting when its foes leave";
	}

	protected int					radius		= 1;
	protected int					idleTicks	= 5;
	protected int					heal		= 100;
	protected boolean				home		= true;
	protected boolean				despawnAdds	= true;
	protected boolean				roomWide	= false;

	// the fight
	protected boolean				engaged		= false;
	protected int					idle		= 0;
	protected final Set<MOB>		foes		= new HashSet<MOB>();

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final String p = (newParms == null) ? "" : newParms;
		radius = Math.max(0, CMParms.getParmInt(p, "RADIUS", 1));
		idleTicks = Math.max(1, CMParms.getParmInt(p, "IDLE", 5));
		heal = Math.max(0, CMParms.getParmInt(p, "HEAL", 100));
		home = CMParms.getParmBool(p, "HOME", true);
		despawnAdds = CMParms.getParmBool(p, "DESPAWNADDS", true);
		roomWide = CMParms.getParmBool(p, "ROOM", false);
	}

	/** the NPCs it looks after: itself, or those of its room */
	protected List<MOB> charges(final Tickable host)
	{
		final List<MOB> list = new ArrayList<MOB>();
		Room R = null;
		if(host instanceof MOB)
		{
			list.add((MOB)host);
			if(roomWide)
				R = (((MOB)host).getStartRoom() != null) ? ((MOB)host).getStartRoom() : ((MOB)host).location();
		}
		else
		if(host instanceof Room)
			R = (Room)host;
		if(R != null)
		{
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && M.isMonster() && (!GameDifficulty.playerSide(M)) && (!list.contains(M)))
					list.add(M);
			}
		}
		return list;
	}

	/** the rooms within reach of one */
	protected Set<Room> near(final Room R)
	{
		final Set<Room> rooms = new HashSet<Room>();
		if(R == null)
			return rooms;
		rooms.add(R);
		List<Room> edge = new ArrayList<Room>();
		edge.add(R);
		for(int step = 0; step < radius; step++)
		{
			final List<Room> next = new ArrayList<Room>();
			for(final Room E : edge)
			{
				for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				{
					final Room R2 = E.getRoomInDir(d);
					if((R2 != null) && (E.getExitInDir(d) != null) && rooms.add(R2))
						next.add(R2);
				}
			}
			edge = next;
		}
		return rooms;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if((ticking instanceof MOB) && (tickID != Tickable.TICKID_MOB))
			return true;
		final List<MOB> charges = charges(ticking);
		boolean fighting = false;
		for(final MOB M : charges)
		{
			if(M.isInCombat())
			{
				fighting = true;
				for(final MOB F : Threat.enemies(M))
					foes.add(F);
				if(M.getVictim() != null)
					foes.add(M.getVictim());
			}
		}
		if(fighting)
		{
			engaged = true;
			idle = 0;
			return true;
		}
		if(!engaged)
			return true;
		// any of those it fought still about?
		final Set<Room> rooms = new HashSet<Room>();
		for(final MOB M : charges)
			rooms.addAll(near(M.location()));
		for(final Iterator<MOB> i = foes.iterator(); i.hasNext();)
		{
			final MOB F = i.next();
			if(F.amDead() || F.amDestroyed())
				i.remove();
			else
			if(rooms.contains(F.location()))
			{
				idle = 0;
				return true;
			}
		}
		if(++idle < idleTicks)
			return true;
		for(final MOB M : charges)
			reset(M);
		engaged = false;
		idle = 0;
		foes.clear();
		return true;
	}

	/**
	 * Puts one back as it was before the fight.
	 * @param M the NPC
	 */
	protected void reset(final MOB M)
	{
		if(M.amDead() || M.amDestroyed())
			return;
		M.makePeace(true);
		if(home && (M.getStartRoom() != null) && (M.location() != M.getStartRoom()))
		{
			final Room was = M.location();
			if(was != null)
				was.show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> leave(s)."));
			M.getStartRoom().bringMobHere(M, false);
			M.location().show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> return(s) to <S-HIS-HER> place."));
		}
		// what was cast on it to harm it
		final List<Ability> harm = new ArrayList<Ability>();
		for(final Enumeration<Ability> a = M.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if((A != null) && A.canBeUninvoked() && (!A.isNowAnAutoEffect()) && (A.abstractQuality() == Ability.QUALITY_MALICIOUS))
				harm.add(A);
		}
		for(final Ability A : harm)
			A.unInvoke();
		// the adds it summoned
		if(despawnAdds)
		{
			final List<MOB> adds = new ArrayList<MOB>();
			for(final Enumeration<Pair<MOB, Short>> f = M.followers(); f.hasMoreElements();)
			{
				final MOB F = f.nextElement().first;
				if((F != null) && F.isMonster() && (!GameDifficulty.playerSide(F)) && ((F.getStartRoom() == null) || F.hasTag("SUMMONED")))
					adds.add(F);
			}
			for(final MOB F : adds)
			{
				if(F.location() != null)
					F.location().show(F, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> fade(s) away."));
				F.destroy();
			}
		}
		M.recoverMaxState();
		if(heal >= 100)
			M.resetToMaxState();
		else
		{
			final int want = (int)Math.round(M.maxState().getHitPoints() * heal / 100.0);
			if(M.curState().getHitPoints() < want)
				M.curState().setHitPoints(want);
			M.curState().setMana(M.maxState().getMana());
			M.curState().setMovement(M.maxState().getMovement());
		}
		Threat.forget(M);
		for(final Enumeration<Behavior> b = M.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if((B != null) && (B != this))
				B.setStat("ENCOUNTERRESET", ""); // C3: a boss's phases rearm
		}
		ScriptEvents.encounterReset(M);
	}
}
