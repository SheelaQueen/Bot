WITH ranked AS (
    SELECT userid, rank() over(ORDER BY tokens DESC) AS rank FROM userdata WHERE %s
)
SELECT rank FROM ranked WHERE userid = ?;