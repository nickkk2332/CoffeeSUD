#!/usr/bin/env python3
"""Played regressions for the project-review fixes; invoked by tools/fixes.sh review."""
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent.parent
GAME = ROOT / 'CoffeeMud'
scratch = Path(sys.argv[1]).resolve()
scratch.mkdir(parents=True, exist_ok=True)
world, db, saves = (scratch / name for name in ('world', 'db', 'saves'))
shutil.copytree(GAME / 'world', world)
db.mkdir()
shutil.copyfile(GAME / 'resources/fakedb/fakedb.schema', db / 'fakedb.schema')
env = os.environ.copy()
env['SUDPLAY_EXPECT_LIMIT'] = '90'
count = 0


def check(ok, name, detail=''):
    global count
    if not ok:
        raise AssertionError(name + ': ' + detail)
    count += 1
    print('FIXTEST ok review-integration/' + name, flush=True)


def play(name, script, settings=None, command=None):
    path = scratch / (name + '.script')
    path.write_text(script)
    process_env = env.copy()
    if command:
        process_env['SUDPLAY_COMMAND'] = json.dumps(command)
        args = []
    else:
        args = [f'DBSERVICE=jdbc:fakedb:{db}', f'WORLDDIR={world}', f'SAVEDIR={saves}', 'TIMEMODE=TURNS', 'RANDOMSEED=42']
    args.extend(settings or [])
    with (scratch / (name + '.txt')).open('w') as log:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(path), *args], cwd=GAME,
                                env=process_env, stdout=log, stderr=subprocess.STDOUT, timeout=240)
    text = (scratch / (name + '.txt')).read_text(errors='replace')
    check(result.returncode == 0, name + ' process', text[-1500:])
    check('FIXTEST FAIL' not in text and 'JS error in JRUN' not in text, name + ' assertions', text[-2500:])
    return text


def fingerprint(directory):
    return {str(path.relative_to(directory)): path.read_bytes() for path in directory.rglob('*') if path.is_file()}


newchar = 'Y\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
child_root = None
try:
    parent = play('isolation-parent', 'Builder\n' + newchar + 'jrun /resources/_fixes/review-isolation.js\nquit\nY\n', ['BUILDER=1'])
    check('FIXTEST done review-isolation' in parent, 'isolated snapshot prepared')
    command = (world / 'resources/review-child-command.txt').read_text().splitlines()
    child_root = Path(next(arg.split('=', 1)[1] for arg in command if arg.startswith('SAVEDIR='))).parent
    child_world = child_root / 'world'
    init = child_world / 'js/init.js'
    init.parent.mkdir(exist_ok=True)
    init.write_text("function onPlayerEnter(m) { m.tell('REVIEW_CHILD_MORTAL '+(!Packages.com.planet_ink.coffee_mud.core.CMSecurity.isASysOp(m))); }\n")
    before = fingerprint(world)
    child = play('isolation-child', '#expect REVIEW_CHILD_MORTAL true\nlook\nquit\nn\n', command=command)
    check("Restored 'playtest'." in child and 'REVIEW_CHILD_MORTAL true' in child, 'child restores snapshot as mortal', child[-2000:])
    check(fingerprint(world) == before, 'child leaves builder files unchanged')
    shutil.rmtree(child_root)
    child_root = None

    made = play('make-review-save', 'Reviewer\n' + newchar + 'jrun /resources/_fixes/review-play.js\nquit\nn\n', ['RESTORE=NEW', 'DEV=1'])
    check('FIXTEST done review-play' in made, 'combat and menu snapshot completed')
    restored = play('restore-review', '#expect REVIEW_SAVED_CHOICE\n1\njrun /resources/_fixes/review-check.js\njrun /resources/_fixes/review-narration.js\nquit\nn\n', ['RESTORE=review', 'DEV=1'])
    check('FIXTEST done review-check' in restored and 'FIXTEST done review-narration' in restored, 'combat identity and queued contexts restored', restored[-2500:])
    narrated = play('restore-narration', '#expect Enter to continue\n\njrun /resources/_fixes/review-narration-check.js\nquit\nn\n', ['RESTORE=narration', 'DEV=1'])
    check('REVIEW_UNREAD_PAGE' in narrated and 'REVIEW_READ_PAGE' not in narrated, 'narration resumes at unread page', narrated[-2000:])
    check('FIXTEST done review-narration-check' in narrated, 'narration clock restored')
    check((saves / 'review/world-manifest.properties').is_file(), 'save includes loaded-definition manifest')
    print(f'FIXTEST done review-integration ({count} checks)', flush=True)
except Exception as exc:
    print('FIXTEST FAIL review-integration: ' + str(exc), flush=True)
    raise
finally:
    if child_root is not None:
        shutil.rmtree(child_root, ignore_errors=True)
