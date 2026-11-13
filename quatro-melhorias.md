> Histórico das versões 3.x. O código atual 4.x usa outra base; veja [arquitetura.md](arquitetura.md) e [ciclo-elite.md](ciclo-elite.md).

# Quatro melhorias finais — defesa, melee, energia e aprendizado

Esta etapa completa os quatro itens posteriores ao diagnostico e a mira reativa: defesa estatistica por situacoes semelhantes; posicionamento contra varios inimigos; potencia baseada no retorno dos tiros; e confianca no aprendizado ao longo da batalha. A base de comparacao e o Titan Prime 3.8, SHA-256 `5c73b414f1bd1e9ac33b29e66a33fb0a0801fa9ffd9da7101a1467e7c5b0aa98`.

As implementacoes sao proprias e ficam em classes internas do mesmo Java. Nenhum modelo neural pre-treinado foi substituido. As cores por batalha e a comemoracao foram preservadas; nao foram acrescentados comentarios no inicio do Java.

## 1. Defesa estatistica contextual

`USE_CONTEXT_DEFENSE` habilita um banco limitado a 192 observacoes por adversario. Ele consulta vizinhos usando as caracteristicas da onda, incluindo movimento lateral, aceleracao e curva estimadas a partir do historico proprio, distancia e paredes. A aceleracao e a curva so sao calculadas quando existem os dois estados consecutivos necessarios.

Somente balas associadas a ondas de confianca pelo menos 0,8 e com erro radial limitado alimentam o banco. Impactos no corpo recebem peso 1; colisoes de projeteis, peso 0,6. A previsao e calculada uma vez por onda no planejamento, e reutilizada pelas trajetorias candidatas. O peso maximo e 0,55, reduzido por amostra pequena e pela distancia ate os exemplos conhecidos. Com menos de seis amostras, o controlador mantem o modelo anterior.

Isso nao e uma implementacao do Precise Wave Surfing do Diamond. A mistura de perigo e uma estimativa, limitada pelos erros de deteccao e pelos disparos que conseguimos observar.

## 2. Posicionamento e fogo cruzado

`USE_TACTICAL_MELEE` modifica a avaliacao das posicoes percorridas pela rota simulada, a cada dez ticks. Penaliza a proximidade de regioes incertas quando um inimigo esta sem scan recente, aumenta o risco de ficar entre adversarios e considera o numero de direcoes livres das paredes.

O calculo de fogo cruzado considera no maximo os quatro inimigos mais proximos, para limitar CPU. Usa posicoes previstas, sem conhecer angulos reais de disparo. A incerteza cresce com a idade da observacao e o horizonte, ate um limite. Inimigos fora da janela de observacao existente continuam podendo ficar desconhecidos; nao existe visao perfeita da arena. O modulo atua apenas enquanto ha varios oponentes.

## 3. Potencia por utilidade estimada

`USE_EFFICIENT_FIRE` avalia oito potencias candidatas e a potencia da politica anterior. Um modelo por adversario registra acertos de tiros reais concluidos em tres faixas de distancia e tres faixas de potencia, com reducao do peso dos dados antigos.

A utilidade combina dano limitado pela energia do alvo, probabilidade estimada de acerto, bonus de energia de um acerto, custo do tiro, resfriamento e tempo de voo. O custo da energia aumenta quando a energia propria cai. Potencias com poucas observacoes recebem estimativas suavizadas por um prior; isso nao equivale a conhecer a probabilidade real de acerto.

As reservas, a verificacao de energia, o limite de potencia para finalizar e os limites legais do Robocode continuam sendo aplicados depois da escolha. Tiros sem faixa conhecida nao recebem uma faixa inventada. Eventos repetidos do mesmo projétil nao treinam o modelo novamente.

## 4. Confianca no aprendizado

`USE_PREDICTIVE_TRUST` compara previsoes dos modelos local e geral antes de aumentar ou reduzir a influencia local. As distribuicoes avaliadas ficam congeladas quando a onda e criada; nao sao recalculadas no modelo que ja viu os eventos seguintes. Ondas virtuais sem tiro nao avaliam a confianca da mira. Na defesa, somente ondas de alta confianca podem atualizar essa avaliacao.

A influencia muda gradualmente, respeita amostra minima e limites, e acompanha a perda preditiva recente. A comparacao entre miras virtuais tambem recebe uma penalidade de incerteza para amostras pequenas. As observacoes nao sao independentes; esses numeros sao heuristicas de confianca, nao intervalos de confianca estatistica garantidos.

O aprendizado novo permanece entre os rounds da mesma batalha. Esta etapa nao habilita arquivos de aprendizado persistentes entre batalhas.

## Experimento registrado antes das batalhas

Selecao: base 3.8, cada um dos quatro modulos isolado e os quatro juntos; tres cenarios fortes, duas largadas e oito rounds por batalha. Total previsto: 36 batalhas e 288 rounds. Cenarios: Diamond 1.8.28, SuperMercutio e ambos simultaneamente.

