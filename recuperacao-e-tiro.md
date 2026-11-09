# Recuperacao, tiro bloqueado e confronto com os robos do usuario

O trabalho partiu do Titan Prime 3.9, SHA-256 `a58850e29d83dde1715f8635294c7674d7874103b625cd521bf9e213bbe33f09`. Foram implementadas as duas candidatas indicadas, corrigidos os problemas de compilacao identificados e executadas batalhas com todos os sete robos autonomos das branches recebidas. A configuracao de uso permanece 3.9, pois a candidata nao demonstrou ganho.

## Correcoes de compilacao

- Em OBraboBot.java, a string de `printf` atravessava uma quebra de linha sem fechar as aspas. A correcao usa `%n`, preservando a mensagem e a estrategia.
- Java.java declara a classe publica ChimpazeComSarna. O arquivo corrigido passa a se chamar ChimpazeComSarna.java, sem mudar a identidade da classe.

As seis implementacoes da main e os quinze fontes de producao da branch Titan foram compilados para a API Robocode 1.11.1, UTF-8 e alvo Java 8. A branch modular nao precisava de correcao de compilacao. Os codigos utilizados foram fixados nos commits `70f1af3fc38ff665c7cd5d2a40c99320ba022ba3` (main) e `ebe7079b963bf66fba95175684e07205118e359a` (Titan).

**Entrega:** as fontes corrigidas foram compiladas e as correcoes estao no patch abaixo. A tentativa de gravação direta no repositorio externo nao concluiu; nenhuma atualizacao da branch externa foi solicitada apos isso. Portanto, nao se afirma que a main externa ja esteja corrigida.

As fontes corrigidas estao tambem em [assets/robos-corrigidos.zip](assets/robos-corrigidos.zip), separadas por branch e sem dependencias binarias. Main e Titan devem ser compiladas separadamente, pois possuem implementacoes diferentes com o mesmo nome qualificado.

A correcao reproduzivel esta em [assets/titan-robo-compilacao.patch](assets/titan-robo-compilacao.patch). `git apply --check` e a aplicacao real foram verificados em uma copia temporaria dos arquivos originais; o resultado coincide byte a byte com as fontes corrigidas usadas para compilar. Ela corrige compilacao, sem alegar que todos os problemas de desempenho dos robos foram resolvidos.

Para coexistirem na arena, os seis fontes unicos foram compilados em namespaces separados `review.main.*`, e o modular em `reviewmod.titan`. As alteracoes de namespace e os hashes de cada fonte constam no manifesto. Todos comecam sem dados externos e podem aprender durante os rounds; nao foram fornecidos modelos pre-treinados desses fontes. O Prime mantem seus pesos incorporados. Os JARs anteriores TitanV3 3.0 e Vingation 1.7 nao foram tratados como equivalentes a esses fontes.

## Referencias aproveitadas no prototipo

A recuperacao usa como referencia `updateMobility` e `beginEscape` do Titans.java do usuario: oito observacoes consecutivas sem progresso quando existe intencao de mover, ou tres contatos recentes, podem abrir uma recuperacao limitada a 18 ticks, com cooldown de 40 ticks. Paradas deliberadas e lacunas de observacao nao contam como bloqueio. A recuperacao reutiliza a avaliacao de risco e ondas do Prime, favorecendo sair da regiao do contato.

O filtro de tiro parte da ideia de `blocked`, mas estima a posicao do possivel bloqueador durante o voo, usando a fisica ja existente. Observacoes antigas sao rejeitadas. Se um inimigo recente bloquear a linha, o Prime pode escolhê-lo por alguns ticks e recalcular toda a mira antes de atirar; nao atribui ao novo alvo a onda do alvo antigo. Uma sentinela pode impedir o disparo, mas nao e escolhida como alvo alternativo.

Os dois modulos estao disponiveis no gerador [tools/pair.py](tools/pair.py), com os patches em `tools/pair-prototype.json`. A configuracao ativa do Prime nao foi substituida por eles. O teste de componentes cobre paradas intencionais, bloqueios reais, contatos, cooldown, obstaculos atras do canhao, alem do alvo, fora da linha, em movimento e com scan antigo.

## Protocolo

Selecao: base, recuperacao isolada, filtro isolado e os dois juntos; duas largadas por contexto, oito rounds. Contextos: Diamond, Titans.java e arena com os sete robos recebidos simultaneamente. A selecao usa vitorias em todos os rounds e turnos pulados no desempate, sem prioridade de primeiro round ou melhor de tres.

Validacao: base e candidata escolhida, duas novas largadas, dez rounds em cada um dos sete duelos e na arena conjunta. Sao 160 rounds por versao na comparacao incluida. O Prime participa sozinho; na arena conjunta os sete adversarios tambem se enfrentam, nao sao uma equipe coordenada.

