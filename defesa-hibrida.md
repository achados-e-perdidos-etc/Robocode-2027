# Avaliação da proposta TitanD

O texto fornecido descreve um projeto modular com `HybridDefense.java`, mas não traz os arquivos ou links funcionais para o ZIP e o JAR mencionados. Esta avaliação se limita ao trecho de seleção de estratégias apresentado. Não é uma revisão do projeto completo nem comprova que ele compila ou vence batalhas.

O Titan Prime mantém o requisito de **um único `titanprime.java`**. Técnicas adicionais podem usar classes internas nesse arquivo.

## O que já existe no nosso robô

| Proposta | Implementação atual |
| --- | --- |
| Wave Surfing | Ondas estimadas a partir da energia adversária, simulação de movimento e avaliação de perigo. |
| Minimum Risk | Avaliação de 32 destinos com risco de inimigos, paredes e ondas. |
| Previsão e mira virtual | Miras direta, linear, circular, GuessFactor e neural comparadas por adversário. |
| Aprendizado durante a batalha | Estatísticas por adversário e redes pequenas, conservadas entre os três rounds. |
| Evitar trajetórias repetidas | Mistura limitada de perigo aprendido e visitas observadas, quando há evidência suficiente. |
| Visualização | Desenho de ondas estimadas, inimigos observados, alvo e destino. |

A avaliação de risco já combina vários sinais na mesma decisão. Alternar entre cinco controladores exclusivos não garante uma defesa melhor.

## Pontos a corrigir antes de experimentar a seleção proposta

A velocidade ou frequência de curvas do adversário descreve **o movimento dele**, não a mira que ele usa contra nós. Selecionar Stop-and-Go porque o inimigo anda em linha reta não é evidência de que esse movimento evitará seus disparos.

A expressão `(hits + 1) / (possibleShots + 8)` é uma estimativa suavizada. Em uma batalha com vários inimigos, quedas de energia também podem vir de colisões ou tiros de terceiros; além disso, nem todo tiro estimado foi destinado a nós. Esse valor não é a precisão real da arma inimiga.

Uma pausa fixa de 35 ticks reduz trocas frequentes, mas pode prolongar uma estratégia ruim diante de uma onda próxima. A escolha precisa considerar tempo até o impacto, espaço para frear, paredes e custo de inverter a velocidade.

Com só três rounds, experimentar muitos controladores durante a competição tem um custo alto. O treino externo deve fornecer uma decisão inicial forte; o aprendizado durante a batalha deve fazer ajustes limitados com as evidências disponíveis.

## Experimento que faz sentido depois desta validação

Comparar uma alternativa por vez contra a versão atual, com as posições efetivamente registradas pelo motor. Medir primeiro séries vencidas e vitórias no primeiro round; depois dano recebido por tempo de exposição, energia de sobrevivência, precisão, colisões e turnos perdidos. Separar duelos, todos contra todos e equipes reais.

Um controlador adaptativo pode comparar defesas usando dano recebido, tempo de exposição e contexto, com um limite para a exploração. A troca deve respeitar a trajetória já iniciada e a onda mais próxima. Essa proposta ainda não foi implementada ou medida no Titan Prime.

Enemy Gun Modeling e Dynamic Clustering são candidatos para melhorar a defesa contra adversários fortes, como o Diamond. Bullet Shielding exige um projeto e uma validação próprios; não há resultado aqui que justifique ativá-lo.

Os testes dos exemplos da instalação estão em [robos-de-exemplo.md](robos-de-exemplo.md). Eles avaliam o Titan Prime; não são testes do TitanD descrito no texto.
