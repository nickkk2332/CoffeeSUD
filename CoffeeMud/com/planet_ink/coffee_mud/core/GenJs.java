package com.planet_ink.coffee_mud.core;

import java.util.*;

import org.mozilla.javascript.*;

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
 * G17: JavaScript in random-area templates.  ${js:expr} in a template's
 * text puts in what the expression gives, and CONDITION="js:expr" and
 * VALIDATE="js:expr" keep a tag when it gives a true value.  The expression
 * sees defined (the generator's variables, by any case; numbers come as
 * numbers, and setting one defines it), obj (what is being built, or null),
 * test(condition) (a condition in the template's own grammar), and what
 * every script scope has: cm, gen, sud, require() from world/js/lib and
 * resources/js/lib, include() and debug().  Math.random is the game's dice,
 * so a seed covers it.  One scope serves a generation run, so a library's
 * state lasts while an area is built.  JSCRIPTS=0 turns it off: ${js:...}
 * gives nothing and a js: condition is false.
 */
public final class GenJs
{
	private GenJs()
	{
	}

	/** the variable a run's scope is kept under; the _ keeps it from being cleared with a prefix */
	public static final String	SCOPE_KEY	= "_JS_SCOPE";

	/** what a run gives an expression to look things up with */
	public interface Lookup
	{
		/**
		 * @param name a variable, upper case
		 * @return its value, a string one filled in, or null
		 * @throws Exception the generator's own, to be passed on
		 */
		public Object value(String name) throws Exception;

		/**
		 * @param condition a condition in the template grammar
		 * @return whether it holds
		 * @throws Exception the generator's own, to be passed on
		 */
		public boolean test(String condition) throws Exception;
	}

	/** carries a generator's own exception out through a script */
	public static final class Carrier extends RuntimeException
	{
		private static final long	serialVersionUID	= 1L;
		/** the exception carried */
		public final Exception		inner;

		Carrier(final Exception e)
		{
			super(e.getMessage(), e);
			inner = e;
		}
	}

	/**
	 * @param s a value or condition
	 * @return whether it is one for JavaScript: js: then the expression
	 */
	public static boolean isJs(final String s)
	{
		if(s == null)
			return false;
		final String t = s.trim();
		return (t.length() >= 3) && t.substring(0, 3).equalsIgnoreCase("js:");
	}

	/**
	 * @param s a js: value or condition
	 * @return the expression
	 */
	public static String body(final String s)
	{
		return unescape(s.trim().substring(3));
	}

	/**
	 * @return whether JavaScript may run here (not JSCRIPTS=0)
	 */
	public static boolean enabled()
	{
		return CMProps.getIntVar(CMProps.Int.JSCRIPTS) != CMSecurity.JSCRIPT__NO_APPROVAL;
	}

	/** the XML escapes a template's text may hold, put back: &amp;lt; &amp;gt; &amp;amp; &amp;quot; &amp;apos; */
	static String unescape(final String s)
	{
		if(s.indexOf('&') < 0)
			return s;
		return s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'").replace("&amp;", "&");
	}

	private static boolean	warnedOff	= false;

	/**
	 * Runs an expression.
	 * @param expr the expression, without its js:
	 * @param obj what is being built, or null
	 * @param defined the run's variables
	 * @param lookup what fills them in and tests conditions
	 * @param where what to call it in an error, as "template STRING innkeeper_name"
	 * @return what it gave, or null if JavaScript is off
	 * @throws Exception the generator's own, from a lookup; or a CMException, its error described
	 */
	public static Object eval(final String expr, final Object obj, final Map<String, Object> defined, final Lookup lookup, final String where) throws Exception
	{
		if(!enabled())
		{
			if(!warnedOff)
			{
				warnedOff = true;
				Log.errOut("GenJs", "JavaScript in a template is off (JSCRIPTS=0): " + where);
			}
			return null;
		}
		final Context cx = Context.enter();
		try
		{
			final Scriptable scope = scopeFor(cx, defined);
			final Object[] before = { scope.get("obj", scope), scope.get("defined", scope), scope.get("test", scope) };
			try
			{
				ScriptableObject.putProperty(scope, "obj", Context.javaToJS(obj, scope));
				ScriptableObject.putProperty(scope, "defined", new Defined(scope, defined, lookup));
				ScriptableObject.putProperty(scope, "test", new Test(lookup));
				return cx.evaluateString(scope, expr, where, 1, null);
			}
			finally
			{
				final String[] names = { "obj", "defined", "test" };
				for(int i = 0; i < names.length; i++)
				{
					if(before[i] == Scriptable.NOT_FOUND)
						ScriptableObject.deleteProperty(scope, names[i]);
					else
						ScriptableObject.putProperty(scope, names[i], before[i]);
				}
			}
		}
		catch(final Carrier c)
		{
			throw c.inner;
		}
		catch(final WrappedException w)
		{
			if(w.getWrappedException() instanceof Carrier)
				throw ((Carrier)w.getWrappedException()).inner;
			throw new com.planet_ink.coffee_mud.core.exceptions.CMException(JSRuntime.report(where, w));
		}
		catch(final RuntimeException e)
		{
			throw new com.planet_ink.coffee_mud.core.exceptions.CMException(JSRuntime.report(where, e));
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * @param r what an expression gave
	 * @return it as text: nothing for null and undefined, 3 for 3.0
	 */
	public static String text(final Object r)
	{
		if((r == null) || (r instanceof Undefined))
			return "";
		if(r instanceof Wrapper)
		{
			final Object o = ((Wrapper)r).unwrap();
			return (o == null) ? "" : o.toString();
		}
		return Context.toString(r);
	}

	/**
	 * @param r what an expression gave
	 * @return whether JavaScript calls it true
	 */
	public static boolean truthy(final Object r)
	{
		if((r == null) || (r instanceof Undefined))
			return false;
		if(r instanceof Wrapper)
		{
			final Object o = ((Wrapper)r).unwrap();
			if(o instanceof Boolean)
				return ((Boolean)o).booleanValue();
			if(o instanceof String)
				return ((String)o).length() > 0;
			if(o instanceof Number)
				return ((Number)o).doubleValue() != 0.0;
			return o != null;
		}
		return Context.toBoolean(r);
	}

	/**
	 * GENERATE CHECK: whether an expression compiles.
	 * @param expr the expression, without its js:
	 * @return null if it does, or what is wrong
	 */
	public static String compileError(final String expr)
	{
		final Context cx = Context.enter();
		try
		{
			cx.compileString(unescape(expr), "check", 1, null);
			return null;
		}
		catch(final EvaluatorException e)
		{
			return e.details();
		}
		catch(final RuntimeException e)
		{
			return e.getMessage();
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * GENERATE CHECK: the ${js:...} expressions a text holds, its braces matched.
	 * @param text a template's text
	 * @return the expressions, without their js:
	 */
	public static List<String> inText(final String text)
	{
		final List<String> found = new ArrayList<String>();
		if(text == null)
			return found;
		int x = text.indexOf("${");
		while(x >= 0)
		{
			int y = x + 2;
			while((y + 1 < text.length()) && (text.charAt(y + 1) == ':') && (!isJs(text.substring(y))))
				y += 2; // the l: u: c: flags
			if(isJs(text.substring(y)))
			{
				int depth = 0;
				int z = y;
				while((z < text.length()) && ((text.charAt(z) != '}') || (depth > 0)))
				{
					if(text.charAt(z) == '{')
						depth++;
					else
					if(text.charAt(z) == '}')
						depth--;
					z++;
				}
				found.add(text.substring(y + 3, z));
				x = text.indexOf("${", z);
			}
			else
				x = text.indexOf("${", x + 2);
		}
		return found;
	}

	private static Scriptable scopeFor(final Context cx, final Map<String, Object> defined)
	{
		final Object kept = (defined == null) ? null : defined.get(SCOPE_KEY);
		if(kept instanceof Scriptable)
			return (Scriptable)kept;
		final ScriptableObject scope = cx.initStandardObjects();
		JSScope.prepare(cx, scope, null);
		if(defined != null)
		{
			try
			{
				defined.put(SCOPE_KEY, scope);
			}
			catch(final RuntimeException e)
			{
				// a map that cannot take it: a scope each time
			}
		}
		return scope;
	}

	/** defined: the run's variables, by any case */
	private static final class Defined extends ScriptableObject
	{
		private static final long			serialVersionUID	= 1L;
		private final transient Map<String, Object>	vars;
		private final transient Lookup				lookup;

		Defined(final Scriptable scope, final Map<String, Object> vars, final Lookup lookup)
		{
			this.vars = (vars == null) ? new HashMap<String, Object>() : vars;
			this.lookup = lookup;
			setParentScope(scope);
			setPrototype(ScriptableObject.getObjectPrototype(scope));
		}

		@Override
		public String getClassName()
		{
			return "Defined";
		}

		private static String key(final String name)
		{
			return name.toUpperCase().trim();
		}

		@Override
		public boolean has(final String name, final Scriptable start)
		{
			return (vars.containsKey(key(name)) && (!SCOPE_KEY.equals(key(name)))) || super.has(name, start);
		}

		@Override
		public Object get(final String name, final Scriptable start)
		{
			final String k = key(name);
			if(SCOPE_KEY.equals(k) || (!vars.containsKey(k)))
				return super.get(name, start);
			Object v;
			try
			{
				v = (lookup == null) ? vars.get(k) : lookup.value(k);
			}
			catch(final Exception e)
			{
				throw new Carrier(e);
			}
			if(v == null)
				return null;
			if(v instanceof String)
			{
				final String s = ((String)v).replace('`', '\'');
				if(s.trim().matches("-?\\d+(\\.\\d+)?"))
					return Double.valueOf(s.trim());
				return s;
			}
			if(v instanceof Number)
				return Double.valueOf(((Number)v).doubleValue());
			if(v instanceof Boolean)
				return v;
			return Context.javaToJS(v, getParentScope());
		}

		@Override
		public void put(final String name, final Scriptable start, final Object value)
		{
			final String k = key(name);
			if(SCOPE_KEY.equals(k))
				return;
			if((value == null) || (value instanceof Undefined))
				vars.remove(k);
			else
			if(value instanceof Wrapper)
				vars.put(k, ((Wrapper)value).unwrap());
			else
				vars.put(k, Context.toString(value));
		}

		@Override
		public void delete(final String name)
		{
			vars.remove(key(name));
		}

		@Override
		public Object[] getIds()
		{
			final List<Object> ids = new ArrayList<Object>();
			for(final String k : new ArrayList<String>(vars.keySet()))
			{
				if(!SCOPE_KEY.equals(k))
					ids.add(k);
			}
			return ids.toArray();
		}
	}

	/** test(condition): a condition in the template grammar */
	private static final class Test extends BaseFunction
	{
		private static final long		serialVersionUID	= 1L;
		private final transient Lookup	lookup;

		Test(final Lookup lookup)
		{
			this.lookup = lookup;
		}

		@Override
		public Object call(final Context cx, final Scriptable scope, final Scriptable thisObj, final Object[] args)
		{
			if((lookup == null) || (args.length == 0))
				return Boolean.TRUE;
			try
			{
				return Boolean.valueOf(lookup.test(Context.toString(args[0])));
			}
			catch(final Exception e)
			{
				throw new Carrier(e);
			}
		}
	}
}
