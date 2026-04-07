<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Annotation\Route;

class AuthController extends AbstractController
{
    #[Route('/login/admin', name: 'login_admin')]
    public function loginAdmin(Request $request): Response
    {
        $session = $request->getSession();
        $session->set('role', 'ROLE_ADMIN');
        $session->set('user_id', 1);
        $session->set('username', 'Master Admin');
        
        $this->addFlash('success', 'Logged in as Admin (Full Control)');
        return $this->redirectToRoute('quiz_index');
    }

    #[Route('/login/user', name: 'login_user')]
    public function loginUser(Request $request): Response
    {
        $session = $request->getSession();
        $session->set('role', 'ROLE_USER');
        $session->set('user_id', 4); // "gig@gmail.com" in your DB
        $session->set('username', 'Standard User');
        
        $this->addFlash('success', 'Logged in as User (Read Only)');
        return $this->redirectToRoute('quiz_index');
    }

    #[Route('/logout', name: 'app_logout')]
    public function logout(Request $request): Response
    {
        $session = $request->getSession();
        $session->remove('role');
        $session->remove('user_id');
        $session->remove('username');
        
        $this->addFlash('info', 'Logged out successfully.');
        return $this->redirectToRoute('quiz_index');
    }
}
