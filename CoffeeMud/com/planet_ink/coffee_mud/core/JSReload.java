package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.util.*;

import org.mozilla.javascript.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Areas.interfaces.Area;
import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Exits.interfaces.Exit;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.*;
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
 * JS10: JS RELOAD [file|ALL] puts changed world/js files into the running
 * world.  A file that does not compile is reported with its line, and left
 * as it was.  Then, by where it is:
 * <ul>
 * <li>world/js/init.js is read again, and its onReload(old) called with the
 * old file's scope, if it defines one;</li>
 * <li>world/js/lib: the scopes made from now on read the libraries again
 * (a scope keeps what it has required);</li>
 * <li>a folder of JavaScript classes (JS5): the class is compiled again; the
 * behaviours and effects of that class in the world are replaced with new
 * ones, their text copied; other kinds (mobs, items, rooms) made from now on
 * are new;</li>
 * <li>anything else (Prop_JScript's and JSBehavior's files, JS9): each one
 * running the file reads it again, and its onReload(old) is called with the
 * old scope, if it defines one.</li>
 * </ul>
 * A changed file is one whose time is later than the game's start, or than
 * when it was last reloaded.  JS WATCH ON looks every few seconds and
 * reloads by itself.
 */
public final class JSReload
{
	/** the files' times when last reloaded, by path */
	private static final Map<String, Long>	seen	= new HashMap<String, Long>();
	/** files changed after this are changed */
	private static final long				started	= java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime();

	private static volatile Thread	watcher	= null;
	private static volatile MOB		builder	= null;
	private static volatile int		seconds	= 3;

	private JSReload()
	{
	}

	/** what holds a JSHooks: Prop_JScript and JSBehavior, and what JS11 adds */
	public interface Holder
	{
		/** @return the file it runs, as SCRIPT= names it */
		public String scriptPath();

		/** reads the file again, and calls its onReload(old) */
		public void reloadScript();
	}

	/** @return world/js, or null */
	public static File jsDir()
	{
		final File world = WorldFiles.worldDir();
		return (world == null) ? null : new File(world, "js");
	}

	/** @return every .js file under world/js, as js/... paths, sorted */
	public static List<String> allFiles()
	{
		final List<String> out = new ArrayList<String>();
		final File js = jsDir();
		if(js != null)
			collect(js, "js", out, 0);
		Collections.sort(out);
		return out;
	}

	private static void collect(final File dir, final String path, final List<String> out, final int depth)
	{
		final File[] files = dir.listFiles();
		if((files == null) || (depth > 6))
			return;
		for(final File F : files)
		{
			if(F.isDirectory())
				collect(F, path + "/" + F.getName(), out, depth + 1);
			else
			if(F.getName().toLowerCase().endsWith(".js"))
				out.add(path + "/" + F.getName());
		}
	}

	private static File fileOf(final String path)
	{
		return new File(WorldFiles.worldDir(), path);
	}

	/** @return the files changed since the game started, or since they were last reloaded */
	public static List<String> changed()
	{
		final List<String> out = new ArrayList<String>();
		synchronized(seen)
		{
			for(final String p : allFiles())
			{
				final long t = fileOf(p).lastModified();
				final Long was = seen.get(p);
				if((was == null) ? (t > started) : (t != was.longValue()))
					out.add(p);
			}
		}
		return out;
	}

	/**
	 * A file named as a builder names it: js/props/x.js, props/x.js,
	 * world:js/props/x.js or world/js/props/x.js.
	 * @param name the name
	 * @return the js/... path, or null if there is no such file
	 */
	public static String named(final String name)
	{
		String n = name.trim().replace('\\', '/');
		if(n.toLowerCase().startsWith("world:"))
			n = n.substring(6);
		if(n.toLowerCase().startsWith("world/"))
			n = n.substring(6);
		if(!n.toLowerCase().startsWith("js/"))
			n = "js/" + n;
		if(!n.toLowerCase().endsWith(".js"))
			n = n + ".js";
		if(n.indexOf("..") >= 0)
			return null;
		return fileOf(n).isFile() ? n : null;
	}

