package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Libraries.interfaces.JournalsLibrary;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

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
 * B3: scripts, descriptions and quest scripts edited a line at a time: in
 * the line editor (the journals' message maker), or, with EDITOR= in the
 * ini, in a window (WINDOW) or in the builder's own editor (EXTERNAL:
 * EDITORCOMMAND= in the ini, or $VISUAL or $EDITOR, on a temporary file).
 * A script or quest script all on one line is shown a statement to a line,
 * split at its unescaped ';', and joined so again; one with line breaks
 * keeps them.  A script is checked by SCRIPTCHECK when it is saved, and a
 * quest script by QUESTCHECK, and the builder may edit it again, keep it,
 * or cancel.
 */
public final class TextEditor
{
	private TextEditor()
	{
	}

	/** what is edited: how it is split into lines, and how it is checked */
	public enum Form
	{
		/** text: a line to each line, unchecked */
		TEXT,
		/** a MOBPROG script, checked by SCRIPTCHECK */
		SCRIPT,
		/** a quest script, checked by QUESTCHECK */
		QUEST
	}

	private static String normal(final String text)
	{
		return text.replace("\r\n", "\n").replace("\n\r", "\n").replace('\r', '\n');
	}

	/** whether a text is kept on one line, its statements split at ';' */
	private static boolean oneLine(final String text, final Form form)
	{
		return (form != Form.TEXT) && (text.indexOf('\n') < 0) && (text.indexOf('\r') < 0);
	}

	/**
	 * The lines a text is edited as.
	 * @param text the text
	 * @param form what it is
	 * @return its lines, none for no text
	 */
	public static List<String> toLines(final String text, final Form form)
	{
		final List<String> lines = new ArrayList<String>();
		if((text == null) || (text.length() == 0))
			return lines;
		if(!oneLine(text, form))
		{
			lines.addAll(Arrays.asList(normal(text).split("\n", -1)));
			if((lines.size() > 1) && (lines.get(lines.size() - 1).length() == 0))
				lines.remove(lines.size() - 1);
			return lines;
		}
		// as the engine reads it: a ';' inside a <SCRIPT> block is the script's own
		final String upper = text.toUpperCase();
		boolean inScript = false;
		int start = 0;
		for(int i = 0; i < text.length(); i++)
		{
			if(text.charAt(i) == '<')
			{
				if((!inScript) && upper.startsWith("<SCRIPT>", i))
					inScript = true;
				else
				if(inScript && upper.startsWith("</SCRIPT>", i))
					inScript = false;
			}
			if((!inScript) && (text.charAt(i) == ';') && ((i == 0) || (text.charAt(i - 1) != '\\')))
			{
				lines.add(text.substring(start, i));
				start = i + 1;
			}
		}
		if(start < text.length())
			lines.add(text.substring(start));
		return lines;
	}

	/**
	 * A text from its edited lines, kept as the old one was: on one line,
	 * joined with ';', or a line to each line.
	 * @param lines the lines
	 * @param old the text before it was edited
	 * @param form what it is
	 * @return the text
	 */
	public static String fromLines(final List<String> lines, final String old, final Form form)
	{
		final String was = (old == null) ? "" : old;
		if(oneLine(was, form))
		{
			final String joined = String.join(";", lines);
			// a script on one line ends with its ';', as such scripts are written
			if((lines.size() > 0) && ((was.length() == 0) || was.endsWith(";")))
				return joined + ";";
			return joined;
		}
		if(lines.isEmpty())
			return "";
		return String.join("\n", lines) + (normal(was).endsWith("\n") ? "\n" : "");
	}

	/**
	 * A script on one line as a file holds it: a statement to a line.
	 * @param script the script
	 * @return its text, with line breaks
	 */
	public static String asFile(final String script)
	{
		final List<String> lines = toLines(script, Form.SCRIPT);
		final StringBuilder str = new StringBuilder();
		for(final String line : lines)
			str.append(line.trim()).append("\n");
		return str.toString();
	}

	/**
	 * The problems a check finds in what was edited.
	 * @param title what to call it
	 * @param text the text
	 * @param form what it is
	 * @return the problems, none for text
	 */
	public static List<String> problems(final String title, final String text, final Form form)
	{
		if((text == null) || (text.trim().length() == 0))
			return new ArrayList<String>();
		switch(form)
		{
		case SCRIPT:
			return ScriptCheck.checkText(title, text, null);
		case QUEST:
			return QuestCheck.checkText(title, text);
		default:
			return new ArrayList<String>();
		}
	}

	/**
	 * Which editor EDITOR= in the ini asks for.
	 * @return LINE, WINDOW or EXTERNAL
	 */
	public static String editorMode()
	{
		final String s = CMProps.instance().getStr("EDITOR", "").trim().toUpperCase();
		if(s.startsWith("W"))
			return "WINDOW";
		if(s.startsWith("E"))
			return "EXTERNAL";
		return "LINE";
	}

	/**
	 * The command EDITOR=EXTERNAL runs: EDITORCOMMAND= in the ini, or
	 * $VISUAL or $EDITOR.
	 * @return its words, or null if there is none
	 */
	public static List<String> externalCommand()
	{
		String cmd = CMProps.instance().getStr("EDITORCOMMAND", "").trim();
		if(cmd.length() == 0)
			cmd = (System.getenv("VISUAL") == null) ? "" : System.getenv("VISUAL").trim();
		if(cmd.length() == 0)
			cmd = (System.getenv("EDITOR") == null) ? "" : System.getenv("EDITOR").trim();
		if(cmd.length() == 0)
			return null;
		return new ArrayList<String>(Arrays.asList(cmd.split("\\s+")));
	}

