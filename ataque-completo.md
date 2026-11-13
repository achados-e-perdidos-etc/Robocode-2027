> Histórico das versões 3.x. O código atual 4.x usa outra base; veja [arquitetura.md](arquitetura.md) e [ciclo-elite.md](ciclo-elite.md).

# Titan Prime 3.7 — implementação das lacunas ofensivas

Todo o robô continua em `robots/equipe/titanprime.java`. Foram implementadas as sete lacunas da lista de 25 técnicas: comparação de sequências, previsão física, classificação de movimento, mira de reação ao tiro, exploração de padrões, finalização e seleção contextual de miras. Os itens 5 e 15 compartilham o mesmo algoritmo. A lista não representa 25 armas independentes.

A finalização está ativa. As novas miras e a seleção contextual estão disponíveis no mesmo Java, mas desativadas por padrão: ativá-las em conjunto piorou o desempenho observado. As sete miras anteriores permanecem ativas. Cores, comemoração, modelos neurais e aprendizado entre rounds foram preservados.

## O que foi construído

- Histórico limitado a 512 observações por inimigo. O comparador procura oito movimentos semelhantes e usa uma continuação histórica conhecida, até 96 ticks. Trechos com scans ausentes ou mudança de round são rejeitados.
- Previsor com aceleração, frenagem, inversão fracionária, limite de giro e colisão com paredes. Reproduz uma hipótese executável de movimento; não conhece os comandos futuros do inimigo.
- Classificador de cinco estados: desconhecido/irregular, parado, linear, circular e oscilante. Usa observações contínuas, sem identificar o código do adversário.
- Mira reativa com até 256 deslocamentos observados após tiros reais em duelos. Combina vizinhos recentes e históricos; limita o ângulo por duas trajetórias de escape simuladas. É uma implementação própria, sem equivalência afirmada ao Diamond ou garantia anti-surfing.
- Seletor contextual que compara resultados reais das miras por classe de movimento. Visitas sem tiro não contam como acertos virtuais.
- Finalização gradual: alvo com energia até 12, energia própria mínima de 25, scan do alvo até dois ticks, conhecimento recente de todos os inimigos, afastamento dos terceiros e ausência de onda estimada com chegada em menos de 15 ticks. O destino conserva a avaliação de risco e paredes. Sai automaticamente quando as condições deixam de valer.

As previsões adicionais são recalculadas a cada quatro ticks ou mudança da velocidade da bala. Os limites de escape são estimativas condicionais; não são uma solução exata de todos os movimentos possíveis.

## Comparação no motor

Robocode 1.11.1, Java 21, bytecode Java 8, arena 800 × 600, três rounds. Vitória significa vencer pelo menos dois. Titan entra sozinho em todos os casos.

Foram comparados seis cenários: Diamond 1.8.28, cópia da versão 3.6, SuperMercutio, seis exemplos simultâneos, 16 exemplos simultâneos e a equipe MyFirstTeam com cinco integrantes. Nos grupos de exemplos todos também se enfrentam; no teste de equipe os cinco oponentes pertencem à mesma equipe.

| Seleção, duas largadas por cenário | Séries vencidas | Rounds vencidos | Turnos pulados |
| --- | ---: | ---: | ---: |
| Base 3.6 | 9/12 | 24/36 | 0 |
| Novas miras e contexto | 9/12 | 26/36 | 1 |
| Finalização | 10/12 | 29/36 | 1 |
| Todas as novidades | 8/12 | 25/36 | 3 |

A finalização foi escolhida antes de examinar as três largadas reservadas por cenário.

| Validação reservada | Base | Finalização |
| --- | ---: | ---: |
| Séries vencidas | 11/18 | 14/18 |
| Rounds vencidos | 37/54 | 38/54 |
| Vitórias no primeiro round | 13/18 | 13/18 |
| Turnos pulados | 0 | 0 |
| Diamond | 0/3 | 0/3 |
| Espelho 3.6 | 0/3 | 2/3 |
| SuperMercutio | 3/3 | 3/3 |
| Seis exemplos simultâneos | 3/3 | 3/3 |
| 16 exemplos simultâneos | 3/3 | 3/3 |
| Equipe de cinco | 2/3 | 3/3 |

