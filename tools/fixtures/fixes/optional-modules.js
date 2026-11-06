FIXFILE = "optional-modules";
var MD = CM.core.ModuleDefinitions, OM = CM.core.OptionalModules;
var Files = java.nio.file.Files;
var directory = Files.createTempDirectory("sud-module-unit-").toFile();
var modules = new java.io.File(directory, "modules"); modules.mkdirs();
function base(id) {
    return {schema:1,id:id,title:id,version:1,clock_modes:["TURNS","REALTIME"],
        scopes:["world","character","profile","item","relationship","organization"],
        settings:{step:{type:"integer",default:1,min:1,max:10},label:{type:"string",default:"étoile 火",max_length:100}},
        state:{count:{type:"integer",default:0,min:0,max:1000}},on_disable:"suspend"};
}
function write(id, definition) {
    var f = new java.io.File(modules,id + ".json");
    Files.write(f.toPath(),new java.lang.String(JSON.stringify(definition)).getBytes("UTF-8"));
}
function raw(id, text) { Files.write(new java.io.File(modules,id+".json").toPath(),new java.lang.String(text).getBytes("UTF-8")); }
function clear() { var files=modules.listFiles();for(var i=0;i<files.length;i++) files[i].delete(); }
function plan(enabled, settings) { return MD.load(directory, enabled || "", settings || ""); }
function throws(name, fn) {
    var caught=false;try{fn();}catch(e){caught=true;}
    check(name,caught,"expected refusal");
}
function valid(p) { check("valid-plan",p.errors.isEmpty(),String(p.problems())); }
function values(n) { return MD.parse('{"count":'+n+'}'); }
function expectError(name, p, text) { check(name,String(p.problems()).indexOf(text)>=0,String(p.problems())); }
var oldTurns=CM.core.GameTime.isTurnBased(), oldPause=CM.core.GameTime.isPaused();
try {
    check("json-literal-boundaries",MD.parse('{"value":true}').get("value")==java.lang.Boolean.TRUE
        &&MD.parse('{"value":false}').get("value")==java.lang.Boolean.FALSE
        &&new CM.core.MiniJSON().parse("null")==CM.core.MiniJSON.NULL,"boolean/null boundary parse");
    var core=base("test.core"), feature=base("test.feature");
    core.provides=["counter"];feature.requires={"test.core":1};feature.references=[{module:"test.core",name:"counter"}];
    write(core.id,core);write(feature.id,feature);
    var off=plan(""); valid(off);check("default-off",off.enabled.isEmpty(),String(off.enabled));
    var active=plan("test.feature");valid(active);
    check("dependency-selected",active.enabled.contains("test.core")&&active.enabled.contains("test.feature"),String(active.enabled));
    check("dependency-provenance",active.reasons.get("test.core").contains("test.feature"),String(active.reasons));
    expectError("unknown-selection",plan("missing.feature"),"unknown module");
    expectError("duplicate-selection",plan("test.feature,test.feature"),"duplicate selected");
    feature.requires["test.core"]=2;write(feature.id,feature);expectError("exact-dependency-version",plan("test.feature"),"requires test.core version 2");
    feature.requires["test.core"]=1;core.requires={"test.feature":1};write(feature.id,feature);write(core.id,core);expectError("dependency-cycle",plan("test.feature"),"dependency cycle");delete core.requires;write(core.id,core);
    core.conflicts=["test.feature"];write(core.id,core);expectError("asymmetric-conflict",plan("test.feature"),"enabled conflict");delete core.conflicts;write(core.id,core);
    feature.references[0].name="missing";write(feature.id,feature);expectError("unresolved-reference",plan("test.feature"),"unresolved");feature.references[0].name="counter";
    delete feature.requires;write(feature.id,feature);expectError("undeclared-reference",plan("test.feature"),"undeclared");feature.requires={"test.core":1};write(feature.id,feature);
    expectError("typed-setting",plan("test.feature",'{"test.feature":{"step":"two"}}'),"integer");
    expectError("setting-bounds",plan("test.feature",'{"test.feature":{"step":11}}'),"bounds");
    expectError("unknown-setting",plan("test.feature",'{"test.feature":{"unknown":2}}'),"unknown field");
    expectError("unknown-setting-module",plan("test.feature",'{"typo":{}}'),"unknown module");
    raw("duplicate",'{"schema":1,"schema":1}');expectError("duplicate-json-key",plan(""),"duplicate JSON key");new java.io.File(modules,"duplicate.json").delete();
    raw("trailing",JSON.stringify(base("trailing"))+" trailing");expectError("trailing-json",plan(""),"invalid JSON");new java.io.File(modules,"trailing.json").delete();
    var bad=base("bad");bad.schema=2;write("bad",bad);expectError("unsupported-schema",plan(""),"unsupported manifest");new java.io.File(modules,"bad.json").delete();
    bad=base("bad");bad.unknown=true;write("bad",bad);expectError("unknown-definition-field",plan(""),"unknown field");new java.io.File(modules,"bad.json").delete();
    bad=base("other");write("bad",bad);expectError("filename-identity",plan(""),"filename must be other.json");new java.io.File(modules,"bad.json").delete();
    bad=base("bad");bad.clock_modes=["WALLCLOCK"];write("bad",bad);expectError("unsupported-clock",plan(""),"clock_modes");new java.io.File(modules,"bad.json").delete();
    // A draft, a stale manifest or a leftover setting the selection does not use warns; it stops no campaign.
    raw("draft",'{"schema":1,');var stale=base("stale");stale.requires={"test.core":9};write(stale.id,stale);
    var user=base("dep.user");user.requires={"draft":1};write(user.id,user);
    var drafted=plan("test.feature",'{"gone.module":{"step":1},"stale":{"step":99}}'),warned=String(drafted.warnings());
    check("unselected-problems-warn",drafted.blocking.isEmpty()&&warned.indexOf("modules/draft.json")>=0&&warned.indexOf("requires test.core version 9")>=0
        &&warned.indexOf("unknown module gone.module")>=0&&warned.indexOf("modules/stale.json settings.step")>=0&&String(drafted.problems()).indexOf("modules/draft.json")>=0,String(drafted.problems())+" / blocking: "+String(drafted.blocking));
    var started=false;try{OM.prepare(drafted,null,false);OM.prepare(drafted,null,true);started=true;}catch(e){started=String(e);}
    check("unselected-draft-campaign-starts",started===true,"a draft stopped a campaign that does not select it: "+started);
    check("selected-broken-module-blocks",!plan("draft").blocking.isEmpty()&&!plan("stale").blocking.isEmpty()&&!plan("test.feature",'{"test.feature":{"step":99}}').blocking.isEmpty(),"a selected broken module would start");
    check("selected-dependency-file-error-blocks",String(plan("dep.user").blocking).indexOf("modules/draft.json")>=0,String(plan("dep.user").blocking));
    throws("selected-problem-refuses-start",function(){OM.prepare(plan("stale"),null,false);});
    new java.io.File(modules,"draft.json").delete();new java.io.File(modules,"stale.json").delete();new java.io.File(modules,"dep.user.json").delete();
    // A UTF-8 byte order mark, as some editors write, is not part of the JSON.
    Files.write(new java.io.File(modules,"bom.json").toPath(),new java.lang.String("\uFEFF"+JSON.stringify(base("bom"))).getBytes("UTF-8"));
    var bom=plan("bom");check("utf8-bom-manifest-loads",bom.errors.isEmpty()&&bom.definitions.containsKey("bom"),String(bom.problems()));
    new java.io.File(modules,"bom.json").delete();
    // The fingerprint is of the contract's meaning: defaults written out, set orders, key order, title and layout do not change it.
    var plainFp=base("fp.check");plainFp.settings.label.choices=["étoile 火","b"];plainFp.settings.note={type:"string",default:""};
    write(plainFp.id,plainFp);var fpPlan=plan("fp.check"),fpBefore=String(fpPlan.definitions.get("fp.check").fingerprint);
    var fpGame=String(OM.prepare(fpPlan,null,false).snapshot());
    var spelled={state:{count:{max:1000,min:0,"default":0,type:"integer"}},on_disable:"suspend",handler:"data",requires:{},conflicts:[],provides:[],references:[],
        settings:{note:{choices:[],max_length:4096,"default":"",type:"string"},label:{max_length:100,choices:["b","étoile 火"],type:"string","default":"étoile 火"},step:{max:10,min:1,type:"integer","default":1}},
        scopes:["organization","relationship","item","profile","character","world"],clock_modes:["REALTIME","TURNS"],title:"Renamed",version:1,id:"fp.check",schema:1};
    Files.write(new java.io.File(modules,"fp.check.json").toPath(),new java.lang.String(JSON.stringify(spelled,null,2)).getBytes("UTF-8"));
    check("canonical-fingerprint",String(plan("fp.check").definitions.get("fp.check").fingerprint)===fpBefore,"a meaning-free edit changed the fingerprint");
    check("canonical-fingerprint-restores",String(OM.prepare(plan("fp.check"),fpGame,true).problem("fp.check"))==="","a meaning-free edit suspended the save");
    spelled.settings.step["default"]=2;Files.write(new java.io.File(modules,"fp.check.json").toPath(),new java.lang.String(JSON.stringify(spelled)).getBytes("UTF-8"));
    check("fingerprint-keeps-meaning",String(plan("fp.check").definitions.get("fp.check").fingerprint)!==fpBefore,"a changed default kept the fingerprint");
    new java.io.File(modules,"fp.check.json").delete();
    var configured=plan("test.feature",'{"test.feature":{"step":2,"label":"étoile 火"}}');valid(configured);
    var game=OM.prepare(configured,null,false);
    var alice=OM.owner("character","@instance:alice"),bob=OM.owner("character","@instance:bob");
    game.put("test.feature",alice,values(7));game.put("test.feature",bob,values(3));
    check("character-isolation",Number(game.get("test.feature",alice).get("count"))===7&&Number(game.get("test.feature",bob).get("count"))===3,"cross-character state");
    var owners=[OM.world(),OM.owner("profile","@profile:one"),OM.owner("item","@instance:tool"),OM.owner("organization","@organization:research"),OM.owner("relationship",'["@instance:alice","@instance:bob"]')];
    for(var i=0;i<owners.length;i++)game.put("test.feature",owners[i],values(i+10));
    var changed=game.get("test.feature",alice);changed.put("count",java.lang.Long.valueOf(99));
    check("get-is-defensive-copy",Number(game.get("test.feature",alice).get("count"))===7,"read modified durable state");
    throws("state-type-refused",function(){game.put("test.feature",alice,MD.parse('{"count":1.5}'));});
    throws("state-bound-refused",function(){game.put("test.feature",alice,values(1001));});
    var decimal=values(0);decimal.put("count",new java.math.BigDecimal("1.5"));
    throws("java-decimal-is-not-integer",function(){game.put("test.feature",alice,decimal);});
    decimal.put("count",new java.math.BigInteger("18446744073709551617"));
    throws("java-big-integer-does-not-wrap",function(){game.put("test.feature",alice,decimal);});

    check("failed-write-atomic",Number(game.get("test.feature",alice).get("count"))===7,"invalid write committed");
    game.put("test.core",alice,values(19));check("module-state-isolation",Number(game.get("test.feature",alice).get("count"))===7,"module namespaces overlapped");
    var first=CMClass.getItem("GenItem");var oneOwner=OM.item(first);var copied=first.copyOf();
    var copyOwner=OM.item(copied);check("copied-item-identity",String(oneOwner.key)!==String(copyOwner.key),"copied item shares state owner");
    check("relationship-roles",String(OM.relationship(first,copied).key)!==String(OM.relationship(copied,first).key),"roles collapsed");
    first.destroy();copied.destroy();
    throws("display-name-not-owner",function(){OM.owner("item","a sword");});
    var saved=String(game.snapshot());
    var restored=OM.prepare(configured,saved,true);
    check("all-scope-roundtrip",Number(restored.get("test.feature",owners[4]).get("count"))===14&&Number(restored.get("test.feature",bob).get("count"))===3,"scope loss");
    check("unicode-roundtrip",String(restored.setting("test.feature","label"))==="étoile 火","Unicode lost");
    // Damaged capsule data follows the saved policy: suspend keeps it exactly as saved; refuse refuses.
    function damage(text, id, change) {
        var root=MD.parse(text),list=root.get("modules");
        for(var d=0;d<list.length;d++) if(String(list[d].get("id"))===id) change(list[d]);
        return String(MD.json(root));
    }
    var hurtText=damage(saved,"test.feature",function(c){c.get("records")[0].put("owner","not an owner");});
    var hurt=OM.prepare(configured,hurtText,true);
    check("damaged-capsule-suspends",String(hurt.problem("test.feature")).indexOf("damaged saved module data")>=0&&String(hurt.problem("test.core"))==="",String(hurt.problem("test.feature")));
    check("damaged-capsule-kept-as-saved",String(hurt.snapshot()).indexOf('"owner":"not an owner"')>=0&&String(hurt.snapshot()).indexOf('"count":7')>=0,"damaged data dropped");
    check("damaged-capsule-noted",String(hurt.notes(false)).indexOf("test.feature: suspended")>=0,String(hurt.notes(false)));
    var coreHurt=OM.prepare(configured,damage(saved,"test.core",function(c){c.put("fingerprint","zz");}),true);
    check("damaged-dependency-suspends-dependent",String(coreHurt.problem("test.core")).indexOf("damaged")>=0&&String(coreHurt.problem("test.feature")).indexOf("dependency test.core")>=0,String(coreHurt.problem("test.feature")));
    throws("damaged-refuse-capsule-refuses",function(){OM.prepare(configured,damage(hurtText,"test.feature",function(c){c.put("on_disable","refuse");}),true);});
    var defaultsChanged=plan("test.feature",'{"test.feature":{"step":9}}');
    var pinned=OM.prepare(defaultsChanged,saved,true);
    check("saved-settings-pinned",Number(pinned.setting("test.feature","step"))===2,"world override rewrote campaign settings");
    pinned.configure("test.feature","step","4");
    check("explicit-campaign-setting",Number(pinned.setting("test.feature","step"))===4,"explicit setting failed");
    throws("invalid-campaign-setting-atomic",function(){pinned.configure("test.feature","step","99");});
    throws("setting-rejects-extra-data",function(){pinned.configure("test.feature","step",'3,"extra":true');});
    check("invalid-setting-keeps-value",Number(pinned.setting("test.feature","step"))===4,"invalid setting modified value");
    var legacy=OM.prepare(configured,null,true);
    check("legacy-save-unchanged",String(legacy.problem("test.feature")).indexOf("not adopted")>=0,"implicitly enabled old save");
    legacy.adopt("test.feature");check("explicit-adoption",String(legacy.problem("test.feature"))==="","adoption failed");
    throws("adoption-world-restriction",function(){legacy.adopt("not.allowed");});
    var suspended=OM.prepare(plan(""),saved,true);
    throws("disabled-state-inaccessible",function(){suspended.get("test.feature",alice);});
    check("suspended-data-kept",String(suspended.snapshot()).indexOf('"count":7')>=0,"disabled data lost");
    check("reactivation-keeps-data",Number(OM.prepare(configured,suspended.snapshot(),true).get("test.feature",alice).get("count"))===7,"reactivation reset");
    new java.io.File(modules,"test.feature.json").delete();
    var removed=OM.prepare(plan(""),saved,true);check("removed-definition-preserved",String(removed.snapshot()).indexOf('"count":7')>=0,"missing definition discarded state");
    write(feature.id,feature);
    feature.version=2;feature.state={total:{type:"integer",default:0,min:0,max:1000}};feature.settings={increment:{type:"integer",default:1,min:1,max:10},label:{type:"string",default:"étoile 火",max_length:100}};
    feature.migrations=[{from:1,to:2,state_renames:{count:"total"},setting_renames:{step:"increment"}}];write(feature.id,feature);
    feature.state.total.max=8;write(feature.id,feature);
    var lateFailure=OM.prepare(plan("test.feature"),saved,true);
    check("multi-owner-migration-atomic",String(lateFailure.snapshot()).indexOf('"count":7')>=0&&String(lateFailure.snapshot()).indexOf('"total":')<0,"partial owner migration committed");
    feature.state.total.max=1000;write(feature.id,feature);
    feature.version=3;write(feature.id,feature);
    expectError("incomplete-migration-chain",plan("test.feature"),"no migration from version 2");
    feature.version=2;feature.migrations[0].state_renames.count="missing";write(feature.id,feature);
    expectError("missing-final-migration-field",plan("test.feature"),"unknown final state field");
    feature.migrations[0].state_renames.count="total";write(feature.id,feature);

    var migrated=OM.prepare(plan("test.feature"),saved,true);
    check("state-rename-migration",Number(migrated.get("test.feature",alice).get("total"))===7,"state not migrated");
    check("setting-rename-migration",Number(migrated.setting("test.feature","increment"))===2,"setting not migrated");
    var once=String(migrated.snapshot());check("migration-idempotent",String(OM.prepare(plan("test.feature"),once,true).snapshot())===once,"migration repeated");
    delete feature.migrations;write(feature.id,feature);
    var noRoute=OM.prepare(plan("test.feature"),saved,true);check("missing-migration-suspends",String(noRoute.problem("test.feature")).indexOf("no migration")>=0,"missing migration accepted");
    check("failed-migration-keeps-version",String(noRoute.snapshot()).indexOf('"version":1')>=0&&String(noRoute.snapshot()).indexOf('"count":7')>=0,"failed migration partially committed");
    feature=base("test.feature");feature.requires={"test.core":1};write(feature.id,feature);
    var sameVersionChanged=OM.prepare(plan("test.feature"),saved,true);
    check("changed-contract-suspended",String(sameVersionChanged.problem("test.feature")).indexOf("without a version")>=0,"same-version contract changed");
    // A refusal policy is retained in the save even if the creator removes its definition.
    feature.on_disable="refuse";write(feature.id,feature);
    var protectedSave=OM.prepare(plan("test.feature"),null,false).snapshot();
    throws("refused-disabling",function(){OM.prepare(plan(""),protectedSave,true);});
    new java.io.File(modules,"test.feature.json").delete();throws("refused-removal",function(){OM.prepare(plan(""),protectedSave,true);});
    feature=base("test.feature");feature.requires={"test.core":1};feature.clock_modes=["TURNS"];write(feature.id,feature);
    var clocked=OM.prepare(plan("test.feature"),null,false);
    CM.core.GameTime.setTurnBased(false);check("unsupported-mode-inactive",String(clocked.problem("test.feature")).indexOf("clock REALTIME")>=0,"wrong clock active");
    CM.core.GameTime.setTurnBased(true);check("supported-mode-reactivates",String(clocked.problem("test.feature"))==="","clock failed to recover");
    var pureBefore=String(clocked.snapshot());OM.prepare(plan("test.feature"),pureBefore,true);
    check("preview-is-read-only",String(clocked.snapshot())===pureBefore,"preview modified source state");
    throws("unknown-save-schema",function(){OM.prepare(plan("test.feature"),'{"schema":99,"modules":[]}',true);});
    throws("malformed-snapshot",function(){OM.prepare(plan("test.feature"),'{"schema":1,"modules":[{}]}',true);});
    var slot=new java.io.File(directory,"slot");slot.mkdirs();
    Files.write(new java.io.File(slot,"header.properties").toPath(),new java.lang.String("version=1\nmoduleformat=1\n").getBytes("UTF-8"));
    throws("missing-module-file-is-not-legacy",function(){OM.readSave(directory,"slot");});
    new java.io.File(slot,"header.properties").delete();slot.delete();
    var wfClass=java.lang.Class.forName("com.planet_ink.coffee_mud.core.save.WorldFiles");
    var known=wfClass.getDeclaredField("knownSums");known.setAccessible(true);
    var originalWorld=WorldFiles.worldDir(), originalKnown=known.get(null);
    try {
        Files.write(new java.io.File(modules,"notes.txt").toPath(),new java.lang.String("source notes").getBytes("UTF-8"));
        WorldFiles.configure(directory);known.set(null,new java.util.TreeMap(WorldFiles.fileSums()));
        WorldFiles.acceptModuleChanges();
        check("module-reload-keeps-unrelated-provenance",!WorldFiles.changedOutside().containsKey("modules/notes.txt"),"non-manifest source was marked changed");
        // The boot reads MODULESETTINGS as the launcher's startup check did: raw, with no entity conversion.
        var launchField=java.lang.Class.forName("com.planet_ink.coffee_mud.core.OptionalModules").getDeclaredField("launched");launchField.setAccessible(true);
        var launchedBefore=launchField.get(null);
        try {
            OM.launchedWith("test.feature",'{"test.feature":{"label":"a&amp;b"}}');
            check("boot-reads-launch-settings-raw",String(OM.configuredPlan().settings.get("test.feature").get("label"))==="a&amp;b",String(OM.configuredPlan().settings));
        } finally {launchField.set(null,launchedBefore);}
        new java.io.File(modules,"test.feature.json").delete();WorldFiles.acceptModuleChanges();
        check("module-reload-acknowledges-removal",!WorldFiles.changedOutside().containsKey("modules/test.feature.json"),"removed manifest remains changed");
        // world/affixes.xml and the modules' folders are versioned by the modules themselves: a world that
        // has them, but no modules, keeps the world version its saves and its content cache recorded.
        var bare=Files.createTempDirectory("sud-module-sum-").toFile();
        try {
            WorldFiles.configure(bare);var bareSum=String(WorldFiles.checksum());
            var put=function(path,text){var f=new java.io.File(bare,path);f.getParentFile().mkdirs();Files.write(f.toPath(),new java.lang.String(text).getBytes("UTF-8"));};
            put("affixes.xml","<AFFIXES></AFFIXES>\n");put("modules/x.json","{}");put("compositions/x.json","{}");put("combat/x.json","{}");put("itemmods/x.json","{}");
            check("module-files-outside-world-checksum",String(WorldFiles.checksum())===bareSum,"affixes.xml or a module folder changed the world version");
            known.set(null,new java.util.TreeMap(WorldFiles.fileSums()));
            check("module-files-outside-saved-world-version",String(WorldFiles.loadedChecksum())===bareSum&&WorldFiles.loadedManifest().containsKey("affixes.xml"),String(WorldFiles.loadedManifest()));
            put("affixes.xml","<AFFIXES><AFFIX id=\"x\"/></AFFIXES>\n");
            check("module-files-still-tracked",String(WorldFiles.changedOutside().get("affixes.xml"))==="edited"&&String(WorldFiles.checksum())===bareSum,WorldFiles.changedOutside());
            put("lore/x.ini","X=1\n");check("other-assets-still-versioned",String(WorldFiles.checksum())!==bareSum,"lore no longer counts");
        } finally {
            var remove=function(f){var inside=f.listFiles();if(inside!=null)for(var g=0;g<inside.length;g++)remove(inside[g]);f.delete();};
            remove(bare);
        }
    } finally {WorldFiles.configure(originalWorld);known.set(null,originalKnown);}
    done();
} finally {
    CM.core.GameTime.setTurnBased(oldTurns);if(oldPause)CM.core.GameTime.pause();
    clear();modules.delete();directory.delete();
}
