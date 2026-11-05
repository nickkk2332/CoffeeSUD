package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.collections.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Exits.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.Libraries.interfaces.*;
import com.planet_ink.coffee_mud.Locales.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
import com.planet_ink.coffee_mud.core.save.WorldFiles;
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
 * I6: a piece of an item set.  SET=drowned_king (the set's id, the same on
 * every piece); NAME="Regalia of the Drowned King"; COUNT=WORN (pieces worn,
 * the default) or HELD (pieces carried); TIER2="Prop_WearAdjuster(savewat+20)"
 * TIER4="Prop_WearSpellCast(Spell_WaterBreathing);Prop_WearEnabler(Chant_Tsunami)"
 * what each number of pieces gives (all tiers met apply); PIECES= how many
 * there are; ONMSG=, OFFMSG= what the wearer is told as a tier comes and
 * goes ($t the tier); MASK= who may have it.  The tiers may be on one piece
 * only, or in world/itemsets.txt (SET=id NAME=... TIER2=... a line).  The
 * tiers' properties go on one counted piece, unsaved, and come off when a
 * piece does.  LOOK at a piece shows "Set: 3/5 worn" and its tiers.
 * SETCOUNT(who set) and SET_PROG set [ALL] (with $0 the tier, $1 ON or OFF)
 * for scripts.
 */
public class Prop_ItemSet extends Property
{
	@Override
	public String ID()
	{
		return "Prop_ItemSet";
	}

	@Override
	public String name()
	{
		return "Part of a set";
	}

	@Override
	protected int canAffectCode()
	{
		return Ability.CAN_ITEMS;
	}

	@Override
	public String accountForYourself()
	{
		return progress((affected instanceof Item && ((Item)affected).owner() instanceof MOB) ? (MOB)((Item)affected).owner() : null);
	}

	protected String						setId		= "";
	protected Map<String, String>			parms		= new HashMap<String, String>();
	protected MaskingLibrary.CompiledZMask	mask		= null;
	protected final List<Ability>			added		= new ArrayList<Ability>();
	protected final Set<Integer>			activeTiers	= new TreeSet<Integer>();
	protected boolean						ticking		= false;

	private static Map<String, Map<String, String>>	fileSets	= new HashMap<String, Map<String, String>>();
	private static long								fileStamp	= -1;
	public static synchronized void clearDefinitions() { fileStamp = -1; fileSets = new HashMap<String, Map<String, String>>(); }

	@Override
	public void setMiscText(final String newText)
	{
		super.setMiscText(newText);
		parms = CMParms.parseExactParms((newText == null) ? "" : newText);
		setId = CMParms.getParmStr(parms, "SET", "").trim().toUpperCase();
		final String m = CMParms.getParmStr(parms, "MASK", "").trim();
		mask = (m.length() == 0) ? null : CMLib.masking().getPreCompiledMask(m);
	}

	@Override
	public String getStat(final String code)
	{
		if(code.equalsIgnoreCase("SET"))
			return setId;
		if(code.equalsIgnoreCase("COUNT"))
			return CMParms.getParmStr(parms, "COUNT", "").toUpperCase();
		if(code.equalsIgnoreCase("TIERS"))
			return CMParms.toListString(activeTiers.iterator());
		return super.getStat(code);
	}

	@Override
	public void setAffectedOne(final Physical P)
	{
		super.setAffectedOne(P);
		if((P != null) && (!ticking))
		{
			ticking = true;
			CMLib.threads().startTickDown(this, Tickable.TICKID_PROPERTY_SPECIAL, 1);
		}
	}

