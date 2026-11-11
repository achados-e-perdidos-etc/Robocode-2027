# Revisão das derrotas registradas

A busca foi ampliada para todos os experimentos locais e os arquivos de testes do repositório: 236 CSVs inspecionados, 134 com o formato atual, 1066 registros únicos de rounds e 376 JSONs examinados nesta fotografia. Os 31 CSVs de formatos antigos foram revisados separadamente, incluindo os três resultados exportados do PC. Novos rounds ainda em execução não são resultados fechados.

Os arquivos antigos contêm 205 linhas explicitamente marcadas como LOSS, após remover arquivos com conteúdo idêntico. Há sobreposição entre trechos de batalhas e diagnósticos de TitanVingation e outros robôs; esse número não é uma contagem de derrotas do TitanPrime atual. Campos anteriores à correção da ordem de WinEvent não certificam derrotas. Nos resultados do PC, a versão antiga fez 0/30 contra Diamond, 28/30 contra TitanV3 e 10/30 no confronto triplo.

Ausência de WinEvent pode incluir empate ou mortes simultâneas. Casos sem fonte identificado ou dados completos permanecem limitados, e batalhas com falha não são creditadas como vitórias.

## Diagnóstico e alterações

| Evidência | Alteração experimental | Verificação |
| --- | --- | --- |
| 86/86 registros sem WinEvent da versão 3.10 contra Diamond gastaram mais energia em tiros do que causaram de dano nominal | Potência econômica e comparação de miras virtuais, incluindo duas redes neurais online | Candidata neural: 49/50 válidos, 3.636 × 1.733 pontos; ainda perde um round |
| A derrota da candidata neural no round 18 teve 30,9 gastos, 10,8 de dano nominal causado e 77,1 recebido, sem parede ou turno perdido | Reserva de energia de 18 e intervalo mínimo de 44 ticks em condições de baixa energia | Candidata 4.2: 49/50 válidos, 3.565 × 1.878; não resolveu a derrota restante |
| A derrota seguinte ocorreu no round 49, com 19,1 gastos, 4,4 de dano nominal causado e 79,8 recebido | Reserva de 30, intervalo de 66 ticks e proteção mantida enquanto o adversário tem energia positiva | Variante de potência 0,1 teve falha do motor no Diamond: série inválida. Variante 0,3 válida: 45/50 conservadoras, 3.533 × 2.182; pior desempenho, não aprovada |
| Pesos de distância KNN e larguras de interseção podem degenerar; fallback Melee pode ficar sem destino válido | Limites numéricos, perigo alternativo finito e frenagem com laço limitado | Compilação e verificações de física/KNN/fallback; confirmar no candidato final |
| Batalhas iniciadas em Melee calculavam GuessFactor zero mesmo depois de restar apenas um inimigo | Usar a quantidade de inimigos no instante da onda ao registrar o deslocamento do duelo final | Regressão confirma a falha original e a correção em três cenários; integração de batalha pendente, sem promoção |

A última derrota observada da variante 0,1 envolveu 90,0 de dano nominal recebido em 931 ticks. Aumentar apenas a reserva de tiros não resolve esse padrão de defesa. Como a série inteira teve falha de execução do oponente, os totais de vitórias e pontos foram descartados da avaliação de desempenho.

As regressões também confirmaram dois problemas adicionais: selecionar uma onda mais lenta pela ordem dos disparos e reutilizar vizinhos KNN de outra onda no mesmo índice. A candidata corrigida ordena por tempo estimado de impacto e vincula o cache à identidade da onda. O cenário de teste usa uma bala de potência 3 disparada no tick 0 e outra de potência 0,1 no tick 16; no tick 20 a segunda já é a ameaça que chega primeiro. A base antiga falha nas duas verificações, e a correção passa.

Foi confirmado ainda que DeathEvent pode congelar o CSV antes dos impactos terminais do mesmo tick. A correção preserva esses impactos e solicita a atualização do registro, sem incluir eventos posteriores ao encerramento. O teste isolado dos contadores passou; a integração de persistência no motor continua necessária. Os totais nominais antigos podem omitir esses eventos finais e não devem ser tratados como perda líquida de energia nem pontuação oficial. As vitórias oficiais não são alteradas por essa correção.

A variante de três ondas concluiu uma série válida em 49/50, com 3.603 × 1.910 pontos; não passou a meta. A variante pré-treinada seguinte concluiu 50/50 válidos, com 3.673 × 1.738. A candidata final corrigida iniciou a qualificação com orçamento de referência de 10,03 ms para ambos; o resultado da candidata anterior não certifica automaticamente esse novo fonte. A qualificação final incluirá as correções de transição, ordenação, cache e telemetria, com nova verificação do fonte efetivamente testado.

Regressões: [transição para duelo](tests/elite/late-duel-regression.json), [ordenação e cache](tests/elite/temporal-regression.json), [eventos terminais](tests/elite/terminal-regression.json). Comparação das séries completas: [gráfico](tests/elite/defeat-comparison.svg) e [dados](tests/elite/defeat-comparison.json).

## Generalização e recursos neurais

A mesma candidata 4.2 venceu 3/3 e liderou em pontos contra cada um dos 26 adversários autônomos inventariados: 78/78 rounds no conjunto, todos os confrontos válidos. Inclui TitanV3, TitanVingation, ChimpazeComSarna e SuperMercutio, além dos exemplos. Esse rastreio curto não comprova vitória permanente contra esses robôs. A candidata seguinte precisa repetir a avaliação.

O confronto isolado de 50 rounds com ChimpazeComSarna foi válido: 44/50 e 5.153 × 1.646 pontos, usando o protótipo 4.0. Não é resultado da nova candidata.

O pré-treinamento externo separou 5.718 exemplos de treinamento e 1.771 de validação, excluindo a série longa contra Diamond. A entropia cruzada caiu de 4,129 para 4,033; isso ainda não comprova ganho em combate, A variante pré-treinada venceu a série de 50 rounds, com largadas diferentes das demais candidatas; isso não isola um efeito causal dos pesos. Esses pesos estão nessa variante e na candidata final, que também continuam aprendendo online.

O orçamento de desenvolvimento foi de 50 ms para ambos os participantes. Falta confirmar uma mesma versão com o orçamento normal de CPU. Nenhum fonte novo foi promovido à main.

Inventários: [registros atuais](tests/elite/all-losses-review.json) e [formatos antigos e resultados do PC](tests/elite/legacy-loss-review.json).
