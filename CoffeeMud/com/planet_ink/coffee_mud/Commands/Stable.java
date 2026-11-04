package com.planet_ink.coffee_mud.Commands;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.Behaviors.Nanny;
import com.planet_ink.coffee_mud.Behaviors.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Local and sister-stable services, using Nanny's existing saved records. */
public class Stable extends StdCommand
{
    private final String[] access=I(new String[]{"STABLE","RETRIEVE"});
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean execute(final MOB M, final List<String> commands, final int flags)
    {
        if(M.location()==null) return false;
        for(final Enumeration<MOB> e=M.location().inhabitants();e.hasMoreElements();)
        {
            final MOB keeper=e.nextElement(); final Behavior B=keeper.fetchBehavior("Nanny");
            if(B instanceof Travel.StableService)
            {
                String request=CMParms.combine(commands,0);
                if(commands.size()==1||commands.get(1).equalsIgnoreCase("LIST")) request="list";
                M.tell(((Travel.StableService)B).stable(keeper,M,request)); return false;
            }
        }
        M.tell(L("There is no stable keeper here.")); return false;
    }
    @Override public boolean canBeOrdered() { return true; }
}
