#!/usr/bin/env python3
"""G01 expeditions in real play, across fresh processes: a gated generated destination, a chart previewed
(nothing spent or drawn) and opened (chart and cost consumed once), the instance made from the chart's seed with its
modifier, the run's attempts and instance kept across a restore, a death using an attempt, and the recovery path
when the instance is gone (laid out again from the same seed)."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
GAME = ROOT / 'CoffeeMud'
EXAMPLES = ROOT / 'docs/creator/examples'
scratch = Path(sys.argv[1]).resolve()
scratch.mkdir(parents=True, exist_ok=True)
world, db, saves = (scratch / name for name in ('world', 'db', 'saves'))
shutil.copytree(GAME / 'world', world)
db.mkdir()
shutil.copyfile(GAME / 'resources/fakedb/fakedb.schema', db / 'fakedb.schema')
(world / 'modules').mkdir(exist_ok=True)
for pack in ('reward-escrow', 'expeditions'):
    for f in (EXAMPLES / pack / 'modules').iterdir():
        shutil.copyfile(f, world / 'modules' / f.name)
creator = world / 'resources/creator'
creator.mkdir(parents=True, exist_ok=True)
(world / 'world.ini').write_text('MODULES=rewards.escrow,expeditions.contracts\n')
newchar = 'Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
count = 0


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok expedition-integration/' + name, flush=True)


def play(name, script, *settings):
    path = scratch / (name + '.script')
    path.write_text(script)
    with (scratch / (name + '.txt')).open('w') as log:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(path),
            'DBSERVICE=jdbc:fakedb:' + str(db), 'WORLDDIR=' + str(world), 'SAVEDIR=' + str(saves),
            'TIMEMODE=TURNS', 'RANDOMSEED=42', *settings],
            env=dict(os.environ, SUDPLAY_LIMIT='420'), stdout=log, stderr=subprocess.STDOUT, timeout=480)
    text = (scratch / (name + '.txt')).read_text(errors='replace')
    check(result.returncode == 0, name + ' process', text[-3000:])
    check('JS error' not in text and 'FIXTEST FAIL' not in text and 'Huh?' not in text, name + ' assertions', text[-3000:])
    return text


def script(name, body):
    (creator / name).write_text("var CM = Packages.com.planet_ink.coffee_mud, CL = CM.core.CMLib, CC = CM.core.CMClass, EX = CM.core.Expeditions;\n"
        "function ok(n, c, why) { mob().tell((c ? 'FIXTEST ok ' : 'FIXTEST FAIL ') + 'expedition-integration/' + n + (c ? '' : ': ' + why)); }\n"
        "function run() { var st = JSON.parse(String(CM.core.OptionalModules.getJSON('expeditions.contracts', CM.core.OptionalModules.world()))); "
        "return (typeof st.runs == 'string') ? JSON.parse(st.runs) : st.runs; }\n"
        + body)


def saved_run(slot):
    data = json.loads((saves / slot / 'modules.json').read_text())
    module = next(m for m in data['modules'] if m['id'] == 'expeditions.contracts')
    runs = json.loads(module['records'][0]['values']['runs'])['runs']
    return runs['1']


try:
    # the destination: a generated area behind a gate room, beside the start room
    script('exp-build.js', """
