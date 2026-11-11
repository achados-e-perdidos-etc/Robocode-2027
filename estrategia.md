# Estratégia atual — Titan Prime 4.8

O fonte completo é [robots/equipe/titanprime.java](robots/equipe/titanprime.java). Há um único Java e nenhuma dependência de classes do adversário, servidor externo ou internet durante a batalha.

A base de combate foi substituída por uma **adaptação direta do Diamond aberto**, de Voidious, incluindo as árvores de Rednaxela. A licença permite a adaptação e exige preservar a origem e os avisos. Os avisos originais continuam no fonte e a licença integral está em [licenses/diamond.txt](licenses/diamond.txt). O código não é apresentado como criação original deste projeto nem como o Diamond oficial.

| Área | Implementação ativa |
| --- | --- |
| Radar | Acompanhamento no duelo e atualização dos adversários em melee, com posição, energia e idade dos scans. |
| Defesa 1v1 | Wave Surfing com modelos KNN de perigo, interseção precisa, previsão física, ângulos de escape e sombras de balas próprias. |
| Defesa melee | Minimum Risk Movement com 64 direções candidatas, adversários e posições recentes; recuperação limitada quando todas as rotas previstas atingem paredes. |
| Mira 1v1 | Mira inicial perceptual, TripHammer KNN e mira específica para movimentos adaptativos, além de duas miras neurais online, comparadas por Virtual Guns e ondas. |
| Mira melee | Distribuição de ângulos por deslocamentos históricos, considerando os vários alvos. |
| Energia | Em duelos, potência limitada a 0,1, com reserva de energia abaixo de 18 e espaçamento de tiros em condições de distância e energia inimiga. No Melee, o controlador considera distância, energias e potência de finalização. |
| Aprendizado | Contextos KNN, ondas e desempenho das miras são atualizados durante a batalha e conservados entre seus rounds. |
| Diagnóstico | CSV limitado a 48 KB, com um registro por round e correção da vitória quando o evento chega depois do encerramento. |
| Visual | Cor sorteada por batalha; giro e cores animadas após vencer um round; pintura de radar, mira e movimento. |

O genoma e os controladores 3.x deixaram de comandar o robô. As duas novas redes neurais são independentes daquele motor antigo. Enumerar 25 técnicas não significa ter 25 sistemas independentes ativos. A tabela descreve o código realmente adotado.

O aprendizado de combate novo fica em memória durante a batalha; não é recarregado de arquivos em batalhas independentes. O CSV é observação, não um modelo treinado. Nos testes com largadas aleatórias, os identificadores dos lotes não fixam sementes do PRNG.

O robô luta sozinho. Todos contra todos significa que os inimigos também se enfrentam; uma equipe coordenada é uma avaliação diferente. A lista de robôs na instalação e os resultados de versões antigas continuam como histórico, sem provar o desempenho da 4.8.

Veja a [arquitetura](arquitetura.md), a [pesquisa e validação](ciclo-elite.md) e o [guia de instalação](como-testar.md).
