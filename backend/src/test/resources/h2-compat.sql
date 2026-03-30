-- H2 PostgreSQL compatibility: create JSONB domain so @ColumnTransformer(write = "?::jsonb") works
CREATE DOMAIN IF NOT EXISTS JSONB AS CLOB;
