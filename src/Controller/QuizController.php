<?php

namespace App\Controller;

use App\Entity\Question;
use App\Entity\Quiz;
use App\Repository\CourseRepository;
use App\Repository\QuestionRepository;
use App\Repository\QuizRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

#[Route('/quiz')]
class QuizController extends AbstractController
{
    #[Route('', name: 'quiz_index', methods: ['GET'])]
    public function index(Request $request, QuizRepository $quizRepository, CourseRepository $courseRepository): Response
    {
        $search = $request->query->get('search');
        $courseId = $request->query->get('course');

        $quizzes = $quizRepository->searchAndFilter($search, $courseId ? (int)$courseId : null);
        $courses = $courseRepository->findAllOrderedByTitle();

        return $this->render('quiz/index.html.twig', [
            'quizzes' => $quizzes,
            'courses' => $courses,
            'search' => $search,
            'currentCourse' => $courseId,
        ]);
    }

    #[Route('/new', name: 'quiz_new', methods: ['GET', 'POST'])]
    public function new(Request $request, CourseRepository $courseRepository, EntityManagerInterface $entityManager): Response
    {
        $courses = $courseRepository->findAllOrderedByTitle();
        $errors = [];

        if ($request->getSession()->get('role') !== 'ROLE_ADMIN') {
            $this->addFlash('error', 'Access Denied: You must be an Admin to create quizzes.');
            return $this->redirectToRoute('quiz_index');
        }

        if ($request->isMethod('POST')) {
            $title = $request->request->get('title');
            $courseId = $request->request->get('course');

            // Manual Validation
            if (empty($title) || strlen($title) < 3) {
                $errors[] = "Le titre du quiz est requis et doit comporter au moins 3 caractères.";
            }

            $course = $courseId ? $courseRepository->find($courseId) : null;
            if (!$course) {
                $errors[] = "Un cours valide est requis.";
            }

            if (empty($errors)) {
                $quiz = new Quiz();
                $quiz->setTitle($title);
                $quiz->setCourse($course);

                $entityManager->persist($quiz);
                $entityManager->flush();

                $this->addFlash('success', 'Quiz créé avec succès !');
                return $this->redirectToRoute('quiz_index');
            }
        }

        return $this->render('quiz/new.html.twig', [
            'courses' => $courses,
            'errors' => $errors,
        ]);
    }

    #[Route('/{id}', name: 'quiz_show', methods: ['GET'])]
    public function show(Quiz $quiz, Request $request): Response
    {
        $isAdmin = $request->getSession()->get('role') === 'ROLE_ADMIN';
        return $this->render('quiz/show.html.twig', [
            'quiz' => $quiz,
            'isAdmin' => $isAdmin
        ]);
    }

    #[Route('/{id}/edit', name: 'quiz_edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, Quiz $quiz, CourseRepository $courseRepository, EntityManagerInterface $entityManager): Response
    {
        $courses = $courseRepository->findAllOrderedByTitle();
        $errors = [];

        if ($request->getSession()->get('role') !== 'ROLE_ADMIN') {
            $this->addFlash('error', 'Access Denied: Only Admins can edit quizzes.');
            return $this->redirectToRoute('quiz_index');
        }

        if ($request->isMethod('POST')) {
            $title = $request->request->get('title');
            $courseId = $request->request->get('course');

            if (empty($title) || strlen($title) < 3) {
                $errors[] = "Le titre du quiz est obligatoire et doit faire au moins 3 caractères.";
            }

            $course = $courseId ? $courseRepository->find($courseId) : null;
            if (!$course) {
                $errors[] = "Sélectionnez un cours valide.";
            }

            if (empty($errors)) {
                $quiz->setTitle($title);
                $quiz->setCourse($course);
                $entityManager->flush();

                $this->addFlash('success', 'Quiz mis à jour !');
                return $this->redirectToRoute('quiz_index');
            }
        }

        return $this->render('quiz/edit.html.twig', [
            'quiz' => $quiz,
            'courses' => $courses,
            'errors' => $errors,
        ]);
    }

