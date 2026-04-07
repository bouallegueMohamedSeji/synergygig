<?php

namespace App\Repository;

use App\Entity\Quiz;
use Doctrine\Bundle\DoctrineBundle\Repository\ServiceEntityRepository;
use Doctrine\Persistence\ManagerRegistry;

/**
 * @extends ServiceEntityRepository<Quiz>
 *
 * @method Quiz|null find($id, $lockMode = null, $lockVersion = null)
 * @method Quiz|null findOneBy(array $criteria, array $orderBy = null)
 * @method Quiz[]    findAll()
 * @method Quiz[]    findBy(array $criteria, array $orderBy = null, $limit = null, $offset = null)
 */
class QuizRepository extends ServiceEntityRepository
{
    public function __construct(ManagerRegistry $registry)
    {
        parent::__construct($registry, Quiz::class);
    }

    /**
     * @return Quiz[]
     */
    public function searchAndFilter(?string $search, ?int $courseId): array
    {
        $qb = $this->createQueryBuilder('q')
            ->leftJoin('q.course', 'c')
            ->addSelect('c');

        if ($search) {
            $qb->andWhere('q.title LIKE :search')
               ->setParameter('search', '%' . $search . '%');
        }

        if ($courseId) {
            $qb->andWhere('c.id = :courseId')
               ->setParameter('courseId', $courseId);
        }

        return $qb->orderBy('q.id', 'DESC')
            ->getQuery()
            ->getResult();
    }
}
