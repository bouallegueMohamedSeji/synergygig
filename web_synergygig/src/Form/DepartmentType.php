<?php

namespace App\Form;

use App\Entity\Department;
use App\Entity\User;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class DepartmentType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('name', TextType::class, [
                'constraints' => [
                    new Assert\NotBlank(message: 'Department name is required.'),
                    new Assert\Length(min: 2, max: 100),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => 'e.g. Engineering'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('description', TextareaType::class, [
                'required' => false,
                'constraints' => [
                    new Assert\Length(max: 500),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => 'Brief description of the department', 'rows' => 3],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('manager', EntityType::class, [
                'class' => User::class,
                'choice_label' => function (User $user) {
                    return $user->getFirst_name() . ' ' . $user->getLast_name();
                },
                'required' => true,
                'placeholder' => 'Select a manager',
                'constraints' => [new Assert\NotBlank(message: 'Each department must have a manager.')],
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('allocated_budget', NumberType::class, [
                'required' => false,
                'constraints' => [
                    new Assert\PositiveOrZero(message: 'Budget must be a positive number.'),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => '0.00'],
                'label' => 'Budget ($)',
                'label_attr' => ['class' => 'form-label'],
            ]);
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults([
            'data_class' => Department::class,
        ]);
    }
}
