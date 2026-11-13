> Histórico das versões 3.x. O código atual 4.x usa outra base; veja [arquitetura.md](arquitetura.md) e [ciclo-elite.md](ciclo-elite.md).

# Titan Prime contra os exemplos do seu Robocode

A versão **3.3** foi testada com os binários da instalação enviada, **Robocode 1.11.1**, no Java 21. O robô continua em um único arquivo: [robots/equipe/titanprime.java](robots/equipe/titanprime.java).

A bateria final teve **84 séries válidas de três rounds, totalizando 252 rounds**, além de uma tentativa inválida de iniciar a classe auxiliar `Slave`. Foram cobertas as **25 entradas de exemplos** mostradas na lista do jogo. Nem todas são adversários autônomos.

## Confrontos competitivos

| Cenário | Séries vencidas | Rounds vencidos |
| --- | ---: | ---: |
| 16 exemplos autônomos, cada um em duelo | 48/48 | 144/144 |
| Grupos de 2, 3, 6 e 16 adversários | 12/12 | 36/36 |
| Equipe real `sampleteam.MyFirstTeam`, cinco contra um | 3/3 | 8/9 |
| **Total competitivo** | **63/63** | **188/189** |

O Titan venceu o primeiro round das **63 séries competitivas**. A única derrota de round aconteceu no segundo round da equipe, na posição 719; a série terminou 2 a 1. Ganhar pontos não substitui vencer pelo menos dois rounds.

Os grupos comuns são todos contra todos. A equipe usa o arquivo `.team` original, com líder e quatro droids que trocam mensagens; ela aparece como uma única equipe no resultado. O líder começou com 200 de energia e cada droid com 120, contra 100 do Titan.

### Duelo de cada exemplo autônomo

| Adversário | Séries | Rounds |
| --- | ---: | ---: |
| `sample.Corners` | 3/3 | 9/9 |
| `sample.Crazy` | 3/3 | 9/9 |
| `sample.Fire` | 3/3 | 9/9 |
| `sample.MyFirstJuniorRobot` | 3/3 | 9/9 |
| `sample.MyFirstRobot` | 3/3 | 9/9 |
| `sample.PaintingRobot` | 3/3 | 9/9 |
| `sample.RamFire` | 3/3 | 9/9 |
| `sample.SpinBot` | 3/3 | 9/9 |
| `sample.TrackFire` | 3/3 | 9/9 |
| `sample.Tracker` | 3/3 | 9/9 |
| `sample.VelociRobot` | 3/3 | 9/9 |
| `sample.Walls` | 3/3 | 9/9 |
| `sampleex.Alien` | 3/3 | 9/9 |
| `sampleex.AlienComposition` | 3/3 | 9/9 |
| `sampleex.MasterAndSlave` | 3/3 | 9/9 |
| `sampleex.ProxyOfGreyEminence` | 3/3 | 9/9 |

## Outros exemplos e condições especiais

| Entradas | Condição e resultado |
| --- | --- |
| `sample.SittingDuck`, `sample.Target` | Demonstrações passivas, 6/6 séries e 18/18 rounds. Separadas do total competitivo. |
| `sample.Interactive`, `sample.Interactive_v2` | Sem teclado ou mouse neste ambiente. Os 18 rounds terminaram em vitória, mas não medem desempenho contra um jogador humano. |
| `sampleteam.MyFirstDroid`, `sampleteam.MyFirstLeader` | Cada um também foi executado isoladamente, 6/6 séries. O droid depende de mensagens do líder para combater; esses duelos não entram no total competitivo. |
| `samplesentry.BorderGuard` | Três séries com Titan + Walls + sentinela, 9/9 rounds para o Titan. O sentinela começou com 500 de energia e não disputa a vitória normal. Isso não é uma vitória de duelo sobre ele. |
| `sampleex.Slave` | Falha de inicialização confirmada: não tem construtor sem argumentos. É um auxiliar do exemplo `MasterAndSlave`, que foi testado normalmente. A falha não conta como vitória. |

## Correções feitas no Java

O radar agora registra a flag de sentinela em cada inimigo observado. A seleção de alvo não escolhe sentinelas, mas mantém suas observações e ondas para a defesa. Em três cenários equivalentes, a versão anterior direcionou **177 tiros** ao `BorderGuard`; a nova direcionou **zero**. A auditoria conta também tiros durante a animação após o anúncio da vitória.

