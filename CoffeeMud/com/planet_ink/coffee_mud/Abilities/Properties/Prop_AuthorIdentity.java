package com.planet_ink.coffee_mud.Abilities.Properties;

/* Copyright 2026 CoffeeSUD contributors. Licensed under the Apache License, Version 2.0. */
/** Internal, saved identities for authored sources that have no explicit ID. */
public class Prop_AuthorIdentity extends Property
{
    @Override public String ID() { return "Prop_AuthorIdentity"; }
    @Override public String name() { return "Author identities"; }
    @Override protected int canAffectCode() { return CAN_ROOMS|CAN_AREAS|CAN_ITEMS|CAN_MOBS|CAN_EXITS; }
}
