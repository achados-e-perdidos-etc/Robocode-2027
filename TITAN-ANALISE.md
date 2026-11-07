# Titan analisado e ideia incorporada ao Nêmesis 3.2

Foi analisado o `Titan-Vingation-Projeto-Completo.zip` enviado pelo usuário, com **18 arquivos Java de produção**. O objetivo confirmado foi aproveitar suas boas ideias para melhorar o Nêmesis, mantendo o robô principal inteiro em um único Java e a competição em melhor de três.

**Resultado:** foi incorporada uma defesa que reduz a repetição dos ângulos de passagem pelas ondas quando o adversário está acertando com frequência. É uma implementação própria do conceito presente no Titan, integrada ao planejador e à rede já existentes. O peso extra para agressores recentes foi testado, mas ficou desligado.

Não foi substituído o Nêmesis pelo Titan nem convertido o projeto modular inteiro. Organizar um robô em módulos é uma prática normal; os resultados dependem das decisões e da qualidade dos modelos.

## O que encontramos no Titan

**Boas ideias:** estatísticas por adversário, limitação do histórico, gravação de dados com controle de tamanho, risco de cruzar perto de outros robôs, memória temporária de agressores e mistura entre impactos recebidos e ângulos de passagem para reduzir previsibilidade.

**Pontos que merecem cuidado:**

1. `WaveSurfingMovement.closestWaves` ordena pela distância restante da onda. Balas com velocidades diferentes precisam ser priorizadas pelo tempo restante. Uma onda a 270 pixels, viajando a 11 pixels/tick, demora cerca de 24,5 ticks; outra a 324,2 pixels, viajando a 19,7, demora cerca de 16,5. A segunda deve ser considerada primeiro. O Nêmesis já divide distância por velocidade.
2. A simulação de inversão do Titan soma 2 de velocidade ao frear no sentido oposto. Isso aproxima incorretamente inversões fracionárias: de −1 em direção positiva chega a +1, enquanto o integrador validado do Nêmesis chega a +0,5 nesse passo. Mantivemos a física do Nêmesis.
3. O perfil de perigo de esquiva começa zerado quando não há dados. Nesse estado, os ângulos não recebem uma preferência inicial baseada na hipótese de tiros diretos. O Nêmesis já tem um prior de perigo e pesos neurais incorporados.
4. A tática de conservação pode restringir disparos contra inimigos saudáveis e distantes a um disparo de controle após 100 ticks. Em duelo, o modo de espera pode segurar tiros por até 120 ticks contra um inimigo fraco. São escolhas com troca entre economia e oportunidade, não erros universais: o Titan venceu os testes básicos de arena. Não copiamos essas esperas.
5. Na finalização de um inimigo com energia zerada, o Titan pode escolher potência 3. Um tiro de potência 0,1 pode bastar para eliminar um alvo que continue desativado e também viaja mais rápido. A condição pode mudar se o inimigo recuperar energia com uma bala ainda em voo. O Nêmesis já limita a potência pela energia conhecida do alvo.
6. O ZIP não contém os quatro arquivos de aprendizado `.dat`. O Titan inicia sem esse histórico em uma instalação nova. Os avisos de memória ausente nesse primeiro início são esperados; não significam falha de compilação.

As ondas virtuais criadas a cada turno também são uma ideia útil para investigar, mas uma alternativa de observações extras já havia sido testada no Nêmesis sem superar o controle em séries curtas. Não foi adotada automaticamente só por aparecer nesse projeto.

Os relatórios do ZIP informavam testes com robô simulado e ausência de batalhas no motor completo. Nesta análise, compilamos as fontes com a API real do Robocode 1.12.0 e executamos batalhas. O JAR e a API incluídos no ZIP não foram usados como executáveis ou dependência de teste.

## Teste do Titan original

Três séries por contexto, três rounds por série, sem dados de batalhas anteriores. Cada série recebeu uma classe principal com nome próprio para manter sua pasta de dados separada; a estratégia e os módulos foram preservados.

| Adversários | Séries vencidas pelo Titan | Rounds vencidos pelo Titan |
| --- | ---: | ---: |
| Nêmesis 3.0 | 0/3 | 1/9 |
| Diamond | 0/3 | 1/9 |
| Seis robôs de exemplo | 3/3 | 9/9 |

Esses resultados mostram um robô funcional, com limites contra adversários mais fortes. São amostras pequenas e posições específicas, não um ranking geral. O único round ganho contra Diamond não demonstra superioridade ao Nêmesis em outras condições.

O motor criou **36 arquivos de dados** nas nove séries. Foi confirmada a gravação dos quatro arquivos por série nesse ambiente, além dos avisos esperados de ausência de memória na primeira inicialização. Isso não testa migração dos dados antigos do usuário nem todas as versões do jogo.

## Comparação das ideias no Nêmesis

Foram testadas quatro alternativas em cinco contextos: cópia do Nêmesis 3.0, Titan original, Diamond, arena com sete robôs e grupo com Diamond, SpinBot e TrackFire. Cada alternativa teve duas posições por contexto, sempre em batalhas independentes de três rounds.

| Alternativa | Séries vencidas | Primeiros rounds vencidos | Total de rounds vencidos |
| --- | ---: | ---: | ---: |
| Controle 3.0 | 4/10 | 3/10 | 13/30 |
| Peso extra para agressores | 4/10 | 2/10 | 13/30 |
| Redução de trajetórias repetidas | **5/10** | **5/10** | **16/30** |
| As duas ideias juntas | 5/10 | 2/10 | 12/30 |

