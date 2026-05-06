<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

class HealthCheckController extends AbstractController {
    #[Route('/health', methods: ['GET'])]
    public function health(): JsonResponse {
        return new JsonResponse([
            'status' => 'healthy',
            'timestamp' => date('c'),
            'environment' => $_ENV['APP_ENV'],
        ]);
    }

    #[Route('/api/health', methods: ['GET'])]
    public function apiHealth(): JsonResponse {
        return $this->json([
            'status' => 'ok',
            'api' => 'SynergyGig Web API v1',
            'timestamp' => date('c'),
        ]);
    }
}
