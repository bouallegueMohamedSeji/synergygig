<?php

namespace App\Service;

use Psr\Log\LoggerInterface;
use Symfony\Contracts\HttpClient\HttpClientInterface;
use Symfony\Contracts\HttpClient\Exception\TransportExceptionInterface;

/**
 * GitHub Issues API integration — creates, closes, and syncs issues from tasks/projects.
 * Requires GITHUB_TOKEN (personal access token with repo scope) in .env.
 */
class GitHubService
{
    private const API_BASE = 'https://api.github.com';

    public function __construct(
        private HttpClientInterface $http,
        private LoggerInterface $logger,
        private ?string $token = ''
    ) {
        $this->token = $token ?? '';
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Create a GitHub issue in the given repo.
     * @param string $repo  "owner/repo" format
     * @return array{number:int,html_url:string}|null
     */
    public function createIssue(string $repo, string $title, string $body, array $labels = []): ?array
    {
        if (!$this->token) {
            $this->logger->error('GitHubService: GITHUB_TOKEN not configured.');
            return null;
        }
        try {
            $payload = ['title' => $title, 'body' => $body];
            if ($labels) {
                $payload['labels'] = $labels;
            }
            $resp = $this->http->request('POST', self::API_BASE . "/repos/{$repo}/issues", [
                'headers' => $this->headers(),
                'json'    => $payload,
            ]);
            $data = $resp->toArray(false);
            if (isset($data['number'])) {
                return ['number' => $data['number'], 'html_url' => $data['html_url']];
            }
            $this->logger->error('GitHubService: unexpected response creating issue', ['resp' => $data]);
            return null;
        } catch (TransportExceptionInterface $e) {
            $this->logger->error('GitHubService: HTTP error creating issue', ['err' => $e->getMessage()]);
            return null;
        }
    }

    /**
     * Close an existing GitHub issue.
     */
    public function closeIssue(string $repo, int $issueNumber): bool
    {
        return $this->patchIssue($repo, $issueNumber, ['state' => 'closed']);
    }

    /**
     * Reopen a closed GitHub issue.
     */
    public function reopenIssue(string $repo, int $issueNumber): bool
    {
        return $this->patchIssue($repo, $issueNumber, ['state' => 'open']);
    }

    /**
     * Update the title/body of a GitHub issue (e.g., after a task is edited).
     */
    public function updateIssue(string $repo, int $issueNumber, string $title, string $body): bool
    {
        return $this->patchIssue($repo, $issueNumber, ['title' => $title, 'body' => $body]);
    }

    /**
     * List open issues for a repo (first 30, most recently updated).
     * @return array<int, array{number:int, title:string, html_url:string, state:string, created_at:string}>
     */
    public function listIssues(string $repo, string $state = 'open', int $perPage = 30): array
    {
        if (!$this->token) {
            return [];
        }
        try {
            $resp = $this->http->request('GET', self::API_BASE . "/repos/{$repo}/issues", [
                'headers' => $this->headers(),
                'query'   => ['state' => $state, 'per_page' => $perPage, 'sort' => 'updated'],
            ]);
            $items = $resp->toArray(false);
            if (!is_array($items)) {
                return [];
            }
            return array_map(fn($i) => [
                'number'     => $i['number'],
                'title'      => $i['title'],
                'html_url'   => $i['html_url'],
                'state'      => $i['state'],
                'created_at' => $i['created_at'] ?? '',
            ], $items);
        } catch (TransportExceptionInterface $e) {
            $this->logger->error('GitHubService: HTTP error listing issues', ['err' => $e->getMessage()]);
            return [];
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private function patchIssue(string $repo, int $issueNumber, array $payload): bool
    {
        if (!$this->token) {
            return false;
        }
        try {
            $resp = $this->http->request('PATCH', self::API_BASE . "/repos/{$repo}/issues/{$issueNumber}", [
                'headers' => $this->headers(),
                'json'    => $payload,
            ]);
            return $resp->getStatusCode() === 200;
        } catch (TransportExceptionInterface $e) {
            $this->logger->error('GitHubService: HTTP error patching issue', ['err' => $e->getMessage(), 'issue' => $issueNumber]);
            return false;
        }
    }

    private function headers(): array
    {
        return [
            'Authorization' => 'Bearer ' . $this->token,
            'Accept'        => 'application/vnd.github+json',
            'X-GitHub-Api-Version' => '2022-11-28',
            'User-Agent'    => 'SynergyGig/1.0',
        ];
    }
}
