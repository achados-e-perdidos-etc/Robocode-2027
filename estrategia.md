# Robocode 2027 — Titan Prime

Melhoria de robô para o **Robocode clássico**, em Java. O código completo do robô está em **[robots/equipe/titanprime.java](robots/equipe/titanprime.java)**. Todos os componentes são classes internas desse único arquivo. Não há bibliotecas externas para instalar no robô, nem serviços de IA ou internet durante a batalha.

As próximas evoluções serão salvas diretamente na branch `main`, conforme combinado.

O criterio atual e **vencer mais rounds, contando todos igualmente**, com batalhas longas e adversarios fortes. Os estudos e ferramentas antigos de melhor de tres ficam preservados como historico. As quatro melhorias mais recentes e seus resultados estao em [quatro-melhorias.md](quatro-melhorias.md). Os pesos pre-treinados ja estao carregados no inicio de cada batalha.

**Versao de uso: Titan Prime 3.9.** O novo posicionamento melee esta ativo; os novos ajustes de defesa contextual, potencia e confianca estao disponiveis como experimentais. A decisao e os resultados estao em [quatro-melhorias.md](quatro-melhorias.md).

## Estratégia implementada

| Pedido | Implementação |
| --- | --- |
| Wave surfing e esquiva | Ondas estimadas, histórico, filtro de aquecimento, simulação de rotas até as quatro ondas mais próximas e mistura limitada de passagens recentes para reduzir previsibilidade sob impactos frequentes. |
| Mira preditiva | Miras direta, linear, circular, GuessFactor, neural, KNN de visitas, KNN de tiros reais e reativa. A selecao usa os resultados observados por adversario; outras miras permanecem experimentais. |
| Aprendizado contínuo | Histogramas segmentados, redes neurais com retropropagação, replay limitado e perfis de ângulos de tiro por adversário. Dados antigos recebem menos peso. |
| Visão da arena | Memória de posição, direção, velocidade, energia, idade da observação, previsão de movimento, alvo e ondas estimadas. Visualização pela pintura do Robocode. |
| Administração de energia | Potência considera distância, energia própria, precisão observada, quantidade de inimigos e energia necessária para finalizar o alvo. Mantém uma reserva para sobreviver. |
| Um ou vários adversários | Em duelo, radar acompanha o alvo. Com vários inimigos, radar faz varredura completa, prioriza alvos e combina risco de proximidade com ondas. A estratégia muda automaticamente quando sobra um inimigo. |

O aprendizado combina estatística, redes neurais pequenas e simulação física. Os pesos do pré-treino vêm incorporados no próprio arquivo Java. O aprendizado novo ocorre durante os rounds e é mantido entre os rounds da mesma batalha na memória do processo; esse progresso novo **não é salvo automaticamente ao encerrar uma batalha**. Veja [treinamento.md](treinamento.md) para o ciclo por gerações e a transferência de pesos.

Robôs não enxergam as balas em voo na API clássica. As ondas são hipóteses, não a localização conhecida de cada bala. Em melee, danos e bônus de terceiros tornam a identificação de disparos menos confiável; essas ondas recebem peso menor. Nenhuma esquiva é garantida.

## Instalação e testes

Siga [como-testar.md](como-testar.md). Os cenários de teste estão em [battles](battles) e os resultados da versão anterior em [validacao.md](validacao.md). O novo experimento e as comparações estão em [evolucao.md](evolucao.md).

## Referências pesquisadas

Esta implementação foi escrita para este projeto a partir dos conceitos públicos, sem incorporar código dos robôs citados:

- [Diamond, de Voidious](https://github.com/Voidious/Diamond): referência competitiva que combina surfing em duelo e avaliação de risco em melee. O ranking citado no repositório é histórico, de 2015.
- [Understanding DrussGT, pelo autor Julian Kent](https://jkflying.net/posts/understanding_drussgt/): simular o percurso até uma onda e aprender os ângulos perigosos. Nosso modelo é muito menor que o do DrussGT.
- [Dookious](https://robowiki.net/wiki/Dookious): referência histórica de GuessFactor e wave surfing, ex-campeão do RoboRumble.
- [Wave Surfing Tutorial](https://robowiki.net/wiki/Wave_Surfing_Tutorial): conceitos de tempo de disparo, histórico e correspondência entre impactos e ondas.
- [GuessFactor](https://robowiki.net/wiki/GuessFactor) e [física do Robocode](https://robowiki.net/wiki/Robocode/Game_Physics): normalização dos ângulos e limites de movimento.
- [API oficial do AdvancedRobot](https://robocode.sourceforge.io/docs/robocode/robocode/AdvancedRobot.html): comandos assíncronos e eventos.

Essas referências orientam o projeto; não significam que o Titan Prime tenha o desempenho de um campeão. O confronto com Diamond também consta na validação.
