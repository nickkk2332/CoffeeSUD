package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.zip.CRC32;

import com.planet_ink.coffee_mud.core.save.WorldFiles;

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
 * Phase 2's rules files, in a save: a save keeps each one's sum, and a restore
 * names each that changed since, with what the change means for a campaign
 * already under way (each feature keeps what it recorded, and reads the new
 * rules from then on).
 */
public final class RulesFiles
{
	private RulesFiles()
	{
	}

	/** each file, and what a change to it means for a save already made */
	private static final String[][] FILES = {
		{ "cultures.ini", "cultures already taken up keep what they gave; leaving one takes away what was recorded, even if the file no longer has it" },
		{ "talents.ini", "talents already decided stay decided; the others are checked against the file as it is now" },
		{ "formations.ini", "members keep their roles by name; a role the file no longer has does nothing until it is given again" },
		{ "orgpools.ini", "pools keep their units; a kind the file no longer has keeps them, unseen, until it is back" },
		{ "challenges.ini", "personal records are kept; a book the file no longer has is not shown" },
		{ "affiliations.ini", "the rules as they are now apply from now on; a new CONFLICT does not end memberships already held, it refuses the next join" },
	};

	private static String sumOf(final File F)
	{
		if(!F.isFile())
			return "none";
		try
		{
			final CRC32 crc = new CRC32();
			crc.update(Files.readAllBytes(F.toPath()));
			return F.length() + "-" + Long.toHexString(crc.getValue());
		}
		catch(final java.io.IOException e)
		{
			return "?";
		}
	}

	/** the files' sums now, as a save keeps them: "file=sum;..." */
	public static String saved()
	{
		final StringBuilder s = new StringBuilder();
		for(final String[] f : FILES)
			s.append((s.length() > 0) ? ";" : "").append(f[0]).append('=').append(sumOf(new File(WorldFiles.worldDir(), f[0])));
		return s.toString();
	}

	/** the restore report's lines for the files that changed since a save (none for a save that kept no sums) */
	public static List<String> changes(final String savedSums)
	{
		final List<String> out = new ArrayList<String>();
		if((savedSums == null) || savedSums.trim().isEmpty())
			return out;
		final Map<String, String> was = new TreeMap<String, String>();
		for(final String pair : savedSums.split(";"))
		{
			final int x = pair.indexOf('=');
			if(x > 0)
				was.put(pair.substring(0, x), pair.substring(x + 1));
		}
		for(final String[] f : FILES)
		{
			final String before = was.get(f[0]);
			final String now = sumOf(new File(WorldFiles.worldDir(), f[0]));
			if((before == null) || before.equals(now))
				continue;
			final String how = before.equals("none") ? "New" : now.equals("none") ? "Gone" : "Changed";
			out.add(how + " rules file: " + f[0] + " (" + f[1] + ")."); // before boot: no language library yet
		}
		return out;
	}
}
