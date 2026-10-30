package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Libraries.interfaces.AbilityMapper.CompoundingRule;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Races.interfaces.Race;

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
 * <li>@RaceList@, the playable races</li>
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
		if(name.equalsIgnoreCase("RaceList"))
		{
			final StringBuilder list=new StringBuilder("");
			for(final Enumeration<Race> r=CMClass.races();r.hasMoreElements();)
			{
				final Race R=r.nextElement();
				if((R.availabilityCode()&Area.THEME_FANTASY)>0)
					list.append(list.length()>0?", ":"").append(R.name());
			}
			return list.toString();
		}
		if(name.equalsIgnoreCase("WebServerPort"))
			return "";
		return null;
	}

	private static boolean condition(final String cond)
	{
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
