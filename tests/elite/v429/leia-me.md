# Validação experimental v429

O código principal publicado continua na versão 4.15. As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| combined-melee20 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 11/20 | 3838 / 4094 |
| control-melee20 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/20 | 3535 / 3970 |
| geometry-melee20 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/20 | 4112 / 4342 |
| route-melee20 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 12/20 | 4320 / 4306 |
| route-melee50 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 19/50 | 8436 / 10826 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.

Este estudo altera apenas o controlador Melee. A comparação independente teve controle10/20, geometria10/20, rota12/20 e combinação11/20. A rota selecionada caiu para19/50 e8436pontos contra10826do Diamond na confirmação. Ela não foi incorporada à entrega4.28. As derrotas de duelo da base, cujo código ficou inalterado, não foram substituídas por novos sorteios.
