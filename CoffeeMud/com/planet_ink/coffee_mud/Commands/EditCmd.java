package com.planet_ink.coffee_mud.Commands;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.save.SaveGame;
import com.planet_ink.coffee_mud.Areas.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;

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
 * B3, for builders: EDIT SCRIPT target [N] opens a thing's Nth script (its
 * Scriptable behaviours', then its scripts) in the editor (core/TextEditor),
 * a statement to a line, checked by SCRIPTCHECK on save; a script that is
 * only LOAD=file opens the file, which every LOAD= of it shares.  EDIT DESC
 * target opens its description.  EXTRACT SCRIPT target [N] [file] moves an
 * inline script to resources/progs/NAME.script (the world's own copy, in
 * builder mode) and puts LOAD= of it in its place, for others to share.
 * The target is as SCRIPT takes it: a mob or item here or carried, HERE,
 * AREA, or name@room.
 */
public class EditCmd extends StdCommand
{
	public EditCmd()
	{
	}

	private final String[] access = I(new String[] { "EDIT", "EXTRACT" });

	@Override
	public String[] getAccessWords()
	{
		return access;
	}

	/**
	 * A script a thing holds: a Scriptable behaviour's parameters, or a
	 * script added to it.
	 */
	public static final class Held
	{
		/** the behaviour, or null for a script */
		public final Behavior			behavior;
		/** the engine */
		public final ScriptingEngine	engine;

		Held(final Behavior behavior, final ScriptingEngine engine)
		{
			this.behavior = behavior;
			this.engine = engine;
		}

		/**
		 * The script's text.
		 * @return the text
		 */
		public String text()
		{
			return (behavior != null) ? behavior.getParms() : engine.getScript();
		}

		/**
		 * Sets the script's text, which is read again.
		 * @param text the text
		 */
		public void set(final String text)
		{
			if(behavior != null)
				behavior.setParms(text);
			else
				engine.setScript(text);
		}
	}

	/**
	 * The scripts a thing holds, in the order EDIT SCRIPT numbers them: its
	 * Scriptable behaviours', then those added to it.
	 * @param P the thing
	 * @return the scripts
	 */
	public static List<Held> scriptsOf(final PhysicalAgent P)
	{
		final List<Held> held = new ArrayList<Held>();
		for(final Enumeration<Behavior> b = P.behaviors(); b.hasMoreElements();)
		{
			final Behavior B = b.nextElement();
			if((B instanceof ScriptingEngine) && B.isSavable())
				held.add(new Held(B, (ScriptingEngine)B));
		}
		for(final Enumeration<ScriptingEngine> s = P.scripts(); s.hasMoreElements();)
		{
			final ScriptingEngine S = s.nextElement();
			if((S != null) && S.isSavable())
				held.add(new Held(null, S));
		}
		return held;
	}

	/**
	 * The file of a script that is only LOAD=file.
	 * @param script the script
	 * @return the file's name, as LOAD= gives it, or null
	 */
	public static String loadOnly(final String script)
	{
		String s = (script == null) ? "" : script.trim();
		if(!s.toUpperCase().startsWith("LOAD="))
			return null;
		s = s.substring(5).trim();
		while(s.endsWith(";") || s.endsWith("~"))
			s = s.substring(0, s.length() - 1).trim();
		if((s.length() == 0) || (s.indexOf(';') >= 0) || (s.indexOf('~') >= 0) || (s.indexOf(' ') >= 0))
			return null;
		return s;
	}

	/**
	 * A file name made of a thing's name, as the world files make theirs.
	 * @param name the name
	 * @return lower case, letters and digits, dashes between
	 */
	public static String fileName(final String name)
	{
		final StringBuilder base = new StringBuilder();
		for(final char c : name.toLowerCase().toCharArray())
		{
			if(Character.isLetterOrDigit(c) && (c < 128))
				base.append(c);
			else
			if((base.length() > 0) && (base.charAt(base.length() - 1) != '-'))
				base.append('-');
		}
		while((base.length() > 0) && (base.charAt(base.length() - 1) == '-'))
			base.setLength(base.length() - 1);
		return (base.length() == 0) ? "script" : base.toString();
	}

