# Revisao de gustavoolyver/Titan-Robo

**Etapa posterior:** as correcoes reproduziveis e as batalhas contra todos os sete robos estao em [recuperacao-e-tiro.md](recuperacao-e-tiro.md). A revisao abaixo descreve os commits originais, anteriores a correcao.

Os dois links recebidos identificam **duas branches do mesmo repositorio**, nao dois projetos independentes. A revisao foi fixada nestes commits:

- `main`: `70f1af3fc38ff665c7cd5d2a40c99320ba022ba3`.
- `Titan`: `ebe7079b963bf66fba95175684e07205118e359a`.

Foram lidos seis fontes unicos na main e o projeto modular da branch Titan. Os 26 arquivos de texto obtidos foram verificados contra os hashes dos blobs Git originais. A revisao incluiu compilacao e os testes simulados fornecidos; nao houve novas batalhas dessas versoes e nenhum codigo delas foi incorporado ao Prime.

## Conteudo e verificacao

| Arquivo ou branch | O que encontramos | Verificacao realizada |
| --- | --- | --- |
| `main/Java.java` | Robo de duelo, classe publica `ChimpazeComSarna`, kNN e modelo especializado de movimento associado a Kader | Compila ao usar o nome de arquivo `ChimpazeComSarna.java`; o nome original nao corresponde a classe publica |
| `main/OBraboBot.java` | Q-learning tabular, acoes de movimento e recompensas por eventos | Falha de compilacao: string literal quebrada na linha 631 |
| `main/TitanVingation.java` | Versao monolitica estatistica com persistencia | Compila |
| `main/Titan.java` | Seis miras, kNN limitado e checkpoints alternados | Compila |
| `main/TitanV2.java` | Cobertura estimada por outros robos e estrategia de baixa energia | Compila |
| `main/Titans.java` | Recuperacao de bloqueios, previsao curta de colisao, cobertura, calibracao de tiros e checkpoints por etapas | Compila |
| Branch `Titan` | Projeto modular com metadados versao 1.0 e testes de regressao | Compila; 26 verificacoes simuladas passaram |

As compilacoes usaram ECJ, alvo Java 8, UTF-8 e a API Robocode 1.11.1; os testes executaram no Java 21. Em `Java.java` ajustamos somente o nome do arquivo na copia de compilacao. Nao corrigimos nem publicamos alteracoes no repositorio externo. Os fontes e binarios externos tambem nao foram redistribuidos no nosso projeto.

O relatorio da branch Titan declara explicitamente que nao executou batalhas no motor nem verificou escrita no sandbox real. Confirmamos os 26 testes simulados, sem transforma-los em evidencia de vitorias. Titulos como Grandmaster e comentarios de cabecalho nao foram usados para classificar desempenho.

## Ideias que vale aproveitar

### Recuperacao quando o robo fica preso

Em `Titans.java`, `updateMobility` distingue a intencao de mover da falta real de deslocamento, acompanha contatos e dispara uma recuperacao limitada. `beginEscape` e `escapeMove` planejam a saida. Isso e mais especifico que somente evitar paredes ou repulsar inimigos no planejamento.

Para o Prime, e uma boa candidata a experimento contra colisao repetida e encurralamento. Precisamos distinguir uma parada deliberada para esquivar de um bloqueio real, para nao desfazer uma boa decisao defensiva. O novo posicionamento da 3.9 ja melhora a avaliacao de cercamento, mas ainda nao tem esse controlador dedicado de recuperacao.

### Tiro bloqueado e alvo oportunista

A funcao `blocked` rejeita tiros quando outro robo aparece entre o canhao e o alvo escolhido. Pode ajudar a evitar gastar energia tentando atingir um alvo oculto. O filtro atual do arquivo usa uma observacao recente e geometria instantanea; o bloqueador pode sair antes da chegada da bala.

Uma adaptacao util seria comparar a interceptacao do bloqueador e a troca de alvo, em vez de cancelar automaticamente todo tiro: acertar outro inimigo tambem pode ser vantajoso em melee. A mudanca precisa de comparacao contra a nossa mira e selecao de alvos atuais.

### Cobertura por outros robos

`shieldConfidence` procura um terceiro robo entre o atirador e uma posicao protegida, estima seu deslocamento e reduz a confianca quando o tempo de voo ou a idade do scan aumentam. A verificacao considera tambem a distancia ja percorrida pela onda.

