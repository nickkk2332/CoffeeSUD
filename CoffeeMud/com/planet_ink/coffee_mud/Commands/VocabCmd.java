package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
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
 * DT30, for builders: VOCAB tests the translation and parser rules.  TEST
 * text shows a text after the session, final and filter rules, and which
 * rules matched; INPUT line what the pre-processor makes of a typed command;
 * LIST key the lists.ini value in effect and the file it came from; RELOAD
 * forgets the rules, lists and help, so the files are read again; ADD
 * section rule appends a rule to the world's own rule file.
 */
public class VocabCmd extends StdCommand
{
	public VocabCmd()
	{
	}

	private final String[] access = I(new String[] { "VOCAB" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** the parser file's sections; the others are the translation file's */
	public static final String[] PARSER_SECTIONS = { "COMMAND-PRE-PROCESSOR", "ITEM-PRE-PROCESSOR", "ITEM-FAIL-PROCESSOR", "RAW-INPUT-PROCESSOR" };

	/**
	 * VOCAB RELOAD: the caches of the rules, the lists and the help, so that
	 * the files are read again when next needed.
	 * @param mob the one asking
	 * @return how many were forgotten
	 */
	public static int reload(final MOB mob)
	{
		final List<String> keys = new ArrayList<String>();
		for(final String prefix : new String[] { "PARSED_LISTFILE", "TRANSLATION_", "PARSER_", "PARSED_SOCIALS" })
		{
			for(final Iterator<String> i = Resources.findResourceKeys(prefix); i.hasNext();)
			{
				final String k = i.next();
				if(k.toUpperCase().startsWith(prefix))
					keys.add(k);
			}
		}
		for(final String k : keys)
			Resources.removeResource(k);
		CMLib.lang().clear();
		CMLib.help().unloadHelpFile(mob);
		return keys.size();
	}

	/**
	 * VOCAB LIST: a lists.ini key's value in effect, and the file it came from,
	 * the files of LISTFILE= read in order, a later one's value winning, the
	 * world's resources/ before the game's.
	 * @param key the key
	 * @return the lines
	 */
	public static String listValue(final String key)
	{
		final String names = CMProps.instance().getStr("LISTFILE", "resources/lists.ini");
		String value = null;
		String from = null;
		final List<String> seen = new ArrayList<String>();
		for(final String name : CMParms.parseCommas(names, true))
		{
			final File world = new File(WorldFiles.worldDir(), name.startsWith("/") ? name.substring(1) : name);
			final File stock = new File(name.startsWith("/") ? name.substring(1) : name);
			for(final File F : new File[] { stock, world })
			{
				if(!F.isFile())
					continue;
				try
				{
					final Properties P = new Properties();
					P.load(new StringReader(new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8)));
					for(final Object o : P.keySet())
					{
						if(o.toString().equalsIgnoreCase(key))
						{
							value = P.getProperty(o.toString());
							from = F.getPath();
							seen.add(F.getPath());
						}
					}
				}
				catch(final IOException e)
				{
					seen.add(F.getPath() + " (unreadable: " + e.getMessage() + ")");
				}
			}
		}
		if(value == null)
			return "No list file of " + names + " has " + key.toUpperCase() + ".";
		final StringBuilder str = new StringBuilder(key.toUpperCase() + "=" + value + "\n\r  from " + from);
		if(seen.size() > 1)
			str.append(" (also in ").append(String.join(", ", seen.subList(0, seen.size() - 1))).append(", which it overrides)");
		return str.toString();
	}

	/**
	 * VOCAB ADD: a rule appended to the world's own rule file, under its
	 * section, then the rules forgotten so it takes effect.
	 * @param section the section, such as FINAL-TRANSLATION
	 * @param rule the rule, such as replaceall "colour" with "color"
	 * @return what was done
	 */
	public static String add(final String section, final String rule)
	{
		final String sec = section.toUpperCase().trim();
		final boolean parser = CMParms.contains(PARSER_SECTIONS, sec);
		String lang = CMProps.instance().getStr("LANGUAGE", "").trim().toUpperCase();
		String country = CMProps.instance().getStr("COUNTRY", "").trim().toUpperCase();
		if(lang.length() == 0)
			lang = "EN";
		if(country.length() == 0)
			country = "TX";
		String fileName = (parser ? "parser_" : "translation_") + lang + "_" + country + ".properties";
		// the name the world's file, or else the game's, has: the files are found without regard to case
		final File worldRes = new File(WorldFiles.worldDir(), "resources");
		for(final File dir : new File[] { new File("resources"), worldRes })
		{
			final File[] in = dir.listFiles();
			if(in == null)
				continue;
			for(final File G : in)
			{
				if(G.getName().equalsIgnoreCase(fileName))
					fileName = G.getName();
			}
		}
		final File F = new File(worldRes, fileName);
		try
		{
			final List<String> lines = F.isFile() ? new ArrayList<String>(Files.readAllLines(F.toPath(), StandardCharsets.UTF_8)) : new ArrayList<String>();
			int at = -1;
			for(int i = 0; i < lines.size(); i++)
			{
				if(lines.get(i).trim().equalsIgnoreCase("[" + sec + "]"))
				{
					at = i + 1;
					while((at < lines.size()) && (!lines.get(at).trim().startsWith("[")))
						at++;
					break;
				}
			}
			if(at < 0)
			{
				lines.add("[" + sec + "]");
				lines.add(rule.trim());
			}
			else
				lines.add(at, rule.trim());
			if((F.getParentFile() != null) && (!F.getParentFile().isDirectory()))
				F.getParentFile().mkdirs();
			Files.write(F.toPath(), (String.join("\n", lines) + "\n").getBytes(StandardCharsets.UTF_8));
		}
		catch(final IOException e)
		{
			return "Cannot write " + F.getPath() + ": " + e.getMessage();
		}
		CMLib.lang().clear();
		return "Added to [" + sec + "] in " + F.getPath() + ": " + rule.trim();
	}

	/**
	 * A rule as the rule files write it, from the words typed (which lose their
	 * quotes): COMMAND "text" with "text", or COMMAND "text".
	 * @param words the words after the section
	 * @return the rule
	 */
	public static String ruleText(final List<String> words)
	{
		final List<String> w = new ArrayList<String>();
		for(final String s : words)
		{
			String u = s;
			if((u.length() > 1) && u.startsWith("\"") && u.endsWith("\""))
				u = u.substring(1, u.length() - 1);
			w.add(u);
		}
		if(w.isEmpty())
			return "";
		final String cmd = w.get(0).toLowerCase();
		int with = -1;
		for(int i = 2; i < w.size(); i++)
		{
			if(w.get(i).equalsIgnoreCase("with"))
			{
				with = i;
				break;
			}
		}
		if(with < 0)
			return cmd + " \"" + CMParms.combine(w, 1) + "\"";
		return cmd + " \"" + CMParms.combine(w.subList(1, with), 0) + "\" with \"" + CMParms.combine(w.subList(with + 1, w.size()), 0) + "\"";
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String what = (commands.size() > 1) ? commands.get(1).toUpperCase() : "";
		final String rest = (commands.size() > 2) ? CMParms.combine(commands, 2) : "";
		if(what.equals("TEST") && (rest.length() > 0))
			mob.tell(String.join("\n\r", CMLib.lang().explainTranslation(rest)));
		else
		if(what.equals("INPUT") && (rest.length() > 0))
			mob.tell(String.join("\n\r", CMLib.lang().explainInput(rest)));
		else
		if(what.equals("LIST") && (rest.length() > 0))
			mob.tell(listValue(rest.trim()));
		else
		if(what.equals("RELOAD"))
			mob.tell(L("Forgot @x1 cached rule and list files, and the help: they are read again when next needed.", "" + reload(mob)));
		else
		if(what.equals("ADD") && (commands.size() > 3))
		{
			if(!SaveGame.isBuilderMode())
			{
				mob.tell(L("VOCAB ADD writes the world's rule file, so it is for builder mode."));
				return false;
			}
			mob.tell(add(commands.get(2), ruleText(commands.subList(3, commands.size()))));
		}
		else
			mob.tell(L("VOCAB TEST [TEXT], VOCAB INPUT [COMMAND LINE], VOCAB LIST [KEY], VOCAB RELOAD, VOCAB ADD [SECTION] [RULE]"));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDAREAS);
	}
}
