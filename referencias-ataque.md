# As 25 técnicas de ataque no Titan Prime

A lista combina algoritmos de mira, formas de coletar dados, seleção de alvos e decisões de energia. Várias técnicas trabalham juntas. As estrelas de complexidade não medem a força em uma competição.

O estudo usou como base o Titan Prime 3.4; a combinação de KNN e visitas foi adotada no Titan Prime 3.5 após confirmação adicional e testes de proteção. A decisão sobre a versão de uso está em [ataque-experimentos.md](ataque-experimentos.md). Os experimentos preservam as cores por batalha e a comemoração após vitórias.

| Nº | Técnica | Situação e significado no projeto |
| --- | --- | --- |
| 1 | Head-On Targeting | Já existente: ângulo direto para a posição estimada atual do inimigo. |
| 2 | Linear Targeting | Já existente: interceptação com velocidade e direção constantes. |
| 3 | Circular Targeting | Já existente: interceptação com taxa de curva observada. |
| 4 | GuessFactor Targeting | Já existente: histogramas de ângulos observados. |
| 5 | Pattern Matching Gun | Não implementado um comparador de sequências históricas de movimentos. KNN não equivale a esse algoritmo. |
| 6 | Dynamic Clustering Gun | Adicionado na 3.5: busca por situações semelhantes com KNN limitado, sem árvore KD ou agrupamento K-means. |
| 7 | KNN Targeting | Adicionado na 3.5: até 256 exemplos por banco e por adversário, com 12 características, recência e densidade angular. |
| 8 | Neural Network Targeting | Já existente: rede 12 → 16 → 47, com probabilidades de GuessFactors e aprendizado durante a batalha. |
| 9 | Precise Prediction Gun | Não há um previsor completo das decisões futuras do inimigo. As previsões linear/circular respeitam os limites da arena, mas não reproduzem todos os modelos físicos avançados. |
| 10 | Iterative Targeting | Já existente: avança a previsão em ticks até o projétil alcançar a distância prevista do alvo. |
| 11 | Virtual Guns | Já existente: cinco miras comparadas com ondas. A versão 3.5 adiciona até duas alternativas. |
| 12 | Adaptive Gun Selection | Já existente: comparação por adversário com peso maior para desempenho recente. |
| 13 | Enemy Movement Classification | Observa velocidade, aceleração e curva. Não atribui rótulos como “surfer” ou “movimento circular” por um classificador separado. |
| 14 | Anti-Surfing Gun | A versão 3.5 separa exemplos associados a disparos reais dos exemplos de visitas. Isso ajuda a estudar reações ao tiro, mas não é o anti-surfing completo do Diamond. |
| 15 | Anti-Pattern Targeting | Não há detector específico de sequências repetidas. O aprendizado estatístico pode explorar regularidades observadas sem identificar explicitamente o padrão. |
| 16 | Segmented GuessFactor Gun | Já existente: 24 segmentos de distância, velocidade lateral e aceleração. |
| 17 | GuessFactor Visit Counting | A base usa visitas observadas nas ondas de tiros reais. A versão 3.5 também coleta ondas sem disparar, entre oportunidades de tiro. |
| 18 | Wave-Based Targeting | Já existente: ondas registram a posição observada do inimigo no cruzamento, inclusive quando a bala erra. |
| 19 | Adaptive Firepower | Já existente: potência varia com distância, energia, precisão recente e energia do alvo. Potência maior reduz a velocidade da bala. |
| 20 | Online Learning Gun | Já existente; os novos bancos KNN também aprendem entre os rounds da mesma batalha. |
| 21 | Target Prioritization | Já existente: considera distância, energia, idade da observação e continuidade do alvo. Sentinelas não são escolhidas como alvo de pontuação. |
| 22 | Finisher Mode | Já limita a potência pela energia necessária para finalizar. Não há um modo separado de perseguir/aproximar-se automaticamente de todo inimigo enfraquecido. |
| 23 | Predictive Interception | Já existente nas miras linear e circular. |
| 24 | Energy Management Gun | Já existente: reservas de energia, redução de potência e verificação de energia antes do disparo. |
| 25 | Counter-Defense Targeting | A seleção compara resultados observados das miras. Não identifica o código de defesa nem garante uma contramedida vencedora. |

## Como o ataque se organiza

O `Enemy` representa as observações atuais. O `Brain` guarda estatísticas e modelos por adversário. As miras propõem ângulos, o seletor compara seus resultados e o controlador existente decide alinhamento, energia e potência. Tudo é gerado como classes internas do mesmo Java.

Com KNN, a sexta alternativa usa um banco de visitas. Quando as ondas de observação estão ativas, uma sétima usa apenas exemplos de ondas associadas a tiros reais. Cada banco tem até 256 amostras; a busca é limitada, apropriada ao orçamento de CPU deste protótipo. Não se afirma analisar milhares de amostras por decisão.

As ondas extras surgem no máximo a cada quatro ticks no duelo ou oito com vários adversários, respeitando o limite de 96 ondas de mira. Elas não consomem energia de tiro. Somente ondas de disparos reais atualizam a comparação de precisão das miras; visitas extras treinam os modelos sem contar acertos fictícios.

Os exemplos são observações da posição inimiga, não previsões certas de onde ele estará. Em especial, um inimigo pode reagir de forma diferente quando há disparo real. Essa é a razão de manter bancos separados e avaliar os resultados no motor.

O aprendizado novo fica na memória da batalha e atravessa seus três rounds. Os bancos KNN não são gravados em disco nem incorporados como um histórico permanente. Os modelos neurais pré-treinados da base foram preservados neste experimento.

## Referências verificadas

O [TripHammerKnnGun do Diamond][trip] combina vizinhos próximos e GuessFactors. O [AntiSurferGun][anti] usa múltiplos modelos e informações específicas, incluindo ângulos de escape precisos; é mais amplo que o protótipo desenvolvido aqui. [GunEnemy][enemy] distingue ondas virtuais e ondas de disparos reais ao registrar observações.

Essas fontes foram consultadas na revisão `2d1c6a2b911cf077c5f13d2baa45da7d18d6babb`, sob a licença zlib modificada do projeto. O novo KNN é código próprio; não incorpora classes do Diamond. As técnicas e o código efetivamente presentes foram descritos separadamente para não apresentar as 25 entradas como 25 sistemas completos já integrados.

[trip]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/gun/TripHammerKnnGun.java
[anti]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/gun/AntiSurferGun.java
[enemy]: https://github.com/Voidious/Diamond/blob/2d1c6a2b911cf077c5f13d2baa45da7d18d6babb/voidious/gun/GunEnemy.java
