package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Behaviors.interfaces.Behavior;
import com.planet_ink.coffee_mud.Commands.interfaces.Command;
import com.planet_ink.coffee_mud.Libraries.interfaces.DatabaseEngine;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.interfaces.CMObject;
import com.planet_ink.coffee_mud.core.interfaces.Modifiable;

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
 * N7: the generic behaviours (Behaviors/GenBehavior): CREATE, MODIFY and
 * DESTROY BEHAVIOR, their editor, and their keeping.  The database keeps
 * them with the generic commands, base class "GenBehavior", so that the
 * world files (custom/behaviors/) and the cache work as for commands.  This
 * class knows them only through their stats, as the core cannot name a class
 * the engine loads.
 */
public final class GenBehaviors
{
	private GenBehaviors()
	{
	}

	/** the base class the database records a generic behaviour under */
	public static final String		BASE		= "GenBehavior";

	/** the fields the editor and MODIFY BEHAVIOR id FIELD value set */
	public static final String[]	FIELDS		= { "NAME", "HELP", "CANIMPROVE", "PARMS", "SCRIPT", "ACCOUNT", "VARSCOPE" };

	private static String L(final String str, final String... xs)
	{
		return CMLib.lang().L(str, xs);
	}

	/**
	 * @param B a behaviour
	 * @return true if it is a generic one, not the bare GenBehavior
	 */
	public static boolean isGeneric(final CMObject B)
	{
		return (B instanceof Behavior)
			&& BASE.equals(((Behavior)B).getStat("JAVACLASS"))
			&& (!B.ID().equals(BASE));
	}

	/**
	 * @param id an ID
	 * @return the generic behaviour of that ID's prototype, or null
	 */
	public static Behavior find(final String id)
	{
		final CMObject O = CMClass.getPrototypeByID(CMClass.CMObjectType.BEHAVIOR, id);
		return isGeneric(O) ? (Behavior)O : null;
	}

	/**
	 * Makes the generic behaviours the database has, at boot.
	 * @param recs the generic commands' records, with them among them
	 * @return how many
	 */
	public static int loadAll(final List<DatabaseEngine.AckRecord> recs)
	{
		int n = 0;
		for(final DatabaseEngine.AckRecord rec : recs)
		{
			if(!BASE.equals(rec.typeClass()))
				continue;
			final Behavior B = CMClass.getBehavior(BASE);
			if(B == null)
				return n;
			B.setStat("ALLXML", rec.data());
			n++;
		}
		return n;
	}

	/**
	 * @param rec a generic command's record
	 * @return true if it is a generic behaviour's
	 */
	public static boolean isBehaviorRecord(final DatabaseEngine.AckRecord rec)
	{
		return (rec != null) && BASE.equals(rec.typeClass());
	}

	/** keeps a generic behaviour's definition in the database */
	private static void store(final Behavior B)
	{
		CMLib.database().DBDeleteCommand(B.ID());
		CMLib.database().DBCreateCommand(B.ID(), BASE, B.getStat("ALLXML"));
	}

	/**
	 * CREATE BEHAVIOR id: a new generic behaviour, then its editor.
	 * @param mob the builder
	 * @param id its ID
	 * @return it, or null
	 * @throws IOException if the session fails
	 */
	public static Behavior create(final MOB mob, final String id) throws IOException
	{
		if((id == null) || (id.trim().length() == 0))
		{
			mob.tell(L("The format is CREATE BEHAVIOR [ID]."));
			return null;
		}
		if(!id.matches("[A-Za-z][A-Za-z0-9_]*"))
		{
			mob.tell(L("'@x1' is not a good ID: letters, digits and _, from a letter.", id));
			return null;
		}
		final CMObject old = CMClass.getPrototypeByID(CMClass.CMObjectType.BEHAVIOR, id);
		if(old != null)
		{
			if(isGeneric(old))
				mob.tell(L("The generic behaviour @x1 already exists: MODIFY BEHAVIOR @x1.", old.ID()));
			else
				mob.tell(L("@x1 is a behaviour the game has; choose another ID.", old.ID()));
			return null;
		}
		final Command C = CMClass.getCommand(id);
		if((C != null) && C.isGeneric() && C.ID().equalsIgnoreCase(id))
		{
			mob.tell(L("A generic command has the ID @x1; choose another.", C.ID()));
			return null;
		}
		final Behavior B = CMClass.getBehavior(BASE);
		if(B == null)
			return null;
		B.setStat("CLASS", id);
		B.setStat("NAME", id);
		B.setStat("CANIMPROVE", "MOBS");
		B.setStat("SCRIPT", "GREET_PROG 100\nMPECHO $I looks at $n.\n~\n");
		edit(mob, B, -1);
		store(B);
		mob.tell(L("The behaviour @x1 is made.  Give it as @x1 key=value...; SAVE writes it to the world's files.", B.ID()));
		return B;
	}

