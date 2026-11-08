# Como instalar e testar o Titan Prime

Você precisa do **Robocode clássico** e do Java. Este arquivo não serve para o Tank Royale. O código usa sintaxe Java 8 e foi validado no Java 21 com Robocode 1.12.0 e com a sua instalação 1.11.1.

## Colocar o robô no jogo

1. No GitHub, abra **Code > Download ZIP** e extraia o projeto.
2. Abra a pasta de instalação do seu Robocode e entre em `robots`.
3. Crie a pasta `equipe`, se ainda não existir.
4. Copie somente `robots/equipe/titanprime.java` do projeto para essa pasta. O caminho final deve ser `robocode/robots/equipe/titanprime.java`.
5. Para evitar selecionar o robô antigo, remova `Nemesis.java`, `Nemesis.class` e `Nemesis$*.class` da sua pasta `robots/equipe`. Se você instalou a primeira versão, remova os arquivos `Sentinela.java`, `Sentinela.class` e eventuais `Sentinela$*.class` da sua instalação para não selecionar o robô antigo.
6. No jogo, abra **Robot > Source Editor**, abra `titanprime.java` e selecione **Compiler > Compile**.
7. Em **Battle > New**, procure `equipe.titanprime` (pode aparecer com `*`, indicando uma versão em desenvolvimento).

O compilador gera vários arquivos `.class` para as classes internas. Isso é normal: todo o código fonte continua em um único `.java`.

## Batalhas de avaliacao

Use arena **800 x 600**, resfriamento **0,1** e, inicialmente, **10 rounds**. Conte todas as vitorias de round igualmente; nao escolha a versao por pontos nem por vencer dois dos tres primeiros rounds. Repita com outras largadas e batalhas novas para evitar selecionar por um resultado isolado.

Priorize Diamond 1.8.28, SuperMercutio, os dois simultaneamente e os Titans fornecidos. Em cada confronto, adicione apenas uma copia do Prime, sem aliados. Em todos contra todos, os demais oponentes tambem se enfrentam; uma equipe coordenada e outro tipo de teste.

RamFire e SpinBot servem para uma verificacao curta de instalacao ou regressao. Nao e necessario repetir grandes baterias de exemplos a cada alteracao. Os arquivos `.battle` antigos e as ferramentas de melhor de tres permanecem para reproduzir estudos historicos, sem definir o criterio atual de melhoria.

A comparacao das quatro melhorias, os comandos de teste e os resultados estao em [quatro-melhorias.md](quatro-melhorias.md). O Prime precisa somente do Java unico; os adversarios devem estar instalados separadamente. O projeto nao redistribui seus fontes ou binarios.

## Ver a percepção do robô

Durante a batalha, abra a janela do Titan Prime e habilite **Paint**. Os nomes exatos podem variar com a versão do jogo. A tecla **V**, quando encaminhada ao robô pelo jogo, alterna o desenho.

- Círculos vermelhos: ondas de tiros estimadas.
- Quadrado amarelo: alvo escolhido; cinza: outros adversários observados.
- Linha azul: destino escolhido pela avaliação de risco.
- `E`: energia conhecida; `age`: ticks desde o último scan.

Os desenhos representam estimativas disponíveis ao robô, não revelam informações ocultas do jogo. Os dados de depuração também mostram o modo da mira: 0 direta, 1 linear, 2 circular, 3 GuessFactor, 4 neural, 5 KNN de visitas, 6 KNN de tiros reais e 9 reativa. Os indices 7 e 8 pertencem a miras experimentais que nao estao habilitadas por padrao.

## Comparar versões

Registre a versão do Robocode, versão do código, adversários, regras, rounds, pontuação e primeiras colocações. Repita as mesmas condições, idealmente com várias sementes ou lotes: as posições iniciais influenciam os resultados. Uma única vitória não demonstra melhora.

No console do robô, cada round registra tiros, acertos, ondas detectadas, ondas associadas a balas, colisões com paredes e turnos perdidos. As linhas `learn` mostram o histórico acumulado por inimigo, incluindo amostras neurais. As amostras de defesa incluem balas que acertaram o robô e colisões entre balas.

Na versão 3.2, `passed` e `taken` indicam massas com decaimento usadas na defesa que reduz trajetórias repetidas. São estimativas de ondas observadas, não contadores exatos da precisão adversária. A análise do Titan e os testes dessa atualização estão em [titan-analise.md](titan-analise.md).