var R0 = mob().location(), D = CM.core.Directions;
var A = CC.getAreaType('StdAutoGenInstance'); A.setName('Fixdepths'); CL.map().addArea(A); CL.database().DBCreateArea(A);
A.setStat('GENERATIONFILEPATH', 'randareas/example.xml'); A.setStat('OTHERVARS', 'AREA_ID=maze_dungeon areasize=4');
var g = CC.getLocale('StdRoom'); g.setRoomID('Fixdepths#0'); g.setArea(A); g.setDisplayText('A chart-sealed stair'); g.setDescription('A stair only a chart opens.');
CL.database().DBCreateRoom(g);
var dir = -1; for (var d = 0; d < 6 && dir < 0; d++) if (R0.getRoomInDir(d) == null && R0.getRawExit(d) == null) dir = d;
var op = D.getOpDirectionCode(dir);
R0.setRawExit(dir, CC.getExit('Open')); R0.rawDoors()[dir] = g; g.setRawExit(op, CC.getExit('Open')); g.rawDoors()[op] = R0;
CL.database().DBUpdateExits(R0); CL.database().DBUpdateExits(g); CL.database().DBUpdateArea(A.Name(), A);
mob().tell('FIXSETUP gate ' + CM.core.CMLib.directions().getDirectionName(dir).toLowerCase() + ' from ' + CL.map().getExtendedRoomID(R0));
""")
    built = play('build', 'Builder\n' + newchar + 'jrun /resources/creator/exp-build.js\nquit\nY\n', 'BUILDER=1')
    line = next(l for l in built.splitlines() if 'FIXSETUP gate' in l)
    rest = line.split('FIXSETUP gate ', 1)[1]
    way, start = rest.split()[0], rest.split(' from ', 1)[1].strip()
    back = {'north': 'south', 'south': 'north', 'east': 'west', 'west': 'east', 'up': 'down', 'down': 'up'}[way]
    check(start != '', 'build', line)
    (world / 'expeditions').mkdir(exist_ok=True)
    (world / 'expeditions/fix.depths.v1.json').write_text(json.dumps({
        'schema': 1, 'id': 'fix.depths', 'version': 1, 'title': 'The Fix Depths',
        'destination': {'area': 'Fixdepths', 'gated': True}, 'return_room': start,
        'modifiers': [{'id': 'marked', 'title': 'Marked foes', 'mob_effect': {'id': 'Prop_ResourceMax', 'text': 'fix.mark=+1'}},
                      {'id': 'hardy', 'title': 'Hardy foes', 'mob_hp_percent': 50}],
        'offer': 2, 'choose': 1, 'costs': {'money': 5}, 'attempts': 2, 'party': {'max': 2}}))

    # a new game: the gate refuses; a chart previewed (nothing changes), opened, entered
    script('exp-chart.js', """
var I = CC.getItem('GenItem'); I.setName('a fix depths chart'); I.setDisplayText('A chart lies here.');
var P = CC.getAbility('Prop_ExpeditionChart'); P.setMiscText('EXPEDITION=fix.depths SEED=0 TIER=1'); I.addNonUninvokableEffect(P);
mob().addItem(I); CL.beanCounter().addMoney(mob(), 20.0);
mob().tell('FIXINFO streams ' + CM.core.RandomStreams.counter('expeditions'));
""")
    script('exp-open.js', """
var chart = mob().findItem(null, 'a fix depths chart');
ok('previewed-nothing', CM.core.RandomStreams.counter('expeditions') == 0 && EX.seed(chart, false) == 0, 'the preview drew a seed');
var before = CL.beanCounter().getTotalAbsoluteValue(mob(), CL.beanCounter().getCurrency(mob()));
var def = EX.definitions().latest('fix.depths').value;
var seed = Number(EX.nextSeed()); // in Java: a JavaScript number cannot hold the 64-bit draw
var offered = EX.offered(def, seed);
ok('offers-two', offered.size() == 2, 'offered ' + offered.size());
var cmd = CC.getCommand('ExpeditionCmd');
cmd.execute(mob(), CM.core.CMParms.parse('EXPEDITION OPEN a fix depths chart marked'), 0);
var r = run().runs['1'];
ok('opened', r != null && r.status == 'open' && r.seed == seed && String(r.mods) == 'marked' && r.left == 2, JSON.stringify(r));
ok('consumed-once', mob().findItem(null, 'a fix depths chart') == null && CL.beanCounter().getTotalAbsoluteValue(mob(), CL.beanCounter().getCurrency(mob())) == before - 5,
	'the chart or the cost was not taken');
""")
    script('exp-in.js', """
