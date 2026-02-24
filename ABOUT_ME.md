# 👨‍💻 About Me - Anas Chagour

## 🎯 Who Am I?

**Anas Chagour** is a passionate software developer and the **Founder & CEO of SynergyGig**, an innovative freelance contract management platform. Based in **Tunisia** (Africa/Tunis timezone), Anas combines technical expertise with entrepreneurial vision to build solutions that bridge the gap between freelancers and clients.

---

## 🚀 My Project: SynergyGig

### Overview
SynergyGig is a comprehensive **JavaFX desktop application** designed to revolutionize how freelance contracts are created, managed, and verified. The platform leverages cutting-edge technologies including **Artificial Intelligence** and **Blockchain** concepts to ensure secure, transparent, and professional contract management.

### 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                    SYNERGYGIG ARCHITECTURE                   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   JavaFX    │  │  AtlantaFX  │  │    CSS      │  UI     │
│  │   Controls  │  │   Theme     │  │  Styling    │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ Controllers │  │  Services   │  │    DAOs     │  LOGIC  │
│  │   (FXML)    │  │ (Business)  │  │  (Data)     │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │   MySQL     │  │   Ollama    │  │  External   │  DATA   │
│  │  Database   │  │   (LLaMA3)  │  │   APIs      │         │
│  └─────────────┘  └─────────────┘  └─────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

---

## ✨ Key Features

### 1. 📋 Offer Management
- Create, edit, publish, and delete job offers
- Support for **GIG** and **INTERNAL** offer types
- Status workflow: `DRAFT → PUBLISHED → IN_PROGRESS → COMPLETED/CANCELLED`
- Image upload and preview functionality

### 2. 📝 Application System
- Freelancers can apply to published offers
- Application status tracking: `PENDING → ACCEPTED → REJECTED`
- Link applications to contract generation

### 3. 🤖 AI-Powered Contract Generation
- **Ollama Integration** with LLaMA3 model
- Automatic contract summarization
- Contract improvement suggestions
- Professional legal contract generation

### 4. 🔐 Blockchain Verification
- SHA-256 hash generation for contract integrity
- QR code generation for easy verification
- Immutable audit trail

### 5. 📄 PDF Contract Generation
- Professional PDF documents with iText7
- Company logo and branding
- Digital signature and stamp
- QR code for blockchain verification



### 7. 📧 Email Service
- Professional HTML email templates
- PDF contract attachments
- Elastic Email SMTP integration

### 8. 📊 Interactive Dashboard
- Real-time statistics with animated counters
- Pie charts and bar charts for data visualization
- Platform health indicator
- News feed integration (NewsAPI)
- Weather widget (OpenWeatherMap)

### 9. 🎨 Modern UI/UX
- Animated splash screen with music
- Smooth transitions and fade effects
- Responsive design with AtlantaFX theme

---

## 🛠️ Technology Stack

| Category | Technologies |
|----------|-------------|
| **Language** | Java 17 |
| **Framework** | JavaFX 21 |
| **Theme** | AtlantaFX (PrimerLight) |
| **Database** | MySQL 8.0 |
| **Build Tool** | Maven |
| **PDF Generation** | iText7 |
| **QR Codes** | ZXing |
| **Payments** | Stripe Java SDK |
| **AI/ML** | Ollama (LLaMA3), HuggingFace |
| **Email** | Jakarta Mail, Elastic Email |
| **APIs** | NewsAPI, OpenWeatherMap |
| **Testing** | JUnit 5 |

---

## 📁 Project Structure

