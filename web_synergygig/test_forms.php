<?php
/**
 * Test form submissions by POSTing to create endpoints.
 * This verifies that form fields, CSRF, and DB enum values all work together.
 */
$base = 'http://127.0.0.1:8000';
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');
$pdo->setAttribute(PDO::ATTR_ERRMODE, PDO::ERRMODE_EXCEPTION);

$userId = $pdo->query("SELECT id FROM user LIMIT 1")->fetchColumn();
$deptId = $pdo->query("SELECT id FROM departments LIMIT 1")->fetchColumn();
$projId = $pdo->query("SELECT id FROM projects LIMIT 1")->fetchColumn();
$offerId = $pdo->query("SELECT id FROM offers LIMIT 1")->fetchColumn();

$ok = 0; $fail = 0; $results = [];

function testFormSubmit($base, $getPath, $postPath, $formName, $fields, &$ok, &$fail, &$results) {
    // Step 1: GET the form page to extract CSRF token and session cookie
    $ctx = stream_context_create([
        'http' => [
            'method' => 'GET',
            'header' => "Accept: text/html\r\n",
            'follow_location' => true,
            'max_redirects' => 5,
        ]
    ]);
    $html = @file_get_contents($base . $getPath, false, $ctx);
    if (!$html) {
        $fail++;
        $results[] = "FAIL $getPath: Could not GET form page";
        echo "  FAIL $getPath: Could not GET form page\n";
        return;
    }
    
    // Extract session cookie
    $sessionCookie = '';
    foreach ($http_response_header ?? [] as $h) {
        if (preg_match('/^Set-Cookie:\s*(PHPSESSID=[^;]+)/', $h, $cm)) {
            $sessionCookie = $cm[1];
        }
    }
    
    // Extract CSRF token
    $tokenField = $formName . '[_token]';
    if (preg_match('/name="' . preg_quote($formName . '[_token]', '/') . '"\s+value="([^"]+)"/', $html, $m)) {
        $csrfToken = $m[1];
    } elseif (preg_match('/value="([^"]+)"\s+name="' . preg_quote($formName . '[_token]', '/') . '"/', $html, $m)) {
        $csrfToken = $m[1];
    } else {
        $fail++;
        $results[] = "FAIL $getPath: Could not extract CSRF token for $formName";
        echo "  FAIL $getPath: No CSRF token found\n";
        return;
    }
    
    // Build form data
    $fields[$formName . '[_token]'] = $csrfToken;
    $postData = http_build_query($fields);
    
    // Step 2: POST the form with session cookie
    $ctx = stream_context_create([
        'http' => [
            'method' => 'POST',
            'header' => "Content-Type: application/x-www-form-urlencoded\r\nCookie: $sessionCookie\r\nAccept: text/html\r\n",
            'content' => $postData,
            'follow_location' => false,
            'max_redirects' => 0,
            'ignore_errors' => true,
        ]
    ]);
    $response = @file_get_contents($base . $postPath, false, $ctx);
    $responseHeaders = $http_response_header ?? [];
    $statusCode = 0;
    if ($responseHeaders) {
        foreach ($responseHeaders as $h) {
            if (preg_match('/^HTTP\/\S+\s+(\d+)/', $h, $m)) {
                $statusCode = (int)$m[1];
            }
        }
    }
    
    // 302/303 = redirect (success), 200 = form showed again (validation error), 500 = server error
    if ($statusCode === 302 || $statusCode === 303) {
        $ok++;
        $results[] = "OK   $postPath: $statusCode (redirect = success)";
        echo "  OK   $postPath => $statusCode (redirect)\n";
    } elseif ($statusCode === 200) {
        // Could be validation errors - check for error text
        if (strpos($response, 'is not valid') !== false || strpos($response, 'This value should') !== false || strpos($response, 'form-error') !== false) {
            $fail++;
            $results[] = "FAIL $postPath: $statusCode (validation errors in response)";
            echo "  FAIL $postPath => $statusCode (validation errors)\n";
        } else {
            $ok++;
            $results[] = "OK   $postPath: $statusCode (form reloaded, likely success)";
            echo "  OK   $postPath => $statusCode (reloaded)\n";
        }
    } else {
        $fail++;
        $results[] = "FAIL $postPath: $statusCode";
        echo "  FAIL $postPath => $statusCode\n";
        // Print first 500 chars of response body for debugging
        if ($response && $statusCode >= 500) {
            $stripped = strip_tags($response);
            $stripped = preg_replace('/\s+/', ' ', $stripped);
            echo "    Body: " . substr($stripped, 0, 300) . "\n";
        }
    }
}

echo "=== TESTING FORM SUBMISSIONS ===\n\n";

// Department
testFormSubmit($base, '/departments/new', '/departments/new', 'department', [
    'department[name]' => 'Test Dept ' . time(),
], $ok, $fail, $results);

// Leave - DB enum: SICK,VACATION,UNPAID,PERSONAL,MATERNITY,PATERNITY / status: PENDING,APPROVED,REJECTED
testFormSubmit($base, '/leaves/new', '/leaves/new', 'leave', [
    'leave[user]' => $userId,
    'leave[type]' => 'VACATION',
    'leave[start_date]' => '2025-07-01',
    'leave[end_date]' => '2025-07-05',
    'leave[reason]' => 'Test leave',
    'leave[status]' => 'PENDING',
], $ok, $fail, $results);