	/**
	 * Edits a text, and checks it if it is a script.
	 * @param mob the builder
	 * @param title what is edited, as the editor names it
	 * @param text the text now
	 * @param form what it is
	 * @return the new text, or null if it was not saved
	 * @throws IOException if the session fails
	 */
	public static String edit(final MOB mob, final String title, final String text, final Form form) throws IOException
	{
		if((mob == null) || (mob.session() == null))
			return null;
		final Session sess = mob.session();
		String current = (text == null) ? "" : text;
		while(!sess.isStopped())
		{
			final String edited = editOnce(mob, title, current, form);
			if(edited == null)
				return null;
			final List<String> found = problems(title, edited, form);
			if(found.isEmpty())
				return edited;
			final StringBuilder str = new StringBuilder(CMLib.lang().L("@x1 has problems:", title));
			for(final String p : found)
				str.append("\n\r  ").append(p);
			mob.tell(str.toString());
			final String c = sess.choose(CMLib.lang().L("Edit it again, keep it as it is, or cancel (E/k/c)? "), "EKC", "E");
			if(c.equalsIgnoreCase("K"))
				return edited;
			if(c.equalsIgnoreCase("C"))
				return null;
			current = edited;
		}
		return null;
	}

	/**
	 * An edit of a field, as the generic editor shows one: its number, name
	 * and start, then the editor if it is the field chosen.
	 * @param mob the builder
	 * @param old the value now
	 * @param showNumber the field's number
	 * @param showFlag the field chosen, or as the generic editor takes it
	 * @param label the field's name
	 * @param form what it is
	 * @return the new value, or the old one
	 * @throws IOException if the session fails
	 */
	public static String field(final MOB mob, final String old, final int showNumber, final int showFlag, final String label, final Form form) throws IOException
	{
		final String was = (old == null) ? "" : old;
		if((showFlag > 0) && (showFlag != showNumber))
			return was;
		final String flat = CMStrings.removeCRLF(was);
		mob.tell(((showNumber == 0) ? "   " : (showNumber + ". ")) + label + ": '" + CMStrings.limit(flat, 50) + ((flat.length() > 50) ? "..." : "") + "'.");
		if((showFlag != showNumber) && (showFlag > -999))
			return was;
		final String edited = edit(mob, label, was, form);
		if(edited == null)
		{
			mob.tell(CMLib.lang().L("(no change)"));
			return was;
		}
		return edited;
	}

	private static String editOnce(final MOB mob, final String title, final String text, final Form form) throws IOException
	{
		String mode = editorMode();
		if(mode.equals("WINDOW") && java.awt.GraphicsEnvironment.isHeadless())
		{
			mob.tell(CMLib.lang().L("EDITOR=WINDOW needs a display, so the line editor it is."));
			mode = "LINE";
		}
		if(mode.equals("EXTERNAL") && (externalCommand() == null))
		{
			mob.tell(CMLib.lang().L("EDITOR=EXTERNAL needs EDITORCOMMAND= in the ini, or $VISUAL or $EDITOR, so the line editor it is."));
			mode = "LINE";
		}
		if(mode.equals("WINDOW"))
			return editWindow(title, text);
		if(mode.equals("EXTERNAL"))
			return editExternal(mob, title, text);
		final List<String> vbuf = toLines(text, form);
		final JournalsLibrary.MsgMkrResolution res = CMLib.journals().makeMessage(mob, title, vbuf, vbuf.isEmpty());
		if(res != JournalsLibrary.MsgMkrResolution.SAVEFILE)
			return null;
		return fromLines(vbuf, text, form);
	}

	/** EDITOR=EXTERNAL: the builder's own editor on a temporary file, the game waiting for it */
	private static String editExternal(final MOB mob, final String title, final String text) throws IOException
	{
		final File F = File.createTempFile("cmedit-", ".txt");
		try
		{
			Files.write(F.toPath(), text.getBytes(StandardCharsets.UTF_8));
			final long was = F.lastModified();
			final List<String> cmd = externalCommand();
			cmd.add(F.getAbsolutePath());
			mob.tell(CMLib.lang().L("Editing @x1 in @x2; the game waits for it to close.", title, cmd.get(0)));
			final Process P = new ProcessBuilder(cmd).inheritIO().start();
			final int exit = P.waitFor();
			if(exit != 0)
			{
				mob.tell(CMLib.lang().L("@x1 ended with @x2, so nothing changed.", cmd.get(0), "" + exit));
				return null;
			}
			final String now = new String(Files.readAllBytes(F.toPath()), StandardCharsets.UTF_8);
			if(now.equals(text) && (F.lastModified() == was))
				return null;
			return now;
		}
		catch(final InterruptedException e)
		{
			Thread.currentThread().interrupt();
			return null;
		}
		finally
		{
			F.delete();
		}
	}

	/** EDITOR=WINDOW: a window with the text, Save and Cancel, the game waiting for it */
	private static String editWindow(final String title, final String text)
	{
		final String[] result = new String[1];
		try
		{
			javax.swing.SwingUtilities.invokeAndWait(new Runnable()
			{
				@Override
				public void run()
				{
					final javax.swing.JTextArea area = new javax.swing.JTextArea(text, 24, 80);
					area.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 13));
					final javax.swing.JScrollPane pane = new javax.swing.JScrollPane(area);
					final Object[] options = { "Save", "Cancel" };
					final int choice = javax.swing.JOptionPane.showOptionDialog(null, pane, title,
						javax.swing.JOptionPane.DEFAULT_OPTION, javax.swing.JOptionPane.PLAIN_MESSAGE, null, options, options[0]);
					result[0] = (choice == 0) ? area.getText() : null;
				}
			});
		}
		catch(final Exception e)
		{
			Log.errOut("TextEditor", e);
			return null;
		}
		return result[0];
	}
}
