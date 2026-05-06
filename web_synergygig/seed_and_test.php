<?php
/**
 * Seed test data into empty tables and verify all CRUD pages work.
 * Uses correct DB ENUM values discovered via DESCRIBE queries.
 */
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

// Get existing user IDs
$users = $pdo->query("SELECT id FROM user LIMIT 2")->fetchAll(PDO::FETCH_COLUMN);
if (count($users) < 2) { die("ERROR: Need at least 2 users in DB\n"); }
$u1 = $users[0]; $u2 = $users[1];

// Get existing department
$depts = $pdo->query("SELECT id FROM departments LIMIT 1")->fetchAll(PDO::FETCH_COLUMN);
$d1 = $depts[0] ?? null;

$seeded = [];
$errors = [];

function seedIfEmpty($pdo, $table, $sql, $params, &$seeded, &$errors) {
    $count = $pdo->query("SELECT COUNT(*) FROM $table")->fetchColumn();
    if ($count > 0) {
        echo "  $table: already has $count rows, SKIP\n";
        return;
    }
    try {
        $stmt = $pdo->prepare($sql);
        $stmt->execute($params);
        $seeded[] = $table;
        echo "  $table: SEEDED (id=" . $pdo->lastInsertId() . ")\n";
    } catch (Exception $e) {
        $errors[] = "$table: " . $e->getMessage();
        echo "  $table: ERROR - " . $e->getMessage() . "\n";
    }
}

echo "=== SEEDING TEST DATA ===\n";

// Leaves - DB enum: SICK,VACATION,UNPAID,PERSONAL,MATERNITY,PATERNITY / status: PENDING,APPROVED,REJECTED
seedIfEmpty($pdo, 'leaves',
    "INSERT INTO leaves (user_id, type, start_date, end_date, reason, status, created_at) VALUES (?, 'VACATION', '2025-01-15', '2025-01-20', 'Holiday trip', 'PENDING', NOW())",
    [$u1], $seeded, $errors);

// Projects - status is varchar(50)
seedIfEmpty($pdo, 'projects',
    "INSERT INTO projects (name, description, owner_id, department_id, start_date, deadline, status, created_at) VALUES ('Test Project', 'A test project', ?, ?, '2025-01-01', '2025-06-30', 'IN_PROGRESS', NOW())",
    [$u1, $d1], $seeded, $errors);

// Get project id for tasks
$projId = $pdo->query("SELECT id FROM projects LIMIT 1")->fetchColumn();

// Tasks - status: TODO,IN_PROGRESS,DONE / priority: LOW,MEDIUM,HIGH
seedIfEmpty($pdo, 'tasks',
    "INSERT INTO tasks (title, description, project_id, assigned_to, status, priority, due_date, created_at) VALUES ('Test Task', 'A test task', ?, ?, 'TODO', 'MEDIUM', '2025-03-15', NOW())",
    [$projId, $u2], $seeded, $errors);

// Offers - offer_type: FULL_TIME,PART_TIME,FREELANCE,INTERNSHIP,CONTRACT / status: DRAFT,OPEN,CLOSED,CANCELLED
seedIfEmpty($pdo, 'offers',
    "INSERT INTO offers (title, description, offer_type, status, required_skills, location, amount, currency, owner_id, department_id, start_date, end_date, created_at) VALUES ('PHP Developer', 'Looking for a Symfony dev', 'FULL_TIME', 'OPEN', 'PHP, Symfony, MySQL', 'Tunis', 3000.00, 'TND', ?, ?, '2025-02-01', '2025-06-01', NOW())",
    [$u1, $d1], $seeded, $errors);

// Get offer id
$offerId = $pdo->query("SELECT id FROM offers LIMIT 1")->fetchColumn();

// Job Applications - status: PENDING,REVIEWED,SHORTLISTED,ACCEPTED,REJECTED,WITHDRAWN
seedIfEmpty($pdo, 'job_applications',
    "INSERT INTO job_applications (offer_id, applicant_id, cover_letter, status, applied_at) VALUES (?, ?, 'Cover letter text here', 'PENDING', NOW())",
    [$offerId, $u2], $seeded, $errors);

// Get application id
$appId = $pdo->query("SELECT id FROM job_applications LIMIT 1")->fetchColumn();

// Interviews - status: PENDING,ACCEPTED,REJECTED,COMPLETED,CANCELLED
seedIfEmpty($pdo, 'interviews',
    "INSERT INTO interviews (organizer_id, candidate_id, date_time, status, meet_link, offer_id, created_at) VALUES (?, ?, '2025-02-15 14:00:00', 'PENDING', 'https://meet.google.com/test', ?, NOW())",
    [$u1, $u2, $offerId], $seeded, $errors);

// Training Courses - category: TECHNICAL,SOFT_SKILLS,COMPLIANCE,ONBOARDING,LEADERSHIP / difficulty: BEGINNER,INTERMEDIATE,ADVANCED / status: DRAFT,ACTIVE,ARCHIVED
seedIfEmpty($pdo, 'training_courses',
    "INSERT INTO training_courses (title, description, category, difficulty, duration_hours, instructor_name, max_participants, status, start_date, end_date, created_at) VALUES ('Symfony Basics', 'Learn Symfony framework', 'TECHNICAL', 'BEGINNER', 8.0, 'Dr. Symfony', 30, 'ACTIVE', '2025-03-01', '2025-03-05', NOW())",
    [], $seeded, $errors);

