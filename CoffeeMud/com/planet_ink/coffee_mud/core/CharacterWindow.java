package com.planet_ink.coffee_mud.core;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

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
 * A window of the player's for one of their characters: what the character
 * sees, scrolling, in colour, and a line to type their commands into, with
 * the lines typed before a key away.  A session reads what is typed from
 * {@link #input()} and writes to {@link #output()}, as it would a socket;
 * the output may hold the colour codes of an ANSI terminal, and telnet
 * negotiation, which is dropped.  A panel beside the text can show what
 * {@link #setPanel(String)} gives it, such as the character's state, a map
 * and the party (see {@link WindowPanel}).  See Common/WindowSession.
 */
public final class CharacterWindow
{
	/** how much text the window keeps */
	private static final int		MAX_CHARS		= 400000;
	/** the colour of text, and of the window */
	private static final Color		TEXT			= new Color(204, 204, 204);
	private static final Color		BACKGROUND		= new Color(16, 16, 16);
	/** the colour of what the player types, shown with what it answers */
	private static final Color		TYPED			= new Color(120, 160, 120);
	/** the colour of the panel */
	private static final Color		PANEL_BACKGROUND	= new Color(28, 28, 34);
	/** the eight colours of an ANSI terminal, and their bright forms */
	private static final Color[]	NORMAL			=
	{
		new Color(0, 0, 0), new Color(205, 0, 0), new Color(0, 205, 0), new Color(205, 205, 0),
		new Color(70, 110, 255), new Color(205, 0, 205), new Color(0, 205, 205), new Color(229, 229, 229)
	};
	private static final Color[]	BRIGHT			=
	{
		new Color(127, 127, 127), new Color(255, 70, 70), new Color(70, 255, 70), new Color(255, 255, 70),
		new Color(120, 150, 255), new Color(255, 70, 255), new Color(70, 255, 255), new Color(255, 255, 255)
	};
	/** how many have opened, so that each opens a little apart from the last */
	private static int				opened			= 0;

	/** how many characters wide the panel is */
	private static final int		PANEL_COLUMNS	= 34;

	private JFrame					frame;
	private JTextPane				text;
	private JTextField				line;
	private JTextPane				panel;
	private JScrollPane				panelScroll;
	private final Queue<Integer>	typed			= new ConcurrentLinkedQueue<Integer>();
	private final List<String>		history			= new ArrayList<String>();
	private int						historyAt		= 0;
	private volatile boolean		closed			= false;
	private final Charset			inCharset;
	private final CharsetDecoder	decoder;
	private final Runnable			onClose;

	// the state of what is being written: telnet, then escape codes and colour
	private int						telnet			= 0;
	private final Painter			painter			= new Painter();
	private ByteBuffer				leftover		= ByteBuffer.allocate(0);

	private CharacterWindow(final String inCharset, final String outCharset, final Runnable onClose)
	{
		this.inCharset = charset(inCharset);
		this.decoder = charset(outCharset).newDecoder()
			.onMalformedInput(CodingErrorAction.REPLACE)
			.onUnmappableCharacter(CodingErrorAction.REPLACE);
		this.onClose = onClose;
	}

	private static Charset charset(final String name)
	{
		try
		{
			return Charset.forName(name);
		}
		catch(final Exception e)
		{
			return Charset.forName("ISO-8859-1");
		}
	}

	/**
	 * Returns whether windows can open here: whether there is a display.
	 * @return true if they can
	 */
	public static boolean canOpen()
	{
		return !GraphicsEnvironment.isHeadless();
	}

	/**
	 * Opens a window.
	 * @param title the window's title
	 * @param inCharset the character set to send what is typed in
	 * @param outCharset the character set of what is written to it
	 * @param onClose what to do when the player closes it
	 * @return the window, or null if it could not open
	 */
	public static CharacterWindow open(final String title, final String inCharset, final String outCharset, final Runnable onClose)
	{
		if(!canOpen())
			return null;
		final CharacterWindow W = new CharacterWindow(inCharset, outCharset, onClose);
		try
		{
			SwingUtilities.invokeAndWait(new Runnable()
			{
				@Override
				public void run()
				{
					W.build(title);
				}
			});
			return W;
		}
		catch(final Throwable e)
		{
			// a display that is set but cannot be reached throws an Error
			Log.errOut("CharacterWindow", e.toString());
			return null;
		}
	}

	private void build(final String title)
	{
		frame = new JFrame(title);
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		final Font font = new Font(Font.MONOSPACED, Font.PLAIN, 13);
		text = new JTextPane();
		text.setEditable(false);
		text.setFont(font);
		text.setBackground(BACKGROUND);
		text.setForeground(TEXT);
		text.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		line = new JTextField();
		line.setFont(font);
		line.setBackground(BACKGROUND);
		line.setForeground(TEXT);
		line.setCaretColor(TEXT);
		line.addActionListener(e -> enter());
		line.addKeyListener(new KeyAdapter()
		{
			@Override
			public void keyPressed(final KeyEvent e)
			{
				if((e.getKeyCode() == KeyEvent.VK_UP) && (historyAt > 0))
					line.setText(history.get(--historyAt));
				else
				if((e.getKeyCode() == KeyEvent.VK_DOWN) && (historyAt < history.size()))
					line.setText((++historyAt < history.size()) ? history.get(historyAt) : "");
			}
		});
		panel = new JTextPane();
		panel.setEditable(false);
		panel.setFocusable(false);
		panel.setFont(font);
		panel.setBackground(PANEL_BACKGROUND);
		panel.setForeground(TEXT);
		panel.setBorder(BorderFactory.createEmptyBorder(4, 6, 4, 6));
		panelScroll = new JScrollPane(panel);
		final int columnWidth = panel.getFontMetrics(font).charWidth('m');
		panelScroll.setPreferredSize(new Dimension((columnWidth * PANEL_COLUMNS) + 16, 0));
		panelScroll.setVisible(false);
		frame.getContentPane().setLayout(new BorderLayout());
		frame.getContentPane().add(new JScrollPane(text), BorderLayout.CENTER);
		frame.getContentPane().add(panelScroll, BorderLayout.EAST);
		frame.getContentPane().add(line, BorderLayout.SOUTH);
		frame.addWindowListener(new WindowAdapter()
		{
			@Override
			public void windowClosing(final WindowEvent e)
			{
				if(onClose != null)
					onClose.run();
			}

			@Override
			public void windowActivated(final WindowEvent e)
			{
				line.requestFocusInWindow();
			}
		});
		frame.setSize(760, 520);
		synchronized(CharacterWindow.class)
		{
			frame.setLocation(60 + (40 * (opened % 8)), 60 + (40 * (opened % 8)));
			opened++;
		}
		frame.setVisible(true);
		line.requestFocusInWindow();
	}

	/**
	 * The player pressed enter: what they typed goes to the session, and is
	 * shown with what it answers.
	 */
	private void enter()
	{
		final String typedLine = line.getText();
		line.setText("");
		if(typedLine.trim().length() > 0)
		{
			history.add(typedLine);
			while(history.size() > 200)
				history.remove(0);
		}
		historyAt = history.size();
		final SimpleAttributeSet attrs = new SimpleAttributeSet();
		StyleConstants.setForeground(attrs, TYPED);
		append(typedLine + "\n", attrs);
		for(final byte b : (typedLine + "\n").getBytes(inCharset))
			typed.add(Integer.valueOf(b & 0xff));
	}

	/**
	 * Sets the window's title.
	 * @param title the title
	 */
	public void setTitle(final String title)
	{
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				if(frame != null)
					frame.setTitle(title);
			}
		});
	}

	/**
	 * Shows text in the panel beside the window's text, in place of what it
	 * showed, or hides the panel.  The text may hold the colour codes of an
	 * ANSI terminal, as what is written to the window may.
	 * @param ansi the text, or null to hide the panel
	 */
	public void setPanel(final String ansi)
	{
		final List<Object[]> runs = new ArrayList<Object[]>();
		if(ansi != null)
		{
			final Painter P = new Painter();
			P.paint(CharBuffer.wrap(ansi), runs);
		}
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				if((frame == null) || (panel == null))
					return;
				final boolean wasVisible = panelScroll.isVisible();
				if(ansi == null)
					panelScroll.setVisible(false);
				else
				{
					final StyledDocument doc = panel.getStyledDocument();
					try
					{
						doc.remove(0, doc.getLength());
						for(final Object[] r : runs)
							doc.insertString(doc.getLength(), (String)r[0], (SimpleAttributeSet)r[1]);
					}
					catch(final BadLocationException e)
					{
					}
					panel.setCaretPosition(0);
					panelScroll.setVisible(true);
				}
				if(wasVisible != panelScroll.isVisible())
					frame.getContentPane().revalidate();
			}
		});
	}

	/**
	 * Returns the text the panel shows, without its colours, once what was
	 * given it before has been shown; or null if the panel is hidden.
	 * @return the text, or null
	 */
	public String panelShown()
	{
		final String[] shown = new String[1];
		try
		{
			SwingUtilities.invokeAndWait(new Runnable()
			{
				@Override
				public void run()
				{
					if((panelScroll != null) && panelScroll.isVisible())
						shown[0] = panel.getText();
				}
			});
		}
		catch(final Exception e)
		{
			return null;
		}
		return shown[0];
	}

	/**
	 * Closes the window.
	 */
	public void close()
	{
		closed = true;
		SwingUtilities.invokeLater(new Runnable()
		{
			@Override
			public void run()
			{
				if(frame != null)
					frame.dispose();
			}
		});
	}

	/**
	 * Returns whether the window has been closed.
	 * @return true if it has
	 */
	/** Makes a utility window display-only. */
	public void setInputEnabled(final boolean enabled)
	{
		SwingUtilities.invokeLater(() -> line.setEnabled(enabled));
	}

	public boolean isClosed()
	{
		return closed;
	}

	/**
	 * Returns what the player types into the window, a byte at a time,
	 * without ever blocking: nothing is available until they press enter.
	 * @return the stream
	 */
	public InputStream input()
	{
		return new InputStream()
		{
			@Override
			public int available()
			{
				return typed.size();
			}

			@Override
			public int read()
			{
				final Integer b = typed.poll();
				return (b == null) ? -1 : b.intValue();
			}

			@Override
			public int read(final byte[] buf, final int off, final int len)
			{
				if(len == 0)
					return 0;
				int n = 0;
				Integer b;
				while((n < len) && ((b = typed.poll()) != null))
					buf[off + n++] = (byte)b.intValue();
				return (n == 0) ? -1 : n;
			}
		};
	}

	/**
	 * Returns the stream that shows what is written to it in the window.
	 * @return the stream
	 */
	public OutputStream output()
	{
		return new OutputStream()
		{
			@Override
			public void write(final int b)
			{
				write(new byte[] { (byte)b }, 0, 1);
			}

			@Override
			public void write(final byte[] buf, final int off, final int len)
			{
				show(buf, off, len);
			}
		};
	}

	/**
	 * Shows bytes written to the window: telnet negotiation is dropped, the
	 * rest decoded, and ANSI colour codes turned into colour.
	 */
	private synchronized void show(final byte[] buf, final int off, final int len)
	{
		if(closed)
			return;
		final ByteBuffer bytes = ByteBuffer.allocate(leftover.remaining() + len);
		bytes.put(leftover);
		for(int i = off; i < off + len; i++)
		{
			final int b = buf[i] & 0xff;
			switch(telnet)
			{
			case 0:
				if(b == 255)
					telnet = 1;
				else
					bytes.put((byte)b);
				break;
			case 1: // after IAC
				if(b == 255)
				{
					bytes.put((byte)b);
					telnet = 0;
				}
				else
				if((b >= 251) && (b <= 254))
					telnet = 2;
				else
				if(b == 250)
					telnet = 3;
				else
					telnet = 0;
				break;
			case 2: // the option a WILL, WONT, DO or DONT is about
				telnet = 0;
				break;
			case 3: // a subnegotiation, until IAC SE
				if(b == 255)
					telnet = 4;
				break;
			default:
				telnet = (b == 240) ? 0 : 3;
				break;
			}
		}
		bytes.flip();
		final CharBuffer chars = CharBuffer.allocate((int)Math.ceil(bytes.remaining() * decoder.maxCharsPerByte()) + 1);
		decoder.decode(bytes, chars, false);
		leftover = ByteBuffer.allocate(bytes.remaining());
		leftover.put(bytes);
		leftover.flip();
		chars.flip();
		final List<Object[]> runs = new ArrayList<Object[]>();
		painter.paint(chars, runs);
		if(runs.size() > 0)
		{
			SwingUtilities.invokeLater(new Runnable()
			{
				@Override
				public void run()
				{
					for(final Object[] r : runs)
						append((String)r[0], (SimpleAttributeSet)r[1]);
				}
			});
		}
	}

	/**
	 * Turns text with the escape codes of an ANSI terminal into runs of text,
	 * each with its colour.  The colour carries over from one call to the
	 * next, as in a terminal.
	 */
	private static final class Painter
	{
		private int						escape			= 0;
		private final StringBuilder		codes			= new StringBuilder();
		private int						fgIndex			= -1;
		private Color					fgColor			= null;
		private Color					bgColor			= null;
		private boolean					bold			= false;

		/**
		 * Adds the runs of the given text to a list, each an array of its text
		 * and its attributes.
		 */
		void paint(final CharBuffer chars, final List<Object[]> runs)
		{
			final StringBuilder run = new StringBuilder();
			while(chars.hasRemaining())
			{
				final char c = chars.get();
				switch(escape)
				{
				case 0:
					if(c == 27)
					{
						flush(run, runs);
						escape = 1;
					}
					else
					if((c == '\n') || (c == '\t') || (c >= ' '))
						run.append(c);
					break;
				case 1: // after ESC
					escape = (c == '[') ? 2 : 0;
					codes.setLength(0);
					break;
				default: // in a CSI sequence, until its final character
					if((c >= 0x40) && (c <= 0x7e))
					{
						if(c == 'm')
							setGraphics(codes.toString());
						escape = 0;
					}
					else
						codes.append(c);
					break;
				}
			}
			flush(run, runs);
		}

		/**
		 * Keeps the text so far, in the colour so far, to be shown.
		 */
		private void flush(final StringBuilder run, final List<Object[]> runs)
		{
			if(run.length() == 0)
				return;
			final SimpleAttributeSet attrs = new SimpleAttributeSet();
			Color fg = fgColor;
			if((fg == null) && (fgIndex >= 0))
				fg = bold ? BRIGHT[fgIndex] : NORMAL[fgIndex];
			StyleConstants.setForeground(attrs, (fg == null) ? (bold ? Color.WHITE : TEXT) : fg);
			if(bgColor != null)
				StyleConstants.setBackground(attrs, bgColor);
			StyleConstants.setBold(attrs, bold);
			runs.add(new Object[] { run.toString(), attrs });
			run.setLength(0);
		}

		/**
		 * Applies an ANSI select-graphic-rendition code: colours, in 8, 16, 256
		 * or true colour, and bold.
		 * @param params the code's numbers, separated by semicolons
		 */
		private void setGraphics(final String params)
		{
			final String[] p = (params.length() == 0) ? new String[] { "0" } : params.split(";", -1);
			for(int i = 0; i < p.length; i++)
			{
				final int n = CMath.s_int(p[i]);
				if(n == 0)
				{
					fgIndex = -1;
					fgColor = null;
					bgColor = null;
					bold = false;
				}
				else
				if(n == 1)
					bold = true;
				else
				if(n == 22)
					bold = false;
				else
				if((n >= 30) && (n <= 37))
				{
					fgIndex = n - 30;
					fgColor = null;
				}
				else
				if((n >= 90) && (n <= 97))
				{
					fgIndex = -1;
					fgColor = BRIGHT[n - 90];
				}
				else
				if(n == 39)
				{
					fgIndex = -1;
					fgColor = null;
				}
				else
				if((n >= 40) && (n <= 47))
					bgColor = NORMAL[n - 40];
				else
				if((n >= 100) && (n <= 107))
					bgColor = BRIGHT[n - 100];
				else
				if(n == 49)
					bgColor = null;
				else
				if(((n == 38) || (n == 48)) && (i + 1 < p.length))
				{
					Color C = null;
					if(CMath.s_int(p[i + 1]) == 5 && (i + 2 < p.length))
					{
						C = xterm(CMath.s_int(p[i + 2]));
						i += 2;
					}
					else
					if(CMath.s_int(p[i + 1]) == 2 && (i + 4 < p.length))
					{
						C = new Color(clamp(p[i + 2]), clamp(p[i + 3]), clamp(p[i + 4]));
						i += 4;
					}
					if(n == 38)
					{
						fgIndex = -1;
						fgColor = C;
					}
					else
						bgColor = C;
				}
			}
		}
	}

	private static int clamp(final String s)
	{
		return Math.max(0, Math.min(255, CMath.s_int(s)));
	}

	/**
	 * Returns one of the 256 colours of an xterm.
	 * @param i its number
	 * @return the colour
	 */
	private static Color xterm(final int i)
	{
		if((i >= 0) && (i < 8))
			return NORMAL[i];
		if((i >= 8) && (i < 16))
			return BRIGHT[i - 8];
		if((i >= 16) && (i < 232))
		{
			final int[] level = { 0, 95, 135, 175, 215, 255 };
			final int c = i - 16;
			return new Color(level[c / 36], level[(c / 6) % 6], level[c % 6]);
		}
		if((i >= 232) && (i < 256))
		{
			final int g = 8 + (10 * (i - 232));
			return new Color(g, g, g);
		}
		return TEXT;
	}

	/**
	 * Adds text at the end of the window, keeping it scrolled to the end and
	 * no longer than it keeps.  On the Swing thread.
	 */
	private void append(final String s, final SimpleAttributeSet attrs)
	{
		if(text == null)
			return;
		final StyledDocument doc = text.getStyledDocument();
		try
		{
			doc.insertString(doc.getLength(), s, attrs);
			if(doc.getLength() > MAX_CHARS)
				doc.remove(0, doc.getLength() - ((MAX_CHARS * 3) / 4));
		}
		catch(final BadLocationException e)
		{
		}
		text.setCaretPosition(doc.getLength());
	}
}