```
employee_contratt/
├── pom.xml                          # Maven configuration
├── contracts/                       # Generated PDF contracts
├── uploads/                         # User uploaded images
└── src/
    ├── main/
    │   ├── java/tn/esprit/synergygig/
    │   │   ├── main/
    │   │   │   └── MainApp.java     # Application entry point
    │   │   ├── controllers/         # FXML controllers
    │   │   │   ├── DashboardController.java
    │   │   │   ├── OfferController.java
    │   │   │   ├── ContractsAdminController.java
    │   │   │   ├── ApplicationsAdminController.java
    │   │   │   ├── ClientOfferController.java
    │   │   │   ├── GigOffersController.java
    │   │   │   ├── EditOfferController.java
    │   │   │   ├── MainLayoutController.java
    │   │   │   ├── SidebarController.java
    │   │   │   ├── SplashController.java
    │   │   │   └── VerifyContractController.java
    │   │   ├── entities/            # Data models
    │   │   │   ├── User.java
    │   │   │   ├── Offer.java
    │   │   │   ├── Contract.java
    │   │   │   ├── Application.java
    │   │   │   ├── Milestone.java
    │   │   │   ├── NewsArticles.java
    │   │   │   └── enums/
    │   │   ├── dao/                 # Data Access Objects
    │   │   │   ├── OfferDAO.java
    │   │   │   ├── ContractDAO.java
    │   │   │   ├── ApplicationDAO.java
    │   │   │   ├── MilestoneDAO.java
    │   │   │   └── UserDAO.java
    │   │   ├── services/            # Business logic
    │   │   │   ├── OfferService.java
    │   │   │   ├── ContractService.java
    │   │   │   ├── ContractPDFService.java
    │   │   │   ├── ApplicationService.java
    │   │   │   ├── OllamaService.java
    │   │   │   ├── AiRiskService.java
    │   │   │   ├── BlockchainService.java
    │   │   │   ├── PaymentService.java
    │   │   │   ├── EmailService.java
    │   │   │   ├── NewsService.java
    │   │   │   ├── WeatherService.java
    │   │   │   └── DashboardService.java
    │   │   └── utils/
    │   │       └── MyDBConnexion.java
    │   └── resources/
    │       ├── images/
    │       └── tn/esprit/synergygig/gui/
    │           ├── *.fxml           # UI layouts
    │           ├── app.css          # Main styles
    │           ├── dashboard.css    # Dashboard styles
    │           ├── images/          # UI images
    │           └── Sounds/          # Audio files
    └── test/                        # Unit tests
```

---

## 🎯 Skills Demonstrated

### Technical Skills
- ✅ **Object-Oriented Programming** - Clean entity design with proper encapsulation
- ✅ **Design Patterns** - Singleton (DB connection), DAO pattern, MVC architecture
- ✅ **Database Design** - MySQL with proper relationships
- ✅ **API Integration** - REST APIs (NewsAPI, OpenWeatherMap, HuggingFace)
- ✅ **AI Integration** - Ollama/LLaMA3 for contract generation
- ✅ **Cryptography** - SHA-256 hashing for blockchain verification
- ✅ **PDF Generation** - Professional document creation with iText7
- ✅ **Payment Processing** - Stripe integration
- ✅ **Email Services** - SMTP with HTML templates

### Soft Skills
- 🎨 **UI/UX Design** - Modern, animated user interfaces
- 📊 **Data Visualization** - Charts and dashboards
- 🔄 **Project Management** - Well-organized code structure
- 🚀 **Entrepreneurship** - Building a complete product from scratch

---

## 📞 Contact Information

| Field | Value |
|-------|-------|
| **Name** | Anas Chagour |
| **Email** | anas.chagour12@gmail.com |
| **Role** | Founder & CEO, SynergyGig |
| **Location** | Tunisia |
| **Timezone** | Africa/Tunis (UTC+1) |

---

## 🏆 Project Highlights

1. **Full-Stack Development**: Complete desktop application from database to UI
2. **AI Integration**: Leveraging LLaMA3 for intelligent contract generation
3. **Security**: Blockchain-inspired verification system
4. **Professional Output**: PDF contracts with signatures and stamps
5. **Modern UI**: Animated, galaxy-themed user experience
6. **Real-world APIs**: Integration with multiple external services

---

## 🚀 How to Run

```bash
# Clone the repository
git clone <repository-url>

# Navigate to project directory
cd employee_contratt

# Build with Maven
mvn clean install

# Run the application
mvn javafx:run
```

### Prerequisites
- Java 17+
- MySQL 8.0+
- Maven 3.6+
- Ollama (optional, for AI features)

---

## 📝 Database Setup

```sql
CREATE DATABASE synergygig_db;

-- Tables will be created based on entity structure
-- Users, Offers, Applications, Contracts, Milestones
```

---

> *"Building the future of freelance contract management, one line of code at a time."*
> 
> — **Anas Chagour**, Founder & CEO, SynergyGig

---

*Document generated on February 22, 2026*
