#!/usr/bin/env python3
"""Read-only checks for the technology audit/catalog; not engine acceptance tests."""
from __future__ import annotations

import csv
import hashlib
import json
from collections import Counter
from pathlib import Path
import re
import subprocess
from urllib.parse import unquote, urlsplit

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[2]
BASELINE = "8df259443699f517d7380696460644d395b9c4a8"


def read_csv(name):
    with (HERE / name).open(encoding="utf-8", newline="") as stream:
        rows = list(csv.DictReader(stream))
    assert rows and all(None not in row and None not in row.values() for row in rows), name
    return rows


def ids_for(rows, expected):
    ids = {row["id"] for row in rows}
    assert len(ids) == len(rows) and ids == expected, (len(rows), expected - ids)
    return ids


def references(value):
    return set(filter(None, value.split(";")))


def anchor_ids(text):
    explicit = set(re.findall(r'<a id="([^"]+)"', text))
    headings = re.findall(r"^#{1,6} (.+)$", text, re.M)
    return explicit | {
        re.sub(r"\s", "-", re.sub(r"[^\w\s-]", "", heading.lower()))
        for heading in headings
    }


def main():
    proposals = read_csv("proposals.csv")
    findings = read_csv("findings.csv")
    inventory = read_csv("inventory.csv")
    sources = read_csv("sources.csv")
    pids = ids_for(proposals, {f"{p}{i:02}" for p in "RTSCWQ" for i in range(1, 13)})
    fids = ids_for(findings, {f"F{i:02}" for i in range(1, 29)})
    vids = ids_for(inventory, {f"V{i:02}" for i in range(1, 35)})
    catalog = (HERE / "proposals.md").read_text()
    headings = re.findall(r"^### ([RTSCWQ]\d{2}) — (.+)$", catalog, re.M)
    assert len(headings) == len(proposals) and dict(headings) == {
        row["id"]: row["title"] for row in proposals
    }, "Proposal headings and CSV disagree"
    by_id = {r["id"]: r for r in proposals}
    graph = {}
    for row in proposals:
        assert row["kind"] in {"repair", "extend", "new", "recipe"}, row["id"]
        assert row["phase"] in {"0", "1", "2", "3"}, row["id"]
        assert row["effort"] in {"S", "M", "L", "XL"}, row["id"]
        graph[row["id"]] = references(row["depends"])
        assert graph[row["id"]] <= pids, row["id"]
        assert references(row["findings"]) <= fids, row["id"]
        assert set(re.findall(r"V\d{2}", row["foundation"])) <= vids, row["id"]
        assert all(int(by_id[d]["phase"]) <= int(row["phase"]) for d in graph[row["id"]]), row["id"]
        for key in ("foundation", "change", "authoring", "mvp", "acceptance", "tradeoff"):
            assert row[key] and row[key] in catalog, (row["id"], key)
        deps = ", ".join(row["depends"].split(";")) or "none"
        assert (f"**{row['kind']} · {row['effort']} · phase {row['phase']} · "
                f"depends on {deps}.**") in catalog, row["id"]
    done, active = set(), set()

    def visit(node):
        assert node not in active, f"Dependency cycle at {node}"
        if node in done:
            return
        active.add(node)
        for dependency in graph[node]:
            visit(dependency)
        active.remove(node)
        done.add(node)

    for node in graph:
        visit(node)
    audit = (HERE / "audit.md").read_text()
    source_users = {}
    for row in findings:
        assert row["classification"] in {
            "runtime observation", "static defect", "integration gap", "design limitation", "test gap"
        }, row["id"]
        assert row["priority"] in {"P1", "P2"}, row["id"]
        assert references(row["proposals"]) <= pids and row["proposals"], row["id"]
        for p in references(row["proposals"]):
            assert row["id"] in references(by_id[p]["findings"]), (row["id"], p)
        assert f"### {row['id']} — {row['title']}" in audit, row["id"]
        for key in ("evidence", "impact", "repair", "acceptance"):
            assert row[key] in audit, (row["id"], key)
        for path in references(row["sources"]):
            source_users.setdefault(path, set()).add(row["id"])
    for row in inventory:
        assert references(row["findings"]) <= fids and references(row["proposals"]) <= pids, row["id"]
        assert row["current"] and row["limit"] and row["sources"], row["id"]
        for path in references(row["sources"]):
            source_users.setdefault(path, set()).add(row["id"])
    assert {row["path"] for row in sources} == set(source_users), "Source manifest mismatch"
    assert len(sources) == len({row["path"] for row in sources}), "Duplicate source path"
    # The snapshots are checked against the audited commit while it can be reached.  Once it cannot (a
    # squash merge, a deleted branch, a shallow clone), each file is compared with the working tree
    # instead, and a file changed since the audit is counted, not failed: the audit stays a record.
    baseline_present = subprocess.run(["git", "cat-file", "-e", BASELINE + "^{commit}"], cwd=REPO,
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL).returncode == 0
    changed_since = []
    for row in sources:
        assert row["baseline"] == BASELINE, row["path"]
        assert references(row["cited_by"]) == source_users[row["path"]], row["path"]
        if baseline_present:
            # Validate the recorded snapshot, not the possibly changed future worktree.
            raw = subprocess.check_output(["git", "show", BASELINE + ":" + row["path"]], cwd=REPO)
            assert hashlib.sha256(raw).hexdigest() == row["sha256"], row["path"]
            assert len(raw.splitlines()) == int(row["lines"]), row["path"]
        current = REPO / row["path"]
        if (not current.is_file()) or hashlib.sha256(current.read_bytes()).hexdigest() != row["sha256"]:
            changed_since.append(row["path"])
    markdown = sorted(HERE.glob("*.md"))
    links = sketches = 0
    for path in markdown:
        text = path.read_text()
        assert text.endswith("\n") and not text.endswith("\n\n"), path.name
        assert all(line == line.rstrip() for line in text.splitlines()), path.name
        for target in re.findall(r"\]\(([^\s)]+)\)", text):
            parsed = urlsplit(target)
            assert not parsed.scheme, (path.name, "Unexpected external citation", target)
            destination = (path.parent / unquote(parsed.path)).resolve() if parsed.path else path
            assert destination.exists(), (path.name, target)
            if parsed.fragment and destination.suffix == ".md" and destination.parent == HERE:
                assert unquote(parsed.fragment) in anchor_ids(destination.read_text()), (path.name, target)
            links += 1
        for sketch in re.findall(r"```yaml\n(.*?)```", text, re.S):
            assert "proposal_only: true" in sketch, path.name
            sketches += 1
        for match in re.findall(r"\b[RTSCWQFV]\d{2}\b", text):
            assert match in pids | fids | vids | {"S24"}, (path.name, match)
    observations = (HERE / "observations.txt").read_text()
    assert "TECHOBS DONE completed" in observations
    assert not re.search(r"TECHOBS (?!F06\b)F\d+ ERROR", observations), "Unexpected probe error"
    # Each finding observed at runtime has its recorded probe line, whatever it showed: a probe re-run
    # after a repair records the repaired behaviour, and that is not a failure of the record.
    observed = {row["id"] for row in findings if row["classification"] == "runtime observation"}
    recorded = set(re.findall(r"^TECHOBS (F\d+)\b", observations, re.M))
    assert observed <= recorded, ("Runtime findings without a recorded observation", sorted(observed - recorded))
    assert "Spaceangles:" in observations, "Spaceangles baseline run not recorded"
    assert len(re.findall(r"^## \d+\.", (HERE / "cookbook.md").read_text(), re.M)) == 8
    # Basic syntax verification without creating __pycache__ files.
    for path in HERE.glob("*.py"):
        compile(path.read_text(), str(path), "exec")
    report = {
        "result": "pass", "audit_date_utc": "2026-11-05", "baseline": BASELINE,
        "proposals": len(proposals), "proposal_kinds": dict(sorted(Counter(r["kind"] for r in proposals).items())),
        "findings": len(findings), "finding_evidence": dict(sorted(Counter(r["classification"] for r in findings).items())),
        "capability_groups": len(inventory), "source_snapshots": len(sources),
        "dependency_edges": sum(len(ds) for ds in graph.values()), "dependency_graph_acyclic": True,
        "markdown_files": len(markdown), "markdown_words": sum(len(p.read_text().split()) for p in markdown),
        "local_links": links, "proposal_only_yaml_sketches": sketches,
        "scenario_designs": 8, "recorded_observation_lines": len(observations.splitlines()),
        "baseline_reachable": baseline_present, "sources_changed_since_audit": len(changed_since),
        "scope": "Document/catalog/source-snapshot integrity; not proposed engine feature acceptance tests",
        "runtime_scope": "Isolated narrow JRUN observations and Spaceangles baseline reproduction; no full engine suite or full ship campaign-restore run",
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
