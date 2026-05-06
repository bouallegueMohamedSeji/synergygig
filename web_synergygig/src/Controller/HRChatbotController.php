<?php

namespace App\Controller;

use App\Service\AIService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;
use Symfony\Component\Security\Http\Attribute\IsGranted;

#[Route('/hr-chatbot')]
#[IsGranted('ROLE_HR')]
class HRChatbotController extends AbstractController
{
    public function __construct(private AIService $ai) {}

    #[Route('', name: 'app_hr_chatbot')]
    public function index(): Response
    {
        return $this->render('hr_chatbot/index.html.twig');
    }

    #[Route('/ask', name: 'app_hr_chatbot_ask', methods: ['POST'])]
    public function ask(Request $request): JsonResponse
    {
        $data     = json_decode($request->getContent(), true);
        $question = trim($data['question'] ?? '');
        $history  = $data['history'] ?? [];

        if (strlen($question) < 3) {
            return $this->json(['error' => 'Please enter a question.'], 422);
        }

        // Sanitize history — only allow role/content keys
        $safeHistory = [];
        foreach ($history as $msg) {
            if (isset($msg['role'], $msg['content']) && in_array($msg['role'], ['user', 'assistant'], true)) {
                $safeHistory[] = ['role' => $msg['role'], 'content' => substr($msg['content'], 0, 2000)];
            }
        }
        // Limit history to last 10 messages
        $safeHistory = array_slice($safeHistory, -10);

        $answer = $this->ai->chatHRPolicy($question, $safeHistory);
        return $this->json(['answer' => $answer]);
    }
}
