# Plano de evolução a partir do documento TitanV2

Atualização ofensiva: as lacunas da lista de 25 foram implementadas no Titan Prime 3.7. A finalização está ativa; as miras novas permanecem experimentais após comparação em batalhas. Veja [ataque-completo.md](ataque-completo.md).

Análise de `TitanV2_Plano_Completo_de_Sistemas.docx`, enviado pelo usuário, comparado ao **Titan Prime 3.5** do commit `26af41108680157af4dab10ce3e004540bec65b6`. O documento apresenta **15 sistemas e 120 itens**, além dos anexos com as listas de defesa e ataque já discutidas.

Este arquivo é um plano, não uma declaração de que as propostas foram implementadas. A versão de combate permanece 3.5. O documento original não foi publicado; esta análise registra apenas o conteúdo técnico relevante ao projeto.

## Regras que continuam orientando o trabalho

- Um único `robots/equipe/titanprime.java`; organização por classes internas quando necessário.
- Arquivos do repositório em minúsculas e atualizações na `main`.
- Objetivo principal: vencer pelo menos dois dos três rounds.
- Muitas **batalhas independentes de três rounds**, com memória inicial controlada, para comparar versões. Uma batalha longa não substitui essa avaliação.
- Preservar as cores por batalha e a comemoração após vitória.
- Uma alteração de cada vez ou comparação explícita das combinações, com critérios registrados antes da execução.

## Comparação dos 15 sistemas

“Parcial” significa que há componentes úteis, mas não todo o sistema proposto.

| Sistema do documento | Estado na 3.5 | Complemento que merece avaliação |
| --- | --- | --- |
| 01 — Defesa híbrida | Parcial: detecção de ondas, simulação de rotas, risco de inimigos e paredes, adaptação estatística | Calibrar confiança das ondas e comparar defesas por exposição. Anti-Gravity vetorial, Wall Smoothing dedicado e seleção aprendida não estão integrados como novos controladores. |
| 02 — Ataque híbrido | Grande parte presente: sete alternativas de mira, GuessFactor, KNN, redes, ondas e potência adaptativa | Pattern Matching e anti-surfing completo continuam pendentes. Preservar a separação entre visitas e ondas de tiros reais. |
| 03 — Radar inteligente | Lock no duelo, varredura em melee, timestamps, previsão entre scans, retomada da busca e controles independentes | Medir revisitas e tempo sem alvo antes de substituir a varredura por um agendador. Incerteza de posição ainda não é calibrada como uma distribuição. |
| 04 — Inteligência adaptativa | Parcial: modelos por inimigo, contexto nas características, decaimento e seleção por desempenho virtual | Controle de confiança mais explícito, classificação de movimentos e seleção de políticas por retorno. Não há bandit contextual completo. |
| 05 — Memória persistente | Memória entre rounds e pesos pré-treinados incorporados; formato de serialização neural usado nas ferramentas | Histórico novo do jogador não é salvo entre batalhas. Persistência de KNN e perfis exige formato, limites, validação e modo separado de avaliação. |
| 06 — Energia | Potência por distância/energia/precisão, reserva, calor e potência para finalizar | Comparar dano esperado por energia e aproximação segura. Energia inimiga deve continuar tratada como observação datada. |
| 07 — Navegação | Destinos candidatos, simulação física, distância desejada, penalidades e replanejamento | Risco de cerco e de cantos com poucas saídas; controle de velocidade e suavização de parede mais específicos. |
| 08 — Melee | Observação de vários inimigos, escolha de alvo, risco espacial, radar amplo e transição para duelo | Linha de fogo cruzada e risco de ficar entre adversários; atribuição de energia/tiros de terceiros continua incerta. |
| 09 — Análise do adversário | Cinemática observada e modelos separados por inimigo, incluindo bancos de visitas e tiros | Detectar mudança de fase e comparar hipóteses de mira com confiança. Os modelos não revelam o código rival. |
| 10 — Simulação | Movimento com giro/aceleração, ondas, avaliação de rotas, limites de candidatos e horizonte | Mais validação de previsões do inimigo e contingência por incerteza. A ação futura dele permanece desconhecida. |
| 11 — Seleção de estratégias | Um planejador final de movimento e continuidade de destino/alvo | Políticas defensivas explícitas, crédito temporal, razões de decisão e exploração limitada. Evitar múltiplos controladores emitindo comandos simultâneos. |
| 12 — Telemetria | Tiros, acertos, ondas, associações, paredes, turnos perdidos, miras selecionadas e resultados por round/série | **Prioridade imediata:** dano recebido/causado, balas recebidas sem onda associada, qualidade de scans e eficiência por energia. |
| 13 — Finalização | Potência limitada pela energia necessária e reserva própria | Aproximação lateral gradual e reversível, com validação de risco. Não existe um modo dedicado de perseguição segura. |
| 14 — Otimização | Ferramentas externas de variantes, treino, comparação, sementes, hashes e critérios de adoção | Ampliar adversários e confirmação independente; não otimizar somente contra exemplos ou contra a própria versão. |
| 15 — Estabilidade | Limites de memória/ondas, limpeza, verificação de física, compilação e regressões | Monitorar custo dos módulos novos e validar outras arenas. Testes longos podem verificar estabilidade, mas a promoção usa séries de três rounds. |

