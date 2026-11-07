# Treinos contra RamFire e SpinBot

> **Correção de metodologia:** este relatório histórico usou a API de posições como string. O diagnóstico posterior mostrou que ela não reproduziu os pares X/Y solicitados. O cenário descrito como “100 pixels” não teve essa distância confirmada, e a diferença de 4,77% não demonstra uma melhoria com largadas pareadas. Os resultados originais foram preservados. O teste foi refeito com a API corrigida na versão 3.3: **3/3 séries e 9/9 rounds contra RamFire a 100 pixels**, sem reposicionamento registrado. Veja [robos-de-exemplo.md](robos-de-exemplo.md) e [tests/contact-results.json](tests/contact-results.json).

Foram executados **56 séries de três rounds, totalizando 168 rounds**, no motor real do Robocode. O objetivo principal foi vencer pelo menos dois rounds por série. O código de produção do Titan Prime foi **mantido**, porque a candidata não atingiu todos os critérios de promoção definidos antes dos resultados.

## O que foi testado

- Duelo contra `sample.RamFire`, com início distante.
- Duelo contra RamFire com **100 pixels de separação solicitados, não confirmados**, robôs inicialmente voltados um para o outro. Esse é um caso de estresse para aproximação e colisões.
- Duelo contra `sample.SpinBot`.
- Arena com os dois adversários ao mesmo tempo, todos contra todos.

Os inícios próximos foram configurados pela API do motor; os arquivos `.battle` comuns usam as posições que o jogo gerar. Cada série começa com o modelo incorporado e memória nova de batalha. O aprendizado normal continua entre os seus três rounds.

## Ajuste de parâmetros e treino neural

Três perfis foram comparados em duas posições por contexto, somando 24 séries / 72 rounds:

| Perfil | Distância desejada de duelo | Multiplicador de potência | Séries vencidas | Ticks totais |
| --- | ---: | ---: | ---: | ---: |
| Atual | 441,29 | 1,0094 | 8/8 | 10.163 |
| Mais agressivo | 350 | 1,10 | 8/8 | 8.824 |
| Maior distância | 510 | 0,85 | 8/8 | 10.735 |

Em empate de séries, primeiros rounds e total de rounds vencidos, o teste usou duração como desempate. Foi selecionado o perfil mais agressivo. Isso é uma busca pequena de parâmetros, não um novo ciclo genético completo.

Depois, houve quatro séries de pré-treino de três rounds, transferindo os pesos gerais entre elas: **12 rounds adicionais**. A rede de mira passou de 1.228 para **1.368 observações**; a de defesa, de 128 para **129**. Os pesos da candidata foram guardados no JSON do experimento, sem ativá-los no Java principal.

## Avaliação em posições reservadas

Foram usadas três posições novas por contexto e versão: 24 séries / 72 rounds. Cada linha contém três séries, ou nove rounds, por versão. Os ticks são a soma das durações oficiais dos rounds, não tempo de CPU nem tempo de relógio.

| Contexto | Atual: séries | Atual: rounds | Candidata: séries | Atual: ticks | Candidata: ticks |
| --- | ---: | ---: | ---: | ---: | ---: |
| RamFire — início distante | 3/3 | 9/9 | 3/3 | 3268 | 3070 |
| RamFire — início a 100 pixels | 3/3 | 9/9 | 3/3 | 3164 | 3078 |
| SpinBot | 3/3 | 9/9 | 3/3 | 3332 | 3126 |
| RamFire + SpinBot | 3/3 | 9/9 | 3/3 | 5104 | 4885 |

Tanto o código atual quanto a candidata venceram **12/12 séries e 36/36 rounds**, inclusive todos os primeiros rounds. Não houve aumento de vitórias nessa avaliação.

A duração total passou de **14.868 para 14.159 ticks**, redução de **4,77%**. A pontuação relativa média caiu de **94,11% para 91,39%**, principalmente na arena com os dois adversários. Ganhar a série tem prioridade sobre pontos, mas a redução reforça que uma configuração mais agressiva envolve trocas, não uma melhora em tudo.

## Verificação adicional e decisão

Foram executadas ainda quatro séries / 12 rounds: versão atual e candidata contra Walls e Diamond. Ambas venceram Walls e perderam a série contra Diamond. A pontuação relativa no duelo Diamond foi 13,81% no controle e 17,38% na candidata. Um único lote não demonstra melhora geral contra esse adversário.

Os critérios definidos antes da avaliação foram: não perder séries em nenhum dos quatro contextos, preservar o primeiro round, preservar os resultados dos testes adicionais e ganhar mais séries **ou reduzir pelo menos 5% dos ticks em caso de empate**. A candidata passou nos três primeiros critérios e ficou em **4,77%**, abaixo do último. Por isso, o Java de produção não foi substituído. Não ajustamos a meta depois de ver os números.

## Ambiente e limites

Robocode clássico 1.12.0, OpenJDK 21.0.13, compilador ECJ 3.37.0 com alvo Java 8, arena 800 × 600, resfriamento 0,1. Seleção: posições 31 e 67. Pré-treino: posição 83. Avaliação reservada: posições 131, 197 e 263. Verificações adicionais: posição 307.

Os scripts solicitavam a repetição das posições nos três rounds, mas não verificaram as coordenadas efetivas. A aleatoriedade interna dos adversários e a carga da JVM não foram completamente fixadas. A comparação de duração é entre esses lotes, sem intervalo de confiança. RamFire e SpinBot são robôs de exemplo; vencê-los não demonstra força contra robôs de elite. A candidata alterou parâmetros e pesos juntos, sem isolar o efeito de cada um.

## Repetir no computador

Os cenários de três rounds estão em `battles/duelo-ramfire.battle`, `battles/duelo-spinbot.battle` e `battles/ramfire-spinbot.battle`. No Robocode, também é possível abrir **Battle > New**, selecionar `equipe.titanprime` e os adversários e configurar **3 rounds**.

Para repetir o treino em uma instalação separada:

```sh
python tools/ram_spin.py --home /caminho/robocode-testes
```

Acrescente `--ecj /caminho/ecj.jar` se não houver `javac`. Python usa somente a biblioteca padrão; o jogador não precisa de Python para usar o robô. A ferramenta não publica nem promove automaticamente a candidata.

Dados: [tests/ram-spin-results.json](tests/ram-spin-results.json), incluindo os pesos neurais experimentais e hashes dos arquivos; [tests/ram-spin-scenarios.json](tests/ram-spin-scenarios.json), com participantes e posições iniciais. Verificação dos inícios próximos e da regra de seleção: `python tests/ram_spin_checks.py`.