Cada batalha usa uma JVM nova, e dados externos gerados sao arquivados antes da proxima. A regra anterior ao teste permitia repetir uma unica vez o grupo pareado inteiro se houvesse execucao invalida, preservando todas as tentativas. Nenhuma derrota de um grupo inteiramente valido foi repetida para melhorar seu placar.

A promocao exigia quatro vitorias reservadas adicionais, telemetria completa, limite de CPU e evidencia de ativacao dos modulos. Esses criterios nao foram relaxados depois dos resultados. A auditoria posterior separou validade do placar de completude da telemetria, sem transformar a candidata em aprovada.

## Resultados

| Selecao, 48 rounds por versao | Vitorias |
| --- | ---: |
| Prime 3.9 | 22/48 |
| Recuperacao | 17/48 |
| Filtro de tiro | 18/48 |
| Ambos | 21/48 |

Ambos foi o melhor dos prototipos, mas ficou abaixo da base na selecao. A validacao independente confirmou ausencia de ganho.

| Adversario, 20 rounds por versao | Prime 3.9 | Prototipo combinado |
| --- | ---: | ---: |
| ChimpazeComSarna | 16/20 | 15/20 |
| OBraboBot corrigido | 20/20 | 20/20 |
| Titan | 11/20 | 12/20 |
| TitanV2 | 13/20 | 13/20 |
| TitanVingation da main | 18/20 | 18/20 |
| Titans | 12/20 | 13/20 |
| Vingation modular da branch Titan | 19/20 | 15/20 |
| Todos os sete juntos | 10/20 | 7/20 |
| Total com placares completos | **119/160** | **113/160** |

A base venceu mais da metade dos rounds em cada duelo. Contra todos simultaneamente, venceu metade dos rounds. Titan, Titans e TitanV2 foram os desafios mais fortes desses duelos nesta amostra; nomes de edicao ou a presenca de Q-learning nao determinaram a classificacao.

Houve poucas situacoes elegiveis: a recuperacao isolada foi acionada uma vez na selecao; a combinacao nao registrou acionamentos na selecao. Nas linhas disponiveis da validacao combinada, foram observadas oito decisoes de tiro bloqueado e cinco redirecionamentos; as lacunas de telemetria impedem afirmar um total exato desses eventos. Portanto, nao atribuimos toda variacao de placar a esses mecanismos. Nao ha evidencia de ganho que justifique ativa-los por padrao.

## Falhas, telemetria e contagem

A selecao teve 24 batalhas completas, 192 rounds. A validacao executou 38 tentativas por causa das repeticoes de grupos, com uma falha real de execucao do ChimpazeComSarna numa tentativa original. Essa tentativa nao conta como vitoria nem como placar valido.

Algumas batalhas de grupo terminaram com todos os placares e duracoes dos dez rounds, mas faltaram linhas de telemetria do Prime. Foram marcadas como incompletas para o criterio de promocao. Isso nao transforma seus placares oficiais completos em derrotas ou em resultados inexistentes. A tabela anterior usa os placares completos dos 32 casos incluidos apos as repeticoes, e reporta separadamente essa limitacao.

Ao todo foram **62 tentativas**, com **562 rounds de placares completos** e **502 rounds pertencentes a batalhas que passaram todos os requisitos de execucao e telemetria**, incluindo as tentativas originais dos grupos repetidos. Na comparacao principal entram 192 rounds da selecao e 320 da validacao; tentativas substituidas sao preservadas separadamente. Nao se somam substitutos e originais como se fossem novos pares independentes. As posicoes reais e ajustes do motor estao registrados.

Dados: `tests/pair-results.json`, `tests/pair-selection.json` e `tests/pair-validation.json`. `outcome_valid`/`official_wins` identificam o placar; `status` e `validation_reasons` conservam a verificacao mais rigorosa de instrumentacao. O Prime de uso continua com o mesmo hash da 3.9.

## Usar ou reproduzir

```bash
python tools/pair.py --source <titanprime-da-3.9.java> --home <robocode> --ecj <ecj.jar> --repository <pasta-com-main-e-Titan> --output <pasta-nova>
python tools/pair.py --export /tmp/titanprime.java --profile both
python tests/pair_checks.py --api <robocode.jar> --ecj <ecj.jar>
```

A pasta de entrada contem `main/*.java` e `Titan/titan-vingation/src/main/java`. O instalador aplica a correcao do printf e usa o nome publico correto antes de compilar; nao precisa das fontes alteradas remotamente para reproduzir. O exportador gera o prototipo em um unico Java. Ele e experimental e nao e a configuracao recomendada pelos resultados.

As referencias do usuario continuam uteis: a previsao de colisao antes do contato e os snapshots alternados com verificacao de formato merecem estudos separados. O formato desses snapshots nao pode ser copiado diretamente para os modelos de 47 bins do Prime.
