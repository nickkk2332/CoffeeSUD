package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.planet_ink.coffee_mud.CharClasses.interfaces.CharClass;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
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
 * P13: perks, traits and flaws, world/perks.ini.  A perk's line: its ID, then
 * NAME, POOL, MASK, REQUIRES (perk IDs), COST (1 if not given) and REWARDS
 * (achievement reward syntax).  PICKS= gives each pool's picks ("GENERAL every
 * 3 levels; CLASS:Fighter at 1,5,10", a class's own levels for CLASS:...);
 * the TRAIT pool is bought at creation with TRAITS= points, a flaw's COST
 * below 0 giving points back.  A perk taken is a tattoo of its ID.
 */
public class Perks
{
	private Perks()
	{
	}

	/** the pool of the creation traits and flaws */
	public static final String TRAIT = "TRAIT";

	/** one perk */
	public static class Perk
	{
		public String				id		= "";
		public String				name	= "";
		public String				pool	= "GENERAL";
		public String				mask	= "";
		public final List<String>	requires	= new ArrayList<String>();
		public int					cost	= 1;
		public String				rewards	= "";
		public String				help	= "";
	}

	/** one PICKS entry */
	public static class Pick
	{
		public String				pool	= "";
		public String				cls		= "";
		public int					every	= 0;
		public final Set<Integer>	at		= new HashSet<Integer>();
	}

	/** perks.ini, as read */
	public static class Plan
	{
		public final List<Perk>	perks	= new ArrayList<Perk>();
		public final List<Pick>	picks	= new ArrayList<Pick>();
		public int				traits	= 0;
	}

	private static Plan		cached		= null;
	private static long		cachedWhen	= -1;
	private static String	cachedPath	= null;

	/** @return world/perks.ini, or null with no world folder */
	public static File file()
	{
		final File W = WorldFiles.worldDir();
		return (W == null) ? null : new File(W, "perks.ini");
	}

	/** @return perks.ini as it is now; none without it */
	public static synchronized void clear() { cached = null; cachedWhen = -1; cachedPath = null; }

