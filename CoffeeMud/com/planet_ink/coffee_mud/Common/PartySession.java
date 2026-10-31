package com.planet_ink.coffee_mud.Common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.CharCreationLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * The session of one of the player's characters who is in the world but not
 * being played: a party member, or one left in the world.  Nothing is read
 * from it and what it would show goes nowhere; the player gives the
 * character commands with AS, or plays them with SWITCH, which swaps the
 * terminal's session with this one.  See {@link com.planet_ink.coffee_mud.core.Party}.
 */
public class PartySession extends DefaultSession
{
	@Override
	public String ID()
	{
		return "PartySession";
	}

	@Override
	public boolean initialize(final Socket s, final String groupName)
	{
		return super.initialize(makeSocket(), groupName);
	}

	/**
	 * Returns what the session reads from and writes to: here, nothing.
	 * @return the socket
	 */
	protected Socket makeSocket()
	{
		return new PartySocket();
	}

	@Override
	protected boolean openHandshake() throws IOException
	{
		return true;
	}

	/**
	 * Brings one of the player's characters into the world on this session.
	 * The game makes these sessions only for the player's own characters, so
	 * there is no password.
	 * @param name the character's name
	 * @param password not used
	 * @return true if the character is in the world
	 */
	@Override
	public boolean autoLogin(final String name, final String password)
	{
		if(name==null)
			return false;
		final MOB mob=CMLib.players().getLoadPlayer(CMStrings.capitalizeAndLower(name));
		if((mob==null)||(mob.playerStats()==null))
			return false;
		setMob(mob);
		try
		{
			if(CMLib.login().completePlayerLogin(this,false)!=CharCreationLibrary.LoginResult.NORMAL_LOGIN)
			{
				setMob(null);
				return false;
			}
		}
		catch(final IOException e)
		{
			setMob(null);
			return false;
		}
		return setLoggedInState(CharCreationLibrary.LoginResult.NORMAL_LOGIN);
	}

	/**
	 * A socket with nothing behind it.  Only the methods that DefaultSession
	 * calls are meaningful.
	 */
	protected static class PartySocket extends Socket
	{
		private volatile boolean	closed	= false;

		private final InputStream	in		= new InputStream()
		{
			@Override
			public int available()
			{
				return 0;
			}

			@Override
			public int read()
			{
				return -1;
			}
		};

		private final OutputStream	out		= new OutputStream()
		{
			@Override
			public void write(final int b)
			{
			}

			@Override
			public void write(final byte[] b, final int off, final int len)
			{
			}
		};

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
			return !closed;
		}

		@Override
		public boolean isClosed()
		{
			return closed;
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
}
