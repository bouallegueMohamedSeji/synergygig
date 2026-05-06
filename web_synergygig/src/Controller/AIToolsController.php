<?php

namespace App\Controller;

use App\Service\AIService;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\JsonResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/ai')]
class AIToolsController extends AbstractController
{
    public function __construct(private AIService $ai) {}

    /* ───────────────────── Hub Page ───────────────────── */

    #[Route('', name: 'app_ai_index')]
    public function index(): Response
    {
        return $this->render('ai/index.html.twig');
    }

    /* ───────────────────── Code Review ───────────────────── */

    #[Route('/code-review', name: 'app_ai_code_review')]
    public function codeReview(): Response
    {
        return $this->render('ai/code_review.html.twig');
    }

    #[Route('/code-review/process', name: 'app_ai_code_review_process', methods: ['POST'])]
    public function processCodeReview(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);
        $code = trim($data['code'] ?? '');
        $lang = $data['language'] ?? 'Unknown';

        if (strlen($code) < 10) {
            return $this->json(['error' => 'Code must be at least 10 characters.'], 422);
        }

        $review = $this->ai->reviewCode($code, $lang);
        return $this->json(['review' => $review]);
    }

    /* ───────────────────── Email Composer ───────────────────── */

    #[Route('/email-composer', name: 'app_ai_email_composer')]
    public function emailComposer(): Response
    {
        return $this->render('ai/email_composer.html.twig');
    }

    #[Route('/email-composer/process', name: 'app_ai_email_composer_process', methods: ['POST'])]
    public function processEmailComposer(Request $request): JsonResponse
    {
        $data   = json_decode($request->getContent(), true);
        $to     = trim($data['recipient'] ?? '') ?: 'Colleague';
        $purpose = trim($data['purpose'] ?? '');
        $points = trim($data['keyPoints'] ?? '');
        $tone   = $data['tone'] ?? 'Professional';

        if ($purpose === '') {
            return $this->json(['error' => 'Purpose is required.'], 422);
        }

        $email = $this->ai->composeEmail($to, $purpose, $points, $tone);
        return $this->json(['email' => $email]);
    }

    /* ───────────────────── Meeting Summarizer ───────────────────── */

    #[Route('/meeting-summarizer', name: 'app_ai_meeting_summarizer')]
    public function meetingSummarizer(): Response
    {
        return $this->render('ai/meeting_summarizer.html.twig');
    }

    #[Route('/meeting-summarizer/process', name: 'app_ai_meeting_summarizer_process', methods: ['POST'])]
    public function processMeetingSummarizer(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);
        $text = trim($data['transcript'] ?? '');

        if (strlen($text) < 30) {
            return $this->json(['error' => 'Transcript must be at least 30 characters.'], 422);
        }

        $summary = $this->ai->summarizeMeeting($text);
        return $this->json(['summary' => $summary]);
    }

    /* ───────────────────── Resume Parser ───────────────────── */

    #[Route('/resume-parser', name: 'app_ai_resume_parser')]
    public function resumeParser(): Response
    {
        return $this->render('ai/resume_parser.html.twig');
    }

    #[Route('/resume-parser/process', name: 'app_ai_resume_parser_process', methods: ['POST'])]
    public function processResumeParser(Request $request): JsonResponse
    {
        $data = json_decode($request->getContent(), true);
        $text = trim($data['text'] ?? '');

        if (strlen($text) < 30) {
            return $this->json(['error' => 'Resume text must be at least 30 characters.'], 422);
        }

        $parsed = $this->ai->parseResume($text);
        return $this->json($parsed);
    }

    /* ───────────────────── Interview Prep ───────────────────── */

    #[Route('/interview-prep', name: 'app_ai_interview_prep')]
    public function interviewPrep(): Response
    {
        return $this->render('ai/interview_prep.html.twig');
    }

    #[Route('/interview-prep/process', name: 'app_ai_interview_prep_process', methods: ['POST'])]
    public function processInterviewPrep(Request $request): JsonResponse
    {
        $data   = json_decode($request->getContent(), true);
        $role   = trim($data['role'] ?? '');
        $level  = $data['level'] ?? 'Junior';
        $action = $data['action'] ?? 'start';
        $answer = trim($data['answer'] ?? '');
        $qNum   = intval($data['questionNumber'] ?? 0);

        if ($role === '') {
            return $this->json(['error' => 'Role is required.'], 422);
        }

        $response = $this->ai->interviewQuestion($role, $level, $action, $answer, $qNum);
        return $this->json($response);
    }
}
