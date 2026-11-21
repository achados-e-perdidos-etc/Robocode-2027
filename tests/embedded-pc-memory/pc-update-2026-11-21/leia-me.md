# Atualização do aprendizado — 21/11/2026

O placar enviado registra 7 vitórias em 10 rounds para `equipe.titanprime_v4_41*`, 757 a 485 pontos, contra `equipe.titanprime*`. O nome do adversário não informa qual revisão do Titan estava sendo executada. Este arquivo não é um confronto contra Diamond.

O diagnóstico contém os 10 rounds da mesma batalha: 94.124 ticks, 8.475 tiros e 732 acertos (8,64%), 18 colisões com paredes e 204 turnos perdidos. A energia final média foi 2,08. Os rounds foram longos, com média de 9.412 ticks. O arquivo de erros não foi enviado; o diagnóstico isolado não prova a causa dos turnos perdidos nem permite certificar ausência de erros fatais.

As memórias têm CRC e estrutura válidos. A mais recente é `memoria-a.dat`, geração 45, 66.615 bytes; `memoria-b.dat` está na geração 44. A geração mais alta, não a letra do arquivo, decide a seleção.

Foram incorporados 5 perfis: a nova experiência contra `equipe.titanprime*`, com 8.392 amostras neurais, e os quatro perfis anteriores. Diamond, ChimpazeComSarna, OBraboBot e TitanV2 foram mantidos byte a byte. Não foram criadas observações que não existiam nos arquivos.

A atualização mantém as regras de combate, o nome técnico `equipe.titanprime_v4_41` e sua pasta de dados. A identificação no console e na descrição é `titanprime4.41`. A base embutida agora informa 5 perfis e geração 45. Memórias locais válidas continuam tendo prioridade por adversário.

Passaram 40 verificações no código Java real, incluindo carga sem arquivos, escolha da geração 45 do slot A, fallback para 44 se A estiver corrompido, retenção de perfis ausentes no arquivo local, recuperação das redes/miras/KNN e gravação/recarga exata. O codec anterior também passou em 3 verificações lendo um snapshot novo de geração 46 com 5 perfis. Compilação completa e integridade das 117 classes do JAR verificadas, bytecode 52.

Esta atualização de dados ainda não recebeu uma nova comparação competitiva; o 7/10 pertence ao código que produziu os arquivos enviados, antes de incorporar o snapshot 45. O checkpoint anterior de geração 38 está preservado em `releases/checkpoints/titanprime-v4-41-geracao38.jar` e `variants/checkpoints/4.41-geracao38/equipe/titanprime_v4_41.java`.
