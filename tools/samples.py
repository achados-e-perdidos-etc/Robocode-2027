#!/usr/bin/env python3
"""Audita exemplos, equipes e sentinelas no motor real, em series de 3 rounds.

Use uma copia separada da instalacao: compila clones de teste em robots/treino.
Nenhum resultado com falha de inicializacao conta como vitoria.
"""
import argparse
from collections import Counter
import hashlib
import json
import math
import os
from pathlib import Path
import re
import subprocess
import train
import bo3

SEEDS = (401, 557, 719)
FIGHTERS = ["sample." + name for name in (
    "Corners", "Crazy", "Fire", "MyFirstJuniorRobot", "MyFirstRobot",
    "PaintingRobot", "RamFire", "SpinBot", "Tracker", "TrackFire", "VelociRobot", "Walls")]
FIGHTERS += ["sampleex." + name for name in (
    "Alien", "AlienComposition", "MasterAndSlave", "ProxyOfGreyEminence")]
DEMOS = ["sample.SittingDuck", "sample.Target"]
INTERACTIVE = ["sample.Interactive", "sample.Interactive_v2"]
DEPENDENT = ["sampleteam.MyFirstDroid", "sampleteam.MyFirstLeader"]
GROUPS = {
    "two": "sample.RamFire,sample.SpinBot",
    "three": "sample.Walls,sample.Crazy,sample.Tracker",
    "six": "sample.Walls,sample.SpinBot,sample.Crazy,sample.RamFire,sample.TrackFire,sample.Corners",
    "all_autonomous": ",".join(FIGHTERS),
}
JAVA_OPTIONS = train.JAVA_OPTIONS

RUNNER = r"""
import java.io.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import robocode.control.*;
import robocode.control.events.*;
import robocode.control.snapshot.*;
public class samplerunner extends BattleAdaptor {
 boolean completed, failed;
 boolean firstTurn;
 int currentRound;
 RobotSetup[] setups;
 ITurnSnapshot latest;
 public void onRoundStarted(RoundStartedEvent e){currentRound=e.getRound();firstTurn=true;}
 public void onBattleError(BattleErrorEvent e){failed=true;System.out.println("ERROR\t"+e.getError());}
 public void onTurnEnded(TurnEndedEvent e){
  if(firstTurn){
   firstTurn=false;
   for(IRobotSnapshot r:e.getTurnSnapshot().getRobots()){
    RobotSetup s=setups[r.getRobotIndex()];
    System.out.println("START_POSITION\t"+currentRound+"\t"+r.getName()+"\t"+r.getX()+"\t"+r.getY()+"\t"+r.getBodyHeading());
    if(Math.hypot(r.getX()-s.getX(),r.getY()-s.getY())>1.01){
     System.out.println("POSITION_ADJUSTED\t"+currentRound+"\t"+r.getName());
    }
   }
  }
  if(latest==null)for(IRobotSnapshot r:e.getTurnSnapshot().getRobots())
   System.out.println("ROSTER\t"+r.getName()+"\t"+r.getTeamName()+"\t"+r.getContestantIndex()+"\t"+r.getTeamIndex()+"\t"+r.isSentryRobot()+"\t"+r.getEnergy()+"\t"+r.getState()+"\t"+r.getX()+"\t"+r.getY());
  latest=e.getTurnSnapshot();
  for(IRobotSnapshot r:latest.getRobots()) {
   String text=r.getOutputStreamSnapshot();
   if(text!=null&&!text.isEmpty())for(String line:text.split("\\r?\\n"))System.out.println("["+r.getName()+"] "+line);
  }
 }
 public void onRoundEnded(RoundEndedEvent e){
  if(latest==null){failed=true;return;}
  System.out.println("ROUND_TURNS\t"+e.getRound()+"\t"+e.getTurns());
  for(IRobotSnapshot r:latest.getRobots())System.out.println("ROUND_SCORE\t"+e.getRound()+"\t"+r.getName()+"\t"+r.getScoreSnapshot().getTotalFirsts());
 }
 public void onBattleCompleted(BattleCompletedEvent e){
  completed=true;
  for(robocode.BattleResults r:e.getSortedResults())System.out.println("RESULT\t"+r.getTeamLeaderName()+"\t"+r.getScore()+"\t"+r.getFirsts());
 }
 public static void main(String[] args)throws Exception {
  RobocodeEngine engine=new RobocodeEngine(new File(args[0]));
  System.out.println("VERSION\t"+engine.getVersion());
  System.out.println("POSITION_MODE\trobot_setup_verified");
  for(RobotSpecification r:engine.getLocalRepository())if(r.getClassName().startsWith("sample"))
   System.out.println("CAT\t"+r.getNameAndVersion());
  samplerunner listener=new samplerunner();engine.addBattleListener(listener);
  for(String line:Files.readAllLines(Paths.get(args[1]),StandardCharsets.UTF_8)){
   if(line.trim().isEmpty())continue;
   String[] a=line.split("\\|",-1);String[] names=a[1].split(",");
   List<RobotSpecification> expanded=new ArrayList<RobotSpecification>();
   System.out.println("BEGIN\t"+a[0]);
   listener.completed=false;listener.failed=false;listener.latest=null;
   for(String name:names){
    RobotSpecification[] found=engine.getLocalRepository(name);
    boolean team=name.equals("sampleteam.MyFirstTeam");
    if(found.length!=(team?5:1))throw new IllegalStateException("Selection ambiguous or missing: "+name+", got "+found.length);
    expanded.addAll(Arrays.asList(found));
   }
   RobotSpecification[] bots=expanded.toArray(new RobotSpecification[0]);
   String[] coordinates=a[3].split(",");
   if(coordinates.length!=bots.length*3)throw new IllegalStateException("Bad positions for "+a[0]);
   listener.setups=new RobotSetup[bots.length];
   for(int i=0;i<bots.length;i++)listener.setups[i]=new RobotSetup(Double.valueOf(coordinates[i*3]),Double.valueOf(coordinates[i*3+1]),Double.valueOf(coordinates[i*3+2]));
   BattleSpecification spec=new BattleSpecification(new BattlefieldSpecification(800,600),3,450,0.1,100,false,bots,listener.setups);
   engine.runBattle(spec,true);
   System.out.println("STATUS\t"+(listener.completed&&!listener.failed?"complete":"error"));
   System.out.println("END\t"+a[0]);
  }
  engine.close();System.exit(0);
 }
}
"""


