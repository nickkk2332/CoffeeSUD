package com.planet_ink.coffee_mud.Abilities.Properties;

import java.io.IOException;
import java.util.*;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.MiniJSON.JSONObject;
import com.planet_ink.coffee_mud.core.interfaces.CombatStatSource;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Saved per-modifier records. Their stats are consumed only by the opted-in combat adapter. */
public class Prop_ItemModifiers extends StdAbility implements CombatStatSource
{
    @Override public String ID(){return "Prop_ItemModifiers";}
    @Override public String name(){return "Structured item modifiers";}
    @Override public int classificationCode(){return Ability.ACODE_PROPERTY;}
    @Override protected int canAffectCode(){return Ability.CAN_ITEMS;}
    @Override public CombatRules.Stats combatStats()throws IOException
    {
        if(!OptionalModules.enabled(ItemModifierRules.MODULE))return CombatRules.bonuses("{\"schema\":1}");
        final JSONObject record=ItemModifierRules.parse(text());
        if(affected==null||!record.get("item").equals(InstanceIds.PREFIX+InstanceIds.existing(affected)))throw new IOException("modifier record belongs to another item; use ItemModifiers.copyOf for managed copies");
        if(ItemModifierRules.modifiers(record).size()>ItemModifiers.limit())throw new IOException("item exceeds this campaign's modifier limit");
        final List<CombatRules.Stats> stats=new ArrayList<CombatRules.Stats>();
        for(final JSONObject m:ItemModifierRules.modifiers(record))stats.add(ItemModifierRules.stats((JSONObject)m.get("values")));
        return CombatRules.sumBonuses(stats);
    }
    @Override public String accountForYourself(){return "Tracked modifier rolls; ITEMMODS inspects each source.";}
    @Override public List<String> combatStatDetails()throws IOException
    {
        if(!OptionalModules.enabled(ItemModifierRules.MODULE))return Collections.singletonList("tracked modifiers inactive");
        final List<String> out=new ArrayList<String>();
        for(final JSONObject m:ItemModifierRules.modifiers(ItemModifierRules.parse(text())))
        {final JSONObject pin=(JSONObject)m.get("pin");out.add(pin.get("id")+" v"+pin.get("version")+" tier "+m.get("tier")+": "+ModuleDefinitions.json((JSONObject)m.get("values")));}
        return Collections.unmodifiableList(out);
    }
}
