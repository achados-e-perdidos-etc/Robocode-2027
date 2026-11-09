# Pontos e vitorias: escolhas de ataque e rotas curtas

A base foi o Titan Prime 3.9, commit `f2249ba0b74d1a2d64df0fa125ecced343cb7d72`. A nova avaliacao considera pontos e vitorias. Uma posicao inicial favoravel ou uma unica gravacao nao define a versao de uso.

## Implementacao e referencias

O TitanV2 do usuario serviu de referencia para estimativa de chance de acerto, valor de alvos e previsao curta de contatos. O ataque candidato combina distancia, movimento e precisao observada; considera dano, finalizacao e tempo para girar o canhao. Sua potencia melee e ajustada por alcance e chance estimada. As reservas de energia e limites de tiro letal da base continuam aplicados. Nao ha acesso a comandos futuros dos adversarios.

A outra candidata reduz o horizonte das rotas melee de 100 para 40 ticks, verifica aproximacao a corpos de 36 x 36 durante os primeiros 20 ticks e reduz o peso das ondas inferidas em melee. As previsoes de contato usam velocidade, limite de giro, paredes e scans recentes. Previsoes ausentes sao rejeitadas; nao ha mudanca no horizonte dos duelos. Esses recursos sao um pacote de movimentacao: o estudo nao isola qual parte causou uma variacao.

As miras, os pesos pre-treinados, o aprendizado por adversario, o radar, as cores e a comemoracao foram preservados. O codigo final continua em um unico `robots/equipe/titanprime.java`.

## Protocolo

Robocode 1.11.1, Java 21, compilacao ECJ com alvo Java 8, arena 800 x 600 e resfriamento 0,1. Cada batalha usa uma JVM nova; dados externos gerados sao arquivados. Os pesos incorporados do Prime sao iguais entre as variantes, e o aprendizado dentro de cada batalha permanece ativo.

A selecao comparou base, ataque, rotas e combinacao em duas arenas e duas largadas de oito rounds: 128 rounds. O ranking combina igualmente a taxa media de vitorias e a parcela media dos pontos por contexto. A parcela de pontos permite comparar arenas com quantidades diferentes de participantes; os pontos absolutos tambem sao apresentados.

| Selecao, 32 rounds por versao | Vitorias | Pontos | Parcela media por contexto |
| --- | ---: | ---: | ---: |
| Base 3.9 | 11/32 | 9545 | 25.15% |
| Ataque | 15/32 | 11221 | 30.71% |
| Rotas | 17/32 | 10879 | 29.33% |
| Combinacao | 8/32 | 8710 | 24.23% |

Rotas foi a candidata escolhida pela formula anterior aos resultados. A validacao usou duas novas largadas de 12 rounds em cinco contextos: 240 rounds comparativos.

| Validacao das rotas, 24 rounds por contexto | Base: vitorias / pontos | Rotas: vitorias / pontos |
| --- | ---: | ---: |
| Sete robos do usuario juntos | 9/24 · 10557 | 10/24 · 10296 |
| TitanV2 + Titans + OBraboBot | 10/24 · 4916 | 11/24 · 4549 |
| Diamond + SuperMercutio | 2/24 · 2397 | 2/24 · 2119 |
| Diamond em duelo | 1/24 · 426 | 0/24 · 388 |
| TitanV2 em duelo | 18/24 · 2388 | 17/24 · 2192 |

Rotas nao foi promovida. Na arena dos sete, a parcela media dos pontos caiu, apesar de um round vencido a mais. O criterio nao foi afrouxado depois dessa observacao.

## Confirmacao independente do ataque

A candidata de ataque tinha melhorado pontos e vitorias na selecao e recebeu uma confirmacao separada: tres largadas ainda nao usadas (17011, 17417, 17827), 12 rounds cada, nas tres arenas melee. Sao mais 216 rounds comparativos; os resultados reservados das rotas nao foram reutilizados para confirmar o ataque.

| Confirmacao, 36 rounds por contexto | Base: vitorias / pontos | Ataque: vitorias / pontos |
| --- | ---: | ---: |
| Sete robos do usuario juntos | 10/36 · 14308 | 18/36 · 15989 |
| TitanV2 + Titans + OBraboBot | 19/36 · 7747 | 16/36 · 7552 |
| Diamond + SuperMercutio | 10/36 · 4636 | 8/36 · 4518 |

Total da confirmacao: base **39/108 vitorias e 26691 pontos**; ataque **42/108 vitorias e 28059 pontos**. Parcela media por contexto: 30.70% para 31.05%.

Para promover: pelo menos quatro vitorias adicionais, ganho relativo de 2% na parcela media dos pontos, preservacao de pontos e vitorias na arena dos sete, todos os placares completos, ao menos 90% das linhas de telemetria em cada versao e no maximo tres turnos pulados adicionais.

## Ataque condicionado a quantidade de inimigos

