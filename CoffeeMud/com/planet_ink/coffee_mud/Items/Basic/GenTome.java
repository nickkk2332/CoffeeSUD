package com.planet_ink.coffee_mud.Items.Basic;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.CharClasses.interfaces.*;
import com.planet_ink.coffee_mud.Commands.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Races.interfaces.*;

import com.planet_ink.coffee_mud.core.save.WorldFiles;
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
 * I3: a book whose pages are kept in it (its readable text), so they go
 * into the world files and every new game: &lt;PAGE TITLE="..." LANG=Elvish
 * LORE=key&gt;text&lt;/PAGE&gt; blocks, or FILE=world/texts/ost.txt whose
 * "=== title ===" lines begin the pages ("=== title | LANG=Elvish ===").
 * READ BOOK shows the contents, READ 3 BOOK, READ NEXT BOOK and READ PREV
 * BOOK a page (or READ [TITLE] BOOK); a page in a language the reader does
 * not know stays closed to them; &lt;VARIES&gt; works in a page; a page with
 * LORE= gives its reader the tattoo LORE:key.  READ_PROG PAGE n fires for
 * that page.  TEAR PAGE 3 FROM BOOK gives a page of its own, and the book
 * is without it (a tag, TORN:3).
 */
public class GenTome extends GenReadable implements Paged, VerbTaker
{
	@Override
	public String ID()
	{
		return "GenTome";
	}

	public GenTome()
	{
		super();
		setName("a generic tome");
		setDisplayText("a generic tome lies here.");
		setMaterial(RawMaterial.RESOURCE_PAPER);
		ScriptEvents.propVerbsSeen();
	}

	/** one page */
	public static class Page
	{
		public String	title	= "";
		public String	lang	= "";
		public String	lore	= "";
		public String	text	= "";
	}

	protected List<Page>			pages		= new ArrayList<Page>();
	protected int					pagesOf		= 0;
	protected final Map<String, Integer>	reading	= new HashMap<String, Integer>();

	/** the text its pages come from: its own, or the file FILE= names */
	protected String source()
	{
		final String t = readableText();
		if((t == null) || (!t.trim().toUpperCase().startsWith(com.planet_ink.coffee_mud.core.interfaces.Readable.FILE_PREFIX)))
			return (t == null) ? "" : t;
		final String f = t.trim().substring(com.planet_ink.coffee_mud.core.interfaces.Readable.FILE_PREFIX.length()).trim();
		try
		{
			if(f.toLowerCase().startsWith("world/"))
			{
				final java.io.File W = WorldFiles.worldDir();
				final java.io.File F = new java.io.File((W == null) ? new java.io.File(".") : W, f.substring(6));
				if(F.isFile())
					return "\n" + new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
			}
			final StringBuffer buf = Resources.getFileResource(f, true);
			return (buf == null) ? "" : ("\n" + buf.toString());
		}
		catch(final Exception e)
		{
			return "";
		}
	}