	/**
	 * JS RELOAD.
	 * @param which a file, ALL, or null for the changed ones
	 * @return what was done, a line each
	 */
	public static List<String> reload(final String which)
	{
		final List<String> report = new ArrayList<String>();
		final List<String> files;
		if((which == null) || (which.trim().length() == 0))
			files = changed();
		else
		if(which.trim().equalsIgnoreCase("ALL"))
			files = allFiles();
		else
		{
			final String p = named(which);
			if(p == null)
			{
				report.add("There is no file world/" + (which.toLowerCase().startsWith("js/") ? which : ("js/" + which)) + ".");
				return report;
			}
			files = new ArrayList<String>();
			files.add(p);
		}
		if(files.isEmpty())
		{
			report.add("No JavaScript file in world/js has changed.");
			return report;
		}
		boolean libs = false;
		for(final String p : files)
		{
			final File F = fileOf(p);
			final String problem = compileProblem(p, F);
			synchronized(seen)
			{
				seen.put(p, Long.valueOf(F.lastModified()));
			}
			if(problem != null)
			{
				report.add("world/" + problem + " (not reloaded)");
				continue;
			}
			final String[] parts = p.split("/");
			final String folder = (parts.length > 2) ? parts[1] : "";
			if(p.equalsIgnoreCase("js/init.js"))
				report.add("world/js/init.js read again" + (WorldJS.reload() ? ", and its onReload called." : "."));
			else
			if(folder.equals("lib"))
			{
				libs = true;
				report.add("world/" + p + ": a library, read again where it is required from now on.");
			}
			else
			if(JSScope.CLASS_FOLDERS.contains(folder))
				report.add(reloadClass(p, folder));
			else
			{
				final int n = reloadHolders("world:" + p);
				report.add("world/" + p + ": read again by " + n + " " + ((n == 1) ? "property or behaviour" : "properties and behaviours") + " that run it.");
			}
		}
		if(libs)
			JSScope.reload();
		return report;
	}

