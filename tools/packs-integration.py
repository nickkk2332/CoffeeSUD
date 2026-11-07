#!/usr/bin/env python3
"""Q10: the worked packs, built and played as their guides say, across fresh processes, on an Ember Trial deployment:
the guild laboratory (a fellowship's research advanced by a staged recipe whose work piece survives a restore), the
bonded familiar (a charm whose kill calls a wisp, and a bond track kept across a restore) and the branching
expedition (a chart's branch chosen, a generated crypt cleared, its reward banked and claimed, and kept)."""
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
EXAMPLES = ROOT / 'docs/creator/examples'
spec = importlib.util.spec_from_file_location('forge_sample', EXAMPLES / 'ember-trial/make_world.py')
factory = importlib.util.module_from_spec(spec); spec.loader.exec_module(factory)
scratch = Path(sys.argv[1]).resolve(); scratch.mkdir(parents=True, exist_ok=True)
dest = factory.create(scratch / 'deployment')
world = dest / 'world'
PACKS = ['guild-laboratory', 'bonded-familiar', 'branching-expedition']


def install():
    # as the guide says: the packs' folders into the published world, their modules selected, fellowships free to found
    for pack in PACKS:
        for folder in (EXAMPLES / pack).iterdir():
            if folder.is_dir():
                shutil.copytree(folder, world / folder.name, dirs_exist_ok=True)
    ini = (world / 'world.ini').read_text()
    ini = re.sub(r'(?m)^MODULES=(.*)$', r'MODULES=\1,progression.tracks,abilities.triggers,rewards.escrow,expeditions.contracts', ini, count=1)
    (world / 'world.ini').write_text(ini + '\nCLANCOST=0\n')
newchar = 'Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
count = 0


def check(condition, name, detail=''):
    global count
    if not condition:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok packs-integration/' + name, flush=True)


def play(name, commands, restore, *settings, expected=()):
    path = scratch / (name + '.input'); path.write_text(commands)
    with (scratch / (name + '.txt')).open('w') as output:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(path),
            'WORLDDIR=' + str(world), 'DBSERVICE=jdbc:fakedb:' + str(dest / 'db'),
            'SAVEDIR=' + str(dest / ('.build/saves' if restore is None else 'saves')),
            'BUILDER=1' if restore is None else 'RESTORE=' + restore, 'TIMEMODE=TURNS', 'RANDOMSEED=42', *settings],
            cwd=ROOT, env=dict(os.environ, SUDPLAY_LIMIT='480'), stdout=output, stderr=subprocess.STDOUT, timeout=600)
    text = (scratch / (name + '.txt')).read_text(errors='replace')
    check(result.returncode == 0, name + ' process', text[-2500:])
    check('JS error' not in text and 'JavaScript error' not in text and 'Huh?' not in text, name + ' commands', text[-2500:])
    normal = ' '.join(text.split())
    for marker in expected:
        check(marker in normal, name + ' / ' + marker, text[-3000:])
    return normal


CLEAR = """var CM = Packages.com.planet_ink.coffee_mud, CL = CM.core.CMLib;
var A = mob().location().getArea(), foes = [];
for (var e = A.getProperMap(); e.hasMoreElements();) { var R = e.nextElement(); for (var i = 0; i < R.numInhabitants(); i++) { var M = R.fetchInhabitant(i); if (M.isMonster() && M.fetchEffect('Prop_Summoned') == null) foes.push(M); } }
mob().tell('FIXINFO the instance ' + A.Name() + ' holds ' + foes.length + ' foes');
for (var i = 0; i < foes.length; i++) CL.combat().postDeath(mob(), foes[i], null);
mob().tell('FIXINFO cleared ' + foes.length);
"""

