package com.planet_ink.coffee_mud.Common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.net.InetAddress;
import java.net.Socket;
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
 * The single-player session: a DefaultSession whose "connection" is the
 * process's own standard input and output.  No telnet negotiation is sent,
 * and any telnet commands the session writes later are filtered out before
 * they reach the terminal.  When standard input ends, the connection reports
 * itself closed, so the session logs the player out and saves them.
 */
public class ConsoleSession extends DefaultSession
{
	@Override
	public String ID()
	{
		return "ConsoleSession";
	}

	@Override
	public boolean initialize(final Socket s, final String groupName)
	{
		return super.initialize(new ConsoleSocket(System.in, System.out), groupName);
	}

	@Override
	protected boolean openHandshake() throws IOException
	{
		setServerTelnetMode(TELNET_ANSI,true);
		setClientTelnetMode(TELNET_ANSI,true);
		return true;
	}

	/**
	 * A socket that is really the console.  Only the methods that
	 * DefaultSession calls are meaningful.
	 */
	protected static class ConsoleSocket extends Socket
	{
		private final ConsoleInput	in;
		private final ConsoleOutput	out;
		private volatile boolean	closed	= false;

		public ConsoleSocket(final InputStream stdin, final PrintStream stdout)
		{
			in = new ConsoleInput(stdin);
			out = new ConsoleOutput(stdout);
		}

		@Override
		public InputStream getInputStream()
		{
			return in;
		}

		@Override
		public OutputStream getOutputStream()
		{
			return out;
		}

		@Override
		public boolean isConnected()
		{
			return !isClosed();
		}

		@Override
		public boolean isClosed()
		{
			return closed || in.isFinished();
		}

		@Override
		public synchronized void close()
		{
			closed = true;
		}

		@Override
		public void shutdownInput()
		{
			closed = true;
		}

		@Override
		public void shutdownOutput()
		{
			closed = true;
		}

		@Override
		public InetAddress getInetAddress()
		{
			return InetAddress.getLoopbackAddress();
		}

		@Override
		public synchronized void setSoTimeout(final int timeout)
		{
		}

		@Override
		public void setSoLinger(final boolean on, final int linger)
		{
		}

		@Override
		public void setTcpNoDelay(final boolean on)
		{
		}

		@Override
		public void setKeepAlive(final boolean on)
		{
		}
	}

	/**
	 * Standard input, read by a background thread so that the session can
	 * poll it without blocking.  Once standard input ends and every byte has
	 * been consumed, the stream reports itself finished, after a short grace
	 * period so that the output of the last command is not cut off.
	 */
	protected static class ConsoleInput extends InputStream
	{
		private static final long				GRACE_MS	= 1500;
		private final LinkedBlockingDeque<Integer>	bytes		= new LinkedBlockingDeque<Integer>();
		private volatile long					eofTime		= 0;
		private volatile long					lastReadTime= System.currentTimeMillis();

		public ConsoleInput(final InputStream stdin)
		{
			final Thread pump = new Thread(new Runnable()
			{
				@Override
				public void run()
				{
					try
					{
						int b;
						while((b = stdin.read()) >= 0)
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
	}

	/**
	 * Standard output with telnet commands, NUL keep-alives and carriage
	 * returns removed.  Closing it never closes the real standard output.
	 */
	protected static class ConsoleOutput extends OutputStream
	{
		private static final int	IAC	= 255;
		private static final int	SB	= 250;
		private static final int	SE	= 240;
		private static final int	WILL= 251;
		private static final int	DONT= 254;

		private final PrintStream	stdout;
		private int					state	= 0; // 0 text, 1 after IAC, 2 option byte, 3 subnegotiation, 4 IAC in subnegotiation

		public ConsoleOutput(final PrintStream stdout)
		{
			this.stdout = stdout;
		}

		@Override
		public synchronized void write(final int i)
		{
			final int b = i & 0xff;
			switch(state)
			{
			case 0:
				if(b == IAC)
					state = 1;
				else
				if((b != 0) && (b != '\r'))
					stdout.write(b);
				break;
			case 1:
				if(b == IAC)
				{
					stdout.write(b);
					state = 0;
				}
				else
				if((b >= WILL) && (b <= DONT))
					state = 2;
				else
				if(b == SB)
					state = 3;
				else
					state = 0;
				break;
			case 2:
				state = 0;
				break;
			case 3:
				if(b == IAC)
					state = 4;
				break;
			case 4:
				state = (b == SE) ? 0 : 3;
				break;
			}
		}

		@Override
		public void flush()
		{
			stdout.flush();
		}

		@Override
		public void close()
		{
			stdout.flush();
		}
	}
}
