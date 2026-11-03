package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityMapper.CompoundingRule;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Races.interfaces.Race;
import com.planet_ink.coffee_mud.CharClasses.interfaces.CharClass;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.MOBS.interfaces.Deity;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.Modifiable;

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
 * Expands the few web-macro tokens that the game's text and help files use,
 * now that the web macro engine is gone:
 * <ul>
 * <li>@MudInfo?NAME@ and @MudInfo?DOMAIN@</li>
 * <li>@INIValue?INI=KEY@ or @INIValue?MASK=KEY&amp;VALUE@, with DESCZAPPERMASK to describe a mask</li>
 * <li>@RaceList@, the playable races; @ClassList@, @DeityList@ and @StatList@ (P18); the lists
 * take FORMAT=TABLE (a name and the first line of its help a line), and with SHOWLOCKED=YES
 * show a race or class locked behind a RACE_ or CHARCLASS_ tattoo with its UNLOCKHINT</li>
 * <li>@if?Tattoo=NAME@, true if the character (or their profile) has the tattoo (P18)</li>
 * <li>@if?COND@ ... @elif?COND@ ... @else@ ... @endif@, where COND is
 * CheckFactionLoaded?ID&amp;!ID or SystemConfig?MANACOMPOUND[&amp;COOLDOWN]&amp;ENABLED;
 * anything else is false</li>
 * </ul>
 * Text without an @ comes back unchanged.
 */
public class TextMacros
{
	private TextMacros()
	{
	}

	public static StringBuffer filter(final StringBuffer text)
	{
		if((text==null)||(text.indexOf("@")<0))
			return text;
		return new StringBuffer(filter(text.toString()));
	}

	private static final ThreadLocal<MOB> who = new ThreadLocal<MOB>();

	/**
	 * P18: filters text shown to a character: their tattoos and unlocked
	 * races and classes count.
	 * @param text the text
	 * @param mob the character, or null
	 * @return the text filtered
	 */
	public static String filter(final String text, final MOB mob)
	{
		final MOB was=who.get();
		who.set(mob);
		try
		{
			return filter(text);
		}
		finally
		{
			who.set(was);
		}
	}

	/**
	 * P18: filters text shown to a character.
	 * @param text the text
	 * @param mob the character, or null
	 * @return the text filtered
	 */
	public static StringBuffer filter(final StringBuffer text, final MOB mob)
	{
		if((text==null)||(text.indexOf("@")<0))
			return text;
		return new StringBuffer(filter(text.toString(), mob));
	}

	/** @return true when SHOWLOCKED=YES: locked races and classes are listed with their hints */
	public static boolean showLocked()
	{
		final CMProps P=CMProps.instance();
		final String v=(P==null)?"":P.getStr("SHOWLOCKED","NO").trim().toUpperCase();
		return v.startsWith("Y")||v.equals("TRUE")||v.equals("ON");
	}

	/**
	 * @param M a race or class
	 * @return its UNLOCKHINT, or ""
	 */
	public static String unlockHint(final Modifiable M)
	{
		if((M==null)||(!CMParms.contains(M.getStatCodes(), "UNLOCKHINT")))
			return "";
		final String h=M.getStat("UNLOCKHINT");
		return (h==null)?"":h.trim();
	}

	/**
	 * The first line of something's help: its own HELP, else the help entry
	 * of its name, with the "Key : value" header lines passed over.
	 * @param M a race, class or deity
	 * @param name its name
	 * @return the line, or ""
	 */
	public static String firstHelpLine(final Modifiable M, final String name)
	{
		String help="";
		if((M!=null)&&CMParms.contains(M.getStatCodes(), "HELP"))
			help=M.getStat("HELP");
		if((help==null)||(help.trim().length()==0))
		{
			final String h=CMLib.help().getHelpText(name, null, false);
			help=(h==null)?"":h;
		}
		for(final String raw : CMStrings.removeColors(help.replace("\\n\\r","\n")).split("\r?\n|\r"))
		{
			final String line=raw.trim();
			if((line.length()==0)||line.matches("^[A-Za-z][A-Za-z ()/'-]{0,24}:.*"))
				continue;
			return (line.length()>78)?(line.substring(0,75)+"..."):line;
		}
		return "";
	}

