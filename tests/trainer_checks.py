#!/usr/bin/env python3
"""Regressoes do treinador: modelos incorporados, selecao de resultados e arenas."""
import base64
import math
import pathlib
import struct
import sys

ROOT = pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import train


def main():
    source = (ROOT / "robots/equipe/TitanPrime.java").read_text(encoding="utf-8")
    model = base64.b64encode(struct.pack(">ii", 0x4E4D5333, 123) + bytes(4028)).decode()
    production = source
    import re
    for label in ("GUN", "SURF"):
        production = re.sub(r'PRETRAINED_' + label + r' = "[^"]*"',
                            'PRETRAINED_' + label + ' = "' + model + '"', production)
    clone = train.variant(production, "Candidate", export=False)
    assert "public class Candidate extends" in clone
    assert "package treino;" in clone
    assert clone.count(model) == 2, "evaluation must not erase pretrained weights"
    for count in (2, 4, 7):
        raw = train.positions(count, 901)
        assert raw == train.positions(count, 901)
        assert raw != train.positions(count, 902)
        values = [float(value) for value in raw.split(",")]
        assert len(values) == count * 3
        points = [values[i:i + 3] for i in range(0, len(values), 3)]
        for x, y, heading in points:
            assert 36 <= x <= 764 and 36 <= y <= 564 and 0 <= heading <= 360
        assert min(math.dist(a[:2], b[:2]) for i, a in enumerate(points) for b in points[i + 1:]) > 72
    result = {"scores": [{"robot": "treino.Candidate*", "score": 200, "wins": 2},
                         {"robot": "sample.Walls", "score": 100, "wins": 1}],
              "block": "[treino.Candidate*] TitanPrime round=0 shots=12 hits=4 skipped=2\n"
                       "[treino.Candidate2*] TitanPrime round=0 shots=99 hits=99 skipped=99\n"}
    own = train.score_for(result, "Candidate")
    assert own["skipped"] == 2 and own["shots"] == 12 and own["hits"] == 4
    assert abs(own["score_share"] - 2 / 3) < 1e-12
    print("TRAINER CHECKS PASSED: embedded models preserved, safe spawn layouts, exact robot accounting")


if __name__ == "__main__":
    main()
