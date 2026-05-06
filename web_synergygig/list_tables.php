<?php
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');
$stmt = $pdo->query("SHOW TABLES");
while ($r = $stmt->fetch(PDO::FETCH_NUM)) {
    $count = $pdo->query("SELECT COUNT(*) FROM `{$r[0]}`")->fetchColumn();
    echo "{$r[0]} ($count)\n";
}
