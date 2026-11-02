package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

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
 * C13: the game's difficulty.  DIFFICULTY=NORMAL in coffeemud.ini (or
 * world.ini) is a new game's; the DIFFICULTY command changes it in play, and
 * the save keeps it.  Each preset, DIFFICULTY_NAME="NPCDAMAGE=n NPCHP=n
 * PLAYERDAMAGE=n PLAYERXP=n D=x" (percents, and the factor @xD gives the
 * combat formulas), scales the damage NPCs deal to the player's side, their
 * hit points, the damage the player's side deals to NPCs, and the
 * experience characters gain.  The player's side is the characters and
 * whoever follows one.
 */
public final class GameDifficulty
{
	private GameDifficulty()
	{
	}

	/** the presets the game knows without the ini's */
	public static final String[][]	DEFAULTS	= {
		{ "STORY", "NPCDAMAGE=60 NPCHP=80 PLAYERDAMAGE=125 PLAYERXP=100 D=0.5" },
		{ "EASY", "NPCDAMAGE=80 NPCHP=90 PLAYERDAMAGE=110 PLAYERXP=100 D=0.75" },
		{ "NORMAL", "NPCDAMAGE=100 NPCHP=100 PLAYERDAMAGE=100 PLAYERXP=100 D=1" },
		{ "HARD", "NPCDAMAGE=130 NPCHP=120 PLAYERDAMAGE=100 PLAYERXP=110 D=1.25" }
	};

	/** one preset */
	public static final class Preset
	{
		/** its name */
		public String	name			= "NORMAL";
		/** percent of their damage NPCs deal to the player's side */
		public int		npcDamage		= 100;
		/** percent of their hit points NPCs have */
		public int		npcHp			= 100;
		/** percent of its damage the player's side deals to NPCs */
		public int		playerDamage	= 100;
		/** percent of the experience characters gain */
		public int		playerXp		= 100;
		/** the formulas' @xD */
		public double	d				= 1.0;

		/** @return its numbers, as the ini writes them */
		public String describe()
		{
			return "NPCDAMAGE=" + npcDamage + " NPCHP=" + npcHp + " PLAYERDAMAGE=" + playerDamage + " PLAYERXP=" + playerXp + " D=" + d;
		}
	}

	/** the difficulty chosen in play, or null for the ini's */
	private static volatile String	chosen	= null;
	/** the preset in force, worked out again when the choice changes */
	private static volatile Preset	inForce	= null;

	private static String iniText(final String name)
	{
		final String s = CMProps.instance().getStr("DIFFICULTY_" + name);
		if((s != null) && (s.trim().length() > 0))
			return s.trim();
		for(final String[] D : DEFAULTS)
		{
			if(D[0].equals(name))
				return D[1];
		}
		return null;
	}

	/**
	 * @return the presets' names: the game's, then any more the ini defines
	 */
	public static List<String> names()
	{
		final List<String> list = new ArrayList<String>();
		for(final String[] D : DEFAULTS)
			list.add(D[0]);
		for(final String k : CMProps.instance().stringPropertyNames())
		{
			final String K = k.toUpperCase();
			if(K.startsWith("DIFFICULTY_") && (K.length() > 11) && (!list.contains(K.substring(11))))
			{
				final String v = CMProps.instance().getStr(k);
				if((v != null) && (v.trim().length() > 0))
					list.add(K.substring(11));
			}
		}
		return list;
	}

	/**
	 * @param name a preset's name, or the start of one
	 * @return its name as the game knows it, or null
	 */
	public static String find(final String name)
	{
		if(name == null)
			return null;
		final String n = name.trim().toUpperCase();
		if(n.length() == 0)
			return null;
		final List<String> all = names();
		if(all.contains(n))
			return n;
		for(final String s : all)
		{
			if(s.startsWith(n))
				return s;
		}
		return null;
	}

	/**
	 * @param name a preset's name
	 * @return its numbers (NORMAL's for one not known)
	 */
	public static Preset preset(final String name)
	{
		final Preset P = new Preset();
		final String text = (name == null) ? null : iniText(name.toUpperCase());
		if(text == null)
			return P;
		P.name = name.toUpperCase();
		final Map<String, String> M = CMParms.parseEQParms(text);
		P.npcDamage = Math.max(0, CMath.s_int(M.containsKey("NPCDAMAGE") ? M.get("NPCDAMAGE") : "100"));
		P.npcHp = Math.max(1, CMath.s_int(M.containsKey("NPCHP") ? M.get("NPCHP") : "100"));
		P.playerDamage = Math.max(0, CMath.s_int(M.containsKey("PLAYERDAMAGE") ? M.get("PLAYERDAMAGE") : "100"));
		P.playerXp = Math.max(0, CMath.s_int(M.containsKey("PLAYERXP") ? M.get("PLAYERXP") : "100"));
		P.d = M.containsKey("D") ? CMath.s_double(M.get("D")) : 1.0;
		return P;
	}

