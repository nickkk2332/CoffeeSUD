package com.planet_ink.coffee_mud.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.mozilla.javascript.*;
import org.mozilla.javascript.json.JsonParser;
import com.planet_ink.coffee_mud.core.interfaces.PhysicalAgent;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Bounded background work with JSON-only transfer and game-thread completion. Not campaign state. */
public final class JSJobs
{
	private static final ThreadFactory daemon=task->
	{
		final Thread t=new Thread(task,"javascript-background");
		t.setDaemon(true);
		return t;
	};
	private static final ExecutorService workers=new ThreadPoolExecutor(2,2,0,TimeUnit.SECONDS,new ArrayBlockingQueue<Runnable>(32),daemon,
		new ThreadPoolExecutor.AbortPolicy());
	private static final ScheduledExecutorService deadlines=Executors.newSingleThreadScheduledExecutor(daemon);
	private static final Set<Job> jobs=Collections.newSetFromMap(new ConcurrentHashMap<Job,Boolean>());
	private static volatile long generation;
	private JSJobs()
	{
	}

	public static final class Job
	{
		private final AtomicBoolean finished=new AtomicBoolean();
		private final long epoch=generation;
		private final Function callback;
		private final Scriptable scope;
		private final PhysicalAgent host;
		private volatile Future<?> work;
		private volatile ScheduledFuture<?> deadline;
		private volatile String error="",json="null";
		private volatile boolean completed;
		private Job(final Function callback,final Scriptable scope,final PhysicalAgent host)
		{
			this.callback=callback;
			this.scope=scope;
			this.host=host;
		}
		public boolean done()
		{
			return completed;
		}
		public String error()
		{
			return error;
		}
		public String resultJSON()
		{
			return json;
		}
		public void cancel()
		{
			finish("cancelled","null");
		}
		private void finish(final String error,final String json)
		{
			if(!finished.compareAndSet(false,true))
				return;
			this.error=error;
			this.json=json;
			completed=true;
			final ScheduledFuture<?> timer=deadline;
			if(timer!=null)
				timer.cancel(false);
			if(!error.isEmpty()&&work!=null)
				work.cancel(true);
			jobs.remove(this);
			if(epoch!=generation)
				return;
			CMLib.threads().executeRunnable(()->
			{
				if(epoch!=generation||host!=null&&host.amDestroyed())
					return;
				final Context cx=JSRuntime.enter(JSRuntime.limitSeconds());
				try
				{
					callback.call(cx,scope,scope,new Object[]{error.isEmpty()?null:error,new JsonParser(cx,scope).parseValue(json)});
				}
				catch(final Exception e)
				{
					JSRuntime.report("cm.background callback",e);
				}
				finally
				{
					Context.exit();
				}
			});
		}
	}

	public static Job start(final Function function,final Object data,final int timeoutMs,final Function callback,final Scriptable scope,
		final PhysicalAgent host)
	{
		if(function==null||callback==null)
			throw new IllegalArgumentException("cm.background needs a worker and a completion function");
		if(timeoutMs<1||timeoutMs>300000)
			throw new IllegalArgumentException("cm.background timeout must be 1..300000 milliseconds");
		final Context caller=Context.getCurrentContext();
		final Object encoded=NativeJSON.stringify(caller,scope,data,null,null);
		if(!(encoded instanceof String))
			throw new IllegalArgumentException("cm.background data must be JSON");
		final String source=Context.toString(function),input=(String)encoded;
		final Job job=new Job(callback,scope,host);
		jobs.add(job);
		try
		{
			job.work=workers.submit(()->
			{
				if(job.done())
					return;
				final Context cx=JSRuntime.enter(Math.max(1,(timeoutMs+999)/1000));
				try
				{
					// Fresh scope: no cm, closure, game objects or shared script globals.
					cx.setClassShutter(name->!name.startsWith("com.planet_ink.")&&!name.startsWith("org.mozilla."));
					final Scriptable isolated=cx.initStandardObjects();
					final Function worker=(Function)cx.evaluateString(isolated,"("+source+")","cm.background worker",1,null);
					final Object value=worker.call(cx,isolated,isolated,new Object[]{new JsonParser(cx,isolated).parseValue(input)});
					final Object result=NativeJSON.stringify(cx,isolated,value,null,null);
					job.finish("",result instanceof String?(String)result:"null");
				}
				catch(final Throwable e)
				{
					job.finish(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(),"null");
				}
				finally
				{
					Context.exit();
				}
			});
			job.deadline=deadlines.schedule(()->job.finish("timed out after "+timeoutMs+" ms","null"),timeoutMs,TimeUnit.MILLISECONDS);
			if(job.done())
				job.deadline.cancel(false);
		}
		catch(final RejectedExecutionException e)
		{
			job.finish("background queue is full","null");
		}
		return job;
	}

	/** Old-world completions can never mutate a newly restored game. */
	public static void reset()
	{
		generation++;
		for(final Job job:jobs)
			job.cancel();
		jobs.clear();
	}
}
