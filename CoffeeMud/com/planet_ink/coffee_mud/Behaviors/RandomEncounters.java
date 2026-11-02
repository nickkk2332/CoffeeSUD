package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * C9: random encounters as the party moves, from an encounter table
 * (core/Encounters, world/encounters/NAME.enc).  On an area or a room:
 * TABLE=name, CHANCE=n% a room entered (10), COOLDOWN=n rooms after one
 * (or COOLDOWN=nDAYS, game days), LOCALES="WOODS,PLAINS" the kinds of room,
 * TRIGGER="mask" on the one who comes in, MAXACTIVE=n of its monsters about
 * at once (6), DESPAWN=n ticks after the party leaves them (10), BUDGET=pct
 * of the party's power in monsters.
 */
public class RandomEncounters extends StdBehavior
{
	@Override
	public String ID()
	{
		return "RandomEncounters";
	}

	@Override
	public String name()
	{
		return "random encounters";
	}

	@Override
	protected int canImproveCode()
	{
		return Behavior.CAN_AREAS | Behavior.CAN_ROOMS;
	}

	@Override
	public String accountForYourself()
	{
		return "random encounters";
	}

	protected String						table		= "";
	protected int							chance		= 10;
	protected int							cooldown	= 0;
	protected int							cooldownDays= 0;
	protected Set<String>					locales		= null;
	protected MaskingLibrary.CompiledZMask	trigger		= null;
	protected int							maxActive	= 6;
	protected int							despawn		= 10;
	protected int							budget		= 0;

	// the state
	protected int							roomsSince	= Integer.MAX_VALUE / 2;
	protected long							lastDay		= -1;
	protected final Map<PhysicalAgent, int[]>	active	= new LinkedHashMap<PhysicalAgent, int[]>();
	protected String						lastRolled	= "";
	protected PhysicalAgent					host		= null;
	protected final List<Object[]>			pending		= new ArrayList<Object[]>();

	@Override
	public void setParms(final String newParms)
	{
		super.setParms(newParms);
		final Map<String, String> q = CMParms.parseExactParms((newParms == null) ? "" : newParms);
		table = CMParms.getParmStr(q, "TABLE", "").trim();
		chance = CMath.s_int(CMParms.getParmStr(q, "CHANCE", "10").replace("%", "").trim());
		final String c = CMParms.getParmStr(q, "COOLDOWN", "0").trim().toUpperCase();
		if(c.endsWith("DAYS") || c.endsWith("DAY") || c.endsWith("D"))
		{
			cooldownDays = CMath.s_int(c.replaceAll("[^0-9]", ""));
			cooldown = 0;
		}
		else
		{
			cooldown = CMath.s_int(c);
			cooldownDays = 0;
		}
		final String l = CMParms.getParmStr(q, "LOCALES", "").trim();
		locales = null;
		if(l.length() > 0)
		{
			locales = new HashSet<String>();
			for(final String s : CMParms.parseCommas(l.toUpperCase(), true))
				locales.add(s);
		}
		final String t = CMParms.getParmStr(q, "TRIGGER", "").trim();
		trigger = (t.length() == 0) ? null : CMLib.masking().getPreCompiledMask(t);
		maxActive = Math.max(1, CMParms.getParmInt(q, "MAXACTIVE", 6));
		despawn = Math.max(1, CMParms.getParmInt(q, "DESPAWN", 10));
		budget = Math.max(0, CMath.s_int(CMParms.getParmStr(q, "BUDGET", "0").replace("%", "").trim()));
		roomsSince = Integer.MAX_VALUE / 2; // new rules: no cooldown running
		lastDay = -1;
	}

	/** the kind of a room, as LOCALES= names them */
	protected static String localeOf(final Room R)
	{
		final int d = R.domainType();
		if((d & Room.INDOORS) != 0)
		{
			final int i = d - Room.INDOORS;
			return ((i >= 0) && (i < Room.DOMAIN_INDOORS_DESCS.length)) ? Room.DOMAIN_INDOORS_DESCS[i] : "";
		}
		return ((d >= 0) && (d < Room.DOMAIN_OUTDOOR_DESCS.length)) ? Room.DOMAIN_OUTDOOR_DESCS[d] : "";
	}

	/** monsters of its encounters still about */
	protected int activeMobs()
	{
		int n = 0;
		for(final PhysicalAgent P : active.keySet())
		{
			if((P instanceof MOB) && (!P.amDestroyed()) && (!((MOB)P).amDead()))
				n++;
		}
		return n;
	}

