package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Food;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Items.interfaces.RawMaterial;

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
 * CR15: named resource variants as data, world/resources.txt, one a line,
 * the fields between tabs as in world/materials.txt: SUBTYPE, BASE_RESOURCE
 * (a resource, as HERBS, or a family, as VEGETATION; any when blank),
 * DISPLAY_NAME, VALUE (a unit), WEIGHT_PER_UNIT (kept for the format; a raw
 * resource's weight is its amount, so it is not used), DESCRIPTION, SMELL,
 * EFFECTS (as Spell_Light;Prop_HaveResister(fire 5)) and NOURISHMENT (a
 * unit, for food).  Whenever a raw resource of that base is given that
 * subtype (gathering, ResourceOverride, butchering, the fishing and hunting
 * tables, a skill's own), the row is put on it; its name follows the row
 * when it is bundled or split.  An item loaded from a file keeps what it was
 * saved with.  The file is read again when it changes.
 */
public final class ResourceVariants
{
	private ResourceVariants()
	{
	}

	/** a row of the file */
	public static class Row
	{
		public String	subType		= "";
		public String	base		= "";
		public String	display		= "";
		public int		value		= -1;
		public String	description	= "";
		public String	smell		= "";
		public String	effects		= "";
		public int		nourishment	= -1;
	}

	private static List<Row>	rows		= new ArrayList<Row>();
	private static String		loadedKey	= null;

	private static final ThreadLocal<Boolean> loading = new ThreadLocal<Boolean>();

	/** @return where the file is */
	public static File file()
	{
		return new File(com.planet_ink.coffee_mud.core.save.WorldFiles.worldDir(), "resources.txt");
	}

	/**
	 * While an item is loaded from a file, a subtype set on it does not bring
	 * the row: it keeps what it was saved with.
	 * @param on true while loading
	 * @return what it was
	 */
	public static boolean loading(final boolean on)
	{
		final Boolean was = loading.get();
		loading.set(Boolean.valueOf(on));
		return (was != null) && was.booleanValue();
	}

	/** @return the rows, read again when the file changes */
	public static synchronized List<Row> rows()
	{
		final File F = file();
		final String key = F.isFile() ? (F.getAbsolutePath() + "|" + F.lastModified() + "|" + F.length()) : "";
		if(key.equals(loadedKey))
			return rows;
		loadedKey = key;
		final List<Row> L = new ArrayList<Row>();
		if(F.isFile())
		{
			try
			{
				final List<String> lines = java.nio.file.Files.readAllLines(F.toPath(), java.nio.charset.StandardCharsets.UTF_8);
				for(final String line : lines)
				{
					final String t = line.trim();
					if((t.length() == 0) || t.startsWith("#") || ((L.size() == 0) && t.toUpperCase().startsWith("SUBTYPE\t")))
						continue;
					final String[] f = line.split("\t", -1);
					final Row R = new Row();
					R.subType = f[0].trim().toUpperCase();
					if(R.subType.length() == 0)
						continue;
					R.base = (f.length > 1) ? f[1].trim().toUpperCase() : "";
					R.display = (f.length > 2) ? f[2].trim() : "";
					R.value = ((f.length > 3) && (f[3].trim().length() > 0)) ? CMath.s_int(f[3].trim()) : -1;
					R.description = (f.length > 5) ? f[5].trim() : "";
					R.smell = (f.length > 6) ? f[6].trim() : "";
					R.effects = (f.length > 7) ? f[7].trim() : "";
					R.nourishment = ((f.length > 8) && (f[8].trim().length() > 0)) ? CMath.s_int(f[8].trim()) : -1;
					L.add(R);
				}
			}
			catch(final Exception e)
			{
				Log.errOut("ResourceVariants", F.getPath() + ": " + e.getMessage());
			}
		}
		rows = L;
		return rows;
	}

	/**
	 * @param I a raw resource
	 * @return its variant's row, or null
	 */
	public static Row of(final Item I)
	{
		if(!(I instanceof RawMaterial))
			return null;
		final String sub = ((RawMaterial)I).getSubType();
		if((sub == null) || (sub.length() == 0))
			return null;
		final List<Row> L = rows();
		if(L.isEmpty())
			return null;
		final String rsc = RawMaterial.CODES.NAME(I.material()).toUpperCase();
		final RawMaterial.Material fam = RawMaterial.Material.findByMask(I.material() & RawMaterial.MATERIAL_MASK);
		final String family = (fam == null) ? "" : fam.name();
		for(final Row R : L)
		{
			if(R.subType.equalsIgnoreCase(sub.trim())
			&&((R.base.length() == 0) || R.base.equals(rsc) || R.base.equals(family)))
				return R;
		}
		return null;
	}

	/**
	 * @param I a raw resource
	 * @return its variant's smell, or ""
	 */
	public static String smell(final Item I)
	{
		final Row R = of(I);
		return (R == null) ? "" : R.smell;
	}

	/**
	 * Puts a raw resource's variant row on it, when its subtype is set: name,
	 * value, description, effects and nourishment.
	 * @param I the raw resource
	 */
	public static void apply(final Item I)
	{
		final Boolean l = loading.get();
		if((l != null) && l.booleanValue())
			return;
		final Row R = of(I);
		if(R == null)
			return;
		final int units = Math.max(1, I.basePhyStats().weight());
		CMLib.materials().adjustResourceName(I);
		if(R.value >= 0)
			I.setBaseValue(R.value * units);
		if((R.nourishment >= 0) && (I instanceof Food))
			((Food)I).setNourishment(R.nourishment * units);
		for(final String e : CMParms.parseSemicolons(R.effects, true))
		{
			final String s = e.trim();
			final int x = s.indexOf('(');
			final Ability A = CMClass.getAbility((x > 0) ? s.substring(0, x).trim() : s);
			if((A == null) || (I.fetchEffect(A.ID()) != null))
				continue;
			A.setMiscText(((x > 0) && s.endsWith(")")) ? s.substring(x + 1, s.length() - 1) : "");
			I.addNonUninvokableEffect(A);
		}
		I.recoverPhyStats();
	}
}
