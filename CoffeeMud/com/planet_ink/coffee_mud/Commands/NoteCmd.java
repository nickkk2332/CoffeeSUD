package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
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
 * DT26: NOTE text, in any game, writes a playtest note to notes/playtest.txt,
 * outside the world and the saves, so that a note made in play reaches
 * builder mode: the date, the save, the character, the room and the game time
 * with it.  NOTE HERE text adds the room as it is.  NOTES lists them
 * numbered; NOTES DONE n marks one done, NOTES UNDONE n takes that back, and
 * NOTES GOTO n (builder mode or DEV=1) goes to its room.
 */
public class NoteCmd extends StdCommand
{
	public NoteCmd()
	{
	}

	private final String[] access = I(new String[] { "NOTE", "NOTES" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/** the file the notes are kept in, from the game's folder */
	public static final String NOTES_FILE = "notes/playtest.txt";

	private static File notesFile = null;

	/**
	 * The notes' file: NOTESFILE= in the ini, or notes/playtest.txt.
	 * @return the file
	 */
	public static synchronized File notesFile()
	{
		if(notesFile == null)
		{
			final String name = CMProps.instance().getStr("NOTESFILE", "").trim();
			notesFile = new File((name.length() > 0) ? name : NOTES_FILE);
		}
		return notesFile;
	}

	/**
	 * Sets the notes' file, as a test does.
	 * @param F the file, or null for the ini's
	 */
	public static synchronized void setNotesFile(final File F)
	{
		notesFile = F;
	}

	/**
	 * One note as the file keeps it: a first line "[ ] header | text", then
	 * lines starting with two spaces (NOTE HERE's room).
	 */
	public static class Note
	{
		/** whether it is done */
		public boolean		done;
		/** the first line, after the box */
		public String		head;
		/** the lines after */
		public List<String>	more	= new ArrayList<String>();

		/**
		 * The room ID the note was written in.
		 * @return the ID, or ""
		 */
		public String roomID()
		{
			final String[] parts = head.split(" \\| ");
			for(final String p : parts)
			{
				if(p.startsWith("room "))
				{
					final String r = p.substring(5).trim();
					final int x = r.indexOf(" (");
					return (x > 0) ? r.substring(0, x) : r;
				}
			}
			return "";
		}
	}

	/**
	 * Reads the notes.
	 * @return the notes, oldest first
	 */
	public static synchronized List<Note> read()
	{
		final List<Note> notes = new ArrayList<Note>();
		final File F = notesFile();
		if(!F.isFile())
			return notes;
		try
		{
			Note N = null;
			for(final String line : Files.readAllLines(F.toPath(), StandardCharsets.UTF_8))
			{
				if(line.startsWith("[ ] ") || line.startsWith("[x] "))
				{
					N = new Note();
					N.done = line.startsWith("[x]");
					N.head = line.substring(4);
					notes.add(N);
				}
				else
				if((N != null) && (line.startsWith("  ")))
					N.more.add(line);
			}
		}
		catch(final IOException e)
		{
			Log.errOut("NoteCmd", e.getMessage());
		}
		return notes;
	}

	/**
	 * Writes the notes back.
	 * @param notes the notes
	 * @return null if written, else why not
	 */
	public static synchronized String write(final List<Note> notes)
	{
		final StringBuilder str = new StringBuilder();
		str.append("# Playtest notes (NOTE): [ ] to do, [x] done.  NOTES lists them, NOTES DONE n marks one.\n");
		for(final Note N : notes)
		{
			str.append(N.done ? "[x] " : "[ ] ").append(N.head).append("\n");
			for(final String m : N.more)
				str.append(m).append("\n");
		}
		final File F = notesFile();
		try
		{
			final File dir = F.getAbsoluteFile().getParentFile();
			if((dir != null) && (!dir.isDirectory()))
				dir.mkdirs();
			Files.write(F.toPath(), str.toString().getBytes(StandardCharsets.UTF_8));
			return null;
		}
		catch(final IOException e)
		{
			return e.getMessage();
		}
	}

	/**
	 * A room as NOTE HERE writes it down: what STAT ROOM would show of it.
	 * @param R the room
	 * @return the lines, each starting with two spaces
	 */
	public static List<String> describeRoom(final Room R)
	{
		final List<String> lines = new ArrayList<String>();
		lines.add("  " + R.ID() + " in " + R.getArea().Name() + ": " + CMStrings.removeColors(R.displayText()));
		for(int d = 0; d < Directions.NUM_DIRECTIONS(); d++)
		{
			final Room to = R.rawDoors()[d];
			final Exit E = R.getRawExit(d);
			if((to != null) || (E != null))
				lines.add("  exit " + CMLib.directions().getDirectionName(d).toLowerCase() + ": "
					+ ((E == null) ? "(no exit)" : E.ID()) + " to " + ((to == null) ? "nowhere" : CMLib.map().getExtendedRoomID(to)));
		}
		for(final Enumeration<MOB> m = R.inhabitants(); m.hasMoreElements();)
		{
			final MOB M = m.nextElement();
			if((M != null) && M.isMonster())
				lines.add("  mob " + M.name() + " (" + M.ID() + ", level " + M.phyStats().level() + ")");
		}
		for(final Enumeration<Item> i = R.items(); i.hasMoreElements();)
		{
			final Item I = i.nextElement();
			if((I != null) && (I.container() == null))
				lines.add("  item " + I.name() + " (" + I.ID() + ")");
		}
		for(final Enumeration<Ability> a = R.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if(A != null)
				lines.add("  effect " + A.ID() + ((A.text().length() > 0) ? " " + A.text() : ""));
		}
		for(final Enumeration<Behavior> b = R.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if(B != null)
				lines.add("  behaviour " + B.ID() + ((B.getParms().length() > 0) ? " " + B.getParms() : ""));
		}
		return lines;
	}

	/**
	 * Writes a note.
	 * @param mob who writes it
	 * @param text the note
	 * @param here true to add the room as it is
	 * @return the note's number, or 0 if it could not be written
	 */
	public static int note(final MOB mob, final String text, final boolean here)
	{
		final Room R = mob.location();
		final Note N = new Note();
		final StringBuilder head = new StringBuilder();
		head.append(new SimpleDateFormat("yyyy-MM-dd HH:mm").format(new Date()));
		head.append(" | ").append(SaveGame.isBuilderMode() ? "builder mode" : ("save " + ((SaveGame.currentSlot() == null) ? "(new game)" : SaveGame.currentSlot())));
		head.append(" | ").append(mob.Name());
		if(R != null)
		{
			head.append(" | room ").append(CMLib.map().getExtendedRoomID(R)).append(" (").append(CMStrings.removeColors(R.displayText())).append(")");
			head.append(" | ").append(R.getArea().getTimeObj().getShortTimeDescription());
		}
		head.append(" | ").append(text.replace('\n', ' ').replace('\r', ' ').trim());
		N.head = head.toString();
		if(here && (R != null))
			N.more.addAll(describeRoom(R));
		synchronized(NoteCmd.class)
		{
			final List<Note> notes = read();
			notes.add(N);
			if(write(notes) != null)
				return 0;
			return notes.size();
		}
	}

	private static boolean mayGo(final MOB mob)
	{
		return SaveGame.isBuilderMode() || SaveGame.isDevMode();
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final boolean list = commands.get(0).toUpperCase().startsWith("NOTES")
						|| ((commands.size() > 1) && commands.get(1).equalsIgnoreCase("LIST"));
		if(!list)
		{
			boolean here = false;
			int from = 1;
			if((commands.size() > 2) && commands.get(1).equalsIgnoreCase("HERE"))
			{
				here = true;
				from = 2;
			}
			final String text = CMParms.combine(commands, from).trim();
			if(text.length() == 0)
			{
				mob.tell(L("NOTE (HERE) [TEXT], NOTES (DONE/UNDONE/GOTO [NUMBER])"));
				return false;
			}
			final int n = note(mob, text, here);
			if(n == 0)
				mob.tell(L("The note could not be written to @x1.", notesFile().getPath()));
			else
				mob.tell(L("Note @x1 written to @x2.", "" + n, notesFile().getPath()));
			return false;
		}
		final int first = commands.get(0).toUpperCase().startsWith("NOTES") ? 1 : 2;
		final String what = (commands.size() > first) ? commands.get(first).toUpperCase() : "";
		final List<Note> notes = read();
		if(what.equals("DONE") || what.equals("UNDONE") || what.equals("GOTO"))
		{
			final int n = (commands.size() > first + 1) ? CMath.s_int(commands.get(first + 1)) : 0;
			if((n < 1) || (n > notes.size()))
			{
				mob.tell(L("There is no note @x1: there are @x2.", (commands.size() > first + 1) ? commands.get(first + 1) : "", "" + notes.size()));
				return false;
			}
			final Note N = notes.get(n - 1);
			if(what.equals("GOTO"))
			{
				if(!mayGo(mob))
				{
					mob.tell(L("NOTES GOTO is for builder mode and DEV=1 games."));
					return false;
				}
				final Room R = CMLib.map().getRoom(N.roomID());
				if(R == null)
				{
					mob.tell(L("Note @x1's room '@x2' is not in the world.", "" + n, N.roomID()));
					return false;
				}
				R.bringMobHere(mob, true);
				CMLib.commands().postLook(mob, true);
				return false;
			}
			N.done = what.equals("DONE");
			final String why = write(notes);
			if(why != null)
				mob.tell(L("The notes could not be written: @x1", why));
			else
				mob.tell(L("Note @x1 is @x2.", "" + n, N.done ? "done" : "to do again"));
			return false;
		}
		if(notes.isEmpty())
		{
			mob.tell(L("There are no notes in @x1.", notesFile().getPath()));
			return false;
		}
		final boolean all = what.equals("ALL");
		final StringBuilder str = new StringBuilder(L("Playtest notes in @x1:", notesFile().getPath()));
		int left = 0;
		for(int i = 0; i < notes.size(); i++)
		{
			final Note N = notes.get(i);
			if(!N.done)
				left++;
			if(N.done && !all)
				continue;
			str.append("\n\r").append(i + 1).append(". ").append(N.done ? "[x] " : "[ ] ").append(N.head);
			for(final String m : N.more)
				str.append("\n\r").append(m);
		}
		str.append("\n\r").append(L("@x1 to do, @x2 done@x3.", "" + left, "" + (notes.size() - left), all ? "" : " (NOTES ALL shows those too)"));
		mob.tell(str.toString());
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
		return true;
	}
}