// Payrolls - status: PENDING,PAID,CANCELLED
seedIfEmpty($pdo, 'payrolls',
    "INSERT INTO payrolls (user_id, month, year, base_salary, bonus, deductions, net_salary, total_hours_worked, status, generated_at) VALUES (?, 1, 2025, 2500.00, 200.00, 150.00, 2550.00, 160.0, 'PENDING', NOW())",
    [$u1], $seeded, $errors);

// Contracts - status: DRAFT,PENDING_SIGNATURE,ACTIVE,COMPLETED,TERMINATED,DISPUTED
seedIfEmpty($pdo, 'contracts',
    "INSERT INTO contracts (offer_id, applicant_id, owner_id, amount, currency, status, start_date, end_date, terms, created_at) VALUES (?, ?, ?, 3000.00, 'TND', 'DRAFT', '2025-03-01', '2026-03-01', 'Standard employment terms', NOW())",
    [$offerId, $u2, $u1], $seeded, $errors);

echo "\n=== SEED SUMMARY ===\n";
echo "Seeded: " . count($seeded) . " tables (" . implode(', ', $seeded) . ")\n";
echo "Errors: " . count($errors) . "\n";
foreach ($errors as $e) echo "  ! $e\n";

// Now test all pages
echo "\n=== TESTING ALL PAGES ===\n";
$base = 'http://127.0.0.1:8000';

// Index pages
$pages = [
    '/' => 'Dashboard',
    '/departments/' => 'Departments',
    '/users/' => 'Users',
    '/hr' => 'HR Dashboard',
    '/attendance/' => 'Attendance',
    '/leaves/' => 'Leaves',
    '/payroll/' => 'Payroll',
    '/projects/' => 'Projects',
    '/tasks/' => 'Tasks',
    '/interviews/' => 'Interviews',
    '/offers/' => 'Offers',
    '/training/' => 'Training',
    '/applications/' => 'Applications',
    '/contracts/' => 'Contracts',
    '/community/' => 'Community',
    '/notifications/' => 'Notifications',
    '/profile' => 'Profile',
    '/settings' => 'Settings',
];

// New/form pages
$newPages = [
    '/departments/new' => 'New Department',
    '/users/new' => 'New User',
    '/attendance/new' => 'New Attendance',
    '/leaves/new' => 'New Leave',
    '/payroll/new' => 'New Payroll',
    '/projects/new' => 'New Project',
    '/tasks/new' => 'New Task',
    '/interviews/new' => 'New Interview',
    '/offers/new' => 'New Offer',
    '/training/new' => 'New Training',
    '/applications/new' => 'New Application',
    '/contracts/new' => 'New Contract',
];

$ok = 0; $fail = 0; $failList = [];

foreach (array_merge($pages, $newPages) as $path => $label) {
    $code = @file_get_contents("$base$path") !== false ? 200 : 0;
    if ($code === 200) {
        $ok++;
    } else {
        // Try with stream context to get actual code
        $headers = @get_headers("$base$path");
        $code = $headers ? (int)substr($headers[0], 9, 3) : 0;
        if ($code === 200) { $ok++; }
        else { $fail++; $failList[] = "$path ($code) - $label"; echo "  FAIL: $path => $code\n"; }
    }
}
echo "Index+New pages: $ok OK, $fail FAIL\n";
if ($failList) foreach ($failList as $f) echo "  ! $f\n";

// Show/Edit pages - test with first row of each table
echo "\n=== TESTING SHOW/EDIT PAGES ===\n";
$showEdit = [
    'departments' => '/departments',
    'user' => '/users',
    'attendance' => '/attendance',
    'leaves' => '/leaves',
    'payrolls' => '/payroll',
    'projects' => '/projects',
    'tasks' => '/tasks',
    'interviews' => '/interviews',
    'offers' => '/offers',
    'training_courses' => '/training',
    'job_applications' => '/applications',
    'contracts' => '/contracts',
];

$showOk = 0; $showFail = 0; $editOk = 0; $editFail = 0;
foreach ($showEdit as $table => $basePath) {
    $id = $pdo->query("SELECT id FROM $table LIMIT 1")->fetchColumn();
    if (!$id) { echo "  SKIP $table (empty)\n"; continue; }

    // Test show
    $url = "$base$basePath/$id";
    $headers = @get_headers($url);
    $code = $headers ? (int)substr($headers[0], 9, 3) : 0;
    if ($code === 200) { $showOk++; }
    else { $showFail++; echo "  SHOW FAIL: $basePath/$id => $code\n"; }

    // Test edit
    $url = "$base$basePath/$id/edit";
    $headers = @get_headers($url);
    $code = $headers ? (int)substr($headers[0], 9, 3) : 0;
    if ($code === 200) { $editOk++; }
    else { $editFail++; echo "  EDIT FAIL: $basePath/$id/edit => $code\n"; }
}

echo "Show pages: $showOk OK, $showFail FAIL\n";
echo "Edit pages: $editOk OK, $editFail FAIL\n";

echo "\n=== GRAND TOTAL ===\n";
$totalOk = $ok + $showOk + $editOk;
$totalFail = $fail + $showFail + $editFail;
echo "TOTAL: $totalOk OK, $totalFail FAIL\n";
if ($totalFail === 0) echo "ALL PAGES WORKING!\n";
