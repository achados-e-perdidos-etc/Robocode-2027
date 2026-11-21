# Comparação da 4.38 com a 4.41-pc — 21/11/2026

Foram 100 rounds comparativos, 50 por versão, em 10 batalhas válidas. A 4.38 venceu 49/50 e a 4.41-pc venceu 50/50, distribuídos entre quatro adversários. Contra Diamond, foram 20 rounds por versão; os demais tiveram 10 cada. Os pontos abaixo mostram Titan × adversário.

| Adversário | Vitórias 4.38 | Vitórias 4.41-pc | Pontos 4.38 | Pontos 4.41-pc | Mudança na participação de pontos (pp) |
|---|---:|---:|---:|---:|---:|
| Diamond 1.8.28 | 19/20 | 20/20 | 1407 × 825 | 1491 × 755 | +3.35 |
| ChimpazeComSarna | 10/10 | 10/10 | 606 × 77 | 628 × 77 | +0.35 |
| OBraboBot | 10/10 | 10/10 | 1247 × 143 | 1282 × 200 | -3.21 |
| TitanV2 | 10/10 | 10/10 | 902 × 323 | 927 × 301 | +1.86 |

## Interpretação

O aprendizado apresentou sinal favorável nesta amostra: uma vitória adicional contra Diamond e mais pontos próprios contra os quatro adversários. O ganho não foi uniforme: contra OBraboBot o adversário também pontuou mais, reduzindo a participação do Titan nos pontos. Contra Chimpanzé a diferença foi pequena. As amostras e largadas independentes não comprovam uma melhoria estatisticamente confiável ou permanente.

A 4.38 continua como versão principal; a 4.41-pc permanece disponível como variante. Nenhum código de combate ou peso foi alterado durante os testes.

## Protocolo e integridade

Robocode 1.12.0, Java 21.0.13, arena 800×600, resfriamento 0,1, inatividade 450 e limite de 10.030.489ns igual para os participantes. Uma JVM e instalação limpa por batalha, sem arquivos locais de aprendizado importados. Os logs confirmam 4 perfis embutidos na 4.41-pc e nenhum perfil inicial na 4.38. As duas continuam aprendendo normalmente entre os rounds da mesma batalha.

O primeiro plano definiu 80 rounds nos quatro adversários. Após o primeiro par contra Diamond, foi declarada uma confirmação independente de 10 rounds por versão, em ordem invertida, conservando todos os resultados anteriores. IDs de casos não são sementes do gerador aleatório; as posições iniciais foram sorteadas e registradas.

O Diamond foi o JAR original 1.8.28, SHA256 `1ab39d66041eaf4edf5c073d8caa7c61ff5e783961adfad03914f14017d048ea`. Chimpanzé, OBraboBot e TitanV2 foram compilados dos fontes atuais do repositório do usuário, com os nomes de desenvolvimento que correspondem aos perfis enviados. A identidade por nome não comprova que sejam exatamente as revisões usadas no PC. OBraboBot tinha uma quebra de linha inválida numa mensagem de log; somente essa sintaxe foi reparada, sem alterar sua estratégia, e o mesmo binário foi usado nas duas versões.

Duas batalhas de instalação ficaram separadas dos 100 rounds: uma contra Diamond foi invalidada por falta do módulo de interface; após instalar o componente, uma contra Chimpanzé validou o ambiente. Seus resultados não foram creditados na comparação e estão no arquivo de registros.

[Resumo completo](summary.json), [plano inicial](plan.json), [confirmação declarada](confirmation-plan.json) e [logs, CSVs, posições e verificações](registros.zip). Erros de motor, participante inativo, desativação ou ausência de placar invalidam um caso; os dez casos comparativos passaram nessas verificações.
