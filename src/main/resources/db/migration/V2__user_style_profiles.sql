CREATE TABLE user_style_profiles(
    user_id UUID PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    preferred_styles JSONB,
    preferred_colors JSONB,
    body_measurements JSONB,
    default_size JSONB,
    age_group VARCHAR(50),
    gender_pref VARCHAR(20)
);