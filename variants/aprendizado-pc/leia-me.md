# titanprime4.41 — aprendizado embutido

## Memória atualizada em 21/11/2026

O [Java principal](../../robots/equipe/titanprime_v4_41.java) e o [JAR atual](../../releases/titanprime-v4-41.jar) incorporam agora a geração **45** do arquivo `memoria-a.dat`, com **cinco perfis**. A geração 44 do slot B também é válida, mas é anterior. O novo perfil é `equipe.titanprime*`, com 8.392 amostras neurais; os quatro anteriores foram mantidos byte a byte. [Análise dos arquivos, placar e validação](../../tests/embedded-pc-memory/pc-update-2026-11-21/leia-me.md).

A identificação ao iniciar é **titanprime4.41**. O identificador técnico continua `equipe.titanprime_v4_41`, para manter a mesma pasta `robots/equipe/titanprime_v4_41.data`. Não é necessário mover os dados novamente. Com a pasta vazia, a base embutida carrega 5 perfis na geração 45; com arquivos locais válidos, eles continuam tendo prioridade.

O teste recebido foi contra outro Titan: 7/10 e 757 a 485 pontos. Não é uma nova série contra Diamond. O código com o snapshot 45 ainda não recebeu nova comparação competitiva. O [JAR anterior de geração 38](../../releases/checkpoints/titanprime-v4-41-geracao38.jar) e seu [fonte](../checkpoints/4.41-geracao38/equipe/titanprime_v4_41.java) ficam preservados.

## Histórico da geração 38

## Nome com versão visível

Use o [JAR Titan Prime 4.41](../../releases/titanprime-v4-41.jar) ou o [Java identificado](equipe/titanprime_v4_41.java). O nome técnico é `equipe.titanprime_v4_41`; o JAR traz a versão `4.41`. Ao iniciar, o console mostra `titanprime4.41 | codigo carregado`. O nome não contém o sufixo pc.

Ao copiar o fonte, mantenha o nome **titanprime_v4_41.java** em `robots/equipe`. Na compilação de desenvolvimento ele pode aparecer como `equipe.titanprime_v4_41*`; no JAR, selecione `equipe.titanprime_v4_41 4.41`. O número está no próprio nome técnico para identificar a troca nos dois formatos.

Como o nome técnico mudou, a pasta de dados passa a ser `robots/equipe/titanprime_v4_41.data`. O aprendizado embutido é o mesmo. Para levar também atualizações recentes feitas no PC, feche o Robocode e copie o conteúdo de `robots/equipe/titanprime.data` para a nova pasta. Preserve os arquivos antigos como backup.

A mudança é de identificação: o aprendizado e as regras de combate foram mantidos. O JAR foi reconhecido pelo Robocode 1.12.0 e as 39 verificações de memória passaram com a classe renomeada. As comparações abaixo continuam referentes à identificação anterior, sem um novo torneio para esta mudança de nome.

## Versão anterior preservada

Variante experimental baseada na 4.38 preservada. O único fonte do robô é [equipe/titanprime.java](equipe/titanprime.java). JAR pronto: [titanprime-4.41-pc.jar](../../releases/titanprime-4.41-pc.jar).

Usa o snapshot íntegro mais recente enviado pelo usuário: `memoria-b.dat`, esquema 2, geração 38, 53.237 bytes. Perfis exatos: `voidious.Diamond 1.8.28`, `campeao.ChimpazeComSarna*`, `titan.TitanV2*` e `obrabo.OBraboBot*`. O diagnóstico cita outros adversários, mas somente esses quatro modelos estavam presentes no snapshot. Nenhum aprendizado ausente foi inventado.

O Java contém os dados compactados em 24 blocos de texto. Sem arquivos locais, carrega os quatro perfis. Havendo memória local válida, escolhe o slot local de maior geração e dá prioridade aos seus perfis. Os demais perfis embutidos são conservados. Gerações de computadores diferentes não são usadas para descartar uma atualização local. O robô continua aprendendo e gravando nos dois arquivos habituais; não reescreve o próprio Java.

Mantém pesos de duas redes por adversário, avaliações de quatro miras, 24 amostras de impactos e 24 visitas defensivas por perfil, além dos contadores salvos. Os nomes precisam coincidir com os vistos pelo radar; outra versão ou pacote é tratada como outro adversário. Os dados embutidos são usados em arena 800×600 e resfriamento 0,1; parâmetros incompatíveis são rejeitados pelo codec, mantendo a inicialização normal ou a memória local compatível.

Importe o JAR e selecione `equipe.titanprime 4.41-pc`. Caso prefira o fonte, copie-o com o nome `titanprime.java`. Preserve sua pasta `.data`. O checkpoint principal continua sendo a 4.38.

Validação técnica: 39 verificações usando o código Java real, restauração das árvores KNN e avaliações das miras, corrupção, prioridades, limites e gravação/recarga; 3 verificações adicionais confirmaram que o codec da 4.38 lê um snapshot produzido pela variante. Compilação completa e integridade do JAR verificadas, bytecode 52. [Relatório](../../tests/embedded-pc-memory/validation.json).

Comparação preliminar de 21/11/2026: a 4.41-pc venceu 50/50 rounds distribuídos entre quatro adversários; a 4.38 venceu 49/50. Contra Diamond foram 20/20 versus 19/20; os demais tiveram 10 rounds por versão. A participação de pontos melhorou contra Diamond, Chimpanzé e TitanV2, mas caiu contra OBraboBot. Amostras pequenas com largadas independentes não comprovam uma melhoria permanente. [Resultados completos](../../tests/embedded-pc-memory/comparison-2026-11-21/leia-me.md). O código principal 4.38 continua preservado.

Para repetir as verificações com seu ZIP e a API do Robocode:

```sh
python tests/embedded-pc-memory/check.py --api /caminho/robocode.jar --data-zip /caminho/titanprime.data.zip
```

O mesmo Java identificado também está disponível na pasta principal: [robots/equipe/titanprime_v4_41.java](../../robots/equipe/titanprime_v4_41.java), acompanhado das [propriedades](../../robots/equipe/titanprime_v4_41.properties). As duas cópias têm exatamente o mesmo conteúdo.
