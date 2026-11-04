package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Authored passages, activated by entering or using the host, or JOURNEY host. */
public class Prop_Journey extends Property implements Travel.PassageSource
{
    @Override public String ID() { return "Prop_Journey"; }
    @Override public String name() { return "Journey"; }
    @Override protected int canAffectCode() { return CAN_ITEMS|CAN_MOBS|CAN_EXITS|CAN_ROOMS; }
    public String begin(final MOB who)
    {
        return Travel.beginJourney(who,affected,text());
    }
    @Override public void executeMsg(final Environmental host, final CMMsg msg)
    {
        super.executeMsg(host,msg);
        if(!msg.source().isPlayer()) return;
        if(msg.targetMinor()==CMMsg.TYP_ENTER&&(msg.target()==affected||msg.tool()==affected))
            msg.addTrailerRunnable(() -> { final String error=begin(msg.source()); if(error!=null) msg.source().tell(error); });
    }
}
