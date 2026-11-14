# Titan Prime 4.31 — melhoria no Melee

A atualização corrige o aprendizado de mira e as informações de distância nas batalhas com vários adversários. O robô continua em um único [titanprime.java](robots/equipe/titanprime.java), classe `equipe.titanprime`, sem comentários no Java. O [JAR pronto](releases/titanprime-4.31.jar) inclui todas as 115 classes internas.

Foram corrigidos dois erros confirmados em testes do Java real:

- A previsão da posição de outro atirador recebia velocidade e direção em ordem trocada, produzindo origens erradas para ondas de treinamento da mira Melee.
- A atualização das distâncias dependia de um laço de pares e deixava o último adversário sem atualização. Agora cada adversário vivo é atualizado independentemente da ordem, inclusive quando só um foi observado numa batalha iniciada com vários.

Também foi protegida a transição rápida para 1v1: a mira continua válida mesmo quando ainda não existe a alternativa perceptual. O fallback dos duelos iniciados em 1v1 foi verificado separadamente. Não foram adotadas novamente as rotas experimentais que pioraram a versão anterior.

Todos os testes abaixo são do Titan sozinho contra Diamond 1.8.28, ChimpazeComSarna e TitanV3 simultaneamente, sem equipes:

| Versão e série | Vitórias | Pontos Titan | Pontos Diamond |
| --- | ---: | ---: | ---: |
| Referência 4.28 | 5/10 | 1.902 | 2.287 |
| Triagem 4.31 | 4/10 | 1.770 | 2.418 |
| Confirmação independente 4.31 | 13/20 | 4.442 | 4.026 |
| Total dos dois testes4.31 | 17/30 | 6.212 | 6.444 |

A confirmação foi melhor, mas houve oscilação e a soma dos pontos da nova versão ainda ficou abaixo do Diamond. Essas amostras curtas com largadas aleatórias não provam superioridade estatística. Todas as derrotas estão nos [relatórios completos](tests/elite/v431/leia-me.md).

Os testes usaram Robocode 1.12.0, arena 800×600 e aproximadamente 10,03 ms por turno iguais para todos os participantes. Os IDs identificam casos; não fixam a semente do motor. Os adversários originais permaneceram inalterados. Fonte publicada: SHA256 `76f83838ab1c29867d1d5d23a5f66fda2ed3b135663fa24addd940c4cc63ae6a`.

- [JAR 4.31](releases/titanprime-4.31.jar), [metadados](releases/titanprime-4.31.json) e [instalação](como-testar.md).
- [Regressões do Melee](tests/elite/v431/release-melee-checks.json), [física/KNN](tests/elite/v431/release-physics.json) e [memória](tests/elite/v431/release-memory.json), verificadas com Java 17 e bytecode compatível com Java 8.
- [JAR final carregado em Melee no Java 17](tests/elite/v431/release-jar-smoke.json): round completo, quatro robôs carregados, memória salva e nenhum erro; teste de instalação de um round, separado das séries competitivas.
- [Aprendizado entre batalhas](aprendizado.md), cores e comemoração mantidos.
- [Versão 4.28 preservada](releases/titanprime-4.28.jar). Seus 49/50 contra Diamond e 48/50 contra Chimp pertencem à 4.28; esses duelos não foram repetidos nem atribuídos à 4.31.

A base incorpora Diamond, de Voidious, com KNN de Rednaxela. As [licenças e avisos obrigatórios](licenses/source-notices.txt) continuam preservados no projeto e no JAR.
