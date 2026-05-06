<?php

namespace App\Controller;

use KnpU\OAuth2ClientBundle\Client\ClientRegistry;
use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\RedirectResponse;
use Symfony\Component\HttpFoundation\Request;
use Symfony\Component\Routing\Annotation\Route;

class OAuthController extends AbstractController
{
    #[Route('/connect/google', name: 'connect_google')]
    public function connectGoogle(ClientRegistry $registry): RedirectResponse
    {
        return $registry->getClient('google')->redirect(['email', 'profile'], []);
    }

    #[Route('/connect/google/check', name: 'connect_google_check')]
    public function connectGoogleCheck(Request $request): never
    {
        // Handled by GoogleAuthenticator — this action is never reached.
        throw new \LogicException('This should never be reached.');
    }

    #[Route('/connect/github', name: 'connect_github')]
    public function connectGithub(ClientRegistry $registry): RedirectResponse
    {
        return $registry->getClient('github')->redirect(['user:email'], []);
    }

    #[Route('/connect/github/check', name: 'connect_github_check')]
    public function connectGithubCheck(Request $request): never
    {
        throw new \LogicException('This should never be reached.');
    }
}
