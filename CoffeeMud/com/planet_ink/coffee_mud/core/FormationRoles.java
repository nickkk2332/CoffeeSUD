package com.planet_ink.coffee_mud.core;

import java.io.File;
import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Common.interfaces.Session;
import com.planet_ink.coffee_mud.Common.interfaces.Tattoo;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.Origins.Origin;
import com.planet_ink.coffee_mud.core.save.WorldFiles;

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
 * P06: named formation roles.  world/formations.ini has an [ID] section for
 * each role, as origins.ini does: NAME, ROW (the row it takes when given),
 * TARGET (whom it fights: LEADER, the group leader's foe, the default;
 * WEAKEST, the foe with the least hit points by share; STRONGEST, the most;
 * CASTER, the foe with the most mana; ATTACKER, a foe attacking the one it
 * guards), GUARDS (LEADER, or a role: the member it guards), INTERCEPT (a
 * share: when the guarded one's hit points fall below it, the role draws one
 * of their attackers on to itself, each round) and FALLBACK (a share: below
 * it, the role steps to the back of the formation, and stays there until
 * moved).  A member's role is kept as the tattoo ROLE_[ID], so a save keeps
 * it, and a group of NPCs can have roles too.  Each round of a fight, before
 * its attacks, a member with a role reacts and picks its target by these
 * rules.
 */
public final class FormationRoles
{
	private FormationRoles()
	{
	}

	public static final String	FAMILY	= "formations";
	private static final String	PREFIX	= "ROLE_";
	/** worn by a member that fell back, until it returns to its role's row */
	private static final String	BACK	= "ROLEBACK";
	private static final List<String>	TARGETS	= Arrays.asList("LEADER", "WEAKEST", "STRONGEST", "CASTER", "ATTACKER");

	/** @return world/formations.ini, or null with no world folder */
	public static File file()
	{
		final File W = WorldFiles.worldDir();
		return (W == null) ? null : new File(W, "formations.ini");
	}

	private static String	cachedPath	= "";
	private static long		cachedStamp	= -1;
	private static List<Origin>	cached	= new ArrayList<Origin>(0);

	/** every role, in the file's order */
	public static synchronized List<Origin> all()
	{
		final File F = file();
		if((F == null) || !F.isFile())
			return new ArrayList<Origin>(0);
		final long stamp = F.lastModified() ^ (F.length() << 20);
		if(F.getPath().equals(cachedPath) && (stamp == cachedStamp))
			return cached;
		try
		{
			cached = Collections.unmodifiableList(Origins.parse(new String(java.nio.file.Files.readAllBytes(F.toPath()), "UTF-8")));
			cachedPath = F.getPath();
			cachedStamp = stamp;
		}
		catch(final Exception e)
		{
			Log.errOut("FormationRoles", "formations.ini: " + e.getMessage());
			cached = new ArrayList<Origin>(0);
		}
		return cached;
	}

	/** a role by its ID or name, or null */
	public static Origin find(final String id)
	{
		final String in = (id == null) ? "" : id.trim();
		for(final Origin R : all())
		{
			if(R.id.equalsIgnoreCase(in) || R.name().equalsIgnoreCase(in))
				return R;
		}
		return null;
	}

	/** the role a member's tattoo names, even one formations.ini no longer has; "" for none */
	public static String named(final MOB M)
	{
		if(M == null)
			return "";
		for(final Enumeration<Tattoo> t = M.tattoos(); t.hasMoreElements();)
		{
			final Tattoo T = t.nextElement();
			if((T != null) && T.name().startsWith(PREFIX))
				return T.name().substring(PREFIX.length());
		}
		return "";
	}

	/** a member's role, or null; quick for those with none */
	public static Origin of(final MOB M)
	{
		if(M == null)
			return null;
		String id = null;
		for(final Enumeration<Tattoo> t = M.tattoos(); t.hasMoreElements();)
		{
			final Tattoo T = t.nextElement();
			if((T != null) && T.name().startsWith(PREFIX))
			{
				id = T.name().substring(PREFIX.length());
				break;
			}
		}
		return (id == null) ? null : find(id);
	}

	private static int share(final MOB M)
	{
		return (int)Math.round(100.0 * M.curState().getHitPoints() / Math.max(1, M.maxState().getHitPoints()));
	}

