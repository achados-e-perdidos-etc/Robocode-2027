# Titan Prime 4.8 — Robocode clássico

Versão salva na `main` a pedido do usuário antes de continuar as melhorias. O código está em um único [titanprime.java](robots/equipe/titanprime.java), classe `equipe.titanprime`.

Contra o Diamond 1.8.28 original, o mesmo fonte fez **49/50 e 48/50** em duas séries válidas: **97 vitórias em 100 rounds**, liderando em pontos nas duas. A meta de vencer todos os rounds continua pendente.

| Série | Vitórias | Pontos do Titan | Pontos do Diamond |
| --- | ---: | ---: | ---: |
| Referência 1 | 49/50 | 3.624 | 1.991 |
| Referência 2 | 48/50 | 3.512 | 1.933 |

Robocode 1.12.0, arena 800 × 600, CPU de referência de 10,03 ms igual para ambos, largadas aleatórias e memória nova por batalha. [Resultados e verificações](tests/elite/v48/checkpoint.json). A candidata anterior pré-treinada fez 50/50 em desenvolvimento; ela tem outro hash e não é apresentada como resultado desta versão.

- [Fonte Java único](robots/equipe/titanprime.java).
- [JAR completo compilado](releases/titanprime-4.8.jar), com todas as classes internas e licença; [metadados](releases/titanprime-4.8.json).
- [Instalar e testar](como-testar.md).
- [Arquitetura](arquitetura.md) e [sistemas ativos](estrategia.md).
- [Revisão das derrotas](revisao-das-derrotas.md) e [estado dos testes](status-dos-testes.md).

A base incorpora diretamente o motor aberto **Diamond, de Voidious**, com KNN de **Rednaxela**, sob a [licença preservada](licenses/diamond.txt). As adaptações incluem duas miras neurais com pesos iniciais e aprendizado online, energia econômica em duelos, correções de ondas e cache, diagnóstico por round, paleta por batalha e comemoração.

O aprendizado em memória continua entre rounds da mesma batalha. O CSV persiste diagnósticos; pesos novos não são recarregados de disco entre batalhas independentes. Os experimentos e relatórios 3.x descrevem versões históricas e suas APIs próprias.
