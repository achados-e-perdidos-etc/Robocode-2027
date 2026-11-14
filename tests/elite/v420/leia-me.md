# Validação experimental 4.20

Estes resultados pertencem a fontes experimentais identificadas pelo SHA256 nos relatórios. O Java principal publicado continua na versão 4.15; os resultados abaixo não devem ser atribuídos a ele.

| Fonte experimental | Adversário e condição | Vitórias / rounds | Pontos Titan / adversário |
|---|---|---:|---:|
| precision07 | Diamond, início sem memória importada | 50/50 | 3666 / 1641 |
| precision07 | Diamond, validação adicional 1 | 49/50 | 3622 / 1748 |
| precision07 | Diamond, validação adicional 2 | 48/50 | 3531 / 1881 |
| precision07 | Diamond, nova JVM com memória própria recarregada | 48/50 | 3516 / 2033 |
| precision07 | ChimpazeComSarna | 44/50 | 3573 / 2473 |
| precision12 | Diamond | 50/50 | 3705 / 1679 |
| precision12 | ChimpazeComSarna | 42/50 | 3608 / 2899 |
| precision07 | Sozinho contra Diamond + ChimpazeComSarna + TitanV3 | 2/5 | 926 / 1176 do Diamond |

As três séries independentes contra o Diamond com a mesma fonte precision07 somam 147/150 vitórias. A série com memória recarregada é separada. O resultado precision12 pertence a outra fonte e não compõe essa soma. A meta de vencer todos os rounds não foi atingida.

Os confrontos usam Robocode 1.12.0, arena 800×600, energia inicial 100, resfriamento 0,1, inatividade 450 e o mesmo limite de processamento para ambos os robôs. As largadas são aleatórias a cada round; os identificadores dos casos não fixam a semente do motor. O JAR original do Diamond permanece inalterado e seu hash aparece nos relatórios.

`index.json` organiza os resultados. Os CSVs contêm diagnósticos de cada round. `registros.zip` conserva os logs completos; `registros.json` registra o hash de cada arquivo. Não houve repetição de uma derrota válida para substituí-la por uma vitória.

A versão experimental compara modelos de mira, incluindo duas redes neurais que aprendem durante a batalha, e limita a potência conforme a precisão real observada no duelo. Também preserva correções anteriores de ondas, projéteis e amostragem do aprendizado. Nenhuma dessas técnicas garante invencibilidade.
