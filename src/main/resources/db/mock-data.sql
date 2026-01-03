create database if not exists commerce;
use commerce;

INSERT INTO admin(username, role, password)
VALUES ("admin", "ROLE_ADMIN", "$2a$10$33jAxhxoVRtTN4ybMoSkB.ZY2jqjcx/8o2FyhgnrrDJlTkvLRF0Ji")


-- user 1000명 삽입
INSERT INTO `user` (username, role, created_at, updated_at)
WITH RECURSIVE seq(n) AS (
    SELECT 0
    UNION ALL
    SELECT n + 1 FROM seq WHERE n < 999
    )
SELECT CONCAT('user', n), 'ROLE_USER', NOW(), NOW()
FROM seq;