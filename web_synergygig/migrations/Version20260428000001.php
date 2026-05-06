<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260428000001 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add CV upload fields to user table';
    }

    public function up(Schema $schema): void
    {
        $this->addSql('ALTER TABLE user ADD cv_path VARCHAR(255) DEFAULT NULL, ADD cv_original_name VARCHAR(255) DEFAULT NULL, ADD cv_uploaded_at DATETIME DEFAULT NULL, ADD cv_skills_text LONGTEXT DEFAULT NULL');
    }

    public function down(Schema $schema): void
    {
        $this->addSql('ALTER TABLE user DROP cv_path, DROP cv_original_name, DROP cv_uploaded_at, DROP cv_skills_text');
    }
}
