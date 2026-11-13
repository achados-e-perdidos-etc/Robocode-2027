# Estado dos testes — Titan Prime 4.15

A 4.15 acrescenta gravação e recarga de modelos neurais, avaliações de miras e amostras defensivas, compressão com quantidade de perfis dependente da cota, duas cópias com checksum e histórico externo opcional. O Java único está sem comentários, com os avisos obrigatórios preservados nos arquivos de licença.

Os [testes de memória](tests/memory/plan.json) foram executados com o fonte SHA256 `8e5c1b4503363cf3b1bd88ba7377111fca87c627326a26287dd5953ba57c98e9`. Passaram: reconstrução dos modelos e KNN, migração do formato anterior, nove perfis no mesmo snapshot, limites de leitura/descompressão, recarga em novos processos com Java 17 e Java 21, recuperação após corrupção e conservação/restauração de 40 checkpoints externos. A previsão neural mudou menos de `4e-12` no caso numérico verificado. A comemoração conservou 64 cores animadas em cada um dos nove rounds de teste visual; o CSV também passou na verificação de rotação.

As batalhas de integração de gravação e os adversários passivos dos testes visuais não são qualificação competitiva. A primeira série competitiva da 4.15 contra o Diamond original terminou válida em **48/50**, com 3498 pontos do Titan e 1878 do Diamond. O [resultado completo](tests/elite/v415/results.json), os [rounds](tests/elite/v415/rounds.csv) e as [duas derrotas](tests/elite/v415/losses.json) estão preservados. Mesmo orçamento de CPU de 10,03 ms, arena 800 × 600, largadas aleatórias e memória inicialmente limpa. A meta de vencer todos os rounds não passou. O [diagnóstico do PC](diagnostico-java17.md) registra a correção oficial do problema de threads da instalação 1.11.1.

## Checkpoint anterior 4.8

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
