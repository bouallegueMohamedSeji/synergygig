-- Create gigs table
CREATE TABLE IF NOT EXISTS gigs (
    id INT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    user_id INT, -- Worker
    status VARCHAR(50) DEFAULT 'OPEN',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- Add department_id to users table (if not exists)
-- Note: MySQL doesn't support IF NOT EXISTS for columns in ALTER TABLE directly in all versions, 
-- but we can try to add it. If it fails, it might already exist.
-- Ideally we would check information_schema, but for simplicity:

ALTER TABLE users ADD COLUMN department_id INT NULL;
ALTER TABLE users ADD CONSTRAINT fk_user_dept FOREIGN KEY (department_id) REFERENCES departments(id) ON DELETE SET NULL;
