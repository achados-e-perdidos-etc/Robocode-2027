# Validação do Nêmesis 2.0

Testes feitos em 7 de novembro de 2026. Robocode clássico **1.12.0**, OpenJDK **21.0.13**, compilador Eclipse **3.37.0**, bytecode Java 8. Arena 800 × 600, resfriamento 0,1, inatividade 450 ticks. Cada cenário começa uma batalha independente e permite aprendizado entre os seus rounds. Posições iniciais aleatórias, sem semente fixada. CPU constante calibrada pelo motor: 5.344.295 ns. Não é um estudo estatístico com várias sementes.

## Resultados da versão final

| Adversários | Rounds | Primeiras colocações do Nêmesis | Pontuação do Nêmesis | Colisões com paredes | Turnos perdidos |
| --- | ---: | ---: | ---: | ---: | ---: |
| Walls | 30 | 30 | 4880 | 0 | 0 |
| Corners | 30 | 30 | 5238 | 0 | 0 |
| Tracker | 30 | 30 | 5368 | 0 | 0 |
| Walls + Tracker | 30 | 30 | 9324 | 0 | 1 |
| Walls + Tracker + Corners | 30 | 30 | 11734 | 0 | 0 |
| Diamond | 20 | 0 | 246 | 0 | 1 |

Os cinco cenários com robôs de exemplo somam **150 primeiras colocações em 150 rounds**. São adversários básicos; esse resultado não demonstra força contra robôs competitivos. Os testes com dois e três adversários são todos contra todos, não equipes coordenadas.

Contra **Diamond**, o Nêmesis perdeu todos os 20 rounds. A mira e a defesa ainda estão longe desse nível. Referência utilizada: código original de [Voidious/Diamond](https://github.com/Voidious/Diamond/tree/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb), compilado separadamente sem alterar sua estratégia. Os nomes terminados em `*` identificam versões em desenvolvimento. Nenhum código do Diamond faz parte do Nêmesis.

Houve turnos perdidos nos cenários indicados na tabela. O custo de CPU depende da máquina e merece acompanhamento. Nenhuma exceção foi observada nos logs finais. Colisões zero nesta amostra não garantem que nunca ocorram.

## Verificações de implementação

- Compilação do único arquivo fonte com a API real do Robocode aprovada.
- `tests/core_checks.py` aprovado: aceleração, frenagem, inversão fracionária, limites de velocidade, segmentos, expansão de ondas, normalização e inversão de GuessFactors, mudança de padrão aprendido e isolamento entre adversários.
- Um robô auxiliar temporário comparou **220 passos previstos** com o movimento real executado pelo motor: **zero divergências**, tolerância de 1e-7 para velocidade e posição. Esse teste verifica o integrador físico, não demonstra que as previsões das ações inimigas são corretas.
- Logs `learn` confirmaram preservação dos histogramas entre rounds. No confronto final com Diamond, o histórico terminou com 1.293 amostras de mira e 330 de defesa. As amostras de defesa incluem impactos e colisões entre balas.
- A tentativa inicial com cadastro incorreto do robô foi descartada; todos os resultados desta tabela confirmaram dois, três ou quatro participantes, conforme o cenário.

## Evidência e repetição

[tests/results.json](tests/results.json) contém pontuações de todos os participantes, contadores por round e SHA-256 do código testado. Os arquivos em `battles/` permitem repetir os cenários. O cenário Diamond depende de instalação separada desse adversário. Resultados variarão com as posições iniciais e o ambiente.

A execução automática usou a API `RobocodeEngine`, com um observador de resultados e saídas dos robôs. Uma execução equivalente com a interface de linha de comando do jogo, a partir da pasta de instalação, é:

```sh
java -Xmx512m -Djava.awt.headless=true -cp "libs/*" robocode.Robocode -nodisplay -nosound -battle battles/duelo-walls.battle -results resultado-walls.txt
```

Confirme que o arquivo de resultado inclui `equipe.Nemesis` e o adversário antes de aceitar a medição. Um erro de seleção pode iniciar uma batalha incompleta.

## Limites atuais

O aprendizado é estatístico e fica na memória da batalha; não é uma rede neural nem persiste entre partidas independentes. As ondas dependem de observações do radar e ficam menos confiáveis em melee. O modelo de defesa usa interseção aproximada com raio do robô; não implementa sombras de balas, interseção precisa do retângulo, clustering ou os modelos completos dos campeões. A previsão de inimigos supõe continuidade por um horizonte limitado, não conhece suas decisões futuras.

O próximo ganho deve vir de testes contra adversários mais variados, aprimoramento da mira contra mudanças de direção, perfis de defesa e tratamento das incertezas do radar. Esta versão entrega a primeira implementação funcional das técnicas, não um título de campeão.
