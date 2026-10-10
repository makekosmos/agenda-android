package com.mundus.agenda

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.mundus.agenda.ui.AgendaApp
import com.mundus.agenda.ui.AgendaTheme

class MainActivity : ComponentActivity() {
    private lateinit var repository: EngineAgendaRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        repository = EngineAgendaRepository(filesDir.absolutePath)
        setContent { AgendaTheme { AgendaApp(repository) } }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing) {
            repository.close()
        }
    }
}
