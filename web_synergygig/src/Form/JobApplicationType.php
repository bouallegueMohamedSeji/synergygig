<?php

namespace App\Form;

use App\Entity\JobApplication;
use App\Entity\Offer;
use App\Entity\User;
use Symfony\Bridge\Doctrine\Form\Type\EntityType;
use Symfony\Component\Form\AbstractType;
use Symfony\Component\Form\Extension\Core\Type\ChoiceType;
use Symfony\Component\Form\Extension\Core\Type\TextareaType;
use Symfony\Component\Form\FormBuilderInterface;
use Symfony\Component\OptionsResolver\OptionsResolver;
use Symfony\Component\Validator\Constraints as Assert;

class JobApplicationType extends AbstractType
{
    public function buildForm(FormBuilderInterface $builder, array $options): void
    {
        $builder
            ->add('offer', EntityType::class, [
                'class' => Offer::class,
                'choice_label' => 'title',
                'placeholder' => 'Select offer',
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select a job offer.']),
                ],
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
            ->add('cover_letter', TextareaType::class, [
                'required' => false,
                'label' => 'Cover Letter',
                'attr' => ['class' => 'form-control', 'rows' => '5'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\Length(['max' => 5000, 'maxMessage' => 'Cover letter must not exceed {{ limit }} characters.']),
                ],
            ])
            ->add('status', ChoiceType::class, [
                'choices' => [
                    'Pending' => 'PENDING',
                    'Reviewed' => 'REVIEWED',
                    'Shortlisted' => 'SHORTLISTED',
                    'Accepted' => 'ACCEPTED',
                    'Rejected' => 'REJECTED',
                    'Withdrawn' => 'WITHDRAWN',
                ],
                'attr' => ['class' => 'form-control form-select'],
                'label_attr' => ['class' => 'form-label'],
                'constraints' => [
                    new Assert\NotBlank(['message' => 'Please select a status.']),
                ],
            ])
        ;
    }

    public function configureOptions(OptionsResolver $resolver): void
    {
        $resolver->setDefaults(['data_class' => JobApplication::class]);
    }
}
