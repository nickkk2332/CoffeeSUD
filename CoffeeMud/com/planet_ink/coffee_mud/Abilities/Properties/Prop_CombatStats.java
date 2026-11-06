package com.planet_ink.coffee_mud.Abilities.Properties;

import java.io.IOException;
import com.planet_ink.coffee_mud.Abilities.StdAbility;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.core.CombatRules;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Additive stats consumed only by opted-in profile combat; never mutates ordinary PhyStats/CharStats. */
public class Prop_CombatStats extends StdAbility implements com.planet_ink.coffee_mud.core.interfaces.CombatStatSource
{
    @Override public String ID() { return "Prop_CombatStats"; }
    @Override public String name() { return "Combat profile stats"; }
    @Override public int classificationCode() { return Ability.ACODE_PROPERTY; }
    @Override protected int canAffectCode() { return Ability.CAN_MOBS | Ability.CAN_ITEMS; }
    private CombatRules.Stats bonuses;
    private String problem = "";
    public Prop_CombatStats() { setMiscText("{\"schema\":1}"); }
    @Override public synchronized void setMiscText(final String text)
    {
        super.setMiscText(text); bonuses = null; problem = "";
        try
        {
            if(text.length() > 8192) throw new IOException("combat stat property exceeds 8192 characters");
            bonuses = CombatRules.bonuses(text);
        }
        catch(final IOException e) { problem = e.getMessage(); }
    }
    public synchronized CombatRules.Stats bonuses() throws IOException
    { if(bonuses == null) throw new IOException("Prop_CombatStats: " + problem); return bonuses; }
    @Override public CombatRules.Stats combatStats() throws IOException { return bonuses(); }
    @Override public synchronized String accountForYourself() { return bonuses == null ? "Invalid combat profile stats: " + problem : "Profile combat only: " + bonuses.describe(); }
}
