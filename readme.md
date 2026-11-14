# Titan Prime 4.32 — checkpoint preservado

Variante salva por solicitação do usuário após vencer **50/50 contra o Diamond 1.8.28**. O código continua em um único [titanprime.java](robots/equipe/titanprime.java), classe `equipe.titanprime`, sem comentários no Java.

| Adversário | Vitórias | Pontos Titan | Pontos adversário |
| --- | ---: | ---: | ---: |
| Diamond 1.8.28 | 50/50 | 3.695 | 1.807 |
| ChimpazeComSarna | 47/50 | 4.181 | 1.913 |

As duas batalhas usaram exatamente a mesma fonte, sem aprendizado importado: Robocode 1.12.0, arena 800×600, orçamento de cerca de 10,03 ms por turno igual para os participantes e largadas aleatórias. Os adversários originais permaneceram inalterados. [Resultados, regras, hashes, logs e derrotas](tests/elite/v432/leia-me.md).

A meta de 50/50 contra os dois ainda não foi atingida. A melhoria contra o ChimpazeComSarna continua em experiências separadas; este checkpoint será mantido.

A atualização restaura a cadência de tiros fracos em situações de pouca energia, aplica a potência de recuperação depois da redução cúbica e reduz tiros fortes quando seus próprios resultados observados são ruins. Os critérios são baseados nas observações do combate, não em nomes de adversários.

- [JAR completo 4.32](releases/titanprime-4.32.jar) e [metadados](releases/titanprime-4.32.json), com todas as 115 classes internas e bytecode compatível com Java 8.
- [Instalação](como-testar.md) e [aprendizado entre batalhas](aprendizado.md).
- [4.31 preservada](releases/titanprime-4.31.jar), [4.28](releases/titanprime-4.28.jar) e [4.15](releases/titanprime-4.15.jar).

Fonte avaliada e publicada: SHA256 `63038a3583572531cfc4ffb73af685ff2c71445059ac684b86c5e91b46d62737`. O JAR foi montado com as próprias classes compiladas usadas no teste de 50 rounds, sem alterar o código. Uma triagem da outra variante foi invalidada pelo motor e não foi creditada como vitória; ela permanece nos registros.

O aprendizado persistente, as cores e a comemoração foram mantidos. A base usa Diamond, de Voidious, e KNN de Rednaxela; os [avisos obrigatórios](licenses/source-notices.txt) continuam no projeto e no JAR.
