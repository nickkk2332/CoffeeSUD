package com.planet_ink.coffee_mud.Behaviors;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
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
 * N7: a behaviour defined as data (CREATE BEHAVIOR id in builder mode, kept
 * in world/custom/behaviors/): its own ID, a NAME, HELP for AHELP, what it
 * CANIMPROVE, PARMS with their defaults ("price=20 exit=north"), and a
 * SCRIPT in MOBPROG (with &lt;SCRIPT&gt; JavaScript if it likes) that reads
 * them as PARM(price), or parm('price') in JavaScript.  It goes wherever a
 * behaviour ID goes, with its parameters after it: TollKeeper price=5
 * exit=west.  Each copy has its own script engine; all share the
 * definition, so MODIFY BEHAVIOR changes every one at once.  WHEN=, TOD= and
 * HOURS= work in its parameters as in any behaviour's (N1).
 * <p>
 * The definition is held in a map shared by the prototype and its copies
 * (no inner classes: the class may be dropped from the class loader's cache
 * by DESTROY BEHAVIOR, as GenCommand's is).
 */
public class GenBehavior extends Scriptable
{
	/** the definition's fields, also its stats; ALLXML holds them all */
	public static final String[]	DEF_CODES	= { "CLASS", "NAME", "HELP", "CANIMPROVE", "PARMS", "SCRIPT", "ACCOUNT", "VARSCOPE" };

	private static final String[]	TARGETS		= { "MOBS", "ITEMS", "AREAS", "ROOMS", "EXITS" };

	/** the definition: DEF_CODES, and VERSION, counted up on each change */
	protected Map<String, String>	def			= newDef("GenBehavior");
	/** this copy's own parameters, as given */
	protected String				own			= "";
	/** the definition's version its engine has */
	protected int					hasVersion	= -1;
	/** the defaults, then this copy's own */
	protected Map<String, String>	values		= new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);

	private static Map<String, String> newDef(final String id)
	{
		final Map<String, String> d = new Hashtable<String, String>();
		for(final String c : DEF_CODES)
			d.put(c, "");
		d.put("CLASS", id);
		d.put("CANIMPROVE", "MOBS");
		d.put("VERSION", "0");
		return d;
	}

	@Override
	public String ID()
	{
		return def.get("CLASS");
	}

	@Override
	public String name()
	{
		final String n = def.get("NAME");
		return (n.length() > 0) ? n : ID();
	}

	@Override
	protected int canImproveCode()
	{
		int code = 0;
		for(final String t : CMParms.parseCommas(def.get("CANIMPROVE").toUpperCase(), true))
		{
			final int x = CMParms.indexOf(TARGETS, t.endsWith("S") ? t : (t + "S"));
			if(x >= 0)
				code |= (1 << x);
		}
		return (code == 0) ? Behavior.CAN_MOBS : code;
	}

	@Override
	public String accountForYourself()
	{
		final String a = def.get("ACCOUNT");
		return (a.length() > 0) ? a : ("behaving as " + name());
	}

	@Override
	public boolean isSavable()
	{
		return super.isSavable() && (!ID().equals("GenBehavior"));
	}

	@Override
	public CMObject newInstance()
	{
		final GenBehavior B = new GenBehavior();
		if(!ID().equals("GenBehavior"))
		{
			B.def = def;
			B.setParms("");
		}
		return B;
	}

	@Override
	public CMObject copyOf()
	{
		final CMObject O = super.copyOf();
		if(O instanceof GenBehavior)
		{
			final GenBehavior B = (GenBehavior)O;
			B.values = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
			B.values.putAll(values);
			B.hasVersion = -1; // its copied engine is set again
		}
		return O;
	}

	/** the engine, with the definition's script, set again after MODIFY BEHAVIOR */
	@Override
	protected ScriptingEngine engine()
	{
		if(engine == null)
		{
			engine = (ScriptingEngine)CMClass.getCommon("DefaultScriptingEngine");
			hasVersion = -1;
		}
		final int v = CMath.s_int(def.get("VERSION"));
		if(hasVersion != v)
		{
			hasVersion = v;
			computeValues(); // the defaults may have changed too
			engine.setVarScope(def.get("VARSCOPE"));
			engine.setScript(def.get("SCRIPT"));
			engine.setParmValues(values);
		}
		return engine;
	}

	/** the defaults: "a=1 b=2", or "a=1;b=2" */
	protected Map<String, String> defaults()
	{
		final Map<String, String> d = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		for(final String piece : def.get("PARMS").split(";"))
		{
			if(piece.trim().length() > 0)
				d.putAll(CMParms.parseEQParms(piece));
		}
		return d;
	}

	@Override
	public String getParms()
	{
		return own;
	}

	@Override
	public void setParms(final String newParms)
	{
		own = (newParms == null) ? "" : newParms.trim();
		parms = own;
		computeValues();
		if(engine != null)
			engine.setParmValues(values);
	}

	/** the defaults, then this copy's own parameters over them */
	protected void computeValues()
	{
		final Map<String, String> v = new TreeMap<String, String>(String.CASE_INSENSITIVE_ORDER);
		v.putAll(defaults());
		v.putAll(CMParms.parseEQParms(own));
		values = v;
	}

	/**
	 * @param name a parameter
	 * @return its value here, or its default, or ""
	 */
	public String parm(final String name)
	{
		final String v = values.get(name);
		return (v == null) ? "" : v;
	}

	/** @return what AHELP shows of it */
	protected String helpText()
	{
		final StringBuilder str = new StringBuilder("");
		str.append("Behavior   : ").append(ID()).append(" (a generic behaviour)\n\r");
		final List<String> targets = new ArrayList<String>();
		final int code = canImproveCode();
		for(int i = 0; i < TARGETS.length; i++)
		{
			if((code & (1 << i)) != 0)
				targets.add(CMStrings.capitalizeAndLower(TARGETS[i]));
		}
		str.append("Targets    : ").append(CMParms.toListString(targets)).append("\n\r");
		final String p = def.get("PARMS").replace(';', ' ').trim();
		str.append("Parameters : ").append((p.length() == 0) ? "(none)" : p).append("\n\r");
		str.append("Example    : ").append(ID()).append((p.length() == 0) ? "" : (" " + p)).append("\n\r");
		str.append("Description: \n\r").append(def.get("HELP"));
		return str.toString();
	}

	@Override
	public boolean isStat(final String code)
	{
		return (CMParms.indexOf(DEF_CODES, code.toUpperCase().trim()) >= 0) || super.isStat(code);
	}

	@Override
	public String getStat(final String code)
	{
		final String c = code.toUpperCase().trim();
		if(c.equals("CLASS"))
			return ID();
		if(c.equals("ALLXML"))
		{
			final StringBuilder str = new StringBuilder("");
			for(final String d : DEF_CODES)
				str.append("<").append(d).append(">").append(CMLib.xml().parseOutAngleBrackets(def.get(d))).append("</").append(d).append(">");
			return str.toString();
		}
		if(c.equals("JAVACLASS"))
			return "GenBehavior";
		if(c.equals("HELPTEXT"))
			return helpText();
		if(c.equals("VERSION"))
			return def.get("VERSION");
		if(CMParms.indexOf(DEF_CODES, c) >= 0)
			return def.get(c);
		return super.getStat(code);
	}

	@Override
	public void setStat(final String code, final String val)
	{
		final String c = code.toUpperCase().trim();
		final String v = (val == null) ? "" : val;
		if(c.equals("ALLXML"))
		{
			final List<XMLLibrary.XMLTag> V = CMLib.xml().parseAllXML(v);
			if((V == null) || (V.size() == 0))
				return;
			// the ID first, so that the rest is set on the definition it names
			final String id = CMLib.xml().restoreAngleBrackets(CMLib.xml().getValFromPieces(V, "CLASS")).trim();
			if(id.length() > 0)
				setStat("CLASS", id);
			for(final String d : DEF_CODES)
			{
				if(!d.equals("CLASS"))
					def.put(d, CMLib.xml().restoreAngleBrackets(CMLib.xml().getValFromPieces(V, d)));
			}
			bump();
			return;
		}
		if(c.equals("CLASS"))
		{
			// a new definition under this ID, which this object is the prototype of
			final String id = v.trim();
			if((id.length() == 0) || (id.indexOf(' ') >= 0) || id.equals(ID()))
				return;
			final Map<String, String> d = newDef(id);
			for(final String k : DEF_CODES)
			{
				if(!k.equals("CLASS"))
					d.put(k, def.get(k));
			}
			def = d;
			final CMObject old = CMClass.getPrototypeByID(CMClass.CMObjectType.BEHAVIOR, id);
			if((old != null) && (old != this))
				CMClass.delClass(CMClass.CMObjectType.BEHAVIOR, old);
			CMClass.addClass(CMClass.CMObjectType.BEHAVIOR, this);
			bump();
			return;
		}
		if(CMParms.indexOf(DEF_CODES, c) >= 0)
		{
			def.put(c, v);
			bump();
			return;
		}
		super.setStat(code, val);
	}

	/** the definition changed: every copy sets its engine again */
	protected void bump()
	{
		// each copy reads the defaults and the script again when next asked
		def.put("VERSION", "" + (CMath.s_int(def.get("VERSION")) + 1));
	}

	@Override
	protected String debugState()
	{
		return "generic, parameters: " + (own.length() == 0 ? "(none)" : own) + "; values: " + values;
	}

	@Override
	public void executeMsg(final Environmental affecting, final CMMsg msg)
	{
		syncValues();
		super.executeMsg(affecting, msg);
	}

	@Override
	public boolean okMessage(final Environmental host, final CMMsg msg)
	{
		syncValues();
		return super.okMessage(host, msg);
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		syncValues();
		return super.tick(ticking, tickID);
	}

	/** after MODIFY BEHAVIOR changed the defaults: this copy's values again */
	protected void syncValues()
	{
		if(hasVersion != CMath.s_int(def.get("VERSION")))
			engine();
	}
}
