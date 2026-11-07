package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.CharClasses.interfaces.CharClass;
import com.planet_ink.coffee_mud.Common.interfaces.Tattoo;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.CMObject;
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
 * A05: TREE, ROOT and LINKS make a tree whose perks are taken next to one
 * already held; REQUIRESANY needs one of several; GROUP lets only one of a
 * group be held; EFFECTS are held while the perk is; REFUND (or REFUNDS= for
 * all) lets PERK REFUND give one back when nothing held depends on it.  A06:
 * MPPERK gives a pool picks of its own (a boss, a track, a guild) and CLEAR
 * takes back only that pool's perks.
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
		/** A05: the tree it is in, if any */
		public String				tree	= "";
		/** A05: a way into its tree: taken without a linked perk */
		public boolean				root	= false;
		/** A05: the perks of its tree it is next to (links go both ways) */
		public final List<String>	links	= new ArrayList<String>();
		/** A05: perks (or tattoos) of which one is needed */
		public final List<String>	requiresAny	= new ArrayList<String>();
		/** A05: only one perk of a group can be held */
		public String				group	= "";
		/** A05: effects held while the perk is (Prop_SpellAdder's form) */
		public String				effects	= "";
		/** A05: YES or NO for PERK REFUND; blank follows REFUNDS= */
		public String				refund	= "";
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
		/** A05: whether perks can be refunded by default */
		public boolean			refunds	= false;
		/** what was not understood */
		public final List<String> problems = new ArrayList<String>();
	}

	/** A05: the hidden tattoo marking a perk taken: PERK|id */
	public static final String	TAKEN	= "PERK|";
	/** A05: the hidden tattoo of an effect a perk holds: PERKFX|perk|effect|hash */
	public static final String	HELD	= "PERKFX|";
	/** A06: the hidden tattoo of a pool's picks given by scripts: PERKPICKS|pool=n */
	public static final String	GIVEN	= "PERKPICKS|";

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
			if(up.startsWith("REFUNDS="))
			{
				P.refunds = CMath.s_bool(line.substring(8).trim()) || line.substring(8).trim().equalsIgnoreCase("YES");
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
				if(k.equals("TREE"))
					K.tree = v;
				else
				if(k.equals("ROOT"))
					K.root = CMath.s_bool(v) || v.equalsIgnoreCase("YES");
				else
				if(k.equals("LINKS"))
				{
					for(final String r : CMParms.parseCommas(v.toUpperCase().replace(' ', ','), true))
						K.links.add(r.trim());
				}
				else
				if(k.equals("REQUIRESANY"))
				{
					for(final String r : CMParms.parseCommas(v.toUpperCase().replace(' ', ','), true))
						K.requiresAny.add(r.trim());
				}
				else
				if(k.equals("GROUP"))
					K.group = v.toUpperCase();
				else
				if(k.equals("EFFECTS"))
					K.effects = v;
				else
				if(k.equals("REFUND"))
					K.refund = (CMath.s_bool(v) || v.equalsIgnoreCase("YES")) ? "YES" : "NO";
				else
				{
					Log.errOut("Perks", "perks.ini " + K.id + ": unknown key " + k);
					P.problems.add(K.id + ": unknown key " + k);
				}
			}
			if(K.name.length() == 0)
				K.name = CMStrings.capitalizeAndLower(K.id.replaceFirst("^PERK_", "").replace('_', ' '));
			P.perks.add(K);
		}
		check(P);
		return P;
	}

	/** A05: what does not fit together: links to perks that are not there or in another tree, a tree with no way in */
	private static void check(final Plan P)
	{
		final Map<String, Perk> byId = new java.util.HashMap<String, Perk>();
		for(final Perk K : P.perks)
		{
			if(byId.containsKey(K.id))
				P.problems.add(K.id + ": defined twice");
			byId.put(K.id, K);
		}
		final Set<String> trees = new LinkedHashSet<String>(), rooted = new HashSet<String>();
		for(final Perk K : P.perks)
		{
			if(K.tree.length() > 0)
			{
				trees.add(K.tree.toUpperCase());
				if(K.root)
					rooted.add(K.tree.toUpperCase());
			}
			else
			if(K.root || (K.links.size() > 0))
				P.problems.add(K.id + ": ROOT and LINKS need a TREE");
			for(final String l : K.links)
			{
				final Perk L = byId.get(l);
				if(L == null)
					P.problems.add(K.id + ": LINKS names " + l + ", which is not a perk");
				else
				if(!L.tree.equalsIgnoreCase(K.tree))
					P.problems.add(K.id + ": LINKS names " + l + ", of another tree");
			}
			if(K.pool.equalsIgnoreCase(TRAIT) && ((K.tree.length() > 0) || (K.group.length() > 0)))
				P.problems.add(K.id + ": a trait cannot be in a TREE or a GROUP");
		}
		for(final String t : trees)
		{
			if(!rooted.contains(t))
				P.problems.add("tree " + t + ": no perk of it is a ROOT, so none can be taken");
		}
	}

	/** @return what perks.ini has that does not fit, for WORLD CHECK */
	public static List<String> problems()
	{
		return plan().problems;
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
		return n + given(mob, pool);
	}

	/**
	 * A06: the picks of a pool a script gave (a boss's death, a track, a guild).
	 * @param mob the character
	 * @param pool the pool
	 * @return how many
	 */
	public static int given(final MOB mob, final String pool)
	{
		final Tattoo T = givenTattoo(mob, pool);
		return (T == null) ? 0 : CMath.s_int(T.getTattooName().substring(T.getTattooName().indexOf('=') + 1));
	}

	private static Tattoo givenTattoo(final MOB mob, final String pool)
	{
		if(mob == null)
			return null;
		final String pre = (GIVEN + pool + "=").toUpperCase();
		for(final Enumeration<Tattoo> e = mob.tattoos(); e.hasMoreElements();)
		{
			final Tattoo T = e.nextElement();
			if((T != null) && T.getTattooName().startsWith(pre))
				return T;
		}
		return null;
	}

	/**
	 * A06: sets the picks of a pool given by scripts.
	 * @param mob the character
	 * @param pool the pool
	 * @param n how many, 0 or more
	 */
	public static void setGiven(final MOB mob, final String pool, final int n)
	{
		final Tattoo T = givenTattoo(mob, pool);
		if(T != null)
			mob.delTattoo(T);
		if(n > 0)
			mob.addTattoo((GIVEN + pool + "=" + n).toUpperCase());
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
		if(K.requiresAny.size() > 0)
		{
			boolean any = false;
			final List<String> names = new ArrayList<String>();
			for(final String r : K.requiresAny)
			{
				final Perk R = byId(r);
				names.add((R == null) ? r : R.name);
				any = any || ((R != null) ? has(mob, R) : (mob.findTattoo(r) != null));
			}
			if(!any)
				return "needs one of " + String.join(", ", names);
		}
		if(K.group.length() > 0)
		{
			for(final Perk O : plan().perks)
			{
				if((O != K) && O.group.equalsIgnoreCase(K.group) && has(mob, O))
					return "you have " + O.name + ", and only one of " + K.group.toLowerCase() + " can be held";
			}
		}
		if(!reachable(mob, K, null))
		{
			final List<String> names = new ArrayList<String>();
			for(final Perk L : linked(K))
				names.add(L.name);
			return "not next to a perk you have in " + K.tree + (names.isEmpty() ? "" : " (" + String.join(" or ", names) + ")");
		}
		if((K.cost > 0) && (left(mob, K.pool) < K.cost))
			return K.pool.equalsIgnoreCase(TRAIT) ? "not enough trait points" : ("no " + K.pool.toLowerCase() + " pick left");
		return null;
	}

	/** a perk by its exact ID, or null */
	public static Perk byId(final String id)
	{
		for(final Perk K : plan().perks)
		{
			if(K.id.equalsIgnoreCase(id))
				return K;
		}
		return null;
	}

	/** A05: the perks next to one in its tree, both ways */
	public static List<Perk> linked(final Perk K)
	{
		final List<Perk> out = new ArrayList<Perk>();
		if(K.tree.length() == 0)
			return out;
		for(final Perk O : plan().perks)
		{
			if((O != K) && O.tree.equalsIgnoreCase(K.tree) && (K.links.contains(O.id) || O.links.contains(K.id)))
				out.add(O);
		}
		return out;
	}

	/**
	 * A05: whether a perk can be reached: outside a tree, a root, or next to a
	 * perk held (not counting one being given back).
	 */
	private static boolean reachable(final MOB mob, final Perk K, final Perk without)
	{
		if((K.tree.length() == 0) || K.root)
			return true;
		for(final Perk L : linked(K))
		{
			if((L != without) && has(mob, L))
				return true;
		}
		return false;
	}

	/**
	 * A05: the perks a character holds that stand on this one: they require it,
	 * it is the only one they have of their REQUIRESANY, or without it they
	 * are cut off from every root of their tree.
	 * @param mob the character
	 * @param K the perk
	 * @return those perks, none if it can go
	 */
	public static List<Perk> dependants(final MOB mob, final Perk K)
	{
		final List<Perk> out = new ArrayList<Perk>();
		final Plan P = plan();
		for(final Perk O : P.perks)
		{
			if((O == K) || (!has(mob, O)))
				continue;
			if(O.requires.contains(K.id))
			{
				out.add(O);
				continue;
			}
			if(O.requiresAny.contains(K.id))
			{
				boolean other = false;
				for(final String r : O.requiresAny)
				{
					final Perk R = byId(r);
					other = other || ((!r.equalsIgnoreCase(K.id)) && ((R != null) ? has(mob, R) : (mob.findTattoo(r) != null)));
				}
				if(!other)
				{
					out.add(O);
					continue;
				}
			}
		}
		if(K.tree.length() > 0)
		{
			// what of the tree is still joined to a held root without it
			final Set<Perk> joined = new HashSet<Perk>();
			final List<Perk> todo = new ArrayList<Perk>();
			for(final Perk O : P.perks)
			{
				if((O != K) && O.root && O.tree.equalsIgnoreCase(K.tree) && has(mob, O))
				{
					joined.add(O);
					todo.add(O);
				}
			}
			while(!todo.isEmpty())
			{
				final Perk O = todo.remove(0);
				for(final Perk L : linked(O))
				{
					if((L != K) && has(mob, L) && joined.add(L))
						todo.add(L);
				}
			}
			for(final Perk O : P.perks)
			{
				if((O != K) && O.tree.equalsIgnoreCase(K.tree) && has(mob, O) && (!joined.contains(O)) && (!out.contains(O)))
					out.add(O);
			}
		}
		return out;
	}

	/** A05: whether PERK REFUND may give this perk back */
	public static boolean refundable(final Perk K)
	{
		if(K.pool.equalsIgnoreCase(TRAIT))
			return false;
		return (K.refund.length() > 0) ? K.refund.equals("YES") : plan().refunds;
	}

	/**
	 * A05: PERK REFUND: gives a perk back, its pick with it, when the world
	 * allows it and nothing held depends on it.  Its rewards stay.
	 * @param mob the character
	 * @param K the perk
	 * @return null when done, else why not
	 */
	public static String refund(final MOB mob, final Perk K)
	{
		if(!has(mob, K))
			return "you do not have it";
		if(!refundable(K))
			return "it cannot be given back";
		final List<Perk> on = dependants(mob, K);
		if(!on.isEmpty())
		{
			final List<String> names = new ArrayList<String>();
			for(final Perk O : on)
				names.add(O.name);
			return "first give back what stands on it: " + String.join(", ", names);
		}
		remove(mob, K);
		return null;
	}

	/**
	 * A06: takes back every perk of a pool (a guild left, a curse lifted): their
	 * effects go, their rewards stay; other pools are untouched.
	 * @param mob the character
	 * @param pool the pool
	 * @return how many perks went
	 */
	public static int clear(final MOB mob, final String pool)
	{
		int n = 0;
		for(final Perk K : plan().perks)
		{
			if(K.pool.equalsIgnoreCase(pool) && has(mob, K))
			{
				remove(mob, K);
				n++;
			}
		}
		return n;
	}

	private static List<Ability> effectsOf(final String spec)
	{
		final List<Ability> L = new ArrayList<Ability>();
		if((spec == null) || (spec.trim().length() == 0))
			return L;
		for(final CMObject o : CMLib.coffeeMaker().getCodedSpellsOrBehaviors(spec))
		{
			if(o instanceof Ability)
				L.add((Ability)o);
		}
		return L;
	}

	private static String heldTattoo(final Perk K, final Ability A)
	{
		return (HELD + K.id + "|" + A.ID() + "|" + Integer.toHexString(A.text().hashCode())).toUpperCase();
	}

	/** the lasting effect of this kind and text on the character, or null */
	private static Ability heldEffect(final MOB mob, final String id, final String hash)
	{
		for(int e = mob.numEffects() - 1; e >= 0; e--)
		{
			final Ability E = mob.fetchEffect(e);
			if((E != null) && E.ID().equalsIgnoreCase(id) && (!E.canBeUninvoked()) && Integer.toHexString(E.text().hashCode()).equalsIgnoreCase(hash))
				return E;
		}
		return null;
	}

	private static void hold(final MOB mob, final Perk K)
	{
		for(final Ability A : effectsOf(K.effects))
		{
			final String t = heldTattoo(K, A);
			if(mob.findTattoo(t) != null)
				continue;
			// as a rank's: one of the same kind from elsewhere is left alone, and this one is not added
			if(mob.fetchEffect(A.ID()) != null)
				continue;
			final Ability E = (Ability)A.copyOf();
			E.setMiscText(A.text());
			mob.addNonUninvokableEffect(E);
			mob.addTattoo(t);
		}
	}

	private static void release(final MOB mob, final String tattoo)
	{
		final String[] p = tattoo.split("\\|");
		if(p.length >= 4)
		{
			final Ability E = heldEffect(mob, p[2], p[3]);
			if(E != null)
			{
				E.unInvoke();
				mob.delEffect(E);
			}
		}
		mob.delTattoo(tattoo);
	}

	/**
	 * A05: puts a character's perk effects right with perks.ini as it is now:
	 * a perk's new effects are added, and the effects of a perk taken out of
	 * the world (or of an effect taken off a perk) go.  At login and PERKS.
	 * @param mob the character
	 */
	public static void sync(final MOB mob)
	{
		if((mob == null) || (mob.playerStats() == null))
			return;
		final List<String> stale = new ArrayList<String>();
		for(final Enumeration<Tattoo> e = mob.tattoos(); e.hasMoreElements();)
		{
			final Tattoo T = e.nextElement();
			if((T == null) || (!T.getTattooName().startsWith(HELD)))
				continue;
			final String[] p = T.getTattooName().split("\\|");
			final Perk K = (p.length >= 4) ? byId(p[1]) : null;
			boolean still = false;
			if((K != null) && has(mob, K))
			{
				for(final Ability A : effectsOf(K.effects))
					still = still || heldTattoo(K, A).equals(T.getTattooName());
			}
			if(!still)
				stale.add(T.getTattooName());
		}
		for(final String t : stale)
			release(mob, t);
		for(final Perk K : plan().perks)
		{
			if((K.effects.length() > 0) && has(mob, K))
				hold(mob, K);
		}
		if(!stale.isEmpty())
		{
			mob.recoverCharStats();
			mob.recoverPhyStats();
			mob.recoverMaxState();
		}
	}

	/**
	 * A05: perks a character took that are no longer in this world: their
	 * tattoo stays, their pick is free again, their effects have gone.
	 * @param mob the character
	 * @return their IDs
	 */
	public static List<String> removed(final MOB mob)
	{
		final List<String> out = new ArrayList<String>();
		for(final Enumeration<Tattoo> e = mob.tattoos(); e.hasMoreElements();)
		{
			final Tattoo T = e.nextElement();
			if((T != null) && T.getTattooName().startsWith(TAKEN) && (byId(T.getTattooName().substring(TAKEN.length())) == null))
				out.add(T.getTattooName().substring(TAKEN.length()));
		}
		return out;
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
		if(mob.findTattoo(TAKEN + K.id) == null)
			mob.addTattoo(TAKEN + K.id);
		if(K.effects.length() > 0)
		{
			hold(mob, K);
			mob.recoverCharStats();
			mob.recoverPhyStats();
			mob.recoverMaxState();
		}
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
		if(!has(mob, K))
			return;
		mob.delTattoo(K.id);
		mob.delTattoo(TAKEN + K.id);
		final List<String> held = new ArrayList<String>();
		for(final Enumeration<Tattoo> e = mob.tattoos(); e.hasMoreElements();)
		{
			final Tattoo T = e.nextElement();
			if((T != null) && T.getTattooName().startsWith(HELD + K.id + "|"))
				held.add(T.getTattooName());
		}
		for(final String t : held)
			release(mob, t);
		if(!held.isEmpty())
		{
			mob.recoverCharStats();
			mob.recoverPhyStats();
			mob.recoverMaxState();
		}
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
		sync(mob);
		final StringBuilder owned = new StringBuilder("");
		final StringBuilder open = new StringBuilder("");
		final StringBuilder locked = new StringBuilder("");
		final Set<String> pools = new HashSet<String>();
		final Set<String> trees = new LinkedHashSet<String>();
		boolean anyRefund = false;
		for(final Perk K : P.perks)
		{
			if(K.tree.length() > 0)
				trees.add(K.tree);
			final String line = K.name + ((K.tree.length() > 0) ? (" [" + K.tree + "]") : "") + ((K.help.length() > 0) ? (": " + K.help) : "");
			if(has(mob, K))
			{
				owned.append("  ").append(line).append(refundable(K) ? " (can be given back)" : "").append("\n\r");
				anyRefund = anyRefund || refundable(K);
			}
			else
			if(K.pool.equalsIgnoreCase(TRAIT))
				continue;
			else
			{
				pools.add(K.pool);
				final String why = why(mob, K, false);
				if(why == null)
					open.append("  ^H").append(K.name).append("^N (").append(K.pool.toLowerCase()).append(", ").append(costText(K))
						.append((K.tree.length() > 0) ? (", " + K.tree) : "").append(")")
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
		final List<String> gone = removed(mob);
		if(gone.size() > 0)
			s.append(CMLib.lang().L("^HNo longer in this world^N (their picks are free again): @x1\n\r", String.join(", ", gone)));
		if(trees.size() > 0)
			s.append(CMLib.lang().L("Trees: @x1 (PERKS TREE [name]).\n\r", String.join(", ", trees)));
		if(anyRefund)
			s.append(CMLib.lang().L("PERK REFUND [name] gives a perk back, and its pick, when nothing you have stands on it.\n\r"));
		return s.toString();
	}

	/**
	 * A05: PERKS TREE: a tree's perks, which are held, open and locked, and what each is next to.
	 * @param mob the character
	 * @param name the tree
	 * @return the text
	 */
	public static String tree(final MOB mob, final String name)
	{
		final StringBuilder s = new StringBuilder("");
		String title = null;
		for(final Perk K : plan().perks)
		{
			if(!K.tree.equalsIgnoreCase(name.trim()))
				continue;
			if(title == null)
			{
				title = K.tree;
				s.append("^H").append(title).append("^N:\n\r");
			}
			final String why = has(mob, K) ? null : why(mob, K, false);
			s.append(has(mob, K) ? "  [x] " : ((why == null) ? "  [ ] " : "  [-] ")).append(K.name);
			if(K.root)
				s.append(" (a way in)");
			final List<String> next = new ArrayList<String>();
			for(final Perk L : linked(K))
				next.add(L.name);
			if(next.size() > 0)
				s.append(", next to ").append(String.join(", ", next));
			if((why != null) && (!has(mob, K)))
				s.append(": ").append(why);
			s.append("\n\r");
		}
		if(title == null)
			return CMLib.lang().L("There is no perk tree called '@x1'.", name);
		return s.append(CMLib.lang().L("[x] yours, [ ] can be taken, [-] not yet.\n\r")).toString();
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
