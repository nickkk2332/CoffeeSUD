package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.net.URI;
import java.util.*;

import org.mozilla.javascript.*;
import org.mozilla.javascript.commonjs.module.*;
import org.mozilla.javascript.commonjs.module.provider.*;

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
 * What every JavaScript scope of the game gets, whatever runs it (a script's
 * &lt;SCRIPT&gt;, JRUN, JCONSOLE, a quest, cron, world/js/init.js, a JS
 * class): JS3's require() for the shared libraries in world/js/lib, then
 * resources/js/lib.
 */
public final class JSScope
{
	private static volatile ModuleScriptProvider modules = null;

	private JSScope()
	{
	}

	/**
	 * Gives a scope what every scope has.
	 * @param cx the context it runs in
	 * @param scope the scope, its standard objects already in it
	 */
	public static void prepare(final Context cx, final Scriptable scope)
	{
		if((cx == null) || (scope == null))
			return;
		try
		{
			new RequireBuilder().setModuleScriptProvider(modules()).setSandboxed(true).createRequire(cx, scope).install(scope);
		}
		catch(final RuntimeException e)
		{
			Log.errOut("JSScope", e);
		}
	}

	/**
	 * Where require() looks, in order: the world's js/lib, then resources/js/lib.
	 * @return the folders
	 */
	public static List<File> libraryDirs()
	{
		final List<File> dirs = new ArrayList<File>(2);
		final File world = WorldFiles.worldDir();
		if(world != null)
			dirs.add(new File(new File(world, "js"), "lib"));
		dirs.add(new File(new File("resources", "js"), "lib"));
		return dirs;
	}

	private static synchronized ModuleScriptProvider modules()
	{
		if(modules == null)
		{
			final List<URI> paths = new ArrayList<URI>();
			for(final File dir : libraryDirs())
			{
				URI u = dir.getAbsoluteFile().toURI();
				if(!u.toString().endsWith("/"))
					u = URI.create(u.toString() + "/");
				paths.add(u);
			}
			// compiled once each; each require() looks at the file's time, and a changed one is read again
			modules = new SoftCachingModuleScriptProvider(new UrlModuleSourceProvider(paths, null, new DefaultUrlConnectionExpiryCalculator(0), null));
		}
		return modules;
	}

	/**
	 * Drops the compiled libraries, so that the next require() reads them
	 * again (JS RELOAD), and finds the folders again (a new world folder).
	 */
	public static synchronized void reload()
	{
		modules = null;
	}
}
