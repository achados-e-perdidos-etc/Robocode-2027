# Titan Prime 3.10: diagnostico persistente e espaco no duelo

O fonte enviado pelo usuario foi identificado como o Prime 3.9 da main, com diferenca apenas em espaco em branco final. SHA-256 recebido: `68e8a44e8ff3cec522ea5fd3e334574edcfadbdedaa59740fda9aabdf36554c8`; baseline do repositorio: `a58850e29d83dde1715f8635294c7674d7874103b625cd521bf9e213bbe33f09`. A classe principal recebida tinha major 61 (Java 17). A nova compilacao de verificacao usa major 52 (Java 8), tambem compativel com Java 17.

## O que mudou

1. O Prime grava automaticamente `diagnostico.csv` na sua pasta de dados. O arquivo identifica batalha e round, adversarios observados, tiros, acertos, energia investida, dano nominal, impactos recebidos, ondas, associacoes de impactos, paredes, turnos pulados, tempo com canhao frio, observacao indisponivel, distancia media dos disparos e tempo perto das bordas. A gravacao ocorre no relato de morte/fim do round e, quando necessario, e atualizada pelo evento de vitoria.
2. No duelo, a avaliacao reduz a repulsao por distancia, aumenta o peso da faixa desejada e penaliza ficar a menos de 100 pixels das paredes. O ajuste moderado foi escolhido entre dois candidatos. O planejador continua considerando ondas e fisica. Em melee, essas equacoes novas so entram quando resta um adversario.

Os pesos neurais incorporados, as miras, as cores e a comemoracao foram preservados. O CSV e um diagnostico: ele nao transforma automaticamente os registros em aprendizado salvo entre batalhas. O novo fonte continua em um unico Java.

O arquivo guarda os registros mais recentes ate 48000 bytes, respeitando a cota disponivel do Robocode. Ao atingir o limite, remove as linhas mais antigas. Nao e um historico ilimitado. Uma interrupcao forcada durante a gravacao ou falha de disco pode impedir o registro; erros de escrita aparecem como `DIAGNOSTIC_ERROR`.

## Testes de combate

Motor Robocode 1.11.1, Java 21, fonte com alvo Java 8, arena 800 x 600, resfriamento 0,1. Cada batalha usa JVM nova e dados externos reiniciados; o aprendizado continua durante os rounds. TitanV3 3.0 e o JAR original do usuario, nao o fonte TitanV2. O JAR duplicado em `sampleex` foi retirado apenas da copia de testes.

Selecao: base com diagnostico, ajuste moderado e ajuste forte, duas largadas de seis rounds contra Diamond: 36 rounds. Nenhuma versao venceu rounds. Pontos: base 220, moderado 264, forte 239. O moderado foi escolhido por uma formula que da peso igual a vitorias e parcela dos pontos. O logger esta ativo nas tres variantes para separar a comparacao de movimento do novo registro.

Validacao: duas novas largadas, oito rounds por versao e contexto. Sao 64 rounds incluidos na comparacao abaixo. A regra anterior aos resultados exigia pelo menos duas vitorias adicionais, ganho relativo de 2% na parcela media dos pontos, placares completos e no maximo tres turnos pulados adicionais. Nao ha prioridade de primeiro round ou melhor de tres.

| Validacao, 16 rounds por versao em cada contexto | Base 3.9 com diagnostico | Ajuste adotado na 3.10 |
| --- | ---: | ---: |
| Diamond em duelo: vitorias / pontos | 0/16 / 221 | 0/16 / 279 |
| Diamond + TitanV3: vitorias / pontos | 12/16 / 3180 | 14/16 / 3275 |
| Total: vitorias / pontos | 12/32 / 3401 | 14/32 / 3554 |

A parcela media dos pontos por contexto subiu de 30,80% para 32,69%. A pontuacao absoluta subiu cerca de 4,5%. Turnos pulados observados: sete na base e seis na candidata, incluindo os casos repetidos que entraram no lugar dos originais. Esses numeros se referem somente as batalhas incluidas, nao a todas as tentativas.

Uma tentativa original da base, no trio da largada 21407, teve centenas de mensagens do Diamond: `ERROR: No safe destinations found`. A auditoria descartou o par inteiro da comparacao incluida e repetiu ambas as versoes uma unica vez. Os originais e os substitutos foram preservados. A versao nova do avaliador detecta explicitamente erros reportados por robos e avisos de inicializacao. O resultado preliminar de 14 contra 10 vitorias nao e o resultado final: depois da repeticao correta, sao 14 contra 12.

