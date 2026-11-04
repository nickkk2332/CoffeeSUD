package com.planet_ink.coffee_mud.Abilities.Properties;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.Common.interfaces.*;
import com.planet_ink.coffee_mud.Items.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Declarative helm access for world-owned ships and caravans. */
public class Prop_HelmRestrict extends Property implements Travel.Helm
{
    @Override public String ID() { return "Prop_HelmRestrict"; }
    @Override public String name() { return "Restricted helm"; }
    @Override protected int canAffectCode() { return Ability.CAN_ITEMS; }
    public boolean allowed(final MOB M, final Boardable ship)
    {
        if(M==null) return false;
        if(CMSecurity.isAllowed(M,M.location(),CMSecurity.SecFlag.CMDITEMS)) return true;
        if(M.isMonster()&&M.fetchBehavior("Sailor")!=null&&M.location()!=null&&M.location().getArea()==ship.getArea()) return true;
        final String mask=Organizations.parameterMask(Travel.option(text(),"NAVMASK",Travel.option(text(),"MASK","")));
        return mask.length()==0||CMLib.masking().maskCheck(mask,M,true);
    }
    @Override public boolean okMessage(final Environmental host, final CMMsg msg)
    {
        if(!super.okMessage(host,msg)) return false;
        if(affected instanceof Boardable&&msg.targetMessage()!=null
        &&(msg.sourceMinor()==CMMsg.TYP_HUH||msg.sourceMinor()==CMMsg.TYP_SKILLFAIL)
        &&CMLib.map().areaLocation(msg.source())==((Boardable)affected).getArea()
        &&msg.targetMessage().matches("(?i)^(?:COURSE|STEER|SAIL|DRIVE|TENDER|RAISE|LOWER|SET|RELEASE|BRAKE|ANCHOR)(?:\\s.*)?$")
        &&!allowed(msg.source(),(Boardable)affected))
        {
            msg.source().tell(Travel.option(text(),"MESSAGE","The crew will not let you use the helm."));
            final String function=Travel.option(text(),"ONFAIL","");
            if(function.length()>0) for(final ScriptingEngine S : ScriptEvents.engines((PhysicalAgent)affected))
                ScriptHooks.call(S,(PhysicalAgent)affected,function,msg.source(),msg.source(),msg.targetMessage());
            return false;
        }
        return true;
    }
}
