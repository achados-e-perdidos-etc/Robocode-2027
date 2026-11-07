# Como instalar e testar o Nêmesis

Você precisa do **Robocode clássico** e do Java. Este arquivo não serve para o Tank Royale. O código usa sintaxe Java 8 e foi validado no Java 21 com Robocode 1.12.0.

## Colocar o robô no jogo

1. No GitHub, abra **Code > Download ZIP** e extraia o projeto.
2. Abra a pasta de instalação do seu Robocode e entre em `robots`.
3. Crie a pasta `equipe`, se ainda não existir.
4. Copie somente `robots/equipe/Nemesis.java` do projeto para essa pasta. O caminho final deve ser `robocode/robots/equipe/Nemesis.java`.
5. Se você instalou a versão anterior, remova os arquivos `Sentinela.java`, `Sentinela.class` e eventuais `Sentinela$*.class` da sua instalação para não selecionar o robô antigo.
6. No jogo, abra **Robot > Source Editor**, abra `Nemesis.java` e selecione **Compiler > Compile**.
7. Em **Battle > New**, procure `equipe.Nemesis` (pode aparecer com `*`, indicando uma versão em desenvolvimento).

O compilador gera vários arquivos `.class` para as classes internas. Isso é normal: todo o código fonte continua em um único `.java`.

## Primeiras batalhas

Comece com arena **800 × 600**, resfriamento **0,1** e **30 rounds**, mantendo as demais regras padrão. Teste separadamente:

- Nêmesis contra `sample.Walls`.
- Nêmesis contra `sample.Corners`.
- Nêmesis contra `sample.Tracker`.
- Nêmesis contra `sample.Walls` e `sample.Tracker` juntos.
- Nêmesis contra `sample.Walls`, `sample.Tracker` e `sample.Corners` juntos.

Os arquivos `.battle` prontos estão na pasta `battles`. Copie-os para `robocode/battles` e abra no jogo. Eles usam o padrão `equipe.Nemesis*` para localizar o robô em desenvolvimento. Se você exportar uma versão com outro identificador, ajuste a seleção no arquivo ou na janela de batalha.

Esses testes com vários adversários são **todos contra todos**. Não são equipes coordenadas de dois ou três contra um. O robô considera todos os outros participantes inimigos; não implementa comunicação com aliados. Para avaliar desvantagem contra equipes reais, será necessário configurar essas equipes e registrar outro teste.

O cenário opcional `duelo-diamond.battle` exige instalar o [Diamond](https://github.com/Voidious/Diamond) separadamente. O projeto não inclui código nem binário dele.

## Ver a percepção do robô

Durante a batalha, abra a janela do Nêmesis e habilite **Paint**. Os nomes exatos podem variar com a versão do jogo. A tecla **V**, quando encaminhada ao robô pelo jogo, alterna o desenho.

- Círculos vermelhos: ondas de tiros estimadas.
- Quadrado amarelo: alvo escolhido; cinza: outros adversários observados.
- Linha azul: destino escolhido pela avaliação de risco.
- `E`: energia conhecida; `age`: ticks desde o último scan.

Os desenhos representam estimativas disponíveis ao robô, não revelam informações ocultas do jogo. Os dados de depuração também mostram o modo da mira: 0 direta, 1 linear, 2 circular, 3 GuessFactor.

## Comparar versões

Registre a versão do Robocode, versão do código, adversários, regras, rounds, pontuação e primeiras colocações. Repita as mesmas condições, idealmente com várias sementes ou lotes: as posições iniciais influenciam os resultados. Uma única vitória não demonstra melhora.

No console do robô, cada round registra tiros, acertos, ondas detectadas, ondas associadas a balas, colisões com paredes e turnos perdidos. As linhas `learn` mostram o histórico acumulado por inimigo. As amostras de defesa incluem balas que acertaram o robô e colisões entre balas.

## Verificação numérica opcional

Com Python 3 e um JDK instalado:

```sh
python tests/core_checks.py --api /caminho/robocode/libs/robocode.jar
```

Se usar o compilador Eclipse em vez de `javac`, acrescente `--ecj /caminho/ecj.jar`. Esse teste gera seu auxiliar Java em uma pasta temporária; o robô continua com um único arquivo fonte no projeto.