var r = run().runs['1'], A = mob().location().getArea();
ok('entered', r.status == 'entered' && String(A.Name()) == r.instance, 'in ' + A.Name() + ' / ' + JSON.stringify(r));
ok('seeded', String(A.getBlurbFlag('GENSEED')) == String(r.seed) || String(A.getBlurbFlag('GENSEED')).indexOf(String(r.seed)) >= 0, 'GENSEED ' + A.getBlurbFlag('GENSEED') + ' seed ' + r.seed);
var marked = 0, mobs = 0;
for (var e = A.getProperMap(); e.hasMoreElements();) { var R = e.nextElement(); for (var i = 0; i < R.numInhabitants(); i++) { var M = R.fetchInhabitant(i); if (!M.isMonster()) continue; mobs++; if (M.fetchEffect('Prop_ResourceMax') != null) marked++; } }
ok('modified', marked == mobs, marked + ' of ' + mobs + ' foes marked');
mob().tell('FIXINFO instance ' + A.Name() + ' rooms ' + A.numberOfProperIDedRooms());
""")
    # GOD ALL OFF: a DEV=1 character walks through refusals (GOTO gives NOCLIP); this one plays as a mortal
    text = play('open', 'Explorer\n' + newchar + 'god all off\njrun /resources/creator/exp-chart.js\n' + way + '\nlook\n'
                'expedition preview a fix depths chart\njrun /resources/creator/exp-open.js\n' + way + '\njrun /resources/creator/exp-in.js\nledger expedition\n'
                'save exp1\nquit\nn\n', 'RESTORE=NEW', 'DEV=1')
    normal = ' '.join(text.split())
    check('Only an opened expedition chart leads there.' in normal, 'gated', text[-2500:])
    check('Seed: ' in normal and 'not yet drawn' in normal and 'Modifiers offered (choose 1):' in normal and 'It can be opened here.' in normal,
          'preview', text[-2500:])
    # Q05: the expedition's ledger: its definition pin, seed and the scenario that draws it again, its party
    check('Ledger of expedition 1:' in normal and 'drawn from the expeditions stream at draw' in normal and 'Party: Explorer;' in normal, 'ledger', text[-2500:])
    first = saved_run('exp1')
    check(first['status'] == 'entered' and first['instance'] != '' and first['left'] == 2, 'saved run', json.dumps(first))

    # a restore: the same run and instance; a death inside uses an attempt
    script('exp-restored.js', """
var r = run().runs['1'];
ok('restored-run', r.status == 'entered' && r.left == 2 && CL.map().getArea(r.instance) != null && String(mob().location().getArea().Name()) == r.instance,
	JSON.stringify(r) + ' in ' + mob().location().getArea().Name());
mob().tell('FIXINFO instance ' + r.instance);
var foe = CC.getMOB('GenMob'); foe.setName('a fix depths shade'); foe.bringToLife(mob().location(), true);
CL.combat().postDeath(foe, mob(), null);
foe.destroy();
""")
    script('exp-after-death.js', """
var r = run().runs['1'];
ok('attempt-used', r.left == 1 && r.status == 'entered', JSON.stringify(r));
""")
    text = play('restore', 'god all off\njrun /resources/creator/exp-restored.js\nlook\njrun /resources/creator/exp-after-death.js\nsave exp2\nquit\nn\n', 'RESTORE=exp1', 'DEV=1')
    check("Restored 'exp1'." in text, 'restored', text[-2000:])
    second = saved_run('exp2')
    check(second['instance'] == first['instance'] and second['left'] == 1, 'instance identity kept', json.dumps(second))

    # the recovery path: the instance gone, the run laid out again from the same seed on the next entry
    script('exp-gone.js', """
var r = run().runs['1'], A = CL.map().getArea(r.instance);
var home = CL.map().getRoom('""" + start + """');
if (mob().location().getArea() == A) home.bringMobHere(mob(), false);
if (A != null) { CL.map().delArea(A); A.destroy(); }
ok('gone', CL.map().getArea(r.instance) == null, 'the instance is still there');
""")
    script('exp-again.js', """
var r = run().runs['1'], A = mob().location().getArea();
ok('laid-out-again', r.status == 'entered' && String(A.Name()) == r.instance && String(A.getBlurbFlag('GENSEED')).indexOf(String(r.seed)) >= 0,
	JSON.stringify(r) + ' in ' + A.Name() + ' GENSEED ' + A.getBlurbFlag('GENSEED'));
""")
    text = play('recover', 'god all off\njrun /resources/creator/exp-gone.js\nexpedition recover\nexpedition status\n' + way + '\njrun /resources/creator/exp-again.js\n'
                'save exp3\nquit\nn\n', 'RESTORE=exp2', 'DEV=1')
    normal = ' '.join(text.split())
    check('it will be laid out again from the chart' in normal, 'recover', text[-2500:])
    check('it is laid out again from the chart' in normal, 'told', text[-2500:])
    print('FIXTEST done expedition-integration (%d checks)' % count, flush=True)
except Exception as error:
    print('FIXTEST FAIL expedition-integration: ' + str(error), flush=True)
    raise