	/** the sets of world/itemsets.txt, read again when it changes */
	protected static synchronized Map<String, String> fileSet(final String id)
	{
		final java.io.File W = WorldFiles.worldDir();
		final java.io.File F = new java.io.File((W == null) ? new java.io.File(".") : W, "itemsets.txt");
		final long stamp = F.isFile() ? F.lastModified() : 0;
		if(stamp != fileStamp)
		{
			fileStamp = stamp;
			fileSets = new HashMap<String, Map<String, String>>();
			if(stamp > 0)
			{
				try
				{
					for(final String l : new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8").split("\\r?\\n"))
					{
						if((l.trim().length() == 0) || l.trim().startsWith("#"))
							continue;
						final Map<String, String> q = CMParms.parseExactParms(l.trim());
						final String sid = CMParms.getParmStr(q, "SET", "").trim().toUpperCase();
						if(sid.length() > 0)
							fileSets.put(sid, q);
					}
				}
				catch(final Exception e)
				{
					Log.errOut("Prop_ItemSet", e.getMessage());
				}
			}
		}
		return fileSets.get(id);
	}

	/** the set's definition: the piece with tiers among these, or the file */
	protected Map<String, String> def(final List<Item> pieces)
	{
		if(hasTiers(parms))
			return parms;
		for(final Item I : pieces)
		{
			final Ability A = I.fetchEffect(ID());
			if((A instanceof Prop_ItemSet) && hasTiers(((Prop_ItemSet)A).parms))
				return ((Prop_ItemSet)A).parms;
		}
		final Map<String, String> f = fileSet(setId);
		return (f != null) ? f : parms;
	}

	protected static boolean hasTiers(final Map<String, String> q)
	{
		for(final String k : q.keySet())
		{
			if(k.toUpperCase().startsWith("TIER"))
				return true;
		}
		return false;
	}

	/** the tiers of a definition, by number */
	protected static TreeMap<Integer, String> tiers(final Map<String, String> q)
	{
		final TreeMap<Integer, String> t = new TreeMap<Integer, String>();
		for(final String k : q.keySet())
		{
			if(k.toUpperCase().startsWith("TIER") && CMath.isInteger(k.substring(4)))
				t.put(Integer.valueOf(CMath.s_int(k.substring(4))), q.get(k));
		}
		return t;
	}

	/** @return the set's name */
	public String setName()
	{
		final String n = CMParms.getParmStr(parms, "NAME", "");
		return (n.length() > 0) ? n : setId.toLowerCase().replace('_', ' ');
	}

	/**
	 * The pieces of a set one has, that count.
	 * @param M the one
	 * @param setId the set
	 * @return the pieces
	 */
	public static List<Item> pieces(final MOB M, final String setId)
	{
		return ItemSets.pieces(M, setId);
	}

	/** puts on (or takes off) what the tiers give, on this piece */
	protected void apply(final MOB M, final List<Item> counted)
	{
		final Map<String, String> d = def(counted);
		final TreeMap<Integer, String> tiers = tiers(d);
		final boolean carrier = (!counted.isEmpty()) && (counted.get(0) == affected)
			&& ((mask == null) || CMLib.masking().maskCheck(mask, M, true));
		final Set<Integer> want = new TreeSet<Integer>();
		if(carrier)
		{
			for(final Integer n : tiers.keySet())
			{
				if(counted.size() >= n.intValue())
					want.add(n);
			}
		}
		if(want.equals(activeTiers))
			return;
		for(final Ability A : added)
		{
			affected.delEffect(A);
			A.setAffectedOne(null);
		}
		added.clear();
		for(final Integer n : want)
		{
			for(final String[] w : Prop_When.parseWraps(tiers.get(n)))
			{
				final Ability A = CMClass.getAbility(w[0]);
				if(A != null)
				{
					A.setMiscText(w[1]);
					A.setSavable(false);
					affected.addNonUninvokableEffect(A);
					added.add(A);
				}
			}
		}
		final Set<Integer> was = new TreeSet<Integer>(activeTiers);
		activeTiers.clear();
		activeTiers.addAll(want);
		affected.recoverPhyStats();
		if(M != null)
		{
			M.recoverPhyStats();
			M.recoverCharStats();
			M.recoverMaxState();
			for(final Integer n : want)
			{
				if(!was.contains(n))
					tell(M, d, "ONMSG", n, "ON");
			}
			for(final Integer n : was)
			{
				if(!want.contains(n))
					tell(M, d, "OFFMSG", n, "OFF");
			}
		}
	}

	protected void tell(final MOB M, final Map<String, String> d, final String key, final Integer n, final String onOff)
	{
		final String m = CMParms.getParmStr(d, key, "");
		if(m.length() > 0)
			M.tell(m.replace("$t", "" + n));
		ScriptEvents.itemSet((Item)affected, M, setId, n.intValue(), onOff);
	}

	/**
	 * Looks again at the set now.
	 */
	public void look()
	{
		if(!(affected instanceof Item))
			return;
		final Item I = (Item)affected;
		final MOB M = (I.owner() instanceof MOB) ? (MOB)I.owner() : null;
		apply(M, pieces(M, setId));
	}

	public String progress(final MOB M)
	{
		final List<Item> counted = pieces(M, setId);
		final Map<String, String> d = def(counted);
		final TreeMap<Integer, String> tiers = tiers(d);
		final String count = CMParms.getParmStr(d, "COUNT", "WORN").toUpperCase();
		int total = CMParms.getParmInt(d, "PIECES", 0);
		if((total <= 0) && (!tiers.isEmpty()))
			total = tiers.lastKey().intValue();
		final StringBuilder b = new StringBuilder("Set: ").append(CMParms.getParmStr(d, "NAME", setName()))
			.append(", ").append(counted.size()).append((total > 0) ? ("/" + total) : "").append(count.equals("HELD") ? " held." : " worn.");
		for(final Integer n : tiers.keySet())
			b.append("\n\r  ").append(n).append(" pieces: ").append((counted.size() >= n.intValue()) ? "active" : "inactive");
		return b.toString();
	}

	@Override
	public void executeMsg(final Environmental myHost, final CMMsg msg)
	{
		super.executeMsg(myHost, msg);
		if((msg.target() == affected) && (affected instanceof Item)
		&& ((msg.targetMinor() == CMMsg.TYP_LOOK) || (msg.targetMinor() == CMMsg.TYP_EXAMINE)))
		{
			final MOB M = msg.source();
			final StringBuilder b = new StringBuilder(progress(M));
			msg.addTrailerMsg(CMClass.getMsg(M, affected, null, CMMsg.MSG_OK_VISUAL, b.toString(), CMMsg.NO_EFFECT, null, CMMsg.NO_EFFECT, null));
		}
	}

	@Override
	public boolean tick(final Tickable ticking, final int tickID)
	{
		if(tickID != Tickable.TICKID_PROPERTY_SPECIAL)
			return super.tick(ticking, tickID);
		if((!(affected instanceof Item)) || ((Item)affected).amDestroyed())
		{
			this.ticking = false;
			return false;
		}
		look();
		return true;
	}
}
