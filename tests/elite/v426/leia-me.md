# Validação experimental v426

O código principal publicado continua na versão 4.15. As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| roster-melee10 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 6/10 | 2060 / 2059 |
| roster-titanv3-10 | titan.TitanV3 3.0 | 10/10 | 798 / 266 |
| roster-vingation-10 | titan.TitanVingation 1.7 | 10/10 | 781 / 337 |
| train-chimp-a | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 9/10 | 835 / 433 |
| train-chimp-b | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 9/10 | 817 / 451 |
| train-diamond-a | voidious.Diamond 1.8.28 | 10/10 | 736 / 413 |
| train-diamond-b | voidious.Diamond 1.8.28 | 10/10 | 747 / 353 |
| tuned-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 44/50 | 3916 / 2337 |
| tuned-diamond50 | voidious.Diamond 1.8.28 | 47/50 | 3464 / 2024 |
| tuned-warm50 | voidious.Diamond 1.8.28 | 49/50 | 3590 / 1792 |
| validation-chimp | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 894 / 409 |
| validation-diamond | voidious.Diamond 1.8.28 | 10/10 | 734 / 325 |

Os seis casos `train-*` e `validation-*` são coleta de dados em batalhas inteiras independentes, usando uma fonte instrumentada distinta. Seus resultados não qualificam a fonte neural posterior. Os quatro casos `train-*` alimentam o gradiente; os dois `validation-*` são usados para escolher modelo/época e, portanto, não são um teste final imparcial. Os casos `roster-*` são verificações pequenas de compatibilidade competitiva. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.

Foram coletados 11.619 exemplos próprios: 7.799 para treino e 3.820 para validação. Quatro modelos foram comparados com o prior realmente incorporado, com dois trabalhadores offline e sem motor de batalha concorrente. O modelo escolhido reduziu a perda média balanceada de 4,05665 para 4,03594; a perda contra Diamond isoladamente aumentou ligeiramente, de 4,05222 para 4,05293, dentro da tolerância declarada. A elegibilidade estatística não comprovou ganho competitivo: a fonte nova terminou 47/50 contra Diamond e 44/50 contra ChimpazeComSarna, ambos com mais pontos. Ela não substituiu o robô principal.

O teste com memória recarregada terminou 49/50 contra Diamond. TitanV3 e TitanVingation foram vencidos em 10/10 cada. No confronto simultâneo com Diamond, ChimpazeComSarna e TitanV3, foram 6/10 vitórias e 2060 pontos contra 2059 do maior adversário. Essa margem de um ponto e os testes curtos não comprovam domínio.

A telemetria instrumentada registrou 919 impactos, todos associados a ondas; 14 ocorreram em ângulos incluídos em sombras e todos tinham cruzamento previsto no mesmo tick do impacto. Não apareceram impactos cobertos apenas por um cruzamento futuro nem intervalos sem origem válida. Isso motiva investigar a ordem de colisões, sem atribuir a ela todas as derrotas. A verificação angular separada passou em 20 casos do Java real; essa correção não foi aplicada à fonte neural deste relatório.
