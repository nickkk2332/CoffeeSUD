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
	 * JS5: the world's script and JavaScript files, under world/scripts/ and
	 * world/js/, as paths in the world folder.
	 * @return the paths
	 */
	public static List<String> worldFiles()
	{
		final List<String> out = new ArrayList<String>();
		final File world = WorldFiles.worldDir();
		for(final String sub : new String[] { "js", "scripts" })
			listUnder(new File(world, sub), sub, out);
		Collections.sort(out);
		return out;
	}

	private static void listUnder(final File dir, final String path, final List<String> out)
	{
		final File[] fs = dir.listFiles();
		if(fs == null)
			return;
		for(final File F : fs)
		{
			if(F.isDirectory())
				listUnder(F, path + "/" + F.getName(), out);
			else
			if(F.isFile())
				out.add(path + "/" + F.getName());
		}
	}

	/**
	 * JS5: what is wrong with the world's JavaScript classes, in
	 * world/js/[kind]/ (not lib/): an //extends line missing or naming no
	 * class, and a file that does not compile.
	 * @return the problems, each with its file
	 */
	public static List<String> checkWorldClasses()
	{
		final List<String> problems = new ArrayList<String>();
		final File js = new File(WorldFiles.worldDir(), "js");
		final File[] kinds = js.listFiles();
		if(kinds == null)
			return problems;
		Arrays.sort(kinds);
		for(final File kind : kinds)
		{
			if((!kind.isDirectory()) || kind.getName().equals("lib"))
				continue;
			final File[] files = kind.listFiles();
			if(files == null)
				continue;
			Arrays.sort(files);
			for(final File F : files)
			{
				if(!F.getName().toLowerCase().endsWith(".js"))
					continue;
				final String where = "world/js/" + kind.getName() + "/" + F.getName();
				String text;
				try
				{
					text = new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8");
				}
				catch(final java.io.IOException e)
				{
					problems.add(where + ": cannot be read");
					continue;
				}
				String ext = null;
				for(final String line : text.split("\n"))
				{
					if(line.trim().toUpperCase().startsWith("//EXTENDS "))
					{
						ext = line.trim().substring(10).trim();
						break;
					}
				}
				if(ext == null)
					problems.add(where + ": no //extends line, so it is no class");
				else
				if(!classExists(ext))
					problems.add(where + ": //extends " + ext + ", which is no class");
				final Context cx = Context.enter();
				try
				{
					cx.compileString(text, where, 1, null);
				}
				catch(final RhinoException e)
				{
					problems.add(where + ":" + e.lineNumber() + ": " + e.details());
				}
				finally
				{
					Context.exit();
				}
			}
		}
		return problems;
	}

	private static boolean classExists(final String name)
	{
		try
		{
			Class.forName(name, false, JSScope.class.getClassLoader());
			return true;
		}
		catch(final ClassNotFoundException e)
		{
			final int x = name.lastIndexOf('.');
			return CMClass.getObjectOrPrototype((x < 0) ? name : name.substring(x + 1)) != null;
		}
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
