#!/usr/bin/env python3
"""Assemble and publish the sample in a new destination, never a user's world."""
import argparse
import os
from pathlib import Path
import shlex
import shutil
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def create(destination):
    dest = Path(destination).expanduser().resolve()
    dest.mkdir(parents=True, exist_ok=False)
    shutil.copytree(ROOT / 'CoffeeMud/world', dest / 'world')
    shutil.copytree(HERE / 'world', dest / 'world', dirs_exist_ok=True)
    # Reuse published parent contracts/presets instead of forking their hashes.
    examples = HERE.parent
    shutil.copy(examples / 'ability-composition/modules/abilities.composition.json', dest / 'world/modules')
    shutil.copy(examples / 'combat-profiles/modules/combat.profiles.json', dest / 'world/modules')
    shutil.copytree(examples / 'combat-profiles/combat', dest / 'world/combat')
    shutil.copytree(examples / 'item-modifiers', dest / 'world', dirs_exist_ok=True,
                    ignore=shutil.ignore_patterns('README.md'))
    (dest / 'db').mkdir()
    shutil.copy(ROOT / 'CoffeeMud/resources/fakedb/fakedb.schema', dest / 'db/fakedb.schema')
    (dest / 'saves').mkdir()
    return dest


def publish(dest):
    work = dest / '.build'; work.mkdir()
    script = work / 'publish.input'
    script.write_text('Trialbuilder\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n'
                      'jrun /resources/creator/build.js\nworld check only=modules,compositions,combat,itemmods,scripts\n'
                      'save\nquit\nY\n', encoding='utf-8')
    env = dict(os.environ, SUDPLAY_LIMIT='360')
    env['JAVA_TOOL_OPTIONS'] = '-Djava.awt.headless=true ' + env.get('JAVA_TOOL_OPTIONS', '')
    with (work / 'publish.txt').open('w', encoding='utf-8') as log:
        result = subprocess.run([sys.executable, str(ROOT / 'tools/sudplay.py'), str(script),
            'WORLDDIR=' + str(dest / 'world'), 'DBSERVICE=jdbc:fakedb:' + str(dest / 'db'),
            'SAVEDIR=' + str(work / 'saves'), 'BUILDER=1', 'TIMEMODE=TURNS', 'RANDOMSEED=42'],
            cwd=ROOT, env=env, stdout=log, stderr=subprocess.STDOUT, timeout=420)
    output = (work / 'publish.txt').read_text(encoding='utf-8', errors='replace')
    if (result.returncode or 'EMBER TRIAL BUILT' not in output or 'WORLD CHECK: 0 errors' not in output
            or 'JS error' in output or 'JavaScript error' in output
            or not list((dest / 'world/areas').glob('*ember*'))):
        raise RuntimeError('Sample publication failed; see ' + str(work / 'publish.txt'))
    # Builder profiles/saves are separate from the actual adventure's SAVEDIR.
    (dest / '.ember-trial').write_text('Ember Trial v2 (modifier forge)\n', encoding='utf-8')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('destination', help='a new deployment directory')
    parser.add_argument('--prepare-only', action='store_true', help='copy sources without running the builder')
    args = parser.parse_args()
    try:
        dest = create(args.destination)
        print('Created isolated world/database/saves: ' + str(dest), flush=True)
        if not args.prepare_only:
            publish(dest)
            print('Published. Play with: ' + shlex.join(['python3', str(HERE / 'play.py'), str(dest)]))
        else:
            print('Run the builder recipe and SAVE as described in README.md.')
        return 0
    except (OSError, RuntimeError, subprocess.TimeoutExpired) as error:
        print(str(error), file=sys.stderr)
        return 1


if __name__ == '__main__':
    sys.exit(main())
