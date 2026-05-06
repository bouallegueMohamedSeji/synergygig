<?php
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');

$modules = [
    '/users/' => 'user',
    '/departments/' => 'departments',
    '/attendance/' => 'attendance',
    '/leaves/' => 'leaves',
    '/payroll/' => 'payrolls',
    '/projects/' => 'projects',
    '/tasks/' => 'tasks',
    '/interviews/' => 'interviews',
    '/offers/' => 'offers',
    '/training/' => 'training_courses',
    '/contracts/' => 'contracts',
    '/applications/' => 'job_applications',
];

$ok = 0; $fail = 0; $skip = 0; $errors = [];
foreach ($modules as $base => $table) {
    $row = $pdo->query("SELECT id FROM `$table` ORDER BY id LIMIT 1")->fetch();
    if (!$row) {
        echo "SKIP  (empty) $table -> $base\n";
        $skip++;
        continue;
    }
    $id = $row['id'];

    // Show page
    $ctx = stream_context_create(['http' => ['timeout' => 5]]);
    $body = @file_get_contents("http://127.0.0.1:8000{$base}{$id}", false, $ctx);
    $status = $http_response_header[0] ?? 'FAIL';
    $is200 = strpos($status, '200') !== false;
    if ($is200) { $ok++; echo "OK   {$base}{$id}\n"; }
    else { $fail++; $errors[] = "{$base}{$id} => $status"; echo "ERR  $status  {$base}{$id}\n"; }

    // Edit page
    $body2 = @file_get_contents("http://127.0.0.1:8000{$base}{$id}/edit", false, $ctx);
    $status2 = $http_response_header[0] ?? 'FAIL';
    $is200e = strpos($status2, '200') !== false;
    if ($is200e) { $ok++; echo "OK   {$base}{$id}/edit\n"; }
    else { $fail++; $errors[] = "{$base}{$id}/edit => $status2"; echo "ERR  $status2  {$base}{$id}/edit\n"; }
}

// Community post show
$row = $pdo->query("SELECT id FROM posts ORDER BY id LIMIT 1")->fetch();
if ($row) {
    $id = $row['id'];
    $ctx = stream_context_create(['http' => ['timeout' => 5]]);
    $body = @file_get_contents("http://127.0.0.1:8000/community/post/{$id}", false, $ctx);
    $status = $http_response_header[0] ?? 'FAIL';
    $is200 = strpos($status, '200') !== false;
    if ($is200) { $ok++; echo "OK   /community/post/{$id}\n"; }
    else { $fail++; $errors[] = "/community/post/{$id} => $status"; echo "ERR  $status  /community/post/{$id}\n"; }
} else { $skip++; echo "SKIP  No posts\n"; }

echo "\n=== RESULTS ===\nOK: $ok | FAIL: $fail | SKIP: $skip\n";
if ($errors) echo "ERRORS:\n" . implode("\n", $errors) . "\n";