	/** how a role works, in words */
	public static String explain(final Origin R)
	{
		final List<String> s = new ArrayList<String>();
		s.add(CMLib.lang().L("row @x1", R.get("ROW").isEmpty() ? "0" : R.get("ROW")));
		final String t = R.get("TARGET").isEmpty() ? "LEADER" : R.get("TARGET").toUpperCase(Locale.ROOT);
		switch(t)
		{
		case "WEAKEST":
			s.add(CMLib.lang().L("fights the most wounded foe"));
			break;
		case "STRONGEST":
			s.add(CMLib.lang().L("fights the least wounded foe"));
			break;
		case "CASTER":
			s.add(CMLib.lang().L("fights the foe with the most mana"));
			break;
		case "ATTACKER":
			s.add(CMLib.lang().L("fights whoever attacks @x1", guardsWord(R)));
			break;
		default:
			s.add(CMLib.lang().L("fights the leader's foe"));
			break;
		}
		if(!R.get("INTERCEPT").isEmpty())
			s.add(CMLib.lang().L("draws an attacker off @x1 at or below @x2% hit points", guardsWord(R), R.get("INTERCEPT")));
		if(!R.get("FALLBACK").isEmpty())
			s.add(CMLib.lang().L("falls back to the rear below @x1% hit points, and returns when healed or when the fight ends", R.get("FALLBACK")));
		return String.join("; ", s);
	}

	private static String guardsWord(final Origin R)
	{
		final String g = R.get("GUARDS");
		if(g.isEmpty() || g.equalsIgnoreCase("LEADER"))
			return CMLib.lang().L("the leader");
		final Origin other = find(g);
		return CMLib.lang().L("the @x1", (other == null) ? g.toLowerCase(Locale.ROOT) : other.name().toLowerCase(Locale.ROOT));
	}

	/** the group a member fights with: those it follows and who follow them */
	private static Set<MOB> group(final MOB M)
	{
		return M.getGroupMembers(new HashSet<MOB>());
	}

	/** the foes of a group in its room: anyone fighting one of them, and their victims */
	private static List<MOB> foes(final MOB M, final Set<MOB> group)
	{
		final List<MOB> out = new ArrayList<MOB>();
		final Room R = M.location();
		if(R == null)
			return out;
		for(int i = 0; i < R.numInhabitants(); i++)
		{
			final MOB F = R.fetchInhabitant(i);
			if((F == null) || group.contains(F) || F.amDead())
				continue;
			boolean foe = (F.getVictim() != null) && group.contains(F.getVictim());
			for(final MOB G : group)
				foe = foe || (G.getVictim() == F);
			if(foe)
				out.add(F);
		}
		return out;
	}

	/** the member a role guards: the group's leader, or the member whose role GUARDS names */
	private static MOB guarded(final MOB M, final Origin R, final Set<MOB> group)
	{
		final String g = R.get("GUARDS");
		if(g.isEmpty() || g.equalsIgnoreCase("LEADER"))
		{
			final MOB L = M.getGroupLeader();
			return (L == M) ? null : L;
		}
		for(final MOB G : group)
		{
			final Origin other = of(G);
			if((G != M) && (other != null) && other.id.equalsIgnoreCase(g))
				return G;
		}
		return null;
	}

