package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
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
 * C15: mob ranks.  Prop_MobRank RANK=MINION|NORMAL|ELITE|BOSS (or one the
 * ini defines) gives a mob its rank's numbers, from MOBRANK_NAME in
 * coffeemud.ini or world.ini: HP+n% DAMAGE+n% ATTACK+n ARMOR-n SPEED+n
 * XP*x POWER=x TAG=text RESIST="fire 25% mind 25%".  One edit retunes every
 * mob of the rank.  RANK($i) in scripts, -RANK +BOSS in masks; the tag shows
 * in LOOK and CONSIDER; POWER is what an encounter budget counts.
 */
public final class MobRanks
{
	private MobRanks()
	{
	}

	/** the ranks the game knows without the ini's */
	public static final String[][]	DEFAULTS	= {
		{ "MINION", "HP-50% DAMAGE-25% ATTACK-10 XP*0.5 POWER=0.5 TAG=[minion]" },
		{ "NORMAL", "POWER=1" },
		{ "ELITE", "HP+100% DAMAGE+25% ATTACK+10 ARMOR-10 XP*2.5 POWER=2 TAG=[elite]" },
		{ "BOSS", "HP+300% DAMAGE+50% ATTACK+20 ARMOR-20 XP*5 POWER=5 TAG=[boss]" }
	};

	/** one rank's numbers */
	public static final class Rank
	{
		/** its name */
		public String	name	= "NORMAL";
		/** percent more (or less) hit points */
		public int		hpPct	= 0;
		/** percent more damage */
		public int		dmgPct	= 0;
		/** added to the attack adjustment */
		public int		attack	= 0;
		/** added to the armor (less is better) */
		public int		armor	= 0;
		/** added to the speed */
		public double	speed	= 0;
		/** the experience for killing it is multiplied by this */
		public double	xp		= 1.0;
		/** what it counts for in an encounter's budget */
		public double	power	= 1.0;
		/** what LOOK and CONSIDER show, or "" */
		public String	tag		= "";
		/** saving throw code and percent pairs */
		public final List<int[]>	resists	= new ArrayList<int[]>();
	}

	private static final Pattern			NUM		= Pattern.compile("^([A-Z]+)\\s*([+\\-*=])\\s*([0-9.]+)(%?)$");
	private static final Pattern			QUOTED	= Pattern.compile("(?i)\\b(TAG|RESIST)\\s*=\\s*(?:\"([^\"]*)\"|(\\S+))");
	private static final Map<String, Rank>	cache	= new HashMap<String, Rank>();

	/**
	 * @param name a rank's name
	 * @return its text: the ini's, the game's, or null for none
	 */
	public static String text(final String name)
	{
		final String n = (name == null) ? "" : name.trim().toUpperCase();
		final String s = CMProps.instance().getStr("MOBRANK_" + n);
		if((s != null) && (s.trim().length() > 0))
			return s.trim();
		for(final String[] D : DEFAULTS)
		{
			if(D[0].equals(n))
				return D[1];
		}
		return null;
	}

	/**
	 * @return the ranks' names: the game's, then any the ini adds
	 */
	public static List<String> names()
	{
		final List<String> list = new ArrayList<String>();
		for(final String[] D : DEFAULTS)
			list.add(D[0]);
		for(final String k : CMProps.instance().stringPropertyNames())
		{
			final String K = k.toUpperCase();
			if(K.startsWith("MOBRANK_") && (K.length() > 8) && (!list.contains(K.substring(8))))
			{
				final String v = CMProps.instance().getStr(k);
				if((v != null) && (v.trim().length() > 0))
					list.add(K.substring(8));
			}
		}
		return list;
	}

	/**
	 * @param name a rank's name
	 * @return its numbers (NORMAL's, none, for one not known)
	 */
	public static Rank rank(final String name)
	{
		final String n = (name == null) ? "NORMAL" : name.trim().toUpperCase();
		final String text = text(n);
		final String key = n + "=" + ((text == null) ? "" : text);
		synchronized(cache)
		{
			final Rank was = cache.get(key);
			if(was != null)
				return was;
		}
		final Rank R = parse(n, (text == null) ? "" : text);
		synchronized(cache)
		{
			cache.put(key, R);
		}
		return R;
	}

