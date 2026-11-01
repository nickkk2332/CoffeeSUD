package com.planet_ink.coffee_mud.Common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.Socket;

import com.planet_ink.coffee_mud.core.*;
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
 * played on the terminal, shown in a window of its own (see
 * {@link CharacterWindow}): the window shows what the character sees, and
 * what is typed into it is the character's commands.  Closing the window
 * sends the character back to rest.  Used in place of a PartySession while
 * windows are on (see {@link Party#setWindows(boolean)}); with no display,
 * it is a PartySession.
 */
public class WindowSession extends PartySession
{
	private CharacterWindow	window	= null;
	/** draws the window's panel again, when the panel's sections change */
	private final Runnable	panelRefresh	= new Runnable()
	{
		@Override
		public void run()
		{
			refreshPanel();
		}
	};

	@Override
	public String ID()
	{
		return "WindowSession";
	}

	@Override
	protected Socket makeSocket()
	{
		final WindowSession me = this;
		window = CharacterWindow.open("CoffeeSUD",
			CMProps.getVar(CMProps.Str.CHARSETINPUT), CMProps.getVar(CMProps.Str.CHARSETOUTPUT),
			new Runnable()
			{
				@Override
				public void run()
				{
					// the player closed the window, on the window's own thread
					CMLib.threads().executeRunnable(new Runnable()
					{
						@Override
						public void run()
						{
							final MOB M = me.mob();
							if(M != null)
								Party.rest(M);
							else
								me.stopSession(true, false, true, false);
						}
					});
				}
			});
		if(window == null)
			return super.makeSocket();
		WindowPanel.addWindow(panelRefresh);
		return new WindowSocket(window);
	}

	@Override
	protected boolean openHandshake() throws IOException
	{
		setServerTelnetMode(TELNET_ANSI,true);
		setClientTelnetMode(TELNET_ANSI,true);
		return true;
	}

	@Override
	public void setMob(final MOB newmob)
	{
		super.setMob(newmob);
		if(window != null)
			window.setTitle((newmob == null) ? "CoffeeSUD" : newmob.Name());
	}

	@Override
	public void showPrompt()
	{
		super.showPrompt();
		refreshPanel();
	}

	@Override
	public void stopSession(final boolean disconnect, final boolean removeMOB, final boolean dropSession, final boolean killThread)
	{
		WindowPanel.removeWindow(panelRefresh);
		super.stopSession(disconnect, removeMOB, dropSession, killThread);
	}

	/**
	 * Draws the window's panel again with what its sections show of the
	 * character now, in the window's colours, or hides it when the windows
	 * have no panel.
	 */
	protected void refreshPanel()
	{
		final CharacterWindow W = window;
		if((W == null) || W.isClosed())
			return;
		final String which = WindowPanel.sections();
		final MOB M = mob();
		if((which.length() == 0) || (M == null))
		{
			W.setPanel(null);
			return;
		}
		final String text = WindowPanel.text(M, which);
		W.setPanel(CMLib.coffeeFilter().fullOutFilter(this, M, M, M, null, text, false));
	}

	/**
	 * Returns the window of this session's character, or null if it has none.
	 * @return the window
	 */
	public CharacterWindow window()
	{
		return window;
	}

	/**
	 * A socket that is really a window.  Only the methods that
	 * DefaultSession calls are meaningful.
	 */
	protected static class WindowSocket extends Socket
	{
		private final CharacterWindow	window;
		private final InputStream		in;
		private final OutputStream		out;
		private volatile boolean		closed	= false;

		public WindowSocket(final CharacterWindow window)
		{
			this.window = window;
			this.in = window.input();
			this.out = window.output();
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
			window.close();
		}

		@Override
		public void shutdownInput()
		{
			close();
		}

		@Override
		public void shutdownOutput()
		{
			close();
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
