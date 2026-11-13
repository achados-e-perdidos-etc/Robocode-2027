> Histórico das versões 3.x. O código atual 4.x usa outra base; veja [arquitetura.md](arquitetura.md) e [ciclo-elite.md](ciclo-elite.md).

# Experimentos de defesa: retângulo e contexto

Foram executadas **84 séries independentes de três rounds: 252 rounds**, no Robocode **1.11.1** enviado pelo usuário. A base foi o Titan Prime **3.3**. As alternativas não foram promovidas, pois não aumentaram o número de séries vencidas na avaliação reservada.

O Titan Prime 3.4 acrescenta cores e comemoração à defesa mantida. Os números abaixo pertencem à base 3.3 e aos protótipos identificados pelos hashes, não constituem uma nova medição competitiva da versão 3.4.

## Alternativas implementadas

**Retângulo:** calcula interseções de um anel de bala com os lados e cantos do retângulo de 36 × 36 do robô. Acumula um envelope angular durante os ticks de cruzamento e avalia a densidade média de perigo nesse intervalo. É uma aproximação conservadora: unir intervalos pode incluir lacunas, e densidade média não é a probabilidade exata de impacto. Não reproduz todo o sistema de Precise Wave Surfing do Diamond.

**Contexto:** um KNN separado por adversário guarda até 192 exemplos de ângulos observados. Usa 12 características, vizinhos próximos, redução do peso de observações antigas e mistura limitada com os modelos existentes. Aprende apenas de ondas associadas a balas com confiança suficiente. É uma implementação própria e limitada; não inclui a árvore KD e os vários modelos do Diamond.

**Combinada:** ativa ambas. Os parâmetros de mira, potência e pesos neurais incorporados foram preservados em todas as alternativas. A seleção não tentou copiar código dos adversários nem ativar 20 controladores simultaneamente.

## Seleção: 48 séries / 144 rounds

Cada versão enfrentou Diamond 1.8.28, SuperMercutio, TrackFire, VelociRobot, seis exemplos juntos e a equipe real de cinco robôs. Duas posições por contexto: sementes 811 e 877.

| Versão | Séries vencidas | Rounds vencidos | Primeiros rounds vencidos | Turnos perdidos |
| --- | ---: | ---: | ---: | ---: |
| Atual 3.3 | 10/12 | 29/36 | 10/12 | 0 |
| Retângulo | 10/12 | 30/36 | 10/12 | 1 |
| Contexto | 10/12 | 30/36 | 10/12 | 0 |
| Combinada | 10/12 | 28/36 | 9/12 | 0 |

A versão retangular foi selecionada pelo desempate definido no programa: séries, primeiro round, rounds, pontuação relativa e turnos perdidos. A diferença de pontuação para Contexto foi pequena e não demonstra superioridade geral. Nenhuma alternativa venceu uma série contra Diamond nessa etapa.

## Avaliação reservada: 36 séries / 108 rounds

A versão atual e a selecionada enfrentaram os mesmos seis contextos em três novas posições: sementes 1009, 1151 e 1297.

| Versão | Séries vencidas | Rounds vencidos | Primeiros rounds vencidos | Turnos perdidos | Pontuação relativa média |
| --- | ---: | ---: | ---: | ---: | ---: |
| Atual 3.3 | 15/18 | 44/54 | 14/18 | 0 | 68,04% |
| Retângulo | 15/18 | 43/54 | 14/18 | 1 | 66,34% |

Ambas perderam as três séries contra Diamond e venceram as outras quinze. O retângulo não aumentou as séries vencidas e venceu um round a menos. A defesa de uso normal foi mantida.

O critério de promoção foi registrado antes das batalhas: aumentar séries vencidas na avaliação reservada, preservar as séries por contexto e as vitórias no primeiro round, com no máximo três turnos perdidos extras. Pontos maiores sozinhos não satisfazem a regra de ganhar dois dos três rounds. Uma candidata aprovada ainda precisaria passar pela bateria completa dos exemplos antes da adoção.

## Evidências e limites

As 84 séries começaram com os mesmos contadores de pré-treino: 1.228 observações de mira e 128 de defesa. O aprendizado foi conservado entre rounds, com reinício entre séries independentes.

As posições usam `RobotSetup[]` e são registradas nos três rounds. Houve reposicionamento de um participante em uma série da seleção com a base; as 36 séries da avaliação reservada confirmaram as posições planejadas, admitindo o primeiro passo de movimento e rotação. A aleatoriedade interna e a carga da JVM não foram completamente fixadas. São amostras pequenas, sem intervalo de confiança e sem demonstração de força de elite.

SuperMercutio foi compilado a partir da referência identificada em [referencias-defesa.md](referencias-defesa.md). Apenas pacote e classe foram renomeados no ambiente local de teste. Diamond usou o JAR 1.8.28 da instalação enviada. Nenhuma dessas fontes ou binários foi redistribuída no projeto.

A verificação geométrica cobriu **56.435 pontos de interseção amostrados**, intervalos espelhados e passagem pelo limite ±π. Também foram verificados aprendizado condicional, normalização, substituição de padrões antigos e limite de memória do KNN. Esses testes verificam os componentes; os resultados de batalha determinam se ajudaram.

## Repetir

Use uma instalação separada com `voidious.Diamond 1.8.28`. Forneça o arquivo original do SuperMercutio como entrada local:

```sh
python tools/defense.py --home /caminho/robocode-testes --output /caminho/defesa --supermercutio /caminho/SuperMercutio.java
python tests/defense_checks.py --api /caminho/robocode/libs/robocode.jar
```

Acrescente `--ecj /caminho/ecj.jar` quando não houver `javac`. Para repetir a base histórica, obtenha `robots/equipe/titanprime.java` do commit `a8409eadf4b9b5fb182bfbc487422e8ffab59301` e passe `--source /caminho/titanprime-3.3.java`. Sem essa opção, o programa usa o arquivo atual e cria um experimento novo. A opção `--parse-only` exige os fontes originais correspondentes aos logs.

A ferramenta gera variantes temporárias e uma proposta, sem modificar automaticamente o Java principal. Os protótipos ficam como alterações textuais verificadas por hash em [tools/defense-prototype.json](tools/defense-prototype.json); o robô entregue continua em um único Java.

Dados: [tests/defense-results.json](tests/defense-results.json). A avaliação das 20 referências está em [referencias-defesa.md](referencias-defesa.md).
