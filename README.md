# Esprit-PIDEV-3A16-2026-SynergyGig

> **SynergyGig** — A comprehensive HR & Workforce Management platform built with **JavaFX 21**, featuring real-time messaging, audio/video calls with live transcription, AI-powered HR tools, job scanning, training & certification, and community features.
>
> Developed at **Esprit School of Engineering** – Tunisia | Academic Year 2025–2026

![Java](https://img.shields.io/badge/Java-17-orange?logo=openjdk)
![JavaFX](https://img.shields.io/badge/JavaFX-21.0.2-blue?logo=java)
![MySQL](https://img.shields.io/badge/MySQL-8.0-4479A1?logo=mysql&logoColor=white)
![FastAPI](https://img.shields.io/badge/FastAPI-0.104-009688?logo=fastapi&logoColor=white)
![Docker](https://img.shields.io/badge/Docker-Deployed-2496ED?logo=docker&logoColor=white)
![License](https://img.shields.io/badge/License-Academic-red)

---

## Table of Contents

- [Overview](#overview)
- [Features](#features)
- [Architecture](#architecture)
- [Tech Stack](#tech-stack)
- [Getting Started](#getting-started)
- [Configuration](#configuration)
- [Project Structure](#project-structure)
- [Modules](#modules)
- [Screenshots](#screenshots)
- [Contributors](#contributors)
- [Academic Context](#academic-context)
- [Acknowledgments](#acknowledgments)

---

## Overview

**SynergyGig** is a full-featured desktop application designed for modern HR and workforce management. It combines traditional HR operations (leave, attendance, payroll) with gig-economy tools (job offers, contracts, freelancer management) and modern collaboration features (real-time chat, video calls, AI assistants).

Built as a **PIDEV** (Projet Intégré de Développement) capstone project at **Esprit School of Engineering** (Tunisia), SynergyGig demonstrates enterprise-level JavaFX development with REST API integration, WebSocket real-time communication, AI/ML services, and a rich modular UI.

---

## Features

### Core Platform
- **Authentication** — Email/password login, Face ID recognition, email verification, OTP password reset, CAPTCHA
- **User Profiles** — Avatars, bios, role-based access (Admin, HR Manager, Employee, Project Owner, Gig Worker)
- **Admin Panel** — User management, account freeze/unfreeze, role assignment

### Communication
- **Real-time Chat** — Private DMs, group chat rooms, emoji picker with color rendering
- **AI Chat Assistant** — Integrated AI-powered assistant in conversations
- **Audio Calls** — Voice calls via WebSocket relay with volume controls
- **Video Calls** — Screen sharing, webcam support, picture-in-picture preview
- **Live Transcription (CC)** — Real-time speech-to-text subtitles during calls via Groq Whisper API, with auto-translation support

### HR Management
- **Leave Management** — Request, approve/reject leaves with status tracking
- **Attendance Tracking** — Clock in/out, daily records
- **Payroll** — Salary computation, PDF export
- **Employee of the Month** — Recognition system
- **Onboarding Checklist** — New hire task tracking
- **HR Backlog** — HR task queue management
- **HR Policy Chat** — AI chatbot for company policy Q&A

### Projects & Tasks
- **Project Management** — CRUD projects, department assignment, member management
- **Task Board** — Kanban-style task tracking per project with assignees
- **Code Review** — Integrated code review tool

### Jobs & Contracts
- **Job Offers** — Create/manage job postings with required skills
- **Contracts** — Generate and manage employment contracts with PDF export
- **Job Scanner** — Scrape LinkedIn/Reddit/RSS for external job postings via n8n webhooks
- **Skills-based Search** — Auto-suggest job searches based on your training certificates and completed courses
- **Resume Parser** — AI-powered CV/resume parsing and analysis

### Training & Certification
- **Training Courses** — Course catalog with categories (Technical, Soft Skills, Compliance, Leadership, Onboarding)
- **Enrollments** — Track progress, quizzes with timers, completion status
- **Certificates** — Auto-generated PDF certificates with HR digital signatures and blockchain verification

### Community & Social
- **Community Groups** — Create/join groups, public/private privacy settings
- **Posts & Comments** — Social feed with reactions (like, heart, etc.)
- **Friend System** — Follow/friend requests with accept/reject workflow
- **Content Moderation** — Bad words API integration for profanity filtering

### AI Integration
- **Multi-Provider AI** — Z.AI (GLM-5), Groq (Llama/Whisper), OpenRouter, OpenCode with automatic fallback
- **Interview Prep Coach** — AI-driven mock interview practice
- **Meeting Summarizer** — AI meeting note generation
- **Document OCR** — Extract text from documents and images
- **Email Composer** — AI-assisted email drafting
- **Auto Scheduler** — AI-powered scheduling assistant

### Utilities
- **Weather Widget** — Live weather display (wttr.in)
- **Currency Converter** — Real-time exchange rates
- **Dark/Light Theme** — Full theme support with scheduled auto-switching
- **Notification System** — In-app notifications with sound effects
- **Sound Manager** — UI interaction sounds (click, call ring, message, etc.)

---

## Architecture

```
┌─────────────────────────────────────────────┐
│              JavaFX Desktop App              │
│  ┌─────────┐ ┌──────────┐ ┌──────────────┐  │
│  │  FXML   │ │Controllers│ │   Services   │  │
│  │  Views  │ │  (32)     │ │   (29)       │  │
│  └─────────┘ └──────────┘ └──────┬───────┘  │
│                                   │          │
│  ┌──────────┐ ┌──────────────────┴────────┐ │
│  │ Entities │ │      Utils (37 classes)    │ │
│  │  (25)    │ │ API · Audio · AI · Cache   │ │
│  └──────────┘ └──────────────────┬────────┘ │
└──────────────────────────────────┼──────────┘
                                   │
        ┌──────────────────────────┼──────────────┐
        │              REST API (FastAPI)          │
        │         Docker · Nginx · SSL            │
        └──────────────────┬──────────────────────┘
                           │
        ┌──────────────────┴──────────────────────┐
        │              MySQL 8.0                   │
        │           34 tables · Docker             │
        └─────────────────────────────────────────┘
```

**Communication layer:**
- REST API (CRUD) via `ApiClient` → FastAPI backend
- WebSocket relay for audio/video calls and signaling
- JDBC fallback for direct database access
- In-memory caching with configurable TTL

---

## Tech Stack

| Layer | Technology |
|-------|-----------|
| **UI** | JavaFX 21.0.2, FXML, CSS |
| **Language** | Java 17 |
| **Build** | Maven, javafx-maven-plugin, maven-shade-plugin |
| **Database** | MySQL 8.0 (HikariCP connection pool) |
| **Backend API** | FastAPI (Python), Docker |
| **Auth** | BCrypt, Face Recognition (MediaPipe), CAPTCHA |
| **AI** | Z.AI (GLM-5), Groq (Whisper + Llama), OpenRouter, Gemini |
| **Real-time** | WebSocket (audio/video/signaling) |
| **PDF** | Apache PDFBox, iText-style generation |
| **Documents** | Apache POI (Excel), PDFBox (PDF extraction) |
| **Email** | JavaMail (SMTP) |
| **Automation** | n8n (webhooks, scheduled workflows) |
| **Infra** | Docker, Nginx Proxy Manager, Let's Encrypt SSL, CoTURN |
| **Monitoring** | Grafana, Prometheus |
| **Python** | Face recognition service, HR assistant AI |

---

## Getting Started

### Prerequisites

- **JDK 17+** (tested with JDK 25)
- **Maven 3.8+**
- **MySQL 8.0** (local or remote)
- **Git**

### Clone & Build

```bash
git clone https://github.com/bouallegueMohamedSeji/synergygig.git
cd synergygig
```

Copy the example config and fill in your credentials:

```bash
cp config.properties.example config.properties
# Edit config.properties with your settings
```

Build and run:

```bash
mvn clean compile
mvn javafx:run
```

Build a fat JAR:

```bash
mvn clean package -DskipTests
java -jar target/SynergyGig-1.0-SNAPSHOT.jar
```

---

## Configuration

Create a `config.properties` file in the project root with these keys:

```properties
# App mode: "api" (remote REST API) or "local" (direct JDBC)
app.mode=api

# Database (used in local mode or as JDBC fallback)
db.url=jdbc:mysql://localhost:3306/synergygig
db.user=root
db.password=yourpassword

# REST API
rest.base_url=https://your-server.com/api

# AI Providers (at least one required for AI features)
zai.api.key=your_zai_key
groq.api.key=your_groq_key
openrouter.api.key=your_openrouter_key
opencode.api.key=your_opencode_key
gemini.api_key=your_gemini_key

# Email (Gmail App Password for OTP/verification)
smtp.email=your-email@gmail.com
smtp.password=your_app_password

# Content Moderation
badwords.api_key=your_badwords_key

# n8n Automation (optional)
n8n.base_url=http://your-server:5678
n8n.webhook_url=https://your-n8n-domain.com

# Server (for SSH tunnel, optional)
server.host=your.server.ip
server.ssh_user=username

# AI Service
ai.base_url=https://your-ai-service.com
```

---

## Project Structure

```
SynergyGig/
├── pom.xml                          # Maven build config
├── config.properties                # App configuration (gitignored)
├── config.properties.example        # Config template
├── README.md
│
├── src/main/java/
│   ├── mains/
│   │   ├── MainFX.java             # JavaFX Application entry
│   │   └── Launcher.java           # Fat JAR launcher
│   │
│   ├── controllers/                 # 32 FXML controllers
│   │   ├── ChatController.java      # Messaging, calls, video, CC
│   │   ├── DashboardController.java # Main dashboard
│   │   ├── JobScannerController.java# Job scanning + skills search
│   │   ├── TrainingController.java  # Courses & certificates
│   │   ├── HRModuleController.java  # Leave, attendance, payroll
│   │   └── ...
│   │
│   ├── entities/                    # 25 data model classes
│   │   ├── User.java
│   │   ├── ChatRoom.java
│   │   ├── TrainingCertificate.java
│   │   └── ...
│   │
│   ├── services/                    # 29 service classes (API + JDBC)
│   │   ├── ServiceUser.java
│   │   ├── ZAIService.java          # Multi-provider AI engine
│   │   └── ...
│   │
│   └── utils/                       # 37 utility classes
│       ├── ApiClient.java           # REST API client
│       ├── AudioCallService.java    # Voice call engine
│       ├── GroqWhisperService.java  # Speech-to-text
│       ├── LiveTranscriptionManager.java # CC subtitle manager
│       ├── InMemoryCache.java       # TTL cache
│       ├── FaceRecognitionUtil.java # Face ID auth
│       └── ...
│
├── src/main/resources/
│   ├── css/
│   │   ├── style.css                # Dark theme (6200+ lines)
│   │   └── light-theme.css          # Light theme (3100+ lines)
│   ├── fxml/                        # 29 view files
│   ├── images/                      # Icons & assets
│   ├── sounds/                      # UI sound effects
│   └── videos/                      # Intro/splash videos
│
├── python/                          # Python AI subsystem
│   ├── face_recognition_service.py  # Face ID REST service
│   └── hr_assistant.py              # AI assistant
│
└── docs/                            # Documentation
    └── Module1_Documentation.md
```

---

## Modules

| Module | Description | Key Files |
|--------|-------------|-----------|
| **Auth** | Login, signup, Face ID, OTP, CAPTCHA | `LoginController`, `SignupController` |
| **Chat** | Real-time messaging, AI assistant | `ChatController`, `Chat.fxml` |
| **Calls** | Audio/video calls, screen share, CC | `AudioCallService`, `GroqWhisperService` |
| **HR** | Leave, attendance, payroll management | `HRModuleController`, `HRBacklogController` |
| **Projects** | Project & task management | `ProjectManagementController` |
| **Jobs** | Offers, contracts, job scanner | `OfferContractController`, `JobScannerController` |
| **Training** | Courses, enrollments, certificates | `TrainingController`, `TrainingCertificatePdf` |
| **Community** | Groups, posts, reactions, friends | `CommunityController` |
| **AI Tools** | Interview prep, meeting summary, OCR, email | Various AI controllers |
| **Settings** | Theme, voice, dark mode scheduler | `SettingsController` |

---

## Contributors

| Name | GitHub |
|------|--------|
| **Seji** (Mohamed Seji Bouallegue) | [@bouallegueMohamedSeji](https://github.com/bouallegueMohamedSeji) |
| **Anas** | |
| **Med_Anas** | |
| **Youssef** | |
| **Syrine** | |
| **Amine** | |

---

## Academic Context

| | |
|---|---|
| **Institution** | Esprit School of Engineering — École Supérieure Privée d'Ingénierie et de Technologies, Tunisia |
| **Project Type** | PIDEV — Projet Intégré de Développement |
| **Class** | 3A16 |
| **Academic Year** | 2025–2026 |
| **Coaches** | **Sana Fayechi** · **Fakhreddine GHALLEB** (Enseignant) |

---

## Acknowledgments

We would like to thank our coaches **Sana Fayechi** and **Fakhreddine GHALLEB** for their guidance and support throughout this project, and **Esprit School of Engineering** for providing the academic framework.

---

## License

This project is proprietary and developed as part of the **PIDEV** academic curriculum at **Esprit School of Engineering**, Tunisia.