## Ordem recomendada para os próximos experimentos

### 1. Medir por que as séries contra Diamond são perdidas

O ganho da 3.5 foi confirmado em séries contra a versão anterior; as séries contra Diamond continuam sem vitória. Antes de adicionar outro controlador, a instrumentação deve permitir distinguir perda por defesa, mira, perda de observação ou custo de processamento.

Campos propostos, **ainda não implementados neste plano**:

- Balas recebidas, potência e dano nominal, separando isso de variação de energia e de pontuação oficial.
- Balas recebidas com e sem onda associada, erro radial da associação e confiança da onda.
- Intervalo entre scans, idade do alvo no momento do tiro e ticks sem alvo válido.
- Energia gasta em disparos, acertos e desempenho por distância e por contexto.
- Mudanças de alvo/destino e contagem das operações caras, além dos turnos perdidos já registrados.

Começar com contadores agregados e linhas por round. O CSV pode ser produzido pelas ferramentas externas a partir do console, sem gravar cada tick dentro do robô. Verificar que a instrumentação não altera as decisões e medir seu custo. Uma melhora na contagem de dados não deve ser apresentada como melhora de combate.

### 2. Calibrar confiança das previsões e do rastreamento

Comparar previsões antigas com a próxima observação real. Registrar erro por idade do scan, distância e quantidade de inimigos. Usar esses dados para decidir quando retomar a busca, reduzir a confiança de uma onda ou recusar um tiro.

O duelo já usa radar lock; portanto, não presumir que o radar é a causa das derrotas sem observar suas métricas. Da mesma forma, quedas de energia em melee não identificam perfeitamente o autor ou destinatário de um disparo.

### 3. Modelar a mira adversária e testar uma mudança defensiva

Comparar hipóteses simples de tiro direto, linear e distribuição estatística com as balas efetivamente observadas. Manter um perfil por adversário e controlar sua confiança. Escolher uma mudança defensiva orientada pelo diagnóstico, com a mesma mira da base, para isolar o efeito.

Na avaliação de políticas, usar dano e tempo de exposição comparáveis. Um impacto após uma troca de estratégia pode vir de uma bala disparada antes dela; não atribuir todo o dano automaticamente à política que estiver ativa no instante do impacto. A evidência pode envolver várias decisões durante o voo da bala.

### 4. Finalização e posicionamento como alternativas menores

Experimentar aproximação lateral contra inimigo enfraquecido, mantendo reserva própria e rota de saída. Comparar separadamente um reforço de risco de cerco/cantos. Definir condições de entrada e saída para evitar perseguição permanente ou reversões contraditórias.

### 5. Seleção aprendida de políticas e persistência

A seleção por retorno precisa de dados e de uma atribuição de crédito minimamente confiável. Em somente três rounds, explorar muitas defesas consome parte da própria partida. Priorizar treino externo e uma decisão inicial forte; testar exploração online limitada como uma candidata separada.

Persistência entre execuções fica como recurso opcional, condicionado às regras da competição. A base atual já aprende entre os rounds. Antes de ativar arquivos: definir versão do formato, tamanho máximo, validação de dados e comportamento quando o carregamento falhar. Separar resultados com memória nova dos resultados com histórico carregado, sem misturá-los na comparação.

## Critério de adoção

Registrar previamente base, candidata, adversários, regras, posições solicitadas e critérios. Comparar primeiro séries vencidas e resultados por cenário; observar também primeiro round, total de rounds, dano, energia e turnos perdidos. Registrar as coordenadas efetivas quando o motor reposicionar participantes.

Uma candidata promissora passa por confirmação em posições novas e testes de proteção. Resultados contrários à hipótese continuam no relatório. Ganhar pontos não substitui ganhar dois rounds. Adicionar nomes de técnicas ou aumentar a quantidade de módulos não é um critério de promoção.

## Fontes e estado do trabalho

- Documento do usuário: `TitanV2_Plano_Completo_de_Sistemas.docx`.
- Base analisada: [fonte 3.5](https://github.com/achados-e-perdidos-etc/Robocode-2027/blob/26af41108680157af4dab10ce3e004540bec65b6/robots/equipe/titanprime.java).
- [Evidências da evolução ofensiva](ataque-experimentos.md), [referências de ataque](referencias-ataque.md) e [referências de defesa](referencias-defesa.md).

Esta revisão acrescenta planejamento e análise. Não modifica a estratégia do Java nem declara implementados os recursos pendentes.

SHA-256 do documento analisado: `1a569cef1104fea7430d644f12f2f680e3a7af8a9c9e6f46553d904c168e091b`.

## Primeira etapa executada: 3.6

A instrumentação inicial foi implementada e comparada à 3.5 em 30 séries independentes / 90 rounds, sem regressão de séries por contexto. O primeiro diagnóstico contra Diamond encontrou radar atualizado e associações consistentes nos impactos registrados, mantendo as derrotas. Os dados orientam a investigação da mira e do perigo estatístico, sem assumir que mais controle de radar resolverá o problema. Veja [telemetria.md](telemetria.md) e [arquitetura.md](arquitetura.md). As demais etapas deste plano continuam propostas.
