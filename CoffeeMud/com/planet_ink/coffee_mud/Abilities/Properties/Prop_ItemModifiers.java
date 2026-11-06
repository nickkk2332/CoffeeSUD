package com.planet_ink.coffee_mud.Abilities.Properties;

import java.io.IOException;
import java.util.*;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
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
    /** why the bonuses are withheld, as last logged */
    private String withheld="";
    /**
     * The item's own modifiers, or null: a generic copy (bound to another item), a corrupt record or one over the
     * campaign's limit grants nothing, logged once and named by WORLD CHECK ONLY=itemmods, and its wearer's combat goes on.
     */
    private List<CombatRules.Stats> granted()
    {
        try
        {
            if(!(affected instanceof Item))throw new IOException("not on an item");
            final List<JSONObject> mods=ItemModifierRules.modifiers(ItemModifiers.bound((Item)affected,text()));
            if(mods.size()>ItemModifiers.limit())throw new IOException("more modifiers than this campaign's limit of "+ItemModifiers.limit());
            final List<CombatRules.Stats> stats=new ArrayList<CombatRules.Stats>();
            for(final JSONObject m:mods)stats.add(ItemModifierRules.stats((JSONObject)m.get("values")));
            withheld="";return stats;
        }
        catch(final IOException|RuntimeException e)
        {
            final String why=String.valueOf(e.getMessage());
            if(!why.equals(withheld)){withheld=why;Log.warnOut(ID(),(affected==null?"(nothing)":affected.Name())+": tracked modifier bonuses withheld: "+why);}
            return null;
        }
    }
    @Override public CombatRules.Stats combatStats()throws IOException
    {
        final List<CombatRules.Stats> stats=OptionalModules.enabled(ItemModifierRules.MODULE)?granted():null;
        return stats==null?CombatRules.bonuses("{\"schema\":1}"):CombatRules.sumBonuses(stats);
    }
    @Override public String accountForYourself(){return "Tracked modifier rolls; ITEMMODS inspects each source.";}
    @Override public List<String> combatStatDetails()throws IOException
    {
        if(!OptionalModules.enabled(ItemModifierRules.MODULE))return Collections.singletonList("tracked modifiers inactive");
        if(granted()==null)return Collections.singletonList("tracked modifiers withheld: "+withheld);
        final List<String> out=new ArrayList<String>();
        for(final JSONObject m:ItemModifierRules.modifiers(ItemModifierRules.parse(text())))
        {final JSONObject pin=(JSONObject)m.get("pin");out.add(pin.get("id")+" v"+pin.get("version")+" tier "+m.get("tier")+": "+ModuleDefinitions.json((JSONObject)m.get("values")));}
        return Collections.unmodifiableList(out);
    }
}