O critério principal foi vencer dois rounds. O primeiro round foi o primeiro desempate, seguido por rounds vencidos, pontuação e CPU. A variante somente com redução de repetição foi selecionada antes da avaliação reservada.

## Avaliação reservada em outras posições

Três séries por contexto e versão, sem usar essas posições na seleção:

| Contexto | Controle: séries | Nêmesis 3.2: séries | Controle: rounds | Nêmesis 3.2: rounds |
| --- | ---: | ---: | ---: | ---: |
| Cópia do Nêmesis 3.0 | 1/3 | 1/3 | 4/9 | 3/9 |
| Titan original | 1/3 | **2/3** | 4/9 | **6/9** |
| Diamond 1v1 | 0/3 | 0/3 | 0/9 | 0/9 |
| Arena com sete robôs | 3/3 | 3/3 | 9/9 | 9/9 |
| Diamond + SpinBot + TrackFire | 0/3 | 0/3 | 0/9 | **2/9** |
| Total | **5/15** | **6/15** | **17/45** | **20/45** |

As vitórias no primeiro round passaram de **3/15 para 5/15**. A pontuação relativa média passou de 34,21% para 36,90%. A nova defesa foi promovida por melhorar os critérios principais nessa amostra, mas houve regressão de rounds e pontuação no duelo contra a cópia anterior. Não é uma melhora uniforme nem estatisticamente comprovada contra qualquer adversário.

O Nêmesis continua sem vencer séries contra Diamond. No grupo com Diamond surgiram duas vitórias de round, mas em séries diferentes, insuficientes para ganhar uma melhor de três.

## Como a defesa nova funciona

- Registra o ângulo em que passamos por cada onda de confiança alta, incluindo as que não causaram impacto.
- Mantém um perfil de passagens separado do perfil de impactos, por adversário e entre os três rounds da mesma batalha.
- Depois de uma amostra mínima, estima a frequência de impactos nessas ondas observadas. Quando ela aumenta, mistura uma parcela do perfil de passagens ao perigo previsto.
- A mistura é limitada a 30%. Rotas que repetem ângulos recentes ficam menos atraentes, preservando o restante do planejamento físico e da rede neural.
- Ondas de baixa confiança, comuns em melee, não treinam esse perfil. Os cálculos de distribuição são preparados uma vez por onda e plano, evitando repetir a normalização em cada tick simulado.

Os campos `passed` e `taken` no console são massas estatísticas com decaimento, não contadores inteiros de todas as balas do inimigo. Não representam uma medição perfeita da precisão adversária. Os pesos neurais pré-treinados foram preservados; não houve novo treino neural externo nesta etapa.

O registro de agressores foi implementado como alternativa de teste, com decaimento temporal e limites, mas `USE_THREATS` permanece `false` no arquivo entregue. A opção ativa é `USE_FLATTENER = true`. Não há dependência dos módulos do Titan nem de arquivos `.dat` para jogar.

## Validação e limites

Foram **79 séries de três rounds = 237 rounds**: nove séries do Titan original, 40 de seleção e 30 de avaliação reservada. O motor confirmou os três resultados por round e as primeiras colocações totais. Cada série do Titan usou uma pasta de dados nova; os três rounds da mesma série puderam compartilhar seu aprendizado normal.

Os testes numéricos do Nêmesis passaram, incluindo os novos casos de decaimento de ameaça, ativação mínima, limites da mistura e normalização dos perfis de passagem. As verificações de melhor de três e preservação dos modelos incorporados também passaram.

Na seleção, faltou uma linha de telemetria de fim de round do controle; os snapshots oficiais dos três rounds permaneceram completos e os resultados de vitória foram conferidos. Na avaliação reservada, a telemetria dos 90 rounds ficou completa. Foram registrados 11 turnos perdidos no controle e três na candidata nessa etapa; JVM e carga da máquina influenciam esses números, portanto não se conclui que a defesa nova seja sempre mais rápida.

Ambiente: Robocode clássico 1.12.0, OpenJDK 21.0.13, ECJ 3.37.0 com alvo Java 8, arena 800 × 600, resfriamento 0,1. As posições foram planejadas, repetidas dentro de cada série e diferentes entre seleção e avaliação. A aleatoriedade interna dos adversários e a carga da JVM não foram totalmente fixadas. Os grupos são todos contra todos, não equipes coordenadas.

Dados e reprodução:

- [tests/titan-original-results.json](tests/titan-original-results.json): confrontos do original.
- [tests/titan-comparison.json](tests/titan-comparison.json): alternativas, seleção, avaliação e hashes das fontes analisadas.
- [tests/titan-scenarios.json](tests/titan-scenarios.json): participantes e posições.
- [tools/titan_compare.py](tools/titan_compare.py): teste com uma cópia extraída do ZIP, instalação separada de testes e Diamond instalado. Um identificador novo separa os dados do Titan em cada execução.

```sh
git show 7354f0c2541aa7704ce76ff55eee10691eb8bb45:robots/equipe/Nemesis.java > /tmp/Nemesis-antes-titan.java
python tools/titan_compare.py --home /caminho/robocode-testes --baseline /tmp/Nemesis-antes-titan.java --titan-project /caminho/titan-vingation
```

Acrescente `--ecj /caminho/ecj.jar` se não houver `javac`. O projeto modular enviado não foi publicado no repositório: o robô de produção continua sendo somente [Nemesis.java](robots/equipe/Nemesis.java).
