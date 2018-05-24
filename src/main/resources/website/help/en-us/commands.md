# Commands
These are all the commands the bot has to offer.<br>
Arguments in &lt;these&gt; are required, while arguments in [these] are optional.

- `g*hm`, `g*ttt`, `g*c4`, and `g*ms` - Used to start games (those are shortcut commands, the full game names also work).
- `g*jm` and `g*join` - Joins a match started by someone else in the current channel.
- `g*stop` - Force stops the game going on in the current channel (**Admin only**).
- `g*listplayers` - View all the players on the match for the channel you run the command in.

- `g*slots <amount>` - Gamble your tokens in a slots game! 100% not rigged trust me
- `g*tokens [user]` - See how many tokens you or someone else has.
- `g*profile [user]` - Shows you (or someone else's) profile in the guild. This is also viewable by clicking a name in your server's page in the website. 
- `g*leaderboard` - Sends the leaderboard for your guild. This is also viewable from your server's page in the website.

- `g*emote <emote>` - Change your tile used in ConnectFour and TicTacToe.
- `g*lang [language]` - Changes the language the bot should use when it responds to your commands. 
- `g*glang [language]` - Changes the language the bot uses on your server (**Admin only**).
- `g*setprefix <prefix>` - Set the prefix the bot should use on your server (What you use before commands, i.e. `g*` **Admin only**).

- `g*changelog` - Shows the changelog for the latest update.
- `g*invite` - A quick link to invite the bot.
- `g*ping` - Views the ping (or latency) from the bot to Discord. This is the avg. time it takes for your command to get processed.
- `g*help` - A list of games and a link to this page.
- `g*info` - Displays basic info about the bot.
- `g*shardinfo` and `g*shards` - Shows the current amount of servers, channels within those servers, and users in those servers the bot is in (also shows them split up into spooky scary "shards" for debug purposes).

## Game Settings
Some games support settings, that allow you to get a more customized gameplay. If you look on that game's page, you'll see more information about the supported settings.

To change these settings, use the `key=value` syntax and add the text in the end of the game's command. (i.e. `g*minesweeper bombs=10`)