def cases(seeds=SEEDS):
    rows = []
    for category, names in (("duel", FIGHTERS), ("demo", DEMOS),
                            ("interactive", INTERACTIVE), ("dependent", DEPENDENT)):
        for name in names:
            for seed in seeds:
                rows.append({"key": name.lower() + "-" + str(seed), "category": category,
                             "opponents": name, "physical_count": 2, "seed": seed})
    for name, robots in GROUPS.items():
        for seed in seeds:
            rows.append({"key": name + "-" + str(seed), "category": "melee", "opponents": robots,
                         "physical_count": 1 + len(robots.split(",")), "seed": seed})
    for category, robots, count in (("team", "sampleteam.MyFirstTeam", 6),
                                    ("sentry", "sample.Walls,samplesentry.BorderGuard", 3)):
        for seed in seeds:
            rows.append({"key": category + "-" + str(seed), "category": category,
                         "opponents": robots, "physical_count": count, "seed": seed})
    rows.append({"key": "slave", "category": "helper", "opponents": "sampleex.Slave",
                 "physical_count": 2, "seed": seeds[0]})
    return rows


def positions(case):
    values = list(map(float, train.positions(case["physical_count"], case["seed"]).split(",")))
    if case["category"] == "sentry":
        x, y = values[-3:-1]
        if min(x, 800 - x) < min(y, 600 - y):
            values[-3] = 50 if x < 400 else 750
        else:
            values[-2] = 50 if y < 300 else 550
    for i in range(case["physical_count"]):
        for j in range(i):
            if math.dist(values[i*3:i*3+2], values[j*3:j*3+2]) < 40:
                raise ValueError("Overlapping initial positions")
    return ",".join(f"{v:.6f}" for v in values)


