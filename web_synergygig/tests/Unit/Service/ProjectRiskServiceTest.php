<?php

declare(strict_types=1);

namespace App\Tests\Unit\Service;

use App\Entity\Project;
use App\Entity\Task;
use App\Service\ProjectRiskService;
use PHPUnit\Framework\TestCase;

/**
 * Unit tests for ProjectRiskService.
 * Tests risk score calculation logic using in-memory entity objects.
 */
class ProjectRiskServiceTest extends TestCase
{
    private ProjectRiskService $service;

    protected function setUp(): void
    {
        $this->service = new ProjectRiskService();
    }

    private function makeTask(string $status, ?\DateTimeInterface $dueDate = null, ?\DateTimeInterface $createdAt = null): Task
    {
        $task = new Task();
        $task->setTitle('Test task');
        $task->setStatus($status);
        if ($dueDate !== null) {
            $task->setDueDate($dueDate);
        }
        $task->setCreatedAt($createdAt ?? new \DateTimeImmutable());
        return $task;
    }

    // ─────────────────────────────────────────────────────────────
    // Test 9 — Empty project → risk score is a valid integer in 0–100
    // ─────────────────────────────────────────────────────────────
    public function testEmptyProjectProducesValidRiskScore(): void
    {
        $project = new Project();
        $project->setName('Empty Project');
        $project->setDeadline(new \DateTimeImmutable('+60 days'));

        $result = $this->service->buildForecast($project);

        $this->assertArrayHasKey('risk_score', $result);
        $this->assertArrayHasKey('risk_level', $result);
        $this->assertArrayHasKey('signals', $result);
        $this->assertGreaterThanOrEqual(0, $result['risk_score']);
        $this->assertLessThanOrEqual(100, $result['risk_score']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 10 — Overdue tasks increase risk score
    // ─────────────────────────────────────────────────────────────
    public function testOverdueTasksIncreaseRiskScore(): void
    {
        $cleanProject = new Project();
        $cleanProject->setName('Clean Project');
        $cleanProject->setDeadline(new \DateTimeImmutable('+60 days'));

        $riskyProject = new Project();
        $riskyProject->setName('Risky Project');
        $riskyProject->setDeadline(new \DateTimeImmutable('+60 days'));

        // Add 5 overdue tasks (due 10 days ago, not completed)
        for ($i = 0; $i < 5; $i++) {
            $task = $this->makeTask('TODO', new \DateTimeImmutable('-10 days'));
            $riskyProject->getTasks()->add($task);
        }

        $cleanScore = $this->service->buildForecast($cleanProject)['risk_score'];
        $riskyScore = $this->service->buildForecast($riskyProject)['risk_score'];

        $this->assertGreaterThan($cleanScore, $riskyScore);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 11 — No deadline sets minimum deadline risk (5 pts)
    // ─────────────────────────────────────────────────────────────
    public function testNoDeadlineUsesDefaultRisk(): void
    {
        $project = new Project();
        $project->setName('No Deadline Project');
        // No deadline set → should use default 5 pts deadline risk

        $result = $this->service->buildForecast($project);

        $this->assertSame(5, $result['signals']['deadline_score']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 12 — Past deadline flags maximum deadline risk (40 pts)
    // ─────────────────────────────────────────────────────────────
    public function testExpiredDeadlineMaximisesDeadlineScore(): void
    {
        $project = new Project();
        $project->setName('Overdue Project');
        $project->setDeadline(new \DateTimeImmutable('-5 days'));

        $result = $this->service->buildForecast($project);

        $this->assertSame(40, $result['signals']['deadline_score']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 13 — All done tasks → completion 100% → low backlog score
    // ─────────────────────────────────────────────────────────────
    public function testAllDoneTasksProducesLowBacklogScore(): void
    {
        $project = new Project();
        $project->setName('Finished Project');
        $project->setDeadline(new \DateTimeImmutable('+30 days'));

        for ($i = 0; $i < 4; $i++) {
            $task = $this->makeTask('DONE', new \DateTimeImmutable('+5 days'));
            $project->getTasks()->add($task);
        }

        $result = $this->service->buildForecast($project);

        // Completion 100% → backlog score is the minimum (2 — never zero by design)
        $this->assertLessThanOrEqual(5, $result['signals']['backlog_score']);
        $this->assertSame(100, $result['task_stats']['completion_percent']);
    }

    // ─────────────────────────────────────────────────────────────
    // Test 14 — Result contains all expected top-level keys
    // ─────────────────────────────────────────────────────────────
    public function testForecastResultStructure(): void
    {
        $project = new Project();
        $project->setName('Struct Check');

        $result = $this->service->buildForecast($project);

        foreach (['project_id', 'project_name', 'risk_score', 'risk_level', 'signals', 'task_stats', 'recommendations', 'generated_at'] as $key) {
            $this->assertArrayHasKey($key, $result, "Missing key: $key");
        }
    }
}
