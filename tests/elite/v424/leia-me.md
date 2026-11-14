# Validação experimental v424

O código principal publicado continua na versão 4.15. As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| confidence-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 9/10 | 795 / 431 |
| confidence-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 45/50 | 3792 / 2483 |
| confidence-diamond10 | voidious.Diamond 1.8.28 | 9/10 | 667 / 382 |
| confidence-diamond50 | voidious.Diamond 1.8.28 | 50/50 | 3654 / 1538 |
| confidence-power-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 9/10 | 831 / 516 |
| confidence-power-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 46/50 | 3935 / 2458 |
| confidence-power-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 729 / 377 |
| confidence-power-diamond50 | voidious.Diamond 1.8.28 | 49/50 | 3631 / 1892 |
| confidence-power-warm50 | voidious.Diamond 1.8.28 | 49/50 | 3616 / 1809 |
| confidence-reserved100 | voidious.Diamond 1.8.28 | 48/50 | 3543 / 1939 |
| confidence-reserved100 | voidious.Diamond 1.8.28 | 48/50 | 3510 / 1863 |
| confidence-warm50 | voidious.Diamond 1.8.28 | 49/50 | 3585 / 1862 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.
