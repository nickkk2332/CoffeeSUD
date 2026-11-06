package com.planet_ink.coffee_mud.Abilities.Skills;

import java.io.IOException;
import java.util.*;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Common.interfaces.CMMsg;
import com.planet_ink.coffee_mud.Items.interfaces.Weapon;
import com.planet_ink.coffee_mud.Locales.interfaces.Room;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.CompositionDefinitions.Compiled;
import com.planet_ink.coffee_mud.core.interfaces.Physical;
import com.planet_ink.coffee_mud.Abilities.Misc.CompositionBurn;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Explicitly learned adapter for authored direct-hit skills; legacy invocations are untouched. */
public class Skill_Compose extends StdAbility
{
    @Override public String ID() { return "Skill_Compose"; }
    @Override public String name() { return "Ability composition"; }
    private static final String[] TRIGGERS = { "COMPOSE" };
    @Override public String[] triggerStrings() { return TRIGGERS; }
    @Override public int abstractQuality() { return Ability.QUALITY_MALICIOUS; }
    @Override protected int canTargetCode() { return Ability.CAN_MOBS; }
    private static final ThreadLocal<Compiled> CAST = new ThreadLocal<Compiled>();
    @Override public int classificationCode() { return CAST.get() != null && CAST.get().tags.contains("spell") ? Ability.ACODE_SPELL | Ability.DOMAIN_EVOCATION : Ability.ACODE_SKILL; }
    @Override public int maxRange()
    { try { return CAST.get() == null ? 5 : (int)Math.floor(CAST.get().value("range")); } catch(final IOException e) { return 0; } }
    @Override public int usageType() { return Ability.USAGE_MANA | Ability.USAGE_MOVEMENT; }
    @Override public int[] usageCost(final MOB mob, final boolean ignoreClassOverride)
    {
        final int[] cost = new int[Ability.USAGEINDEX_TOTAL];
        try
        {
            if(CAST.get() != null) { cost[0] = (int)Math.ceil(CAST.get().value("mana")); cost[1] = (int)Math.ceil(CAST.get().value("movement")); }
        }
        catch(final IOException e) { cost[0] = 10000; cost[1] = 10000; }
        return cost;
    }
    private List<String> args(final List<String> commands)
    {
        final List<String> result = new ArrayList<String>(commands);
        if(!result.isEmpty() && result.get(0).equalsIgnoreCase("COMPOSE")) result.remove(0);
        if(result.size() > 1) result.set(1, result.get(1).toLowerCase(Locale.ROOT));
        if(result.size() > 2 && (result.get(0).equalsIgnoreCase("LINK") || result.get(0).equalsIgnoreCase("UNLINK")))
            result.set(2, result.get(2).toLowerCase(Locale.ROOT));
        return result;
    }
    @Override public double checkedCastingCost(final MOB mob, final List<String> commands)
    {
        final List<String> args = args(commands);
        if(args.isEmpty() || !args.get(0).equalsIgnoreCase("CAST")) return 0;
        try { return args.size() > 1 ? AbilityComposition.compiled(mob, args.get(1)).value("actions") : 1; }
        catch(final IOException e) { return 1; }
    }
    @Override public boolean invoke(final MOB mob, final List<String> commands, final Physical givenTarget, final boolean auto, final int asLevel)
    {
        if(mob == null) return false;
        synchronized(mob)
        {
            try
            {
                AbilityComposition.require();
                if(mob.fetchAbility(ID()) == null) throw new IOException("you have not learned ability composition");
                final List<String> args = args(commands);
                if(args.isEmpty() || args.size() == 1 && args.get(0).equalsIgnoreCase("LIST")) { mob.tell(AbilityComposition.list(mob)); return false; }
                if(args.size() == 2)
                {
                    final String sub = args.get(0).toUpperCase(Locale.ROOT), id = args.get(1);
                    if(sub.equals("SHOW")) { mob.tell(AbilityComposition.show(mob, id)); return false; }
                    if(sub.equals("LEARN")) { AbilityComposition.learn(mob, id); mob.tell("Selected " + id + "; supports cleared, cooldown retained."); return false; }
                    if(sub.equals("FORGET")) { AbilityComposition.forget(mob, id); mob.tell("Forgot " + id + "; cooldown retained."); return false; }
                }
                if(args.size() == 3 && (args.get(0).equalsIgnoreCase("LINK") || args.get(0).equalsIgnoreCase("UNLINK")))
                {
                    if(args.get(0).equalsIgnoreCase("LINK")) AbilityComposition.link(mob, args.get(1), args.get(2)); else AbilityComposition.unlink(mob, args.get(1), args.get(2));
                    mob.tell("Updated supports for " + args.get(1) + "."); return false;
                }
                if(args.size() >= 2 && args.get(0).equalsIgnoreCase("CAST"))
                    return cast(mob, args, givenTarget, asLevel); // even scripted/auto calls pay costs and pass all gates
                mob.tell("COMPOSE [LIST], LEARN/SHOW/FORGET [skill], LINK/UNLINK [skill] [support], CAST [skill] [target].");
            }
            catch(final IOException e) { mob.tell("Composition: " + e.getMessage()); }
            return false;
        }
    }
    private boolean cast(final MOB mob, final List<String> args, final Physical givenTarget, final int asLevel) throws IOException
    {
        if(CAST.get() != null) throw new IOException("composed casts cannot recursively trigger another composed cast");
        final Compiled compiled = AbilityComposition.compiled(mob, args.get(1));
        final Compiled previous = CAST.get(); CAST.set(compiled);
        try
        {
            final MOB target = getTarget(mob, new ArrayList<String>(args.subList(2, args.size())), givenTarget);
            if(!legal(mob, target, mob.location())) return false;
            final CombatProfiles.Cast profile = CombatProfiles.prepare(mob, target);
            AbilityComposition.canCast(mob, compiled.skill.id);
            final int chains = Math.min((int)Math.floor(compiled.value("chains")), AbilityComposition.setting("max_chains", 0, 8));
            final Budget budget = new Budget(AbilityComposition.setting("event_budget", 1, 64));
            final int manaBefore = mob.curState().getMana(), movementBefore = mob.curState().getMovement();
            final boolean invoked = super.invoke(mob, args, target, false, asLevel);
            CombatProfiles.paid(profile, Math.max(0,manaBefore-mob.curState().getMana()), Math.max(0,movementBefore-mob.curState().getMovement()));
            if(!invoked) { CombatProfiles.failed(mob,target,compiled,profile,"Native invocation rejected."); return false; }
            AbilityComposition.cooldown(mob, compiled.skill.id, (int)Math.ceil(compiled.value("cooldown")));
            if(!proficiencyCheck(mob, 0, false))
            {
                CombatProfiles.failed(mob,target,compiled,profile,"Native proficiency fizzle; cooldown retained.");
                return maliciousFizzle(mob, target, "<S-NAME> fail(s) to use " + compiled.skill.name + ".");
            }
            final Room room = mob.location();
            final CMMsg msg = CMClass.getMsg(mob, target, this, CMMsg.MASK_MALICIOUS | CMMsg.MSG_NOISYMOVEMENT, "<S-NAME> use(s) " + compiled.skill.name + " against <T-NAMESELF>!");
            final boolean delivered;
            if(profile != null) delivered=CombatProfiles.announce(mob,target,this,msg);
            else { delivered=room.okMessage(mob,msg); if(delivered) room.send(mob,msg); }
            if(!delivered) { CombatProfiles.failed(mob,target,compiled,profile,"World vetoed or redirected the paid cast."); return false; }
            setInvoker(mob);
            // Snapshot candidates once. New arrivals, recursive effects and duplicate names cannot add hops.
            final List<MOB> targets = new ArrayList<MOB>(); targets.add(target);
            for(int i = 0; i < room.numInhabitants() && targets.size() <= chains; i++)
            {
                final MOB candidate = room.fetchInhabitant(i);
                if(candidate != target && legal(mob, candidate, room)) targets.add(candidate);
            }
            for(final MOB victim : targets)
            {
                if(budget.left <= 0 || !legal(mob, victim, room)) continue;
                if(profile == null) hit(mob, victim, compiled, budget);
                else CombatProfiles.hit(mob, victim, this, compiled, profile, budget);
            }
            return true;
        }
        finally { if(previous == null) CAST.remove(); else CAST.set(previous); }
    }
    private boolean legal(final MOB caster, final MOB target, final Room room)
    {
        if(caster == null || target == null || target == caster || room == null || caster.location() != room || target.location() != room || caster.amDead() || target.amDead() || target.amDestroyed() || Downed.isDown(target) || !caster.mayIFight(target)) return false;
        return !caster.getGroupMembers(new HashSet<MOB>()).contains(target) && CMLib.flags().canBeSeenBy(target, caster);
    }
    private static final class Budget implements CombatProfiles.Budget { int left; Budget(final int n) { left = n; } public boolean take() { if(left <= 0) return false; left--; return true; } }
    private static final int[] MESSAGES = { CMMsg.TYP_JUSTICE, CMMsg.TYP_FIRE, CMMsg.TYP_COLD, CMMsg.TYP_ELECTRIC, CMMsg.TYP_ACID };
    private static final int[] WEAPONS = { Weapon.TYPE_BASHING, Weapon.TYPE_BURNING, Weapon.TYPE_FROSTING, Weapon.TYPE_STRIKING, Weapon.TYPE_MELTING };
    private void hit(final MOB caster, final MOB victim, final Compiled compiled, final Budget budget) throws IOException
    {
        final Room origin = caster.location();
        if(compiled.tags.contains("attack") && !CMLib.combat().rollToHit(caster, victim))
        {
            caster.location().show(caster, victim, this, CMMsg.MSG_NOISYMOVEMENT, compiled.skill.name + " miss(es) <T-NAME>.");
            budget.take(); return;
        }
        final Set<String> conditions = new HashSet<String>();
        if(victim.curState().getHitPoints() < victim.maxState().getHitPoints() / 2.0) conditions.add("target_wounded");
        if(victim.fetchEffect("CompositionBurn") != null) conditions.add("target_burning");
        final Map<String,Double> values = compiled.values(conditions); boolean accepted = false;
        for(int i = 0; i < CompositionDefinitions.TYPES.size(); i++)
        {
            int damage = (int)Math.round(values.get("damage." + CompositionDefinitions.TYPES.get(i)));
            if(damage <= 0 || !legal(caster, victim, origin) || !budget.take()) continue;
            final Room room = victim.location();
            final CMMsg save = CMClass.getMsg(caster, victim, this, CMMsg.MASK_MALICIOUS | (compiled.tags.contains("spell") ? CMMsg.MASK_MAGIC : 0) | MESSAGES[i], null);
            if(!room.okMessage(caster, save)) continue;
            room.send(caster, save);
            if(save.value() > 0) damage = (int)Math.round(damage / 2.0);
            if(damage <= 0) continue;
            final int before = victim.curState().getHitPoints();
            CMLib.combat().postDamage(caster, victim, this, damage, CMMsg.MASK_ALWAYS | MESSAGES[i], WEAPONS[i], compiled.skill.name + " <DAMAGE> <T-NAME>!");
            accepted |= victim.curState().getHitPoints() < before;
        }
        // Every burn attempt uses one event, whether or not its chance roll succeeds.
        if(accepted && legal(caster, victim, origin) && values.get("burn_damage") >= .5 && values.get("burn_ticks") >= 1
            && budget.take() && CMLib.dice().rollPercentage() <= values.get("burn_chance"))
            CompositionBurn.apply(caster, victim, (int)Math.round(values.get("burn_damage")), (int)Math.floor(values.get("burn_ticks")), this);
    }
}
