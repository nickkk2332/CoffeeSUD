package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.XMLLibrary;

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
 * VARS: the script variables (MPSETVAR and the like), to see and change while
 * building and testing.
 * <ul>
 * <li>VARS LIST [scope] [host] [mask]: each variable's scope, host, name and
 * value.  The scope is * for all, GLOBAL, QUEST name, a scope's name, or
 * INDIVIDUAL for the engines that keep their own; the host and the mask may
 * hold * as a wild card.</li>
 * <li>VARS SCOPES: the scopes, with how many hosts and variables each has.</li>
 * <li>VARS SET host name value [SCOPE scope]: sets one, as MPSETVAR does
 * (++, +5, -2 and the like work).</li>
 * <li>VARS CLEAR host [name|*] [SCOPE scope]: clears one, or all a host has.</li>
 * <li>VARS WATCH host name: tells of each change to it, with where the script
 * that made it came from; VARS UNWATCH stops them, VARS WATCH alone lists
 * them.</li>
 * <li>VARS DUMP file: writes the list to a file, to compare two runs.</li>
 * </ul>
 */
public class Vars extends StdCommand
{
	public Vars()
	{
	}

	private final String[] access = I(new String[] { "VARS" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** one variable, as listed */
	protected static final class Var implements Comparable<Var>
	{
		final String	scope;
		final String	host;
		final String	name;
		final String	value;

		Var(final String scope, final String host, final String name, final String value)
		{
			this.scope = scope;
			this.host = host;
			this.name = name;
			this.value = value;
		}

		@Override
		public int compareTo(final Var o)
		{
			int c = scope.compareTo(o.scope);
			if(c == 0)
				c = host.compareToIgnoreCase(o.host);
			if(c == 0)
				c = name.compareTo(o.name);
			return c;
		}

		@Override
		public String toString()
		{
			return scope + "  " + host + "  " + name + " = " + value;
		}
	}

	/**
	 * Whether a name matches a mask with * as a wild card, without regard to
	 * case.
	 */
	protected static boolean matches(final String mask, final String name)
	{
		if(mask.equals("*"))
			return true;
		final StringBuilder re = new StringBuilder("(?i)");
		for(final char c : mask.toCharArray())
		{
			if(c == '*')
				re.append(".*");
			else
			if(Character.isLetterOrDigit(c))
				re.append(c);
			else
				re.append('\\').append(c);
		}
		return name.matches(re.toString());
	}

	/** adds the variables of one store of variables (a Resources of SCRIPTVAR- maps) */
	@SuppressWarnings("unchecked")
	protected static void addStore(final String scope, final Resources store, final List<Var> into)
	{
		if(store == null)
			return;
		for(final Iterator<String> k = store._findResourceKeys("SCRIPTVAR-"); k.hasNext();)
		{
			final String key = k.next();
			if(!key.startsWith("SCRIPTVAR-"))
				continue;
			final Object o = store._getResource(key);
			if(!(o instanceof Map))
				continue;
			final Map<String, String> H = new TreeMap<String, String>((Map<String, String>)o);
			for(final String name : H.keySet())
				into.add(new Var(scope, key.substring(10), name, H.get(name)));
		}
	}

	/** the engines on a thing, room or area that keep variables of their own */
	protected static void addIndividual(final PhysicalAgent P, final List<Var> into)
	{
		if(P == null)
			return;
		for(final Enumeration<ScriptingEngine> e = P.scripts(); e.hasMoreElements();)
		{
			final ScriptingEngine S = e.nextElement();
			if(S == null)
				continue;
			final String sc = S.getVarScope();
			if(!(sc.equals("*") || sc.equalsIgnoreCase("INDIVIDUAL")))
				continue;
			final String xml = S.getLocalVarXML();
			if((xml == null) || (xml.length() == 0))
				continue;
			try
			{
				for(final XMLLibrary.XMLTag hostTag : CMLib.xml().parseAllXML(xml))
				{
					for(final XMLLibrary.XMLTag varTag : hostTag.contents())
						into.add(new Var("INDIVIDUAL:" + P.Name(), hostTag.tag(), varTag.tag(), CMLib.xml().restoreAngleBrackets(varTag.value())));
				}
			}
			catch(final Exception ex)
			{
				// a host name the XML cannot hold: left out
			}
		}
	}

	/**
	 * Every script variable in the game: the global scope, the named scopes
	 * (a quest's is its name), and the individual stores of the engines in the
	 * world.
	 * @return the variables, sorted
	 */
	public static List<Var> allVars()
	{
		final List<Var> all = new ArrayList<Var>();
		for(final Iterator<String> k = Resources.findResourceKeys("VARSCOPE-"); k.hasNext();)
		{
			final String key = k.next();
			if(!key.startsWith("VARSCOPE-"))
				continue;
			final Object o = Resources.getResource(key);
			if(o instanceof Resources)
			{
				final String scope = key.substring(9);
				addStore(scope.equals("*GLOBAL*") ? "GLOBAL" : scope, (Resources)o, all);
			}
		}
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
			addIndividual(a.nextElement(), all);
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			addIndividual(R, all);
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				addIndividual(M, all);
				for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
					addIndividual(i.nextElement(), all);
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
				addIndividual(i.nextElement(), all);
		}
		Collections.sort(all);
		return all;
	}

	/**
	 * The variables a VARS LIST asks for.
	 * @param scope * for all, GLOBAL, INDIVIDUAL, or a scope's name
	 * @param host the host's mask
	 * @param mask the name's mask
	 * @return the lines
	 */
	public static List<String> listing(final String scope, final String host, final String mask)
	{
		final List<String> out = new ArrayList<String>();
		for(final Var V : allVars())
		{
			final boolean scopeOk = scope.equals("*")
				|| V.scope.equalsIgnoreCase(scope)
				|| (scope.equalsIgnoreCase("INDIVIDUAL") && V.scope.startsWith("INDIVIDUAL:"));
			if(scopeOk && matches(host, V.host) && matches(mask, V.name))
				out.add(V.toString());
		}
		return out;
	}

	/** an engine of the given scope, to set and clear variables as scripts do */
	protected static ScriptingEngine engine(final String scope)
	{
		final ScriptingEngine S = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
		S.setVarScope(((scope == null) || scope.equalsIgnoreCase("GLOBAL")) ? "" : scope);
		return S;
	}

	/** takes a trailing SCOPE x off the words, and returns x, or null */
	protected static String takeScope(final List<String> words)
	{
		for(int i = words.size() - 2; i >= 0; i--)
		{
			if(words.get(i).equalsIgnoreCase("SCOPE"))
			{
				final String scope = words.get(i + 1);
				words.remove(i + 1);
				words.remove(i);
				return scope;
			}
		}
		return null;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final List<String> words = new ArrayList<String>(commands);
		words.remove(0);
		final String sub = words.size() > 0 ? words.remove(0).toUpperCase() : "LIST";
		if(sub.equals("LIST"))
		{
			String scope = "*";
			if(words.size() > 0)
			{
				scope = words.remove(0);
				if(scope.equalsIgnoreCase("QUEST") && (words.size() > 0))
					scope = words.remove(0).toUpperCase();
			}
			final String host = words.size() > 0 ? words.remove(0) : "*";
			final String mask = words.size() > 0 ? words.remove(0) : "*";
			final List<String> lines = listing(scope, host, mask);
			if(lines.isEmpty())
				mob.tell(L("No script variables match."));
			else
				mob.tell(L("Script variables (scope, host, name = value):\n\r") + String.join("\n\r", lines));
			return false;
		}
		if(sub.equals("SCOPES"))
		{
			final Map<String, Set<String>> hosts = new TreeMap<String, Set<String>>();
			final Map<String, int[]> counts = new TreeMap<String, int[]>();
			for(final Var V : allVars())
			{
				if(!hosts.containsKey(V.scope))
				{
					hosts.put(V.scope, new TreeSet<String>());
					counts.put(V.scope, new int[1]);
				}
				hosts.get(V.scope).add(V.host.toUpperCase());
				counts.get(V.scope)[0]++;
			}
			if(hosts.isEmpty())
				mob.tell(L("There are no script variables."));
			else
			{
				final StringBuilder str = new StringBuilder(L("Scopes of script variables:"));
				for(final String scope : hosts.keySet())
					str.append("\n\r").append(L("@x1: @x2 hosts, @x3 variables", scope, "" + hosts.get(scope).size(), "" + counts.get(scope)[0]));
				mob.tell(str.toString());
			}
			return false;
		}
		if(sub.equals("SET"))
		{
			final String scope = takeScope(words);
			if(words.size() < 3)
			{
				mob.tell(L("VARS SET host name value (SCOPE scope)"));
				return false;
			}
			final String host = words.get(0);
			final String name = words.get(1).toUpperCase();
			final String value = CMParms.combine(words, 2);
			final ScriptingEngine S = engine(scope);
			S.setVar(host, name, value);
			mob.tell(L("@x1 @x2 @x3 is '@x4'.", (scope == null) ? "GLOBAL" : scope.toUpperCase(), host, name, S.getVar(host, name)));
			return false;
		}
		if(sub.equals("CLEAR"))
		{
			final String scope = takeScope(words);
			if(words.size() < 1)
			{
				mob.tell(L("VARS CLEAR host (name|*) (SCOPE scope)"));
				return false;
			}
			final String host = words.get(0);
			final String name = (words.size() > 1) ? words.get(1).toUpperCase() : "*";
			engine(scope).setVar(host, name, "");
			mob.tell(name.equals("*")
				? L("Cleared the variables of @x1.", host)
				: L("Cleared @x1 of @x2.", name, host));
			return false;
		}
		if(sub.equals("WATCH"))
		{
			if(words.size() == 0)
			{
				final List<String> mine = ScriptVarWatch.watchesOf(mob);
				mob.tell(mine.isEmpty() ? L("You watch no script variables.") : L("You watch (host name): @x1", String.join(", ", mine)));
				return false;
			}
			final String host = words.get(0);
			final String name = (words.size() > 1) ? words.get(1) : "*";
			ScriptVarWatch.watch(mob, host, name);
			mob.tell(L("You will be told of each change to @x1 of @x2.", name.toUpperCase(), host));
			return false;
		}
		if(sub.equals("UNWATCH"))
		{
			final int n = ScriptVarWatch.unwatch(mob);
			mob.tell(L("You stop watching @x1 script variables.", "" + n));
			return false;
		}
		if(sub.equals("DUMP"))
		{
			if(words.size() < 1)
			{
				mob.tell(L("VARS DUMP file"));
				return false;
			}
			final List<String> lines = listing("*", "*", "*");
			final CMFile F = new CMFile(CMParms.combine(words, 0), mob);
			if(F.saveText(String.join("\n", lines) + "\n"))
				mob.tell(L("Wrote @x1 variables to @x2.", "" + lines.size(), F.getAbsolutePath()));
			else
				mob.tell(L("Could not write @x1.", F.getAbsolutePath()));
			return false;
		}
		mob.tell(L("VARS LIST (scope) (host) (mask), VARS SCOPES, VARS SET host name value (SCOPE scope), VARS CLEAR host (name), VARS WATCH host name, VARS UNWATCH, or VARS DUMP file."));
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
		return CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDQUESTS);
	}
}
