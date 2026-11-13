> Histórico das versões 3.x. O código atual 4.x usa outra base; veja [arquitetura.md](arquitetura.md) e [ciclo-elite.md](ciclo-elite.md).

# Referências dos 20 estilos de defesa

As referências foram conferidas em revisões específicas do GitHub. Esta tabela trata da defesa; o KNN de mira adotado na 3.5 está descrito em [referencias-ataque.md](referencias-ataque.md). A lista mistura controladores de movimento, modelos estatísticos e componentes geométricos que podem funcionar juntos. O nome de uma técnica num README não comprova uma implementação completa.

| Nº | Técnica | Evidência consultada | Situação no Titan Prime |
| --- | --- | --- | --- |
| 1 | Wave Surfing | [Diamond: SurfMover][surf] | Presente: ondas e simulação de rotas. |
| 2 | Precise Wave Surfing | [Diamond: preciseIntersection][wave] | Experimento de interseção retangular; não adotado. Não reproduz todo o pipeline do Diamond. |
| 3 | Statistical Wave Surfing | [Diamond: modelos por inimigo][enemy] | Histogramas e rede neural presentes. |
| 4 | Dynamic Clustering Surfing | [Diamond: KNN][knn] | KNN por contexto implementado e testado como experimento; não adotado. |
| 5 | Minimum Risk Movement | [Diamond: MeleeMover][melee] | Presente: avaliação de destinos e risco espacial. |
| 6 | Anti-Gravity Movement | [SuperMercutio][super] e [The Unfolding Robot][anti] | Há penalidade por proximidade; não há um controlador vetorial dedicado de forças. |
| 7 | Wall Smoothing | [The Unfolding Robot: controlador de parede][wall] | Há limites da arena e penalidades; isso não equivale à suavização angular dedicada. |
| 8 | Randomized Movement | [Wilde][wilde] | Movimento de combate não recebeu aleatoriedade adicional. O sorteio cosmético usa gerador separado. |
| 9 | Stop-and-Go Movement | [Diamond: opção STOP][surf] é relacionada, mas não confirma um controlador periódico stop-and-go | O planejador já considera frenagem. Não foi adicionado um alternador periódico. |
| 10 | Oscillating Movement | [The Unfolding Robot: orquestração][unfolding] e classes de oscilação no projeto | Na revisão consultada, a chamada ao oscilador no duelo está comentada; o caminho ativo usa movimento aleatório. |
| 11 | Orbital Movement | [Diamond: movimento orbital sem onda][surf] | Preferência por movimento lateral presente; não foi adicionado um novo controlador orbital. |
| 12 | Adaptive Distance Control | [Diamond: DistanceController e distancingDanger][surf] | Há distância desejada e repulsão. Isso não é a adaptação completa do Diamond. |
| 13 | Bullet Shadowing | [Diamond: shadowFactor, shadowed e sombras][wave] | Não implementado. |
| 14 | Bullet Shielding | Robotnikk citado não expõe Java na revisão consultada | Sem implementação verificada nesse repositório; não adicionado ao Titan. |
| 15 | Pattern-Breaking Movement | [Wilde][wilde] usa distâncias e escolhas pseudoaleatórias | Referência relacionada; não comprova um detector específico de padrões. |
| 16 | Velocity Profiling | Termo amplo; [Diamond: previsão de movimento][predictor] modela a dinâmica | Velocidade e aceleração entram na simulação e nas características; não há um novo controlador com esse nome. |
| 17 | Corner Avoidance | [The Unfolding Robot: paredes][wall] | Restrições de arena presentes; não foi integrado um controlador exclusivo de cantos. |
| 18 | Enemy Gun Modeling | [Diamond: modelos de ângulos por inimigo][enemy] | Há estatísticas de disparos observados; KNN contextual ficou experimental. Não identifica o código do inimigo. |
| 19 | Predictive Evasion | [SuperMercutio][super] e [Diamond][surf] | Simulação física de rotas presente. SuperMercutio usa hipóteses de tiro direto e linear. |
| 20 | Adaptive Movement Selection | [The Unfolding Robot][unfolding] escolhe controladores conforme o contexto | O Titan ajusta a avaliação entre duelo e múltiplos adversários; não recebeu seleção aprendida entre 20 modos. |

