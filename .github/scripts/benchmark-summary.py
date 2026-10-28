import glob
import json
import os
import sys


def main(folder):
    files = sorted(glob.glob(os.path.join(folder, "*.json")))
    if not files:
        print("no benchmark results found")
        return
    for path in files:
        with open(path) as handle:
            data = json.load(handle)
        for bench in data.get("benchmarks", []):
            name = bench.get("className", "").split(".")[-1] + "." + bench.get("name", "")
            for metric, values in sorted(bench.get("metrics", {}).items()):
                print("%s %s median=%.2f min=%.2f max=%.2f" % (
                    name, metric, values.get("median", 0), values.get("minimum", 0), values.get("maximum", 0)))
            for metric, values in sorted(bench.get("sampledMetrics", {}).items()):
                picked = " ".join("%s=%.2f" % (key, values[key]) for key in ("P50", "P90", "P95", "P99") if key in values)
                print("%s %s %s" % (name, metric, picked))


main(sys.argv[1] if len(sys.argv) > 1 else "benchmark-out")
