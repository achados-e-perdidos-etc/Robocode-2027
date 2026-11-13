# Instalar e testar o Titan Prime 4.15

Use **Robocode clássico**. A classe para selecionar é `equipe.titanprime`. O robô é uma adaptação licenciada do Diamond aberto e não precisa do JAR do adversário para compilar ou funcionar. Para enfrentá-lo, instale o Diamond separadamente.

Baixe o [JAR 4.15](releases/titanprime-4.15.jar) para importar pelo Robocode, ou compile o fonte conforme abaixo. A 4.15 acrescenta memória entre batalhas; os resultados 49/50 e 48/50 pertencem ao [checkpoint 4.8](releases/titanprime-4.8.jar).

Use Robocode **1.12.0** com Java **17**. Seu log mostrava a versão 1.11.1, com um problema conhecido no encerramento de robôs. Veja o [diagnóstico](diagnostico-java17.md). Instale a versão nova em uma pasta separada e preserve a anterior.

## Instalação no Windows

1. Feche o Robocode e copie [robots/equipe/titanprime.java](robots/equipe/titanprime.java) para `C:\robocode\robots\equipe\titanprime.java`.
2. Guarde uma cópia do diagnóstico antigo se desejar. Remova somente os antigos `titanprime.class` e `titanprime$*.class` dessa pasta para evitar misturar compilações. Não remova os robôs de exemplo.
3. Abra o jogo com o Java 17 que você já configurou. No editor, abra o novo fonte e compile.
4. Em **Battle > New**, pressione **Ctrl+R** se necessário e selecione `equipe.titanprime` ou `equipe.titanprime*`.

Muitos `.class` são esperados: todas as classes internas saem do mesmo Java. Copiar apenas a classe principal compilada deixa dependências faltando.

Se preferir compilar no Prompt de Comando, com o JDK 17 no PATH:

```bat
cd /d C:\robocode
javac --release 8 -encoding UTF-8 -cp libs\robocode.jar -d robots robots\equipe\titanprime.java
```

`--release 8` gera bytecode 52, evitando o erro de classe 61 quando uma instalação acaba abrindo pelo Java 8. A persistência desta atualização foi verificada no Robocode 1.12.0 com Java 21 e Temurin 17.0.21+9; use Java 17 no seu PC.

## Avaliar contra Diamond

Selecione uma cópia do Prime e **voidious.Diamond 1.8.28**, arena **800 × 600**, resfriamento **0,1**, inatividade **450** e **30 rounds**. O cenário [battles/duelo-diamond.battle](battles/duelo-diamond.battle) usa essas regras.

Guarde pontos e `1sts`. Se a soma de `1sts` ultrapassar 30, houve primeiras colocações simultâneas; o validador desconta esse excesso das vitórias do Prime. Repita em novas batalhas para avaliar variação, conservando também resultados ruins.

Para repetir o protocolo automático numa instalação separada, com Python 3 e JDK:

```sh
python tools/elite_duel.py --home /caminho/robocode --opponent /caminho/voidious.Diamond_1.8.28.jar --output /caminho/novo-lote --random-starts
```

Se usar Eclipse ECJ, acrescente `--ecj /caminho/ecj.jar`. A ferramenta copia apenas `libs` e `config`, instala os dois participantes, compila a classe canônica e cria o plano antes de começar. Não modifica sua instalação. Não sobrescreve lotes anteriores. No modo `--random-starts`, o motor sorteia as posições a cada round. Os números dos lotes não são sementes de aleatoriedade. Sem essa opção, a ferramenta permite reproduzir os estudos antigos de largadas fixas. Veja o [relatório](ciclo-elite.md).

Para vários adversários, adicione os inimigos desejados e somente uma cópia do Prime. Em todos contra todos eles também se enfrentam. Os demais cenários em `battles` ajudam a montar confrontos, mas seus resultados históricos não validam automaticamente a base atual.

## Cores, pintura e diagnóstico

Cada batalha sorteia uma paleta, mantida entre rounds; cores podem se repetir por acaso em batalhas diferentes. Ao vencer um round, o Prime gira corpo e canhão e anima as cores até o encerramento. Não é preciso ativar Paint para isso.

Para desenhar as estimativas, habilite **Paint** na janela do robô. As teclas **R**, **G** e **M**, encaminhadas pelo Robocode, alternam radar, mira e movimento. A tecla V pertence ao motor antigo. A pintura representa informações observadas e hipóteses de ondas, sem acesso às balas ocultas.

Após os rounds, encontre `C:\robocode\robots\equipe\titanprime.data\diagnostico.csv`. Ele guarda até 48 KB recentes, com vitória, tiros, acertos, gasto de energia, dano nominal, paredes e turnos perdidos. O esquema 4.0 substitui um arquivo 3.x existente. A [arquitetura](arquitetura.md) explica os limites dos campos.

Nessa mesma pasta, `memoria-a.dat` e `memoria-b.dat` guardam modelos recarregados na próxima batalha. Mantenha esses arquivos para conservar o aprendizado. O [guia de memória](aprendizado.md) explica a cota, o arquivador externo opcional e a recuperação de checkpoints.

## Verificações do código

```sh
python tests/elite_checks.py --api /caminho/robocode/libs/robocode.jar
python tests/diagnostic_persistence.py --home /caminho/robocode --ecj /caminho/ecj.jar --output /caminho/nova-verificacao
```

O primeiro aceita `--ecj` se necessário. O segundo usa adversário passivo somente para conferir gravação, vitória tardia e rotação do CSV; não mede força competitiva. A lista de [testes atuais](tests/readme.md) separa essas verificações dos treinadores históricos 3.x, incompatíveis com o novo motor.
