package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * DT31, for builders: LANGUAGE PREVIEW id [proficiency] text shows what a
 * language does to a text: what one who does not know it hears, what a
 * listener and the speaker at that proficiency make of it, a reader's view,
 * and where each word's translation came from.  LANGUAGE COVERAGE id
 * file|room|area|HERE counts the dictionary's hits over a text, with the
 * words most often missing.  LANGUAGE with other words is LANGUAGES.
 */
public class LangToolsCmd extends StdCommand
{
	public LangToolsCmd()
	{
	}

	private final String[] access = I(new String[] { "LANGUAGE" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * A language by ID or name.
	 * @param id the ID or name
	 * @return the language, or null
	 */
	public static Language findLanguage(final String id)
	{
		final Ability A = CMClass.getAbility(id);
		if(A instanceof Language)
			return (Language)A;
		final Ability B = CMClass.findAbility(id, Ability.ACODE_LANGUAGE, -1, false);
		return (B instanceof Language) ? (Language)B : null;
	}

	private static int letters(final String s)
	{
		int n = 0;
		for(int i = 0; i < s.length(); i++)
		{
			if(Character.isLetter(s.charAt(i)))
				n++;
		}
		return n;
	}

	/**
	 * Where a word's translation comes from.
	 * @param L the language
	 * @param word the word
	 * @return the words
	 */
	public static String wordSource(final Language L, final String word)
	{
        if(L.isGeneric()){final String source=LanguageLexicon.source(L,word);if(source!=null)return source;}
		if(L.translationHash(L.ID()).containsKey(word.toUpperCase()))
			return "dictionary";
		if(CMLib.players().getPlayerAllHosts(word) != null)
			return "kept: a character's name";
		final List<String[]> lists = L.translationLists(L.ID());
		if((lists == null) || lists.isEmpty())
			return "kept: no word lists";
		return "word list of " + Math.min(word.length(), lists.size()) + " letters";
	}

	/**
	 * LANGUAGE PREVIEW: what the language does to a text.
	 * @param L the language
	 * @param prof the proficiency of the listener, speaker and reader
	 * @param text the text
	 * @return the lines
	 */
	public static String preview(final Language L, final int prof, final String text)
	{
		final StringBuilder str = new StringBuilder("'" + text + "' in " + L.name() + " (" + L.ID() + "), at " + prof + "%:");
		final int mess = (int)Math.round(CMath.mul(letters(text), CMath.div(100 - prof, 100)));
		if(L instanceof com.planet_ink.coffee_mud.core.Languages.Scrambler)
		{
			final com.planet_ink.coffee_mud.core.Languages.Scrambler S = (com.planet_ink.coffee_mud.core.Languages.Scrambler)L;
			str.append("\n\r  One who does not know it hears: ").append(S.scrambleAll(L.ID(), text, 0));
			str.append("\n\r  A listener at ").append(prof).append("% understands: ").append(com.planet_ink.coffee_mud.core.Languages.render(L.ID(), text, prof));
			str.append("\n\r  The speaker at ").append(prof).append("% is heard by those who do not know it as: ").append(S.scrambleAll(L.ID(), text, mess));
			str.append("\n\r  Written, it says '").append(S.scrambleAll(L.ID(), text, mess).trim()).append("'; a reader at ").append(prof)
				.append("% reads: ").append(com.planet_ink.coffee_mud.core.Languages.render(L.ID(), text, prof));
		}
		else
			str.append("\n\r  (").append(L.ID()).append(" is no standard language: only its words are shown.)");
		str.append("\n\r  Words:");
		final Set<String> done = new HashSet<String>();
		for(final String w : text.split("[^A-Za-z']+"))
		{
			if((w.length() == 0) || (!done.add(w.toLowerCase())))
				continue;
			str.append("\n\r    ").append(w).append(" -> ").append(L.translate(L.ID(), w)).append(" (").append(wordSource(L, w)).append(")");
		}
		return str.toString();
	}

	/**
	 * The text LANGUAGE COVERAGE reads: a file, a room (or HERE), or an area,
	 * its rooms' titles and descriptions and the names and descriptions of
	 * the mobs and items in them.
	 * @param mob the one asking
	 * @param what the file, room ID, HERE or area name
	 * @return the text, or null if there is no such thing
	 */
	public static String textOf(final MOB mob, final String what)
	{
		final List<Room> rooms = new ArrayList<Room>();
		if(what.equalsIgnoreCase("HERE") && (mob.location() != null))
			rooms.add(mob.location());
		else
		if(CMLib.map().getRoom(what) != null)
			rooms.add(CMLib.map().getRoom(what));
		else
		{
			final Area A = CMLib.map().getArea(what);
			if(A != null)
			{
				for(final Enumeration<Room> r = A.getProperMap(); r.hasMoreElements();)
				{
					final Room R = r.nextElement();
					if(R != null)
						rooms.add(R);
				}
			}
			else
			{
				final CMFile F = new CMFile(what, mob, CMFile.FLAG_LOGERRORS);
				if(F.exists() && (!F.isDirectory()))
					return CMStrings.removeColors(F.text().toString());
				return null;
			}
		}
		final StringBuilder str = new StringBuilder();
		for(final Room R : rooms)
		{
			str.append(R.displayText()).append(' ').append(R.description()).append(' ');
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M != null) && M.isMonster())
					str.append(M.name()).append(' ').append(M.description()).append(' ');
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if(I != null)
					str.append(I.name()).append(' ').append(I.description()).append(' ');
			}
		}
		return CMStrings.removeColors(str.toString());
	}

	/**
	 * LANGUAGE COVERAGE: the dictionary's hits over a text.
	 * @param L the language
	 * @param text the text
	 * @param top how many missing words to name
	 * @return the lines
	 */
	public static String coverage(final Language L, final String text, final int top)
	{
		final Map<String, String> dict = L.translationHash(L.ID());
		int words = 0;
		int hits = 0;
		final Map<String, int[]> missing = new HashMap<String, int[]>();
		for(final String w : text.split("[^A-Za-z']+"))
		{
			if(w.length() == 0)
				continue;
			words++;
			if(dict.containsKey(w.toUpperCase())||(L.isGeneric()&&LanguageLexicon.dictionary(L,w)!=null))
				hits++;
			else
			{
				final String k = w.toLowerCase();
				if(!missing.containsKey(k))
					missing.put(k, new int[1]);
				missing.get(k)[0]++;
			}
		}
		final StringBuilder str = new StringBuilder(L.ID() + "'s dictionary has " + hits + " of " + words + " words");
		str.append((words == 0) ? "." : (" (" + Math.round(100.0 * hits / words) + "%)."));
		if(!missing.isEmpty())
		{
			final List<Map.Entry<String, int[]>> list = new ArrayList<Map.Entry<String, int[]>>(missing.entrySet());
			Collections.sort(list, new Comparator<Map.Entry<String, int[]>>()
			{
				@Override
				public int compare(final Map.Entry<String, int[]> a, final Map.Entry<String, int[]> b)
				{
					if(a.getValue()[0] != b.getValue()[0])
						return b.getValue()[0] - a.getValue()[0];
					return a.getKey().compareTo(b.getKey());
				}
			});
			str.append("\n\r  The most common missing (").append(missing.size()).append(" in all): ");
			final List<String> names = new ArrayList<String>();
			for(int i = 0; (i < list.size()) && (i < top); i++)
				names.add(list.get(i).getKey() + " (" + list.get(i).getValue()[0] + ")");
			str.append(String.join(", ", names)).append(".");
		}
		return str.toString();
	}

	private static boolean mayUse(final MOB mob)
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDABILITIES);
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String what = (commands.size() > 1) ? commands.get(1).toUpperCase() : "";
		if((what.equals("PREVIEW") || what.equals("COVERAGE")) && mayUse(mob))
		{
			if(commands.size() < 4)
			{
				mob.tell(L("LANGUAGE PREVIEW [ID] ([PROFICIENCY]) [TEXT], LANGUAGE COVERAGE [ID] [FILE/ROOM/AREA/HERE]"));
				return false;
			}
			final Language L = findLanguage(commands.get(2));
			if(L == null)
			{
				mob.tell(L("There is no language '@x1'.", commands.get(2)));
				return false;
			}
			if(what.equals("PREVIEW"))
			{
				int from = 3;
				int prof = 50;
				if(CMath.isInteger(commands.get(3)) && (commands.size() > 4))
				{
					prof = Math.max(0, Math.min(100, CMath.s_int(commands.get(3))));
					from = 4;
				}
				mob.tell(preview(L, prof, CMParms.combine(commands, from)));
			}
			else
			{
				final String text = textOf(mob, CMParms.combine(commands, 3));
				if(text == null)
					mob.tell(L("There is no file, room or area '@x1'.", CMParms.combine(commands, 3)));
				else
					mob.tell(coverage(L, text, 20));
			}
			return false;
		}
		// anything else is LANGUAGES, as LANGUAGE was before
		final Command C = CMClass.getCommand("Languages");
		if(C != null)
			return C.execute(mob, commands, metaFlags);
		return false;
	}

	@Override
	public boolean canBeOrdered()
	{
		return false;
	}

	@Override
	public boolean securityCheck(final MOB mob)
	{
		return true;
	}
}
