package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
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
 * C4: the spawn rules RandomMonsters and RandomItems share.
 * CATALOG="grey wolf=4,dire wolf=1" picks catalog entries by weight;
 * WHEN="[mask]" is tested on the room a spawn would go to;
 * DESPAWN=WHENFALSE|DAWN|TICKS=n makes a spawn leave, unseen;
 * NEAR=PARTY:n spawns within n rooms of the player's side;
 * MAXAREA=n caps the spawns of the area; LEVEL=PARTY(+n|-n|+-n) sets a
 * spawned mob's level from the party's.
 */
public class SpawnRules
{
	public static final int	DESPAWN_NONE		= 0;
	public static final int	DESPAWN_WHENFALSE	= 1;
	public static final int	DESPAWN_DAWN		= 2;
	public static final int	DESPAWN_TICKS		= 3;

	/** the tag on what a spawner made */
	public static final String	TAG				= "SPAWNED";

	protected final List<String>			names		= new ArrayList<String>();
	protected final List<Integer>			weights		= new ArrayList<Integer>();
	protected MaskingLibrary.CompiledZMask	when		= null;
	protected int							despawn		= DESPAWN_NONE;
	protected int							despawnTicks= 0;
	protected int							near		= -1;
	protected int							maxArea		= 0;
	protected boolean						levelParty	= false;
	protected int							levelLo		= 0;
	protected int							levelHi		= 0;

	// the spawner's own ticks, and the tick each of its spawns came on
	protected long							ticks		= 0;
	protected final Map<Physical, Long>		born		= new WeakHashMap<Physical, Long>();

	/**
	 * Reads the rules from a spawner's parameters (the part before its ;).
	 * @param parms the parameters
	 * @return the rules
	 */
	public static SpawnRules parse(final String parms)
	{
		final SpawnRules S = new SpawnRules();
		final Map<String, String> q = CMParms.parseExactParms((parms == null) ? "" : parms);
		for(final String e : CMParms.parseCommas(CMParms.getParmStr(q, "CATALOG", ""), true))
		{
			String n = e.trim();
			int w = 1;
			final int x = n.lastIndexOf('=');
			if((x > 0) && CMath.isInteger(n.substring(x + 1).trim()))
			{
				w = CMath.s_int(n.substring(x + 1).trim());
				n = n.substring(0, x).trim();
			}
			if((n.length() > 0) && (w > 0))
			{
				S.names.add(n);
				S.weights.add(Integer.valueOf(w));
			}
		}
		final String w = CMParms.getParmStr(q, "WHEN", "").trim();
		S.when = (w.length() == 0) ? null : CMLib.masking().getPreCompiledMask(w);
		final String d = CMParms.getParmStr(q, "DESPAWN", "").trim().toUpperCase();
		if(d.equals("WHENFALSE"))
			S.despawn = DESPAWN_WHENFALSE;
		else
		if(d.equals("DAWN"))
			S.despawn = DESPAWN_DAWN;
		else
		if(d.startsWith("TICKS"))
		{
			S.despawn = DESPAWN_TICKS;
			S.despawnTicks = Math.max(1, CMath.s_int(d.substring(5).replace("=", "").replace(":", "").trim()));
		}
		final String nr = CMParms.getParmStr(q, "NEAR", "").trim().toUpperCase();
		if(nr.startsWith("PARTY"))
			S.near = (nr.indexOf(':') > 0) ? Math.max(0, CMath.s_int(nr.substring(nr.indexOf(':') + 1).trim())) : 3;
		S.maxArea = Math.max(0, CMParms.getParmInt(q, "MAXAREA", 0));
		final String lv = CMParms.getParmStr(q, "LEVEL", "").trim().toUpperCase().replace(" ", "");
		if(lv.startsWith("PARTY"))
		{
			S.levelParty = true;
			final String r = lv.substring(5);
			if(r.startsWith("±") || r.startsWith("+-") || r.startsWith("-+"))
			{
				final int n = CMath.s_int(r.substring(r.startsWith("±") ? 1 : 2));
				S.levelLo = -n;
				S.levelHi = n;
			}
			else
			if(r.startsWith("+"))
				S.levelLo = S.levelHi = CMath.s_int(r.substring(1));
			else
			if(r.startsWith("-"))
				S.levelLo = S.levelHi = -CMath.s_int(r.substring(1));
		}
		return S;
	}

	/**
	 * @return whether CATALOG= names the spawns
	 */
	public boolean hasCatalog()
	{
		return names.size() > 0;
	}

	/**
	 * @return CATALOG='s names, in order
	 */
	public List<String> catalogNames()
	{
		return names;
	}

	/**
	 * @return whether the rules choose rooms themselves: NEAR=, or WHEN=
	 */
	public boolean picksRooms()
	{
		return (near >= 0) || (when != null);
	}

	/**
	 * Picks one of the catalog entries, by weight.
	 * @param have which of them could be made (the others are skipped)
	 * @return its index, or -1
	 */
	public int pick(final boolean[] have)
	{
		int total = 0;
		for(int i = 0; i < weights.size(); i++)
		{
			if((have == null) || ((i < have.length) && have[i]))
				total += weights.get(i).intValue();
		}
		if(total <= 0)
			return -1;
		int roll = CMLib.dice().roll(1, total, 0);
		for(int i = 0; i < weights.size(); i++)
		{
			if((have != null) && ((i >= have.length) || (!have[i])))
				continue;
			roll -= weights.get(i).intValue();
			if(roll <= 0)
				return i;
		}
		return -1;
	}

	/**
	 * @param R a room
	 * @return whether WHEN= lets a spawn be there now
	 */
	public boolean roomOk(final Room R)
	{
		return (R != null) && ((when == null) || CMLib.masking().maskCheck(when, R, true));
	}

