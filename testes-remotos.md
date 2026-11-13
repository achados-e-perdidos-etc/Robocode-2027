> Histórico das versões 3.x. O código atual 4.x usa outra base; veja [arquitetura.md](arquitetura.md) e [ciclo-elite.md](ciclo-elite.md).

# Novas batalhas remotas do Titan Prime 3.7

Esta bateria substitui os testes que nao puderam ser concluidos no computador do usuario. O Java do robo nao foi alterado nem treinado entre os cenarios. A versao de origem e o commit `59b0969d49371a11a6369a24a4d813cdb2d72de4`.

Protocolo: Robocode 1.11.1 da instalacao fornecida, Java 21 no servidor, bytecode Java 8, arena 800 x 600, resfriamento 0,1, inatividade 450, tres rounds por batalha. Ganha a serie quem vence pelo menos dois rounds. Largadas solicitadas pelas sementes novas 5801, 6007 e 6211, com posicoes reais registradas em cada round. Os ajustes do motor sao preservados; nao se pressupoe que todas as largadas solicitadas tenham sido respeitadas.

Cada batalha independente comeca com os mesmos modelos neurais pre-treinados e memoria por adversario vazia. O aprendizado entre os tres rounds continua ativo. A configuracao e a de uso: finalizacao ativa, miras adicionais experimentais desligadas. Nao houve selecao de estrategia com estes resultados.

## Reproducao

Use uma copia de testes do Robocode com os adversarios instalados. O programa compila clones de auditoria fora do repositorio; o produto continua em um unico Java.

```bash
python tools/samples.py --home <robocode> --ecj <ecj.jar> --output <pasta-externa> --label remotetests --seeds 5801,6007,6211
python tools/elite.py --home <robocode> --ecj <ecj.jar> --output <outra-pasta-externa> --seeds 5801,6007,6211
```

O segundo comando precisa de `voidious.Diamond 1.8.28` e `reference.supermercutio*`, instalados separadamente. Codigo de adversarios nao e distribuido neste repositorio. `--parse-only` reprocessa o log da suite correspondente; no ensaio elite o protocolo e o hash da fonte precisam coincidir com os originais.

Os grupos melee sao todos contra todos: Titan participa sozinho, mas os inimigos tambem se enfrentam. O teste com MyFirstTeam e diferente: cinco adversarios pertencem a mesma equipe e o Titan entra sozinho.

As demonstracoes passivas, interativas e dependentes sao separadas das series competitivas. Slave nao pode ser iniciado sozinho; sua tentativa invalida nao conta como vitoria.

## Resultados dos exemplos

85 tentativas, 84 series validas e 252 rounds validos. Apenas Slave falhou na inicializacao, como esperado.

| Categoria competitiva | Series vencidas | Rounds vencidos | Turnos pulados |
| --- | ---: | ---: | ---: |
| Duelos com 16 exemplos | 48/48 | 144/144 | 1 |
| Grupos melee | 12/12 | 36/36 | 0 |
| Equipe de cinco | 3/3 | 8/9 | 0 |
| Total competitivo | 63/63 | 188/189 | 1 |

Nos grupos melee foram incluidos os 16 exemplos autonomos simultaneamente. Os demais testes validos foram seis series de demonstracoes passivas, seis interativas sem intervencao humana, seis dependentes e tres com sentinela. Todos terminaram em tres vitorias de round do Titan, mas ficam fora do total competitivo. Houve dois turnos pulados adicionais na categoria passiva. Nenhum tiro foi direcionado a sentinela.

Cinco series competitivas tiveram ajustes de largada: dois duelos e tres melee. Os dados e as posicoes observadas estao em `tests/remote-samples.json`. A melhora de 187 para 188 rounds em relacao a bateria anterior ocorreu sem mudanca no robo, com outras largadas; nao demonstra uma melhoria de codigo.

## Resultados contra referencias fortes

| Cenario | Series vencidas | Rounds vencidos | Turnos pulados |
| --- | ---: | ---: | ---: |
| Diamond 1.8.28 | 0/3 | 1/9 | 1 |
| SuperMercutio | 3/3 | 8/9 | 0 |
| Diamond + SuperMercutio simultaneamente | 0/3 | 0/9 | 0 |

Foram nove series validas e 27 rounds adicionais. Os dados, incluindo auditoria de tiros, scans, acertos e posicoes, estao em `tests/remote-elite.json`. O confronto simultaneo e todos contra todos, sem aliados do Titan. Nao e uma equipe de elite coordenada.

Vencer um round contra Diamond nao representa vencer uma melhor de tres. O resultado segue sendo zero series vencidas contra ele; a mudanca em relacao a testes anteriores nao foi causada por alteracao de codigo nesta etapa.

As duas baterias somam **94 tentativas, 93 series validas e 279 rounds validos**. As 72 series competitivas somam 66 vitorias do Titan: 63 contra os exemplos e tres contra SuperMercutio. As seis derrotas competitivas, tres contra Diamond e tres na arena forte, foram preservadas. A tentativa invalida de Slave permanece fora desses totais.

A proxima investigacao deve concentrar-se em acertos e exposicao a tiros nos confrontos fortes, especialmente no melee com Diamond. Estes resultados, por si sos, nao identificam qual subsistema causou cada derrota. Nenhuma estrategia foi promovida, nenhum peso neural foi substituido e nenhum comentario foi adicionado ao Java do robo.
