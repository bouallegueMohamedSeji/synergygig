<?php
/**
 * Adds camelCase getter/setter aliases to all entities that use underscore-style methods.
 * This is needed because Symfony PropertyAccessor expects camelCase.
 */
$dir = __DIR__ . '/src/Entity/';
$skip = ['Department.php', 'User.php']; // already fixed manually

foreach (glob($dir . '*.php') as $file) {
    if (in_array(basename($file), $skip)) continue;
    
    $code = file_get_contents($file);
    $lines = explode("\n", $code);
    $newLines = [];
    $added = 0;
    
    for ($i = 0; $i < count($lines); $i++) {
        $newLines[] = $lines[$i];
        
        // Match: public function getXxx_yyy(...): ... {
        // or:    public function setXxx_yyy(...): ... {
        // or:    public function is_xxx(...): ... {
        if (preg_match('/^\s*public function (get|set|is)([A-Za-z]*_[A-Za-z_]+)\(([^)]*)\)\s*:\s*(.+)$/', $lines[$i], $m)) {
            $prefix = $m[1];
            $name = $m[2];
            $params = $m[3];
            $returnType = trim($m[4]);
            
            // Build camelCase version
            $camel = preg_replace_callback('/_([a-z])/i', function($x) { return strtoupper($x[1]); }, $name);
            
            if ($camel === $name) continue;
            
            $camelMethod = $prefix . $camel;
            
            // Skip if alias already exists
            if (strpos($code, "function $camelMethod(") !== false) continue;
            
            // Collect the full method body (find closing brace)
            $braceCount = 0;
            $methodEnd = $i;
            for ($j = $i; $j < count($lines); $j++) {
                $braceCount += substr_count($lines[$j], '{') - substr_count($lines[$j], '}');
                if ($braceCount === 0 && $j > $i) {
                    $methodEnd = $j;
                    break;
                }
            }
            
            // Extract param variable names for forwarding
            $paramVars = '';
            if ($params) {
                preg_match_all('/\$(\w+)/', $params, $pn);
                $paramVars = implode(', ', array_map(function($n) { return '$' . $n; }, $pn[1]));
            }
            
            $originalMethod = $prefix . $name;
            
            // After the closing brace of the original method, add alias
            // We need to add it after $methodEnd
            // Add alias lines after the method ends
            $aliasLines = [
                '',
                "    public function $camelMethod($params): $returnType",
                '    {',
                "        return \$this->$originalMethod($paramVars);",
                '    }',
            ];
            
            // Insert after method end
            // We'll track what to insert after which line
            // For simplicity, add to a buffer keyed by line number
            $insertAfter[$methodEnd] = $aliasLines;
            $added++;
        }
    }
    
    // Now rebuild with insertions
    if ($added > 0) {
        $finalLines = [];
        if (!isset($insertAfter)) $insertAfter = [];
        for ($i = 0; $i < count($newLines); $i++) {
            $finalLines[] = $newLines[$i];
            if (isset($insertAfter[$i])) {
                foreach ($insertAfter[$i] as $al) {
                    $finalLines[] = $al;
                }
            }
        }
        file_put_contents($file, implode("\n", $finalLines));
        echo basename($file) . ": $added camelCase aliases added\n";
        $insertAfter = [];
    }
}
echo "Done!\n";
