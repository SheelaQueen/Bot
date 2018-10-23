CREATE TABLE IF NOT EXISTS guilddata (
    permstartgame TEXT, 
    permstopgame TEXT, 
    prefix TEXT,
    guildid TEXT,
    leaderboardbackgroundimgurl TEXT,
    language TEXT
);
CREATE TABLE IF NOT EXISTS userdata (
    emote TEXT,
    language TEXT,
    tokens INT,
    lastupvote BIGINT,
    upvoteddays INT,
    userid TEXT,
    profilebackgroundimgurl TEXT,
    websitesession TEXT,
    badges INT,
    gameplaytime BIGINT,
    gamesplayed INT
);
CREATE TABLE IF NOT EXISTS leaderboardentries (
    userid TEXT,
    guildid TEXT,
    gameid TEXT,
    victories INT,
    losses INT,
    gamesplayed INT
);
CREATE TABLE IF NOT EXISTS statsplots (
    shardsOnline INT,
    avgCommandDelay BIGINT,
    totalCommandsExecuted BIGINT,
    totalGamesPlayed BIGINT,
    activeGames BIGINT,
    websocketPing BIGINT,
    guilds BIGINT,
    users BIGINT,
    textChannels BIGINT,
    ramUsage BIGINT,
    upvotes BIGINT,

    time BIGINT
);
CREATE TABLE IF NOT EXISTS achievements (
    type TEXT,
    userid TEXT,
    amount INT
);
CREATE TABLE IF NOT EXISTS blacklist (
    userid TEXT,
    botownerid TEXT,
    reason TEXT,
    time BIGINT
);
CREATE TABLE IF NOT EXISTS transactions (
    userid TEXT,
    amount INT,
    time BIGINT,
    message SMALLINT
);
CREATE TABLE IF NOT EXISTS translationsuggestions (
    language TEXT,
    key TEXT,
    translation TEXT,
    time BIGINT,
    userid TEXT,
    anonymous BOOLEAN,
    rating INT,
    raters TEXT[]
);

ALTER TABLE userdata ADD COLUMN gamesplayed INT;
ALTER TABLE userdata ADD COLUMN gameplaytime BIGINT;