    #[Route('/{id}/delete', name: 'quiz_delete', methods: ['POST'])]
    public function delete(Request $request, Quiz $quiz, EntityManagerInterface $entityManager): Response
    {
        if ($request->getSession()->get('role') !== 'ROLE_ADMIN') {
            $this->addFlash('error', 'Access Denied.');
            return $this->redirectToRoute('quiz_index');
        }

        if ($this->isCsrfTokenValid('delete'.$quiz->getId(), $request->request->get('_token'))) {
            $entityManager->remove($quiz);
            $entityManager->flush();
            $this->addFlash('success', 'Quiz supprimé.');
        }

        return $this->redirectToRoute('quiz_index');
    }

    // --- Question Management ---

    #[Route('/{id}/question/new', name: 'question_new', methods: ['GET', 'POST'])]
    public function questionNew(Request $request, Quiz $quiz, EntityManagerInterface $entityManager): Response
    {
        $errors = [];

        if ($request->getSession()->get('role') !== 'ROLE_ADMIN') {
            $this->addFlash('error', 'Access Denied.');
            return $this->redirectToRoute('quiz_index');
        }

        if ($request->isMethod('POST')) {
            $text = $request->request->get('question_text');
            $optA = $request->request->get('option_a');
            $optB = $request->request->get('option_b');
            $optC = $request->request->get('option_c');
            $correct = $request->request->get('correct_option');

            if (empty($text) || strlen($text) < 5) $errors[] = "La question doit faire au moins 5 caractères.";
            if (empty($optA) || empty($optB) || empty($optC)) $errors[] = "Toutes les options sont obligatoires.";
            if (!in_array($correct, ['A', 'B', 'C'])) $errors[] = "L'option correcte doit être A, B ou C.";

            if (empty($errors)) {
                $question = new Question();
                $question->setQuiz($quiz);
                $question->setQuestionText($text);
                $question->setOptionA($optA);
                $question->setOptionB($optB);
                $question->setOptionC($optC);
                $question->setCorrectOption($correct);

                $entityManager->persist($question);
                $entityManager->flush();

                $this->addFlash('success', 'Question ajoutée !');
                return $this->redirectToRoute('quiz_show', ['id' => $quiz->getId()]);
            }
        }

        return $this->render('quiz/question_form.html.twig', [
            'quiz' => $quiz,
            'errors' => $errors,
            'mode' => 'new'
        ]);
    }

    #[Route('/{id}/question/{qid}/edit', name: 'question_edit', methods: ['GET', 'POST'])]
    public function questionEdit(Request $request, Quiz $quiz, int $qid, QuestionRepository $questionRepository, EntityManagerInterface $entityManager): Response
    {
        $question = $questionRepository->find($qid);
        if (!$question) throw $this->createNotFoundException();

        $errors = [];

        if ($request->getSession()->get('role') !== 'ROLE_ADMIN') {
            $this->addFlash('error', 'Access Denied.');
            return $this->redirectToRoute('quiz_index');
        }

        if ($request->isMethod('POST')) {
            $text = $request->request->get('question_text');
            $optA = $request->request->get('option_a');
            $optB = $request->request->get('option_b');
            $optC = $request->request->get('option_c');
            $correct = $request->request->get('correct_option');

            if (empty($text) || strlen($text) < 5) $errors[] = "La question doit faire au moins 5 caractères.";
            if (empty($optA) || empty($optB) || empty($optC)) $errors[] = "Toutes les options sont obligatoires.";
            if (!in_array($correct, ['A', 'B', 'C'])) $errors[] = "L'option correcte doit être A, B ou C.";

            if (empty($errors)) {
                $question->setQuestionText($text);
                $question->setOptionA($optA);
                $question->setOptionB($optB);
                $question->setOptionC($optC);
                $question->setCorrectOption($correct);
                $entityManager->flush();

                $this->addFlash('success', 'Question mise à jour !');
                return $this->redirectToRoute('quiz_show', ['id' => $quiz->getId()]);
            }
        }

        return $this->render('quiz/question_form.html.twig', [
            'quiz' => $quiz,
            'question' => $question,
            'errors' => $errors,
            'mode' => 'edit'
        ]);
    }