Foram 84 séries e 252 rounds na seleção e validação. O critério pré-declarado exigia mais séries na validação, nenhuma regressão de séries por cenário e no máximo três turnos pulados adicionais. A candidata passou. É uma amostra pequena, não uma demonstração estatística de superioridade geral. Contra Diamond ainda perdeu todos os rounds.

As largadas usam `RobotSetup[]`, com captura da posição real nos três rounds. O motor ajustou posições em 12 das 84 séries; esses registros permanecem em `position_adjustments`. Não se afirma pareamento perfeito das posições. Os contadores de início confirmam modelos pré-treinados iguais e memória por adversário vazia em cada batalha independente.

## Usar e reproduzir

Para uso normal, compile o único Java da pasta `robots/equipe`. Não é necessário gerar módulos.

Para exportar um Java com todas as novidades experimentais ligadas:

```bash
python tools/complete.py --export /tmp/titanprime.java
```

O arquivo exportado continua sendo `equipe.titanprime`. Alternativamente, altere `USE_EXTENDED_GUNS` para `true` no Java. Isso ativa as novas miras, o histórico, o classificador e a seleção contextual. `USE_FINISHER=true` já é o padrão. A ativação completa não é a configuração recomendada pelos testes atuais.

O protocolo original pode ser reproduzido com `tools/complete.py --home <robocode> --ecj <ecj.jar> --source <java-da-3.6> --output <pasta-externa>`. A base exata está no commit `ae7049b79988cdbffea55b228d41589400b1acdd`; o SHA-256 e os hashes de cada candidata estão em `tests/complete-results.json`. O patch gerador rejeita outra base. Reexecutar gera novas batalhas; a disposição e o escalonamento do motor podem variar.

Os testes de componentes estão em `tests/complete_checks.py`: incluem continuidade, classificação, reprodução de padrão, física, memória limitada e impedimentos da finalização. Os resultados completos de seleção/validação estão em `tests/complete-results.json`. As observações não são gravadas em disco entre batalhas.

## Bateria de proteção e decisão final

A primeira execução teve 85 tentativas: 83 séries válidas, uma partida interativa sem confirmação final e o auxiliar Slave sem construtor. Foi preservada integralmente em `tests/complete-guards-initial.json`. Venceu as 63 séries competitivas e 187/189 rounds, mas registrou 15 turnos pulados nesses confrontos. As compilações de testes simultâneas são uma possível interferência, não uma causa demonstrada.

A repetição sem compilações simultâneas teve 85 tentativas e 84 séries válidas. Apenas Slave continuou inválido, como esperado; essa tentativa nunca conta como vitória. Nas categorias competitivas foram **63/63 séries e 187/189 rounds**, com um turno pulado. Duelo: 48/48 séries e 144/144 rounds; melee: 12/12 e 35/36; equipe: 3/3 e 8/9. As demonstrações passivas, interativas e dependentes ficam fora desse total. Houve quatro turnos pulados adicionais na categoria de demonstrações dependentes. Quatro séries melee tiveram posições ajustadas pelo motor. Dados: `tests/complete-guards.json`.

O alvo secundário de preservar pelo menos os 188/189 rounds da bateria histórica **não foi atingido**, inclusive na repetição. A finalização foi mantida ativa por preservar 63/63 séries e melhorar a validação reservada de 11/18 para 14/18 séries, alinhada à regra confirmada de vencer dois rounds. Essa decisão aceita uma regressão de um round na bateria de exemplos; não se afirma que todos os indicadores melhoraram nem que a candidata passou esse alvo secundário. Quem preferir desativá-la pode usar `USE_FINISHER=false` no mesmo Java.

Passaram os testes de componentes `core`, `attack`, `defense`, `telemetry`, `complete`, os verificadores de BO3, treino, catálogo de exemplos e RamFire/SpinBot, e a compilação dos protótipos usados pelos testes. O teste de seleção de alvo foi atualizado para fornecer a energia no robô simulado; não executa APIs do motor sem inicialização. Os dois blocos de pesos neurais são idênticos aos da 3.6.

O teste visual no motor passou em três batalhas e nove rounds: três paletas observadas, paleta estável entre rounds, nove comemorações com giro do corpo/canhão e cores animadas. É um teste contra SittingDuck passivo, sem valor como benchmark competitivo. Dados: `tests/complete-colors.json`.
