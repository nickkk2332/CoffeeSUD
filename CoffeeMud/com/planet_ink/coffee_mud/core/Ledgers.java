package com.planet_ink.coffee_mud.core;

import java.io.IOException;
import java.util.*;

import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.interfaces.Physical;

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
 * Q05: ledgers, LEDGER: what the optional module families hold for a
 * character, an item, a relationship between two, and an expedition, in plain
 * text: who controls it, what it has been granted and by which rule, its
 * pending transactions, its recent events with their sources, and the last
 * refused transition with its owner, rule and recovery.  Reading a ledger
 * changes nothing: it reads module state without making records, and draws
 * no random value.
 */
public final class Ledgers
{
	private Ledgers()
	{
	}

	private interface Part
	{
		String text() throws IOException;
	}

	private static void section(final StringBuilder s, final String module, final Part p)
	{
		if(!OptionalModules.enabled(module))
			return;
		try
		{
			final String t = p.text();
			if((t != null) && (t.trim().length() > 0))
				s.append("\n[").append(module).append("]\n  ").append(t.trim().replace("\n", "\n  "));
		}
		catch(final IOException | RuntimeException e)
		{
			s.append("\n[").append(module).append("] unreadable: ").append(e.getMessage());
		}
	}

	private static void provenance(final StringBuilder s, final Physical P)
	{
		final List<Provenance.Event> recent = Provenance.recent(P);
		s.append("\nRecent events (since this game began or was restored):");
		if(recent.isEmpty())
			s.append(" none");
		for(final Provenance.Event e : recent)
			s.append("\n  ").append(e.describe());
		final Provenance.Refusal r = Provenance.lastRefusal(P);
		if(r != null)
			s.append("\nLast refused: ").append(r.describe());
	}

	/** who controls a character */
	public static String controller(final MOB mob)
	{
		if(mob.playerStats() == null)
			return (mob.amFollowing() != null) ? "the world; follows " + mob.amFollowing().Name() : "the world (not a player character)";
		final StringBuilder s = new StringBuilder();
		s.append((mob.session() != null) ? "played at a terminal" : "a player character not played at a terminal now");
		if(Party.profileOf(mob) != null)
			s.append(", of the profile ").append(Party.profileOf(mob).getAccountName());
		final MOB leader = Party.leaderOf(mob);
		if((leader != null) && (leader != mob))
			s.append("; in the party led by ").append(leader.Name());
		if(Party.isOnTheirOwn(mob))
			s.append("; on their own");
		return s.toString();
	}

	/** LEDGER [character] */
	public static String character(final MOB mob)
	{
		final StringBuilder s = new StringBuilder("Ledger of ").append(mob.Name()).append(" (").append(InstanceIds.reference(mob)).append("):");
		s.append("\nController: ").append(controller(mob));
		section(s, CompositionDefinitions.MODULE, () -> AbilityComposition.list(mob));
		section(s, NamedResources.MODULE, () -> NamedResources.describe(mob));
		section(s, AbilityTriggers.MODULE, () -> AbilityTriggers.describe(mob));
		section(s, CombatRules.MODULE, () -> CombatProfiles.describeStats(mob));
		section(s, RewardEscrow.MODULE, () -> RewardEscrow.describe(mob));
		section(s, ProgressionTracks.MODULE, () -> ProgressionTracks.describe(mob));
		section(s, ItemModifiers.MODULE, () -> "Pending transactions: " + ItemCrafting.status(mob));
		section(s, Expeditions.MODULE, () -> Expeditions.status(mob));
		phase2(s, mob);
		provenance(s, mob);
		return s.toString();
	}

	/** phase 2's state of a character, where it has any: their culture, their talents, their formation role */
	private static void phase2(final StringBuilder s, final MOB mob)
	{
		final Origins.Origin C = Cultures.current(mob);
		if(C != null)
		{
			final List<String> gave = Cultures.gave(mob);
			s.append("\nCulture: ").append(C.name()).append(gave.isEmpty() ? "" : "; from it: " + String.join(", ", gave));
		}
		final List<String> talents = new ArrayList<String>();
		for(final Talents.Talent t : Talents.talents().values())
		{
			final String d = Talents.decided(mob, t.id);
			if(d.length() > 0)
				talents.add(t.title + " (" + d + ")");
		}
		if(!talents.isEmpty())
			s.append("\nTalents: ").append(String.join(", ", talents));
		final Origins.Origin R = FormationRoles.of(mob);
		if(R != null)
			s.append("\nFormation role: ").append(R.name()).append(" (").append(FormationRoles.explain(R)).append(")");
	}

