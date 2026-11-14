# Validação experimental v430

O código principal publicado continua na versão 4.15. As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| combined-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 826 / 293 |
| combined-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 715 / 364 |
| fast-combined-chimp20 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 18/20 | 1682 / 886 |
| fast-combined-diamond20 | voidious.Diamond 1.8.28 | 19/20 | 1393 / 873 |
| pressure18-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 916 / 315 |
| pressure18-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 727 / 427 |
| wall-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 877 / 293 |
| wall-diamond10 | voidious.Diamond 1.8.28 | 9/10 | 661 / 442 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.

O usuário pediu mais rapidez durante as triagens. `speed-amendment.json` preserva a alteração do protocolo antes das confirmações: o caso ativo foi concluído, o supervisor anterior foi encerrado entre batalhas e nenhum resultado válido foi repetido. Os testes longos pendentes foram substituídos por20rounds contra cada adversário, com a mesma fonte e as mesmas regras de CPU. A combinação fez19/20 contra Diamond e18/20 contra ChimpazeComSarna. Não foi promovida; a entrega ficou na base4.28com séries maiores. Os testes de recarga, já verificados anteriormente, não foram repetidos nesta etapa rápida.