def parse_block(block, case, name):
    scores = [{"robot": n, "score": int(s), "wins": int(w)} for n, s, w in
              re.findall(r"RESULT\t([^\t\n]+)\t(\d+)\t(\d+)", block)]
    rounds = [{"round": int(i), "robot": n, "firsts": int(w)} for i, n, w in
              re.findall(r"ROUND_SCORE\t(\d+)\t([^\t\n]+)\t(\d+)", block)]
    turns = {int(i): int(t) for i, t in re.findall(r"ROUND_TURNS\t(\d+)\t(\d+)", block)}
    roster = []
    for n, team, contestant, team_index, sentry, energy, state, x, y in re.findall(
            r"ROSTER\t([^\t\n]+)\t([^\t\n]+)\t(-?\d+)\t(-?\d+)\t(true|false)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)", block):
        roster.append({"robot": n, "team": team, "contestant": int(contestant),
                       "team_index": int(team_index), "sentry": sentry == "true",
                       "energy": float(energy), "state": state, "x": float(x), "y": float(y)})
    errors = sorted(set(line.strip() for line in block.splitlines() if any(
        text in line for text in ("Exception", "ERROR\t", "An error occurred", "SYSTEM: Disabled"))))
    starts = [{"round": int(i), "robot": n, "x": float(x), "y": float(y), "heading": float(h)}
              for i, n, x, y, h in re.findall(
                  r"START_POSITION\t(\d+)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)\t([^\t\n]+)", block)]
    setup = list(map(float, positions(case).split(",")))
    expected = {r["robot"]: setup[i*3:i*3+3] for i, r in enumerate(roster)}
    position_keys = {(s["round"], s["robot"]) for s in starts}
    positions_recorded = len(starts) == case["physical_count"] * 3 and position_keys == {
        (i, r["robot"]) for i in range(3) for r in roster}
    position_ok = positions_recorded
    adjustments = []
    for start in starts:
        if start["robot"] not in expected:
            position_ok = False
            continue
        x, y, heading = expected[start["robot"]]
        error = (start["heading"] - math.radians(heading) + math.pi) % math.tau - math.pi
        if math.hypot(start["x"] - x, start["y"] - y) > 1.01 or abs(error) > math.radians(10.01):
            position_ok = False
            adjustments.append({"round": start["round"], "robot": start["robot"]})
    result = {**case, "scores": scores, "round_scores": rounds, "round_turns": turns,
              "roster": roster, "errors": errors, "start_positions": starts,
              "positions_verified": position_ok, "positions_recorded": positions_recorded}
    expected_scores = 2 if case["category"] == "team" else case["physical_count"]
    complete = "STATUS\tcomplete" in block and not errors and positions_recorded
    complete = complete and len(roster) == case["physical_count"] and len(scores) == expected_scores
    complete = complete and all(r["state"] == "ACTIVE" and r["energy"] > 0 for r in roster)
    if not complete:
        result.update({"status": "invalid", "series_win": None, "eligible": False})
        return result
    if sorted(turns) != [0, 1, 2]:
        raise ValueError("Missing round duration: " + case["key"])
    summary = bo3.summarize_series({"scores": scores, "round_scores": rounds,
                                    "round_turns": turns, "block": block,
                                    "position_adjustments": adjustments}, name)
    telemetry_rounds = sorted(r["round"] for r in summary["round_stats"])
    telemetry_complete = telemetry_rounds == [0, 1, 2]
    shots = Counter(re.findall(r"AUDIT_SHOT ([^\s]+)", block))
    result.update(summary)
    result.update({"status": "complete", "eligible": case["category"] in ("duel", "melee", "team"),
                   "telemetry_complete": telemetry_complete, "telemetry_rounds": telemetry_rounds,
                   "reported_shots": summary["shots"], "shots": sum(shots.values()),
                   "unreported_shots": sum(shots.values()) - summary["shots"],
                   "targets": dict(shots), "sentry_shots": sum(n for k, n in shots.items() if k.startswith("samplesentry."))})
    # O motor permite movimento/tiros durante a animacao apos anunciar o vencedor.
    # A auditoria inclui esses tiros; a linha de fim de round pode antecede-los.
    if sum(shots.values()) < summary["shots"]:
        raise ValueError("Shot audit disagrees with telemetry: " + case["key"])
    if case["category"] == "team":
        members = [r for r in roster if r["team_index"] >= 0]
        if len(members) != 5 or len({r["team_index"] for r in members}) != 1:
            raise ValueError("Five opponents were not a coordinated team")
    # O primeiro snapshot do 1.11.1 pode anteceder a inicializacao da flag.
    # O BorderGuard original recebe 500 de energia ao entrar como sentinela.
    if case["category"] == "sentry" and not any(
            bo3.robot_key(r["robot"]) == "samplesentry.BorderGuard" and r["energy"] == 500
            for r in roster):
        raise ValueError("Missing active sentry")
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--home", required=True, type=Path)
    parser.add_argument("--ecj", type=Path)
    parser.add_argument("--source", type=Path, default=train.ROOT / "robots/equipe/titanprime.java")
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--label", default="samples")
    parser.add_argument("--seeds", default=",".join(map(str, SEEDS)))
    parser.add_argument("--include", help="Comma-separated categories; omitted means full suite")
    parser.add_argument("--parse-only", action="store_true", help="Reprocessa um log existente, sem executar batalhas")
    args = parser.parse_args()
    for key in ("home", "ecj", "source", "output"):
        if getattr(args, key) is not None: setattr(args, key, getattr(args, key).resolve())
    seeds = tuple(map(int, args.seeds.split(",")))
    if not seeds or len(set(seeds)) != len(seeds): parser.error("Use distinct seeds")
    scenarios = cases(seeds)
    if args.include:
        allowed = set(args.include.split(","))
        if allowed - {r["category"] for r in scenarios}: parser.error("Unknown category")
        scenarios = [r for r in scenarios if r["category"] in allowed]
    args.output.mkdir(parents=True, exist_ok=True)
    source = args.source.read_text(encoding="utf-8")
    # Somente instrumentacao: nao altera a logica do robo em avaliacao.
    name = "sampleaudit"
    instrumented = source.replace("shots++;", 'shots++; out.println("AUDIT_SHOT " + e.name);', 1)
    if instrumented == source: raise ValueError("Shot instrumentation anchor missing")
    suite = args.output / (args.label + ".suite")
    expected_suite = "\n".join(r["key"] + "|treino." + name + "*," + r["opponents"] + "|3|" +
                               positions(r) for r in scenarios) + "\n"
    log = args.output / (args.label + ".log")
    command = ["java", "-Xmx768m", "-Djava.awt.headless=true", *JAVA_OPTIONS, "-cp",
               str(args.output) + os.pathsep + str(args.home / "libs/*"),
               "samplerunner", str(args.home), str(suite)]
    if args.parse_only:
        if suite.read_text() != expected_suite: raise ValueError("Scenario file differs from requested suite")
    else:
        runner = args.output / "samplerunner.java"; runner.write_text(RUNNER, encoding="utf-8")
        train.compile_java(args, [runner], args.output)
        bo3.install(args, name, instrumented)
        suite.write_text(expected_suite)
        with log.open("w", encoding="utf-8") as stream:
            subprocess.run(command, cwd=args.home, stdout=stream, stderr=subprocess.STDOUT, check=True, timeout=3600)
    text = log.read_text(encoding="utf-8")
    blocks = dict(re.findall(r"BEGIN\t([^\n]+)\n(.*?)END\t\1(?:\n|$)", text, re.S))
    if set(blocks) != {r["key"] for r in scenarios}: raise ValueError("Incomplete suite; inspect " + str(log))
    versions = re.findall(r"^VERSION\t([^\n]+)", text, re.M)
    if len(versions) != 1: raise ValueError("Missing engine version")
    if "POSITION_MODE\trobot_setup_verified" not in text: raise ValueError("Unverified legacy position format")
    rows = [parse_block(blocks[r["key"]], r, name) for r in scenarios]
    cold = {tuple(r["cold_start"]) for r in rows if r["status"] == "complete"}
    if len(cold) != 1: raise ValueError("Knowledge leaked across independent series")
    groups = {}
    for category in dict.fromkeys(r["category"] for r in rows):
        group = [r for r in rows if r["category"] == category]
        valid = [r for r in group if r["status"] == "complete"]
        groups[category] = {"attempts": len(group), "complete": len(valid),
                            "series_wins": sum(r["series_win"] for r in valid),
                            "round_wins": sum(r["wins"] for r in valid), "rounds": 3 * len(valid),
                            "complete_telemetry": sum(r["telemetry_complete"] for r in valid),
                            "verified_positions": sum(r["positions_verified"] for r in valid),
                            "skipped": sum(r["skipped"] for r in valid),
                            "sentry_shots": sum(r["sentry_shots"] for r in valid)}
    evidence = []
    paths = sorted((args.home / "libs").glob("*.jar"))
    for folder in ("sample", "sampleex", "sampleteam", "samplesentry"):
        paths.extend(sorted((args.home / "robots" / folder).rglob("*.class")))
    paths.append(args.home / "robots/sampleteam/MyFirstTeam.team")
    for path in paths:
        evidence.append({"path": path.relative_to(args.home).as_posix(),
                         "sha256": hashlib.sha256(path.read_bytes()).hexdigest()})
    report = {"engine": versions[0], "position_mode": "RobotSetup[]; first-turn positions checked in all rounds",
              "java_options": JAVA_OPTIONS, "java_version": subprocess.check_output(
                  ["java", "-version"], stderr=subprocess.STDOUT, text=True).strip(),
              "rules": {"width": 800, "height": 600, "rounds": 3, "inactivity": 450,
                        "cooling": 0.1, "sentry_border": 100, "win": "at least two round firsts"},
              "source_sha256": hashlib.sha256(source.encode()).hexdigest(), "seeds": seeds,
              "catalog": sorted(set(re.findall(r"^CAT\t([^\n]+)", text, re.M))),
              "installation_files": evidence, "summary": groups, "series": rows}
    dest = args.output / (args.label + ".json")
    dest.write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(groups, indent=2), flush=True)
    print("RESULTS", dest, flush=True)
    unexpected = [r["key"] for r in rows if r["status"] != "complete" and r["category"] != "helper"]
    if unexpected: raise RuntimeError("Invalid matches: " + ", ".join(unexpected))


if __name__ == "__main__":
    main()
