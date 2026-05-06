<?php
require __DIR__ . '/vendor/autoload.php';

use Doctrine\DBAL\DriverManager;

// Load .env manually
$envFile = __DIR__ . '/.env';
if (file_exists($envFile)) {
    foreach (file($envFile) as $line) {
        $line = trim($line);
        if ($line === '' || str_starts_with($line, '#') || !str_contains($line, '=')) continue;
        [$k, $v] = explode('=', $line, 2);
        $_ENV[trim($k)] = trim($v, " \t\n\r\"'");
    }
}

$conn = DriverManager::getConnection([
    'driver'   => 'pdo_mysql',
    'host'     => $_ENV['DB_HOST'] ?? '127.0.0.1',
    'port'     => (int)($_ENV['DB_PORT'] ?? 3306),
    'dbname'   => $_ENV['DB_NAME'] ?? 'finale_synergygig',
    'user'     => $_ENV['DB_USER'] ?? 'root',
    'password' => $_ENV['DB_PASSWORD'] ?? '',
    'charset'  => 'utf8mb4',
]);

$hash = password_hash('Admin1234!', PASSWORD_BCRYPT);
$affected = $conn->executeStatement(
    "UPDATE user SET password = ?, is_verified = 1, is_active = 1 WHERE email = 'admin@synergygig.local'",
    [$hash]
);

echo "Updated $affected row(s).\n";
echo "Login with: admin@synergygig.local / Admin1234!\n";