As estatísticas do round passaram a ser emitidas também em `onDeath`, com proteção contra impressão duplicada. No motor 1.11.1, apenas `onRoundEnded` deixou de registrar um round em que o robô morreu cedo numa execução preliminar. A bateria final registrou os três rounds em todas as 84 séries válidas.

Os pesos neurais e os parâmetros de combate foram preservados. São correções funcionais, sem alegação de melhoria universal: no controle, a equipe perdeu 9/9 rounds; na nova versão, perdeu 8/9, mantendo as três séries vencidas. Houve **cinco turnos perdidos** na bateria final, quatro em duelos e um no cenário com sentinela. Carga da JVM e instrumentação também afetam esses contadores.

O teste de contato com RamFire foi refeito usando a API corrigida: **3/3 séries e 9/9 rounds**, com distância inicial de 100 pixels e nenhum reposicionamento registrado. Dados em [tests/contact-results.json](tests/contact-results.json).

## Posições e limites da comparação

Arena 800 × 600; resfriamento 0,1; inatividade 450; faixa do sentinela 100. Posições planejadas pelas sementes 401, 557 e 719. As sementes escolhem as coordenadas de largada; não fixam toda a aleatoriedade interna do motor ou dos adversários.

A API antiga recebia uma string com coordenadas e não reproduziu os pares X/Y solicitados. Isso foi observado no Robocode 1.11.1 e confirmado num diagnóstico na 1.12.0, registrado em [tests/position-api.json](tests/position-api.json). O treinador e o novo auditor passaram a usar **`RobotSetup[]`**, com conferência das posições realmente executadas.

Na arena de 17 participantes, o motor reposicionou alguns robôs nos rounds seguintes em duas das três séries finais. As coordenadas efetivas de todos os rounds estão nos JSONs, com `positions_verified` e `position_adjustments`. Esses confrontos servem como teste de resistência; não sustentam uma comparação perfeitamente pareada entre versões. Nas outras 82 séries válidas, as posições planejadas foram confirmadas nos três rounds, admitindo o primeiro passo de movimento e rotação.

As primeiras execuções, com a API de string, foram preservadas como **exploratórias**, sem tratá-las como comparação controlada. A falha também limita as comparações históricas que diziam fixar posições: os resultados de batalha continuam sendo resultados observados, mas a equivalência das largadas não estava demonstrada.

Vencer exemplos básicos não estabelece nível de elite. Os testes anteriores contra Diamond mostraram fraqueza; ele não faz parte do total de exemplos deste relatório. O próximo trabalho de defesa deve enfrentar adversários avançados e medir o efeito de cada alteração.

## Repetir os testes

Use uma cópia separada da sua instalação, pois a ferramenta compila clones em `robots/treino`:

```sh
python tools/samples.py --home /caminho/robocode-testes --output /caminho/resultados
```

Sem `javac`, acrescente `--ecj /caminho/ecj.jar`. Para executar só os cenários especiais, use `--include team,sentry`. `--seeds 401,557,719` escolhe as posições. O clone recebe apenas outro nome, marcadores de início e contagem de alvos; não muda a estratégia avaliada.

Os cenários prontos para a interface do jogo são `battles/equipe-cinco.battle`, `battles/todos-autonomos.battle` e `battles/sentinela.battle`. Todos usam três rounds; a interface pode gerar outras posições iniciais.

Verificações:

```sh
python tests/core_checks.py --api /caminho/robocode/libs/robocode.jar
python tests/bo3_checks.py
python tests/trainer_checks.py
python tests/ram_spin_checks.py
python tests/samples_checks.py
```

No primeiro comando também é possível usar `--ecj`. O teste de núcleo verifica a exclusão de sentinelas como alvo e a preservação da observação defensiva; o auditor verifica resultados de equipes, regras de séries, falhas de inicialização e cobertura das 25 entradas.

Dados completos: [bateria final](tests/samples-results.json), [controle anterior](tests/samples-baseline.json), [resumo dos cenários](tests/samples-scenarios.json) e [execuções exploratórias](tests/samples-exploratory.json). Os hashes dos JARs do motor e das classes de exemplo identificam os binários usados. A pasta original enviada não foi publicada no repositório.

A avaliação do texto sobre o TitanD está em [defesa-hibrida.md](defesa-hibrida.md). Os arquivos desse projeto não vieram junto com o texto, portanto não houve comparação direta contra ele.