	public static synchronized Plan plan()
	{
		final File F = file();
		if((F == null) || (!F.isFile()))
			return new Plan();
		if((cached != null) && (cachedWhen == F.lastModified()) && F.getPath().equals(cachedPath))
			return cached;
		String text = "";
		try
		{
			text = new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8);
		}
		catch(final java.io.IOException e)
		{
			Log.errOut("Perks", "perks.ini: " + e.getMessage());
		}
		cached = parse(text);
		cachedWhen = F.lastModified();
		cachedPath = F.getPath();
		return cached;
	}

	/**
	 * Reads perks.ini's text.
	 * @param text the text
	 * @return the plan
	 */
	public static Plan parse(final String text)
	{
		final Plan P = new Plan();
		final String[] lines = text.replace("\r", "").split("\n");
		for(int l = 0; l < lines.length; l++)
		{
			String line = lines[l].trim();
			while(line.endsWith("\\") && (l + 1 < lines.length))
				line = line.substring(0, line.length() - 1).trim() + " " + lines[++l].trim();
			if((line.length() == 0) || line.startsWith("#") || line.startsWith(";"))
				continue;
			final String up = line.toUpperCase();
			if(up.startsWith("PICKS="))
			{
				for(final String entry : line.substring(6).split(";"))
				{
					final Pick K = pick(entry.trim());
					if(K != null)
						P.picks.add(K);
					else
					if(entry.trim().length() > 0)
						Log.errOut("Perks", "perks.ini: PICKS entry not understood: " + entry.trim());
				}
				continue;
			}
			if(up.startsWith("TRAITS="))
			{
				P.traits = CMath.s_int(line.substring(7).trim());
				continue;
			}
			final int sp = line.indexOf(' ');
			final Perk K = new Perk();
			K.id = ((sp < 0) ? line : line.substring(0, sp)).trim().toUpperCase();
			final String rest = (sp < 0) ? "" : line.substring(sp + 1);
			final Map<String, String> parms = CMParms.parseEQParms(rest);
			for(final Map.Entry<String, String> e : parms.entrySet())
			{
				final String k = e.getKey().toUpperCase();
				final String v = e.getValue().trim();
				if(k.equals("NAME"))
					K.name = v;
				else
				if(k.equals("POOL"))
					K.pool = v.toUpperCase();
				else
				if(k.equals("MASK"))
					K.mask = v;
				else
				if(k.equals("REQUIRES"))
				{
					for(final String r : CMParms.parseCommas(v.toUpperCase().replace(' ', ','), true))
						K.requires.add(r.trim());
				}
				else
				if(k.equals("COST"))
					K.cost = CMath.s_int(v);
				else
				if(k.equals("REWARDS"))
					K.rewards = v;
				else
				if(k.equals("HELP"))
					K.help = v;
				else
					Log.errOut("Perks", "perks.ini " + K.id + ": unknown key " + k);
			}
			if(K.name.length() == 0)
				K.name = CMStrings.capitalizeAndLower(K.id.replaceFirst("^PERK_", "").replace('_', ' '));
			P.perks.add(K);
		}
		return P;
	}

	private static Pick pick(final String entry)
	{
		final List<String> w = CMParms.parse(entry);
		if(w.size() < 2)
			return null;
		final Pick K = new Pick();
		final String head = w.get(0);
		final int x = head.indexOf(':');
		K.pool = ((x < 0) ? head : head.substring(0, x)).toUpperCase();
		K.cls = (x < 0) ? "" : head.substring(x + 1);
		if(w.get(1).equalsIgnoreCase("EVERY") && (w.size() >= 3))
			K.every = Math.max(1, CMath.s_int(w.get(2)));
		else
		if(w.get(1).equalsIgnoreCase("AT") && (w.size() >= 3))
		{
			for(final String n : CMParms.parseCommas(CMParms.combine(w, 2).replace(' ', ','), true))
			{
				if(CMath.isInteger(n.trim()))
					K.at.add(Integer.valueOf(CMath.s_int(n.trim())));
			}
		}
		else
			return null;
		return K;
	}

	/**
	 * @param name a perk's ID or name
	 * @return the perk, or null
	 */
	public static Perk perk(final String name)
	{
		final String n = (name == null) ? "" : name.trim();
		if(n.length() == 0)
			return null;
		final Plan P = plan();
		for(final Perk K : P.perks)
		{
			if(K.id.equalsIgnoreCase(n) || K.name.equalsIgnoreCase(n) || K.id.equalsIgnoreCase("PERK_" + n.replace(' ', '_')))
				return K;
		}
		return find(P.perks, n);
	}

	/**
	 * Finds what was typed among perks: an ID or name, the start of a name, or
	 * words of one.
	 * @param perks the perks
	 * @param input what was typed
	 * @return the perk, or null
	 */
	public static Perk find(final List<Perk> perks, final String input)
	{
		final String n = (input == null) ? "" : input.trim();
		if(n.length() == 0)
			return null;
		for(final Perk K : perks)
		{
			if(K.id.equalsIgnoreCase(n) || K.name.equalsIgnoreCase(n))
				return K;
		}
		for(final Perk K : perks)
		{
			if(K.name.toLowerCase().startsWith(n.toLowerCase()))
				return K;
		}
		for(final Perk K : perks)
		{
			if(CMLib.english().containsString(K.name, n))
				return K;
		}
		return null;
	}

	/**
	 * @param mob a character
	 * @param K a perk
	 * @return true if they have it
	 */
	public static boolean has(final MOB mob, final Perk K)
	{
		return (mob != null) && (K != null) && (mob.findTattoo(K.id) != null);
	}

	private static int levelFor(final MOB mob, final Pick K)
	{
		if(K.cls.length() == 0)
			return mob.basePhyStats().level();
		CharClass C = CMClass.getCharClass(K.cls);
		if(C == null)
			C = CMClass.findCharClass(K.cls);
		if(C == null)
			return 0;
		return Math.max(0, mob.baseCharStats().getClassLevel(C));
	}

	/**
	 * The picks a character has earned in a pool by their levels.
	 * @param mob the character
	 * @param pool the pool
	 * @return how many
	 */
	public static int earned(final MOB mob, final String pool)
	{
		if(pool.equalsIgnoreCase(TRAIT))
			return plan().traits;
		int n = 0;
		for(final Pick K : plan().picks)
		{
			if(!K.pool.equalsIgnoreCase(pool))
				continue;
			final int L = levelFor(mob, K);
			if(K.every > 0)
				n += L / K.every;
			for(final Integer a : K.at)
			{
				if(a.intValue() <= L)
					n++;
			}
		}
		return n;
	}

	/**
	 * What a character has spent of a pool: the costs of the perks of it they have.
	 * @param mob the character
	 * @param pool the pool
	 * @return the points spent (flaws count against)
	 */
	public static int spent(final MOB mob, final String pool)
	{
		int n = 0;
		for(final Perk K : plan().perks)
		{
			if(K.pool.equalsIgnoreCase(pool) && has(mob, K))
				n += pool.equalsIgnoreCase(TRAIT) ? K.cost : Math.max(0, K.cost);
		}
		return n;
	}

	/**
	 * @param mob a character
	 * @param pool a pool
	 * @return the picks or points left in it
	 */
	public static int left(final MOB mob, final String pool)
	{
		return earned(mob, pool) - spent(mob, pool);
	}

	/**
	 * Why a character cannot take a perk now.
	 * @param mob the character
	 * @param K the perk
	 * @param creating true while the character is being made (traits)
	 * @return the reason, or null if they can
	 */
	public static String why(final MOB mob, final Perk K, final boolean creating)
	{
		if(has(mob, K))
			return "already yours";
		if(K.pool.equalsIgnoreCase(TRAIT) && (!creating))
			return "a trait, chosen when a character is made";
		if((!K.pool.equalsIgnoreCase(TRAIT)) && creating)
			return "taken after creation";
		if((K.mask.length() > 0) && (!CMLib.masking().maskCheck(K.mask, mob, true)))
			return "needs " + CMLib.masking().maskDesc(K.mask, true);
		for(final String r : K.requires)
		{
			final Perk R = perk(r);
			if((R != null) && (!has(mob, R)))
				return "needs " + R.name;
			if((R == null) && (mob.findTattoo(r) == null))
				return "needs " + r;
		}
		if((K.cost > 0) && (left(mob, K.pool) < K.cost))
			return K.pool.equalsIgnoreCase(TRAIT) ? "not enough trait points" : ("no " + K.pool.toLowerCase() + " pick left");
		return null;
	}

	/**
	 * Gives a character a perk: its tattoo, and its rewards unless it is a
	 * trait being chosen at creation (those come at the end of creation).
	 * @param mob the character
	 * @param K the perk
	 * @param rewardsNow true to give the rewards now
	 */
	public static void give(final MOB mob, final Perk K, final boolean rewardsNow)
	{
		if(has(mob, K))
			return;
		mob.addTattoo(K.id);
		if(rewardsNow && (K.rewards.length() > 0))
			CMLib.achievements().giveAwards(mob, K.rewards);
	}

	/**
	 * Takes a perk off a character (its rewards stay).
	 * @param mob the character
	 * @param K the perk
	 */
	public static void remove(final MOB mob, final Perk K)
	{
		if(has(mob, K))
			mob.delTattoo(K.id);
	}

	/**
	 * Gives the rewards of the traits and flaws chosen at creation.
	 * @param mob the character
	 */
	public static void traitRewards(final MOB mob)
	{
		for(final Perk K : plan().perks)
		{
			if(K.pool.equalsIgnoreCase(TRAIT) && has(mob, K) && (K.rewards.length() > 0))
				CMLib.achievements().giveAwards(mob, K.rewards);
		}
	}

	/**
	 * @param mob a character
	 * @return the traits and flaws, for the creation step
	 */
	public static List<Perk> traits(final MOB mob)
	{
		final List<Perk> list = new ArrayList<Perk>();
		for(final Perk K : plan().perks)
		{
			if(K.pool.equalsIgnoreCase(TRAIT) && ((K.mask.length() == 0) || CMLib.masking().maskCheck(K.mask, mob, true)))
				list.add(K);
		}
		return list;
	}

	private static String costText(final Perk K)
	{
		return (K.cost < 0) ? ("+" + (-K.cost)) : ("" + K.cost);
	}

	/**
	 * The creation step's screen: the traits and flaws, which are taken, and
	 * the points left.
	 * @param mob the character being made
	 * @return the text
	 */
	public static String traitMenu(final MOB mob)
	{
		final StringBuilder s = new StringBuilder(CMLib.lang().L("\n\r^!Traits and flaws^N (points left: ^H@x1^N):\n\r", "" + left(mob, TRAIT)));
		for(final Perk K : traits(mob))
		{
			s.append(has(mob, K) ? "^H[x] " : "^N[ ] ").append(K.name).append("^N (")
			 .append((K.cost < 0) ? ("flaw, gives " + (-K.cost)) : ("costs " + K.cost)).append(")");
			if(K.help.length() > 0)
				s.append(": ").append(K.help);
			s.append("\n\r");
		}
		return s.toString();
	}

	/**
	 * PERKS: what a character has, can take and cannot yet, and the picks left.
	 * @param mob the character
	 * @return the text
	 */
	public static String list(final MOB mob)
	{
		final Plan P = plan();
		if(P.perks.size() == 0)
			return CMLib.lang().L("There are no perks in this world.");
		final StringBuilder owned = new StringBuilder("");
		final StringBuilder open = new StringBuilder("");
		final StringBuilder locked = new StringBuilder("");
		final Set<String> pools = new HashSet<String>();
		for(final Perk K : P.perks)
		{
			final String line = K.name + ((K.help.length() > 0) ? (": " + K.help) : "");
			if(has(mob, K))
				owned.append("  ").append(line).append("\n\r");
			else
			if(K.pool.equalsIgnoreCase(TRAIT))
				continue;
			else
			{
				pools.add(K.pool);
				final String why = why(mob, K, false);
				if(why == null)
					open.append("  ^H").append(K.name).append("^N (").append(K.pool.toLowerCase()).append(", ").append(costText(K)).append(")")
						.append((K.help.length() > 0) ? (": " + K.help) : "").append("\n\r");
				else
					locked.append("  ").append(K.name).append(" (").append(why).append(")\n\r");
			}
		}
		final StringBuilder s = new StringBuilder("");
		final StringBuilder left = new StringBuilder("");
		for(final String pool : pools)
			left.append(pool.toLowerCase()).append(' ').append(left(mob, pool)).append(", ");
		if(left.length() > 0)
			s.append(CMLib.lang().L("^HPicks left^N: @x1\n\r", left.substring(0, left.length() - 2)));
		if(owned.length() > 0)
			s.append(CMLib.lang().L("^HYours^N:\n\r")).append(owned);
		if(open.length() > 0)
			s.append(CMLib.lang().L("^HYou can take^N (PERK [name]):\n\r")).append(open);
		if(locked.length() > 0)
			s.append(CMLib.lang().L("^HNot yet^N:\n\r")).append(locked);
		return s.toString();
	}

	/**
	 * Said at a level-up when the character has a pick to spend.
	 * @param mob the character
	 * @return the line, or ""
	 */
	public static String levelNote(final MOB mob)
	{
		final Plan P = plan();
		if(P.picks.size() == 0)
			return "";
		final Set<String> pools = new HashSet<String>();
		for(final Pick K : P.picks)
			pools.add(K.pool);
		for(final String pool : pools)
		{
			if(left(mob, pool) > 0)
				return CMLib.lang().L("^HYou may choose a perk: PERKS.^N\n\r");
		}
		return "";
	}
}
