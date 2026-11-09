# Batalhas no computador do usuario

Resultados enviados pelo usuario em 9 de novembro de 2026. Sao tres placares de 30 rounds, totalizando 90 rounds de partidas. Nao sao novos testes no ambiente de simulacao remoto. O nome `equipe.titanprime*` nao comprova a versao exata do fonte instalado; nao atribuir automaticamente estes resultados ao hash da 3.9 ou ao prototipo experimental.

| Confronto | Pontos do Prime | Vitorias do Prime | Resultado |
| --- | ---: | ---: | --- |
| Diamond 1.8.28 | 434 de 4368 (9,94%) | 0/30 | 2o; Diamond venceu os 30 |
| TitanV3 3.0 | 3151 de 4135 (76,20%) | 28/30 | 1o no CSV; comparacao afetada por aviso de inicializacao |
| Diamond + TitanV3 | 3688 de 10701 (34,46%) | 10/30 | 2o; Diamond 18 vitorias, TitanV3 duas |

## O que os dados sustentam

Contra Diamond em duelo, o Prime causou 434 pontos de dano por bala, contra 1779 de Diamond: cerca de 24,4% do dano do adversario. O objetivo imediato e entender a baixa producao ofensiva e a exposicao aos tiros. Os CSVs nao informam quantidade de disparos, precisao, ondas associadas, rotas ou turnos pulados; nao permitem determinar qual desses mecanismos causou a derrota.

Na arena com tres participantes, Diamond marcou 4781 pontos contra 3688 do Prime, uma diferenca de 1093. A diferenca de dano por bala foi 597 (2098 contra 1501), mais da metade desse intervalo. Melhorar o dano ofensivo e uma prioridade concreta, junto da sobrevivencia. As parcelas Survival/Survival Bonus diferiram em 250/160 pontos. A coluna Bullet Bonus e bonus de pontuacao por eliminacao; nao e a energia recuperada a cada acerto.

O resultado de 28/30 contra TitanV3 e o placar exportado, mas deve ser tratado com ressalva. O console registra: `titan.TitanV3 3.0 still has not started after 3009 ms... giving up.` Isso indica falha de inicializacao em pelo menos uma ocorrencia e pode ter facilitado uma vitoria. O aviso aparece no segundo bloco de 30 rounds; a associacao com o segundo CSV segue a ordem aparente, pois o log nao traz IDs de partida. Nao ha evidencia de que TitanV3 ficou desativado em todos os rounds.

## Limites dos logs e videos

Tres textos colados sao identicos; o quarto termina um pouco antes. Todos repetem tres blocos de 30 inicializacoes e o mesmo aviso de TitanV3. Eles representam a mesma sequencia, nao quatro baterias adicionais. O texto e do console geral: nao contem `TitanPrime round=`, `TELEMETRY`, `GUN_USAGE` ou contadores de `skipped`. A ausencia desses dados nao prova ausencia de turnos pulados.

Os quatro links enviados depois abriram corretamente. Os arquivos foram baixados e examinados por amostras visuais ao longo das gravacoes; nao se afirma assistir continuamente a todos os frames. `IMG_0420.MOV` (105,88 s) e `IMG_0421.MOV` (10,11 s) mostram o duelo com Diamond; `IMG_0422.MOV` (80,59 s) mostra TitanV3; `IMG_0423.MOV` (167,02 s) mostra os tres participantes. Os instantes amostrados estao em `tests/pc/videos-analise.json`. Os videos e suas URLs privadas nao foram copiados para o repositorio.

Nos trechos de Diamond, o Prime muda de posicao e aparece frequentemente proximo das bordas. Isso sugere testar controle de distancia e preservacao de espaco lateral em duelos, mas nao mede colisao nem prova travamento. A velocidade do simulador e alterada nas gravacoes; uma filmagem da tela nao permite atribuir turnos pulados a CPU ou medir acertos com confianca.

No video de tres participantes, aos 25,1 s e 50,1 s restam Prime e Diamond no primeiro round. Perto de 50,1 s, os rotulos visiveis mostram Prime com 40,0 de energia e Diamond com 1,1. Essa situacao fornece um caso concreto para verificar finalizacao de inimigos com pouca energia. Nao se conclui, so por esse frame, que a mira travou ou que o alvo venceu posteriormente. Os frames de TitanV3 mostram o adversario ativo em varios momentos; a falha de inicializacao registrada nao deve ser estendida aos 30 rounds inteiros.

As proximas duas linhas de trabalho indicadas sao: (1) diagnosticar mira, cadencia e finalizacao contra adversarios evasivos; (2) medir correspondencia de ondas e posicionamento em duelos, incluindo a transicao de melee para um inimigo restante. Esses sao candidatos a testes, nao correcoes de causa ja comprovada.

Para fechar o diagnostico, falta o console individual do Prime (botao com o nome do robo, copiar a saida dele). Tambem e preciso confirmar qual fonte foi compilado antes de comparar diretamente com os estudos remotos. Repetir a partida com TitanV3 so e necessario depois de verificar sua inicializacao. Nao e preciso repetir todos os testes fracos.

Fontes preservadas em `tests/pc`: tres CSVs originais, um console geral sem duplicatas e `resultados.json`. Nenhuma estrategia ou configuracao ativa do Java foi alterada com base apenas nestes placares.
