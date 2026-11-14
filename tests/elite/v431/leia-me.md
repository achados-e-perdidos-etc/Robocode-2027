# Validação experimental v431

A fonte candidata4.31 foi publicada após regressões reproduzindo os defeitos da4.28 e validação nas batalhas. A promoção foi revisada, não automática. As duas fontes permanecem separadas por SHA256.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| candidate10 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 4/10 | 1770 / 2418 |
| confirmation20 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 13/20 | 4442 / 4026 |
| control10 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 5/10 | 1902 / 2287 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.

Nesta atualização, o cenário é sempre solo contra três adversários fortes simultaneamente. A4.28fez5/10; a4.31fez4/10 na triagem e13/20 na confirmação independente, totalizando17/30. Os pontos totais da candidata foram6212contra6444do Diamond, embora a confirmação isolada tenha liderado4442a4026. Não omitimos o primeiro resultado inferior e não afirmamos significância estatística. Os campos `accepted=false` referem-se à meta estrita de vencer todos os rounds, não a falhas de execução: todos os casos foram válidos.

O código corrige a ordem de argumentos na previsão das origens alternativas, atualiza a distância de todos os adversários em batalhas iniciadas com vários robôs e protege a mira na transição com poucos dados. As regressões chamam os métodos reais, exigem que a versão anterior reproduza os defeitos e verificam que a candidata passe. O teste também conserva o fallback perceptual dos duelos iniciados em1v1.

As verificações finais, feitas com Temurin17.0.21+9, cobrem compilação paraJava8,157trajetórias físicas,100comparações KNN, codec de memória e carga do JAR final num Melee de um round. Esse teste de instalação não foi somado aos30rounds competitivos.
