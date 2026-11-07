#!/usr/bin/env python3
"""Torneios geneticos + aprendizado neural no motor REAL do Robocode.

Requer instalacao separada de testes, Python 3, JDK ou --ecj e Diamond instalado.
Nao publica no GitHub nem substitui automaticamente o robo de producao.
"""
import argparse
import base64
import hashlib
import json
import math
import pathlib
import random
import re
import subprocess

ROOT = pathlib.Path(__file__).resolve().parents[1]
BOUNDS = [(0.4, 2), (300, 580), (0.006, 0.06), (0.05, 0.65),
          (9000, 36000), (0.65, 1.2), (-3.14, 3.14), (0.6, 1.6), (-3.14, 3.14), (0.6, 1.6)]
TRAIN = [
    ("trackfire", "sample.TrackFire"),
    ("three", "sample.SpinBot,sample.Crazy,sample.RamFire"),
    ("crowd", "sample.Walls,sample.SpinBot,sample.Crazy,sample.RamFire,sample.TrackFire,sample.Corners"),
    ("mixed", "voidious.Diamond*,sample.TrackFire,sample.Walls"),
]
HOLDOUT = [
    ("veloci", "sample.VelociRobot"),
    ("diamond", "voidious.Diamond*"),
    ("crowd", "sample.Walls,sample.SpinBot,sample.Crazy,sample.RamFire,sample.TrackFire,sample.Corners"),
    ("mixed", "voidious.Diamond*,sample.SpinBot,sample.TrackFire"),
]

RUNNER = r"""
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import robocode.control.*;
import robocode.control.events.*;
import robocode.control.snapshot.*;
public class TrainingRunner extends BattleAdaptor {
 boolean completed, failed;
 ITurnSnapshot latest;
 public void onBattleError(BattleErrorEvent e){failed=true;System.out.println("ERROR\t"+e.getError());}
 public void onTurnEnded(TurnEndedEvent e){
  latest=e.getTurnSnapshot();
  for(IRobotSnapshot r:e.getTurnSnapshot().getRobots()) {
   String text=r.getOutputStreamSnapshot();
   if(text!=null&&!text.isEmpty())for(String line:text.split("\\r?\\n"))System.out.println("["+r.getName()+"] "+line);
  }
 }
 public void onRoundEnded(RoundEndedEvent e){
  if(latest==null)throw new IllegalStateException("Missing round snapshot");
  System.out.println("ROUND_TURNS\t"+e.getRound()+"\t"+e.getTurns());
  for(IRobotSnapshot r:latest.getRobots())System.out.println("ROUND_SCORE\t"+e.getRound()+"\t"+r.getName()+"\t"+r.getScoreSnapshot().getTotalFirsts());
 }
 public void onBattleCompleted(BattleCompletedEvent e){
  completed=true;
  for(robocode.BattleResults r:e.getSortedResults())System.out.println("RESULT\t"+r.getTeamLeaderName()+"\t"+r.getScore()+"\t"+r.getFirsts());
 }
 public static void main(String[] args)throws Exception {
  RobocodeEngine engine=new RobocodeEngine(new File(args[0]));
  TrainingRunner listener=new TrainingRunner();engine.addBattleListener(listener);
  for(String line:Files.readAllLines(Paths.get(args[1]),StandardCharsets.UTF_8)){
   if(line.trim().isEmpty())continue;
   String[] a=line.split("\\|",-1);
   String[] names=a[1].split(",");RobotSpecification[] bots=new RobotSpecification[names.length];
   for(int i=0;i<names.length;i++){
    RobotSpecification[] found=engine.getLocalRepository(names[i]);
    if(found.length!=1)throw new IllegalStateException("Expected one robot: "+names[i]+", got "+found.length);
    bots[i]=found[0];
   }
   System.out.println("BEGIN\t"+a[0]+"\t"+bots.length);
   listener.completed=false;listener.failed=false;
   engine.runBattle(new BattleSpecification(Integer.parseInt(a[2]),new BattlefieldSpecification(800,600),bots),a[3],true);
   if(listener.failed||!listener.completed)throw new IllegalStateException("Battle incomplete "+a[0]);
   System.out.println("END\t"+a[0]);
  }
  engine.close();System.exit(0);
 }
}
"""


