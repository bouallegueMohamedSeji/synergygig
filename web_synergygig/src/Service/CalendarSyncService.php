<?php

namespace App\Service;

use App\Entity\Project;
use App\Entity\Task;

class CalendarSyncService
{
    public function createMilestoneSyncPayload(Project $project, int $milestoneId, array $payload, ?Task $task = null): array
    {
        $provider = strtolower(trim((string) ($payload['provider'] ?? 'google_calendar')));
        if ($provider === '') {
            $provider = 'google_calendar';
        }

        $title = trim((string) ($payload['title'] ?? ''));
        if ($title === '') {
            $title = $task ? $task->getTitle() : sprintf('%s - Milestone %d', $project->getName(), $milestoneId);
        }

        $timezone = trim((string) ($payload['timezone'] ?? 'UTC'));
        if ($timezone === '') {
            $timezone = 'UTC';
        }

        [$startAt, $endAt] = $this->resolveDateRange($payload, $task);
        $attendees = $this->sanitizeAttendees($payload['attendees'] ?? []);
        $dryRun = filter_var($payload['dry_run'] ?? true, FILTER_VALIDATE_BOOL, FILTER_NULL_ON_FAILURE);
        $dryRun = $dryRun !== null ? $dryRun : true;

        return [
            'project_id' => $project->getId(),
            'milestone_id' => $milestoneId,
            'provider' => $provider,
            'dry_run' => $dryRun,
            'sync_status' => $dryRun ? 'DRY_RUN' : 'PENDING_PROVIDER_CREDENTIALS',
            'external_event_id' => null,
            'message' => $dryRun
                ? 'Dry run generated. Calendar provider call was not executed.'
                : 'Calendar sync skeleton ready. Connect provider credentials to execute real sync.',
            'event_payload' => [
                'title' => $title,
                'description' => (string) ($payload['description'] ?? ''),
                'timezone' => $timezone,
                'start_at' => $startAt->format(\DateTimeInterface::ATOM),
                'end_at' => $endAt->format(\DateTimeInterface::ATOM),
                'attendees' => $attendees,
                'project_ref' => sprintf('project:%d', (int) $project->getId()),
                'milestone_ref' => sprintf('milestone:%d', $milestoneId),
            ],
            'generated_at' => (new \DateTimeImmutable())->format(\DateTimeInterface::ATOM),
        ];
    }

    /**
     * @return array{0: \DateTimeImmutable, 1: \DateTimeImmutable}
     */
    private function resolveDateRange(array $payload, ?Task $task): array
    {
        $startAt = $this->parseDateTime($payload['start_at'] ?? null);
        $endAt = $this->parseDateTime($payload['end_at'] ?? null);

        if (!$startAt && $task?->getDueDate()) {
            $dueDate = $task->getDueDate();
            $baseDate = $dueDate instanceof \DateTimeImmutable
                ? $dueDate
                : \DateTimeImmutable::createFromMutable(\DateTime::createFromInterface($dueDate));
            $startAt = $baseDate->setTime(10, 0);
        }

        if (!$startAt) {
            $startAt = (new \DateTimeImmutable('tomorrow'))->setTime(10, 0);
        }

        if (!$endAt || $endAt <= $startAt) {
            $endAt = $startAt->modify('+45 minutes');
        }

        return [$startAt, $endAt];
    }

    private function parseDateTime(mixed $value): ?\DateTimeImmutable
    {
        if (!is_string($value) || trim($value) === '') {
            return null;
        }

        try {
            return new \DateTimeImmutable($value);
        } catch (\Throwable) {
            return null;
        }
    }

    private function sanitizeAttendees(mixed $rawAttendees): array
    {
        if (!is_array($rawAttendees)) {
            return [];
        }

        $emails = [];
        foreach ($rawAttendees as $attendee) {
            if (!is_string($attendee)) {
                continue;
            }
            $email = strtolower(trim($attendee));
            if ($email === '' || !filter_var($email, FILTER_VALIDATE_EMAIL)) {
                continue;
            }
            $emails[$email] = true;
        }

        return array_keys($emails);
    }
}
