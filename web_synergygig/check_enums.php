<?php
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');
$tables = [
    'leaves' => ['type','status'],
    'payrolls' => ['status'],
    'contracts' => ['status'],
    'offers' => ['offer_type','status'],
    'projects' => ['status'],
    'interviews' => ['status'],
    'job_applications' => ['status'],
    'tasks' => ['status','priority'],
    'training_courses' => ['status','category','difficulty'],
];
foreach ($tables as $t => $cols) {
    echo "=== $t ===\n";
    $stmt = $pdo->query("DESCRIBE $t");
    while ($r = $stmt->fetch(PDO::FETCH_ASSOC)) {
        if (in_array($r['Field'], $cols)) {
            echo "  {$r['Field']}: {$r['Type']}\n";
        }
    }
}
