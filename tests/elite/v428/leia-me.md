# Validação experimental v428

O código principal publicado continua na versão 4.15. As fontes experimentais são identificadas por SHA256 nos relatórios e não foram promovidas automaticamente.

| Caso | Adversário(s) | Vitórias / rounds | Pontos Titan / maior adversário |
|---|---|---:|---:|
| angular-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 800 / 356 |
| angular-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 724 / 374 |
| combined-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 854 / 286 |
| combined-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 48/50 | 4241 / 2023 |
| combined-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 740 / 363 |
| combined-diamond50 | voidious.Diamond 1.8.28 | 49/50 | 3620 / 1811 |
| combined-melee10 | voidious.Diamond 1.8.28 + titan.TitanV3 3.0 + review.main.chimpazecomsarna.ChimpazeComSarna 1 | 5/10 | 2025 / 2413 |
| combined-titanv3-10 | titan.TitanV3 3.0 | 10/10 | 728 / 290 |
| combined-vingation-10 | titan.TitanVingation 1.7 | 10/10 | 791 / 356 |
| combined-warm50 | voidious.Diamond 1.8.28 | 49/50 | 3600 / 1875 |
| deadline-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | 10/10 | 916 / 400 |
| deadline-diamond10 | voidious.Diamond 1.8.28 | 10/10 | 729 / 426 |

Os casos de 10 rounds são triagens pequenas. Os casos `warm50` usam nova JVM e recarregam a memória própria, separados dos inícios sem memória importada. Não somamos fontes diferentes como se fossem uma versão única. Os IDs dos casos não fixam a semente do motor: as largadas são aleatórias a cada round.

As batalhas usam Robocode 1.12.0, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms por turno para ambos os robôs. O JAR original do Diamond permanece inalterado. Os JSONs registram hashes, verificações, resultados e regras; os CSVs preservam os diagnósticos por round. `registros.zip` contém os logs completos, com hashes em `registros.json`.

As simulações e verificações numéricas não comprovam melhoria competitiva. Uma série sem derrotas não garante vitórias em todas as batalhas futuras. O histórico externo registra somente estados próprios observados; seu tamanho depende do disco disponível.

Foram comparadas três fontes: conversão angular da mira neural, sombras limitadas ao prazo anterior ao primeiro impacto previsto, e a combinação. Todas venceram os dois testes exploratórios de10rounds. O critério pré-declarado de pior fração de vitórias e depois pior participação nos pontos selecionou a combinação; isso não demonstra efeito causal entre largadas aleatórias.

A fonte combinada terminou49/50 contra Diamond,48/50 contra ChimpazeComSarna,49/50 após recarregar memória contra Diamond, e10/10 contra TitanV3 e TitanVingation. No confronto solo simultâneo contra os três adversários fortes, foram5/10 vitórias e2025pontos contra2413do Diamond. O objetivo de vencer todos os rounds e liderar pontos continua não atendido.

A proteção temporal conserva cruzamentos válidos anteriores ao impacto previsto e exclui conservadoramente os cruzamentos no mesmo tick. As uniões de intervalos são armazenadas em cache por onda, limitadas a32prazos e invalidadas por novos disparos ou aposentadoria de projéteis. A fonte anterior falhou no caso controlado que assumia proteção garantida no mesmo tick; as novas fontes passaram nos testes de invalidação, intervalos sobrepostos e virada angular. Essa correção matemática não garante ganho competitivo.

A mira utiliza a distribuição neural anterior. O novo prior treinado na etapa4.26foi preservado para análise e não foi adotado nesta base porque apresentou menos vitórias nos testes frios. A telemetria que motivou a investigação temporal está documentada em `../v426/impactos.json`, com eventos completos nos respectivos registros.
