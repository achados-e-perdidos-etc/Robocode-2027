# Validação experimental v422

O código principal publicado continua na versão 4.15. As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| integral-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 781 / 354 |
| integral-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 730 / 451 |
| integral-trajectory-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 810 / 496 |
| integral-trajectory-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 46/50 | 3664 / 2266 |
| integral-trajectory-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 757 / 283 |
| integral-trajectory-diamond50 | voidious.Diamond 1.8.28 | 46/50 | 3414 / 2223 |
| integral-trajectory-warm50 | voidious.Diamond 1.8.28 | 49/50 | 3622 / 2122 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.
