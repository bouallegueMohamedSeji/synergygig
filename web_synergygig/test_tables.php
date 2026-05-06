<?php
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');
foreach ($pdo->query('SHOW TABLES') as $r) {
    $t = $r[0];
    $c = $pdo->query("SELECT COUNT(*) FROM `$t`")->fetchColumn();
    echo "$t ($c rows)\n";
}
