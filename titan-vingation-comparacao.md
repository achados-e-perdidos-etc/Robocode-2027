# TitanVingation original versus Titan Prime 3.8

Foi avaliado o `titan-vingation.jar` recebido na pasta do Robocode. A classe e `titan.TitanVingation`, versao **1.7**, hash SHA-256 `742d901f78391a697ef70359c5ee591e4d0abc7cf81ffbed29a0220404d1d8bc`. O JAR nao inclui fontes Java. O projeto modular fornecido separadamente declara versao 1.2 em suas propriedades; ele nao foi recompilado como substituto do JAR 1.7.

O controle e o Titan Prime 3.8, sem mudanca de estrategia, pesos ou configuracao. Foi mantido o mesmo protocolo da comparacao com TitanV3: batalhas de dez rounds, todas as vitorias com o mesmo peso, sementes 9001 e 9257, arena 800 x 600, Robocode 1.11.1, Java 21 no servidor, resfriamento 0,1 e inatividade 450.

## Dois estados de aprendizado do Vingation

A pasta recebida contem seis arquivos `.dat` e um CSV de diagnostico para a classe TitanVingation. Por isso foram avaliados:

- **Vingation limpo:** nenhum arquivo externo de aprendizado no inicio da batalha.
- **Vingation com dados recebidos:** os sete arquivos da pasta `TitanVingation.data` sao restaurados antes de cada batalha, sempre a partir do mesmo estado recebido.

Cada batalha usa um processo Java novo. Todos podem aprender entre os dez rounds. As pastas `.data` produzidas sao arquivadas fora da instalacao antes da batalha seguinte; o aprendizado do ensaio anterior nao passa para o proximo. O Prime conserva seus pesos neurais incorporados. Nenhum arquivo recebido foi alterado ou publicado: apenas os hashes e os tamanhos entram no relatorio de resultados.

Os JARs de outros Titans foram removidos somente da copia isolada da instalacao para evitar conflitos entre classes do pacote `titan`. O original permaneceu intacto.

## Cenarios

Contra Diamond, SuperMercutio e ambos simultaneamente, cada uma das tres configuracoes realiza duas batalhas de dez rounds: 180 rounds no total. O Prime tambem enfrenta cada estado do Vingation em duas batalhas diretas: mais 40 rounds compartilhados entre os participantes. O total previsto e de 22 batalhas, **220 rounds**.

No melee o Titan entra sozinho, mas Diamond e SuperMercutio tambem se enfrentam; nao e uma equipe coordenada. Nenhum exemplo fraco e usado neste ensaio. A contagem principal e de rounds vencidos, sem criterio de melhor de tres, sem preferencia pelos primeiros e sem escolher por pontuacao.

As largadas sao solicitadas por `RobotSetup[]`, e as posicoes reais sao registradas em cada round. Ajustes feitos pelo motor permanecem nos dados. Mesmo com largadas solicitadas iguais, a execucao e o escalonamento do motor podem variar; nao se afirma determinismo nem pareamento perfeito.

## Reproducao

```bash
python tools/titan_vingation.py --home <robocode-de-testes> --ecj <ecj.jar> --jar <titan-vingation.jar-original> --saved-data <pasta-TitanVingation.data-recebida> --output <pasta-nova> --rounds 10 --seeds 9001,9257
```

A ferramenta copia a instalacao, instala o JAR original, compila o clone do Prime e o controlador de batalhas e preserva os dados gerados em cada caso. `--resume` verifica o protocolo, as fontes, o JAR e os hashes do modelo recebido antes de continuar; nao substitui automaticamente nenhuma batalha ja registrada, inclusive invalida.

Para reproduzir a fonte exata do Prime, use a versao do commit `eb00e31357b756f376dc63fa1d5e57ff8c4d8662`, cujo SHA-256 e `5c73b414f1bd1e9ac33b29e66a33fb0a0801fa9ffd9da7101a1467e7c5b0aa98`. A ferramenta le o Java em `robots/equipe/titanprime.java`; se ele mudar futuramente, sera um ensaio de outra versao.

## Resultados

As **22 batalhas foram validas, com 220 rounds**. Nao houve partida invalida substituida. As vitorias abaixo incluem todos os rounds.

| Referencia | Prime 3.8 | Vingation limpo | Vingation com dados recebidos |
| --- | ---: | ---: | ---: |
| Diamond | 1/20 | 1/20 | 2/20 |
| SuperMercutio | 19/20 | 19/20 | 19/20 |
| Diamond + SuperMercutio | 6/20 | 7/20 | 5/20 |
| Total contra referencias | 26/60 | 27/60 | 26/60 |

Nos duelos diretos:

- Prime versus Vingation limpo: **16 a 4**, com placares de 8 a 2 nas duas largadas.
- Prime versus Vingation com dados: **15 a 5**, com placares de 9 a 1 e 6 a 4.

Para comparar cada variante com o Prime usando quatro confrontos de vinte rounds, somamos os 60 contra referencias aos respectivos vinte do duelo direto. Contra o limpo, o Prime ficou em **42/80** e o Vingation em **31/80**. Contra a variante com dados, o Prime ficou em **41/80** e o Vingation em **31/80**. Os mesmos 60 rounds do controle sao reutilizados nessas duas comparacoes; nao sao duas amostras independentes do Prime e nao devem ser somados como novas batalhas.

**Decisao: manter Titan Prime 3.8 como base.** O Vingation nao foi inferior em todos os cenarios: o limpo venceu um round adicional no melee, e o historico recebido acrescentou um round contra Diamond. Entretanto, nao apareceu uma vantagem geral que justificasse substituir a base atual, e o Prime venceu a maioria dos duelos diretos. Essas pequenas diferencas contra referencias usam apenas duas largadas por cenario; nao demonstram superioridade estatistica universal.

Os dados recebidos nao melhoraram todos os resultados: o Vingation com historico ganhou um round contra Diamond, mas perdeu dois no melee em relacao a versao limpa. Nao copiamos seus arquivos `.dat` para o Prime, cujos modelos e formatos sao diferentes. O JAR e o historico foram preservados para novos estudos, sem incorporar codigo ou reescrever o Java atual.

Os placares, hashes do JAR e da fonte, hashes e tamanhos dos sete arquivos recebidos, ajustes de largada e posicoes reais em todos os rounds estao em `tests/titan-vingation-results.json`. Os arquivos recebidos continuam com os mesmos hashes depois do estudo. O nome de auditoria `treino.primev3*` identifica o clone do Prime 3.8 usado pelo controlador; nao e o TitanV3 antigo.
