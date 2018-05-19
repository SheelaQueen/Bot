# GamesROB
GamesROB is a Discord Bot for playing games in text chat.

## Games

- [ConnectFour](/help/games/connectfour) - `g*connectfour`
ConnectFour is a fun game where your goal is to get 4 tiles in a row.  You choose a slot from one to 9, and a tile of your color falls into the slot.

- [TicTacToe](/help/games/tictactoe) - `g*tictactoe`
TicTacToe is a really simple game, you start with an empty 3x3 board, and your objective is to get 3 of your tile in a row, preventing your opponent from winning in the process.

- [Hangman](/help/games/hangman) - `g*hangman`
Hangman is another game with a basic concept. The person who starts the game messages the bot a word in DMs, then their opponent has to try and guess the word they put, losing limbs with every letter they guess incorrectly, and if they don't guess the word within enough tries, their stickman dies.

- [Minesweeper](/help/games/minesweeper) - `g*minesweeper`
Minesweeper is a different game from the others. Minesweeper is singleplayer only. Your goal is to reveal all tiles without bombs. It isn't as hard is you may think though, you do get some hints. When you reveal a tile, it either shows up with a bomb, a number, or a blank. If it shows you a bomb, you lost. If it shows you a number or a blank, it means there are the number of bombs shown on the tile (blank means 0) in the surrounding 8 tiles.

## Features

- **Multiplayer**: By adding the amount of players you want to play with in the end of a game's command (i.e. `g*connectfour 3` for 3 player ConnectFour), you can play with however many people you want.
- **Leaderboards**: Have some competition in your guild! On your server's page and with `g*leaderboard` you can see the top 5+ players overall and for each game.
- **Customizability**: You can change the bot's prefix, the default server language and the permissions to stop and start games on your server's page.

## Commands
Here are some of the basic commands you need to know:

- `g*lm` and `g*leave` - Leaves the match you are currently in.
- `g*jm` and `g*join` - Joins a match started by someone else in the current channel.
- `g*hm`, `g*ttt`, `g*c4`, and `g*ms` - Used to start games (those are shortcut commands, the full game names also work).
- `g*stop` - Force stops the game going on in the current channel (this **is** an admin command).
- `g*help` - A list of games and a link to this page.
- `g*emote` or `g*et` - Change your tile used in ConnectFour and TicTacToe.
- `g*lang` - Changes the language the bot should use when it responds to your commands. You can also change the default language used in the guild current guild with `g*glang` (Admin only). Doing the command with no arguments gives you a list of all the languages we currently support (Join the server and ping deprilula28 if you'd like to translate the bot).

[Click for the full list and more information](/help/commands)

## Where'd the bot come from?
Wayyyy back, in 2017, around October, there was a mystical magical bot named Boat. This bot was a multi-purpose, like Mee6 or Dyno. This bot had a games feature that was nice and simple. It had two games- TicTacToe and ConnectFour. Boat never caught on, it had around 1000 servers when we decided to close the project. It was just another multi-purpose bot. We took the games feature, as that was the most popular feature BY FAR and put it in it's own bot, the bot we know and love today- **GamesROB**.
