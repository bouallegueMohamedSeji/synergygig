<?php

namespace App\Form;

use App\Entity\Leave;
use App\Entity\User;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\DateType;
use Symfony\Component\Form\Extension\Core\Type\FileType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\Form\FormError;
use Symfony\Component\Form\FormEvent;
use Symfony\Component\Form\FormEvents;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class LeaveType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        // Only HR can pick the employee; employees auto-assigned in controller
        if ($options['is_hr']) {
            $builder->add('user', EntityType::class, [
                'class' => User::class,
                'choice_label' => function (User $u) {
                    return $u->getFirstName() . ' ' . $u->getLastName();
                },
                'placeholder' => 'Select employee',
                'constraints' => [new Assert\NotBlank(message: 'Please select an employee.')],
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ]);
        }

        $builder
            ->add('type', ChoiceType::class, [
                'choices' => [
                    'Sick Leave' => 'SICK',
                    'Vacation' => 'VACATION',
                    'Personal Leave' => 'PERSONAL',
                    'Maternity' => 'MATERNITY',
                    'Paternity' => 'PATERNITY',
                    'Unpaid' => 'UNPAID',
                ],
                'constraints' => [new Assert\NotBlank(message: 'Please select a leave type.')],
                'placeholder' => 'Select type',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('start_date', DateType::class, [
                'widget' => 'single_text',
                'label' => 'Start Date',
                'constraints' => [new Assert\NotBlank(message: 'Start date is required.')],
                'attr' => ['class' => 'form-control'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('end_date', DateType::class, [
                'widget' => 'single_text',
                'label' => 'End Date',
                'constraints' => [new Assert\NotBlank(message: 'End date is required.')],
                'attr' => ['class' => 'form-control'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('reason', TextareaType::class, [
                'required' => true,
                'constraints' => [
                    new Assert\NotBlank(message: 'Please provide a reason for your leave.'),
                    new Assert\Length(min: 5, max: 2000, minMessage: 'Reason must be at least {{ limit }} characters.', maxMessage: 'Reason must not exceed {{ limit }} characters.'),
                ],
                'attr' => ['class' => 'form-control', 'rows' => 3, 'placeholder' => 'Reason for leave...'],
                'label_attr' => ['class' => 'form-label'],
            ])
            ->add('attachmentFile', FileType::class, [
                'mapped' => false,
                'required' => false,
                'label' => 'Supporting Document (PDF / Image)',
                'attr' => ['class' => 'form-control', 'accept' => '.pdf,.jpg,.jpeg,.png'],
                'constraints' => [
                    new Assert\File([
                        'maxSize' => '5M',
                        'mimeTypes' => ['application/pdf', 'image/jpeg', 'image/png'],
                        'mimeTypesMessage' => 'Please upload a valid PDF or image file (max 5 MB).',
                    ])
                ],
            ]);

        // Only HR can change status; employees always get PENDING
        if ($options['is_hr']) {
            $builder->add('status', ChoiceType::class, [
                'choices' => [
                    'Pending' => 'PENDING',
                    'Approved' => 'APPROVED',
                    'Rejected' => 'REJECTED',
                ],
                'constraints' => [new Assert\NotBlank(message: 'Please select a status.')],
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
            ]);
        }

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
        $resolver->setDefaults([
            'data_class' => Leave::class,
            'is_hr' => false,
        ]);
    }
}
