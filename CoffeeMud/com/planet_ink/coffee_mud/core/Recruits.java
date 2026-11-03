package com.planet_ink.coffee_mud.core;

import java.util.ArrayList;
import java.util.Enumeration;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.CharClasses.interfaces.CharClass;
import com.planet_ink.coffee_mud.Commands.interfaces.Command;
import com.planet_ink.coffee_mud.Common.interfaces.CharState;
import com.planet_ink.coffee_mud.Common.interfaces.CharStats;
import com.planet_ink.coffee_mud.Common.interfaces.PhyStats;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerAccount;
import com.planet_ink.coffee_mud.Common.interfaces.PlayerStats;
import com.planet_ink.coffee_mud.Common.interfaces.Tattoo;
import com.planet_ink.coffee_mud.Items.interfaces.Container;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.collections.XVector;
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
 * P9: an NPC met in the world joins the profile as a player character
 * (MPRECRUIT, RECRUIT with Prop_Recruitable, and this call from JavaScript),
 * keeping its race, class and level, stats, abilities, effects, inventory,
 * factions, tattoos and description; then it joins the party or rests.
 */
public class Recruits
{
	private Recruits()
	{
	}

	/**
	 * The name a recruit takes: NAME=, else the first word of its name that is
	 * not an article.
	 * @param npc the NPC
	 * @param name the name asked for, or null
	 * @return the name
	 */
	public static String nameFor(final MOB npc, final String name)
	{
		String n = (name == null) ? "" : name.trim();
		if(n.length() == 0)
		{
			for(final String w : CMParms.parse(CMStrings.removeColors(npc.Name())))
			{
				if((n.length() == 0) && (!w.equalsIgnoreCase("a")) && (!w.equalsIgnoreCase("an")) && (!w.equalsIgnoreCase("the")))
					n = w;
			}
		}
		n = n.replaceAll("[^A-Za-z]", "");
		return CMStrings.capitalizeAndLower(n);
	}

	/**
	 * Why an NPC cannot join this player's profile now.
	 * @param npc the NPC
	 * @param player the player
	 * @param party true if it is to join the party
	 * @param name the name it would take
	 * @return what to say, or null if it can
	 */
	public static String refusal(final MOB npc, final MOB player, final boolean party, final String name)
	{
		if((npc == null) || (player == null))
			return CMLib.lang().L("There is no one to recruit.");
		if((!npc.isMonster()) || (npc.playerStats() != null))
			return CMLib.lang().L("@x1 is not someone to recruit.", npc.Name());
		if(npc.amDead() || npc.amDestroyed())
			return CMLib.lang().L("@x1 cannot join anyone now.", npc.Name());
		final PlayerAccount acct = Party.profileOf(player);
		if(acct == null)
			return CMLib.lang().L("You have no profile to take @x1 into.", npc.Name());
		if((name.length() < 2) || CMLib.login().isBadName(name))
			return CMLib.lang().L("'@x1' cannot be a character's name; give one with NAME=.", name);
		if(!PlayerTemplates.nameFree(name))
			return CMLib.lang().L("There is a character called @x1 already; give another name with NAME=.", name);
		int max = CMProps.getIntVar(CMProps.Int.COMMONACCOUNTSYSTEM);
		if(max < Integer.MAX_VALUE)
			max += acct.getBonusCharsLimit();
		if(WorldRules.profileMax() > 0)
			max = Math.min(max, WorldRules.profileMax());
		if((acct.numPlayers() >= max) && (!acct.isSet(PlayerAccount.AccountFlag.NUMCHARSOVERRIDE)))
			return CMLib.lang().L("Your profile is full: it may have only @x1 characters.", "" + max);
		if(party)
		{
			final String full = WorldRules.partyRefusal(acct, Party.inOrder(Party.leaderOf(player)).size(), WorldRules.inWorld(acct), true);
			if(full != null)
				return full;
		}
		return null;
	}

