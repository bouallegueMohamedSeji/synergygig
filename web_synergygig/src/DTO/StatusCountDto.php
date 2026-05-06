<?php

namespace App\DTO;

class StatusCountDto
{
    public function __construct(
        public ?string $label,
        public int $total
    ) {
    }
}
