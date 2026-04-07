<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class XdController extends AbstractController
{
    #[Route('/xd', name: 'app_xd')]
    public function index(): Response
    {
        return $this->render('xd/index.html.twig', [
            'controller_name' => 'XdController',
        ]);
    }
}
