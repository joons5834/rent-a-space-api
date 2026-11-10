-- 공간 카테고리 시드.
-- 카테고리 생성 API가 없어서 공간을 만들려면 이 값들이 먼저 있어야 한다.
INSERT INTO categories (name) VALUES
    ('meeting'),
    ('practice'),
    ('studio'),
    ('party')
ON CONFLICT (name) DO NOTHING;
