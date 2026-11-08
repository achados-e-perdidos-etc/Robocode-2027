# Ataque híbrido do Titan Prime 3.5

O **Titan Prime 3.5** adota KNN por adversário e ondas adicionais de observação, integrados ao seletor de miras virtuais. Na confirmação adicional com posições novas, venceu **25/30 séries**, contra **23/30** da versão 3.4. Depois preservou **63/63 séries competitivas e 188/189 rounds** na bateria dos exemplos.

O código continua em um único [titanprime.java](robots/equipe/titanprime.java). O algoritmo de defesa, os parâmetros de potência, os pesos neurais pré-treinados, as cores por batalha e a comemoração foram preservados. As alterações de ataque mudam naturalmente o desenrolar das batalhas e o aprendizado durante os rounds.

## O que foi acrescentado

A base já tinha miras direta, linear, circular, GuessFactor e neural. Agora o seletor também compara duas distribuições KNN: uma treinada com visitas observadas, outra somente com ondas associadas a disparos reais. Cada banco guarda até 256 exemplos por adversário, com 12 características, vizinhos próximos e peso de recência.

As ondas sem disparo coletam visitas durante o resfriamento ou outras oportunidades, no máximo a cada quatro ticks no duelo e oito com vários adversários. Elas não gastam energia de tiro. **Somente ondas de disparos reais avaliam o desempenho das miras**, evitando contar um tiro inexistente como acerto.

Os exemplos novos também alimentam os histogramas e o aprendizado neural existentes. Tudo permanece em memória entre os três rounds da batalha, com reinício dos bancos KNN entre batalhas independentes. Isso não é uma implementação completa do anti-surfing do Diamond nem de Pattern Matching.

A relação com as 25 técnicas está em [referencias-ataque.md](referencias-ataque.md).

## Seleção inicial: 48 séries / 144 rounds

Quatro versões enfrentaram Diamond 1.8.28, uma cópia da versão atual, SuperMercutio, VelociRobot, seis exemplos juntos e a equipe real de cinco robôs. Sementes de posições: 1523 e 1607.

| Versão | Séries vencidas | Rounds vencidos | Primeiros rounds | Turnos perdidos |
| --- | ---: | ---: | ---: | ---: |
| Atual 3.4 | 9/12 | 25/36 | 10/12 | 0 |
| KNN | 10/12 | 29/36 | 9/12 | 0 |
| Visitas extras | 9/12 | 27/36 | 8/12 | 0 |
| KNN + visitas | 10/12 | 31/36 | 10/12 | 1 |

A combinação foi selecionada pela ordem definida no programa: séries, primeiros rounds, rounds, pontuação relativa e turnos perdidos.

## Primeira avaliação reservada: 36 séries / 108 rounds

Posições novas: 1877, 1993 e 2131, nos mesmos seis contextos.

| Versão | Séries vencidas | Rounds vencidos | Primeiros rounds | Turnos perdidos |
| --- | ---: | ---: | ---: | ---: |
| Atual 3.4 | 14/18 | 40/54 | 15/18 | 0 |
| KNN + visitas | 15/18 | 44/54 | 14/18 | 0 |

A candidata ganhou mais séries, mas perdeu um primeiro round a mais. **Não passou no protocolo inicial**, que exigia preservar também o total de vitórias no primeiro round. Esse resultado foi preservado sem reclassificá-lo como aprovado.

## Confirmação adicional: 60 séries / 180 rounds

Após o resultado anterior, foi planejada uma confirmação independente, com código congelado e sementes novas: 2393, 2539, 2671, 2801 e 2957. O protocolo adicional prioriza a regra confirmada da competição: vencer pelo menos dois dos três rounds. Exige mais séries vencidas, nenhuma regressão de séries por contexto e no máximo três turnos perdidos extras. Os primeiros rounds são relatados como métrica secundária. Essa etapa foi planejada depois da primeira avaliação, não faz parte do seu protocolo original.

| Versão | Séries vencidas | Rounds vencidos | Primeiros rounds | Turnos perdidos |
| --- | ---: | ---: | ---: | ---: |
| Atual 3.4 | 23/30 | 65/90 | 23/30 | 1 |
| KNN + visitas | 25/30 | 73/90 | 24/30 | 3 |

| Contexto | Atual: séries | Nova: séries | Atual: rounds | Nova: rounds |
| --- | ---: | ---: | ---: | ---: |
| Diamond 1.8.28 | 0/5 | 0/5 | 0/15 | 0/15 |
| Cópia da 3.4 | 3/5 | 5/5 | 8/15 | 13/15 |
| SuperMercutio | 5/5 | 5/5 | 13/15 | 15/15 |
| VelociRobot | 5/5 | 5/5 | 15/15 | 15/15 |
| Arena com seis exemplos | 5/5 | 5/5 | 15/15 | 15/15 |
| Equipe real de cinco | 5/5 | 5/5 | 14/15 | 15/15 |

