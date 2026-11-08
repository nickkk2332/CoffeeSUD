#!/usr/bin/env python3
"""Read-only integrity checks for the research dossier; no engine tests."""

from __future__ import annotations

import csv
import json
import re
from collections import Counter
from pathlib import Path
from urllib.parse import unquote, urlsplit


ROOT = Path(__file__).resolve().parent


def read_csv(name: str) -> list[dict[str, str]]:
    with (ROOT / name).open(newline="", encoding="utf-8") as handle:
        rows = list(csv.DictReader(handle))
    assert rows and all(None not in row and None not in row.values() for row in rows), name
    return rows


def main() -> None:
    proposals = read_csv("proposals.csv")
    sources = read_csv("sources.csv")
    coverage = read_csv("coverage.csv")
    ids = {row["id"] for row in proposals}
    expected = {
        f"{prefix}{number:02d}"
        for prefix, count in {"A": 24, "I": 20, "G": 24, "P": 16, "Q": 12}.items()
        for number in range(1, count + 1)
    }
    assert len(proposals) == 96 and ids == expected, "Proposal IDs must be unique and complete"
    catalog = (ROOT / "proposals.md").read_text(encoding="utf-8")
    headings = re.findall(r"^### ([A-Z]\d{2}) — (.+)$", catalog, re.M)
    assert len(headings) == 96 and dict(headings) == {
        row["id"]: row["title"] for row in proposals
    }, "CSV and Markdown proposal headings disagree"

    graph: dict[str, set[str]] = {}
    for row in proposals:
        assert row["kind"] in {"recipe", "extend", "new", "defer"}, row["id"]
        assert row["effort"] in {"S", "M", "L", "XL"}, row["id"]
        assert row["phase"] in {"0", "1", "2", "3", "4"}, row["id"]
        graph[row["id"]] = set(filter(None, row["depends"].split(";")))
        assert graph[row["id"]] <= ids, (row["id"], "missing dependency")
        for field in ("change", "foundation", "mvp", "acceptance"):
            assert row[field], (row["id"], field)
        for field in ("change", "foundation", "mvp", "acceptance"):
            assert row[field] in catalog, (row["id"], field)
        dependencies = ", ".join(row["depends"].split(";")) if row["depends"] else "none"
        metadata = (
            f"**{row['kind']} · {row['effort']} · phase {row['phase']} · "
            f"depends on {dependencies}.**"
        )
        assert metadata in catalog, (row["id"], "catalog metadata")
        for source in row["sources"].split(";"):
            assert (ROOT / source).is_file(), (row["id"], source)

    done: set[str] = set()
    active: set[str] = set()

    def visit(node: str) -> None:
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

    source_urls = {row["url"] for row in sources}
    assert len(source_urls) == len(sources), "Duplicate registered source URL"
    allowed_access = {"excerpt-opened", "indexed-excerpt", "rendering-limited", "access-failed"}
    for row in sources:
        assert row["access"] in allowed_access, row["url"]
        assert row["accessed_utc"] == "2026-11-05", row["url"]
        assert row["source_type"] and row["scope_note"], row["url"]

    local_links = 0
    external_links = 0
    referenced_urls: set[str] = set()
    url_locations: dict[str, set[str]] = {}
    yaml_examples = 0
    markdown_files = sorted(ROOT.glob("*.md"))
    for path in markdown_files:
        text = path.read_text(encoding="utf-8")
        assert text.endswith("\n") and not text.endswith("\n\n"), (path.name, "EOF newline")
        assert not any(line.rstrip() != line for line in text.splitlines()), path.name
        for code in re.findall(r"```yaml\n(.*?)```", text, re.S):
            yaml_examples += 1
            assert re.search(r"^proposal_only: true$", code, re.M), path.name
        for target in re.findall(r"\]\(([^\s)]+)\)", text):
            parsed = urlsplit(target)
            if parsed.scheme in {"http", "https"}:
                external_links += 1
                referenced_urls.add(target)
                url_locations.setdefault(target, set()).add(path.name)
                assert target in source_urls, (path.name, "unregistered source", target)
            elif not parsed.scheme:
                if parsed.path:
                    destination = (path.parent / unquote(parsed.path)).resolve()
                    assert destination.exists(), (path.name, target)
                    local_links += 1
                    if parsed.fragment and destination.parent == ROOT and destination.suffix == ".md":
                        headings = re.findall(r"^#{1,6} (.+)$", destination.read_text(), re.M)
                        anchors = {
                            re.sub(r"\s+", "-", re.sub(r"[^\w\s-]", "", heading.lower()))
                            for heading in headings
                        }
                        assert unquote(parsed.fragment) in anchors, (path.name, target)

    assert source_urls == referenced_urls, "Source register must match cited references"
    for row in sources:
        assert row["used_in"] and all(
            (ROOT / name).is_file() for name in row["used_in"].split(";")
        ), row["url"]
        assert set(row["used_in"].split(";")) == url_locations[row["url"]], row["url"]
    for row in coverage:
        assert (ROOT / row["guide"]).is_file(), row
        assert set(filter(None, row["proposal_ids"].split(";"))) <= ids, row
        assert row["limits"], row

    history = (ROOT / "poe1-history.md").read_text(encoding="utf-8")
    release_rows = re.findall(r"^\| (?:0\.11|1\.\d|2\.\d|3\.\d+) .+$", history, re.M)
    assert len(release_rows) == 49, f"Expected 48 league entries plus announcement, found {len(release_rows)}"
    systems = (ROOT / "poe1-systems.md").read_text()
    class_rows = re.findall(
        r"^\| (Marauder|Duelist|Ranger|Shadow|Witch|Templar|Scion) \| ([^|]+) \|",
        systems,
        re.M,
    )
    assert len(class_rows) == 7 and sum(len(names.split(",")) for _, names in class_rows) == 21
    guild_text = (ROOT / "three-scapes.md").read_text(encoding="utf-8")
    guilds = set(re.findall(r"https://www\.3scapes\.org/guilds/([^/)]+)/", guild_text))
    assert len(guilds) == 22, guilds
    lost_souls = (ROOT / "lost-souls.md").read_text()
    deep_section = lost_souls.split("## Guilds worth studying in depth")[1].split("## Broader guild directory coverage")[0]
    deep_guilds = re.findall(r"^\| \[.*?\]\(https://wiki\.lostsouls\.org/articles/", deep_section, re.M)
    assert len(deep_guilds) == 19, len(deep_guilds)
    recipes = re.findall(r"^## \d+\.", (ROOT / "cookbook.md").read_text(), re.M)
    assert len(recipes) == 10, len(recipes)

    report = {
        "result": "pass",
        "checked_at_utc_date": "2026-11-05",
        "baseline_commit": "df43a4c7",
        "markdown_files": len(markdown_files),
        "markdown_words": sum(len(path.read_text().split()) for path in markdown_files),
        "proposals": len(proposals),
        "proposal_kinds": dict(sorted(Counter(row["kind"] for row in proposals).items())),
        "dependency_edges": sum(len(dependencies) for dependencies in graph.values()),
        "dependency_graph_acyclic": True,
        "coverage_rows": len(coverage),
        "registered_cited_sources": len(sources),
        "source_access": dict(sorted(Counter(row["access"] for row in sources).items())),
        "local_file_links": local_links,
        "external_link_occurrences": external_links,
        "proposal_only_yaml_examples": yaml_examples,
        "poe1_league_entries": 48,
        "poe1_announcement_entries": 1,
        "poe1_named_ascendancy_branches": 21,
        "three_scapes_guild_pages": len(guilds),
        "lost_souls_deep_guild_pages": len(deep_guilds),
        "design_cookbook_recipes": len(recipes),
        "scope": "Document/catalog integrity only; no engine feature or live-game behavior tests",
    }
    print(json.dumps(report, indent=2, ensure_ascii=False))


if __name__ == "__main__":
    main()
