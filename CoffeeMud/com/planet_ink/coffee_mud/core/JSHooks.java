package com.planet_ink.coffee_mud.core;

import java.util.*;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.core.interfaces.*;

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
 * JS9: a JavaScript file that a property (Prop_JScript) or a behaviour
 * (JSBehavior) runs, from its text: SCRIPT=world:js/props/bloodblade.js
 * KILLS=0.  The file is compiled once while it is unchanged, and run in a
 * scope of its own for each property or behaviour, so that what it keeps in
 * its own variables is its own.  The other parameters are its parms (numbers
 * as numbers); what a hook changes in parms is written back to the text, so
 * that it is saved with the host.  The scope has cm, sud, CMMsg and what
 * every JavaScript scope has.  A hook that fails is reported once, then not
 * called again until the text is set again.
 */
public class JSHooks
{
	/** the hooks a file may define */
	public static final String[]	HOOKS	= { "affectPhyStats", "affectCharStats", "affectCharState", "okMessage", "executeMsg", "tick", "onAdded", "onRemoved", "onReload", "catchUp" };

	private final String		kind;
	private String				path		= "";
	private Scriptable			scope		= null;
	private Scriptable			parms		= null;
	private final Set<String>	defined		= new HashSet<String>();
	private final Set<String>	broken		= new HashSet<String>();
	private String				loadedText	= null;
	private String				error		= null;

	/**
	 * @param kind what runs it, as Prop_JScript, for errors
	 */
	public JSHooks(final String kind)
	{
		this.kind = kind;
	}

	/** @return the file, as SCRIPT= names it */
	public String path()
	{
		return path;
	}

	/** @return why the file could not be run, or null */
	public String error()
	{
		return error;
	}

	/**
	 * @param fn a hook
	 * @return true if the file defines it, and it has not failed
	 */
	public boolean has(final String fn)
	{
		return defined.contains(fn) && !broken.contains(fn);
	}

	/**
	 * Reads the text and runs the file in a new scope, if the text is not
	 * the one last read.
	 * @param text SCRIPT=path and the parameters
	 * @param host the thing it is on, or null
	 */
	public void load(final String text, final PhysicalAgent host)
	{
		final String t = (text == null) ? "" : text;
		if(t.equals(loadedText) && (scope != null))
			return;
		loadedText = t;
		defined.clear();
		broken.clear();
		error = null;
		scope = null;
		final Map<String, String> P = CMParms.parseEQParms(t);
		path = P.containsKey("SCRIPT") ? P.get("SCRIPT").trim() : "";
		if(path.length() == 0)
		{
			error = "no SCRIPT=";
			return;
		}
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Script S = JSScope.compileFile(cx, path);
			if(S == null)
			{
				error = "no such file: " + path;
				Log.errOut(kind, error);
				return;
			}
			final ImporterTopLevel top = new ImporterTopLevel(cx);
			JSScope.prepare(cx, top, host);
			ScriptableObject.putProperty(top, "CMMsg", new NativeJavaClass(top, CMMsg.class));
			final Scriptable p = cx.newObject(top);
			for(final Map.Entry<String, String> e : new TreeMap<String, String>(P).entrySet())
			{
				if(e.getKey().equals("SCRIPT"))
					continue;
				final String v = e.getValue();
				if(CMath.isInteger(v.trim()) || CMath.isDouble(v.trim()))
					ScriptableObject.putProperty(p, e.getKey(), Double.valueOf(CMath.s_double(v.trim())));
				else
					ScriptableObject.putProperty(p, e.getKey(), v);
			}
			ScriptableObject.putProperty(top, "parms", p);
			S.exec(cx, top);
			for(final String h : HOOKS)
			{
				if(ScriptableObject.getProperty(top, h) instanceof Function)
					defined.add(h);
			}
			parms = p;
			scope = top;
		}
		catch(final Exception e)
		{
			error = JSRuntime.report(kind + " " + path, e);
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * JS10: reads the file again, in a new scope, and calls its
	 * onReload(old) with the old scope, if it defines one, so that it can
	 * take what it kept there.
	 * @param text SCRIPT=path and the parameters
	 * @param host the thing it is on, or null
	 * @return true if onReload was called
	 */
	public boolean reload(final String text, final PhysicalAgent host)
	{
		final Scriptable old = scope;
		loadedText = null;
		load(text, host);
		if((old == null) || (!has("onReload")))
			return false;
		call("onReload", old);
		return true;
	}

	/**
	 * Runs a hook, if the file defines it.
	 * @param fn the hook
	 * @param args what it gets, Java objects
	 * @return what it returned, as a Java object, or null
	 */
	public Object call(final String fn, final Object... args)
	{
		if((scope == null) || (!has(fn)))
			return null;
		final Object F = ScriptableObject.getProperty(scope, fn);
		if(!(F instanceof Function))
			return null;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Object[] jsArgs = new Object[args.length];
			for(int i = 0; i < args.length; i++)
				jsArgs[i] = Context.javaToJS(args[i], scope);
			return Context.jsToJava(((Function)F).call(cx, scope, scope, jsArgs), Object.class);
		}
		catch(final Exception e)
		{
			broken.add(fn);
			JSRuntime.report(kind + " " + path + " " + fn + "()", e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * The text, with parms as they are now: SCRIPT= first, then the rest by name.
	 * @param was the text it has
	 * @return the text it should have (was, if nothing changed)
	 */
	public String textNow(final String was)
	{
		if((parms == null) || (path.length() == 0))
			return was;
		final StringBuilder str = new StringBuilder("SCRIPT=").append(quote(path));
		final TreeMap<String, String> sorted = new TreeMap<String, String>();
		for(final Object id : parms.getIds())
		{
			final Object v = parms.get(id.toString(), parms);
			if((v == null) || (v instanceof Undefined) || (v instanceof Function))
				continue;
			String s;
			if(v instanceof Number)
			{
				final double d = ((Number)v).doubleValue();
				s = (d == Math.rint(d) && !Double.isInfinite(d)) ? Long.toString((long)d) : Double.toString(d);
			}
			else
				s = Context.toString(v);
			sorted.put(id.toString().toUpperCase(), s);
		}
		for(final Map.Entry<String, String> e : sorted.entrySet())
			str.append(' ').append(e.getKey()).append('=').append(quote(e.getValue()));
		final String now = str.toString();
		if(now.equals(was))
			return was;
		loadedText = now;
		return now;
	}

	private static String quote(final String v)
	{
		if((v.length() > 0) && (v.indexOf(' ') < 0) && (v.indexOf('"') < 0) && (v.indexOf('=') < 0))
			return v;
		return "\"" + v.replace("\"", "'") + "\"";
	}
}
