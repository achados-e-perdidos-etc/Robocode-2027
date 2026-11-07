#!/usr/bin/env python3
"""Simulacoes e treino contra RamFire/SpinBot, em series de tres rounds."""
import argparse
import hashlib
import json
import math
from pathlib import Path
import random
import re
import train
import bo3

CONTEXTS = {"ramfire": "sample.RamFire", "ramfire_close": "sample.RamFire",
            "spinbot": "sample.SpinBot", "both": "sample.RamFire,sample.SpinBot"}


def close_positions(seed):
    angle = random.Random(seed).random() * math.tau
    dx, dy = math.sin(angle) * 50, math.cos(angle) * 50
    return ",".join(str(value) for value in
                    (400 - dx, 300 - dy, math.degrees(angle),
                     400 + dx, 300 + dy, (math.degrees(angle) + 180) % 360))


def install(args, name, source, genes=None, models=None, export=False):
    text = train.variant(source, name, genes, models, export=export)
    marker = 'if (getRoundNum() == 0) out.println("COLD_START gun=" + GENERAL_GUN.samples + " surf=" + GENERAL_SURF.samples);'
    text = text.replace("public void run() {", "public void run() {\n        " + marker, 1)
    train.install(args, name, text)


def evaluate(args, label, names, seeds, contexts=CONTEXTS):
    suite, overrides = [], {}
    for name in names:
        for context, robots in contexts.items():
            for seed in seeds:
                key = f"{name}-{context}-{seed}"
                suite.append((key, "treino." + name + "*," + robots, 3, seed))
                if context == "ramfire_close": overrides[key] = close_positions(seed + args.position_seed)
    results = train.run_suite(args, label, suite, position_overrides=overrides)
    report = {}
    for name in names:
        report[name] = []
        for context in contexts:
            for seed in seeds:
                result = results[f"{name}-{context}-{seed}"]
                row = bo3.summarize_series(result, name)
                if sorted(result["round_turns"]) != [0, 1, 2]:
                    raise ValueError("Missing duration for a three-round series")
                row.update({"context": context, "seed": seed, "round_turns": result["round_turns"],
                            "scores": result["scores"]})
                report[name].append(row)
        if len({tuple(row["cold_start"]) for row in report[name]}) != 1:
            raise ValueError("Learning leaked between independent series")
    return report


def rank(rows):
    return (sum(row["series_win"] for row in rows), sum(row["first_round_win"] for row in rows),
            sum(row["wins"] for row in rows), -sum(sum(row["round_turns"].values()) for row in rows),
            sum(row["score_share"] for row in rows) / len(rows), -sum(row["skipped"] for row in rows))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=Path)
    parser.add_argument("--ecj", type=Path)
    parser.add_argument("--output", type=Path, default=train.ROOT / "training-runs/ram-spin")
    parser.add_argument("--position-seed", type=int, default=0)
    args = parser.parse_args()
    for key in ("home", "ecj", "output"):
        value = getattr(args, key)
        if value is not None: setattr(args, key, value.resolve())
    args.output.mkdir(parents=True, exist_ok=True)
    runner = args.output / "trainingrunner.java"; runner.write_text(train.RUNNER)
    train.compile_java(args, [runner], args.output)
    source = (train.ROOT / "robots/equipe/titanprime.java").read_text()
    (args.output / "baseline.java.txt").write_text(source)
    genes = json.loads("[" + re.search(r"GENOME = \{([^}]+)\}", source)[1] + "]")
    pressure, range_genes = genes.copy(), genes.copy()
    pressure[1], pressure[5] = 350, 1.10
    range_genes[1], range_genes[5] = 510, 0.85
    profiles = {"RSBase": genes, "RSPressure": pressure, "RSRange": range_genes}
    for name, genome in profiles.items(): install(args, name, source, genome)
    selection = evaluate(args, "selection", list(profiles), (31, 67))
    selected = max(profiles, key=lambda name: rank(selection[name]))
    report = {"baseline_sha256": hashlib.sha256(source.encode()).hexdigest(),
              "profiles": profiles, "selection": selection, "selected": selected,
              "selection_rank": {name: rank(rows) for name, rows in selection.items()}}
    (args.output / "progress.json").write_text(json.dumps(report, indent=2) + "\n")
    print("SELECTED", selected, report["selection_rank"], flush=True)
    models = tuple(re.search(r'PRETRAINED_' + label + r' = "([^"]*)"', source)[1]
                   for label in ("GUN", "SURF"))
    # Continua o treino externo dos pesos gerais, em quatro series curtas.
    for index, (context, robots) in enumerate(CONTEXTS.items()):
        name = "RSWarm" + str(index)
        install(args, name, source, profiles[selected], models, export=True)
        key = name + "-" + context + "-83"
        override = {key: close_positions(83 + args.position_seed)} if context == "ramfire_close" else {}
        result = train.run_suite(args, "pretrain-" + context,
                                 [(key, "treino." + name + "*," + robots, 3, 83)], override)[key]
        models = tuple(re.findall(r"MODEL_" + label + r" ([A-Za-z0-9+/=]+)", result["block"])[-1]
                       for label in ("GUN", "SURF"))
        print("PRETRAIN", context, "complete", flush=True)
    candidate = train.variant(source, "titanprime", profiles[selected], models, export=False)
    candidate = candidate.replace("package treino;", "package equipe;", 1)
    candidate = candidate.replace("TitanPrime 3.2 -", "TitanPrime 3.3 -", 1)
    (args.output / "titanprime.java").write_text(candidate)
    install(args, "RSCandidate", candidate)
    validation = evaluate(args, "validation", ["RSBase", "RSCandidate"], (131, 197, 263))
    guards = evaluate(args, "guards", ["RSBase", "RSCandidate"], (307,),
                      {"walls": "sample.Walls", "diamond": "voidious.Diamond*"})
    report.update({"validation": validation, "guards": guards, "candidate_models": dict(zip(("gun", "surf"), models)),
                   "validation_rank": {name: rank(rows) for name, rows in validation.items()},
                   "candidate_sha256": hashlib.sha256(candidate.encode()).hexdigest()})
    base, new = validation["RSBase"], validation["RSCandidate"]
    base_time = sum(sum(row["round_turns"].values()) for row in base)
    new_time = sum(sum(row["round_turns"].values()) for row in new)
    base_wins, new_wins = sum(row["series_win"] for row in base), sum(row["series_win"] for row in new)
    context_safe = all(sum(row["series_win"] for row in new if row["context"] == context)
                       >= sum(row["series_win"] for row in base if row["context"] == context) for context in CONTEXTS)
    first_safe = sum(row["first_round_win"] for row in new) >= sum(row["first_round_win"] for row in base)
    guard_safe = all(n["series_win"] >= b["series_win"] and n["score_share"] >= b["score_share"] * 0.9
                     for b, n in zip(guards["RSBase"], guards["RSCandidate"]))
    report["promotion_checks"] = {"no_context_series_regression": context_safe,
        "first_round_preserved": first_safe, "guards_preserved": guard_safe,
        "more_series_or_five_percent_faster": new_wins > base_wins or new_time <= base_time * 0.95}
    report["promote"] = all(report["promotion_checks"].values())
    (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    print("VALIDATION", report["validation_rank"], "PROMOTE", report["promote"], report["promotion_checks"], flush=True)


if __name__ == "__main__":
    main()
