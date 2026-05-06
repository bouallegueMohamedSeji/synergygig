<?php

namespace App\Form;

use App\Entity\Department;
use App\Entity\Offer;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\NumberType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\Extension\Core\Type\TextType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\Form\FormError;
use Symfony\Component\Form\FormEvent;
use Symfony\Component\Form\FormEvents;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class OfferType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $isNew = !$options['data'] || !$options['data']->getId();

        $builder
            ->add('title', TextType::class, [
                'constraints' => [new Assert\NotBlank(), new Assert\Length(min: 2, max: 200)],
                'attr' => ['class' => 'form-control', 'placeholder' => 'Job title or offer name'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('description', TextareaType::class, [
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please provide a description.']),
                    new Assert\Length(['min' => 10, 'minMessage' => 'Description must be at least {{ limit }} characters.']),
                ],
                'attr' => ['class' => 'form-control', 'rows' => 4, 'placeholder' => 'Describe the offer...'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('offer_type', ChoiceType::class, [
                'choices' => [
                    'Full Time' => 'FULL_TIME',
                    'Part Time' => 'PART_TIME',
                    'Freelance' => 'FREELANCE',
                    'Internship' => 'INTERNSHIP',
                    'Contract' => 'CONTRACT',
                ],
                'constraints' => [new Assert\NotBlank(message: 'Please select a type.')],
                'label' => 'Type',
                'placeholder' => 'Select type',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('department', EntityType::class, [
                'class' => Department::class,
                'choice_label' => 'name',
                'placeholder' => 'Select department',
                'constraints' => [new Assert\NotBlank(['message' => 'Please select a department.'])],
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('required_skills', TextareaType::class, [
                'label' => 'Required Skills',
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please list the required skills.']),
                ],
                'attr' => ['class' => 'form-control', 'rows' => 2, 'placeholder' => 'PHP, Symfony, MySQL...'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('location', TextType::class, [
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please specify a location.']),
                ],
                'attr' => ['class' => 'form-control', 'placeholder' => 'Remote / Paris / Tunis...'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('amount', NumberType::class, [
                'label' => 'Salary / Budget',
                'attr' => ['class' => 'form-control', 'placeholder' => '0.00'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please enter a salary or budget.']),
                    new Assert\PositiveOrZero(['message' => 'Amount must be zero or a positive number.']),
                ],
            ])
            ->add('currency', ChoiceType::class, [
                'choices' => ['USD' => 'USD', 'EUR' => 'EUR', 'TND' => 'TND', 'GBP' => 'GBP'],
                'placeholder' => 'Select currency',
                'constraints' => [new Assert\NotBlank(['message' => 'Please select a currency.'])],
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('start_date', DateType::class, [
                'widget' => 'single_text',
                'label' => 'Start Date',
                'attr' => ['class' => 'form-control'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => array_filter([
                    new Assert\NotBlank(['message' => 'Please select a start date.']),
                    $isNew ? new Assert\GreaterThanOrEqual(['value' => 'today', 'message' => 'Start date cannot be in the past.']) : null,
                ]),
            ])
            ->add('end_date', DateType::class, [
                'widget' => 'single_text',
                'label' => 'End Date',
                'attr' => ['class' => 'form-control'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select an end date.']),
                ],
            ])
        ;

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
        $resolver->setDefaults(['data_class' => Offer::class]);
    }
}
