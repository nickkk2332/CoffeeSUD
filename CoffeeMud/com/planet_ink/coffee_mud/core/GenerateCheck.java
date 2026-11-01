package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary.XMLTag;

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
 * GENERATE CHECK (DT14): loads a generator XML file as GENERATE does, with
 * its LOAD and FROM includes, generates nothing, and reports by file:line and
 * tag id: what loading it logs (duplicate ids, missing includes, FROM without
 * an id); INSERT ids that are not defined; LIKE ids that are not defined or
 * not of the same tag; CLASS values that are no class of their kind
 * (catalog: and metacraft excepted); unknown layouts; SELECT modes that are
 * none (a number or expression is one).
 */
public final class GenerateCheck
{
	private GenerateCheck()
	{
	}

	private static final Pattern SELECT_MODE = Pattern.compile("(?i)NONE|ALL|FIRST|LAST|ANY|(FIRST|LAST|PICK|LIMIT|ANY|REPEAT)-.+");

	/** a value written out, which can be checked: no variables, no expressions */
	private static boolean literal(final String v)
	{
		return (v != null) && (v.trim().length() > 0) && (v.indexOf('$') < 0) && (v.indexOf('(') < 0) && (v.indexOf('<') < 0)
			&& (v.indexOf('{') < 0);
	}

	/** whether a class of a tag's kind exists; null if the tag has no kind */
	private static Boolean classExists(final String kind, final String id)
	{
		final String c = id.trim();
		if(c.toLowerCase().startsWith("catalog:") || c.toLowerCase().startsWith("metacraft"))
			return Boolean.TRUE;
		switch(kind)
		{
		case "AREA":
			return Boolean.valueOf(CMClass.getAreaType(c) != null);
		case "ROOM":
			return Boolean.valueOf(CMClass.getLocalePrototype(c) != null);
		case "MOB":
			return Boolean.valueOf(CMClass.getMOBPrototype(c) != null);
		case "ITEM":
			return Boolean.valueOf(CMClass.getItemPrototype(c) != null);
		case "EXIT":
			return Boolean.valueOf(CMClass.getExit(c) != null);
		case "BEHAVIOR":
			return Boolean.valueOf(CMClass.getBehavior(c) != null);
		case "AFFECT":
		case "ABILITY":
			return Boolean.valueOf(CMClass.getAbility(c) != null);
		default:
			return null;
		}
	}

	private static final Set<String> KINDS = new HashSet<String>(java.util.Arrays.asList(new String[] {
		"AREA", "ROOM", "MOB", "ITEM", "EXIT", "BEHAVIOR", "AFFECT", "ABILITY" }));

	/** the line a tag with this id is at in the text, or 0 */
	private static int lineOf(final String text, final String id)
	{
		if((id == null) || (id.length() == 0))
			return 0;
		final java.util.regex.Matcher m = Pattern.compile("(?i)\\bID\\s*=\\s*[\"']" + Pattern.quote(id) + "[\"']").matcher(text);
		if(!m.find())
			return 0;
		int n = 1;
		for(int i = 0; i < m.start(); i++)
		{
			if(text.charAt(i) == '\n')
				n++;
		}
		return n;
	}

	private static final class Walk
	{
		final String				file;
		final String				text;
		final Map<String, Object>	defined;
		final List<String>			out	= new ArrayList<String>();

		Walk(final String file, final String text, final Map<String, Object> defined)
		{
			this.file = file;
			this.text = text;
			this.defined = defined;
		}

		void problem(final String id, final String tag, final String what)
		{
			final int line = lineOf(text, id);
			out.add(file + ((line > 0) ? (":" + line) : "") + " " + tag + ((id != null) ? (" " + id) : "") + ": " + what);
		}
	}

