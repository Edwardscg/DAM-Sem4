package com.example.redsocialapp

import com.google.firebase.Timestamp

data class Post(
    val id: String = "",
    val texto: String = "",
    val fecha: Timestamp? = null
)