	/**
	 * @return the difficulty the game is on: chosen in play, else the ini's DIFFICULTY, else NORMAL
	 */
	public static String current()
	{
		if(chosen != null)
			return chosen;
		final String ini = find(CMProps.instance().getStr("DIFFICULTY"));
		return (ini == null) ? "NORMAL" : ini;
	}

	/**
	 * @return the preset in force
	 */
	public static Preset inForce()
	{
		Preset P = inForce;
		if((P == null) || (!P.name.equals(current())))
		{
			P = preset(current());
			inForce = P;
			CMath.setFormulaDifficulty(P.d);
		}
		return P;
	}

	/**
	 * A game begins, or a save is restored: its difficulty.
	 * @param saved what the save kept, or null for a new game (the ini's)
	 */
	public static void restore(final String saved)
	{
		chosen = ((saved == null) || (saved.trim().length() == 0)) ? null : find(saved);
		inForce = null;
		inForce();
	}

	/**
	 * Changes the difficulty in play; the NPCs' hit points follow at once.
	 * @param name a preset's name
	 * @return the name set, or null if there is no such preset
	 */
	public static String set(final String name)
	{
		return set(name, null);
	}

	/**
	 * Changes the difficulty in play; the NPCs' hit points follow at once,
	 * in the world's rooms and the one given.
	 * @param name a preset's name
	 * @param here a room to see to as well (one not in the map), or null
	 * @return the name set, or null if there is no such preset
	 */
	public static String set(final String name, final Room here)
	{
		final String n = find(name);
		if(n == null)
			return null;
		chosen = n;
		inForce = null;
		inForce();
		refreshNPCs(here);
		return n;
	}

	/**
	 * @param M a mob
	 * @return true if it is on the player's side: a character, or one who follows one
	 */
	public static boolean playerSide(final MOB M)
	{
		if(M == null)
			return false;
		if(M.playerStats() != null)
			return true;
		final MOB lead = M.getGroupLeader();
		return (lead != null) && (lead != M) && (lead.playerStats() != null);
	}

	/**
	 * Damage about to be dealt: NPCs' to the player's side, and the player's
	 * side's to NPCs, by the preset.
	 * @param msg the damage message, its value the damage
	 * @param target who takes it
	 */
	public static void scaleDamage(final CMMsg msg, final MOB target)
	{
		final int was = msg.value();
		final MOB attacker = msg.source();
		if((was <= 0) || (attacker == null) || (attacker == target) || (target == null))
			return;
		final boolean aP = playerSide(attacker);
		final boolean tP = playerSide(target);
		if(aP == tP)
			return;
		final Preset P = inForce();
		final int pct = aP ? P.playerDamage : P.npcDamage;
		if(pct == 100)
			return;
		msg.setValue((int)Math.max((pct > 0) ? 1 : 0, Math.round(was * (pct / 100.0))));
	}

	/**
	 * @param M a mob whose hit points are worked out
	 * @return what an NPC's are multiplied by (1 for the player's side)
	 */
	public static double npcHpFactor(final MOB M)
	{
		if((M == null) || (!M.isMonster()) || playerSide(M))
			return 1.0;
		final int pct = inForce().npcHp;
		return (pct == 100) ? 1.0 : (pct / 100.0);
	}

	/**
	 * @param M who gains it
	 * @param amount experience to gain
	 * @return what a character gains, by the preset
	 */
	public static int xp(final MOB M, final int amount)
	{
		if((M == null) || (amount <= 0) || (M.playerStats() == null))
			return amount;
		final int pct = inForce().playerXp;
		return (pct == 100) ? amount : (int)Math.round(amount * (pct / 100.0));
	}

	/**
	 * The difficulty changed: each NPC's hit points worked out again, as
	 * hurt as it was.
	 */
	public static void refreshNPCs()
	{
		refreshNPCs(null);
	}

	/**
	 * The difficulty changed: each NPC's hit points worked out again, as
	 * hurt as it was, in the world's rooms and the one given.
	 * @param here a room to see to as well, or null
	 */
	public static void refreshNPCs(final Room here)
	{
		final Set<Room> rooms = new LinkedHashSet<Room>();
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
			rooms.add(r.nextElement());
		if(here != null)
			rooms.add(here);
		for(final Room R : rooms)
		{
			if(R == null)
				continue;
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || (!M.isMonster()) || playerSide(M) || M.amDead())
					continue;
				final int max = Math.max(1, M.maxState().getHitPoints());
				final double part = Math.min(1.0, M.curState().getHitPoints() / (double)max);
				M.recoverMaxState();
				M.curState().setHitPoints((int)Math.max(1, Math.round(M.maxState().getHitPoints() * part)));
			}
		}
	}
}
