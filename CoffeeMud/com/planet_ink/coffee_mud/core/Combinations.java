package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
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
 * I10: USE X ON Y and COMBINE A WITH B.  First USE_PROG or COMBINE_PROG on
 * either thing; then world/combinations.txt, one row a line (# for a note):
 * A | B | RESULT | keys, where A and B are a name (or part of one), tag:X or
 * catalog:X (B empty for USE X alone), RESULT a catalog item or class, or
 * nothing, and the keys VERB=USE|COMBINE (either, by default), CONSUME=A,B
 * (A, B, BOTH or NONE), MASK= on who does it, SKILL= an ability they must
 * have, FAILMSG= when they do not, MSG= what the room sees (&lt;S-NAME&gt; who,
 * &lt;O-NAME&gt; the first, &lt;T-NAME&gt; the second), ACTIONS= actions (as
 * Prop_Switch's) for the first thing.  The rest: "Nothing happens."
 */
public final class Combinations
{
	private Combinations()
	{
	}

	/** one row of the table */
	public static class Row
	{
		public String				a		= "";
		public String				b		= "";
		public String				result	= "";
		public Map<String, String>	keys	= new HashMap<String, String>();

		public String key(final String k, final String dflt)
		{
			return CMParms.getParmStr(keys, k, dflt);
		}
	}

	private static List<Row>	rows	= new ArrayList<Row>();
	private static long			loaded	= -1;
	private static String		loadedFrom	= "";

	/**
	 * @return the table's file, world/combinations.txt
	 */
	public static File file()
	{
		final File W = WorldFiles.worldDir();
		return new File((W == null) ? new File(".") : W, "combinations.txt");
	}

	/**
	 * Reads a table's text.
	 * @param text the lines
	 * @return the rows
	 */
	public static List<Row> parse(final String text)
	{
		final List<Row> list = new ArrayList<Row>();
		for(final String raw : text.split("\\r?\\n"))
		{
			final String l = raw.trim();
			if((l.length() == 0) || l.startsWith("#"))
				continue;
			final String[] p = l.split("\\|", 4);
			if(p.length < 2)
				continue;
			final Row r = new Row();
			r.a = p[0].trim();
			r.b = p[1].trim();
			r.result = (p.length > 2) ? p[2].trim() : "";
			r.keys = CMParms.parseExactParms((p.length > 3) ? p[3].trim() : "");
			if(r.a.length() > 0)
				list.add(r);
		}
		return list;
	}

	/**
	 * @return the rows of world/combinations.txt, read again when it changes
	 */
	public static synchronized List<Row> rows()
	{
		final File F = file();
		final long mod = F.isFile() ? F.lastModified() : 0;
		if((mod != loaded) || (!F.getPath().equals(loadedFrom)))
		{
			loaded = mod;
			loadedFrom = F.getPath();
			rows = new ArrayList<Row>();
			if(mod > 0)
			{
				try
				{
					rows = parse(new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"));
				}
				catch(final Exception e)
				{
					Log.errOut("Combinations", e.getMessage());
				}
			}
		}
		return rows;
	}

	/**
	 * Whether a thing fits a row's A or B.
	 * @param spec a name, tag:X or catalog:X
	 * @param E the thing
	 * @return true if it fits
	 */
	public static boolean fits(final String spec, final Environmental E)
	{
		if(E == null)
			return spec.length() == 0;
		if(spec.length() == 0)
			return false;
		final String s = spec.trim();
		if(s.toLowerCase().startsWith("tag:"))
			return (E instanceof Taggable) && ((Taggable)E).hasTag(s.substring(4).trim());
		if(s.toLowerCase().startsWith("catalog:"))
			return E.Name().equalsIgnoreCase(s.substring(8).trim());
		return E.Name().equalsIgnoreCase(s) || CMLib.english().containsString(E.Name(), s);
	}

	/**
	 * The row for two things.
	 * @param verb USE or COMBINE
	 * @param a the first thing
	 * @param b the second, or null
	 * @return the row, or null
	 */
	public static Row find(final String verb, final Environmental a, final Environmental b)
	{
		for(final Row r : rows())
		{
			final String v = r.key("VERB", "").toUpperCase();
			if((v.length() > 0) && (!v.equals(verb)) && (!v.equals("ANY")))
				continue;
			if((fits(r.a, a) && fits(r.b, b)) || ((b != null) && fits(r.a, b) && fits(r.b, a)))
				return r;
		}
		return null;
	}

	/**
	 * Does what a row says.
	 * @param mob who does it
	 * @param r the row
	 * @param a the first thing
	 * @param b the second, or null
	 * @return true if it was done
	 */
	public static boolean apply(final MOB mob, final Row r, final Environmental a, final Environmental b)
	{
		final Room R = mob.location();
		if(R == null)
			return false;
		final String mask = r.key("MASK", "").trim();
		final String skill = r.key("SKILL", "").trim();
		if(((mask.length() > 0) && (!CMLib.masking().maskCheck(mask, mob, true)))
		|| ((skill.length() > 0) && (mob.fetchAbility(skill) == null)))
		{
			mob.tell(mob, b, a, r.key("FAILMSG", CMLib.lang().L("You do not know how.")));
			return true;
		}
		// the first thing as A, the second as B, in the row's order
		final Environmental A = fits(r.a, a) ? a : b;
		final Environmental B = (A == a) ? b : a;
		final String msg = r.key("MSG", "");
		if(msg.length() > 0)
			R.show(mob, B, A, CMMsg.MSG_OK_ACTION, msg);
		if(r.result.length() > 0)
		{
			final Item I = Transform.make(r.result);
			if(I == null)
				Log.errOut("Combinations", "no item called " + r.result);
			else
			{
				mob.addItem(I);
				mob.tell(CMLib.lang().L("You now have @x1.", I.name(mob)));
			}
		}
		final String actions = r.key("ACTIONS", "");
		if(actions.length() > 0)
			Actions.run((A instanceof PhysicalAgent) ? (PhysicalAgent)A : R, mob, actions);
		final String consume = r.key("CONSUME", "").toUpperCase();
		if((consume.indexOf('A') >= 0) || consume.equals("BOTH"))
			consume(A);
		if(((consume.indexOf('B') >= 0) && (!consume.equals("BOTH")) && (B != A)) || (consume.equals("BOTH") && (B != A)))
			consume(B);
		mob.recoverPhyStats();
		return true;
	}

	protected static void consume(final Environmental E)
	{
		if((E instanceof Item) && (!((Item)E).amDestroyed()))
			((Item)E).destroy();
	}

	/**
	 * USE or COMBINE: the scripts of either thing, then the table.
	 * @param mob who does it
	 * @param verb USE or COMBINE
	 * @param a the first thing
	 * @param b the second, or null
	 * @return true if something happened
	 */
	public static boolean act(final MOB mob, final String verb, final Environmental a, final Environmental b)
	{
		if(ScriptEvents.use(verb, mob, a, b))
			return true;
		final Row r = find(verb, a, b);
		if(r != null)
			return apply(mob, r, a, b);
		mob.tell(CMLib.lang().L("Nothing happens."));
		return false;
	}

	/**
	 * Finds what a USE or COMBINE names: carried, then in the room, its mobs, its exits.
	 * @param mob who
	 * @param name the name, or a direction
	 * @return the thing, or null
	 */
	public static Environmental thing(final MOB mob, final String name)
	{
		final Room R = mob.location();
		if((R == null) || (name == null) || (name.trim().length() == 0))
			return null;
		final String n = name.trim();
		final Item I = mob.fetchItem(null, Wearable.FILTER_ANY, n);
		if(I != null)
			return I;
		final int d = CMLib.directions().getGoodDirectionCode(n);
		if((d >= 0) && (R.getExitInDir(d) != null))
			return R.getExitInDir(d);
		final Environmental E = R.fetchFromRoomFavorItems(null, n);
		if(E != null)
			return E;
		for(int x = 0; x < Directions.NUM_DIRECTIONS(); x++)
		{
			final Exit X = R.getExitInDir(x);
			if((X != null) && (CMLib.english().containsString(X.name(), n) || CMLib.english().containsString(X.doorName(), n)))
				return X;
		}
		return null;
	}
}
