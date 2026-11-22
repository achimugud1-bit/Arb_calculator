package com.arbmaster.pro

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.arbmaster.pro.data.ArbRepository
import com.arbmaster.pro.ui.ArbMasterApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = ArbRepository(applicationContext)
        setContent {
            ArbMasterApp(repo = repository)
        }
    }
}
