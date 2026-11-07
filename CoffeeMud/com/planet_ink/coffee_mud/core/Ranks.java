package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.Faction;
import com.planet_ink.coffee_mud.Common.interfaces.Tattoo;
import com.planet_ink.coffee_mud.Libraries.interfaces.AchievementLibrary;
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
 * P25: rank ladders, world/ranks.ini.  [KEY] starts one, with NAME=,
 * RANKS="Footpad, Cutpurse, Shadow, Master" and FACTION= (a standing shown
 * beside it); for each rank n, RANKn.REQUIRES (a mask: met, the character
 * rises to it by itself; none, only MPRANK raises them), RANKn.REWARDS
 * (achievement rewards, once), RANKn.EFFECTS (Prop_SpellAdder's form, held
 * while at the rank), RANKn.TITLE and RANKn.NEXT (what the RANKS list says
 * the rank needs).  A character's rank is the tattoo KEY_n.
 */
public class Ranks
{
	private Ranks()
	{
	}

	/** A ladder. */
	public static class Ladder
	{
		public final String key;
		public final Map<String, String> vals = new HashMap<String, String>();

		Ladder(final String key)
		{
			this.key = key;
		}

		/** @return its name */
		public String name()
		{
			final String n = vals.get("NAME");
			return ((n == null) || (n.length() == 0)) ? CMStrings.capitalizeAndLower(key) : unq(n);
		}

		/** @return its ranks' names, lowest first */
		public List<String> ranks()
		{
			return CMParms.parseCommas(unq(get("RANKS")), true);
		}

		/**
		 * @param key a field
		 * @return it, quotes off, or ""
		 */
		public String get(final String key)
		{
			final String v = vals.get(key.toUpperCase());
			return (v == null) ? "" : unq(v);
		}

		/**
		 * @param n a rank, 1 up
		 * @param field REQUIRES, REWARDS, EFFECTS, TITLE or NEXT
		 * @return that rank's field, or ""
		 */
		public String rank(final int n, final String field)
		{
			return get("RANK" + n + "." + field);
		}

		/**
		 * @param n a rank, 0 for none
		 * @return its name
		 */
		public String rankName(final int n)
		{
			final List<String> R = ranks();
			return ((n < 1) || (n > R.size())) ? CMLib.lang().L("none") : R.get(n - 1);
		}
	}

	private static String unq(final String v)
	{
		final String s = (v == null) ? "" : v.trim();
		if((s.length() >= 2) && (s.startsWith("\"") && s.endsWith("\"")))
			return s.substring(1, s.length() - 1);
		return s;
	}

	private static List<Ladder> cache = null;
	private static long cacheStamp = -1;

	/** @return world/ranks.ini, or null with no world folder */
	public static File file()
	{
		final File W = WorldFiles.worldDir();
		return (W == null) ? null : new File(W, "ranks.ini");
	}

