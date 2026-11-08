# TitanV3 original versus Titan Prime 3.8

Foi testado o `TitanV3.jar` fornecido na pasta do Robocode, sem alterar suas classes. Hash SHA-256: `cc0a7ccdc8a481031a49bd95842c8971ba3f964a80c70bdddbdc67d7fb59fee4`. A classe principal declarada e `titan.TitanV3`, versao 3.0. O arquivo contem 54 classes compiladas, bytecode Java 8, e nenhuma fonte `.java`.

O nome TitanV3 nao identifica o projeto TitanVingation estudado anteriormente. Este ensaio avalia o JAR especifico enviado, sem presumir que seja equivalente aquele projeto modular ou aos seus resultados antigos.

## Metodo

Batalhas de dez rounds, duas largadas por cenario, sementes 9001 e 9257. A metrica e o numero de rounds vencidos em todos os rounds, sem preferencia pelos primeiros e sem criterio de melhor de tres. Os resultados de pontuacao tambem foram preservados, mas nao decidem a comparacao.

Robocode 1.11.1, Java 21 no servidor, arena 800 x 600, resfriamento 0,1, inatividade 450. O Titan Prime e a versao 3.8 do commit `eb00e31357b756f376dc63fa1d5e57ff8c4d8662`, sem alteracao de estrategia durante o estudo.

Cada batalha usa um processo Java novo. As pastas externas `.data` sao arquivadas antes da batalha seguinte, fora da instalacao pesquisada pelo motor. Ambos podem aprender durante os dez rounds, mas nao recebem arquivos produzidos por batalhas anteriores do ensaio. O Prime conserva seus pesos neurais incorporados; o TitanV3 conserva os recursos originais do JAR. Nao foi fornecido um historico externo TitanV3.data para restaurar; os arquivos antigos encontrados no ZIP eram de TitanVingation, outra classe, e nao foram misturados.

Foi usada uma copia isolada da instalacao. O JAR, originalmente em subpasta, foi colocado na raiz `robots` dessa copia para que o motor o reconhecesse. A retomada do estudo preservou a primeira batalha ja concluida depois de corrigir a organizacao do arquivo e o arquivamento de pastas de dados aninhadas. Nenhum resultado valido foi substituido.

Os oponentes sao Diamond 1.8.28, SuperMercutio, Diamond + SuperMercutio simultaneamente e o duelo direto entre os dois Titans. No melee, todos tambem se enfrentam; nao e uma equipe coordenada contra o Titan. Nao foram usados exemplos fracos neste ensaio.

Largadas usam `RobotSetup[]`; as posicoes reais sao registradas em todos os rounds, junto dos ajustes feitos pelo motor. Os resultados nao pressupoeem que cada largada solicitada tenha sido respeitada exatamente.

## Reproducao

```bash
python tools/titan_v3.py --home <robocode-de-testes> --ecj <ecj.jar> --jar <TitanV3.jar-original> --output <pasta-nova> --rounds 10 --seeds 9001,9257
```

A instalacao precisa conter uma unica copia desse JAR e os oponentes. O comando cria uma copia isolada, compila somente o clone do Prime e o controlador de batalhas, e executa o TitanV3 original. O JAR do usuario nao e republicado no repositorio.

Se uma execucao for interrompida, `--resume` confere o protocolo e os hashes antes de continuar. Batalhas ja registradas, inclusive invalidas, nao sao repetidas automaticamente. Fontes e rounds diferentes precisam de uma pasta nova. A ferramenta registra vitorias e posicoes para qualquer quantidade de rounds informada em `--rounds`.

## Resultados

As 14 batalhas foram validas, somando **140 rounds**. Todas as vitorias abaixo contam igualmente, do primeiro ao decimo round de cada batalha.

| Confronto | Titan Prime 3.8 | TitanV3 original |
| --- | ---: | ---: |
| Contra Diamond | 0/20 | 0/20 |
| Contra SuperMercutio | 20/20 | 16/20 |
| Contra Diamond + SuperMercutio | 4/20 | 2/20 |
| Duelo direto Prime versus TitanV3 | 17/20 | 3/20 |

Os dois duelos diretos terminaram em 9 a 1 e 8 a 2 para o Prime. Contra as referencias, o Prime somou 24/60 rounds e o TitanV3 18/60. Os duelos diretos sao 20 rounds compartilhados entre os dois participantes, e nao 40 rounds adicionais. A pontuacao nao foi usada para escolher a base.

**Decisao: manter o Titan Prime 3.8 como base.** O TitanV3 nao demonstrou vantagem em nenhum dos tres cenarios com referencias e perdeu a maioria dos rounds do confronto direto. Isso nao prova superioridade universal do Prime, mas nao sustenta substituir a base pelo JAR nesta etapa.

O Prime tambem perdeu todos os 20 rounds contra Diamond nesta bateria mais longa. A vitoria em uma serie curta do estudo anterior nao se traduziu em vitorias nestas largadas; isso reforca a necessidade de melhorar consistencia e evitar conclusoes fortes com poucas batalhas.

O JAR fornecido continua disponivel como adversario e referencia para estudos. Nao foi descompilado, convertido em fonte unica ou incorporado ao Java atual, pois a condicao de demonstrar uma base melhor nao foi atendida. O projeto modular TitanVingation anterior nao foi confundido com este TitanV3.

Os resultados completos, hashes do JAR e da fonte, placares de todos os participantes e posicoes reais em cada round estao em `tests/titan-v3-results.json`. Houve ajustes de largada pelo motor, preservados nesses registros; as comparacoes nao sao uma prova de equivalencia perfeita de todas as posicoes. O Java principal permanece inalterado.
