# Revisão das derrotas registradas

A busca foi ampliada para todos os experimentos locais e os arquivos de testes do repositório: 225 CSVs inspecionados, 123 com o formato atual, 893 registros únicos de rounds e 345 JSONs examinados nesta fotografia. Os 31 CSVs de formatos antigos foram revisados separadamente, incluindo os três resultados exportados do PC. Novos rounds ainda em execução não são resultados fechados.

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

## Generalização e recursos neurais

A mesma candidata 4.2 venceu 3/3 e liderou em pontos contra cada um dos 26 adversários autônomos inventariados: 78/78 rounds no conjunto, todos os confrontos válidos. Inclui TitanV3, TitanVingation, ChimpazeComSarna e SuperMercutio, além dos exemplos. Esse rastreio curto não comprova vitória permanente contra esses robôs. A candidata seguinte precisa repetir a avaliação.

O confronto isolado de 50 rounds com ChimpazeComSarna foi válido: 44/50 e 5.153 × 1.646 pontos, usando o protótipo 4.0. Não é resultado da nova candidata.

O pré-treinamento externo separou 5.718 exemplos de treinamento e 1.771 de validação, excluindo a série longa contra Diamond. A entropia cruzada caiu de 4,129 para 4,033; isso ainda não comprova ganho em combate, e esses pesos não estão nos testes atuais. As redes usadas nas batalhas aprendem online.

O orçamento de desenvolvimento foi de 50 ms para ambos os participantes. Falta confirmar uma mesma versão com o orçamento normal de CPU. Nenhum fonte novo foi promovido à main.

Inventários: [registros atuais](tests/elite/all-losses-review.json) e [formatos antigos e resultados do PC](tests/elite/legacy-loss-review.json).