	/**
	 * Makes a player character of the profile from an NPC, which leaves the
	 * world; it joins the party, or rests in the profile.
	 * @param npc the NPC
	 * @param player the player whose profile it joins
	 * @param how PARTY or REST
	 * @param askedName NAME=, or null
	 * @return null when it joined, else what stopped it
	 */
	public static String recruit(final MOB npc, final MOB player, final String how, final String askedName)
	{
		final boolean party = (how == null) || (!how.toUpperCase().startsWith("REST"));
		final String name = nameFor(npc, askedName);
		final String why = refusal(npc, player, party, name);
		if(why != null)
			return why;
		final PlayerAccount acct = Party.profileOf(player);
		final MOB M = CMClass.getMOB("StdMOB");
		M.setName(name);
		final PlayerStats ps = (PlayerStats)CMClass.getCommon("DefaultPlayerStats");
		M.setPlayerStats(ps);
		ps.setAccount(acct);
		ps.setPassword(acct.getPasswordStr());
		ps.setLastUpdated(System.currentTimeMillis());
		ps.setLastDateTime(System.currentTimeMillis());
		M.setBaseCharStats((CharStats)npc.baseCharStats().copyOf());
		final int level = Math.max(1, npc.basePhyStats().level());
		CharClass C = M.baseCharStats().getCurrentClass();
		if((C == null) || C.ID().equals("StdCharClass"))
		{
			C = CMClass.getCharClass("Apprentice");
			if(C == null)
				C = CMClass.getCharClass("StdCharClass");
			M.baseCharStats().setCurrentClass(C);
		}
		if(M.baseCharStats().getClassLevel(C) < 1)
			M.baseCharStats().setClassLevel(C, level);
		final PhyStats P = (PhyStats)npc.basePhyStats().copyOf();
		P.setArmor(100);
		P.setAttackAdjustment(0);
		P.setDamage(0);
		P.setDisposition(0);
		P.setLevel(level);
		P.setRejuv(PhyStats.NO_REJUV);
		M.setBasePhyStats(P);
		M.setBaseState((CharState)npc.baseState().copyOf());
		M.setDescription(npc.description());
		M.setDisplayText(CMLib.lang().L("@x1 is here.", name));
		for(final Enumeration<Ability> a = npc.abilities(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if((A != null) && (M.fetchAbility(A.ID()) == null))
				M.addAbility((Ability)A.copyOf());
		}
		for(final Enumeration<Ability> a = npc.effects(); a.hasMoreElements();)
		{
			final Ability A = a.nextElement();
			if((A == null) || (!A.isSavable()) || A.ID().equals("Prop_Recruitable") || A.ID().equals("Prop_ModExperience"))
				continue;
			final Ability N = (Ability)A.copyOf();
			M.addEffect(N);
			N.setAffectedOne(M);
		}
		final Map<Item, Item> copies = new IdentityHashMap<Item, Item>();
		final List<Item> items = new ArrayList<Item>();
		for(int i = 0; i < npc.numItems(); i++)
		{
			final Item I = npc.getItem(i);
			if(I != null)
				items.add(I);
		}
		for(final Item I : items)
		{
			final Item N = (Item)I.copyOf();
			copies.put(I, N);
			M.addItem(N);
			N.setRawWornCode(I.rawWornCode());
		}
		for(final Item I : items)
		{
			if((I.container() != null) && (copies.get(I.container()) instanceof Container))
				copies.get(I).setContainer((Container)copies.get(I.container()));
		}
		for(final Enumeration<String> f = npc.factions(); f.hasMoreElements();)
		{
			final String id = f.nextElement();
			M.addFaction(id, npc.fetchFaction(id));
		}
		for(final Enumeration<Tattoo> t = npc.tattoos(); t.hasMoreElements();)
			M.addTattoo(t.nextElement().getTattooName());
		for(final Enumeration<String> e = npc.expertises(); e.hasMoreElements();)
			M.addExpertise(e.nextElement());
		final Room where = npc.location();
		M.setStartRoom((player.getStartRoom() != null) ? player.getStartRoom() : where);
		M.setLocation(where);
		M.recoverCharStats();
		M.recoverPhyStats();
		M.recoverMaxState();
		M.resetToMaxState();
		CMLib.database().DBCreateCharacter(M);
		CMLib.players().addPlayer(M);
		acct.addNewPlayer(M);
		CMLib.database().DBUpdateAccount(acct);
		CMLib.database().DBUpdatePlayer(M);
		M.setLocation(null);
		npc.setFollowing(null);
		npc.destroy();
		if(party)
		{
			final Command PC = CMClass.getCommand("PartyCmd");
			try
			{
				if(PC != null)
					PC.execute(player, new XVector<String>("PARTY", "ADD", name), 0);
			}
			catch(final java.io.IOException e)
			{
				return e.getMessage();
			}
		}
		else
			player.tell(CMLib.lang().L("@x1 rests in your profile (PARTY ADD @x1 brings them in).", name));
		return null;
	}

	/**
	 * RECRUIT [NPC]: an NPC with Prop_Recruitable here joins the party, if its
	 * MASK lets the player in and the player pays its COST in gold.
	 * @param player the player
	 * @param npc the NPC
	 * @return null when it joined, else what to say
	 */
	public static String recruitHere(final MOB player, final MOB npc)
	{
		final Ability A = (npc == null) ? null : npc.fetchEffect("Prop_Recruitable");
		if(A == null)
			return CMLib.lang().L("@x1 has no wish to join you.", (npc == null) ? "No one" : npc.Name());
		final String text = A.text();
		final String mask = CMParms.getParmStr(text, "MASK", "").trim();
		if((mask.length() > 0) && (!CMLib.masking().maskCheck(mask, player, true)))
			return CMLib.lang().L("@x1 will not join you.", npc.Name());
		final double cost = CMath.s_double(CMParms.getParmStr(text, "COST", "0"));
		if((cost > 0) && (CMLib.beanCounter().getTotalAbsoluteNativeValue(player) < cost))
			return CMLib.lang().L("@x1 wants @x2 to join you.", npc.Name(), CMLib.beanCounter().nameCurrencyShort(player, cost));
		final String why = refusal(npc, player, true, nameFor(npc, CMParms.getParmStr(text, "NAME", "")));
		if(why != null)
			return why;
		final Room R = npc.location();
		final String msg = CMParms.getParmStr(text, "MESSAGE", "").trim();
		if(R != null)
			R.show(npc, player, com.planet_ink.coffee_mud.Common.interfaces.CMMsg.MSG_OK_ACTION,
				(msg.length() > 0) ? msg : CMLib.lang().L("<S-NAME> agree(s) to travel with <T-NAMESELF>."));
		if(cost > 0)
			CMLib.beanCounter().subtractMoney(player, cost);
		return recruit(npc, player, "PARTY", CMParms.getParmStr(text, "NAME", ""));
	}
}
