WITH ranked AS (
    SELECT userid, rank() over(ORDER BY tournmentwins DESC) AS rank FROM premiumguildmember WHERE guildid = ?
)
SELECT rank FROM ranked WHERE userid = ?;