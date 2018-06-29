# GamesROB
A Discord bot for playing games from chat!<br>
[![Discord Bots](https://discordbots.org/api/widget/383995098754711555.svg?topcolor=132028&highlightcolor=132028&certifiedcolor=e4a72f&datacolor=FFFFFF)](https://discordbots.org/bot/383995098754711555)

## The Games
All the games the bot current has:
- TicTacToe
- Connect4
- Minesweeper
- Hangman
- Russian Roulette
- Detective

## Links
[Trello](https://trello.com/b/rdcazEMd/gamesrob)<br>
[Support Server](https://discord.gg/xajeDYR)

### Running the bot yourself
Click on the releases button on the top, choose the version you want and click on GamesROB.jar.<br>
You'll need to add the `token=<bot token>` argument when running the bot, whether you do it on a batch file or .sh file.

### How to build a jar yourself
1. Install [jdk8](http://www.oracle.com/technetwork/pt/java/javase/downloads/jdk8-downloads-2133151.html), [git](https://git-scm.com/downloads) and [IntelliJ](https://www.jetbrains.com/idea/download/).<br>
2. With cmd on an empty folder, type `git clone https://github.com/deprilula28/GamesROB` and `git clone https://github.com/deprilula28/DepsJDAFramework`.
3. Open up the GamesROB and DepsJDAFramework folders with IntelliJ. On the notification on the bottom, choose to set as Maven project for both.<br>
For setting up the maven installs on both projects, click the combo box (aka dropdown) besides the play button on top of IntelliJ, and Edit Configurations.<br>
5. On DepsJDAFramework, click +, Maven, and type `clean install` on command line. Click the play button and wait until it installs.<br>
6. On GamesROB, click +, Maven, and type `clean package` on the command line. Click the play button and wait until it builds.
7. On the target folder in GamesROB, you have the gamesrob.jar file.
