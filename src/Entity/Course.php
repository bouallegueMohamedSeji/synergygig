<?php

namespace App\Entity;

use App\Repository\CourseRepository;
use Doctrine\Common\Collections\ArrayCollection;
use Doctrine\Common\Collections\Collection;
use Doctrine\DBAL\Types\Types;
use Doctrine\ORM\Mapping as ORM;

#[ORM\Entity(repositoryClass: CourseRepository::class)]
#[ORM\Table(name: "courses")]
class Course
{
    #[ORM\Id]
    #[ORM\GeneratedValue]
    #[ORM\Column]
    private ?int $id = null;

    #[ORM\Column(length: 255)]
    private ?string $title = null;

    #[ORM\Column(type: Types::TEXT, nullable: true)]
    private ?string $description = null;

    #[ORM\Column(nullable: true)]
    private ?int $instructor_id = null;

    #[ORM\Column(nullable: true)]
    private ?int $skill_id = null;

    #[ORM\Column(length: 50, options: ["default" => "Beginner"])]
    private ?string $skill_level = "Beginner";

    #[ORM\OneToMany(mappedBy: 'course', targetEntity: Quiz::class, orphanRemoval: true)]
    private Collection $quizzes;

    public function __construct()
    {
        $this->quizzes = new ArrayCollection();
    }

    public function getId(): ?int
    {
        return $this->id;
    }

    public function getTitle(): ?string
    {
        return $this->title;
    }

    public function setTitle(string $title): self
    {
        $this->title = $title;
        return $this;
    }

    public function getDescription(): ?string
    {
        return $this->description;
    }

    public function setDescription(?string $description): self
    {
        $this->description = $description;
        return $this;
    }

    public function getInstructorId(): ?int
    {
        return $this->instructor_id;
    }

    public function setInstructorId(?int $instructor_id): self
    {
        $this->instructor_id = $instructor_id;
        return $this;
    }

    public function getSkillId(): ?int
    {
        return $this->skill_id;
    }

    public function setSkillId(?int $skill_id): self
    {
        $this->skill_id = $skill_id;
        return $this;
    }

    public function getSkillLevel(): ?string
    {
        return $this->skill_level;
    }

    public function setSkillLevel(string $skill_level): self
    {
        $this->skill_level = $skill_level;
        return $this;
    }

    /**
     * @return Collection<int, Quiz>
     */
    public function getQuizzes(): Collection
    {
        return $this->quizzes;
    }

    public function addQuiz(Quiz $quiz): self
    {
        if (!$this->quizzes->contains($quiz)) {
            $this->quizzes->add($quiz);
            $quiz->setCourse($this);
        }

        return $this;
    }

    public function removeQuiz(Quiz $quiz): self
    {
        if ($this->quizzes->removeElement($quiz)) {
            // set the owning side to null (unless already changed)
            if ($quiz->getCourse() === $this) {
                $quiz->setCourse(null);
            }
        }

        return $this;
    }
}
