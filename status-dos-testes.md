# Estado dos testes — atualização de 11/11/2026

O fonte disponível na main continua sendo a versão 3.10. As reconstruções 4.x estão em desenvolvimento e ainda não foram entregues como versão aprovada. A meta anterior de 10/30 foi substituída pela exigência de todos os rounds vencidos contra Diamond e vantagem em pontos, além de avaliar os demais adversários disponíveis.

## Batalhas completas e válidas

| Candidata | Adversário | Vitórias | Pontos do Titan | Pontos do adversário |
| --- | --- | ---: | ---: | ---: |
| Protótipo 4.0 | ChimpazeComSarna | 44/50 | 5.153 | 1.646 |
| Protótipo neural 4.1 | Diamond 1.8.28 original | 49/50 | 3.636 | 1.733 |
| Protótipo 4.2 com reserva de energia | Diamond 1.8.28 original | 49/50 | 3.565 | 1.878 |
| Protótipo 4.3 com reserva maior e potência 0,3 | Diamond 1.8.28 original | 45/50 conservadoras | 3.533 | 2.182 |

O primeiro lote de 50 contra Chimp teve erro de execução e foi invalidado. A linha acima corresponde à repetição isolada, sem erros. A candidata neural ainda perdeu um round contra Diamond e não atende à meta completa.

Os testes usam Robocode 1.12.0, Java 21 e orçamento de CPU de 50 ms igual para ambos os participantes, em desenvolvimento. As largadas são aleatórias e as posições são registradas. Os números dos lotes não fixam a aleatoriedade interna. Ainda é necessária uma avaliação de entrega com o orçamento normal de processamento.

## Revisão e próxima candidata

A revisão percorreu os CSVs e JSONs disponíveis, preservando versões, resultados desfavoráveis e casos inválidos. Os relatórios históricos se sobrepõem; não devem ser somados como batalhas independentes. Ausência de WinEvent também pode representar empate.

A derrota restante no round 18 ocorreu sem paredes nem turnos perdidos: 30,9 de energia investida, 10,8 de dano nominal causado e 77,1 recebido. A candidata 4.2 com disparos mais espaçados também fez 49/50 e não passou a meta. Ela venceu 3/3 e liderou os pontos contra cada um dos 26 adversários do catálogo: 78/78 no rastreio curto, em confrontos válidos.

A série seguinte com reserva maior teve uma falha de execução no Diamond e foi descartada. A variante de potência 0,3 concluiu uma série válida, mas regrediu: 45 vitórias conservadoras em 50. O motor atribuiu 46 primeiros lugares ao Titan e cinco ao Diamond, incluindo um primeiro simultâneo; ele não conta como vitória exclusiva. A próxima etapa compara planejamento de três ondas, distância de combate e pesos neurais pré-treinados separadamente.

A [revisão ampliada das derrotas](revisao-das-derrotas.md) inclui os formatos antigos e os resultados do PC. Foi confirmado e corrigido em teste de regressão um erro de GuessFactor na passagem de Melee para duelo; falta integração na candidata final.

As redes neurais aprendem com ondas resolvidas e competem com as miras KNN por resultados virtuais. O pré-treinamento externo separou 5.718 exemplos para treinamento e 1.771 para validação, excluindo a série longa contra Diamond. A melhora do erro estatístico foi pequena e ainda não demonstra melhora em combate.

A reconstrução incorpora diretamente o núcleo aberto Diamond, de Voidious, sob sua licença modificada zlib, com os avisos de origem preservados. Os módulos neurais e as adaptações do projeto não são apresentados como autoria original daquele núcleo.

## Trabalho pendente

- Concluir a comparação das variantes de defesa e mira.
- Repetir os 26 confrontos com a candidata final.
- Tratar equipes, sentinelas e exemplos interativos conforme suas condições próprias.
- Confirmar o desempenho final e empacotar o único Java com todas as classes internas.

O guia PDF fornecido foi lido. Ele orienta o pipeline headless, a telemetria e a diversidade de testes, mas não é evidência de vitória. SpinBot não é estático, e constantes físicas de aceleração não devem ser alteradas como se mudassem as regras do motor.

Dados do registro: [tests/evolution-status.json](tests/evolution-status.json). Nenhum teste finito garante vitória universal contra qualquer estratégia futura.