Como o ataque irrestrito ganhou na arena dos sete e perdeu nas arenas menores, uma terceira candidata so o habilitou enquanto existissem quatro ou mais adversarios, voltando ao ataque original quando restassem tres. Ela foi avaliada em tres largadas novas (18101, 18413, 18719), com 12 rounds por largada e por versao: mais 72 rounds comparativos.

| Arena dos sete, 36 rounds por versao | Vitorias | Pontos |
| --- | ---: | ---: |
| Base 3.9 | 13/36 | 15254 |
| Ataque condicionado | 10/36 | 13857 |

A hipotese nao se confirmou. Um controle adicional de dois rounds por versao, com tres adversarios, terminou sem erros e registrou zero decisoes de ataque por oportunidade, como previsto pelo bloqueio. Esse controle verifica a desativacao, nao demonstra ganho de desempenho. As duas linhas de contador foram observadas.

No estudo anterior do ataque irrestrito, o Prime liderou o acumulado das tres batalhas contra os sete: 15989 pontos e 18 vitorias, frente a 13754 pontos e oito vitorias do segundo colocado por pontos (TitanV2). A base ficou atras de Titan naquele conjunto: 14308 pontos e dez vitorias contra 15196 pontos e 12 vitorias. Essa lideranca do prototipo e real para a amostra, mas nao e uma garantia de vencer cada batalha e nao elimina as quedas nas outras arenas.

## Versao de uso: Titan Prime 3.9

Nenhuma candidata passou todos os criterios reservados de sua confirmacao. As implementacoes ficam disponiveis como experimentais; o Java de uso foi mantido. Os ganhos de selecao nao foram apresentados como melhorias comprovadas.

## Limites e auditoria

A comparacao principal soma **656 rounds** (128 de selecao, 240 das rotas, 216 do ataque e 72 do ataque condicionado), mais **quatro rounds de controle**, antes de eventuais repeticoes por falhas reais. Todas as tentativas sao preservadas. Nenhum placar valido foi repetido para substituir uma derrota. Linhas ausentes de telemetria sao reportadas separadamente e nao transformam um placar oficial completo em derrota. Os totais de ativacao cobrem somente linhas observadas.

Duas execucoes de integracao foram interrompidas antes da bateria comparativa, apos acrescentar uma protecao para previsoes de contato ausentes. Uma delas completou oito rounds apenas da base, preservados fora da comparacao.

Todas as 60 batalhas da comparacao principal terminaram com placares oficiais completos e nenhuma precisou de repeticao. Houve lacunas em algumas linhas de telemetria, preservadas nos arquivos. Os numeros sao amostras de batalhas, nao uma garantia de vencer sempre. As sementes controlam as posicoes iniciais; as decisoes aleatorias internas dos oponentes nao sao fixadas por esse mecanismo. Os duelos mostram que ainda existe uma diferenca grande para Diamond. O Prime entra sozinho, e os outros participantes tambem disputam entre si.

Dados compactos e hashes dos logs: `tests/podium-results.json`, `tests/podium-selection.json`, `tests/podium-validation.json`, `tests/podium-attack-results.json` `tests/podium-attack-confirmation.json`, `tests/podium-crowd-results.json` e `tests/podium-crowd-confirmation.json`. Os scripts geram os logs e registros detalhados locais. Os resultados do controle de tres adversarios estao em `guard_results` do arquivo `podium-crowd-results.json`.

## Reproducao

Recupere o Java da base 3.9 no commit citado acima. A instalacao separada de testes precisa conter os sete adversarios com os namespaces `review.main.*` e `reviewmod.titan.*` usados por `tools/pair.py`, alem de Diamond 1.8.28 e `reference.supermercutio`. Os fontes corrigidos do usuario continuam em `assets/robos-corrigidos.zip`. Compile cada branch separadamente antes da adaptacao dos namespaces.

```sh
python tools/podium.py --home <instalacao-preparada> --ecj <ecj.jar> --source <titanprime-3.9.java> --output <pasta-nova>
python tools/podium_attack.py --home <instalacao-preparada> --ecj <ecj.jar> --source <titanprime-3.9.java> --output <outra-pasta-nova>
python tools/podium_crowd.py --home <instalacao-preparada> --ecj <ecj.jar> --source <titanprime-3.9.java> --output <terceira-pasta-nova>
```

Os scripts exportam `candidate.java.txt`; a geracao confere o hash da base. Para jogar, basta o Java unico de uso.

O melhor prototipo por pontos da selecao tambem esta pronto como um unico arquivo em [assets/titanprime-ataque-experimental.java.txt](assets/titanprime-ataque-experimental.java.txt). O sufixo `.txt` identifica a variante experimental; para compilar, seu nome precisa ser `titanprime.java`. Seu SHA-256 e `a8b3d17dded89e67e87fda20aa74f0893d1cc43eb3a254f943a006c1c338a816`. Ele nao e a configuracao de uso recomendada pelos criterios completos.
