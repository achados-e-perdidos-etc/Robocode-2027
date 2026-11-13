> Histórico da reconstrução inicial 4.0. A versão de memória publicada é a 4.15; consulte [status-dos-testes.md](status-dos-testes.md) e [aprendizado.md](aprendizado.md). Os geradores e comandos deste relato reproduzem a versão histórica; não regeneram o fonte atual.

# Reconstrução competitiva — Titan Prime 4.0

## Objetivo e mudança de base

A meta pedida foi vencer pelo menos 10 dos 30 rounds contra o Diamond. O critério reservado deste ciclo exige esse mínimo em **cada uma de três batalhas independentes**, com o fonte congelado e primeiras colocações simultâneas descontadas. Pontuação também é registrada, sem transformá-la em vitória de round.

A alteração é uma **adaptação direta do motor aberto Diamond**, de Voidious, com árvores KNN de Rednaxela. O objetivo deixou de ser acrescentar nomes de técnicas ao motor 3.x e passou a incorporar uma implementação competitiva completa de geometria, aprendizado e coordenação. Não se afirma que esse motor foi originalmente escrito pelo projeto Titan.

Origem fixada: [Diamond no commit 2d1c6a2](https://github.com/Voidious/Diamond/tree/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb). A licença modificada zlib permite adaptação e redistribuição com origem e avisos preservados. Os avisos continuam no Java e a [licença integral](licenses/diamond.txt) acompanha o projeto e o JAR. O [manifesto](tests/elite/import-manifest.json) registra os 60 arquivos de produção incorporados e seus hashes. Os testes e ferramentas upstream não viraram dependências do robô.

O robô final continua em um único `robots/equipe/titanprime.java`. Um gerador reproduzível, [diamond_import.py](tools/diamond_import.py), reúne os tipos em classes internas e aplica as adaptações do Titan. A regeneração foi comparada byte a byte com a candidata testada.

## Pesquisa utilizada

| Fonte primária | Aplicação |
| --- | --- |
| [Código do Diamond](https://github.com/Voidious/Diamond) e [descrição pelo autor](https://robowiki.net/wiki/Diamond) | Base licenciada de radar, miras, surfing e melee. O ranking de seu README é histórico, não uma classificação atual. |
| [Understanding DrussGT, de Julian Kent](https://jkflying.net/posts/understanding_drussgt/) | Estudo da previsão física, busca de movimentos e aprendizado defensivo; seu código não foi incorporado. |
| [Precise Intersection](https://robowiki.net/wiki/Precise_Intersection) e os testes de Wave do projeto Diamond | Estudo da geometria entre ondas e corpo do robô. |
| [Bullet Shadow](https://robowiki.net/wiki/Bullet_Shadow) e o tratamento de FiredBullet no Diamond | Sombras produzidas pelos nossos disparos e remoção das sombras após intercepções. |
| [AdvancedRobot](https://robocode.sourceforge.io/docs/robocode/robocode/AdvancedRobot.html), [Rules](https://robocode.sourceforge.io/docs/robocode/robocode/Rules.html) e [RobotSetup](https://robocode.sourceforge.io/docs/robocode/robocode/control/RobotSetup.html) | Eventos públicos, limites físicos, potências e largadas em graus, conferidas em snapshots em radianos. |
| [Robocode 1.11.2](https://github.com/robo-code/robocode/releases/tag/v1.11.2) e [1.12.0](https://github.com/robo-code/robocode/releases/tag/v1.12.0) | Correção oficial do encerramento de threads que prejudicava os testes no motor anterior. |

Também foram revistos os CSVs e as amostras visuais dos vídeos de PC já analisados em [batalhas-no-pc.md](batalhas-no-pc.md). Os quadros sugerem posicionamento frequente perto das bordas, mas não identificam sozinhos a causa de cada impacto. Não se afirma ter acompanhado todos os frames. Os vídeos privados não foram publicados.

## Alterações efetivas

- Defesa de duelo com KNN, interseção precisa, ângulos de escape com paredes e sombras de balas próprias.
- Miras de duelo com dados de contexto, modelo anti-surfer e comparação por Virtual Guns; mira inicial antes de acumular histórico suficiente.
- Radar e defesa específicos para melee, com troca automática para duelo.
- Recuperação finita quando todos os destinos de melee são inseguros, em lugar de um loop sem limite.
- Proteções contra divisões por zero no risco de melee e contra corte inválido de arquivo no gravador de erros.
- Paleta por batalha, giro com cores após a vitória e diagnóstico persistente do Titan, sem manter os antigos controladores 3.x comandando o combate.
- Guia atual, README, empacotador completo e verificação de compilação no GitHub Actions. Estudos antigos conservados como históricos.

As redes neurais e o genoma 3.x foram retirados do controle ativo. Isso não significa que todos os 25 nomes da lista anterior estejam ativos como módulos separados. A [arquitetura](arquitetura.md) descreve a implementação efetiva. O ganho deste pacote não foi isolado por componente.

## Protocolo

Arena 800 × 600, 30 rounds, inatividade 450, resfriamento 0,1, energia inicial 100. Um Prime, sem aliados, e o JAR original `voidious.Diamond_1.8.28.jar` enviado pelo usuário. O adversário não foi editado nem teve energia, modelos ou processamento reduzidos.

SHA256 do adversário: `1ab39d66041eaf4edf5c073d8caa7c61ff5e783961adfad03914f14017d048ea`.

A ferramenta [elite_duel.py](tools/elite_duel.py) usa a classe canônica `equipe.titanprime`, em vez de nomes diferentes para cada candidata. Copia apenas bibliotecas/configuração do motor, compila o Prime com apenas a API no classpath, instala somente os participantes e arquiva dados anteriores antes de iniciar outra JVM. O limite calibrado `robocode.cpu.constant=10030489` é compartilhado por ambos, tanto na base anterior quanto na candidata. Plano, hashes das bibliotecas/configuração e versão Java ficam registrados antes da primeira série.

Sementes controlam **somente as largadas**; não fixam a aleatoriedade interna dos robôs. A mesma largada é repetida dentro de cada batalha para os 30 rounds, e os componentes aprendem entre eles. A segunda série inverte as duas posições. Lotes independentes reiniciam a memória, sem importar arquivos de aprendizado.

Se a soma de primeiras colocações excede 30, subtraímos todo o excesso das primeiras colocações do Prime. Esse é um limite inferior conservador para suas vitórias exclusivas. Um empate pode conceder primeiras colocações sem enviar `WinEvent`; o CSV é conferido contra esse intervalo, não forçado a igualar o total oficial.

A série só é válida se há todos os rounds, resultados, robôs independentes ativos com energia 100, posições verificadas e nenhum erro de motor/robô. Não são repetidas perdas válidas até aparecer um placar favorável.

## Tentativas anteriores preservadas

No Robocode 1.11.1, a base 3.10 venceu 3/30. O núcleo inicialmente reunido venceu 7/12; após integração visual e diagnóstica, venceu 4/12 pelo desconto conservador. Esses lotes eram desenvolvimento, não a confirmação final.

As primeiras três séries reservadas, ainda no 1.11.1, produziram limites de 13, 14 e 17 vitórias, mas **não foram aceitas**. Houve largadas divergentes; na primeira houve falha ao encerrar a thread do Prime e na terceira houve falha com o próprio Diamond. Os [logs e resultados completos](tests/elite/engine-1.11.1) permanecem disponíveis. A comparação inicial do CSV ainda exigia igualdade com primeiras colocações oficiais; a correção distingue empates de eventos de vitória, sem apagar as demais falhas.

Foi identificado o conserto oficial do encerramento de threads no 1.11.2. O protocolo foi transferido para o motor 1.12.0 instalado, com o mesmo limite compartilhado de processamento. A base 3.10 foi testada novamente nesse motor: **1 vitória em 30**, série válida. Os números de motores diferentes não foram misturados numa única comparação.

## Confirmação reservada no Robocode 1.12.0

Resultados serão inseridos após o encerramento dos três lotes. As largadas 32003, 32413 e 32909 já foram declaradas no [plano da migração](tests/elite/engine-migration-plan.json), antes das execuções.

## Limites práticos

Resultados dependem da arena, versão do motor, processamento disponível e comportamento aleatório. O teste não demonstra invulnerabilidade, vitória em todos os rounds ou superioridade universal. O aprendizado novo é mantido entre rounds da batalha, sem ser recarregado de disco em batalhas independentes. O CSV é diagnóstico, não um modelo aprendido.

Os cenários com vários inimigos são todos contra todos: os adversários também se enfrentam. Equipes coordenadas precisam de avaliação própria. As verificações contra robôs passivos servem apenas para validar gravação, animação e empacotamento.
