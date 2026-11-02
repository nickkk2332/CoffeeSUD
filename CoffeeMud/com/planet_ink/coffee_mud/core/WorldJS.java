package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.Function;
import org.mozilla.javascript.Scriptable;
import org.mozilla.javascript.ScriptableObject;

import com.planet_ink.coffee_mud.core.save.WorldFiles;

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
 * ST6 (from JS4): world/js/init.js, if there is one, read once when the game
 * starts, and its functions called when the game's course changes:
 * onBoot(), onNewGame(player), onRestore(player, slot), onBeforeSave(slot)
 * (returning false refuses the save), onPlayerEnter(player),
 * onSwitch(fromChar, toChar), onPartyChange(leader) and
 * onBuilderStart(builder).  Each that the file defines; the others are
 * skipped.  They run on the game thread, with the usual time limit, and an
 * error is told as any script error is.
 */
public final class WorldJS
{
	private WorldJS()
	{
	}

	private static Scriptable	scope	= null;
	private static boolean		read	= false;

	/**
	 * The file.
	 * @return world/js/init.js
	 */
	public static File file()
	{
		return new File(new File(WorldFiles.worldDir(), "js"), "init.js");
	}

	/**
	 * Forgets the file as read, for a restart of the game, which reads it
	 * again.
	 */
	public static synchronized void reset()
	{
		scope = null;
		read = false;
	}

	private static synchronized Scriptable scope()
	{
		if(read)
			return scope;
		read = true;
		final File F = file();
		if(!F.isFile())
			return null;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final ScriptableObject S = cx.initStandardObjects();
			cx.evaluateString(S, new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8), "world/js/init.js", 1, null);
			scope = S;
		}
		catch(final Exception e)
		{
			JSRuntime.report("world/js/init.js", e);
		}
		finally
		{
			Context.exit();
		}
		return scope;
	}

	/**
	 * Calls one of the file's functions, if it defines it.
	 * @param fn the function's name
	 * @param args its arguments, Java objects
	 * @return what it returned, as a Java object, or null
	 */
	public static Object call(final String fn, final Object... args)
	{
		final Scriptable S = scope();
		if(S == null)
			return null;
		final Object F = ScriptableObject.getProperty(S, fn);
		if(!(F instanceof Function))
			return null;
		final Context cx = JSRuntime.enter(JSRuntime.limitSeconds());
		try
		{
			final Object[] jsArgs = new Object[args.length];
			for(int i = 0; i < args.length; i++)
				jsArgs[i] = Context.javaToJS(args[i], S);
			final Object result = ((Function)F).call(cx, S, S, jsArgs);
			return Context.jsToJava(result, Object.class);
		}
		catch(final Exception e)
		{
			JSRuntime.report("world/js/init.js " + fn + "()", e);
			return null;
		}
		finally
		{
			Context.exit();
		}
	}

	/**
	 * Whether the file defines a function.
	 * @param fn its name
	 * @return true if it does
	 */
	public static boolean has(final String fn)
	{
		final Scriptable S = scope();
		return (S != null) && (ScriptableObject.getProperty(S, fn) instanceof Function);
	}
}