	/**
	 * MODIFY BEHAVIOR id [FIELD value]: one field, or the editor.
	 * @param mob the builder
	 * @param words what follows MODIFY BEHAVIOR
	 * @return true if it was changed
	 * @throws IOException if the session fails
	 */
	public static boolean modify(final MOB mob, final List<String> words) throws IOException
	{
		if(words.size() < 1)
		{
			mob.tell(L("The format is MODIFY BEHAVIOR [ID] ([FIELD] [VALUE])."));
			return false;
		}
		final Behavior B = find(words.get(0));
		if(B == null)
		{
			mob.tell(L("There is no generic behaviour '@x1'.", words.get(0)));
			return false;
		}
		if(words.size() > 1)
		{
			final String field = words.get(1).toUpperCase();
			if(CMParms.indexOf(FIELDS, field) < 0)
			{
				mob.tell(L("Its fields are @x1.", CMParms.toListString(FIELDS)));
				return false;
			}
			final String value = CMParms.combine(words, 2);
			B.setStat(field, field.equals("SCRIPT") ? value.replace("\\n", "\n") : value);
		}
		else
			edit(mob, B, -1);
		store(B);
		mob.tell(L("@x1 is changed, on everything that has it.", B.ID()));
		return true;
	}

	/**
	 * DESTROY BEHAVIOR id.
	 * @param mob the builder
	 * @param id its ID
	 * @return true if it was destroyed
	 */
	public static boolean destroy(final MOB mob, final String id)
	{
		final Behavior B = find(id);
		if(B == null)
		{
			mob.tell(L("There is no generic behaviour '@x1'.", id));
			return false;
		}
		CMClass.delClass(CMClass.CMObjectType.BEHAVIOR, B);
		CMLib.database().DBDeleteCommand(B.ID());
		mob.tell(L("@x1 is destroyed.  What has it keeps it until the world is loaded again, without it.", B.ID()));
		return true;
	}

	/**
	 * The editor: each field, then which to change.
	 * @param mob the builder
	 * @param B the behaviour
	 * @param showFlag as the generic editors take it
	 * @throws IOException if the session fails
	 */
	public static void edit(final MOB mob, final Behavior B, int showFlag) throws IOException
	{
		if((mob == null) || mob.isMonster())
			return;
		final Modifiable me = B;
		if((showFlag == -1) && (CMProps.getIntVar(CMProps.Int.EDITORTYPE) > 0))
			showFlag = -999;
		boolean ok = false;
		while((mob.session() != null) && (!mob.session().isStopped()) && (!ok))
		{
			int n = 0;
			CMLib.genEd().promptStatStr(mob, me, null, ++n, showFlag, L("Name"), "NAME", false);
			CMLib.genEd().promptStatStr(mob, me, L("What AHELP @x1 says of it.", B.ID()), ++n, showFlag, L("Help"), "HELP", true);
			CMLib.genEd().promptStatStr(mob, me, L("What it may go on: MOBS, ITEMS, ROOMS, AREAS, EXITS, with commas."), ++n, showFlag, L("Can improve"), "CANIMPROVE", false);
			CMLib.genEd().promptStatStr(mob, me, L("Its parameters and their defaults: price=20 exit=north.  The script reads PARM(price)."), ++n, showFlag, L("Parameters"), "PARMS", true);
			final String old = me.getStat("SCRIPT");
			final String now = TextEditor.field(mob, old, ++n, showFlag, L("Script"), TextEditor.Form.SCRIPT);
			if((now != null) && (!now.equals(old)))
				me.setStat("SCRIPT", now);
			CMLib.genEd().promptStatStr(mob, me, L("What STAT says it is doing, or empty."), ++n, showFlag, L("Account"), "ACCOUNT", true);
			CMLib.genEd().promptStatStr(mob, me, L("Empty: its variables are its host's, as a Scriptable's; *: its own."), ++n, showFlag, L("Variable scope"), "VARSCOPE", true);
			if(showFlag < -900)
			{
				ok = true;
				break;
			}
			if(showFlag > 0)
			{
				showFlag = -1;
				continue;
			}
			showFlag = CMath.s_int(mob.session().prompt(L("Edit which? "), ""));
			if(showFlag <= 0)
			{
				showFlag = -1;
				ok = true;
			}
		}
	}

	/**
	 * AHELP: a generic behaviour's help, by its ID or BEHAVIOR_ID.
	 * @param key the help key, upper case
	 * @return its help, or null
	 */
	public static String helpFor(final String key)
	{
		if(key == null)
			return null;
		final String k = key.startsWith("BEHAVIOR_") ? key.substring(9) : key;
		final Behavior B = find(k);
		return (B == null) ? null : B.getStat("HELPTEXT");
	}
}
