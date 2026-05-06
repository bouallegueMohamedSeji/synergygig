<?php

namespace App\Controller;

use App\Service\N8nWebhookService;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/job-scanner')]
class JobScannerController extends AbstractController
{
    public function __construct(
        private N8nWebhookService $n8n,
        private EntityManagerInterface $em,
    ) {}

    #[Route('', name: 'app_job_scanner')]
    public function index(): Response
    {
        $user = $this->getUser();
        $skillsText = null;
        if ($user) {
            $raw = $user->getCvSkillsText();
            // Reject hash-like values (file hash stored instead of real skill text)
            if ($raw !== null && str_word_count($raw) >= 5) {
                $skillsText = $raw;
            }
            // Fallback: collect completed training course titles as skill keywords
            if ($skillsText === null) {
                $rows = $this->em->createQuery(
                    'SELECT c.title FROM App\Entity\TrainingEnrollment e
                     JOIN e.course c
                     WHERE e.user = :u AND e.status = :s'
                )->setParameter('u', $user)->setParameter('s', 'completed')->getScalarResult();
                $titles = array_column($rows, 'title');
                $skillsText = $titles ? implode(' ', $titles) : null;
            }
        }

        return $this->render('job_scanner/index.html.twig', [
            'userSkillsText' => $skillsText,
        ]);
    }

    #[Route('/scan', name: 'app_job_scanner_scan', methods: ['POST'])]
    public function scan(Request $request): JsonResponse
    {
        $data     = json_decode($request->getContent(), true);
        $query    = trim($data['query'] ?? '');
        $source   = $data['source'] ?? 'all';
        $jobType  = $data['jobType'] ?? 'all';
        $datePosted = $data['datePosted'] ?? 'any';
        $location = trim($data['location'] ?? '');

        if (strlen($query) < 2) {
            return $this->json(['error' => 'Please enter a job title to search.'], 422);
        }

        $result = $this->n8n->fire('/webhook/job-search', [
            'query'      => $query,
            'source'     => strtolower($source),
            'jobType'    => $jobType === 'All Types' ? 'all' : strtolower($jobType),
            'datePosted' => $datePosted === 'Any time' ? 'any' : strtolower($datePosted),
            'location'   => $location,
        ]);

        if ($result === null) {
            return $this->json(['error' => 'Job search service unavailable. Please try again later.'], 503);
        }

        $jobs  = $result['results'] ?? [];
        $count = $result['count'] ?? count($jobs);

        return $this->json([
            'results' => $jobs,
            'count'   => $count,
        ]);
    }
}
