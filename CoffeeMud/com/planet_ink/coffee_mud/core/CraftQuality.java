package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Armor;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Items.interfaces.Weapon;
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
 * CR17: rolled quality tiers for what a player crafts, from
 * resources/skills/craftquality.txt (a world's own copy first), one tier a
 * line, tab between: NAME, CHANCE (a formula: @x1 the crafter's level less
 * the item's, @x2 their proficiency, @x3 the station's QUALITY), VALUE,
 * ARMOR, DAMAGE and USES (multipliers), LEVEL (added) and EFFECTS
 * (ABILITY(PARMS);...).  The tiers are tried in order and the first that
 * rolls under its chance is the item's: its name gets the tier's word and
 * its stats change.  No tiers (the shipped file has them all in comments),
 * a GenCraftSkill with QUALITY off, or a recipe with NOQUALITY=1: nothing.
 */
public final class CraftQuality
{
	private CraftQuality()
	{
	}

	/** a tier */
	public static class Tier
	{
		public String	name	= "";
		public String	chance	= "0";
		public double	value	= 1.0;
		public double	armor	= 1.0;
		public double	damage	= 1.0;
		public double	uses	= 1.0;
		public int		level	= 0;
		public String	effects	= "";
	}

	private static List<Tier>	tiers		= new ArrayList<Tier>();
	private static String		loadedKey	= null;

	/**
	 * @return the tiers, read again when the file changes
	 */
	public static synchronized List<Tier> tiers()
	{
		final CMFile F = new CMFile(Resources.makeFileResourceName("skills/craftquality.txt"), null, CMFile.FLAG_FORCEALLOW);
		final String key = F.canRead() ? (F.getAbsolutePath() + "|" + F.lastModified() + "|" + F.text().length()) : "";
		if(key.equals(loadedKey))
			return tiers;
		loadedKey = key;
		final List<Tier> L = new ArrayList<Tier>();
		if(F.canRead())
		{
			for(final String line : F.text().toString().split("\\r?\\n"))
			{
				if((line.trim().length() == 0) || line.trim().startsWith("#"))
					continue;
				final String[] f = line.split("\\t");
				if(f.length < 2)
					continue;
				final Tier T = new Tier();
				T.name = f[0].trim();
				T.chance = f[1].trim();
				T.value = (f.length > 2) && (f[2].trim().length() > 0) ? CMath.s_double(f[2].trim()) : 1.0;
				T.armor = (f.length > 3) && (f[3].trim().length() > 0) ? CMath.s_double(f[3].trim()) : 1.0;
				T.damage = (f.length > 4) && (f[4].trim().length() > 0) ? CMath.s_double(f[4].trim()) : 1.0;
				T.uses = (f.length > 5) && (f[5].trim().length() > 0) ? CMath.s_double(f[5].trim()) : 1.0;
				T.level = (f.length > 6) ? CMath.s_int(f[6].trim()) : 0;
				T.effects = (f.length > 7) ? f[7].trim() : "";
				if(T.name.length() > 0)
					L.add(T);
			}
		}
		tiers = L;
		return tiers;
	}

	/**
	 * @param mob the crafter
	 * @param skill the skill
	 * @param I what was made
	 * @return the tier it rolls, or null
	 */
	public static Tier roll(final MOB mob, final Ability skill, final Item I)
	{
		final List<Tier> L = tiers();
		if(L.isEmpty() || (mob == null) || (I == null))
			return null;
		final double[] vars = { mob.phyStats().level() - I.basePhyStats().level(), (skill == null) ? 0 : skill.proficiency(),
			(skill == null) ? 0 : Workstations.quality(mob, skill) };
		for(final Tier T : L)
		{
			double chance;
			try
			{
				chance = CMath.parseMathExpression(T.chance, vars);
			}
			catch(final Exception e)
			{
				chance = 0;
			}
			if((chance > 0) && (CMLib.dice().rollPercentage() <= chance))
				return T;
		}
		return null;
	}

	/**
	 * @param I what was made
	 * @param T its tier
	 */
	public static void apply(final Item I, final Tier T)
	{
		if((I == null) || (T == null))
			return;
		final String old = I.Name();
		I.setName(CMLib.english().insertUnColoredAdjective(old, T.name));
		if((I.displayText() != null) && (I.displayText().indexOf(old) >= 0))
			I.setDisplayText(I.displayText().replace(old, I.Name()));
		if(T.value != 1.0)
			I.setBaseValue((int)Math.round(I.baseGoldValue() * T.value));
		if((T.armor != 1.0) && (I instanceof Armor))
			I.basePhyStats().setArmor((int)Math.round(I.basePhyStats().armor() * T.armor));
		if((T.damage != 1.0) && (I instanceof Weapon))
			I.basePhyStats().setDamage((int)Math.round(I.basePhyStats().damage() * T.damage));
		if((T.uses != 1.0) && (I.usesRemaining() > 0) && (I.usesRemaining() < Integer.MAX_VALUE / 4))
			I.setUsesRemaining((int)Math.round(I.usesRemaining() * T.uses));
		if(T.level != 0)
			I.basePhyStats().setLevel(Math.max(1, I.basePhyStats().level() + T.level));
		for(final String e : CMParms.parseSemicolons(T.effects, true))
		{
			final String s = e.trim();
			final int x = s.indexOf('(');
			final Ability A = CMClass.getAbility((x > 0) ? s.substring(0, x).trim() : s);
			if(A == null)
				continue;
			A.setMiscText(((x > 0) && s.endsWith(")")) ? s.substring(x + 1, s.length() - 1) : "");
			I.addNonUninvokableEffect(A);
		}
		I.recoverPhyStats();
	}
}
