# Estado dos testes — Titan Prime 4.8

A versão 4.8 foi salva na main por solicitação expressa do usuário, antes de continuar os ajustes. O fonte é exatamente o avaliado, SHA256 `467d018c56a20bffcb224aa14ca8749f337e7a614d35388e3140fa88b9171460`.

| Série da 4.8 contra Diamond 1.8.28 | Vitórias | Pontos do Titan | Pontos do Diamond | Validade |
| --- | ---: | ---: | ---: | --- |
| Referência 1 | 49/50 | 3.624 | 1.991 | Válida |
| Referência 2 | 48/50 | 3.512 | 1.933 | Válida |

Total: **97/100**, com liderança em pontos nas duas séries. Arena 800 × 600, resfriamento 0,1, inatividade 450, Java 21, Robocode 1.12.0 e orçamento de referência de 10,03 ms para ambos. As posições são sorteadas pelo motor a cada round, registradas e verificadas; os identificadores dos lotes não fixam o PRNG.

Os 100 registros concordam com os placares do motor, sem erros de execução. Nas três derrotas não houve colisão com parede nem turno perdido. A avaliação de todos os adversários e os 50 rounds contra Chimp com este mesmo fonte ainda estão pendentes: os resultados anteriores pertencem a outras candidatas.

A verificação numérica passou em 157 trajetórias capturadas, 100 comparações KNN com busca exaustiva e recuperação limitada quando não há destino seguro. O bytecode é compatível com Java 8. [Checkpoint](tests/elite/v48/checkpoint.json), [resultados completos](tests/elite/v48/results.json), [rounds](tests/elite/v48/rounds.csv) e [verificação numérica](tests/elite/v48/physics.json).

A variante anterior pré-treinada fechou 50/50 em desenvolvimento, usando 50 ms para ambos, mas tem outro fonte. Esse resultado é preservado em [neural-pretrained-50-results.json](tests/elite/neural-pretrained-50-results.json). As largadas diferentes impedem atribuir isoladamente um ganho causal aos pesos neurais.

As correções da 4.8 incluem GuessFactor na transição Melee→duelo, ordenação das ondas por tempo estimado de impacto, invalidação do cache KNN quando a identidade da onda muda e registro de impactos terminais do mesmo tick. [Revisão das derrotas](revisao-das-derrotas.md).

A meta de vencer todos os rounds permanece aberta. A publicação deste checkpoint não altera os resultados desfavoráveis nem aprova essa meta.
