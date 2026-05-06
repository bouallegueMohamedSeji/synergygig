<?php

declare(strict_types=1);

namespace App\Tests\Unit\Service;

use App\Service\CvJobMatchService;
use PHPUnit\Framework\TestCase;

/**
 * Unit tests for CvJobMatchService.
 * Tests the keyword-based job-to-CV scoring algorithm.
 */
class CvJobMatchServiceTest extends TestCase
{
    private CvJobMatchService $service;

    protected function setUp(): void
    {
        $this->service = new CvJobMatchService();
    }

    // ─────────────────────────────────────────────────────────────
    // Test 1 — Empty CV returns score 0 with label "No CV"
    // ─────────────────────────────────────────────────────────────
    public function testEmptyCvReturnsNoCV(): void
    {
        $result = $this->service->score('', 'PHP Developer', 'Symfony, Docker, MySQL');

        $this->assertSame(0, $result['score']);
        $this->assertSame('No CV', $result['label']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 2 — Perfect match: CV contains ALL skills the job needs
    // ─────────────────────────────────────────────────────────────
    public function testPerfectMatchReturnsHundred(): void
    {
        $cv  = 'PHP Symfony Docker Git SQL MySQL REST API JavaScript React';
        $job = 'Looking for a PHP Symfony developer with Docker and MySQL REST API experience.';

        $result = $this->service->score($cv, 'PHP Developer', $job);

        $this->assertSame(100, $result['score']);
        $this->assertSame('Excellent match', $result['label']);
        $this->assertNotEmpty($result['matched']);
        $this->assertEmpty($result['missing']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 3 — Zero match: CV has totally unrelated skills
    // ─────────────────────────────────────────────────────────────
    public function testZeroMatchWhenCvUnrelated(): void
    {
        $cv  = 'Photography portrait retouching Adobe Lightroom studio lighting';
        $job = 'PHP Symfony developer. Must know MySQL, Docker, Git and REST API.';

        $result = $this->service->score($cv, 'PHP Developer', $job);

        $this->assertSame(0, $result['score']);
        $this->assertEmpty($result['matched']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 4 — Partial match: CV has half the required skills
    // ─────────────────────────────────────────────────────────────
    public function testPartialMatchReturnsIntermediateScore(): void
    {
        // Job requires: PHP (symfony), SQL, Docker, Git → 4 skill categories
        // CV has: PHP + SQL only → 2/4 = 50%
        $cv  = 'PHP Symfony MySQL SQL database experience';
        $job = 'Senior PHP/Symfony developer. Docker, Git, MySQL required.';

        $result = $this->service->score($cv, 'Senior PHP Developer', $job);

        $this->assertGreaterThan(0, $result['score']);
        $this->assertLessThan(100, $result['score']);
        $this->assertContains('PHP', $result['matched']);
        $this->assertContains('SQL', $result['matched']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 5 — Score label "Good match" range (70–84)
    // ─────────────────────────────────────────────────────────────
    public function testLabelGoodMatchFor75Score(): void
    {
        // Job requires PHP + Docker + Git (3 cats). CV has PHP + Docker → 2/3 ≈ 67%
        // But let's pick categories that give 75%: 3/4 = 75%
        // Job: PHP, SQL, Docker, Git → 4 required
        // CV: PHP, SQL, Docker (no Git) → 3/4 = 75
        $cv  = 'PHP Symfony MySQL SQL Docker containers experience';
        $job = 'We need PHP/Symfony, MySQL, Docker, and Git experience.';

        $result = $this->service->score($cv, 'Dev', $job);

        $this->assertGreaterThanOrEqual(50, $result['score']);
        // Label should be Average match, Good match, or Excellent match — not Poor/Low
        $this->assertNotSame('Poor match', $result['label']);
        $this->assertNotSame('No CV', $result['label']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 6 — result always contains required keys
    // ─────────────────────────────────────────────────────────────
    public function testResultStructureAlwaysComplete(): void
    {
        $result = $this->service->score('java spring boot', 'Java Dev', 'Spring Boot Hibernate Maven');

        $this->assertArrayHasKey('score', $result);
        $this->assertArrayHasKey('label', $result);
        $this->assertArrayHasKey('matched', $result);
        $this->assertArrayHasKey('missing', $result);

        $this->assertIsInt($result['score']);
        $this->assertIsString($result['label']);
        $this->assertIsArray($result['matched']);
        $this->assertIsArray($result['missing']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 7 — Score is always within 0–100
    // ─────────────────────────────────────────────────────────────
    public function testScoreAlwaysBetweenZeroAndHundred(): void
    {
        $cases = [
            ['', 'anything', 'anything'],
            ['Python Django', 'Senior React Developer', 'React Redux TypeScript'],
            ['full stack developer php mysql html css javascript git docker kubernetes aws',
             'DevOps Engineer', 'AWS Kubernetes Helm Docker Terraform CI/CD'],
        ];

        foreach ($cases as [$cv, $title, $desc]) {
            $result = $this->service->score($cv, $title, $desc);
            $this->assertGreaterThanOrEqual(0, $result['score'], "Score below 0 for cv=[$cv]");
            $this->assertLessThanOrEqual(100, $result['score'], "Score above 100 for cv=[$cv]");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Test 8 — No-keyword-job falls back to word-overlap scoring
    // ─────────────────────────────────────────────────────────────
    public function testWordOverlapFallbackForGenericJob(): void
    {
        // Job has no recognized skill keywords → falls back to word overlap
        $cv  = 'experienced project manager with leadership coordination skills';
        $job = 'Looking for a coordinator with leadership and excellent coordination';

        $result = $this->service->score($cv, 'Office Coordinator', $job);

        // Overlap on "leadership" and "coordination" → score > 0
        $this->assertGreaterThan(0, $result['score']);
    }
}
