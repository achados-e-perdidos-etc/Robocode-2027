# Titan Prime 4.38

Variante salva por solicitação do usuário. Um único [titanprime.java](robots/equipe/titanprime.java), classe `equipe.titanprime`, sem comentários no Java.

| Adversário | Vitórias | Pontos Titan | Pontos adversário |
| --- | ---: | ---: | ---: |
| ChimpazeComSarna | 50/50 | 3.042 | 503 |
| Diamond 1.8.28 | 49/50 | 3.637 | 1.739 |

As duas séries válidas usaram exatamente a mesma fonte, nova JVM, aprendizado inicialmente vazio, adversários originais, arena 800×600 e orçamento igual de cerca de 10,03 ms por turno. As largadas foram aleatórias. A meta de 50/50 contra ambos ainda não foi atingida: a derrota contra Diamond no round41 está preservada.

A variante mantém Wave Surfing, miras adaptativas e aprendizado persistente. Acrescenta interceptação de tiros fortes: estima ondas, desloca o robô lateralmente e calcula o cruzamento de um tiro leve com a trajetória estimada. Após três impactos, retorna à defesa normal. A abertura permite movimento durante o aquecimento inicial e prepara a parada antes do primeiro disparo. O controlador usa observações do combate, sem escolher estratégias pelo nome do adversário.

Também corrige a referência do controlador de movimento entre rounds e inicializa a previsão de velocidade máxima em8, conforme a física do jogo. Cores e comemoração foram mantidas.

- [JAR completo 4.38](releases/titanprime-4.38.jar), [metadados](releases/titanprime-4.38.json) e [instalação](como-testar.md).
- [Resultados, hashes, logs e derrota](tests/elite/v438/leia-me.md).
- [4.32 preservada](releases/titanprime-4.32.jar) e [seus resultados](tests/elite/v432/leia-me.md).
- [Histórico completo](status-dos-testes.md) e [aprendizado entre batalhas](aprendizado.md).

O JAR contém117 classes, bytecode52, montadas com as próprias classes usadas nas duas séries de50 rounds. Fonte SHA256 `7c402d76bed85fcf1c4068fcb77128db0a62e5c9040b682fe27fd0d66a3280da`.

A base usa Diamond, de Voidious, e KNN de Rednaxela; os [avisos obrigatórios](licenses/source-notices.txt) permanecem no projeto e no JAR.

O JAR empacotado passou em Java17: duelo de3 rounds com o Chimpanzé e Melee de1 round com Diamond, Chimpanzé eTitanV3. Verificação de instalação, separada das séries competitivas.

## Variante com aprendizado do PC

A [4.41-pc](variants/aprendizado-pc/leia-me.md) incorpora quatro perfis enviados pelo usuário dentro de um único Java e continua salvando atualizações locais. [JAR da variante](releases/titanprime-4.41-pc.jar). Validação de memória concluída; desempenho competitivo ainda não reavaliado. O código principal e o JAR 4.38 permanecem preservados.


[Comparação de 100 rounds entre a 4.38 e a 4.41-pc](tests/embedded-pc-memory/comparison-2026-11-21/leia-me.md): 49/50 versus 50/50 distribuídos entre quatro adversários; resultado preliminar, com ganho não uniforme. As duas versões permanecem disponíveis.
