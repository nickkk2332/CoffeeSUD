package com.planet_ink.coffee_mud.core;
import com.planet_ink.coffee_mud.Abilities.interfaces.Ability;
import com.planet_ink.coffee_mud.Abilities.Properties.Prop_Cursed;
import com.planet_ink.coffee_mud.Items.interfaces.Item;
import com.planet_ink.coffee_mud.MOBS.interfaces.MOB;
/** The identity an examiner can discover, including hidden curse detection. */
public final class ItemIdentity
{
    private ItemIdentity() {}
    public static boolean concealsCurse(final Item item, final MOB examiner)
    {
        final Ability curse=item.fetchEffect("Prop_Cursed");
        return curse instanceof Prop_Cursed && !((Prop_Cursed)curse).detectedBy(examiner);
    }
    public static String describe(final Item item, final MOB examiner)
    {
        if(concealsCurse(item,examiner)) return CMLib.lang().L("@x1 seems normal.",item.name(examiner));
        final String identity=item.secretIdentity();
        if(item.fetchEffect("Prop_Cursed")!=null && !identity.contains("Cursed"))return identity+"\n\r"+CMLib.lang().L("Cursed");
        return identity;
    }
}