def compile_java(args, sources, destination):
    compiler = ["java", "-jar", str(args.ecj), "-8", "-nowarn"] if args.ecj else ["javac", "-source", "8", "-target", "8"]
    subprocess.run(compiler + ["-encoding", "UTF-8", "-cp", str(args.home / "libs/robocode.jar"),
                             "-d", str(destination)] + [str(s) for s in sources], check=True)


def variant(template, name, genome=None, models=None, export=True):
    text = template.replace("package equipe;", "package treino;", 1)
    text = re.sub(r"public class (?:TitanPrime|Nemesis) extends", "public class " + name + " extends", text, count=1)
    if genome is not None:
        text = re.sub(r"static final double\[\] GENOME = \{[^}]+\};",
                      "static final double[] GENOME = " + str(genome).replace("[", "{").replace("]", "}") + ";", text)
    for label, model in zip(("GUN", "SURF"), models if models is not None else ()):
        if model and len(base64.b64decode(model, validate=True)) != 4036:
            raise ValueError("Invalid neural model")
        text = re.sub(r'static final String PRETRAINED_' + label + r' = "[^"]*";',
                      'static final String PRETRAINED_' + label + ' = "' + model + '";', text)
    text = text.replace("EXPORT_LEARNING = false", "EXPORT_LEARNING = " + str(export).lower())
    return text


def install(args, name, text):
    path = args.home / "robots/treino" / (name + ".java")
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text, encoding="utf-8")
    compile_java(args, [path], args.home / "robots")


def positions(count, seed):
    rng = random.Random(seed)
    phase = rng.random() * math.tau
    points = []
    for i in range(count):
        angle = phase + i * math.tau / count
        points.extend([400 + math.cos(angle) * 290, 300 + math.sin(angle) * 200, rng.random() * 360])
    return ",".join(f"{value:.6f}" for value in points)


def run_suite(args, label, scenarios, position_overrides=None):
    overrides = position_overrides or {}
    suite = args.output / (label + ".suite")
    suite.write_text("\n".join("|".join((key, bots, str(rounds), overrides[key] if key in overrides else positions(len(bots.split(",")), seed + args.position_seed)))
                                for key, bots, rounds, seed in scenarios) + "\n")
    log = args.output / (label + ".log")
    with log.open("w", encoding="utf-8") as stream:
        import os
        command = ["java", "-Xmx768m", "-Djava.awt.headless=true", "-cp",
                   str(args.output) + os.pathsep + str(args.home / "libs/*"),
                   "TrainingRunner", str(args.home), str(suite)]
        subprocess.run(command, cwd=args.home, stdout=stream, stderr=subprocess.STDOUT, check=True, timeout=1800)
    text = log.read_text(encoding="utf-8")
    if "Exception" in text or "ERROR\t" in text:
        raise RuntimeError("Engine error; inspect " + str(log))
    results = {}
    for key, count, block in re.findall(r"BEGIN\t([^\t\n]+)\t(\d+)\n(.*?)END\t\1", text, re.S):
        scores = [{"robot": name, "score": int(score), "wins": int(wins)}
                  for name, score, wins in re.findall(r"RESULT\t([^\t\n]+)\t(\d+)\t(\d+)", block)]
        if len(scores) != int(count):
            raise RuntimeError("Incomplete roster " + key)
        rounds = [{"round": int(index), "robot": name, "firsts": int(wins)}
                  for index, name, wins in re.findall(r"ROUND_SCORE\t(\d+)\t([^\t\n]+)\t(\d+)", block)]
        turns = {int(index): int(value) for index, value in re.findall(r"ROUND_TURNS\t(\d+)\t(\d+)", block)}
        results[key] = {"scores": scores, "round_scores": rounds, "round_turns": turns, "block": block}
    if len(results) != len(scenarios):
        raise RuntimeError("Incomplete suite; inspect " + str(log))
    return results


