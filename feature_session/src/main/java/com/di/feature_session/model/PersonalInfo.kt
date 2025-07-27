package com.di.feature_session.model

import java.time.LocalDate

data class PersonalInfo(
    val name: String = "",
    val age: Int? = null,
    val weight: Float? = null,
    val dateOfBirth: LocalDate? = null,
    val medicalNotes: String = "",
    val fitnessGoals: String = "",
    val emergencyContactName: String = "",
    val emergencyContactPhone: String = "",
    val emergencyContactEmail: String = ""
)