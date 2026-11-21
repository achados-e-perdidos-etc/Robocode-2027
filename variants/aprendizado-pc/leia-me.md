# Titan Prime 4.41-pc — aprendizado embutido

Variante experimental baseada na 4.38 preservada. O único fonte do robô é [equipe/titanprime.java](equipe/titanprime.java). JAR pronto: [titanprime-4.41-pc.jar](../../releases/titanprime-4.41-pc.jar).

Usa o snapshot íntegro mais recente enviado pelo usuário: `memoria-b.dat`, esquema 2, geração 38, 53.237 bytes. Perfis exatos: `voidious.Diamond 1.8.28`, `campeao.ChimpazeComSarna*`, `titan.TitanV2*` e `obrabo.OBraboBot*`. O diagnóstico cita outros adversários, mas somente esses quatro modelos estavam presentes no snapshot. Nenhum aprendizado ausente foi inventado.

O Java contém os dados compactados em 24 blocos de texto. Sem arquivos locais, carrega os quatro perfis. Havendo memória local válida, escolhe o slot local de maior geração e dá prioridade aos seus perfis. Os demais perfis embutidos são conservados. Gerações de computadores diferentes não são usadas para descartar uma atualização local. O robô continua aprendendo e gravando nos dois arquivos habituais; não reescreve o próprio Java.

Mantém pesos de duas redes por adversário, avaliações de quatro miras, 24 amostras de impactos e 24 visitas defensivas por perfil, além dos contadores salvos. Os nomes precisam coincidir com os vistos pelo radar; outra versão ou pacote é tratada como outro adversário. Os dados embutidos são usados em arena 800×600 e resfriamento 0,1; parâmetros incompatíveis são rejeitados pelo codec, mantendo a inicialização normal ou a memória local compatível.

Importe o JAR e selecione `equipe.titanprime 4.41-pc`. Caso prefira o fonte, copie-o com o nome `titanprime.java`. Preserve sua pasta `.data`. O checkpoint principal continua sendo a 4.38.

Validação técnica: 39 verificações usando o código Java real, restauração das árvores KNN e avaliações das miras, corrupção, prioridades, limites e gravação/recarga; 3 verificações adicionais confirmaram que o codec da 4.38 lê um snapshot produzido pela variante. Compilação completa e integridade do JAR verificadas, bytecode 52. [Relatório](../../tests/embedded-pc-memory/validation.json).

Ainda não foram executadas batalhas competitivas desta variante. Os resultados 50/50 e49/50 pertencem à4.38 e não são atribuídos a este aprendizado importado. Os limites de armazenamento dos arquivos locais continuam valendo.

Para repetir as verificações com seu ZIP e a API do Robocode:

```sh
python tests/embedded-pc-memory/check.py --api /caminho/robocode.jar --data-zip /caminho/titanprime.data.zip
```
