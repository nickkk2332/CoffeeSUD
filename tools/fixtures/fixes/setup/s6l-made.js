// S6: the totem carved is the catalog's, with its effect
FIXFILE = "s6l-made";
var made = null;
for (var i = 0; i < M.numItems(); i++) if (String(M.getItem(i).Name()).indexOf("fix totem") >= 0) made = M.getItem(i);
for (var j = 0; (made == null) && (j < M.location().numItems()); j++) if (String(M.location().getItem(j).Name()).indexOf("fix totem") >= 0) made = M.location().getItem(j);
check("made", made != null, "no totem was made");
check("catalog-effect", (made != null) && (made.fetchEffect("Prop_NoPurge") != null), "the totem has not the catalog's effect");
done();
