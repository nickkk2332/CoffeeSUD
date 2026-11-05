#!/usr/bin/env python3
"""Create a new isolated example deployment. Refuses an existing destination."""
from pathlib import Path
import shutil
import sys


def create(destination):
    here = Path(__file__).resolve().parent
    root = here.parents[2]
    dest = Path(destination).expanduser().resolve()
    dest.mkdir(parents=True, exist_ok=False)
    shutil.copytree(root / 'CoffeeMud/world', dest / 'world')
    shutil.copytree(here / 'world', dest / 'world', dirs_exist_ok=True)
    (dest / 'db').mkdir()
    shutil.copy(root / 'CoffeeMud/resources/fakedb/fakedb.schema', dest / 'db/fakedb.schema')
    (dest / 'saves').mkdir()
    return dest


if __name__ == '__main__':
    if len(sys.argv) != 2:
        sys.exit('Usage: python3 docs/creator/examples/make_world.py NEW_DESTINATION')
    dest = create(sys.argv[1])
    print('Created isolated world, database and saves under ' + str(dest))
    print('Launch in builder mode with the paths described in examples/README.md.')
