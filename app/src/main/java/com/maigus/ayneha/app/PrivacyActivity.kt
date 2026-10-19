// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 MAIGUS

package com.maigus.ayneha.app

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class PrivacyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_privacy)

        findViewById<TextView>(R.id.tv_version_date).text = getString(
            R.string.privacy_version_format,
            BuildConfig.VERSION_NAME,
            getString(R.string.privacy_last_update)
        )
    }
}