	private static String list(final List<String[]> rows, final boolean table)
	{
		final StringBuilder s=new StringBuilder("");
		for(final String[] r : rows)
		{
			if(table)
				s.append(r[0]).append((r[1].length()>0)?(": "+r[1]):"").append("\n\r");
			else
				s.append((s.length()>0)?", ":"").append(r[0]);
		}
		return s.toString();
	}

	public static byte[] filter(final byte[] text)
	{
		if(text==null)
			return text;
		return filter(new String(text)).getBytes();
	}

	public static String filter(final String text)
	{
		if((text==null)||(text.indexOf('@')<0))
			return text;
		final StringBuilder out=new StringBuilder(text.length());
		// one entry per open @if@: whether its current branch is shown, and whether any branch has been
		final List<boolean[]> ifs=new ArrayList<boolean[]>();
		int i=0;
		while(i<text.length())
		{
			final char c=text.charAt(i);
			final int end=(c=='@')?text.indexOf('@',i+1):-1;
			if(end<0)
			{
				if(showing(ifs))
					out.append(c);
				i++;
				continue;
			}
			final String token=text.substring(i+1,end);
			final String name=token.toLowerCase();
			if(name.startsWith("if?"))
			{
				final boolean show=showing(ifs) && condition(token.substring(3));
				ifs.add(new boolean[]{show,show});
			}
			else
			if(name.startsWith("elif?") && !ifs.isEmpty())
			{
				final boolean[] cur=ifs.get(ifs.size()-1);
				cur[0]=(!cur[1]) && showing(ifs.subList(0,ifs.size()-1)) && condition(token.substring(5));
				cur[1]=cur[1]||cur[0];
			}
			else
			if(name.equals("else") && !ifs.isEmpty())
			{
				final boolean[] cur=ifs.get(ifs.size()-1);
				cur[0]=(!cur[1]) && showing(ifs.subList(0,ifs.size()-1));
				cur[1]=true;
			}
			else
			if(name.equals("endif") && !ifs.isEmpty())
				ifs.remove(ifs.size()-1);
			else
			{
				final String value=value(token);
				if(value==null)
				{
					// not a macro: keep the @ and carry on from the next character
					if(showing(ifs))
						out.append(c);
					i++;
					continue;
				}
				if(showing(ifs))
					out.append(value);
			}
			i=end+1;
		}
		return out.toString();
	}

	private static boolean showing(final List<boolean[]> ifs)
	{
		for(final boolean[] b : ifs)
		{
			if(!b[0])
				return false;
		}
		return true;
	}

