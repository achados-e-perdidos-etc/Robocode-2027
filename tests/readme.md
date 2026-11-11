# Testes e evidências

A produção atual é a base 4.8, descrita em [arquitetura.md](../arquitetura.md). Os relatórios e testes 3.x permanecem para reprodução histórica.

## Verificações atuais

- `elite_checks.py`: compila somente com a API do Robocode; compara 157 previsões com trajetórias registradas por LocationBot no projeto Diamond, faz 100 comparações KNN com busca exaustiva após descarte FIFO e verifica frenagem quando todos os destinos atingiriam paredes.
- `diagnostic_persistence.py`: duas batalhas no motor real contra uma fixture passiva para testar vitória tardia, uma linha por round, duas batalhas conservadas e rotação em 48 KB.
- `elite_runner_checks.py`: verifica que o parser rejeita erros do motor, participantes incorretos, aliados, rounds incompletos e largadas divergentes.
- [elite](elite): plano prévio, fonte fixado por SHA256, resultados e logs das séries contra Diamond; dados históricos de PC continuam em [pc](pc).
- [tools/elite_duel.py](../tools/elite_duel.py): protocolo atual de batalhas, sem renomear o robô ou modificar o oponente.

Os resultados de fixtures passivas são verificações de integração, não vitórias competitivas.

## Estudos antigos

`core_checks.py`, `trainer_checks.py`, `bo3_checks.py`, `ram_spin_checks.py`, `samples_checks.py`, `defense_checks.py`, `attack_checks.py`, `telemetry_checks.py`, `complete_checks.py`, `reactive_checks.py`, `pair_checks.py` e `four_checks.py` foram escritos para APIs internas e/ou telemetria das versões 3.x. As ferramentas de treinamento por genoma, pesos neurais e protótipos também exigem os fontes históricos. Não devem ser aplicadas à base 4.8 como se fossem a mesma arquitetura.

Para reproduzir a última base 3.x, use um checkout separado do commit `462bfbbdcd13098cf1d53390868f3f3970feb63b` e siga seus relatórios. As tentativas rejeitadas continuam rejeitadas; preservar seus arquivos não as ativa no robô atual. O treinador agora informa explicitamente essa incompatibilidade ao receber o fonte 4.x.

Os JSONs antigos são resultados das versões identificadas neles, não resultados da 4.8. Não precisam ser recalculados nem reescritos para aprovar uma versão nova.

O [checkpoint 4.8](elite/v48/checkpoint.json) registra 49/50 e 48/50 em duas séries válidas, com o fonte e orçamento de referência fixados. A publicação foi solicitada pelo usuário antes de alcançar a meta de todos os rounds.
