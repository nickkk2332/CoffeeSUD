package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
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
 * A14: holds part of a named resource pool for as long as it is on its host:
 * an aura or stance on a character (OF=SELF), or a minion's upkeep from the
 * one it follows (OF=LEADER).  POOL= the pool (it must be reservable),
 * AMOUNT= how much, NAME= what RESOURCES calls it, EFFECTS= what it gives
 * while it fits (Prop_SpellAdder's form: stat, resistance and message
 * properties, worked through this one), ONSUSPEND=PAUSE (the effects stop
 * until there is room again) or LEAVE (a minion is dismissed, a character's
 * reservation taken off).  Nothing is stored in the pool: the reservation is
 * this property, so it ends with it, is saved with its host, and one that
 * no longer fits (the pool drained, its maximum lowered) is suspended.
 */
public class Prop_Reserve extends Property implements NamedResources.Reserver
{
	@Override
	public String ID()
	{
		return "Prop_Reserve";
	}

	@Override
	public String name()
	{
		return "Reserving a resource";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_MOBS;
	}

	protected String			pool		= "";
	protected long				amount		= 0;
	protected String			rname		= "";
	protected boolean			ofLeader	= false;
	protected boolean			leave		= false;
	protected final List<Ability>	effects	= new ArrayList<Ability>();
	/** whether it fitted at the last check (its effects work only then) */
	protected boolean			fitting		= false;
	protected boolean			checked		= false;
	/** the game time it was made: its place in line (kept in its text as SINCE=) */
	protected long				since		= 0;
	private static long			lastSince	= 0;
	protected String			spec		= "";

	@Override
	public void setMiscText(final String newText)
	{
		final String t = (newText == null) ? "" : newText;
		spec = t.replaceAll("(?i)\\s*\\bSINCE=\\S*", "").trim();
		super.setMiscText(spec);
		final Map<String, String> q = CMParms.parseExactParms(t);
		if(q.containsKey("SINCE"))
			since = Math.max(0, CMath.s_long(CMParms.getParmStr(q, "SINCE", "0")));
		pool = CMParms.getParmStr(q, "POOL", "").trim();
		amount = Math.max(0, CMParms.getParmInt(q, "AMOUNT", 0));
		rname = CMParms.getParmStr(q, "NAME", "").trim();
		ofLeader = CMParms.getParmStr(q, "OF", "SELF").trim().equalsIgnoreCase("LEADER");
		leave = CMParms.getParmStr(q, "ONSUSPEND", "PAUSE").trim().equalsIgnoreCase("LEAVE");
		effects.clear();
		final String spec = CMParms.getParmStr(q, "EFFECTS", "");
		if(spec.trim().length() > 0)
		{
			for(final CMObject o : CMLib.coffeeMaker().getCodedSpellsOrBehaviors(spec))
			{
				if(o instanceof Ability)
					effects.add((Ability)o);
			}
		}
		checked = false;
	}

	@Override
	public String text()
	{
		return (since > 0) ? (spec + " SINCE=" + since) : spec;
	}

	@Override
	public long reservedSince()
	{
		return since;
	}

	@Override
	public String reservationSpec()
	{
		return spec;
	}

	@Override
	public String reservedPool()
	{
		return pool;
	}

	@Override
	public long reservedAmount()
	{
		return amount;
	}

	@Override
	public String reservationName()
	{
		if(rname.length() > 0)
			return rname;
		return (ofLeader && (affected != null)) ? affected.name() : CMLib.lang().L("a reservation");
	}

	@Override
	public boolean reservesFromLeader()
	{
		return ofLeader;
	}

	/** @return whom the pool belongs to: the host, or the one it follows (or was summoned by) */
	public MOB owner()
	{
		if(!(affected instanceof MOB))
			return null;
		final MOB M = (MOB)affected;
		if(!ofLeader)
			return M;
		return (M.amFollowing() == null) ? null : M.amFollowing();
	}

	/** @return whether it fits now */
	public boolean fits()
	{
		return NamedResources.fits(owner(), this);
	}

	@Override
	public String accountForYourself()
	{
		return CMLib.lang().L("Holds @x1 @x2 while it lasts", "" + amount, pool) + (fitting ? "" : CMLib.lang().L(" (suspended: not enough)"));
	}

	/** checks whether it fits, and says so when that changes */
	protected void check()
	{
		if(!(affected instanceof MOB))
			return;
		final MOB host = (MOB)affected;
		final boolean now = fits();
		if(checked && (now == fitting))
			return;
		final boolean was = fitting;
		fitting = now;
		final boolean first = !checked;
		checked = true;
		final MOB owner = owner();
		if((!now) && leave && (owner != null))
		{
			owner.tell(CMLib.lang().L("There is not enough @x1 for @x2, and it goes.", pool, reservationName()));
			if(ofLeader)
			{
				final Room R = host.location();
				if(R != null)
					R.show(host, null, CMMsg.MSG_OK_VISUAL, CMLib.lang().L("<S-NAME> fade(s) away."));
				host.destroy();
			}
			else
				host.delEffect(this);
			return;
		}
		if((owner != null) && ((!first) || (!now)))
			owner.tell(now ? CMLib.lang().L("@x1 holds again.", CMStrings.capitalizeFirstLetter(reservationName()))
							: CMLib.lang().L("There is not enough @x1 for @x2: it is suspended.", pool, reservationName()));
		if(was != now)
		{
			host.recoverCharStats();
			host.recoverPhyStats();
			host.recoverMaxState();
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		if(tickID == Tickable.TICKID_MOB)
			check();
		if(fitting)
		{
			for(final Ability A : effects)
				A.tick(ticking, tickID);
		}
		return true;
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		for(final Ability A : effects)
			A.setAffectedOne(P);
		if(P == null)
			since = 0; // taken off: put on again, it goes to the back of the line
		else
		if(since <= 0)
		{
			// the game clock, kept apart from one made just before (in turn-based time it can stand still)
			synchronized(Prop_Reserve.class)
			{
				since = Math.max(Math.max(1, GameTime.now()), lastSince + 1);
				lastSince = since;
			}
		}
		checked = false;
		fitting = false;
	}

	@Override
	public void affectPhyStats(final Physical host, final PhyStats stats)
	{
		super.affectPhyStats(host, stats);
		if(fitting)
		{
			for(final Ability A : effects)
				A.affectPhyStats(host, stats);
		}
	}

	@Override
	public void affectCharStats(final MOB host, final CharStats stats)
	{
		super.affectCharStats(host, stats);
		if(fitting)
		{
			for(final Ability A : effects)
				A.affectCharStats(host, stats);
		}
	}

	@Override
	public void affectCharState(final MOB host, final CharState state)
	{
		super.affectCharState(host, state);
		if(fitting)
		{
			for(final Ability A : effects)
				A.affectCharState(host, state);
		}
	}

	@Override
	public boolean okMessage(final Environmental myHost, final CMMsg msg)
	{
		if(!super.okMessage(myHost, msg))
			return false;
		if(fitting)
		{
			for(final Ability A : effects)
			{
				if(!A.okMessage(myHost, msg))
					return false;
			}
		}
		return true;
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if(fitting)
		{
			for(final Ability A : effects)
				A.executeMsg(myHost, msg);
		}
	}
}