try:
    factory.publish(dest)
    install()
    check(True, 'trial published, then the packs installed')
    play('build', 'Trialbuilder\njrun /resources/creator/lab-build.js\njrun /resources/creator/familiar-build.js\njrun /resources/creator/branch-build.js\n'
         'world check only=modules,refs,tracks,triggers,expeditions,rewards,itemmods\nsave\nquit\nY\n', None,
         expected=['LAB BUILT', 'FAMILIAR BUILT', 'BRANCH BUILT', 'a hooded lantern at camp', 'WORLD CHECK: 0 errors'])

    # the guild laboratory: a fellowship, a work piece's first stage, a save
    play('lab1', 'Labmaker\n' + newchar + 'clancreate The Ember Lab party\nopen supply coffer\n#expect You open\nget "cinder iron" coffer\n#expect You get\n'
         'modcraft begin lab.mantle-work\nmodcraft prepare lab.mantle-work "unfinished lab mantle"\nmodcraft commit\nmodcraft status\nsave lab1\nquit\nn\n', 'NEW',
         expected=['Your party founded The Ember Lab', 'You set out an unfinished lab mantle', 'Prepared craft #1', 'Committed craft #1',
                   'stage 1 of 2 (Cut the cloth)', 'Work piece an unfinished lab mantle: Weave a laboratory mantle, 1 of 2 stages done; next Bind the seams'])
    # restored: the piece kept its stage; the last stage makes the mantle and the fellowship's research grows
    lab = play('lab2', 'modcraft status\nmodcraft prepare lab.mantle-work "unfinished lab mantle"\nmodcraft commit\ntracks\nledger\n'
               "get charm\n#expect You get\nwear charm\n#expect charm\neast\n#expect training stones\ncompose cast embertrial.ember cinder moth\n#expect is DEAD\n"
               'tracks\nlook\nsave lab2\nquit\nn\n', 'lab1',
               expected=['1 of 2 stages done; next Bind the seams', 'Committed craft #2', 'is finished', 'Laboratory research: level 1 of 2',
                         '[x] 1. Benches set up', 'items.modifiers (craft): Committed craft #2'])
    # the bonded familiar: the kill called the wisp and began the bond
    check('appears at Labmaker' in lab and 'Familiar bond: level 1 of 3' in lab and '[x] 1. The wisp knows you' in lab, 'the wisp answers the kill', lab[-2500:])

    # restored again: the bond is kept; the branching expedition: the gate refuses, a branch is chosen, the crypt cleared
    (world / 'resources/creator/branch-clear.js').write_text(CLEAR)
    run = play('exp1', 'god all off\ntracks\nwest\n#expect pilgrims camp\nwest\nget "branching chart"\n#expect You get\nget lantern\n#expect You get\nhold lantern\n#expect light\n'
               'expedition preview chart\nexpedition open chart marked\n#expect begins\nwest\nlook\njrun /resources/creator/branch-clear.js\nrewards\nrewards claim\nsave exp1\nquit\nn\n', 'lab2', 'DEV=1',
               expected=['Familiar bond: level 1 of 3', 'Only an opened expedition chart leads there.', 'You light up a hooded lantern.', 'Modifiers offered (choose 1):',
                         'You open a branching chart: expedition 1 begins.', 'The expedition is complete.', 'Spoils of the Branching Depths: 75 in coin', 'Claimed reward'])
    check("You can't see anything" not in run, 'the lantern lights the crypt', run[-2500:])
    foes = re.search(r'FIXINFO the instance (.+?) holds (\d+) foes', run)
    check(foes is not None and 'Branching Depths' in foes.group(1) and int(foes.group(2)) > 0, 'the generated crypt held foes', run[-2000:])
    # restored: the claim is kept; the completed run no longer holds the crypt open, so the save resumes at camp;
    # the crypt's foe was the familiar bond's second kill
    after = play('exp2', 'rewards\nexpedition status\ntracks\nquit\nn\n', 'exp1',
                 expected=['Restored', 'The pilgrims camp', '1. claimed, Spoils of the Branching Depths: 75 in coin', 'You are not on an expedition.',
                           'Familiar bond: level 2 of 3', 'Laboratory research: level 1 of 2'])
    check('provisional' not in after and 'banked' not in after, 'nothing left unclaimed', after[-1500:])
    print('FIXTEST done packs-integration (%d checks)' % count, flush=True)
except Exception as error:
    print('FIXTEST FAIL packs-integration: ' + str(error), flush=True)
    raise
