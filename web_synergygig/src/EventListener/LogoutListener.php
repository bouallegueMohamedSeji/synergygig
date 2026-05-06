<?php

namespace App\EventListener;

use App\Entity\User;
use App\Repository\AttendanceRepository;
use Doctrine\ORM\EntityManagerInterface;
use Symfony\Component\EventDispatcher\Attribute\AsEventListener;
use Symfony\Component\Security\Http\Event\LogoutEvent;

#[AsEventListener(event: LogoutEvent::class, priority: -10)]
class LogoutListener
{
    public function __construct(
        private readonly EntityManagerInterface $em,
        private readonly AttendanceRepository $repo,
    ) {}

    public function __invoke(LogoutEvent $event): void
    {
        $token = $event->getToken();
        if (!$token) {
            return;
        }

        $user = $token->getUser();
        if (!$user) {
            return;
        }

        // Mark user offline on logout
        if ($user instanceof User) {
            $user->setIsOnline(false);
            $user->setOnlineStatus('offline');
        }

        $today = new \DateTime('today');
        $record = $this->repo->findOneBy(['user' => $user, 'date' => $today]);

        if ($record && $record->getCheckIn()) {
            $record->setCheckOut(new \DateTime());
            $this->em->flush();
        }
    }
}
