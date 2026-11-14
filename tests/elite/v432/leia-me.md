# Validação experimental v432

A variante calibrada foi salva como checkpoint 4.32 na main por solicitação do usuário. A fonte é exatamente a avaliada; a meta completa contra os dois adversários continua não atendida.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| calibrated-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 817 / 371 |
| calibrated-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 47/50 | 4181 / 1913 |
| calibrated-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 745 / 417 |
| calibrated-diamond50 | voidious.Diamond 1.8.28 | 50/50 | 3695 / 1807 |
| control-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 819 / 348 |
| reserve-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 797 / 365 |
| reserve-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 737 / 294 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.

A variante calibrada fez50/50 contra Diamond e47/50 contra ChimpazeComSarna, em séries frias independentes com o mesmo SHA256. A outra variante teve uma triagem contra Diamond invalidada pelo motor (`No score will be generated`); seu placar não é uma vitória válida. Nenhum resultado desfavorável foi removido. O JAR4.32contém as próprias classes usadas no teste validado de50rounds contra Diamond. Os testes seguintes têm outras fontes e não substituem este checkpoint.
