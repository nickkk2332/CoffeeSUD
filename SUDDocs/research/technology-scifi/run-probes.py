#!/usr/bin/env python3
"""Run audit observations in an isolated game copy; no source/world edits.

Usage: python docs/research/technology-scifi/run-probes.py --output /tmp/tech-audit
Output contains a transcript and a compact observations.txt. These deliberately
report existing defects, rather than assert that the engine is correct.
"""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import tempfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    repo = here.parents[2]
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="coffeesud-tech-audit-") as temp:
        scratch = Path(temp)
        game = scratch / "game"
        shutil.copytree(repo / "CoffeeMud", game)
        subprocess.run([str(game / "build.sh")], check=True,
                       stdout=subprocess.PIPE, stderr=subprocess.STDOUT, text=True)
        db = scratch / "db"
        db.mkdir()
        shutil.copyfile(game / "resources/fakedb/fakedb.schema", db / "fakedb.schema")
        shutil.copyfile(here / "probes.js", game / "resources/_technology-audit.js")
        script = scratch / "input.txt"
        script.write_text(
            "Auditor\nY\nN\nHuman\nY\nM\nN\nApprentice\nY\ngood\n\n"
            "jrun /resources/_technology-audit.js\n"
            "test spaceangles\nquit\nY\n", encoding="utf-8")
        env = dict(os.environ, SUD_GAME_DIR=str(game), SUDPLAY_LIMIT="420")
        command = ["python3", str(repo / "tools/sudplay.py"), str(script),
                   "DBSERVICE=jdbc:fakedb:" + str(db),
                   "WORLDDIR=" + str(game / "world"),
                   "SAVEDIR=" + str(scratch / "saves"),
                   "BUILDER=1", "RANDOMSEED=42", "TIMEMODE=TURNS"]
        with (output / "transcript.txt").open("w", encoding="utf-8") as log:
            result = subprocess.run(command, env=env, stdout=log,
                                    stderr=subprocess.STDOUT, timeout=480)
        transcript = (output / "transcript.txt").read_text(encoding="utf-8", errors="replace")
        # Remove SUD prompt prefixes, retaining the exact observation payload.
        lines = []
        for line in transcript.splitlines():
            if "TECHOBS " in line:
                lines.append(line[line.index("TECHOBS "):])
            elif re.search(r"Spaceangles:|^Failed|^Passed", line):
                lines.append(line)
        (output / "observations.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")
        print(json.dumps({"exit_code": result.returncode, "observations": len(lines),
                          "probe_completed": "TECHOBS DONE completed" in transcript,
                          "output": str(output)}, indent=2))
        if result.returncode or "TECHOBS DONE completed" not in transcript:
            raise SystemExit(1)


if __name__ == "__main__":
    main()
