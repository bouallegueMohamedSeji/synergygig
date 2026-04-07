<?php
namespace App\Controller;

use App\Repository\EventRepository;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

class EventController extends AbstractController
{
    #[Route('/events/enabled/count', name: 'app_events_enabled_count')]
    public function showEnabledEventsCount(EventRepository $eventRepository): Response
    {
        $count = $eventRepository->countEnabledEvents();

        $message = "evennements actifs (enable=1) est : $count";
        
        return new Response($message);

    }
}
