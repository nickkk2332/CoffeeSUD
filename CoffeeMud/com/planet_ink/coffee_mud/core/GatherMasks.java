package com.planet_ink.coffee_mud.core;

import java.util.*;

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
 * CR10: a MASK column for the gathering tables: hunting.txt and fishing.txt
 * (the fifth column), herbology.txt and floristry.txt (the second) and
 * mycology.txt (the fifth).  A row with a mask is a choice only when the
 * hunter, fisher or gatherer, or their room, passes it: mob keys (-LEVEL,
 * -CLASS) test them, room keys (-AREA, -SEASON, -HOUR, -WEATHER) either.
 * A row without one is as before.
 */
public final class GatherMasks
{
	private GatherMasks()
	{
	}

	/**
	 * @param mask a zapper mask, or ""
	 * @param mob the gatherer
	 * @return whether it passes for them or their room; "" always does
	 */
	public static boolean passes(final String mask, final MOB mob)
	{
		if((mask == null) || (mask.trim().length() == 0))
			return true;
		if(mob == null)
			return false;
		if(CMLib.masking().maskCheck(mask.trim(), mob, true))
			return true;
		final Room R = mob.location();
		return (R != null) && CMLib.masking().maskCheck(mask.trim(), R, true);
	}

	/**
	 * @param row a table row
	 * @param col its MASK column
	 * @param mob the gatherer
	 * @return whether the row is a choice for them
	 */
	public static boolean passes(final List<String> row, final int col, final MOB mob)
	{
		return (row == null) || (row.size() <= col) || passes(row.get(col), mob);
	}

	/**
	 * @param rows a table
	 * @param col its MASK column
	 * @return whether any row has a mask
	 */
	public static boolean anyMask(final List<List<String>> rows, final int col)
	{
		for(final List<String> row : rows)
		{
			if((row.size() > col) && (row.get(col).trim().length() > 0))
				return true;
		}
		return false;
	}

	/**
	 * A weighted pick among a table's rows that pass, each weighing its
	 * frequency plus one.
	 * @param rows the table
	 * @param freqCol its frequency column
	 * @param maskCol its MASK column
	 * @param mob the gatherer
	 * @return a row, or null if none passes
	 */
	public static List<String> pick(final List<List<String>> rows, final int freqCol, final int maskCol, final MOB mob)
	{
		final List<List<String>> ok = new ArrayList<List<String>>();
		int total = 0;
		for(final List<String> row : rows)
		{
			if((row.size() > freqCol) && (row.get(0).trim().length() > 0) && passes(row, maskCol, mob))
			{
				ok.add(row);
				total += Math.max(0, CMath.s_int(row.get(freqCol))) + 1;
			}
		}
		if(total <= 0)
			return null;
		int roll = CMLib.dice().roll(1, total, 0);
		for(final List<String> row : ok)
		{
			roll -= Math.max(0, CMath.s_int(row.get(freqCol))) + 1;
			if(roll <= 0)
				return row;
		}
		return ok.get(ok.size() - 1);
	}

	/**
	 * @param lines a list of names, each perhaps followed by a tab and a MASK
	 * @param mob the gatherer
	 * @return the names that pass for them
	 */
	public static List<String> names(final List<String> lines, final MOB mob)
	{
		final List<String> L = new ArrayList<String>();
		for(final String line : lines)
		{
			if(line == null)
				continue;
			final int t = line.indexOf('\t');
			final String name = ((t >= 0) ? line.substring(0, t) : line).trim();
			if((name.length() == 0) || name.startsWith("#"))
				continue;
			if(passes((t >= 0) ? line.substring(t + 1) : "", mob))
				L.add(name);
		}
		return L;
	}
}
