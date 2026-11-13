# Diagnóstico do computador

O arquivo de compilação enviado pelo usuário termina com `Compiled successfully.`. A compilação levou aproximadamente 32 segundos. Havia um aviso de uso de `new Long(long)`, substituído no fonte atual por `Long.toString(...)`. O aviso não impedia a geração das classes.

O log de execução mostra Java 17.0.21+9 com Robocode 1.11.1. A sequência `Unable to stop thread`, `cannot be stopped` e `You cannot take action in this thread!` corresponde a um problema corrigido oficialmente no [Robocode 1.11.2](https://github.com/robo-code/robocode/releases/tag/v1.11.2). O gatilho pode ser um robô exceder seu orçamento de CPU; a versão antiga falhava ao encerrar a thread e comprometia rounds seguintes.

Use o [Robocode 1.12.0](https://github.com/robo-code/robocode/releases/tag/v1.12.0), instalado em uma pasta separada, preservando a instalação e os dados anteriores. O Java 17 já está correto. Importe o JAR completo ou recompile o Java único nessa nova instalação. O aviso de descontinuação do SecurityManager, por si só, não indica falha na compilação.

A atualização acrescenta encerramento cooperativo do loop após os eventos terminais. A comemoração executa 64 frames antes de gravar a memória e encerrar. Isso foi verificado no motor real. O teste de recarga também foi executado em Linux com Temurin 17.0.21+9 e Robocode 1.12.0; esse ambiente não reproduz a velocidade de CPU nem todo o Windows do usuário. Ainda é necessário observar turnos perdidos no computador real.

O fonte Java não contém comentários. Os avisos de autoria e as condições da base incorporada permanecem em [licenses/source-notices.txt](licenses/source-notices.txt), na [licença](licenses/diamond.txt) e no JAR distribuído.
