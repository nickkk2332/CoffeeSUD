package com.planet_ink.coffee_mud.core.interfaces;

import java.io.IOException;
import com.planet_ink.coffee_mud.core.CombatRules;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Shared across the core and CMClass ability loaders. */
public interface CombatStatSource
{
    CombatRules.Stats combatStats() throws IOException;
}