	private static String value(final String token)
	{
		final int x=token.indexOf('?');
		final String name=(x<0)?token:token.substring(0,x);
		final Map<String,String> parms=CMParms.parseEQParms((x<0)?"":token.substring(x+1).replace('&',' '));
		if(name.equalsIgnoreCase("MudInfo"))
		{
			final String what=(x<0)?"":token.substring(x+1).toUpperCase();
			if(what.equals("NAME"))
				return CMProps.getVar(CMProps.Str.MUD_NAME);
			return "";
		}
		if(name.equalsIgnoreCase("INIValue"))
		{
			String key=parms.get("INI");
			if(key==null)
				key=parms.get("MASK");
			if(key==null)
				return "";
			final String val=CMProps.instance().getStr(key.toUpperCase());
			if(val==null)
				return "";
			if(token.toUpperCase().indexOf("DESCZAPPERMASK")>0)
				return CMLib.masking().maskDesc(val);
			return val;
		}
		final boolean table="TABLE".equalsIgnoreCase(parms.get("FORMAT"));
		final MOB mob=who.get();
		if(name.equalsIgnoreCase("RaceList"))
		{
			final List<String[]> rows=new ArrayList<String[]>();
			for(final Enumeration<Race> r=CMClass.races();r.hasMoreElements();)
			{
				final Race R=r.nextElement();
				final boolean open=((R.availabilityCode()&Area.THEME_FANTASY)>0)
					&&(CMLib.login().isAvailableRace(R)
						||((mob!=null)&&CMLib.login().isTattooedLike(mob,"RACE_"+R.ID().toUpperCase())));
				final String hint=unlockHint(R);
				if(open)
					rows.add(new String[]{R.name(),table?firstHelpLine(R,R.name()):""});
				else
				if(showLocked()&&(hint.length()>0))
					rows.add(new String[]{R.name()+" (locked: "+hint+")",""});
			}
			return list(rows,table);
		}
		if(name.equalsIgnoreCase("ClassList"))
		{
			final List<String[]> rows=new ArrayList<String[]>();
			for(final Enumeration<CharClass> c=CMClass.charClasses();c.hasMoreElements();)
			{
				final CharClass C=c.nextElement();
				final boolean open=((C.availabilityCode()&Area.THEME_FANTASY)>0)
					&&(CMLib.login().isAvailableCharClass(C)
						||((mob!=null)&&CMLib.login().isTattooedLike(mob,"CHARCLASS_"+C.ID().toUpperCase())));
				final String hint=unlockHint(C);
				if(open)
					rows.add(new String[]{C.name(),table?firstHelpLine(C,C.name()):""});
				else
				if(showLocked()&&(hint.length()>0))
					rows.add(new String[]{C.name()+" (locked: "+hint+")",""});
			}
			return list(rows,table);
		}
		if(name.equalsIgnoreCase("DeityList"))
		{
			final List<String[]> rows=new ArrayList<String[]>();
			for(final Enumeration<Deity> d=CMLib.map().deities();d.hasMoreElements();)
			{
				final Deity D=d.nextElement();
				rows.add(new String[]{D.name(),table?firstHelpLine(null,D.Name()):""});
			}
			return list(rows,table);
		}
		if(name.equalsIgnoreCase("StatList"))
		{
			final List<String[]> rows=new ArrayList<String[]>();
			for(final int i : CharStats.CODES.BASECODES())
				rows.add(new String[]{CMStrings.capitalizeAndLower(CharStats.CODES.NAME(i)),""});
			return list(rows,table);
		}
		if(name.equalsIgnoreCase("WebServerPort"))
			return "";
		return null;
	}

	private static boolean condition(final String cond)
	{
		if(cond.toUpperCase().startsWith("TATTOO=")||cond.toUpperCase().startsWith("TATTOO?"))
		{
			// P18: @if?Tattoo=RACE_DROW@, the character's or their profile's
			final MOB mob=who.get();
			final String t=cond.substring(7).trim();
			final boolean not=t.startsWith("!");
			final boolean has=(mob!=null)&&CMLib.login().isTattooedLike(mob,(not?t.substring(1):t).toUpperCase());
			return not?(!has):has;
		}
		final int x=cond.indexOf('?');
		final String name=(x<0)?cond:cond.substring(0,x);
		final String[] args=(x<0)?new String[0]:cond.substring(x+1).split("&");
		if(name.equalsIgnoreCase("CheckFactionLoaded"))
		{
			for(final String arg : args)
			{
				final boolean not=arg.startsWith("!");
				final String id=not?arg.substring(1):arg;
				if(CMLib.factions().isFactionLoaded(id)==not)
					return false;
			}
			return true;
		}
		if(name.equalsIgnoreCase("SystemConfig"))
		{
			boolean manaCompound=false;
			boolean cooldown=false;
			for(final String arg : args)
			{
				manaCompound=manaCompound||arg.equalsIgnoreCase("MANACOMPOUND");
				cooldown=cooldown||arg.equalsIgnoreCase("COOLDOWN");
			}
			if(!manaCompound)
				return false;
			for(final Enumeration<CompoundingRule> e=CMLib.ableMapper().compoundingRules();e.hasMoreElements();)
			{
				final CompoundingRule rule=e.nextElement();
				if((rule.mobMask()==null)&&(rule.compoundingTicks()>0))
				{
					if(cooldown && (rule.amtPenalty()<0))
						return true;
					if((!cooldown) && (rule.amtPenalty()>-1) && ((rule.amtPenalty()>0)||(rule.pctPenalty()>0)))
						return true;
				}
			}
			return false;
		}
		return false;
	}
}
