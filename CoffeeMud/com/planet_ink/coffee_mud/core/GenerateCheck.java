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
		final String file;
		final String text;
		final Map<String, Object> defined;
		final List<String> out = new ArrayList<String>();

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
		if(literal(layout))
		{
			for(final String one : CMParms.parseCommas(layout, true)) // G16: a comma list, each one known
			{
				if(CMLib.percolator().getLayoutManager(one.trim()) == null)
					W.problem(id, tag, "unknown layout " + one.trim());
			}
		}
		final String select = piece.getParmValue("SELECT");
		if((select != null) && (select.trim().length() > 0) && (!SELECT_MODE.matcher(select.trim()).matches())
			&& (!CMath.isMathExpression(select.trim())) && literal(select))
			W.problem(id, tag, "SELECT " + select.trim()
				+ ": not a selection (NONE, ALL, FIRST, LAST, ANY, a number, or FIRST-, LAST-, PICK-, LIMIT-, ANY-, REPEAT- one)");
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
		for(final String parm : new String[] { "CONDITION", "VALIDATE" })
		{
			final String c = piece.getParmValue(parm);
			if(GenJs.isJs(c)) // G17: a js: one compiles
			{
				final String err = GenJs.compileError(GenJs.body(CMLib.xml().restoreAngleBrackets(c)));
				if(err != null)
					W.problem(id, tag, parm + " " + CMStrings.limit(c.trim(), 40) + ": " + err);
			}
		}
		if(piece.contents().size() == 0)
		{
			for(final String js : GenJs.inText(piece.value())) // G17: and a ${js:...}
			{
				final String err = GenJs.compileError(js);
				if(err != null)
					W.problem(id, tag, "${js:" + CMStrings.limit(js, 40) + "}: " + err);
			}
		}
		for(final XMLTag sub : piece.contents())
			walk(W, sub, id, tag.equals("CLASS") ? kind : myKind);
	}

	private static final Set<String> ATTRIBUTES = new HashSet<String>(java.util.Arrays
		.asList("ABILITIES ABILITY ACTION AFFBEHAV AFFECTS AFFIXES ALIGNMENT AREA_ ARMOR AT ATTACK AUTHOR BEHAVIORS CHAR CLASS CONDITION CONTENT_LOAD COPYOF COUNT CURSED DAMAGE DEFCLOSED DEFINE DEFLOCKED DESCRIPTION DIRECTION DISPLAY DISPOSITION DURATION EXPIRATION FACTION FALLBACK FROM GENDER HASALID HASALOCK HPMOD ID IN INSERT ISGETTABLE ITEM_ ITEM_POWERBONUS KEYNAME KINDS LAYOUT LEVEL LEVELS LEVELSDIR LIKE LLM LOAD MAGICABILITY MAPFILE MATERIAL MAX MERGE MIN MODBASE MOB_ MOB_POWERBONUS NAME NOWEAR NUMBER ONGENERATE ORDER PARMS PICKWEIGHT POS POWER POWERBONUS PREDEFINE PRICE PROFF QUALIFY QUEST QUEST_TEMPLATE_ID RACE REPLACE REQUIRED REQUIRES ROOM ROOMLINK SEED SELECT SIZE STRING TAGDEFINE TEXT THEME TITLE VALIDATE VALUE VAR WEIGHT WHERE"
		.split(" ")));
	private static final Pattern VARIABLE = Pattern.compile("\\$(?:\\{(?:[lucp]:)*([A-Za-z_][A-Za-z0-9_]*)\\}|([A-Za-z_][A-Za-z0-9_]*))");
	private static final Set<String> CONTEXT = new HashSet<String>(java.util.Arrays
		.asList("GAME_DAY GEN_SEED PLAYER_LEVEL PARTY_SIZE AREA_LEVEL AREA_THEME HOUR SEASON WEATHER AREATYPE POP_ITEMS POP_MOBS NOFANTASY MULTI_LEVEL ADJ_SUBTHEME"
		.split(" ")));

	private static Set<String> declarations(final XMLTag piece, final Set<String> inherited)
	{
		final Set<String> names=new HashSet<String>(inherited);
		for(final String key:new String[]{"DEFINE","PREDEFINE","TAGDEFINE","REQUIRES"})
		{
			final String text=piece.getParmValue(key);
			if(text==null)
				continue;
			for(final String part:CMParms.parseCommas(text,true))
			{
				final String name=part.split("[=+*/-]",2)[0].trim().replaceFirst("^\\$","").toUpperCase();
				if(name.matches("[A-Z_][A-Z0-9_]*"))
					names.add(name);
			}
		}
		return names;
	}

	private static void flow(final Walk W, final XMLTag piece, final Set<String> inherited, final String nearest)
	{
		final String own=piece.getParmValue("ID"), id=own==null?nearest:own;
		final Set<String> names=declarations(piece,inherited);
		final List<String> texts=new ArrayList<String>();
		if(piece.contents().isEmpty())
			texts.add(piece.value());
		for(final Map.Entry<String,String> attr:piece.parms().entrySet())
		{
			final String key=attr.getKey().toUpperCase();
			if(!ATTRIBUTES.contains(key) && !validStat(piece,key))
				W.problem(id,piece.tag(),"unknown attribute "+key);
			if(!key.equals("REQUIRES") && !key.equals("DEFINE") && !key.equals("PREDEFINE") && !key.equals("TAGDEFINE"))
				texts.add(attr.getValue());
		}
		final Set<String> seen=new HashSet<String>();
		for(final String text:texts)
		{
			// JavaScript's $ syntax and strings are not generator variable references.
			String plain=text;
			for(final String js:GenJs.inText(text))
				plain=plain.replace("${js:"+js+"}","");
			if(GenJs.isJs(plain))
				continue;
			final java.util.regex.Matcher m=VARIABLE.matcher(plain);
			while(m.find())
			{
				final String name=(m.group(1)==null?m.group(2):m.group(1)).toUpperCase();
				if(!name.matches("I|N|T|O|P|G") && !names.contains(name) && !W.defined.containsKey(name)
					&& !name.matches("(ROOMTAG|ROOMLINK|ROOMTITLE|NODETYPE|AREA|ROOM|MOB|ITEM|EXIT|ABILITY|AFFECT|BEHAVIOR|STAT)_.*")
					&& seen.add(name))
					W.problem(id,piece.tag(),"variable $"+name+" is not defined or declared in REQUIRES");
			}
		}
		final String requires=piece.getParmValue("REQUIRES");
		if(requires!=null)
		{
			final Map<String,String> values=new java.util.HashMap<String,String>();
			for(final String key:new String[]{"PREDEFINE","DEFINE"})
			{
				final String text=piece.getParmValue(key);
				if(text!=null)
					for(final String part:CMParms.parseCommas(text,true))
					{
						final int eq=part.indexOf('=');
						if(eq>0)
							values.put(part.substring(0,eq).trim().replaceFirst("^\\$","").toUpperCase(),part.substring(eq+1).trim());
					}
			}
			final Map<String,Set<String>> permitted=new java.util.HashMap<String,Set<String>>();
			for(final String part:CMParms.parseCommas(requires,true))
			{
				final int eq=part.indexOf('=');
				if(eq<0)
					continue;
				final String key=part.substring(0,eq).trim().replaceFirst("^\\$","").toUpperCase(), spec=part.substring(eq+1).trim();
				if(key.isEmpty())
				{
					W.problem(id,piece.tag(),"REQUIRES has an empty variable name");
					continue;
				}
				final boolean numeric=spec.matches("(?i)int|integer|double|number|#"), string=spec.matches("(?i)string|\\$");
				final String value=values.get(key);
				if(value!=null && value.indexOf('$')<0)
				{
					final boolean ok=numeric?CMath.isMathExpression(value):string?!value.isEmpty():CMParms.parseSemicolons(spec,true).contains(value);
					if(!ok)
						W.problem(id,piece.tag(),"REQUIRES "+key+"="+spec+" cannot be met by definition "+value);
				}
				if(!numeric && !string)
				{
					final Set<String> allowed=new HashSet<String>(CMParms.parseSemicolons(spec,true));
					if(permitted.containsKey(key))
						allowed.retainAll(permitted.get(key));
					permitted.put(key,allowed);
					if(allowed.isEmpty() && !spec.isEmpty())
						W.problem(id,piece.tag(),"REQUIRES "+key+" has no possible value");
				}
			}
		}
		// Declarations in an inserted sibling may be evaluated lazily by the percolator.
		final Set<String> childNames=new HashSet<String>(names);
		for(final XMLTag child:piece.contents())
			childNames.addAll(declarations(child,names));
		for(final XMLTag child:piece.contents())
			flow(W,child,childNames,id);
	}

	private static void allDeclarations(final XMLTag piece,final Set<String> names,final Set<XMLTag> seen)
	{
		if(!seen.add(piece))
			return;
		names.addAll(declarations(piece,names));
		for(final XMLTag child:piece.contents())
			allDeclarations(child,names,seen);
	}

	private static boolean validStat(final XMLTag tag,final String key)
	{
		final String cls=tag.getParmValue("CLASS");
		com.planet_ink.coffee_mud.core.interfaces.Modifiable model=null;
		if(tag.tag().equalsIgnoreCase("MOB"))
			model=CMClass.getMOBPrototype(cls==null?"GenMob":cls);
		if(tag.tag().equalsIgnoreCase("ITEM"))
			model=CMClass.getItemPrototype(cls==null?"GenItem":cls);
		if(tag.tag().equalsIgnoreCase("ROOM"))
			model=CMClass.getLocalePrototype(cls==null?"StdRoom":cls);
		return model!=null && model.isStat(key);
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
		final Set<String> names=new HashSet<String>(CONTEXT);
		final Set<XMLTag> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<XMLTag,Boolean>());
		for(final Object value:defined.values())
			if(value instanceof XMLTag)
				allDeclarations((XMLTag)value,names,seen);
		for(final XMLTag root:roots)
			allDeclarations(root,names,seen);
		for(final XMLTag root : roots)
		{
			walk(W, root, null, null);
			flow(W, root, names, null);
		}
		out.addAll(W.out);
		return out;
	}
}
