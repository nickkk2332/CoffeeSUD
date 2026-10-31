package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import org.mozilla.javascript.Context;
import org.mozilla.javascript.ContextFactory;
import org.mozilla.javascript.RhinoException;

import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.core.save.SaveGame;

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
 * The JavaScript runtime that every script runs in: a time limit, so that a
 * runaway loop cannot freeze the game's one thread, and a record of errors
 * with where they happened, shown to the builder in builder mode and listed
 * by JS ERRORS.
 * <p>
 * The limit comes from JSTIMELIMIT in the ini (seconds, default 2); JRUN
 * gives its scripts JSTIMELIMIT_JRUN (default 30).  It is installed once, as
 * Rhino's global context factory, so it covers every Context.enter(): script
 * blocks, JRUN, JCONSOLE, quests and cron jobs.  JavaScript classes
 * (//extends) are compiled to Java bytecode, which instruction counting
 * cannot see, so they have no limit.  Scripts are ES6.  This class is loaded
 * once per process, so it survives the engine's restarts.
 */
public final class JSRuntime
{
	/** most errors kept for JS ERRORS */
	private static final int			MAX_ERRORS		= 50;

	private static volatile boolean		installed		= false;
	private static final ThreadLocal<Integer> limitOverride = new ThreadLocal<Integer>();
	private static final LinkedList<String> errors		= new LinkedList<String>();

	private JSRuntime()
	{
	}

	/**
	 * Thrown when a script runs past its time limit.  Scripts cannot catch it:
	 * Rhino lets a JavaScript catch take only its own errors, so only the
	 * script's finally blocks run on the way out.
	 */
	public static class TimeLimitExceeded extends RuntimeException
	{
		private static final long serialVersionUID = 1L;

		public TimeLimitExceeded(final int seconds)
		{
			super("it ran longer than its time limit of "+seconds+" second"+((seconds==1)?"":"s"));
		}
	}

	private static class TimedContext extends Context
	{
		private final long	deadline;
		private final int	seconds;

		private TimedContext(final ContextFactory factory, final int seconds)
		{
			super(factory);
			this.seconds = seconds;
			this.deadline = System.currentTimeMillis() + (seconds * 1000L);
		}
	}

	private static class TimedFactory extends ContextFactory
	{
		@Override
		protected Context makeContext()
		{
			final TimedContext cx = new TimedContext(this, limitSeconds());
			// modern JavaScript: arrow functions, template strings, let and const
			cx.setLanguageVersion(Context.VERSION_ES6);
			// instruction counting needs the interpreter
			cx.setOptimizationLevel(-1);
			cx.setInstructionObserverThreshold(10000);
			return cx;
		}

		@Override
		protected void observeInstructionCount(final Context cx, final int instructionCount)
		{
			if((cx instanceof TimedContext)
			&&(System.currentTimeMillis() > ((TimedContext)cx).deadline))
				throw new TimeLimitExceeded(((TimedContext)cx).seconds);
		}
	}

	/**
	 * Installs the timed runtime as Rhino's global context factory, once.
	 */
	public static synchronized void install()
	{
		if(installed)
			return;
		installed = true;
		if(!ContextFactory.hasExplicitGlobal())
			ContextFactory.initGlobal(new TimedFactory());
	}

	/**
	 * The time limit for a script started now on this thread, in seconds.
	 * @return the limit
	 */
	public static int limitSeconds()
	{
		final Integer over = limitOverride.get();
		if(over != null)
			return over.intValue();
		return setting("JSTIMELIMIT", 2);
	}

	/**
	 * The time limit JRUN gives its scripts, in seconds.
	 * @return the limit
	 */
	public static int jrunLimitSeconds()
	{
		return setting("JSTIMELIMIT_JRUN", 30);
	}

	private static int setting(final String key, final int dflt)
	{
		final CMProps props = CMProps.instance();
		if(props == null)
			return dflt;
		final String s = props.getStr(key, "");
		if((s == null) || (s.trim().length() == 0) || (!CMath.isInteger(s.trim())))
			return dflt;
		return Math.max(1, CMath.s_int(s.trim()));
	}

	/**
	 * Enters a JavaScript context with the given time limit, instead of the
	 * usual one, as JRUN does.  Exit it with Context.exit() as usual.
	 * @param seconds the limit
	 * @return the context
	 */
	public static Context enter(final int seconds)
	{
		limitOverride.set(Integer.valueOf(seconds));
		try
		{
			return Context.enter();
		}
		finally
		{
			limitOverride.remove();
		}
	}

	/**
	 * Describes a script error for a builder: where it is, the line, and what
	 * went wrong, with the line's text when Rhino has it.
	 * @param where where the script is, such as "mob 'Brenna' @ Town#4 SPEECH_PROG"
	 * @param e what went wrong
	 * @return the description
	 */
	public static String describe(final String where, final Throwable e)
	{
		final StringBuilder str = new StringBuilder("JS error in ").append(where);
		if(e instanceof TimeLimitExceeded)
			return str.append(": ").append(e.getMessage()).toString();
		Throwable cause = e;
		while((cause != null) && (!(cause instanceof RhinoException)) && (cause.getCause() != null) && (cause.getCause() != cause))
			cause = cause.getCause();
		if(cause instanceof RhinoException)
		{
			final RhinoException re = (RhinoException)cause;
			if(re.lineNumber() > 0)
				str.append(" line ").append(re.lineNumber());
			str.append(": ").append(re.details());
			final String src = re.lineSource();
			if((src != null) && (src.trim().length() > 0))
				str.append("\n\r    ").append(src.trim());
		}
		else
			str.append(": ").append((e == null) ? "unknown error" : (e.getMessage() == null) ? e.toString() : e.getMessage());
		return str.toString();
	}

	/**
	 * Records a script error: in the log, in the list JS ERRORS shows, and, in
	 * builder mode, on the builder's screen.
	 * @param where where the script is
	 * @param e what went wrong
	 * @return the description recorded
	 */
	public static String report(final String where, final Throwable e)
	{
		final String desc = describe(where, e);
		Log.errOut("JavaScript", desc.replace("\n\r", " "));
		synchronized(errors)
		{
			errors.addLast(CMLib.time().date2String(System.currentTimeMillis()) + "  " + desc);
			while(errors.size() > MAX_ERRORS)
				errors.removeFirst();
		}
		if(SaveGame.isBuilderMode())
		{
			for(final Session S : CMLib.sessions().allIterable())
			{
				final MOB M = S.mob();
				if(M != null)
					M.tell("^R" + desc + "^?");
			}
		}
		return desc;
	}

	/**
	 * The most recent script errors, oldest first.
	 * @param max most to return
	 * @return the errors
	 */
	public static List<String> recentErrors(final int max)
	{
		synchronized(errors)
		{
			final List<String> list = new ArrayList<String>(errors);
			if(list.size() > max)
				return list.subList(list.size() - max, list.size());
			return list;
		}
	}
}
