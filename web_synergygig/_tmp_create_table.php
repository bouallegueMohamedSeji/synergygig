<?php
$host = getenv('DB_HOST') ?: '127.0.0.1';
$port = getenv('DB_PORT') ?: '3306';
$name = getenv('DB_NAME') ?: 'finale_synergygig';
$user = getenv('DB_USER') ?: 'root';
$pass = getenv('DB_PASSWORD') ?: '';
$dsn = sprintf('mysql:host=%s;port=%s;dbname=%s', $host, $port, $name);
$pdo = new PDO($dsn, $user, $pass);
$pdo->exec('CREATE TABLE IF NOT EXISTS call_signals (
    id INT AUTO_INCREMENT PRIMARY KEY,
    call_id INT NOT NULL,
    from_user_id INT NOT NULL,
    signal_type VARCHAR(20) NOT NULL,
    payload LONGTEXT NOT NULL,
    created_at DATETIME NOT NULL,
    INDEX idx_call_signals_call (call_id),
    INDEX idx_call_signals_user (from_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4');
echo 'Table created successfully';
