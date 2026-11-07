#!/usr/bin/env python3
"""Seleciona e avalia partidas independentes de EXATAMENTE tres rounds."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import train

CONTEXTS = [
    ("mirror", "treino.Anchor*"),
    ("diamond", "voidious.Diamond*"),
    ("crowd", "sample.Walls,sample.SpinBot,sample.Crazy,sample.RamFire,sample.TrackFire,sample.Corners"),
    ("mixed", "voidious.Diamond*,sample.SpinBot,sample.TrackFire"),
]
SELECTION_SEEDS = (11, 23, 37)
VALIDATION_SEEDS = (101, 127, 149, 173)


def robot_key(name):
    return name.split(" ")[0].rstrip("*")


def prepare_template(source):
    if "static final boolean FAST_OBSERVATION" in source:
        return source
    patch = json.loads((Path(__file__).with_name("bo3-prototype.json")).read_text())
    if hashlib.sha256(source.encode()).hexdigest() != patch["baseline_sha256"]:
        raise ValueError("Experimental prototype does not match this robot version; update bo3-prototype.json")
    for change in patch["changes"]:
        if source.count(change["old"]) != 1:
            raise ValueError("Ambiguous experimental prototype patch")
        source = source.replace(change["old"], change["new"], 1)
    return source


def summarize_series(result, name):
    own = train.score_for(result, name)
    key = "treino." + name
    records = sorted((row for row in result["round_scores"] if robot_key(row["robot"]) == key),
                     key=lambda row: row["round"])
    if [row["round"] for row in records] != [0, 1, 2]:
        raise ValueError("A BO3 must contain all three round snapshots")
    cumulative = [row["firsts"] for row in records]
    round_wins = [cumulative[0], cumulative[1] - cumulative[0], cumulative[2] - cumulative[1]]
    if any(value not in (0, 1) for value in round_wins) or sum(round_wins) != own["wins"]:
        raise ValueError("Round wins disagree with official battle results")
    leaders = [row for row in result["scores"] if row["wins"] >= 2]
    winner = leaders[0]["robot"] if len(leaders) == 1 else None
    own_lines = [line for line in result["block"].splitlines() if line.startswith("[" + key + "*")]
    cold = re.findall(r"COLD_START gun=(\d+) surf=(\d+)", "\n".join(own_lines))
    if len(cold) != 1:
        raise ValueError("Expected exactly one cold start per three-round series")
    stats = [dict(zip(("round", "shots", "hits", "waves", "matched", "walls", "skipped", "virtual"),
                      (int(value or 0) for value in row))) for row in re.findall(
        r"(?:TitanPrime|Nemesis) round=(\d+) shots=(\d+) hits=(\d+) waves=(\d+) matched=(\d+) walls=(\d+) skipped=(\d+)(?: virtual=(\d+))?",
        "\n".join(own_lines))]
    return {**own, "round_wins": round_wins,
            "series_win": winner is not None and robot_key(winner) == key,
            "first_round_win": round_wins[0] == 1, "majority_winner": winner,
            "cold_start": list(map(int, cold[0])),
            "position_adjustments": result.get("position_adjustments", []),
            "round_stats": stats,
            "virtual": sum(int(value) for value in re.findall(r"virtual=(\d+)", "\n".join(own_lines)))}


def ranking_key(matches):
    # Uma serie ganha vale mais que qualquer desempate de pontos.
    return (sum(row["series_win"] for row in matches),
            sum(row["first_round_win"] for row in matches),
            sum(row["wins"] for row in matches),
            sum(row["score_share"] for row in matches) / len(matches),
            -sum(row["skipped"] for row in matches))


def install(args, name, source):
    source = train.variant(source, name, export=False)
    marker = 'if (getRoundNum() == 0) out.println("COLD_START gun=" + GENERAL_GUN.samples + " surf=" + GENERAL_SURF.samples);'
    source = source.replace("public void run() {", "public void run() {\n        " + marker, 1)
    train.install(args, name, source)


def evaluate(args, stage, names, seeds):
    suite = [(f"{name}-{context}-{seed}", "treino." + name + "*," + bots, 3, seed + index * 41)
             for name in names for index, (context, bots) in enumerate(CONTEXTS) for seed in seeds]
    results = train.run_suite(args, stage, suite)
    rows = {}
    for name in names:
        rows[name] = []
        for index, (context, bots) in enumerate(CONTEXTS):
            for seed in seeds:
                key = f"{name}-{context}-{seed}"
                summary = summarize_series(results[key], name)
                summary.update({"context": context, "seed": seed + index * 41,
                                "scores": results[key]["scores"]})
                rows[name].append(summary)
    # Sem heranca acidental de exemplos de uma partida de treino para outra.
    for name in names:
        if len({tuple(row["cold_start"]) for row in rows[name]}) != 1:
            raise ValueError("Knowledge leaked between independent series: " + name)
    return rows


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=Path)
    parser.add_argument("--ecj", type=Path)
    parser.add_argument("--baseline", required=True, type=Path)
    parser.add_argument("--output", type=Path, default=train.ROOT / "training-runs/bo3")
    parser.add_argument("--position-seed", type=int, default=0)
    args = parser.parse_args()
    for key in ("home", "ecj", "baseline", "output"):
        value = getattr(args, key)
        if value is not None: setattr(args, key, value.resolve())
    args.output.mkdir(parents=True, exist_ok=True)
    runner = args.output / "trainingrunner.java"
    runner.write_text(train.RUNNER, encoding="utf-8")
    train.compile_java(args, [runner], args.output)
    baseline = args.baseline.read_text(encoding="utf-8")
    template = prepare_template((train.ROOT / "robots/equipe/titanprime.java").read_text(encoding="utf-8"))
    sources = {"Baseline": baseline}
    for name, observe, warm in (("Observe", True, False), ("Warm", False, True), ("Both", True, True)):
        sources[name] = re.sub(r"FAST_OBSERVATION = (true|false)", "FAST_OBSERVATION = " + str(observe).lower(), template)
        sources[name] = re.sub(r"WARM_DEFENSE = (true|false)", "WARM_DEFENSE = " + str(warm).lower(), sources[name])
    install(args, "Anchor", baseline)
    for name, source in sources.items(): install(args, name, source)
    selection = evaluate(args, "selection", list(sources), SELECTION_SEEDS)
    ranking = sorted(sources, key=lambda name: ranking_key(selection[name]), reverse=True)
    selected = ranking[0]
    progress = {"rounds_per_series": 3, "selection_seeds": SELECTION_SEEDS,
                "validation_seeds": VALIDATION_SEEDS, "contexts": CONTEXTS,
                "baseline_sha256": hashlib.sha256(baseline.encode()).hexdigest(),
                "selection": selection, "ranking": ranking, "selected": selected,
                "selection_keys": {name: ranking_key(rows) for name, rows in selection.items()}}
    (args.output / "progress.json").write_text(json.dumps(progress, indent=2) + "\n")
    print("SELECTED", selected, "RANKINGS", progress["selection_keys"], flush=True)
    candidate = train.production_source(sources[selected])
    (args.output / "titanprime.java").write_text(candidate, encoding="utf-8")
    install(args, "Candidate", candidate)
    validation = evaluate(args, "validation", ["Baseline", "Candidate"], VALIDATION_SEEDS)
    progress["validation"] = validation
    progress["validation_keys"] = {name: ranking_key(rows) for name, rows in validation.items()}
    progress["candidate_sha256"] = hashlib.sha256(candidate.encode()).hexdigest()
    progress["position_seed"] = args.position_seed
    (args.output / "results.json").write_text(json.dumps(progress, indent=2) + "\n")
    print("VALIDATION", progress["validation_keys"], flush=True)


if __name__ == "__main__":
    main()
