-- Anonymous usage statistics (POST /api/stats): one row per installation per day, sent only
-- by apps whose owner turned statistics on. Rows older than a year are deleted daily.
CREATE TABLE daily (
  id TEXT NOT NULL,          -- random per-installation UUID, forgotten when statistics are turned off
  day TEXT NOT NULL,         -- YYYY-MM-DD, the device's local day
  app TEXT NOT NULL,         -- CastBay version
  android TEXT NOT NULL,
  sdk INTEGER NOT NULL,
  maker TEXT NOT NULL,
  model TEXT NOT NULL,
  screen TEXT NOT NULL,      -- 2160p / 1440p / 1080p / 720p / smaller
  device TEXT NOT NULL,      -- tv / car / other
  touch INTEGER NOT NULL,
  lang TEXT NOT NULL,
  counts TEXT NOT NULL,      -- JSON object of whitelisted counters
  settings TEXT NOT NULL,    -- JSON object of whitelisted settings
  received TEXT NOT NULL,
  PRIMARY KEY (id, day)
);
CREATE INDEX daily_day ON daily (day);