## Verificação numérica opcional

Com Python 3 e um JDK instalado:

```sh
python tests/core_checks.py --api /caminho/robocode/libs/robocode.jar
```

Se usar o compilador Eclipse em vez de `javac`, acrescente `--ecj /caminho/ecj.jar`. Esse teste gera seu auxiliar Java em uma pasta temporária; o robô continua com um único arquivo fonte no projeto.

## Treinar várias versões

Veja [treinamento.md](treinamento.md) para rodar o torneio por gerações em uma instalação separada e comparar uma candidata sem substituir o robô atual. Você não precisa de Python nem dessas ferramentas para jogar com `titanprime.java`.

## Regra da competição: melhor de três

O objetivo é vencer **pelo menos dois dos três rounds**. O placar padrão do Robocode usa pontuação acumulada; para esta regra, conte as primeiras colocações (`1sts`) de cada robô. Uma pontuação maior, sozinha, não significa ganhar a série.

Repita várias batalhas independentes de três rounds. Não use uma batalha única de 30 rounds e divida o resultado depois: o aprendizado acumulado tornaria a comparação diferente da competição. Use `tools/bo3.py` para comparar início, vitórias por round e vitórias de séries. Veja [melhor-de-tres.md](melhor-de-tres.md).

## Treinos RamFire e SpinBot

Foram adicionados três cenários prontos de três rounds. O resultado dos treinos e o cenário de aproximação a 100 pixels estão em [ramfire-spinbot.md](ramfire-spinbot.md). O Java atual foi preservado após a comparação com a candidata treinada.

Os nomes dos arquivos do projeto foram padronizados em minúsculas. O robô atual é `robots/equipe/titanprime.java`, com classe `equipe.titanprime`. Relatórios e comandos de commits antigos preservam os nomes usados nas respectivas versões.

Ao atualizar uma instalação antiga, remova também `TitanPrime.java`, `TitanPrime.class` e `TitanPrime$*.class` antigos antes de copiar e compilar `titanprime.java`. O nome para selecionar na batalha passa a ser `equipe.titanprime`.

## Todos os exemplos da instalação

A versão 3.3 foi testada contra os exemplos autônomos, grupos de adversários e a equipe real de cinco robôs. Há cenários prontos em `battles/equipe-cinco.battle`, `battles/todos-autonomos.battle` e `battles/sentinela.battle`.

Os resultados completos, os exemplos que dependem de controle humano e a correção das posições do treinador estão em [robos-de-exemplo.md](robos-de-exemplo.md). Para repetir automaticamente, use `python tools/samples.py --home /caminho/robocode-testes --output /caminho/resultados`, acrescentando `--ecj /caminho/ecj.jar` se necessário.

## Cores e comemoração (3.4)

Cada batalha sorteia uma paleta, mantida entre seus rounds. Após vencer um round, o Titan gira e troca de cores até o encerramento. Reduza a velocidade do jogo para ver a animação; **Paint** não é necessário. Detalhes e teste em [cores-e-comemoracao.md](cores-e-comemoracao.md).

As referências das 20 técnicas de defesa estão em [referencias-defesa.md](referencias-defesa.md). Os testes de retângulo e KNN ficaram experimentais após não aumentarem as séries vencidas: [defesa-experimentos.md](defesa-experimentos.md).

## Ataque híbrido (3.5)

A versão 3.5 acrescenta KNN por contexto e ondas de observação entre tiros, preservando as cores e a comemoração. No indicador de mira, os modos 5 e 6 são KNN por visitas e KNN de ondas de tiros reais. Os modos 0 a 4 continuam disponíveis. O ganho medido e as derrotas remanescentes estão em [ataque-experimentos.md](ataque-experimentos.md); a relação com as 25 técnicas está em [referencias-ataque.md](referencias-ataque.md).

## Diagnóstico de combate (3.6)

A versão 3.6 acrescenta uma linha de telemetria por round, sem ativar nova estratégia de ataque ou defesa. A ferramenta externa gera CSV de dano nominal, energia investida, scans e associações de impactos: [telemetria.md](telemetria.md). A [arquitetura real](arquitetura.md) distingue os recursos implementados das propostas futuras.
