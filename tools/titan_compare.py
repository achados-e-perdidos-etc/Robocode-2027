#!/usr/bin/env python3
"""Compara ideias do Titan em series de tres rounds. Requer o projeto enviado extraido."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import secrets
import train
import bo3

CONTEXTS = ("mirror", "titan", "diamond", "crowd", "mixed")
SEEDS_TRAIN = (19, 53)
SEEDS_TEST = (101, 151, 211)


def compile_titan(args, paths):
    compiler = ["java", "-jar", str(args.ecj), "-8", "-nowarn"] if args.ecj else ["javac", "-source", "8", "-target", "8"]
    subprocess.run(compiler + ["-encoding", "UTF-8", "-cp",
                   str(args.home / "libs/robocode.jar") + os.pathsep + str(args.home / "robots"),
                   "-d", str(args.home / "robots")] + [str(p) for p in paths], check=True)


def install_nemesis(args, name, source):
    source = train.variant(source, name, export=False)
    marker = 'if (getRoundNum() == 0) out.println("COLD_START gun=" + GENERAL_GUN.samples + " surf=" + GENERAL_SURF.samples);'
    source = source.replace("public void run() {", "public void run() {\n        " + marker, 1)
    train.install(args, name, source)


def phase(args, label, names, seeds, titan_source):
    suite = []
    for name in names:
        for index, context in enumerate(CONTEXTS):
            for seed in seeds:
                if context == "mirror": other = "treino.TitanAnchor*"
                elif context == "titan":
                    # Cada serie tem um proprietario de dados novo. Os tres rounds
                    # da mesma serie podem compartilhar o aprendizado normal.
                    clone = "T" + label[0].upper() + args.run_tag + name + str(seed)
                    path = args.home / "robots/titan" / (clone + ".java")
                    if path.exists():
                        raise ValueError("Use a clean test installation or a new case namespace: " + str(path))
                    path.write_text(titan_source.replace("public class TitanVingation", "public class " + clone, 1), encoding="utf-8")
                    compile_titan(args, [path]); other = "titan." + clone + "*"
                elif context == "diamond": other = "voidious.Diamond*"
                elif context == "crowd": other = "sample.Walls,sample.SpinBot,sample.Crazy,sample.RamFire,sample.TrackFire,sample.Corners"
                else: other = "voidious.Diamond*,sample.SpinBot,sample.TrackFire"
                suite.append((f"{name}-{context}-{seed}", "treino." + name + "*," + other, 3, seed + index * 41))
    results = train.run_suite(args, label, suite)
    report = {}
    for name in names:
        report[name] = []
        for index, context in enumerate(CONTEXTS):
            for seed in seeds:
                result = results[f"{name}-{context}-{seed}"]
                row = bo3.summarize_series(result, name)
                row.update({"context": context, "seed": seed + index * 41, "scores": result["scores"]})
                report[name].append(row)
        if len({tuple(row["cold_start"]) for row in report[name]}) != 1:
            raise ValueError("Memory leaked between independent series")
    return report


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=Path)
    parser.add_argument("--ecj", type=Path)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--titan-project", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=train.ROOT / "training-runs/titan")
    parser.add_argument("--position-seed", type=int, default=0)
    parser.add_argument("--run-tag", default="R" + secrets.token_hex(3), help="Identificador novo para manter os dados do Titan separados")
    args = parser.parse_args()
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_]{0,19}", args.run_tag):
        parser.error("run-tag must be a Java identifier of at most 20 characters")
    for key in ("home", "ecj", "baseline", "titan_project", "output"):
        value = getattr(args, key)
        if value is not None: setattr(args, key, value.resolve())
    args.output.mkdir(parents=True, exist_ok=True)
    runner = args.output / "TrainingRunner.java"; runner.write_text(train.RUNNER)
    train.compile_java(args, [runner], args.output)
    modules = sorted((args.titan_project / "src/main/java").rglob("*.java"))
    if len(modules) != 18: raise ValueError("Expected the 18 original Titan source modules")
    compile_titan(args, modules)
    titan = (args.titan_project / "src/main/java/titan/TitanVingation.java").read_text(encoding="utf-8")
    baseline = args.baseline.read_text(encoding="utf-8")
    template = (train.ROOT / "robots/equipe/Nemesis.java").read_text(encoding="utf-8")
    sources = {"TitanBase": baseline}
    for name, threats, flatten in (("Threat", True, False), ("Flatten", False, True), ("Combined", True, True)):
        source = re.sub(r"USE_THREATS = (true|false)", "USE_THREATS = " + str(threats).lower(), template)
        source = re.sub(r"USE_FLATTENER = (true|false)", "USE_FLATTENER = " + str(flatten).lower(), source)
        if "USE_THREATS" not in source: raise ValueError("Prototype missing from source")
        sources[name] = source
    install_nemesis(args, "TitanAnchor", baseline)
    for name, source in sources.items(): install_nemesis(args, name, source)
    selection = phase(args, "selection", list(sources), SEEDS_TRAIN, titan)
    ranking = sorted(sources, key=lambda name: bo3.ranking_key(selection[name]), reverse=True)
    selected = ranking[0]
    result = {"rule": "win at least two of three rounds", "selection": selection, "run_tag": args.run_tag,
              "selected": selected, "ranking": ranking,
              "selection_keys": {name: bo3.ranking_key(rows) for name, rows in selection.items()},
              "baseline_sha256": hashlib.sha256(baseline.encode()).hexdigest(),
              "titan_sources": {str(p.relative_to(args.titan_project)): hashlib.sha256(p.read_bytes()).hexdigest() for p in modules}}
    (args.output / "progress.json").write_text(json.dumps(result, indent=2) + "\n")
    print("SELECTED", selected, result["selection_keys"], flush=True)
    source = sources[selected]
    (args.output / "Nemesis.java").write_text(source, encoding="utf-8")
    install_nemesis(args, "TitanFinal", source)
    validation = phase(args, "validation", ["TitanBase", "TitanFinal"], SEEDS_TEST, titan)
    result["validation"] = validation
    result["validation_keys"] = {name: bo3.ranking_key(rows) for name, rows in validation.items()}
    result["candidate_sha256"] = hashlib.sha256(source.encode()).hexdigest()
    result["same_code_control"] = source == baseline
    (args.output / "results.json").write_text(json.dumps(result, indent=2) + "\n")
    print("VALIDATION", result["validation_keys"], flush=True)


if __name__ == "__main__":
    main()