No total, houve 100 rounds incluidos (36 de selecao e 64 de validacao), 16 rounds originais do par substituido e quatro rounds de verificacao: dois contra alvo passivo para testar arquivos, um contra Diamond e um contra Diamond + TitanV3. Foram 120 rounds executados. Os testes passivos nao medem forca de combate. Os dois controles curtos com adversarios fortes foram derrotas da versao final; eles confirmaram a correspondencia do arquivo com os placares, e nao fazem parte da validacao reservada.

## O que o diagnostico revelou

Nos 16 duelos reservados com Diamond, a distancia media dos disparos caiu de aproximadamente 566 para 499 pixels. A proporcao de decisoes a menos de 80 pixels das bordas caiu de 31,6% para 3,9%. A precisao observada subiu de 6,7% para 7,6%, mas continua baixa. A energia gasta em tiros aumentou de aproximadamente 775 para 871 no conjunto dos 16 rounds; nao se deve confundir mais dano com economia de energia.

Isso confirma que o novo avaliador altera o posicionamento pretendido. Nao demonstra que ja temos uma defesa ou mira suficiente para vencer Diamond: permanecemos em 0/16 nos duelos reservados. A proxima investigacao deve priorizar precisao e retorno energetico dos tiros contra movimento evasivo.

## Verificacao do arquivo

A primeira implementacao do campo `won` identificou um detalhe da ordem dos eventos: o fim do round pode ser relatado antes de `onWin`. Corrigimos isso com atualizacao da mesma linha por batalha/round quando a vitoria chega. Nao sao criadas duas linhas para o mesmo round.

Os 100 registros da comparacao incluida foram conferidos contra tiros, acertos, paredes e turnos pulados do console. O campo de vitoria nesses CSVs anteriores a correcao esta explicitamente identificado como `won_pre_fix`, nao deve ser usado como placar e nao participou da escolha da movimentacao. As vitorias usadas na tabela sao as oficiais do motor.

A correcao de gravacao foi verificada no fonte final: duas vitorias de controle, em JVMs diferentes, produziram duas linhas distintas com `won=1`, sem duplicatas. Um arquivo preenchido artificialmente ate 47994 bytes foi reduzido por descarte de linhas antigas e terminou com 47999 bytes. As linhas artificiais sao somente fixture do teste e nao sao rounds reais. Dois controles adicionais com adversarios fortes registraram corretamente `won=0`. A correcao posterior afeta a escrita apos o resultado do round; as equacoes e os comandos de combate sao os mesmos da candidata avaliada.

Dados: `tests/diagnostic-duel-results.json`, `tests/diagnostic-duel-selection.json`, `tests/diagnostic-duel-validation.json` e `tests/diagnostic-duel-records.csv`. Os metadados preservam a auditoria de campos anteriores a correcao e os controles do fonte final. Os logs completos permanecem no ambiente de testes, com hashes nos registros.

## Atualizar no computador

1. Copie `robots/equipe/titanprime.java` da main para `C:\robocode\robots\equipe\titanprime.java`.
2. Abra pelo iniciador Java 17 e compile no editor do Robocode. Os `.class` internos sao gerados pelo compilador.
3. Ao encerrar um round, procure `C:\robocode\robots\equipe\titanprime.data\diagnostico.csv`. Esse caminho foi confirmado na instalacao de testes. Em versoes com outro armazenamento, use a pasta de dados que o Robocode atribui ao Prime.
4. Envie esse CSV depois dos proximos testes. Ele e mantido ao fechar o jogo normalmente, ate o limite de tamanho descrito acima.

Para reproduzir a selecao, recupere o fonte da 3.9 no commit `a1a5daaf1799e10d9bf210ab9f70a711306abd79` e prepare uma instalacao com Diamond e TitanV3. A ferramenta exige uma pasta de saida nova:

```sh
python tools/diagnostic_duel.py --home <robocode> --ecj <ecj.jar> --source <titanprime-3.9.java> --output <pasta-nova>
python tests/diagnostic_persistence.py --home <robocode> --ecj <ecj.jar> --output <outra-pasta-nova>
```

O segundo comando gera um alvo passivo temporario somente para verificar persistencia, limites e ordem dos eventos. Ele nao deve entrar no ranking de adversarios ou no calculo de desempenho.
