package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
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
 * N12: an NPC's own wording for the stock lines it says (postSay): the
 * shopkeeper's "I'm not interested.", the banker's, the hireling's.
 * Prop_NPCLines on the NPC, or on its area for the whole town, holds
 * KEY="text" entries: KEY one of the short names below, or the stock line
 * itself in quotes.  Alternatives: "one"|"two", one picked at random.
 * @x1, @x2... in the text are the stock line's values, $n the one spoken
 * to.  A [mask] after an entry makes it that customer's alone; such
 * entries are tried first.  An empty text says nothing.
 */
public final class NpcLines
{
	private NpcLines()
	{
	}

	/** the property's ID */
	public static final String	ID		= "Prop_NPCLines";

	/** the short names, and the stock lines they stand for */
	public static final String[][]	KEYS	= {
		{ "NOTBUYING", "I'm not interested." },
		{ "NOTBUYINGTHOSE", "I'm sorry, I'm not buying those." },
		{ "STOLEN", "I don't buy stolen goods." },
		{ "OUTOFSTOCK", "I don't have that in stock.  Ask for my LIST." },
		{ "TOOPOOR", "You can't afford to buy @x1." },
		{ "SHOPPOOR", "Sorry, I can't afford that right now.  Try back later." },
		{ "TOODEAR", "That's way out of my price range! Try AUCTIONing it." },
		{ "OFFER", "I'll give you @x1 for @x2." },
		{ "FIRM", "I'm afraid my prices are firm." },
		{ "TOOADVANCED", "That's too advanced for you, I'm afraid." },
		{ "TOOLOW", "You aren't experienced enough to buy @x1." },
		{ "CLANSONLY", "I only sell to clans." },
		{ "BALANCE", "Ok, your new balance is @x1." },
		{ "NOLOANS", "I'm sorry, I don't loan money." },
		{ "ACCOUNTCLOSED", "I have closed that account. Thanks for your business." },
		{ "ONLYCURRENCY", "I'm sorry, I only deal in @x1." },
		{ "SAFE", "Thank you, @x1 is safe with us." },
		{ "DELIVER", "I'll deliver that for ya right away!" },
		{ "FORHIRE", "I'm for hire.  Just give me @x1 and I'll work for you for @x2 \"hours\"." },
		{ "HIRED", "Ok.  You've got me for at least @x1 \"hours\".  My skills include: @x2.  I'll follow you.  Just ORDER me to do what you want." },
		{ "PRICE", "My price is @x1.  Give me @x2 more and I'll work for you for @x3 \"hours\"." },
		{ "ONTHEJOB", "I'm on the job for @x1 more." },
		{ "TIMEUP", "Your time is up.  Goodbye!" },
		{ "GOODBYE", "Suit yourself.  Goodbye." },
		{ "WONTWORK", "I wouldn't work for the likes of you." },
		{ "SKILLS", "My skills include: @x1." },
	};

	/** one entry: the stock line it rewords, the texts, a mask or null */
	private static final class Entry
	{
		Pattern			line;
		List<String>	texts	= new ArrayList<String>();
		String			mask	= null;
	}

	private static final Map<String, List<Entry>>	parsed	= new Hashtable<String, List<Entry>>();
	private static final Map<String, Pattern>		lines	= new Hashtable<String, Pattern>();

	/**
	 * @param key a short name
	 * @return its stock line, or null
	 */
	public static String stockLine(final String key)
	{
		for(final String[] k : KEYS)
		{
			if(k[0].equalsIgnoreCase(key))
				return k[1];
		}
		return null;
	}

	/** a stock line, @x1... its values, as a pattern that matches it whole */
	private static Pattern patternOf(final String line)
	{
		Pattern P = lines.get(line);
		if(P != null)
			return P;
		final StringBuilder re = new StringBuilder("");
		final Matcher m = Pattern.compile("@x(\\d)").matcher(line);
		int at = 0;
		while(m.find())
		{
			re.append(Pattern.quote(line.substring(at, m.start())));
			re.append("(?<x").append(m.group(1)).append(">.*?)");
			at = m.end();
		}
		re.append(Pattern.quote(line.substring(at)));
		P = Pattern.compile(re.toString(), Pattern.DOTALL);
		lines.put(line, P);
		return P;
	}

