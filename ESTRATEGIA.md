# Robocode 2027 — Nêmesis

Melhoria de robô para o **Robocode clássico**, em Java. O código completo do robô está em **[robots/equipe/Nemesis.java](robots/equipe/Nemesis.java)**. Todos os componentes são classes internas desse único arquivo. Não há bibliotecas externas para instalar no robô, nem serviços de IA ou internet durante a batalha.

As próximas evoluções serão salvas diretamente na branch `main`, conforme combinado.

## Estratégia implementada

| Pedido | Implementação |
| --- | --- |
| Wave surfing e esquiva | Ondas estimadas por queda de energia, histórico dos últimos ticks, filtro de aquecimento do canhão e simulação das rotas até o cruzamento das quatro ondas mais próximas. |
| Mira preditiva | Cinco miras virtuais: direta, linear, circular, GuessFactor e neural. A seleção usa os resultados recentes de cada uma por adversário. |
| Aprendizado contínuo | Histogramas segmentados, redes neurais com retropropagação, replay limitado e perfis de ângulos de tiro por adversário. Dados antigos recebem menos peso. |
| Visão da arena | Memória de posição, direção, velocidade, energia, idade da observação, previsão de movimento, alvo e ondas estimadas. Visualização pela pintura do Robocode. |
| Administração de energia | Potência considera distância, energia própria, precisão observada, quantidade de inimigos e energia necessária para finalizar o alvo. Mantém uma reserva para sobreviver. |
| Um ou vários adversários | Em duelo, radar acompanha o alvo. Com vários inimigos, radar faz varredura completa, prioriza alvos e combina risco de proximidade com ondas. A estratégia muda automaticamente quando sobra um inimigo. |

O aprendizado combina estatística, redes neurais pequenas e simulação física. Os pesos do pré-treino vêm incorporados no próprio arquivo Java. O aprendizado novo ocorre durante os rounds e é mantido entre os rounds da mesma batalha na memória do processo; esse progresso novo **não é salvo automaticamente ao encerrar uma batalha**. Veja [TREINAMENTO.md](TREINAMENTO.md) para o ciclo por gerações e a transferência de pesos.

Robôs não enxergam as balas em voo na API clássica. As ondas são hipóteses, não a localização conhecida de cada bala. Em melee, danos e bônus de terceiros tornam a identificação de disparos menos confiável; essas ondas recebem peso menor. Nenhuma esquiva é garantida.

## Instalação e testes

Siga [COMO-TESTAR.md](COMO-TESTAR.md). Os cenários de teste estão em [battles](battles) e os resultados da versão anterior em [VALIDACAO.md](VALIDACAO.md). O novo experimento e as comparações estão em [EVOLUCAO.md](EVOLUCAO.md).

## Referências pesquisadas

Esta implementação foi escrita para este projeto a partir dos conceitos públicos, sem incorporar código dos robôs citados:

- [Diamond, de Voidious](https://github.com/Voidious/Diamond): referência competitiva que combina surfing em duelo e avaliação de risco em melee. O ranking citado no repositório é histórico, de 2015.
- [Understanding DrussGT, pelo autor Julian Kent](https://jkflying.net/posts/understanding_drussgt/): simular o percurso até uma onda e aprender os ângulos perigosos. Nosso modelo é muito menor que o do DrussGT.
- [Dookious](https://robowiki.net/wiki/Dookious): referência histórica de GuessFactor e wave surfing, ex-campeão do RoboRumble.
- [Wave Surfing Tutorial](https://robowiki.net/wiki/Wave_Surfing_Tutorial): conceitos de tempo de disparo, histórico e correspondência entre impactos e ondas.
- [GuessFactor](https://robowiki.net/wiki/GuessFactor) e [física do Robocode](https://robowiki.net/wiki/Robocode/Game_Physics): normalização dos ângulos e limites de movimento.
- [API oficial do AdvancedRobot](https://robocode.sourceforge.io/docs/robocode/robocode/AdvancedRobot.html): comandos assíncronos e eventos.

Essas referências orientam o projeto; não significam que o Nêmesis tenha o desempenho de um campeão. O confronto com Diamond também consta na validação.
