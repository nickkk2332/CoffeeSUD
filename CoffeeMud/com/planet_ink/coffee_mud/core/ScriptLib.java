package com.planet_ink.coffee_mud.core;

import java.util.*;

import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.CatalogLibrary;

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
 * The plain helpers behind the script language's newer functions (SC1 lists,
 * SC10 strings, SC11 numbers and dice, SC3 catalog copies): nothing here
 * knows a script.
 */
public final class ScriptLib
{
	private ScriptLib()
	{
	}

	/**
	 * SC1: a list as scripts keep one: split on commas if there are any,
	 * otherwise on spaces, quotes keeping words together.
	 * @param text the list
	 * @return its items
	 */
	public static List<String> listOf(final String text)
	{
		// a script's quotes reach here as `, as the script parser keeps them
		final String t = (text == null) ? "" : text.trim().replace('`', '"');
		final List<String> items = new ArrayList<String>();
		if(t.length() == 0)
			return items;
		if(t.indexOf(',') >= 0)
		{
			for(final String s : CMParms.parseCommas(t, true))
			{
				if(s.trim().length() > 0)
					items.add(s.trim());
			}
			return items;
		}
		for(final String s : CMParms.parse(t))
		{
			if(s.length() > 0)
				items.add(s);
		}
		return items;
	}

	/**
	 * SC1: a list written back as text, items with spaces quoted.
	 * @param items the items
	 * @return the text
	 */
	public static String listText(final List<String> items)
	{
		final StringBuilder str = new StringBuilder();
		for(final String s : items)
		{
			if(str.length() > 0)
				str.append(' ');
			if(s.indexOf(' ') >= 0)
				str.append('"').append(s).append('"');
			else
				str.append(s);
		}
		return str.toString();
	}

	/**
	 * SC1: MPLIST's changes to a list.
	 * @param list the list as text
	 * @param op ADD, ADDUNIQUE, DEL, CLEAR, SHUFFLE or SORT
	 * @param words the words, for ADD, ADDUNIQUE and DEL
	 * @return the list as text, or null for an unknown operation
	 */
	public static String listChange(final String list, final String op, final String words)
	{
		final List<String> items = listOf(list);
		final List<String> more = listOf(words);
		switch(op.toUpperCase())
		{
		case "ADD":
			items.addAll(more);
			break;
		case "ADDUNIQUE":
			for(final String w : more)
			{
				boolean has = false;
				for(final String s : items)
					has = has || s.equalsIgnoreCase(w);
				if(!has)
					items.add(w);
			}
			break;
		case "DEL":
			for(final String w : more)
			{
				for(final Iterator<String> i = items.iterator(); i.hasNext();)
				{
					if(i.next().equalsIgnoreCase(w))
						i.remove();
				}
			}
			break;
		case "CLEAR":
			items.clear();
			break;
		case "SHUFFLE":
			for(int i = items.size() - 1; i > 0; i--)
			{
				final int j = CMLib.dice().roll(1, i + 1, -1);
				final String x = items.get(i);
				items.set(i, items.get(j));
				items.set(j, x);
			}
			break;
		case "SORT":
			Collections.sort(items, String.CASE_INSENSITIVE_ORDER);
			break;
		default:
			return null;
		}
		return listText(items);
	}

	/**
	 * SC11: rolls dice written as 3d6+2, 1d20-1 or 2d8 (or a plain number).
	 * @param spec the dice
	 * @return the roll
	 */
	public static long dice(final String spec)
	{
		final String s = spec.replace(" ", "").toLowerCase();
		final int d = s.indexOf('d');
		if(d < 0)
			return CMath.s_long(s);
		final int n = (d == 0) ? 1 : CMath.s_int(s.substring(0, d));
		int end = s.length();
		long mod = 0;
		for(int i = d + 1; i < s.length(); i++)
		{
			final char c = s.charAt(i);
			if((c == '+') || (c == '-'))
			{
				end = i;
				mod = CMath.s_long(s.substring(i + 1)) * ((c == '-') ? -1 : 1);
				break;
			}
		}
		final int die = CMath.s_int(s.substring(d + 1, end));
		if((n <= 0) || (die <= 0))
			return mod;
		return CMLib.dice().roll(n, die, 0) + mod;
	}

	/**
	 * SC11: a pick by weight from words written word:weight.
	 * @param text the words
	 * @return the one picked, or ""
	 */
	public static String weightedPick(final String text)
	{
		final List<String> words = listOf(text);
		final List<String> names = new ArrayList<String>();
		final List<Integer> weights = new ArrayList<Integer>();
		int total = 0;
		for(final String w : words)
		{
			final int x = w.lastIndexOf(':');
			final String name = (x > 0) ? w.substring(0, x) : w;
			final int weight = (x > 0) ? Math.max(0, CMath.s_int(w.substring(x + 1))) : 1;
			names.add(name);
			weights.add(Integer.valueOf(weight));
			total += weight;
		}
		if(total <= 0)
			return "";
		int r = CMLib.dice().roll(1, total, -1);
		for(int i = 0; i < names.size(); i++)
		{
			r -= weights.get(i).intValue();
			if(r < 0)
				return names.get(i);
		}
		return names.get(names.size() - 1);
	}

