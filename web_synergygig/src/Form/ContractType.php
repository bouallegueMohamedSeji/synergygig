<?php

namespace App\Form;

use App\Entity\Contract;
use App\Entity\Offer;
use App\Entity\User;
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

class ContractType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('offer', EntityType::class, [
                'class' => Offer::class,
                'choice_label' => 'title',
                'placeholder' => 'Select offer',
                'required' => false,
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('applicant', EntityType::class, [
                'class' => User::class,
                'choice_label' => function (User $u) {
                    return $u->getFirstName() . ' ' . $u->getLastName();
                },
                'placeholder' => 'Select applicant',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select an applicant.']),
                ],
            ])
            ->add('owner', EntityType::class, [
                'class' => User::class,
                'choice_label' => function (User $u) {
                    return $u->getFirstName() . ' ' . $u->getLastName();
                },
                'placeholder' => 'Select owner',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select a contract owner.']),
                ],
            ])
            ->add('amount', NumberType::class, [
                'required' => false,
                'scale' => 2,
                'attr' => ['class' => 'form-control', 'step' => '0.01'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\PositiveOrZero(['message' => 'Amount must be zero or a positive number.']),
                ],
            ])
            ->add('currency', ChoiceType::class, [
                'choices' => ['USD' => 'USD', 'EUR' => 'EUR', 'TND' => 'TND', 'GBP' => 'GBP'],
                'required' => false,
                'placeholder' => 'Select currency',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('status', ChoiceType::class, [
                'choices' => [
                    'Draft' => 'DRAFT',
                    'Pending Review' => 'PENDING_REVIEW',
                    'Counter Proposed' => 'COUNTER_PROPOSED',
                    'Pending Signature' => 'PENDING_SIGNATURE',
                    'Active' => 'ACTIVE',
                    'Completed' => 'COMPLETED',
                    'Terminated' => 'TERMINATED',
                    'Disputed' => 'DISPUTED',
                ],
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select a contract status.']),
                ],
            ])
            ->add('start_date', DateType::class, [
                'widget' => 'single_text',
                'required' => false,
                'attr' => ['class' => 'form-control'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('end_date', DateType::class, [
                'widget' => 'single_text',
                'required' => false,
                'attr' => ['class' => 'form-control'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('terms', TextareaType::class, [
                'required' => false,
                'attr' => ['class' => 'form-control', 'rows' => '5'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\Length(['max' => 5000, 'maxMessage' => 'Terms must not exceed {{ limit }} characters.']),
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
        $resolver->setDefaults(['data_class' => Contract::class]);
    }
}
