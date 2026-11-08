# Diagnostico e mira reativa — primeiro par de melhorias

O trabalho tem dois objetivos: medir o ataque, a exposicao a tiros e a energia contra Diamond; e construir uma mira que aprenda respostas observadas apos disparos reais. Defesa, radar, potencia dos tiros, finalizacao, cores e modelos neurais nao foram ajustados nesta etapa.

## Diagnostico inicial

Nos nove rounds da bateria remota anterior contra Diamond, o Titan disparou 614 vezes e acertou 51 (8,3%). Gastou 572,94 de energia nos tiros, causou 215,54 de dano nominal e recebeu 463,89. Foram aproximadamente 0,376 de dano nominal por unidade de energia gasta. O intervalo medio entre scans foi 8569/8564 ticks, praticamente um tick; a soma das idades dos scans usados nos 614 tiros foi um tick.

Isso prioriza investigar a eficacia da mira e o retorno dos disparos. Nao demonstra que radar e defesa estejam perfeitos. As 97 associacoes de impactos recebidos com ondas comprovam associacao de eventos, nao previsao correta do angulo inimigo. Dano nominal nao desconta sobre-dano nem constitui uma contabilidade completa de energia. Os registros abrangem os eventos entregues ate o relatorio do round.

A evidencia original esta em `tests/remote-elite.json`, da versao do commit `59b0969d49371a11a6369a24a4d813cdb2d72de4`. Nenhuma alteracao no robo ocorreu entre esse diagnostico e a base da comparacao.

## Mira implementada

A mira reativa anterior calculava uma media de deslocamentos futuros. Respostas opostas podem se cancelar nessa media, produzindo um ponto no meio. A candidata usa uma distribuicao angular com multiplas regioes de probabilidade e seleciona uma regiao considerando a largura angular do alvo.

O banco e independente por adversario, limitado a 384 observacoes e treinado somente por ondas associadas a tiros reais disparados durante duelos. Visitas sem tiro, ondas de melee e scans com lacunas nao confiaveis ficam fora. Os dados atravessam rounds da mesma batalha, sem persistencia em disco.

As 16 caracteristicas incluem as 12 ja usadas pela mira estatistica — distancia, velocidades, aceleracao, curva, paredes, voo da bala e contexto — mais tempo desde a reversao lateral observada, desde a mudanca de velocidade, desde o ultimo tiro proprio contra aquele alvo e velocidade assinada em relacao a direcao lateral. Os tempos iniciais sao estimativas censuradas: nao conhecemos o movimento anterior ao primeiro scan. A associacao a um tiro nao prova que o inimigo reagiu por causa dele.

Duas visoes de vizinhos, historico de ate 384 e janela recente de 48, combinam densidades sem tirar a media dos angulos. A rede neural, os modelos anteriores e a selecao das sete miras existentes sao preservados. A nova mira ocupa o indice 9; as outras miras experimentais continuam desligadas.

## Protocolo de avaliacao

Robocode 1.11.1, Java 21 no servidor, bytecode Java 8, arena 800 x 600, tres rounds. Vence quem conquista pelo menos dois rounds. Modelos pre-treinados iguais, memoria por adversario vazia a cada batalha independente e aprendizado permitido entre os tres rounds.

Quatro de cinco cenarios sao competitivos: Diamond, SuperMercutio, espelho da base atual e Diamond com SuperMercutio simultaneamente. RamFire e apenas um controle de regressao, 20% das series. O espelho e uma comparacao com a propria versao, nao um terceiro robo de elite externo. No melee o Titan entra sozinho; os outros dois tambem se enfrentam.

A primeira candidata usa limites de escape simulados e selecao por pontuacao virtual sem margem adicional. Selecao: sementes 7001 e 7211; validacao: 7481, 7691 e 7907. Depois de observar a selecao desfavoravel foi definida uma segunda candidata, sem reutilizar essas largadas na sua confirmacao: sementes 8123, 8353 e 8597. Ela remove o recorte por limites aproximados de escape, conserva o limite teorico de GuessFactors e exige oito avaliacoes virtuais da mira nova, alem de vantagem de 0,03 na estimativa de acerto, antes de seleciona-la.

A regra registrada antes de cada confirmacao exige mais series reservadas, nenhuma regressao por contexto, melhora de series contra Diamond ou no melee forte e no maximo tres turnos pulados adicionais. Uma execucao invalida impede promocao. Aumentar a precisao sem vencer mais series nao basta. Nao se escolhem parametros depois de olhar o resultado da confirmacao da mesma candidata.

Os disparos informados como `reactive_selections` sao selecoes de mira no tick do disparo. Uma solucao angular pode estar retida do tick anterior, por ate dois ticks; o contador nao e uma atribuicao exata de cada angulo disparado. Os hashes de fonte e candidata e as posicoes reais estao nos JSONs.

## Reproduzir e exportar

```bash
python tools/reactive.py --home <robocode> --ecj <ecj.jar> --output <pasta-externa>
python tools/reactive.py --conservative --home <robocode> --ecj <ecj.jar> --output <outra-pasta-externa>
python tools/reactive.py --export /tmp/titanprime.java --conservative
```

Os oponentes precisam estar instalados separadamente, incluindo `reference.supermercutio*`. O exportador gera um unico Java. `--parse-only` reprocessa os logs e rejeita fonte ou protocolo diferentes. Falhas de execucao permanecem nos dados sem contar como vitorias.

