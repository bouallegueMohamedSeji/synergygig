<?php
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');
$tables = ['leaves', 'projects', 'tasks', 'interviews', 'offers', 'training_courses', 'contracts', 'job_applications', 'payrolls'];
foreach ($tables as $t) {
    echo "\n=== $t ===\n";
    $s = $pdo->query("DESCRIBE `$t`");
    while ($r = $s->fetch(PDO::FETCH_ASSOC)) {
        echo $r['Field'] . ' ' . $r['Type'] . ($r['Null'] === 'YES' ? ' NULL' : '') . "\n";
    }
}