	/**
	 * The player's side in an area: characters with player stats, and those
	 * who follow them.
	 * @param A the area
	 * @return them
	 */
	public static List<MOB> party(final Area A)
	{
		final List<MOB> list = new ArrayList<MOB>();
		if(A == null)
			return list;
		for(final Enumeration<Room> r = A.getMetroMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && (!M.amDead()) && GameDifficulty.playerSide(M))
					list.add(M);
			}
		}
		return list;
	}

	/**
	 * A room for a spawn in an area: within NEAR= of the party, or any, and
	 * one WHEN= lets it be in.
	 * @param A the area
	 * @param ok the spawner's own test of a room, or null
	 * @return the room, or null
	 */
	public Room pickRoom(final Area A, final java.util.function.Predicate<Room> ok)
	{
		if(A == null)
			return null;
		if(near >= 0)
		{
			final List<Room> rooms = new ArrayList<Room>();
			for(final MOB M : party(A))
			{
				for(final Room R : within(M.location(), near, A))
				{
					if((!rooms.contains(R)) && roomOk(R) && ((ok == null) || ok.test(R)))
						rooms.add(R);
				}
			}
			return rooms.isEmpty() ? null : rooms.get(CMLib.dice().roll(1, rooms.size(), -1));
		}
		for(int tries = 0; tries < 100; tries++)
		{
			final Room R = A.getRandomMetroRoom();
			if(roomOk(R) && ((ok == null) || ok.test(R)))
				return R;
		}
		return null;
	}

	/** the rooms within so many steps of one, by open exits, in the area */
	protected static List<Room> within(final Room from, final int steps, final Area A)
	{
		final List<Room> rooms = new ArrayList<Room>();
		if(from == null)
			return rooms;
		rooms.add(from);
		List<Room> edge = new ArrayList<Room>(rooms);
		for(int s = 0; s < steps; s++)
		{
			final List<Room> next = new ArrayList<Room>();
			for(final Room E : edge)
			{
				for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
				{
					final Room R2 = E.getRoomInDir(d);
					final Exit X = E.getExitInDir(d);
					if((R2 != null) && (X != null) && X.isOpen() && (!rooms.contains(R2)) && A.inMyMetroArea(R2.getArea()))
					{
						rooms.add(R2);
						next.add(R2);
					}
				}
			}
			edge = next;
		}
		return rooms;
	}

	/**
	 * @param A the area
	 * @return whether MAXAREA= says the area has spawns enough
	 */
	public boolean areaFull(final Area A)
	{
		if((maxArea <= 0) || (A == null))
			return false;
		int n = 0;
		for(final Enumeration<Room> r = A.getMetroMap(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && M.hasTag(TAG))
					n++;
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if((I != null) && I.hasTag(TAG))
					n++;
			}
		}
		return n >= maxArea;
	}

	/**
	 * Makes a spawned mob the party's level, by LEVEL=PARTY; its stats follow
	 * the level formulas.  Done once it is alive, since bringing a catalog
	 * copy to life makes it over from the catalog.
	 * @param M the mob
	 * @param A the area of the party
	 */
	public void level(final MOB M, final Area A)
	{
		if(!levelParty)
			return;
		int best = 0;
		for(final MOB P : party(A))
			best = Math.max(best, P.phyStats().level());
		if(best <= 0)
			return;
		final int lvl = Math.max(1, best + ((levelHi > levelLo) ? CMLib.dice().roll(1, levelHi - levelLo + 1, levelLo - 1) : levelLo));
		M.basePhyStats().setLevel(lvl);
		M.recoverPhyStats();
		CMLib.leveler().fillOutMOB(M, lvl);
		M.basePhyStats().setRejuv(PhyStats.NO_REJUV); // a spawn, still
		M.recoverCharStats();
		M.recoverPhyStats();
		M.recoverMaxState();
		M.resetToMaxState();
	}

	/** counts one of the spawner's ticks */
	public void tick()
	{
		ticks++;
	}

	/**
	 * Notes a new spawn: tagged, and its tick kept for DESPAWN=TICKS.
	 * @param P what was spawned
	 */
	public void spawned(final PhysicalAgent P)
	{
		born.put(P, Long.valueOf(ticks));
		P.addTag(TAG);
	}

	/**
	 * @param P a spawn
	 * @return whether DESPAWN= says it should go now (when no one sees it)
	 */
	public boolean shouldGo(final Physical P)
	{
		final Room R = CMLib.map().roomLocation(P);
		if(R == null)
			return false;
		switch(despawn)
		{
		case DESPAWN_WHENFALSE:
			return !roomOk(R);
		case DESPAWN_DAWN:
		{
			final TimeClock.TimeOfDay tod = R.getArea().getTimeObj().getTODCode();
			return (tod == TimeClock.TimeOfDay.DAWN) || (tod == TimeClock.TimeOfDay.DAY);
		}
		case DESPAWN_TICKS:
		{
			final Long b = born.get(P);
			return (b != null) && ((ticks - b.longValue()) >= despawnTicks);
		}
		default:
			return false;
		}
	}

	/**
	 * @return whether DESPAWN= is set
	 */
	public boolean despawns()
	{
		return despawn != DESPAWN_NONE;
	}

	/**
	 * Whether a spawn can leave without anyone seeing it go: no player in its
	 * room, and it is not fighting.
	 * @param P the spawn
	 * @return true if it can
	 */
	public static boolean unseen(final Physical P)
	{
		final Room R = CMLib.map().roomLocation(P);
		if(R == null)
			return true;
		if(R.numPCInhabitants() > 0)
			return false;
		return !((P instanceof MOB) && ((MOB)P).isInCombat());
	}
}