	private static String what(final PhysicalAgent P)
	{
		if(P instanceof Room)
			return CMLib.map().getExtendedRoomID((Room)P);
		return P.Name();
	}

	/** keeps the change: a room or area in the database; a mob or item waits for SAVE ROOM */
	private void keep(final MOB mob, final PhysicalAgent P)
	{
		if(P instanceof Room)
		{
			CMLib.database().DBUpdateRoom((Room)P);
			mob.tell(L("@x1 is changed and saved.", what(P)));
		}
		else
		if(P instanceof Area)
		{
			CMLib.database().DBUpdateArea(P.Name(), (Area)P);
			mob.tell(L("The area @x1 is changed and saved.", P.Name()));
		}
		else
			mob.tell(L("@x1 is changed; SAVE ROOM keeps it.", P.name()));
	}

	private static class Args
	{
		String	target	= "";
		int		n		= 1;
		String	file	= null;
	}

	/** the words after SCRIPT or DESC: the target, then a number, then (EXTRACT) a file */
	private static Args args(final List<String> words, final boolean withFile)
	{
		final Args a = new Args();
		final List<String> w = new ArrayList<String>(words);
		if(withFile && (w.size() > 1))
		{
			final String last = w.get(w.size() - 1);
			if(last.toLowerCase().endsWith(".script") || (last.indexOf('/') >= 0))
			{
				a.file = last;
				w.remove(w.size() - 1);
			}
		}
		if((w.size() > 1) && CMath.isInteger(w.get(w.size() - 1)))
		{
			a.n = CMath.s_int(w.get(w.size() - 1));
			w.remove(w.size() - 1);
		}
		a.target = CMParms.combine(w, 0).trim();
		return a;
	}

	private Held chosen(final MOB mob, final PhysicalAgent P, final int n)
	{
		final List<Held> held = scriptsOf(P);
		if(held.isEmpty())
		{
			mob.tell(L("@x1 has no script: give it a Scriptable behaviour (MODIFY, or EDIT SCRIPT once it has one).", what(P)));
			return null;
		}
		if((n < 1) || (n > held.size()))
		{
			mob.tell(L("@x1 has @x2 scripts: 1 to @x2.", what(P), "" + held.size()));
			return null;
		}
		return held.get(n - 1);
	}

	/**
	 * EDIT SCRIPT: a thing's script in the editor, or its file if it is only
	 * LOAD=file.
	 * @param mob the builder
	 * @param P the thing
	 * @param n which script, from 1
	 * @throws java.io.IOException if the session fails
	 */
	public void editScript(final MOB mob, final PhysicalAgent P, final int n) throws java.io.IOException
	{
		final Held H = chosen(mob, P, n);
		if(H == null)
			return;
		final String file = loadOnly(H.text());
		if(file != null)
		{
			final CMFile F = new CMFile(Resources.makeFileResourceName(file), null, CMFile.FLAG_FORCEALLOW);
			final String old = F.exists() ? F.text().toString() : "";
			final String edited = TextEditor.edit(mob, file, old, TextEditor.Form.SCRIPT);
			if((edited == null) || edited.equals(old))
			{
				mob.tell(L("(no change)"));
				return;
			}
			if(!F.saveText(edited))
			{
				mob.tell(L("@x1 could not be written.", file));
				return;
			}
			// every script that is LOAD= of it reads it again
			Resources.removeResource(H.engine.getScriptResourceKey());
			H.set(H.text());
			mob.tell(L("@x1 is saved; every LOAD=@x1 shares it.", file));
			return;
		}
		final String old = H.text();
		final String edited = TextEditor.edit(mob, L("@x1's script @x2", what(P), "" + n), old, TextEditor.Form.SCRIPT);
		if((edited == null) || edited.equals(old))
		{
			mob.tell(L("(no change)"));
			return;
		}
		H.set(edited);
		keep(mob, P);
	}