// Payroll - status: PENDING,PAID,CANCELLED
testFormSubmit($base, '/payroll/new', '/payroll/new', 'payroll', [
    'payroll[user]' => $userId,
    'payroll[month]' => '6',
    'payroll[year]' => '2025',
    'payroll[base_salary]' => '2500',
    'payroll[bonus]' => '100',
    'payroll[deductions]' => '50',
    'payroll[net_salary]' => '2550',
    'payroll[total_hours_worked]' => '160',
    'payroll[status]' => 'PENDING',
], $ok, $fail, $results);

// Project
testFormSubmit($base, '/projects/new', '/projects/new', 'project', [
    'project[name]' => 'Test Project ' . time(),
    'project[description]' => 'Test desc',
    'project[owner]' => $userId,
    'project[department]' => $deptId,
    'project[start_date]' => '2025-07-01',
    'project[deadline]' => '2025-12-31',
    'project[status]' => 'IN_PROGRESS',
], $ok, $fail, $results);

// Task - status: TODO,IN_PROGRESS,DONE / priority: LOW,MEDIUM,HIGH
testFormSubmit($base, '/tasks/new', '/tasks/new', 'task', [
    'task[title]' => 'Test Task ' . time(),
    'task[description]' => 'Test desc',
    'task[project]' => $projId,
    'task[assignedTo]' => $userId,
    'task[status]' => 'TODO',
    'task[priority]' => 'MEDIUM',
    'task[due_date]' => '2025-08-15',
], $ok, $fail, $results);

// Offer - offer_type: FULL_TIME,.../ status: DRAFT,OPEN,CLOSED,CANCELLED
testFormSubmit($base, '/offers/new', '/offers/new', 'offer', [
    'offer[title]' => 'Test Offer ' . time(),
    'offer[description]' => 'Test desc',
    'offer[offer_type]' => 'FULL_TIME',
    'offer[department]' => $deptId,
    'offer[owner]' => $userId,
    'offer[required_skills]' => 'PHP',
    'offer[location]' => 'Tunis',
    'offer[amount]' => '3000',
    'offer[currency]' => 'TND',
    'offer[start_date]' => '2025-07-01',
    'offer[end_date]' => '2025-12-31',
    'offer[status]' => 'OPEN',
], $ok, $fail, $results);

// Interview - status: PENDING,ACCEPTED,REJECTED,COMPLETED,CANCELLED
testFormSubmit($base, '/interviews/new', '/interviews/new', 'interview', [
    'interview[organizer]' => $userId,
    'interview[candidate]' => $userId,
    'interview[date_time]' => '2025-08-01T14:00',
    'interview[offer]' => $offerId,
    'interview[meet_link]' => 'https://meet.google.com/test2',
    'interview[status]' => 'PENDING',
], $ok, $fail, $results);

// Contract - status: DRAFT,PENDING_SIGNATURE,ACTIVE,COMPLETED,TERMINATED,DISPUTED
testFormSubmit($base, '/contracts/new', '/contracts/new', 'contract', [
    'contract[offer]' => $offerId,
    'contract[applicant]' => $userId,
    'contract[owner]' => $userId,
    'contract[amount]' => '5000',
    'contract[currency]' => 'TND',
    'contract[status]' => 'DRAFT',
    'contract[start_date]' => '2025-07-01',
    'contract[end_date]' => '2026-07-01',
    'contract[terms]' => 'Test terms',
], $ok, $fail, $results);

// Job Application - status: PENDING,REVIEWED,SHORTLISTED,ACCEPTED,REJECTED,WITHDRAWN
testFormSubmit($base, '/applications/new', '/applications/new', 'job_application', [
    'job_application[offer]' => $offerId,
    'job_application[applicant]' => $userId,
    'job_application[cover_letter]' => 'Test cover letter',
    'job_application[status]' => 'PENDING',
], $ok, $fail, $results);

// Training - category: TECHNICAL,SOFT_SKILLS,.../ difficulty: BEGINNER,.../ status: DRAFT,ACTIVE,ARCHIVED
testFormSubmit($base, '/training/new', '/training/new', 'training_course', [
    'training_course[title]' => 'Test Course ' . time(),
    'training_course[description]' => 'Test desc',
    'training_course[category]' => 'TECHNICAL',
    'training_course[difficulty]' => 'BEGINNER',
    'training_course[duration_hours]' => '8',
    'training_course[instructor_name]' => 'Dr. Test',
    'training_course[max_participants]' => '30',
    'training_course[start_date]' => '2025-09-01',
    'training_course[end_date]' => '2025-09-05',
    'training_course[status]' => 'ACTIVE',
], $ok, $fail, $results);

// Attendance
testFormSubmit($base, '/attendance/new', '/attendance/new', 'attendance', [
    'attendance[user]' => $userId,
    'attendance[date]' => '2025-07-01',
    'attendance[check_in]' => '08:00',
    'attendance[check_out]' => '17:00',
    'attendance[status]' => 'PRESENT',
], $ok, $fail, $results);

echo "\n=== FORM SUBMISSION RESULTS ===\n";
echo "OK: $ok, FAIL: $fail\n";
foreach ($results as $r) echo "  $r\n";
