> Histórico das versões 3.x. O código atual 4.x usa outra base; veja [arquitetura.md](arquitetura.md) e [ciclo-elite.md](ciclo-elite.md).

# Telemetria do Titan Prime 3.6

A versão 3.6 acrescenta observabilidade à estratégia da 3.5. Os contadores não entram na escolha de alvo, rota, mira ou potência. O retorno diagnóstico da associação de balas é usado somente nas métricas. Os pesos pré-treinados e as cores foram preservados.

Foram executadas **30 séries de três rounds, totalizando 90 rounds**, comparando a base e a versão instrumentada. Ambas venceram **12/15 séries**. O controle venceu 36 rounds e a versão instrumentada 37; isso não é apresentado como ganho de combate. Os oponentes incluem código não inteiramente determinístico, e o motor não fixa toda a aleatoriedade pelas sementes de posições.

## O que é medido

| Medida | Definição |
| --- | --- |
| Dano causado/recebido nominal | Soma de `Rules.getBulletDamage` nos eventos entregues. Não é a perda real de energia, dano efetivamente aplicado ou pontuação oficial. Pode incluir sobreposição com energia que já não existia no alvo. |
| Energia investida | Potência dos disparos realmente aceitos pelo motor. Ondas sem bala não entram. |
| Bônus nominal | Bônus calculado pela regra nos eventos de acerto, separado do custo de tiro. Não representa toda a variação de energia. |
| Associação de ondas | Impactos recebidos com/sem uma onda encontrada pelo critério atual, erro radial e confiança interna da onda. Uma associação encontrada não é prova independente de identificação perfeita. |
| Intervalo entre scans | Distância temporal entre scans sucessivos do mesmo inimigo, agrupada por round. Exclui o primeiro scan de cada inimigo, repetições no mesmo tick e sentinelas. |
| Idade no tiro | Ticks desde o último scan do alvo no instante de um disparo real. |
| Falta de alvo | Fração das decisões executadas sem alvo válido. Não é uma medição de todo o tempo de simulação, pois ticks perdidos não são amostrados. |
| Faixas de distância | Distância no momento do disparo: menor que 200, de 200 até menos de 400, e 400 ou mais. O impacto é atribuído à faixa daquele disparo. |
| Desempenho | Contadores existentes de turnos perdidos, vitórias e estatísticas por round/série. Não se afirma medir tempo exato de CPU por essas contagens. |

Os contadores cobrem os eventos de combate entregues **antes do primeiro relatório de encerramento**. A animação de vitória fica fora. Prioridades de eventos e encerramento podem limitar a entrega de eventos simultâneos; os números não devem ser confundidos com um inventário perfeito de todos os eventos físicos do motor. As prioridades existentes não foram alteradas para obter os dados.

O mapa adicional de faixas acompanha até 64 balas próprias simultâneas e remove entradas em acertos, perdas ou colisões entre balas. Se faltar a associação de faixa, o acerto fica explicitamente em `unknown_hit_range`. Métricas sem denominador ficam como `null` no JSON e célula vazia no CSV, em vez de aparentarem uma taxa zero medida.

## Resultado do diagnóstico contra Diamond

Três séries / nove rounds da versão instrumentada, usando o Diamond **1.8.28** da instalação enviada:

- Intervalo médio entre scans sucessivos: **1.0010 ticks**.
- Idade média do scan no momento dos tiros: **0.0 ticks**.
- Decisões sem alvo válido: **0.42%**.
- Impactos recebidos registrados: **86**, todos com associação pelo critério atual; **0** sem associação.
- Erro radial médio das associações: aproximadamente **3.7e-13 unidades**, compatível com erro numérico muito pequeno.
- Acertos/tiros registrados: **52/625 (8.32%)**.
- Energia investida em tiros: **602.19**; dano nominal causado registrado: **216.79**; recebido: **407.28**.

As três séries continuaram perdidas. Neste lote, não há indicação de perda frequente de radar como principal explicação. A associação de impactos e a origem/tempo das ondas pareceram consistentes com as observações recebidas. Isso não valida as probabilidades angulares de perigo nem as ondas de tiros que não atingiram o robô.

A próxima investigação deve comparar a qualidade da mira e do modelo de perigo, em vez de presumir que substituir o radar resolverá as derrotas. O valor de confiança 1 usado numa onda de duelo é uma regra interna; não significa certeza estatística calibrada.

## Comparação e custo

Robocode 1.11.1, Java 21, compilação Java 8, arena 800 × 600 e três rounds por batalha. Contextos: Diamond, TrackFire, RamFire, seis exemplos em todos contra todos e equipe real de cinco. Três posições por contexto e versão: 3509, 3659 e 3821.

As versões preservaram o total de séries vencidas em todos os contextos. Foram registrados **dois turnos perdidos no controle e um na versão instrumentada**. Essa diferença pequena não demonstra uma otimização de CPU. O critério registrado antes da execução foi preservar séries por contexto e limitar o aumento a três turnos perdidos.

O observador imprime uma linha por round, com esquema `TELEMETRY v=1`. Não grava arquivos dentro do robô nem imprime dados a cada tick. O CSV é produzido externamente. Os agregadores antigos foram corrigidos para ler seus contadores apenas nas linhas de estatísticas de round; assim a nova linha não duplica tiros ou acertos.

## Usar e repetir

Os dados estão em [tests/telemetry-results.json](tests/telemetry-results.json), com coordenadas efetivas, resultados e hashes. O [CSV por round](tests/telemetry-rounds.csv) contém os 45 registros da versão instrumentada. Os rounds são numerados 0, 1 e 2, como na API.

```sh
python tools/diagnose.py --home /caminho/robocode-testes --baseline /caminho/titanprime-3.5.java --output /caminho/diagnostico
python tests/telemetry_checks.py --api /caminho/robocode/libs/robocode.jar
```

Use uma instalação separada com Diamond 1.8.28. A base 3.5 pode ser obtida de `robots/equipe/titanprime.java` no commit `26af41108680157af4dab10ce3e004540bec65b6`. Acrescente `--ecj /caminho/ecj.jar` se não houver `javac`. Para reprocessar os logs sem novas batalhas, use `--parse-only` com as fontes originais correspondentes.

O verificador rejeita registros incompletos, duplicados, valores não finitos, esquemas desconhecidos e contagens incompatíveis. Nenhuma nova defesa ou mira foi ativada nesta versão.
