# Validação experimental v421

O código principal publicado continua na versão 4.15. As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| prior16-diamond50 | voidious.Diamond 1.8.28 | 48/50 | 3518 / 1953 |
| prior16-melee10 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 6/10 | 2166 / 2338 |
| prior16-route-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 43/50 | 3498 / 2734 |
| prior16-route-diamond50 | voidious.Diamond 1.8.28 | 48/50 | 3529 / 1883 |
| prior16-route-melee10 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 7/10 | 2481 / 1710 |
| prior16-route-warm50 | voidious.Diamond 1.8.28 | 49/50 | 3594 / 1795 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.
