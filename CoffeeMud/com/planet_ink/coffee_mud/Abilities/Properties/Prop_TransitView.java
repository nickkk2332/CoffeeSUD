package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
/** Unsaved MovingRoom description overlay; the room's authored text stays intact. */
public class Prop_TransitView extends Property
{
    @Override public String ID() { return "Prop_TransitView"; }
    @Override public String name() { return "Transit view"; }
    @Override protected int canAffectCode() { return CAN_ROOMS; }
    @Override public void executeMsg(final Environmental host, final CMMsg msg)
    {
        super.executeMsg(host,msg);
        if(msg.target()==affected&&msg.targetMinor()==CMMsg.TYP_LOOK&&text().length()>0)
            msg.addTrailerRunnable(() -> msg.source().tell(text()));
    }
}