def score_for(result, name):
    own = next(row for row in result["scores"] if row["robot"].split(" ")[0].rstrip("*") == "treino." + name)
    total = sum(row["score"] for row in result["scores"])
    lines = [line for line in result["block"].splitlines() if line.startswith("[treino." + name + "*")
             or line.startswith("[treino." + name + " ") or line.startswith("[treino." + name + "]")]
    counters = {key: sum(int(v) for v in re.findall(key + r"=(\d+)", "\n".join(lines)))
                for key in ("shots", "hits", "waves", "matched", "walls", "skipped")}
    return {**own, "score_share": own["score"] / max(1, total), **counters}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=pathlib.Path)
    parser.add_argument("--ecj", type=pathlib.Path)
    parser.add_argument("--baseline", required=True, type=pathlib.Path)
    parser.add_argument("--output", type=pathlib.Path, default=ROOT / "training-runs/latest")
    parser.add_argument("--population", type=int, default=4)
    parser.add_argument("--generations", type=int, default=3)
    parser.add_argument("--rounds", type=int, default=3)
    parser.add_argument("--validation-rounds", type=int, default=3)
    parser.add_argument("--objective", choices=("series", "score"), default="series")
    parser.add_argument("--seed", type=int, default=2027)
    parser.add_argument("--position-seed", type=int, default=0)
    parser.add_argument("--initial-genome", help="Lista JSON opcional com os dez genes iniciais")
    args = parser.parse_args()
    for key in ("home", "baseline", "output", "ecj"):
        value = getattr(args, key)
        if value is not None: setattr(args, key, value.resolve())
    if args.population < 2 or min(args.generations, args.rounds, args.validation_rounds) < 1:
        parser.error("population >= 2; generations and rounds >= 1")
    if args.objective == "series" and (args.rounds != 3 or args.validation_rounds != 3):
        parser.error("series objective requires exactly three rounds in training and validation")
    args.output.mkdir(parents=True, exist_ok=True)
    runner = args.output / "TrainingRunner.java"
    # Java source is embedded here; only the robot .java lives in the repository.
    runner.write_text(RUNNER, encoding="utf-8")
    compile_java(args, [runner], args.output)
    template = (ROOT / "robots/equipe/TitanPrime.java").read_text(encoding="utf-8")
    original = args.baseline.read_text(encoding="utf-8")
    genome = json.loads("[" + re.search(r"GENOME = \{([^}]+)\}", template)[1] + "]")
    if args.initial_genome:
        genome = json.loads(args.initial_genome)
        if len(genome) != len(BOUNDS) or any(not isinstance(v, (int, float))
                or not math.isfinite(v) or not low <= v <= high for v, (low, high) in zip(genome, BOUNDS)):
            parser.error("initial-genome must contain ten finite values within BOUNDS")
    rng = random.Random(args.seed)

    def mutate(parent):
        return [round(max(low, min(high, value + rng.gauss(0, (high - low) * 0.10))), 7)
                if rng.random() < 0.65 else value for value, (low, high) in zip(parent, BOUNDS)]

    population = [genome] + [mutate(genome) for _ in range(args.population - 1)]
    report = {"seed": args.seed, "source_sha256": hashlib.sha256(template.encode()).hexdigest(),
              "baseline_sha256": hashlib.sha256(original.encode()).hexdigest(), "generations": [],
              "train_scenarios": TRAIN, "holdout_scenarios": HOLDOUT, "parameters": vars(args).copy()}
    report["parameters"] = {k: str(v) if isinstance(v, pathlib.Path) else v for k, v in report["parameters"].items()}
    champion = None
    for generation in range(args.generations):
        names = [f"G{generation}C{i}" for i in range(args.population)]
        for name, genes in zip(names, population): install(args, name, variant(template, name, genes, models=("", "")))
        scenarios = [(name + "-" + key, "treino." + name + "*," + bots, args.rounds, 101 + index * 17)
                     for name in names for index, (key, bots) in enumerate(TRAIN)]
        scenarios.append(("league", ",".join("treino." + name + "*" for name in names), args.rounds, 313))
        results = run_suite(args, "generation-" + str(generation), scenarios)
        ranking = []
        for name, genes in zip(names, population):
            matches = [score_for(results[name + "-" + key], name) for key, _ in TRAIN]
            league = score_for(results["league"], name)
            def quality(row):
                if args.objective == "score": return row["score_share"]
                return 0.70 * (row["wins"] >= 2) + 0.20 * row["wins"] / 3 + 0.10 * row["score_share"]
            fitness = sum(quality(row) for row in matches) / len(matches)
            fitness = 0.8 * fitness + 0.2 * quality(league) - 0.001 * sum(row["skipped"] for row in matches)
            ranking.append({"name": name, "genome": genes, "fitness": fitness, "matches": matches, "league": league})
        ranking.sort(key=lambda row: row["fitness"], reverse=True)
        report["generations"].append(ranking)
        champion = ranking[0]
        print("GENERATION", generation, "winner", champion["name"], "fitness", round(champion["fitness"], 4), flush=True)
        # Elitismo + cruzamento dos dois primeiros + mutacao. Sem olhar o holdout.
        population = [champion["genome"]]
        for _ in range(args.population - 1):
            child = [rng.choice(pair) for pair in zip(ranking[0]["genome"], ranking[1]["genome"])]
            population.append(mutate(child))
        (args.output / "progress.json").write_text(json.dumps(report, indent=2) + "\n")

    models = ("", "")
    for index, (key, bots) in enumerate(TRAIN):
        name = "Warm" + str(index)
        install(args, name, variant(template, name, champion["genome"], models))
        warm_rounds = args.rounds if args.objective == "series" else args.rounds * 2
        result = run_suite(args, "pretrain-" + str(index), [(key, "treino." + name + "*," + bots, warm_rounds, 501 + index * 17)])
        block = result[key]["block"]
        models = tuple(re.findall(r"MODEL_" + label + r" ([A-Za-z0-9+/=]+)", block)[-1] for label in ("GUN", "SURF"))
        print("PRETRAIN", key, "complete", flush=True)
    final = variant(template, "Candidate", champion["genome"], models, export=False)
    install(args, "Candidate", final)
    install(args, "Baseline", variant(original, "Baseline", export=False))
    validation = [(name + "-" + key, "treino." + name + "*," + bots, args.validation_rounds, 901 + index * 17)
                  for name in ("Baseline", "Candidate") for index, (key, bots) in enumerate(HOLDOUT)]
    evaluated = run_suite(args, "holdout", validation)
    report["champion"] = champion
    report["holdout"] = {name: {key: {"own": score_for(evaluated[name + "-" + key], name),
                                    "scores": evaluated[name + "-" + key]["scores"]} for key, _ in HOLDOUT}
                         for name in ("Baseline", "Candidate")}
    # Resultado proposto e autocontido; a promocao requer revisar o holdout.
    production = final.replace("package treino;", "package equipe;", 1).replace("public class Candidate extends", "public class TitanPrime extends", 1)
    (args.output / "TitanPrime.java").write_text(production, encoding="utf-8")
    report["candidate_sha256"] = hashlib.sha256(production.encode()).hexdigest()
    (args.output / "results.json").write_text(json.dumps(report, indent=2) + "\n")
    print(json.dumps(report["holdout"], indent=2), flush=True)


if __name__ == "__main__":
    main()
