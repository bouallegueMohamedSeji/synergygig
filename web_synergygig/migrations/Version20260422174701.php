<?php

declare(strict_types=1);

namespace DoctrineMigrations;

use Doctrine\DBAL\Schema\Schema;
use Doctrine\Migrations\AbstractMigration;

/**
 * Auto-generated Migration: Please modify to your needs!
 */
final class Version20260422174701 extends AbstractMigration
{
    public function getDescription(): string
    {
        return '';
    }

    public function up(Schema $schema): void
    {
        if (!$schema->getTable('user')->hasColumn('reset_token')) {
            $this->addSql('ALTER TABLE user ADD reset_token VARCHAR(255) DEFAULT NULL');
        }

        if (!$schema->getTable('user')->hasColumn('reset_token_expires_at')) {
            $this->addSql('ALTER TABLE user ADD reset_token_expires_at DATETIME DEFAULT NULL');
        }
    }

    public function down(Schema $schema): void
    {
        if ($schema->getTable('user')->hasColumn('reset_token')) {
            $this->addSql('ALTER TABLE user DROP reset_token');
        }

        if ($schema->getTable('user')->hasColumn('reset_token_expires_at')) {
            $this->addSql('ALTER TABLE user DROP reset_token_expires_at');
        }
    }
}