Validacao: base e candidata escolhida, quatro cenarios, tres novas largadas e doze rounds. Total previsto: 24 batalhas e 288 rounds. O quarto cenario e o TitanVingation 1.7 com os mesmos dados recebidos restaurados antes de cada batalha. Nao ha exemplos fracos nessa selecao ou validacao.

Todas as vitorias de round contam igualmente, independentemente da sua ordem. Pontos e melhor de tres nao escolhem a candidata. O desempate da selecao usa menos turnos pulados e menos modulos alterados. A candidata precisa de pelo menos quatro vitorias adicionais nos 144 rounds reservados de cada versao, com todos os casos incluidos validos e no maximo tres turnos pulados adicionais. Os resultados de cada cenario sao reportados, inclusive eventuais regressoes.

Uma falha de execucao permite uma unica repeticao do grupo pareado inteiro daquele cenario e semente, com todas as versoes da fase. As tentativas anteriores permanecem nos dados. Grupos inteiramente validos nunca sao repetidos para trocar derrotas por resultados melhores. Essa regra faz parte do protocolo inicial desta etapa.

Antes da validacao reservada tambem registramos uma verificacao de aplicabilidade: o posicionamento melee isolado so pode ser ativado se melhorar o proprio cenario melee, pois nao muda os comandos no duelo. Diferencas observadas em duelos desse perfil nao serao apresentadas como efeito causal do modulo. As linhas de ativacao precisam comprovar uso dos modulos selecionados.

Cada batalha usa uma JVM nova. A memoria por adversario comeca vazia e os pesos embutidos sao iguais; so o aprendizado dos rounds dessa batalha e compartilhado. As pastas `.data` geradas sao arquivadas antes de cada nova batalha. Somente o caso Vingation recebe uma copia dos arquivos originalmente fornecidos, sem acumular o aprendizado produzido pelo ensaio.

As largadas solicitadas e as posicoes observadas sao preservadas. Ajustes do motor e variacao de escalonamento podem impedir pareamento perfeito mesmo com a mesma semente. As batalhas usam Robocode 1.11.1, Java 21 no servidor, bytecode Java 8, arena 800 x 600 e resfriamento 0,1.

## Ferramentas e verificacoes

```bash
python tools/four.py --source <titanprime-da-3.8.java> --home <robocode> --ecj <ecj.jar> --vingation-jar <titan-vingation.jar> --saved-data <TitanVingation.data> --output <pasta-nova>
python tools/four.py --export /tmp/titanprime.java --profile all
python tests/four_checks.py --api <robocode.jar> --ecj <ecj.jar>
```

O gerador confere o hash da base antiga antes de aplicar os patches. O resultado exportado e um unico Java. Para reproduzir este estudo, recupere o Java da versao 3.8 no commit `f70967b5305de6ddbc242757f4385dcbd0428d42` e use `--source <java-da-base-3.8>`. O Java atual ja e a candidata 3.9; usa-lo como base inicia outro experimento. Os oponentes e os dados externos nao sao redistribuidos no repositorio.

Os testes cobrem distribuicoes condicionais e normalizadas, memoria limitada, diminuicao de confianca em situacoes desconhecidas, simetria geometrica do fogo cruzado, saidas limitadas por paredes, aumento e limite da incerteza, potencias legais, condicionamento por distancia, previsoes congeladas e contabilizacao unica dos tiros reais. O leitor de telemetria aceita explicitamente a quantidade de rounds; as ferramentas historicas mantem o padrao antigo para reproduzir seus resultados.

A linha `UPGRADES` registra consultas e decisoes de cada modulo, alem de contadores de aprendizado. Os contadores de consultas sao do round; os totais aprendidos sao cumulativos entre rounds da batalha e nao devem ser somados como novas amostras. Esses contadores servem para verificar a ativacao, nao para decidir os comandos.

## Resultados e configuracao de uso — Titan Prime 3.9

| Selecao, 48 rounds por perfil | Vitorias | Turnos pulados |
| --- | ---: | ---: |
| Base 3.8 | 19/48 | 0 |
| Defesa contextual isolada | 22/48 | 0 |
| Posicionamento melee isolado | 24/48 | 0 |
| Potencia por utilidade isolada | 21/48 | 1 |
| Confianca preditiva isolada | 19/48 | 3 |
| Os quatro juntos | 24/48 | 2 |

O posicionamento foi escolhido pelo desempate registrado: igualou a combinacao completa em vitorias e teve menos turnos pulados. A combinacao dos quatro nao foi apresentada como superior ao modulo isolado, e os resultados de selecao nao substituem validacao reservada.

| Validacao, 36 rounds por cenario | Base 3.8 | Novo posicionamento |
| --- | ---: | ---: |
| Diamond | 1/36 | 1/36 |
| SuperMercutio | 36/36 | 34/36 |
| Diamond + SuperMercutio | 7/36 | 11/36 |
| TitanVingation com dados recebidos | 27/36 | 30/36 |
| Total | **71/144** | **76/144** |
| Turnos pulados | 0 | 1 |

