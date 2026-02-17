PROJECT: SynergyGig – Smart Gig & HR Platform

GOAL:
A modular HR + Freelance management system for Tunisian SMEs and gig workers.

ARCHITECTURE:
Layered architecture using Java:

Entity → Repository → Service → Controller → UI

Each module must follow this structure.

GLOBAL USER ROLES:
- Admin
- HR Manager
- Employee
- Project Owner (extends Employee)
- Gig Worker

CORE MODULES:

1. User & Interview Communication
Entities: User, Message, ChatRoom, Interview
Purpose: messaging + interview scheduling

2. Recruitment
Entities: Offer, Application, Contract
Purpose: Hiring and gig recruitment workflow



3. HR Administration
Entities: Department, Attendance, Leave, Payroll
Purpose: employee lifecycle management

4. Project & Performance
Entities: Project, Task, KPI
Purpose: productivity tracking

5. Skills & Training
Entities: Course, Resource, Quiz
Purpose: onboarding and skill development

6. Culture & Community
Entities: Event, Feedback, Post, Comment
Purpose: engagement and internal community

RULES:
- Each module is independent
- Services contain business logic
- Controllers expose API
- Entities must use relationships (OneToMany, ManyToOne)
- User entity is central to the whole system