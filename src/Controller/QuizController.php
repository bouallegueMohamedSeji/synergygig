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
    public function show(Quiz $quiz): Response
    {
        return $this->render('quiz/show.html.twig', [
            'quiz' => $quiz,
        ]);
    }

    #[Route('/{id}/edit', name: 'quiz_edit', methods: ['GET', 'POST'])]
    public function edit(Request $request, Quiz $quiz, CourseRepository $courseRepository, EntityManagerInterface $entityManager): Response
    {
        $courses = $courseRepository->findAllOrderedByTitle();
        $errors = [];

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
        $question = $questionRepository->find($qid);
        if ($question && $this->isCsrfTokenValid('delete_question'.$question->getId(), $request->request->get('_token'))) {
            $entityManager->remove($question);
            $entityManager->flush();
            $this->addFlash('success', 'Question supprimée.');
        }

        return $this->redirectToRoute('quiz_show', ['id' => $quiz->getId()]);
    }
}
