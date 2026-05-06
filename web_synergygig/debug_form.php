<?php
/**
 * Debug a single form submission to see what's going wrong.
 */
$base = 'http://127.0.0.1:8000';

// Step 1: GET the form page
$ctx = stream_context_create([
    'http' => [
        'method' => 'GET',
        'header' => "Accept: text/html\r\n",
    ]
]);
$html = file_get_contents("$base/departments/new", false, $ctx);
echo "=== GET /departments/new ===\n";
echo "HTML length: " . strlen($html) . "\n";

// Extract session cookie
$sessionCookie = '';
foreach ($http_response_header as $h) {
    if (preg_match('/^Set-Cookie:\s*(PHPSESSID=[^;]+)/', $h, $cm)) {
        $sessionCookie = $cm[1];
    }
}
echo "Session cookie: $sessionCookie\n";

// Find all form field names
preg_match_all('/name="([^"]+)"/', $html, $matches);
echo "\nForm fields found:\n";
foreach (array_unique($matches[1]) as $field) {
    echo "  $field\n";
}

// Find CSRF token
if (preg_match('/name="department\[_token\]"\s+value="([^"]+)"/', $html, $m)) {
    echo "\nCSRF token (pattern 1): " . substr($m[1], 0, 20) . "...\n";
    $token = $m[1];
} elseif (preg_match('/id="department__token"\s+name="department\[_token\]"\s+value="([^"]+)"/', $html, $m)) {
    echo "\nCSRF token (pattern 2): " . substr($m[1], 0, 20) . "...\n";
    $token = $m[1];
} else {
    // Try a more generic pattern
    preg_match_all('/value="([^"]{20,})"/', $html, $tokenMatches);
    echo "\nLong values (potential CSRF tokens):\n";
    foreach ($tokenMatches[1] as $v) {
        echo "  " . substr($v, 0, 40) . "...\n";
    }
    // Try yet another pattern
    if (preg_match('/department\[_token\].*?value="([^"]+)"/s', $html, $m)) {
        $token = $m[1];
        echo "\nCSRF token (pattern 3): " . substr($token, 0, 20) . "...\n";
    } elseif (preg_match('/value="([^"]+)".*?department\[_token\]/s', $html, $m)) {
        $token = $m[1];
        echo "\nCSRF token (pattern 4): " . substr($token, 0, 20) . "...\n";
    } else {
        echo "\nERROR: No CSRF token found!\n";
        // Dump the token-related HTML
        if (preg_match('/_token.{0,200}/s', $html, $m)) {
            echo "Token HTML: " . $m[0] . "\n";
        }
        $token = null;
    }
}

if (!$token) {
    die("Cannot proceed without CSRF token\n");
}

// Step 2: POST the form
echo "\n=== POST /departments/new ===\n";
$fields = [
    'department[name]' => 'Debug Dept ' . time(),
    'department[_token]' => $token,
];

$postData = http_build_query($fields);
echo "POST data: $postData\n\n";

$ctx = stream_context_create([
    'http' => [
        'method' => 'POST',
        'header' => "Content-Type: application/x-www-form-urlencoded\r\nCookie: $sessionCookie\r\n",
        'content' => $postData,
        'follow_location' => false,
        'max_redirects' => 0,
        'ignore_errors' => true,
    ]
]);

$response = file_get_contents("$base/departments/new", false, $ctx);
echo "Response headers:\n";
foreach ($http_response_header as $h) {
    echo "  $h\n";
}
echo "\nResponse length: " . strlen($response) . "\n";

// Check for errors in response
$stripped = strip_tags($response);
$stripped = preg_replace('/\s+/', ' ', $stripped);

// Look for error patterns
$patterns = [
    'error', 'invalid', 'not valid', 'This value', 'required', 'token',
    'CSRF', 'form-error', 'alert', 'exception', 'Error'
];
foreach ($patterns as $p) {
    if (stripos($stripped, $p) !== false) {
        // Find context
        $pos = stripos($stripped, $p);
        $context = substr($stripped, max(0, $pos - 50), 150);
        echo "\nFound '$p' at pos $pos: ...$context...\n";
    }
}

// Check if data was saved
$pdo = new PDO('mysql:host=127.0.0.1;dbname=finale_synergygig', 'root', '');
$count = $pdo->query("SELECT COUNT(*) FROM departments")->fetchColumn();
echo "\nDepartments count: $count\n";
