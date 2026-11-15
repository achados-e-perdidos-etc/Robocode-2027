# Validação experimental v434

Na execução destes casos, o checkpoint publicado era a versão4.32-calibrated, commit `1d87d3138ee57b9550de1d8923cedfb1c3189028`. Nenhuma destas candidatas atingiu o requisito de 50/50 contra os dois adversários.

| Caso | Adversário | Validade | Vitórias / rounds | Pontos Titan / adversário |
|---|---|---|---:|---:|
| moderate07-chimp30 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | Válido | 25/30 | 2256 / 1678 |
| moderate10-chimp30 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | Válido | 27/30 | 2332 / 1248 |

As fontes são identificadas por SHA256. Cada caso usa nova JVM, dados próprios vazios, largadas aleatórias, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms para ambos os robôs. Os JARs adversários originais não foram alterados. IDs de casos não fixam o gerador aleatório. Os JSONs contêm verificações e erros; os CSVs e `registros.zip` preservam os resultados e logs completos, inclusive as derrotas.

As duas políticas de potência moderada falharam na triagem de 30 rounds; nenhuma seguiu para confirmação de 50 rounds.
