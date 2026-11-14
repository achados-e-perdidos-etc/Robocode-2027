# Titan Prime 4.28 — Robocode clássico

Versão final desta etapa: um único [titanprime.java](robots/equipe/titanprime.java), classe `equipe.titanprime`, sem comentários no Java. O [JAR pronto](releases/titanprime-4.28.jar) inclui todas as 115 classes internas; você pode usá-lo sem compilar no computador.

A atualização corrige a previsão defensiva de sombras de projéteis e a conversão angular da mira neural perto de paredes. Também reúne controle de potência baseado na precisão observada, estatísticas defensivas por potência e conservação equilibrada das amostras de aprendizado. A memória entre batalhas, as cores e a comemoração foram mantidas.

| Teste desta mesma fonte | Vitórias | Pontos Titan | Maior adversário |
| --- | ---: | ---: | ---: |
| Diamond 1.8.28, início sem memória importada | 49/50 | 3.620 | 1.811 |
| ChimpazeComSarna, início sem memória importada | 48/50 | 4.241 | 2.023 |
| Diamond, memória recarregada em nova JVM | 49/50 | 3.600 | 1.875 |
| TitanV3 | 10/10 | 728 | 290 |
| TitanVingation | 10/10 | 791 | 356 |
| Solo contra Diamond + ChimpazeComSarna + TitanV3 | 5/10 | 2.025 | 2.413 |

A meta de vencer todos os rounds não foi atingida. O desempenho em Melee ainda precisa melhorar. Os testes curtos contra TitanV3 e TitanVingation não comprovam domínio sobre todas as estratégias. [Resultados completos, derrotas, hashes e logs](tests/elite/v428/leia-me.md).

As batalhas usam Robocode 1.12.0, arena 800 × 600, orçamento de referência de aproximadamente 10,03 ms por turno igual para ambos e largadas aleatórias. IDs de casos não fixam a semente do motor. As séries frias e com memória estão separadas. A fonte publicada é exatamente a avaliada, SHA256 `0c30d0aaba20ee8cc5ecb32c0279ec22a35497f60ca63593467d37124dc4f2ce`.

A experiência 4.30 terminou 19/20 contra Diamond e 18/20 contra ChimpazeComSarna na confirmação rápida e não substituiu esta base. O novo prior neural e as trajetórias de Melee que não confirmaram ganho também foram preservados nos relatórios, sem serem adotados na entrega.

- [Baixar o JAR completo 4.28](releases/titanprime-4.28.jar) e [metadados de compilação](releases/titanprime-4.28.json).
- [Instalar e testar](como-testar.md), preferencialmente com Robocode 1.12.0 e Java 17.
- [Verificação de física, KNN e paredes](tests/elite/v428/release-physics.json) e [integridade da memória](tests/elite/v428/release-memory.json), executadas com Java 17; bytecode compatível com Java 8.
- [Teste do JAR final no Robocode com Java 17](tests/elite/v428/release-jar-smoke.json): carregamento, round completo e gravação da memória passaram; teste de instalação de um round, separado das séries competitivas.
- [Aprendizado entre batalhas](aprendizado.md) e [diagnóstico da instalação Java 17](diagnostico-java17.md).
- [Treino neural e resultados independentes](tests/elite/v426/leia-me.md), [comparação de rotas](tests/elite/v429/leia-me.md) e [última comparação rápida](tests/elite/v430/leia-me.md).
- Versões anteriores preservadas: [4.15](releases/titanprime-4.15.jar) e [4.8](releases/titanprime-4.8.jar).

O aprendizado próprio é salvo em duas cópias verificadas e recarregado em batalhas futuras, respeitando a cota do Robocode. O [arquivador externo](aprendizado.md) mantém histórico sem limite fixo de versões, sujeito ao espaço em disco. Os contadores novos por grupo de potência ficam na RAM entre rounds; os modelos neurais, amostras defensivas e avaliações globais continuam sendo persistidos.

A base incorpora o motor aberto Diamond, de Voidious, e KNN de Rednaxela. Os avisos e licenças obrigatórios estão preservados em [licenses](licenses/source-notices.txt) e no JAR. Os relatórios históricos descrevem as fontes e o estado da publicação na data em que foram gerados.