	/**
	 * One round of a member with a role, before its attacks: it reacts
	 * (INTERCEPT, FALLBACK) and turns on the foe its TARGET picks.
	 * @param M the member
	 * @return what it did, in words, or "" (for tests and COMBATDEBUG)
	 */
	public static String round(final MOB M)
	{
		final Origin R = of(M);
		if((R == null) || (M.location() == null) || !M.isInCombat() || played(M))
			return "";
		final Set<MOB> group = group(M);
		final List<MOB> foes = foes(M, group);
		if(foes.isEmpty())
			return "";
		final List<String> did = new ArrayList<String>();
		final MOB P = guarded(M, R, group);
		// healed again: back to its role's row
		if((M.findTattoo(BACK) != null) && !R.get("FALLBACK").isEmpty() && (share(M) >= CMath.s_int(R.get("FALLBACK"))))
			did.add(returned(M, R));
		// a guard draws one attacker off the one it guards, when they are hurt enough
		if((P != null) && !R.get("INTERCEPT").isEmpty() && (share(P) <= CMath.s_int(R.get("INTERCEPT"))))
		{
			for(final MOB F : foes)
			{
				if(F.getVictim() == P)
				{
					M.location().show(M, P, F, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> step(s) in front of <T-NAME>, drawing <O-NAME>'s attacks."));
					F.setVictim(M);
					M.setVictim(F);
					CombatDebug.note(M, F, "role " + R.name() + ": draws " + F.name() + " off " + P.name() + " (" + share(P) + "% hit points, at or below " + R.get("INTERCEPT") + "%)");
					ModuleStats.count(FAMILY, R.id.toLowerCase(Locale.ROOT), "interceptions");
					did.add("intercepted " + F.Name());
					break;
				}
			}
		}
		// falling back: behind everyone, until moved again; not again once alone at the rear
		if(!R.get("FALLBACK").isEmpty() && (share(M) < CMath.s_int(R.get("FALLBACK"))) && (M.amFollowing() != null))
		{
			final List<MOB>[] rows = CMLib.combat().getFormation(M);
			int deepest = rows.length - 1;
			while((deepest > 0) && ((rows[deepest] == null) || rows[deepest].isEmpty()))
				deepest--;
			final boolean aloneAtRear = (rows[deepest] != null) && (rows[deepest].size() == 1) && rows[deepest].contains(M);
			if(!aloneAtRear)
			{
				final int back = deepest + 1;
				M.amFollowing().addFollower(M, back);
				M.addTattoo(BACK);
				CombatDebug.note(M, M.getVictim(), "role " + R.name() + ": falls back to row " + back + " (" + share(M) + "% hit points, below " + R.get("FALLBACK") + "%)");
				M.location().show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> fall(s) back."));
				ModuleStats.count(FAMILY, R.id.toLowerCase(Locale.ROOT), "fallbacks");
				Provenance.event(M, FAMILY, "formations.ini [" + R.id + "]", "fell back to row " + back + " below " + R.get("FALLBACK") + "% hit points");
				did.add("fell back to row " + back);
			}
		}
		// the target its rule picks, unless it has just drawn an attacker
		if(did.isEmpty() || !did.get(0).startsWith("intercepted"))
		{
			final MOB T = pick(M, R, P, foes);
			if((T != null) && (T != M.getVictim()))
			{
				final MOB was = M.getVictim();
				M.setVictim(T);
				did.add("turned on " + T.Name());
				CombatDebug.note(M, T, "role " + R.name() + ": turns on " + T.name() + (was == null ? "" : " from " + was.name()) + " (" + why(R, T) + ")");
			}
		}
		return String.join("; ", did);
	}

	/**
	 * Whether someone is playing a member: the character at the terminal, or
	 * one in a window of its own.  Its role waits, so it fights whom it is told
	 * to; a party character no one is playing (SWITCH away from it) and an NPC
	 * follower are left to their roles.
	 * @param M the member
	 * @return true if it is being played
	 */
	public static boolean played(final MOB M)
	{
		final Session S = M.session();
		return (S != null) && !"PartySession".equals(S.ID());
	}

	/** why a role's TARGET picked a foe, in words, for COMBATDEBUG */
	private static String why(final Origin R, final MOB T)
	{
		final String t = R.get("TARGET").isEmpty() ? "LEADER" : R.get("TARGET").toUpperCase(Locale.ROOT);
		switch(t)
		{
		case "WEAKEST":
			return "the most wounded foe, " + share(T) + "% hit points";
		case "STRONGEST":
			return "the least wounded foe, " + share(T) + "% hit points";
		case "CASTER":
			return "the foe with the most mana, " + T.curState().getMana();
		case "ATTACKER":
			return "it attacks the one it guards";
		default:
			return "the leader's foe";
		}
	}

	/** a member that fell back takes its role's row again */
	private static String returned(final MOB M, final Origin R)
	{
		M.delTattoo(BACK);
		final int row = Math.max(0, CMath.s_int(R.get("ROW")));
		if(M.amFollowing() != null)
			M.amFollowing().addFollower(M, row);
		if(M.location() != null)
			M.location().show(M, null, CMMsg.MSG_OK_ACTION, CMLib.lang().L("<S-NAME> return(s) to <S-HIS-HER> place in the formation."));
		CombatDebug.note(M, M.getVictim(), "role " + R.name() + ": returns to row " + row);
		Provenance.event(M, FAMILY, "formations.ini [" + R.id + "]", "returned to row " + row);
		return "returned to row " + row;
	}

	/** a fight ends: a member that fell back returns to its row */
	public static void fightEnded(final MOB M)
	{
		if((M == null) || (M.findTattoo(BACK) == null))
			return;
		final Origin R = of(M);
		if(R == null)
			M.delTattoo(BACK);
		else
			returned(M, R);
	}

	private static MOB pick(final MOB M, final Origin R, final MOB P, final List<MOB> foes)
	{
		final String t = R.get("TARGET").isEmpty() ? "LEADER" : R.get("TARGET").toUpperCase(Locale.ROOT);
		MOB best = null;
		switch(t)
		{
		case "WEAKEST":
		case "STRONGEST":
			for(final MOB F : foes)
			{
				if((best == null) || (t.equals("WEAKEST") ? (share(F) < share(best)) : (share(F) > share(best))))
					best = F;
			}
			return best;
		case "CASTER":
			for(final MOB F : foes)
			{
				if((best == null) || (F.curState().getMana() > best.curState().getMana()))
					best = F;
			}
			return best;
		case "ATTACKER":
		{
			final MOB guarded = (P != null) ? P : M.getGroupLeader();
			for(final MOB F : foes)
			{
				if(F.getVictim() == guarded)
					return F;
			}
			return null;
		}
		default:
		{
			final MOB L = M.getGroupLeader();
			return ((L != null) && (L != M) && foes.contains(L.getVictim())) ? L.getVictim() : null;
		}
		}
	}

	/** FORMATION [name] ROLE [role|NONE]: gives a member a role, and the row it takes */
	public static String assign(final MOB who, final String role) throws IOException
	{
		final Origin R = role.equalsIgnoreCase("NONE") ? null : find(role);
		if((R == null) && !role.equalsIgnoreCase("NONE"))
			throw new IOException(CMLib.lang().L("there is no role '@x1'; FORMATION ROLES lists them", role));
		final List<String> had = new ArrayList<String>();
		for(final Enumeration<Tattoo> t = who.tattoos(); t.hasMoreElements();)
		{
			final Tattoo T = t.nextElement();
			if((T != null) && T.name().startsWith(PREFIX))
				had.add(T.name());
		}
		for(final String t : had)
			who.delTattoo(t);
		who.delTattoo(BACK);
		if(R == null)
			return CMLib.lang().L("@x1 has no role now.", who.name());
		who.addTattoo(PREFIX + R.id.toUpperCase(Locale.ROOT));
		final int row = Math.max(0, CMath.s_int(R.get("ROW")));
		final MOB lead = (who.amFollowing() != null) ? who.amFollowing() : who;
		lead.addFollower(who, row);
		ModuleStats.count(FAMILY, R.id.toLowerCase(Locale.ROOT), "assigned");
		Provenance.event(who, FAMILY, "formations.ini [" + R.id + "]", "given the role " + R.name() + ", row " + row);
		return CMLib.lang().L("@x1 is your @x2 now (row @x3): @x4.", who.name(), R.name().toLowerCase(Locale.ROOT), "" + row, explain(R));
	}

	/** FORMATION ROLES: every role, and how it works */
	public static String list()
	{
		final StringBuilder s = new StringBuilder();
		for(final Origin R : all())
			s.append(s.length() > 0 ? "\n" : "").append(R.name()).append(" (").append(R.id.toLowerCase(Locale.ROOT)).append("): ").append(explain(R));
		return (s.length() == 0) ? CMLib.lang().L("This world has no formation roles.") : s.toString();
	}

	/** what WORLD CHECK reports */
	public static List<String> problems()
	{
		final List<String> out = new ArrayList<String>();
		for(final Origin R : all())
		{
			final String t = R.get("TARGET").toUpperCase(Locale.ROOT);
			if(!t.isEmpty() && !TARGETS.contains(t))
				out.add("formations.ini [" + R.id + "]: TARGET is one of " + String.join(", ", TARGETS) + ", not " + t);
			for(final String k : new String[] { "ROW", "INTERCEPT", "FALLBACK" })
			{
				if(!R.get(k).isEmpty() && !CMath.isInteger(R.get(k)))
					out.add("formations.ini [" + R.id + "]: " + k + " is a number, not " + R.get(k));
			}
		}
		return out;
	}

	/** renames.ini: a member's role (and the tattoo masks test) under the name the file gives it now; @return what was renamed */
	public static List<String> rename(final MOB M, final Map<String, String> renames)
	{
		final List<String> did = new ArrayList<String>();
		for(final Map.Entry<String, String> e : renames.entrySet())
		{
			if(RulesFiles.renameTattoo(M, PREFIX + e.getKey(), PREFIX + e.getValue()))
				did.add("role " + e.getKey() + " is " + e.getValue() + " now");
		}
		return did;
	}
}
