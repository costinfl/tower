#!/usr/bin/env python3
"""Cut an OpenAPI description down to the operations Tower actually reads.

A vendor's full description is not something to commit: GitHub's is 12.9 MB,
which would dwarf this repository's own source and turn every upstream edit into
an unreadable diff. What Tower needs from it is the schemas for two endpoints.
This walks every $ref reachable from the named paths and writes a standalone
description carrying only those — 96 KB for GitHub, at the time of writing.

Two properties are deliberate.

Only GET operations are kept. Tower issues nothing else (ADR-001), so the
vendored description cannot describe a write even in principle. A reader who
wonders whether the slice could have smuggled one in can see that it could not.

The output is deterministic: sorted keys, fixed separators, one trailing
newline. Re-slicing the same upstream produces a byte-identical file, so a diff
means the vendor changed something rather than that the script ran again. This
is the property NFR-025 asks of generated documents, for the same reason.

Usage:
  python3 scripts/slice-openapi.py \\
    --from <url-or-path> --path '/repos/{owner}/{repo}' --out specs/.../x.json
"""

import argparse
import json
import sys
import urllib.request

# Component sections a $ref can point at. Named rather than discovered, so a
# pointer into something unexpected fails loudly instead of being dropped and
# leaving a slice that validates nothing.
COMPONENT_SECTIONS = (
    "schemas", "responses", "parameters", "examples",
    "requestBodies", "headers", "links", "callbacks",
)


def load(source):
    if source.startswith("http://") or source.startswith("https://"):
        # Honours the environment's proxy settings, which is how anything
        # reaches the network here.
        with urllib.request.urlopen(source, timeout=180) as response:
            return json.loads(response.read().decode("utf-8"))
    with open(source, encoding="utf-8") as handle:
        return json.load(handle)


def without_examples(node, keys_are_names=False):
    """The same description with its examples dropped.

    Examples are documentation. They validate nothing, and for GitHub they were
    39 KB of a 106 KB slice — a third of a file whose whole purpose is to be
    small enough to read a diff of.

    ``keys_are_names`` guards the one place a blind filter would corrupt the
    schema: under ``properties``, the keys are property names, and a vendor is
    perfectly entitled to describe a field called "example".
    """
    if isinstance(node, dict):
        pruned = {}
        for key, value in node.items():
            if not keys_are_names and key in ("example", "examples"):
                continue
            pruned[key] = without_examples(
                value,
                keys_are_names=not keys_are_names
                and key in ("properties", "patternProperties"))
        return pruned
    if isinstance(node, list):
        return [without_examples(value) for value in node]
    return node


def slice_spec(spec, wanted_paths):
    kept = {section: {} for section in COMPONENT_SECTIONS}
    components = spec.get("components", {})

    def follow(node):
        if isinstance(node, dict):
            for key, value in node.items():
                if key == "$ref" and isinstance(value, str):
                    include(value)
                else:
                    follow(value)
        elif isinstance(node, list):
            for value in node:
                follow(value)

    def include(ref):
        if not ref.startswith("#/components/"):
            raise SystemExit(
                "This slicer only follows local component references, and found "
                + ref + ". An external or inline-document reference needs the "
                "description bundling first.")
        _, _, section, name = ref.split("/", 3)
        if section not in COMPONENT_SECTIONS:
            raise SystemExit("Unknown component section in " + ref + ".")
        if name in kept[section]:
            return
        target = components.get(section, {}).get(name)
        if target is None:
            raise SystemExit(ref + " is referenced but not defined upstream.")
        # Recorded before recursing, so a schema referring to itself — GitHub's
        # do — terminates instead of running away.
        kept[section][name] = target
        follow(target)

    paths = {}
    for path in wanted_paths:
        item = spec.get("paths", {}).get(path)
        if item is None:
            raise SystemExit("The upstream description has no path " + path + ".")
        if "get" not in item:
            raise SystemExit(path + " has no GET, and only GET is kept.")
        # Path-level parameters apply to the operation and are easy to lose.
        operation = {"get": item["get"]}
        if "parameters" in item:
            operation["parameters"] = item["parameters"]
        paths[path] = operation
        follow(operation)

    sliced = {
        "openapi": spec["openapi"],
        "info": dict(spec["info"]),
        "paths": paths,
        "components": {s: v for s, v in kept.items() if v},
    }
    if "servers" in spec:
        sliced["servers"] = spec["servers"]
    # Small, and referred to by name rather than by $ref, so it would not be
    # picked up by the walk above.
    if "securitySchemes" in components:
        sliced["components"]["securitySchemes"] = components["securitySchemes"]
    return sliced


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--from", dest="source", required=True,
                        help="Upstream description, as a URL or a local file.")
    parser.add_argument("--path", dest="paths", action="append", required=True,
                        help="A path to keep. Repeatable. GET only.")
    parser.add_argument("--out", required=True, help="Where to write the slice.")
    parser.add_argument("--title", help="Overrides info.title, for a clearer diff.")
    args = parser.parse_args()

    spec = without_examples(load(args.source))
    sliced = slice_spec(spec, args.paths)
    if args.title:
        sliced["info"]["title"] = args.title

    # Provenance travels with the file. An x- extension is valid anywhere in
    # OpenAPI and carries no timestamp, so it does not disturb the byte-identical
    # property above.
    sliced["info"]["x-tower-slice"] = {
        "source": args.source,
        "paths": sorted(args.paths),
        "note": "Sliced by scripts/slice-openapi.py. GET operations only.",
    }

    with open(args.out, "w", encoding="utf-8") as handle:
        json.dump(sliced, handle, indent=2, sort_keys=True, ensure_ascii=True)
        handle.write("\n")

    counts = ", ".join(
        section + ": " + str(len(items))
        for section, items in sorted(sliced["components"].items()))
    print("wrote " + args.out, file=sys.stderr)
    print("  paths: " + str(len(sliced["paths"])) + " | " + counts, file=sys.stderr)


if __name__ == "__main__":
    main()
