#!/usr/bin/env python3
"""Gera o grafico do experimento registrado. Requer matplotlib, opcional ao jogo."""
import json
from pathlib import Path
import matplotlib
matplotlib.use("Agg")
import matplotlib.pyplot as plt
plt.rcParams["svg.fonttype"] = "none"


def main():
    root = Path(__file__).resolve().parents[1]
    evolution = json.loads((root / "tests/evolution-results.json").read_text())
    second = json.loads((root / "tests/validation-second.json").read_text())
    generations = evolution["generations"]
    fixed = [[sum(m["score_share"] for m in individual["matches"]) / len(individual["matches"])
              for individual in generation] for generation in generations]
    fig, axes = plt.subplots(1, 2, figsize=(11, 4.8), layout="constrained")
    fig.patch.set_facecolor("#f7f8fc")
    for axis in axes:
        axis.set_facecolor("#ffffff")
        axis.spines[["top", "right"]].set_visible(False)
    xs = list(range(1, len(fixed) + 1))
    axes[0].plot(xs, [max(row) * 100 for row in fixed], "o-", color="#6a36bd", label="Melhor contra adversarios fixos")
    axes[0].plot(xs, [sum(row) / len(row) * 100 for row in fixed], "o--", color="#23779d", label="Media da populacao")
    axes[0].set(xticks=xs, xlabel="Geracao (4 candidaturas por geracao)", ylabel="Participacao na pontuacao (%)",
                title="Treino: escala ampliada", ylim=(40, 70))
    axes[0].grid(axis="y", alpha=0.2)
    axes[0].legend(frameon=False, fontsize=8, loc="lower right")
    labels = ["VelociRobot", "Diamond 1v1", "Arena com 7", "Diamond + 2"]
    keys = ["veloci", "diamond", "crowd", "mixed"]
    changes = []
    for key in keys:
        changes.append(100 * sum(
            batch["Candidate"][key]["own"]["score_share"] - batch["Baseline"][key]["own"]["score_share"]
            for batch in (evolution["holdout"], second["results"])) / 2)
    axes[1].barh(labels, changes, color=["#177d8d" if value >= 0 else "#b44456" for value in changes])
    axes[1].axvline(0, color="#333333", linewidth=0.8)
    axes[1].invert_yaxis()
    for index, value in enumerate(changes):
        axes[1].text(value + 0.025, index, f"{value:+.2f}", va="center", fontsize=10)
    axes[1].set(xlabel="Diferenca da pontuacao relativa (pontos percentuais)",
                title="Comparacao reservada: nova menos anterior", xlim=(min(-0.1, min(changes) - 0.2), max(changes) + 0.35))
    fig.suptitle("Nemesis 3.0 | redes neurais + treino por geracoes", fontsize=16, fontweight="bold")
    fig.supxlabel("Dois lotes com posicoes iniciais diferentes, 12 rounds por cenario e versao.\nGanho pequeno nesta amostra; 0/24 vitorias no duelo contra Diamond.", fontsize=9)
    out = root / "assets"
    out.mkdir(exist_ok=True)
    fig.savefig(out / "treinamento.svg", metadata={"Date": None})


if __name__ == "__main__":
    main()
