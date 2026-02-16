-- phpMyAdmin SQL Dump
-- version 5.2.1
-- https://www.phpmyadmin.net/
--
-- Host: 127.0.0.1
-- Generation Time: Feb 15, 2026 at 09:05 PM
-- Server version: 10.4.32-MariaDB
-- PHP Version: 8.1.25

SET FOREIGN_KEY_CHECKS=0;
SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- Database: `synergygig_db`
--

-- --------------------------------------------------------

--
-- Table structure for table `applications`
--

CREATE TABLE IF NOT EXISTS `applications` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `offer_id` int(11) NOT NULL,
  `applicant_id` int(11) NOT NULL,
  `status` enum('PENDING','ACCEPTED','REJECTED') DEFAULT 'PENDING',
  `applied_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `offer_id` (`offer_id`),
  KEY `applicant_id` (`applicant_id`),
  CONSTRAINT `applications_ibfk_1` FOREIGN KEY (`offer_id`) REFERENCES `offers` (`id`) ON DELETE CASCADE,
  CONSTRAINT `applications_ibfk_2` FOREIGN KEY (`applicant_id`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `applications`
--

INSERT INTO `applications` (`id`, `offer_id`, `applicant_id`, `status`, `applied_at`) VALUES
(1, 5, 3, 'ACCEPTED', '2026-02-09 23:54:56'),
(2, 6, 3, 'REJECTED', '2026-02-10 16:22:08'),
(3, 4, 3, 'ACCEPTED', '2026-02-10 19:43:42'),
(4, 8, 3, 'PENDING', '2026-02-11 08:27:27'),
(5, 11, 3, 'ACCEPTED', '2026-02-11 08:33:53'),
(6, 15, 3, 'REJECTED', '2026-02-13 19:02:09'),
(7, 19, 3, 'ACCEPTED', '2026-02-13 21:46:10'),
(8, 22, 3, 'PENDING', '2026-02-15 12:43:45'),
(9, 23, 3, 'ACCEPTED', '2026-02-15 13:35:35'),
(10, 17, 3, 'REJECTED', '2026-02-15 13:36:21');

-- --------------------------------------------------------

--
-- Table structure for table `contracts`
--

CREATE TABLE IF NOT EXISTS `contracts` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `application_id` int(11) NOT NULL,
  `start_date` date NOT NULL,
  `end_date` date NOT NULL,
  `amount` decimal(10,2) NOT NULL,
  `terms` text DEFAULT NULL,
  `status` enum('GENERATED','SIGNED','COMPLETED','ARCHIVED') DEFAULT 'GENERATED',
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `application_id` (`application_id`),
  CONSTRAINT `contracts_ibfk_1` FOREIGN KEY (`application_id`) REFERENCES `applications` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `offers`
--

CREATE TABLE IF NOT EXISTS `offers` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(150) NOT NULL,
  `description` text NOT NULL,
  `type` enum('INTERNAL','GIG') NOT NULL,
  `status` enum('DRAFT','PUBLISHED','IN_PROGRESS','COMPLETED','CANCELLED') DEFAULT 'DRAFT',
  `created_by` int(11) DEFAULT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  `image_url` varchar(255) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `offers_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `offers`
--

