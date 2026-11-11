# Estado dos testes — atualização de 11/11/2026

O fonte disponível na main continua sendo a versão 3.10. As reconstruções 4.x estão em desenvolvimento e ainda não foram entregues como versão aprovada. A meta anterior de 10/30 foi substituída pela exigência de todos os rounds vencidos contra Diamond e vantagem em pontos, além de avaliar os demais adversários disponíveis.

## Batalhas completas e válidas

| Candidata | Adversário | Vitórias | Pontos do Titan | Pontos do adversário |
| --- | --- | ---: | ---: | ---: |
| Protótipo 4.0 | ChimpazeComSarna | 44/50 | 5.153 | 1.646 |
| Protótipo neural 4.1 | Diamond 1.8.28 original | 49/50 | 3.636 | 1.733 |

O primeiro lote de 50 contra Chimp teve erro de execução e foi invalidado. A linha acima corresponde à repetição isolada, sem erros. A candidata neural ainda perdeu um round contra Diamond e não atende à meta completa.

Os testes usam Robocode 1.12.0, Java 21 e orçamento de CPU de 50 ms igual para ambos os participantes, em desenvolvimento. As largadas são aleatórias e as posições são registradas. Os números dos lotes não fixam a aleatoriedade interna. Ainda é necessária uma avaliação de entrega com o orçamento normal de processamento.

## Revisão e próxima candidata

A revisão percorreu os CSVs e JSONs disponíveis, preservando versões, resultados desfavoráveis e casos inválidos. Os relatórios históricos se sobrepõem; não devem ser somados como batalhas independentes. Ausência de WinEvent também pode representar empate.

A derrota restante no round 18 ocorreu sem paredes nem turnos perdidos: 30,9 de energia investida, 10,8 de dano nominal causado e 77,1 recebido. Uma candidata separada testa disparos mais espaçados quando a energia fica baixa. Sua eficácia ainda está sendo medida.

As redes neurais aprendem com ondas resolvidas e competem com as miras KNN por resultados virtuais. O pré-treinamento externo separou 5.718 exemplos para treinamento e 1.771 para validação, excluindo a série longa contra Diamond. A melhora do erro estatístico foi pequena e ainda não demonstra melhora em combate.

A reconstrução incorpora diretamente o núcleo aberto Diamond, de Voidious, sob sua licença modificada zlib, com os avisos de origem preservados. Os módulos neurais e as adaptações do projeto não são apresentados como autoria original daquele núcleo.

## Trabalho pendente

- Concluir os 50 rounds da candidata com reserva de energia.
- Avaliar uma mesma candidata contra os 26 adversários autônomos inventariados.
- Tratar equipes, sentinelas e exemplos interativos conforme suas condições próprias.
- Confirmar o desempenho final e empacotar o único Java com todas as classes internas.

O guia PDF fornecido foi lido. Ele orienta o pipeline headless, a telemetria e a diversidade de testes, mas não é evidência de vitória. SpinBot não é estático, e constantes físicas de aceleração não devem ser alteradas como se mudassem as regras do motor.

Dados do registro: [tests/evolution-status.json](tests/evolution-status.json). Nenhum teste finito garante vitória universal contra qualquer estratégia futura.
