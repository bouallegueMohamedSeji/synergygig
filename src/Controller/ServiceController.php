<?php
namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

class ServiceController extends AbstractController
{
    #[Route('/service/{name}', name: 'app_service_show')]
    public function showService(string $name): Response
    {
        return $this->render('service/index.html.twig', [
            'name' => $name
        ]);
    }

    #[Route('/go-to-index', name: 'app_go_to_index')]
    public function goToIndex()
    {
        return $this->redirectToRoute('app_home');
    }
}
