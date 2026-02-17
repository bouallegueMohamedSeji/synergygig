USE finale_synergygig;

-- 1. Seed Skills (if not exists)
INSERT IGNORE INTO skills (name) VALUES 
('Java'), 
('Python'), 
('Web Development'), 
('Data Science'), 
('Communication'), 
('Project Management'), 
('Design'), 
('Marketing');

-- 2. Alter Courses Table to add skill_id
-- Check if column exists first (workaround for MySQL 5.7+ not supporting IF NOT EXISTS for columns directly in simple ALTER)
-- We will just try to add it. If it fails, it might already exist, but safe to try given previous review showed it missing.

ALTER TABLE courses ADD COLUMN skill_id INT NULL;
ALTER TABLE courses ADD CONSTRAINT fk_courses_skill FOREIGN KEY (skill_id) REFERENCES skills(id) ON DELETE SET NULL;
