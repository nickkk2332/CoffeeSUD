package com.planet_ink.coffee_mud.core;

import java.util.*;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
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
 * JS14: the +JS "expr" and -JS "expr" entries of a zapper mask.  The
 * expression is compiled once, with the mask, and run in one scope kept for
 * masks, under the time limit; it sees mob (the mob checked, or the item's
 * owner), item (the item checked), room (where), target (whatever is
 * checked) and cm, cm.store included.  It is true when its value is truthy;
 * an error counts as false, and is reported once for each expression.
 */
public final class JSMask
{
	private static Scriptable			scope		= null;
	private static final Set<String>	reported	= new HashSet<String>();

	private JSMask()
	{
	}

	/**
	 * Compiles an expression for a mask.
	 * @param expr the expression
	 * @return what the mask keeps: the script and the expression, or null if it does not compile
	 */
	public static Object[] compile(final String expr)
	{
		final String e = (expr == null) ? "" : expr.trim();
		if(e.length() == 0)
			return null;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			return new Object[] { cx.compileString("(" + e + "\n)", "+JS", 1, null), e };
		}
		catch(final RhinoException ex)
		{
			report(e, ex);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	private static synchronized void report(final String expr, final Exception e)
	{
		if(reported.add(expr))
			JSRuntime.report("mask +JS \"" + expr + "\"", e);
	}

	private static Scriptable scope(final Context cx)
	{
		if(scope == null)
		{
			final ImporterTopLevel top = new ImporterTopLevel(cx);
			JSScope.prepare(cx, top, null);
			scope = top;
		}
		return scope;
	}

	/**
	 * Runs a mask's expression.
	 * @param compiled what compile gave
	 * @param E what is checked
	 * @param mob the mob checked, or null
	 * @param item the item checked, or null
	 * @param room where, or null
	 * @return true if the expression's value is truthy
	 */
	public static synchronized boolean test(final Object[] compiled, final Environmental E, final MOB mob, final Item item, final Room room)
	{
		if((compiled == null) || (compiled.length < 2) || !(compiled[0] instanceof Script))
			return false;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Scriptable S = scope(cx);
			// from what is checked: the mask's own mob for an item is a stand-in, and its item may be none
			final Item I = (E instanceof Item) ? (Item)E : item;
			final MOB M = (E instanceof MOB) ? (MOB)E : ((I != null) && (I.owner() instanceof MOB)) ? (MOB)I.owner() : null;
			final Room R = (room != null) ? room : ((E instanceof Physical) ? CMLib.map().roomLocation((Physical)E) : null);
			ScriptableObject.putProperty(S, "mob", Context.javaToJS(M, S));
			ScriptableObject.putProperty(S, "item", Context.javaToJS(I, S));
			ScriptableObject.putProperty(S, "room", Context.javaToJS(R, S));
			ScriptableObject.putProperty(S, "target", Context.javaToJS(E, S));
			ScriptableObject.putProperty(S, "cm", Context.javaToJS(JSCm.forScope(S, (E instanceof PhysicalAgent) ? (PhysicalAgent)E : M), S));
			return Context.toBoolean(((Script)compiled[0]).exec(cx, S));
		}
		catch(final Exception e)
		{
			report((String)compiled[1], e);
			return false;
		}
		finally
		{
			Context.exit();
		}
	}
}