	/**
	 * Reads pages from a text: PAGE blocks, or "=== title ===" sections.
	 * @param text the text
	 * @return the pages
	 */
	public static List<Page> parse(final String text)
	{
		final List<Page> list = new ArrayList<Page>();
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)<PAGE([^>]*)>(.*?)</PAGE>").matcher(text);
		while(m.find())
		{
			final Page P = new Page();
			final Map<String, String> q = CMParms.parseExactParms(m.group(1).trim());
			P.title = CMParms.getParmStr(q, "TITLE", "");
			P.lang = CMParms.getParmStr(q, "LANG", "");
			P.lore = CMParms.getParmStr(q, "LORE", "");
			P.text = m.group(2).trim();
			list.add(P);
		}
		if(!list.isEmpty())
			return list;
		Page P = null;
		final StringBuilder b = new StringBuilder();
		for(final String raw : text.split("\\r?\\n"))
		{
			final String l = raw.trim();
			if(l.startsWith("===") && l.endsWith("===") && (l.length() > 6))
			{
				if(P != null)
				{
					P.text = b.toString().trim();
					list.add(P);
				}
				P = new Page();
				String head = l.substring(3, l.length() - 3).trim();
				final int bar = head.indexOf('|');
				if(bar >= 0)
				{
					final Map<String, String> q = CMParms.parseExactParms(head.substring(bar + 1).trim());
					P.lang = CMParms.getParmStr(q, "LANG", "");
					P.lore = CMParms.getParmStr(q, "LORE", "");
					head = head.substring(0, bar).trim();
				}
				P.title = head;
				b.setLength(0);
				continue;
			}
			if(P != null)
				b.append(raw).append("\n\r");
		}
		if(P != null)
		{
			P.text = b.toString().trim();
			list.add(P);
		}
		return list;
	}

	/**
	 * @return its pages
	 */
	public List<Page> pages()
	{
		final String src = source();
		if(src.hashCode() != pagesOf)
		{
			pagesOf = src.hashCode();
			pages = parse(src);
		}
		return pages;
	}

	/**
	 * @param page a page, from 1
	 * @return whether it was torn out
	 */
	public boolean torn(final int page)
	{
		return hasTag("TORN:" + page);
	}

	@Override
	public String pageTitle(final int page)
	{
		final List<Page> P = pages();
		return ((page < 1) || (page > P.size())) ? "" : P.get(page - 1).title;
	}

	@Override
	public int pageFor(final MOB mob, final String request)
	{
		final List<Page> P = pages();
		String r = (request == null) ? "" : request.trim();
		if(r.indexOf("<S-NAME>") >= 0)
			r = "";
		if((r.length() == 0) || r.equalsIgnoreCase("CONTENTS") || P.isEmpty())
			return 0;
		final Integer at = (mob == null) ? null : reading.get(mob.Name());
		final int cur = (at == null) ? 0 : at.intValue();
		if(r.equalsIgnoreCase("NEXT"))
			return Math.min(P.size(), cur + 1);
		if(r.equalsIgnoreCase("PREV") || r.equalsIgnoreCase("PREVIOUS"))
			return Math.max(1, cur - 1);
		if(r.toUpperCase().startsWith("PAGE "))
			r = r.substring(5).trim();
		if(CMath.isInteger(r))
			return Math.max(1, Math.min(P.size(), CMath.s_int(r)));
		for(int i = 0; i < P.size(); i++)
		{
			if(CMLib.english().containsString(P.get(i).title, r))
				return i + 1;
		}
		return 0;
	}

	/**
	 * What a page shows a reader.
	 * @param mob who
	 * @param page the page, from 1
	 * @return the text
	 */
	public String show(final MOB mob, final int page)
	{
		final List<Page> P = pages();
		if((page < 1) || (page > P.size()))
			return "";
		final Page p = P.get(page - 1);
		final String head = "Page " + page + ((p.title.length() > 0) ? (": " + p.title) : "") + "\n\r";
		if(torn(page))
			return head + "The page has been torn out.";
		if((p.lang.length() > 0) && (mob != null) && (mob.fetchAbility(p.lang) == null) && (mob.fetchEffect(p.lang) == null))
			return head + "It is written in " + p.lang + ", which you cannot read.";
		String text = p.text;
		if(text.startsWith("<VARIES>") && (mob != null))
			text = CMLib.commands().parseVaries(mob, (mob.location() != null) ? mob.location().getArea() : null, mob.location(), text);
		if((p.lore.length() > 0) && (mob != null) && (mob.findTattoo("LORE:" + p.lore.toUpperCase()) == null))
			mob.addTattoo("LORE:" + p.lore.toUpperCase());
		return head + text;
	}

	/**
	 * @param mob who
	 * @return the contents
	 */
	public String contents(final MOB mob)
	{
		final StringBuilder b = new StringBuilder(CMStrings.capitalizeFirstLetter(name(mob))).append(":\n\r");
		final List<Page> P = pages();
		for(int i = 0; i < P.size(); i++)
		{
			b.append("  ").append(i + 1).append(". ").append((P.get(i).title.length() > 0) ? P.get(i).title : "(untitled)");
			if(torn(i + 1))
				b.append(" (torn out)");
			b.append("\n\r");
		}
		b.append("READ [N], READ NEXT or READ PREV ").append(name(mob)).append(".");
		return b.toString();
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		if((msg.target() == this) && (msg.targetMinor() == CMMsg.TYP_READ) && (msg.source() != null)
		&& (!pages().isEmpty()) && CMLib.flags().canBeSeenBy(this, msg.source())
		&& ((msg.targetMessage() == null) || (!msg.targetMessage().equals("CANCEL"))))
		{
			final MOB mob = msg.source();
			final int page = pageFor(mob, msg.targetMessage());
			final String text;
			if(page == 0)
				text = contents(mob);
			else
			{
				reading.put(mob.Name(), Integer.valueOf(page));
				text = show(mob, page);
			}
			msg.addTrailerMsg(CMClass.getMsg(mob, this, null,
				CMMsg.MSG_WASREAD | CMMsg.MASK_ALWAYS, text,
				CMMsg.MSG_WASREAD | CMMsg.MASK_ALWAYS, text,
				CMMsg.NO_EFFECT, null));
			msg.setTargetMessage("CANCEL");
		}
		super.executeMsg(myHost, msg);
	}

	@Override
	public boolean takeVerb(final MOB mob, final PhysicalAgent host, final String verb, final String rest)
	{
		if((host != this) || (!verb.equalsIgnoreCase("tear")))
			return false;
		final java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?i)^page\\s+(\\d+)\\s+(?:from|out of)\\s+(.+)$").matcher(rest.trim());
		if((!m.matches()) || (!ScriptEvents.names(this, m.group(2).trim())))
			return false;
		final int n = CMath.s_int(m.group(1));
		final List<Page> P = pages();
		if((n < 1) || (n > P.size()) || torn(n))
		{
			mob.tell(L("There is no page @x1 in @x2.", "" + n, name(mob)));
			return true;
		}
		final Page p = P.get(n - 1);
		final Item I = CMClass.getItem("GenReadable");
		I.setName(L("a page torn from @x1", name()));
		I.setDisplayText(L("a page torn from @x1 lies here.", name()));
		I.setDescription(L("Page @x1 of @x2@x3.", "" + n, name(), (p.title.length() > 0) ? (": " + p.title) : ""));
		I.setReadableText(((p.lang.length() > 0) ? ("<PAGE TITLE=\"" + p.title + "\" LANG=" + p.lang + ">" + p.text + "</PAGE>") : p.text));
		I.addTag("PAGE:" + n);
		mob.addItem(I);
		addTag("TORN:" + n);
		if(mob.location() != null)
			mob.location().show(mob, this, CMMsg.MSG_OK_ACTION, L("<S-NAME> tear(s) a page from <T-NAME>."));
		return true;
	}
}
