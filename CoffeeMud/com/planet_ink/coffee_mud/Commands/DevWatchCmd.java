package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.File;
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
 * B5, for builders: DEVWATCH ON [seconds] looks at the world files and the
 * scripts in resources/ (and the world's own resources/) every few seconds,
 * and when one changes outside the game queues the reload that matches for
 * the builder, on the game thread: WORLD RELOAD CHANGED for world files
 * (as WORLD STATUS lists them), WORLD RELOAD SCRIPT file for a script.
 * Each is noted in DEVLOG.  DEVWATCH OFF stops it, DEVWATCH alone says what
 * it does.
 */
public class DevWatchCmd extends StdCommand
{
	public DevWatchCmd()
	{
	}

	private final String[] access = I(new String[] { "DEVWATCH" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	private static volatile Thread					watcher	= null;
	private static volatile MOB						builder	= null;
	private static volatile int						seconds	= 3;
	/** each script file's time, by its path as LOAD= names it */
	private static final Map<String, Long>			times	= new HashMap<String, Long>();

	private static void scripts(final File dir, final String prefix, final Map<String, Long> into, final int depth)
	{
		final File[] fs = dir.listFiles();
		if((fs == null) || (depth > 12))
			return;
		for(final File F : fs)
		{
			if(F.isDirectory())
			{
				if(!F.getName().startsWith("."))
					scripts(F, prefix + F.getName() + "/", into, depth + 1);
			}
			else
			{
				final String n = F.getName().toLowerCase();
				if(n.endsWith(".script") || n.endsWith(".js"))
				{
					final String path = prefix + F.getName();
					final Long was = into.get(path);
					into.put(path, Long.valueOf(Math.max(F.lastModified(), (was == null) ? 0L : was.longValue())));
				}
			}
		}
	}

	/** the script files, the stock ones and the world's own copies, by their paths after resources/ */
	private static Map<String, Long> scriptTimes()
	{
		final Map<String, Long> now = new HashMap<String, Long>();
		scripts(new File("resources"), "", now, 0);
		final File world = WorldFiles.worldDir();
		if(world != null)
			scripts(new File(world, "resources"), "", now, 0);
		return now;
	}

	/**
	 * The reloads what changed since the last look calls for.
	 * @return the commands, each a WORLD RELOAD
	 */
	public static List<String> pending()
	{
		final List<String> cmds = new ArrayList<String>();
		boolean world = false;
		for(final String how : WorldFiles.changedOutside().values())
		{
			if(!how.equals("deleted"))
				world = true;
		}
		if(world)
			cmds.add("WORLD RELOAD CHANGED");
		final Map<String, Long> now = scriptTimes();
		synchronized(times)
		{
			for(final Map.Entry<String, Long> e : now.entrySet())
			{
				final Long was = times.get(e.getKey());
				if((was != null) && (!was.equals(e.getValue())))
					cmds.add("WORLD RELOAD SCRIPT " + e.getKey());
			}
			times.clear();
			times.putAll(now);
		}
		return cmds;
	}

	/**
	 * Starts watching, for a builder.
	 * @param mob the builder
	 * @param every how many seconds between looks
	 */
	public static synchronized void start(final MOB mob, final int every)
	{
		stop();
		builder = mob;
		seconds = Math.max(1, every);
		synchronized(times)
		{
			times.clear();
			times.putAll(scriptTimes());
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
					if((M == null) || (M.amDestroyed()) || (watcher != Thread.currentThread()))
						return;
					try
					{
						for(final String cmd : pending())
						{
							Log.sysOut("DevWatch", "a file changed: " + cmd);
							M.enqueCommand(CMParms.parse(cmd), 0, 0.0);
						}
					}
					catch(final Exception e)
					{
						Log.errOut("DevWatch", e);
					}
				}
			}
		}, "DevWatch");
		T.setDaemon(true);
		watcher = T;
		T.start();
	}

	/**
	 * Stops watching.
	 */
	public static synchronized void stop()
	{
		final Thread T = watcher;
		watcher = null;
		builder = null;
		if(T != null)
			T.interrupt();
	}

	/**
	 * Whether it is watching.
	 * @return true if it is
	 */
	public static boolean isOn()
	{
		return watcher != null;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final String w = (commands.size() > 1) ? commands.get(1).toUpperCase() : "";
		if(w.equals("ON"))
		{
			final int every = ((commands.size() > 2) && CMath.isInteger(commands.get(2))) ? CMath.s_int(commands.get(2)) : 3;
			start(mob, every);
			mob.tell(L("DEVWATCH looks at the world files and resources' scripts every @x1 seconds, and loads again what changes outside the game (DEVLOG notes each).", "" + Math.max(1, every)));
			return false;
		}
		if(w.equals("OFF"))
		{
			stop();
			mob.tell(L("DEVWATCH is off."));
			return false;
		}
		mob.tell(isOn()
			? L("DEVWATCH is on, every @x1 seconds.  DEVWATCH OFF stops it.", "" + seconds)
			: L("DEVWATCH is off.  DEVWATCH ON ([SECONDS]) loads again, as they change, the world files and resources' scripts."));
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
		return SaveGame.isBuilderMode() || CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
