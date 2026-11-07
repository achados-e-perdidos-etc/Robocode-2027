#!/usr/bin/env python3
"""Regra da competicao: pontos nao substituem vencer dois rounds."""
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import bo3


def match(wins, cumulative, own_score, other_score):
    return {"scores": [{"robot": "treino.Test*", "wins": wins, "score": own_score},
                       {"robot": "sample.Walls", "wins": 3 - wins, "score": other_score}],
            "round_scores": [{"round": i, "robot": "treino.Test*", "firsts": value}
                             for i, value in enumerate(cumulative)],
            "block": "[treino.Test*] COLD_START gun=1228 surf=128\n"}


def main():
    source = (Path(__file__).resolve().parents[1] / "robots/equipe/titanprime.java").read_text()
    prototype = bo3.prepare_template(source)
    assert "FAST_OBSERVATION = true" in prototype and "WARM_DEFENSE = true" in prototype
    assert "Neural GENERAL_GUN" in prototype
    for scenario in (Path(__file__).resolve().parents[1] / "battles").glob("*.battle"):
        rounds = [line.split("=", 1)[1] for line in scenario.read_text().splitlines()
                  if line.startswith("robocode.battle.numRounds=")]
        assert rounds == ["3"], "competition scenario must contain exactly three rounds"
    high_points_loss = bo3.summarize_series(match(1, [1, 1, 1], 900, 100), "Test")
    low_points_win = bo3.summarize_series(match(2, [0, 1, 2], 100, 900), "Test")
    assert not high_points_loss["series_win"] and high_points_loss["first_round_win"]
    assert low_points_win["series_win"] and not low_points_win["first_round_win"]
    assert bo3.ranking_key([low_points_win]) > bo3.ranking_key([high_points_loss])
    broken = match(2, [1, 1, 1], 500, 400)
    try:
        bo3.summarize_series(broken, "Test")
    except ValueError:
        pass
    else:
        raise AssertionError("must reject inconsistent round accounting")
    broken = match(1, [1, 1, 1], 500, 400)
    broken["block"] = ""
    try:
        bo3.summarize_series(broken, "Test")
    except ValueError:
        pass
    else:
        raise AssertionError("must verify one cold start per match")
    print("BO3 CHECKS PASSED: two wins outrank points, per-round accounting and independent matches")


if __name__ == "__main__":
    main()
