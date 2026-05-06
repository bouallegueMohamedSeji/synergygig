<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

final class Version20260423000001 extends AbstractMigration
{
    public function getDescription(): string
    {
        return 'Add GitHub Issues fields to tasks and projects tables';
    }

    public function up(Schema $schema): void
    {
        $taskTable = $schema->getTable('tasks');
        if (!$taskTable->hasColumn('github_issue_number')) {
            $this->addSql('ALTER TABLE tasks ADD github_issue_number INT DEFAULT NULL');
        }
        if (!$taskTable->hasColumn('github_issue_url')) {
            $this->addSql('ALTER TABLE tasks ADD github_issue_url VARCHAR(512) DEFAULT NULL');
        }

        $projectTable = $schema->getTable('projects');
        if (!$projectTable->hasColumn('github_repo')) {
            $this->addSql('ALTER TABLE projects ADD github_repo VARCHAR(255) DEFAULT NULL');
        }
    }

    public function down(Schema $schema): void
    {
        $taskTable = $schema->getTable('tasks');
        if ($taskTable->hasColumn('github_issue_number')) {
            $this->addSql('ALTER TABLE tasks DROP github_issue_number');
        }
        if ($taskTable->hasColumn('github_issue_url')) {
            $this->addSql('ALTER TABLE tasks DROP github_issue_url');
        }

        $projectTable = $schema->getTable('projects');
        if ($projectTable->hasColumn('github_repo')) {
            $this->addSql('ALTER TABLE projects DROP github_repo');
        }
    }
}
