# Cores por batalha e comemoração

No Titan Prime 3.4, cada nova batalha sorteia uma paleta para corpo, canhão, radar, bala e varredura. A cor base fica guardada em memória estática e se mantém entre os rounds daquela batalha. Ao iniciar outra batalha, o Robocode carrega uma nova instância e faz outro sorteio.

O sorteio usa um gerador separado da lógica de combate e não grava arquivos. Uma cor parecida pode reaparecer por acaso em uma batalha futura, como acontece com paletas aleatórias.

Ao receber a vitória de um round (`onWin`), o Titan:

- anuncia a vitória no console;
- freia e gira o corpo, o canhão e o radar;
- troca continuamente as cores do corpo, canhão e radar durante o tempo de comemoração permitido pelo jogo.

No round seguinte, volta à paleta base da batalha. A animação só começa após a vitória e não precisa habilitar **Paint**. O motor determina quanto tempo o vencedor fica em exibição; em velocidades muito altas, a comemoração pode passar rapidamente. Reduza a velocidade da batalha para observar.

Foi realizado um teste de integração com **três batalhas de três rounds** contra SittingDuck, um alvo passivo. O motor confirmou três paletas distintas, estabilidade das cores durante o combate, nove vitórias com animação de cores e giros do corpo/canhão e velocidade final zero. Esse é um teste visual e de eventos, sem valor como avaliação de força competitiva.

Dados: [tests/colors-results.json](tests/colors-results.json). Para repetir:

```sh
python tools/colors.py --home /caminho/robocode-testes --output /caminho/cores
```

Acrescente `--ecj /caminho/ecj.jar` se precisar do compilador Eclipse. O teste usa snapshots do motor e pode ser executado sem interface gráfica. O código de uso normal continua somente em [robots/equipe/titanprime.java](robots/equipe/titanprime.java).
