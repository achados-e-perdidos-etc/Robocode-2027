#!/usr/bin/env python3
"""Cenario de contato proximo e prioridade de vitorias no treino RamFire/SpinBot."""
import math
from pathlib import Path
import sys
sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "tools"))
import ram_spin


def main():
    for seed in (31, 67, 83, 131, 197, 263):
        values = list(map(float, ram_spin.close_positions(seed).split(",")))
        assert len(values) == 6
        assert abs(math.dist(values[:2], values[3:5]) - 100) < 1e-8
        assert abs((values[5] - values[2]) % 360 - 180) < 1e-8
        assert all(18 <= values[i] <= (782 if i % 3 == 0 else 582) for i in (0, 1, 3, 4))
    win = {"series_win": True, "first_round_win": True, "wins": 3,
           "round_turns": {0: 200, 1: 200, 2: 200}, "score_share": 0.6, "skipped": 0}
    slow = {**win, "round_turns": {0: 300, 1: 300, 2: 300}}
    loss = {**win, "series_win": False, "wins": 1, "round_turns": {0: 10, 1: 10, 2: 10}, "score_share": 0.99}
    assert ram_spin.rank([win]) > ram_spin.rank([slow])
    assert ram_spin.rank([slow]) > ram_spin.rank([loss])
    print("RAM/SPIN CHECKS PASSED: 100-pixel starts, legal placement and wins before speed/points")


if __name__ == "__main__":
    main()
