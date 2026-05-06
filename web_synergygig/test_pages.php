<?php
$urls = [
    '/',
    '/users/', '/users/new',
    '/departments/', '/departments/new',
    '/attendance/', '/attendance/new',
    '/leaves/', '/leaves/new',
    '/payroll/', '/payroll/new',
    '/projects/', '/projects/new',
    '/tasks/', '/tasks/new',
    '/interviews/', '/interviews/new',
    '/offers/', '/offers/new',
    '/training/', '/training/new',
    '/contracts/', '/contracts/new',
    '/applications/', '/applications/new',
    '/notifications/',
    '/community/', '/community/groups', '/community/people',
    '/hr',
    '/profile', '/settings',
    '/login', '/signup',
];

$ok = 0; $fail = 0;
foreach ($urls as $u) {
    $h = @get_headers('http://127.0.0.1:8000' . $u);
    $status = $h ? $h[0] : 'FAIL';
    $is200 = strpos($status, '200') !== false;
    echo ($is200 ? 'OK ' : 'ERR') . "  $status  $u\n";
    $is200 ? $ok++ : $fail++;
}
echo "\nTotal: " . count($urls) . " | OK: $ok | FAIL: $fail\n";