A candidata passou o limite de quatro vitorias adicionais e teve ganho no proprio melee: quatro rounds a mais. As linhas de ativacao confirmaram o uso do novo avaliador de risco. A versao foi adotada como **Titan Prime 3.9**.

**Somente `USE_TACTICAL_MELEE` fica ativo por padrao entre os quatro recursos novos.** Defesa contextual, potencia por utilidade e confianca preditiva estao implementadas e disponiveis no mesmo Java, mas permanecem experimentais: nao tiveram confirmacao reservada individual nesta etapa. A defesa estatistica, a administracao de energia e o aprendizado anteriores continuam funcionando; os novos ajustes opcionais nao substituem esses sistemas por estarem desligados.

As quatro implementacoes realmente foram exercitadas na selecao, incluindo consultas de perigo contextual, dezenas de milhares de avaliacoes taticas, decisoes de potencia e centenas de avaliacoes de previsoes congeladas. Sua presenca no codigo nao e a unica evidencia de funcionamento. Os testes de componentes tambem habilitam os quatro ao mesmo tempo.

O modulo de melee nao atua em batalhas que ja comecam como duelos. As diferencas contra SuperMercutio e Vingation nao sao atribuidas causalmente a uma melhoria de mira ou defesa: sao resultados observados das execucoes, com nomes de auditoria e escalonamento diferentes. Nao escondemos a perda de dois rounds contra SuperMercutio. A melhora tem tamanho modesto, usa tres largadas reservadas por cenario e nao demonstra superioridade universal. Contra Diamond em duelo continuamos com apenas uma vitoria em 36 rounds.

### Tentativas invalidas e contagem de rounds

Na selecao, uma execucao do perfil melee teve a thread interrompida pelo motor e uma `RobotException`; o grupo inteiro de seis versoes foi repetido uma vez. Na validacao, uma batalha da base contra Vingation terminou com placares, mas faltaram a telemetria e as estatisticas de um dos rounds. Por isso ela foi invalidada para o estudo, e o par inteiro foi repetido uma vez. A causa da thread interrompida nao foi isolada; nao se afirma que toda falha veio exclusivamente do motor.

Foram **68 tentativas nas etapas fortes, 66 validas e 628 rounds validos executados**. Desses, **576 rounds** pertencem aos grupos completos usados na comparacao principal. Os outros 52 rounds validos sao das execucoes originais dos grupos que precisaram ser repetidos; estao preservados, mas nao sao misturados com seus substitutos na comparacao pareada. Os 20 rounds pertencentes as duas tentativas invalidas nao contam como rounds validos ou vitorias do estudo.

Os grupos inteiramente validos nao foram repetidos para melhorar placares. Tres casos incluidos da selecao e um da validacao tiveram ajustes de largada registrados pelo motor. Os JSONs mantem posicoes reais em todos os rounds e erros ou motivos de invalidacao.

Uma verificacao curta adicional, fora da selecao e da validacao, venceu **6/6 rounds**: tres contra a equipe MyFirstTeam, com cinco adversarios, e tres no cenario com Walls e BorderGuard. Nao houve turnos pulados nem tiros direcionados a sentinela. Essa verificacao nao usa melhor de tres para escolher a versao; serve para exercitar varios inimigos e a regra de nao atacar sentinelas. O total desta etapa, incluindo essa verificacao, foi de **634 rounds validos**. Nao foi repetida a bateria inteira dos exemplos fracos.

### Evidencias e verificacoes finais

- `tests/four-results.json`: protocolo, hashes, criterios, resumos e decisao de ativacao.
- `tests/four-selection.json` e `tests/four-validation.json`: todas as tentativas e referencias explicitas aos casos incluidos; originais invalidos ou substituidos permanecem nos arquivos.
- `tests/four-rounds.csv`: telemetria por round, com tentativa, estado e indicador de inclusao na comparacao principal. Ausencia de telemetria nao e preenchida com valores inventados.
- `tests/four-guards.json`: a verificacao curta de equipe e sentinela.

A releitura dos logs verificou que os estados de validade e as vitorias dos 128 registros de tentativas/casos incluidos permaneceram iguais. Os testes de fisica, ondas, estatisticas, mira, KNN, rede neural, telemetria, modelos reativos e quatro recursos novos passaram. Os verificadores legados de catalogo, geracao de variantes e formatos antigos tambem passaram, sem definir o criterio de promocao atual.

O Java entregue e exatamente a candidata testada, SHA-256 `a58850e29d83dde1715f8635294c7674d7874103b625cd521bf9e213bbe33f09`. Os dois blocos de pesos neurais continuam identicos aos da 3.8. As funcoes de cores e comemoracao nao foram alteradas.

Nas estatisticas da potencia experimental, a distancia e a faixa registradas sao as do alvo visado no instante do tiro. Um tiro que atinja outro robo tambem e um acerto entregue; essa medida nao representa uma probabilidade exata de atingir exclusivamente o alvo original.
