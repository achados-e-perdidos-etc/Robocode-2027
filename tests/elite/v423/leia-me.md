# Validação experimental v423

O código principal publicado continua na versão 4.15. As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| pressure-medium-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 8/10 | 683 / 602 |
| pressure-medium-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 46/50 | 4048 / 1949 |
| pressure-medium-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 733 / 290 |
| pressure-medium-diamond50 | voidious.Diamond 1.8.28 | 50/50 | 3677 / 1578 |
| pressure-medium-reserved100 | voidious.Diamond 1.8.28 | 47/50 | 3437 / 2021 |
| pressure-medium-reserved100 | voidious.Diamond 1.8.28 | 47/50 | 3431 / 2192 |
| pressure-medium-warm50 | voidious.Diamond 1.8.28 | 49/50 | 3585 / 1829 |
| pressure-near-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 8/10 | 655 / 624 |
| pressure-near-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 719 / 350 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.
