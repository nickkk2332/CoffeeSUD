package com.planet_ink.coffee_mud.Commands;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Touch an authored object, including attunable waystones. */
public class Touch extends StdCommand
{
    private final String[] access=I(new String[]{"TOUCH","FEEL"});
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean execute(final MOB M, final List<String> commands, final int flags)
    {
        if(commands.size()==1)
        {
            boolean found=false;
            for(final com.planet_ink.coffee_mud.Abilities.interfaces.Ability A : Waypoints.here(M)) found=Waypoints.use(M,A.affecting())||found;
            if(!found) M.tell(L("Touch what here?"));
            return false;
        }
        final Environmental waypoint=Waypoints.target(M,CMParms.combine(commands,1));
        final Environmental target=waypoint==null?Scenery.target(M,CMParms.combine(commands,1)):waypoint;
        if(target==null) M.tell(L("Touch what here?"));
        else if(!ViewRules.allowed(target,M)) M.tell("You cannot find that here.");
        else if(!Waypoints.use(M,target)&&!Scenery.sense(M,target,"TOUCH")) {
            // a USE_PROG or a TOUCH combination comes first; the stock TOUCH social matches any
            // target, so trying it first meant those never ran
            if(ScriptEvents.use("TOUCH",M,target,null)) return false;
            if(Combinations.find("TOUCH",target,null)!=null) { Combinations.act(M,"TOUCH",target,null); return false; }
            final com.planet_ink.coffee_mud.Common.interfaces.Social social=CMLib.socials().fetchSocial(commands,true,true);
            if(social!=null) social.invoke(M,commands,target instanceof Physical?(Physical)target:null,false); else M.tell(L("Nothing happens."));
        }
        return false;
    }
    @Override public boolean canBeOrdered() { return true; }
}