    #[Route('/{id}/question/{qid}/delete', name: 'question_delete', methods: ['POST'])]
    public function questionDelete(Request $request, Quiz $quiz, int $qid, QuestionRepository $questionRepository, EntityManagerInterface $entityManager): Response
    {
        if ($request->getSession()->get('role') !== 'ROLE_ADMIN') {
            $this->addFlash('error', 'Access Denied.');
            return $this->redirectToRoute('quiz_index');
        }

        $question = $questionRepository->find($qid);
        if ($question && $this->isCsrfTokenValid('delete_question'.$question->getId(), $request->request->get('_token'))) {
            $entityManager->remove($question);
            $entityManager->flush();
            $this->addFlash('success', 'Question supprimée.');
        }

        return $this->redirectToRoute('quiz_show', ['id' => $quiz->getId()]);
    }

    // --- Quiz Taking & Certification ---

    #[Route('/{id}/take', name: 'quiz_take', methods: ['GET'])]
    public function take(Quiz $quiz, Request $request): Response
    {
        if (!$request->getSession()->get('role')) {
            $this->addFlash('error', 'Please login to take the quiz.');
            return $this->redirectToRoute('quiz_index');
        }

        return $this->render('quiz/take.html.twig', [
            'quiz' => $quiz,
        ]);
    }

    #[Route('/{id}/submit', name: 'quiz_submit', methods: ['POST'])]
    public function submit(Request $request, Quiz $quiz, EntityManagerInterface $entityManager): Response
    {
        $userId = $request->getSession()->get('user_id');
        if (!$userId) {
            $this->addFlash('error', 'Authentication error.');
            return $this->redirectToRoute('quiz_index');
        }

        $answers = $request->request->all('answers');
        $questions = $quiz->getQuestions();
        $total = count($questions);
        $correctCount = 0;

        if ($total === 0) {
            $this->addFlash('error', 'This quiz has no questions.');
            return $this->redirectToRoute('quiz_show', ['id' => $quiz->getId()]);
        }

        foreach ($questions as $q) {
            $submitted = $answers[$q->getId()] ?? null;
            if ($submitted === $q->getCorrectOption()) {
                $correctCount++;
            }
        }

        $score = round(($correctCount / $total) * 100);
        $passed = $score >= 70;

        // Save Attempt (Raw SQL for simplicity)
        $conn = $entityManager->getConnection();
        $conn->executeStatement(
            'INSERT INTO quiz_attempts (quiz_id, user_id, score, attempt_at) VALUES (?, ?, ?, NOW())',
            [$quiz->getId(), $userId, $score]
        );

        // Award Skill if Passed
        if ($passed && $quiz->getCourse() && $quiz->getCourse()->getSkillId()) {
            $skillId = $quiz->getCourse()->getSkillId();
            $level = $quiz->getCourse()->getSkillLevel() ?: 'Beginner';
            
            // Check if skill already exists
            $existing = $conn->fetchOne('SELECT 1 FROM user_skills WHERE user_id = ? AND skill_id = ?', [$userId, $skillId]);
            
            if ($existing) {
                $conn->executeStatement(
                    'UPDATE user_skills SET skill_level = ? WHERE user_id = ? AND skill_id = ?',
                    [$level, $userId, $skillId]
                );
            } else {
                $conn->executeStatement(
                    'INSERT INTO user_skills (user_id, skill_id, skill_level) VALUES (?, ?, ?)',
                    [$userId, $skillId, $level]
                );
            }
        }

        return $this->render('quiz/result.html.twig', [
            'quiz' => $quiz,
            'score' => $score,
            'passed' => $passed,
            'correct' => $correctCount,
            'total' => $total
        ]);
    }
}
