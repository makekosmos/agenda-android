package com.mundus.agenda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mundus.agenda.ui.AgendaApp
import com.mundus.agenda.ui.AgendaTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as AgendaApplication).repository
        setContent { AgendaTheme { AgendaApp(repository) } }
    }
}