A candidata passou nos critérios adicionais. O ganho de séries veio do confronto contra a cópia da 3.4. As derrotas contra Diamond continuam presentes; a atualização não comprova nível de elite.

## Bateria de proteção antes da adoção

A versão candidata recebeu apenas a troca do comentário de versão para 3.5 e foi testada contra as 25 entradas de exemplos da instalação, conforme a classificação de [robos-de-exemplo.md](robos-de-exemplo.md).

- Duelo com cada um dos 16 exemplos autônomos: **48/48 séries, 144/144 rounds**.
- Grupos de 2, 3, 6 e 16 adversários: **12/12 séries, 36/36 rounds**.
- Equipe real de cinco: **3/3 séries, 8/9 rounds**.
- Total competitivo: **63/63 séries, 188/189 rounds e 63/63 primeiros rounds**.

A bateria completa teve 84 séries válidas / 252 rounds; exemplos passivos, interativos, dependentes e sentinela foram separados do total competitivo. A tentativa de iniciar `sampleex.Slave` foi inválida e não conta como vitória. Houve um turno perdido nas séries válidas e nenhum tiro direcionado ao sentinela.

O requisito adicional antes da adoção era preservar as 63 séries e pelo menos 188 dos 189 rounds competitivos. A versão passou. As cores e os giros de comemoração foram conferidos novamente em três batalhas de três rounds contra SittingDuck, como teste cosmético separado.

## Evidências e limites

Robocode clássico 1.11.1 da instalação enviada, Java 21, compilação com alvo Java 8, arena 800 × 600, resfriamento 0,1 e três rounds por batalha. Posições solicitadas por `RobotSetup[]`, com coordenadas efetivas e possíveis reposicionamentos registrados. As sementes não fixam toda a aleatoriedade interna nem a carga da JVM. Não se trata de estimativa com intervalo de confiança.

As partidas começam com os mesmos modelos neurais incorporados e memória por adversário vazia. Os marcadores de início verificam isso nos estudos ofensivos. O aprendizado ocorre dentro de cada batalha; não foi feita uma batalha longa para depois dividi-la em séries.

A seleção das novas miras foi registrada em tiros reais. Na primeira avaliação reservada, houve 1.326 registros de seleção de uma das alternativas KNN no tick do disparo e 9.515 ondas extras geradas. A solução angular pode ficar retida por até dois ticks, como na base; esses contadores descrevem a seleção registrada, não uma auditoria exata da origem de cada ângulo retido. Precisão virtual estimada e acertos reais são métricas diferentes.

Os testes numéricos verificaram aprendizado condicional, normalização, limites e cópia da memória, escolha por largura do alvo e a separação entre visitas e avaliação de disparos. A defesa e os pesos pré-treinados foram comparados aos da base. Os testes não demonstram conhecimento das decisões futuras do adversário.

## Repetir

Use uma instalação separada com Diamond 1.8.28 e o SuperMercutio do estudo anterior instalado como `reference.supermercutio`. A versão anterior é obtida de `robots/equipe/titanprime.java` no commit `83ddd0da9f869d46b6a6184a5188ad86e4964ae3`.

```sh
python tools/attack.py --home /caminho/robocode-testes --source /caminho/titanprime-3.4.java --output /caminho/ataque
python tests/attack_checks.py --api /caminho/robocode/libs/robocode.jar
```

Acrescente `--ecj /caminho/ecj.jar` se não houver `javac`. Sem `--source`, um novo estudo usa o arquivo atual, que já inclui as novas miras, e não reproduz a comparação histórica da 3.4.

A confirmação é repetida com `tools/attack_confirm.py`, passando `--source` da base e `--candidate` do estudo. Os scripts não publicam nem substituem o robô automaticamente.

Para a ferramenta histórica `tools/bo3.py`, o protótipo foi adaptado à 3.5: `FAST_OBSERVATION` controla as ondas extras já existentes e preserva a avaliação de miras somente em ondas de tiros reais; `WARM_DEFENSE` continua sendo uma alternativa experimental. Isso não ativa as defesas retangular ou KNN rejeitadas no estudo anterior.

Dados: [seleção e primeira avaliação](tests/attack-results.json), [confirmação adicional](tests/attack-confirmation.json), [bateria dos exemplos](tests/attack-guards.json), [decisão e hashes](tests/attack-promotion.json) e [cores](tests/colors-results.json).