	/**
	 * EDIT DESC: a thing's description in the editor.
	 * @param mob the builder
	 * @param P the thing
	 * @throws java.io.IOException if the session fails
	 */
	public void editDescription(final MOB mob, final PhysicalAgent P) throws java.io.IOException
	{
		final String old = P.description();
		final String edited = TextEditor.edit(mob, L("@x1's description", what(P)), old, TextEditor.Form.TEXT);
		if((edited == null) || edited.equals(old))
		{
			mob.tell(L("(no change)"));
			return;
		}
		P.setDescription(edited);
		// B9: written now
		if(P.hasTag(DigMode.TODO_TAG))
			P.delTag(DigMode.TODO_TAG);
		keep(mob, P);
	}

	/**
	 * EXTRACT SCRIPT: an inline script moved to its own file, and LOAD= of
	 * that file in its place.
	 * @param mob the builder
	 * @param P the thing
	 * @param n which script, from 1
	 * @param file the file, or null for progs/NAME.script
	 * @return the file, as LOAD= names it, or null if nothing was moved
	 */
	public String extractScript(final MOB mob, final PhysicalAgent P, final int n, final String file)
	{
		final Held H = chosen(mob, P, n);
		if(H == null)
			return null;
		final String script = H.text();
		if(loadOnly(script) != null)
		{
			mob.tell(L("That script is in @x1 already.", loadOnly(script)));
			return null;
		}
		if(script.trim().length() == 0)
		{
			mob.tell(L("That script is empty."));
			return null;
		}
		if(script.trim().toUpperCase().startsWith("STATIC="))
		{
			mob.tell(L("A STATIC= script stays as it is."));
			return null;
		}
		String path;
		if(file != null)
		{
			path = file.startsWith("/") ? file.substring(1) : file;
			if(path.startsWith("resources/"))
				path = path.substring(10);
			if(new CMFile(Resources.makeFileResourceName(path), null, CMFile.FLAG_FORCEALLOW).exists())
			{
				mob.tell(L("@x1 exists already: name another file.", path));
				return null;
			}
		}
		else
		{
			final String base = "progs/" + fileName(P.Name());
			path = base + ".script";
			for(int i = 2; new CMFile(Resources.makeFileResourceName(path), null, CMFile.FLAG_FORCEALLOW).exists(); i++)
				path = base + "-" + i + ".script";
		}
		final CMFile F = new CMFile(Resources.makeFileResourceName(path), null, CMFile.FLAG_FORCEALLOW);
		if(!F.saveText(TextEditor.asFile(script)))
		{
			mob.tell(L("@x1 could not be written.", path));
			return null;
		}
		H.set("LOAD=" + path);
		mob.tell(L("@x1's script is moved to resources/@x2@x3; it is LOAD=@x2 now, which others may share.",
			what(P), path, SaveGame.isBuilderMode() ? L(" (the world's own copy)") : ""));
		keep(mob, P);
		return path;
	}

	@Override
	public boolean execute(final MOB mob, final List<String> commands, final int metaFlags)
		throws java.io.IOException
	{
		final boolean extract = commands.get(0).toUpperCase().startsWith("EXT");
		final String usage = extract ? L("EXTRACT SCRIPT [TARGET] ([N]) ([FILE])") : L("EDIT SCRIPT [TARGET] ([N]), EDIT DESC [TARGET]");
		if(commands.size() < 3)
		{
			mob.tell(usage);
			return false;
		}
		final String verb = commands.get(1).toUpperCase();
		final boolean script = verb.startsWith("SCRIPT");
		final boolean desc = verb.startsWith("DESC");
		if((!script) && ((!desc) || extract))
		{
			mob.tell(usage);
			return false;
		}
		final Args a = args(commands.subList(2, commands.size()), extract);
		final PhysicalAgent P = ScriptCmd.findTarget(mob, a.target);
		if(P == null)
		{
			mob.tell(L("There is no '@x1' here.", a.target));
			return false;
		}
		if(extract)
			extractScript(mob, P, a.n, a.file);
		else
		if(script)
			editScript(mob, P, a.n);
		else
			editDescription(mob, P);
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
		return SaveGame.isBuilderMode()
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDMOBS)
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDITEMS)
			|| CMSecurity.isAllowedAnywhere(mob, CMSecurity.SecFlag.CMDROOMS);
	}
}
