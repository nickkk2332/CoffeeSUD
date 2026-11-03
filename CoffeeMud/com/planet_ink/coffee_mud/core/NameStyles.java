package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;

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
 * G12: name styles as data.  One file per style, world/namegen/STYLE.ini, else
 * resources/namegen/STYLE.ini (or the world's own copy there).  Lines are
 * KEY=value; # starts a comment, and a key given twice adds to the first.
 * <ul>
 * <li>VOWELS=, CONSONANTS=: syllables, each followed by a digit of flags as
 * makeRandomNameSets reads them: 4 in the middle, 2 at the start, 1 at the
 * end (7 anywhere, 6 not last, 3 not in the middle).  Spaces and commas
 * between them are left out.  A style without them uses default's.</li>
 * <li>START=VOWEL|CONSONANT|EITHER: the first syllable (VOWEL).</li>
 * <li>SYLLABLES=2-4: the count when none is asked for.</li>
 * <li>PREFIXES=, SUFFIXES= (comma lists) with PREFIXPCT=, SUFFIXPCT= (25):
 * put before or after a made name.</li>
 * <li>MALE=, FEMALE=, NEUTER=: given names, used GIVENPCT= (50) percent of
 * the time instead of a made one.</li>
 * <li>FAMILY=, or FAMILYSTART= and FAMILYEND= joined: family names, and
 * TITLES= with TITLEPCT= (20): for the full form only.</li>
 * <li>RACES=, LANGUAGES=: races (ID, name or category) and languages that
 * use this style when they name none of their own (NAMESTYLE).</li>
 * </ul>
 * default.ini is today's table; a world's own default.ini changes every
 * random name that names no style.  Used by SYSTEM_RANDOM_NAME:2-4:style in
 * templates, RANDNAME() in scripts, NEW * [style|race] and Spell_TrueName.
 */
public final class NameStyles
{
	private NameStyles()
	{
	}

	/** the style used when none is named */
	public static final String	DEFAULT	= "default";

	private static final String	STOCK_VOWELS	= "a7e7i7o7u7a7e7i7o7u7a7e7i7o7u7a7e7i7o7u7a7e7i7o7u7a7e7i7o7u7a7e7i7o7u7a7e7i7o7u7"
		+ "a7e7i7o7u7a7e7i7o7u7a7e7i7o7u7a7e7i7o7u7ae7ai7ao7au7aa7ea7eo7eu7ee7eau7ia7io7iu7ii7oa7oe7oi7ou7oo7'4y7ay7ay7ei7ei7ei7ua7ua7";
	private static final String	STOCK_CONSONANTS= "b7c7d7f7g7h7j7k7l7m7n7p7qu6r7s7t7v7w7x7y7z7sc7ch7gh7ph7sh7th7wh6ck5nk5rk5sk7wk0cl6fl6gl6kl6ll6pl6sl6"
		+ "br6cr6dr6fr6gr6kr6pr6sr6tr6ss5st7str6b7c7d7f7g7h7j7k7l7m7n7p7r7s7t7v7w7b7c7d7f7g7h7j7k7l7m7n7p7r7s7t7v7w7br6dr6fr6gr6kr6";

	/** a name style, as read */
	public static final class Style
	{
		/** its name, lower case */
		public final String				name;
		/** the file it came from and when that was changed, to read it again */
		String							stamp		= "";
		Pair<String, Integer>[]			vowels, consonants;
		/** 0 a vowel first, 1 a consonant, 2 either */
		int								start		= 0;
		/** the syllables when none are asked for */
		int								lo			= 2, hi = 4;
		String[]						prefixes	= new String[0], suffixes = new String[0];
		int								prefixPct	= 25, suffixPct = 25, givenPct = 50, titlePct = 20;
		final Map<Character, String[]>	given		= new HashMap<Character, String[]>();
		String[]						family		= new String[0], familyStart = new String[0], familyEnd = new String[0];
		String[]						titles		= new String[0];
		final Set<String>				races		= new HashSet<String>(), languages = new HashSet<String>();

		Style(final String name)
		{
			this.name = name;
		}

		/** @return the syllable range when none is asked for */
		public int[] syllables()
		{
			return new int[] { lo, hi };
		}

		/** @return true if this style has family names, for the full form */
		public boolean hasFamily()
		{
			return (family.length > 0) || ((familyStart.length > 0) && (familyEnd.length > 0));
		}
	}

	private static final Map<String, Style>		cache		= new Hashtable<String, Style>();
	private static volatile Map<String, String>	raceIndex	= null;
	private static volatile String				indexStamp	= "";

	/** drops the styles read, so the files are read again (a changed file is read again anyway) */
	public static void reload()
	{
		cache.clear();
		raceIndex = null;
	}

	/**
	 * The syllable table, as makeRandomNameSets reads it: each syllable then a
	 * digit of flags (4 middle, 2 start, 1 end).
	 * @param raw the table
	 * @return the syllables and their flags
	 */
	@SuppressWarnings("unchecked")
	public static Pair<String, Integer>[] syllableSet(final String raw)
	{
		final String rawData = raw.replaceAll("[\\s,]+", "");
		final List<Pair<String, Integer>> set = new ArrayList<Pair<String, Integer>>();
		for(int i = 0; i < rawData.length(); i++)
		{
			final int start = i;
			while((i + 1 < rawData.length()) && (!Character.isDigit(rawData.charAt(i))))
				i++;
			if(!Character.isDigit(rawData.charAt(i)))
				break;
			set.add(new Pair<String, Integer>(rawData.substring(start, i), Integer.valueOf(rawData.substring(i, i + 1))));
		}
		return set.toArray(new Pair[0]);
	}

	private static CMFile fileFor(final String name)
	{
		for(final String path : new String[] { "world/namegen/" + name + ".ini", "resources/namegen/" + name + ".ini" })
		{
			final CMFile F = new CMFile(path, null);
			if(F.exists() && F.canRead() && !F.isDirectory())
				return F;
		}
		return null;
	}

	private static String stampOf(final CMFile F)
	{
		return (F == null) ? "" : (F.getAbsolutePath() + "@" + F.lastModified());
	}

	private static String[] list(final String s)
	{
		final List<String> L = new ArrayList<String>();
		for(final String x : s.split(","))
		{
			if(x.trim().length() > 0)
				L.add(x.trim());
		}
		return L.toArray(new String[0]);
	}

	private static Style parse(final String name, final String text)
	{
		final Style S = new Style(name);
		final Map<String, StringBuilder> keys = new HashMap<String, StringBuilder>();
		for(final String line : text.split("\\r?\\n"))
		{
			final String ln = line.trim();
			if((ln.length() == 0) || ln.startsWith("#") || ln.startsWith(";"))
				continue;
			final int x = ln.indexOf('=');
			if(x <= 0)
				continue;
			final String key = ln.substring(0, x).trim().toUpperCase();
			final String val = ln.substring(x + 1).trim();
			if(keys.containsKey(key))
				keys.get(key).append(",").append(val);
			else
				keys.put(key, new StringBuilder(val));
		}
		final String v = keys.containsKey("VOWELS") ? keys.get("VOWELS").toString() : null;
		final String c = keys.containsKey("CONSONANTS") ? keys.get("CONSONANTS").toString() : null;
		S.vowels = syllableSet((v != null) && (v.trim().length() > 0) ? v : STOCK_VOWELS);
		S.consonants = syllableSet((c != null) && (c.trim().length() > 0) ? c : STOCK_CONSONANTS);
		if(S.vowels.length == 0)
			S.vowels = syllableSet(STOCK_VOWELS);
		if(S.consonants.length == 0)
			S.consonants = syllableSet(STOCK_CONSONANTS);
		for(final Map.Entry<String, StringBuilder> e : keys.entrySet())
		{
			final String val = e.getValue().toString();
			switch(e.getKey())
			{
			case "START":
				S.start = val.toUpperCase().startsWith("C") ? 1 : val.toUpperCase().startsWith("E") ? 2 : 0;
				break;
			case "SYLLABLES":
			{
				final String[] r = val.split("\\s*-\\s*");
				S.lo = Math.max(1, CMath.s_int(r[0].trim()));
				S.hi = (r.length > 1) ? Math.max(S.lo, CMath.s_int(r[1].trim())) : S.lo;
				break;
			}
			case "PREFIXES":
				S.prefixes = list(val);
				break;
			case "SUFFIXES":
				S.suffixes = list(val);
				break;
			case "PREFIXPCT":
				S.prefixPct = CMath.s_int(val);
				break;
			case "SUFFIXPCT":
				S.suffixPct = CMath.s_int(val);
				break;
			case "GIVENPCT":
				S.givenPct = CMath.s_int(val);
				break;
			case "TITLEPCT":
				S.titlePct = CMath.s_int(val);
				break;
			case "MALE":
				S.given.put(Character.valueOf('M'), list(val));
				break;
			case "FEMALE":
				S.given.put(Character.valueOf('F'), list(val));
				break;
			case "NEUTER":
				S.given.put(Character.valueOf('N'), list(val));
				break;
			case "FAMILY":
				S.family = list(val);
				break;
			case "FAMILYSTART":
				S.familyStart = list(val);
				break;
			case "FAMILYEND":
				S.familyEnd = list(val);
				break;
			case "TITLES":
				S.titles = list(val);
				break;
			case "RACES":
				for(final String r : list(val))
					S.races.add(r.toUpperCase());
				break;
			case "LANGUAGES":
				for(final String r : list(val))
					S.languages.add(r.toUpperCase());
				break;
			}
		}
		return S;
	}

	/**
	 * @param name a style's name
	 * @return the style, read once, or null if there is no such file; default
	 *         is always there
	 */
	public static Style get(String name)
	{
		if((name == null) || (name.trim().length() == 0))
			name = DEFAULT;
		name = name.trim().toLowerCase();
		if(!name.matches("[a-z0-9_\\-]+"))
			return null;
		final CMFile F = fileFor(name);
		final String stamp = stampOf(F);
		Style S = cache.get(name);
		if((S != null) && S.stamp.equals(stamp))
			return S;
		if(F == null)
		{
			cache.remove(name);
			if(!name.equals(DEFAULT))
				return null;
			S = parse(DEFAULT, "");
		}
		else
			S = parse(name, F.text().toString());
		S.stamp = stamp;
		cache.put(name, S);
		return S;
	}

	/** @return the styles there are, by name, default first */
	public static List<String> names()
	{
		final Set<String> found = new TreeSet<String>();
		for(final String path : new String[] { "world/namegen", "resources/namegen" })
		{
			final CMFile D = new CMFile(path, null);
			if(!D.isDirectory())
				continue;
			final CMFile[] files = D.listFiles();
			if(files == null)
				continue;
			for(final CMFile F : files)
			{
				final String n = F.getName().toLowerCase();
				if(n.endsWith(".ini") && (n.length() > 4))
					found.add(n.substring(0, n.length() - 4));
			}
		}
		found.remove(DEFAULT);
		final List<String> L = new ArrayList<String>();
		L.add(DEFAULT);
		L.addAll(found);
		return L;
	}

	private static Map<String, String> index()
	{
		final List<String> all = names();
		final List<Style> styles = new ArrayList<Style>();
		final StringBuilder stamp = new StringBuilder();
		for(final String n : all)
		{
			final Style S = get(n);
			if(S != null)
			{
				styles.add(S);
				stamp.append(S.name).append('=').append(S.stamp).append(';');
			}
		}
		Map<String, String> M = raceIndex;
		if((M != null) && indexStamp.equals(stamp.toString()))
			return M;
		M = new HashMap<String, String>();
		for(final Style S : styles)
		{
			final String n = S.name;
			for(final String r : S.races)
			{
				if(!M.containsKey("R:" + r))
					M.put("R:" + r, n);
			}
			for(final String l : S.languages)
			{
				if(!M.containsKey("L:" + l))
					M.put("L:" + l, n);
			}
		}
		raceIndex = M;
		indexStamp = stamp.toString();
		return M;
	}

	/**
	 * @param R a race
	 * @return its style: a GenRace's NAMESTYLE, else the style whose RACES
	 *         names its ID, name or category; null if none
	 */
	public static String styleFor(final Race R)
	{
		if(R == null)
			return null;
		if(R.isGeneric())
		{
			final String s = R.getStat("NAMESTYLE");
			if((s != null) && (s.trim().length() > 0) && (get(s) != null))
				return s.trim().toLowerCase();
		}
		final Map<String, String> M = index();
		for(final String k : new String[] { R.ID(), R.name(), R.racialCategory() })
		{
			if((k != null) && M.containsKey("R:" + k.toUpperCase()))
				return M.get("R:" + k.toUpperCase());
		}
		return null;
	}

	/**
	 * @param L a language
	 * @return its style: a GenLanguage's NAMESTYLE, else the style whose
	 *         LANGUAGES names its ID or name; null if none
	 */
	public static String styleFor(final Language L)
	{
		if(L == null)
			return null;
		final String s = L.getStat("NAMESTYLE");
		if((s != null) && (s.trim().length() > 0) && (get(s) != null))
			return s.trim().toLowerCase();
		final Map<String, String> M = index();
		for(final String k : new String[] { L.ID(), L.name() })
		{
			if((k != null) && M.containsKey("L:" + k.toUpperCase()))
				return M.get("L:" + k.toUpperCase());
		}
		return null;
	}

	/**
	 * @param M a mob
	 * @return its race's style, else null
	 */
	public static String styleFor(final MOB M)
	{
		if(M == null)
			return null;
		return styleFor(M.charStats().getMyRace());
	}

	/**
	 * @param word a style, a race or a language, by name
	 * @return the style it names, or null if none
	 */
	public static String resolve(final String word)
	{
		if((word == null) || (word.trim().length() == 0))
			return null;
		final String w = word.trim();
		if(get(w) != null)
			return w.toLowerCase();
		Race R = CMClass.getRace(w);
		if(R == null)
			R = CMClass.findRace(w);
		if(R != null)
		{
			final String s = styleFor(R);
			return (s != null) ? s : DEFAULT;
		}
		final Ability A = CMClass.findAbility(w);
		if(A instanceof Language)
		{
			final String s = styleFor((Language) A);
			return (s != null) ? s : DEFAULT;
		}
		return null;
	}

	private static String pick(final String[] from)
	{
		return from[CMLib.dice().roll(1, from.length, -1)];
	}

	/**
	 * One made name, from syllables, as generateRandomName did.
	 * @param S the style
	 * @param minSyllable the fewest syllables
	 * @param maxSyllable the most
	 * @return the name
	 */
	public static String syllables(final Style S, final int minSyllable, final int maxSyllable)
	{
		final StringBuilder name = new StringBuilder("");
		final DiceLibrary dice = CMLib.dice();
		final int numSyllables = (maxSyllable <= minSyllable) ? minSyllable : dice.roll(1, maxSyllable - minSyllable + 1, minSyllable - 1);
		final int first = dice.rollPercentage();
		boolean isVowel = (S.start == 0) ? (first > 0) : (S.start == 1) ? false : (first > 50);
		final Pair<String, Integer>[] vowels = S.vowels;
		final Pair<String, Integer>[] cons = S.consonants;
		for(int i = 1; i <= numSyllables; i++)
		{
			Pair<String, Integer> data = null;
			for(int x = 0; x < 100; x++)
			{
				if(isVowel)
					data = vowels[dice.roll(1, vowels.length, -1)];
				else
					data = cons[dice.roll(1, cons.length, -1)];
				if(i == 1)
				{
					if(CMath.bset(data.second.intValue(), 2))
						break;
				}
				else
				if(i == numSyllables)
				{
					if(CMath.bset(data.second.intValue(), 1))
						break;
				}
				else
				{
					if(CMath.bset(data.second.intValue(), 4))
						break;
				}
			}
			if(data != null)
				name.append(data.first);
			isVowel = !isVowel;
		}
		if((S.prefixes.length > 0) && (dice.rollPercentage() <= S.prefixPct))
			name.insert(0, pick(S.prefixes).toLowerCase());
		if((S.suffixes.length > 0) && (dice.rollPercentage() <= S.suffixPct))
			name.append(pick(S.suffixes).toLowerCase());
		return CMStrings.capitalizeFirstLetter(name.toString());
	}

	private static String[] givenFor(final Style S, final char gender)
	{
		final char g = Character.toUpperCase(gender);
		if(S.given.containsKey(Character.valueOf(g)))
			return S.given.get(Character.valueOf(g));
		if((g == 'M') || (g == 'F') || (g == 'N'))
			return new String[0];
		final List<String> all = new ArrayList<String>();
		for(final String[] l : S.given.values())
			all.addAll(Arrays.asList(l));
		return all.toArray(new String[0]);
	}

	/**
	 * A name in a style.
	 * @param style the style's name, a race or a language (null for default)
	 * @param min the fewest syllables, 0 for the style's own range
	 * @param max the most
	 * @param gender M, F, N or anything else for any
	 * @param full true to add a family name and maybe a title
	 * @return the name
	 */
	public static String make(final String style, final int min, final int max, final char gender, final boolean full)
	{
		String s = resolve(style);
		if(s == null)
			s = DEFAULT;
		return make(get(s), min, max, gender, full);
	}

	/**
	 * A name in a style.
	 * @param S the style (null for default)
	 * @param min the fewest syllables, 0 for the style's own range
	 * @param max the most
	 * @param gender M, F, N or anything else for any
	 * @param full true to add a family name and maybe a title
	 * @return the name
	 */
	public static String make(Style S, int min, int max, final char gender, final boolean full)
	{
		if(S == null)
			S = get(DEFAULT);
		if(min <= 0)
		{
			min = S.lo;
			max = S.hi;
		}
		final String[] given = givenFor(S, gender);
		final StringBuilder str = new StringBuilder();
		if((given.length > 0) && (CMLib.dice().rollPercentage() <= S.givenPct))
			str.append(pick(given));
		else
			str.append(syllables(S, min, max));
		if(full)
		{
			if(S.family.length > 0)
				str.append(" ").append(pick(S.family));
			else
			if((S.familyStart.length > 0) && (S.familyEnd.length > 0))
				str.append(" ").append(CMStrings.capitalizeFirstLetter((pick(S.familyStart) + pick(S.familyEnd)).toLowerCase()));
			if((S.titles.length > 0) && (CMLib.dice().rollPercentage() <= S.titlePct))
				str.append(" ").append(pick(S.titles));
		}
		return str.toString();
	}

	/**
	 * The name for NEW * or a login *: one word, in the style given after
	 * the star, else default.
	 * @param after what came after the star: a style, race or language
	 * @param min the fewest syllables
	 * @param max the most
	 * @return the name
	 */
	public static String loginName(final String after, final int min, final int max)
	{
		final String s = resolve(after);
		final Style S = get((s == null) ? DEFAULT : s);
		final String n = make(S, (s == null) ? min : 0, max, '?', false);
		final int x = n.indexOf(' ');
		return (x > 0) ? n.substring(0, x) : n;
	}
}