	/**
	 * Reads a Prop_NPCLines text: KEY="text"|"text"[mask] ...
	 * @param text the text
	 * @return its entries, in order
	 */
	private static List<Entry> parse(final String text)
	{
		List<Entry> list = parsed.get(text);
		if(list != null)
			return list;
		list = new ArrayList<Entry>();
		final String s = text;
		int i = 0;
		while(i < s.length())
		{
			while((i < s.length()) && Character.isWhitespace(s.charAt(i)))
				i++;
			if(i >= s.length())
				break;
			// the key: a word, or the stock line in quotes
			String key;
			if(s.charAt(i) == '"')
			{
				final int e = s.indexOf('"', i + 1);
				if(e < 0)
					break;
				key = s.substring(i + 1, e);
				i = e + 1;
			}
			else
			{
				int e = i;
				while((e < s.length()) && (s.charAt(e) != '=') && (!Character.isWhitespace(s.charAt(e))))
					e++;
				key = stockLine(s.substring(i, e));
				i = e;
			}
			while((i < s.length()) && Character.isWhitespace(s.charAt(i)))
				i++;
			if((i >= s.length()) || (s.charAt(i) != '='))
				continue; // not an entry
			i++;
			final Entry E = new Entry();
			// the texts: "one"|"two", or a word
			while(i < s.length())
			{
				if(s.charAt(i) == '"')
				{
					final int e = s.indexOf('"', i + 1);
					if(e < 0)
					{
						E.texts.add(s.substring(i + 1));
						i = s.length();
						break;
					}
					E.texts.add(s.substring(i + 1, e));
					i = e + 1;
				}
				else
				{
					int e = i;
					while((e < s.length()) && (!Character.isWhitespace(s.charAt(e))) && (s.charAt(e) != '|') && (s.charAt(e) != '['))
						e++;
					E.texts.add(s.substring(i, e));
					i = e;
				}
				if((i < s.length()) && (s.charAt(i) == '|'))
				{
					i++;
					continue;
				}
				break;
			}
			if((i < s.length()) && (s.charAt(i) == '['))
			{
				final int e = s.indexOf(']', i);
				E.mask = (e < 0) ? s.substring(i + 1) : s.substring(i + 1, e);
				i = (e < 0) ? s.length() : (e + 1);
			}
			if(key != null)
			{
				E.line = patternOf(key);
				list.add(E);
			}
		}
		parsed.put(text, list);
		return list;
	}

	/**
	 * The words a stock line is said in by this NPC: its own Prop_NPCLines,
	 * then its area's; else the line as it is.
	 * @param mob the NPC
	 * @param target the one spoken to, or null
	 * @param text the stock line, its values in it
	 * @return the words, the line as it was, or null to say nothing
	 */
	public static String reword(final MOB mob, final MOB target, final String text)
	{
		if((mob == null) || (text == null) || (mob.playerStats() != null))
			return text;
		final Ability own = mob.fetchEffect(ID);
		final Area A = CMLib.map().areaLocation(mob);
		final Ability town = (A == null) ? null : A.fetchEffect(ID);
		if((own == null) && (town == null))
			return text;
		for(final Ability P : new Ability[] { own, town })
		{
			if(P == null)
				continue;
			final String r = reword(parse(P.text()), target, text);
			if(r != NONE)
				return r;
		}
		return text;
	}

	/** no entry of a list rewords the line */
	private static final String	NONE	= new String("");

	private static String reword(final List<Entry> entries, final MOB target, final String text)
	{
		// the masked entries first, those the one spoken to fits; then the rest
		for(int pass = 0; pass < 2; pass++)
		{
			for(final Entry E : entries)
			{
				if((pass == 0) != (E.mask != null))
					continue;
				if((pass == 0) && ((target == null) || (!CMLib.masking().maskCheck(E.mask, target, true))))
					continue;
				final Matcher m = E.line.matcher(text);
				if(!m.matches())
					continue;
				if(E.texts.isEmpty())
					return null;
				String said = E.texts.get(CMLib.dice().roll(1, E.texts.size(), -1));
				if(said.trim().length() == 0)
					return null;
				for(int x = 9; x >= 1; x--)
				{
					if(said.indexOf("@x" + x) < 0)
						continue;
					String v = "";
					try
					{
						v = m.group("x" + x);
					}
					catch(final IllegalArgumentException e)
					{
					}
					said = said.replace("@x" + x, (v == null) ? "" : v);
				}
				if(said.indexOf("$n") >= 0)
					said = said.replace("$n", (target == null) ? "" : target.name());
				return said;
			}
		}
		return NONE;
	}
}
