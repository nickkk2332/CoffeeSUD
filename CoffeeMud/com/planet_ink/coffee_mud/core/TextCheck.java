package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.interfaces.Readable;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.Locales.interfaces.Room.VariationCode;

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
 * CHECKTEXT (DT17): what in a room's title and description would print as
 * text instead of varying: variation tags in a text that does not start with
 * &lt;VARIES&gt;; tags that are none (&lt;nigth&gt;); tags written in small
 * letters (still reported as not the usual spelling, though StoryText.parse
 * finds a closing tag whatever its letters; only a leading &lt;VARIES&gt; must
 * be in capitals); tags opened
 * and not closed, or closed and not opened; a MASK whose mask has a &gt; in
 * it, which ends the tag; and a colour code left open at the end.
 */
public final class TextCheck
{
	private TextCheck()
	{
	}

	private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z][A-Za-z0-9]*)([^>]*)>");

	private static VariationCode codeOf(final String name)
	{
		return (VariationCode)CMath.s_valueOf(VariationCode.class, name.toUpperCase());
	}

	/**
	 * Checks one text.
	 * @param text the text, as written
	 * @return the problems
	 */
	public static List<String> checkText(final String text)
	{
		final List<String> out = new ArrayList<String>();
		if((text == null) || (text.length() == 0))
			return out;
		final boolean varies = text.startsWith("<VARIES>");
		final Matcher languages=Pattern.compile("\\^<(/?)LANG\\b(.*?)\\^>",Pattern.CASE_INSENSITIVE|Pattern.DOTALL).matcher(text);
		boolean languageOpen=false;
		int languageTags=0;
		while(languages.find())
		{
			languageTags++;
			if(!languages.group(1).isEmpty())
			{
				if(!languageOpen)
					out.add("closing LANG has no opening LANG");
				if(!languages.group(2).trim().isEmpty())
					out.add("closing LANG takes no attributes");
				languageOpen=false;
			}
			else
			{
				if(languageOpen)
					out.add("nested LANG tags are not supported");
				languageOpen=true;
				final String[] parts=languages.group(2).trim().split("\\s+",2);
				if(parts[0].isEmpty()||Languages.language(parts[0])==null)
					out.add("unknown LANG language "+parts[0]);
				if(parts.length>1)
				{
					out.addAll(AuthoringOptions.errors(parts[1]));
					for(final AuthoringOptions.Token token:AuthoringOptions.tokens(parts[1]))
						if(!token.assignment||!token.key.equals("NOTE"))
							out.add("unknown LANG attribute "+token.value);
				}
			}
		}
		final Matcher starts=Pattern.compile("\\^</?LANG\\b",Pattern.CASE_INSENSITIVE).matcher(text);
		int languageStarts=0;
		while(starts.find())
			languageStarts++;
		if(languageStarts>languageTags)
			out.add("LANG tag is missing its ^> terminator");
		if(languageOpen)
			out.add("LANG is not closed");
		final Matcher m = TAG.matcher(text);
		final LinkedList<String> open = new LinkedList<String>();
		boolean anyCode = false;
		while(m.find())
		{
			if(m.start()>0&&text.charAt(m.start()-1)=='^')
				continue;
			// MXP and LANG are not VARIES tags.
			final boolean close = m.group(1).length() > 0;
			final String name = m.group(2);
			final VariationCode code = codeOf(name);
			if(code == null&&!StoryText.known(name.toUpperCase())&&!name.equalsIgnoreCase("CALL"))
			{
				if(varies)
					out.add("<" + m.group(1) + name + "> is not a tag");
				continue;
			}
			if(name.equalsIgnoreCase("CALL"))
				continue;
			anyCode = true;
			if(!name.equals(name.toUpperCase()))
				out.add("<" + m.group(1) + name + ">: write it <" + m.group(1) + name.toUpperCase()
					+ "> (the usual spelling; a closing tag is found whatever its letters, but a leading <VARIES> must be in capitals)");
			final String up = name.toUpperCase();
			if((name.toUpperCase().startsWith("MASK")) && (!close))
			{
				final String mask = m.group(3).trim();
				if(mask.endsWith("+") || mask.endsWith("-"))
					out.add("<MASK " + mask + "...>: the mask has a '>' in it, which ends the tag (write &gt;)");
				if(mask.indexOf('<') >= 0)
					out.add("<MASK " + mask + ">: the mask has a '<' in it (write &lt;)");
			}
			if(code == VariationCode.VARIES)
				continue;
			// needs no end
			if(!close)
				open.push(up);
			else
				if(open.isEmpty() || (!open.peek().equals(up)))
				out.add("</" + up + "> closes no <" + up + ">" + (open.isEmpty() ? "" : (" (the open one is <" + open.peek() + ">)")));
			else
				open.pop();
		}
		for(final String o : open)
			out.add("<" + o + "> is not closed");
		if(anyCode && (!varies))
			out.add("variation tags, but the text does not start with <VARIES>, so they print as they are");
		// a colour code left open at the end
		String lastColour = null;
		for(int i = 0; i < text.length() - 1; i++)
		{
			if(text.charAt(i) == '^')
			{
				final char c = text.charAt(i + 1);
				if(c == '^')
				{
					i++;
					continue;
				}
				if(Character.isLetter(c) || (c == '?') || (c == '.'))
					lastColour = "" + c;
			}
		}
		if((lastColour != null) && (!lastColour.equals("N")) && (!lastColour.equals("?")) && (!lastColour.equals(".")))
			out.add("the colour ^" + lastColour + " is not closed (end with ^N)");
		return out;
	}

	public static List<String> checkHost(final Physical host)
	{
		final List<String> out=new ArrayList<String>();
		final Room room=CMLib.map().roomLocation(host);
		final String where=(room==null?"":CMLib.map().getExtendedRoomID(room)+" / ")+host.Name();
		final String[] fields={host.displayText(),host.description()};
		for(int i=0;i<fields.length;i++)
			for(final String problem:checkText(fields[i]))
				out.add(where+" "+(i==0?(host instanceof Room?"title":"display"):"description")+": "+problem);
		if(host instanceof Readable)
		{
			final String text=((Readable)host).readableText();
			if(text.trim().toUpperCase().startsWith("FILE="))
			{
				final String path=text.trim().substring(5).trim();
				if(path.startsWith("world/"))
				{
					final java.io.File file=new java.io.File(com.planet_ink.coffee_mud.core.save.WorldFiles.worldDir(),path.substring(6));
					if(!file.isFile())
						out.add(where+": missing FILE="+path);
					else
						try
						{
							for(final String problem:checkText(new String(java.nio.file.Files.readAllBytes(file.toPath()),"UTF-8")))
								out.add(where+" FILE="+path+": "+problem);
						}
						catch(final java.io.IOException e)
						{
							out.add(where+": unreadable FILE="+path);
						}
				}
				else
				{
					final CMFile file=new CMFile(Resources.makeFileResourceName(path),null,CMFile.FLAG_FORCEALLOW);
					if(!file.canRead())
						out.add(where+": missing FILE="+path);
					else
						for(final String problem:checkText(file.text().toString()))
							out.add(where+" FILE="+path+": "+problem);
				}
			}
			else
				for(final String problem:checkText(text))
					out.add(where+" readable text: "+problem);
		}
		final Ability details=host.fetchEffect("Prop_Details");
		if(details!=null)
		{
			final java.util.Set<String> keywords=new java.util.HashSet<String>();
			for(final String record:Scenery.statements(details.text()))
			{
				final int eq=record.indexOf('=');
				if(eq<1)
				{
					out.add(where+" Prop_Details: expected keywords=text: "+record);
					continue;
				}
				for(final String key:record.substring(0,eq).split(",",-1))
				{
					if(key.trim().isEmpty())
						out.add(where+" Prop_Details: empty keyword");
					else if(!keywords.add(key.trim().toUpperCase()))
						out.add(where+" Prop_Details: duplicate keyword "+key.trim());
				}
				for(final String problem:checkText(record.substring(eq+1)))
					out.add(where+" Prop_Details "+record.substring(0,eq)+": "+problem);
			}
		}
		return out;
	}

	/**
	 * Checks a room's title and description.
	 * @param R the room
	 * @return "room title: ..." and "room description: ..." lines
	 */
	public static List<String> checkRoom(final Room R)
	{
		final List<String> out = new ArrayList<String>();
		if(R == null)
			return out;
		out.addAll(checkHost(R));
		final java.util.Set<Physical> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<Physical,Boolean>());
		for(int d=0;d<Directions.NUM_DIRECTIONS();d++)
			if(R.getExitInDir(d)!=null)
				seen.add(R.getExitInDir(d));
		for(final Enumeration<Item> e=R.items();e.hasMoreElements();)
			seen.add(e.nextElement());
		for(final Enumeration<MOB> e=R.inhabitants();e.hasMoreElements();)
		{
			final MOB mob=e.nextElement();
			seen.add(mob);
			for(final Enumeration<Item> i=mob.items();i.hasMoreElements();)
				seen.add(i.nextElement());
		}
		for(final Physical host:seen)
			out.addAll(checkHost(host));
		return out;
	}

	/**
	 * Checks the rooms of an area, or of the world.
	 * @param A the area, or null for the world
	 * @return the problems
	 */
	public static List<String> checkRooms(final Area A)
	{
		final List<String> out = new ArrayList<String>();
		for(final Enumeration<Room> r = (A != null) ? A.getProperMap() : CMLib.map().rooms(); r.hasMoreElements();)
			out.addAll(checkRoom(r.nextElement()));
		return out;
	}
}
