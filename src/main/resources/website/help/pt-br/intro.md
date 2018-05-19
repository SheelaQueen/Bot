# GamesROB
GamesROB é um bot de Discord para jogar partidas em chat de texto.

## Games

- [Lig4](/help/games/connectfour) - `g*connectfour`
Lig4 é um jogo divertido com objetivo de alinhar 4 botões. Você escolhe uma linha de 1 à 6 para derrubar um botão da sua cor.

- [Jogo Da Velha](/help/games/tictactoe) - `g*tictactoe`
Jogo Da Velha é um jogo bem simples, onde você começa com uma área vazia 3x3, com objetivo de conseguir 3 do seu símbolo em uma linha, prevenindo o oponente de fazer o mesmo durante isso.

- [Forca](/help/games/hangman) - `g*hangman`
Forca é outro jogo com um conceito simples. Um jogador escolhe uma palavra, e os oponents tentam adivinhar-na, perdendo partes cada tentativa errada. Se não conseguirem adivinhar em 5 tentativas, eles perdem.

- [Campo Minado](/help/games/minesweeper) - `g*minesweeper`
Campo Minado é um jogo diferente dos outros. É um jogo solo. O objetivo é revelar todos os blocos que não tem bombas. Quanto você revela um bloco que não é uma bomba, ele irá te mostrar o número de bombas nos 8 blocos próximos

## Características

- **Multijogador**: Adicionando a quantidade de jogadores com qual você quer jogar no fim do comando (ex. `g*connectfour 3` para Lig4 de 3 jogadores), você pode jogar com quantas pessoas quiser.
- **Placares de Liderança**: Tenha uma competição no seu servidor! Na página dele e com o comando `g*leaderboard`, você pode ver os 5+ melhores jogadores em geral e para cada jogo.
- **Customização**: Você pode mudar o prefixo do bot, a língua padrão do servidor e as permissões para começar e parar jogos na página do seu servidor.

## Comandos
Aqui estão alguns dos comandos que você precisa saber:

- `g*lm` e `g*leave` - Sai da partida que você está.
- `g*jm` e `g*join` - Entra em uma partida começada por outra pessoa no canal atual.
- `g*hm`, `g*ttt`, `g*c4`, and `g*ms` - Usado para começar jogos (esses são atalhos, você também pode usar o nome do jogo em inglês).
- `g*stop` - Força uma parada no jogo (Este **é** um comando para admins).
- `g*help` - Uma lista de jogos e um link para essa página.
- `g*emote` ou `g*et` - Altere seu símbolo usado em Jogo da Velha e Lig4.
- `g*lang` - Altera a linguagem que o bot deveria utilizar quando ele responde aos seus comandos. Você também pode mudar a língua padrão no seu servidor com `g*glang` (Apenas para admins). Usando `g*lang` sem argumentos irá retornar a lista de linguagens que suportamos atualmente.

[Ver a lista completa com mais informações](/help/commands)

## De onde veio esse bot?
Era uma vez, em Outubro de 2017, tinha um bot místico chamado Bot. Ele tinha várias funções, como Mee6 e Dyno. Ele tinha uma funcionalidade de jogos que era bem simples, com Lig4 e Jogo da Velha. Boat nunca deu certo, tinha 1000 servidores quando fechamos o projeto. Era só outro bot com várias funções. Pegamos a funcionalidade de jogos, como era a mais popular e fizemos dela um bot inteiro- **GamesROB**.