	/**
	 * @return every ladder, re-read when the file changes
	 */
	public static synchronized List<Ladder> all()
	{
		final File F = file();
		if((F == null) || (!F.isFile()))
			return new ArrayList<Ladder>(0);
		if((cache != null) && (cacheStamp == F.lastModified()))
			return cache;
		try
		{
			cache = parse(new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"));
			cacheStamp = F.lastModified();
		}
		catch(final Exception e)
		{
			Log.errOut("Ranks", "ranks.ini: " + e.getMessage());
			cache = new ArrayList<Ladder>(0);
		}
		return cache;
	}

	/**
	 * @param text a ranks.ini
	 * @return its ladders
	 */
	public static List<Ladder> parse(final String text)
	{
		final List<Ladder> all = new ArrayList<Ladder>();
		Ladder L = null;
		final String[] lines = text.replace("\r", "").split("\n");
		for(int l = 0; l < lines.length; l++)
		{
			String line = lines[l].trim();
			while(line.endsWith("\\") && (l + 1 < lines.length))
				line = line.substring(0, line.length() - 1).trim() + " " + lines[++l].trim();
			if((line.length() == 0) || line.startsWith("#") || line.startsWith(";"))
				continue;
			if(line.startsWith("[") && line.endsWith("]"))
			{
				L = new Ladder(line.substring(1, line.length() - 1).trim().toUpperCase().replace(' ', '_'));
				all.add(L);
				continue;
			}
			final int x = line.indexOf('=');
			if((L == null) || (x <= 0))
				continue;
			L.vals.put(line.substring(0, x).trim().toUpperCase(), line.substring(x + 1).trim());
		}
		return all;
	}

	/**
	 * @param key a ladder's key or name
	 * @return it, or null
	 */
	public static Ladder find(final String key)
	{
		if(key == null)
			return null;
		for(final Ladder L : all())
		{
			if(L.key.equalsIgnoreCase(key.trim().replace(' ', '_')) || L.name().equalsIgnoreCase(key.trim()))
				return L;
		}
		return null;
	}

	/**
	 * @param mob a character
	 * @param key a ladder
	 * @return their rank on it, 0 for none
	 */
	public static int rank(final MOB mob, final String key)
	{
		if(mob == null)
			return 0;
		final Ladder L = find(key);
		final String pre = ((L == null) ? key.toUpperCase().trim() : L.key) + "_";
		int best = 0;
		for(final Enumeration<Tattoo> t = mob.tattoos(); t.hasMoreElements();)
		{
			final Tattoo T = t.nextElement();
			final String n = T.getTattooName();
			if(n.startsWith(pre) && CMath.isInteger(n.substring(pre.length())))
				best = Math.max(best, CMath.s_int(n.substring(pre.length())));
		}
		return best;
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

	private static String title(final Ladder L, final int n)
	{
		return L.rank(n, "TITLE");
	}

	/**
	 * Sets a character's rank: its tattoo, the effects and title of the rank
	 * left and of the rank reached, the rewards of each rank gained (once),
	 * and the RANKUP achievements.
	 * @param mob the character
	 * @param L the ladder
	 * @param n the rank, 0 for none
	 * @return the rank now
	 */
	public static int set(final MOB mob, final Ladder L, final int n)
	{
		if((mob == null) || (L == null))
			return 0;
		final int to = Math.max(0, Math.min(n, L.ranks().size()));
		final int was = rank(mob, L.key);
		if(to == was)
			return was;
		if(was > 0)
		{
			mob.delTattoo(mob.findTattoo(L.key + "_" + was));
			for(final Ability A : effectsOf(L.rank(was, "EFFECTS")))
			{
				// only the effect the rank put on (lasting, with its text): not one of
				// the same kind from a spell, an item or another ladder
				for(int e = mob.numEffects() - 1; e >= 0; e--)
				{
					final Ability E = mob.fetchEffect(e);
					if((E != null) && E.ID().equals(A.ID()) && (!E.canBeUninvoked()) && E.text().equals(A.text()))
					{
						E.unInvoke();
						mob.delEffect(E);
						break;
					}
				}
			}
			final String t = title(L, was);
			if((t.length() > 0) && (mob.playerStats() != null))
				mob.playerStats().delTitle(t);
		}
		if(to > 0)
		{
			mob.addTattoo(L.key + "_" + to);
			for(final Ability A : effectsOf(L.rank(to, "EFFECTS")))
			{
				if(mob.fetchEffect(A.ID()) == null)
				{
					final Ability E = (Ability)A.copyOf();
					E.setMiscText(A.text());
					mob.addNonUninvokableEffect(E);
				}
			}
			final String t = title(L, to);
			if((t.length() > 0) && (mob.playerStats() != null) && (!mob.playerStats().getTitles().contains(t)))
				mob.playerStats().addTitle(t);
			for(int r = was + 1; r <= to; r++)
			{
				final String rew = L.rank(r, "REWARDS");
				if((rew.length() > 0) && (mob.findTattoo("RANKREWARD_" + L.key + "_" + r) == null))
				{
					mob.addTattoo("RANKREWARD_" + L.key + "_" + r);
					CMLib.achievements().giveAwards(mob, rew);
				}
			}
			mob.tell(CMLib.lang().L("^HYou are now @x1 (@x2/@x3) of @x4.^N", L.rankName(to), "" + to, "" + L.ranks().size(), L.name()));
		}
		else
			mob.tell(CMLib.lang().L("^HYou are no longer of @x1.^N", L.name()));
		mob.recoverCharStats();
		mob.recoverPhyStats();
		mob.recoverMaxState();
		if(to > was)
			CMLib.achievements().possiblyBumpAchievement(mob, AchievementLibrary.Event.RANKUP, to - was, L.key);
		return to;
	}

	/**
	 * Raises a character up every ladder whose next ranks' REQUIRES they meet.
	 * @param mob the character
	 */
	public static void check(final MOB mob)
	{
		if((mob == null) || (mob.playerStats() == null))
			return;
		for(final Ladder L : all())
		{
			for(int guard = 0; guard < 50; guard++)
			{
				final int now = rank(mob, L.key);
				final int next = now + 1;
				if(next > L.ranks().size())
					break;
				final String req = L.rank(next, "REQUIRES");
				if((req.length() == 0) || (!CMLib.masking().maskCheck(req, mob, true)))
					break;
				set(mob, L, next);
			}
		}
	}

	/**
	 * RANKS: the ladders the character is on, or may climb by themselves.
	 * @param mob the character
	 * @return the text
	 */
	public static String list(final MOB mob)
	{
		check(mob);
		final StringBuilder s = new StringBuilder("");
		for(final Ladder L : all())
		{
			final int n = rank(mob, L.key);
			if((n == 0) && (L.rank(1, "REQUIRES").length() == 0) && (L.rank(1, "NEXT").length() == 0))
				continue;
			s.append("^H").append(L.name()).append("^N: ");
			if(n > 0)
				s.append(L.rankName(n)).append(" (").append(n).append("/").append(L.ranks().size()).append(")");
			else
				s.append(CMLib.lang().L("not a member"));
			if(n < L.ranks().size())
			{
				final String need = L.rank(n + 1, "NEXT");
				if(need.length() > 0)
					s.append(CMLib.lang().L(": next rank needs @x1", need));
			}
			final String fid = L.get("FACTION");
			if(fid.length() > 0)
			{
				final Faction F = CMLib.factions().getFaction(fid);
				if((F != null) && (mob.fetchFaction(F.factionID()) != Integer.MAX_VALUE))
				{
					final Faction.FRange FR = F.fetchRange(mob.fetchFaction(F.factionID()));
					s.append(CMLib.lang().L(", standing @x1", (FR == null) ? ("" + mob.fetchFaction(F.factionID())) : FR.name()));
				}
			}
			s.append("\n\r");
		}
		return (s.length() == 0) ? CMLib.lang().L("You hold no ranks.") : s.toString();
	}

	/**
	 * MPRANK's change: +n, -n, =n, or CHECK.
	 * @param mob the character
	 * @param key the ladder
	 * @param how the change
	 * @return the rank now, or -1 for no such ladder
	 */
	public static int change(final MOB mob, final String key, final String how)
	{
		final Ladder L = find(key);
		if(L == null)
			return -1;
		final String h = (how == null) ? "+1" : how.trim();
		final int now = rank(mob, L.key);
		if(h.equalsIgnoreCase("CHECK"))
		{
			check(mob);
			return rank(mob, L.key);
		}
		if(h.startsWith("="))
			return set(mob, L, CMath.s_int(h.substring(1).trim()));
		if(h.startsWith("-"))
			return set(mob, L, now - Math.max(1, CMath.s_int(h.substring(1).trim())));
		final String up = h.startsWith("+") ? h.substring(1).trim() : h;
		return set(mob, L, now + ((up.length() == 0) ? 1 : CMath.s_int(up)));
	}
}
