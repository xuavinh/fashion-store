CREATE TABLE categories (
    category_id UUID PRIMARY KEY,
    parent_id UUID REFERENCES categories(category_id) ON DELETE SET NULL,
    name VARCHAR(200) NOT NULL,
    slug VARCHAR(200) NOT NULL UNIQUE,
    level SMALLINT NOT NULL DEFAULT 1,
    gender_filter VARCHAR(20) NOT NULL DEFAULT 'ALL'
);