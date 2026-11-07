package com.planet_ink.coffee_mud.core;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * P15: latent talents.  world/talents.ini declares each, one a line:
 * [id]=TITLE="..." LORE="KEY,KEY" (MASK="...") (CHANCE=n) (ABILITY=id
 * PROFICIENCY=n) (TATTOO=name) (MESSAGE="...").  Whenever a character
 * learns lore or makes a discovery (and when they come into the game), their
 * talents not yet decided are checked again; one whose lore is all known (to
 * them or, for profile lore, their profile) and whose mask they pass is
 * decided at once and for good: with a chance below 100, by one draw from the
 * campaign's talents stream.  It awakens (the ability learned, the tattoo and
 * the message given) or stays dormant, and either is kept as a tattoo,
 * TALENT_[ID] or TALENT_[ID]_DORMANT, so reading again, a refreshed menu or a
 * restore never decides it again.
 */
public final class Talents
{
	private Talents()
	{
	}

	public static final String	STREAM	= "talents";
	public static final String	FAMILY	= "talents";

	/** a talent as the file declares it */
	public static final class Talent
	{
		public final String			id, title, mask, ability, tattoo, message;
		public final List<String>	lore;
		public final int			chance, proficiency;

		Talent(final String id, final String parms)
		{
			this.id = id.trim().toUpperCase(Locale.ROOT);
			title = CMParms.getParmStr(parms, "TITLE", this.id.toLowerCase(Locale.ROOT));
			mask = CMParms.getParmStr(parms, "MASK", "").trim();
			ability = CMParms.getParmStr(parms, "ABILITY", "").trim();
			tattoo = CMParms.getParmStr(parms, "TATTOO", "").trim();
			message = CMParms.getParmStr(parms, "MESSAGE", "").trim();
			final List<String> l = new ArrayList<String>();
			for(final String k : CMParms.parseCommas(CMParms.getParmStr(parms, "LORE", ""), true))
			{
				if(k.trim().length() > 0)
					l.add(k.trim().toUpperCase(Locale.ROOT));
			}
			lore = Collections.unmodifiableList(l);
			chance = Math.max(0, Math.min(100, CMath.s_int(CMParms.getParmStr(parms, "CHANCE", "100"))));
			proficiency = Math.max(0, Math.min(100, CMath.s_int(CMParms.getParmStr(parms, "PROFICIENCY", "0"))));
		}
	}

	/** the talents the world declares; none without the file */
	public static Map<String, Talent> talents()
	{
		final Map<String, Talent> out = new TreeMap<String, Talent>();
		final File f = new File(WorldFiles.worldDir(), "talents.ini");
		if(!f.isFile())
			return out;
		try(final Reader r = Files.newBufferedReader(f.toPath(), StandardCharsets.UTF_8))
		{
			final Properties p = new Properties();
			p.load(r);
			for(final String key : p.stringPropertyNames())
				out.put(key.trim().toUpperCase(Locale.ROOT), new Talent(key, p.getProperty(key)));
		}
		catch(final IOException | IllegalArgumentException e)
		{
			Log.errOut("Talents", "talents.ini: " + e.getMessage());
		}
		return out;
	}

	/** what is wrong with the file: abilities that do not exist, talents with no lore */
	public static List<String> problems()
	{
		final List<String> out = new ArrayList<String>();
		for(final Talent t : talents().values())
		{
			if(t.lore.isEmpty())
				out.add("talents.ini: " + t.id + ": LORE names no lore, so it would awaken at once");
			if((t.ability.length() > 0) && (CMClass.getAbility(t.ability) == null))
				out.add("talents.ini: " + t.id + ": no ability " + t.ability);
		}
		return out;
	}

	/** a decided talent: "awakened", "dormant", or "" while it waits */
	public static String decided(final MOB who, final String id)
	{
		final String k = "TALENT_" + id.toUpperCase(Locale.ROOT);
		if(who.findTattoo(k) != null)
			return "awakened";
		if(who.findTattoo(k + "_DORMANT") != null)
			return "dormant";
		return "";
	}

	/** what a character still lacks for a talent, in words; none when it would be decided now */
	public static List<String> missing(final MOB who, final Talent t)
	{
		final List<String> out = new ArrayList<String>();
		for(final String k : t.lore)
		{
			if(!LoreBook.known(who, k))
			{
				final LoreBook.Entry e = LoreBook.entries().get(k);
				out.add("the lore of " + ((e == null) ? k.toLowerCase(Locale.ROOT) : e.title));
			}
		}
		if((t.mask.length() > 0) && !CMLib.masking().maskCheck(t.mask, who, true))
			out.add(CMLib.masking().maskDesc(t.mask));
		return out;
	}

	/**
	 * Checks a character's undecided talents again, and decides each whose needs
	 * they meet, once.
	 * @return what awakened, for them to be told
	 */
	public static List<String> recheck(final MOB who)
	{
		final List<String> told = new ArrayList<String>();
		if((who == null) || who.isMonster() && (who.amFollowing() == null) && (who.playerStats() == null))
			return told;
		for(final Talent t : talents().values())
		{
			if((decided(who, t.id).length() > 0) || !missing(who, t).isEmpty() || t.lore.isEmpty())
				continue;
			final boolean awakens = (t.chance >= 100) || ((t.chance > 0) && (RandomStreams.roll(STREAM, 100) <= t.chance));
			ModuleStats.count(FAMILY, t.id.toLowerCase(Locale.ROOT), awakens ? "awakened" : "dormant");
			if(!awakens)
			{
				who.addTattoo("TALENT_" + t.id + "_DORMANT");
				continue;
			}
			who.addTattoo("TALENT_" + t.id);
			if(t.tattoo.length() > 0)
				who.addTattoo(t.tattoo);
			if((t.ability.length() > 0) && (who.fetchAbility(t.ability) == null))
			{
				final Ability A = CMClass.getAbility(t.ability);
				if(A != null)
				{
					A.setProficiency(t.proficiency);
					who.addAbility(A);
					A.autoInvocation(who, false);
				}
			}
			told.add(t.message.length() > 0 ? t.message : CMLib.lang().L("A talent awakens in you: @x1.", t.title));
		}
		for(final String s : told)
			who.tell(s);
		return told;
	}

	/** TALENTS: each talent, awakened, dormant, or what it waits for */
	public static String list(final MOB who)
	{
		final Map<String, Talent> all = talents();
		if(all.isEmpty())
			return CMLib.lang().L("This world has no latent talents.");
		final StringBuilder s = new StringBuilder(CMLib.lang().L("Talents:"));
		for(final Talent t : all.values())
		{
			final String d = decided(who, t.id);
			s.append("\n  ").append(t.title).append(": ");
			if(d.equals("awakened"))
				s.append("awakened");
			else
			if(d.equals("dormant"))
				s.append("it did not awaken in you, and will not");
			else
				s.append("latent; it waits for ").append(String.join(", ", missing(who, t)));
		}
		return s.toString();
	}
}
