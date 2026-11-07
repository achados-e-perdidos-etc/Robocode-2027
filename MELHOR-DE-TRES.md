# Competição: vencer dois dos três rounds

Nome atual: **Titan Prime**, em `robots/equipe/TitanPrime.java`. Os nomes antigos neste relatório identificam as versões testadas.

Este relatório registra a etapa anterior com a versão 3.0. A atualização posterior para 3.2 e sua comparação em séries de três rounds estão em [TITAN-ANALISE.md](TITAN-ANALISE.md). Para reproduzir exatamente o experimento antigo, use o robô e os scripts do commit `7354f0c2541aa7704ce76ff55eee10691eb8bb45`; os protótipos da branch atual acompanham o código novo.

A regra confirmada é **vencer pelo menos dois rounds em uma batalha de exatamente três rounds**. A pontuação acumulada serve para análise, mas não substitui esse resultado. O placar padrão do Robocode pode ordenar por pontos; confira a coluna de primeiras colocações (`1sts`) para esta regra.

Os cenários `.battle`, os padrões do treinador e a validação foram ajustados para três rounds. O robô já carrega os pesos pré-treinados no início da batalha; não precisa disputar dezenas de rounds antes de carregar esse conhecimento. O aprendizado novo continua entre os três rounds e é reiniciado na próxima batalha independente.

## Decisão sobre o código

**Foi mantido o código da versão 3.0.** Duas alterações para acelerar a adaptação foram experimentadas separadamente e juntas, mas não superaram o controle no critério de séries vencidas. Não foram ativadas no robô principal. O arquivo `Nemesis.java` entregue permanece com o mesmo SHA-256 da versão anterior, `d297edc2e4d835af07316870797e03280271169b5185c2893e37e9e2b19c0337`.

As alternativas testadas foram:

- **Observe:** criar observações de tiros virtuais durante o resfriamento do canhão, com peso reduzido e sem gastar energia. Elas atualizam as estatísticas de mira; não são contadas como tiros reais nem usadas para treinar a rede.
- **Warm:** aproveitar com mais antecedência o modelo geral de defesa pré-treinado, com influência inicial limitada.
- **Both:** as duas alterações juntas.

Os protótipos são reconstruídos somente pelo treinador a partir de [tools/bo3-prototype.json](tools/bo3-prototype.json). Esse arquivo contém as alterações experimentais vinculadas ao SHA da versão testada. O robô principal não consulta esse arquivo e continua inteiro em um único Java.

## Seleção: 48 séries independentes

Cada alternativa enfrentou quatro contextos em três posições iniciais, sempre em séries de três rounds: duelo contra uma cópia da versão 3.0, duelo contra Diamond, arena com sete robôs de exemplo e grupo com Diamond, SpinBot e TrackFire. Houve início novo de memória para cada série.

| Alternativa | Séries vencidas | Vitórias no primeiro round | Rounds vencidos |
| --- | ---: | ---: | ---: |
| Controle 3.0 | 5/12 | 6/12 | 15/36 |
| Observe | 4/12 | 4/12 | 13/36 |
| Warm | 5/12 | 5/12 | 15/36 |
| Both | 4/12 | 6/12 | 13/36 |

O critério principal foi ganhar a série. Em empate, a seleção considerou primeiro o desempenho no round inicial, depois o total de rounds vencidos, pontuação relativa e custo de CPU. O controle foi escolhido. A amostra é pequena: isso não prova que uma alternativa nunca será útil, mas não justifica ativá-la agora.

## Verificação em outras posições: 32 séries

A alternativa selecionada foi o próprio controle. Por isso, as duas execuções desta etapa usam **código idêntico**, com nomes diferentes; não representam comparação entre uma versão nova e uma antiga. Cada execução teve quatro séries por contexto, em posições diferentes das da seleção.

| Contexto | Execução A: séries vencidas | Execução B: mesmo código |
| --- | ---: | ---: |
| Contra cópia da versão 3.0 | 2/4 | 1/4 |
| Diamond 1v1 | 0/4 | 0/4 |
| Arena com sete robôs | 4/4 | 4/4 |
| Diamond + SpinBot + TrackFire | 0/4 | 0/4 |

A diferença entre as execuções do mesmo código evidencia a variação causada por comportamento aleatório e execução. Não se apresenta essa diferença como melhoria ou regressão do robô. O duelo contra Diamond continua sendo uma fraqueza séria: nenhuma das oito séries foi vencida.

## O que foi conferido

- **80 séries × 3 rounds = 240 rounds**, além de um teste inicial de três rounds.
- Cada série contém os três resultados por round. O avaliador confere os primeiros lugares dos snapshots com o resultado final oficial.
- O teste de regra verifica que uma vitória por 2–1 prevalece mesmo com menos pontos que o adversário.
- O marcador de início confirmou, em cada nova série, os mesmos pesos de partida: modelos provenientes de 1.228 observações de mira e 128 de defesa. Não houve acúmulo de exemplos de uma série para a próxima.
- Os dados incluem tiros, acertos, ondas, colisões e turnos perdidos separados por round, para investigar especificamente o início da partida.
- Compilação com API real, aprendizagem neural e verificações do treinador continuaram aprovadas.

Ambiente: Robocode clássico 1.12.0, Java 21.0.13, arena 800 × 600 e resfriamento 0,1. As posições foram planejadas e repetidas entre os três rounds de cada série para controlar o início; séries diferentes usam posições diferentes. Isso não cobre toda a variação de um torneio que sorteia posições novas a cada round. A aleatoriedade interna dos adversários e a carga da JVM não foram completamente fixadas.

As arenas com vários robôs são todos contra todos, não equipes coordenadas. Séries em que ninguém atinge duas primeiras colocações são registradas como sem vencedor por maioria; o avaliador não escolhe automaticamente quem fez mais pontos.

## Próximo critério de melhoria

Uma alteração deve ganhar mais séries curtas em várias condições, preservar o desempenho do primeiro round e respeitar o orçamento de CPU. Pontuação maior em uma batalha longa não basta. A principal lacuna continua sendo acertar e sobreviver contra adversários adaptativos fortes; o formato curto reforça a importância do pré-treino, mas não torna os modelos atuais campeões.

## Repetir

Use uma instalação separada de testes com Diamond instalado. Preserve o código atual como referência:

```sh
git show b554916b29f271ae593f67a428ef8ec301608c91:robots/equipe/Nemesis.java > /tmp/Nemesis-v3.java
python tools/bo3.py --home /caminho/robocode-testes --baseline /tmp/Nemesis-v3.java
```

Acrescente `--ecj /caminho/ecj.jar` se não houver `javac`. A ferramenta não publica nem substitui automaticamente o robô. Se o Java principal mudar, o protótipo experimental precisa ser atualizado: a ferramenta rejeita alterações incompatíveis para não testar o código errado.

Para o treino genético futuro, os padrões agora são três rounds e objetivo `series`:

```sh
python tools/train.py --home /caminho/robocode-testes --baseline /tmp/Nemesis-v3.java --rounds 3 --validation-rounds 3 --objective series
```

O ciclo genético completo não foi reexecutado nesta etapa; aqui foram comparadas as duas mudanças de adaptação rápida. Os experimentos anteriores permanecem documentados como históricos, com suas regras e tamanhos de batalha originais.

Dados: [tests/bo3-results.json](tests/bo3-results.json) e [tests/bo3-scenarios.json](tests/bo3-scenarios.json). Verificações da regra: `python tests/bo3_checks.py`.