`tests/reactive_checks.py` exercita respostas opostas sem cancelar a previsao para o centro, condicionamento por reversao, copia das caracteristicas, memoria limitada, exclusao de visitas sem tiro, exclusao de melee e rejeicao de scans com lacunas. O parametro `--conservative` compila e verifica a segunda candidata.

## Resultados e decisao — Titan Prime 3.8

A primeira candidata venceu 4/10 series na selecao, contra 7/10 da base. Na validacao teve 8/14 series validas contra 8/15 da base. Uma partida da candidata contra Diamond + SuperMercutio foi invalidada quando Diamond parou de agir e o motor nao conseguiu encerrar sua thread. Os denominadores sao diferentes; essa comparacao nao autorizou promocao. Dados: `tests/reactive-first.json`.

A segunda candidata teve inicialmente 8/14 contra 6/15 series validas e venceu uma serie no melee forte. A partida invalida foi contra o espelho, cuja thread nao encerrou. O lote foi preservado em `tests/reactive-conservative-initial.json` e repetido por inteiro, sem alterar a candidata. Essa repeticao teve outra falha do motor, desta vez SuperMercutio na base; ficou em `tests/reactive-confirmation-original.json`.

Para completar a comparacao, repetimos uma unica vez o par afetado nessa confirmacao, em ambas as versoes e com a mesma semente 8597. A origem, os hashes, os dois resultados novos e a composicao final constam em `tests/reactive-confirmation.json`. O lote original permanece invalido e nao foi promovido isoladamente. A recuperacao por par foi definida apos a falha, nao fazia parte do protocolo inicial. Somente um par com execucao invalida pode ser repetido; derrotas validas nunca sao elegiveis para substituicao. O resultado original valido da candidata nesse par tambem foi preservado, separado do resultado incluido na comparacao final.

| Confirmacao com 15 pares completos | Base 3.7 | Candidata conservadora |
| --- | ---: | ---: |
| Series vencidas | 7/15 | 9/15 |
| Rounds vencidos | 23/45 | 26/45 |
| Diamond | 0/3 series, 1/9 rounds | 1/3 series, 2/9 rounds |
| SuperMercutio | 3/3 series | 3/3 series |
| Espelho da base | 1/3 series | 2/3 series |
| Diamond + SuperMercutio | 0/3 series, 2/9 rounds | 0/3 series, 1/9 rounds |
| RamFire, controle | 3/3 series | 3/3 series |
| Turnos pulados | 1 | 0 |

A candidata foi adotada como **Titan Prime 3.8**, com `USE_REACTIVE_GUN=true`, mantendo `USE_EXTENDED_GUNS=false`. Ela passou os criterios de series e turnos no conjunto completo obtido com a recuperacao do par. No melee forte continuou sem vencer series e perdeu um round a mais. Nao houve melhora universal, e uma vitoria em tres series contra Diamond ainda e evidencia pequena. Nao repetimos lotes adicionais para buscar resultados melhores.

### Diagnostico de Diamond na confirmacao

| Indicador, nove rounds de cada versao | Base | Candidata |
| --- | ---: | ---: |
| Tiros | 549 | 690 |
| Acertos | 34 | 63 |
| Precisao observada | 6,2% | 9,1% |
| Energia gasta em tiros | 581,34 | 632,72 |
| Dano nominal causado | 141,13 | 224,95 |
| Dano nominal por energia gasta | 0,243 | 0,356 |
| Dano nominal recebido | 375,12 | 399,32 |
| Intervalo medio entre scans | aproximadamente 1 tick | aproximadamente 1 tick |
| Idade media do scan ao disparar | 0 | 0 |

A mira nova foi selecionada em 55 ticks de disparo contra Diamond. O retorno nominal dos tiros melhorou nessa amostra; o gasto e o dano recebido totais aumentaram. A duracao e as trajetorias das batalhas tambem mudam, portanto nao se interpreta essa tabela como isolamento causal perfeito da defesa.

Foram **112 tentativas, 109 series validas e 327 rounds validos**, contando selecao, validacoes e o par repetido. Os tres casos invalidos permanecem nos JSONs e nao contam como vitorias. Quatro de cinco cenarios eram competitivos, com um unico exemplo basico como controle. Posicoes observadas e ajustes do motor estao nos registros; nao se afirma pareamento perfeito de todas as largadas.

O Java de uso e exatamente o da candidata congelada, hash SHA-256 `5c73b414f1bd1e9ac33b29e66a33fb0a0801fa9ffd9da7101a1467e7c5b0aa98`. Para reproduzir os estudos, passe `--source <java-original-da-3.7>` aos comandos, recuperando esse arquivo no commit `0202c3dff89c5a16c592456d9e1d48bbb4391918`. A versao atual ja inclui a mudanca e nao equivale a base antiga. Os modelos neurais embutidos foram preservados byte a byte; nao foram adicionados comentarios no inicio do Java.

A recuperacao do par pode ser reproduzida com `tools/reactive_repair.py --original <results.json-original> --source <java-original-da-3.7> --home <robocode> --ecj <ecj.jar> --output <pasta-nova>`. A ferramenta confere os hashes das duas fontes e recusa substituir pares que so tenham partidas validas.
