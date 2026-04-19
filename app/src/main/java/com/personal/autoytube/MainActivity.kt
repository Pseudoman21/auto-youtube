package com.personal.autoytube

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val text = TextView(this).apply {
            text = """
Auto YouTube

This app runs inside Android Auto — it has no phone UI.

Setup steps:
1. Open the Android Auto app
2. Tap the menu (≡) → About
3. Tap the version number 10 times to enable Developer Mode
4. Go back → menu → Developer Settings
5. Enable "Unknown sources"
6. Connect your phone to your car via USB
7. "Auto YouTube" will appear in the Android Auto launcher

The app lets you search and play YouTube videos directly on your car screen.
            """.trimIndent()
            textSize = 16f
            setPadding(48, 48, 48, 48)
        }

        val scroll = ScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(text)
        }
        scroll.addView(layout)
        setContentView(scroll)
    }
}
