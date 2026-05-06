<?php

namespace App\Form;

use App\Entity\TrainingCourse;
use App\Entity\User;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\IntegerType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\Form\FormError;
use Symfony\Component\Form\FormEvent;
use Symfony\Component\Form\FormEvents;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class TrainingCourseType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('title', TextType::class, [
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Title is required.']),
                    new Assert\Length(['min' => 2, 'minMessage' => 'Title must be at least {{ limit }} characters.', 'max' => 200, 'maxMessage' => 'Title cannot exceed {{ limit }} characters.']),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => 'Course title'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('description', TextareaType::class, [
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Description is required.']),
                    new Assert\Length(['min' => 10, 'minMessage' => 'Description must be at least {{ limit }} characters.', 'max' => 5000]),
                ],
                'attr' => ['class' => 'form-control', 'rows' => 3, 'placeholder' => 'Course description...'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('category', ChoiceType::class, [
                'choices' => [
                    'Technical' => 'TECHNICAL',
                    'Soft Skills' => 'SOFT_SKILLS',
                    'Compliance' => 'COMPLIANCE',
                    'Onboarding' => 'ONBOARDING',
                    'Leadership' => 'LEADERSHIP',
                ],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select a category.']),
                ],
                'placeholder' => 'Select category',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('difficulty', ChoiceType::class, [
                'choices' => [
                    'Beginner' => 'BEGINNER',
                    'Intermediate' => 'INTERMEDIATE',
                    'Advanced' => 'ADVANCED',
                ],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select a difficulty level.']),
                ],
                'placeholder' => 'Select difficulty',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('duration_hours', NumberType::class, [
                'label' => 'Duration (hours)',
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Duration is required.']),
                    new Assert\Positive(['message' => 'Duration must be a positive number.']),
                    new Assert\LessThanOrEqual(['value' => 10000, 'message' => 'Duration cannot exceed {{ compared_value }} hours.']),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => '0', 'min' => '1'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('instructor_name', TextType::class, [
                'label' => 'Instructor',
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Instructor name is required.']),
                    new Assert\Length(['min' => 2, 'minMessage' => 'Instructor name must be at least {{ limit }} characters.', 'max' => 100]),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => 'Instructor name'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('max_participants', IntegerType::class, [
                'label' => 'Max Participants',
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Max participants is required.']),
                    new Assert\Positive(['message' => 'Max participants must be a positive number.']),
                    new Assert\LessThanOrEqual(['value' => 10000, 'message' => 'Max participants cannot exceed {{ compared_value }}.']),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => '30', 'min' => '1'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('start_date', DateType::class, [
                'widget' => 'single_text',
                'label' => 'Start Date',
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Start date is required.']),
                    new Assert\GreaterThanOrEqual(['value' => 'today', 'message' => 'Start date cannot be in the past.']),
                ],
                'attr' => ['class' => 'form-control'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('end_date', DateType::class, [
                'widget' => 'single_text',
                'label' => 'End Date',
                'constraints' => [
                    new Assert\NotBlank(['message' => 'End date is required.']),
                    new Assert\GreaterThanOrEqual(['value' => 'today', 'message' => 'End date cannot be in the past.']),
                ],
                'attr' => ['class' => 'form-control'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('status', ChoiceType::class, [
                'choices' => [
                    'Draft' => 'DRAFT',
                    'Active' => 'ACTIVE',
                    'Archived' => 'ARCHIVED',
                ],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select a status.']),
                ],
                'placeholder' => 'Select status',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ]);

        $builder->addEventListener(FormEvents::POST_SUBMIT, function (FormEvent $event) {
            $form = $event->getForm();
            $startDate = $form->get('start_date')->getData();
            $endDate = $form->get('end_date')->getData();
            if ($startDate && $endDate && $endDate < $startDate) {
                $form->get('end_date')->addError(new FormError('End date must be after or equal to start date.'));
            }
        });
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults(['data_class' => TrainingCourse::class]);
    }
}
