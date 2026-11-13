# Aprendizado entre batalhas

O Titan Prime 4.15 grava e recarrega automaticamente dois modelos neurais por adversário, avaliações das miras virtuais e amostras recentes da defesa. A identificação inclui o nome e a versão do adversário. A gravação ocorre depois da vitória ou da derrota, com tentativas adicionais no encerramento do round e da batalha.

Os arquivos ficam em `robots/equipe/titanprime.data/memoria-a.dat` e `memoria-b.dat`. Cada gravação alterna a cópia; a próxima batalha carrega a geração válida mais recente. Se uma cópia estiver incompleta ou corrompida, tenta a outra. Se ambas forem inválidas, usa os pesos iniciais incorporados. Arena e resfriamento diferentes também exigem começar com os modelos iniciais. A versão 4.15 lê os formatos 1 e 2; grava o formato 2 comprimido e com CRC32.

Não existe mais um limite fixo de quatro adversários. A quantidade de perfis gravados depende do tamanho comprimido e da cota disponível. O arquivo de cada cópia tem no máximo 70.000 bytes; o diagnóstico reserva até 48.000 bytes. Se faltar espaço, os perfis vistos mais recentemente têm prioridade. Redes e estatísticas continuam sendo atualizadas, mas o robô não guarda todas as observações brutas: a defesa mantém uma janela recente e as redes consolidam exemplos nos pesos.

## Histórico externo sem limite fixo de versões

O Robocode limita a escrita do robô. Para conservar mais histórico durante treinos, use o [arquivador externo](tools/archive_learning.py) com **Python 3.9 ou mais recente**. Ele somente lê os dois arquivos do próprio Titan e copia checkpoints válidos para outra pasta; não controla o robô nem fornece informações durante o combate. O robô funciona e recarrega sua memória mesmo sem o arquivador.

No Windows, na pasta do repositório, com o diretório de dados já criado:

```bat
py -3 tools\archive_learning.py --data-dir "C:\robocode\robots\equipe\titanprime.data" --archive-dir "%USERPROFILE%\Documents\titan-historico"
```

Mantenha esse terminal aberto durante os treinos. `Ctrl+C` encerra a captura. O arquivo `index.sqlite3` registra os checkpoints e também conserva os registros observados no `diagnostico.csv`, incluindo revisões de vitória tardia. Assim, a rotação do CSV do jogo não apaga os rounds já capturados. Os arquivos `.dat` são identificados por SHA256. Cópias repetidas não ocupam novos objetos; reiniciar o arquivador conserva o histórico anterior. Não há regra de exclusão por idade ou quantidade. O limite real é o espaço disponível no disco.

O arquivador verifica os arquivos a cada 0,2 segundo e ignora gravações ainda incompletas. Ele preserva os checkpoints que consegue observar; se estiver desligado ou não acompanhar a velocidade das gravações, pode perder versões intermediárias. Isso não é um registro de cada tiro nem um treinamento neural externo. O robô carrega suas duas cópias locais, não todo o arquivo externo.

Para uma captura única, acrescente `--once`. Para recuperar uma versão histórica, **feche o Robocode**, escolha o SHA256 exibido na captura e execute:

```bat
py -3 tools\archive_learning.py --data-dir "C:\robocode\robots\equipe\titanprime.data" --archive-dir "%USERPROFILE%\Documents\titan-historico" --restore SHA256_COMPLETO
```

Essa operação arquiva as cópias locais válidas antes de substituí-las. A batalha seguinte recarrega o checkpoint restaurado. Restaurar uma versão antiga recupera aquele estado completo; não mescla automaticamente modelos de várias versões.

## Verificações

Os [relatórios de memória](tests/memory/plan.json) identificam o SHA256 do fonte avaliado. Foram conferidos previsão neural após serialização, reconstrução do KNN defensivo, migração do formato anterior, nove adversários no mesmo arquivo, validação de limites e recuperação de arquivos corrompidos. Batalhas em processos separados verificam a recarga real. O arquivador foi verificado com 40 versões, reinício, duplicatas, corrupção e restauração.

Esses testes verificam persistência e integridade. Eles não demonstram que todo aprendizado melhora o desempenho, nem que o Titan vence todos os rounds.

Na [captura durante uma série real](tests/memory/archive-live.json), a ferramenta conservou 43 checkpoints e os 50 registros de rounds. O arquivo externo atingiu cerca de 665 KB, enquanto as duas cópias locais do robô somavam aproximadamente 27 KB. Esse teste usou uma candidata experimental 4.16, identificada no relatório; não é um resultado competitivo da 4.15.