Isso e **cobertura geometrica estimada por corpos de robos**, nao Bullet Shielding por disparos proprios nem Bullet Shadowing. Nao revela onde esta a bala inimiga nem garante que ela sera interceptada. Vale comparar com o nosso avaliador de fogo cruzado, usando reducao de risco limitada, rotas de saida e observacoes recentes.

### Memoria persistente com recuperacao

`Titan.java`, `TitanV2.java` e `Titans.java` usam dois slots de snapshots, versoes de formato, geracoes, compactacao GZIP, verificacao de leitura e limites de tamanho. O carregamento escolhe a copia valida mais recente. O trabalho de checkpoint pode ser dividido entre ticks para reduzir pausas.

O desenho e util para preservar aprendizado entre batalhas, algo que o Prime ainda nao faz automaticamente. A implementacao deve ser propria do nosso formato: esses modelos usam, por exemplo, 31 bins e sete caracteristicas, enquanto o Prime usa 47 bins e vetores de 12 ou 16 caracteristicas. Copiar arquivos de modelo diretamente nao seria uma migracao valida.

Tambem precisamos medir custo de CPU, cota real, recuperacao apos escrita interrompida e como avaliar batalhas com historicos controlados. A copia da lista de entradas de um checkpoint nao congela profundamente todos os objetos de modelo; checkpoints incrementais podem incluir modelos de momentos diferentes. Isso deve ser definido no formato, sem prometer um snapshot global instantaneo.

### Baixa energia e atribuicao de aprendizado

O modo chamado `healing` em Titans tenta aproveitar o bonus de energia por acerto. Nao e regeneracao automatica. A decisao depende de uma estimativa de precisao, que precisa ser calibrada; uma confianca exagerada pode consumir a energia restante.

O Q-learning de OBraboBot e uma referencia para selecao de acoes, mas primeiro precisa compilar. A atribuicao temporal das recompensas tambem merece revisao: o impacto de uma bala pode ocorrer durante outra macro-acao, diferente daquela que originou o disparo. Nao substituimos a selecao do Prime apenas por haver uma tabela Q.

## O que nao deve ser tratado como novidade pronta

O Prime ja tem mira linear/circular, GuessFactor, kNN, miras virtuais, ondas, aprendizado por adversario, controle de potencia, radar de duelo/melee e posicionamento por risco. Reintroduzir esses nomes como novas tecnicas duplicaria sistemas existentes.

`Java.java` tem uma hipotese especializada de movimento para Kader, validada por erro observado; esse modelo nao deve ser generalizado como conhecimento exato de qualquer inimigo. Os nomes iguais tambem nao estabelecem equivalencia: a branch modular declara versao 1.0, o ZIP de fontes anterior declara 1.2 e o JAR que testamos era 1.7. O monolitico da main e outra implementacao. Os placares dos JARs anteriores nao sao resultados desses fontes.

## Decisao

Mantivemos **Titan Prime 3.9**, com o novo posicionamento validado e os outros tres ajustes da ultima etapa como experimentais. A revisao nao justifica trocar a base sem batalhas comparativas destas versoes.

As candidatas mais claras para um proximo par de experimentos sao **recuperacao de bloqueios/colisoes** e **decisao de tiro quando existe um bloqueador**, preservando a mira atual. Persistencia recuperavel e cobertura por terceiros ficam como estudos separados, pois mudam outras condicoes de combate e avaliacao.

O inventario, hashes e resultados de compilacao/teste estao em `tests/repository-review.json`. O resultado da etapa concluida do Prime, 76/144 rounds contra 71/144 da base, esta em [quatro-melhorias.md](quatro-melhorias.md).

## Fontes examinadas

- [main, revisao fixada](https://github.com/gustavoolyver/Titan-Robo/tree/70f1af3fc38ff665c7cd5d2a40c99320ba022ba3).
- [Titans.java — implementacoes de cobertura, bloqueio, recuperacao e memoria](https://github.com/gustavoolyver/Titan-Robo/blob/70f1af3fc38ff665c7cd5d2a40c99320ba022ba3/Titans.java).
- [OBraboBot.java — erro de string](https://github.com/gustavoolyver/Titan-Robo/blob/70f1af3fc38ff665c7cd5d2a40c99320ba022ba3/OBraboBot.java#L631).
- [branch Titan, revisao fixada](https://github.com/gustavoolyver/Titan-Robo/tree/ebe7079b963bf66fba95175684e07205118e359a).
- [Relatorio de validacao modular](https://github.com/gustavoolyver/Titan-Robo/blob/ebe7079b963bf66fba95175684e07205118e359a/titan-vingation/docs/validation-report.md).
