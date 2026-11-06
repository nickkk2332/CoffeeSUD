package com.planet_ink.coffee_mud.Commands;

import java.util.List;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
import com.planet_ink.coffee_mud.core.*;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Read-only inspection of this participant's recorded profile-combat events. */
public class CombatResult extends StdCommand
{
    private final String[] access = I(new String[] { "COMBATRESULT" });
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean canBeOrdered() { return false; }
    @Override public double actionsCost(final MOB mob, final List<String> commands) { return 0; }
    @Override public double combatActionsCost(final MOB mob, final List<String> commands) { return 0; }
    @Override public boolean securityCheck(final MOB mob) { return CMProps.isSinglePlayer(); }
    @Override public boolean execute(final MOB mob, final List<String> commands, final int metaFlags) throws java.io.IOException
    {
        try
        {
            CombatProfiles.playerAccess();
            if(commands.size() == 2 && commands.get(1).equalsIgnoreCase("STATS")) mob.tell(CombatProfiles.describeStats(mob));
            else if(commands.size() == 1 || commands.size() == 2 && commands.get(1).matches("[1-8]"))
                mob.tell(CombatProfiles.trace(mob, commands.size() == 1 ? 1 : Integer.parseInt(commands.get(1))));
            else mob.tell("COMBATRESULT [1-8] reads a recent resolved event; COMBATRESULT STATS shows your current profile stats. Neither rolls dice or repeats an event.");
        }
        catch(final java.io.IOException e) { mob.tell("Combat result: " + e.getMessage()); }
        return false;
    }
}
