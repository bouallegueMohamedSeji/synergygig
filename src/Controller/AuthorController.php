<?php

namespace App\Controller;

use Symfony\Bundle\FrameworkBundle\Controller\AbstractController;
use Symfony\Component\HttpFoundation\Response;
use Symfony\Component\Routing\Attribute\Route;

final class AuthorController extends AbstractController
{
    #[Route('/showAuthor/{name}', name: 'author_show')]
    public function showAuthor($name): Response
    {
        return $this->render('author/show.html.twig', [
            'author_name' => $name,
        ]);
    }

    #[Route('/authors', name: 'author_list')]
    public function authorList(): Response
    {
        $authors = [
            ['id' => 1, 'picture' => '/images/AuthorImage.jpg','username' => 'Victor Hugo', 'email' => 'victor.hugo@gmail.com', 'nb_books' => 100],
            ['id' => 2, 'picture' => '/images/Author1Image.jpg','username' => 'William Shakespeare', 'email' => 'william.shakespeare@gmail.com', 'nb_books' => 200],
            ['id' => 3, 'picture' => '/images/Author2Image.jpg','username' => 'Taha Hussein', 'email' => 'taha.hussein@gmail.com', 'nb_books' => 300],
        ];

        return $this->render('author/list.html.twig', [
            'authors' => $authors,      
        ]);
    }

    #[Route('/author/{id}', name: 'author_details')]
    public function authorDetails($id): Response
    {
        $authors = [
            1 => ['id' => 1, 'picture' => '/images/AuthorImage.jpg', 'username' => 'Victor Hugo', 'email' => 'victor.hugo@gmail.com', 'nb_books' => 100],
            2 => ['id' => 2, 'picture' => '/images/Author1Image.jpg', 'username' => 'William Shakespeare', 'email' => 'william.shakespeare@gmail.com', 'nb_books' => 200],
            3 => ['id' => 3, 'picture' => '/images/Author2Image.jpg', 'username' => 'Taha Hussein', 'email' => 'taha.hussein@gmail.com', 'nb_books' => 300],
        ];

        $author = $authors[$id] ?? null;

        if (!$author) {
            throw $this->createNotFoundException("Auteur avec l'id $id non trouvé !");
        }

        return $this->render('author/details.html.twig', [
            'author' => $author,
        ]);
    }
}
