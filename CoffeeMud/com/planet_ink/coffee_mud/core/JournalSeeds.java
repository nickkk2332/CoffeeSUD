package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
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
 * I3: the first pages of a book or board, from world/journals/NAME.txt
 * (NAME its name, lower case, spaces as _): "=== subject ===" begins each
 * entry, and an "AUTHOR: name" line before the first gives the author.  An
 * empty book or board is filled from it the first time it is read.
 */
public final class JournalSeeds
{
	private JournalSeeds()
	{
	}

	private static final Set<String>	done	= Collections.synchronizedSet(new HashSet<String>());

	/**
	 * @param journal a book's or board's name
	 * @return its seed file
	 */
	public static File fileOf(final String journal)
	{
		final File W = WorldFiles.worldDir();
		final String n = journal.toLowerCase().replaceAll("\\s+", "_").replaceAll("[^a-z0-9_\\-]", "");
		return new File(new File((W == null) ? new File(".") : W, "journals"), n + ".txt");
	}

	/**
	 * Parses a seed file's text.
	 * @param text the text
	 * @return the author, then subject and text pairs
	 */
	public static List<String[]> parse(final String text)
	{
		final List<String[]> list = new ArrayList<String[]>();
		String author = "";
		String subj = null;
		final StringBuilder b = new StringBuilder();
		for(final String raw : text.split("\\r?\\n"))
		{
			final String l = raw.trim();
			if((subj == null) && l.toUpperCase().startsWith("AUTHOR:"))
			{
				author = l.substring(7).trim();
				continue;
			}
			if(l.startsWith("===") && l.endsWith("===") && (l.length() > 6))
			{
				if(subj != null)
					list.add(new String[] { subj, b.toString().trim() });
				subj = l.substring(3, l.length() - 3).trim();
				b.setLength(0);
				continue;
			}
			if(subj != null)
				b.append(raw).append("\n\r");
		}
		if(subj != null)
			list.add(new String[] { subj, b.toString().trim() });
		list.add(0, new String[] { author, "" });
		return list;
	}

	/**
	 * Fills an empty journal from its seed file, once.
	 * @param journal its name
	 */
	public static void seed(final String journal)
	{
		if((journal == null) || (journal.length() == 0) || (!done.add(journal.toUpperCase())))
			return;
		final File F = fileOf(journal);
		if(!F.isFile())
			return;
		try
		{
			if(CMLib.database().DBCountJournal(journal, null, null) > 0)
				return;
			final List<String[]> entries = parse(new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"));
			final String author = (entries.get(0)[0].length() > 0) ? entries.get(0)[0] : "Anonymous";
			for(int i = 1; i < entries.size(); i++)
				CMLib.database().DBWriteJournal(journal, author, "ALL", entries.get(i)[0], entries.get(i)[1]);
		}
		catch(final Exception e)
		{
			Log.errOut("JournalSeeds", e.getMessage());
		}
	}

	/**
	 * Forgets which have been seeded (a new game).
	 */
	public static void reset()
	{
		done.clear();
	}
}
