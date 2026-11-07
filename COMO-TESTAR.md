# Como testar o Sentinela

Primeira versao para o Robocode classico em Java (nao Tank Royale).

1. Instale o Robocode classico com uma versao de Java compativel.
2. Baixe este repositorio em Code > Download ZIP e extraia.
3. Copie a pasta `robots/equipe` deste projeto para a pasta `robots` da instalacao do Robocode.
4. No jogo, abra Robot > Source Editor, abra `equipe/Sentinela.java` e selecione Compiler > Compile.
5. Crie uma batalha em Battle > New com `equipe.Sentinela` contra `sample.Walls`.
6. Use arena 800 x 600, 50 rounds e configuracoes padrao. Repita contra `sample.Corners`, um adversario por vez.

O Sentinela usa radar independente, mira linear, movimento orbital e potencia de tiro ajustavel. E uma base inicial voltada a duelos; ainda nao foi compilado nem avaliado em batalhas. Nao ha desempenho comprovado. A mira linear pode errar contra mudancas de direcao e a deteccao de disparos por energia pode confundir danos com tiros.

Anote a versao do jogo, adversario, rounds, pontuacao e vitorias. Compare cada alteracao nas mesmas condicoes antes de afirmar que o robo melhorou. Salve as evolucoes em commits descritivos no GitHub.
