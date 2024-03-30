# bp2build progress graphs

This directory contains tools to generate reports and .png graphs of the
bp2build conversion progress, for any module.

This tool relies on `json-module-graph` and `bp2build` to be buildable targets
for this branch.

## Prerequisites

* `/usr/bin/dot`: turning dot graphviz files into .pngs
* Optional: `/usr/bin/jq`: running the query scripts over the json-module-graph.

Tip: `--use_queryview=true` allows running `bp2build-progress.py` without `jq`.

## Instructions

# Generate the report for a module, e.g. adbd

```
./bp2build-progress.py report -m adbd
```

or:

```
./bp2build-progress.py report -m adbd --use_queryview=true
```

# Generate the report for a module, e.g. adbd

```
./bp2build-progress.py graph -m adbd > /tmp/graph.in && dot -Tpng -o /tmp/graph.png /tmp/graph.in
```

or:

```
./bp2build-progress.py graph -m adbd --use_queryview=true > /tmp/graph.in && dot -Tpng -o /tmp/graph.png /tmp/graph.in
```
