// S6, in builder mode: a catalog item, a fix totem, that keeps from purging
FIXFILE = "s6l-build";
var T = CMClass.getItem("GenItem");
T.setName("a fix totem");
T.setDisplayText("A fix totem stands here.");
T.addNonUninvokableEffect(CMClass.getAbility("Prop_NoPurge"));
CMLib.catalog().addCatalog(T);
check("catalog", CMLib.catalog().getCatalogItem("a fix totem") != null, "not in the catalog");
done();