	protected long today(final Room R)
	{
		final TimeClock C = R.getArea().getTimeObj();
		return ((long)C.getYear() * C.getMonthsInYear() + C.getMonth()) * C.getDaysInMonth() + C.getDayOfMonth();
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((msg.targetMinor() != CMMsg.TYP_ENTER) || (!(msg.target() instanceof Room)) || (table.length() == 0))
			return;
		final Room R = (Room)msg.target();
		final MOB who = msg.source();
		if((who == null) || (!GameDifficulty.playerSide(who)))
			return;
		if((myHost instanceof Room) && (myHost != R))
			return;
		if((myHost instanceof Area) && (!((Area)myHost).inMyMetroArea(R.getArea())))
			return;
		// a party rolls once: its leader, not those who follow it in
		final MOB lead = who.amFollowing();
		if((lead != null) && (lead != who) && GameDifficulty.playerSide(lead) && (lead.location() == R))
			return;
		host = (myHost instanceof PhysicalAgent) ? (PhysicalAgent)myHost : null;
		if(cooldown > 0)
		{
			if(roomsSince < cooldown)
			{
				roomsSince++;
				return;
			}
		}
		if((cooldownDays > 0) && (lastDay >= 0) && ((today(R) - lastDay) < cooldownDays))
			return;
		if((locales != null) && (!locales.contains(localeOf(R))))
			return;
		if((trigger != null) && (!CMLib.masking().maskCheck(trigger, who, true)))
			return;
		if(activeMobs() >= maxActive)
			return;
		if(CMLib.dice().rollPercentage() > chance)
			return;
		// on its next tick, the move done: they see the room, then what comes
		synchronized(pending)
		{
			pending.add(new Object[] { R, who });
		}
	}

	/**
	 * Rolls the table for one who came into a room.
	 * @param R the room
	 * @param who who came in
	 * @return what came of it
	 */
	public Encounters.Result encounter(final Room R, final MOB who)
	{
		final Encounters.Table T = Encounters.table(table);
		if(T == null)
		{
			Log.errOut("RandomEncounters", "no encounter table '" + table + "' (world/encounters/" + table + ".enc)");
			return new Encounters.Result();
		}
		final Encounters.Result res = Encounters.roll(T, R, who, host, budget);
		if(res.entry == null)
			return res;
		roomsSince = 0;
		lastDay = today(R);
		lastRolled = res.entry.line;
		for(final PhysicalAgent P : res.spawned)
			active.put(P, new int[] { 0 });
		return res;
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(!super.tick(ticking, tickID))
			return false;
		final List<Object[]> due;
		synchronized(pending)
		{
			due = new ArrayList<Object[]>(pending);
			pending.clear();
		}
		for(final Object[] o : due)
		{
			final Room R = (Room)o[0];
			final MOB who = (MOB)o[1];
			if((who.location() == R) && (!who.amDead()) && (activeMobs() < maxActive))
				encounter(R, who); // not if they went on already
		}
		for(final Iterator<Map.Entry<PhysicalAgent, int[]>> i = active.entrySet().iterator(); i.hasNext();)
		{
			final Map.Entry<PhysicalAgent, int[]> e = i.next();
			final PhysicalAgent P = e.getKey();
			if(P.amDestroyed() || ((P instanceof MOB) && ((MOB)P).amDead()))
			{
				i.remove();
				continue;
			}
			if((P instanceof Item) && (!(((Item)P).owner() instanceof Room)))
			{
				i.remove(); // taken: it is theirs now
				continue;
			}
			final Room R = CMLib.map().roomLocation(P);
			boolean party = false;
			if(R != null)
			{
				for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
				{
					final MOB M = m.nextElement();
					if((M != null) && (M != P) && GameDifficulty.playerSide(M))
					{
						party = true;
						break;
					}
				}
			}
			if(party || ((P instanceof MOB) && ((MOB)P).isInCombat()))
			{
				e.getValue()[0] = 0;
				continue;
			}
			if(++e.getValue()[0] >= despawn)
			{
				i.remove();
				P.destroy(); // gone, unseen
			}
		}
		return true;
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("LAST"))
			return lastRolled;
		if(code.equalsIgnoreCase("ACTIVE"))
			return "" + activeMobs();
		return super.getStat(code);
	}
}
