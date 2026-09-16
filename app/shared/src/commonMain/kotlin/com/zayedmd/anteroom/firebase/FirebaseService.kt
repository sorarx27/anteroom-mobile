package com.zayedmd.anteroom.firebase

import dev.gitlive.firebase.Firebase
import dev.gitlive.firebase.auth.auth
import dev.gitlive.firebase.firestore.firestore
import dev.gitlive.firebase.storage.storage
import dev.gitlive.firebase.functions.functions

object FirebaseService {
    val auth = Firebase.auth
    val firestore = Firebase.firestore
    val storage = Firebase.storage
    val functions = Firebase.functions
}
