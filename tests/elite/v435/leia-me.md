# Validação experimental v435

As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente. O checkpoint publicado está identificado nos arquivos de versão do repositório. Casos inválidos não contam como vitórias.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| owner-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 45/50 | 3862 / 2407 |
| owner-diamond20 | voidious.Diamond 1.8.28 | 19/20 | 1404 / 943 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.

A correção de referência entre rounds foi reproduzida no código anterior e verificada na candidata. Ela não atingiu a meta competitiva: Chimp45/50; Diamond19/20. O checkpoint4.32-calibrated continua publicado.
