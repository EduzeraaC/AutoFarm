CREATE TABLE IF NOT EXISTS `auto_farm` (
    `player_id` INT UNSIGNED NOT NULL DEFAULT 0,
    `name` VARCHAR(35),
    `remaining_minutes` INT NOT NULL DEFAULT 0,
    PRIMARY KEY (`player_id`)
);