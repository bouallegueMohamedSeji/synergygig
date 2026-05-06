param(
    [switch]$ApplySchema,
    [switch]$SkipTests,
    [switch]$SkipPhpStan
)

$ErrorActionPreference = "Stop"
Set-StrictMode -Version Latest

$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location $projectRoot

Write-Host "== SynergyGig Doctrine Doctor Autofix ==" -ForegroundColor Cyan
Write-Host "Project: $projectRoot"

function Run-Step {
    param(
        [string]$Title,
        [scriptblock]$Action
    )

    Write-Host "`n-- $Title" -ForegroundColor Yellow
    & $Action
}

# 1) Cache warm reset to ensure profiler sees latest code
Run-Step "Clear Symfony cache" {
    php bin/console cache:clear
}

# 2) Schema drift detection (+ optional apply)
Run-Step "Validate doctrine schema" {
    try {
        php bin/console doctrine:schema:validate
    } catch {
        Write-Host "Schema validation reports drift (expected if mapping changed)." -ForegroundColor DarkYellow
    }

    Write-Host "`nSQL diff preview:" -ForegroundColor Gray
    php bin/console doctrine:schema:update --dump-sql

    if ($ApplySchema) {
        Write-Host "`nApplying schema changes (ApplySchema enabled)..." -ForegroundColor DarkYellow
        php bin/console doctrine:schema:update --force
    } else {
        Write-Host "`nDry run only. Re-run with -ApplySchema to execute SQL." -ForegroundColor DarkGray
    }
}

# 3) Safe code rewrite for controller-level findAll() anti-patterns
Run-Step "Auto-fix unrestricted findAll() in controllers" {
    $controllerFiles = Get-ChildItem -Path "src/Controller" -Recurse -Filter "*.php"
    $changed = 0

    foreach ($file in $controllerFiles) {
        $content = Get-Content -Raw -Path $file.FullName
        $updated = $content -replace "->findAll\(\)", "->findBy([], ['id' => 'DESC'], 200)"

        if ($updated -ne $content) {
            Set-Content -Path $file.FullName -Value $updated -NoNewline
            $changed++
            Write-Host "Fixed findAll in $($file.FullName.Substring($projectRoot.Length + 1))"
        }
    }

    if ($changed -eq 0) {
        Write-Host "No controller findAll() occurrences found."
    } else {
        Write-Host "Updated $changed file(s)." -ForegroundColor Green
    }
}

# 4) Aggregation hotspots report (for DTO hydration pass)
Run-Step "Report aggregation hotspots (groupBy + non-DTO hydration)" {
    $phpFiles = Get-ChildItem -Path "src" -Recurse -Filter "*.php"
    $hits = @()

    foreach ($file in $phpFiles) {
        $lines = Get-Content -Path $file.FullName
        $hasGroupBy = $false
        $hasNonDtoHydration = $false

        foreach ($line in $lines) {
            if ($line -match "groupBy\(") { $hasGroupBy = $true }
            if ($line -match "getArrayResult\(\)|getScalarResult\(\)") { $hasNonDtoHydration = $true }
        }

        if ($hasGroupBy -and $hasNonDtoHydration) {
            $hits += $file.FullName.Substring($projectRoot.Length + 1)
        }
    }

    if ($hits.Count -eq 0) {
        Write-Host "No groupBy + array/scalar hydration hotspots detected."
    } else {
        Write-Host "Potential DTO hydration targets:" -ForegroundColor DarkYellow
        $hits | Sort-Object | ForEach-Object { Write-Host "  - $_" }
    }
}

# 5) Validation checks
if (-not $SkipPhpStan) {
    Run-Step "Run focused PHPStan" {
        php vendor/bin/phpstan analyse src/Controller/ChatController.php src/Controller/DashboardController.php src/Controller/HRController.php src/Controller/PayrollController.php --no-progress
    }
}

if (-not $SkipTests) {
    Run-Step "Run representative PHPUnit suite" {
        php vendor/bin/phpunit tests/Service/UserManagerTest.php --testdox
    }
}

Write-Host "`nDone. Refresh Symfony Profiler on /dashboard and compare Doctrine Doctor counts." -ForegroundColor Cyan
Write-Host "Tip: Run with -ApplySchema if schema drift remains high." -ForegroundColor DarkGray
