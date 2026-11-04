package com.planet_ink.coffee_mud.Commands;
import java.util.*;
import com.planet_ink.coffee_mud.core.*;
import com.planet_ink.coffee_mud.core.interfaces.*;
import com.planet_ink.coffee_mud.Abilities.Properties.*;
import com.planet_ink.coffee_mud.Abilities.interfaces.*;
import com.planet_ink.coffee_mud.MOBS.interfaces.*;
/** Inspect, resume, cancel or begin an authored passage. */
public class Journey extends StdCommand
{
    private final String[] access=I(new String[]{"JOURNEY"});
    @Override public String[] getAccessWords() { return access; }
    @Override public boolean execute(final MOB mob, final List<String> commands, final int flags)
    {
        Ability A=mob.getGroupLeader().fetchEffect("Prop_JourneyState");
        if(A==null) A=mob.fetchEffect("Prop_JourneyState");
        final String action=CMParms.combine(commands,1);
        if(A instanceof Travel.Passage)
        {
            final Travel.Passage state=(Travel.Passage)A;
            if(action.equalsIgnoreCase("RESUME")) mob.tell(state.resumeJourney());
            else if(action.equalsIgnoreCase("CANCEL")) state.cancelJourney();
            else mob.tell(state.status());
            return false;
        }
        Environmental host=Combinations.thing(mob,action);
        if(action.length()==0) host=mob.location();
        A=host instanceof Physical?((Physical)host).fetchEffect("Prop_Journey"):null;
        if(A instanceof Travel.PassageSource) { final String error=((Travel.PassageSource)A).begin(mob); if(error!=null) mob.tell(error); }
        else mob.tell(L("You are on no journey. JOURNEY [passage] begins one here."));
        return false;
    }
    @Override public boolean canBeOrdered() { return true; }
}
