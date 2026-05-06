<?php

namespace App\Form;

use App\Entity\Department;
use App\Entity\User;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\EmailType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\Extension\Core\Type\PasswordType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class UserType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('first_name', TextType::class, [
                'constraints' => [
                    new Assert\NotBlank(),
                    new Assert\Length(min: 2, max: 50),
                ],
                'label' => 'First Name',
                'attr' => ['class' => 'form-control', 'placeholder' => 'John'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('last_name', TextType::class, [
                'constraints' => [
                    new Assert\NotBlank(),
                    new Assert\Length(min: 2, max: 50),
                ],
                'label' => 'Last Name',
                'attr' => ['class' => 'form-control', 'placeholder' => 'Doe'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('email', EmailType::class, [
                'constraints' => [
                    new Assert\NotBlank(),
                    new Assert\Email(),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => 'john@company.com'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('bio', TextareaType::class, [
                'required' => false,
                'attr' => ['class' => 'form-control', 'rows' => 3, 'placeholder' => 'Short bio...'],
                'label_attr' => ['class' => 'form-label'],
            ]);

        if ($options['show_admin_fields']) {
            $builder
                ->add('role', ChoiceType::class, [
                    'choices' => [
                        'Employee' => 'EMPLOYEE',
                        'Project Owner' => 'PROJECT_OWNER',
                        'HR Manager' => 'HR_MANAGER',
                        'Gig Worker' => 'GIG_WORKER',
                        'Admin' => 'ADMIN',
                    ],
                    'required' => false,
                    'placeholder' => 'Select role',
                    'attr' => ['class' => 'form-control form-select'],
                    'label_attr' => ['class' => 'form-label'],
                ])
                ->add('department', EntityType::class, [
                    'class' => Department::class,
                    'choice_label' => 'name',
                    'required' => false,
                    'placeholder' => 'Select department',
                    'attr' => ['class' => 'form-control form-select'],
                    'label_attr' => ['class' => 'form-label'],
                ])
                ->add('monthly_salary', NumberType::class, [
                    'required' => false,
                    'label' => 'Monthly Salary ($)',
                    'constraints' => [
                        new Assert\PositiveOrZero(),
                    ],
                    'attr' => ['class' => 'form-control', 'placeholder' => '0.00'],
                    'label_attr' => ['class' => 'form-label'],
                ])
                ->add('hourly_rate', NumberType::class, [
                    'required' => false,
                    'label' => 'Hourly Rate ($)',
                    'constraints' => [
                        new Assert\PositiveOrZero(),
                    ],
                    'attr' => ['class' => 'form-control', 'placeholder' => '0.00'],
                    'label_attr' => ['class' => 'form-label'],
                ]);
        }
        if ($options['is_new']) {
            $builder->add('password', PasswordType::class, [
                'constraints' => [
                    new Assert\NotBlank(),
                    new Assert\Length(min: 8, minMessage: 'Password must be at least {{ limit }} characters.'),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => '••••••••'],
                'label_attr' => ['class' => 'form-label'],
            ]);
        }
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => User::class,
            'is_new' => false,
            'show_admin_fields' => true,
        ]);
    }
}