	/**
	 * Reads a rank's text.
	 * @param name its name
	 * @param text HP+n% DAMAGE+n% ATTACK+n ARMOR-n SPEED+n XP*x POWER=x TAG=text RESIST="..."
	 * @return its numbers
	 */
	public static Rank parse(final String name, final String text)
	{
		final Rank R = new Rank();
		R.name = name;
		String rest = text;
		// TAG= and RESIST= first, quoted or not
		final Matcher q = QUOTED.matcher(rest);
		final StringBuffer left = new StringBuffer();
		while(q.find())
		{
			final String k = q.group(1).toUpperCase();
			final String v = (q.group(2) != null) ? q.group(2) : q.group(3);
			if(k.equals("TAG"))
				R.tag = v.trim();
			else
			{
				final List<String> r = CMParms.parse(v);
				for(int i = 0; i + 1 < r.size(); i += 2)
				{
					final int code = saveCode(r.get(i));
					if(code >= 0)
						R.resists.add(new int[] { code, CMath.s_int(r.get(i + 1).replace("%", "")) });
				}
			}
			q.appendReplacement(left, " ");
		}
		q.appendTail(left);
		rest = left.toString();
		for(final String w : CMParms.parse(rest))
		{
			final String up = w.toUpperCase();
			final Matcher m = NUM.matcher(up);
			if(!m.matches())
				continue;
			final String k = m.group(1);
			final String op = m.group(2);
			final double v = CMath.s_double(m.group(3)) * (op.equals("-") ? -1 : 1);
			if(k.equals("HP"))
				R.hpPct = (int)Math.round(v);
			else
			if(k.equals("DAMAGE"))
				R.dmgPct = (int)Math.round(v);
			else
			if(k.equals("ATTACK"))
				R.attack = (int)Math.round(v);
			else
			if(k.equals("ARMOR"))
				R.armor = (int)Math.round(v);
			else
			if(k.equals("SPEED"))
				R.speed = v;
			else
			if(k.equals("XP"))
				R.xp = Math.abs(v);
			else
			if(k.equals("POWER"))
				R.power = Math.abs(v);
		}
		return R;
	}

	/**
	 * @param word a saving throw's name, as fire, mind, electricity
	 * @return its CharStats code, or -1
	 */
	public static int saveCode(final String word)
	{
		final String w = word.trim().toUpperCase();
		if(w.length() == 0)
			return -1;
		for(final int c : CharStats.CODES.SAVING_THROWS())
		{
			final String n = CharStats.CODES.NAME(c).toUpperCase();
			if(n.equals(w) || n.equals("SAVE" + w) || n.startsWith(w))
				return c;
		}
		return -1;
	}

	/**
	 * @param M a mob
	 * @return its rank's name: its Prop_MobRank's, else NORMAL
	 */
	public static String rankOf(final MOB M)
	{
		if(M == null)
			return "NORMAL";
		final Ability A = M.fetchEffect("Prop_MobRank");
		if(A == null)
			return "NORMAL";
		return nameIn(A.text());
	}

	/**
	 * @param parms a Prop_MobRank's parameters: RANK=ELITE, or ELITE
	 * @return the rank's name
	 */
	public static String nameIn(final String parms)
	{
		final String p = (parms == null) ? "" : parms.trim();
		String n = CMParms.getParmStr(p, "RANK", "");
		if(n.length() == 0)
			n = p;
		n = n.trim().toUpperCase();
		return (n.length() == 0) ? "NORMAL" : n;
	}

	/**
	 * @param M a mob
	 * @return its rank's tag, or ""
	 */
	public static String tagOf(final MOB M)
	{
		return rank(rankOf(M)).tag;
	}

	/**
	 * @param victim the mob the experience is for killing, or null
	 * @param amount the experience
	 * @return it, multiplied by the victim's rank's XP
	 */
	public static int xpFor(final MOB victim, final int amount)
	{
		if((victim == null) || (amount <= 0) || (victim.fetchEffect("Prop_MobRank") == null))
			return amount;
		final double x = rank(rankOf(victim)).xp;
		return (x == 1.0) ? amount : (int)Math.round(amount * x);
	}

	/**
	 * @param M a mob
	 * @return what it counts for in an encounter's budget
	 */
	public static double powerOf(final MOB M)
	{
		return rank(rankOf(M)).power;
	}
}
