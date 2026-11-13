# Titan Prime 4.15 — Robocode clássico

O código continua em um único [titanprime.java](robots/equipe/titanprime.java), classe `equipe.titanprime`, agora sem comentários. A atualização grava aprendizado por adversário e recarrega os modelos em batalhas futuras. O [arquivador externo](aprendizado.md) conserva checkpoints sem limite fixo de versões, sujeito ao espaço em disco. Dentro do jogo, a memória respeita a cota do Robocode.

O checkpoint **4.8**, preservado em seu JAR e no histórico, fez **49/50 e 48/50** contra o Diamond 1.8.28 original: **97 vitórias em 100 rounds**, liderando em pontos nas duas. Esses resultados pertencem à 4.8. A nova 4.15 fez **48/50**, com liderança em pontos, em sua primeira série de 50 rounds; os [resultados completos](tests/elite/v415/results.json) conservam as duas derrotas. A meta de vencer todos os rounds continua pendente.

| Série | Vitórias | Pontos do Titan | Pontos do Diamond |
| --- | ---: | ---: | ---: |
| Referência 1 | 49/50 | 3.624 | 1.991 |
| Referência 2 | 48/50 | 3.512 | 1.933 |

Robocode 1.12.0, arena 800 × 600, CPU de referência de 10,03 ms igual para ambos, largadas aleatórias e memória nova por batalha. [Resultados e verificações](tests/elite/v48/checkpoint.json). A candidata anterior pré-treinada fez 50/50 em desenvolvimento; ela tem outro hash e não é apresentada como resultado desta versão.

- [Fonte Java único](robots/equipe/titanprime.java).
- [JAR completo 4.15](releases/titanprime-4.15.jar), com todas as classes internas e licenças; [metadados](releases/titanprime-4.15.json).
- [Checkpoint 4.8](releases/titanprime-4.8.jar) e [metadados originais](releases/titanprime-4.8.json).
- [Aprendizado entre batalhas](aprendizado.md) e [diagnóstico do Java 17](diagnostico-java17.md).
- [Instalar e testar](como-testar.md).
- [Arquitetura](arquitetura.md) e [sistemas ativos](estrategia.md).
- [Revisão das derrotas](revisao-das-derrotas.md) e [estado dos testes](status-dos-testes.md).

A base incorpora diretamente o motor aberto **Diamond, de Voidious**, com KNN de **Rednaxela**, sob a [licença preservada](licenses/diamond.txt). As adaptações incluem duas miras neurais com pesos iniciais e aprendizado online, energia econômica em duelos, correções de ondas e cache, diagnóstico por round, paleta por batalha e comemoração.

Os testes de memória verificaram recarga real em novos processos com Java 17 e Java 21, recuperação de cópia corrompida, nove adversários no arquivo comprimido e restauração do histórico externo. Isso comprova persistência e integridade; não demonstra vitória garantida nem ganho competitivo causado pela memória. Os experimentos e relatórios 3.x descrevem versões históricas e suas APIs próprias.
