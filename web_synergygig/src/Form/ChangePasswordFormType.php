<?php

namespace App\Form;

use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\PasswordType;
use Symfony\Component\Form\Extension\Core\Type\RepeatedType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints\Length;
use Symfony\Component\Validator\Constraints\NotBlank;

class ChangePasswordFormType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('plainPassword', RepeatedType::class, [
                'type'            => PasswordType::class,
                'first_options'   => [
                    'label'       => 'New password',
                    'constraints' => [
                        new NotBlank(['message' => 'Please enter a password.']),
                        new Length(['min' => 8, 'minMessage' => 'Password must be at least {{ limit }} characters.']),
                    ],
                    'attr' => ['class' => 'form-control', 'autocomplete' => 'new-password'],
                ],
                'second_options'  => [
                    'label' => 'Repeat password',
                    'attr'  => ['class' => 'form-control', 'autocomplete' => 'new-password'],
                ],
                'invalid_message' => 'The password fields must match.',
                'mapped'          => false,
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([]);
    }
}
