package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.LinkedBlockingDeque;

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
 * Standard input, read by one background thread for the life of the process
 * so that sessions can poll it without blocking.  Every console session
 * reads from the same queue: when the engine restarts to restore a saved
 * game, the new session takes up whatever the player typed ahead.
 *
 * This lives in core, which the system class loader loads once; the session
 * classes are loaded again at each boot.
 *
 * Once standard input ends and every byte has been read, the input reports
 * itself finished, after a short grace period so that the output of the last
 * command is not cut off.
 */
public final class ConsoleInput extends InputStream
{
	private static final long							GRACE_MS	= 1500;
	private static final LinkedBlockingDeque<Integer>	bytes		= new LinkedBlockingDeque<Integer>();
	private static volatile long						eofTime		= 0;
	private static volatile long						lastReadTime= System.currentTimeMillis();
	private static Thread								pump		= null;

	private static final ConsoleInput	instance	= new ConsoleInput();

	private ConsoleInput()
	{
	}

	/**
	 * Returns the console's input, starting the thread that reads standard
	 * input the first time.
	 * @return the input
	 */
	public static synchronized ConsoleInput instance()
	{
		if(pump == null)
		{
			pump = new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						int b;
						while((b = System.in.read()) >= 0)
							bytes.add(Integer.valueOf(b));
					}
					catch(final IOException e)
					{
					}
					eofTime = System.currentTimeMillis();
				}
			}, "ConsoleInput");
			pump.setDaemon(true);
			pump.start();
		}
		lastReadTime = System.currentTimeMillis();
		return instance;
	}

	/**
	 * Returns whether standard input has ended and been read to the end.
	 * @return true if finished
	 */
	public boolean isFinished()
	{
		return (eofTime != 0)
			&& bytes.isEmpty()
			&& (System.currentTimeMillis() - Math.max(eofTime, lastReadTime) > GRACE_MS);
	}

	@Override
	public int available()
	{
		return bytes.size();
	}

	@Override
	public int read()
	{
		final Integer b = bytes.poll();
		if(b == null)
			return -1;
		lastReadTime = System.currentTimeMillis();
		return b.intValue();
	}

	@Override
	public int read(final byte[] buf, final int off, final int len)
	{
		if(len == 0)
			return 0;
		int n = 0;
		Integer b;
		while((n < len) && ((b = bytes.poll()) != null))
			buf[off + n++] = (byte)b.intValue();
		if(n > 0)
			lastReadTime = System.currentTimeMillis();
		return (n == 0) ? -1 : n;
	}

	@Override
	public void close()
	{
		// standard input outlives any one session
	}
}
