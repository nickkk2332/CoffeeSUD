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
		prepare(cx, scope, null);
	}

	/**
	 * Gives a scope what every scope has: require() (JS3), and (JS6) cm,
	 * include(path), debug(x) and sud.
	 * @param cx the context it runs in
	 * @param scope the scope, its standard objects already in it
	 * @param host the thing the script runs for (cm.host()), or null
	 */
	public static void prepare(final Context cx, final Scriptable scope, final Object host)
	{
		if((cx == null) || (scope == null))
			return;
		try
		{
			new RequireBuilder().setModuleScriptProvider(modules()).setSandboxed(true).createRequire(cx, scope).install(scope);
			// JS13: Math.random is the game's dice, so a seed (RANDOMSEED=) and a save cover it
			final Object math = ScriptableObject.getProperty(scope, "Math");
			if(math instanceof Scriptable)
				ScriptableObject.putProperty((Scriptable)math, "random", new DiceRandom());
			final JSCm cm = JSCm.forScope(scope, (host instanceof com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent)
				? (com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent)host : null);
			ScriptableObject.putProperty(scope, "cm", Context.javaToJS(cm, scope));
			ScriptableObject.putProperty(scope, "include", new Include(scope));
			if(!ScriptableObject.hasProperty(scope, "debug"))
				ScriptableObject.putProperty(scope, "debug", new Debug((host instanceof com.planet_ink.coffee_mud.core.interfaces.Environmental)
					? ((com.planet_ink.coffee_mud.core.interfaces.Environmental)host).Name() : "javascript"));
			cx.evaluateString(scope, SUD_JS, "sud", 1, null);
		}
		catch(final RuntimeException e)
		{
			Log.errOut("JSScope", e);
		}
	}

	/**
	 * JS11: a cm for a scope, its host given (a shared scope's, for each call).
	 * @param scope the scope
	 * @param host the host
	 * @return the cm
	 */
	public static Object cmFor(final Scriptable scope, final com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent host)
	{
		return JSCm.forScope(scope, host);
	}

	/**
	 * JCONSOLE (JS6): where its user is, each time a line runs: here, area and
	 * target (who it fights, or null).
	 * @param scope the console's scope
	 * @param mob its user
	 */
	public static void where(final Scriptable scope, final com.planet_ink.coffee_mud.MOBS.interfaces.MOB mob)
	{
		if((scope == null) || (mob == null))
			return;
		final com.planet_ink.coffee_mud.Locales.interfaces.Room R = mob.location();
		ScriptableObject.putProperty(scope, "here", Context.javaToJS(R, scope));
		ScriptableObject.putProperty(scope, "area", Context.javaToJS((R == null) ? null : R.getArea(), scope));
		ScriptableObject.putProperty(scope, "target", Context.javaToJS(mob.getVictim(), scope));
	}

	/** JS6: sud, the short names, over cm */
	private static final String SUD_JS =
		"var sud = (function(cm) { function n(x) { return (x === undefined) ? null : x; } return {"
		+ " time: { now: function() { return cm.now(); }, ticks: function() { return cm.ticks(); }, isTurnBased: function() { return cm.isTurnBased(); },"
		+ "   advance: function(t) { return cm.advance(t); }, passTime: function(h, a) { cm.passTime(h, n(a)); },"
		+ "   waitUntil: function(f, then) { return cm.waitUntil(f, n(then)); } },"
		+ " party: { leader: function() { var p = cm.party(); return p.isEmpty() ? null : p.get(0); },"
		+ "   members: function() { return cm.party(); }, isMember: function(m) { return cm.party().contains(m); } },"
		+ " save: { slot: function() { return cm.saveSlot(); }, isBuilder: function() { return cm.isBuilder(); },"
		+ "   isDev: function() { return cm.isDev(); } },"
		+ " find: { room: function(id) { return cm.room(id); }, mob: function(m, w) { return cm.findMob(m, n(w)); },"
		+ "   item: function(i, w) { return cm.findItem(i, n(w)); } },"
		+ " vars: { get: function(h, v) { return String(cm.getVar(n(h), v)); }, set: function(h, v, x) { cm.setVar(n(h), v, n(x)); },"
		+ "   list: function(h) { return cm.vars(n(h)); } },"
		+ " on: function(e, f, k) { return (k == null) ? cm.on(e, f) : cm.on(e, f, String(k)); },"
		+ " off: function(k) { return cm.off(String(k)); }"
		+ "}; })(cm);";

	/** the files compiled, by their path: when the file was changed, and the script */
	private static final Map<String, Object[]> compiled = new HashMap<String, Object[]>();

	/**
	 * A file of resources/ (or world:path), compiled once while it is
	 * unchanged (include(), JS9's Prop_JScript and JSBehavior).
	 * @param cx the context
	 * @param path the file, as resources/ names it, or world:path
	 * @return the script, or null if there is no such file (or the path has ..)
	 */
	public static Script compileFile(final Context cx, final String path)
	{
		if((path == null) || (path.trim().length() == 0) || (path.indexOf("..") >= 0))
			return null;
		final String p = path.trim();
		final String vfs = p.toLowerCase().startsWith("world:") ? ("/world/" + p.substring(6)) : ("/" + Resources.makeFileResourceName(p));
		final CMFile F = new CMFile(vfs, null);
		if((!F.exists()) || (!F.canRead()))
			return null;
		synchronized(compiled)
		{
			final Object[] had = compiled.get(vfs);
			if((had != null) && (((Long)had[0]).longValue() == F.lastModified()))
				return (Script)had[1];
			final Script S = cx.compileString(F.text().toString().replaceAll("\\r\\n|\\n\\r|\\r", "\n"), p, 1, null);
			compiled.put(vfs, new Object[] { Long.valueOf(F.lastModified()), S });
			return S;
		}
	}

	/**
	 * JS15: the text of a file of resources/ (or world:path).
	 * @param path the file
	 * @return its text, or null if there is no such file (or the path has ..)
	 */
	public static String fileText(final String path)
	{
		if((path == null) || (path.trim().length() == 0) || (path.indexOf("..") >= 0))
			return null;
		final String p = path.trim();
		final CMFile F = new CMFile(p.toLowerCase().startsWith("world:") ? ("/world/" + p.substring(6)) : ("/" + Resources.makeFileResourceName(p)), null);
		return ((!F.exists()) || (!F.canRead())) ? null : F.text().toString();
	}

	/** JS6: include(path): runs a file of resources/ (or world:path) in this scope, compiled once while it is unchanged */
	private static final class Include extends BaseFunction
	{
		private static final long serialVersionUID = 1L;
		private final Scriptable home;

		Include(final Scriptable home)
		{
			this.home = home;
		}

		@Override
		public Object call(final Context cx, final Scriptable scope, final Scriptable thisObj, final Object[] args)
		{
			final String path = (args.length > 0) ? Context.toString(args[0]).trim() : "";
			final Script S = compileFile(cx, path);
			if(S == null)
				throw ScriptRuntime.constructError("Error", "include: no such file: " + path);
			return S.exec(cx, home);
		}
	}

	/** JS13: Math.random(), from the game's dice */
	private static final class DiceRandom extends BaseFunction
	{
		private static final long serialVersionUID = 1L;

		@Override
		public Object call(final Context cx, final Scriptable scope, final Scriptable thisObj, final Object[] args)
		{
			return Double.valueOf(CMLib.dice().getRandomizer().nextDouble());
		}

		@Override
		public String getFunctionName()
		{
			return "random";
		}
	}

	/** JS6: debug(x), to the developer log, where the scope has none of its own */
	private static final class Debug extends BaseFunction
	{
		private static final long serialVersionUID = 1L;
		private final String who;

		Debug(final String who)
		{
			this.who = who;
		}

		@Override
		public Object call(final Context cx, final Scriptable scope, final Scriptable thisObj, final Object[] args)
		{
			JSRuntime.debug(who, (args.length > 0) ? args[0] : null);
			return Context.getUndefinedValue();
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

	/** the folders of world/js that hold JavaScript classes (JS5), as SUD loads them */
	public static final Set<String> CLASS_FOLDERS = new HashSet<String>(Arrays.asList("behaviors", "abilities", "properties", "items",
		"mobs", "races", "charclasses", "commands", "common", "locales", "exits", "areas"));

	/**
	 * JS5: what is wrong with the world's JavaScript classes, in
	 * world/js/[kind]/ (not lib/, nor JS9's props/): an //extends line missing or naming no
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
			if((!kind.isDirectory()) || (!CLASS_FOLDERS.contains(kind.getName()))) // not lib, nor JS9's props
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