	/** LEDGER ITEM [item] */
	public static String item(final Item I)
	{
		final StringBuilder s = new StringBuilder("Ledger of ").append(I.Name()).append(" (").append(InstanceIds.reference(I)).append("):");
		s.append("\nHeld by: ").append((I.owner() == null) ? "nothing" : I.owner().Name())
		 .append(I.container() != null ? ", in " + I.container().Name() : "").append(I.amWearingAt(Item.IN_INVENTORY) ? "" : ", worn or wielded");
		if(ItemModifiers.isManaged(I))
		{
			section(s, ItemModifiers.MODULE, () -> {
				final JSONObject d = ItemModifierRules.parse(I.fetchEffect(ItemModifiers.PROPERTY).text());
				final JSONObject base = (JSONObject)d.get("base");
				final StringBuilder t = new StringBuilder("Base ").append(base.get("id")).append(" v").append(base.get("version")).append(", revision ").append(d.get("revision"))
					.append(", made by ").append(d.get("source"));
				for(final JSONObject m : ItemModifierRules.modifiers(d))
				{
					final JSONObject pin = (JSONObject)m.get("pin");
					t.append("\n").append(m.get("name")).append(": from ").append(pin.get("id")).append(" v").append(pin.get("version"))
					 .append(Boolean.TRUE.equals(m.get("protected")) ? ", protected" : "").append("; ").append(ModuleDefinitions.json((JSONObject)m.get("values")));
				}
				return t.toString();
			});
		}
		final Ability order = I.fetchEffect("Prop_WorkOrder");
		if(order != null)
			section(s, ItemModifiers.MODULE, () -> "A work piece: " + order.text());
		final Ability chart = I.fetchEffect("Prop_ExpeditionChart");
		if(chart != null)
			section(s, Expeditions.MODULE, () -> "An expedition chart: " + chart.text());
		if(I.fetchEffect("Prop_Tracks") != null)
			section(s, ProgressionTracks.MODULE, () -> {
				final String tree = ProgressionTracks.tree(ProgressionTracks.of(I));
				return tree.trim().isEmpty() ? "Tracks it carries: " + String.join(", ", ProgressionTracks.itemTracks(I)) + "; none earned yet" : tree;
			});
		final Ability bind = I.fetchEffect("Prop_ItemBinder");
		if(bind != null)
			s.append("\nBinding: ").append(bind.text().trim().isEmpty() ? "on its defaults" : bind.text().trim());
		final String kind = ItemKnowledge.kind(I);
		if(!kind.isEmpty())
			s.append("\nOf a kind one must learn: ").append(kind).append(" (shown as ").append(ItemKnowledge.alias(I)).append(" until then)");
		provenance(s, I);
		return s.toString();
	}

	/** LEDGER BETWEEN [a] [b]: what each module keeps between two, in each order */
	public static String between(final Physical a, final Physical b) throws IOException
	{
		final StringBuilder s = new StringBuilder("Between ").append(a.Name()).append(" and ").append(b.Name()).append(':');
		if((a instanceof MOB) && (b instanceof MOB))
		{
			final MOB x = (MOB)a, y = (MOB)b;
			if(x.amFollowing() == y)
				s.append("\n").append(x.Name()).append(" follows ").append(y.Name()).append('.');
			if(y.amFollowing() == x)
				s.append("\n").append(y.Name()).append(" follows ").append(x.Name()).append('.');
			if(Party.leaderOf(x) == Party.leaderOf(y))
				s.append("\nThey are in one party, led by ").append(Party.leaderOf(x).Name()).append('.');
		}
		int n = 0;
		for(final Physical[] pair : new Physical[][] { { a, b }, { b, a } })
		{
			final Map<String, JSONObject> kept = OptionalModules.stored(OptionalModules.relationship(pair[0], pair[1]));
			for(final Map.Entry<String, JSONObject> e : kept.entrySet())
			{
				s.append("\n[").append(e.getKey()).append("] ").append(pair[0].Name()).append(" to ").append(pair[1].Name()).append(": ").append(ModuleDefinitions.json(e.getValue()));
				n++;
			}
		}
		if(n == 0)
			s.append("\nNo module keeps a record between them.");
		return s.toString();
	}

	/** LEDGER EXPEDITION: a character's run, with what can replay its seed */
	public static String expedition(final MOB mob) throws IOException
	{
		if(!OptionalModules.enabled(Expeditions.MODULE))
			throw new IOException("expeditions (" + Expeditions.MODULE + ") are not active in this campaign");
		final Map.Entry<String, JSONObject> run = Expeditions.runOfCharacter(mob);
		if(run == null)
			return "You are on no expedition." + (Provenance.lastRefusal(mob) != null ? "\nLast refused: " + Provenance.lastRefusal(mob).describe() : "");
		final JSONObject r = run.getValue();
		final StringBuilder s = new StringBuilder("Ledger of expedition ").append(run.getKey()).append(':');
		final JSONObject exp = (JSONObject)r.get("exp");
		s.append("\nDefinition: ").append(exp.get("id")).append(" v").append(exp.get("version")).append(" (").append(exp.get("hash")).append(')');
		s.append("\nStatus: ").append(r.get("status")).append("; attempts left ").append(r.get("left")).append("; tier ").append(r.get("tier"));
		s.append("\nSeed: ").append(r.get("seed"));
		if(r.get("scenario") instanceof JSONObject)
		{
			final JSONObject sc = (JSONObject)r.get("scenario");
			s.append("; drawn from the ").append(sc.get("stream")).append(" stream at draw ").append(sc.get("counter")).append(" of the campaign seed ").append(sc.get("seed"))
			 .append(" (WORLD MODULES STREAMS; a cursor there draws it again)");
		}
		s.append("\nModifiers: ").append(ModuleDefinitions.json(r.get("mods")));
		s.append("\nInstance: ").append(String.valueOf(r.get("instance")).isEmpty() ? "not laid out yet" : r.get("instance"));
		s.append("\nParty:");
		for(final Object o : (Object[])r.get("party"))
		{
			final Object M = InstanceIds.find(String.valueOf(o));
			s.append(' ').append((M instanceof MOB) ? ((MOB)M).Name() : "(not in the world: " + o + ")").append(';');
		}
		provenance(s, mob);
		return s.toString();
	}
}