	/**
	 * Whether a value compares to another: numbers as numbers, the rest as
	 * text, ignoring case.
	 * @param value the value
	 * @param cmp ==, !=, &gt;, &lt;, &gt;= or &lt;=
	 * @param against the other
	 * @return true if it does
	 */
	public static boolean compare(final String value, final String cmp, final String against)
	{
		final String v = (value == null) ? "" : value.trim();
		final String a = (against == null) ? "" : against.trim();
		final boolean nums = CMath.isNumber(v) && CMath.isNumber(a);
		final int c = nums ? Double.compare(CMath.s_double(v), CMath.s_double(a)) : v.compareToIgnoreCase(a);
		switch(cmp)
		{
		case "==": case "=": return c == 0;
		case "!=": case "<>": return c != 0;
		case ">": return c > 0;
		case "<": return c < 0;
		case ">=": case "=>": return c >= 0;
		case "<=": case "=<": return c <= 0;
		default: return false;
		}
	}

	/** the comparisons a test may end with */
	public static final String[]	COMPARES	= { "==", "!=", "<>", ">=", "<=", "=>", "=<", ">", "<", "=" };

	/**
	 * Splits a test's words where its comparison is: the words before it, the
	 * comparison, the words after.
	 * @param words the test's words
	 * @return the three, or null if there is no comparison
	 */
	public static String[] splitCompare(final List<String> words)
	{
		for(int i = 0; i < words.size(); i++)
		{
			for(final String c : COMPARES)
			{
				if(words.get(i).equals(c))
				{
					return new String[] { String.join(" ", words.subList(0, i)), c, String.join(" ", words.subList(i + 1, words.size())) };
				}
			}
		}
		return null;
	}

	/**
	 * SC3: a loader's catalog form, CATALOG [FORCE] name [count].
	 */
	public static final class CatalogLoad
	{
		/** the catalog entry's name */
		public String name = "";
		/** how many copies */
		public int count = 1;
		/** true to make copies past the entry's cap */
		public boolean force = false;
	}

	/**
	 * SC3: reads a loader's catalog form: CATALOG, then FORCE if the cap is
	 * to be passed, the entry's name, and how many copies (1 to 100) when the
	 * name with that number is no entry.
	 * @param text the loader's words
	 * @param mob true for a mob entry, false for an item
	 * @return the form, or null if the words are not one
	 */
	public static CatalogLoad catalogLoad(final String text, final boolean mob)
	{
		String rest = (text == null) ? "" : text.trim();
		if(!rest.toUpperCase().startsWith("CATALOG "))
			return null;
		rest = rest.substring(8).trim();
		final CatalogLoad L = new CatalogLoad();
		if(rest.toUpperCase().startsWith("FORCE "))
		{
			L.force = true;
			rest = rest.substring(6).trim();
		}
		final int x = rest.lastIndexOf(' ');
		if((x > 0) && CMath.isInteger(rest.substring(x + 1)) && (catalogEntry(rest, mob) == null))
		{
			L.count = Math.max(1, Math.min(100, CMath.s_int(rest.substring(x + 1))));
			rest = rest.substring(0, x).trim();
		}
		L.name = rest;
		return L;
	}

	/**
	 * SC3: the catalog's mob or item so called.
	 * @param name its name
	 * @param mob true for a mob, false for an item
	 * @return the catalog's own copy, or null
	 */
	public static Physical catalogEntry(final String name, final boolean mob)
	{
		if((name == null) || (name.trim().length() == 0))
			return null;
		return mob ? CMLib.catalog().getCatalogMob(name.trim()) : CMLib.catalog().getCatalogItem(name.trim());
	}

	/**
	 * SC3: whether a catalog entry has as many copies in the world as its cap
	 * allows (a cap of 0 is none).
	 * @param entry the catalog's own copy
	 * @return true if no more may be made
	 */
	public static boolean catalogFull(final Physical entry)
	{
		final CatalogLibrary.CataData data = (entry == null) ? null : CMLib.catalog().getCatalogData(entry);
		return (data != null) && (data.getCap() > 0) && (data.numReferences() >= data.getCap());
	}

	/**
	 * SC3: the copies of a catalog entry in the world.
	 * @param entry the catalog's own copy
	 * @return how many
	 */
	public static int catalogCount(final Physical entry)
	{
		final CatalogLibrary.CataData data = (entry == null) ? null : CMLib.catalog().getCatalogData(entry);
		return (data == null) ? 0 : data.numReferences();
	}

	/**
	 * SC3: a copy of a catalog entry, counted by the catalog.
	 * @param entry the catalog's own copy
	 * @param force true to make it past the entry's cap
	 * @return the copy, or null at the cap
	 */
	public static Physical catalogCopy(final Physical entry, final boolean force)
	{
		if((entry == null) || ((!force) && catalogFull(entry)))
			return null;
		final Physical P = (Physical)entry.copyOf();
		CMLib.catalog().changeCatalogUsage(P, true);
		return P;
	}

	/**
	 * SC3: the copies a catalog form asks for, as many as the cap allows.
	 * @param L the form
	 * @param mob true for a mob entry, false for an item
	 * @return the copies, perhaps none
	 */
	public static List<Physical> catalogCopies(final CatalogLoad L, final boolean mob)
	{
		final List<Physical> copies = new ArrayList<Physical>();
		final Physical entry = (L == null) ? null : catalogEntry(L.name, mob);
		for(int i = 0; (entry != null) && (i < L.count); i++)
		{
			final Physical P = catalogCopy(entry, L.force);
			if(P == null)
				break;
			copies.add(P);
		}
		return copies;
	}
}