## Correções nas associações dos projetos

**WSMovement** existe, mas importa `EnemyWave`, `RoboUtils` e a interface `Movement`; a classe não é um robô autossuficiente em um único arquivo. [Fonte][ws].

**The Unfolding Robot** contém vários controladores. Seu README descreve oscilação no duelo, mas o caminho ativo da revisão consultada chama `RandomMoveController` e deixa a chamada ao oscilador comentada. A avaliação acima considera o código. [Orquestração][unfolding].

**AntiGrav-WavesurfingBot** tem código de equipe, repulsão de paredes e mira preditiva. No `Main.java` consultado não foi identificado rastreamento de ondas de balas inimigas nem seleção de rotas por perigo de ondas. A descrição de “wavesurfing radar” no README não basta para confirmar Wave Surfing defensivo. [Código do projeto][ag].

**Robotnikk** tem licença MIT, mas a árvore consultada não contém fontes Java: há README, licença, configuração e uma imagem de batalha. A implementação de Bullet Shielding e KNN não pôde ser verificada ali. [Repositório nessa revisão][robotnikk].

## Licenças e uso neste projeto

Diamond usa licença zlib modificada, conforme seu arquivo `LICENSE`; Wilde usa BSD de três cláusulas; Robotnikk tem MIT. Não foi identificada uma licença na raiz das revisões consultadas de SuperMercutio, WSMovement, Unfolding e AntiGrav-WavesurfingBot. Isso não estabelece uma licença geral para copiar suas fontes; dependências podem ter avisos próprios.

O Titan não recebeu fontes desses robôs. Os experimentos implementam os conceitos com código próprio. SuperMercutio foi compilado apenas na instalação local de testes, com renomeação de pacote/classe, mantendo a atribuição e a estratégia; sua fonte e seu binário não foram publicados neste repositório.

As revisões e hashes dos textos consultados estão em [tests/defense-references.json](tests/defense-references.json). Os resultados das alternativas estão em [defesa-experimentos.md](defesa-experimentos.md). O robô de uso normal continua em um único [titanprime.java](robots/equipe/titanprime.java).

[surf]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/move/SurfMover.java
[wave]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/utils/Wave.java
[enemy]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/move/MoveEnemy.java
[knn]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/utils/KnnView.java
[melee]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/move/MeleeMover.java
[predictor]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/utils/MovementPredictor.java
[super]: https://github.com/mgalushka/robocode-robots/blob/08e7384eb2bbc370e1045e3a546b4cff953485ad/src/main/java/sample/SuperMercutio.java
[anti]: https://github.com/khoitnm/RoboCode/blob/b026b93f2fb86a44a8feafe3db59a894c268f6fd/common/src/main/java/org/tnmk/robocode/common/movement/strategy/antigravity/AntiGravityMoveController.java
[wall]: https://github.com/khoitnm/RoboCode/blob/b026b93f2fb86a44a8feafe3db59a894c268f6fd/common/src/main/java/org/tnmk/robocode/common/movement/strategy/wallsmooth/WallSmoothMoveController.java
[unfolding]: https://github.com/khoitnm/RoboCode/blob/b026b93f2fb86a44a8feafe3db59a894c268f6fd/the-unfolding-robot/src/main/java/org/tnmk/robocode/robot/TheUnfoldingMovement.java
[wilde]: https://github.com/alexjamesmacpherson/robocode/blob/8db2c570f397e2c587a8b0a6466dba4fbab47090/Wilde.java
[ws]: https://github.com/camiloh12/robocode/blob/35c4af54c0ba72874a75add50e0e1e604c69c5b9/MyRobots/src/cam/movement/WSMovement.java
[ag]: https://github.com/parthnan/AntiGrav-WavesurfingBot/blob/ff48d9598f2167c87103268f1bb9344f0d8ab0a3/robocode-AntiGrav%2BWavesurfingBot/group08/Main.java
[robotnikk]: https://github.com/ItaloSLeao/robotnikk-knn/tree/4075bb20346672b5fb4eda1bcc22e905a9070e1a