	private static String compileProblem(final String p, final File F)
	{
		final Context cx = Context.enter();
		try
		{
			cx.compileString(new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8"), "world/" + p, 1, null);
			return null;
		}
		catch(final RhinoException e)
		{
			return p + ":" + e.lineNumber() + ": " + e.details();
		}
		catch(final java.io.IOException e)
		{
			return p + ": cannot be read";
		}
		finally
		{
			Context.exit();
		}
	}

	private static CMClass.CMObjectType typeOf(final String folder)
	{
		switch(folder)
		{
		case "behaviors":
			return CMClass.CMObjectType.BEHAVIOR;
		case "abilities":
		case "properties":
			return CMClass.CMObjectType.ABILITY;
		case "items":
			return CMClass.CMObjectType.ITEM;
		case "mobs":
			return CMClass.CMObjectType.MOB;
		case "races":
			return CMClass.CMObjectType.RACE;
		case "charclasses":
			return CMClass.CMObjectType.CHARCLASS;
		case "commands":
			return CMClass.CMObjectType.COMMAND;
		case "common":
			return CMClass.CMObjectType.COMMON;
		case "locales":
			return CMClass.CMObjectType.LOCALE;
		case "exits":
			return CMClass.CMObjectType.EXIT;
		case "areas":
			return CMClass.CMObjectType.AREA;
		default:
			return null;
		}
	}

	private static String reloadClass(final String p, final String folder)
	{
		final CMClass.CMObjectType type = typeOf(folder);
		final String name = p.substring(p.lastIndexOf('/') + 1, p.length() - 3);
		if((type == null) || !CMClass.loadClass(type, "world/" + p, false))
			return "world/" + p + ": the class could not be loaded again (see the log).";
		final Object proto = CMClass.getObjectOrPrototype(name);
		if(!(proto instanceof CMObject))
			return "world/" + p + ": loaded, but there is no class " + name + " now.";
		final String id = ((CMObject)proto).ID();
		final Class<?> now = proto.getClass();
		final int[] n = { 0 }, refused = { 0 };
		everything(P ->
		{
			if(proto instanceof Behavior)
			{
				final List<Behavior> old = new ArrayList<Behavior>();
				for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
				{
					final Behavior B = b.nextElement();
					if((B != null) && B.ID().equals(id) && (B.getClass() != now))
						old.add(B);
				}
				for(final Behavior B : old)
				{
					final Behavior N = (Behavior)((CMObject)proto).newInstance();
					N.setParms(B.getParms());
					final String state;
					try
					{
						state = B.getStat("STATE");
						N.setStat("STATE", state);
						if(state != null && !state.isEmpty() && N.getStat("STATE").isEmpty())
							throw new IllegalArgumentException("new class cannot accept saved behavior state");
					}
					catch(final RuntimeException e) { refused[0]++; Log.warnOut("JSReload",id+" on "+P.Name()+": kept old instance: "+e.getMessage()); continue; }
					P.delBehavior(B);
					P.addBehavior(N);
					N.setStat("STATE",state);
					n[0]++;
				}
			}
			else
			if(proto instanceof Ability)
			{
				final List<Ability> old = new ArrayList<Ability>();
				for(final Enumeration<Ability> a = P.effects(); a.hasMoreElements();)
				{
					final Ability A = a.nextElement();
					if((A != null) && A.ID().equals(id) && (A.getClass() != now))
						old.add(A);
				}
				for(final Ability A : old)
				{
					final Ability N = (Ability)((CMObject)proto).newInstance();
					try { migrateAbility(A,N); }
					catch(final RuntimeException e) { refused[0]++; Log.warnOut("JSReload",id+" on "+P.Name()+": kept old effect: "+e.getMessage()); continue; }
					final boolean lasting = !A.canBeUninvoked();
					final boolean savedFlag = A.isSavable();
					final Map<String,String> states=new LinkedHashMap<String,String>();
					for(final String code:new String[]{"STATE","ACTIVITY"})
					{
						final String value=A.getStat(code);if(value!=null&&!value.isEmpty())states.put(code,value);
					}
					final int ticks = CMath.s_int(A.getStat("TICKDOWN"));
					final boolean wasTicking=CMLib.threads().isTicking(A,Tickable.TICKID_MOB);
					P.delEffect(A);
					A.setAffectedOne(null);
					CMLib.threads().deleteTick(A,-1);
					if(lasting)
						P.addNonUninvokableEffect(N);
					else
					{
						P.addEffect(N);
					}
					for(final Map.Entry<String,String> saved:states.entrySet())N.setStat(saved.getKey(),saved.getValue());
					N.setStat("TICKDOWN",Integer.toString(ticks));
					if(!(P instanceof MOB)&&(ticks>0||wasTicking))CMLib.threads().startTickDown(N,Tickable.TICKID_MOB,1);
					N.setSavable(savedFlag);
					P.recoverPhyStats();
					if(P instanceof MOB){((MOB)P).recoverCharStats();((MOB)P).recoverMaxState();}
					else if(P instanceof Room)((Room)P).recoverRoomStats();
					n[0]++;
				}
				if(P instanceof MOB)
				{
					final MOB M = (MOB)P;
					final Ability A = M.fetchAbility(id);
					if((A != null) && (A.getClass() != now))
					{
						final Ability N = (Ability)((CMObject)proto).newInstance();
						try { migrateAbility(A,N); }
						catch(final RuntimeException e) { refused[0]++; Log.warnOut("JSReload",id+" on "+P.Name()+": kept old learned ability: "+e.getMessage()); return; }
						M.delAbility(A);
						M.addAbility(N);
						n[0]++;
					}
				}
			}
		});
		if((proto instanceof Behavior) || (proto instanceof Ability))
			return "world/" + p + ": class " + id + " compiled again; " + n[0] + " in the world replaced; " + refused[0] + " incompatible instances kept (see log).";
		return "world/" + p + ": class " + id + " compiled again; those made from now on use it, those in the world keep the old code until they are made again.";
	}

	/** Only state declared through the engine's public state APIs can migrate automatically. */
	public static void migrateAbility(final Ability from, final Ability to)
	{
		to.setMiscText(from.text()); to.setInvoker(from.invoker()); to.setProficiency(from.proficiency()); to.setSavable(from.isSavable());
		for(final String code : new String[]{"STATE","ACTIVITY"})
		{
			final String state = from.getStat(code);
			if(state != null && !state.isEmpty())
			{
				to.setStat(code,state);
				if(to.getStat(code).isEmpty()) throw new IllegalArgumentException("new class cannot accept " + code);
			}
		}
		to.setStat("TICKDOWN",from.getStat("TICKDOWN"));
		to.setStat("CANUNINVOKE",Boolean.toString(from.canBeUninvoked()));
		to.setStat("NEXTCAST",from.getStat("NEXTCAST"));
	}

	/**
	 * Has each Prop_JScript and JSBehavior in the world that runs a file read it again.
	 * @param script the file, as SCRIPT= names it (world:js/...)
	 * @return how many
	 */
	public static int reloadHolders(final String script)
	{
		final int[] n = { 0 };
		everything(P ->
		{
			final List<Holder> found = new ArrayList<Holder>();
			for(final Enumeration<Ability> a = P.effects(); a.hasMoreElements();)
			{
				final Ability A = a.nextElement();
				if((A instanceof Holder) && script.equalsIgnoreCase(((Holder)A).scriptPath()))
					found.add((Holder)A);
			}
			for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
			{
				final Behavior B = b.nextElement();
				if((B instanceof Holder) && script.equalsIgnoreCase(((Holder)B).scriptPath()))
					found.add((Holder)B);
			}
			for(final Holder H : found)
			{
				H.reloadScript();
				n[0]++;
			}
		});
		return n[0];
	}

	/**
	 * Every area, room, exit, mob and item in the world.
	 * @param each what to do with each
	 */
	public static void everything(final java.util.function.Consumer<PhysicalAgent> each)
	{
		for(final Enumeration<Area> a = CMLib.map().areas(); a.hasMoreElements();)
		{
			final Area A = a.nextElement();
			if(A != null)
				each.accept(A);
		}
		final Set<Exit> exits = new HashSet<Exit>();
		for(final Enumeration<Room> r = CMLib.map().rooms(); r.hasMoreElements();)
		{
			final Room R = r.nextElement();
			if(R == null)
				continue;
			each.accept(R);
			for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
			{
				final Exit E = R.getRawExit(d);
				if((E != null) && exits.add(E))
					each.accept(E);
			}
			for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
			{
				final MOB M = m.nextElement();
				if(M == null)
					continue;
				each.accept(M);
				for(final Enumeration<Item> i = M.items(); i.hasMoreElements();)
				{
					final Item I = i.nextElement();
					if(I != null)
						each.accept(I);
				}
			}
			for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
			{
				final Item I = i.nextElement();
				if(I != null)
					each.accept(I);
			}
		}
	}

	/**
	 * JS WATCH ON: looks at world/js every few seconds, and has the builder
	 * JS RELOAD what changed.
	 * @param mob the builder
	 * @param every seconds between looks
	 */
	public static synchronized void watch(final MOB mob, final int every)
	{
		unwatch();
		builder = mob;
		seconds = Math.max(1, every);
		synchronized(seen)
		{
			for(final String p : changed())
				seen.put(p, Long.valueOf(fileOf(p).lastModified()));
		}
		final Thread T = new Thread(new Runnable()
		{
			@Override
			public void run()
			{
				while(watcher == Thread.currentThread())
				{
					try
					{
						Thread.sleep(seconds * 1000L);
					}
					catch(final InterruptedException e)
					{
						return;
					}
					final MOB M = builder;
					if((M == null) || M.amDestroyed() || (watcher != Thread.currentThread()))
						return;
					try
					{
						if(!changed().isEmpty())
							M.enqueCommand(CMParms.parse("JS RELOAD"), 0, 0.0);
					}
					catch(final Exception e)
					{
						Log.errOut("JSReload", e);
					}
				}
			}
		}, "JSWatch");
		T.setDaemon(true);
		watcher = T;
		T.start();
	}

	/** JS WATCH OFF */
	public static synchronized void unwatch()
	{
		final Thread T = watcher;
		watcher = null;
		builder = null;
		if(T != null)
			T.interrupt();
	}

	/** @return true while JS WATCH is on */
	public static boolean isWatching()
	{
		return watcher != null;
	}

	/** @return seconds between JS WATCH's looks */
	public static int watchSeconds()
	{
		return seconds;
	}
}
