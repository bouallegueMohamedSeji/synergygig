<?php

use App\Kernel;
use Symfony\Component\Dotenv\Dotenv;

require_once dirname(__DIR__).'/vendor/autoload.php';

if (!class_exists(Dotenv::class)) {
    throw new LogicException('Please install the "symfony/dotenv" package to load the ".env" files configuring the application.');
}

$dotenv = new Dotenv();
$dotenv->loadEnv(dirname(__DIR__).'/.env');

$kernel = new Kernel($_SERVER['APP_ENV'], (bool) $_SERVER['APP_DEBUG']);
$kernel->boot();

return $kernel->getContainer();
