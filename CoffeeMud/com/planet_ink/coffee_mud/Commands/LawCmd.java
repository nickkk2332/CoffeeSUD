package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
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
 * LAW (DT21), for builders: an area's legal system as it found its people and
 * places, and its warrants.
 * <ul>
 * <li>LAW: the law in use, the officers, judges, jail and release rooms it
 * found by their names (or that it found none), the taxes and the crimes.</li>
 * <li>LAW CRIMES [text]: the crimes, with their names and sentences.</li>
 * <li>LAW WARRANTS [name]: the warrants out.</li>
 * <li>LAW PARDON name [crime|ALL]: takes warrants away.</li>
 * <li>LAW ACCUSE name crime: makes one, as a witness would.</li>
 * </ul>
 */
public class LawCmd extends StdCommand
{
	public LawCmd()
	{
	}

	private final String[] access = I(new String[] { "LAW" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** as the law finds its jail and release rooms: by ID, else by title, else by description */
	private static Room findRoom(final Area A, final String which)
	{
		final Room R = CMLib.map().getRoom(which);
		if(R != null)
			return R;
		for(final Enumeration<Room> r = A.getMetroMap(); r.hasMoreElements();)
		{
			final Room R2 = r.nextElement();
			if(CMLib.english().containsString(R2.displayText(), which))
				return R2;
		}
		for(final Enumeration<Room> r = A.getMetroMap(); r.hasMoreElements();)
		{
			final Room R2 = r.nextElement();
			if(CMLib.english().containsString(R2.description(), which))
				return R2;
		}
		return null;
	}

	/** whether a mob's name or look holds one of the law's names for its people */
	private static boolean named(final MOB M, final List<String> names)
	{
		for(final String n : names)
		{
			if(n.equals("@"))
				continue;
			if(CMLib.english().containsString(M.Name(), n) || CMLib.english().containsString(M.displayText(), n))
				return true;
		}
		return false;
	}

	private static String rooms(final Area A, final List<String> names)
	{
		if(names.isEmpty() || names.get(0).equals("@"))
			return "(none: where the criminal is)";
		final List<String> found = new ArrayList<String>();
		for(final String n : names)
		{
			final Room R = findRoom(A, n);
			found.add("'" + n + "' " + ((R == null) ? "FOUND NOWHERE" : ("is " + CMLib.map().getExtendedRoomID(R))));
		}
		return String.join("; ", found);
	}

	/**
	 * The law of the area a character is in, as it found its people and places.
	 * @param mob the one asking
	 * @return the description, or why there is none
	 */
	public static String describe(final MOB mob)
	{
		final Room R = mob.location();
		final LegalBehavior B = (R == null) ? null : CMLib.law().getLegalBehavior(R);
		final Area A = (R == null) ? null : CMLib.law().getLegalObject(R);
		if((B == null) || (A == null))
			return "There is no law here (an area needs a legal behaviour, such as Arrest).";
		final Law L = B.legalInfo(A);
		final StringBuilder str = new StringBuilder("The law of " + A.Name() + ": " + B.ID()
			+ ((B.getParms().trim().length() == 0) ? " (laws.ini)" : (" " + B.getParms())));
		if(L == null)
			return str.append("\n\rIts law could not be read.").toString();
		final List<String> officers = new ArrayList<String>();
		final List<String> judges = new ArrayList<String>();
		final List<String> notQuite = new ArrayList<String>();
		for(final Enumeration<Room> r = A.getMetroMap(); r.hasMoreElements();)
		{
			final Room R2 = r.nextElement();
			for(final Enumeration<MOB> m = R2.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if((M == null) || (!M.isMonster()))
					continue;
				final String at = M.Name() + " @ " + CMLib.map().getExtendedRoomID(R2);
				final boolean mobile = CMLib.flags().isMobile(M);
				if(B.isAnyOfficer(A, M))
					officers.add(at);
				else
				if(named(M, L.officerNames()))
					notQuite.add(at + ": named as an officer, but " + (mobile ? "kept from officialdom (SUPPRESS_OFFICIALDOM)" : "does not wander (an officer needs the Mobile behaviour)"));
				if(B.isJudge(A, M))
					judges.add(at);
				else
				if(named(M, L.judgeNames()))
					notQuite.add(at + ": named as a judge, but " + (mobile ? "wanders (a judge must stay put)" : "kept from officialdom (SUPPRESS_OFFICIALDOM)"));
			}
		}
		str.append("\n\rOfficers (").append(String.join(", ", L.officerNames())).append("): ").append(officers.size()).append(" found")
			.append(officers.isEmpty() ? "" : (": " + String.join(", ", officers)));
		str.append("\n\rJudges (").append(String.join(", ", L.judgeNames())).append("): ").append(judges.size()).append(" found")
			.append(judges.isEmpty() ? "" : (": " + String.join(", ", judges)));
		for(final String s : notQuite)
			str.append("\n\r  ").append(s);
		str.append("\n\rJail: ").append(rooms(A, L.jailRooms()));
		str.append("\n\rRelease: ").append(rooms(A, L.releaseRooms()));
		final List<String> taxes = new ArrayList<String>();
		for(final String key : new String[] { "SALESTAX", "CITTAX", "PROPERTYTAX" })
		{
			final Object v = L.taxLaws().get(key);
			if((v instanceof String) && (((String)v).trim().length() > 0))
				taxes.add(key.toLowerCase() + " " + v + "%");
		}
		str.append("\n\rTaxes: ").append(taxes.isEmpty() ? "none" : String.join(", ", taxes));
		str.append("\n\rCrimes: ").append(L.basicCrimes().size()).append(" basic, ").append(L.otherCrimes().size()).append(" others, ")
			.append(L.abilityCrimes().size()).append(" of skills and effects, ").append(L.bannedItems().size()).append(" banned items (LAW CRIMES)");
		str.append("\n\rWarrants: ").append(L.warrants().size()).append(" (LAW WARRANTS)");
		return str.toString();
	}

	private static String crimeLine(final String key, final String[] bits)
	{
		if((bits == null) || (bits.length < Law.BIT_NUMBITS))
			return key;
		return key + ": " + bits[Law.BIT_CRIMENAME] + " (" + bits[Law.BIT_SENTENCE] + ")"
			+ ((bits[Law.BIT_CRIMELOCS].trim().length() > 0) ? (" where " + bits[Law.BIT_CRIMELOCS]) : "")
			+ ((bits[Law.BIT_CRIMEFLAGS].trim().length() > 0) ? (" when " + bits[Law.BIT_CRIMEFLAGS]) : "");
	}

	private static MOB findPerson(final MOB mob, final String name)
	{
		MOB M = (mob.location() == null) ? null : mob.location().fetchInhabitant(name);
		if(M == null)
			M = CMLib.players().getPlayer(name);
		if(M == null)
			M = Party.findInWorld(mob, name);
		return M;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final Room R = mob.location();
		if(commands.size() < 2)
		{
			mob.tell(describe(mob));
			return false;
		}
		final LegalBehavior B = (R == null) ? null : CMLib.law().getLegalBehavior(R);
		final Area A = (R == null) ? null : CMLib.law().getLegalObject(R);
		final Law L = ((B == null) || (A == null)) ? null : B.legalInfo(A);
		if(L == null)
		{
			mob.tell(describe(mob));
			return false;
		}
		final String verb = commands.get(1).toUpperCase();
		final String rest = CMParms.combine(commands, 2);
		if(verb.equals("CRIMES"))
		{
			final StringBuilder str = new StringBuilder("Crimes of " + A.Name() + ":");
			final Map<String, String[]> all = new TreeMap<String, String[]>();
			all.putAll(L.basicCrimes());
			all.putAll(L.abilityCrimes());
			for(final Map.Entry<String, String[]> e : all.entrySet())
			{
				final String line = crimeLine(e.getKey(), e.getValue());
				if((rest.length() == 0) || line.toUpperCase().contains(rest.toUpperCase()))
					str.append("\n\r").append(line);
			}
			for(final List<String> other : L.otherCrimes())
			{
				final String line = "other: " + String.join(", ", other);
				if((rest.length() == 0) || line.toUpperCase().contains(rest.toUpperCase()))
					str.append("\n\r").append(line);
			}
			mob.tell(str.toString());
			return false;
		}
		if(verb.equals("WARRANTS"))
		{
			final StringBuilder str = new StringBuilder("Warrants in " + A.Name() + ":");
			int n = 0;
			for(final LegalWarrant W : L.warrants())
			{
				final String who = (W.criminal() == null) ? "?" : W.criminal().Name();
				if((rest.length() > 0) && (!who.toUpperCase().startsWith(rest.toUpperCase())))
					continue;
				n++;
				str.append("\n\r").append(who).append(": ").append(W.crime())
					.append((W.victim() != null) ? (" against " + W.victim().Name()) : "")
					.append(", offence ").append(W.offenses()).append(", state ").append(W.state());
			}
			if(n == 0)
				str.append(" none.");
			mob.tell(str.toString());
			return false;
		}
		if(verb.equals("PARDON") || verb.equals("ACCUSE"))
		{
			if(commands.size() < 3)
			{
				mob.tell(L("LAW @x1 [NAME] [CRIME]", verb));
				return false;
			}
			final MOB M = findPerson(mob, commands.get(2));
			if(M == null)
			{
				mob.tell(L("There is no '@x1'.", commands.get(2)));
				return false;
			}
			final String crime = CMParms.combine(commands, 3).trim();
			if(verb.equals("ACCUSE"))
			{
				if(crime.length() == 0)
				{
					mob.tell(L("LAW ACCUSE [NAME] [CRIME]: LAW CRIMES lists them."));
					return false;
				}
				final int before = L.warrants().size();
				B.accuse(A, M, null, new String[] { crime.toUpperCase() });
				if(L.warrants().size() > before)
					mob.tell(L("@x1 is accused of @x2.", M.Name(), crime));
				else
					mob.tell(L("@x1 is not accused: '@x2' is no crime of this law, or the law is off (LAW CRIMES).", M.Name(), crime));
				return false;
			}
			int n = 0;
			for(final LegalWarrant W : new ArrayList<LegalWarrant>(L.warrants()))
			{
				if((W.criminal() == M)
				&& ((crime.length() == 0) || crime.equalsIgnoreCase("ALL") || W.crime().equalsIgnoreCase(crime)))
				{
					B.deleteWarrant(A, W);
					n++;
				}
			}
			mob.tell(L("@x1 warrants against @x2 taken away.", "" + n, M.Name()));
			return false;
		}
		mob.tell(L("LAW (CRIMES ([TEXT])/WARRANTS ([NAME])/PARDON [NAME] ([CRIME]/ALL)/ACCUSE [NAME] [CRIME])"));
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
		return SaveGame.isBuilderMode() || SaveGame.isDevMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.ABOVELAW);
	}
}
