#!/usr/bin/env python3
"""Compara uma candidata pronta com a versao anterior sem refazer as geracoes."""
import argparse
import hashlib
import json
import pathlib
import train


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=pathlib.Path)
    parser.add_argument("--baseline", required=True, type=pathlib.Path)
    parser.add_argument("--candidate", type=pathlib.Path, default=train.ROOT / "robots/equipe/TitanPrime.java")
    parser.add_argument("--ecj", type=pathlib.Path)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--position-seed", type=int, default=1000)
    parser.add_argument("--output", type=pathlib.Path, default=train.ROOT / "training-runs/evaluation")
    args = parser.parse_args()
    if args.rounds < 1: parser.error("rounds must be positive")
    for key in ("home", "baseline", "candidate", "ecj", "output"):
        value = getattr(args, key)
        if value is not None: setattr(args, key, value.resolve())
    args.output.mkdir(parents=True, exist_ok=True)
    runner = args.output / "TrainingRunner.java"
    runner.write_text(train.RUNNER, encoding="utf-8")
    train.compile_java(args, [runner], args.output)
    candidate = args.candidate.read_text(encoding="utf-8")
    baseline = args.baseline.read_text(encoding="utf-8")
    # Preserva os modelos incorporados da candidata ao mudar somente seu nome.
    train.install(args, "Candidate", train.variant(candidate, "Candidate", export=False))
    train.install(args, "Baseline", train.variant(baseline, "Baseline", export=False))
    suite = [(name + "-" + key, "treino." + name + "*," + bots, args.rounds, 901 + index * 17)
             for name in ("Baseline", "Candidate") for index, (key, bots) in enumerate(train.HOLDOUT)]
    results = train.run_suite(args, "comparison", suite)
    report = {"position_seed": args.position_seed, "rounds": args.rounds,
              "candidate_sha256": hashlib.sha256(candidate.encode()).hexdigest(),
              "baseline_sha256": hashlib.sha256(baseline.encode()).hexdigest(),
              "results": {name: {key: {"own": train.score_for(results[name + "-" + key], name),
                                      "scores": results[name + "-" + key]["scores"]}
                                  for key, _ in train.HOLDOUT} for name in ("Baseline", "Candidate")}}
    (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    for name, contexts in report["results"].items():
        print(name, {key: {k: row["own"][k] for k in ("score_share", "wins", "shots", "hits", "skipped")}
                     for key, row in contexts.items()}, flush=True)


if __name__ == "__main__":
    main()