	private static void walk(final Walk W, final XMLTag piece, final String nearestId, final String kind)
	{
		final String tag = piece.tag().toUpperCase();
		final String ownId = piece.getParmValue("ID");
		final String id = ((ownId != null) && (ownId.length() > 0)) ? ownId : nearestId;
		final String myKind = KINDS.contains(tag) ? tag : kind;
		final String insert = piece.getParmValue("INSERT");
		if((insert != null) && (!insert.trim().toUpperCase().startsWith("SELECT:")))
		{
			for(String s : CMParms.parseCommas(insert, true))
			{
				s = s.startsWith("$") ? s.substring(1).trim() : s.trim();
				if((s.length() > 0) && literal(s) && (!(W.defined.get(s.toUpperCase()) instanceof XMLTag)))
					W.problem(id, tag, "INSERT " + s + ": no such id");
			}
		}
		final String like = piece.getParmValue("LIKE");
		if(like != null)
		{
			for(String s : CMParms.parseCommas(like, true))
			{
				s = s.startsWith("$") ? s.substring(1).trim() : s.trim();
				if((s.length() == 0) || (!literal(s)))
					continue;
				final Object o = W.defined.get(s.toUpperCase());
				if(!(o instanceof XMLTag))
					W.problem(id, tag, "LIKE " + s + ": no such id");
				else
				if(!((XMLTag)o).tag().equalsIgnoreCase(tag))
					W.problem(id, tag, "LIKE " + s + ": a " + ((XMLTag)o).tag().toUpperCase() + ", not a " + tag);
			}
		}
		final String layout = piece.getParmValue("LAYOUT");
		if(literal(layout) && (CMLib.percolator().getLayoutManager(layout.trim()) == null))
			W.problem(id, tag, "unknown layout " + layout.trim());
		final String select = piece.getParmValue("SELECT");
		if((select != null) && (select.trim().length() > 0) && (!SELECT_MODE.matcher(select.trim()).matches())
		&& (!CMath.isMathExpression(select.trim())) && literal(select))
			W.problem(id, tag, "SELECT " + select.trim() + ": not a selection (NONE, ALL, FIRST, LAST, ANY, a number, or FIRST-, LAST-, PICK-, LIMIT-, ANY-, REPEAT- one)");
		final String cls = piece.getParmValue("CLASS");
		if(literal(cls) && (myKind != null))
		{
			final Boolean ok = classExists(myKind, cls);
			if(Boolean.FALSE.equals(ok))
				W.problem(id, tag, "CLASS " + cls.trim() + ": no such " + myKind + " class");
		}
		if(tag.equals("CLASS") && (kind != null) && (piece.contents().size() == 0) && literal(piece.value()))
		{
			final Boolean ok = classExists(kind, piece.value());
			if(Boolean.FALSE.equals(ok))
				W.problem(id, kind, "CLASS " + piece.value().trim() + ": no such " + kind + " class");
		}
		for(final XMLTag sub : piece.contents())
			walk(W, sub, id, tag.equals("CLASS") ? kind : myKind);
	}

	/**
	 * Checks a generator XML file.
	 * @param path the file, as GENERATE ... FROM takes it (under resources)
	 * @return the problems, or one saying the file is missing
	 */
	public static List<String> check(final String path)
	{
		final CMFile F = new CMFile(Resources.buildResourcePath(path), null, CMFile.FLAG_FORCEALLOW);
		final List<String> out = new ArrayList<String>();
		if(!F.canRead())
		{
			out.add(path + ": no such file");
			return out;
		}
		final String text = F.textUnformatted().toString();
		final Map<String, Object> defined = new Hashtable<String, Object>();
		List<XMLTag> roots;
		Log.startCapture();
		try
		{
			roots = CMLib.xml().parseAllXML(text);
			CMLib.percolator().buildDefinedIDSet(roots, defined, new HashSet<String>());
		}
		catch(final RuntimeException e)
		{
			roots = null;
			out.add(path + ": cannot be read: " + e);
		}
		finally
		{
			for(final String s : Log.stopCapture())
				out.add(path + ": " + s.replace('\n', ' ').replace('\r', ' '));
		}
		if(roots == null)
			return out;
		final Walk W = new Walk(path, text, defined);
		for(final XMLTag root : roots)
			walk(W, root, null, null);
		out.addAll(W.out);
		return out;
	}
}
