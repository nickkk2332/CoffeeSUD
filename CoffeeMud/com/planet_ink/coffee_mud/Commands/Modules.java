package com.planet_ink.coffee_mud.Commands;

import java.util.List;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Player-visible module status and an explicitly invoked demonstration, never implicit gameplay. */
public class Modules extends StdCommand
{
    private final String[] access = I(new String[] { "MODULES" });
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean canBeOrdered() { return false; }
    @Override public boolean securityCheck(final MOB mob) { return CMProps.isSinglePlayer(); }
    @Override public boolean execute(final MOB mob, final List<String> commands, final int metaFlags) throws java.io.IOException
    {
        if(commands.size() == 1) mob.tell(OptionalModules.describe(null, false));
        else if(commands.size() == 3 && commands.get(1).equalsIgnoreCase("DEMO"))
        {
            try { mob.tell(OptionalModules.example(mob, commands.get(2))); }
            catch(final java.io.IOException e) { mob.tell("Modules: " + e.getMessage()); }
        }
        else mob.tell("MODULES lists this campaign's modules. MODULES DEMO [ID] invokes an enabled example counter.");
        return false;
    }
}
