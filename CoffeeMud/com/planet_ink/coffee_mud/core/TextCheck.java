package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Areas.interfaces.Area;
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
 * letters (the parser finds the closing tag only in capitals); tags opened
 * and not closed, or closed and not opened; a MASK whose mask has a &gt; in
 * it, which ends the tag; and a colour code left open at the end.
 */
public final class TextCheck
{
	private TextCheck()
	{
	}

	private static final Pattern TAG = Pattern.compile("<(/?)([A-Za-z]+)([^>]*)>");

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
		final Matcher m = TAG.matcher(text);
		final LinkedList<String> open = new LinkedList<String>();
		boolean anyCode = false;
		while(m.find())
		{
			final boolean close = m.group(1).length() > 0;
			final String name = m.group(2);
			final VariationCode code = codeOf(name);
			if(code == null)
			{
				if(varies)
					out.add("<" + m.group(1) + name + "> is not a tag");
				continue;
			}
			anyCode = true;
			if(!name.equals(name.toUpperCase()))
				out.add("<" + m.group(1) + name + ">: write it <" + m.group(1) + name.toUpperCase() + ">, or the parser will not find where it ends");
			final String up = name.toUpperCase();
			if((code == VariationCode.MASK) && (!close))
			{
				final String mask = m.group(3).trim();
				if(mask.endsWith("+") || mask.endsWith("-"))
					out.add("<MASK " + mask + "...>: the mask has a '>' in it, which ends the tag (write &gt;)");
				if(mask.indexOf('<') >= 0)
					out.add("<MASK " + mask + ">: the mask has a '<' in it (write &lt;)");
			}
			if(code == VariationCode.VARIES)
				continue; // needs no end
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
		final String id = CMLib.map().getExtendedRoomID(R);
		for(final String p : checkText(R.displayText()))
			out.add(id + " title: " + p);
		for(final String p : checkText(R.description()))
			out.add(id + " description: " + p);
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
