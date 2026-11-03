package com.planet_ink.coffee_mud.core;

import java.io.File;
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
 * CR2: the world's own materials, world/materials.txt, one resource a line,
 * the fields between tabs: NAME, FAMILY (METAL, WOODEN, VEGETATION and the
 * like), VALUE, FREQUENCY (1-10, how common), HARDNESS (1-10), BUOYANCY,
 * CATEGORY (FISH, BERRY or WOODY, or blank), SMELL, EFFECTS (as
 * Spell_Light;Prop_HaveResister(fire 5)), DESCRIPTION and CODE (a fixed
 * number, if it must never move).  A # starts a comment, and a first line
 * starting NAME is a heading.  RawMaterial.CODES reads it once, after the
 * built-in resources: a row named as a built-in changes that one; the rest
 * come after the built-ins, at their CODE or sorted by name.
 */
public final class WorldMaterials
{
	private WorldMaterials()
	{
	}

	/** a row of the file */
	public static class Row
	{
		public String	name		= "";
		public String	family		= "";
		public int		value		= 0;
		public int		frequency	= 5;
		public int		hardness	= 1;
		public int		buoyancy	= 1000;
		public String	category	= "";
		public String	smell		= "";
		public String	effects		= "";
		public String	description	= "";
		public int		code		= -1;
		public int		line		= 0;
	}

	/** @return where the file is */
	public static File file()
	{
		return new File(com.planet_ink.coffee_mud.core.save.WorldFiles.worldDir(), "materials.txt");
	}

	private static int num(final String[] f, final int i, final int dflt)
	{
		return ((f.length > i) && (f[i].trim().length() > 0)) ? CMath.s_int(f[i].trim()) : dflt;
	}

	private static String str(final String[] f, final int i)
	{
		return (f.length > i) ? f[i].trim() : "";
	}

	/**
	 * @return the file's rows, none if there is no file
	 */
	public static List<Row> read()
	{
		final List<Row> rows = new ArrayList<Row>();
		final File F = file();
		if(!F.isFile())
			return rows;
		final List<String> lines;
		try
		{
			lines = java.nio.file.Files.readAllLines(F.toPath(), java.nio.charset.StandardCharsets.UTF_8);
		}
		catch(final Exception e)
		{
			Log.errOut("WorldMaterials", F.getPath() + ": " + e.getMessage());
			return rows;
		}
		for(int n = 0; n < lines.size(); n++)
		{
			final String line = lines.get(n);
			final String t = line.trim();
			if((t.length() == 0) || t.startsWith("#") || ((rows.size() == 0) && t.toUpperCase().startsWith("NAME\t")))
				continue;
			final String[] f = line.split("\t", -1);
			final Row R = new Row();
			R.name = str(f, 0).toUpperCase();
			R.family = str(f, 1).toUpperCase();
			if((R.name.length() == 0) || (R.family.length() == 0))
			{
				Log.errOut("WorldMaterials", F.getName() + " line " + (n + 1) + ": a row needs a NAME and a FAMILY, between tabs");
				continue;
			}
			R.value = num(f, 2, 0);
			R.frequency = num(f, 3, 5);
			R.hardness = num(f, 4, 1);
			R.buoyancy = num(f, 5, 1000);
			R.category = str(f, 6).toUpperCase();
			R.smell = str(f, 7);
			R.effects = str(f, 8);
			R.description = str(f, 9);
			R.code = num(f, 10, -1);
			R.line = n + 1;
			rows.add(R);
		}
		return rows;
	}
}
