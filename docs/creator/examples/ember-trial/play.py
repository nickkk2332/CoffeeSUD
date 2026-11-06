#!/usr/bin/env python3
"""Play a published Ember Trial deployment with its own world/database/saves."""
import argparse
import os
from pathlib import Path
import subprocess
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[3]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('deployment')
    selection = parser.add_mutually_exclusive_group()
    selection.add_argument('--new', action='store_true')
    selection.add_argument('--restore', metavar='SLOT')
    args = parser.parse_args()
    dest = Path(args.deployment).expanduser().resolve()
    if (not (dest / '.ember-trial').is_file() or not (dest / 'db/fakedb.schema').is_file()
            or not list((dest / 'world/areas').glob('*ember*'))):
        parser.error('Create/publish a fresh deployment with make_world.py first.')
    launcher = ROOT / 'CoffeeMud' / ('sud.bat' if os.name == 'nt' else 'sud.sh')
    command = [str(launcher), 'WORLDDIR=' + str(dest / 'world'),
               'DBSERVICE=jdbc:fakedb:' + str(dest / 'db'), 'SAVEDIR=' + str(dest / 'saves')]
    if args.new:
        command.append('RESTORE=NEW')
    elif args.restore:
        command.append('RESTORE=' + args.restore)
    return subprocess.call(command, cwd=ROOT)


if __name__ == '__main__':
    sys.exit(main())