INSERT INTO `offers` (`id`, `title`, `description`, `type`, `status`, `created_by`, `created_at`, `image_url`) VALUES
(1, 'Java Backend Mission', 'CRUD JDBC + MySQL', 'GIG', 'CANCELLED', 1, '2026-02-07 13:47:22', NULL),
(2, 'UPDATED Java Mission', 'CRUD JDBC + MySQL', 'GIG', 'CANCELLED', 1, '2026-02-07 13:57:25', NULL),
(4, 'Service Layer Offer', 'Test via OfferService', 'INTERNAL', 'CANCELLED', 1, '2026-02-07 14:14:59', NULL),
(5, 'ENUM Offer', 'Strong business logic', 'GIG', 'IN_PROGRESS', 1, '2026-02-07 14:46:52', NULL),
(6, 'ENUM Offer', 'Strong business logic', 'GIG', 'PUBLISHED', 1, '2026-02-07 14:51:25', NULL),
(8, 'asd', 'wadw', 'GIG', 'PUBLISHED', 1, '2026-02-07 16:49:45', NULL),
(11, 'ffvfv', 'fvfvf', 'GIG', 'IN_PROGRESS', 1, '2026-02-07 18:44:54', NULL),
(13, 'Draft Java Offerddd', 'Offre en brouillon pour test dashboard', 'GIG', 'CANCELLED', 1, '2026-02-08 21:13:33', NULL),
(14, 'anas', 'qwwqd', 'INTERNAL', 'DRAFT', 1, '2026-02-09 00:00:57', NULL),
(15, 'hbkjvjhb', 'h nb', 'INTERNAL', 'PUBLISHED', 1, '2026-02-09 01:40:40', NULL),
(16, 'jwdjqjwh', 'fwqfbjwq', 'GIG', 'DRAFT', 1, '2026-02-09 20:04:14', NULL),
(17, 'nousti', 'the best gigworker', 'GIG', 'PUBLISHED', 1, '2026-02-13 21:23:56', NULL),
(18, 'syrine ', 'dsfsa', 'INTERNAL', 'CANCELLED', 1, '2026-02-13 21:24:30', NULL),
(19, 'aca', 'dscA', 'INTERNAL', 'IN_PROGRESS', 1, '2026-02-13 21:44:24', NULL),
(20, 'anasss', 'fdlllll', 'GIG', 'PUBLISHED', 1, '2026-02-14 01:13:56', NULL),
(21, 'gsdfs', 'dsssddddddddddd', 'GIG', 'PUBLISHED', 1, '2026-02-14 01:27:08', 'Anime Art Night Sky Scenery Wallpaper iPhone Phone 4k 1400f.jpg'),
(22, 'frt', 'hhhhh', 'GIG', 'PUBLISHED', 1, '2026-02-14 01:38:52', 'bmw-e30-synthwave-neon-desktop-wallpaper-4k.jpg'),
(23, 'sdfds', 'sadf', 'GIG', 'IN_PROGRESS', 1, '2026-02-15 13:35:06', '590807354_122188269362340139_5199369176720042024_n (1).jpg'),
(24, 'asdsas', 'assas', 'INTERNAL', 'PUBLISHED', 1, '2026-02-15 20:02:26', 'ChatGPT Image Feb 15, 2026, 03_40_28 PM.png'),
(25, 'anassssssssssssss', 'saa', 'INTERNAL', 'CANCELLED', 1, '2026-02-15 20:03:19', 'ComfyUI_00046_.png');

-- --------------------------------------------------------

--
-- Table structure for table `skills`
--

CREATE TABLE IF NOT EXISTS `skills` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `name` varchar(100) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Table structure for table `users`
--

CREATE TABLE IF NOT EXISTS `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `full_name` varchar(100) NOT NULL,
  `email` varchar(100) NOT NULL,
  `password` varchar(255) NOT NULL,
  `role` enum('ADMIN','HR','EMPLOYEE','PROJECT_OWNER','GIG_WORKER') NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  UNIQUE KEY `email` (`email`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

--
-- Dumping data for table `users`
--

INSERT INTO `users` (`id`, `full_name`, `email`, `password`, `role`, `created_at`) VALUES
(1, 'Admin Anas', 'admin@sg.com', 'anasadmin123', 'ADMIN', '2026-02-05 22:27:08'),
(2, 'HR Manager Anas', 'hr@sg.com', 'anashr123', 'HR', '2026-02-05 22:27:08'),
(3, 'Employee Anas', 'emp@sg.com', 'anasemp123', 'EMPLOYEE', '2026-02-05 22:27:08'),
(4, 'Gig Worker Anas', 'gig@sg.com', 'anasgig123', 'GIG_WORKER', '2026-02-05 22:27:08');

-- --------------------------------------------------------

--
-- Table structure for table `user_skills`
--

CREATE TABLE IF NOT EXISTS `user_skills` (
  `user_id` int(11) NOT NULL,
  `skill_id` int(11) NOT NULL,
  `level` enum('BEGINNER','INTERMEDIATE','ADVANCED') DEFAULT NULL,
  PRIMARY KEY (`user_id`,`skill_id`),
  KEY `skill_id` (`skill_id`),
  CONSTRAINT `user_skills_ibfk_1` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`) ON DELETE CASCADE,
  CONSTRAINT `user_skills_ibfk_2` FOREIGN KEY (`skill_id`) REFERENCES `skills` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------
-- Table structure for table `forums`
-- --------------------------------------------------------

CREATE TABLE IF NOT EXISTS `forums` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `title` varchar(255) NOT NULL,
  `content` text NOT NULL,
  `created_by` int(11) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `forums_ibfk_1` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------
-- Table structure for table `forum_comments`
-- --------------------------------------------------------

CREATE TABLE IF NOT EXISTS `forum_comments` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `forum_id` int(11) NOT NULL,
  `content` text NOT NULL,
  `created_by` int(11) NOT NULL,
  `created_at` timestamp NOT NULL DEFAULT current_timestamp(),
  PRIMARY KEY (`id`),
  KEY `forum_id` (`forum_id`),
  KEY `created_by` (`created_by`),
  CONSTRAINT `forum_comments_ibfk_1` FOREIGN KEY (`forum_id`) REFERENCES `forums` (`id`) ON DELETE CASCADE,
  CONSTRAINT `forum_comments_ibfk_2` FOREIGN KEY (`created_by`) REFERENCES `users` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;


COMMIT;
SET FOREIGN_KEY_CHECKS=1;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
