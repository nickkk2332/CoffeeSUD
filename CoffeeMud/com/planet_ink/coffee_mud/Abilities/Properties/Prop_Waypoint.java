package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
/** An attunable item or place in a discoverable fast-travel network. */
public class Prop_Waypoint extends Property
{
    @Override public String ID() { return "Prop_Waypoint"; }
    @Override public String name() { return "Waypoint"; }
    @Override protected int canAffectCode() { return CAN_ITEMS|CAN_ROOMS; }
    @Override public void executeMsg(final Environmental host, final CMMsg msg)
    {
        super.executeMsg(host,msg);
        if(msg.source().isPlayer()&&msg.targetMinor()==CMMsg.TYP_ENTER&&msg.target()==CMLib.map().roomLocation(affected)
        &&Travel.option(text(),"DISCOVER","ENTER").equalsIgnoreCase("ENTER")) Waypoints.discover(msg.source(),this,false);
    }
}
