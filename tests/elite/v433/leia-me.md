# Validação experimental v433

Na execução destes casos, o checkpoint publicado era a versão4.32-calibrated, commit `1d87d3138ee57b9550de1d8923cedfb1c3189028`. Nenhuma destas candidatas atingiu o requisito de 50/50 contra os dois adversários.

| Caso | Adversário | Validade | Vitórias / rounds | Pontos Titan / adversário |
|---|---|---|---:|---:|
| comeback-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | Válido | 10/10 | 817 / 328 |
| comeback-chimp50 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | Válido | 46/50 | 4026 / 2430 |
| comeback-diamond10 | voidious.Diamond 1.8.28 | Válido | 10/10 | 740 / 360 |
| comeback-diamond50 | voidious.Diamond 1.8.28 | Inválido | Desconsiderado | — |
| original-chimp10 | review.main.chimpazecomsarna.ChimpazeComSarna 1 | Válido | 8/10 | 736 / 536 |
| original-diamond10 | voidious.Diamond 1.8.28 | Válido | 9/10 | 654 / 440 |

As fontes são identificadas por SHA256. Cada caso usa nova JVM, dados próprios vazios, largadas aleatórias, arena 800×600 e o mesmo orçamento de cerca de 10,03 ms para ambos os robôs. Os JARs adversários originais não foram alterados. IDs de casos não fixam o gerador aleatório. Os JSONs contêm verificações e erros; os CSVs e `registros.zip` preservam os resultados e logs completos, inclusive as derrotas.

O caso `comeback-diamond50` é inválido: o motor informou `No score will be generated` para o Diamond. Seus números aparentes foram excluídos desta tabela. Os testes longos do perfil original foram dispensados após o perfil combinado falhar contra o Chimpanzé. A decisão está registrada em `sequence-amendment.json`.
