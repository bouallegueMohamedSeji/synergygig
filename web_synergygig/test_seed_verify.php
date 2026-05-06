<?php
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

$now = date('Y-m-d H:i:s');
$today = date('Y-m-d');

// Seed departments
$pdo->exec("INSERT IGNORE INTO departments (id, name, description, allocated_budget) VALUES (1, 'Engineering', 'Software development team', 50000)");

// Seed leaves
$pdo->exec("INSERT IGNORE INTO leaves (id, user_id, type, start_date, end_date, reason, status, created_at) VALUES (1, 1, 'ANNUAL', '2026-04-10', '2026-04-15', 'Family vacation', 'PENDING', '$now')");

// Seed payrolls
$pdo->exec("INSERT IGNORE INTO payrolls (id, user_id, month, year, base_salary, bonus, deductions, net_salary, total_hours_worked, status, generated_at) VALUES (1, 1, 4, 2026, 3500.00, 200.00, 150.00, 3550.00, 160, 'GENERATED', '$now')");

// Seed projects
$pdo->exec("INSERT IGNORE INTO projects (id, name, description, status, start_date, end_date, budget, created_at) VALUES (1, 'SynergyGig Platform', 'Main HR platform development', 'IN_PROGRESS', '2026-01-01', '2026-12-31', 100000, '$now')");

// Seed tasks
$pdo->exec("INSERT IGNORE INTO tasks (id, title, description, status, priority, due_date, project_id, assigned_to, created_at) VALUES (1, 'Build Dashboard', 'Create main dashboard with stats', 'IN_PROGRESS', 'HIGH', '2026-04-30', 1, 1, '$now')");

// Seed interviews
$pdo->exec("INSERT IGNORE INTO interviews (id, organizer_id, candidate_id, date_time, status, meet_link, notes, created_at) VALUES (1, 1, 2, '2026-04-10 10:00:00', 'PENDING', 'https://meet.google.com/abc', 'Technical interview', '$now')");

// Seed offers
$pdo->exec("INSERT IGNORE INTO offers (id, title, description, location, salary_range, status, created_at) VALUES (1, 'Senior PHP Developer', 'Looking for experienced Symfony dev', 'Tunis, Tunisia', '3000-5000 TND', 'OPEN', '$now')");

// Seed training
$pdo->exec("INSERT IGNORE INTO training_courses (id, title, description, instructor, duration, status, created_at) VALUES (1, 'Symfony Mastery', 'Advanced Symfony 6 workshop', 'Expert Trainer', 40, 'ACTIVE', '$now')");

// Seed contracts
$pdo->exec("INSERT IGNORE INTO contracts (id, offer_id, applicant_id, owner_id, terms, amount, currency, status, created_at) VALUES (1, 1, 2, 1, 'Standard employment terms', 4000.00, 'TND', 'DRAFT', '$now')");

// Seed job applications
$pdo->exec("INSERT IGNORE INTO job_applications (id, offer_id, applicant_id, cover_letter, status, created_at) VALUES (1, 1, 2, 'I am very interested in this position.', 'PENDING', '$now')");

echo "Seeded test data successfully.\n";

// Now verify all show/edit pages
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

$ok = 0; $fail = 0; $errors = [];
foreach ($modules as $base => $table) {
    $row = $pdo->query("SELECT id FROM `$table` ORDER BY id LIMIT 1")->fetch();
    if (!$row) { echo "EMPTY $table\n"; continue; }
    $id = $row['id'];

    foreach (['', '/edit'] as $suffix) {
        $url = "http://127.0.0.1:8000{$base}{$id}{$suffix}";
        $ctx = stream_context_create(['http' => ['timeout' => 8]]);
        $body = @file_get_contents($url, false, $ctx);
        $status = $http_response_header[0] ?? 'FAIL';
        $is200 = strpos($status, '200') !== false;
        if ($is200) { $ok++; }
        else {
            $fail++;
            $errors[] = "{$base}{$id}{$suffix}";
            // Get error detail from response
            if ($body && preg_match('/<title>(.*?)<\/title>/s', $body, $m)) {
                $errors[] = "  -> " . strip_tags($m[1]);
            }
        }
        echo ($is200 ? 'OK  ' : 'ERR ') . "{$base}{$id}{$suffix}\n";
    }
}

// Community post
$row = $pdo->query("SELECT id FROM posts LIMIT 1")->fetch();
if ($row) {
    $url = "http://127.0.0.1:8000/community/post/{$row['id']}";
    $ctx = stream_context_create(['http' => ['timeout' => 8]]);
    $body = @file_get_contents($url, false, $ctx);
    $status = $http_response_header[0] ?? 'FAIL';
    $is200 = strpos($status, '200') !== false;
    echo ($is200 ? 'OK  ' : 'ERR ') . "/community/post/{$row['id']}\n";
    $is200 ? $ok++ : $fail++;
}

echo "\n=== SHOW/EDIT RESULTS ===\nOK: $ok | FAIL: $fail\n";
if ($errors) echo "ERRORS:\n" . implode("\n", $errors) . "\n";
