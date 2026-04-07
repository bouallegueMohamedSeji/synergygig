<?php

namespace App\Entity;

use App\Repository\QuestionRepository;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: QuestionRepository::class)]
#[ORM\Table(name: "questions")]
class Question
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\ManyToOne(targetEntity: Quiz::class, inversedBy: 'questions')]
    #[ORM\JoinColumn(name: "quiz_id", referencedColumnName: "id", nullable: false)]
    private ?Quiz $quiz = null;

    #[ORM\Column(type: Types::TEXT)]
    private ?string $question_text = null;

    #[ORM\Column(length: 255)]
    private ?string $option_a = null;

    #[ORM\Column(length: 255)]
    private ?string $option_b = null;

    #[ORM\Column(length: 255)]
    private ?string $option_c = null;

    #[ORM\Column(length: 1)]
    private ?string $correct_option = null;

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getQuiz(): ?Quiz
    {
        return $this->quiz;
    }

    public function setQuiz(?Quiz $quiz): self
    {
        $this->quiz = $quiz;
        return $this;
    }

    public function getQuestionText(): ?string
    {
        return $this->question_text;
    }

    public function setQuestionText(string $question_text): self
    {
        $this->question_text = $question_text;
        return $this;
    }

    public function getOptionA(): ?string
    {
        return $this->option_a;
    }

    public function setOptionA(string $option_a): self
    {
        $this->option_a = $option_a;
        return $this;
    }

    public function getOptionB(): ?string
    {
        return $this->option_b;
    }

    public function setOptionB(string $option_b): self
    {
        $this->option_b = $option_b;
        return $this;
    }

    public function getOptionC(): ?string
    {
        return $this->option_c;
    }

    public function setOptionC(string $option_c): self
    {
        $this->option_c = $option_c;
        return $this;
    }

    public function getCorrectOption(): ?string
    {
        return $this->correct_option;
    }

    public function setCorrectOption(string $correct_option): self
    {
        $this->correct_option = $correct_option;
        return $this;
    }
}
