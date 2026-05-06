<?php

namespace App\Form;

use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\EmailType;
use Symfony\Component\Form\Extension\Core\Type\PasswordType;
use Symfony\Component\Form\Extension\Core\Type\RepeatedType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class RegistrationType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('first_name', TextType::class, [
                'constraints' => [
                    new Assert\NotBlank(['message' => 'First name is required.']),
                    new Assert\Length([
                        'min' => 2, 'minMessage' => 'First name must be at least {{ limit }} characters.',
                        'max' => 50, 'maxMessage' => 'First name cannot exceed {{ limit }} characters.',
                    ]),
                    new Assert\Regex([
                        'pattern' => '/^[\pL\s\-]+$/u',
                        'message' => 'First name can only contain letters, spaces and hyphens.',
                    ]),
                ],
                'label' => 'First Name',
                'attr' => ['class' => 'form-control', 'placeholder' => 'John', 'maxlength' => 50],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('last_name', TextType::class, [
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Last name is required.']),
                    new Assert\Length([
                        'min' => 2, 'minMessage' => 'Last name must be at least {{ limit }} characters.',
                        'max' => 50, 'maxMessage' => 'Last name cannot exceed {{ limit }} characters.',
                    ]),
                    new Assert\Regex([
                        'pattern' => '/^[\pL\s\-]+$/u',
                        'message' => 'Last name can only contain letters, spaces and hyphens.',
                    ]),
                ],
                'label' => 'Last Name',
                'attr' => ['class' => 'form-control', 'placeholder' => 'Doe', 'maxlength' => 50],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('email', EmailType::class, [
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Email is required.']),
                    new Assert\Email(['message' => 'Please enter a valid email address.']),
                    new Assert\Length(['max' => 180, 'maxMessage' => 'Email cannot exceed {{ limit }} characters.']),
                ],
                'label' => 'Work Email',
                'attr' => ['class' => 'form-control', 'placeholder' => 'name@company.com', 'autocomplete' => 'email'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('role', ChoiceType::class, [
                'choices' => [
                    'Employee' => 'EMPLOYEE',
                    'Gig Worker' => 'GIG_WORKER',
                ],
                'expanded' => true,
                'data' => 'EMPLOYEE',
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select a role.']),
                ],
                'label' => 'I want to join as',
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('password', RepeatedType::class, [
                'type' => PasswordType::class,
                'invalid_message' => 'The password fields must match.',
                'first_options' => [
                    'label' => 'Password',
                    'attr' => ['class' => 'form-control', 'placeholder' => '••••••••', 'autocomplete' => 'new-password'],
                    'label_attr' => ['class' => 'form-label'],
                ],
                'second_options' => [
                    'label' => 'Confirm Password',
                    'attr' => ['class' => 'form-control', 'placeholder' => '••••••••', 'autocomplete' => 'new-password'],
                    'label_attr' => ['class' => 'form-label'],
                    'constraints' => [
                        new Assert\NotBlank(['message' => 'Please confirm your password.']),
                    ],
                ],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Password is required.']),
                    new Assert\Length([
                        'min' => 8, 'minMessage' => 'Password must be at least {{ limit }} characters.',
                        'max' => 128, 'maxMessage' => 'Password cannot exceed {{ limit }} characters.',
                    ]),
                    new Assert\Regex([
                        'pattern' => '/[A-Z]/',
                        'message' => 'Password must contain at least one uppercase letter.',
                    ]),
                    new Assert\Regex([
                        'pattern' => '/[a-z]/',
                        'message' => 'Password must contain at least one lowercase letter.',
                    ]),
                    new Assert\Regex([
                        'pattern' => '/\d/',
                        'message' => 'Password must contain at least one digit.',
                    ]),
                ],
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([]);
    }
}
